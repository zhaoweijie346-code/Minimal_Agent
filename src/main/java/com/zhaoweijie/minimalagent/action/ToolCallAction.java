package com.zhaoweijie.minimalagent.action;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 请求执行工具的 Agent 动作。
 *
 * @param toolCallId LLM 为本次工具调用生成的唯一标识
 * @param toolName   待调用的工具名称
 * @param arguments  LLM 生成的结构化工具参数
 */
public record ToolCallAction(
        /** LLM 为本次工具调用生成的唯一标识。 */
        String toolCallId,
        /** 待调用的工具名称。 */
        String toolName,
        /** LLM 生成的结构化工具参数。 */
        JsonNode arguments
) implements AgentAction {
}
