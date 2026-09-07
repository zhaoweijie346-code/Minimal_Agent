package com.zhaoweijie.minimalagent.session;

import com.zhaoweijie.minimalagent.context.AgentMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次 Agent 对话的内存会话状态。
 */
public class AgentSession {

    /** 会话唯一标识。 */
    private String sessionId;

    /** 发起会话的用户标识。 */
    private String userId;

    /** 会话中按时间顺序保存的消息。 */
    private List<AgentMessage> messages = new ArrayList<>();

    /** 历史上下文压缩后生成的摘要。 */
    private String summary;

    /** 会话创建时间。 */
    private Instant createdAt;

    /** 会话最近更新时间。 */
    private Instant updatedAt;

    public AgentSession() {
    }

    public AgentSession(
            String sessionId,
            String userId,
            List<AgentMessage> messages,
            String summary,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        setMessages(messages);
        this.summary = summary;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<AgentMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AgentMessage> messages) {
        // 复制调用方列表，防止会话初始状态被外部集合的后续修改影响。
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
