package com.zhaoweijie.minimalagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 上下文构建相关的可配置属性。
 */
@Component
@ConfigurationProperties(prefix = "agent.context")
public class AgentContextProperties {

    /** 每次请求放在首位的 System Prompt。 */
    private String systemPrompt = "You are a helpful AI assistant.";

    /** 从 Session 尾部选取的最大近期消息数量。 */
    private int maxRecentMessages = 20;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public int getMaxRecentMessages() {
        return maxRecentMessages;
    }

    public void setMaxRecentMessages(int maxRecentMessages) {
        if (maxRecentMessages < 1) {
            throw new IllegalArgumentException("maxRecentMessages must be greater than zero");
        }
        this.maxRecentMessages = maxRecentMessages;
    }
}
