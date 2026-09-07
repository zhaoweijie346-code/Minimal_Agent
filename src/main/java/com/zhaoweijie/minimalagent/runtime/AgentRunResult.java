package com.zhaoweijie.minimalagent.runtime;

/**
 * 一次 Agent Runtime 执行完成后的用户可见结果。
 *
 * @param sessionId 本次请求所属 Session 标识
 * @param answer    模型生成的最终回答
 * @param rounds    本次执行实际调用 LLM 的轮数
 */
public record AgentRunResult(
        /** 本次请求所属 Session 标识。 */
        String sessionId,
        /** 模型生成的最终回答。 */
        String answer,
        /** 本次执行实际调用 LLM 的轮数。 */
        int rounds
) {
}
