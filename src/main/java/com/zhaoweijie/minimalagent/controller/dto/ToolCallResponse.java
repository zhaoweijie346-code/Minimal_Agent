package com.zhaoweijie.minimalagent.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Session 消息中的工具调用 HTTP DTO。
 *
 * @param toolCallId 工具调用关联标识
 * @param toolName   工具名称
 * @param arguments  结构化工具参数
 */
public record ToolCallResponse(
        /** 工具调用关联标识。 */
        String toolCallId,
        /** 工具名称。 */
        String toolName,
        /** 结构化工具参数。 */
        JsonNode arguments
) {
    public ToolCallResponse {
        arguments = arguments == null ? null : arguments.deepCopy();
    }
}
