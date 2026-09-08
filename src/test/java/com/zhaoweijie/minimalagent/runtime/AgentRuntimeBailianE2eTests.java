package com.zhaoweijie.minimalagent.runtime;

import com.zhaoweijie.minimalagent.trace.AgentTrace;
import com.zhaoweijie.minimalagent.trace.AgentTraceRecorder;
import com.zhaoweijie.minimalagent.trace.TraceEvent;
import com.zhaoweijie.minimalagent.trace.TraceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 通过生产 AgentRuntime 调用真实百炼 Qwen 的端到端测试。
 *
 * <p>测试受 integration Profile 和 DASHSCOPE_API_KEY 双重保护，普通 Maven
 * 测试只加载上下文并跳过用例，不会产生真实请求或费用。</p>
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentRuntimeBailianE2eTests {

    /** 输出真实 E2E 问题和最终回答，不记录 API Key、Header 或底层 HTTP 请求。 */
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRuntimeBailianE2eTests.class);

    /** 所有用例共用的测试用户，不同场景使用不同 Session 保持隔离。 */
    private static final String USER_ID = "bailian-e2e-user";

    /** 生产 Agent 主循环，内部使用真实 BailianLlmClient 和真实工具。 */
    @Autowired
    private AgentRuntime agentRuntime;

    /** 生产内存 Trace 记录器，用于验证 LLM 与工具行为。 */
    @Autowired
    private AgentTraceRecorder traceRecorder;

    @BeforeEach
    void requireExplicitIntegrationOptIn() {
        String activeProfiles = firstNonBlank(
                System.getProperty("spring.profiles.active"),
                System.getenv("SPRING_PROFILES_ACTIVE")
        );
        String apiKey = System.getenv("DASHSCOPE_API_KEY");

        assumeTrue(hasProfile(activeProfiles, "integration"),
                "需要显式启用 integration Profile");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "需要通过 DASHSCOPE_API_KEY 提供百炼 API Key");
    }

    @Test
    void answersGreetingWithoutToolCall() {
        AgentRunResult result = run("你好");

        AgentTrace trace = assertCompleted(result);
        assertThat(trace.events())
                .noneMatch(event -> event.type() == TraceType.TOOL_CALL);
    }

    @Test
    void calculatesWithCalculatorTool() {
        AgentRunResult result = run("请使用 calculator 工具精确计算 (25+15)*3，并告诉我结果。");

        AgentTrace trace = assertCompleted(result);
        TraceEvent calculatorCall = assertSuccessfulTool(trace, "calculator");
        assertThat(calculatorCall.arguments().path("expression").asText()).isNotBlank();
    }

    @Test
    void searchesJava21WithSearchTool() {
        AgentRunResult result = run("请使用 search 工具查询 Java 21。");

        AgentTrace trace = assertCompleted(result);
        TraceEvent searchCall = assertSuccessfulTool(trace, "search");
        assertThat(searchCall.arguments().path("query").asText()).isNotBlank();
    }

    @Test
    void addsTodoWithTodoTool() {
        AgentRunResult result = run("请使用 todo 工具添加待办：周五写周报。");

        AgentTrace trace = assertCompleted(result);
        TraceEvent todoCall = assertSuccessfulTool(trace, "todo");
        assertThat(todoCall.arguments().path("action").asText()).isEqualTo("add");
        assertThat(todoCall.arguments().path("content").asText()).isNotBlank();
    }

    @Test
    void searchesThenAddsTodoBeforeFinalAnswer() {
        AgentRunResult result = run(
                "请先使用 search 工具查询 Java 21，再使用 todo 工具添加待办“学习 Java 21”，最后回复我。"
        );

        AgentTrace trace = assertCompleted(result);
        assertSuccessfulTool(trace, "search");
        assertSuccessfulTool(trace, "todo");
        assertThat(toolCallNames(trace)).containsSubsequence("search", "todo");
    }

    @Test
    void keepsContextAcrossCustomerCountTodoAndListFollowUps() {
        String sessionId = "e2e-follow-up-" + UUID.randomUUID();

        AgentRunResult countResult = run(
                sessionId,
                "明天有1个客户要见，后天有两个客户要见，未来两天内我要见多少位客户？"
        );
        AgentTrace countTrace = assertCompleted(countResult);
        // 简单加法允许模型直接回答；本场景重点验证后续 Todo 工具能读取同一 Session 的结论。
        assertThat(countResult.answer()).contains("3");

        AgentRunResult addResult = run(
                sessionId,
                "帮我把未来两天要见多少位客户记为待办"
        );
        AgentTrace addTrace = assertCompleted(addResult);
        TraceEvent addCall = assertSuccessfulTool(addTrace, "todo");
        assertThat(addCall.arguments().path("action").asText()).isEqualTo("add");
        assertThat(addCall.arguments().path("content").asText()).contains("3");

        AgentRunResult listResult = run(sessionId, "查看一下我的待办");
        AgentTrace listTrace = assertCompleted(listResult);
        TraceEvent listCall = assertSuccessfulTool(listTrace, "todo");
        assertThat(listCall.arguments().path("action").asText()).isEqualTo("list");

        assertThat(countResult.sessionId()).isEqualTo(sessionId);
        assertThat(addResult.sessionId()).isEqualTo(sessionId);
        assertThat(listResult.sessionId()).isEqualTo(sessionId);
    }

    /**
     * 使用唯一 Session 执行真实请求，避免并发或重复执行时共享历史。
     */
    private AgentRunResult run(String message) {
        String sessionId = "e2e-" + UUID.randomUUID();
        return run(sessionId, message);
    }

    /**
     * 在指定 Session 中执行真实请求，用于验证多轮追问的 Context 与 Todo 状态延续。
     */
    private AgentRunResult run(String sessionId, String message) {
        AgentRunResult result = agentRuntime.run(USER_ID, sessionId, message);
        LOGGER.info("Bailian E2E question: {}", message);
        LOGGER.info("Bailian E2E final answer: {}", result.answer());
        return result;
    }

    /**
     * 验证请求产生了非空最终回答、有效 Trace，且没有 Runtime 错误事件。
     */
    private AgentTrace assertCompleted(AgentRunResult result) {
        assertThat(result.traceId()).isNotBlank();
        assertThat(result.sessionId()).isNotBlank();
        assertThat(result.answer()).isNotBlank();
        assertThat(result.rounds()).isPositive();

        AgentTrace trace = traceRecorder.getTrace(result.traceId());
        assertThat(trace.traceId()).isEqualTo(result.traceId());
        assertThat(trace.userId()).isEqualTo(USER_ID);
        assertThat(trace.sessionId()).isEqualTo(result.sessionId());
        assertThat(trace.events()).anyMatch(event -> event.type() == TraceType.LLM_CALL);
        assertThat(trace.events()).noneMatch(event -> event.type() == TraceType.ERROR);
        assertThat(trace.events().getLast().type()).isEqualTo(TraceType.FINAL);
        assertThat(trace.events().getLast().finalAnswer()).isEqualTo(result.answer());
        return trace;
    }

    /**
     * 验证指定工具确实执行成功，且 TOOL_CALL 与 TOOL_RESULT 通过同一 tool_call_id 关联。
     */
    private TraceEvent assertSuccessfulTool(AgentTrace trace, String toolName) {
        TraceEvent toolCall = trace.events().stream()
                .filter(event -> event.type() == TraceType.TOOL_CALL)
                .filter(event -> toolName.equals(event.toolName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未调用预期工具: " + toolName));
        assertThat(toolCall.toolCallId()).isNotBlank();
        assertThat(toolCall.arguments()).isNotNull();

        assertThat(trace.events().stream()
                .filter(event -> event.type() == TraceType.TOOL_RESULT)
                .filter(event -> toolCall.toolCallId().equals(event.toolCallId()))
                .filter(event -> toolName.equals(event.toolName())))
                .singleElement()
                .satisfies(toolResult -> {
                    assertThat(toolResult.toolResult()).isNotNull();
                    assertThat(toolResult.toolResult().success()).isTrue();
                });
        return toolCall;
    }

    /**
     * 按事件顺序提取 Trace 中实际调用的工具名称。
     */
    private List<String> toolCallNames(AgentTrace trace) {
        return trace.events().stream()
                .filter(event -> event.type() == TraceType.TOOL_CALL)
                .map(TraceEvent::toolName)
                .toList();
    }

    /**
     * 返回第一个非空配置值。
     */
    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    /**
     * 精确解析逗号、分号或空白分隔的 Spring Profile 列表。
     */
    private boolean hasProfile(String activeProfiles, String expectedProfile) {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }
        return Arrays.stream(activeProfiles.split("[,;\\s]+"))
                .anyMatch(expectedProfile::equals);
    }
}
