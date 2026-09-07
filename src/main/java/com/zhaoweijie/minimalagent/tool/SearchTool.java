package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 基于独立 Mock 数据源进行简单关键词检索的工具。
 */
@Component
public class SearchTool implements AgentTool {

    /** 工具注册名称。 */
    private static final String NAME = "search";

    /** 用于构建参数 Schema 和搜索结果的 Jackson 对象映射器。 */
    private final ObjectMapper objectMapper;

    /** 工具参数的 JSON Schema。 */
    private final JsonNode parameterSchema;

    /**
     * 创建 Mock 搜索工具。
     *
     * @param objectMapper 应用统一配置的 Jackson 对象映射器
     */
    public SearchTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.parameterSchema = createParameterSchema();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "根据问题或关键词搜索相关信息";
    }

    @Override
    public JsonNode parameterSchema() {
        // JsonNode 可变，因此每次返回副本，避免调用方破坏工具定义。
        return parameterSchema.deepCopy();
    }

    @Override
    public ToolResult execute(ToolExecutionContext context, JsonNode arguments) {
        JsonNode queryNode = arguments == null ? null : arguments.get("query");
        if (queryNode == null) {
            return failure("Missing required parameter: query");
        }
        if (!queryNode.isTextual() || queryNode.textValue().isBlank()) {
            return failure("Query must be a non-blank string");
        }

        String normalizedQuery = queryNode.textValue().trim().toLowerCase(Locale.ROOT);
        ArrayNode results = objectMapper.createArrayNode();

        // 匹配规则只依赖文档数据，新主题可通过扩充 MockSearchData 添加。
        MockSearchData.documents().stream()
                .filter(document -> matches(document, normalizedQuery))
                .forEach(document -> {
                    ObjectNode result = results.addObject();
                    result.put("title", document.title());
                    result.put("snippet", document.snippet());
                });

        ObjectNode data = objectMapper.createObjectNode();
        data.set("results", results);
        return new ToolResult(true, NAME, data, null);
    }

    /**
     * 使用标题、摘要和文档关键词执行不区分大小写的简单包含匹配。
     */
    private boolean matches(MockSearchData.SearchDocument document, String normalizedQuery) {
        String searchableText = (document.title() + " " + document.snippet())
                .toLowerCase(Locale.ROOT);
        if (searchableText.contains(normalizedQuery)) {
            return true;
        }

        return document.keywords().stream()
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(keyword -> normalizedQuery.contains(keyword)
                        || keyword.contains(normalizedQuery));
    }

    /**
     * 构建提供给 LLM 的工具参数 JSON Schema。
     */
    private JsonNode createParameterSchema() {
        ObjectNode query = objectMapper.createObjectNode();
        query.put("type", "string");
        query.put("description", "需要查询的问题或关键词");

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("query", query);

        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", properties);
        schema.putArray("required").add("query");
        return schema;
    }

    /**
     * 创建失败的标准工具结果。
     */
    private ToolResult failure(String error) {
        return new ToolResult(false, NAME, null, error);
    }
}
