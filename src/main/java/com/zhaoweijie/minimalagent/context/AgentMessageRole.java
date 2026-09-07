package com.zhaoweijie.minimalagent.context;

/**
 * Agent 对话消息的发送方角色。
 */
public enum AgentMessageRole {
    /** 系统指令。 */
    SYSTEM,
    /** 用户输入。 */
    USER,
    /** LLM 助手输出。 */
    ASSISTANT,
    /** 工具执行结果。 */
    TOOL
}
