package com.zhaoweijie.minimalagent.tool;

/**
 * 工具执行时所需的最小调用上下文。
 *
 * @param userId    当前用户标识
 * @param sessionId 当前会话标识
 */
public record ToolExecutionContext(
        /** 当前用户标识。 */
        String userId,
        /** 当前会话标识。 */
        String sessionId
) {
}
