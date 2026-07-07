# Codex 执行提示词｜阶段 08

请在 Converge API 仓库根目录完整实施：

```text
docs/ai-gateway/phase-08-runtime-governance/README.md
```

## 必读文件

```text
AGENTS.md
docs/ai-gateway/phase-01-gateway-runtime/
docs/ai-gateway/phase-02-control-plane-catalog/
docs/ai-gateway/phase-03-credential-resource/
docs/ai-gateway/phase-04-gateway-snapshot-sync/
docs/ai-gateway/phase-05-static-routing/
docs/ai-gateway/phase-06-client-access/
docs/ai-gateway/phase-07-openai-direct-forwarding/
docs/ai-gateway/progress/phase-01-*.md
docs/ai-gateway/progress/phase-02-*.md
docs/ai-gateway/progress/phase-03-*.md
docs/ai-gateway/progress/phase-04-*.md
docs/ai-gateway/progress/phase-05-*.md
docs/ai-gateway/progress/phase-06-client-access-result.md
docs/ai-gateway/progress/phase-07-openai-direct-forwarding-result.md
docs/ai-gateway/phase-08-runtime-governance/README.md
```

## 当前实际基线

阶段 07 已完成，已知提交：

```text
7284b17968e75f7b7feebc2fe57e85838676748f
feat(网关): 新增 OpenAI 直连执行与模型映射
```

当前 Gateway 已具备：

```text
独立 Vert.x Gateway
Gateway Snapshot V1 / V2 / V3
Client API Key 本地认证
AccessGroup 精确授权
StaticRoutePlan
StaticRouteRequestSelector
GatewayExecutionRuntime
共享上游 HttpClient
POST /v1/chat/completions
非流式 JSON 中转
SSE 中转
请求与响应模型映射
上游 runtime secret 内存解封装
Gateway 不访问 PostgreSQL
```

当前未具备：

```text
运行时资源策略
跨实例并发控制
Redis lease
lease renew
动态资源健康
熔断
429 cooldown
half-open probe
真实 retry / failover
用户级 RPM / TPM
计费
Claude
Gemini
WebSocket
React 管理台
```

先审查真实代码、Flyway、实体、Mapper XML、Snapshot Projector、GatewaySnapshotRuntime、GatewayExecutionRuntime、Chat Completions Handler、SSE Executor、routing-core 和现有测试。

先输出简短内容：

```text
当前代码发现
预计修改文件
动态状态风险点
```

随后立即连续实施，不等待确认。

---

## 执行权限

已授予完成本阶段所需的全部普通工程操作权限。

可以：

```text
读取、修改、创建仓库文件
新增 Flyway
新增实体、Mapper XML、Service、Controller、DTO、测试
新增 Snapshot V4 契约
新增 routing-core 纯 Java 类和测试
新增 Gateway Redis Runtime Store、Lua Script、Lease、Health Runtime、状态接口和测试
运行 Maven、Testcontainers、Docker 测试
连续修复失败
Git add / commit
```

不要因为以下情况停止询问：

```text
测试红灯
Maven 依赖下载
Testcontainers
Redis 脚本调试
命名适配
Mapper XML
配置项
现有接口命名不完全一致
```

只有真实无法继续的外部阻塞，才允许在最终报告明确说明。

---

## A. 严格阶段目标

实现：

```text
Execution Resource Runtime Policy
  → Gateway Snapshot V4
  → Redis Atomic Lease
  → Concurrent Limit
  → Failure Circuit
  → 429 Cooldown
  → Half-open Probe
  → Dynamic Candidate Selection
```

必须继续遵守：

```text
Gateway 不访问 PostgreSQL
Gateway 不依赖 Spring / MyBatis / TenantContext
Gateway 不读取 AI_CREDENTIAL_* 数据库加密密钥
Gateway 不保存 Client raw key
Gateway 不记录 runtime secret
Gateway 不记录 Authorization、Cookie、request body、response body
```

---

## B. 控制面与 Flyway

新增：

```text
V11__ai_execution_resource_runtime_policy.sql
```

新增 tenant-scoped 一对一运行时策略实体。

最小字段：

```text
id
tenantId
executionResourceId
maxConcurrentRequests
consecutiveFailureThreshold
failureResetAfterMs
failureCooldownMs
rateLimitCooldownMs
policyVersion
createdAt
updatedAt
```

要求：

