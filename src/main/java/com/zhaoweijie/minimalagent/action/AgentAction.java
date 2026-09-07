package com.zhaoweijie.minimalagent.action;

public sealed interface AgentAction permits ToolCallAction, FinalAnswerAction {
}
