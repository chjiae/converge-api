# 阶段 03｜上游凭据保险库与 Direct API 可执行资源

## 1. 阶段定位

本阶段在现有 `converge-web-service` 控制面中，新增：

1. **AI 上游凭据保险库**：安全保存一个合法 API Key；
2. **Direct API 可执行资源**：将已存在的 `AiUpstreamConnection` 与 `AiCredential` 组合为未来可被调度器选择的资源。

本阶段结束后，管理员可以在**当前租户**内配置：

```text
AiProvider
  ├─ AiUpstreamConnection
  └─ AiCredential（API_KEY，密文保存）
       └─ AiExecutionResource（DIRECT_API）
```

本阶段不让网关读取这些数据，也不请求任何上游服务。

---

## 2. 前置事实：必须继承，不得重做

已完成阶段 01：

- `converge-gateway` 是独立 Vert.x JVM 进程；
- 网关不得依赖 Spring、MyBatis、Redis、PostgreSQL、JWT、`TenantContext`；
- 本阶段不得修改网关源码、POM、资源文件或内部接口。

已完成阶段 02：

- 已有租户级 `AiProvider`、`AiUpstreamConnection`、`AiPublicModel`；
- 已有 `AiCatalogTenantGuard`，Service 层会拒绝空租户上下文；
- 已有 `AiBaseUrlNormalizer`，上游连接 URL 已完成规范化；
- 管理角色仅为现有 `TENANT_OWNER`、`TENANT_ADMIN`；
- 当前 AI 控制面接口前缀为 `/api/v1/ai`；
- 现有 Flyway 已有 V6，后续迁移必须使用 V7 或更高版本；
- 阶段 02 未接入凭据、资源、路由、网关同步或上游调用。

不得新增第二套租户、组织、用户、角色、审计、异常、分页或数据库迁移机制。

---

## 3. 本阶段目标

### 3.1 `AiCredential`：租户级上游 API Key 凭据

新增 `ai_credential`，本阶段仅支持 `API_KEY`。

建议字段：

| 字段 | 要求 |
|---|---|
| `id`、`tenant_id`、`created_at`、`updated_at` | 复用现有 BaseEntity 和租户规范 |
| `provider_id` | 必填，必须属于当前租户 |
| `code`、`display_name`、`description` | 当前租户/Provider 下可读、可管理 |
| `credential_type` | 本阶段只允许 `API_KEY`，为未来 OAuth 等类型保留枚举扩展点 |
| `admin_status` | `ENABLED` / `DISABLED`；不得混入运行时 429、过载、过期状态 |
| `secret_reference` | 服务端生成的 UUID 或等价稳定随机引用，用于 AAD 绑定；绝不由客户端提供 |
| `encryption_key_id` | 当前数据库加密主密钥的版本标识 |
| `encryption_algorithm` | 固定记录 `AES-256-GCM` |
| `encrypted_secret`、`nonce` | 仅存密文与随机 nonce；不得存明文 |
| `secret_fingerprint` | 基于独立 HMAC 密钥生成的不可逆指纹，用于同租户/同 Provider 去重 |
| `masked_preview` | 仅保存安全掩码，如 `sk-...abcd`；不能通过它恢复秘密 |
| `secret_version`、`rotated_at` | 凭据轮换审计信息 |

约束：

- 同一租户、同一 Provider、同一 `credential_type`、同一 `secret_fingerprint` 只能存在一条凭据；
- 不创建“读取 API Key 明文”的接口；
- 不允许客户端写入 `tenantId`、`encryptedSecret`、`nonce`、`secretFingerprint`、`secretReference`、`encryptionKeyId`；
- 不提供删除接口；使用启用/停用，保留审计历史；
- 旋转凭据时保留 `AiCredential` ID，更新密文、指纹、掩码、版本与轮换时间；
- 不在响应、审计目标、异常消息、日志、测试失败输出中暴露原始 Key。

### 3.2 加密与密钥配置

新增控制面专用的凭据加密组件。不得将其放进未来网关可依赖的公共模块。

要求：

