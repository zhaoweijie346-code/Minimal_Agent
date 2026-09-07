package com.zhaoweijie.minimalagent.llm;

import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 ToolRegistry 中的工具动态转换为 LLM ToolDefinition。
 */
@Component
public class ToolDefinitionProvider {

    /** 应用中全部工具 Bean 的注册表。 */
    private final ToolRegistry toolRegistry;

    /**
     * 创建工具定义提供器。
     *
     * @param toolRegistry 工具注册表
     */
    public ToolDefinitionProvider(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 根据当前注册表生成工具定义，不缓存或硬编码具体工具。
     *
     * @return 当前全部工具的不可修改定义列表
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool tool : toolRegistry.getAll().values()) {
            definitions.add(new ToolDefinition(
                    tool.name(),
                    tool.description(),
                    tool.parameterSchema()
            ));
        }
        return List.copyOf(definitions);
    }
}
