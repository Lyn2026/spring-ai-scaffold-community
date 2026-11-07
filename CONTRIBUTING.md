# 贡献指南

感谢你愿意花时间。为了少走弯路，先看这几条。

## 环境

- JDK 17+、Maven 3.9+
- 提交前请保证：

```bash
mvn -B -DskipTests clean package
```

必须通过。改了自动配置或资源文件时用 `clean`，否则 `target/classes` 里的旧文件会被打进包。

## 代码约定

- **不引入 `spring-ai-*` 依赖**，这是本项目的立身之本；也不要为了一个小功能引入重依赖（Lombok、Guava 之类都不必）。
- 模型调用统一走 `AiHttpClient`，不要在业务代码里自己 new `HttpClient`。
- 新增能力优先「接口开源 + 最简实现开源」，高级实现放私有仓库，公开仓的 POM 绝不引用私有坐标。
- 中文注释、UTF-8 编码；文件换行保持 LF。
- 新增配置项必须同时更新 `AiScaffoldProperties` 与 README 的配置表。

## 新增一个模型协议

1. 在 `model/provider/` 下实现 `ChatModel` / `EmbeddingModel`；
2. 在 `ModelFactory` 注册 `type`；
3. 用 `scripts/mock-model-server.py` 或真实端点验证；
4. 补一个能跑通的最小示例，并在 PR 里贴出请求/响应。

## 提交 PR

- 一个 PR 只做一件事，标题写清「修什么 / 加什么」；
- 附带复现步骤或验证方式（命令、截图、日志片段）；
- 会改变行为的改动，请同步更新 `CHANGELOG.md`。

## 报告问题

用 issue 模板，尽量提供：版本、JDK、操作系统、`ai.scaffold` 配置片段（**脱敏后的**）、完整异常栈。不要粘贴真实 API Key。

## License

提交即表示同意以 Apache-2.0 授权你的贡献。
