# 阶段 08｜动态资源调度、并发租约与健康熔断

## 1. 阶段目标

阶段 07 已实现：

```text
POST /v1/chat/completions
  → Client API Key 认证
  → AccessGroup + CHAT_COMPLETIONS 授权
  → 静态优先级 / 权重选择
  → Direct API 上游请求
  → 非流式 JSON 与 SSE 中转
```

但当前资源选择仍是静态的：

```text
只看 RouteTarget / PoolMember priority 和 weight
不看资源当前并发
不看近期连接失败
不看上游 429
不看熔断状态
```

阶段 08 在不改变阶段 07 HTTP/SSE 转发、Client API Key、授权、静态拓扑和模型映射语义的前提下，增加跨 Gateway 实例共享的运行时资源治理能力：

```text
静态候选资源计划
  → Redis 原子租约尝试
  → 过滤并发已满、熔断、冷却中的资源
  → 获取成功后才发送上游请求
  → 请求结束后释放租约并反馈资源结果
```

本阶段目标：

```text
1. Execution Resource 运行时策略控制面；
2. Gateway Snapshot V4；
3. Redis 跨实例并发租约；
4. SSE 长连接租约续约；
5. 连续失败熔断；
6. 429 限流冷却；
7. 单探针半开恢复；
8. 发送上游请求前的动态候选资源改选；
9. Gateway 内部运行时安全状态摘要。
```

---

## 2. 阶段 07 前置能力复核

当前已有：

```text
独立 Vert.x Gateway
Gateway Snapshot V1 / V2 / V3
Gateway 本地 Client API Key 索引
AccessGroup 精确授权
PublicModel + CanonicalOperation
StaticRoutePlan
StaticRouteRequestSelector
OpenAI Compatible Chat Completions 非流式与 SSE
共享 HttpClient
上游模型名双向映射
上游 runtime secret 内存解封装
Gateway 不访问 PostgreSQL
Gateway 请求不查询 Redis
```

阶段 08 调整后：

```text
Client API Key / 授权 / 静态路线：
仍仅从 Gateway 本地已验证快照读取。

资源治理：
新增 Gateway 到 Redis 的运行时原子操作。

数据库：
Gateway 仍不得访问 PostgreSQL。
```

---

## 3. 核心边界

### 3.1 本阶段做什么

```text
资源最大并发
跨实例租约
租约 TTL 与续约
连续失败计数
连接失败 / TLS / 超时 / 5xx 熔断
429 冷却
半开单探针恢复
请求发出前的动态候选改选
运行时状态安全摘要
```

### 3.2 本阶段不做什么

```text
不做真实上游 retry
不做上游请求发出后的 fallback
不做 circuit breaker 库接入
不做用户级 RPM / TPM
不做 Client API Key 限流
不做价格、倍率、余额、账本、预扣或结算
不做 OAuth、Cookie、账号池、Sticky Session
不做 Claude、Gemini、Responses、Embeddings
不做 WebSocket / Realtime
不修改 React
不让 Gateway 访问 PostgreSQL
不让控制面同步写入每次请求的运行时状态
```

### 3.3 “动态改选”不等于 retry

本阶段允许：

```text
Gateway 尚未发起任何上游请求
  → 候选资源 A 并发满
  → 尝试候选资源 B
  → 成功获取 B 的租约
  → 仅向 B 发起一次上游请求
```

本阶段禁止：

```text
已向资源 A 发起上游请求
  → A 超时或返回错误
  → 自动对资源 B 再发一次请求
```

后者属于真正 retry / failover，必须由后续独立阶段处理，以避免重复生成、重复工具调用、重复扣费或副作用重放。

---

## 4. 总体架构

```text
Client Request
  │
  ▼
GatewayDataPlaneAuthenticator
  │
  ▼
GatewaySnapshotRuntime
  │
  ├─ Client API Key / Grant / StaticRoutePlan
  └─ StaticRouteCandidatePlanner
       │
       ▼
GatewayRuntimeGovernanceRuntime
  │
  ├─ Redis Lua: acquire lease
  ├─ Redis Lua: renew lease
  ├─ Redis Lua: complete / release lease
  └─ Redis Lua: record resource outcome
       │
       ▼
GatewayOpenAiDirectExecutor
  │
  ├─ 只在成功获取租约后发送上游请求
  ├─ 非流式结束时释放租约
  ├─ SSE 完成、断开、错误时释放租约
  └─ SSE 长连接期间持续续约
       │
       ▼
Upstream OpenAI Compatible API
```

