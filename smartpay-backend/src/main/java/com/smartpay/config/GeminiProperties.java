package com.smartpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Gemini API settings (ai.gemini.* properties). When no API key is
 * configured, the local fallback parser is used instead.
 */
@ConfigurationProperties(prefix = "ai.gemini")
public class GeminiProperties {

    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String model = "gemini-2.0-flash";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
