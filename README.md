# Minimal Agent

Minimal Agent 是一个从零实现的最小可用 AI Agent。项目使用 Java 21、Spring Boot
3.5.16 和 Maven，不依赖 LangGraph、OpenHands、OpenClaw、LangChain4j Agent、
Spring AI Agent 或百炼智能体编排。

LLM Provider 明确为：**Alibaba Cloud Model Studio（阿里云百炼）+ Qwen（千问）+
OpenAI Compatible Chat Completions API + 原生 Function Calling**。

百炼在本项目中只负责 LLM 推理和 Function Calling 决策。以下能力均由本项目自行实现：

- `AgentRuntime` 与 Agent Loop
- `ToolRegistry`、工具定义和工具执行
- Session 隔离与结构化消息历史
- Context 构建、Memory 召回与基础压缩
- 请求级 Trace
- REST API 和统一异常处理

## 技术栈

- Java 21
- Spring Boot 3.5.16
- Maven
- Jackson
- Jakarta Bean Validation
- JUnit 5 / Mockito
- SLF4J / Logback
- 阿里云百炼 Qwen OpenAI Compatible API

项目不使用数据库或 Redis。Session、Todo 和 Trace 当前都保存在 JVM 内存中，应用重启后会丢失。

## 核心架构

一次需要工具的请求会经过以下链路：

```text
User
  → AgentRuntime
  → SessionManager 保存 user message
  → ContextManager 召回 Session Memory
  → ToolRegistry 动态生成 API tools
  → BailianLlmClient 调用 Qwen
  → assistant.tool_calls
  → AgentRuntime 按名称从 ToolRegistry 获取 AgentTool
  → AgentTool.execute(context, arguments)
  → ToolResult
  → 保存 role=tool + tool_call_id
  → 再次调用 Qwen
  → 继续工具循环或返回 Final Answer
```

Qwen 返回普通 `assistant.content` 且没有 `tool_calls` 时，Runtime 将其作为最终回答。
Qwen 返回 `tool_calls` 时，Runtime 会先保存完整的 assistant 工具调用消息，再执行工具并保存
对应的 tool 消息。下一轮请求始终保留：

```text
user → assistant(tool_calls) → tool(tool_call_id) → assistant
```

`tool_call_id` 不会丢失，也不会把工具结果伪装成新的 user message。工具执行失败同样会转换为
失败的 `ToolResult` 回传给 Qwen，由模型决定重试、换工具或向用户说明失败。

默认最多执行 8 轮，超过限制会抛出 `MaxAgentRoundsException`。

## Function Calling 与动态 Tool Schema

所有工具都实现统一接口：

```java
public interface AgentTool {
    String name();
    String description();
    JsonNode parameterSchema();
    ToolResult execute(ToolExecutionContext context, JsonNode arguments);
}
```

Spring 会将全部 `AgentTool` Bean 注入 `ToolRegistry`。注册表以工具名称建立索引，重复名称会导致
启动失败，未知名称会产生工具失败结果。`AgentRuntime` 中没有针对 calculator、search 或 todo 的
`if/else` 路由。

每次调用 LLM 时，`ToolDefinitionProvider` 都会从当前 `ToolRegistry` 动态生成
OpenAI Compatible `tools:function`：

```json
{
  "type": "function",
  "function": {
    "name": "calculator",
    "description": "执行精确数学表达式计算",
    "parameters": {
      "type": "object",
      "properties": {
        "expression": {
          "type": "string"
        }
      },
      "required": ["expression"]
    }
  }
}
```

Schema 不会硬编码在 `BailianLlmClient` 中，也不会重复拼入 System Prompt。新增一个
`AgentTool` Bean 后，其名称、描述和参数 Schema 会通过同一机制进入百炼请求。

## 已实现工具

### calculator

精确计算包含整数、小数、`+`、`-`、`*`、`/` 和括号的数学表达式。实现使用专用表达式解析器，
不使用 `eval` 或任意代码执行，并处理非法表达式、缺少参数和除零。

```json
{
  "expression": "(25+15)*3"
}
```

### search

第一版为 Mock Search，使用独立的 Mock 数据源进行简单关键词匹配。目前包含 Java 21、
Spring Boot、AI Agent、Dubbo 和 Nacos 等数据；没有匹配项时返回空的 `results` 数组。

```json
{
  "query": "Java 21"
}
```

### todo

管理当前用户、当前 Session 下的待办，支持 `add`、`list`、`complete` 和 `delete`。
Todo 使用线程安全内存仓库，并以 `userId + sessionId` 作为隔离边界。

```json
{
  "action": "add",
  "content": "周五写周报"
}
```

