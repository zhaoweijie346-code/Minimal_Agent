# AI Prompts

本文严格按照 `Minimal_Agent_Codex_百炼专用_分阶段开发_Prompt.docx` 中第 0–20 条的顺序记录。
原始 Prompt 取自该文档；AI 输出摘要和最终采用方案以当前仓库实现为准。

## Prompt 0：总控 Prompt

### 目标

确立技术栈、职责边界、核心流程、安全要求和分阶段开发方式。

### Prompt

> # **0. 总控 Prompt**
>
> 你现在要帮助我从零实现一个最小可用 AI Agent。
>
> **技术栈**
>
> - Java 21
> - Spring Boot 3.5.16
> - Maven
> - Jackson
> - JUnit 5
> - Mockito
> - SLF4J / Logback
> - 阿里云百炼（Alibaba Cloud Model Studio）
> - Qwen 千问模型
> - 百炼 OpenAI Compatible Chat Completions API
> - 百炼 / Qwen 原生 Function Calling
> **核心限制**
>
> - 不使用 LangGraph、OpenHands、OpenClaw、PI 等现有 Agent Framework。
> - 不使用百炼智能体应用、Agent 编排或 Workflow 实现主流程。
> - 百炼只负责 LLM 推理和 Function Calling 决策。
> - AgentRuntime、Agent Loop、ToolRegistry、Session、Context、Memory、Trace 必须自行实现。
> **核心流程**
>
> ```text
> 用户输入
> → AgentRuntime
> → Session / ContextManager
> → 百炼 Qwen API
> → Qwen 返回普通回答或 tool_calls
> → AgentRuntime 解析
> → ToolRegistry
> → Tool.execute()
> → Tool Result
> → role=tool + tool_call_id
> → 再次调用百炼 Qwen
> → 继续循环或 Final Answer
> ```
>
> **三个工具**
>
> - calculator：精确数学表达式计算。
> - search：外部搜索工具，第一版允许 Mock。
> - todo：当前 Session 内待办管理，支持 add/list/complete/delete。
> **配置与安全**
>
> - API Key 必须通过 DASHSCOPE_API_KEY 环境变量读取。
> - Base URL、模型名称均配置化，不允许硬编码。
> - 不允许把真实 API Key 提交到 GitHub。
> - 默认可使用 qwen-plus，但实际模型名必须允许通过 BAILIAN_MODEL 覆盖。
> **开发原则**
>
> - 不要一次性实现全部功能；严格按照后续阶段逐步开发。
> - 每阶段先分析现有代码，再做最小范围修改。
> - 完成后说明修改文件、关键设计、测试结果。
> - 失败时先定位、修复并重新测试。
> - 保持 MVP 简洁、职责清晰、可测试，不要过度设计。
> 在我发送具体阶段 Prompt 之前，不要直接实现整个项目。

### AI 输出摘要

确定了百炼只负责推理与工具决策，Runtime、Loop、Registry、Session、Context、Memory 和 Trace 均自行实现。

### 最终采用方案

采用 Java 21、Spring Boot、Maven 与百炼 Qwen 原生 Function Calling，Agent 编排全部由仓库代码完成。

### 人工调整

后续补充了实际仓库地址，并要求所有实体字段、业务模块和复杂代码块添加注释。

## Prompt 1：创建 Spring Boot 项目骨架

### 目标

创建可启动、可测试且不含业务逻辑的 Spring Boot 骨架。

### Prompt

> # **1. 创建 Spring Boot 项目骨架**
>
> 请基于当前仓库创建一个最小 Spring Boot 3.5.16 + Java 21 项目骨架。
>
> - 使用 Maven；暂时不要实现具体业务逻辑。
> - 建立 package：controller、runtime、llm、action、tool、session、context、trace、exception、config。
> - 创建 AgentApplication。
> - 添加 Spring Web、Jackson、Validation、Spring Boot Test。
> - 不引入数据库、Redis、任何 Agent Framework。
> - 添加 Spring Boot Context Load Test 并执行 Maven 测试。

