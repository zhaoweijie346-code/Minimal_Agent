package com.zhaoweijie.minimalagent.context;

import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.SessionManager;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在模型调用前召回 Session Memory，并在需要时压缩老消息。
 */
@Component
public class SessionMemoryManager {

    /** 提供隔离的 Session 读取与更新。 */
    private final SessionManager sessionManager;

    /** 将老消息转换为受限长度摘要。 */
    private final MemoryCompressor memoryCompressor;

    /** 消息窗口和压缩阈值配置。 */
    private final AgentContextProperties properties;

    /**
     * 创建 Session Memory 管理器。
     *
     * @param sessionManager   Session 管理器
     * @param memoryCompressor 摘要压缩器
     * @param properties       上下文配置
     */
    public SessionMemoryManager(
            SessionManager sessionManager,
            MemoryCompressor memoryCompressor,
            AgentContextProperties properties
    ) {
        this.sessionManager = sessionManager;
        this.memoryCompressor = memoryCompressor;
        this.properties = properties;
    }

    /**
     * 召回当前用户 Session 的摘要和近期消息，超过阈值时先完成压缩。
     *
     * @param userId    请求用户标识
     * @param sessionId Session 标识
     * @return 本次模型调用使用的 Session Memory
     */
    public SessionMemory recall(String userId, String sessionId) {
        AgentSession session = sessionManager.getSession(sessionId, userId);
        if (session.getMessages().size() <= properties.getCompressionThreshold()) {
            return snapshot(session);
        }

        int recentStart = MessageWindow.startIndex(
                session.getMessages(),
                properties.getMaxRecentMessages(),
                null
        );
        if (recentStart == 0) {
            return snapshot(session);
        }

        // 老消息只进入摘要，近期消息则连同 tool_calls/tool results 原样写回 Session。
        List<AgentMessage> olderMessages = List.copyOf(
                session.getMessages().subList(0, recentStart)
        );
        List<AgentMessage> recentMessages = List.copyOf(
                session.getMessages().subList(recentStart, session.getMessages().size())
        );
        session.setSummary(memoryCompressor.compress(session.getSummary(), olderMessages));
        session.setMessages(recentMessages);

        AgentSession updated = sessionManager.update(session);
        return snapshot(updated);
    }

    /**
     * 从 Session 创建不可变 Memory 快照。
     */
    private SessionMemory snapshot(AgentSession session) {
        return new SessionMemory(session.getSummary(), session.getMessages());
    }
}