完成或删除待办时使用 `todoId`：

```json
{
  "action": "complete",
  "todoId": "待办 ID"
}
```

## User 与 Session

`User != Session`。

- `userId` 表示逻辑用户标识。
- `sessionId` 表示一段独立对话。
- 同一个用户可以拥有多个 Session。
- 不同 Session 的消息、摘要和 Todo 互不可见。
- 即使 `userId` 相同，也不能跨 Session 读取或修改 Todo。
- 使用其他 `userId` 读取已有 Session 会被拒绝，REST API 返回 403。

当前项目没有实现登录、Token 校验或完整身份认证。调用方提交的 `userId` 仅用于 Session
所有权校验和内存数据隔离，生产环境需要在可信认证层中提供它。

调用 `/api/agent/chat` 时，`sessionId` 可以为空，此时 Runtime 会创建新 Session，并在响应中
返回生成的 ID。后续追问必须同时携带相同的 `userId` 和 `sessionId`。

## Context 与 Memory

本项目中的 Session Memory 定义为：

```text
summary + recent messages + tool calls + tool results
```

### 何时召回

`AgentRuntime` 每一轮调用百炼 Qwen 之前都会调用 `ContextManager`。
`ContextManager` 再通过 `SessionMemoryManager` 召回当前用户、当前 Session 的 Memory。
因此第一次模型调用、工具执行后的下一轮调用以及后续用户追问都会重新读取最新状态。

### 存放位置

Memory 保存在 `AgentSession` 中：

- `summary` 保存已压缩的较老历史。
- `messages` 保存近期结构化消息。
- assistant 消息中的 `toolCalls` 保存 Function Call。
- tool 消息中的 `toolCallId` 和 JSON content 保存工具结果关联关系。

`AgentSession` 当前由 `InMemorySessionManager` 使用线程安全 Map 保存，没有数据库持久化。

### 如何进入百炼 messages

构建请求时，消息按以下顺序进入 OpenAI Compatible `messages`：

1. `src/main/resources/prompts/agent-system.txt` 中的 System Prompt。
2. 非空 Session Summary，作为带“不可信历史数据”边界的 assistant message，避免历史用户或工具内容被提升为 system 指令。
3. 截断后的近期 user、assistant 和 tool 结构化消息。
4. 需要时附加当前 tool result。

当消息数量超过 `agent.context.compression-threshold` 时，较老消息由
`BasicMemoryCompressor` 压缩到 Summary，最近消息保留结构化角色和工具调用关系，并按字符预算裁剪正文。压缩重点保留用户目标、重要事实、
助手结论、待完成事项和关键工具结果；它不会保存或要求模型输出完整 Chain-of-Thought。
消息窗口会避免从中间切断 assistant tool call 与对应 tool result 的结构化链路。
除消息数量窗口外，Runtime 还限制用户输入、单条 Tool Result、单条历史消息和整次 Context 的字符数。
同一 Session 的完整 Agent Loop 会串行执行，避免并发请求交叉覆盖消息链；不同 Session 可并发运行。

### 为什么 Todo 不全部放入 Context

Todo 是可变的 Session 状态，不是每轮推理都需要的对话文本。如果每次都把全部 Todo 放入
Prompt，会持续占用上下文窗口、增加 Token 消耗，并可能让模型读取到过期状态。

因此 Context 默认不注入完整 Todo 列表。模型需要待办状态时调用 `todo` 的 `list` 动作，
TodoTool 再按当前 `userId + sessionId` 从仓库读取最新状态。这样既保持 Session 隔离，也只在
真正需要时消耗上下文空间。

## Trace

每次 `/api/agent/chat` 请求都会创建独立 `traceId`。第一版 Trace 保存在内存中，记录：

- `LLM_CALL`
- `TOOL_CALL`
- `TOOL_RESULT`
- `FINAL`
- `ERROR`

事件包含请求用户、Session、轮次、工具名称、参数、结果、`tool_call_id`、耗时和安全错误说明。
Trace 不记录 API Key、Authorization Header 或模型内部 Chain-of-Thought。

## 配置

API Key 必须通过环境变量提供，仓库中没有真实密钥或默认密钥：