职责边界：

```text
converge-web-service
  → 维护运行时策略配置
  → 通过 outbox 投影 Snapshot V4

converge-contract
  → 定义 Snapshot V4 契约
  → 不依赖 Vert.x、Redis、Spring 或数据库

converge-routing-core
  → 构造静态候选资源顺序
  → 不读取 Redis、健康、并发或 secret

converge-gateway
  → 使用 Redis 进行动态租约、健康和熔断治理
  → 不访问 PostgreSQL
```

---

## 5. 运行时策略控制面

### 5.1 Flyway

新增：

```text
V11__ai_execution_resource_runtime_policy.sql
```

新增表：

```text
ai_execution_resource_runtime_policy
```

一条 `ExecutionResource` 必须对应一条运行时策略。

建议字段：

| 字段                              | 说明                       |
| ------------------------------- | ------------------------ |
| `id`                            | 主键                       |
| `tenant_id`                     | 租户 ID                    |
| `execution_resource_id`         | 执行资源 ID                  |
| `max_concurrent_requests`       | 最大并发，`0` 表示不限制并发         |
| `consecutive_failure_threshold` | 连续失败阈值                   |
| `failure_reset_after_ms`        | 两次失败间隔超过此值时，连续失败重新从 1 开始 |
| `failure_cooldown_ms`           | 连接、超时、5xx 等失败熔断时长        |
| `rate_limit_cooldown_ms`        | 上游 429 冷却时长              |
| `policy_version`                | 每次策略变更递增                 |
| `created_at`                    | 创建时间                     |
| `updated_at`                    | 更新时间                     |

建议默认值：

```text
max_concurrent_requests = 0
consecutive_failure_threshold = 3
failure_reset_after_ms = 300000
failure_cooldown_ms = 30000
rate_limit_cooldown_ms = 60000
policy_version = 1
```

约束：

```text
tenant_id + execution_resource_id 唯一
max_concurrent_requests >= 0
consecutive_failure_threshold >= 1
所有毫秒配置必须大于 0
policy_version >= 1
ExecutionResource 必须属于同一 tenant
```

迁移要求：

```text
1. 创建新表；
2. 对现有未删除 ExecutionResource 回填默认策略；
3. 不修改旧 Flyway；
4. 不使用数据库触发器维护动态健康；
5. 控制面后续创建 ExecutionResource 时，必须在同一事务中创建默认策略。
```

### 5.2 控制面 API

沿用现有 `AiExecutionResource` Controller 与 Service 路由风格，新增：

```text
读取某执行资源运行时策略
更新某执行资源运行时策略
```

更新必须：

```text
TENANT_OWNER / TENANT_ADMIN
显式租户校验
@Auditable
policy_version + 1
同一事务写入 Snapshot revision / outbox
```

禁止：

```text
控制面直接写 Redis 健康状态
控制面提供手工修改失败次数接口
控制面提供手工篡改租约接口
控制面返回 Gateway leaseId
```

---

## 6. Gateway Snapshot V4

### 6.1 新契约

新增：

```text
GatewayExecutionResourceRuntimePolicySnapshot
```

建议字段：

```text
tenantId
runtimePolicyId
executionResourceId
maxConcurrentRequests
consecutiveFailureThreshold
failureResetAfterMs
failureCooldownMs
rateLimitCooldownMs
policyVersion
```

`GatewayTenantSnapshot` 新增：

```text
executionResourceRuntimePolicies
```

Schema：

```text
VERSION_4
MIN_SUPPORTED_VERSION = VERSION_1
MAX_SUPPORTED_VERSION = VERSION_4
```

### 6.2 投影与校验

新增 outbox change type：

```text
AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED
```

控制面 Projector 必须：

```text
读取当前 tenant 全量策略
写入 immutable Snapshot V4 payload
签名 manifest
切换 current pointer
发布 changed channel
```

Gateway 加载 V4 时必须校验：

```text
每条策略 tenantId 正确
每条策略 policyVersion >= 1
执行资源存在且属于同一 tenant
每个可执行 DIRECT_API 资源最多一条策略
字段范围合法
```

V4 快照中缺少可执行资源策略时：

```text
该 tenant Snapshot 视为无效
保留该 tenant last-known-good
不得部分覆盖该 tenant
```

### 6.3 兼容与发布顺序

Gateway 必须支持：

