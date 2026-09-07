package com.zhaoweijie.minimalagent.exception;

/**
 * LLM 返回空响应、非法 JSON 或不符合协议的消息结构时抛出的异常。
 */
public class InvalidLlmOutputException extends LlmClientException {

    /**
     * 创建 LLM 输出异常。
     *
     * @param errorType EMPTY_RESPONSE 或 INVALID_RESPONSE 分类
     * @param message   安全错误说明
     */
    public InvalidLlmOutputException(LlmErrorType errorType, String message) {
        super(errorType, message);
    }

    /**
     * 创建包含底层 JSON 解析原因的 LLM 输出异常。
     */
    public InvalidLlmOutputException(
            LlmErrorType errorType,
            String message,
            Throwable cause
    ) {
        super(errorType, message, cause);
    }
}
