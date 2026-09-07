package com.zhaoweijie.minimalagent.action;

/**
 * 表示 Agent 已得到最终回答的动作。
 *
 * @param answer 返回给用户的最终回答
 */
public record FinalAnswerAction(
        /** 返回给用户的最终回答。 */
        String answer
) implements AgentAction {
}
