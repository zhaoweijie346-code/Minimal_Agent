package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具执行后的标准化结果。
 *
 * @param success  工具是否执行成功
 * @param toolName 实际执行的工具名称
 * @param data     工具成功时返回的结构化数据
 * @param error    工具失败时返回的错误信息
 */
public record ToolResult(
        /** 工具是否执行成功。 */
        boolean success,
        /** 实际执行的工具名称。 */
        String toolName,
        /** 工具成功时返回的结构化数据。 */
        JsonNode data,
        /** 工具失败时返回的错误信息。 */
        String error
) {
}
