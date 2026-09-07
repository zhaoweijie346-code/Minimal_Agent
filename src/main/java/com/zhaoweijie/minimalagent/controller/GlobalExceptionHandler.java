package com.zhaoweijie.minimalagent.controller;

import com.zhaoweijie.minimalagent.controller.dto.ErrorResponse;
import com.zhaoweijie.minimalagent.exception.InvalidLlmOutputException;
import com.zhaoweijie.minimalagent.exception.LlmApiException;
import com.zhaoweijie.minimalagent.exception.LlmClientException;
import com.zhaoweijie.minimalagent.exception.LlmErrorType;
import com.zhaoweijie.minimalagent.exception.LlmTimeoutException;
import com.zhaoweijie.minimalagent.exception.MaxAgentRoundsException;
import com.zhaoweijie.minimalagent.exception.SessionAccessDeniedException;
import com.zhaoweijie.minimalagent.exception.SessionNotFoundException;
import com.zhaoweijie.minimalagent.exception.TraceNotFoundException;
import com.zhaoweijie.minimalagent.exception.ToolArgumentException;
import com.zhaoweijie.minimalagent.exception.ToolNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * 将 REST 层异常转换为统一、安全且不包含 StackTrace 的错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 返回请求体字段级 Bean Validation 错误。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    /**
     * 返回查询参数和方法参数的 Bean Validation 错误。
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request parameter validation failed",
                request
        );
    }

    /**
     * 返回缺少参数或无法解析 JSON 请求体的错误。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Malformed request", request);
    }

    /**
     * 将 Session 和 Trace 不存在映射为 404。
     */
    @ExceptionHandler({SessionNotFoundException.class, TraceNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * 将未注册工具映射为 404；正常 Agent Loop 内该异常会先转换成 ToolResult。
     */
    @ExceptionHandler(ToolNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleToolNotFound(
            ToolNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", exception.getMessage(), request);
    }

    /**
     * 将跨用户 Session 访问映射为 403。
     */
    @ExceptionHandler(SessionAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            SessionAccessDeniedException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", exception.getMessage(), request);
    }

    /**
     * 将 Agent 最大轮数耗尽映射为不可处理请求。
     */
    @ExceptionHandler(MaxAgentRoundsException.class)
    public ResponseEntity<ErrorResponse> handleMaxRounds(
            MaxAgentRoundsException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "MAX_ROUNDS_EXCEEDED",
                exception.getMessage(),
                request
        );
    }

    /**
     * 将工具参数错误映射为 400，不回显可能由模型生成的原始 arguments。
     */
    @ExceptionHandler(ToolArgumentException.class)
    public ResponseEntity<ErrorResponse> handleToolArgument(
            ToolArgumentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "TOOL_ARGUMENT_ERROR",
                "Invalid tool arguments",
                request
        );
    }

    /**
     * 将非法或空 LLM 输出映射为 502，并隐藏原始上游响应。
     */
    @ExceptionHandler(InvalidLlmOutputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLlmOutput(
            InvalidLlmOutputException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_GATEWAY,
                "INVALID_LLM_OUTPUT",
                "Upstream LLM returned an invalid response",
                request
        );
    }

    /**
     * 将 LLM 连接或读取超时映射为 504。
     */
    @ExceptionHandler(LlmTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleLlmTimeout(
            LlmTimeoutException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.GATEWAY_TIMEOUT,
                "LLM_TIMEOUT",
                "Upstream LLM request timed out",
                request
        );
    }

    /**
     * 将百炼限流映射为 503，其他 LLM API 错误映射为 502。
     */
    @ExceptionHandler(LlmApiException.class)
    public ResponseEntity<ErrorResponse> handleLlmApi(
            LlmApiException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = exception.getErrorType() == LlmErrorType.RATE_LIMIT
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return response(status, "LLM_API_ERROR", "Upstream LLM request failed", request);
    }

    /**
     * 兼容尚未迁移的通用 LLM Client 异常，同样不回显异常详情。
     */
    @ExceptionHandler(LlmClientException.class)
    public ResponseEntity<ErrorResponse> handleLlmClient(
            LlmClientException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_GATEWAY,
                "LLM_ERROR",
                "Upstream LLM processing failed",
                request
        );
    }

    /**
     * 将业务参数错误映射为 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid request", request);
    }

    /**
     * 对未预期异常只返回固定消息，避免 StackTrace 和内部实现泄露。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Internal server error",
                request
        );
    }

    /**
     * 构建所有错误处理器共用的响应结构。
     */
    private ResponseEntity<ErrorResponse> response(
            HttpStatus status,
            String error,
            String message,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