```text
V1
V2
V3
V4
```

语义：

```text
V1 / V2 / V3：
仅为兼容旧快照加载与回归测试保留；
不具备运行时治理策略；
不得误解析成 V4。

V4：
新控制面唯一发布版本；
所有可执行资源均具备运行时策略。
```

发布顺序：

```text
1. 先发布支持 V1 / V2 / V3 / V4 的 Gateway；
2. 确认 Gateway 正常加载 V3；
3. 再发布控制面，使其开始投影 V4；
4. 确认 Gateway runtime status 正常；
5. 再开启生产流量。
```

---

## 7. Redis 运行时状态模型

### 7.1 命名空间

运行时状态与 Snapshot Redis key 必须隔离：

```text
Snapshot：
converge:gateway:snapshot:*

Runtime：
converge:gateway:runtime:*
```

建议每个资源与策略版本使用同一个 Redis Cluster hash tag：

```text
converge:gateway:runtime:{tenantId:resourceId:policyVersion}:leases
converge:gateway:runtime:{tenantId:resourceId:policyVersion}:health
```

禁止将下面内容写入 Redis runtime state：

```text
raw Client API Key
Authorization
Cookie
request body
response body
runtime secret
Credential
baseUrl
upstreamModelName
```

### 7.2 Lease ZSET

`leases` 使用 ZSET：

```text
member = leaseId
score = leaseExpireAtEpochMillis
```

`leaseId` 必须是不可预测的随机标识，建议由：

```text
gateway process random instance id
+ requestId
+ secure random nonce
```

构造。

禁止将 `leaseId` 返回给客户端、写入 access log 或 `/internal/runtime-status`。

### 7.3 Health Hash

`health` 使用 HASH，最小字段：

```text
state
consecutiveFailureCount
lastFailureAtEpochMillis
openUntilEpochMillis
halfOpenLeaseId
```

状态：

```text
CLOSED
OPEN
HALF_OPEN
```

状态语义：

```text
CLOSED：
正常尝试获取并发租约。

OPEN：
资源处于冷却期，拒绝新请求。

HALF_OPEN：
冷却结束后，允许单个探针请求；
其他候选请求不得进入该资源。

成功探针：
HALF_OPEN → CLOSED。

失败探针：
HALF_OPEN → OPEN。
```

### 7.4 Redis Lua 脚本

所有运行时状态变更必须通过 Redis Lua 脚本执行。

至少实现：

```text
ACQUIRE_LEASE
RENEW_LEASE
COMPLETE_LEASE
```

禁止：

```text
GET 当前并发
→ Java 判断
→ INCR 当前并发

GET circuit state
→ Java 判断
→ SET circuit state
```

理由：

```text
多 Gateway 实例下会发生竞争窗口；
所有关键判断与写入必须在同一个 Redis 原子脚本中完成。
```

脚本要求：

```text
使用 EVALSHA；
遇到 NOSCRIPT 时仅重载当前固定脚本并重试一次；
脚本源码不得按请求动态拼接；
所有访问的 Redis key 必须通过 KEYS 参数显式传入；
同一次脚本的 key 必须共享同一个 Redis Cluster hash tag。
```

### 7.5 ACQUIRE_LEASE 语义

输入：

```text
nowEpochMillis
leaseId
leaseTtlMs
maxConcurrentRequests
policy fields
```

处理：

```text
1. 清除 ZSET 中已过期 lease；
2. 若 health.state == OPEN 且 openUntil > now：
   返回 CIRCUIT_OPEN；
3. 若 OPEN 已到期：
   将当前请求作为唯一 HALF_OPEN probe；
4. 若 health.state == HALF_OPEN：
   返回 HALF_OPEN_BUSY；
5. 若 maxConcurrentRequests > 0 且当前有效 lease 数已满：
   返回 CONCURRENCY_FULL；
6. 写入新 lease；
7. 返回 GRANTED 或 HALF_OPEN_GRANTED。
```

### 7.6 RENEW_LEASE 语义

处理：

```text
1. 清除过期 lease；
2. 检查当前 leaseId 是否仍存在；
3. 存在时延长其 score；
4. HALF_OPEN probe 同时确认 owner 未变化；
5. 返回 RENEWED 或 LEASE_LOST。
```

### 7.7 COMPLETE_LEASE 语义

输入：

```text
leaseId
outcome
nowEpochMillis
```

处理：

