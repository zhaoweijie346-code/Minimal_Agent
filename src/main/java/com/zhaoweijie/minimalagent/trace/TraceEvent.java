package com.zhaoweijie.minimalagent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhaoweijie.minimalagent.tool.ToolResult;

import java.time.Instant;

/**
 * 一条不包含模型内部思维链的 Agent 行为追踪事件。
 *
 * @param type          行为事件类型
 * @param sessionId     事件所属 Session 标识
 * @param toolCallId    工具调用关联标识；非工具事件时为空
 * @param toolName      工具名称；非工具事件时为空
 * @param arguments     工具参数；非工具调用事件时为空
 * @param toolResult    工具结果；非工具结果事件时为空
 * @param finalResponse 最终回答；非最终回答事件时为空
 * @param createdAt     事件创建时间
 */
public record TraceEvent(
        /** 行为事件类型。 */
        TraceEventType type,
        /** 事件所属 Session 标识。 */
        String sessionId,
        /** 工具调用关联标识；非工具事件时为空。 */
        String toolCallId,
        /** 工具名称；非工具事件时为空。 */
        String toolName,
        /** 工具参数；非工具调用事件时为空。 */
        JsonNode arguments,
        /** 工具结果；非工具结果事件时为空。 */
        ToolResult toolResult,
        /** 最终回答；非最终回答事件时为空。 */
        String finalResponse,
        /** 事件创建时间。 */
        Instant createdAt
) {

    public TraceEvent {
        // JsonNode 可变，Trace 必须保存事件发生时的独立快照。
        arguments = arguments == null ? null : arguments.deepCopy();
        if (toolResult != null && toolResult.data() != null) {
            toolResult = new ToolResult(
                    toolResult.success(),
                    toolResult.toolName(),
                    toolResult.data().deepCopy(),
                    toolResult.error()
            );
        }
    }
}
