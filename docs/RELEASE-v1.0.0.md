# v1.0.0 — 首个开源版本

一个不依赖 `spring-ai` 官方库的 Spring Boot starter：引入依赖就有「带记忆的对话 + 可用的知识库问答」。

## 这个版本能做什么

- **对话**：`POST /api/ai/chat` 非流式、`POST /api/ai/chat/stream` SSE 流式，支持工具循环
- **记忆**：内存 / Redis / JDBC 三种存储，按租户 + 用户 + 会话隔离，带历史窗口
- **知识库**：`txt/md/pdf` 上传 → 切分 → 批量 Embedding → 入库（内存 / PgVector / Redis）→ 向量 + 关键词混合召回
- **模型层**：OpenAI 兼容、DashScope 兼容模式、DashScope 原生协议、Ollama，四套适配器，`type` 一行切换
- **工具调用**：`@AiTool` + `@ToolParam` 注解注册，内置 5 个示例工具
- **治理**：租户透传、关键词审核、本地限流、审计与 token 统计、Micrometer 指标
- **可运行 demo**：单页前端 + 示例文档 + 本地 mock 模型脚本（无 Key 也能跑通全链路）

## 三分钟跑起来

```bash
$env:DASHSCOPE_API_KEY = "sk-xxx"                       # PowerShell
mvn -B -DskipTests package
java -jar spring-ai-scaffold-demo/target/spring-ai-scaffold-demo-1.0.0.jar
```

浏览器打开 http://localhost:8080 ，新建知识库 → 上传 `docs/samples/售后政策.md` → 提问「退款多久到账？」。

没有 Key：

```bash
python scripts/mock-model-server.py 9999
# 启动参数加：--ai.scaffold.models.dashscope.base-url=http://127.0.0.1:9999
```

## 不包含（企业版保留）

Supervisor Agent、MCP、自动评估、计费、API Key 与 RBAC、提示词版本管理、LLM 重排、熔断与 fallback、管理后台。

## 升级与兼容

首个版本，无历史包袱。Java 17+、Spring Boot 3.5.x。

## 已知限制

流式链路不支持工具调用；默认存储为进程内（重启丢失）；无重排；无鉴权；限流为单机实现。

---

**完整变更见 [CHANGELOG.md](../CHANGELOG.md)。**
