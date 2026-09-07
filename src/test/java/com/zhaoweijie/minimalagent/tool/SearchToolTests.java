package com.zhaoweijie.minimalagent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SearchToolTests {

    /** 用于构造测试参数的 Jackson 对象映射器。 */
    private ObjectMapper objectMapper;

    /** 被测试的 Mock 搜索工具。 */
    private SearchTool searchTool;

    /** 工具调用所需的最小执行上下文。 */
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        searchTool = new SearchTool(objectMapper);
        context = new ToolExecutionContext("user-1", "session-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Java 21", "Spring Boot", "AI Agent", "Dubbo", "Nacos"})
    void returnsResultsForKnownTopics(String query) {
        ToolResult result = searchTool.execute(context, arguments(query));

        assertThat(result.success()).isTrue();
        assertThat(result.toolName()).isEqualTo("search");
        assertThat(result.error()).isNull();
        assertThat(result.data().path("results")).isNotEmpty();
    }

    @Test
    void returnsEmptyResultsWhenNothingMatches() {
        ToolResult result = searchTool.execute(context, arguments("unmatched-topic"));

        assertThat(result.success()).isTrue();
        assertThat(result.data().path("results").isArray()).isTrue();
        assertThat(result.data().path("results")).isEmpty();
    }

    @Test
    void rejectsBlankQuery() {
        ToolResult result = searchTool.execute(context, arguments("   "));

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error()).isEqualTo("Query must be a non-blank string");
    }

    @Test
    void rejectsMissingQuery() {
        ToolResult result = searchTool.execute(context, objectMapper.createObjectNode());

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error()).isEqualTo("Missing required parameter: query");
    }

    @Test
    void exposesRequiredQuerySchema() {
        JsonNodeAssertions.assertQuerySchema(searchTool.parameterSchema());
    }

    /**
     * 创建符合工具 Schema 的查询参数。
     */
    private ObjectNode arguments(String query) {
        return objectMapper.createObjectNode().put("query", query);
    }

    /**
     * 集中表达 Schema 断言，避免主测试被字段导航细节干扰。
     */
    private static final class JsonNodeAssertions {

        private JsonNodeAssertions() {
        }

        private static void assertQuerySchema(com.fasterxml.jackson.databind.JsonNode schema) {
            assertThat(schema.path("type").textValue()).isEqualTo("object");
            assertThat(schema.path("properties").path("query").path("type").textValue())
                    .isEqualTo("string");
            assertThat(schema.path("properties").path("query").path("description").textValue())
                    .isEqualTo("需要查询的问题或关键词");
            assertThat(schema.path("required").get(0).textValue()).isEqualTo("query");
        }
    }
}
