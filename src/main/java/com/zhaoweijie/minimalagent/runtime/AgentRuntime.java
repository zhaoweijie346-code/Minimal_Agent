package com.zhaoweijie.minimalagent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentRuntimeProperties;
import com.zhaoweijie.minimalagent.context.AgentContext;
import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.context.ContextManager;
import com.zhaoweijie.minimalagent.exception.LlmClientException;
import com.zhaoweijie.minimalagent.exception.InvalidLlmOutputException;
import com.zhaoweijie.minimalagent.exception.LlmApiException;
import com.zhaoweijie.minimalagent.exception.LlmTimeoutException;
import com.zhaoweijie.minimalagent.exception.MaxAgentRoundsException;
import com.zhaoweijie.minimalagent.exception.ToolArgumentException;
import com.zhaoweijie.minimalagent.exception.ToolNotFoundException;
import com.zhaoweijie.minimalagent.llm.LlmClient;
import com.zhaoweijie.minimalagent.llm.LlmResponse;
import com.zhaoweijie.minimalagent.llm.ToolDefinitionProvider;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.SessionManager;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolExecutionContext;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import com.zhaoweijie.minimalagent.tool.ToolResult;
import com.zhaoweijie.minimalagent.trace.AgentTraceRecorder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自行编排 Session、LLM Function Calling、工具执行和 Trace 的核心 Agent 主循环。
 */
@Service
public class AgentRuntime {

    /** 供应商无关的 LLM 调用入口，生产环境由百炼实现提供。 */
    private final LlmClient llmClient;

    /** 每轮调用前构建并召回 Memory 的上下文管理器。 */
    private final ContextManager contextManager;

    /** 持久化用户、助手和工具消息的 Session 管理器。 */
    private final SessionManager sessionManager;

    /** 按模型返回名称动态路由工具的注册表。 */
    private final ToolRegistry toolRegistry;

    /** 从工具注册表动态生成 API tools 定义的提供器。 */
    private final ToolDefinitionProvider toolDefinitionProvider;

    /** 将标准 ToolResult 序列化为 role=tool 消息的 Jackson 映射器。 */
    private final ObjectMapper objectMapper;

    /** 仅记录工具行为与最终回答的追踪器。 */
    private final AgentTraceRecorder traceRecorder;

    /** 主循环最大轮数配置。 */
    private final AgentRuntimeProperties properties;

    /**
     * 创建项目自有的 Agent Runtime。
     */
    public AgentRuntime(
            LlmClient llmClient,
            ContextManager contextManager,
            SessionManager sessionManager,
            ToolRegistry toolRegistry,
            ToolDefinitionProvider toolDefinitionProvider,
            ObjectMapper objectMapper,
            AgentTraceRecorder traceRecorder,
            AgentRuntimeProperties properties
    ) {
        this.llmClient = llmClient;
        this.contextManager = contextManager;
        this.sessionManager = sessionManager;
        this.toolRegistry = toolRegistry;
        this.toolDefinitionProvider = toolDefinitionProvider;
        this.objectMapper = objectMapper;
        this.traceRecorder = traceRecorder;
        this.properties = properties;
    }

    /**
     * 保存用户输入并循环执行原生 Function Calling，直到得到最终回答。
     *
     * @param userId      请求用户标识
     * @param sessionId   Session 标识；为空时创建新 Session
     * @param userMessage 本轮用户输入
     * @return 最终回答、Session 标识和实际 LLM 轮数
     */
    public AgentRunResult run(String userId, String sessionId, String userMessage) {
        requireUserMessage(userMessage);
        AgentSession session = sessionManager.getOrCreate(sessionId, userId);
        String resolvedSessionId = session.getSessionId();
        String traceId = traceRecorder.startTrace(userId, resolvedSessionId);
        long requestStartedAt = System.nanoTime();
        int round = 0;

        try {
            appendMessage(
                    userId,
                    resolvedSessionId,
                    new AgentMessage(AgentMessageRole.USER, userMessage, null, null)
            );

            for (round = 1; round <= properties.getMaxRounds(); round++) {
                // ContextManager 在此处召回并可能压缩 Memory，tools 始终从当前注册表动态生成。
                AgentContext context = contextManager.build(userId, resolvedSessionId);
                LlmResponse response = callLlm(traceId, round, context);

                if (response.hasToolCalls()) {
                    handleToolCalls(userId, resolvedSessionId, traceId, round, response);
                    continue;
                }

                String answer = response.content();
                appendMessage(
                        userId,
                        resolvedSessionId,
                        new AgentMessage(AgentMessageRole.ASSISTANT, answer, null, null)
                );
                traceRecorder.recordFinal(
                        traceId,
                        round,
                        answer,
                        elapsedMillis(requestStartedAt)
                );
                return new AgentRunResult(traceId, resolvedSessionId, answer, round);
            }

            throw new MaxAgentRoundsException(properties.getMaxRounds());
        } catch (RuntimeException exception) {
            // 只记录经过筛选的错误说明，不采集请求 Header、Authorization 或 API Key。
            traceRecorder.recordError(
                    traceId,
                    Math.min(round, properties.getMaxRounds()),
                    null,
                    null,
                    safeError(exception),
                    elapsedMillis(requestStartedAt)
            );
            throw exception;
        }
    }

