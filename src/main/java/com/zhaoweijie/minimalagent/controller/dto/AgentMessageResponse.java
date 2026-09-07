package com.zhaoweijie.minimalagent.controller.dto;

import java.util.List;

/**
 * 一条 Session 消息的 HTTP DTO。
 *
 * @param role       消息角色
 * @param content    消息正文
 * @param toolCallId TOOL 消息关联的调用标识
 * @param toolCalls  ASSISTANT 消息发起的工具调用
 */
public record AgentMessageResponse(
        /** 消息角色。 */
        String role,
        /** 消息正文。 */
        String content,
        /** TOOL 消息关联的调用标识。 */
        String toolCallId,
        /** ASSISTANT 消息发起的工具调用。 */
        List<ToolCallResponse> toolCalls
) {
    public AgentMessageResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
