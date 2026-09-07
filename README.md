# Minimal Agent

一个基于 Java 21、Spring Boot 3.5.16 与阿里云百炼 Qwen 原生 Function Calling
构建的最小 AI Agent。Agent Runtime、工具循环、Session、Context、Memory 与 Trace
均由项目自行实现。

## 运行普通测试

普通测试使用 Fake/Mock LLM 或 Mock HTTP，不会调用真实百炼 API：

```bash
mvn test
```

## 手动运行百炼端到端测试

真实 E2E/Smoke Test 默认关闭，同时满足以下两个条件时才会执行：

1. 环境变量 `DASHSCOPE_API_KEY` 已设置且非空。
2. Maven 命令显式激活 Spring `integration` Profile。

PowerShell：

```powershell
$env:DASHSCOPE_API_KEY = "你的百炼 API Key"
mvn "-Dspring.profiles.active=integration" "-Dtest=AgentRuntimeBailianE2eTests,BailianLlmClientSmokeTests" test
```

Bash：

```bash
export DASHSCOPE_API_KEY="你的百炼 API Key"
mvn -Dspring.profiles.active=integration \
  -Dtest=AgentRuntimeBailianE2eTests,BailianLlmClientSmokeTests test
```

可选配置：

- `BAILIAN_MODEL`：覆盖模型名称，默认 `qwen-plus`。
- `BAILIAN_BASE_URL`：覆盖 OpenAI Compatible API Base URL。

端到端用例覆盖直接问候、calculator、search、todo 以及
`search → todo → final` 多工具循环。断言以工具行为、执行成功状态和 Trace
完整性为主，不依赖模型回答的固定自然语言措辞。

API Key 只能通过环境变量提供；不要写入配置文件、测试源码或提交到 Git。
