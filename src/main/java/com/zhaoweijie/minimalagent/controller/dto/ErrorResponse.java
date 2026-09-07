package com.zhaoweijie.minimalagent.controller.dto;

import java.time.Instant;

/**
 * 所有 REST API 错误使用的统一响应，不包含异常堆栈。
 */
public record ErrorResponse(
        /** 错误响应生成时间。 */ Instant timestamp,
        /** HTTP 状态码。 */ int status,
        /** 稳定的错误类型。 */ String error,
        /** 面向调用方的安全错误说明。 */ String message,
        /** 发生错误的请求路径。 */ String path
) {
}
