package com.zhaoweijie.minimalagent.controller.dto;

/**
 * Agent 对话完成后的 HTTP 响应。
 *
 * @param traceId   请求级 Trace 标识
 * @param sessionId 对话所属 Session 标识
 * @param answer    Agent 最终回答
 * @param rounds    实际 LLM 调用轮数
 */
public record ChatResponse(
        /** 请求级 Trace 标识。 */
        String traceId,
        /** 对话所属 Session 标识。 */
        String sessionId,
        /** Agent 最终回答。 */
        String answer,
        /** 实际 LLM 调用轮数。 */
        int rounds
) {
}