```text
tenant_id + execution_resource_id 唯一
maxConcurrentRequests >= 0
其他数值字段 > 0
policyVersion >= 1
所有关联必须显式 tenant 校验
```

迁移必须：

```text
对现有未删除 ExecutionResource 创建默认策略
后续创建 ExecutionResource 时在同一事务中创建默认策略
不得修改既有 Flyway
不得通过数据库触发器维护实时健康
```

控制面实现：

```text
读取运行时策略
更新运行时策略
TENANT_OWNER / TENANT_ADMIN
AiCatalogTenantGuard 显式 tenant 校验
@Auditable
更新时 policyVersion + 1
同一事务产生 revision / outbox
```

遵循现有 Controller、DTO、Mapper XML、Result、PageResult 和错误风格。

不要猜测当前项目 API 路径；以现有 `AiExecutionResource` 控制器路由风格扩展。

---

## C. Snapshot V4

在 `converge-contract` 新增：

```text
GatewayExecutionResourceRuntimePolicySnapshot
```

并扩展：

```text
GatewayTenantSnapshot
GatewaySnapshotSchema
GatewaySnapshotJson
相关契约测试
```

V4 policy 字段最小集合：

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

要求：

```text
Gateway 保留 V1 / V2 / V3 / V4 加载兼容
控制面本阶段发布 V4
新增 AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED outbox change type
Projector 继续复用 immutable payload、manifest HMAC、checksum、LKG 和 changed channel
V4 中缺少可执行资源策略时 fail closed，并保留 tenant LKG
```

禁止：

```text
将 Redis lease、失败次数、熔断状态写入 Snapshot
将 runtime secret 写入 Runtime Policy
将 Client API Key 写入 Runtime Policy
```

---

## D. Routing Core：候选计划

在 `converge-routing-core` 新增：

```text
StaticRouteCandidatePlanner
StaticRouteCandidate
```

不要修改或误用仅预览语义的：

```text
StaticRoutePreviewSelector
```

新 planner 必须：

```text
输入 StaticRoutePlan + safe seed
输出确定性静态候选顺序
按 RouteTarget priority 从高到低
同 tier Pool 使用 weight 形成顺序
每个 Pool 仅使用最高 PoolMember priority tier
Resource 在该 tier 内按 weight 形成顺序
```

要求：

```text
无 Redis
无 Vert.x
无 Spring
无数据库
无 HTTP
无 secret
无日志
```

seed 允许：

```text
requestId
tenantId
publicModelCode
canonicalOperation
snapshotRevision
```

seed 禁止：

```text
raw Client API Key
Authorization
Cookie
request body
upstream secret
```

增加纯 Java 测试覆盖：

```text
priority
weight
同 seed 稳定性
候选不重复
高 priority 全部拒绝后才进入低 priority
```

---

## E. Gateway Runtime Governance

新增或调整 Gateway 组件，建议职责如下：

```text
GatewayRuntimeGovernanceConfig
GatewayRuntimeGovernanceRuntime
GatewayRuntimeStateStore
GatewayRuntimeLease
GatewayRuntimeLeaseAcquireResult
GatewayRuntimeLeaseOutcome
GatewayRuntimeStatus
GatewayRuntimeStatusHandler
```

可根据现有包结构微调名称，但职责必须明确。

### Redis 连接

运行时 Redis：

```text
使用独立 Redis Client / Connection 生命周期
复用现有 Gateway Redis URI
不复用 SnapshotRuntime 的内部命令连接对象
不让 SnapshotRuntime 承担每请求 lease 操作
```

新增配置：

```text
GATEWAY_RUNTIME_REDIS_COMMAND_TIMEOUT_MS
GATEWAY_RUNTIME_LEASE_TTL_MS
GATEWAY_RUNTIME_LEASE_RENEW_INTERVAL_MS
GATEWAY_RUNTIME_MAX_CANDIDATE_ATTEMPTS
```

建议默认：

```text
1500
120000
30000
64
```

启动期校验：

```text
全部 > 0
renewInterval < leaseTtl / 2
redisCommandTimeout < renewInterval
candidateAttempts 有合理上限
```

### Redis Key

运行时 key 必须与 Snapshot key 隔离：

```text
converge:gateway:runtime:{tenantId:resourceId:policyVersion}:leases
converge:gateway:runtime:{tenantId:resourceId:policyVersion}:health
```

要求：

