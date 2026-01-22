package com.kickstart.timetable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aistudio")
public class AiStudioProperties {

    /** Base URL, e.g. https://aistudio.baidu.com/llm/lmapi/v3 */
    private String baseUrl;

    /** Chat completions path, default: /chat/completions */
    private String chatPath = "/chat/completions";

    /** API Key / Access Token */
    private String apiKey;

    /** Default model name, e.g. ernie-5.0-thinking-preview */
    private String model;

    /**
     * Model used for structured outputs (timetable parsing). Should be a model that supports
     * response_format (structured output), such as ERNIE 4.5 / 4.0-turbo / 3.5.
     * ernie-4.5-turbo-128k-preview
     * ernie-speed-128k
     */
    private String structuredModel = "ernie-4.5-turbo-128k-preview";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getChatPath() {
        return chatPath;
    }

    public void setChatPath(String chatPath) {
        this.chatPath = chatPath;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStructuredModel() {
        return structuredModel;
    }

    public void setStructuredModel(String structuredModel) {
        this.structuredModel = structuredModel;
    }
}
