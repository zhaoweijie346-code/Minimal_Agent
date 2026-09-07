package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.InMemorySessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SessionMemoryManagerTests {

    /** 测试使用的 Session 管理器。 */
    private InMemorySessionManager sessionManager;

    /** 测试使用的 Memory 配置。 */
    private AgentContextProperties properties;

    /** 被测试的 Session Memory 管理器。 */
    private SessionMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        sessionManager = new InMemorySessionManager();
        properties = new AgentContextProperties();
        properties.setCompressionThreshold(3);
        properties.setMaxRecentMessages(2);
        memoryManager = new SessionMemoryManager(
                sessionManager,
                new BasicMemoryCompressor(properties),
                properties
        );
    }

    @Test
    void compressesOlderMessagesAndKeepsRecentMessagesUnchanged() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCallAction toolCall = new ToolCallAction(
                "call-1",
                "todo",
                objectMapper.createObjectNode().put("action", "add")
        );
        AgentMessage assistantToolCall = new AgentMessage(
                AgentMessageRole.ASSISTANT,
                null,
                null,
                List.of(toolCall)
        );
        AgentMessage toolResult = new AgentMessage(
                AgentMessageRole.TOOL,
                "{\"status\":\"PENDING\",\"content\":\"write tests\"}",
                "call-1",
                null
        );
        AgentMessage latestUser = message(AgentMessageRole.USER, "latest question");
        AgentMessage latestAssistant = message(AgentMessageRole.ASSISTANT, "latest answer");
        AgentSession session = sessionManager.getOrCreate("session-1", "user-1");
        session.getMessages().add(message(AgentMessageRole.USER, "build an agent"));
        session.getMessages().add(assistantToolCall);
        session.getMessages().add(toolResult);
        session.getMessages().add(latestUser);
        session.getMessages().add(latestAssistant);
        sessionManager.update(session);

        SessionMemory memory = memoryManager.recall("user-1", "session-1");

        assertThat(memory.summary())
                .contains("用户目标或重要事实: build an agent")
                .contains("工具调用: todo")
                .contains("完成或未完成事项及关键工具结果");
        assertThat(memory.recentMessages()).containsExactly(latestUser, latestAssistant);

        AgentSession stored = sessionManager.getSession("session-1", "user-1");
        assertThat(stored.getSummary()).isEqualTo(memory.summary());
        assertThat(stored.getMessages()).containsExactly(latestUser, latestAssistant);
    }

    @Test
    void leavesSessionUntouchedBelowCompressionThreshold() {
        AgentSession session = sessionManager.getOrCreate("session-1", "user-1");
        AgentMessage message = message(AgentMessageRole.USER, "short conversation");
        session.getMessages().add(message);
        sessionManager.update(session);

        SessionMemory memory = memoryManager.recall("user-1", "session-1");

        assertThat(memory.summary()).isNull();
        assertThat(memory.recentMessages()).containsExactly(message);
    }

    @Test
    void limitsSummaryWithoutDroppingAnOversizedLatestEntry() {
        properties.setMaxSummaryCharacters(30);
        BasicMemoryCompressor compressor = new BasicMemoryCompressor(properties);

        String summary = compressor.compress(
                null,
                List.of(message(AgentMessageRole.USER, "a".repeat(100)))
        );

        assertThat(summary).isNotEmpty().hasSizeLessThanOrEqualTo(30);
    }

    /**
     * 创建不包含工具元数据的普通消息。
     */
    private AgentMessage message(AgentMessageRole role, String content) {
        return new AgentMessage(role, content, null, null);
    }
}
