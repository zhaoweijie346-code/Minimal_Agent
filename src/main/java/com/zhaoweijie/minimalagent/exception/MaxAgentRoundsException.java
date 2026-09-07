package com.zhaoweijie.minimalagent.exception;

/**
 * Agent 在配置的最大轮数内仍未生成最终回答时抛出的异常。
 */
public class MaxAgentRoundsException extends RuntimeException {

    /** 本次运行允许的最大 LLM 调用轮数。 */
    private final int maxRounds;

    /**
     * 创建最大轮数异常。
     *
     * @param maxRounds 已耗尽的最大轮数
     */
    public MaxAgentRoundsException(int maxRounds) {
        super("Agent exceeded maximum rounds: " + maxRounds);
        this.maxRounds = maxRounds;
    }

    public int getMaxRounds() {
        return maxRounds;
    }
}
