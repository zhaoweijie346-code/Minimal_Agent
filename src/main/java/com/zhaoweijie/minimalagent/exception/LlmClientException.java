package com.zhaoweijie.minimalagent.exception;

/**
 * LLM Provider 请求或响应处理失败时抛出的统一异常。
 */
public class LlmClientException extends RuntimeException {

    /** 供应商无关错误分类。 */
    private final LlmErrorType errorType;

    /** HTTP 状态码；非 HTTP 错误时为空。 */
    private final Integer statusCode;

    /**
     * 创建不包含 HTTP 状态码的 LLM 异常。
     *
     * @param errorType 错误分类
     * @param message   安全的错误说明
     */
    public LlmClientException(LlmErrorType errorType, String message) {
        this(errorType, null, message, null);
    }

    /**
     * 创建包含底层原因的 LLM 异常。
     *
     * @param errorType 错误分类
     * @param message   安全的错误说明
     * @param cause     底层异常
     */
    public LlmClientException(LlmErrorType errorType, String message, Throwable cause) {
        this(errorType, null, message, cause);
    }

    /**
     * 创建完整的 LLM 异常。
     *
     * @param errorType 错误分类
     * @param statusCode HTTP 状态码
     * @param message   安全的错误说明
     * @param cause     底层异常
     */
    public LlmClientException(
            LlmErrorType errorType,
            Integer statusCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorType = errorType;
        this.statusCode = statusCode;
    }

    public LlmErrorType getErrorType() {
        return errorType;
    }

    public Integer getStatusCode() {
        return statusCode;
    }
}
