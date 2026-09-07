package com.zhaoweijie.minimalagent.exception;

/**
 * Function Calling 工具参数缺失、类型错误或不是合法 JSON 对象时抛出的异常。
 */
public class ToolArgumentException extends LlmClientException {

    /**
     * 创建工具参数异常。
     *
     * @param message 安全错误说明
     */
    public ToolArgumentException(String message) {
        super(LlmErrorType.INVALID_ARGUMENTS, message);
    }

    /**
     * 创建包含底层 JSON 解析原因的工具参数异常。
     */
    public ToolArgumentException(String message, Throwable cause) {
        super(LlmErrorType.INVALID_ARGUMENTS, message, cause);
    }
}
