package com.zhaoweijie.minimalagent.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathSystemPromptProviderTests {

    @Test
    void loadsFunctionCallingPromptWithoutEmbeddingToolSchemasOrReasoningFormat() {
        ClasspathSystemPromptProvider provider = new ClasspathSystemPromptProvider(
                new ClassPathResource("prompts/agent-system.txt")
        );

        String prompt = provider.getSystemPrompt();

        assertThat(prompt)
                .contains("provided function calling mechanism")
                .contains("Never invent a tool result")
                .contains("Todos are session-scoped")
                .contains("Do not expose internal chain-of-thought")
                .doesNotContain("JSON Schema", "\"type\": \"function\"", "reason field");
    }
}
