package com.zhaoweijie.minimalagent.exception;

/**
 * 请求的 Session 不存在时抛出的异常。
 */
public class SessionNotFoundException extends RuntimeException {

    /** 未找到的 Session 标识。 */
    private final String sessionId;

    /**
     * 创建 Session 不存在异常。
     *
     * @param sessionId 未找到的 Session 标识
     */
    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
        this.sessionId = sessionId;
    }

    /**
     * 获取未找到的 Session 标识。
     *
     * @return Session 标识
     */
    public String getSessionId() {
        return sessionId;
    }
}
