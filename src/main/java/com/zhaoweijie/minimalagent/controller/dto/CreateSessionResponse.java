package com.zhaoweijie.minimalagent.controller.dto;

import java.time.Instant;

/**
 * 创建 Session 后的 HTTP 响应。
 *
 * @param sessionId Session 标识
 * @param userId    Session 所属用户标识
 * @param createdAt Session 创建时间
 */
public record CreateSessionResponse(
        /** Session 标识。 */
        String sessionId,
        /** Session 所属用户标识。 */
        String userId,
        /** Session 创建时间。 */
        Instant createdAt
) {
}
