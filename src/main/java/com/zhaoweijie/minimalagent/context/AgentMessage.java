package com.zhaoweijie.minimalagent.context;

import com.zhaoweijie.minimalagent.action.ToolCallAction;

import java.util.List;

/**
 * Agent 上下文中的一条标准消息。
 *
 * @param role       消息角色
 * @param content    消息正文；仅包含工具调用的助手消息可以为空
 * @param toolCallId TOOL 消息所响应的工具调用标识
 * @param toolCalls  ASSISTANT 消息请求的工具调用列表
 */
public record AgentMessage(
        /** 消息角色。 */
        AgentMessageRole role,
        /** 消息正文；仅包含工具调用的助手消息可以为空。 */
        String content,
        /** TOOL 消息所响应的工具调用标识。 */
        String toolCallId,
        /** ASSISTANT 消息请求的工具调用列表。 */
        List<ToolCallAction> toolCalls
) {

    public AgentMessage {
        // 固化调用列表，避免外部修改已经写入上下文的消息元数据。
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
