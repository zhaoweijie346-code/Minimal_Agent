package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolDefinitionProviderTests {

    @Test
    void dynamicallyBuildsDefinitionsFromToolRegistry() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentTool calculator = tool(
                "calculator",
                "calculate expressions",
                objectMapper
        );
        AgentTool search = tool("search", "search information", objectMapper);
        ToolDefinitionProvider provider = new ToolDefinitionProvider(
                new ToolRegistry(List.of(calculator, search))
        );

        List<ToolDefinition> definitions = provider.getToolDefinitions();

        assertThat(definitions)
                .extracting(ToolDefinition::name)
                .containsExactly("calculator", "search");
        assertThat(definitions.get(0).description()).isEqualTo("calculate expressions");
        assertThat(definitions.get(0).parameters().path("type").textValue())
                .isEqualTo("object");
    }

    /**
     * 创建提供名称、描述和参数 Schema 的工具替身。
     */
    private AgentTool tool(String name, String description, ObjectMapper objectMapper) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        when(tool.description()).thenReturn(description);
        when(tool.parameterSchema()).thenReturn(
                objectMapper.createObjectNode().put("type", "object")
        );
        return tool;
    }
}