### AI 输出摘要

建立 Maven 工程、启动类、指定包结构、基础依赖和 Context Load Test。

### 最终采用方案

使用 Spring Boot 3.5.16 和 Java 21，不引入数据库、Redis 或 Agent Framework。

### 人工调整

无额外人工调整。

## Prompt 2：设计 Agent 核心领域模型

### 目标

定义 Runtime 所需的最小领域对象与结构化消息。

### Prompt

> # **2. 设计 Agent 核心领域模型**
>
> 实现 Agent Runtime 所需的最小领域模型，本阶段不实现 Agent Loop。
>
> - AgentAction 使用 sealed interface，至少包含 ToolCallAction、FinalAnswerAction。
> - ToolCallAction 从一开始预留：toolCallId、toolName、arguments(JsonNode)。
> - FinalAnswerAction：answer。
> - ToolResult：success、toolName、data、error。
> - AgentMessage 支持 SYSTEM、USER、ASSISTANT、TOOL，并允许保存 toolCallId / toolCalls 所需 metadata。
> - AgentSession：sessionId、userId、messages、summary、createdAt、updatedAt。
> - 优先使用 record / enum / 简单 POJO，不使用数据库 Entity。

### AI 输出摘要

实现 sealed AgentAction、ToolCallAction、FinalAnswerAction、ToolResult、AgentMessage 与 AgentSession。

### 最终采用方案

使用 record、enum 和简单 POJO，并保留 toolCallId 与 toolCalls metadata。

### 人工调整

无额外人工调整。

## Prompt 3：实现 Tool 抽象与 ToolRegistry

### 目标

建立统一、动态注册且与具体工具解耦的工具体系。

### Prompt

> # **3. 实现 Tool 抽象与 ToolRegistry**
>
> 实现统一的工具体系。
>
> ```text
> public interface AgentTool {
>     String name();
>     String description();
>     JsonNode parameterSchema();
>     ToolResult execute(ToolExecutionContext context, JsonNode arguments);
> }
> ```
>
> - ToolExecutionContext 包含 userId、sessionId。
> - ToolRegistry 自动注入所有 AgentTool Bean，并使用 Map&lt;String, AgentTool&gt; 注册。
> - ToolRegistry 支持按名称查询和获取全部工具。
> - 重复名称启动失败；Unknown Tool 抛 ToolNotFoundException。
> - Runtime 中禁止根据 calculator/search/todo 写 if/else。

### AI 输出摘要

实现 AgentTool、ToolExecutionContext、ToolRegistry，以及重复名称和未知工具测试。

### 最终采用方案

Spring 注入全部 AgentTool Bean；Runtime 只按名称查询注册表，不写具体工具分支。

### 人工调整

无额外人工调整。

## Prompt 4：实现 CalculatorTool

### 目标

实现安全、精确且不执行任意代码的数学工具。

### Prompt

> # **4. 实现 CalculatorTool**
>
> 实现 calculator 工具，支持 +、-、*、/、()、整数和小数。
>
> ```text
> {
>   "type": "object",
>   "properties": {
>     "expression": {
>       "type": "string",
>       "description": "需要计算的数学表达式"
>     }
>   },
>   "required": ["expression"]
> }
> ```
>
> - 禁止 eval 或任意代码执行。
> - 处理非法表达式、除零、参数缺失。
> - 覆盖 1+1、(1+2)*3、10/4、-5+2、1/0、非法表达式、空参数测试。

### AI 输出摘要

实现表达式解析与正常、负数、小数、括号、非法参数和除零测试。

### 最终采用方案

CalculatorTool 使用专用解析器，不使用 eval 或任意代码执行。

### 人工调整

无额外人工调整。

## Prompt 5：实现 SearchTool

### 目标

实现基于独立 Mock 数据的关键词搜索。

### Prompt

