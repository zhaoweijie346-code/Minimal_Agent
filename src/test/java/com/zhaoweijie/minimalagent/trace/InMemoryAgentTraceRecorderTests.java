package com.zhaoweijie.minimalagent.trace;

import com.zhaoweijie.minimalagent.exception.TraceAccessDeniedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAgentTraceRecorderTests {

    @Test
    void returnsTraceOnlyToItsOwningUser() {
        InMemoryAgentTraceRecorder recorder = new InMemoryAgentTraceRecorder();
        String traceId = recorder.startTrace("user-a", "session-1");

        assertThat(recorder.getTrace(traceId, "user-a").traceId()).isEqualTo(traceId);
        assertThatThrownBy(() -> recorder.getTrace(traceId, "user-b"))
                .isInstanceOf(TraceAccessDeniedException.class);
    }
}