1. 使用 JDK JCA/JCE 的 `AES/GCM/NoPadding`；
2. 每次加密生成新的随机 12 字节 nonce；
3. GCM tag 使用 128 位；
4. 用 AAD 绑定至少这些稳定信息：

```text
tenantId | providerId | secretReference | credentialType | schemaVersion
```

5. 密钥只能由环境变量或外部安全配置提供；不得提供生产默认值；
6. 数据库加密主密钥和 HMAC 指纹密钥必须分开；
7. 测试仅可使用测试专用固定密钥；测试密钥不可写入生产配置；
8. 非测试环境缺少必要密钥时，应在应用启动阶段明确失败，不能悄悄降级为明文或弱加密；
9. 本阶段不实现数据库主密钥轮换，但应保存 `encryption_key_id`，使后续可实现轮换和重加密。

建议环境变量：

```text
AI_CREDENTIAL_ACTIVE_KEY_ID
AI_CREDENTIAL_ACTIVE_KEY_BASE64
AI_CREDENTIAL_FINGERPRINT_KEY_BASE64
```

实现时允许根据项目既有配置风格调整变量名，但不得降低上述安全要求。

### 3.3 `AiExecutionResource`：未来调度的最小静态资源

新增 `ai_execution_resource`。它不是账号池，也不是路由规则；它只是一个未来可执行上游调用的静态绑定。

建议字段：

| 字段 | 要求 |
|---|---|
| `id`、`tenant_id`、审计字段 | 复用现有规范 |
| `provider_id` | 显式保存，用于数据库与 Service 双重一致性校验 |
| `upstream_connection_id` | 关联阶段 02 的连接 |
| `credential_id` | 关联本阶段密文凭据 |
| `resource_type` | 本阶段只能为 `DIRECT_API` |
| `code`、`display_name`、`description` | 管理目录字段 |
| `admin_status` | `ENABLED` / `DISABLED` / `DRAINING` |

强制一致性：

```text
resource.tenant_id
= connection.tenant_id
= credential.tenant_id
= provider.tenant_id

resource.provider_id
= connection.provider_id
= credential.provider_id
```

优先使用 PostgreSQL 复合唯一约束与复合外键保障同租户/同 Provider 关联；Service 层仍必须显式校验，不能只依赖 MyBatis 租户拦截器。

一个 `AiCredential` 可被多个不同连接的 `AiExecutionResource` 引用，以支持未来“同一合法 API Key 绑定多个 URL/区域/代理连接”；同一 `(tenant_id, upstream_connection_id, credential_id)` 不得重复创建资源。

资源绑定一经创建不得修改 `connectionId`、`credentialId` 或 `providerId`。需要改变绑定时，停用或排空旧资源，再新建资源。凭据自身可通过“轮换”保持资源关联不变。

### 3.4 管理 API

遵循阶段 02 的 API 风格、`Result<T>`、`PageResult<T>`、权限控制与 `@Auditable`，具体路径可遵循下列建议：

```text
POST /api/v1/ai/providers/{providerId}/credentials
GET  /api/v1/ai/providers/{providerId}/credentials
GET  /api/v1/ai/credentials/{id}
PUT  /api/v1/ai/credentials/{id}
POST /api/v1/ai/credentials/{id}/rotate
POST /api/v1/ai/credentials/{id}/enable
POST /api/v1/ai/credentials/{id}/disable

POST /api/v1/ai/resources
GET  /api/v1/ai/resources
GET  /api/v1/ai/resources/{id}
PUT  /api/v1/ai/resources/{id}
POST /api/v1/ai/resources/{id}/enable
POST /api/v1/ai/resources/{id}/disable
POST /api/v1/ai/resources/{id}/drain
```

允许实施者为了与现有 Controller 的嵌套风格保持一致而微调路径；不得加入 `/v1/*` 数据面接口。

---

## 4. 本阶段明确不做

