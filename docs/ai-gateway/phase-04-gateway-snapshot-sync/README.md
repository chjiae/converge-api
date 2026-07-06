# 阶段 04｜控制面到 Vert.x 网关的安全配置快照同步

## 1. 阶段目标

阶段 01 已提供独立 Vert.x 网关运行骨架；阶段 02、03 已在 Spring 控制面建立 Provider、Connection、PublicModel、Credential 与 Direct API ExecutionResource。

本阶段的唯一核心目标是：

> **让控制面把当前租户的可运行 AI 配置投影为版本化、不可变、可校验、含密文秘密的 Redis 快照；让 Vert.x 网关无数据库直连地加载、验证、原子替换并在内存中持有快照。**

阶段结束后：

```text
Spring 控制面
  PostgreSQL（事实数据）
       │
       ├─ 事务内记录 snapshot outbox
       ├─ 异步构建租户级不可变快照
       ├─ 解密数据库凭据，再用网关投递密钥重新封装
       ├─ 写 Redis immutable payload + current manifest
       └─ Pub/Sub 发送低延迟刷新提示
                 │
                 ▼
Vert.x 网关
  Redis（只读配置源）
       │
       ├─ 启动时全量对账
       ├─ Pub/Sub 提示后按 tenant 刷新
       ├─ 周期性全量对账，弥补 Pub/Sub 丢消息
       ├─ 校验 manifest HMAC、payload SHA-256、秘密 AES-GCM
       └─ 原子替换本地只读快照
```

本阶段完成后，网关仍然**不提供 `/v1/*` 业务接口，也不向任何上游发请求**。

---

## 2. 必须继承的现有实现

### 阶段 01

- `converge-gateway` 是独立 Vert.x JVM 进程；
- 网关没有 Spring、MyBatis、Flyway、PostgreSQL、JWT、`TenantContext` 等依赖；
- 已有 `/internal/health`、`/internal/ready`、`/internal/version`、requestId、访问日志、错误响应与优雅关闭；
- 本阶段可修改网关以接入 Redis 和快照运行时，但仍不得加入业务中转能力。

### 阶段 02

- `AiProvider`、`AiUpstreamConnection`、`AiPublicModel` 均为现有租户体系下的控制面目录；
- 请求路径由 `AiCatalogTenantGuard.requireCurrentTenantId()` 显式保护；
- `TENANT_OWNER`、`TENANT_ADMIN` 管理 AI 目录；
- 上游 Base URL 已由 `AiBaseUrlNormalizer` 规范化。

### 阶段 03

- `AiCredential` 使用 AES-256-GCM 在 PostgreSQL 中密存 API Key；
- `AiExecutionResource` 代表 `Connection + Credential + DIRECT_API` 静态绑定；
- `AiResourceStatus` 已有 `ENABLED`、`DISABLED`、`DRAINING`；
- 未建立资源池、路由、模型绑定、下游 API Key 或数据面接口；
- V7 已存在，新增迁移必须使用 **V8 或当前实际最大版本后的连续版本**。

不得重新创建租户、用户、角色、审计、异常、分页、凭据加密、Provider、Connection、PublicModel、Credential 或 ExecutionResource 的平行体系。

---

## 3. 本阶段交付范围

## 3.1 新增 `converge-contract` 纯契约模块

新增 Maven 模块：

```text
backend/converge-contract
```

它是共享 JAR，不是服务、不监听端口、不访问数据库或 Redis。

### 允许内容

- Java records、enums、常量；
- 不可变快照 DTO；
- 快照 schema version 常量；
- 纯 Java 加解密/摘要/签名接口定义，或无框架的值对象；
- 仅在确有必要时依赖通用 JSON 库；优先维持 JDK-only。

### 严禁内容

```text
Spring / Spring Boot
Vert.x Handler / Router / Redis Client
MyBatis / Mapper / Entity
Flyway
数据库、Redis、HTTP 调用
TenantContext
Controller、Service 实现
业务路由、计费、鉴权逻辑
```

至少定义以下共享契约，具体 record 命名可与项目风格保持一致：

```text
GatewayTenantSnapshot
GatewaySnapshotManifest
GatewaySnapshotChangedEvent
GatewayPublicModelSnapshot
GatewayExecutionResourceSnapshot
GatewaySecretEnvelope
GatewaySnapshotSyncState
GatewaySnapshotSchema
```

建议字段：

