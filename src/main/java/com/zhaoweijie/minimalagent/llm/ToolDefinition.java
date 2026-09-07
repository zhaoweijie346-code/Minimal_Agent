package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 提供给 LLM 的供应商无关工具定义。
 *
 * @param name        工具唯一名称
 * @param description 工具能力描述
 * @param parameters  工具参数 JSON Schema
 */
public record ToolDefinition(
        /** 工具唯一名称。 */
        String name,
        /** 工具能力描述。 */
        String description,
        /** 工具参数 JSON Schema。 */
        JsonNode parameters
) {

    public ToolDefinition {
        // Schema 节点可变，创建定义时复制以隔离具体工具内部状态。
        parameters = parameters == null ? null : parameters.deepCopy();
    }
}
