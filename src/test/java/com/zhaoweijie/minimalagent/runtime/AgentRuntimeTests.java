package com.zhaoweijie.minimalagent.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.config.AgentRuntimeProperties;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.context.BasicMemoryCompressor;
import com.zhaoweijie.minimalagent.context.ContextManager;
import com.zhaoweijie.minimalagent.context.SessionMemoryManager;
import com.zhaoweijie.minimalagent.exception.MaxAgentRoundsExceededException;
import com.zhaoweijie.minimalagent.llm.FakeLlmClient;
import com.zhaoweijie.minimalagent.llm.LlmResponse;
import com.zhaoweijie.minimalagent.llm.ToolDefinitionProvider;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.InMemorySessionManager;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolExecutionContext;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import com.zhaoweijie.minimalagent.trace.AgentTrace;
import com.zhaoweijie.minimalagent.trace.InMemoryAgentTraceRecorder;
import com.zhaoweijie.minimalagent.trace.TraceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRuntimeTests {

    /** 测试使用的 Jackson 映射器。 */
    private ObjectMapper objectMapper;

    /** 可编排模型响应并捕获上下文的 Fake LLM Client。 */
    private FakeLlmClient llmClient;

    /** 测试使用的线程安全内存 Session 管理器。 */
    private InMemorySessionManager sessionManager;

    /** 测试使用的行为 Trace 记录器。 */
    private InMemoryAgentTraceRecorder traceRecorder;

    /** Runtime 最大轮数配置。 */
    private AgentRuntimeProperties runtimeProperties;

    /** 使用真实 Memory 召回逻辑的上下文管理器。 */
    private ContextManager contextManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        llmClient = new FakeLlmClient();
        sessionManager = new InMemorySessionManager();
        traceRecorder = new InMemoryAgentTraceRecorder();
        runtimeProperties = new AgentRuntimeProperties();

        AgentContextProperties contextProperties = new AgentContextProperties();
        contextProperties.setCompressionThreshold(100);
        SessionMemoryManager memoryManager = new SessionMemoryManager(
                sessionManager,
                new BasicMemoryCompressor(contextProperties),
                contextProperties
        );
        contextManager = new ContextManager(
                memoryManager,
                contextProperties,
                () -> "system prompt"
        );
    }

    @Test
    void completesSingleToolLoopAndPreservesFullMessageChain() throws Exception {
        AgentTool calculator = tool("calculator");
        ObjectNode calculatorData = objectMapper.createObjectNode().put("result", 2);
        when(calculator.execute(any(), any())).thenReturn(
                new ToolResult(true, "calculator", calculatorData, null)
        );
        llmClient.enqueue(toolResponse(
                "call-calculator",
                "calculator",
                objectMapper.createObjectNode().put("expression", "1+1")
        ));
        llmClient.enqueue(new LlmResponse("结果是 2。", List.of()));

        AgentRunResult result = runtime(List.of(calculator)).run(
                "user-1",
                "session-1",
                "计算 1+1"
        );

        assertThat(result.traceId()).isNotBlank();
        assertThat(result.sessionId()).isEqualTo("session-1");
        assertThat(result.answer()).isEqualTo("结果是 2。");
        assertThat(result.rounds()).isEqualTo(2);
        AgentSession session = sessionManager.getSession("session-1", "user-1");
        assertThat(session.getMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT
                );
        assertThat(session.getMessages().get(1).toolCalls().getFirst().toolCallId())
                .isEqualTo("call-calculator");
        assertThat(session.getMessages().get(2).toolCallId()).isEqualTo("call-calculator");
        assertThat(objectMapper.readTree(session.getMessages().get(2).content()).path("success").booleanValue())
                .isTrue();

        // 第二次模型调用必须看到 user → assistant(tool_calls) → tool(tool_call_id) 完整链。
        assertThat(llmClient.calls().get(1).context().recentMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL
                );
        ArgumentCaptor<ToolExecutionContext> executionContext =
                ArgumentCaptor.forClass(ToolExecutionContext.class);
        verify(calculator).execute(executionContext.capture(), any());
        assertThat(executionContext.getValue())
                .isEqualTo(new ToolExecutionContext("user-1", "session-1"));
        AgentTrace trace = traceRecorder.getTrace(result.traceId());
        assertThat(trace.userId()).isEqualTo("user-1");
        assertThat(trace.sessionId()).isEqualTo("session-1");
        assertThat(trace.events())
                .extracting(event -> event.type())
                .containsExactly(
                        TraceType.LLM_CALL,
                        TraceType.TOOL_CALL,
                        TraceType.TOOL_RESULT,
                        TraceType.LLM_CALL,
                        TraceType.FINAL
                );
        assertThat(trace.events().get(0).round()).isEqualTo(1);
        assertThat(trace.events().get(0).toolCallsReturned()).isTrue();
        assertThat(trace.events().get(1).toolCallId()).isEqualTo("call-calculator");
        assertThat(trace.events().get(1).arguments().path("expression").textValue())
                .isEqualTo("1+1");
        assertThat(trace.events().get(2).toolCallId()).isEqualTo("call-calculator");
        assertThat(trace.events().get(2).toolResult().success()).isTrue();
        assertThat(trace.events().get(3).round()).isEqualTo(2);
        assertThat(trace.events().get(3).toolCallsReturned()).isFalse();
        assertThat(trace.events().get(4).finalAnswer()).isEqualTo("结果是 2。");
        assertThat(trace.events())
                .allSatisfy(event -> assertThat(event.durationMillis()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void supportsSearchThenTodoThenFinalAcrossRounds() {
        AgentTool search = tool("search");
        AgentTool todo = tool("todo");
        when(search.execute(any(), any())).thenReturn(new ToolResult(
                true,
                "search",
                objectMapper.createObjectNode().putArray("results").add("Java 21"),
                null
        ));
        when(todo.execute(any(), any())).thenReturn(new ToolResult(
                true,
                "todo",
                objectMapper.createObjectNode().put("id", "todo-1"),
                null
        ));
        llmClient.enqueue(toolResponse(
                "call-search",
                "search",
                objectMapper.createObjectNode().put("query", "Java 21")
        ));
        llmClient.enqueue(toolResponse(
                "call-todo",
                "todo",
                objectMapper.createObjectNode().put("action", "add").put("content", "学习 Java 21")
        ));
        llmClient.enqueue(new LlmResponse("已查询并创建待办。", List.of()));

        AgentRunResult result = runtime(List.of(search, todo)).run(
                "user-1",
                "session-1",
                "查询 Java 21 并加入待办"
        );

        assertThat(result.rounds()).isEqualTo(3);
        assertThat(sessionManager.getSession("session-1", "user-1").getMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT
                );
        assertThat(llmClient.calls()).hasSize(3);
        assertThat(llmClient.calls().getFirst().tools())
                .extracting(definition -> definition.name())
                .containsExactly("search", "todo");
    }

    @Test
    void returnsThrownToolErrorToModelAndLetsItAnswer() throws Exception {
        AgentTool failingTool = tool("failing-tool");
        when(failingTool.execute(any(), any())).thenThrow(new IllegalStateException("secret detail"));
        llmClient.enqueue(toolResponse(
                "call-failure",
                "failing-tool",
                objectMapper.createObjectNode()
        ));
        llmClient.enqueue(new LlmResponse("工具失败，暂时无法完成。", List.of()));

        AgentRunResult result = runtime(List.of(failingTool)).run(
                "user-1",
                "session-1",
                "执行失败工具"
        );

        JsonNode toolMessage = objectMapper.readTree(
                sessionManager.getSession("session-1", "user-1").getMessages().get(2).content()
        );
        assertThat(toolMessage.path("success").booleanValue()).isFalse();
        assertThat(toolMessage.path("error").textValue())
                .isEqualTo("Tool execution failed: IllegalStateException")
                .doesNotContain("secret detail");
        assertThat(result.answer()).isEqualTo("工具失败，暂时无法完成。");
        assertThat(traceRecorder.getTrace(result.traceId()).events())
                .filteredOn(event -> event.type() == TraceType.ERROR)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.toolCallId()).isEqualTo("call-failure");
                    assertThat(event.toolName()).isEqualTo("failing-tool");
                    assertThat(event.error()).isEqualTo("Tool execution failed: IllegalStateException");
                });
    }

    @Test
    void returnsUnknownToolAsToolResultInsteadOfBreakingLoop() throws Exception {
        llmClient.enqueue(toolResponse(
                "call-unknown",
                "unknown-tool",
                objectMapper.createObjectNode()
        ));
        llmClient.enqueue(new LlmResponse("该工具不可用。", List.of()));

        AgentRunResult result = runtime(List.of()).run(
                "user-1",
                "session-1",
                "调用未知工具"
        );

        AgentMessage toolMessage = sessionManager
                .getSession("session-1", "user-1")
                .getMessages()
                .get(2);
        JsonNode resultJson = objectMapper.readTree(toolMessage.content());
        assertThat(toolMessage.toolCallId()).isEqualTo("call-unknown");
        assertThat(resultJson.path("success").booleanValue()).isFalse();
        assertThat(resultJson.path("error").textValue()).contains("unknown-tool");
        assertThat(result.answer()).isEqualTo("该工具不可用。");
    }

    @Test
    void stopsAfterConfiguredMaximumRounds() {
        runtimeProperties.setMaxRounds(2);
        AgentTool calculator = tool("calculator");
        when(calculator.execute(any(), any())).thenReturn(new ToolResult(
                false,
                "calculator",
                null,
                "retry"
        ));
        llmClient.enqueue(toolResponse(
                "call-1",
                "calculator",
                objectMapper.createObjectNode().put("expression", "bad")
        ));
        llmClient.enqueue(toolResponse(
                "call-2",
                "calculator",
                objectMapper.createObjectNode().put("expression", "still bad")
        ));

        assertThatThrownBy(() -> runtime(List.of(calculator)).run(
                "user-1",
                "session-1",
                "不断重试"
        )).isInstanceOfSatisfying(MaxAgentRoundsExceededException.class, exception ->
                assertThat(exception.getMaxRounds()).isEqualTo(2));
        assertThat(llmClient.calls()).hasSize(2);
        assertThat(sessionManager.getSession("session-1", "user-1").getMessages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.USER,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL
                );
    }

    @Test
    void defaultsMaximumRoundsToEight() {
        assertThat(new AgentRuntimeProperties().getMaxRounds()).isEqualTo(8);
    }

    @Test
    void createsANewTraceForEveryRequestInTheSameSession() {
        llmClient.enqueue(new LlmResponse("first", List.of()));
        llmClient.enqueue(new LlmResponse("second", List.of()));
        AgentRuntime runtime = runtime(List.of());

        AgentRunResult first = runtime.run("user-1", "session-1", "first request");
        AgentRunResult second = runtime.run("user-1", "session-1", "second request");

        assertThat(first.traceId()).isNotEqualTo(second.traceId());
        assertThat(traceRecorder.getTracesBySession("session-1"))
                .extracting(AgentTrace::traceId)
                .containsExactly(first.traceId(), second.traceId());
    }

    @Test
    void recordsLlmFailureAndRuntimeErrorWithoutInternalExceptionMessage() {
        AgentRuntime runtime = runtime(List.of());

        assertThatThrownBy(() -> runtime.run("user-1", "session-1", "fail"))
                .isInstanceOf(IllegalStateException.class);

        AgentTrace trace = traceRecorder.getTracesBySession("session-1").getFirst();
        assertThat(trace.events())
                .extracting(event -> event.type())
                .containsExactly(TraceType.LLM_CALL, TraceType.ERROR);
        assertThat(trace.events().get(0).error()).isEqualTo("IllegalStateException");
        assertThat(trace.events().get(1).error()).isEqualTo("IllegalStateException");
        assertThat(trace.events().toString())
                .doesNotContain("No fake LLM response configured", "Authorization", "api-key");
    }

    /**
     * 使用指定工具构建包含真实 Session、Context、Registry 和 Trace 的 Runtime。
     */
    private AgentRuntime runtime(List<AgentTool> tools) {
        ToolRegistry toolRegistry = new ToolRegistry(tools);
        return new AgentRuntime(
                llmClient,
                contextManager,
                sessionManager,
                toolRegistry,
                new ToolDefinitionProvider(toolRegistry),
                objectMapper,
                traceRecorder,
                runtimeProperties
        );
    }

    /**
     * 创建具有动态名称和最小参数 Schema 的 Mockito 工具。
     */
    private AgentTool tool(String name) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(name + " description");
        when(tool.parameterSchema()).thenReturn(
                objectMapper.createObjectNode().put("type", "object")
        );
        return tool;
    }

    /**
     * 创建包含一个原生 Function Calling 的 Fake LLM 响应。
     */
    private LlmResponse toolResponse(String callId, String toolName, JsonNode arguments) {
        return new LlmResponse(
                null,
                List.of(new ToolCallAction(callId, toolName, arguments))
        );
    }
}
