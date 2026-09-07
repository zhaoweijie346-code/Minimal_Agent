package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.SessionManager;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 在每次调用 LLM 前构建隔离、截断且结构化的 Agent Context。
 */
@Component
public class ContextManager {

    /** 提供 Session 读取与用户所有权校验。 */
    private final SessionManager sessionManager;

    /** 提供当前已注册工具集合。 */
    private final ToolRegistry toolRegistry;

    /** 用于生成 OpenAI Compatible 工具定义。 */
    private final ObjectMapper objectMapper;

    /** 上下文系统指令和消息窗口配置。 */
    private final AgentContextProperties properties;

    /**
     * 创建 Agent Context 管理器。
     *
     * @param sessionManager Session 管理器
     * @param toolRegistry   工具注册表
     * @param objectMapper   应用统一配置的 Jackson 对象映射器
     * @param properties     上下文配置属性
     */
    public ContextManager(
            SessionManager sessionManager,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            AgentContextProperties properties
    ) {
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.properties = properties;
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
        AgentSession session = sessionManager.getSession(sessionId, userId);
        List<AgentMessage> recentMessages = recentMessages(
                session.getMessages(),
                currentToolResult
        );

        return new AgentContext(
                properties.getSystemPrompt(),
                session.getSummary(),
                recentMessages,
                currentToolResult,
                buildToolDefinitions()
        );
    }

    /**
     * 截取消息尾部窗口，并在边界处向前扩展以保留完整工具消息链。
     */
    private List<AgentMessage> recentMessages(
            List<AgentMessage> messages,
            AgentMessage currentToolResult
    ) {
        int start = Math.max(0, messages.size() - properties.getMaxRecentMessages());
        start = expandForPersistedToolResult(messages, start);

        // 当前结果尚未入库时，也必须把它所响应的 assistant tool_call 保留在窗口中。
        if (currentToolResult != null) {
            int assistantIndex = findAssistantToolCall(
                    messages,
                    messages.size() - 1,
                    currentToolResult.toolCallId()
            );
            if (assistantIndex >= 0) {
                start = Math.min(start, assistantIndex);
            }
        }

        return List.copyOf(messages.subList(start, messages.size()));
    }

    /**
     * 当截断后的第一条消息是 tool result 时，回溯到对应 assistant tool_call。
     */
    private int expandForPersistedToolResult(List<AgentMessage> messages, int start) {
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
     * 从指定位置向前寻找包含目标 toolCallId 的 assistant 消息。
     */
    private int findAssistantToolCall(
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
     * 判断工具调用列表是否包含指定调用标识。
     */
    private boolean containsToolCall(List<ToolCallAction> toolCalls, String toolCallId) {
        return toolCalls.stream()
                .anyMatch(toolCall -> Objects.equals(toolCall.toolCallId(), toolCallId));
    }

    /**
     * 将当前 ToolRegistry 动态转换为 OpenAI Compatible tools 字段。
     */
    private List<JsonNode> buildToolDefinitions() {
        List<JsonNode> definitions = new ArrayList<>();
        for (AgentTool tool : toolRegistry.getAll().values()) {
            ObjectNode function = objectMapper.createObjectNode();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.set("parameters", tool.parameterSchema());

            ObjectNode definition = objectMapper.createObjectNode();
            definition.put("type", "function");
            definition.set("function", function);
            definitions.add(definition);
        }
        return List.copyOf(definitions);
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