```text
GatewayTenantSnapshot
- schemaVersion
- tenantId
- revision
- generatedAtEpochMillis
- publicModels
- executionResources

GatewaySnapshotManifest
- schemaVersion
- tenantId
- revision
- payloadRedisKey
- payloadSha256Hex
- manifestHmacBase64
- gatewayKeyId
- publishedAtEpochMillis

GatewaySnapshotChangedEvent
- schemaVersion
- tenantId
- revision
- manifestRedisKey
- publishedAtEpochMillis

GatewayExecutionResourceSnapshot
- tenantId
- resourceId
- providerId
- connectionId
- credentialId
- resourceType
- adminStatus
- providerKind
- protocolType
- baseUrl
- secretEnvelope

GatewaySecretEnvelope
- keyId
- algorithm
- nonceBase64
- ciphertextBase64
```

ID 使用字符串或 UUID 均可，但控制面与网关两端的序列化格式必须固定并有契约测试。

---

## 3.2 快照安全模型

### 数据库凭据密钥与网关投递密钥必须隔离

阶段 03 的数据库凭据密钥：

```text
AI_CREDENTIAL_ACTIVE_KEY_ID
AI_CREDENTIAL_ACTIVE_KEY_BASE64
AI_CREDENTIAL_FINGERPRINT_KEY_BASE64
```

**绝不能**被 `converge-gateway` 使用、读取或配置。

本阶段新增网关快照投递密钥：

```text
AI_GATEWAY_SNAPSHOT_KEY_ID
AI_GATEWAY_SNAPSHOT_ENCRYPTION_KEY_BASE64
AI_GATEWAY_SNAPSHOT_SIGNING_KEY_BASE64
```

约束：

- `AI_GATEWAY_SNAPSHOT_ENCRYPTION_KEY_BASE64`：32 字节 AES-256 密钥；
- `AI_GATEWAY_SNAPSHOT_SIGNING_KEY_BASE64`：至少 32 字节 HMAC-SHA-256 密钥；
- 控制面和网关均需网关投递密钥；
- 网关投递密钥不能与阶段 03 的数据库加密密钥、指纹 HMAC 密钥复用；
- 非测试环境缺少任意必要密钥时，相关服务必须明确失败或保持 `ready=false`，绝不降级为明文；
- 测试只能注入测试专用密钥，测试密钥不可写入生产配置。

### 秘密投递流程

控制面构建快照时：

```text
1. 控制面使用阶段 03 数据库凭据密钥短暂解密 AiCredential
2. 控制面为每个 GatewayExecutionResourceSnapshot 生成新的 12 字节随机 nonce
3. 控制面使用网关投递 AES-256-GCM 密钥重新加密 API Key
4. 将 GatewaySecretEnvelope 写入快照
5. 控制面立刻丢弃中间明文，不记录日志、不加入异常、不进入审计
```

网关加载快照时：

```text
1. 先验证 Manifest HMAC 和 payload SHA-256
2. 再解析 snapshot
3. 使用网关投递 AES-256-GCM 密钥解封装 GatewaySecretEnvelope
4. 仅在网关内存中保存运行期所需秘密
5. 解封装失败时拒绝该版本，不替换最后一个有效快照
```

### AAD 绑定

网关投递的 AES-GCM AAD 至少绑定：

```text
schemaVersion
tenantId
executionResourceId
credentialId
snapshotRevision
gatewayKeyId
```

Manifest HMAC 的输入必须包含以下字段的明确、固定顺序编码：

```text
schemaVersion
tenantId
revision
payloadRedisKey
payloadSha256Hex
gatewayKeyId
```

不能只对 payload 做裸 SHA-256 后信任 Redis 中的 manifest；裸 SHA-256 只能发现偶发损坏，不能证明 manifest 没被伪造。

---

## 3.3 PostgreSQL 事实状态与 Transactional Outbox

新增表的实际命名可按项目规范调整，但必须表达以下职责：

```text
ai_gateway_snapshot_revision
ai_gateway_snapshot_outbox
```

### `ai_gateway_snapshot_revision`

租户级版本事实表，至少保存：

```text
tenant_id（主键）
current_revision（单调递增 bigint）
updated_at
```

### `ai_gateway_snapshot_outbox`

每次影响网关运行配置的控制面变更，在**同一数据库事务**内：

1. 递增该租户 `current_revision`；
2. 写入一条 Outbox 事件；
3. 提交后才允许异步投影。

Outbox 至少保存：

```text
id
tenant_id
revision
change_type
status
attempt_count
next_attempt_at
locked_at / lock_owner（或等价安全锁字段）
last_error_summary（绝不含秘密）
created_at
published_at
```

变更来源至少包括：

```text
AI_PROVIDER_CHANGED
AI_CONNECTION_CHANGED
AI_PUBLIC_MODEL_CHANGED
AI_CREDENTIAL_CHANGED
AI_EXECUTION_RESOURCE_CHANGED
```