> # **5. 实现 SearchTool**
>
> 实现 search，第一版使用 Mock Search。
>
> ```text
> {
>   "type": "object",
>   "properties": {
>     "query": {
>       "type": "string",
>       "description": "需要查询的问题或关键词"
>     }
>   },
>   "required": ["query"]
> }
> ```
>
> - Mock 数据独立存放，不在 execute 内堆 if/else。
> - 至少包含 Java 21、Spring Boot、AI Agent、Dubbo、Nacos。
> - 支持简单关键词匹配，无结果返回 results=[]。
> - 测试正常查询、无结果、空 query、缺少参数。

### AI 输出摘要

实现 MockSearchData、关键词匹配、空结果和参数校验测试。

### 最终采用方案

SearchTool 查询独立 MockSearchData，无匹配时返回 results=[]。

### 人工调整

无额外人工调整。

## Prompt 6：实现 TodoTool

### 目标

实现按用户和 Session 严格隔离的待办管理。

### Prompt

> # **6. 实现 TodoTool**
>
> 实现 todo，并严格按照 Session 隔离。
>
> - 动作：add、list、complete、delete。
> - TodoItem：id、sessionId、content、status、createdAt、completedAt。
> - TodoStatus：PENDING、COMPLETED。
> ```text
> {
>   "type": "object",
>   "properties": {
>     "action": {"type":"string","enum":["add","list","complete","delete"]},
>     "content": {"type":"string"},
>     "todoId": {"type":"string"}
>   },
>   "required": ["action"]
> }
> ```
>
> - TodoRepository 使用线程安全内存存储。
> - 同一 userId 的不同 Session 也不能互相看到或修改 Todo。
> - 完整覆盖 add/list/complete/delete 与 Session 隔离测试。

### AI 输出摘要

实现 TodoItem、TodoStatus、线程安全仓库及四种动作和隔离测试。

### 最终采用方案

TodoRepository 以 userId 和 sessionId 复合分区，状态保存在 JVM 内存。

### 人工调整

无额外人工调整。

## Prompt 7：实现 SessionManager

### 目标

实现线程安全的 Session 生命周期与访问校验。

### Prompt

> # **7. 实现 SessionManager**
>
> 实现 createSession、getSession、getOrCreate、save/update。
>
> - 第一版使用 InMemorySessionManager + 线程安全 Map。
> - Session 以 sessionId 为主键，并记录 userId。
> - 读取时必须验证 session.userId == request.userId。
> - 添加 SessionNotFoundException、SessionAccessDeniedException。
> - 覆盖同用户多 Session、消息隔离、跨用户拒绝。

### AI 输出摘要

实现 InMemorySessionManager 与创建、读取、更新、多 Session、消息隔离和越权测试。

### 最终采用方案

sessionId 为主键，每次读取和更新均验证 userId，返回存储快照。

### 人工调整

无额外人工调整。

## Prompt 8：实现 ContextManager

### 目标

在每次 Qwen 调用前构建结构化上下文。

### Prompt

> # **8. 实现 ContextManager**
>
> 实现每次调用 Qwen 前的 Agent Context 构建。
>
> - Context 包含 System Prompt、Session Summary、Recent Messages、当前 Tool Result。
> - Tool Definitions 不直接拼入 System Prompt，而是由 ToolRegistry 动态转换为 API tools 字段。
> - maxRecentMessages 可配置，例如 20。
> - 保留 user/assistant/tool_call/tool_result 的结构化消息关系。
> - 不要长期保存完整 Chain-of-Thought。
> - 测试 Session 隔离、消息截断、工具消息链。

### AI 输出摘要

实现 ContextManager、近期消息窗口和完整工具消息链处理。

### 最终采用方案

System Prompt、Summary、Recent Messages 和 Tool Result 结构化组装；Schema 走 API tools。

### 人工调整

无额外人工调整。

## Prompt 9：实现基础 Context Compression / Memory

### 目标

定义并实现最小 Session Memory 与压缩。

### Prompt

