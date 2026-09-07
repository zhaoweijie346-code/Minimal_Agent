package com.zhaoweijie.minimalagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 上下文构建相关的可配置属性。
 */
@Component
@ConfigurationProperties(prefix = "agent.context")
public class AgentContextProperties {

    /** 从 Session 尾部选取的最大近期消息数量。 */
    private int maxRecentMessages = 20;

    /** 触发 Session 历史消息压缩的数量阈值。 */
    private int compressionThreshold = 40;

    /** 压缩摘要允许保留的最大字符数。 */
    private int maxSummaryCharacters = 4000;

    public int getMaxRecentMessages() {
        return maxRecentMessages;
    }

    public void setMaxRecentMessages(int maxRecentMessages) {
        if (maxRecentMessages < 1) {
            throw new IllegalArgumentException("maxRecentMessages must be greater than zero");
        }
        this.maxRecentMessages = maxRecentMessages;
    }

    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    public void setCompressionThreshold(int compressionThreshold) {
        if (compressionThreshold < 1) {
            throw new IllegalArgumentException("compressionThreshold must be greater than zero");
        }
        this.compressionThreshold = compressionThreshold;
    }

    public int getMaxSummaryCharacters() {
        return maxSummaryCharacters;
    }

    public void setMaxSummaryCharacters(int maxSummaryCharacters) {
        if (maxSummaryCharacters < 1) {
            throw new IllegalArgumentException("maxSummaryCharacters must be greater than zero");
        }
        this.maxSummaryCharacters = maxSummaryCharacters;
    }
}
