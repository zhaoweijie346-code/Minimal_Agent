package com.zhaoweijie.minimalagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云百炼 OpenAI Compatible API 的外部配置占位。
 *
 * <p>本阶段仅绑定配置，不创建网络客户端。</p>
 */
@Component
@ConfigurationProperties(prefix = "llm.bailian")
public class BailianProperties {

    /** 从 DASHSCOPE_API_KEY 环境变量注入的 API Key。 */
    private String apiKey = "";

    /** 可由 BAILIAN_BASE_URL 环境变量提供的 API 基础地址。 */
    private String baseUrl = "";

    /** 可由 BAILIAN_MODEL 覆盖的 Qwen 模型名称。 */
    private String model = "qwen-plus";

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
