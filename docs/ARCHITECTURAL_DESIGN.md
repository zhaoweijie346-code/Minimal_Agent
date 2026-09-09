# Minimal Agent 架构设计

> 本文记录 Minimal Agent 从单机内存版 MVP 向生产级分布式 Agent Runtime 演进时的架构设计。
> 文中的 Redis、关系数据库、Vector DB、持久化 Checkpoint 和 Tool 幂等机制属于后续设计方案，
> 不代表当前仓库已经实现这些能力。

## 1. Agent 后端的 Redis 设计

### 1.1 Redis 定位的变化

在传统后端架构中，Redis 通常位于数据库之前，主要承担高频数据缓存、分布式锁、Session 和限流等
职责。它的核心价值往往是减少数据库访问、降低响应时间和吸收瞬时流量。

Agent 系统的业务流程与传统同步接口不同。一次 Agent 请求可能包含多轮：

```text
用户输入
  -> LLM 推理
  -> Tool Call
  -> Tool Result
  -> 再次调用 LLM
  -> 更多 Tool Call
  -> Final Answer
```

这个过程具有执行时间长、步骤多、可能中断、需要恢复以及存在外部副作用等特点。因此，Redis 的定位
不应停留在单纯的 Cache，而应提升为 **Agent Runtime 的分布式运行时状态层**。

### 1.2 Redis 的四类职责

| 角色 | 保存内容 | 主要目的 |
|---|---|---|
| Cache | 高频业务查询结果、模型配置、工具元数据 | 减少数据库或外部服务访问 |
| Memory | Agent Session、Working Memory、近期消息和上下文变量 | 支持多轮会话和不同实例之间的状态共享 |
| State | Agent Run、Step、Checkpoint 和中间 Tool Result | 支持长任务状态跟踪、断点恢复和故障转移 |
| Coordination | Tool 幂等记录、分布式 Lease、限流计数和事件流 | 协调多个 Runtime 实例并控制重复执行 |

#### Cache：传统业务缓存

Redis 继续承担传统缓存职责，例如缓存外部搜索结果、模型路由配置、工具描述和高频业务数据。缓存
数据应允许过期或重建，不能成为关键业务事实的唯一副本。

#### Memory：会话工作记忆

Redis 可以保存短期 Session 状态、Working Memory、近期结构化消息和 Context 变量，使同一个
Session 的后续请求不必固定路由到某一台应用实例。

需要保存的不只是文本，还包括 Function Calling 的结构化关系：

```text
USER
-> ASSISTANT(tool_calls, tool_call_id)
-> TOOL(tool_call_id, ToolResult)
-> ASSISTANT(final)
```

摘要可以作为会话记忆的一部分，但仍应被视为由历史用户内容派生的不可信数据，不能提升为高权限
System 指令。

#### State：Run、Step 与 Checkpoint

对于长生命周期 Agent 任务，需要保存：

- Agent Run 的当前状态；
- 已完成和正在执行的 Step；
- 每个 Step 的输入、输出和错误；
- 已确认的 Tool Result；
- 可恢复的 Checkpoint；
- 当前轮次、开始时间和最后更新时间。

当 Runtime 实例重启或发生故障时，新实例可以从最近一个可信 Checkpoint 恢复，而不是从第一轮重新
执行整个 Agent Loop。

#### Coordination：分布式协调

Redis 还可以承担多个 Runtime 实例之间的协调职责，包括：

- Tool 调用幂等记录；
- Agent Run 或 Session 的分布式 Lease；
- 用户、模型和工具维度的限流；
- 任务心跳与租约续期；
- 基于 Stream 的步骤事件和异步处理；
- 短期去重与请求状态查询。

Lease 应带过期时间并由持有者续期，不能创建永久锁。锁或 Lease 只能解决并发协调问题，不能替代
业务数据库事务和幂等约束。

### 1.3 Redis 与持久化数据库的边界

Redis 保存 Agent 的“运行时状态”，数据库保存系统的“事实状态”。推荐的数据边界如下：

| 数据 | 推荐存储 | 原因 |
|---|---|---|
| 短期 Session、Working Memory | Redis | 高频读写、需要跨实例共享、允许配置 TTL |
| Run、Step、Checkpoint 热状态 | Redis | 需要低延迟更新和故障恢复 |
| Tool 幂等记录、Lease、限流 | Redis 或 Redis + DB | 需要原子操作和分布式协调 |
| 长期 Conversation | MySQL/PostgreSQL | 需要可靠持久化、查询和数据治理 |
| 订单、支付、Todo 等业务事实 | MySQL/PostgreSQL | 需要事务、唯一约束和审计能力 |
| 长期语义 Memory | Vector DB，并保留事实来源 | 需要按语义检索，不能只依赖短期上下文 |
| 审计日志 | 数据库、日志平台或对象存储 | 需要长期保留和不可抵赖性 |

