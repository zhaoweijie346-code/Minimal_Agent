package com.zhaoweijie.minimalagent.trace;

import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 使用线程安全内存存储按 Session 隔离的 Agent 行为 Trace。
 */
@Component
public class InMemoryAgentTraceRecorder implements AgentTraceRecorder {

    /** 按 Session 标识保存的线程安全事件列表。 */
    private final ConcurrentMap<String, CopyOnWriteArrayList<TraceEvent>> traces =
            new ConcurrentHashMap<>();

    @Override
    public void recordToolCall(String sessionId, ToolCallAction action) {
        add(new TraceEvent(
                TraceEventType.TOOL_CALL,
                sessionId,
                action.toolCallId(),
                action.toolName(),
                action.arguments(),
                null,
                null,
                Instant.now()
        ));
    }

    @Override
    public void recordToolResult(String sessionId, ToolCallAction action, ToolResult result) {
        add(new TraceEvent(
                TraceEventType.TOOL_RESULT,
                sessionId,
                action.toolCallId(),
                action.toolName(),
                null,
                result,
                null,
                Instant.now()
        ));
    }

    @Override
    public void recordFinalResponse(String sessionId, String answer) {
        add(new TraceEvent(
                TraceEventType.FINAL_RESPONSE,
                sessionId,
                null,
                null,
                null,
                null,
                answer,
                Instant.now()
        ));
    }

    @Override
    public List<TraceEvent> getTrace(String sessionId) {
        List<TraceEvent> events = traces.get(sessionId);
        return events == null ? List.of() : List.copyOf(events);
    }

    /**
     * 将事件追加到对应 Session 的独立列表。
     */
    private void add(TraceEvent event) {
        traces.computeIfAbsent(
                event.sessionId(),
                ignored -> new CopyOnWriteArrayList<>()
        ).add(event);
    }
}
