package com.zhaoweijie.minimalagent.tool.todo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolExecutionContext;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 管理当前用户 Session 内待办事项的工具。
 */
@Component
public class TodoTool implements AgentTool {

    /** 工具注册名称。 */
    private static final String NAME = "todo";

    /** Session 隔离的待办内存仓库。 */
    private final TodoRepository repository;

    /** 用于构建参数 Schema 和执行结果的 Jackson 对象映射器。 */
    private final ObjectMapper objectMapper;

    /** 工具参数的 JSON Schema。 */
    private final JsonNode parameterSchema;

    /**
     * 创建待办工具。
     *
     * @param repository   Session 隔离的待办仓库
     * @param objectMapper 应用统一配置的 Jackson 对象映射器
     */
    public TodoTool(TodoRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.parameterSchema = createParameterSchema();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "管理当前 Session 内的待办事项，支持新增、查看、完成和删除";
    }

    @Override
    public JsonNode parameterSchema() {
        // JsonNode 可变，因此每次返回副本，避免调用方破坏工具定义。
        return parameterSchema.deepCopy();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode arguments) {
        if (!hasValidContext(context)) {
            return failure("Tool execution context requires userId and sessionId");
        }

        String action = textArgument(arguments, "action");
        if (action == null) {
            return failure("Missing required parameter: action");
        }

        return switch (action.toLowerCase(Locale.ROOT)) {
            case "add" -> add(context, arguments);
            case "list" -> list(context);
            case "complete" -> complete(context, arguments);
            case "delete" -> delete(context, arguments);
            default -> failure("Unsupported todo action: " + action);
        };
    }

    /**
     * 新增待办，并要求 content 是非空字符串。
     */
    private ToolResult add(ToolExecutionContext context, JsonNode arguments) {
        String content = textArgument(arguments, "content");
        if (content == null) {
            return failure("Action add requires non-blank parameter: content");
        }

        TodoItem item = repository.add(context.userId(), context.sessionId(), content);
        ObjectNode data = objectMapper.createObjectNode();
        data.set("todo", objectMapper.valueToTree(item));
        return success(data);
    }

    /**
     * 列出当前用户 Session 的待办快照。
     */
    private ToolResult list(ToolExecutionContext context) {
        ObjectNode data = objectMapper.createObjectNode();
        data.set("todos", objectMapper.valueToTree(
                repository.list(context.userId(), context.sessionId())
        ));
        return success(data);
    }

    /**
     * 完成当前用户 Session 中的指定待办。
     */
    private ToolResult complete(ToolExecutionContext context, JsonNode arguments) {
        String todoId = textArgument(arguments, "todoId");
        if (todoId == null) {
            return failure("Action complete requires non-blank parameter: todoId");
        }

        return repository.complete(context.userId(), context.sessionId(), todoId)
                .map(item -> {
                    ObjectNode data = objectMapper.createObjectNode();
                    data.set("todo", objectMapper.valueToTree(item));
                    return success(data);
                })
                .orElseGet(() -> failure("Todo not found in current session: " + todoId));
    }

    /**
     * 删除当前用户 Session 中的指定待办。
     */
    private ToolResult delete(ToolExecutionContext context, JsonNode arguments) {
        String todoId = textArgument(arguments, "todoId");
        if (todoId == null) {
            return failure("Action delete requires non-blank parameter: todoId");
        }

        if (!repository.delete(context.userId(), context.sessionId(), todoId)) {
            return failure("Todo not found in current session: " + todoId);
        }

        ObjectNode data = objectMapper.createObjectNode();
        data.put("deleted", true);
        data.put("todoId", todoId);
        return success(data);
    }

    /**
     * 从参数对象读取去除首尾空白后的非空字符串。
     */
    private String textArgument(JsonNode arguments, String fieldName) {
        JsonNode value = arguments == null ? null : arguments.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            return null;
        }
        return value.textValue().trim();
    }

    /**
     * 校验仓库分区所需的用户和 Session 标识。
     */
    private boolean hasValidContext(ToolExecutionContext context) {
        return context != null
                && context.userId() != null
                && !context.userId().isBlank()
                && context.sessionId() != null
                && !context.sessionId().isBlank();
    }

    /**
     * 构建提供给 LLM 的工具参数 JSON Schema。
     */
    private JsonNode createParameterSchema() {
        ObjectNode action = objectMapper.createObjectNode();
        action.put("type", "string");
        action.putArray("enum").add("add").add("list").add("complete").add("delete");

        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "string");

        ObjectNode todoId = objectMapper.createObjectNode();
        todoId.put("type", "string");

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("action", action);
        properties.set("content", content);
        properties.set("todoId", todoId);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.putArray("required").add("action");
        return schema;
    }

    /**
     * 创建成功的标准工具结果。
     */
    private ToolResult success(JsonNode data) {
        return new ToolResult(true, NAME, data, null);
    }

    /**
     * 创建失败的标准工具结果。
     */
    private ToolResult failure(String error) {
        return new ToolResult(false, NAME, null, error);
    }
}