> # **9. 实现基础 Context Compression / Memory**
>
> 实现最小 Session Memory 与 Context 压缩。
>
> - Memory = summary + recent messages + tool calls + tool results。
> - Todo 状态不默认全部塞进 Prompt，由 TodoTool 按 sessionId 按需查询。
> - Memory 召回时机：每次 Agent Loop 调用百炼 Qwen API 之前。
> - 消息超阈值后：老消息压缩成 Summary，最近消息原样保留。
> - Summary 重点保存用户目标、重要事实、完成/未完成事项、关键工具结果。
> - 完成后明确说明：memory 何时召回、存在哪里、如何进入 messages、为什么 Todo 不全部进 Context。

### AI 输出摘要

实现 SessionMemoryManager、BasicMemoryCompressor 与压缩测试。

### 最终采用方案

每轮 LLM 前召回；旧消息压缩进 summary，近期工具链原样保留，Todo 按需读取。

### 人工调整

无额外人工调整。

## Prompt 10：实现百炼友好的 LLM Client 抽象

### 目标

定义 Runtime 可依赖的供应商无关 LLM 抽象。

### Prompt

> # **10. 实现百炼友好的 LLM Client 抽象**
>
> 实现 LLM Client 抽象层。项目确定最终使用阿里云百炼 + Qwen + OpenAI Compatible API，但 Runtime 不直接依赖具体供应商。
>
> ```text
> public interface LlmClient {
>     LlmResponse chat(
>         AgentContext context,
>         List<ToolDefinition> tools
>     );
> }
> ```
>
> - ToolDefinition 至少包含 name、description、parameters JSON Schema。
> - Tool Definitions 必须由 ToolRegistry 动态构建，不能在 LlmClient 中硬编码工具。
> - LlmResponse 能表达普通 assistant final response 或一个/多个 tool_calls。
> - 为测试提供 FakeLlmClient / Mockito Mock。
> - API Key 从配置读取，不写入 Git。
> - 本阶段先不进行真实网络调用。

### AI 输出摘要

实现 LlmClient、LlmResponse、ToolDefinition、动态提供器和 FakeLlmClient。

### 最终采用方案

Runtime 只依赖 LlmClient；工具定义由 ToolRegistry 动态转换；普通测试可使用 Fake。

### 人工调整

无额外人工调整。

## Prompt 11：接入阿里云百炼真实 LLM API

### 目标

接入真实百炼 Qwen OpenAI Compatible API。

### Prompt

> # **11. 接入阿里云百炼真实 LLM API**
>
> 基于现有 LlmClient 抽象，实现阿里云百炼真实 Provider，使用 Qwen 与 OpenAI Compatible Chat Completions API。
>
> **Provider**
>
> - 实现 BailianLlmClient 或 QwenLlmClient，二选一，按现有命名风格决定。
> - 禁止使用百炼 Agent/Workflow、LangChain4j Agent、Spring AI Agent 等现成 Runtime。
> **配置**
>
> ```text
> llm:
>   provider: bailian
>   model: ${BAILIAN_MODEL:qwen-plus}
>   base-url: ${BAILIAN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
>   api-key: ${DASHSCOPE_API_KEY:}
>   connect-timeout: 10s
>   read-timeout: 60s
> ```
>
> - Base URL、model、API key 全部配置化。
> - API Key 必须通过 DASHSCOPE_API_KEY 读取，不允许硬编码。
> **Function Calling**
>
> - 将 ToolRegistry 中每个 AgentTool 动态转换为 OpenAI tools:function 结构。
> ```text
> {
>   "type": "function",
>   "function": {
>     "name": "calculator",
>     "description": "执行精确数学表达式计算",
>     "parameters": {
>       "type": "object",
>       "properties": {
>         "expression": {"type": "string"}
>       },
>       "required": ["expression"]
>     }
>   }
> }
> ```
>
> - 不得在 BailianLlmClient 中硬编码 calculator/search/todo。
> **响应与 Tool Result**
>
> - 普通 assistant content → FinalAnswerAction。
> - assistant.tool_calls → ToolCallAction，并提取 tool_call_id、function.name、function.arguments。
> - arguments 使用 Jackson 转为 JsonNode。
> - Tool 执行后必须作为 role=tool 的 message 回传，并关联原 tool_call_id。
> - 不能把 ToolResult 伪装成新的 user message。
> **异常与测试**
>
> - 处理 401、429、5xx、timeout、空响应、JSON 解析异常、非法 arguments。
> - 不要记录 Authorization Header 或 API Key。
> - 普通测试使用 Mock HTTP；真实 smoke test 默认关闭，仅在提供 DASHSCOPE_API_KEY 和指定 profile 时执行。

