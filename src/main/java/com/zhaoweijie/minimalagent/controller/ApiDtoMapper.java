package com.zhaoweijie.minimalagent.controller;

import com.zhaoweijie.minimalagent.controller.dto.AgentMessageResponse;
import com.zhaoweijie.minimalagent.controller.dto.SessionMessagesResponse;
import com.zhaoweijie.minimalagent.controller.dto.ToolCallResponse;
import com.zhaoweijie.minimalagent.controller.dto.ToolResultResponse;
import com.zhaoweijie.minimalagent.controller.dto.TraceEventResponse;
import com.zhaoweijie.minimalagent.controller.dto.TraceResponse;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import com.zhaoweijie.minimalagent.trace.AgentTrace;
import com.zhaoweijie.minimalagent.trace.TraceEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 将内部领域模型显式映射为稳定的 REST API DTO。
 */
@Component
public class ApiDtoMapper {

    /**
     * 将 Session 消息快照映射为 API 响应。
     */
    public SessionMessagesResponse toSessionMessages(AgentSession session) {
        List<AgentMessageResponse> messages = session.getMessages().stream()
                .map(this::toMessage)
                .toList();
        return new SessionMessagesResponse(
                session.getSessionId(),
                session.getUserId(),
                messages
        );
    }

    /**
     * 将请求级 Trace 快照映射为 API 响应。
     */
    public TraceResponse toTrace(AgentTrace trace) {
        return new TraceResponse(
                trace.traceId(),
                trace.userId(),
                trace.sessionId(),
                trace.createdAt(),
                trace.events().stream().map(this::toTraceEvent).toList()
        );
    }

    /**
     * 映射一条结构化 Agent 消息及其工具调用元数据。
     */
    private AgentMessageResponse toMessage(AgentMessage message) {
        List<ToolCallResponse> toolCalls = message.toolCalls().stream()
                .map(toolCall -> new ToolCallResponse(
                        toolCall.toolCallId(),
                        toolCall.toolName(),
                        toolCall.arguments()
                ))
                .toList();
        return new AgentMessageResponse(
                message.role().name(),
                message.content(),
                message.toolCallId(),
                toolCalls
        );
    }

    /**
     * 映射一条 Trace 事件，不向 API 暴露领域对象引用。
     */
    private TraceEventResponse toTraceEvent(TraceEvent event) {
        return new TraceEventResponse(
                event.type().name(),
                event.round(),
                event.toolCallsReturned(),
                event.toolCallId(),
                event.toolName(),
                event.arguments(),
                toToolResult(event.toolResult()),
                event.durationMillis(),
                event.error(),
                event.finalAnswer(),
                event.createdAt()
        );
    }

    /**
     * 映射可为空的工具结果。
     */
    private ToolResultResponse toToolResult(ToolResult result) {
        if (result == null) {
            return null;
        }
        return new ToolResultResponse(
                result.success(),
                result.toolName(),
                result.data(),
                result.error()
        );
    }
}
