package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.action.FinalAnswerAction;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.exception.InvalidLlmOutputException;
import com.zhaoweijie.minimalagent.exception.LlmClientException;
import com.zhaoweijie.minimalagent.exception.LlmErrorType;
import com.zhaoweijie.minimalagent.exception.ToolArgumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BailianResponseParserTests {

    /** 测试响应和 arguments 使用的 Jackson 映射器。 */
    private ObjectMapper objectMapper;

    /** 被测试的百炼响应解析器。 */
    private BailianResponseParser parser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        parser = new BailianResponseParser(objectMapper);
    }

    @Test
    void parsesAssistantContentAsFinalAnswerAction() {
        LlmResponse response = parser.parse(
                "{\"choices\":[{\"message\":{\"content\":\"final answer\"}}]}"
        );

        assertThat(response.actions())
                .containsExactly(new FinalAnswerAction("final answer"));
    }

    @ParameterizedTest
    @CsvSource({
            "calculator, expression, 1+1",
            "search, query, Java 21",
            "todo, action, list"
    })
    void parsesNativeToolCallsAndKeepsToolCallId(
            String toolName,
            String argumentName,
            String argumentValue
    ) {
        ObjectNode arguments = objectMapper.createObjectNode().put(argumentName, argumentValue);

        LlmResponse response = parser.parse(toolCallResponse(
                "call-123",
                toolName,
                arguments.toString()
        ));

        assertThat(response.actions()).hasSize(1);
        assertThat(response.actions().getFirst())
                .isInstanceOfSatisfying(ToolCallAction.class, action -> {
                    assertThat(action.toolCallId()).isEqualTo("call-123");
                    assertThat(action.toolName()).isEqualTo(toolName);
                    assertThat(action.arguments().path(argumentName).textValue())
                            .isEqualTo(argumentValue);
                });
    }

    @Test
    void rejectsEmptyChoices() {
        assertThatThrownBy(() -> parser.parse("{\"choices\":[]}"))
                .isInstanceOfSatisfying(InvalidLlmOutputException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(LlmErrorType.EMPTY_RESPONSE));
    }

    @Test
    void rejectsMissingMessage() {
        assertErrorType("{\"choices\":[{}]}", LlmErrorType.INVALID_RESPONSE);
    }

    @Test
    void rejectsMalformedToolCallsStructure() {
        assertErrorType(
                "{\"choices\":[{\"message\":{\"tool_calls\":{}}}]}",
                LlmErrorType.INVALID_RESPONSE
        );
    }

    @Test
    void rejectsBlankFunctionName() {
        assertErrorType(
                toolCallResponse("call-1", " ", "{}"),
                LlmErrorType.INVALID_RESPONSE
        );
    }

    @Test
    void rejectsInvalidArgumentsJson() {
        assertThatThrownBy(() -> parser.parse(
                toolCallResponse("call-1", "calculator", "not-json")
        )).isInstanceOfSatisfying(ToolArgumentException.class, exception ->
                assertThat(exception.getErrorType()).isEqualTo(LlmErrorType.INVALID_ARGUMENTS));
    }

    @Test
    void rejectsEmptyContentAndToolCalls() {
        assertErrorType(
                "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[]}}]}",
                LlmErrorType.EMPTY_RESPONSE
        );
    }

    /**
     * 使用 Jackson 构建包含一个原生 Function Calling 的百炼响应。
     */
    private String toolCallResponse(String toolCallId, String toolName, String arguments) {
        ObjectNode response = objectMapper.createObjectNode();
        response.putArray("choices")
                .addObject()
                .putObject("message")
                .putNull("content")
                .putArray("tool_calls")
                .addObject()
                .put("id", toolCallId)
                .put("type", "function")
                .putObject("function")
                .put("name", toolName)
                .put("arguments", arguments);
        return response.toString();
    }

    /**
     * 断言解析失败时返回稳定的错误分类。
     */
    private void assertErrorType(String responseBody, LlmErrorType expectedType) {
        assertThatThrownBy(() -> parser.parse(responseBody))
                .isInstanceOfSatisfying(LlmClientException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expectedType));
    }
}
