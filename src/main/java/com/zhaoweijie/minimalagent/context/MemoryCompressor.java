package com.zhaoweijie.minimalagent.context;

import java.util.List;

/**
 * 将移出近期窗口的 Session 消息压缩为摘要的协议。
 */
public interface MemoryCompressor {

    /**
     * 合并已有摘要和本次被压缩的老消息。
     *
     * @param existingSummary Session 已有摘要
     * @param olderMessages   本次移出近期窗口的结构化消息
     * @return 更新后的摘要
     */
    String compress(String existingSummary, List<AgentMessage> olderMessages);
}
