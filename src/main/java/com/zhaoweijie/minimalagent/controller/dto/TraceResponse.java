package com.zhaoweijie.minimalagent.controller.dto;

import java.time.Instant;
import java.util.List;

/**
 * 一次请求完整 Agent Trace 的 HTTP 响应。
 */
public record TraceResponse(
        /** 请求级 Trace 标识。 */ String traceId,
        /** 请求用户标识。 */ String userId,
        /** 请求所属 Session 标识。 */ String sessionId,
        /** Trace 创建时间。 */ Instant createdAt,
        /** 按发生顺序返回的 Trace 事件。 */ List<TraceEventResponse> events
) {
    public TraceResponse {
        events = events == null ? List.of() : List.copyOf(events);
    }
}
