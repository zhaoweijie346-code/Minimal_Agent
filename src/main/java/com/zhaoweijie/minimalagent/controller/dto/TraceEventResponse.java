package com.zhaoweijie.minimalagent.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 一条 Agent Trace 事件的 HTTP DTO。
 */
public record TraceEventResponse(
        /** Trace 事件类型。 */ String type,
        /** 事件所属循环轮次。 */ int round,
        /** LLM 是否返回 tool_calls。 */ Boolean toolCallsReturned,
        /** 工具调用关联标识。 */ String toolCallId,
        /** 工具名称。 */ String toolName,
        /** 工具调用参数。 */ JsonNode arguments,
        /** 工具执行结果。 */ ToolResultResponse toolResult,
        /** 事件耗时，单位毫秒。 */ long durationMillis,
        /** 安全错误说明。 */ String error,
        /** 最终回答。 */ String finalAnswer,
        /** 事件创建时间。 */ Instant createdAt
) {
    public TraceEventResponse {
        arguments = arguments == null ? null : arguments.deepCopy();
    }
}
