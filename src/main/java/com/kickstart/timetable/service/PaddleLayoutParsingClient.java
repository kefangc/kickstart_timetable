package com.kickstart.timetable.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kickstart.timetable.config.PaddleProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Component
public class PaddleLayoutParsingClient {

    private final PaddleProperties props;
    private final RestClient restClient;
    private final ObjectMapper om;

    public PaddleLayoutParsingClient(PaddleProperties props, RestClient.Builder builder, ObjectMapper om) {
        this.props = props;
        this.restClient = builder.build();
        this.om = om;
    }

    /**
     * Calls PaddleOCR layout parsing endpoint and returns the markdown text.
     */
    public String extractMarkdown(byte[] fileBytes, int fileType) {
        if (props.getApiUrl() == null || props.getApiUrl().isBlank()) {
            throw new IllegalStateException("paddle.api-url 未配置");
        }
        if (props.getToken() == null || props.getToken().isBlank()) {
            throw new IllegalStateException("PADDLE_TOKEN 未配置（paddle.token）");
        }

        String fileBase64 = Base64.getEncoder().encodeToString(fileBytes);

        ObjectNode payload = om.createObjectNode();
        payload.put("file", fileBase64);
        payload.put("fileType", fileType); // 1=image, 0=pdf
        payload.put("useDocOrientationClassify", false);
        payload.put("useDocUnwarping", false);
        payload.put("useChartRecognition", false);

        JsonNode resp = restClient.post()
                .uri(props.getApiUrl())
                .header(HttpHeaders.AUTHORIZATION, "token " + props.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        if (resp == null) {
            throw new RuntimeException("PaddleOCR 无响应");
        }

        // Expected: resp.result.layoutParsingResults[0].markdown.text
        JsonNode markdownNode = resp.at("/result/layoutParsingResults/0/markdown/text");
        if (markdownNode.isMissingNode() || markdownNode.asText().isBlank()) {
            throw new RuntimeException("PaddleOCR 返回中未找到 markdown.text");
        }
        return markdownNode.asText();
    }
}
