# 阶段 01｜实时来源复核记录（2026-07-06）

> 此文件只说明本阶段方案的外部参考依据；实施时仍应以当前仓库代码、`AGENTS.md` 和官方依赖文档为准。

## 1. New-API

- 仓库将自身定位为 LLM Gateway 和 AI Asset Management System，公开列出多协议接口、渠道加权随机、失败重试、用户模型限流、计费等能力。
- `Channel` 模型聚合了上游 Key、Base URL、模型、分组、权重、优先级、模型映射、状态和多 Key 轮询状态。
- 该聚合模型适合其现有 Go 项目，但直接照搬到新 Java 项目会把“连接、凭据、能力、调度状态、模型映射”过早耦合。

### 对本阶段的影响

本阶段只交付独立 HTTP 数据面进程。故意不创建 Channel、模型、路由、重试、计费或 SSE，避免在没有领域模型前复制 New-API 的聚合方式。

来源：

- https://github.com/QuantumNous/new-api
- https://raw.githubusercontent.com/QuantumNous/new-api/main/README.md
- https://raw.githubusercontent.com/QuantumNous/new-api/main/model/channel.go

## 2. Sub2API

- 仓库将自身定位为“订阅配额分发管理”的 AI API 网关平台。
- 其重点不只是 HTTP 转发，而是账户授权态、并发、限流、账户组、会话粘性、账户可调度状态与故障切换。
- 这些能力需要在“真实请求进入网关”与“统一资源模型”具备后才能正确实现。

### 对本阶段的影响

本阶段不创建账户、OAuth、会话粘性、Redis 计数器或资源池。先建立不依赖控制面数据库的 Vert.x 运行时，后续再把资源调度放到正确的领域层。

来源：

- https://github.com/Wei-Shaw/sub2api
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/README_CN.md
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/backend/ent/schema/account.go
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/backend/internal/service/account.go

## 3. Vert.x

- 官方文档强调 Vert.x 事件循环线程不得执行阻塞操作；阻塞工作应离开事件循环。
- Vert.x 提供 HTTP Server、Router、异步关闭以及 JUnit 5 支持，适合作为未来流式 API 中转的数据面基础。
- Maven Central 当前可见 `vertx-stack-depchain` 版本为 5.1.3。

### 对本阶段的影响

网关本阶段只处理轻量级 HTTP 路由、响应、请求 ID 和日志，不接入 JDBC、MyBatis、Redis、Spring 或其他阻塞操作。

来源：

- https://vertx.io/docs/vertx-core/java/
- https://central.sonatype.com/artifact/io.vertx/vertx-stack-depchain/versions

## 4. 本阶段核验结论

- 对 New-API：建立独立网关运行时，为后续协议适配、路由、重试和流式能力提供正确承载，但不复制其巨型 Channel。
- 对 Sub2API：建立将来承载账户资源调度的独立数据面，但不在 HTTP 层提前硬编码账号池。
- 对现有 Converge API：保持 Spring MVC 控制面、MyBatis/Flyway/Redis 既有能力不变；网关不直接依赖其框架或数据库。

结论：阶段 01 范围小、边界清晰，是后续统一 AI 网关的合理起点。
