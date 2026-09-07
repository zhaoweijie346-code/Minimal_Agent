package com.zhaoweijie.minimalagent.session;

import com.zhaoweijie.minimalagent.exception.SessionAccessDeniedException;
import com.zhaoweijie.minimalagent.exception.SessionNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于线程安全内存 Map 的 SessionManager 第一版实现。
 */
@Service
public class InMemorySessionManager implements SessionManager {

    /** 以 Session ID 为主键保存的线程安全 Session 存储。 */
    private final ConcurrentMap<String, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AgentSession createSession(String userId) {
        requireText(userId, "userId");

        // UUID 冲突概率极低，仍使用 putIfAbsent 循环保证主键绝不被覆盖。
        while (true) {
            String sessionId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            AgentSession session = new AgentSession(
                    sessionId,
                    userId,
                    List.of(),
                    null,
                    now,
                    now
            );
            if (sessions.putIfAbsent(sessionId, session) == null) {
                return copyOf(session);
            }
        }
    }

    @Override
    public AgentSession getSession(String sessionId, String userId) {
        requireText(sessionId, "sessionId");
        requireText(userId, "userId");

        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            throw new SessionNotFoundException(sessionId);
        }
        verifyAccess(session, userId);
        return copyOf(session);
    }

    @Override
    public AgentSession getOrCreate(String sessionId, String userId) {
        requireText(userId, "userId");
        if (sessionId == null || sessionId.isBlank()) {
            return createSession(userId);
        }

        // compute 将同一 ID 的首次创建与所有权校验放在单个原子操作中。
        AgentSession session = sessions.compute(sessionId, (id, existing) -> {
            if (existing != null) {
                verifyAccess(existing, userId);
                return existing;
            }

            Instant now = Instant.now();
            return new AgentSession(id, userId, List.of(), null, now, now);
        });
        return copyOf(session);
    }

    @Override
    public AgentSession save(AgentSession session) {
        validateSession(session);

        // save 支持首次写入和同用户覆盖，但绝不允许借同一主键更换所有者。
        AgentSession saved = sessions.compute(session.getSessionId(), (id, existing) -> {
            if (existing != null) {
                verifyAccess(existing, session.getUserId());
            }

            AgentSession snapshot = copyOf(session);
            Instant now = Instant.now();
            snapshot.setCreatedAt(existing == null
                    ? Objects.requireNonNullElse(snapshot.getCreatedAt(), now)
                    : existing.getCreatedAt());
            snapshot.setUpdatedAt(now);
            return snapshot;
        });
        return copyOf(saved);
    }

    @Override
    public AgentSession update(AgentSession session) {
        validateSession(session);

        // compute 保证“检查存在及所有者”和“替换快照”之间没有竞态窗口。
        AgentSession updated = sessions.compute(session.getSessionId(), (id, existing) -> {
            if (existing == null) {
                throw new SessionNotFoundException(id);
            }
            verifyAccess(existing, session.getUserId());

            AgentSession snapshot = copyOf(session);
            snapshot.setCreatedAt(existing.getCreatedAt());
            snapshot.setUpdatedAt(Instant.now());
            return snapshot;
        });
        return copyOf(updated);
    }

    /**
     * 校验请求用户是否拥有目标 Session。
     */
    private void verifyAccess(AgentSession session, String requestUserId) {
        if (!Objects.equals(session.getUserId(), requestUserId)) {
            throw new SessionAccessDeniedException(session.getSessionId(), requestUserId);
        }
    }

    /**
     * 校验待保存 Session 的主键和所有者字段。
     */
    private void validateSession(AgentSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        requireText(session.getSessionId(), "sessionId");
        requireText(session.getUserId(), "userId");
    }

    /**
     * 校验字符串标识不为空。
     */
    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    /**
     * 复制 Session 及其消息列表，隔离仓库存储与调用方的可变状态。
     */
    private AgentSession copyOf(AgentSession source) {
        return new AgentSession(
                source.getSessionId(),
                source.getUserId(),
                source.getMessages(),
                source.getSummary(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}
