package com.zhaoweijie.minimalagent.trace;

/**
 * Agent Runtime 对外记录的行为事件类型。
 */
public enum TraceType {
    /** 一次模型调用完成或失败。 */
    LLM_CALL,
    /** 模型请求调用工具。 */
    TOOL_CALL,
    /** 工具执行完成并得到结果。 */
    TOOL_RESULT,
    /** 模型生成最终回答。 */
    FINAL,
    /** Runtime、LLM 或工具发生错误。 */
    ERROR
}
