package com.zhaoweijie.minimalagent.controller;

import com.zhaoweijie.minimalagent.controller.dto.ChatRequest;
import com.zhaoweijie.minimalagent.controller.dto.ChatResponse;
import com.zhaoweijie.minimalagent.runtime.AgentRunResult;
import com.zhaoweijie.minimalagent.runtime.AgentRuntime;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供同步 Agent 对话能力的 REST Controller。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    /** 项目自有的 Agent 主循环。 */
    private final AgentRuntime agentRuntime;

    public AgentController(AgentRuntime agentRuntime) {
        this.agentRuntime = agentRuntime;
    }

    /**
     * 执行一次用户消息到最终回答的完整 Agent Loop。
     */
    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        AgentRunResult result = agentRuntime.run(
                request.userId(),
                request.sessionId(),
                request.message()
        );
        return new ChatResponse(
                result.traceId(),
                result.sessionId(),
                result.answer(),
                result.rounds()
        );
    }
}
