package com.zhaoweijie.minimalagent.tool;

import com.zhaoweijie.minimalagent.exception.ToolNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolRegistryTests {

    @Test
    void registersAndReturnsToolsByName() {
        AgentTool calculator = toolNamed("calculator");
        AgentTool search = toolNamed("search");
        ToolRegistry registry = new ToolRegistry(List.of(calculator, search));

        assertThat(registry.get("calculator")).isSameAs(calculator);
        assertThat(registry.getAll())
                .containsOnlyKeys("calculator", "search")
                .containsEntry("search", search);
    }

    @Test
    void exposesReadOnlyToolMap() {
        ToolRegistry registry = new ToolRegistry(List.of(toolNamed("calculator")));

        assertThatThrownBy(() -> registry.getAll().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateToolNames() {
        AgentTool first = toolNamed("calculator");
        AgentTool second = toolNamed("calculator");

        assertThatThrownBy(() -> new ToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate tool name: calculator");
    }

    @Test
    void throwsForUnknownTool() {
        ToolRegistry registry = new ToolRegistry(List.of());

        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOfSatisfying(ToolNotFoundException.class, exception -> {
                    assertThat(exception.getToolName()).isEqualTo("missing");
                    assertThat(exception).hasMessage("Tool not found: missing");
                });
    }

    /**
     * 创建具有指定名称的最小工具替身，隔离注册表测试与具体工具实现。
     */
    private AgentTool toolNamed(String name) {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn(name);
        return tool;
    }
}
