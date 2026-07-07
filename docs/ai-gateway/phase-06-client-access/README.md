# 阶段 06｜下游 Client API Key、访问组与模型授权快照

## 1. 阶段目标

阶段 05 已完成公开模型、资源、精确模型绑定、资源池、静态路由策略，并把静态路由编译进 Gateway Snapshot V2。

本阶段只解决下游调用方的**身份与授权**问题：

> 网关收到一把下游 Client API Key 后，能够在不访问 PostgreSQL 的前提下，识别其所属 tenant，验证其是否仍有效，并判定其可使用哪些 `PublicModel + CanonicalOperation`。

阶段完成后的最小闭环：

```text
控制面
  AiAccessGroup
  AiAccessGroupModelGrant
  AiClientApiKey
  AiClientApiKeyAccessGroup
      ↓ transaction + revision/outbox
  Gateway Snapshot V3
      ↓ Redis manifest/payload + Pub/Sub hint
Gateway
  tenant snapshots → immutable global ClientKey index
      ↓
  Authorization: Bearer <client-api-key>
      ↓
  GatewayClientPrincipal
      ↓
  授权后的 GET /v1/models
```

`GET /v1/models` 是本项目第一个公开数据面端点，但它**只返回授权且存在静态路由计划的公开模型目录**。它不请求上游、不计费、不做 Chat Completions、不做 SSE。

---

## 2. 既有实现与不可破坏边界

### 阶段 01

- `converge-gateway` 是独立 Vert.x 进程；
- 不引入 Spring、MyBatis、Flyway、JDBC、PostgreSQL 或 `TenantContext`；
- 已有 requestId、内部健康/就绪/版本/快照状态、统一错误处理和优雅关闭。

### 阶段 02 / 03

- Provider、Connection、PublicModel、Credential、ExecutionResource 均是 tenant 级事实对象；
- `AiCredential` 的数据库加密密钥只允许控制面使用；
- Gateway 绝不读取 `AI_CREDENTIAL_*` 密钥。

### 阶段 04

- `converge-contract` 是纯 Java 契约模块；
- 控制面使用 revision + transactional outbox 投影 Redis immutable payload/current manifest；
- Gateway 使用 initial reconciliation、Pub/Sub hint、periodic reconciliation、last-known-good、copy-on-write；
- 快照 manifest HMAC、payload SHA-256、secret envelope 已完成。

### 阶段 05

- V9 已存在；
- PublicModel → ExecutionResource 的 exact model binding 已完成；
- ResourcePool / RoutePolicy / priority / weight 已完成；
- `converge-routing-core` 已完成静态路由编译；
- Gateway 可读取 V1/V2 snapshot，V2 可编译 `StaticRoutePlan`；
- 当前 `master` 阶段 05 提交为 `727e535 feat(AI网关): 实现静态路由快照 V2`。

不得新建平行的 tenant、用户、角色、审计、快照、Redis 同步、Provider、Resource、PublicModel 或静态路由体系。

---

## 3. 核心概念与严格分层

### 3.1 Client API Key

`AiClientApiKey` 是下游调用网关的 bearer credential：

```text
client application / agent / user tool
  → Client API Key
  → tenant + access groups
```

它不是：

```text
控制面网页登录 JWT
上游 ProviderCredential
OAuth access token
账号 Cookie
资源池成员
用户套餐
价格规则
```

一个 Client API Key 属于一个 tenant，但可绑定多个 AccessGroup。

### 3.2 AccessGroup

`AiAccessGroup` 是**消费侧策略组**：

```text
一个 tenant 内：
  AccessGroup
    → 精确授权 PublicModel + CanonicalOperation
  ClientApiKey
    → 一个或多个 AccessGroup
```

它不是阶段 05 的 `AiResourcePool`：

```text
AiResourcePool：上游调度侧资源分组
AiAccessGroup：下游消费侧授权分组
```

禁止使用一个泛化 `group` 表把两者混合。

### 3.3 授权合并语义

同一 Client API Key 绑定多个 AccessGroup 时，授权采用**并集**：

```text
effectiveGrants(key)
  = union(enabled grants of enabled groups bound to enabled key)
```

本阶段没有 deny rule、优先级覆盖、套餐覆盖、用户覆盖或价格规则。

