package com.zhaoweijie.minimalagent.controller.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建 Session 的 HTTP 请求。
 *
 * @param userId Session 所属用户标识
 */
public record CreateSessionRequest(
        /** Session 所属用户标识。 */
        @NotBlank(message = "userId must not be blank") String userId
) {
}