Redis 不能因为性能高就成为所有数据的唯一存储。对于订单、支付、权限和审计等强持久数据，最终
一致性依据仍应来自关系数据库或专门的持久化系统。

### 1.4 建议的运行链路

```text
Client
  -> Agent API
  -> 获取 Session/Run Lease
  -> 从 Redis 读取 Session、Working Memory 和 Checkpoint
  -> 执行 LLM + Tool Loop
  -> 每个 Step 保存状态和 Tool Result
  -> 更新 Checkpoint
  -> Final Answer
  -> 异步归档长期 Conversation、Trace 和审计数据到持久化存储
```

Redis 中的状态需要设置明确的 TTL、容量上限和淘汰策略。正在运行的 Run、幂等记录和未完成
Checkpoint 不能与普通 Cache 使用相同的随意淘汰策略，否则可能破坏恢复和去重能力。

### 1.5 设计总结

Redis 在 Agent 系统中不再只是“为了少查几次数据库”，而是承担：

```text
Cache + Memory + State + Coordination
```

同时必须坚持一个边界：Redis 负责运行效率和分布式协作，关系数据库、Vector DB 和审计系统负责
长期事实、事务和可追溯性。

## 2. Agent 的幂等性与重试设计

### 2.1 为什么 Tool 不能在异常后直接重试

Agent 的 Tool Retry 不能简单理解为“捕获异常后再次调用”。尤其对于下单、支付、发消息、创建 Todo
等有副作用操作，timeout 只能说明调用方没有在规定时间内拿到确定结果，不能证明下游没有执行成功。

典型风险如下：

```text
Runtime -> 下单 Tool
下游已经创建订单
下游响应在网络中丢失
Runtime 等待超时
Runtime 直接重试
下游再次创建订单
```

因此，timeout 应优先视为执行结果未知，而不是直接视为执行失败。

### 2.2 在 Tool Registry 中声明幂等能力

每个 Tool 应把幂等语义作为结构化元数据注册到 Tool Registry，而不是让 Runtime 根据工具名称写
`if/else`。可以将工具分为：

| 幂等类型 | 含义 | 重试策略 |
|---|---|---|
| `NATURAL_IDEMPOTENT` | 操作天然幂等，例如查询或读取 | 在次数、退避和总 deadline 范围内自动重试 |
| `IDEMPOTENT_WITH_KEY` | 提供稳定幂等键时可以安全重试 | 所有 retry 必须复用同一个 idempotencyKey |
| `NON_IDEMPOTENT` | 无法证明重复执行不会产生新副作用 | 不盲目重试，先查询业务状态或转人工处理 |

示意元数据：

```text
ToolDefinition
  - name
  - description
  - parameters
  - idempotencyType
  - timeout
  - retryPolicy
```

Prompt 可以帮助模型选择工具，但是否允许重试必须由 Runtime 根据结构化元数据判断，不能依赖模型
自行保证安全。

### 2.3 稳定的 Tool Call 与幂等键

每一次逻辑 Tool Call 都应生成稳定的：

```text
toolCallId / idempotencyKey
```

同一次逻辑调用的所有 retry 必须复用同一个 key：

```text
第一次执行：idempotencyKey=order-req-001
第一次超时：结果 UNKNOWN
状态确认或重试：仍使用 idempotencyKey=order-req-001
```

不能在每次技术重试时生成新 key，否则下游会把它识别为新的业务操作。

`tool_call_id` 可以关联 LLM 的 assistant tool_calls 与 tool result；业务幂等键则用于保证下游副作用
只发生一次。简单场景可以复用或组合二者，生产系统中应明确它们的唯一性范围和生命周期。

### 2.4 真正的幂等保证应位于下游

Agent Runtime 可以记录已经见过的调用，但仅靠 JVM 内存去重无法覆盖：

- Runtime 进程重启；
- 多实例并发；
- 网络超时后重新投递；
- Tool 已成功但 Runtime 尚未保存结果时崩溃；
- 消息队列重复消费。

真正的幂等保证应由下游业务服务实现，例如：

- 数据库唯一键；
- 带事务的幂等请求表；
- Redis 原子占位配合数据库事实校验；
- 按业务请求号查询并返回第一次执行结果。

对于同一个幂等键，下游应保存参数摘要和第一次执行结果。相同 key、相同参数的重试返回历史结果；
相同 key、不同参数则应报告幂等冲突，不能静默复用。

### 2.5 ToolExecution 状态机

Agent Runtime 需要持久化 ToolExecution 状态：

