# 阶段 05｜资源池、模型能力绑定与静态路由编译

## 1. 阶段目标

阶段 01~04 已分别完成：

```text
阶段 01：独立 Vert.x 网关运行骨架
阶段 02：租户级 Provider / Connection / PublicModel 目录
阶段 03：Credential 保险库与 Direct API ExecutionResource
阶段 04：控制面 → 网关的安全、版本化快照同步
```

本阶段将已有对象连接为可验证的**静态路由拓扑**：

```text
AiPublicModel
  ↓ exact model capability binding
AiExecutionResource
  ↓ upstream routing pool membership
AiResourcePool
  ↓ static route target
AiRoutePolicy
```

完成后，控制面和网关均能回答：

> 对某个 tenant、公开模型和规范化操作类型，静态配置允许哪些资源池与资源作为候选？这些候选的优先级、权重和上游模型名分别是什么？

本阶段**不向上游发请求**。它只建立并验证静态路由配置、生成快照、在网关内存中编译路由计划，为后续真正 `/v1/*` 请求接入、动态限流、粘性会话、重试和故障切换打基础。

---

## 2. 本阶段必须继承的既有实现

### 阶段 01

- `converge-gateway` 是独立 Vert.x JVM 进程；
- 网关已有内部健康、就绪、版本、快照状态、requestId、日志和优雅关闭；
- 网关不访问 PostgreSQL，不依赖 Spring、MyBatis、Flyway、JWT 或 `TenantContext`。

### 阶段 02

- `AiProvider`、`AiUpstreamConnection`、`AiPublicModel` 是 tenant 级控制面目录；
- PublicModel 的 `code` 是面向下游的稳定别名，不绑定上游模型名称；
- Service 层通过 `AiCatalogTenantGuard.requireCurrentTenantId()` 显式拒绝空租户；
- AI 目录仅允许 `TENANT_OWNER`、`TENANT_ADMIN` 管理。

### 阶段 03

- `AiCredential` 使用控制面数据库密钥加密保存 API Key；
- `AiExecutionResource` 是 `Connection + Credential + DIRECT_API` 的静态绑定；
- Resource 状态包括 `ENABLED`、`DISABLED`、`DRAINING`；
- 一个 Credential 可绑定多个 Connection 对应的 Resource；
- 无 OAuth、无账户池、无动态运行状态。

### 阶段 04

- `converge-contract` 已存在，当前快照 schema 为 V1；
- 控制面使用 revision + transactional outbox 投影 Redis immutable payload + manifest；
- gateway 使用初始对账、Pub/Sub 提示、周期对账、last-known-good 与 copy-on-write 加载快照；
- gateway snapshot secret envelope 与阶段 03 数据库凭据加密密钥隔离；
- `mvn test` 从 backend 根目录已通过。

不得新建平行的 tenant、用户、角色、审计、快照、Outbox、Redis、Provider、Connection、Credential、ExecutionResource 或模型体系。

---

## 3. 设计原则

## 3.1 公开模型、上游模型、资源和路由必须拆开

禁止把下列概念重新聚合回一个 Channel 或 Account 大表：

```text
public model alias
upstream model name
credential
connection
resource
resource pool
priority / weight
route policy
consumer entitlement
price / multiplier
runtime health / limit
```

本阶段的正确关系是：

```text
AiPublicModel
  └─ AiResourceModelBinding
       ├─ execution_resource_id
       ├─ canonical_operation
       └─ upstream_model_name

AiResourcePool
  └─ AiResourcePoolMember
       └─ execution_resource_id
       ├─ priority
       └─ weight

AiRoutePolicy
  └─ AiRouteTarget
       └─ resource_pool_id
       ├─ priority
       └─ weight
```

## 3.2 两层优先级/权重具有不同职责

### RouteTarget 的 priority / weight

决定同一 PublicModel + Operation 的请求应优先进入哪个**资源池**。

```text
优先级：数字越大，越优先。
权重：仅在同优先级 RouteTarget 间按比例选择资源池。
```

典型场景：

```text
claude-sonnet + CHAT_COMPLETIONS
  - primary-pool：priority=100，weight=100
  - fallback-pool：priority=50，weight=100
```

### ResourcePoolMember 的 priority / weight

决定已选择资源池后，优先使用哪个**具体 ExecutionResource**。

