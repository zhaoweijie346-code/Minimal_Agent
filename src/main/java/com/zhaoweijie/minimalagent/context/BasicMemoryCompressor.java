package com.zhaoweijie.minimalagent.context;

import com.zhaoweijie.minimalagent.action.ToolCallAction;
import com.zhaoweijie.minimalagent.config.AgentContextProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 不调用 LLM 的确定性基础摘要压缩器。
 */
@Component
public class BasicMemoryCompressor implements MemoryCompressor {

    /** 单条消息写入摘要时允许保留的最大字符数。 */
    private static final int MAX_ENTRY_CHARACTERS = 500;

    /** 摘要长度配置。 */
    private final AgentContextProperties properties;

    /**
     * 创建基础摘要压缩器。
     *
     * @param properties 上下文与摘要配置
     */
    public BasicMemoryCompressor(AgentContextProperties properties) {
        this.properties = properties;
    }

    @Override
    public String compress(String existingSummary, List<AgentMessage> olderMessages) {
        List<String> entries = new ArrayList<>();
        if (existingSummary != null && !existingSummary.isBlank()) {
            entries.add(normalize(existingSummary));
        }

        // 仅摘要用户可见消息和结构化工具信息，不读取或保存模型内部推理过程。
        for (AgentMessage message : olderMessages) {
            appendMessageEntries(entries, message);
        }
        return retainWithinLimit(entries, properties.getMaxSummaryCharacters());
    }

    /**
     * 按角色提取用户目标、重要事实、助手结论与关键工具信息。
     */
    private void appendMessageEntries(List<String> entries, AgentMessage message) {
        switch (message.role()) {
            case USER -> appendContent(entries, "用户目标或重要事实", message.content());
            case ASSISTANT -> {
                appendContent(entries, "助手结论", message.content());
                for (ToolCallAction toolCall : message.toolCalls()) {
                    entries.add(limitEntry(
                            "工具调用: " + toolCall.toolName() + " " + toolCall.arguments()
                    ));
                }
            }
            case TOOL -> appendContent(
                    entries,
                    toolResultLabel(message.content()),
                    message.content()
            );
            case SYSTEM -> appendContent(entries, "历史系统信息", message.content());
        }
    }

    /**
     * 添加非空消息正文，并限制单条内容长度。
     */
    private void appendContent(List<String> entries, String label, String content) {
        if (content != null && !content.isBlank()) {
            entries.add(limitEntry(label + ": " + normalize(content)));
        }
    }

    /**
     * 对包含 Todo 状态的工具结果突出完成或未完成事项信息。
     */
    private String toolResultLabel(String content) {
        if (content != null && (content.contains("PENDING") || content.contains("COMPLETED"))) {
            return "完成或未完成事项及关键工具结果";
        }
        return "关键工具结果";
    }

    /**
     * 将多行内容压成单行，避免摘要结构被原消息格式打散。
     */
    private String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * 限制单条摘要内容，防止一个超长工具结果挤占整个摘要。
     */
    private String limitEntry(String entry) {
        if (entry.length() <= MAX_ENTRY_CHARACTERS) {
            return entry;
        }
        return entry.substring(0, MAX_ENTRY_CHARACTERS - 3) + "...";
    }

    /**
     * 从最新条目向前选择摘要内容，使最新目标和工具结果优先保留。
     */
    private String retainWithinLimit(List<String> entries, int limit) {
        List<String> retained = new ArrayList<>();
        int usedCharacters = 0;
        for (int index = entries.size() - 1; index >= 0; index--) {
            String entry = entries.get(index);
            int separatorCharacters = retained.isEmpty() ? 0 : 1;
            int availableCharacters = limit - usedCharacters - separatorCharacters;
            if (availableCharacters <= 0) {
                break;
            }
            if (entry.length() > availableCharacters) {
                entry = availableCharacters > 3
                        ? entry.substring(0, availableCharacters - 3) + "..."
                        : entry.substring(0, availableCharacters);
            }
            retained.add(0, entry);
            usedCharacters += entry.length() + separatorCharacters;
        }
        return String.join("\n", retained);
    }
}
