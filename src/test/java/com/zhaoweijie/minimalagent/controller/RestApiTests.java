package com.zhaoweijie.minimalagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.exception.InvalidLlmOutputException;
import com.zhaoweijie.minimalagent.exception.LlmApiException;
import com.zhaoweijie.minimalagent.exception.LlmErrorType;
import com.zhaoweijie.minimalagent.exception.LlmTimeoutException;
import com.zhaoweijie.minimalagent.exception.MaxAgentRoundsException;
import com.zhaoweijie.minimalagent.exception.SessionAccessDeniedException;
import com.zhaoweijie.minimalagent.exception.SessionNotFoundException;
import com.zhaoweijie.minimalagent.exception.ToolArgumentException;
import com.zhaoweijie.minimalagent.exception.ToolNotFoundException;
import com.zhaoweijie.minimalagent.exception.TraceNotFoundException;
import com.zhaoweijie.minimalagent.exception.TraceAccessDeniedException;
import com.zhaoweijie.minimalagent.runtime.AgentRunResult;
import com.zhaoweijie.minimalagent.runtime.AgentRuntime;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.SessionManager;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.trace.AgentTrace;
import com.zhaoweijie.minimalagent.trace.AgentTraceRecorder;
import com.zhaoweijie.minimalagent.trace.TraceEvent;
import com.zhaoweijie.minimalagent.trace.TraceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AgentController.class, SessionController.class, TraceController.class})
@Import({ApiDtoMapper.class, GlobalExceptionHandler.class})
class RestApiTests {

    /** 模拟 HTTP 请求的 Spring MVC 测试入口。 */
    @Autowired
    private MockMvc mockMvc;

    /** 测试 JSON 参数构建使用的 Jackson 映射器。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** Controller 测试使用的 Runtime Mock。 */
    @MockitoBean
    private AgentRuntime agentRuntime;

    /** Controller 测试使用的 SessionManager Mock。 */
    @MockitoBean
    private SessionManager sessionManager;

    /** Controller 测试使用的 TraceRecorder Mock。 */
    @MockitoBean
    private AgentTraceRecorder traceRecorder;

