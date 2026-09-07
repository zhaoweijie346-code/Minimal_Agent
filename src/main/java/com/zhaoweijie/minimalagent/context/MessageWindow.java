package com.zhaoweijie.minimalagent.context;

import com.zhaoweijie.minimalagent.action.ToolCallAction;

import java.util.List;
import java.util.Objects;

/**
 * 计算近期消息窗口边界，并保护结构化工具消息链。
 */
final class MessageWindow {

    private MessageWindow() {
    }

    /**
     * 计算近期窗口起点；必要时向前扩展到对应 assistant tool_call。
     *
     * @param messages          完整消息列表
     * @param maximumMessages   期望保留的最大消息数量
     * @param currentToolCallId 当前未入库工具结果所响应的调用标识
     * @return 包含式窗口起始下标
     */
    static int startIndex(
            List<AgentMessage> messages,
            int maximumMessages,
            String currentToolCallId
    ) {
        int start = Math.max(0, messages.size() - maximumMessages);
        start = expandForPersistedToolResult(messages, start);

        if (currentToolCallId != null) {
            int assistantIndex = findAssistantToolCall(
                    messages,
                    messages.size() - 1,
                    currentToolCallId
            );
            if (assistantIndex >= 0) {
                start = Math.min(start, assistantIndex);
            }
        }
        return start;
    }

    /**
     * 当窗口首条是工具结果时回溯到对应工具调用消息。
     */
    private static int expandForPersistedToolResult(List<AgentMessage> messages, int start) {
        int expandedStart = start;
        while (expandedStart > 0) {
            AgentMessage firstMessage = messages.get(expandedStart);
            if (firstMessage.role() != AgentMessageRole.TOOL || firstMessage.toolCallId() == null) {
                break;
            }

            int assistantIndex = findAssistantToolCall(
                    messages,
                    expandedStart - 1,
                    firstMessage.toolCallId()
            );
            if (assistantIndex < 0) {
                break;
            }
            expandedStart = assistantIndex;
        }
        return expandedStart;
    }

    /**
     * 向前查找包含指定 toolCallId 的 assistant 消息。
     */
    private static int findAssistantToolCall(
            List<AgentMessage> messages,
            int fromIndex,
            String toolCallId
    ) {
        for (int index = fromIndex; index >= 0; index--) {
            AgentMessage message = messages.get(index);
            if (message.role() == AgentMessageRole.ASSISTANT
                    && containsToolCall(message.toolCalls(), toolCallId)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 判断工具调用集合中是否包含指定调用标识。
     */
    private static boolean containsToolCall(List<ToolCallAction> toolCalls, String toolCallId) {
        return toolCalls.stream()
                .anyMatch(toolCall -> Objects.equals(toolCall.toolCallId(), toolCallId));
    }
}
