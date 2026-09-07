package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.action.FinalAnswerAction;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.BailianProperties;
import com.zhaoweijie.minimalagent.context.AgentContext;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.exception.LlmClientException;
import com.zhaoweijie.minimalagent.exception.LlmErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BailianLlmClientTests {

    /** 测试使用的 Jackson 对象映射器。 */
    private ObjectMapper objectMapper;

    /** 测试使用的百炼外部配置。 */
    private BailianProperties properties;

    /** Mock HTTP 服务器。 */
    private MockRestServiceServer server;

    /** 被测试的百炼 LLM Client。 */
    private BailianLlmClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = propertiesWithTestCredentials();
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BailianLlmClient(
                builder.baseUrl(properties.getBaseUrl()).build(),
                properties,
                objectMapper,
                new BailianResponseParser(objectMapper)
        );
    }

    @Test
    void sendsDynamicToolsAndParsesFinalAnswer() {
        ToolDefinition calculator = new ToolDefinition(
                "calculator",
                "执行精确数学表达式计算",
                objectMapper.createObjectNode()
                        .put("type", "object")
                        .set("properties", objectMapper.createObjectNode())
        );
        server.expect(once(), requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.model").value("qwen-plus"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("calculator"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                                + "\"content\":\"The answer is 2.\",\"tool_calls\":null}}]}",
                        MediaType.APPLICATION_JSON
                ));

        LlmResponse response = client.chat(contextWithUserMessage("calculate 1+1"), List.of(calculator));

        assertThat(response.content()).isEqualTo("The answer is 2.");
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.actions()).containsExactly(new FinalAnswerAction("The answer is 2."));
        server.verify();
    }

    @Test
    void parsesMultipleToolCallsAndArgumentsAsJsonNodes() {
        // 使用 Jackson 构造外层响应，避免测试夹具中的双层 JSON 字符串转义干扰断言目标。
        ObjectNode responseBody = objectMapper.createObjectNode();
        var toolCalls = objectMapper.createArrayNode();
        toolCalls.addObject()
                .put("id", "call-1")
                .put("type", "function")
                .putObject("function")
                .put("name", "calculator")
                .put("arguments", "{\"expression\":\"1+1\"}");
        toolCalls.addObject()
                .put("id", "call-2")
                .put("type", "function")
                .putObject("function")
                .put("name", "search")
                .put("arguments", "{\"query\":\"Java 21\"}");
        responseBody.putArray("choices")
                .addObject()
                .putObject("message")
                .put("role", "assistant")
                .putNull("content")
                .set("tool_calls", toolCalls);

        server.expect(requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andRespond(withSuccess(
                        responseBody.toString(),
                        MediaType.APPLICATION_JSON
                ));

        LlmResponse response = client.chat(contextWithUserMessage("do both"), List.of());

        assertThat(response.toolCalls()).hasSize(2);
        assertThat(response.toolCalls().get(0).toolCallId()).isEqualTo("call-1");
        assertThat(response.toolCalls().get(0).toolName()).isEqualTo("calculator");
        assertThat(response.toolCalls().get(0).arguments().path("expression").textValue())
                .isEqualTo("1+1");
        assertThat(response.toolCalls().get(1).toolCallId()).isEqualTo("call-2");
        assertThat(response.actions()).allMatch(ToolCallAction.class::isInstance);
    }

    @Test
    void sendsToolResultWithRoleToolAndOriginalToolCallId() {
        ToolCallAction toolCall = new ToolCallAction(
                "call-1",
                "calculator",
                objectMapper.createObjectNode().put("expression", "1+1")
        );
        AgentContext context = new AgentContext(
                "system",
                null,
                List.of(new AgentMessage(
                        AgentMessageRole.ASSISTANT,
                        null,
                        null,
                        List.of(toolCall)
                )),
                new AgentMessage(AgentMessageRole.TOOL, "{\"result\":2}", "call-1", null)
        );
        server.expect(requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].id").value("call-1"))
                .andExpect(jsonPath("$.messages[2].role").value("tool"))
                .andExpect(jsonPath("$.messages[2].tool_call_id").value("call-1"))
                .andExpect(jsonPath("$.messages[2].content").value("{\"result\":2}"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"content\":\"2\"}}]}",
                        MediaType.APPLICATION_JSON
                ));

        client.chat(context, List.of());

        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "401, AUTHENTICATION",
            "429, RATE_LIMIT",
            "500, SERVER_ERROR",
            "503, SERVER_ERROR"
    })
    void mapsHttpErrors(int status, LlmErrorType expectedType) {
        server.expect(requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.chat(contextWithUserMessage("hello"), List.of()))
                .isInstanceOfSatisfying(LlmClientException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(expectedType);
                    assertThat(exception.getStatusCode()).isEqualTo(status);
                    assertThat(exception.getMessage()).doesNotContain("test-api-key");
                });
    }

    @Test
    void handlesTimeout() {
        RestClient timeoutRestClient = RestClient.builder()
                .baseUrl("https://timeout.test")
                .requestFactory((uri, httpMethod) -> {
                    throw new SocketTimeoutException("simulated timeout");
                })
                .build();
        BailianLlmClient timeoutClient = new BailianLlmClient(
                timeoutRestClient,
                properties,
                objectMapper,
                new BailianResponseParser(objectMapper)
        );

        assertThatThrownBy(() -> timeoutClient.chat(contextWithUserMessage("hello"), List.of()))
                .isInstanceOfSatisfying(LlmClientException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(LlmErrorType.TIMEOUT));
    }

    @Test
    void rejectsResponseWithoutChoices() {
        server.expect(requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertErrorType(LlmErrorType.EMPTY_RESPONSE);
    }

    @Test
    void rejectsInvalidResponseJson() {
        server.expect(requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertErrorType(LlmErrorType.INVALID_RESPONSE);
    }

    @Test
    void rejectsInvalidToolArguments() {
        server.expect(requestTo("https://mock-bailian.test/compatible-mode/v1/chat/completions"))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"tool_calls":[{
                          "id":"call-1",
                          "type":"function",
                          "function":{"name":"calculator","arguments":"not-json"}
                        }]}}]}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        assertErrorType(LlmErrorType.INVALID_ARGUMENTS);
    }

    @Test
    void rejectsMissingApiKeyBeforeNetworkCall() {
        properties.setApiKey("");

        assertErrorType(LlmErrorType.CONFIGURATION);
    }

    /**
     * 断言一次调用抛出指定分类的 LLM 异常。
     */
    private void assertErrorType(LlmErrorType expectedType) {
        assertThatThrownBy(() -> client.chat(contextWithUserMessage("hello"), List.of()))
                .isInstanceOfSatisfying(LlmClientException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }

    /**
     * 创建仅包含一条用户消息的 Agent 上下文。
     */
    private AgentContext contextWithUserMessage(String content) {
        return new AgentContext(
                "system",
                null,
                List.of(new AgentMessage(AgentMessageRole.USER, content, null, null)),
                null
        );
    }

    /**
     * 创建不包含真实凭证的测试配置。
     */
    private BailianProperties propertiesWithTestCredentials() {
        BailianProperties testProperties = new BailianProperties();
        testProperties.setBaseUrl("https://mock-bailian.test/compatible-mode/v1");
        testProperties.setModel("qwen-plus");
        testProperties.setApiKey("test-api-key");
        return testProperties;
    }
}
