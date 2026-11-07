# Changelog

## v1.0.0 (2026-11-07)

首个开源版本，范围为「基础可用」：

### Features

- 统一模型层：`ChatModel` / `EmbeddingModel` / `AiHttpClient` / `ModelRegistry`，零 `spring-ai-*` 依赖
- 协议适配：OpenAI 兼容、DashScope 兼容模式、DashScope 原生协议、Ollama
- 会话记忆：内存 / Redis / JDBC 三种实现，按 `tenant + user + conversationId` 隔离
- 知识库：txt/md/pdf 解析 → 切分 → 批量 Embedding → 入库（内存 / PgVector / Redis）→ 混合检索
- 对话接口：`POST /chat`（含工具循环）与 `POST /chat/stream`（SSE）
- `@AiTool` 注解注册函数调用，内置 5 个示例工具
- 租户透传（`X-Tenant-Id` / `X-User-Id` / `X-Roles`）、关键词审核、本地限流、审计与用量统计、Micrometer 指标
- 可运行 demo：单页前端 + 示例文档 + 本地 mock 模型脚本

### Fixes carried over

- Spring Boot 3.5.3 的 `@ConditionalOnProperty.havingValue()` 只接受单值，`jdbc|auto` 拆成两个 Bean
- 审计表 `error_code` 长度 256，写入前截断 200 字符
- SSE 失败分支改为发 `error` 事件后 `complete()`，不再二次派发
- 审计按天统计改在 Java 侧聚合，避免 `CAST(... AS DATE)` 在 H2 / PostgreSQL / MySQL 语义不一致
- 非法 JSON 请求返回 400 而非 500
- 请求体 `model` 一律取供应商真实模型名，避免 `model_not_found`

### Known gaps

- 无重排（仅 `NoopReranker`）、无提示词版本管理、无 API Key/RBAC、无计费、无 Agent/MCP、无熔断降级
- 流式链路不支持工具调用
