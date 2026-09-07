package com.zhaoweijie.minimalagent.session;

import com.zhaoweijie.minimalagent.context.AgentMessage;
import com.zhaoweijie.minimalagent.context.AgentMessageRole;
import com.zhaoweijie.minimalagent.exception.SessionAccessDeniedException;
import com.zhaoweijie.minimalagent.exception.SessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemorySessionManagerTests {

    /** 被测试的线程安全内存 Session 管理器。 */
    private InMemorySessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new InMemorySessionManager();
    }

    @Test
    void createsMultipleIndependentSessionsForSameUser() {
        AgentSession first = sessionManager.createSession("user-1");
        AgentSession second = sessionManager.createSession("user-1");

        assertThat(first.getSessionId()).isNotEqualTo(second.getSessionId());
        assertThat(first.getUserId()).isEqualTo("user-1");
        assertThat(second.getUserId()).isEqualTo("user-1");
        assertThat(sessionManager.getSession(first.getSessionId(), "user-1").getSessionId())
                .isEqualTo(first.getSessionId());
    }

    @Test
    void keepsMessagesIsolatedBetweenSessions() {
        AgentSession first = sessionManager.createSession("user-1");
        AgentSession second = sessionManager.createSession("user-1");
        first.getMessages().add(new AgentMessage(AgentMessageRole.USER, "first", null, null));

        sessionManager.update(first);

        assertThat(sessionManager.getSession(first.getSessionId(), "user-1").getMessages())
                .extracting(AgentMessage::content)
                .containsExactly("first");
        assertThat(sessionManager.getSession(second.getSessionId(), "user-1").getMessages())
                .isEmpty();
    }

    @Test
    void rejectsCrossUserAccess() {
        AgentSession session = sessionManager.createSession("owner");

        assertThatThrownBy(() -> sessionManager.getSession(session.getSessionId(), "intruder"))
                .isInstanceOfSatisfying(SessionAccessDeniedException.class, exception -> {
                    assertThat(exception.getSessionId()).isEqualTo(session.getSessionId());
                    assertThat(exception.getUserId()).isEqualTo("intruder");
                });
        assertThatThrownBy(() -> sessionManager.getOrCreate(session.getSessionId(), "intruder"))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void getsOrCreatesRequestedSession() {
        AgentSession created = sessionManager.getOrCreate("client-session", "user-1");
        AgentSession existing = sessionManager.getOrCreate("client-session", "user-1");

        assertThat(created.getSessionId()).isEqualTo("client-session");
        assertThat(existing.getCreatedAt()).isEqualTo(created.getCreatedAt());
    }

    @Test
    void savesNewSessionAndUpdatesExistingSession() {
        AgentSession newSession = new AgentSession(
                "imported-session",
                "user-1",
                List.of(),
                null,
                null,
                null
        );

        AgentSession saved = sessionManager.save(newSession);
        saved.setSummary("updated summary");
        AgentSession updated = sessionManager.update(saved);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(updated.getSummary()).isEqualTo("updated summary");
        assertThat(updated.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsUnknownSessionOnReadAndUpdate() {
        assertThatThrownBy(() -> sessionManager.getSession("missing", "user-1"))
                .isInstanceOf(SessionNotFoundException.class);

        AgentSession missing = new AgentSession(
                "missing",
                "user-1",
                List.of(),
                null,
                null,
                null
        );
        assertThatThrownBy(() -> sessionManager.update(missing))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
