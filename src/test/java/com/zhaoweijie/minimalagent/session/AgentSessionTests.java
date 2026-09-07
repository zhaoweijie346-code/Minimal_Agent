package com.zhaoweijie.minimalagent.session;

import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSessionTests {

    @Test
    void initializesSessionState() {
        var createdAt = Instant.parse("2026-09-07T10:00:00Z");
        var updatedAt = Instant.parse("2026-09-07T10:01:00Z");
        var sourceMessages = new ArrayList<>(List.of(
                new AgentMessage(AgentMessageRole.USER, "hello", null, null)
        ));

        var session = new AgentSession(
                "session-1",
                "user-1",
                sourceMessages,
                "greeting",
                createdAt,
                updatedAt
        );
        sourceMessages.clear();

        assertThat(session.getSessionId()).isEqualTo("session-1");
        assertThat(session.getUserId()).isEqualTo("user-1");
        assertThat(session.getMessages()).hasSize(1);
        assertThat(session.getSummary()).isEqualTo("greeting");
        assertThat(session.getCreatedAt()).isEqualTo(createdAt);
        assertThat(session.getUpdatedAt()).isEqualTo(updatedAt);
    }
}
