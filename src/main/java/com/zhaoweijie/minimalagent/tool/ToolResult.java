package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolResult(
        boolean success,
        String toolName,
        JsonNode data,
        String error
) {
}