```text
DASHSCOPE_API_KEY=你的百炼APIKey
BAILIAN_MODEL=qwen-plus
BAILIAN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

默认配置位于 `src/main/resources/application.yml`：

| 配置 | 环境变量 | 默认值 |
| --- | --- | --- |
| `llm.provider` | — | `bailian` |
| `llm.model` | `BAILIAN_MODEL` | `qwen-plus` |
| `llm.base-url` | `BAILIAN_BASE_URL` | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| `llm.api-key` | `DASHSCOPE_API_KEY` | 空 |
| `llm.connect-timeout` | — | `10s` |
| `llm.read-timeout` | — | `60s` |
| `agent.max-rounds` | `AGENT_MAX_ROUNDS` | `8` |
| `agent.max-user-message-characters` | `AGENT_MAX_USER_MESSAGE_CHARACTERS` | `10000` |
| `agent.max-tool-result-characters` | `AGENT_MAX_TOOL_RESULT_CHARACTERS` | `16000` |
| `agent.context.max-recent-messages` | `AGENT_MAX_RECENT_MESSAGES` | `20` |
| `agent.context.compression-threshold` | `AGENT_COMPRESSION_THRESHOLD` | `40` |
| `agent.context.max-summary-characters` | `AGENT_MAX_SUMMARY_CHARACTERS` | `4000` |
| `agent.context.max-message-characters` | `AGENT_MAX_MESSAGE_CHARACTERS` | `16000` |
| `agent.context.max-context-characters` | `AGENT_MAX_CONTEXT_CHARACTERS` | `64000` |

PowerShell：

```powershell
$env:DASHSCOPE_API_KEY = "你的百炼APIKey"
$env:BAILIAN_MODEL = "qwen-plus"
$env:BAILIAN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
```

Bash：

```bash
export DASHSCOPE_API_KEY="你的百炼APIKey"
export BAILIAN_MODEL="qwen-plus"
export BAILIAN_BASE_URL="https://dashscope.aliyuncs.com/compatible-mode/v1"
```

不要把真实 API Key 写入 `application.yml`、测试源码、`.env` 或提交到 Git。

## 构建与启动

前置条件：JDK 21 和 Maven 3.9+。

```bash
mvn clean test
mvn spring-boot:run
```

应用默认监听 `http://localhost:8080`。首次真实调用前必须设置 `DASHSCOPE_API_KEY`。

## REST API

### 创建 Session

```http
POST /api/sessions
Content-Type: application/json

{
  "userId": "user-a"
}
```

### 与 Agent 对话

使用已有 Session：

```http
POST /api/agent/chat
Content-Type: application/json

{
  "userId": "user-a",
  "sessionId": "已有的 sessionId",
  "message": "计算 (25+15)*3"
}
```

也可以省略或传空 `sessionId`，由 Runtime 自动创建 Session。响应至少包含：

```json
{
  "traceId": "请求 traceId",
  "sessionId": "本次使用的 sessionId",
  "answer": "模型最终回答",
  "rounds": 2
}
```

### 查询 Session 消息

```http
GET /api/sessions/{sessionId}/messages?userId=user-a
```

消息响应保留 role、assistant tool calls 和 tool message 的 `toolCallId`。

### 查询 Trace

```http
GET /api/traces/{traceId}?userId=user-a
```

Trace 查询会验证 Trace 所属 userId。REST 层使用 Bean Validation 和统一错误响应。参数错误返回 400，Session 或 Trace 越权返回 403，
Session 或 Trace 不存在返回 404；响应不会返回 StackTrace、Authorization Header 或 API Key。

## 测试

### 普通测试

```bash
mvn test
```

普通 Runtime 测试使用 `FakeLlmClient`，百炼 Provider 测试使用 Mock HTTP，不会调用真实百炼，
也不会消费 API 配额。

### 真实百炼 E2E / Smoke Test

真实测试默认关闭。只有同时设置 `DASHSCOPE_API_KEY` 并显式激活 `integration` Profile 时才会
调用百炼。

PowerShell：

```powershell
$env:DASHSCOPE_API_KEY = "你的百炼APIKey"
mvn "-Dspring.profiles.active=integration" "-Dtest=AgentRuntimeBailianE2eTests,BailianLlmClientSmokeTests" test
```

Bash：

```bash
export DASHSCOPE_API_KEY="你的百炼APIKey"
mvn -Dspring.profiles.active=integration \
  -Dtest=AgentRuntimeBailianE2eTests,BailianLlmClientSmokeTests test
```

E2E 用例覆盖：直接问候、calculator、search、todo 和
`search → todo → final`。测试主要断言是否完成、工具调用名称、工具结果、调用顺序和 Trace，
不依赖 Qwen 返回固定的自然语言文字。

## 当前实现边界

- Search 是本地 Mock Search，不访问互联网。
- Session、Todo、Memory 和 Trace 只保存在进程内存中。
- REST API 是同步调用。
- 没有用户登录与身份认证层，`userId` 由可信调用方提供。
- Context Compression 是确定性的基础摘要器，不额外调用 LLM。
- 未实现百炼 Agent、Workflow 或任何第三方 Agent Framework。