```text
1. 删除当前 leaseId；
2. 按 outcome 更新 health state；
3. 清理过期 lease；
4. 设置合理 TTL，避免无边界 Redis key 堆积；
5. 返回安全结果代码。
```

---

## 8. 动态候选资源选择

### 8.1 新增 Routing Core 组件

在 `converge-routing-core` 新增：

```text
StaticRouteCandidatePlanner
StaticRouteCandidate
```

职责：

```text
输入：
StaticRoutePlan
request selection seed

输出：
按 priority 和 weight 排序的静态候选资源列表
```

必须保持纯 Java：

```text
无 Redis
无 Vert.x
无 Spring
无 MyBatis
无 JDBC
无 HTTP
无 secret
无日志
```

### 8.2 候选顺序

候选规则：

```text
1. RouteTarget 按 priority 从高到低遍历；
2. 同一个 RouteTarget priority tier 内，Pool 按 weight 形成确定性加权顺序；
3. 每个 Pool 中仅取最高 PoolMember priority tier；
4. 该 tier 内 Resource 按 weight 形成确定性加权顺序；
5. 高 priority tier 的候选全部动态不可用时，才尝试下一个 lower priority tier；
6. 第一个成功获取 runtime lease 的资源被选中；
7. 获取成功后，只允许发起一次上游请求。
```

候选 seed 必须包含：

```text
requestId
tenantId
publicModelCode
canonicalOperation
snapshotRevision
```

禁止包含：

```text
raw Client API Key
Client API Key secret
Authorization
Cookie
request body
runtime secret
```

### 8.3 Gateway 动态获取流程

```text
StaticRouteCandidatePlanner
  → Candidate A
     → ACQUIRE_LEASE
     → CONCURRENCY_FULL
  → Candidate B
     → ACQUIRE_LEASE
     → CIRCUIT_OPEN
  → Candidate C
     → ACQUIRE_LEASE
     → GRANTED
  → 执行 Candidate C 的上游请求
```

若所有候选资源均不可用：

```text
HTTP 503
code = no_runtime_eligible_resource
```

不得泄露：

```text
resourceId
poolId
routePolicyId
baseUrl
Provider
upstreamModelName
实际并发数
熔断时间
```

---

## 9. Outcome 分类与资源健康

### 9.1 成功或可达

下列结果应清除连续失败，并在 HALF_OPEN 时恢复 CLOSED：

```text
上游 2xx
上游正常完成 SSE
上游 400 / 404 / 409 / 422
```

原因：

```text
这些结果说明请求已成功到达可用上游；
普通客户端请求不合法不应判定资源故障。
```

### 9.2 资源失败

下列结果应增加连续失败计数：

```text
DNS 失败
连接失败
TLS 失败
连接超时
上游 idle timeout
上游 5xx
上游 2xx 但响应协议非法
SSE 在 [DONE] 前异常中断
上游 401 / 403
```

达到 `consecutiveFailureThreshold` 后：

```text
state = OPEN
openUntil = now + failureCooldownMs
```

### 9.3 上游 429

上游 429：

```text
不增加普通连续失败次数；
直接进入 OPEN；
openUntil = now + rateLimitCooldownMs。
```

本阶段不解析或透传上游 `Retry-After`，避免将不可信或异常的上游值直接用于本地状态。

### 9.4 中性结果

下列结果不改变普通健康计数：

```text
Client 主动断开
Gateway draining
Gateway 被关闭
Client 请求在发起上游前被拒绝
租约获取失败
```

若 HALF_OPEN probe 因客户端中断而无法得到有效上游结果：

```text
HALF_OPEN → OPEN
openUntil = now + failureCooldownMs
```

这样避免资源永久卡在 HALF_OPEN。

---

## 10. Lease TTL 与续约

新增 Gateway 配置：

```text
GATEWAY_RUNTIME_REDIS_COMMAND_TIMEOUT_MS
GATEWAY_RUNTIME_LEASE_TTL_MS
GATEWAY_RUNTIME_LEASE_RENEW_INTERVAL_MS
GATEWAY_RUNTIME_MAX_CANDIDATE_ATTEMPTS
```

建议默认值：

```text
GATEWAY_RUNTIME_REDIS_COMMAND_TIMEOUT_MS=1500
GATEWAY_RUNTIME_LEASE_TTL_MS=120000
GATEWAY_RUNTIME_LEASE_RENEW_INTERVAL_MS=30000
GATEWAY_RUNTIME_MAX_CANDIDATE_ATTEMPTS=64
```

