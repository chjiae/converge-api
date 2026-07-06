# 阶段 02｜AI 控制面：供应商、连接元数据与公开模型目录

## 1. 阶段定位

本阶段在现有 `converge-web-service` Spring MVC 控制面中，新增未来统一 AI 网关需要的第一批**非敏感、非运行时**配置对象：

```text
Tenant
├─ AiProvider
│  └─ AiUpstreamConnection
└─ AiPublicModel
```

它解决的是“租户可在控制面中登记哪些上游供应商、哪些连接地址、以及对下游准备公开哪些模型名”。

本阶段**不是** API 中转阶段，不保存 API Key、OAuth Token、Refresh Token、Cookie 或任何其他密钥；也不向 `converge-gateway` 下发任何配置。

阶段 01 的 `converge-gateway` 应已通过验收，保持独立运行骨架状态。本阶段不修改网关模块。

> **租户兼容性强制约束**：实施前必须阅读同目录的 `TENANT-COMPATIBILITY-REVIEW.md`。本阶段完全复用现有 `tenant`、`BaseEntity.tenantId`、`TenantContext`、MyBatis-Plus 租户拦截、`TENANT_OWNER` / `TENANT_ADMIN` / `TENANT_MEMBER`、审计和权限体系；不得新建 Workspace 或其他平行租户模型。所有 AI 控制面 Service 必须显式要求当前租户非空，不能只依赖 MyBatis 租户拦截器。

---

## 2. 本阶段必须完成

### 2.1 新增租户级 AI 控制面对象

新增下列对象、枚举、实体、Mapper、Service、Controller、请求/响应 DTO 和自动化测试：

| 对象 | 作用 | 必须字段 |
|---|---|---|
| `AiProvider` | 一个逻辑上游供应商，例如 OpenAI、Anthropic、Google 或自定义供应商 | `tenantId`、`code`、`displayName`、`providerKind`、`status`、`description` |
| `AiUpstreamConnection` | 该供应商的一个非敏感连接定义；以后 Credential/Account/Resource 会引用它 | `tenantId`、`providerId`、`code`、`displayName`、`protocolType`、`baseUrl`、`status`、`description` |
| `AiPublicModel` | 对下游准备暴露的稳定模型别名；不直接等同于某个上游模型名 | `tenantId`、`code`、`displayName`、`modelFamily`、`status`、`description` |

### 2.2 新增固定枚举

以下枚举必须落在 Java 代码中，而不是先建“可配置协议表”或 JSON 自由文本：

```text
AiProviderKind:
OPENAI, ANTHROPIC, GOOGLE, XAI, CUSTOM

AiProtocolType:
OPENAI_COMPATIBLE, ANTHROPIC_MESSAGES, GEMINI_GENERATIVE_AI

AiCatalogStatus:
ENABLED, DISABLED
```

若实际代码已有等价枚举，可复用或调整命名；不要重复定义语义相同的枚举。

### 2.3 数据库与租户隔离

1. 新增一条**新的、不可修改既有迁移**的 Flyway SQL 迁移。迁移版本必须根据仓库当前最大版本自动选择，不能在本计划中写死 `V6`。
2. 不新增 Workspace、Organization、AI Tenant 或任何平行租户表；三张 AI 表均属于现有 `tenant`。
3. 创建/更新 DTO 不得出现可写 `tenantId`；租户归属只能由当前已认证的 `TenantContext` 在 Service 层确定。
4. 每个 AI 控制面 Service 必须显式执行 `requireCurrentTenantId()` 或项目中等价的非空租户断言；不能只依赖 MyBatis-Plus 自动租户过滤。
5. 新增表建议命名：

```text
ai_provider
ai_upstream_connection
ai_public_model
```

6. 三张表都必须有 `tenant_id NOT NULL`，且不加入 `ConvergeTenantLineHandler.IGNORE_TABLES`。
7. 所有表必须有 `created_at`、`updated_at`，并建立适合租户过滤和列表查询的索引。
8. 推荐唯一约束：

