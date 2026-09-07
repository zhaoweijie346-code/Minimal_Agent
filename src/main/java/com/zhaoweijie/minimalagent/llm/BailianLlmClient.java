package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.BailianProperties;
import com.zhaoweijie.minimalagent.context.AgentContext;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.exception.LlmClientException;
import com.zhaoweijie.minimalagent.exception.LlmErrorType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 使用 OpenAI Compatible Chat Completions API 调用阿里云百炼 Qwen 的客户端。
 */
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "bailian", matchIfMissing = true)
public class BailianLlmClient implements LlmClient {

    /** Chat Completions 相对请求路径。 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /** 百炼专用 HTTP Client。 */
    private final RestClient restClient;

    /** 模型、地址和认证配置。 */
    private final BailianProperties properties;

    /** 请求与响应 JSON 映射器。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建百炼 LLM Client。
     *
     * @param restClient  百炼专用 RestClient
     * @param properties 百炼配置
     * @param objectMapper 应用统一配置的 Jackson 对象映射器
     */
    public BailianLlmClient(
            @Qualifier("bailianRestClient") RestClient restClient,
            BailianProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LlmResponse chat(AgentContext context, List<ToolDefinition> tools) {
        validateConfiguration();
        ObjectNode request = buildRequest(context, tools == null ? List.of() : tools);

        try {
            // Authorization 仅写入请求 Header；不输出请求 Header、API Key 或完整请求日志。
            String responseBody = restClient.post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .body(request.toString())
                    .retrieve()
                    .body(String.class);
            return parseResponse(responseBody);
        } catch (LlmClientException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw mapHttpException(exception);
        } catch (ResourceAccessException exception) {
            if (hasTimeoutCause(exception)) {
                throw new LlmClientException(
                        LlmErrorType.TIMEOUT,
                        "Bailian request timed out",
                        exception
                );
            }
            throw new LlmClientException(
                    LlmErrorType.CONNECTION_ERROR,
                    "Bailian connection failed",
                    exception
            );
        } catch (RestClientException exception) {
            throw new LlmClientException(
                    LlmErrorType.CONNECTION_ERROR,
                    "Bailian request failed",
                    exception
            );
        }
    }

    /**
     * 构建 OpenAI Compatible Chat Completions 请求体。
     */
    private ObjectNode buildRequest(AgentContext context, List<ToolDefinition> tools) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", properties.getModel());
        ArrayNode messages = request.putArray("messages");
        for (AgentMessage message : context.messages()) {
            messages.add(toRequestMessage(message));
        }

        if (!tools.isEmpty()) {
            ArrayNode toolNodes = request.putArray("tools");
            for (ToolDefinition tool : tools) {
                toolNodes.add(toRequestTool(tool));
            }
        }
        return request;
    }

    /**
     * 将领域消息转换为 OpenAI Compatible 消息，并保留工具调用关联字段。
     */
    private ObjectNode toRequestMessage(AgentMessage message) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", message.role().name().toLowerCase(Locale.ROOT));
        node.put("content", message.content() == null ? "" : message.content());

        if (message.role() == AgentMessageRole.ASSISTANT && !message.toolCalls().isEmpty()) {
            ArrayNode toolCalls = node.putArray("tool_calls");
            for (ToolCallAction toolCall : message.toolCalls()) {
                ObjectNode function = objectMapper.createObjectNode();
                function.put("name", toolCall.toolName());
                function.put(
                        "arguments",
                        toolCall.arguments() == null ? "{}" : toolCall.arguments().toString()
                );

                ObjectNode call = toolCalls.addObject();
                call.put("id", toolCall.toolCallId());
                call.put("type", "function");
                call.set("function", function);
            }
        }

