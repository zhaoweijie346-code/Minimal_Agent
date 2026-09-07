package com.zhaoweijie.minimalagent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.context.AgentContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientTests {

    @Test
    void fakeClientReturnsFinalAssistantResponse() {
        FakeLlmClient client = new FakeLlmClient();
        LlmResponse response = new LlmResponse("final answer", List.of());
        AgentContext context = new AgentContext("system", null, List.of(), null);
        client.enqueue(response);

        LlmResponse actual = client.chat(context, List.of());

        assertThat(actual).isSameAs(response);
        assertThat(actual.isFinalResponse()).isTrue();
        assertThat(client.calls()).hasSize(1);
    }

    @Test
    void responseSupportsMultipleToolCalls() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCallAction calculator = new ToolCallAction(
                "call-1",
                "calculator",
                objectMapper.createObjectNode().put("expression", "1+1")
        );
        ToolCallAction search = new ToolCallAction(
                "call-2",
                "search",
                objectMapper.createObjectNode().put("query", "Java 21")
        );

        LlmResponse response = new LlmResponse(null, List.of(calculator, search));

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls()).containsExactly(calculator, search);
    }
}
