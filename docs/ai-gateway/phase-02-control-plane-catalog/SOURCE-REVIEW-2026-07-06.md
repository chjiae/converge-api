# 阶段 02｜实时来源复核（2026-07-06）

本文件记录本阶段生成时参考的当前公开资料与现有项目事实。它不是源码复制依据，也不要求将外部项目的数据模型直接迁入 Converge API。

## 1. New-API

来源：

- <https://github.com/QuantumNous/new-api/blob/main/README.md>
- <https://github.com/QuantumNous/new-api/blob/main/README.en.md>

当前 README 明确列出：

- OpenAI Responses、Realtime、Claude Messages、Gemini、Rerank 等多协议能力；
- 渠道加权随机；
- 失败自动重试；
- 用户级模型限流；
- 用量/缓存计费。

### 对本阶段的结论

New-API 的产品已经将“上游渠道、模型能力、路由、转发、计费”整合为完整网关能力。Converge API 本阶段只先建立 Provider、Connection、PublicModel 三类目录对象：

- Provider ≠ New-API Channel；
- Connection ≠ Credential；
- PublicModel ≠ 上游真实模型名；
- 本阶段不创建模型映射、权重、优先级、路由、重试或计费。

这是一种有意的拆分：避免把 New-API 的 Channel 聚合模型直接复刻为 Java 巨型实体。

## 2. Sub2API

来源：

- <https://github.com/Wei-Shaw/sub2api>

当前 README 描述：

- 多上游账户类型（OAuth、API Key）；
- 账户智能调度和粘性会话；
- 用户和账户并发限制；
- 请求与 Token 限流；
- API Key 分发、精细计费、管理后台。

### 对本阶段的结论

Sub2API 的核心复杂度在账户授权状态、额度、并发、冷却、会话粘性和调度。本阶段不会创建 Account、OAuth、API Key 或任何密钥字段。

本阶段只创建它们未来所依赖的上游目录边界：Provider 与 Connection。后续再设计 `AuthorizedAccount`、`Credential` 与 `ExecutionResource`，从而不把授权/秘密/运行状态与基础元数据混在一张表。

## 3. Vert.x

来源：

- <https://vertx.io/docs/vertx-core/java/>
- <https://vertx.io/docs/vertx-web/java/>

Vert.x 文档说明 HTTP Server/Client 支持优雅关闭，并且 HTTP 运行时适合独立数据面。阶段 02 不改网关、不向网关传输任何配置，因此不会让 Spring MVC 的 `ThreadLocal` 租户上下文泄漏到 Vert.x 事件循环。

## 4. 现有 Converge API 代码事实

本阶段基于用户提供的 `converge-api.zip` 审查到：

- 根 Maven 当前包含 `converge-common` 与 `converge-web-service`；阶段 01 预计新增 `converge-gateway`；
- 控制面是 Spring Boot MVC、Spring Security、MyBatis-Plus、PostgreSQL、Flyway、Redis；
- `BaseEntity` 已包含 id、tenantId、createdAt、updatedAt；
- `ConvergeTenantLineHandler` 对带 tenant_id 的表自动加入行级过滤；
- 当前 `TenantContext` 是 Servlet ThreadLocal，只限控制面；
- 现有控制面接口使用 `Result<T>`、`PageResult<T>`、`BusinessException` 和 `@Auditable`；
- 当前迁移文件最高可见版本为 V5，实际实施前必须以仓库当前版本为准，不能写死下一版本。

## 5. 核验结论

阶段 02 同时满足：

1. 不偏离 New-API 所需的上游配置与公开模型概念；
2. 不过早复制 Sub2API 的账户资源调度；
3. 复用 Converge API 已有租户、认证、审计与迁移机制；
4. 不违反阶段 01 “独立 Vert.x 网关不直连控制面数据库”的边界；
5. 为下一阶段的 `converge-contract` 和只读快照建立真实、稳定的输入对象。

## 6. 本次复核新增的租户安全结论

当前 `ConvergeTenantLineHandler` 在 `TenantContext.getTenantId() == null` 时会跳过租户条件注入。该行为对平台级既有功能有意义，但对新建的 AI 租户私有控制面对象不能单独依赖。

因此阶段 02 增加了强制性规则：

- 所有 AI 控制面 Service 显式要求当前 tenantId 非空；
- 请求 DTO 不接受可写 tenantId；
- 创建时由服务端写入 tenantId；
- Connection 只能引用当前租户的 Provider；
- SUPER_ADMIN 不增加无租户上下文的隐式跨租户入口。

该补强完全复用现有租户体系，避免新 AI 表因空租户上下文而出现跨租户读写。