### AI 输出摘要

实现 BailianLlmClient、配置、HTTP 映射、动态 tools、异常分类及 Mock HTTP 测试。

### 最终采用方案

使用 Spring RestClient 调用 /chat/completions；模型、地址、密钥和超时均配置化。

### 人工调整

后续将真实测试的指定 Profile 统一为 integration。

## Prompt 12：设计百炼 Function Calling System Prompt

### 目标

设计使用原生 Function Calling 的 System Prompt。

### Prompt

> # **12. 设计百炼 Function Calling System Prompt**
>
> 重新设计 System Prompt。当前项目使用百炼/Qwen 原生 Function Calling，因此不要再要求模型通过文本输出自定义 tool_call JSON。
>
> ```text
> You are a minimal AI agent.
> 
> Your job is to help the user complete tasks using the tools provided by the system.
> 
> You may either:
> 1. Answer directly when no tool is required.
> 2. Call one of the provided tools when external execution or information is required.
> 
> Tool usage rules:
> - Use calculator for exact mathematical calculation.
> - Use search when external/mock information is required.
> - Use todo to create, list, complete, or delete todos.
> 
> Never invent a tool result.
> If a tool is required, use the provided function calling mechanism.
> After receiving a tool result, decide whether another tool is required or provide the final answer.
> Do not repeatedly call the same tool without a reason.
> Use session history to understand follow-up questions.
> Todos are session-scoped.
> Do not expose internal chain-of-thought.
> ```
>
> - Prompt 放在 src/main/resources/prompts/agent-system.txt。
> - 不要在 Prompt 中重复完整 Tool JSON Schema。Schema 应走 API tools 字段。
> - 不再强制 reason 字段或完整思考过程。
> - Trace 只记录行为：tool name、arguments、result、final response。

### AI 输出摘要

将提示词放入独立资源文件，不再要求文本 JSON 工具协议或思维过程。

### 最终采用方案

Prompt 只规定行为与工具规则；Schema 由 API tools 提供，不保存 Chain-of-Thought。

### 人工调整

无额外人工调整。

## Prompt 13：实现百炼响应解析层

### 目标

把百炼响应转换为项目领域动作。

### Prompt

> # **13. 实现百炼响应解析层**
>
> 使用原生 Function Calling 后，不再解析自定义文本 JSON。实现 BailianResponseParser 或通用 LlmResponseParser。
>
> - 普通 assistant content 且无 tool_calls → FinalAnswerAction。
> - 存在 tool_calls → ToolCallAction。
> - ToolCallAction 至少包含 toolCallId、toolName、arguments(JsonNode)。
> - 必须保留 tool_call_id，后续 role=tool 消息需要引用它。
> - 处理 choices 为空、message 为空、tool_calls 结构错误、function.name 为空、arguments 非法 JSON、content/tool_calls 均为空。
> - MVP 可先限制每轮执行一个 Tool Call；如支持多个也要保持逻辑清晰，不必为了并行调用过度设计。
> - 添加 Final、calculator/search/todo ToolCall、非法 arguments、空 choices 等测试。

### AI 输出摘要

实现 BailianResponseParser，覆盖 Final、工具调用和非法响应结构。

### 最终采用方案

解析器保留全部 tool_call_id、工具名和 JsonNode arguments，Runtime 可顺序执行。

### 人工调整

无额外人工调整。

## Prompt 14：实现百炼版 AgentRuntime 主循环

