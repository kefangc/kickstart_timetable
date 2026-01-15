package com.kickstart.timetable.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paddle")
public class PaddleProperties {
    /** Full URL of PaddleOCR layout parsing endpoint. */
    private String apiUrl;

    /** Authorization token used as: Authorization: token <TOKEN> */
    private String token;

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
