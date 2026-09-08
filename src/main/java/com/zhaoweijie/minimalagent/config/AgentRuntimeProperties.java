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

    /** 单条用户输入允许的最大字符数。 */
    private int maxUserMessageCharacters = 10000;

    /** 回传给模型的单条 Tool Result 允许的最大字符数。 */
    private int maxToolResultCharacters = 16000;

    public int getMaxRounds() {
        return maxRounds;
    }

    public void setMaxRounds(int maxRounds) {
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be greater than zero");
        }
        this.maxRounds = maxRounds;
    }

    public int getMaxUserMessageCharacters() {
        return maxUserMessageCharacters;
    }

    public void setMaxUserMessageCharacters(int maxUserMessageCharacters) {
        if (maxUserMessageCharacters < 1) {
            throw new IllegalArgumentException("maxUserMessageCharacters must be greater than zero");
        }
        this.maxUserMessageCharacters = maxUserMessageCharacters;
    }

    public int getMaxToolResultCharacters() {
        return maxToolResultCharacters;
    }

    public void setMaxToolResultCharacters(int maxToolResultCharacters) {
        if (maxToolResultCharacters < 256) {
            throw new IllegalArgumentException("maxToolResultCharacters must be at least 256");
        }
        this.maxToolResultCharacters = maxToolResultCharacters;
    }
}
