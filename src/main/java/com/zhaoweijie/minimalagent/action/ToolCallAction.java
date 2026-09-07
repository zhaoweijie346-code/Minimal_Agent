package com.zhaoweijie.minimalagent.action;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolCallAction(
        String toolCallId,
        String toolName,
        JsonNode arguments
) implements AgentAction {
}