### 目标

实现完整的项目自有 Agent Loop。

### Prompt

> # **14. 实现百炼版 AgentRuntime 主循环**
>
> 实现整个项目核心 AgentRuntime。LLM Provider 为百炼 Qwen，使用原生 Function Calling。
>
> ```text
> User Message
> → 保存 Session
> → ContextManager
> → BailianLlmClient
> → Qwen Response
>    ├─ content → Final
>    └─ tool_calls
>        → 保存 assistant(tool_calls)
>        → ToolRegistry
>        → Tool.execute()
>        → 保存 role=tool + tool_call_id
>        → 再次调用 Qwen
>        → 继续循环或 Final
> ```
>
> - AgentRuntime 仍由项目自行实现。
> - 百炼只做推理与工具选择。
> - Runtime 负责 Loop、Tool 执行、Session、Context、Memory、maxRounds、Trace、错误处理。
> - 下一轮请求必须保留 user → assistant(tool_calls) → tool(tool_call_id) 完整消息链。
> - Tool 失败也转换为 ToolResult 回传，让 Qwen 决定重试、换工具或告知用户。
> - agent.max-rounds 默认 8。
> - Runtime 中禁止具体工具 if/else。
> - 测试单工具循环、多工具 search→todo→final、Tool Error、Unknown Tool、最大轮次。

### AI 输出摘要

连接 Session、Context、LLM、工具、Memory、Trace、错误回传和最大轮次。

### 最终采用方案

先保存 user；工具轮保存 assistant(tool_calls) 与 role=tool；最多 8 轮后失败。

### 人工调整

无额外人工调整。

## Prompt 15：实现 Agent Trace

### 目标

记录请求级、可排查且安全的行为 Trace。

### Prompt

> # **15. 实现 Agent Trace**
>
> 实现 Agent Trace，每次请求创建 traceId。
>
> - 记录 traceId、userId、sessionId、round、LLM 调用、是否返回 tool_calls、Tool 名称/参数/结果、duration、error、Final Answer。
> - 保留 tool_call_id 便于排查完整调用链。
> - TraceType：LLM_CALL、TOOL_CALL、TOOL_RESULT、FINAL、ERROR。
> - 第一版内存保存；不记录 API Key、Authorization Header。

### AI 输出摘要

实现 Trace 聚合、事件模型、线程安全内存记录器及 Runtime 记录点。

### 最终采用方案

记录 LLM_CALL、TOOL_CALL、TOOL_RESULT、FINAL、ERROR，并保留调用 ID 和耗时。

### 人工调整

无额外人工调整。

## Prompt 16：实现 REST API

### 目标

提供 DTO 与领域模型分离的 REST API。

### Prompt

> # **16. 实现 REST API**
>
> 实现 REST API，DTO 与领域模型分离。
>
> ```text
> POST /api/sessions
> POST /api/agent/chat
> GET  /api/sessions/{sessionId}/messages?userId=user-a
> GET  /api/traces/{traceId}
> ```
>
> - Bean Validation；统一错误响应；不返回 StackTrace。
> - Chat Response 至少包含 traceId、sessionId、answer。

### AI 输出摘要

实现 Session 创建、Agent 对话、消息查询、Trace 查询和校验测试。

### 最终采用方案

提供四个指定同步端点，Chat 响应包含 traceId、sessionId、answer 和 rounds。

### 人工调整

无额外人工调整。

## Prompt 17：实现统一异常处理

### 目标

统一异常到安全 HTTP 响应的映射。

### Prompt

> # **17. 实现统一异常处理**
>
> 统一整理异常处理。
>
> - 覆盖 ToolNotFoundException、ToolArgumentException、InvalidLlmOutputException、MaxAgentRoundsException、SessionNotFoundException、SessionAccessDeniedException、LlmApiException、LlmTimeoutException。
> - 参数错误 400，Session 越权 403，不存在 404，LLM/Runtime 内部异常使用合适状态码。
> - 不泄露百炼 API Key、Authorization Header、内部 StackTrace。

