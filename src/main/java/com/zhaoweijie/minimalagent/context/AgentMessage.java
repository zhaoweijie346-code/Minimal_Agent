package com.zhaoweijie.minimalagent.context;

import com.zhaoweijie.minimalagent.action.ToolCallAction;

import java.util.List;

public record AgentMessage(
        AgentMessageRole role,
        String content,
        String toolCallId,
        List<ToolCallAction> toolCalls
) {

    public AgentMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
