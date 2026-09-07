package com.zhaoweijie.minimalagent.exception;

/**
 * LLM 调用失败的供应商无关错误分类。
 */
public enum LlmErrorType {
    /** LLM 客户端配置缺失或非法。 */
    CONFIGURATION,
    /** API Key 无效或没有访问权限。 */
    AUTHENTICATION,
    /** Provider 请求频率超限。 */
    RATE_LIMIT,
    /** Provider 服务端错误。 */
    SERVER_ERROR,
    /** HTTP 连接或读取超时。 */
    TIMEOUT,
    /** 连接失败等非超时网络错误。 */
    CONNECTION_ERROR,
    /** Provider 返回其他 HTTP 错误。 */
    HTTP_ERROR,
    /** Provider 没有返回可用 assistant 消息。 */
    EMPTY_RESPONSE,
    /** Provider 响应不是合法的预期 JSON 结构。 */
    INVALID_RESPONSE,
    /** tool_calls 中的 function.arguments 不是合法 JSON 对象。 */
    INVALID_ARGUMENTS
}
