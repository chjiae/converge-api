# Codex Prompt｜阶段 08.1：Gateway 准入同步与快照增量治理

你要在当前仓库中实现：

```text id="r1f8mz"
阶段 08.1：Gateway 准入同步与快照增量治理
```

注意：阶段 08 已完成。你必须基于阶段 08 的实际实现继续开发，不得回退、删除或重写阶段 08 的 Runtime Governance。

---

## 一、必须先阅读

先阅读这些文档：

```text id="jhj76w"
docs/ai-gateway/phase-04-gateway-snapshot-sync/README.md
docs/ai-gateway/progress/phase-04-gateway-snapshot-sync-result.md

docs/ai-gateway/phase-05-static-routing/README.md
docs/ai-gateway/progress/phase-05-static-routing-result.md

docs/ai-gateway/phase-06-client-access/README.md
docs/ai-gateway/progress/phase-06-client-access-result.md

docs/ai-gateway/phase-07-openai-direct-forwarding/README.md
docs/ai-gateway/progress/phase-07-openai-direct-forwarding-result.md

docs/ai-gateway/phase-08-runtime-governance/README.md
docs/ai-gateway/progress/phase-08-runtime-governance-result.md

docs/ai-gateway/phase-08-1-admission-snapshot-governance/README.md
```

重点确认阶段 08 已落地的能力：

```text id="j0ydfh"
V11__ai_execution_resource_runtime_policy.sql
GatewayExecutionResourceRuntimePolicySnapshot
GatewayTenantSnapshot Snapshot V4
AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED
StaticRouteCandidate
StaticRouteCandidatePlanner
GatewayRuntimeGovernanceRuntime
GatewayRuntimeGovernanceStore
GatewayRuntimeGovernanceStatus
ACQUIRE_LEASE / RENEW_LEASE / COMPLETE_LEASE Lua
Chat Completions 请求前 acquire lease
Redis runtime unavailable fail-closed
```

然后阅读代码，实际类名以仓库为准：

```text id="wdzp94"
backend/converge-gateway/src/main/java/**/snapshot/**
backend/converge-gateway/src/main/java/**/runtime/**
backend/converge-gateway/src/main/java/**/openai/**
backend/converge-contract/src/main/java/**/gateway/**
backend/converge-routing-core/src/main/java/**
backend/converge-web-service/src/main/java/**/gateway/**
```

必须定位并理解：

```text id="tp7n6u"
GatewaySnapshotRuntime
GatewaySnapshotRuntimeStatus
GatewaySnapshotConfig
GatewayLoadedTenantSnapshot
GatewayClientKeyIndexEntry
GatewayDataPlaneAuthenticator
GatewayOpenAiExecutionTarget
GatewayRuntimeGovernanceRuntime
GatewayRuntimeGovernanceStore
StaticRouteCandidatePlanner
GatewaySnapshotChangedEvent
GatewaySnapshotRedisKeys
GatewayTenantSnapshot
GatewaySnapshotManifest
GatewaySnapshotSchema
GatewaySnapshotCrypto
```

---

## 二、任务目标

当前 Gateway Pub/Sub changed event 仍可能触发全量快照对账和全局 Client API Key index 重建。

本阶段要实现：

```text id="em71ad"
1. Pub/Sub 合法 changed event 只触发 tenant 定向刷新；
2. revision 未变化时跳过 payload 加载、secret 解封装、route plan 编译和 key index 重建；
3. 同 tenant 连续事件通过 single-flight + debounce 合并；
4. Client API Key index 按 tenant 增量替换；
5. periodic reconcileAll 继续保留作为可靠兜底；
6. Snapshot V4 runtime policy 与阶段 08 Runtime Governance 不退化；
7. 增加安全观测指标；
8. Gateway 请求热路径仍不访问 PostgreSQL。
```

---

## 三、禁止事项

禁止：

