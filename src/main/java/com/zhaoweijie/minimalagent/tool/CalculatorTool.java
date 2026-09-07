package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 使用受限语法解析器计算四则运算表达式的工具。
 */
@Component
public class CalculatorTool implements AgentTool {

    /** 工具注册名称。 */
    private static final String NAME = "calculator";

    /** 非整除运算使用的十进制精度。 */
    private static final MathContext DIVISION_CONTEXT = MathContext.DECIMAL128;

    /** 用于构建参数 Schema 和结果数据的 Jackson 对象映射器。 */
    private final ObjectMapper objectMapper;

    /** 工具参数的 JSON Schema。 */
    private final JsonNode parameterSchema;

    /**
     * 创建计算器工具。
     *
     * @param objectMapper 应用统一配置的 Jackson 对象映射器
     */
    public CalculatorTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.parameterSchema = createParameterSchema();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "精确计算包含加、减、乘、除和括号的数学表达式";
    }

    @Override
    public JsonNode parameterSchema() {
        // JsonNode 可变，因此每次返回副本，避免调用方破坏工具定义。
        return parameterSchema.deepCopy();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode arguments) {
        JsonNode expressionNode = arguments == null ? null : arguments.get("expression");
        if (expressionNode == null || !expressionNode.isTextual()
                || expressionNode.textValue().isBlank()) {
            return failure("Missing required parameter: expression");
        }

        try {
            BigDecimal result = new ExpressionParser(expressionNode.textValue()).parse();
            ObjectNode data = objectMapper.createObjectNode();
            data.put("result", result.stripTrailingZeros());
            return new ToolResult(true, NAME, data, null);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return failure(exception.getMessage());
        }
    }

    /**
     * 构建提供给 LLM 的工具参数 JSON Schema。
     */
    private JsonNode createParameterSchema() {
        ObjectNode expression = objectMapper.createObjectNode();
        expression.put("type", "string");
        expression.put("description", "需要计算的数学表达式");

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("expression", expression);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.putArray("required").add("expression");
        return schema;
    }

    /**
     * 创建失败的标准工具结果。
     */
    private ToolResult failure(String error) {
        return new ToolResult(false, NAME, null, error);
    }

    /**
     * 仅支持数字、四则运算、括号和一元负号的递归下降解析器。
     */
    private static final class ExpressionParser {

        /** 待解析的原始表达式。 */
        private final String expression;

        /** 当前读取位置。 */
        private int position;

        /**
         * 创建表达式解析器。
         *
         * @param expression 待计算表达式
         */
        private ExpressionParser(String expression) {
            this.expression = expression;
        }

        /**
         * 解析完整表达式，并拒绝尾部多余字符。
         */
        private BigDecimal parse() {
            BigDecimal result = parseExpression();
            skipWhitespace();
            if (position != expression.length()) {
                throw invalidExpression();
            }
            return result;
        }

        /**
         * 解析加减法；乘除法由下一级规则优先处理。
         */
        private BigDecimal parseExpression() {
            BigDecimal result = parseTerm();
            while (true) {
                if (consume('+')) {
                    result = result.add(parseTerm());
                } else if (consume('-')) {
                    result = result.subtract(parseTerm());
                } else {
                    return result;
                }
            }
        }

        /**
         * 解析乘除法，并显式阻止除零。
         */
        private BigDecimal parseTerm() {
            BigDecimal result = parseUnary();
            while (true) {
                if (consume('*')) {
                    result = result.multiply(parseUnary());
                } else if (consume('/')) {
                    BigDecimal divisor = parseUnary();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) {
                        throw new ArithmeticException("Division by zero");
                    }
                    result = result.divide(divisor, DIVISION_CONTEXT);
                } else {
                    return result;
                }
            }
        }

        /**
         * 解析一元负号，使负数和连续取反无需特殊数字格式。
         */
        private BigDecimal parseUnary() {
            if (consume('-')) {
                return parseUnary().negate();
            }
            return parsePrimary();
        }

        /**
         * 解析括号表达式或十进制数字。
         */
        private BigDecimal parsePrimary() {
            if (consume('(')) {
                BigDecimal nested = parseExpression();
                if (!consume(')')) {
                    throw invalidExpression();
                }
                return nested;
            }
            return parseNumber();
        }

        /**
         * 解析不带指数的整数或小数，并确保至少存在一个数字。
         */
        private BigDecimal parseNumber() {
            skipWhitespace();
            int start = position;
            boolean hasDigit = false;

            while (position < expression.length() && Character.isDigit(expression.charAt(position))) {
                position++;
                hasDigit = true;
            }
            if (position < expression.length() && expression.charAt(position) == '.') {
                position++;
                while (position < expression.length() && Character.isDigit(expression.charAt(position))) {
                    position++;
                    hasDigit = true;
                }
            }

            if (!hasDigit) {
                throw invalidExpression();
            }
            return new BigDecimal(expression.substring(start, position));
        }

        /**
         * 忽略运算符和数字之间的空白字符。
         */
        private void skipWhitespace() {
            while (position < expression.length()
                    && Character.isWhitespace(expression.charAt(position))) {
                position++;
            }
        }

        /**
         * 若当前位置是指定字符则消费该字符。
         */
        private boolean consume(char expected) {
            skipWhitespace();
            if (position < expression.length() && expression.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        /**
         * 创建包含错误位置的统一非法表达式异常。
         */
        private IllegalArgumentException invalidExpression() {
            return new IllegalArgumentException("Invalid expression at position " + position);
        }
    }
}
