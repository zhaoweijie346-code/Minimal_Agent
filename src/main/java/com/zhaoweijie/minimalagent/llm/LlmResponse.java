package com.zhaoweijie.minimalagent.llm;

import com.zhaoweijie.minimalagent.action.ToolCallAction;

import java.util.List;

/**
 * 一次 LLM 调用返回的供应商无关响应。
 *
 * @param content   assistant 文本内容；纯工具调用响应时可以为空
 * @param toolCalls assistant 请求的一个或多个结构化工具调用
 */
public record LlmResponse(
        /** assistant 文本内容；纯工具调用响应时可以为空。 */
        String content,
        /** assistant 请求的一个或多个结构化工具调用。 */
        List<ToolCallAction> toolCalls
) {

    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    /**
     * 判断响应是否包含工具调用。
     *
     * @return 至少包含一个工具调用时为 true
     */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    /**
     * 判断响应是否为无需执行工具的普通 assistant 回答。
     *
     * @return 不包含工具调用时为 true
     */
    public boolean isFinalResponse() {
        return toolCalls.isEmpty();
    }
}
