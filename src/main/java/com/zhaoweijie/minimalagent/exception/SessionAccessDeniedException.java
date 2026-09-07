package com.zhaoweijie.minimalagent.exception;

/**
 * 用户尝试访问其他用户 Session 时抛出的异常。
 */
public class SessionAccessDeniedException extends RuntimeException {

    /** 被拒绝访问的 Session 标识。 */
    private final String sessionId;

    /** 发起访问的用户标识。 */
    private final String userId;

    /**
     * 创建 Session 越权访问异常。
     *
     * @param sessionId 被拒绝访问的 Session 标识
     * @param userId    发起访问的用户标识
     */
    public SessionAccessDeniedException(String sessionId, String userId) {
        super("User " + userId + " cannot access session " + sessionId);
        this.sessionId = sessionId;
        this.userId = userId;
    }

    /**
     * 获取被拒绝访问的 Session 标识。
     *
     * @return Session 标识
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取发起访问的用户标识。
     *
     * @return 用户标识
     */
    public String getUserId() {
        return userId;
    }
}
