package com.zhaoweijie.minimalagent.trace;

import java.time.Instant;
import java.util.List;

/**
 * 一次用户请求对应的完整 Agent Trace 快照。
 *
 * @param traceId   请求级追踪标识
 * @param userId    请求用户标识
 * @param sessionId 请求所属 Session 标识
 * @param createdAt Trace 创建时间
 * @param events    按发生顺序保存的行为事件
 */
public record AgentTrace(
        /** 请求级追踪标识。 */
        String traceId,
        /** 请求用户标识。 */
        String userId,
        /** 请求所属 Session 标识。 */
        String sessionId,
        /** Trace 创建时间。 */
        Instant createdAt,
        /** 按发生顺序保存的行为事件。 */
        List<TraceEvent> events
) {

    public AgentTrace {
        // 固化事件集合，避免调用方修改内存 Trace 存储。
        events = events == null ? List.of() : List.copyOf(events);
    }
}
