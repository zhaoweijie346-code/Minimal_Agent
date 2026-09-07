package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
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

    /** 提供当前已注册工具集合。 */
    private final ToolRegistry toolRegistry;

    /** 用于生成 OpenAI Compatible 工具定义。 */
    private final ObjectMapper objectMapper;

    /** 上下文系统指令和消息窗口配置。 */
    private final AgentContextProperties properties;

    /**
     * 创建 Agent Context 管理器。
     *
     * @param memoryManager Session Memory 管理器
     * @param toolRegistry  工具注册表
     * @param objectMapper  应用统一配置的 Jackson 对象映射器
     * @param properties    上下文配置属性
     */
    public ContextManager(
            SessionMemoryManager memoryManager,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            AgentContextProperties properties
    ) {
        this.memoryManager = memoryManager;
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
        // ContextManager 是每次调用模型前的统一入口，因此在这里执行 Memory 召回。
        SessionMemory memory = memoryManager.recall(userId, sessionId);
        List<AgentMessage> recentMessages = recentMessages(
                memory.recentMessages(),
                currentToolResult
        );

        return new AgentContext(
                properties.getSystemPrompt(),
                memory.summary(),
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
