package com.zhaoweijie.minimalagent.tool;

import com.zhaoweijie.minimalagent.exception.ToolNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注册并统一查询应用中的全部 Agent 工具。
 */
@Component
public class ToolRegistry {

    /** 按唯一工具名称索引的只读工具集合。 */
    private final Map<String, AgentTool> tools;

    /**
     * 使用 Spring 注入的全部工具 Bean 构建注册表。
     *
     * @param agentTools 应用上下文中的全部 AgentTool Bean
     * @throws IllegalStateException 存在重复工具名称时抛出，使应用启动失败
     */
    public ToolRegistry(List<AgentTool> agentTools) {
        Map<String, AgentTool> registeredTools = new LinkedHashMap<>();

        // 工具名是 LLM 调用与具体实现之间的路由键，重复时不能静默覆盖。
        for (AgentTool agentTool : agentTools) {
            AgentTool existingTool = registeredTools.putIfAbsent(agentTool.name(), agentTool);
            if (existingTool != null) {
                throw new IllegalStateException("Duplicate tool name: " + agentTool.name());
            }
        }

        this.tools = Collections.unmodifiableMap(registeredTools);
    }

    /**
     * 按名称获取工具。
     *
     * @param name 工具名称
     * @return 已注册工具
     * @throws ToolNotFoundException 工具名称未注册时抛出
     */
    public AgentTool get(String name) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            throw new ToolNotFoundException(name);
        }
        return tool;
    }

    /**
     * 获取全部已注册工具。
     *
     * @return 以工具名称为键的只读 Map
     */
    public Map<String, AgentTool> getAll() {
        return tools;
    }
}
