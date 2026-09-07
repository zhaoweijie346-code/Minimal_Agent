package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 LLM 调用所需的完整 Agent 上下文。
 *
 * @param systemPrompt      应用配置的系统指令
 * @param sessionSummary    Session 历史摘要；没有摘要时为空
 * @param recentMessages    截断后的近期结构化消息
 * @param currentToolResult 本轮尚未写入 Session 的工具结果消息
 * @param toolDefinitions   动态生成的 OpenAI Compatible tools 定义
 */
public record AgentContext(
        /** 应用配置的系统指令。 */
        String systemPrompt,
        /** Session 历史摘要；没有摘要时为空。 */
        String sessionSummary,
        /** 截断后的近期结构化消息。 */
        List<AgentMessage> recentMessages,
        /** 本轮尚未写入 Session 的工具结果消息。 */
        AgentMessage currentToolResult,
        /** 动态生成的 OpenAI Compatible tools 定义。 */
        List<JsonNode> toolDefinitions
) {

    public AgentContext {
        // 固化本次调用的列表快照，避免构建完成后被外部集合修改。
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        toolDefinitions = toolDefinitions == null ? List.of() : toolDefinitions.stream()
                .map(node -> (JsonNode) node.deepCopy())
                .toList();
    }

    /**
     * 按 API 调用顺序组装 System Prompt、摘要、历史消息和当前工具结果。
     *
     * @return 不可修改的结构化消息列表
     */
    public List<AgentMessage> messages() {
        List<AgentMessage> messages = new ArrayList<>();
        messages.add(new AgentMessage(AgentMessageRole.SYSTEM, systemPrompt, null, null));
        if (sessionSummary != null && !sessionSummary.isBlank()) {
            messages.add(new AgentMessage(
                    AgentMessageRole.SYSTEM,
                    "Session summary:\n" + sessionSummary,
                    null,
                    null
            ));
        }
        messages.addAll(recentMessages);
        if (currentToolResult != null) {
            messages.add(currentToolResult);
        }
        return List.copyOf(messages);
    }
}