需要改造阶段 02、03 对应 Service 的所有 create / update / enable / disable / rotate / drain 写操作，使其在同一事务中调用统一 `GatewaySnapshotChangeRecorder`。

### Projector / Publisher

控制面实现异步发布器：

```text
GatewaySnapshotOutboxProjector
```

职责：

- 使用数据库锁或 `FOR UPDATE SKIP LOCKED` 等等价方式安全认领事件；
- 按 tenant 合并待处理事件，读取该 tenant 最新 revision；
- 使用**显式 tenant_id 条件的专用 Mapper 查询**构建快照；
- 不允许在无 `TenantContext` 时依赖 MyBatis 租户拦截器“自动全量查询”；
- Redis 发布成功后将已覆盖 revision 的 Outbox 事件标记为已发布；
- Redis 失败时按退避重试，并只记录安全错误摘要；
- 应用启动与周期性任务必须支持重新投影，以便 Redis 清空、重启、短暂故障后恢复快照；
- 禁止在 Controller 请求事务内直接写 Redis 或向网关发布消息。

### 快照筛选

对一个 tenant 构建快照时：

- `AiPublicModel`：仅投影 `ENABLED` 模型元数据；
- `AiExecutionResource`：
  - `ENABLED`、`DRAINING` 资源可投影；
  - 所属 tenant、Provider、Connection、Credential 必须均有效且一致；
  - `DISABLED` 资源、`DISABLED` Provider、`DISABLED` Connection、`DISABLED` Credential 均不得向网关投递 API Key；
- 已禁用 tenant：应投影为无资源的空快照，或从 gateway tenant index 移除；必须在文档和测试中固定一种行为。推荐：**发布空快照并保留 tenant index**，使网关明确覆盖旧资源，而不是保留旧版本。

本阶段 PublicModel 与 Resource 尚未建立模型绑定，快照中可同时存在模型目录与资源目录，但不得虚构路由关系。

---

## 3.4 Redis 键、不可变 Payload 与原子指针

Redis 中不得保存 API Key 明文。

建议键约定：

```text
converge:gateway:snapshot:tenant-index
converge:gateway:snapshot:tenant:{tenantId}:revision:{revision}
converge:gateway:snapshot:tenant:{tenantId}:current
converge:gateway:snapshot:tenant:{tenantId}:history
converge:gateway:snapshot:changed
```

推荐发布顺序：

```text
1. SET payloadKey payloadBytes NX
2. 写入/更新 tenant current manifest
3. SADD tenant-index tenantId
4. 更新 history，保留当前和最近 N 个版本
5. PUBLISH changed event
6. 将相应 outbox 事件标记 PUBLISHED
```

约束：

- Payload key 是不可变版本键；相同 tenantId + revision 的内容不得原地覆盖；
- Manifest 是唯一可变“当前指针”；
- Gateway 先读取 manifest，再读取 manifest 指向 payload；
- Gateway 校验 HMAC、SHA-256、schema、tenantId、revision 后才原子替换本地快照；
- Pub/Sub 只用于低延迟提示，**不得作为可靠事实来源**；
- Gateway 必须启动全量对账，并按配置周期性全量对账，弥补断线期间遗漏的 Pub/Sub；
- Payload 历史默认至少保留当前版本加最近 2 个旧版本；清理不能删掉 current 指向的 payload；
- Redis 中断、消息遗漏或 payload 校验失败时，网关保留最后一个有效本地快照，禁止替换为半成品。

---

## 3.5 Vert.x 网关快照运行时

网关新增 `vertx-redis-client`，并以非阻塞方式完成：

```text
Redis connection
initial reconciliation
dedicated Pub/Sub subscription
periodic reconciliation
tenant snapshot validation
copy-on-write local snapshot swap
graceful close
```

### 本地快照规则

- 使用不可变 snapshot 对象；
- 以 `AtomicReference<Map<String, GatewayTenantSnapshot>>` 或等价 copy-on-write 结构保存；
- 一个 tenant 新版本通过所有验证后，只替换该 tenant 的 map entry；
- 一个 tenant 新版本失败时，其他 tenant 与该 tenant 的最后有效版本均不得受影响；
- 不得在 Vert.x event loop 执行阻塞 DB、文件、同步网络或长时间密码学批处理；
- 每个 Gateway 实例都要独立订阅和独立对账，不能依赖进程内 EventBus 作为跨实例同步机制。

### Ready 语义

保留 `/internal/health`：进程存活即可 200。

调整 `/internal/ready`：

