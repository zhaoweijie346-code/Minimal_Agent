package com.zhaoweijie.minimalagent.action;

/**
 * Agent 单步决策的统一类型。
 */
public sealed interface AgentAction permits ToolCallAction, FinalAnswerAction {
}
