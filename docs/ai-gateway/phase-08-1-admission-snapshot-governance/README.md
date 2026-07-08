# 阶段 08.1｜Gateway 准入同步与快照增量治理

## 1. 阶段背景

阶段 08 已完成，当前系统已经具备：

```text id="nn51ly"
V11__ai_execution_resource_runtime_policy.sql
GatewayExecutionResourceRuntimePolicySnapshot
Snapshot V4
运行时策略 policyVersion
StaticRouteCandidate / StaticRouteCandidatePlanner
Redis runtime namespace
ACQUIRE_LEASE / RENEW_LEASE / COMPLETE_LEASE Lua 脚本
资源并发租约
资源健康熔断
429 冷却
HALF_OPEN 探针
/internal/runtime-status
Chat Completions 请求前动态候选改选
Redis runtime unavailable fail-closed
```

阶段 08 的边界也已经明确：

```text id="voxwls"
不做真实 retry
不做上游请求后的 fallback
不做 RPM / TPM
不做 Client API Key 限流
不做 pricing / billing / ledger
不做 OAuth / Cookie / account pool / sticky session
不做 Claude / Gemini / Responses / Embeddings
不做 WebSocket / Realtime
不修改 React
不让 Gateway 访问 PostgreSQL
```

本阶段建立在阶段 04～08 的实现基础上，不回退、不推翻、不重写既有快照和运行时治理体系。

---

## 2. 本阶段目标

当前 Gateway 快照同步已经具备：

```text id="y7dftw"
PostgreSQL 事实数据
→ revision / outbox
→ Redis immutable payload
→ current manifest
→ Gateway 校验 HMAC / SHA / schema / secret envelope
→ Gateway 本地 copy-on-write snapshot
→ last-known-good
→ initial reconcile
→ periodic reconcile
```

但当前仍存在一个扩展性问题：

```text id="hw6w35"
任意租户发生快照变更
→ Redis Pub/Sub 提示
→ Gateway reconcileAll()
→ 读取 tenant-index
→ 重新加载所有租户快照
→ 重新解密全部资源 secret
→ 重新编译全部 route plan
→ 重新构建全局 Client API Key index
```

在小规模场景下可以接受；但当 tenant、Client API Key、AccessGroup、ModelGrant、Resource、RoutePolicy 增多后，会导致刷新成本、Redis IO、CPU、GC 和内存峰值放大。

本阶段目标：

```text id="h6hhjr"
保留现有 Snapshot / Outbox / Manifest / LKG 架构；
保留阶段 08 Runtime Governance；
将 Pub/Sub 实时刷新从全量 reconcileAll 优化为 tenant 定向 refresh；
revision 未变化时跳过 payload 加载、secret 解封装和 route plan 编译；
Client API Key index 改为按 tenant 增量替换；
增加刷新成本、Key 数量、Grant 数量和快照大小观测；
为后续用户停用、租户停用、授权版本、余额预扣和准入 Redis L2 留出边界。
```

---

## 3. 设计原则

### 3.1 不推翻阶段 04～08

必须保留：

```text id="5w750v"
GatewaySnapshotManifest
GatewayTenantSnapshot
GatewaySnapshotChangedEvent
GatewaySnapshotRedisKeys
GatewaySnapshotCrypto
GatewaySnapshotJson
GatewayLoadedTenantSnapshot
GatewaySnapshotRuntime
GatewaySnapshotRuntimeStatus
GatewayExecutionResourceRuntimePolicySnapshot
Snapshot V4
Runtime Governance Redis Lua
GatewayRuntimeGovernanceRuntime
GatewayRuntimeGovernanceStore
GatewayRuntimeGovernanceStatus
StaticRouteCandidatePlanner
```

不得删除阶段 04～08 已形成的架构后重新实现。

### 3.2 区分三类状态

后续系统必须区分：

```text id="y1mpgd"
A. 静态配置快照
   Provider、Connection、Resource、RuntimePolicy、Model、Route、AccessGroup、ModelGrant
   低频变更，继续走 tenant Snapshot。

B. 准入状态目录
   Client API Key、用户状态、租户状态、授权版本、风控状态
   中频变更，本阶段先做 tenant 增量刷新和 key index 增量替换；
   后续可拆成 Redis L2 准入目录 + Gateway L1 热缓存。

C. 强动态额度状态
   余额、套餐额度、RPM、TPM、预扣、结算、账本
   高频变更，后续计费阶段通过 Redis Lua / Stream / Ledger 处理；
   不进入 Snapshot。
```

