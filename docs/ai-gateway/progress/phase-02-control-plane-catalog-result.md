# 阶段 02｜AI 控制面目录实施结果

> 由实施者在阶段完成后填写。不要在实施前将下列项目标记为已完成。

## 1. 实施时间与提交

- 开始时间：2026-07-06 20:58 CST
- 完成时间：2026-07-06 21:36 CST
- Git 提交 Hash：本文件随阶段提交一并提交，最终 Hash 见任务报告。
- 实施分支：master

## 2. 前置阶段核对

- 阶段 01 是否已完成：是。已阅读 `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md`，确认独立 `converge-gateway` 模块已存在。
- 核对到的 `converge-gateway` 状态：网关保持阶段 01 的独立 Vert.x 运行骨架，本阶段仅运行回归测试，未修改其源码、POM 或资源文件。
- 是否修改网关模块：否。

## 3. 实际领域对象与表结构

- Flyway 迁移文件：`backend/converge-web-service/src/main/resources/db/migration/V6__ai_control_plane_catalog.sql`，基于当前最大版本 V5 新增，未修改既有迁移。
- `ai_provider`：租户级 Provider 目录，包含 `tenant_id`、`code`、`display_name`、`provider_kind`、`status`、`description`、`created_at`、`updated_at`。
- `ai_upstream_connection`：租户级上游连接目录，包含 `tenant_id`、`provider_id`、`code`、`display_name`、`protocol_type`、`base_url`、`status`、`description`、`created_at`、`updated_at`。
- `ai_public_model`：租户级公开模型目录，包含 `tenant_id`、`code`、`display_name`、`model_family`、`status`、`description`、`created_at`、`updated_at`；公开模型 `code` 为独立别名，不绑定上游模型名。
- 使用的枚举：`AiProviderKind`、`AiProtocolType`、`AiCatalogStatus`。
- 唯一约束与索引：`ai_provider` 使用 `(tenant_id, code)` 唯一约束与 `(tenant_id, id)` 组合唯一约束；`ai_upstream_connection` 使用 `(tenant_id, provider_id, code)` 唯一约束和 `(tenant_id, provider_id)` 到 Provider 的组合外键；`ai_public_model` 使用 `(tenant_id, code)` 唯一约束。三张表均包含租户、状态、创建时间相关索引，且均未加入租户拦截忽略表。

## 4. 实际接口

- Provider：`POST /api/v1/ai/providers`，`GET /api/v1/ai/providers`，`GET /api/v1/ai/providers/{id}`，`PUT /api/v1/ai/providers/{id}`，`POST /api/v1/ai/providers/{id}/enable`，`POST /api/v1/ai/providers/{id}/disable`。
- Connection：`POST /api/v1/ai/providers/{providerId}/connections`，`GET /api/v1/ai/providers/{providerId}/connections`，`GET /api/v1/ai/connections/{id}`，`PUT /api/v1/ai/connections/{id}`，`POST /api/v1/ai/connections/{id}/enable`，`POST /api/v1/ai/connections/{id}/disable`。
- PublicModel：`POST /api/v1/ai/models`，`GET /api/v1/ai/models`，`GET /api/v1/ai/models/{id}`，`PUT /api/v1/ai/models/{id}`，`POST /api/v1/ai/models/{id}/enable`，`POST /api/v1/ai/models/{id}/disable`。

## 5. 权限、租户与审计

- 允许角色：仅 `TENANT_OWNER`、`TENANT_ADMIN` 可管理当前租户内 AI 目录。
- 禁止角色：未认证用户、`TENANT_MEMBER` 和无租户上下文的 `SUPER_ADMIN` 均不能管理 AI 目录；未新增 AI 专属角色。
- 租户隔离验证：创建/更新 DTO 不包含可写 `tenantId`；三个 Service 均通过 `AiCatalogTenantGuard` 显式拒绝空租户上下文；所有查询条件显式限定当前 `tenantId`，并继续受 MyBatis-Plus 租户拦截保护。
- 审计接入：Provider、Connection、PublicModel 的创建、更新、启用、停用接口均使用现有 `@Auditable`。

## 6. Base URL 规则

- 实际校验组件：`AiBaseUrlNormalizer`。
- 支持规则：仅允许绝对 `http` 或 `https` URL，必须包含 host。
- 拒绝规则：拒绝空值、相对 URL、非 http/https scheme、带用户信息、query 或 fragment 的 URL。
- 规范化规则：scheme 与 host 规范为小写，并保证路径以 `/` 结尾；不做 DNS、网络探测或健康检查。

## 7. 测试与命令

```text
mvn -pl converge-web-service -Dtest=AiCatalogIntegrationTest test
# TDD 红灯：新增测试先失败，缺少 AiCatalogStatus、AI Service 等目标实现。

mvn -pl converge-web-service -am "-Dtest=AiCatalogIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
# 通过：AiCatalogIntegrationTest 8 项，失败 0，错误 0。

mvn test
# 通过：Reactor 全部 SUCCESS；Surefire 报告聚合 106 项，失败 0，错误 0，跳过 0。

git diff --name-only -- backend/converge-gateway
# 无输出，确认未修改网关模块。
```

- 新增测试：`AiCatalogIntegrationTest` 覆盖未认证拒绝、`TENANT_MEMBER` 拒绝、`TENANT_OWNER`/`TENANT_ADMIN` 管理、跨租户隔离、`SUPER_ADMIN` 无租户后门、Provider 重复校验、Connection Provider 归属校验、Base URL 正反例、PublicModel 管理、无 tenantId Service 调用拒绝、无删除接口。
- 回归测试：执行 `backend/mvn test`，`Converge Common`、`Converge Web Service`、`Converge Gateway` 均通过。
- 未执行测试及原因：无。

## 8. 偏差与风险

- 与阶段计划的偏差：无。控制面接口挂载在现有 `/api/v1/ai` 业务前缀下，未新增数据面 `/v1/*`。
- 未完成项：无。
- 已知风险：现有全局异常处理会将不支持的 HTTP 方法记录为通用异常响应；本阶段未改变该既有行为，集成测试仅验证删除接口不会成功。
- 下一阶段输入：后续阶段可在本阶段目录对象基础上继续引入 Credential、路由、下发或网关同步等能力，但本阶段未预留秘密字段或连接外部系统。

## 9. 双项目核验结论

- New-API 对照：本阶段只建立 Provider、Connection、PublicModel 这类控制面目录基础，未实现模型转发、Key、余额、路由策略、上游探测或数据面接口。
- Sub2API 对照：本阶段只提供租户内目录配置能力，未实现订阅转换、共享资源池、Token、SSE、WebSocket 或协议转换。
- 未偏离理由：所有新增能力均限定在租户控制面目录 CRUD/状态管理和安全 URL 规范化内，没有触碰网关运行时、Redis 信号、Credential、计费、资源池或数据面转发。