未来 `AiAccessGroupRouteOverride`、套餐、用户、API Key 的定价/模型可见性覆盖必须单独设计，不能污染本阶段的基础授权关系。

---

## 4. Client API Key 安全协议

## 4.1 Key 格式

由服务端生成，不接受客户端自定义 secret：

```text
cvg_live_<keyId>_<secret>
```

约束：

```text
keyId：至少 96 bit SecureRandom，URL-safe Base64 无填充
secret：至少 256 bit SecureRandom，URL-safe Base64 无填充
prefix：本阶段固定 cvg_live
```

`keyId` 是可定位但不可猜测的公开索引，不是 secret。

`secret` 仅在：

```text
创建成功响应
轮换成功响应
```

中返回一次。此后绝不通过读取接口、日志、审计、异常、数据库、Redis、前端状态持久化或测试输出再次提供。

## 4.2 数据库存储：不可逆 verifier，不保存明文或可解密 secret

`AiClientApiKey` 建议字段：

```text
id
tenant_id
key_id                         全局唯一、不可变、公开索引
display_name
description
admin_status                   ENABLED / DISABLED / REVOKED
secret_hash_algorithm          SHA-256
secret_verifier_salt           16+ byte random bytes
secret_verifier_hash           SHA-256 verifier bytes/hex
key_version                    正整数；轮换后 +1
masked_preview                 仅用于安全展示
expires_at                     可空
revoked_at                     可空
created_at
updated_at
```

verifier 输入必须采用固定、明确的二进制编码：

```text
"CONVERGE_CLIENT_KEY_V1"
+ keyId
+ keyVersion
+ verifierSalt
+ secret
```

约束：

- `secret` 必须由服务端生成且至少 256 bit，因此 SHA-256 verifier 不依赖 gateway 共享 HMAC 私钥；
- verifier 不是密码哈希的替代品，适用前提是 secret 具有高熵且无法由用户选择；
- Gateway 通过 `MessageDigest.isEqual` 或同等 constant-time comparison 比较 verifier；
- `keyId` 全局唯一，确保 gateway global index 不会产生跨 tenant 歧义；
- `key_version` 每次 rotate 增加，旧 secret 在新 snapshot 生效后失效；
- Gateway 不需要、也不得读取控制面 DB credential encryption key；
- 控制面和 Gateway snapshot manifest HMAC 仍保护 Redis payload 免遭伪造。

## 4.3 状态语义

```text
ENABLED：可在 expires_at 前通过认证
DISABLED：管理员暂时停用；不可认证
REVOKED：终止状态；不可重新启用或轮换
EXPIRED：由 expires_at 推导，不独立存库；不可认证
```

规则：

- `REVOKED` 不允许重新启用；
- rotation 仅允许对有效且 `ENABLED` 的 key 执行；
- disable / revoke / rotate 都必须进入既有 revision/outbox；
- Gateway 以最新成功加载 snapshot 为准，阶段 04 的 Pub/Sub + periodic reconciliation 提供跨实例同步；
- 管理端必须明确提示“状态在 Gateway 成功应用新 snapshot 后生效”，不能宣称跨实例瞬时线性生效；
- Gateway 超过既有 `snapshot.max-staleness` 时 readiness 必须失败，禁止无限期接受陈旧 Key。

## 4.4 脱敏、日志、审计与响应

以下内容绝不能写入：

```text
raw Client API Key
secret 部分
Authorization header
x-api-key header
secret_verifier_salt
secret_verifier_hash
Redis snapshot payload
manifest HMAC
gateway snapshot encryption key
```

特别要求：

- 现有“Controller 记录入参”规范遇到创建/轮换 Key 接口时，必须使用统一 redaction，不得把 raw key 写日志；
- `@Auditable` 只记录 key ID、显示名称、状态、组绑定变更和操作者，不记录 secret/verifier；
- Gateway access log 不记录 Authorization / x-api-key；
- 认证失败响应统一，不区分 key 格式错误、keyId 不存在、禁用、撤销、过期或 secret 不匹配；
- API key create / rotate HTTP response 必须设置 `Cache-Control: no-store`。

---

## 5. 新增领域模型

## 5.1 `AiAccessGroup`

建议字段：

```text
id
tenant_id
code
display_name
description
admin_status                ENABLED / DISABLED
created_at
updated_at
```

