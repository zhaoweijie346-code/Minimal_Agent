package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.exception.InvalidLlmOutputException;
import com.zhaoweijie.minimalagent.exception.LlmErrorType;
import com.zhaoweijie.minimalagent.exception.ToolArgumentException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将百炼 OpenAI Compatible 原生响应解析为供应商无关的 LLM 响应模型。
 */
@Component
public class BailianResponseParser {

    /** 解析百炼响应及工具 arguments 使用的 Jackson 映射器。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建百炼响应解析器。
     *
     * @param objectMapper 应用统一配置的 Jackson 对象映射器
     */
    public BailianResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析普通 assistant 内容或一个、多个原生 tool_calls。
     *
     * @param responseBody 百炼响应 JSON
     * @return 可转换为 FinalAnswerAction 或 ToolCallAction 的 LLM 响应
     */
    public LlmResponse parse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new InvalidLlmOutputException(
                    LlmErrorType.EMPTY_RESPONSE,
                    "Bailian returned no response"
            );
        }

        JsonNode root = readResponseJson(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new InvalidLlmOutputException(
                    LlmErrorType.EMPTY_RESPONSE,
                    "Bailian response contains no choices"
            );
        }

        JsonNode message = choices.get(0).path("message");
        if (!message.isObject()) {
            throw new InvalidLlmOutputException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian response contains no assistant message"
            );
        }

        String content = parseContent(message.get("content"));
        List<ToolCallAction> toolCalls = parseToolCalls(message.get("tool_calls"));
        if (toolCalls.isEmpty() && (content == null || content.isBlank())) {
            throw new InvalidLlmOutputException(
                    LlmErrorType.EMPTY_RESPONSE,
                    "Bailian assistant message is empty"
            );
        }
        return new LlmResponse(content, toolCalls);
    }

    /**
     * 将完整 HTTP 响应读取成 JSON 树。
     */
    private JsonNode readResponseJson(String responseBody) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new InvalidLlmOutputException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian returned invalid JSON",
                    exception
            );
        }
    }

    /**
     * 读取可为空的 assistant 文本内容，并拒绝非字符串结构。
     */
    private String parseContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        if (!contentNode.isTextual()) {
            throw new InvalidLlmOutputException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian assistant content is not text"
            );
        }
        return contentNode.textValue();
    }

    /**
     * 顺序解析并校验全部原生 Function Calling 调用，不引入并行执行语义。
     */
    private List<ToolCallAction> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull()) {
            return List.of();
        }
        if (!toolCallsNode.isArray()) {
            throw invalidResponse("Bailian tool_calls is not an array");
        }

        List<ToolCallAction> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            if (!toolCallNode.isObject()) {
                throw invalidResponse("Bailian tool call is not an object");
            }
            String toolCallId = requiredText(toolCallNode, "id", "tool call id");
            String type = requiredText(toolCallNode, "type", "tool call type");
            if (!"function".equals(type)) {
                throw invalidResponse("Bailian tool call type is not function");
            }

            JsonNode function = toolCallNode.path("function");
            if (!function.isObject()) {
                throw invalidResponse("Bailian tool call contains no function");
            }
            String toolName = requiredText(function, "name", "tool name");
            String argumentsText = requiredText(function, "arguments", "tool arguments");
            toolCalls.add(new ToolCallAction(
                    toolCallId,
                    toolName,
                    parseArguments(argumentsText)
            ));
        }
        return List.copyOf(toolCalls);
    }

    /**
     * 将 function.arguments JSON 字符串解析为对象节点。
     */
    private JsonNode parseArguments(String argumentsText) {
        try {
            JsonNode arguments = objectMapper.readTree(argumentsText);
            if (arguments == null || !arguments.isObject()) {
                throw new ToolArgumentException(
                        "Bailian tool arguments must be a JSON object"
                );
            }
            return arguments;
        } catch (JsonProcessingException exception) {
            throw new ToolArgumentException(
                    "Bailian tool arguments contain invalid JSON",
                    exception
            );
        }
    }

    /**
     * 读取响应中的必填非空字符串字段。
     */
    private String requiredText(JsonNode parent, String fieldName, String description) {
        JsonNode value = parent.path(fieldName);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw invalidResponse("Bailian response contains invalid " + description);
        }
        return value.textValue();
    }

    /**
     * 创建结构非法的统一异常。
     */
    private InvalidLlmOutputException invalidResponse(String message) {
        return new InvalidLlmOutputException(LlmErrorType.INVALID_RESPONSE, message);
    }
}
