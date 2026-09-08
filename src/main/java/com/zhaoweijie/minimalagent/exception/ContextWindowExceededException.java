package com.zhaoweijie.minimalagent.exception;

/**
 * 当前不可拆分的对话轮次超过配置的 Context 字符预算时抛出的异常。
 */
public class ContextWindowExceededException extends RuntimeException {

    /** 创建不回显原始消息内容的 Context 超限异常。 */
    public ContextWindowExceededException() {
        super("Current conversation turn exceeds the configured context limit");
    }
}
