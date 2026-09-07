package com.zhaoweijie.minimalagent.exception;

/**
 * 请求调用的工具未在 ToolRegistry 中注册时抛出的异常。
 */
public class ToolNotFoundException extends RuntimeException {

    /** 未找到的工具名称。 */
    private final String toolName;

    /**
     * 创建未知工具异常。
     *
     * @param toolName 未找到的工具名称
     */
    public ToolNotFoundException(String toolName) {
        super("Tool not found: " + toolName);
        this.toolName = toolName;
    }

    /**
     * 获取未找到的工具名称。
     *
     * @return 未找到的工具名称
     */
    public String getToolName() {
        return toolName;
    }
}
