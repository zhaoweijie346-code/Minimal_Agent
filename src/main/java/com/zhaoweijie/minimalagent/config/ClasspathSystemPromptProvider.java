package com.zhaoweijie.minimalagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 文本资源加载百炼 Function Calling System Prompt。
 */
@Component
public class ClasspathSystemPromptProvider implements SystemPromptProvider {

    /** 应用启动时读取并固定的完整系统提示词。 */
    private final String systemPrompt;

    /**
     * 加载配置指定的提示词资源。
     *
     * @param promptResource System Prompt 文本资源
     */
    public ClasspathSystemPromptProvider(
            @Value("${agent.context.system-prompt-resource:classpath:prompts/agent-system.txt}")
            Resource promptResource
    ) {
        try {
            this.systemPrompt = promptResource
                    .getContentAsString(StandardCharsets.UTF_8)
                    .strip();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load agent system prompt", exception);
        }
        if (systemPrompt.isBlank()) {
            throw new IllegalStateException("Agent system prompt must not be blank");
        }
    }

    @Override
    public String getSystemPrompt() {
        return systemPrompt;
    }
}