```text
ai_provider:            UNIQUE (tenant_id, code)
ai_upstream_connection: UNIQUE (tenant_id, provider_id, code)
ai_public_model:        UNIQUE (tenant_id, code)
```

9. `AiUpstreamConnection.providerId` 的引用必须由服务层在当前租户上下文内校验。不得通过“忽略租户过滤”实现跨租户关联；优先通过 `(tenant_id, provider_id)` 组合外键进一步约束同租户归属。
10. 本阶段不实现删除接口。对象通过 `DISABLED` 停用，避免在后续 Credential、Resource 和 Route 引用建立后产生破坏性删除问题。

### 2.4 Base URL 的最小安全校验

`AiUpstreamConnection.baseUrl` 是非秘密配置，但仍必须在控制面校验：

- 仅允许绝对 `http` 或 `https` URL；
- 必须包含 host；
- 禁止 URL 内嵌用户名、密码；
- 禁止 query 和 fragment；
- 允许路径，例如 `/v1`；
- 统一规范化末尾斜杠，避免同一地址重复表示；
- 本阶段不做网络连通性测试、不做 DNS 解析、不做 SSRF 探测，因为尚未有网关实际发请求。

### 2.5 控制面 REST API

新增统一根路径：

```text
/api/v1/ai
```

至少提供以下管理接口：

```text
# 供应商
POST   /api/v1/ai/providers
GET    /api/v1/ai/providers?page=&size=&keyword=&status=
GET    /api/v1/ai/providers/{id}
PUT    /api/v1/ai/providers/{id}
POST   /api/v1/ai/providers/{id}/enable
POST   /api/v1/ai/providers/{id}/disable

# 上游连接（归属供应商）
POST   /api/v1/ai/providers/{providerId}/connections
GET    /api/v1/ai/providers/{providerId}/connections?page=&size=&keyword=&status=
GET    /api/v1/ai/connections/{id}
PUT    /api/v1/ai/connections/{id}
POST   /api/v1/ai/connections/{id}/enable
POST   /api/v1/ai/connections/{id}/disable

# 公开模型目录
POST   /api/v1/ai/models
GET    /api/v1/ai/models?page=&size=&keyword=&status=
GET    /api/v1/ai/models/{id}
PUT    /api/v1/ai/models/{id}
POST   /api/v1/ai/models/{id}/enable
POST   /api/v1/ai/models/{id}/disable
```

API 风格应复用现有 `Result<T>`、`PageResult<T>`、参数校验、异常处理和审计切面；不得单独发明第二套响应结构。

### 2.6 权限与审计边界

本阶段这些对象全部是**租户私有控制面数据**：

- 允许角色：复用现有 `TENANT_OWNER`、`TENANT_ADMIN`；不得创建新的 AI 专属角色；
- 禁止现有 `TENANT_MEMBER` 管理；
- 每个 Service 入口必须显式拒绝 `TenantContext` 中没有租户 ID 的调用；
- 不为 `SUPER_ADMIN` 增加“无租户上下文查看所有租户 AI 配置”的隐式后门；该能力应在未来平台治理阶段通过显式 `tenantId`、审计和只读/紧急操作边界单独设计；
- 所有创建、更新、启用、停用操作必须接入已有 `@Auditable`；
- 审计日志只能记录对象 ID、编码、显示名称和状态，禁止预埋任何秘密字段。

### 2.7 后端自动化测试

复用项目已有 Spring Boot 集成测试与 Testcontainers 基础，至少验证：

1. 未认证请求被拒绝；
2. `TENANT_MEMBER` 无管理权限；
3. `TENANT_OWNER` / `TENANT_ADMIN` 可管理本租户对象；
4. 租户 A 不能读取或修改租户 B 的 Provider、Connection、PublicModel；
5. `TenantContext` 中无 tenantId 的 Service 调用明确被拒绝，不得退化为跨租户查询；
6. 相同 `code` 在同一租户被拒绝；在不同租户可存在；
7. Connection 必须关联当前租户下存在的 Provider；
8. Base URL 非法、含用户信息、含 query/fragment 时被拒绝；合法 `https://host/path/` 被规范化；
9. 停用对象后列表、详情和状态变更行为符合既有项目约定；
10. 现有租户、认证、支付、订阅、卡密等测试不回归。