```text id="wojz8e"
推翻阶段 04～08 快照体系
删除 Snapshot / Manifest / Outbox / LKG
删除 Runtime Governance
改变阶段 08 Redis Lua 的核心语义
实现真实 retry
实现上游请求后的 fallback
把 Gateway 请求热路径改成查询 PostgreSQL
把 Gateway 接入 MyBatis / Flyway / Spring MVC
实现余额、充值、预扣、结算、账本
实现 RPM / TPM
实现 Client API Key 限流
实现用户停用实时失效
实现租户停用实时失效
实现 OAuth / Cookie / account pool / sticky
实现 Claude / Gemini / Responses / Embeddings / Realtime
修改 React 前端
输出 raw key、Authorization、Cookie、secret、baseUrl、resourceId、leaseId、Redis key
```

---

## 四、实现要求

## 4.1 Pub/Sub handler

不要继续在 Pub/Sub handler 中无条件：

```java id="numi3d"
reconcileAll("PUBSUB");
```

改为：

```text id="ygisxf"
Pub/Sub response
→ parse GatewaySnapshotChangedEvent
→ validate event metadata
→ scheduleTenantRefresh(tenantId, revision, "PUBSUB")
```

非法事件：

```text id="ev92yw"
记录安全错误分类；
不得抛出到 event loop；
不得清空本地快照；
不得触发全量 reconcile；
等待 periodic reconcileAll 兜底。
```

---

## 4.2 refreshTenant

新增或改造方法：

```java id="jzc5m1"
private Future<Void> refreshTenant(String tenantId, Long hintedRevision, String reason)
```

逻辑：

```text id="ovkmj0"
读取 current manifest；
校验 manifest HMAC；
获取 local revision；
manifest.revision < local revision：忽略；
manifest.revision == local revision：skip；
manifest.revision > local revision：读取 payload；
校验 payload SHA / schema / tenant / revision；
解封装 secret；
编译 route plan；
校验 Client Access；
校验 Runtime Policy V4；
构造 GatewayLoadedTenantSnapshot；
原子替换该 tenant；
增量替换该 tenant key index。
```

注意：

```text id="dayfjw"
不要信任 event 中的 revision 直接替换本地状态；
event revision 只能用于调度和去重；
最终以 Redis current manifest 为准。
```

---

## 4.3 revision skip

必须在读取 payload 前比较 revision。

要求：

```text id="z1n1gd"
旧 revision 不读取 payload；
重复 revision 不读取 payload；
旧 revision 不解密 secret；
重复 revision 不编译 route plan；
重复 revision 不重建 key index；
skip 计数进入 snapshot-status。
```

---

## 4.4 tenant single-flight / debounce

实现同 tenant 刷新合并：

```text id="gkz1py"
同一 tenant 同时最多一个 refresh in-flight；
in-flight 期间收到更高 revision，记录 pendingRevision；
当前 refresh 完成后，如 pendingRevision 仍高于 localRevision，再刷新一次；
不同 tenant 不互相阻塞；
默认 debounce 100ms；
pending 数量有上限。
```

避免：

```text id="lm9nh6"
无限递归
无限 timer
事件风暴
全局锁阻塞所有 tenant
```

---

## 4.5 key index 增量替换

当前全局 key index 不能每次都从所有 tenant 重建。

实现等价于：

```text id="fx5hpz"
tenantKeyIndexes:
  tenantId -> Map<keyId, GatewayClientKeyIndexEntry>

globalClientKeyIndex:
  keyId -> GatewayClientKeyIndexEntry
```

tenant refresh 成功后：

```text id="dhazsd"
构建该 tenant 新 key index；
删除该 tenant 旧 key entries；
加入该 tenant 新 key entries；
copy-on-write 原子替换 global index；
请求线程不能看到半更新状态。
```

必须保持：

```text id="cltuqs"
Key create 生效；
Key disable 生效；
Key revoke 生效；
Key rotate 生效；
Key expiresAt 生效；
grant union 语义不变；
raw key 不进入日志、异常、status、指标。
```

---

## 4.6 Snapshot V4 与 Runtime Governance 回归