本阶段只处理 A 和 B 的刷新治理，不实现 C。

### 3.3 Pub/Sub 只做低延迟提示

Pub/Sub 事件不能作为可信事实来源。

Gateway 收到事件后只能把它当作刷新提示：

```text id="gjyibr"
1. 解析 event；
2. 获取 tenantId / revision；
3. 重新读取 Redis current manifest；
4. 校验 manifest HMAC；
5. 再读取 manifest 指向的 immutable payload；
6. 校验 SHA、schema、tenant、revision；
7. 校验 secret envelope；
8. 校验 runtime policy；
9. 构造新的 GatewayLoadedTenantSnapshot；
10. 原子替换本地状态。
```

### 3.4 请求热路径仍不访问 PostgreSQL

Gateway 正常数据面请求不得访问 PostgreSQL：

```text id="isr2fn"
GET /v1/models
POST /v1/chat/completions
后续 /v1/responses
后续 /v1/embeddings
后续 Claude / Gemini / Realtime
```

请求热路径可以访问：

```text id="u35i3o"
Gateway 本地快照 / key index
Redis Runtime Governance lease / health
后续 Redis Quota Lua
```

但不得访问：

```text id="fx5ye6"
PostgreSQL
MyBatis
Flyway
Spring MVC
控制面 Service
TenantContext
```

---

## 4. 本阶段交付范围

## 4.1 Pub/Sub changed event 改为 tenant 定向刷新

当前 Pub/Sub handler 不应继续无条件：

```java id="q7hud6"
connection.handler(response -> reconcileAll("PUBSUB"));
```

需要改为：

```text id="n7dlxr"
收到 Pub/Sub response
→ 解析 GatewaySnapshotChangedEvent
→ 校验 schemaVersion / tenantId / revision / manifestRedisKey
→ 合法：scheduleTenantRefresh(tenantId, revision, "PUBSUB")
→ 非法：记录安全错误分类，忽略
```

要求：

```text id="5twml7"
1. 合法事件只刷新目标 tenant；
2. 非法事件不得触发全量 reconcile；
3. event 解析失败不得清空本地快照；
4. event 中的 manifestRedisKey 只可用于基本一致性校验，不可直接信任；
5. payload 仍必须通过 current manifest 间接读取；
6. periodic reconcileAll 继续保留作为兜底。
```

---

## 4.2 refreshTenant

新增或改造：

```java id="olft58"
private Future<Void> refreshTenant(String tenantId, Long hintedRevision, String reason)
```

行为：

```text id="q6f7dt"
1. 读取 current manifest；
2. 校验 manifest；
3. 获取本地该 tenant 当前 revision；
4. manifest.revision < localRevision：忽略旧版本；
5. manifest.revision == localRevision：跳过；
6. manifest.revision > localRevision：读取 payload；
7. validatePayload；
8. compileRoutePlans；
9. validateClientAccess；
10. validateRuntimePolicies；
11. 构建 GatewayLoadedTenantSnapshot；
12. 原子替换该 tenant 本地快照；
13. 增量替换该 tenant 的 key index。
```

revision skip 要求：

```text id="lma856"
旧 revision 不得覆盖新 revision；
重复 revision 不得重复解密 secret；
重复 revision 不得重复编译 route plan；
重复 revision 不得重建 key index；
跳过次数需要进入安全指标。
```

---

## 4.3 tenant 级 single-flight / debounce

同一个租户短时间内可能连续出现：

```text id="ty0ybh"
创建 Key
绑定 AccessGroup
修改 ModelGrant
更新 RuntimePolicy
启用 RoutePolicy
禁用 Resource
```

需要实现 tenant 维度刷新合并：

```text id="f71v8f"
同一 tenant 同一时刻最多一个 refresh in-flight；
refresh 过程中收到更高 revision，记录 pendingRevision；
当前 refresh 完成后，如果 pendingRevision 仍高于 localRevision，再执行一次；
不同 tenant 的 refresh 不能互相阻塞；
可配置短 debounce，默认 100ms。
```

新增配置建议：

