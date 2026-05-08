package com.example.tasklist.lmstudio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lmstudio")
public class LmStudioProperties {

    /**
     * OpenAI-compatible root, e.g. http://localhost:1234/v1 (no trailing slash).
     */
    private String baseUrl = "http://localhost:1234/v1";

    /**
     * Model id as shown in LM Studio.
     */
    private String model = "";

    private String apiKey = "";

    private int connectTimeoutSeconds = 10;

    private int timeoutSeconds = 120;

    private int maxTokens = 2048;

    private int maxTaskTitleChars = 200;

    private int maxResponseChars = 32_000;

    /**
     * Max length of the combined user message for advice when board + ancestor context is included.
     */
    private int maxAdviceContextChars = 4000;

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getMaxTaskTitleChars() {
        return maxTaskTitleChars;
    }

    public void setMaxTaskTitleChars(int maxTaskTitleChars) {
        this.maxTaskTitleChars = maxTaskTitleChars;
    }

    public int getMaxResponseChars() {
        return maxResponseChars;
    }

    public void setMaxResponseChars(int maxResponseChars) {
        this.maxResponseChars = maxResponseChars;
    }

    public int getMaxAdviceContextChars() {
        return maxAdviceContextChars;
    }

    public void setMaxAdviceContextChars(int maxAdviceContextChars) {
        this.maxAdviceContextChars = maxAdviceContextChars;
    }
}