| 状态 | 含义 | 后续处理 |
|---|---|---|
| `RUNNING` | 调用已开始，尚未获得确定结果 | 通过 Lease、心跳或超时检测判断执行者状态 |
| `SUCCESS` | 已获得确定的成功结果 | 直接复用已保存 Tool Result，不再执行副作用 |
| `FAILED` | 已获得确定的失败结果 | 根据错误类型决定是否允许使用相同 key 重试 |
| `UNKNOWN` | 无法判断下游是否执行成功，例如 timeout | 优先查询业务状态，不能直接当作失败重放 |

关键状态转换示意：

```text
准备执行
  -> RUNNING
       -> SUCCESS：保存并复用 Tool Result
       -> FAILED：记录确定错误
       -> UNKNOWN：超时、连接中断或执行者失联

UNKNOWN
  -> 查询下游发现已成功 -> SUCCESS
  -> 查询下游确认未执行 -> 按原 key 重试
  -> 无法确认           -> 人工处理或补偿流程
```

`UNKNOWN` 是有副作用 Tool 最重要的状态之一。把所有 timeout 都标记为 `FAILED`，会诱导 Runtime 重放
一个可能已经成功的操作。

### 2.6 不同 Tool 的重试决策

| 场景 | 是否自动重试 | 处理方式 |
|---|---|---|
| 查询天气、查询订单 | 可以有限重试 | 指数退避、抖动、最大次数和总 deadline |
| 使用幂等键创建 Todo 或订单 | 可以谨慎重试 | 必须复用相同 key，并返回第一次结果 |
| 支付、发送不可撤回通知且下游不支持幂等 | 不允许盲目重试 | 查询业务状态、补偿或转人工处理 |
| 参数校验失败 | 不重试原请求 | 修正参数后作为新的逻辑调用执行 |
| 429 或部分 5xx | 根据策略有限重试 | 读取 Retry-After，执行退避并受总预算限制 |
| timeout | 先进入 `UNKNOWN` | 查询状态后再决定，不直接判定失败 |

自动重试还需要同时受以下边界约束：

- 最大重试次数；
- 指数退避和随机抖动；
- 单次 Tool timeout；
- 整个 Agent Run 的 deadline；
- 用户、模型和工具维度的限流；
- Token、费用和 Tool 调用次数预算。

### 2.7 持久化 Agent Step 与 Tool Result

Runtime 不应只保存最终回答，还要持久化每个 Agent Step 和已经确认的 Tool Result。例如：

```text
Step 1：LLM 返回 create_order tool_call
Step 2：ToolExecution(order-req-001) = SUCCESS
Step 3：保存 Tool Result 和业务 orderId
Step 4：带原 tool_call_id 继续调用 LLM
```

如果 Runtime 在 Step 3 之后崩溃，恢复时应读取 Checkpoint 和已保存结果，将原来的 Tool Result 重新
放回消息链，而不是让 LLM 再次生成并执行同一个下单操作。

这要求恢复逻辑以持久化 Step 为依据：

```text
已完成 ToolExecution
  -> 复用结果并继续下一 Step

RUNNING 或 UNKNOWN
  -> 获取执行权并查询下游状态

尚未执行
  -> 按原 idempotencyKey 开始执行
```

### 2.8 与 durable workflow 的关系

当 Runtime 能够持久化 Run、Step、Checkpoint、ToolExecution 状态和 Tool Result，并在故障后从最近
可信步骤恢复时，它本质上已经具备轻量级 durable workflow 的特征：

- 每一步都有稳定身份；
- 已完成步骤不会因整个 Loop 重放而重复产生副作用；
- 未知状态可以查询、补偿或转人工；
- 多实例通过 Lease 和持久化状态协调执行权；
- 恢复依据是已提交状态，而不是重新询问 LLM 猜测发生过什么。

这不意味着必须立即引入大型 Workflow 框架。可以先在当前自研 AgentRuntime 中建立明确的状态机、
幂等协议和 Checkpoint 边界，再根据任务规模决定是否引入专门的持久化编排系统。

### 2.9 设计总结

Agent 的可靠重试不是简单的异常重试，而是以下能力的组合：

```text
稳定的逻辑调用 ID
+ Tool 幂等元数据
+ 下游唯一约束或幂等记录
+ ToolExecution 状态机
+ Step、Result 和 Checkpoint 持久化
+ 有界重试、状态查询和人工处理
```

最终原则是：只有能够证明安全的操作才自动重试；timeout 先视为 `UNKNOWN`；有副作用操作的最终
幂等性由业务事实存储保证；Agent Runtime 通过持久化步骤避免在恢复过程中重复执行已经完成的工具。
