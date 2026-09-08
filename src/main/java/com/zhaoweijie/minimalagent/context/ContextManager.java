package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.config.SystemPromptProvider;
import com.zhaoweijie.minimalagent.exception.ContextWindowExceededException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        String systemPrompt = systemPromptProvider.getSystemPrompt();
        String summary = truncate(memory.summary(), properties.getMaxMessageCharacters());
        AgentMessage limitedCurrentToolResult = limitMessage(currentToolResult);
        List<AgentMessage> budgetedMessages = fitWithinCharacterBudget(
                systemPrompt,
                summary,
                recentMessages.stream().map(this::limitMessage).toList(),
                limitedCurrentToolResult
        );

        return new AgentContext(
                systemPrompt,
                summary,
                budgetedMessages,
                limitedCurrentToolResult
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

    /**
     * 在保留最新完整对话轮次的前提下，从头删除较老轮次直至满足总字符预算。
     */
    private List<AgentMessage> fitWithinCharacterBudget(
            String systemPrompt,
            String summary,
            List<AgentMessage> messages,
            AgentMessage currentToolResult
    ) {
        List<AgentMessage> selected = new ArrayList<>(messages);
        while (contextCharacters(systemPrompt, summary, selected, currentToolResult)
                > properties.getMaxContextCharacters()
                && removeOldestTurn(selected)) {
            // 每次删除一个完整旧轮次，避免留下脱离 assistant(tool_calls) 的孤立 tool 消息。
        }
        if (contextCharacters(systemPrompt, summary, selected, currentToolResult)
                > properties.getMaxContextCharacters()) {
            throw new ContextWindowExceededException();
        }
        return List.copyOf(selected);
    }

    /**
     * 删除第一个用户轮次；若窗口从 assistant/tool 链开始，则删除到下一个 USER 之前。
     */
    private boolean removeOldestTurn(List<AgentMessage> messages) {
        if (messages.size() < 2) {
            return false;
        }
        for (int index = 1; index < messages.size(); index++) {
            if (messages.get(index).role() == AgentMessageRole.USER) {
                messages.subList(0, index).clear();
                return true;
            }
        }
        return false;
    }

    /**
     * 限制单条消息正文及工具 arguments 的大小，同时保留角色和 tool_call_id 关系。
     */
    private AgentMessage limitMessage(AgentMessage message) {
        if (message == null) {
            return null;
        }
        int limit = properties.getMaxMessageCharacters();
        List<ToolCallAction> toolCalls = message.toolCalls().stream()
                .map(toolCall -> new ToolCallAction(
                        toolCall.toolCallId(),
                        toolCall.toolName(),
                        limitArguments(toolCall.arguments(), limit)
                ))
                .toList();
        return new AgentMessage(
                message.role(),
                truncate(message.content(), limit),
                message.toolCallId(),
                toolCalls
        );
    }

    /**
     * 超长历史 arguments 使用合法 JSON 占位符，避免构造非法 Function Calling 消息。
     */
    private JsonNode limitArguments(JsonNode arguments, int limit) {
        if (arguments == null || arguments.toString().length() <= limit) {
            return arguments;
        }
        return JsonNodeFactory.instance.objectNode()
                .put("truncated", true)
                .put("note", "Historical tool arguments exceeded the context limit");
    }

    /**
     * 估算发送给兼容 API 的消息字符数，用稳定上界控制请求大小。
     */
    private int contextCharacters(
            String systemPrompt,
            String summary,
            List<AgentMessage> messages,
            AgentMessage currentToolResult
    ) {
        long total = textLength(systemPrompt) + textLength(summary) + 96L;
        for (AgentMessage message : messages) {
            total += messageCharacters(message);
        }
        total += messageCharacters(currentToolResult);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /** 估算单条结构化消息及工具调用元数据的字符数。 */
    private int messageCharacters(AgentMessage message) {
        if (message == null) {
            return 0;
        }
        long total = 32L + textLength(message.content()) + textLength(message.toolCallId());
        for (ToolCallAction toolCall : message.toolCalls()) {
            total += 48L
                    + textLength(toolCall.toolCallId())
                    + textLength(toolCall.toolName())
                    + (toolCall.arguments() == null ? 0 : toolCall.arguments().toString().length());
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    /** 将文本裁剪到配置上限，并添加明确的截断标记。 */
    private String truncate(String value, int maxCharacters) {
        if (value == null || value.length() <= maxCharacters) {
            return value;
        }
        String marker = "...[truncated]";
        return value.substring(0, maxCharacters - marker.length()) + marker;
    }

    /** 返回可空文本的字符数。 */
    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }
}
