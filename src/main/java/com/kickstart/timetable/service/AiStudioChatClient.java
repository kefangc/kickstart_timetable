package com.kickstart.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kickstart.timetable.config.AiStudioProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiStudioChatClient {

    private final AiStudioProperties props;
    private final RestClient restClient;
    private final ObjectMapper om;

    public AiStudioChatClient(AiStudioProperties props, RestClient.Builder builder, ObjectMapper om) {
        this.props = props;
        this.om = om;
        // Build without baseUrl to avoid startup failure when env vars are not set yet.
        this.restClient = builder.build();
    }

    /**
     * Call AIStudio Chat Completions (non-streaming) and return extracted assistant text.
     */
    public String chat(String system, String user, double temperature, int maxTokens) {
        JsonNode resp = chatRawWith(system, user, temperature, maxTokens, null, null);
        if (resp == null) {
            throw new RuntimeException("AIStudio 无响应");
        }

        String content = extractFinalContent(resp);
        if (content == null || content.isBlank()) {
            String model = resp.at("/model").asText("");
            String id = resp.at("/id").asText("");
            String finishReason = resp.at("/choices/0/finish_reason").asText("");
            String reasoningTokens = resp.at("/usage/completion_tokens_details/reasoning_tokens").asText("");
            throw new RuntimeException(
                    "AIStudio 返回为空（未找到 message.content）。model=" + model + ", id=" + id + ", finish_reason=" + finishReason
                            + (reasoningTokens.isBlank() ? "" : ", reasoning_tokens=" + reasoningTokens)
                            + "。提示：如果你使用的是 thinking 模型，可能会先把 token 用在 reasoning_content 上，导致 content 为空。"
            );
        }
        return content;
    }

    /**
     * Call AIStudio with response_format=json_object using structuredModel.
     *
     * This is recommended for timetable parsing because:
     * 1) thinking 模型可能把 token 全花在 reasoning_content 导致 content 为空
     * 2) AIStudio 文档说明 response_format 结构化输出仅在部分模型上支持（如 ERNIE 4.5 / 4.0-turbo / 3.5）
     */
    public String chatStructuredJsonObject(String system, String user, double temperature, int maxTokens) {
        ObjectNode rf = om.createObjectNode();
        rf.put("type", "json_object");
        String modelOverride = (props.getStructuredModel() == null || props.getStructuredModel().isBlank())
                ? props.getModel()
                : props.getStructuredModel();

        JsonNode resp = chatRawWith(system, user, temperature, maxTokens, modelOverride, rf);
        if (resp == null) {
            throw new RuntimeException("AIStudio 无响应");
        }
        String content = extractFinalContent(resp);
        if (content == null || content.isBlank()) {
            String model = resp.at("/model").asText("");
            String id = resp.at("/id").asText("");
            String finishReason = resp.at("/choices/0/finish_reason").asText("");
            throw new RuntimeException("AIStudio 返回为空（未找到 message.content）。model=" + model + ", id=" + id + ", finish_reason=" + finishReason);
        }
        return content;
    }

    /**
     * Minimal raw call for debugging connectivity and response shape.
     */
    public JsonNode chatRaw(String system, String user, double temperature, int maxTokens) {
        return chatRawWith(system, user, temperature, maxTokens, null, null);
    }

    /**
     * Raw call with optional model override and response_format.
     */
    public JsonNode chatRawWith(String system, String user, double temperature, int maxTokens, String modelOverride, JsonNode responseFormat) {
        validateConfig();

        String model = (modelOverride == null || modelOverride.isBlank()) ? props.getModel() : modelOverride;

        ObjectNode req = om.createObjectNode();
        req.put("model", model);

        ArrayNode messages = om.createArrayNode();
        messages.add(msg("system", system));
        messages.add(msg("user", user));
        req.set("messages", messages);

        req.put("temperature", temperature);
        req.put("stream", false);

        // AIStudio 官方示例使用 max_completion_tokens；为兼容也同时带上 max_tokens
        req.put("max_completion_tokens", maxTokens);
        req.put("max_tokens", maxTokens);

        if (responseFormat != null && !responseFormat.isMissingNode() && !responseFormat.isNull()) {
            req.set("response_format", responseFormat);
        }

        String chatPath = (props.getChatPath() == null || props.getChatPath().isBlank())
                ? "/chat/completions"
                : props.getChatPath();
        String url = props.getBaseUrl().replaceAll("/$", "") + chatPath;

        return restClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(JsonNode.class);
    }

    private void validateConfig() {
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new IllegalStateException("aistudio.base-url 未配置");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException("AISTUDIO_API_KEY 未配置（aistudio.api-key）");
        }
        if (props.getModel() == null || props.getModel().isBlank()) {
            throw new IllegalStateException("aistudio.model 未配置");
        }
    }

    public String extractFinalContent(JsonNode resp) {
        if (resp == null) return "";
        // OpenAI-compatible: choices[0].message.content
        JsonNode contentNode = resp.at("/choices/0/message/content");
        String content = extractContentNode(contentNode);
        if (content != null && !content.isBlank()) return content;

        // Some services return choices[0].text
        content = resp.at("/choices/0/text").asText("");
        if (content != null && !content.isBlank()) return content;

        // Some services return output_text
        content = resp.at("/output_text").asText("");
        if (content != null && !content.isBlank()) return content;

        return "";
    }

    private String extractContentNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText("");

        // Multimodal style: content: [{type:"text", text:"..."}, ...]
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode it : node) {
                if (it == null || it.isNull()) continue;
                if (it.isTextual()) {
                    sb.append(it.asText(""));
                    continue;
                }
                String t = it.path("text").asText("");
                if (!t.isBlank()) sb.append(t);
            }
            return sb.toString();
        }

        // Sometimes content is object: {text:"..."}
        if (node.isObject()) {
            String t = node.path("text").asText("");
            if (!t.isBlank()) return t;
        }

        return node.asText("");
    }

    private ObjectNode msg(String role, String content) {
        ObjectNode m = om.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        return m;
    }
}
