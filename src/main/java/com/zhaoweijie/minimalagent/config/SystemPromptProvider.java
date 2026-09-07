package com.zhaoweijie.minimalagent.config;

/**
 * 为 Agent Context 提供系统提示词，隔离提示词的存储方式与上下文构建逻辑。
 */
public interface SystemPromptProvider {

    /**
     * 获取当前完整的 System Prompt。
     *
     * @return 非空系统提示词
     */
    String getSystemPrompt();
}