本阶段不能破坏阶段 08。

必须回归：

```text id="s4n5dx"
Snapshot V4 解析；
GatewayExecutionResourceRuntimePolicySnapshot 校验；
policyVersion 更新；
StaticRouteCandidatePlanner；
Chat Completions 请求前 acquire lease；
并发满换下一个 candidate；
circuit OPEN 跳过资源；
429 cooldown；
HALF_OPEN 单探针；
SSE renew；
SSE release；
Redis runtime unavailable fail-closed；
Gateway draining 新请求 503；
不做上游请求后的 retry / fallback。
```

---

## 4.7 状态指标

扩展安全状态输出。

允许：

```text id="qeu959"
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

禁止：

```text id="xvzbtg"
raw key
完整 keyId
Authorization
Cookie
secret
runtimeSecret
baseUrl
upstreamModelName
resourceId
leaseId
Redis key
nonce
ciphertext
HMAC
payload
request body
response body
```

---

## 五、测试要求

必须先补失败测试，再实现。

至少覆盖：

```text id="fuz2az"
1. Pub/Sub changed event 只刷新目标 tenant；
2. 非目标 tenant revision 不变化；
3. 非目标 tenant key entries 不丢失；
4. 重复 revision 跳过 payload 加载；
5. 旧 revision 不覆盖新 revision；
6. 非法 event 被忽略；
7. 单 tenant payload 损坏保留 old snapshot；
8. 单 tenant 损坏不影响其他 tenant；
9. key create 后目标 tenant key index 更新；
10. key disable 后认证失败；
11. key revoke 后认证失败；
12. key rotate 后旧 key 失败新 key 成功；
13. key expiresAt 生效；
14. tenant refresh in-flight 时事件合并；
15. periodic reconcileAll 仍能全量恢复；
16. tenant-index 删除 tenant 后本地 snapshot 与 key index 删除；
17. Snapshot V4 runtime policy 仍校验；
18. policyVersion 更新后 runtime governance 使用新版本；
19. Chat Completions acquire lease 回归；
20. /internal/snapshot-status 不输出敏感信息；
21. Gateway 不引入 PostgreSQL / MyBatis / Flyway / Spring MVC 依赖。
```

---

## 六、验收命令

必须执行：

```bash id="bnah1z"
mvn -pl converge-gateway -am test
mvn -pl converge-contract,converge-routing-core -am test
mvn -pl converge-web-service,converge-gateway -am test-compile
mvn test
git diff --check
```

如果先运行局部测试，最终仍必须从 backend 根目录执行完整 `mvn test`。

---

## 七、结果文档

完成后填写：

```text id="iw88qp"
docs/ai-gateway/progress/phase-08-1-admission-snapshot-governance-result.md
```

必须说明：

```text id="ga31u7"
阶段 08 是否已作为前置；
是否保留 Runtime Governance；
Pub/Sub 是否改为 tenant 定向刷新；
revision skip 是否生效；
single-flight / debounce 是否生效；
key index 是否支持 tenant 增量替换；
Snapshot V4 policy 是否保持兼容；
periodic reconcileAll 是否仍保留；
Gateway 是否仍不访问 PostgreSQL；
哪些测试通过；
哪些范围明确未做；
后续 Redis L2 准入目录、用户停用、租户停用、余额预扣如何演进。
```

---

## 八、提交

示例提交：

```bash id="d8ievv"
git add backend docs
git diff --cached --check
git commit -m "feat(网关): 优化准入同步与快照增量刷新"
```

如果只提交本文档：

```bash id="xlufty"
git add \
  docs/ai-gateway/phase-08-1-admission-snapshot-governance/README.md \
  docs/ai-gateway/phase-08-1-admission-snapshot-governance/CODEX-PROMPT.md \
  docs/ai-gateway/progress/phase-08-1-admission-snapshot-governance-result.md

git diff --cached --check
git commit -m "docs(网关): 新增准入同步与快照增量治理阶段计划"
```