---

## 3. 明确不做

以下事项全部属于后续阶段，禁止在本阶段顺带实现：

```text
不创建 converge-contract
不改 converge-gateway
不向网关下发配置或快照
不接 Redis Pub/Sub 或 Redis 快照
不接 PostgreSQL 以外的新存储
不保存 API Key、OAuth Token、Refresh Token、Cookie、账户密码、企业授权信息
不创建 Credential、AuthorizedAccount、ExecutionResource、ResourcePool、RoutePolicy、SessionBinding
不创建 PublicModel 到上游模型的映射
不创建模型定价、余额、用量、计费、下游 API Key
不新增 /v1/* API
不实现 API 转发、上游 HTTP Client、SSE、WebSocket、协议转换、健康探测或失败重试
不修改 React 前端
不为 SUPER_ADMIN 实现跨租户 AI 数据管理
```

---

## 4. 为什么本阶段这样拆分

### 4.1 对照 New-API

New-API 的 `Channel` 将上游地址、上游认证、模型映射、分组、优先级、权重、状态等放在高聚合对象中，并在其上提供模型网关、加权路由和失败重试能力。

本项目不照抄 `Channel`：先把供应商、连接、公开模型这三类不会携带秘密、不会参与实时调度的配置独立出来。后续才会引入 Credential、资源池、模型绑定和路由规则。

**结论：不偏离。** 保留 New-API 的“公开模型与上游连接配置”需求，但避免在 Java 控制面先形成巨型 Channel。

### 4.2 对照 Sub2API

Sub2API 的优势是 OAuth/API Key 多账户、粘性会话、账户并发、RPM/TPM、账户限流/过载状态与调度。它的 `Account` 是一个高价值且高风险对象。

本阶段故意不创建 Account 或任何秘密字段。首先建立账户未来必须依赖的 Provider 与 Connection 元数据，随后才在单独阶段设计授权主体、凭据加密与资源调度状态。

**结论：不偏离。** 先创建资源目录的上游边界，再引入账户资源，避免提前设计错误的 OAuth/API Key 统一表。

### 4.3 对照现有 Converge API

现有项目已有多租户、JWT、Spring Security、MyBatis-Plus 租户拦截、Flyway、审计切面、统一 `Result` 与 Testcontainers 集成测试。本阶段应复用它们，不新建平行框架。

`TenantContext` 仅用于 Spring MVC 控制面。本阶段不让 Vert.x 网关读取该 `ThreadLocal`，也不让网关直连 PostgreSQL。

---

## 5. 实施工作包

Codex 必须一次性完成本阶段全部工作包；这些工作包是阶段内部执行顺序，不是要求分多次回复。

### WP-01：现有约定审查与领域落点确认

1. 阅读阶段 01 实施结果，确认 `converge-gateway` 已独立存在且本阶段不需要修改它。
2. 阅读现有实体、Mapper、Service、Controller、DTO、审计、租户拦截和集成测试基类。
3. 以现有项目包结构为准确定义 AI 领域包路径，例如：

```text
com.github.chjiae.service.entity.ai
com.github.chjiae.service.mapper.ai
com.github.chjiae.service.service.ai
com.github.chjiae.service.controller.ai
com.github.chjiae.service.dto.ai
```

4. 不进行大范围包重构；若现有项目没有按领域拆包，则选取最小兼容位置。

### WP-02：Flyway、枚举、实体与 Mapper

1. 新增迁移，创建三张表、约束、索引、表/列注释。
2. 新增三个状态/类型枚举，放置位置遵循既有 `converge-common` 枚举约定。
3. 新增实体并继承 `BaseEntity`；必须映射 `tenant_id`、时间字段。
4. 新增 Mapper；如需自定义 SQL，放到 XML，不写 SQL 注解。
5. 不修改 `IGNORE_TABLES`，不使用全局忽略租户过滤。

