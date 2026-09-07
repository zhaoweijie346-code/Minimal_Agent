package com.zhaoweijie.minimalagent.controller.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Trace 中标准工具结果的 HTTP DTO。
 *
 * @param success  工具是否执行成功
 * @param toolName 工具名称
 * @param data     成功时的结构化数据
 * @param error    失败时的错误说明
 */
public record ToolResultResponse(
        /** 工具是否执行成功。 */
        boolean success,
        /** 工具名称。 */
        String toolName,
        /** 成功时的结构化数据。 */
        JsonNode data,
        /** 失败时的错误说明。 */
        String error
) {
    public ToolResultResponse {
        data = data == null ? null : data.deepCopy();
    }
}