约束：

```text
UNIQUE(tenant_id, code)
UNIQUE(tenant_id, id)
```

禁止硬删除。

## 5.2 `AiAccessGroupModelGrant`

精确授权一个 PublicModel + CanonicalOperation：

```text
id
tenant_id
access_group_id
public_model_id
canonical_operation
admin_status                ENABLED / DISABLED
created_at
updated_at
```

约束：

```text
UNIQUE(tenant_id, access_group_id, public_model_id, canonical_operation)
```

规则：

- AccessGroup 和 PublicModel 必须属于当前 tenant；
- 本阶段仅允许 exact grant，不支持 `*`、正则、类别通配、默认全部模型；
- Disabled group / disabled grant / disabled PublicModel 不产生 effective grant；
- Grant 可以存在但当前没有 `StaticRoutePlan`；这种情况不应让模型出现在 `/v1/models`；
- 禁止硬删除。

## 5.3 `AiClientApiKey`

使用第 4 节字段及安全约束。

额外约束：

```text
UNIQUE(key_id)
UNIQUE(tenant_id, id)
```

不得存储：

```text
raw_key
secret_plaintext
encrypted_secret
reversible client secret
```

## 5.4 `AiClientApiKeyAccessGroup`

Key 到消费侧 AccessGroup 的关系：

```text
id
tenant_id
client_api_key_id
access_group_id
admin_status                ENABLED / DISABLED
created_at
updated_at
```

约束：

```text
UNIQUE(tenant_id, client_api_key_id, access_group_id)
```

规则：

- Key 与 AccessGroup 必须 tenant 一致；
- 关系使用 ENABLED/DISABLED 保留审计历史，不提供硬删除；
- Key 可绑定多个 group；
- 同 key 同 group 不可重复。

---

## 6. 控制面 API、权限、审计

复用已有：

```text
/api/v1/ai
Result<T>
PageResult<T>
TENANT_OWNER / TENANT_ADMIN
@Auditable
AiCatalogTenantGuard
GatewaySnapshotChangeRecorder
```

### AccessGroup

```text
POST /api/v1/ai/access-groups
GET  /api/v1/ai/access-groups
GET  /api/v1/ai/access-groups/{id}
PUT  /api/v1/ai/access-groups/{id}
POST /api/v1/ai/access-groups/{id}/enable
POST /api/v1/ai/access-groups/{id}/disable
```

### Model Grant

```text
POST /api/v1/ai/access-groups/{groupId}/model-grants
GET  /api/v1/ai/access-groups/{groupId}/model-grants
GET  /api/v1/ai/access-group-model-grants/{id}
PUT  /api/v1/ai/access-group-model-grants/{id}
POST /api/v1/ai/access-group-model-grants/{id}/enable
POST /api/v1/ai/access-group-model-grants/{id}/disable
```

### Client API Key

```text
POST /api/v1/ai/client-api-keys
GET  /api/v1/ai/client-api-keys
GET  /api/v1/ai/client-api-keys/{id}
PUT  /api/v1/ai/client-api-keys/{id}
POST /api/v1/ai/client-api-keys/{id}/rotate
POST /api/v1/ai/client-api-keys/{id}/enable
POST /api/v1/ai/client-api-keys/{id}/disable
POST /api/v1/ai/client-api-keys/{id}/revoke
```

Create / rotate response can额外返回：

```text
rawKey                    仅本次响应
keyId
maskedPreview
keyVersion
status
expiresAt
```

普通 list/detail response 不得含 `rawKey`、verifier salt/hash 或 snapshot 内部字段。

### Key ↔ AccessGroup

```text
POST /api/v1/ai/client-api-keys/{keyId}/access-groups
GET  /api/v1/ai/client-api-keys/{keyId}/access-groups
PUT  /api/v1/ai/client-api-key-access-groups/{id}
POST /api/v1/ai/client-api-key-access-groups/{id}/enable
POST /api/v1/ai/client-api-key-access-groups/{id}/disable
```

所有写操作必须：

```text
1. 显式 requireCurrentTenantId
2. 显式验证所有关联对象 tenant 一致
3. 使用 @Auditable 但不记录 sensitive data
4. 在同一业务事务中调用 GatewaySnapshotChangeRecorder
5. 通过既有 Outbox 投影最新 snapshot
```