### WP-03：Provider 控制面 CRUD

1. 完成 Provider 请求 DTO、响应 DTO、Service、Controller。
2. 对 `code`、名称、类型、状态、描述做 Bean Validation。
3. 同租户 code 冲突返回与现有项目一致的业务错误。
4. 只提供创建、查询、更新、启用、停用；不提供删除。
5. 所有写操作加审计。

### WP-04：Connection 控制面 CRUD 与 Base URL 规范化

1. 完成 Connection 请求 DTO、响应 DTO、Service、Controller。
2. 创建和更新时确保 `providerId` 属于当前租户。
3. 在独立可测的 URL 规范化组件中实现本阶段要求的校验；不可把复杂 URI 逻辑塞进 Controller。
4. 不做真实网络连接测试。
5. 只提供创建、查询、更新、启用、停用；不提供删除。

### WP-05：PublicModel 控制面 CRUD

1. 完成 PublicModel 请求 DTO、响应 DTO、Service、Controller。
2. `code` 是下游稳定别名，不得要求等于上游模型名称。
3. 本阶段不建立 Provider 或 Connection 关联。
4. 只提供创建、查询、更新、启用、停用；不提供删除。

### WP-06：权限、审计、集成测试与文档

1. 为所有 AI 控制面接口建立明确的租户管理员权限保护。
2. 验证超管无租户上下文时不会意外读取所有租户 AI 数据。
3. 完成前述全部集成测试。
4. 在 `docs/ai-gateway/progress/phase-02-control-plane-catalog-result.md` 记录实际表结构、接口、测试命令、偏差与风险。
5. 更新本阶段验收清单。
6. 按根 `AGENTS.md` 要求创建中文 Git 提交。

---

## 6. 建议文件范围

实际文件名以现有代码为准，以下仅作为最小预期：

```text
修改：backend/converge-web-service/src/main/resources/db/migration/V{next}__ai_control_plane_catalog.sql
新增：backend/converge-common/src/main/java/.../enums/AiProviderKind.java
新增：backend/converge-common/src/main/java/.../enums/AiProtocolType.java
新增：backend/converge-common/src/main/java/.../enums/AiCatalogStatus.java
新增：backend/converge-web-service/src/main/java/.../entity/ai/AiProvider.java
新增：backend/converge-web-service/src/main/java/.../entity/ai/AiUpstreamConnection.java
新增：backend/converge-web-service/src/main/java/.../entity/ai/AiPublicModel.java
新增：backend/converge-web-service/src/main/java/.../mapper/ai/*Mapper.java
新增：backend/converge-web-service/src/main/resources/mapper/ai/*.xml（如有自定义 SQL）
新增：backend/converge-web-service/src/main/java/.../dto/ai/*
新增：backend/converge-web-service/src/main/java/.../service/ai/*
新增：backend/converge-web-service/src/main/java/.../controller/ai/*
新增：backend/converge-web-service/src/test/java/.../ai/*IntegrationTest.java
新增：docs/ai-gateway/progress/phase-02-control-plane-catalog-result.md
```

---

## 7. 验收标准

本阶段完成后：

1. `converge-web-service` 通过一条新的 Flyway 迁移创建 AI 控制面目录表；
2. 租户管理员能用现有 JWT 管理自己租户的 Provider、Connection 和 PublicModel；
3. 任何接口、数据库字段、日志、审计日志中都不存在上游秘密；
4. 租户隔离、同租户唯一性、Base URL 校验均有自动化测试；
5. `converge-gateway` 未被修改，不依赖控制面数据库；
6. 所有已有后端回归测试保持通过；
7. 阶段结果和验收清单均已填写；
8. 已创建中文 Git 提交。

---

## 8. 下一阶段输入

本阶段通过后，下一阶段再根据真实表结构讨论：

```text
converge-contract
+ 控制面到网关的版本化只读快照
+ 网关缓存失效信号
```

它将使用本阶段已经确定的 Provider、Connection、PublicModel 元数据；不会重新定义这三类对象。
