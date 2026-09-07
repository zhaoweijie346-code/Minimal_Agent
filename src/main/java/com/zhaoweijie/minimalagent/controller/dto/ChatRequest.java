package com.zhaoweijie.minimalagent.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 执行一次 Agent 对话的 HTTP 请求。
 *
 * @param userId    请求用户标识
 * @param sessionId 已有 Session 标识；为空时自动创建
 * @param message   用户本轮输入
 */
public record ChatRequest(
        /** 请求用户标识。 */
        @NotBlank(message = "userId must not be blank") String userId,
        /** 已有 Session 标识；为空时自动创建。 */
        String sessionId,
        /** 用户本轮输入。 */
        @NotBlank(message = "message must not be blank") String message
) {
}
