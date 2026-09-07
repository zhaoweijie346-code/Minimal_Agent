package com.zhaoweijie.minimalagent.context;

import java.util.List;

/**
 * 每次模型调用前从 Session 召回的最小长期与短期记忆。
 *
 * @param summary        老消息压缩形成的 Session 摘要
 * @param recentMessages 原样保留的近期消息，包含结构化工具调用和工具结果
 */
public record SessionMemory(
        /** 老消息压缩形成的 Session 摘要。 */
        String summary,
        /** 原样保留的近期消息，包含结构化工具调用和工具结果。 */
        List<AgentMessage> recentMessages
) {

    public SessionMemory {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }
}