```text id="l3wt4w"
GATEWAY_SNAPSHOT_TENANT_REFRESH_DEBOUNCE_MS
默认：100

GATEWAY_SNAPSHOT_TENANT_REFRESH_MAX_PENDING
默认：1024

GATEWAY_SNAPSHOT_MAX_EVENT_BYTES
默认：65536
```

---

## 4.4 本地 snapshot 按 tenant 增量替换

继续保留：

```java id="jlii6h"
AtomicReference<Map<String, GatewayLoadedTenantSnapshot>> localSnapshots
```

tenant 定向刷新时使用 copy-on-write 增量替换：

```java id="mabkor"
Map<String, GatewayLoadedTenantSnapshot> current = localSnapshots.get();
Map<String, GatewayLoadedTenantSnapshot> next = new HashMap<>(current);
next.put(tenantId, loaded);
localSnapshots.set(Map.copyOf(next));
```

要求：

```text id="wbl6h3"
1. 单 tenant 新版本校验失败时保留该 tenant 旧版本；
2. 单 tenant 失败不影响其他 tenant；
3. disabled tenant 如果控制面投影为空快照，则应替换为空快照；
4. tenant-index 删除 tenant 时，periodic reconcileAll 负责删除本地 tenant snapshot；
5. tenant 删除与 disabled tenant 的语义必须分开测试。
```

---

## 4.5 Client API Key index 按 tenant 增量替换

当前全局 key index 是：

```text id="h7dj7i"
keyId -> GatewayClientKeyIndexEntry
```

本阶段不要把 Key 完全拆出 Snapshot，但要避免一个 tenant 变化导致所有 tenant key index 重建。

建议结构：

```text id="nksr3n"
tenantKeyIndexes:
  tenantId -> Map<keyId, GatewayClientKeyIndexEntry>

globalClientKeyIndex:
  keyId -> GatewayClientKeyIndexEntry
```

tenant refresh 成功后：

```text id="e5aofo"
1. 从新 tenant snapshot 构建该 tenant 的 key index；
2. copy tenantKeyIndexes，替换 tenantId 对应条目；
3. copy globalClientKeyIndex；
4. 删除该 tenant 旧 key entries；
5. 加入该 tenant 新 key entries；
6. 原子替换 globalClientKeyIndex。
```

要求：

```text id="dyqjbu"
1. 单 tenant 刷新失败不得删除旧 key entries；
2. Key create / disable / revoke / rotate / expiresAt 语义保持正确；
3. keyId 冲突必须显式处理；
4. 请求线程不得看到半更新状态；
5. raw key 不得进入日志、status、异常或指标标签；
6. principal 中的 grant union 语义保持与阶段 06 一致。
```

---

## 4.6 Runtime Policy V4 兼容

阶段 08 已新增 Snapshot V4 与 runtime policy。

本阶段 tenant refresh 必须继续验证：

```text id="dsm8sl"
GatewayExecutionResourceRuntimePolicySnapshot
tenantId 一致
executionResourceId 覆盖关系
policyVersion 合法
maxConcurrentRequests >= 0
consecutiveFailureThreshold 合法
failureResetAfterMs 合法
failureCooldownMs 合法
rateLimitCooldownMs 合法
重复策略拒绝
V4 缺少策略拒绝替换 last-known-good
```

tenant 定向刷新成功后，阶段 08 的 runtime governance 必须读取新 policyVersion，并构造新的 runtime Redis key hash tag，避免旧 lease 污染新策略。

---

## 4.7 Runtime Governance 不退化

不得破坏阶段 08 已实现的行为：

```text id="ua9tyz"
请求前 acquire lease；
并发满时尝试下一个 candidate；
circuit OPEN 时跳过该 resource；
HALF_OPEN 只允许一个探针；
429 独立 cooldown；
SSE 建立后 renew lease；
SSE renew lost 后结束流；
非流式和 SSE 都必须 release；
Redis runtime unavailable fail-closed；
Gateway draining 新请求 503；
不实现上游请求后的 retry / fallback。
```

本阶段只优化配置刷新，不改变阶段 08 的资源治理语义。

---

## 4.8 状态指标

扩展 `/internal/snapshot-status` 或对应状态对象。

允许新增：

