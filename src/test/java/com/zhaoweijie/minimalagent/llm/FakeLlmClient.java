package com.zhaoweijie.minimalagent.llm;

import com.zhaoweijie.minimalagent.context.AgentContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 单元测试使用的可编排、无网络 LlmClient 实现。
 */
public class FakeLlmClient implements LlmClient {

    /** 按调用顺序返回的预设响应。 */
    private final Deque<LlmResponse> responses = new ArrayDeque<>();

    /** 已接收的调用快照。 */
    private final List<Call> calls = new ArrayList<>();

    /**
     * 将响应加入后续调用队列。
     *
     * @param response 预设响应
     */
    public void enqueue(LlmResponse response) {
        responses.addLast(response);
    }

    @Override
    public LlmResponse chat(AgentContext context, List<ToolDefinition> tools) {
        calls.add(new Call(context, tools));
        if (responses.isEmpty()) {
            throw new IllegalStateException("No fake LLM response configured");
        }
        return responses.removeFirst();
    }

    /**
     * 获取已接收的调用快照。
     *
     * @return 不可修改的调用列表
     */
    public List<Call> calls() {
        return List.copyOf(calls);
    }

    /**
     * Fake Client 捕获的一次调用。
     *
     * @param context Agent 上下文
     * @param tools   工具定义快照
     */
    public record Call(
            /** Agent 上下文。 */
            AgentContext context,
            /** 工具定义快照。 */
            List<ToolDefinition> tools
    ) {

        public Call {
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }
}
