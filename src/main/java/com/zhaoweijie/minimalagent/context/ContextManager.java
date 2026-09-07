package com.zhaoweijie.minimalagent.context;

import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.config.SystemPromptProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在每次调用 LLM 前构建隔离、截断且结构化的 Agent Context。
 */
@Component
public class ContextManager {

    /** 在模型调用前召回并按需压缩 Session Memory。 */
    private final SessionMemoryManager memoryManager;

    /** 上下文系统指令和消息窗口配置。 */
    private final AgentContextProperties properties;

    /** 从独立资源文件提供 System Prompt。 */
    private final SystemPromptProvider systemPromptProvider;

    /**
     * 创建 Agent Context 管理器。
     *
     * @param memoryManager Session Memory 管理器
     * @param properties    上下文配置属性
     * @param systemPromptProvider System Prompt 提供者
     */
    public ContextManager(
            SessionMemoryManager memoryManager,
            AgentContextProperties properties,
            SystemPromptProvider systemPromptProvider
    ) {
        this.memoryManager = memoryManager;
        this.properties = properties;
        this.systemPromptProvider = systemPromptProvider;
    }

    /**
     * 构建不包含当前工具结果的上下文。
     *
     * @param userId    请求用户标识
     * @param sessionId Session 标识
     * @return 单次 LLM 调用上下文
     */
    public AgentContext build(String userId, String sessionId) {
        return build(userId, sessionId, null);
    }

    /**
     * 构建包含当前工具结果的上下文。
     *
     * @param userId           请求用户标识
     * @param sessionId        Session 标识
     * @param currentToolResult 本轮工具结果消息
     * @return 单次 LLM 调用上下文
     */
    public AgentContext build(
            String userId,
            String sessionId,
            AgentMessage currentToolResult
    ) {
        validateCurrentToolResult(currentToolResult);
        // ContextManager 是每次调用模型前的统一入口，因此在这里执行 Memory 召回。
        SessionMemory memory = memoryManager.recall(userId, sessionId);
        List<AgentMessage> recentMessages = recentMessages(
                memory.recentMessages(),
                currentToolResult
        );

        return new AgentContext(
                systemPromptProvider.getSystemPrompt(),
                memory.summary(),
                recentMessages,
                currentToolResult
        );
    }

    /**
     * 截取消息尾部窗口，并在边界处向前扩展以保留完整工具消息链。
     */
    private List<AgentMessage> recentMessages(
            List<AgentMessage> messages,
            AgentMessage currentToolResult
    ) {
        String currentToolCallId = currentToolResult == null
                ? null
                : currentToolResult.toolCallId();
        int start = MessageWindow.startIndex(
                messages,
                properties.getMaxRecentMessages(),
                currentToolCallId
        );
        return List.copyOf(messages.subList(start, messages.size()));
    }

    /**
     * 校验当前工具结果仍保留 role=tool 与 tool_call_id 关联信息。
     */
    private void validateCurrentToolResult(AgentMessage currentToolResult) {
        if (currentToolResult == null) {
            return;
        }
        if (currentToolResult.role() != AgentMessageRole.TOOL
                || currentToolResult.toolCallId() == null
                || currentToolResult.toolCallId().isBlank()) {
            throw new IllegalArgumentException(
                    "currentToolResult must be a TOOL message with toolCallId"
            );
        }
    }
}
