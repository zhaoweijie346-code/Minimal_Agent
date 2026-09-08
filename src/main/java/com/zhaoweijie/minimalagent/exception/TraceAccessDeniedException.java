package com.zhaoweijie.minimalagent.exception;

/**
 * 用户尝试读取其他用户 Trace 时抛出的异常。
 */
public class TraceAccessDeniedException extends RuntimeException {

    /** 被拒绝访问的 Trace 标识。 */
    private final String traceId;

    /** 发起访问的用户标识。 */
    private final String userId;

    /**
     * 创建 Trace 越权访问异常。
     *
     * @param traceId 被拒绝访问的 Trace 标识
     * @param userId  发起访问的用户标识
     */
    public TraceAccessDeniedException(String traceId, String userId) {
        super("User " + userId + " cannot access trace " + traceId);
        this.traceId = traceId;
        this.userId = userId;
    }

    /** 获取被拒绝访问的 Trace 标识。 */
    public String getTraceId() {
        return traceId;
    }

    /** 获取发起访问的用户标识。 */
    public String getUserId() {
        return userId;
    }
}
