package com.zhaoweijie.minimalagent.trace;

import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.tool.ToolResult;

import java.util.List;

/**
 * 记录和读取 Agent Runtime 可观测行为的统一协议。
 */
public interface AgentTraceRecorder {

    /** 为一次用户请求创建 Trace，并返回唯一 traceId。 */
    String startTrace(String userId, String sessionId);

    /** 记录一次 LLM 调用、耗时、工具调用标记和安全错误。 */
    void recordLlmCall(
            String traceId,
            int round,
            Boolean toolCallsReturned,
            long durationMillis,
            String error
    );

    /** 记录模型产生的结构化工具调用。 */
    void recordToolCall(String traceId, int round, ToolCallAction action);

    /** 记录工具成功或失败的标准结果。 */
    void recordToolResult(
            String traceId,
            int round,
            ToolCallAction action,
            ToolResult result,
            long durationMillis
    );

    /** 记录返回给用户的最终回答。 */
    void recordFinal(String traceId, int round, String answer, long durationMillis);

    /** 记录 Runtime、LLM 或工具的安全错误说明。 */
    void recordError(
            String traceId,
            int round,
            String toolCallId,
            String toolName,
            String error,
            long durationMillis
    );

    /** 按 traceId 获取一次请求的完整 Trace 快照。 */
    AgentTrace getTrace(String traceId);

    /** 获取指定 Session 下的全部请求 Trace 快照。 */
    List<AgentTrace> getTracesBySession(String sessionId);
}
