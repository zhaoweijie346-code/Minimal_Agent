package com.zhaoweijie.minimalagent.exception;

/**
 * 请求的 Agent Trace 不存在时抛出的异常。
 */
public class TraceNotFoundException extends RuntimeException {

    /** 未找到的请求级 Trace 标识。 */
    private final String traceId;

    /**
     * 创建 Trace 不存在异常。
     *
     * @param traceId 未找到的 Trace 标识
     */
    public TraceNotFoundException(String traceId) {
        super("Trace not found: " + traceId);
        this.traceId = traceId;
    }

    public String getTraceId() {
        return traceId;
    }
}