```text
优先级：数字越大，越优先。
权重：仅在同优先级成员间按比例选择资源。
```

典型场景：

```text
primary-pool
  - resource-a：priority=100，weight=70
  - resource-b：priority=100，weight=30
  - resource-c：priority=50，weight=100
```

本阶段只编译和验证这两层静态候选关系。后续阶段会在实际请求时叠加并发、RPM/TPM、429 冷却、余额、健康状态、会话粘性和重试规则。

## 3.3 上游资源池与未来消费访问组不是同一概念

`AiResourcePool` 是**上游调度侧分组**，对应 New-API 的渠道可选集合和 Sub2API 的账户组/资源组方向。

未来的 `AiAccessGroup` 是**下游消费侧分组**，用于用户、套餐、下游 API Key 的模型权限、价格、倍率、限额和策略覆盖。

本阶段严禁创建万能 `group` 表，也不创建 `AiAccessGroup`。

## 3.4 精确模型绑定优先，通配符映射后置

本阶段 `AiResourceModelBinding` 只支持：

```text
一个 PublicModel
+ 一个 CanonicalOperation
+ 一个 ExecutionResource
→ 一个精确 upstream_model_name
```

不支持 wildcard、正则、全局 alias 覆盖、请求参数转换或多层模型替换。

原因：

- 当前尚未实现协议适配和请求转发；
- 精确映射能保证可审计、可验证、可快照化；
- wildcard/条件映射应在真实协议能力矩阵和请求转换阶段单独设计，不能先把任意规则塞入 JSON。

---

## 4. 本阶段领域模型

## 4.1 `AiCanonicalOperation`

新增规范化操作类型枚举，作为 PublicModel、Binding、RoutePolicy 的共同路由键。

至少包含：

```text
CHAT_COMPLETIONS
RESPONSES
MESSAGES
EMBEDDINGS
RERANK
IMAGE_GENERATION
AUDIO_SPEECH
AUDIO_TRANSCRIPTION
```

约束：

- 本阶段只保存和验证操作类型，不实现任何对应 HTTP 接口；
- `stream=true` 不是独立 operation；后续作为请求模式处理；
- `AiProtocolType` 仍描述 Connection 的上游协议，`AiCanonicalOperation` 描述平台内部请求能力；
- 不支持的 `PublicModel + Operation` 必须在静态路由编译中明确报出，而不是默认走 Chat Completions。

## 4.2 `AiResourcePool`

新增 tenant 级上游资源池。

建议字段：

```text
id
tenant_id
code
display_name
description
admin_status             ENABLED / DISABLED
selection_policy          PRIORITY_WEIGHTED（本阶段唯一合法值）
created_at
updated_at
```

约束：

- `UNIQUE(tenant_id, code)`；
- 不直接保存 Credential、Connection、上游模型、租户外对象或运行时限流状态；
- 不需要 `DRAINING`；只有实际 Resource 才需要排空；
- 禁止硬删除；使用停用保留审计历史。

## 4.3 `AiResourcePoolMember`

新增 Resource 与 Pool 的 tenant 级关联。

建议字段：

```text
id
tenant_id
resource_pool_id
execution_resource_id
admin_status              ENABLED / DISABLED
priority                  integer, default 0
weight                    positive integer, default 100
created_at
updated_at
```

约束：

- `UNIQUE(tenant_id, resource_pool_id, execution_resource_id)`；
- Resource 可属于多个 Pool；
- 创建、启用和更新必须验证 Resource 与 Pool 同 tenant；
- `DRAINING` Resource 可保留 Member 关系，但对**新请求的静态候选**必须视为不可选；
- 禁止硬删除；成员停用后不再进入新请求候选。

## 4.4 `AiResourceModelBinding`

新增 Resource 对 PublicModel 的精确能力与上游模型映射。

建议字段：

```text
id
tenant_id
execution_resource_id
public_model_id
canonical_operation
upstream_model_name
admin_status              ENABLED / DISABLED
created_at
updated_at
```

约束：

- `UNIQUE(tenant_id, execution_resource_id, public_model_id, canonical_operation)`；
- Binding 的 Resource 和 PublicModel 必须属于当前 tenant；
- `upstream_model_name` 必须为非空、精确字符串；本阶段禁止 wildcard/正则；
- Binding 仅描述模型和 operation 能力，不保存参数转换、提示词、Header 覆盖、价格、倍率或协议适配逻辑；
- PublicModel、Resource、Connection、Provider、Credential 任一被禁用时，不得成为可选静态候选；
- 禁止硬删除，使用停用保留审计。

