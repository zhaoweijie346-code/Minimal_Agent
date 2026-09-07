package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Agent 可调用工具的统一协议。
 */
public interface AgentTool {

    /**
     * 获取工具的唯一名称。
     *
     * @return 提供给 LLM 和运行时使用的工具名称
     */
    String name();

    /**
     * 获取工具能力说明。
     *
     * @return 提供给 LLM 的工具描述
     */
    String description();

    /**
     * 获取工具参数的 JSON Schema。
     *
     * @return 描述工具参数的 JSON Schema
     */
    JsonNode parameterSchema();

    /**
     * 在指定上下文中执行工具。
     *
     * @param context   当前用户和会话上下文
     * @param arguments LLM 生成的结构化调用参数
     * @return 标准化工具执行结果
     */
    ToolResult execute(ToolExecutionContext context, JsonNode arguments);
}
