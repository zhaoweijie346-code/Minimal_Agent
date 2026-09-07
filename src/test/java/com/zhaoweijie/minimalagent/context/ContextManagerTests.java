package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import com.zhaoweijie.minimalagent.exception.SessionAccessDeniedException;
import com.zhaoweijie.minimalagent.session.AgentSession;
import com.zhaoweijie.minimalagent.session.InMemorySessionManager;
import com.zhaoweijie.minimalagent.tool.AgentTool;
import com.zhaoweijie.minimalagent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextManagerTests {

    /** 测试使用的内存 Session 管理器。 */
    private InMemorySessionManager sessionManager;

    /** 测试使用的上下文配置。 */
    private AgentContextProperties properties;

    /** 测试使用的 Jackson 对象映射器。 */
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sessionManager = new InMemorySessionManager();
        properties = new AgentContextProperties();
        properties.setSystemPrompt("system instruction");
        objectMapper = new ObjectMapper();
    }

    @Test
    void isolatesContextsBySessionAndUser() {
        AgentSession first = sessionManager.getOrCreate("session-1", "user-1");
        first.getMessages().add(message(AgentMessageRole.USER, "first session"));
        sessionManager.update(first);

        AgentSession second = sessionManager.getOrCreate("session-2", "user-1");
        second.getMessages().add(message(AgentMessageRole.USER, "second session"));
        sessionManager.update(second);

        ContextManager contextManager = contextManager(List.of());
        AgentContext firstContext = contextManager.build("user-1", "session-1");

        assertThat(firstContext.recentMessages())
                .extracting(AgentMessage::content)
                .containsExactly("first session");
        assertThatThrownBy(() -> contextManager.build("other-user", "session-1"))
                .isInstanceOf(SessionAccessDeniedException.class);
    }

    @Test
    void truncatesMessagesToConfiguredRecentWindow() {
        properties.setMaxRecentMessages(3);
        AgentSession session = sessionManager.getOrCreate("session-1", "user-1");
        IntStream.rangeClosed(1, 5)
                .mapToObj(index -> message(AgentMessageRole.USER, "message-" + index))
                .forEach(session.getMessages()::add);
        sessionManager.update(session);

        AgentContext context = contextManager(List.of()).build("user-1", "session-1");

        assertThat(context.recentMessages())
                .extracting(AgentMessage::content)
                .containsExactly("message-3", "message-4", "message-5");
    }

    @Test
    void preservesPersistedToolCallAndResultRelationshipAcrossTruncation() {
        properties.setMaxRecentMessages(1);
        ToolCallAction toolCall = new ToolCallAction(
                "call-1",
                "calculator",
                objectMapper.createObjectNode().put("expression", "1+1")
        );
        AgentSession session = sessionManager.getOrCreate("session-1", "user-1");
        session.getMessages().add(message(AgentMessageRole.USER, "calculate"));
        session.getMessages().add(new AgentMessage(
                AgentMessageRole.ASSISTANT,
                null,
                null,
                List.of(toolCall)
        ));
        session.getMessages().add(new AgentMessage(AgentMessageRole.TOOL, "2", "call-1", null));
        sessionManager.update(session);

        AgentContext context = contextManager(List.of()).build("user-1", "session-1");

        assertThat(context.recentMessages()).hasSize(2);
        assertThat(context.recentMessages().get(0).toolCalls()).containsExactly(toolCall);
        assertThat(context.recentMessages().get(1).toolCallId()).isEqualTo("call-1");
    }

    @Test
    void appendsCurrentToolResultAfterItsAssistantToolCall() {
        properties.setMaxRecentMessages(1);
        ToolCallAction toolCall = new ToolCallAction(
                "call-1",
                "search",
                objectMapper.createObjectNode().put("query", "Java 21")
        );
        AgentSession session = sessionManager.getOrCreate("session-1", "user-1");
        session.setSummary("The user is researching Java.");
        session.getMessages().add(new AgentMessage(
                AgentMessageRole.ASSISTANT,
                null,
                null,
                List.of(toolCall)
        ));
        sessionManager.update(session);
        AgentMessage currentResult = new AgentMessage(
                AgentMessageRole.TOOL,
                "result",
                "call-1",
                null
        );

        AgentContext context = contextManager(List.of()).build(
                "user-1",
                "session-1",
                currentResult
        );

        assertThat(context.sessionSummary()).isEqualTo("The user is researching Java.");
        assertThat(context.messages())
                .extracting(AgentMessage::role)
                .containsExactly(
                        AgentMessageRole.SYSTEM,
                        AgentMessageRole.SYSTEM,
                        AgentMessageRole.ASSISTANT,
                        AgentMessageRole.TOOL
                );
        assertThat(context.messages().get(3).toolCallId()).isEqualTo("call-1");
    }

    @Test
    void buildsApiToolDefinitionsOutsideSystemPrompt() {
        AgentTool tool = mock(AgentTool.class);
        when(tool.name()).thenReturn("sample-tool");
        when(tool.description()).thenReturn("sample description");
        when(tool.parameterSchema()).thenReturn(
                objectMapper.createObjectNode().put("type", "object")
        );
        sessionManager.getOrCreate("session-1", "user-1");

        AgentContext context = contextManager(List.of(tool)).build("user-1", "session-1");

        assertThat(context.systemPrompt()).doesNotContain("sample-tool");
        assertThat(context.toolDefinitions()).hasSize(1);
        assertThat(context.toolDefinitions().get(0).path("type").textValue())
                .isEqualTo("function");
        assertThat(context.toolDefinitions().get(0).path("function").path("name").textValue())
                .isEqualTo("sample-tool");
        assertThat(context.toolDefinitions().get(0).path("function").path("parameters")
                .path("type").textValue()).isEqualTo("object");
    }

    /**
     * 使用指定工具集合创建 ContextManager。
     */
    private ContextManager contextManager(List<AgentTool> tools) {
        return new ContextManager(
                sessionManager,
                new ToolRegistry(tools),
                objectMapper,
                properties
        );
    }

    /**
     * 创建不含工具元数据的普通消息。
     */
    private AgentMessage message(AgentMessageRole role, String content) {
        return new AgentMessage(role, content, null, null);
    }
}