## 4.5 `AiRoutePolicy`

新增 tenant 级默认静态路由策略。

建议字段：

```text
id
tenant_id
public_model_id
canonical_operation
display_name
description
admin_status              DRAFT / ENABLED / DISABLED
selection_policy          PRIORITY_WEIGHTED（本阶段唯一合法值）
created_at
updated_at
```

约束：

- `UNIQUE(tenant_id, public_model_id, canonical_operation)`；
- 一条 Policy 表示当前 tenant 下一个公开模型、一个 operation 的默认上游路由；
- 未来 `AiAccessGroup`、套餐、下游 API Key 的路由覆盖应单独建立 override 机制，不在本阶段污染默认 Policy；
- 创建后默认 `DRAFT`；
- 只有拓扑校验通过才允许 `ENABLED`；
- `DISABLED` Policy 不产生新请求候选；
- 禁止硬删除。

## 4.6 `AiRouteTarget`

新增 Policy 到 ResourcePool 的关联。

建议字段：

```text
id
tenant_id
route_policy_id
resource_pool_id
admin_status              ENABLED / DISABLED
priority                  integer, default 0
weight                    positive integer, default 100
created_at
updated_at
```

约束：

- `UNIQUE(tenant_id, route_policy_id, resource_pool_id)`；
- Policy 和 Pool 必须同 tenant；
- 启用 Target 前，至少要确认 Pool 内存在一个**新请求静态可选**的成员：
  - Pool enabled；
  - Member enabled；
  - Resource enabled；
  - PublicModel enabled；
  - Binding enabled；
  - Binding 的 model + operation 精确匹配；
  - Provider / Connection / Credential enabled；
- `DRAINING` Resource 不满足新请求候选要求；
- Target 可先作为 disabled 草稿创建；启用必须校验；
- 禁止硬删除。

---

## 5. 纯 Java 路由核心模块

新增：

```text
backend/converge-routing-core
```

它是纯 Java JAR，不是服务，不监听端口，不访问数据库/Redis/HTTP。

依赖方向：

```text
converge-contract
        ↑
converge-routing-core
   ↑                 ↑
converge-web-service converge-gateway
```

禁止依赖：

```text
Spring / Spring Boot
Vert.x
Redis Client
MyBatis / Flyway / JDBC / PostgreSQL
Servlet
TenantContext
Controller / Service / Mapper
```

核心职责：

```text
StaticTopologyValidator
StaticRoutePlanCompiler
StaticRoutePreviewSelector
StaticRoutePlan
StaticRouteValidationResult
```

### 编译规则

对 `(tenantId, publicModelCode, canonicalOperation)`：

1. 找到 `ENABLED` 的 RoutePolicy；
2. 找到 enabled RouteTarget，并按 priority 从高到低分组；
3. 过滤 disabled Pool、Member、Resource、Binding、PublicModel、Provider、Connection、Credential；
4. 对每个剩余 RouteTarget，找到精确 `ResourceModelBinding`；
5. 对 Pool 内 ResourceMember 按 priority 从高到低分组；
6. 输出不可变 route plan：
   - 路由目标优先级层；
   - 每层的 Pool 候选与权重；
   - 每个 Pool 内 Resource 候选层、权重、Resource ID、Connection ID、Protocol、Base URL、upstream model name；
7. 不执行 HTTP、不会读取 Redis、不会解密 Secret、不计算余额、不会读取动态限流状态。

### 预览选择规则

`StaticRoutePreviewSelector` 可以接受显式 test seed，做确定性的加权选择，用于：

- 控制面管理员静态预览；
- 单元测试；
- 将来网关在动态过滤完成后的选择器复用。

预览结果必须标注：

```text
STATIC_CONFIGURATION_ONLY
DYNAMIC_STATE_NOT_APPLIED
```

它不是实际调用结果，不代表会绕过后续并发、健康、限流、会话粘性或重试策略。

---

## 6. 控制面 API 与状态变化

沿用既有：

```text
/api/v1/ai
Result<T>
PageResult<T>
TENANT_OWNER / TENANT_ADMIN
@Auditable
AiCatalogTenantGuard
Flyway
```