    @Test
    void createsSession() throws Exception {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(sessionManager.createSession("user-a")).thenReturn(new AgentSession(
                "session-1", "user-a", List.of(), null, createdAt, createdAt
        ));

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user-a\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.userId").value("user-a"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"));
    }

    @Test
    void chatsAndReturnsTraceSessionAndAnswer() throws Exception {
        when(agentRuntime.run("user-a", "session-1", "hello")).thenReturn(
                new AgentRunResult("trace-1", "session-1", "answer", 2)
        );

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-a","sessionId":"session-1","message":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.answer").value("answer"))
                .andExpect(jsonPath("$.rounds").value(2));
    }

    @Test
    void returnsStructuredSessionMessages() throws Exception {
        ToolCallAction toolCall = new ToolCallAction(
                "call-1",
                "calculator",
                objectMapper.createObjectNode().put("expression", "1+1")
        );
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        when(sessionManager.getSession("session-1", "user-a")).thenReturn(new AgentSession(
                "session-1",
                "user-a",
                List.of(
                        new AgentMessage(AgentMessageRole.USER, "calculate", null, null),
                        new AgentMessage(AgentMessageRole.ASSISTANT, null, null, List.of(toolCall)),
                        new AgentMessage(AgentMessageRole.TOOL, "{\"success\":true}", "call-1", null)
                ),
                null,
                now,
                now
        ));

        mockMvc.perform(get("/api/sessions/session-1/messages")
                        .queryParam("userId", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"))
                .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[1].toolCalls[0].toolCallId").value("call-1"))
                .andExpect(jsonPath("$.messages[1].toolCalls[0].toolName").value("calculator"))
                .andExpect(jsonPath("$.messages[1].toolCalls[0].arguments.expression").value("1+1"))
                .andExpect(jsonPath("$.messages[2].role").value("TOOL"))
                .andExpect(jsonPath("$.messages[2].toolCallId").value("call-1"));
    }

    @Test
    void returnsTraceAsApiDto() throws Exception {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        TraceEvent event = new TraceEvent(
                TraceType.LLM_CALL,
                "trace-1",
                "user-a",
                "session-1",
                1,
                false,
                null,
                null,
                null,
                null,
                12,
                null,
                null,
                now
        );
        when(traceRecorder.getTrace("trace-1", "user-a")).thenReturn(new AgentTrace(
                "trace-1", "user-a", "session-1", now, List.of(event)
        ));

        mockMvc.perform(get("/api/traces/trace-1").queryParam("userId", "user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.userId").value("user-a"))
                .andExpect(jsonPath("$.events[0].type").value("LLM_CALL"))
                .andExpect(jsonPath("$.events[0].round").value(1))
                .andExpect(jsonPath("$.events[0].toolCallsReturned").value(false))
                .andExpect(jsonPath("$.events[0].durationMillis").value(12));
    }

    @Test
    void returnsUnifiedValidationErrorWithoutStackTrace() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"message\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/agent/chat"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void mapsCrossUserSessionAccessToForbidden() throws Exception {
        when(sessionManager.getSession("session-1", "user-b")).thenThrow(
                new SessionAccessDeniedException("session-1", "user-b")
        );

        mockMvc.perform(get("/api/sessions/session-1/messages")
                        .queryParam("userId", "user-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void mapsCrossUserTraceAccessToForbidden() throws Exception {
        when(traceRecorder.getTrace("trace-1", "user-b")).thenThrow(
                new TraceAccessDeniedException("trace-1", "user-b")
        );

        mockMvc.perform(get("/api/traces/trace-1").queryParam("userId", "user-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void mapsMissingTraceToNotFound() throws Exception {
        when(traceRecorder.getTrace("missing", "user-a"))
                .thenThrow(new TraceNotFoundException("missing"));

        mockMvc.perform(get("/api/traces/missing").queryParam("userId", "user-a"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Trace not found: missing"))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("unifiedExceptionCases")
    void mapsSpecifiedExceptionsWithoutLeakingSensitiveDetails(
            RuntimeException exception,
            int expectedStatus,
            String expectedError
    ) throws Exception {
        when(agentRuntime.run("user-a", "session-1", "hello")).thenThrow(exception);

        String responseBody = mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-a","sessionId":"session-1","message":"hello"}
                                """))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error").value(expectedError))
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(responseBody)
                .doesNotContain("secret-api-key", "Authorization", "Bearer", "stackTrace");
    }

    /**
     * 提供统一异常类型、预期 HTTP 状态和稳定错误码矩阵。
     */
    private static Stream<Arguments> unifiedExceptionCases() {
        String sensitiveMessage = "Authorization: Bearer secret-api-key";
        return Stream.of(
                Arguments.of(new ToolNotFoundException("missing-tool"), 404, "TOOL_NOT_FOUND"),
                Arguments.of(new ToolArgumentException(sensitiveMessage), 400, "TOOL_ARGUMENT_ERROR"),
                Arguments.of(
                        new InvalidLlmOutputException(LlmErrorType.INVALID_RESPONSE, sensitiveMessage),
                        502,
                        "INVALID_LLM_OUTPUT"
                ),
                Arguments.of(new MaxAgentRoundsException(8), 422, "MAX_ROUNDS_EXCEEDED"),
                Arguments.of(new SessionNotFoundException("session-1"), 404, "NOT_FOUND"),
                Arguments.of(
                        new SessionAccessDeniedException("session-1", "user-a"),
                        403,
                        "ACCESS_DENIED"
                ),
                Arguments.of(
                        new LlmApiException(
                                LlmErrorType.AUTHENTICATION,
                                401,
                                sensitiveMessage,
                                null
                        ),
                        502,
                        "LLM_API_ERROR"
                ),
                Arguments.of(
                        new LlmApiException(
                                LlmErrorType.RATE_LIMIT,
                                429,
                                sensitiveMessage,
                                null
                        ),
                        503,
                        "LLM_API_ERROR"
                ),
                Arguments.of(
                        new LlmTimeoutException(sensitiveMessage, null),
                        504,
                        "LLM_TIMEOUT"
                )
        );
    }
}
