package com.zhaoweijie.minimalagent.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentActionTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void representsToolCallAction() {
        var arguments = objectMapper.createObjectNode().put("expression", "1 + 2");
        AgentAction action = new ToolCallAction("call-1", "calculator", arguments);

        assertThat(action).isInstanceOf(ToolCallAction.class);
        var toolCall = (ToolCallAction) action;
        assertThat(toolCall.toolCallId()).isEqualTo("call-1");
        assertThat(toolCall.toolName()).isEqualTo("calculator");
        assertThat(toolCall.arguments()).isEqualTo(arguments);
    }

    @Test
    void representsFinalAnswerAction() {
        AgentAction action = new FinalAnswerAction("done");

        assertThat(action).isEqualTo(new FinalAnswerAction("done"));
    }
}
