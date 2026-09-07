package com.zhaoweijie.minimalagent.controller;

import com.zhaoweijie.minimalagent.controller.dto.CreateSessionRequest;
import com.zhaoweijie.minimalagent.controller.dto.CreateSessionResponse;
import com.zhaoweijie.minimalagent.controller.dto.SessionMessagesResponse;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.SessionManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 创建 Session 和读取隔离消息历史的 REST Controller。
 */
@Validated
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    /** 提供 Session 生命周期和访问校验。 */
    private final SessionManager sessionManager;

    /** 将 Session 领域模型映射为 API DTO。 */
    private final ApiDtoMapper dtoMapper;

    public SessionController(SessionManager sessionManager, ApiDtoMapper dtoMapper) {
        this.sessionManager = sessionManager;
        this.dtoMapper = dtoMapper;
    }

    /**
     * 为指定用户创建新的 Session。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSessionResponse createSession(@Valid @RequestBody CreateSessionRequest request) {
        AgentSession session = sessionManager.createSession(request.userId());
        return new CreateSessionResponse(
                session.getSessionId(),
                session.getUserId(),
                session.getCreatedAt()
        );
    }

    /**
     * 读取 Session 消息，并由 SessionManager 校验 userId 所有权。
     */
    @GetMapping("/{sessionId}/messages")
    public SessionMessagesResponse getMessages(
            @PathVariable @NotBlank String sessionId,
            @RequestParam @NotBlank(message = "userId must not be blank") String userId
    ) {
        return dtoMapper.toSessionMessages(sessionManager.getSession(sessionId, userId));
    }
}