```text
同一次 Lua Script 使用的全部 key 共享相同 Redis Cluster hash tag
禁止动态生成 Lua 源码
所有 key 通过 KEYS 显式传入
所有普通参数通过 ARGV 传入
```

### Lua Script

至少实现：

```text
ACQUIRE_LEASE
RENEW_LEASE
COMPLETE_LEASE
```

必须使用：

```text
EVALSHA
NOSCRIPT 时固定脚本重载后重试一次
```

禁止：

```text
GET → Java 判断 → INCR
GET → Java 判断 → SET
分多次 Redis 调用完成 acquire
分多次 Redis 调用完成 release + health update
```

### ACQUIRE_LEASE

必须原子完成：

```text
清理过期 lease
判断 OPEN 冷却
判断 HALF_OPEN 单探针
判断 max concurrent
写入 leaseId
返回安全 acquire result
```

结果至少包含：

```text
GRANTED
HALF_OPEN_GRANTED
CONCURRENCY_FULL
CIRCUIT_OPEN
HALF_OPEN_BUSY
RUNTIME_STATE_UNAVAILABLE
```

### RENEW_LEASE

必须：

```text
验证 lease owner
延长 lease expiry
保护 HALF_OPEN probe owner
返回 RENEWED 或 LEASE_LOST
```

### COMPLETE_LEASE

必须原子完成：

```text
删除当前 lease
按 outcome 更新健康状态
设置 circuit OPEN / CLOSED / HALF_OPEN
执行 TTL 清理
```

---

## F. Outcome 分类

实现明确的 outcome enum，不得直接根据 HTTP status 在多个类中分散判断。

建议分类：

```text
SUCCESS
REACHABLE_CLIENT_REJECTION
UPSTREAM_RATE_LIMITED
UPSTREAM_AUTH_FAILURE
UPSTREAM_CONNECTION_FAILURE
UPSTREAM_TIMEOUT
UPSTREAM_SERVER_FAILURE
UPSTREAM_PROTOCOL_FAILURE
CLIENT_CANCELLED
GATEWAY_SHUTDOWN
LEASE_LOST
```

语义：

```text
SUCCESS：
2xx 或正常完成 SSE，重置连续失败。

REACHABLE_CLIENT_REJECTION：
普通 4xx，例如 400 / 404 / 409 / 422；
重置连续失败；
HALF_OPEN 时视为上游可达并 CLOSED。

UPSTREAM_RATE_LIMITED：
429；
直接 OPEN，使用 rateLimitCooldownMs；
不增加普通 failure count。

UPSTREAM_AUTH_FAILURE：
401 / 403；
视为资源问题；
进入失败熔断流程。

UPSTREAM_CONNECTION_FAILURE：
DNS / connect / TLS；
进入失败熔断流程。

UPSTREAM_TIMEOUT：
进入失败熔断流程。

UPSTREAM_SERVER_FAILURE：
5xx；
进入失败熔断流程。

UPSTREAM_PROTOCOL_FAILURE：
2xx response 非法、SSE 非正常中断；
进入失败熔断流程。

CLIENT_CANCELLED / GATEWAY_SHUTDOWN：
普通 CLOSED 状态下不改变失败计数；
HALF_OPEN 探针中则恢复 OPEN，避免永久卡住。
```

---

## G. 动态资源选择与上游执行整合

调整 Stage 07 的上游执行路径：

```text
StaticRouteCandidatePlanner
  → 逐个候选调用 ACQUIRE_LEASE
  → 首个 GRANTED / HALF_OPEN_GRANTED 候选
  → 构造 GatewayOpenAiExecutionTarget
  → 发起唯一一次上游 HTTP 请求
```

规则：

```text
动态 rejection 发生在发出上游请求之前
高 priority tier 的所有候选动态不可用后，才尝试 lower priority tier
任何一个 lease 成功后，停止继续尝试
上游请求发送后不允许 retry、fallback 或二次路由
```

所有候选拒绝：

```text
503 no_runtime_eligible_resource
不发起上游请求
不泄露资源、池、路由、URL 或并发数量
```

Redis Runtime State 不可用：

```text
503 runtime_state_unavailable
fail closed
不发起上游请求
```

---

## H. Lease 生命周期与 SSE

每个已发起的上游请求必须持有 `GatewayRuntimeLease`。

### 非流式

```text
acquire
→ upstream request
→ response / error outcome
→ complete + release
```

### SSE

```text
acquire
→ upstream SSE
→ 周期 renew
→ [DONE] / upstream end / client disconnect / failure
→ complete + release
```

