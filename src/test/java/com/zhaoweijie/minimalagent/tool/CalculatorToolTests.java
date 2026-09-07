package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorToolTests {

    /** 用于构造测试参数的 Jackson 对象映射器。 */
    private ObjectMapper objectMapper;

    /** 被测试的计算器工具。 */
    private CalculatorTool calculatorTool;

    /** 工具调用所需的最小执行上下文。 */
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        calculatorTool = new CalculatorTool(objectMapper);
        context = new ToolExecutionContext("user-1", "session-1");
    }

    @ParameterizedTest
    @CsvSource({
            "'1+1', '2'",
            "'(1+2)*3', '9'",
            "'10/4', '2.5'",
            "'-5+2', '-3'"
    })
    void calculatesSupportedExpressions(String expression, String expected) {
        ToolResult result = calculatorTool.execute(context, arguments(expression));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("calculator");
        assertThat(result.error()).isNull();
        assertThat(result.data().path("result").decimalValue())
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void rejectsDivisionByZero() {
        ToolResult result = calculatorTool.execute(context, arguments("1/0"));

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error()).isEqualTo("Division by zero");
    }

    @Test
    void rejectsInvalidExpression() {
        ToolResult result = calculatorTool.execute(context, arguments("1 + abc"));

        assertThat(result.success()).isFalse();
        assertThat(result.error()).startsWith("Invalid expression at position ");
    }

    @Test
    void rejectsMissingExpression() {
        ToolResult result = calculatorTool.execute(context, objectMapper.createObjectNode());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("Missing required parameter: expression");
    }

    @Test
    void rejectsNullArguments() {
        ToolResult result = calculatorTool.execute(context, null);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("Missing required parameter: expression");
    }

    @Test
    void exposesRequiredExpressionSchema() {
        var schema = calculatorTool.parameterSchema();

        assertThat(schema.path("type").textValue()).isEqualTo("object");
        assertThat(schema.path("properties").path("expression").path("type").textValue())
                .isEqualTo("string");
        assertThat(schema.path("properties").path("expression").path("description").textValue())
                .isEqualTo("需要计算的数学表达式");
        assertThat(schema.path("required").get(0).textValue()).isEqualTo("expression");
    }

    /**
     * 创建符合工具 Schema 的表达式参数。
     */
    private ObjectNode arguments(String expression) {
        return objectMapper.createObjectNode().put("expression", expression);
    }
}
