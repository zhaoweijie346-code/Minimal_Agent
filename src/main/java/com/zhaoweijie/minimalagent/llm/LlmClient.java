package com.zhaoweijie.minimalagent.llm;

import com.zhaoweijie.minimalagent.context.AgentContext;

import java.util.List;

/**
 * Runtime 调用大语言模型的供应商无关接口。
 */
public interface LlmClient {

    /**
     * 使用当前 Agent 上下文和动态工具定义请求一次模型推理。
     *
     * @param context 本次调用的结构化 Agent 上下文
     * @param tools   从 ToolRegistry 动态生成的工具定义
     * @return 模型的普通回答或一个/多个工具调用
     */
    LlmResponse chat(AgentContext context, List<ToolDefinition> tools);
}