```text
不修改 converge-gateway；
不创建 converge-contract；
不创建 ResourcePool、ResourcePoolMember、RoutePolicy、RouteTarget；
不创建 PublicModel 与 Resource 的模型绑定；
不做优先级、权重、并发、RPM、TPM、429 冷却、健康检查或运行时状态；
不请求真实上游，不做“测试连接”；
不实现 OAuth、Refresh Token、Cookie、订阅账号、授权账户；
不创建下游 ClientApiKey、访问分组、套餐映射、倍率、价格、余额、账单；
不新增 /v1/*、SSE、WebSocket、HTTP 转发或协议转换；
不修改 React 前端；
不改造既有租户、认证、订阅、支付、卡密、审计业务。
```

---

## 5. 实施顺序

1. 阅读阶段 01、02 的实施结果及现有 AI 目录代码；
2. 设计并新增 V7（或当前实际最大版本后的连续 Flyway 版本）；
3. 新增凭据相关枚举、实体、Mapper XML、Service、DTO、Controller；
4. 新增控制面专用加密/指纹组件与严格配置校验；
5. 新增可执行资源实体与一致性校验；
6. 接入现有权限、审计、租户守卫、异常响应与分页模式；
7. 新增集成测试、加密单元测试、秘密泄露回归测试；
8. 运行后端全量回归测试；
9. 填写实施结果与验收清单；
10. 创建一个中文 Git 提交。

---

## 6. 验收标准

### 凭据安全

- 数据库中查不到原始 API Key；
- API 响应、审计日志、普通日志、异常信息、测试报告均不含原始 API Key；
- AES-GCM 解密仅在控制面内部必要业务路径使用；
- 被篡改的密文、nonce 或 AAD 绑定信息必须解密失败；
- 缺少非测试环境密钥配置时应用明确失败；
- 同租户/同 Provider 重复 API Key 被拒绝；
- 不同租户可以使用相同原始 API Key，但因 HMAC 指纹与租户作用域不能误冲突；
- 凭据轮换后旧 API Key 不再可被控制面解密，新 Key 可被内部校验使用，响应仅展示新的掩码。

### 租户与资源一致性

- `TENANT_MEMBER`、未认证用户、无租户上下文的 `SUPER_ADMIN` 均不能管理；
- `TENANT_OWNER` 与 `TENANT_ADMIN` 只能访问当前租户数据；
- 不能使用本租户的 Connection + 其他租户的 Credential 创建资源；
- 不能使用同租户不同 Provider 的 Connection/Credential 创建资源；
- 已禁用的 Connection 或 Credential 不允许启用资源；
- 资源 `connectionId` / `credentialId` / `providerId` 不可通过更新接口变更；
- 同一个连接和同一个凭据的重复资源被拒绝；
- 不存在删除接口。

### 阶段边界

- `converge-gateway` 无源码、POM、资源改动；
- 不存在 `/v1/*`、上游 HTTP Client、SSE、OAuth、ResourcePool、Route、计费或下游 API Key；
- 完整 Maven 回归测试通过。

---

## 7. 双项目核验

### New-API

New-API 的 Channel 将地址、Key、模型、分组、优先级、权重等聚合在同一对象中。该项目保留了“配置上游连接与密钥”的必要能力，但刻意将连接、凭据、资源、模型绑定和路由拆开：

```text
Provider → Connection
Provider → Credential
Connection + Credential → ExecutionResource
```

这样不会让后续优先级、权重、模型映射、分组倍率等能力被提前塞入凭据表。

### Sub2API

Sub2API 的 Account 同时包含凭据、账户状态、并发、优先级、限流、会话窗口、分组与模型映射。该项目借鉴“账户/凭据最终必须成为可调度资源”的目标，但本阶段只做静态 `DIRECT_API` 资源；OAuth、账户生命周期、429 冷却、粘性会话、账户组和运行时调度将留在后续阶段。

### 结论

本阶段是两种上游模式的共同底座，不偏离：

- Direct API：未来由 `AiExecutionResource(DIRECT_API)` 代表；
- OAuth/授权账户：未来将以独立账户实体接入，不强塞进 API Key 凭据表；
- 路由、分组、倍率和计费均在拥有真实资源与模型绑定后再设计。