- 初始 Redis 对账成功后：200；
- tenant index 为空但已成功读取：200，表示“暂无租户快照”；
- 未完成第一次成功对账：503；
- 发现 index 中某 tenant 的 current manifest / payload 不存在、HMAC 不匹配、SHA 不匹配、schema 不兼容或秘密解封装失败：503；
- Redis 连接短暂断开但本地所有快照仍在允许的 `snapshot.max-staleness` 内：可返回 200，并在 body 中标记 `DEGRADED`；
- 超过最大陈旧时间或无有效本地快照：503；
- 返回内容绝不含 payload、Redis key 密钥、API Key、nonce、密文或签名。

新增：

```text
GET /internal/snapshot-status
```

仅返回安全运维信息，例如同步状态、index tenant 数、已加载 tenant 数、最后成功对账时间、最近错误分类与当前本地 revision 摘要。

---

## 4. 本阶段明确不做

```text
不新增 /v1/*
不实现 API 中转、上游 HTTP Client、SSE、WebSocket、失败重试、协议转换
不创建下游 ClientApiKey、用户 API Key 鉴权、访问组、套餐映射
不创建 ResourcePool、ResourcePoolMember、RoutePolicy、RouteTarget
不创建 Resource ↔ PublicModel 绑定
不实现路由优先级、权重、主备、限流、并发、429 冷却、健康检查或粘性会话
不实现 OAuth、Refresh Token、Cookie、订阅账号或授权账户
不实现价格、倍率、余额、账单、支付
不修改 React 前端
不让 gateway 访问 PostgreSQL
不让 gateway 使用阶段 03 的数据库凭据加密密钥
不将 Redis Pub/Sub 当作可靠消息队列
```

---

## 5. 建议实施顺序

1. 读取阶段 01、02、03 实施结果与当前代码；
2. 新增 `converge-contract`，先完成契约与契约兼容性测试；
3. 新增 V8（或实际连续版本）的 revision/outbox 表；
4. 在控制面实现 snapshot key 配置、Secret envelope、manifest signing；
5. 改造已有 AI 写操作，在同一事务中写 revision + outbox；
6. 实现 outbox projector、tenant 快照查询、Redis 不可变 payload + manifest 发布；
7. 修改 gateway POM 与运行时配置，接入 Vert.x Redis client；
8. 实现 initial sync、Pub/Sub hint、periodic reconciliation、原子替换与 readiness；
9. 补齐 Redis/Testcontainers、控制面、网关、跨模块契约、故障注入测试；
10. 完整 Maven 回归、文档、验收、提交。

---

## 6. 验收标准

### 契约与依赖

- `converge-contract` 被控制面和网关引用；
- `converge-contract` 不依赖 Spring、Vert.x、Redis、MyBatis、Flyway、数据库驱动或 Servlet；
- 控制面写出的快照可由网关读取，存在跨模块兼容性测试；
- schema 版本不兼容必须显式拒绝，不能静默猜测解析。

### 凭据与快照安全

- Redis payload、manifest、通知、日志、审计、异常、测试报告均不出现 API Key 明文；
- 数据库凭据密钥不出现在网关配置、POM、代码或环境读取中；
- 网关投递加密密钥与签名密钥均与阶段 03 密钥隔离；
- 篡改 payload、manifest tenantId/revision/key、HMAC、nonce 或密文后，网关拒绝新版本；
- 新版本被拒绝后，网关保留最后有效本地快照；
- disabled resource/provider/connection/credential 不向快照投递秘密。

### 一致性与恢复

- 每个 AI 目录/凭据/资源写操作与 snapshot revision/outbox 同一事务；
- Redis 发布失败不丢 Outbox，重试后可成功；
- Redis 清空或网关错过 Pub/Sub 后，通过启动/周期对账可恢复；
- payload 先写、manifest 后切换、通知最后发送；
- tenant 的新 snapshot 校验通过后才原子替换；
- 一个 tenant 更新失败不影响其他 tenant 的已加载快照；
- gateway 不访问 PostgreSQL；
- gateway startup 前首次对账失败时 `/internal/ready` 为 503；
- 对账成功且无 tenant 时 `/internal/ready` 为 200；
- snapshot 过期或损坏时 `/internal/ready` 为 503。

### 回归与边界

- 控制面、gateway、contract 三个模块完整 Maven 测试通过；
- `mvn test` 从 backend 根目录通过；
- 无 `/v1/*`、无上游 HTTP 调用、无模型路由、无资源池、无用户 API Key、无计费功能；
- React 前端未修改；
- 所有新增配置、环境变量、Redis 键、快照状态和故障恢复行为已写入文档；
- 完成阶段结果文档并创建中文 Git 提交。