校验：

```text
所有值必须大于 0
leaseRenewIntervalMs < leaseTtlMs / 2
runtimeRedisCommandTimeoutMs < leaseRenewIntervalMs
maxCandidateAttempts 必须有合理上限
```

运行规则：

```text
所有真实上游请求均持有 lease；
非流式请求结束时 release；
SSE 流保持期间定时 renew；
客户端断开时 release；
Gateway shutdown 时 best-effort release；
进程崩溃时由 lease TTL 自动清理。
```

续约失败：

```text
第一次续约失败：
记录安全分类，等待下一次续约。

在 lease 到期安全边界前仍无法续约：
best-effort 取消上游请求。

下游 Header 尚未发送：
返回 503 runtime_lease_lost。

下游 SSE Header 已发送：
结束 SSE 流，不再输出 JSON error。
```

初次 ACQUIRE_LEASE 无法访问 Redis：

```text
fail closed
HTTP 503
code = runtime_state_unavailable
不发起上游请求。
```

---

## 11. Gateway 运行时状态接口

新增：

```http
GET /internal/runtime-status
```

只允许返回安全摘要，例如：

```json
{
  "state": "READY",
  "redisAvailable": true,
  "activeLocalLeases": 12,
  "leaseAcquireGrantedCount": 4012,
  "leaseAcquireRejectedCount": 56,
  "renewFailureCount": 0,
  "runtimeStateUnavailableCount": 0
}
```

禁止返回：

```text
tenantId
resourceId
leaseId
baseUrl
Provider
upstreamModelName
runtime secret
Redis key
健康状态详细值
失败请求 body
```

该接口不得扫描全部 Redis key；只允许使用 Gateway 本地安全计数器和连接状态。

---

## 12. 数据面错误

新增或复用错误代码：

| 场景                      | HTTP | code                           |
| ----------------------- | ---: | ------------------------------ |
| 所有候选资源并发满、熔断或冷却         |  503 | `no_runtime_eligible_resource` |
| Redis Runtime State 不可用 |  503 | `runtime_state_unavailable`    |
| 已获取的 Lease 丢失           |  503 | `runtime_lease_lost`           |
| Gateway draining        |  503 | `gateway_shutting_down`        |

仍保留阶段 07 错误：

```text
invalid_api_key
model_access_denied
model_not_found
upstream_connection_error
upstream_timeout
upstream_authentication_failed
upstream_rate_limited
upstream_protocol_error
```

---

## 13. 测试与验收

必须覆盖：

```text
1. runtime policy Flyway 与 tenant 隔离；
2. policy 更新触发 revision / outbox / Snapshot V4；
3. Gateway V1 / V2 / V3 / V4 兼容加载；
4. 单实例 max concurrency；
5. 多 Gateway 实例共享 Redis 并发限制；
6. lease TTL 自动回收；
7. 正常 release；
8. SSE 长连接 renew；
9. client disconnect release；
10. Redis unavailable 初次 acquire fail closed；
11. renew lost 后取消上游；
12. 连续失败熔断；
13. 429 冷却；
14. HALF_OPEN 单探针；
15. HALF_OPEN 成功恢复；
16. HALF_OPEN 失败重新 OPEN；
17. 上游 4xx 不视为资源失败；
18. 上游 401 / 403 视为资源失败；
19. 上游 5xx、timeout、连接失败视为资源失败；
20. 所有高优先级候选不可用时进入低优先级 tier；
21. lease rejection 不触发上游请求；
22. 动态改选后只发起一次上游请求；
23. Redis key / secret / Authorization leak scan；
24. backend root mvn test；
25. git diff --check。
```

---

## 14. New-API / Sub2API 对照原则

本阶段只参考以下能力方向：

```text
多资源健康治理
动态并发控制
429 冷却
资源可用性选择
```

严格禁止：

```text
复制 New-API 或 Sub2API 源码
复制其数据表或接口实现
加入未经授权账户、Cookie、订阅账号池
规避上游服务条款
```

---

## 15. 阶段完成标准

阶段 08 完成后，系统应满足：

```text
一个已经能真实转发的 Chat Completions Gateway
  + 多实例共享并发保护
  + 429 冷却
  + 失败熔断
  + 半开恢复
  + 动态候选资源改选
```

但仍不应具备：

```text
真实 retry
计费
用户限流
Claude
Gemini
WebSocket
账号池
前端管理台
```