建议接口：

```text
POST /api/v1/ai/resource-pools
GET  /api/v1/ai/resource-pools
GET  /api/v1/ai/resource-pools/{id}
PUT  /api/v1/ai/resource-pools/{id}
POST /api/v1/ai/resource-pools/{id}/enable
POST /api/v1/ai/resource-pools/{id}/disable

POST /api/v1/ai/resource-pools/{poolId}/members
GET  /api/v1/ai/resource-pools/{poolId}/members
PUT  /api/v1/ai/resource-pool-members/{id}
POST /api/v1/ai/resource-pool-members/{id}/enable
POST /api/v1/ai/resource-pool-members/{id}/disable

POST /api/v1/ai/resource-model-bindings
GET  /api/v1/ai/resource-model-bindings
GET  /api/v1/ai/resource-model-bindings/{id}
PUT  /api/v1/ai/resource-model-bindings/{id}
POST /api/v1/ai/resource-model-bindings/{id}/enable
POST /api/v1/ai/resource-model-bindings/{id}/disable

POST /api/v1/ai/route-policies
GET  /api/v1/ai/route-policies
GET  /api/v1/ai/route-policies/{id}
PUT  /api/v1/ai/route-policies/{id}
POST /api/v1/ai/route-policies/{id}/enable
POST /api/v1/ai/route-policies/{id}/disable

POST /api/v1/ai/route-policies/{policyId}/targets
GET  /api/v1/ai/route-policies/{policyId}/targets
PUT  /api/v1/ai/route-targets/{id}
POST /api/v1/ai/route-targets/{id}/enable
POST /api/v1/ai/route-targets/{id}/disable

POST /api/v1/ai/routes/preview
```

`POST /api/v1/ai/routes/preview`：

- 只允许 `TENANT_OWNER`、`TENANT_ADMIN`；
- 输入：`publicModelCode`、`canonicalOperation`、可选 `selectionSeed`；
- 输出：静态配置校验、按 priority 列出的候选层、按 seed 计算的预览选中 Pool/Resource/上游模型；
- 不返回 API Key、Credential 密文、nonce、Redis key、HMAC、payload 或任何秘密；
- 必须清楚标注预览不包含运行时动态状态。

所有写操作都必须：

```text
1. 使用当前 tenant
2. 显式校验关联对象 tenant 一致
3. 写审计
4. 在同一事务中调用 GatewaySnapshotChangeRecorder
5. 让阶段 04 Outbox 投影新快照
```

新增 change type 至少包含：

```text
AI_RESOURCE_POOL_CHANGED
AI_RESOURCE_POOL_MEMBER_CHANGED
AI_RESOURCE_MODEL_BINDING_CHANGED
AI_ROUTE_POLICY_CHANGED
AI_ROUTE_TARGET_CHANGED
```

---

## 7. 快照协议升级：V1 → V2

阶段 04 的 V1 snapshot 只含 PublicModel 和 ExecutionResource 目录。

本阶段 V2 需要额外包含：

```text
GatewayResourcePoolSnapshot
GatewayResourcePoolMemberSnapshot
GatewayResourceModelBindingSnapshot
GatewayRoutePolicySnapshot
GatewayRouteTargetSnapshot
```

建议 `GatewayTenantSnapshot` V2：

```text
schemaVersion
tenantId
revision
generatedAtEpochMillis
publicModels
executionResources
resourcePools
resourceModelBindings
routePolicies
```

### 兼容与发布顺序

本阶段必须支持安全 rollout：

1. 新 gateway 必须能读取并保持加载 **V1** 快照；
2. 新 gateway 还必须能读取、校验和编译 **V2** 快照；
3. 新 control-plane 只在确认所有 gateway 已升级到支持 V2 后，开始产生 V2 快照；
4. 文档必须说明生产升级顺序：先部署 gateway，再部署 control-plane；
5. 如果 V2 路由拓扑无效，gateway 拒绝新 V2 revision，并保留 last-known-good snapshot；
6. V1 快照本身不产生 route plan，但旧网关健康不受影响；
7. 不得让 schema upgrade 静默丢掉 V1 resource 或 secret envelope。

本阶段可以使用明确配置或 feature flag 控制 target schema，但必须避免“新控制面直接发布旧 gateway 不能识别的快照”这种无保护升级。

