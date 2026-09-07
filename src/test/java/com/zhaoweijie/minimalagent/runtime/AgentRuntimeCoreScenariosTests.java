package com.zhaoweijie.minimalagent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.config.AgentRuntimeProperties;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.context.BasicMemoryCompressor;
import com.zhaoweijie.minimalagent.context.ContextManager;
import com.zhaoweijie.minimalagent.context.SessionMemoryManager;
import com.zhaoweijie.minimalagent.exception.SessionAccessDeniedException;
import com.zhaoweijie.minimalagent.llm.FakeLlmClient;
import com.zhaoweijie.minimalagent.llm.LlmResponse;
import com.zhaoweijie.minimalagent.llm.ToolDefinitionProvider;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.InMemorySessionManager;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.CalculatorTool;
import com.zhaoweijie.minimalagent.tool.SearchTool;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import com.zhaoweijie.minimalagent.tool.todo.TodoRepository;
import com.zhaoweijie.minimalagent.tool.todo.TodoStatus;
import com.zhaoweijie.minimalagent.tool.todo.TodoTool;
import com.zhaoweijie.minimalagent.trace.InMemoryAgentTraceRecorder;
import com.zhaoweijie.minimalagent.trace.TraceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 使用 Fake LLM 与真实基础设施验证 Agent Runtime 的核心用户场景。
 *
 * <p>本测试类不会创建 BailianLlmClient，也不会发起任何真实网络请求。</p>
 */
class AgentRuntimeCoreScenariosTests {

    /** 测试 JSON 参数和 ToolResult 消息所用的 Jackson 映射器。 */
    private ObjectMapper objectMapper;

    /** 可按顺序返回模型决策并捕获每轮上下文的无网络 Fake LLM。 */
    private FakeLlmClient llmClient;

    /** 保存并隔离测试 Session 的线程安全内存实现。 */
    private InMemorySessionManager sessionManager;

    /** 保存 Runtime 行为事件并用于断言工具调用次数的内存 Trace。 */
    private InMemoryAgentTraceRecorder traceRecorder;