续约失败：

```text
第一次失败：记录安全分类；
在安全边界前再次续约；
仍无法续约：best-effort cancel upstream。

Headers 未发送：
503 runtime_lease_lost。

SSE Headers 已发送：
结束流，不再写 JSON error。
```

客户端断开：

```text
best-effort cancel upstream
best-effort release lease
不得 retry
不得记录 request body
```

Gateway draining：

```text
拒绝新请求
已建立请求正常收尾
超时后关闭 Client
best-effort release active leases
```

---

## I. Runtime Status

新增：

```text
GET /internal/runtime-status
```

只返回本地安全摘要：

```text
state
redisAvailable
activeLocalLeases
leaseAcquireGrantedCount
leaseAcquireRejectedCount
renewFailureCount
runtimeStateUnavailableCount
```

禁止：

```text
tenantId
resourceId
leaseId
Redis key
baseUrl
Provider
upstream model
secret
Authorization
request / response body
```

不得扫描全部 Redis key。

---

## J. 日志与安全

允许记录：

```text
requestId
tenantId
clientApiKeyId
publicModelCode
operation
stream
snapshotRevision
安全 outcome category
HTTP status
耗时
lease acquire 结果分类
```

禁止记录：

```text
raw Client API Key
Authorization
Cookie
x-api-key
request body
response body
messages
runtime secret
baseUrl
upstreamModelName
Credential
leaseId
Redis key
Lua ARGV
```

执行一次：

```text
rg "Authorization|Cookie|secret|leaseId|baseUrl|upstreamModelName" backend/converge-gateway
```

人工检查日志与异常拼接点，确认敏感字段未输出。

---

## K. 测试要求

### 控制面

```text
Flyway V11
默认策略回填
资源创建自动创建策略
租户隔离
更新策略 policyVersion + 1
更新触发 revision / outbox
Snapshot V4 契约
```

### Routing Core

```text
priority
weight
候选顺序稳定
候选不重复
lower priority fallback
无动态状态依赖
```

### Gateway Runtime Store

必须使用真实 Redis 集成测试或 Testcontainers：

```text
单资源并发上限
两个 Gateway Runtime 并发抢占同一资源
TTL 自动清理
renew 成功
renew 后 lease lost
release 幂等
OPEN 拒绝
429 cooldown
连续失败 threshold
HALF_OPEN 单探针
HALF_OPEN 成功恢复 CLOSED
HALF_OPEN 失败回到 OPEN
Redis unavailable
NOSCRIPT 重载
```

### Gateway Chat Completions 回归

```text
有 lease 才发起上游请求
lease rejection 不发上游请求
所有高优先级资源不可用时改选低优先级候选
上游请求只发一次
非流式 release
SSE renew
SSE client disconnect release
SSE headers 后 lease lost 不写 JSON error
阶段 07 非流式 / SSE / model mapping 回归
V1 / V2 / V3 / V4 Snapshot 回归
GET /v1/models 回归
```

### 全量验证

```bash
cd backend
mvn test
git diff --check
```

---

## L. 严格禁止

```text
不实现真实 retry
不实现已发请求后的 fallback
不实现用户 RPM / TPM
不实现 Client API Key 限流
不实现价格、账本、余额或支付
不实现 OAuth、Cookie、账号池或 sticky session
不实现 Claude、Gemini、Responses、Embeddings、Realtime
不修改 React
不让 Gateway 访问 PostgreSQL
不把 lease / health state 写入 PostgreSQL
不把 runtime state 放入 Snapshot
不把 Redis 动态状态暴露给客户端
```

---

## M. 最终交付

完成后必须填写：

```text
docs/ai-gateway/progress/phase-08-runtime-governance-result.md
```

建议提交拆分：

```text
feat(控制面): 新增执行资源运行时策略
feat(快照): 发布资源运行时策略 V4
feat(路由): 新增动态候选资源计划
feat(网关): 新增 Redis 租约与资源熔断治理
test(网关): 覆盖动态调度与租约治理
docs: 补充运行时资源治理实施结果
```

最终报告必须包含：

```text
前置提交与代码审查结论
Flyway 与数据模型
Snapshot V4
Redis key 与脚本语义
Lease acquire / renew / complete 行为
失败与 429 分类
HALF_OPEN 行为
动态候选选择规则
所有测试命令与结果
安全扫描结果
未完成项与风险
Git 提交 Hash
```
