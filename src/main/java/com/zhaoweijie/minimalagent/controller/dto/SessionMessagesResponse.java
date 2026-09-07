package com.zhaoweijie.minimalagent.controller.dto;

import java.util.List;

/**
 * 指定 Session 的消息历史 HTTP 响应。
 *
 * @param sessionId Session 标识
 * @param userId    Session 所属用户标识
 * @param messages  按保存顺序返回的消息
 */
public record SessionMessagesResponse(
        /** Session 标识。 */
        String sessionId,
        /** Session 所属用户标识。 */
        String userId,
        /** 按保存顺序返回的消息。 */
        List<AgentMessageResponse> messages
) {
    public SessionMessagesResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