```text id="zx95kx"
loadedTenantCount
tenantIndexCount
loadedClientKeyCount
loadedAccessGroupCount
loadedGrantCount
loadedRoutePlanCount
loadedRuntimePolicyCount
snapshotRefreshTotalCount
snapshotRefreshSkippedCount
snapshotRefreshFailedCount
tenantRefreshInFlightCount
tenantRefreshPendingCount
lastTenantRefreshEpochMillis
lastFullReconcileEpochMillis
lastRefreshDurationMs
maxRefreshDurationMs
estimatedSnapshotPayloadBytes
latestErrorCategory
```

禁止输出：

```text id="gi3rwf"
raw API key
完整 keyId
Authorization
Cookie
upstream secret
runtimeSecret
baseUrl
upstreamModelName
resourceId
leaseId
Redis key
nonce
ciphertext
HMAC
payload body
request body
response body
```

---

## 5. 本阶段不做

```text id="hh89pz"
不推翻阶段 04～08
不删除 Snapshot / Manifest / Outbox / LKG
不删除 Runtime Governance
不改写阶段 08 Redis Lua 语义
不实现真实 retry
不实现请求后的 fallback
不实现完整 LRU 冷租户淘汰
不把 Key 完全拆出 Snapshot
不实现 Redis L2 准入目录
不实现用户停用实时失效
不实现租户停用实时失效
不实现余额、预扣、结算、账本
不实现 RPM / TPM
不实现 Client API Key 限流
不实现 OAuth / Cookie / account pool / sticky
不实现 Claude / Gemini / Responses / Embeddings / Realtime
不修改 React
不让 Gateway 请求热路径访问 PostgreSQL
```

---

## 6. 建议实施顺序

```text id="g9k61b"
1. 阅读阶段 08 实施结果，确认 Snapshot V4 和 Runtime Governance 代码位置；
2. 阅读 GatewaySnapshotRuntime 当前 Pub/Sub / reconcileAll / loadTenant / validatePayload / buildClientKeyIndex；
3. 阅读 GatewayRuntimeGovernanceRuntime 与 Chat Completions 整合点；
4. 先补 tenant 定向刷新失败测试；
5. 实现 GatewaySnapshotChangedEvent 解析与安全校验；
6. 实现 refreshTenant；
7. 实现 revision skip；
8. 实现 tenant single-flight / debounce；
9. 改造 key index 为 tenant 增量替换；
10. 保留并回归 reconcileAll；
11. 扩展 snapshot-status 安全指标；
12. 回归阶段 08 runtime governance；
13. 执行 backend 根目录 mvn test；
14. 填写结果文档；
15. 中文 Git 提交。
```

---

## 7. 验收标准

### 7.1 快照刷新

```text id="omvu6v"
Pub/Sub 合法事件只刷新目标 tenant；
非法事件被忽略；
旧 revision 不覆盖新 revision；
重复 revision 跳过 payload 加载；
tenant refresh 失败保留 last-known-good；
周期 reconcileAll 仍能全量恢复；
tenant-index 删除 tenant 后本地状态被删除。
```

### 7.2 Key Index

```text id="wzy8sk"
单 tenant key 变化只重建该 tenant key index；
其他 tenant key entries 不丢失；
Key create 后可认证；
Key disable 后不可认证；
Key revoke 后不可认证；
Key rotate 后旧 raw key 失败，新 raw key 成功；
Key expiresAt 生效；
keyId 冲突有测试和确定性处理。
```

### 7.3 Snapshot V4 与 Runtime Policy

```text id="ff9g8o"
V4 runtime policy 继续投影；
V4 policy 校验继续生效；
policyVersion 更新后 tenant refresh 生效；
runtime Redis key 使用新 policyVersion；
旧 lease 不污染新策略；
阶段 08 lease / health / cooldown / HALF_OPEN 语义不退化。
```

### 7.4 安全

```text id="z1su7j"
日志不输出 raw key / Authorization / Cookie / secret；
status 不输出 keyId / resourceId / leaseId / baseUrl / upstreamModelName；
Gateway 不引入 PostgreSQL / MyBatis / Flyway / Spring MVC 依赖；
Pub/Sub event 不被当成可信事实来源。
```

### 7.5 测试

至少执行：

```bash id="ajcpkk"
mvn -pl converge-gateway -am test
mvn -pl converge-contract,converge-routing-core -am test
mvn -pl converge-web-service,converge-gateway -am test-compile
mvn test
git diff --check
```

并补充针对 tenant 定向刷新、revision skip、key index 增量替换、Snapshot V4 兼容、Runtime Governance 回归的测试。