新增 snapshot change type：

```text
AI_ACCESS_GROUP_CHANGED
AI_ACCESS_GROUP_MODEL_GRANT_CHANGED
AI_CLIENT_API_KEY_CHANGED
AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED
```

---

## 7. Gateway Snapshot V3

## 7.1 V3 契约

`converge-contract` 新增不可变契约，名称可按现有风格微调：

```text
GatewayAccessGroupSnapshot
GatewayAccessGroupModelGrantSnapshot
GatewayClientApiKeySnapshot
GatewayClientApiKeyAccessGroupSnapshot
GatewayClientPrincipal
GatewayClientKeyAuthenticationResult
```

`GatewayTenantSnapshot` V3 追加：

```text
accessGroups
accessGroupModelGrants
clientApiKeys
clientApiKeyAccessGroups
```

`GatewayClientApiKeySnapshot` 至少包含：

```text
tenantId
clientApiKeyId
keyId
adminStatus
secretHashAlgorithm
secretVerifierSaltBase64
secretVerifierHashBase64
keyVersion
expiresAtEpochMillis
accessGroupIds
```

不包含：

```text
rawKey
secret
provider credential
GatewaySecretEnvelope
Redis key
manifest HMAC
```

## 7.2 V1 → V2 → V3 rollout

新 Gateway 必须接受：

```text
V1：无 route plan，无 client key index
V2：有 static route plan，无 client key index
V3：有 static route plan 与 client key index
```

安全部署顺序：

```text
1. 部署支持 V1/V2/V3 的 Gateway
2. 检查所有实例 /internal/snapshot-status
3. 部署产生 V3 的 control-plane
4. 触发或等待 Outbox reproject
5. 检查 Gateway 成功加载 V3
6. 再启用任何 Client API Key 并对外暴露 /v1/models
```

禁止：

```text
先发布只生成 V3 的 control-plane
再让只支持 V1/V2 的 gateway 读取 V3
```

## 7.3 Gateway global Client Key index

因为 Client API Key 请求到达 Gateway 时尚未知 tenant，Gateway 必须从所有已验证 tenant snapshot 维护不可变 global index：

```text
Map<keyId, GatewayClientPrincipal>
```

更新规则：

```text
tenant 新 snapshot 完成 schema / manifest / checksum / secret envelope /
static route compilation 验证后：
  1. 删除该 tenant 旧 key entries
  2. 编译该 tenant 新 key entries
  3. 以 copy-on-write 原子替换 global key index 与 tenant snapshot map
```

约束：

- `keyId` 在 DB 全局唯一；
- 一个 tenant V3 编译失败时，保留其旧 tenant snapshot 和旧 key entries；
- 一个 tenant 更新失败不得影响其他 tenant 的 key 验证；
- V1/V2 tenant 贡献空 key entries；
- 不得在 key 认证路径访问 Redis 或 PostgreSQL；
- `/internal/snapshot-status` 只允许展示 `clientKeyCount` 等安全计数，不可展示 keyId、hash、salt、group 或任何 secret。

## 7.4 Gateway 认证步骤

```text
1. 从 Authorization: Bearer <raw-key> 提取 raw key
2. 校验 cvg_live_<keyId>_<secret> 严格格式和长度
3. 通过 keyId 查询 local immutable global index
4. 检查 status、expiresAt、snapshot readiness
5. 以固定协议计算 verifier
6. constant-time compare
7. 创建不可变 GatewayClientPrincipal
8. 在 RoutingContext 只保存 principal，不保存 raw key
```

禁止：

```text
接受控制面 JWT / Cookie 作为 Client API Key
从 DB / Redis 同步查询 key
在 Context、异常或日志保存 raw key
```

认证错误统一使用数据面错误 envelope：

```json
{
  "error": {
    "message": "Invalid API key",
    "type": "invalid_request_error",
    "param": null,
    "code": "invalid_api_key"
  }
}
```

HTTP status：

```text
401：缺失或无效 Client API Key
403：Key 认证有效，但没有所需模型/operation 授权
503：Gateway 未 ready、snapshot 过期或未加载 Client Key index
```

---

## 8. 首个公开数据面接口：`GET /v1/models`

本阶段只新增：

```text
GET /v1/models
Authorization: Bearer cvg_live_<keyId>_<secret>
```

