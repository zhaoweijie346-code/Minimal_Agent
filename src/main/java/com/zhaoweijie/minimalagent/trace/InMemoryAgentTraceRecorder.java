package com.zhaoweijie.minimalagent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.exception.TraceNotFoundException;
import com.zhaoweijie.minimalagent.exception.TraceAccessDeniedException;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 使用线程安全内存存储按请求 traceId 隔离的 Agent Trace。
 */
@Component
public class InMemoryAgentTraceRecorder implements AgentTraceRecorder {

    /** 以请求级 traceId 为主键保存的线程安全 Trace 状态。 */
    private final ConcurrentMap<String, TraceState> traces = new ConcurrentHashMap<>();

    @Override
    public String startTrace(String userId, String sessionId) {
        String traceId = UUID.randomUUID().toString();
        traces.put(traceId, new TraceState(userId, sessionId, Instant.now()));
        return traceId;
    }

    @Override
    public void recordLlmCall(
            String traceId,
            int round,
            Boolean toolCallsReturned,
            long durationMillis,
            String error
    ) {
        add(traceId, newEvent(
                traceId, TraceType.LLM_CALL, round, toolCallsReturned,
                null, null, null, null, durationMillis, error, null
        ));
    }

    @Override
    public void recordToolCall(String traceId, int round, ToolCallAction action) {
        add(traceId, newEvent(
                traceId, TraceType.TOOL_CALL, round, null,
                action.toolCallId(), action.toolName(), action.arguments(), null,
                0, null, null
        ));
    }

    @Override
    public void recordToolResult(
            String traceId,
            int round,
            ToolCallAction action,
            ToolResult result,
            long durationMillis
    ) {
        add(traceId, newEvent(
                traceId, TraceType.TOOL_RESULT, round, null,
                action.toolCallId(), action.toolName(), null, result,
                durationMillis, result.error(), null
        ));
    }

    @Override
    public void recordFinal(String traceId, int round, String answer, long durationMillis) {
        add(traceId, newEvent(
                traceId, TraceType.FINAL, round, false,
                null, null, null, null, durationMillis, null, answer
        ));
    }

    @Override
    public void recordError(
            String traceId,
            int round,
            String toolCallId,
            String toolName,
            String error,
            long durationMillis
    ) {
        add(traceId, newEvent(
                traceId, TraceType.ERROR, round, null,
                toolCallId, toolName, null, null, durationMillis, error, null
        ));
    }

    @Override
    public AgentTrace getTrace(String traceId) {
        return snapshot(traceId, requireState(traceId));
    }

    @Override
    public AgentTrace getTrace(String traceId, String userId) {
        TraceState state = requireState(traceId);
        if (!state.userId().equals(userId)) {
            throw new TraceAccessDeniedException(traceId, userId);
        }
        return snapshot(traceId, state);
    }

    @Override
    public List<AgentTrace> getTracesBySession(String sessionId) {
        return traces.entrySet().stream()
                .filter(entry -> entry.getValue().sessionId().equals(sessionId))
                .map(entry -> snapshot(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AgentTrace::createdAt))
                .toList();
    }

    /**
     * 使用 Trace 聚合元数据创建事件，避免 Runtime 重复传递 userId 和 sessionId。
     */
    private TraceEvent newEvent(
            String traceId,
            TraceType type,
            int round,
            Boolean toolCallsReturned,
            String toolCallId,
            String toolName,
            JsonNode arguments,
            ToolResult toolResult,
            long durationMillis,
            String error,
            String finalAnswer
    ) {
        TraceState state = requireState(traceId);
        return new TraceEvent(
                type,
                traceId,
                state.userId(),
                state.sessionId(),
                round,
                toolCallsReturned,
                toolCallId,
                toolName,
                arguments,
                toolResult,
                durationMillis,
                error,
                finalAnswer,
                Instant.now()
        );
    }

    /**
     * 将事件追加到指定请求 Trace。
     */
    private void add(String traceId, TraceEvent event) {
        requireState(traceId).events().add(event);
    }

    /**
     * 获取内部 Trace 状态，不存在时拒绝写入孤立事件。
     */
    private TraceState requireState(String traceId) {
        TraceState state = traces.get(traceId);
        if (state == null) {
            throw new TraceNotFoundException(traceId);
        }
        return state;
    }

    /**
     * 创建不可修改的请求级 Trace 快照。
     */
    private AgentTrace snapshot(String traceId, TraceState state) {
        // 重建事件会再次深拷贝 JsonNode 和 ToolResult data，避免读取方修改内部存储。
        List<TraceEvent> eventSnapshots = state.events().stream()
                .map(this::copyEvent)
                .toList();
        return new AgentTrace(
                traceId,
                state.userId(),
                state.sessionId(),
                state.createdAt(),
                eventSnapshots
        );
    }

    /**
     * 复制事件及其中的可变 JSON 字段。
     */
    private TraceEvent copyEvent(TraceEvent event) {
        return new TraceEvent(
                event.type(),
                event.traceId(),
                event.userId(),
                event.sessionId(),
                event.round(),
                event.toolCallsReturned(),
                event.toolCallId(),
                event.toolName(),
                event.arguments(),
                event.toolResult(),
                event.durationMillis(),
                event.error(),
                event.finalAnswer(),
                event.createdAt()
        );
    }

    /**
     * 内存中保存的可变 Trace 状态。
     *
     * @param userId    请求用户标识
     * @param sessionId 请求所属 Session 标识
     * @param createdAt Trace 创建时间
     * @param events    线程安全事件列表
     */
    private record TraceState(
            /** 请求用户标识。 */
            String userId,
            /** 请求所属 Session 标识。 */
            String sessionId,
            /** Trace 创建时间。 */
            Instant createdAt,
            /** 线程安全事件列表。 */
            CopyOnWriteArrayList<TraceEvent> events
    ) {
        private TraceState(String userId, String sessionId, Instant createdAt) {
            this(userId, sessionId, createdAt, new CopyOnWriteArrayList<>());
        }
    }
}
