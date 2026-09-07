package com.zhaoweijie.minimalagent.trace;

import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.tool.ToolResult;

import java.util.List;

/**
 * 记录和读取 Agent Runtime 可观测行为的统一协议。
 */
public interface AgentTraceRecorder {

    /** 记录模型产生的结构化工具调用。 */
    void recordToolCall(String sessionId, ToolCallAction action);

    /** 记录工具成功或失败的标准结果。 */
    void recordToolResult(String sessionId, ToolCallAction action, ToolResult result);

    /** 记录返回给用户的最终回答。 */
    void recordFinalResponse(String sessionId, String answer);

    /** 获取指定 Session 的行为事件快照。 */
    List<TraceEvent> getTrace(String sessionId);
}
