package com.zhaoweijie.minimalagent.controller;

import com.zhaoweijie.minimalagent.controller.dto.TraceResponse;
import com.zhaoweijie.minimalagent.trace.AgentTraceRecorder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 按请求级 traceId 查询 Agent 行为链的 REST Controller。
 */
@RestController
@RequestMapping("/api/traces")
public class TraceController {

    /** 第一版内存 Trace 记录器。 */
    private final AgentTraceRecorder traceRecorder;

    /** Trace 领域模型到 HTTP DTO 的映射器。 */
    private final ApiDtoMapper dtoMapper;

    public TraceController(AgentTraceRecorder traceRecorder, ApiDtoMapper dtoMapper) {
        this.traceRecorder = traceRecorder;
        this.dtoMapper = dtoMapper;
    }

    /**
     * 返回指定请求的完整 Trace，不包含请求 Header 或密钥。
     */
    @GetMapping("/{traceId}")
    public TraceResponse getTrace(@PathVariable String traceId) {
        return dtoMapper.toTrace(traceRecorder.getTrace(traceId));
    }
}