        if (message.role() == AgentMessageRole.TOOL) {
            if (message.toolCallId() == null || message.toolCallId().isBlank()) {
                throw new IllegalArgumentException("TOOL message requires toolCallId");
            }
            node.put("tool_call_id", message.toolCallId());
        }
        return node;
    }

    /**
     * 将供应商无关定义转换为 OpenAI tools:function 结构。
     */
    private ObjectNode toRequestTool(ToolDefinition tool) {
        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.set(
                "parameters",
                tool.parameters() == null ? objectMapper.createObjectNode() : tool.parameters()
        );

        ObjectNode definition = objectMapper.createObjectNode();
        definition.put("type", "function");
        definition.set("function", function);
        return definition;
    }

    /**
     * 解析普通 assistant 内容或一个/多个 tool_calls。
     */
    private LlmResponse parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new LlmClientException(LlmErrorType.EMPTY_RESPONSE, "Bailian returned no response");
        }

        final JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new LlmClientException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian returned invalid JSON",
                    exception
            );
        }

        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmClientException(
                    LlmErrorType.EMPTY_RESPONSE,
                    "Bailian response contains no choices"
            );
        }
        JsonNode message = choices.get(0).path("message");
        if (message.isMissingNode() || !message.isObject()) {
            throw new LlmClientException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian response contains no assistant message"
            );
        }

        String content = parseContent(message.get("content"));
        List<ToolCallAction> toolCalls = parseToolCalls(message.get("tool_calls"));
        if (toolCalls.isEmpty() && (content == null || content.isBlank())) {
            throw new LlmClientException(
                    LlmErrorType.EMPTY_RESPONSE,
                    "Bailian assistant message is empty"
            );
        }
        return new LlmResponse(content, toolCalls);
    }

    /**
     * 读取可为空的 assistant 文本内容，并拒绝非字符串结构。
     */
    private String parseContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            return null;
        }
        if (!contentNode.isTextual()) {
            throw new LlmClientException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian assistant content is not text"
            );
        }
        return contentNode.textValue();
    }

    /**
     * 解析并校验 tool_calls 列表及其 JSON arguments。
     */
    private List<ToolCallAction> parseToolCalls(JsonNode toolCallsNode) {
        if (toolCallsNode == null || toolCallsNode.isNull()) {
            return List.of();
        }
        if (!toolCallsNode.isArray()) {
            throw new LlmClientException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian tool_calls is not an array"
            );
        }

        List<ToolCallAction> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            String toolCallId = requiredText(toolCallNode, "id", "tool call id");
            JsonNode function = toolCallNode.path("function");
            if (!function.isObject()) {
                throw new LlmClientException(
                        LlmErrorType.INVALID_RESPONSE,
                        "Bailian tool call contains no function"
                );
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
                throw new LlmClientException(
                        LlmErrorType.INVALID_ARGUMENTS,
                        "Bailian tool arguments must be a JSON object"
                );
            }
            return arguments;
        } catch (JsonProcessingException exception) {
            throw new LlmClientException(
                    LlmErrorType.INVALID_ARGUMENTS,
                    "Bailian tool arguments contain invalid JSON",
                    exception
            );
        }
    }

    /**
     * 读取响应中的必填字符串字段。
     */
    private String requiredText(JsonNode parent, String fieldName, String description) {
        JsonNode value = parent.path(fieldName);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new LlmClientException(
                    LlmErrorType.INVALID_RESPONSE,
                    "Bailian response contains invalid " + description
            );
        }
        return value.textValue();
    }

    /**
     * 将常见 HTTP 状态映射为稳定的 LLM 错误分类。
     */
    private LlmClientException mapHttpException(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        LlmErrorType errorType;
        String message;
        if (statusCode == 401) {
            errorType = LlmErrorType.AUTHENTICATION;
            message = "Bailian authentication failed";
        } else if (statusCode == 429) {
            errorType = LlmErrorType.RATE_LIMIT;
            message = "Bailian rate limit exceeded";
        } else if (statusCode >= 500) {
            errorType = LlmErrorType.SERVER_ERROR;
            message = "Bailian server error";
        } else {
            errorType = LlmErrorType.HTTP_ERROR;
            message = "Bailian HTTP request failed";
        }
        return new LlmClientException(errorType, statusCode, message, exception);
    }

    /**
     * 检查异常因果链中是否存在连接或读取超时。
     */
    private boolean hasTimeoutCause(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 在发起网络请求前校验认证与模型配置。
     */
    private void validateConfiguration() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmClientException(
                    LlmErrorType.CONFIGURATION,
                    "DASHSCOPE_API_KEY is not configured"
            );
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new LlmClientException(
                    LlmErrorType.CONFIGURATION,
                    "llm.model is not configured"
            );
        }
    }
}
