package com.zhaoweijie.minimalagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent Runtime 主循环配置。
 */
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentRuntimeProperties {

    /** 单次用户请求允许调用 LLM 的最大轮数。 */
    private int maxRounds = 8;

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be greater than zero");
        }
        this.maxRounds = maxRounds;
    }
}