### AI 输出摘要

实现 GlobalExceptionHandler，覆盖指定领域异常、状态码与敏感信息保护。

### 最终采用方案

400、403、404 与 LLM/Runtime 错误分别映射，响应不暴露栈、Header 或 API Key。

### 人工调整

无额外人工调整。

## Prompt 18：构建 Agent 核心测试用例

### 目标

用 Fake 或 Stub LLM 完整验证 Runtime。

### Prompt

> # **18. 构建 Agent 核心测试用例**
>
> 为 Runtime 建立完整测试，普通测试必须使用 Fake/Stub LlmClient，不真实消费百炼 API。
>
> - 直接回复：你好 → Final，0 次 Tool Call。
> - Calculator：LLM→calculator→LLM→Final。
> - Search：查 Java 21 → search。
> - Todo：记周五写周报 → todo add。
> - 多工具：search→todo→final。
> - 纯聊天追问与带工具追问。
> - Session 隔离、跨用户访问、参数错误、Unknown Tool、最大循环、Context Compression、Tool Exception。
> - 额外断言 tool_call_id 在 assistant/tool 消息链中正确传递。

### AI 输出摘要

补齐直接回答、单工具、多工具、追问、隔离、压缩、错误和调用 ID 测试。

### 最终采用方案

Runtime 普通测试全部使用 FakeLlmClient，不产生真实百炼请求或费用。

### 人工调整

消除了一个依赖相同 createdAt 排序的偶发 Trace 测试断言。

## Prompt 19：添加真实百炼 LLM 端到端测试

### 目标

在显式授权时验证真实百炼端到端链路。

### Prompt

> # **19. 添加真实百炼 LLM 端到端测试**
>
> 添加真实百炼 E2E / Smoke Test，默认关闭。
>
> - 只有 DASHSCOPE_API_KEY 存在且显式开启 integration profile 时运行。
> - 测试：你好；计算 (25+15)*3；查 Java 21；添加 todo；search→todo→final。
> - 不要严格断言自然语言文字，主要检查是否完成、是否调用正确工具、Trace 是否正确。
> - README 中写明手动执行方式。

### AI 输出摘要

增加真实 Runtime E2E 与 Provider Smoke，并使用双重条件默认关闭。

### 最终采用方案

只有 integration Profile 与 DASHSCOPE_API_KEY 同时存在才运行，并检查工具、顺序和 Trace。

### 人工调整

无额外人工调整。

## Prompt 20：编写 README（百炼专用）

### 目标

基于真实实现编写百炼专用 README。

### Prompt

> # **20. 编写 README（百炼专用）**
>
> 基于真实实现编写完整 README.md。
>
> - 明确 LLM Provider：Alibaba Cloud Model Studio / 阿里云百炼 + Qwen + OpenAI Compatible API + Function Calling。
> - 明确百炼只作为 LLM Provider，AgentRuntime、Loop、ToolRegistry、Session、Context、Memory、Trace 自行实现。
> - 说明 calculator/search/todo 与动态 Tool Schema。
> - 解释 User != Session 与 Session 隔离。
> - Memory：定义、召回时机、存放位置、如何进入 messages。
> - 为什么 Todo 状态按需通过工具读取，而不是全部塞进 Context。
> - 说明 Function Calling 链路：ToolRegistry → tools → Qwen tool_calls → Runtime → ToolResult → role=tool + tool_call_id → Qwen。
> ```text
> DASHSCOPE_API_KEY=你的百炼APIKey
> BAILIAN_MODEL=qwen-plus
> BAILIAN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
> ```
>
> - README 不允许填写真实 API Key，不得声称实现未实现能力。

### AI 输出摘要

重写 README，说明架构、工具、隔离、Memory、Trace、配置、API、测试和边界。

### 最终采用方案

README 明确百炼仅是 Provider、Search 为 Mock、状态为内存实现且无身份认证层。

### 人工调整

无额外人工调整。