### Gateway 运行时变化

Gateway 在加载 V2 snapshot 后：

- 使用 `converge-routing-core` 编译静态 route plan；
- 将 route plan 与 tenant local snapshot 一起 copy-on-write 原子替换；
- 编译失败时拒绝新版本、保留旧有效版本；
- `/internal/snapshot-status` 可以增加安全摘要：
  - loaded schema versions；
  - 已加载 tenant 数；
  - 已编译 route plan 数；
  - 无效 route tenant 数；
  - 最近 route validation error category；
- 不新增 `/v1/*`，不新增任何客户端路由接口，不发起上游请求。

---

## 8. 本阶段明确不做

```text
不实现 /v1/*
不实现实际 API 中转、上游 HTTP Client、SSE、WebSocket、失败重试或协议转换
不实现下游 ClientApiKey、用户鉴权、用户 API Key 限流
不实现 AiAccessGroup、用户/套餐/API Key 绑定、模型权限覆盖
不实现价格、成本、倍率、余额、账单、支付、充值
不实现 OAuth、Refresh Token、Cookie、订阅账户、授权账户
不实现账号/账户池动态状态、并发租约、RPM/TPM、429 冷却、健康检查、余额探测
不实现粘性会话、工具调用、多模态参数转换、异步任务或 Realtime
不实现 wildcard/正则/条件模型映射
不修改 React 前端
不让 gateway 访问 PostgreSQL
不让 gateway 使用阶段 03 的数据库凭据密钥
```

---

## 9. 关键验收标准

### 数据与租户

- 所有新表 tenant 级隔离，未加入 tenant ignore tables；
- 请求 DTO 不允许写 tenantId；
- 所有 Service 显式拒绝空 TenantContext；
- 所有关系通过复合约束与 Service 双重校验防止跨 tenant；
- 不存在硬删除接口；
- route/pool/model binding 的启用校验可识别无可用候选拓扑。

### 静态路由

- 能表达一个 PublicModel 在某 operation 下映射到多个 Resource；
- 能表达多个 Resource 进入一个 Pool；
- 能表达多个 Pool 作为一个 RoutePolicy target；
- route target 和 pool member 的 priority/weight 各自独立、语义明确；
- 对相同 seed 的预览结果稳定；
- 高 priority 先于低 priority；同 priority 才按 weight；
- disabled / draining / 缺 binding / provider-disabled / connection-disabled / credential-disabled 的 Resource 不能成为新请求静态候选；
- 路由预览不泄露秘密，不假装执行真实请求。

### 快照与网关

- V2 snapshot 保留 V1 的安全投递原则；
- Redis、日志、审计、异常、测试输出无 API Key 明文；
- 控制面写操作通过 existing revision/outbox 发布 V2；
- Gateway 支持 V1 和 V2；
- Gateway V2 编译错误不替换 last-known-good；
- gateway 不直连 DB；
- 完整 backend `mvn test` 通过。

---

## 10. 双项目核验

### New-API

New-API 当前文档说明：用户组、渠道组、渠道模型共同影响可用性；渠道 priority 决定优先级，weight 用于同优先级分流。其 channel 配置同时聚合模型列表、模型映射、分组、priority 和 weight。

本阶段吸收其中的**模型映射 + 优先级/权重 + 候选资源选择**能力，但将其拆分为：

```text
ResourceModelBinding
ResourcePoolMember
RouteTarget
```

这避免一个资源为不同公开别名或不同模型映射而反复复制 Connection/Credential，也避免把未来消费侧 group/倍率混进上游路由实体。

### Sub2API

Sub2API 当前 Account 同时包含平台、认证类型、凭据、并发、优先级、倍率、状态、限流/过载/临时不可调度窗口、会话窗口、组关系和模型映射。

本阶段借鉴“账户/资源必须进入分组、具备模型映射、并可按 priority/weight 选择”的方向，但只实现 static topology：

```text
Pool membership
Exact model binding
Static route plan
```

并发、rate limit、overload、session window、sticky session、OAuth 生命周期、动态 account selection 仍由后续运行时调度阶段实现。

### 结论

本阶段将阶段 02~04 的目录、资源和快照连接成可验证的静态路由拓扑；没有跳过后续消费者授权、商业计费和运行时调度层，因此不偏离整合方向。