行为：

1. 通过 Gateway Client Key authentication；
2. 得到 tenant + enabled AccessGroup grants；
3. 读取该 tenant 已编译 `StaticRoutePlan`；
4. 返回同时满足以下条件的 `PublicModel`：
   - Key 有至少一个 enabled exact grant；
   - Grant 对应 `PublicModel + CanonicalOperation`；
   - model enabled；
   - 对应 static route plan 存在且有效；
5. 使用最小 OpenAI-compatible list envelope：

```json
{
  "object": "list",
  "data": [
    {
      "id": "public-model-code",
      "object": "model",
      "created": 0,
      "owned_by": "converge"
    }
  ]
}
```

规则：

- 不泄露 upstream model name、Provider、Connection、ResourcePool、RoutePolicy、Credential、内部 ID 或可用资源数量；
- 不作上游 API 调用；
- 不写 usage / billing；
- 不作模型列表缓存以外的额外 Redis 查询；
- 无授权模型时返回合法空 list，不暴露系统是否存在其他 tenant 模型；
- `GET /v1/models/{id}`、`POST /v1/chat/completions`、SSE、Anthropic/Gemini endpoint 均不属于本阶段；
- data plane errors 与 control-plane `Result<T>` 分开，不能混用 Spring 管理端错误 envelope。

---

## 9. 本阶段明确不做

```text
不实现上游 HTTP Client
不实现 POST /v1/chat/completions
不实现 /v1/responses、/v1/embeddings、Claude、Gemini 或 SSE
不实现请求/响应协议转换
不实现计费、价格、倍率、套餐、余额、账本、配额、支付
不实现 RPM/TPM、并发租约、429 冷却、健康检查、失败重试、熔断
不实现 OAuth、Refresh Token、Cookie、授权账户、账号池或粘性会话
不实现 AccessGroup 路由覆盖、deny rule、wildcard grant、套餐/用户覆盖
不修改 React 前端
不让 Gateway 访问 PostgreSQL
不让 Gateway 使用 ProviderCredential 数据库密钥
不让 Redis 或日志保存 raw Client API Key
```

---

## 10. 验收标准

### Key 安全与控制面

- Key secret 使用 SecureRandom 生成，至少 256 bit；
- raw key 只在 create / rotate 结果中返回一次；
- DB、Redis、日志、审计、异常、测试输出不含 raw key；
- verifier 使用固定 encoding、随机 salt 与 constant-time compare；
- keyId 全局唯一；
- Key 可 enable / disable / revoke / rotate / expire；
- revoked 不能 re-enable / rotate；
- 所有对象严格 tenant 隔离；
- 无硬删除 API；
- create / rotate response `Cache-Control: no-store`；
- Tenant owner/admin 才可管理；member/未认证/无 tenant super admin 被拒绝。

### 授权

- AccessGroup 与 ResourcePool 没有混用；
- Key 多 group 授权按并集计算；
- model grants 精确到 PublicModel + CanonicalOperation；
- disabled / expired / revoked key、disabled group/grant/model 不授权；
- 非授权模型返回 403 或在 `/v1/models` 中不出现；
- `/v1/models` 不泄露上游或其他 tenant 信息。

### 快照与网关

- Snapshot V3 继承 V1/V2 所有 HMAC、SHA-256、secret envelope、安全与 last-known-good 原则；
- Gateway 支持 V1/V2/V3；
- 不访问 DB/Redis 验证单个请求；
- global Client Key index 原子替换；
- V3 解析/认证/授权错误不替换 last-known-good；
- Gateway 超过 snapshot max staleness 时不接受 Client Key；
- `/internal/snapshot-status` 无 Key/secret 泄露。

### 测试与交付

- full backend reactor `mvn test` 通过；
- 控制面集成测试：tenant、角色、CRUD、状态、rotation、salt/hash、outbox、secret leak scan；
- gateway 测试：Bearer extraction、bad format、unknown key、wrong secret、expired/disabled/revoked、constant-time verifier helper、multi-tenant index、V1/V2/V3 compatibility、last-known-good；
- `/v1/models` 测试：401/403/503、授权模型过滤、空 list、无上游请求；
- 按 AGENTS.md 填写中文注释、日志、Git commit；
- 填写阶段结果文档与验收清单。
