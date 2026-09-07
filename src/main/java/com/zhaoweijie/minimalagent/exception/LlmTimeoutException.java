package com.zhaoweijie.minimalagent.exception;

/**
 * 外部 LLM API 连接或读取超时时抛出的异常。
 */
public class LlmTimeoutException extends LlmApiException {

    /**
     * 创建 LLM 超时异常。
     */
    public LlmTimeoutException(String message, Throwable cause) {
        super(LlmErrorType.TIMEOUT, message, cause);
    }
}
