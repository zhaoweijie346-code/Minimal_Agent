package com.zhaoweijie.minimalagent.session;

import com.zhaoweijie.minimalagent.context.AgentMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AgentSession {

    private String sessionId;
    private String userId;
    private List<AgentMessage> messages = new ArrayList<>();
    private String summary;
    private Instant createdAt;
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
