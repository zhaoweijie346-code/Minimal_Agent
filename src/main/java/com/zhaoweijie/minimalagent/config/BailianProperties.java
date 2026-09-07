package com.zhaoweijie.minimalagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼 OpenAI Compatible API 的外部配置。
 */
@Component
@ConfigurationProperties(prefix = "llm")
public class BailianProperties {

    /** 当前启用的 LLM Provider 名称。 */
    private String provider = "bailian";

    /** 从 DASHSCOPE_API_KEY 环境变量注入的 API Key。 */
    private String apiKey = "";

    /** 可由 BAILIAN_BASE_URL 环境变量提供的 API 基础地址。 */
    private String baseUrl = "";

    /** 可由 BAILIAN_MODEL 覆盖的 Qwen 模型名称。 */
    private String model = "qwen-plus";

    /** 建立 HTTP 连接的最长等待时间。 */
    private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(10);

    /** 等待 HTTP 响应数据的最长时间。 */
    private java.time.Duration readTimeout = java.time.Duration.ofSeconds(60);

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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

    public java.time.Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(java.time.Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public java.time.Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(java.time.Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
