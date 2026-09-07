package com.zhaoweijie.minimalagent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhaoweijie.minimalagent.action.ToolCallAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMessageTests {

    @Test
    void storesToolCallMetadata() {
        var toolCalls = new ArrayList<>(List.of(new ToolCallAction(
                "call-1",
                "calculator",
                new ObjectMapper().createObjectNode().put("expression", "1 + 2")
        )));

        var message = new AgentMessage(AgentMessageRole.ASSISTANT, null, null, toolCalls);
        toolCalls.clear();

        assertThat(message.toolCalls()).hasSize(1);
        assertThatThrownBy(() -> message.toolCalls().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void usesEmptyToolCallsWhenMetadataIsAbsent() {
        var message = new AgentMessage(AgentMessageRole.TOOL, "3", "call-1", null);

        assertThat(message.toolCallId()).isEqualTo("call-1");
        assertThat(message.toolCalls()).isEmpty();
    }
}