    /** 默认不触发压缩的上下文配置。 */
    private AgentContextProperties contextProperties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        llmClient = new FakeLlmClient();
        sessionManager = new InMemorySessionManager();
        traceRecorder = new InMemoryAgentTraceRecorder();
        contextProperties = new AgentContextProperties();
        contextProperties.setCompressionThreshold(100);
    }

    @Test
    void returnsDirectGreetingWithoutCallingATool() {
        llmClient.enqueue(new LlmResponse("你好！有什么可以帮你？", List.of()));

        AgentRunResult result = runtime(List.of(
                new CalculatorTool(objectMapper),
                new SearchTool(objectMapper)
        )).run("user-a", "session-chat", "你好");

        assertThat(result.answer()).isEqualTo("你好！有什么可以帮你？");
        assertThat(result.rounds()).isEqualTo(1);
        assertThat(llmClient.calls()).hasSize(1);
        assertThat(sessionManager.getSession("session-chat", "user-a").getMessages())
                .extracting(AgentMessage::role)
                .containsExactly(AgentMessageRole.USER, AgentMessageRole.ASSISTANT);
        assertThat(traceRecorder.getTrace(result.traceId()).events())
                .noneMatch(event -> event.type() == TraceType.TOOL_CALL);
    }

    @Test
    void executesRealCalculatorAndReturnsFinalAnswer() throws Exception {
        llmClient.enqueue(toolResponse(
                "call-calculator-1",
                "calculator",
                objectMapper.createObjectNode().put("expression", "(1+2)*3")
        ));
        llmClient.enqueue(new LlmResponse("计算结果是 9。", List.of()));

        AgentRunResult result = runtime(List.of(new CalculatorTool(objectMapper)))
                .run("user-a", "session-calculator", "计算 (1+2)*3");

        AgentSession session = sessionManager.getSession("session-calculator", "user-a");
        JsonNode toolResult = objectMapper.readTree(session.getMessages().get(2).content());
        assertThat(result.answer()).isEqualTo("计算结果是 9。");
        assertThat(toolResult.path("success").booleanValue()).isTrue();
        assertThat(toolResult.path("data").path("result").decimalValue())
                .isEqualByComparingTo("9");
        assertToolCallPair(session, 1, 2, "call-calculator-1");
        assertThat(llmClient.calls().get(1).context().recentMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL
                );
    }

    @Test
    void executesRealSearchForJava21() throws Exception {
        llmClient.enqueue(toolResponse(
                "call-search-1",
                "search",
                objectMapper.createObjectNode().put("query", "Java 21")
        ));
        llmClient.enqueue(new LlmResponse("已找到 Java 21 的相关信息。", List.of()));

        runtime(List.of(new SearchTool(objectMapper)))
                .run("user-a", "session-search", "查 Java 21");

        AgentSession session = sessionManager.getSession("session-search", "user-a");
        JsonNode results = objectMapper.readTree(session.getMessages().get(2).content())
                .path("data")
                .path("results");
        assertThat(results).isNotEmpty();
        assertThat(results.toString()).contains("Java 21");
        assertToolCallPair(session, 1, 2, "call-search-1");
    }

    @Test
    void addsFridayReportTodoToCurrentSession() {
        TodoRepository todoRepository = new TodoRepository();
        llmClient.enqueue(toolResponse(
                "call-todo-add-1",
                "todo",
                objectMapper.createObjectNode()
                        .put("action", "add")
                        .put("content", "周五写周报")
        ));
        llmClient.enqueue(new LlmResponse("已记录周五写周报。", List.of()));

        runtime(List.of(new TodoTool(todoRepository, objectMapper)))
                .run("user-a", "session-todo", "记周五写周报");

        assertThat(todoRepository.list("user-a", "session-todo"))
                .singleElement()
                .satisfies(todo -> {
                    assertThat(todo.content()).isEqualTo("周五写周报");
                    assertThat(todo.status()).isEqualTo(TodoStatus.PENDING);
                });
    }

    @Test
    void executesRealSearchThenTodoThenFinal() {
        TodoRepository todoRepository = new TodoRepository();
        llmClient.enqueue(toolResponse(
                "call-search-multi",
                "search",
                objectMapper.createObjectNode().put("query", "Java 21")
        ));
        llmClient.enqueue(toolResponse(
                "call-todo-multi",
                "todo",
                objectMapper.createObjectNode()
                        .put("action", "add")
                        .put("content", "学习 Java 21 新特性")
        ));
        llmClient.enqueue(new LlmResponse("已查询并创建待办。", List.of()));

        AgentRunResult result = runtime(List.of(
                new SearchTool(objectMapper),
                new TodoTool(todoRepository, objectMapper)
        )).run("user-a", "session-multi", "查询 Java 21 并加入待办");

        AgentSession session = sessionManager.getSession("session-multi", "user-a");
        assertThat(result.rounds()).isEqualTo(3);
        assertThat(session.getMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT
                );
        assertToolCallPair(session, 1, 2, "call-search-multi");
        assertToolCallPair(session, 3, 4, "call-todo-multi");
        assertThat(todoRepository.list("user-a", "session-multi"))
                .extracting(todo -> todo.content())
                .containsExactly("学习 Java 21 新特性");
    }

    @Test
    void keepsHistoryForPureChatFollowup() {
        llmClient.enqueue(new LlmResponse("你好，小明。", List.of()));
        llmClient.enqueue(new LlmResponse("你刚才说你叫小明。", List.of()));
        AgentRuntime runtime = runtime(List.of());

        runtime.run("user-a", "session-followup", "你好，我叫小明");
        runtime.run("user-a", "session-followup", "我刚才说我叫什么？");

        assertThat(llmClient.calls().get(1).context().recentMessages())
                .extracting(AgentMessage::content)
                .containsExactly(
                        "你好，我叫小明",
                        "你好，小明。",
                        "我刚才说我叫什么？"
                );
    }

    @Test
    void keepsPreviousToolChainWhenFollowupAlsoUsesATool() {
        llmClient.enqueue(toolResponse(
                "call-first",
                "calculator",
                objectMapper.createObjectNode().put("expression", "1+1")
        ));
        llmClient.enqueue(new LlmResponse("结果是 2。", List.of()));
        llmClient.enqueue(toolResponse(
                "call-followup",
                "calculator",
                objectMapper.createObjectNode().put("expression", "2+1")
        ));
        llmClient.enqueue(new LlmResponse("再加 1 后是 3。", List.of()));
        AgentRuntime runtime = runtime(List.of(new CalculatorTool(objectMapper)));

        runtime.run("user-a", "session-tool-followup", "计算 1+1");
        runtime.run("user-a", "session-tool-followup", "在刚才结果上再加 1");

        // 第二个请求的首轮调用必须带上上次完整工具链和当前追问。
        assertThat(llmClient.calls().get(2).context().recentMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.USER
                );
        AgentSession session = sessionManager.getSession("session-tool-followup", "user-a");
        assertToolCallPair(session, 1, 2, "call-first");
        assertToolCallPair(session, 5, 6, "call-followup");
    }

    @Test
    void isolatesSessionsAndRejectsCrossUserRuntimeAccess() {
        llmClient.enqueue(new LlmResponse("A 的回答", List.of()));
        llmClient.enqueue(new LlmResponse("B 的回答", List.of()));
        AgentRuntime runtime = runtime(List.of());

        runtime.run("user-a", "session-a", "A 的问题");
        runtime.run("user-a", "session-b", "B 的问题");

        assertThat(sessionManager.getSession("session-a", "user-a").getMessages())
                .extracting(AgentMessage::content)
                .containsExactly("A 的问题", "A 的回答");
        assertThat(sessionManager.getSession("session-b", "user-a").getMessages())
                .extracting(AgentMessage::content)
                .containsExactly("B 的问题", "B 的回答");
        assertThat(llmClient.calls().get(1).context().recentMessages())
                .extracting(AgentMessage::content)
                .containsExactly("B 的问题");
        assertThatThrownBy(() -> runtime.run("user-b", "session-a", "越权访问"))
                .isInstanceOf(SessionAccessDeniedException.class);
        assertThat(llmClient.calls()).hasSize(2);
    }

    @Test
    void returnsToolArgumentFailureToLlmForMissingCalculatorExpression() throws Exception {
        llmClient.enqueue(toolResponse(
                "call-invalid-arguments",
                "calculator",
                objectMapper.createObjectNode()
        ));
        llmClient.enqueue(new LlmResponse("缺少需要计算的表达式。", List.of()));

        AgentRunResult result = runtime(List.of(new CalculatorTool(objectMapper)))
                .run("user-a", "session-invalid", "帮我算一下");

        JsonNode toolResult = objectMapper.readTree(
                sessionManager.getSession("session-invalid", "user-a")
                        .getMessages()
                        .get(2)
                        .content()
        );
        assertThat(toolResult.path("success").booleanValue()).isFalse();
        assertThat(toolResult.path("error").textValue()).contains("expression");
        assertThat(result.answer()).isEqualTo("缺少需要计算的表达式。");
        assertToolCallPair(
                sessionManager.getSession("session-invalid", "user-a"),
                1,
                2,
                "call-invalid-arguments"
        );
    }

    @Test
    void rejectsBlankUserInputBeforeCallingLlm() {
        AgentRuntime runtime = runtime(List.of());

        assertThatThrownBy(() -> runtime.run("user-a", "session-blank", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userMessage");
        assertThat(llmClient.calls()).isEmpty();
    }

    @Test
    void compressesOldConversationBeforeTheNextLlmCall() {
        contextProperties.setCompressionThreshold(2);
        contextProperties.setMaxRecentMessages(1);
        llmClient.enqueue(new LlmResponse("第一轮回答", List.of()));
        llmClient.enqueue(new LlmResponse("第二轮回答", List.of()));
        AgentRuntime runtime = runtime(List.of());

        runtime.run("user-a", "session-memory", "第一轮目标");
        runtime.run("user-a", "session-memory", "第二轮追问");

        assertThat(llmClient.calls().get(1).context().sessionSummary())
                .contains("第一轮目标", "第一轮回答");
        assertThat(llmClient.calls().get(1).context().recentMessages())
                .extracting(AgentMessage::content)
                .containsExactly("第二轮追问");
        AgentSession session = sessionManager.getSession("session-memory", "user-a");
        assertThat(session.getSummary()).contains("第一轮目标", "第一轮回答");
        assertThat(session.getMessages())
                .extracting(AgentMessage::content)
                .containsExactly("第二轮追问", "第二轮回答");
    }

    /**
     * 使用真实 Session、Context、Registry 与 Trace 构建待测 Runtime。
     */
    private AgentRuntime runtime(List<AgentTool> tools) {
        ToolRegistry toolRegistry = new ToolRegistry(tools);
        SessionMemoryManager memoryManager = new SessionMemoryManager(
                sessionManager,
                new BasicMemoryCompressor(contextProperties),
                contextProperties
        );
        ContextManager contextManager = new ContextManager(
                memoryManager,
                contextProperties,
                () -> "system prompt"
        );
        return new AgentRuntime(
                llmClient,
                contextManager,
                sessionManager,
                toolRegistry,
                new ToolDefinitionProvider(toolRegistry),
                objectMapper,
                traceRecorder,
                new AgentRuntimeProperties()
        );
    }

    /**
     * 创建包含单个百炼原生 Function Call 的 Fake 响应。
     */
    private LlmResponse toolResponse(String toolCallId, String toolName, JsonNode arguments) {
        return new LlmResponse(
                null,
                List.of(new ToolCallAction(toolCallId, toolName, arguments))
        );
    }

    /**
     * 同时断言 assistant.tool_calls 与紧随其后的 role=tool 消息使用同一调用 ID。
     */
    private void assertToolCallPair(
            AgentSession session,
            int assistantIndex,
            int toolIndex,
            String expectedToolCallId
    ) {
        AgentMessage assistantMessage = session.getMessages().get(assistantIndex);
        AgentMessage toolMessage = session.getMessages().get(toolIndex);
        assertThat(assistantMessage.role()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(assistantMessage.toolCalls())
                .singleElement()
                .extracting(ToolCallAction::toolCallId)
                .isEqualTo(expectedToolCallId);
        assertThat(toolMessage.role()).isEqualTo(AgentMessageRole.TOOL);
        assertThat(toolMessage.toolCallId()).isEqualTo(expectedToolCallId);
    }
}
