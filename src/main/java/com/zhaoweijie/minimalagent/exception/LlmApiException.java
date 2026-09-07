package com.zhaoweijie.minimalagent.exception;

/**
 * 调用百炼等外部 LLM API 失败时抛出的统一上游服务异常。
 */
public class LlmApiException extends LlmClientException {

    /**
     * 创建非 HTTP 状态类的 LLM API 异常。
     */
    public LlmApiException(LlmErrorType errorType, String message, Throwable cause) {
        super(errorType, message, cause);
    }

    /**
     * 创建包含 HTTP 状态码的 LLM API 异常。
     */
    public LlmApiException(
            LlmErrorType errorType,
            Integer statusCode,
            String message,
            Throwable cause
    ) {
        super(errorType, statusCode, message, cause);
    }
}