    /**
     * 先保存完整 assistant(tool_calls)，再顺序执行并保存每个 role=tool 结果。
     */
    private void handleToolCalls(
            String userId,
            String sessionId,
            String traceId,
            int round,
            LlmResponse response
    ) {
        appendMessage(
                userId,
                sessionId,
                new AgentMessage(
                        AgentMessageRole.ASSISTANT,
                        response.content(),
                        null,
                        response.toolCalls()
                )
        );

        ToolExecutionContext executionContext = new ToolExecutionContext(userId, sessionId);
        for (ToolCallAction action : response.toolCalls()) {
            traceRecorder.recordToolCall(traceId, round, action);
            long toolStartedAt = System.nanoTime();
            ToolResult result = executeTool(executionContext, action);
            long toolDuration = elapsedMillis(toolStartedAt);
            traceRecorder.recordToolResult(traceId, round, action, result, toolDuration);
            if (!result.success()) {
                traceRecorder.recordError(
                        traceId,
                        round,
                        action.toolCallId(),
                        action.toolName(),
                        result.error(),
                        toolDuration
                );
            }
            appendMessage(
                    userId,
                    sessionId,
                    new AgentMessage(
                            AgentMessageRole.TOOL,
                            serializeToolResult(result),
                            action.toolCallId(),
                            null
                    )
            );
        }
    }

    /**
     * 调用一次 LLM 并无论成功或失败都记录轮次、耗时和 tool_calls 标记。
     */
    private LlmResponse callLlm(String traceId, int round, AgentContext context) {
        long startedAt = System.nanoTime();
        try {
            LlmResponse response = llmClient.chat(
                    context,
                    toolDefinitionProvider.getToolDefinitions()
            );
            validateResponse(response);
            traceRecorder.recordLlmCall(
                    traceId,
                    round,
                    response.hasToolCalls(),
                    elapsedMillis(startedAt),
                    null
            );
            return response;
        } catch (RuntimeException exception) {
            traceRecorder.recordLlmCall(
                    traceId,
                    round,
                    null,
                    elapsedMillis(startedAt),
                    safeError(exception)
            );
            throw exception;
        }
    }

    /**
     * 通过 ToolRegistry 执行工具，并将未知工具、返回空值和执行异常统一转换为失败结果。
     */
    private ToolResult executeTool(
            ToolExecutionContext executionContext,
            ToolCallAction action
    ) {
        try {
            AgentTool tool = toolRegistry.get(action.toolName());
            ToolResult result = tool.execute(executionContext, action.arguments());
            return result == null
                    ? failure(action.toolName(), "Tool returned no result")
                    : result;
        } catch (ToolNotFoundException exception) {
            return failure(action.toolName(), exception.getMessage());
        } catch (RuntimeException exception) {
            return failure(
                    action.toolName(),
                    "Tool execution failed: " + exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 使用标准 ToolResult JSON 作为 tool message content，供 Qwen 在下一轮决策。
     */
    private String serializeToolResult(ToolResult result) {
        return objectMapper.valueToTree(result).toString();
    }

    /**
     * 每次从 SessionManager 重新读取最新快照，避免覆盖 ContextManager 的 Memory 压缩结果。
     */
    private void appendMessage(String userId, String sessionId, AgentMessage message) {
        AgentSession latest = sessionManager.getSession(sessionId, userId);
        latest.getMessages().add(message);
        sessionManager.update(latest);
    }

    /**
     * 创建可回传给模型的标准工具失败结果。
     */
    private ToolResult failure(String toolName, String error) {
        return new ToolResult(false, toolName, null, error);
    }

    /**
     * 校验测试替身或后续 Provider 不会向 Runtime 返回空响应或空最终内容。
     */
    private void validateResponse(LlmResponse response) {
        if (response == null) {
            throw new IllegalStateException("LLM returned no response");
        }
        if (!response.hasToolCalls()
                && (response.content() == null || response.content().isBlank())) {
            throw new IllegalStateException("LLM returned no final answer or tool calls");
        }
    }

    /**
     * 拒绝空用户输入，避免创建无意义 Session 消息。
     */
    private void requireUserMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
    }

    /**
     * 计算单调时钟耗时，避免系统时间调整影响 duration。
     */
    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    /**
     * 返回不包含底层请求、Header 或凭证内容的安全错误说明。
     */
    private String safeError(RuntimeException exception) {
        if (exception instanceof LlmTimeoutException) {
            return "LLM request timed out";
        }
        if (exception instanceof ToolArgumentException) {
            return "Invalid tool arguments";
        }
        if (exception instanceof InvalidLlmOutputException) {
            return "Invalid LLM output";
        }
        if (exception instanceof LlmApiException) {
            return "LLM API request failed";
        }
        if (exception instanceof LlmClientException) {
            return "LLM processing failed";
        }
        if (exception instanceof MaxAgentRoundsException) {
            return exception.getMessage();
        }
        return exception.getClass().getSimpleName();
    }
}
