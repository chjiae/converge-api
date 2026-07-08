# 阶段 08.1｜Gateway 准入同步与快照增量治理｜实施结果

> 本文件不得记录任何真实 Client API Key、上游 API Key、Authorization、Cookie、request body、response body、baseUrl、resourceId、leaseId、upstream model、nonce、ciphertext、HMAC、Redis payload 或 secret。

## 1. 实施时间与提交

* 开始时间：2026-07-08 09:20:00 +08:00
* 完成时间：2026-07-08 09:48:00 +08:00
* Git 提交 Hash：本文件随本阶段提交一起提交，真实 Hash 见最终报告
* 实施分支：`master`
* 开始时 `master` 最新提交：`7ddc8b8 feat(网关): 实现运行时资源治理`
* 阶段 08 是否已完成：是
* 阶段 08 提交 Hash：`7ddc8b8`
* 本阶段是否基于 Snapshot V4 与 Runtime Governance 实施：是

---

## 2. 阶段 08 前置复核

* V11 runtime policy 表是否存在：是，`V11__ai_execution_resource_runtime_policy.sql`
* `GatewayExecutionResourceRuntimePolicySnapshot` 是否存在：是
* Snapshot V4 是否存在：是
* `GatewaySnapshotSchema.CURRENT_VERSION` 当前值：`VERSION_4`
* `StaticRouteCandidatePlanner` 是否存在：是
* Redis Runtime Governance Lua 是否存在：是，位于 `GatewayRuntimeStateStore`
* `/internal/runtime-status` 是否存在：是
* Chat Completions 是否已在请求前 acquire lease：是
* Redis runtime unavailable 是否 fail-closed：是
* Gateway PostgreSQL 访问是否仍未新增：是

阶段 08 已实现能力摘要：

```text
阶段 08 已具备 Snapshot V4、执行资源 runtime policy、候选资源规划、Redis Lua lease acquire/renew/complete、
Chat Completions 请求前准入、SSE 续租与释放、非流式释放、Redis 不可用 fail-closed、/internal/runtime-status。
本阶段未改写这些能力，只在快照同步层改造 Pub/Sub 定向刷新、revision skip、tenant single-flight
和本地 key index 增量替换。
```

---

## 3. 实施前问题复核

实施前当前行为：

```text
Pub/Sub changed event 是否触发 reconcileAll：是。
是否读取全部 tenant-index：是。
是否加载全部租户 payload：是。
是否重新解密全部 resource secret：是。
是否重新编译全部 route plan：是。
是否重建全局 Client API Key index：是。
revision 相同时是否重复加载 payload：是。
同 tenant 连续事件是否可能刷新风暴：是，缺少 tenant 级 single-flight / debounce。
```

结论：

```text
阶段 04 的全量对账语义安全可靠，但阶段 08 引入 runtime policy 和候选资源后，单租户配置变更会导致
全量租户、secret、route plan、client key index 一起刷新。阶段 08.1 需要保留 initial/periodic
reconcileAll 作为可靠兜底，同时把 Pub/Sub changed event 改为只提示目标 tenant 刷新。
```

---

## 4. Pub/Sub tenant 定向刷新

* 是否解析 `GatewaySnapshotChangedEvent`：是
* 合法事件是否只刷新目标 tenant：是
* 非法事件处理方式：记录安全错误分类并忽略，不触发 full reconcile
* 解析失败是否影响本地快照：否
* 是否仍保留 initial reconcile：是
* 是否仍保留 periodic reconcileAll：是
* Pub/Sub event 是否仍只作为提示：是
* 是否仍重新读取 Redis current manifest：是
* 是否仍校验 manifest HMAC：是
* 是否仍校验 payload SHA：是

关键类 / 方法：

```text
GatewaySnapshotRuntime#handlePubSubMessage
GatewaySnapshotRuntime#handleChangedEventPayload
GatewaySnapshotRuntime#validateChangedEvent
GatewaySnapshotRuntime#scheduleTenantRefresh
GatewaySnapshotRuntime#refreshTenant
```

---

## 5. refreshTenant 与 revision skip

* 是否新增 / 改造 `refreshTenant`：是
* manifest.revision < localRevision 处理：跳过，不读取 payload
* manifest.revision == localRevision 处理：跳过，不读取 payload
* manifest.revision > localRevision 处理：读取 payload、校验、解封装、编译、替换目标 tenant
* skip 时是否避免 payload 加载：是
* skip 时是否避免 secret 解封装：是
* skip 时是否避免 route plan 编译：是
* skip 时是否避免 key index 重建：是
* skip 计数是否进入状态指标：是，`snapshotRefreshSkippedCount`

测试覆盖：

```text
GatewaySnapshotIncrementalRefreshTest#revisionSkipAvoidsPayloadLoadBeforeReadingMissingPayload
```

---

## 6. tenant single-flight / debounce

* 是否实现 tenant 级 single-flight：是
* 同 tenant 是否最多一个 refresh in-flight：是
* in-flight 期间更高 revision 是否记录 pending：是
* refresh 完成后是否处理 pending：是
* 不同 tenant 是否互不阻塞：是
* debounce 默认值：100ms
* pending 上限：1024 个 tenant
* 是否避免无限递归 / 刷新风暴：是，pending 合并后用 Vert.x timer 串行调度

关键类 / 方法：

```text
GatewaySnapshotRuntime#scheduleTenantRefresh
GatewaySnapshotRuntime#startTenantRefresh
GatewaySnapshotRuntime#finishTenantRefresh
GatewaySnapshotRuntime.TenantRefreshState
GatewaySnapshotConfig#tenantRefreshDebounceMs
GatewaySnapshotConfig#tenantRefreshMaxPending
```

---

## 7. 本地 snapshot 增量替换

* 是否继续使用 copy-on-write：是
* tenant 刷新成功是否只替换该 tenant：是
* tenant 刷新失败是否保留旧版本：是
* 单 tenant 失败是否不影响其他 tenant：是
* disabled tenant 空快照语义是否保持：是
* tenant-index 删除 tenant 后本地状态是否删除：是，由 periodic reconcileAll 处理
* periodic reconcileAll 是否仍能全量恢复：是

关键类 / 方法：

```text
GatewaySnapshotRuntime#replaceTenantSnapshot
GatewaySnapshotRuntime#loadTenantSnapshot
GatewaySnapshotRuntime#reconcileAll
GatewayLoadedTenantSnapshot#estimatedPayloadBytes
```

---

## 8. Client API Key index 增量替换

* 是否新增 tenant 级 key index：是
* 是否保留 global key index：是
* 单 tenant key 变化是否只重建该 tenant key index：是
* 其他 tenant key entries 是否不受影响：是
* Key create 是否生效：是，由 V3/V4 snapshot 中 key 列表刷新后生效
* Key disable 是否生效：是
* Key revoke 是否生效：是
* Key rotate 是否生效：是
* Key expiresAt 是否生效：是
* keyId 冲突处理方式：同 tenant 或跨 tenant 冲突均 fail closed，拒绝替换本地快照
* grant union 语义是否保持：是
* raw key 是否仍只在 create / rotate 返回一次：是，本阶段未修改控制面 key 生成逻辑
* 日志、异常、status 是否不输出 raw key /完整 keyId / verifier：是

关键类 / 方法：

```text
GatewaySnapshotRuntime#buildTenantClientKeyIndexes
GatewaySnapshotRuntime#buildTenantClientKeyIndex
GatewaySnapshotRuntime#buildGlobalClientKeyIndex
GatewaySnapshotRuntime#replaceTenantSnapshot
GatewaySnapshotRuntime#authenticate
```

---

## 9. Snapshot V4 与 Runtime Policy 兼容

* V4 runtime policy 是否仍投影：是
* V4 runtime policy 是否仍校验：是
* 缺少 policy 是否仍拒绝替换 LKG：是
* policyVersion 更新是否生效：是，tenant refresh 替换目标 tenant 后 runtime policy 随快照更新
* runtime Redis key 是否继续包含 policyVersion：是
* 旧 lease 是否不会污染新策略：是，沿用阶段 08 的 policyVersion key tag
* Gateway Runtime Governance 是否读取最新策略：是

测试覆盖：

```text
GatewaySnapshotRuntimeTest
GatewayRuntimeStateStoreTest
GatewayOpenAiDirectForwardingTest
GatewaySnapshotIncrementalRefreshTest
```

---

## 10. Runtime Governance 回归

阶段 08 行为是否保持：

```text
请求前 acquire lease：是。
并发满尝试下一个 candidate：是。
circuit OPEN 跳过资源：是。
429 cooldown：是。
HALF_OPEN 单探针：是。
SSE renew：是。
SSE release：是。
非流式 release：是。
Redis runtime unavailable fail-closed：是。
Gateway draining 新请求 503：是。
无上游请求后 retry：否，阶段边界仍禁止 retry。
无上游请求后 fallback：否，阶段边界仍禁止 fallback。
```

结论：

```text
阶段 08 runtime governance 保持原语义。本阶段只优化快照和准入配置同步，不新增真实 retry、
fallback、计费、限流、健康探测或账号池。
```

---

## 11. 状态指标与安全输出

新增 / 调整指标：

```text
loadedTenantCount: 已存在，继续输出。
tenantIndexCount: 新增，本地 tenant key index 数量。
loadedClientKeyCount: 新增，已加载 client key 数量。
loadedAccessGroupCount: 新增，已加载访问组数量。
loadedGrantCount: 新增，已加载授权数量。
loadedRoutePlanCount: 新增，已加载路由计划数量。
loadedRuntimePolicyCount: 新增，已加载 runtime policy 数量。
snapshotRefreshTotalCount: 新增，刷新总次数。
snapshotRefreshSkippedCount: 新增，revision skip 次数。
snapshotRefreshFailedCount: 新增，刷新失败次数。
tenantRefreshInFlightCount: 新增，tenant 定向刷新运行中数量。
tenantRefreshPendingCount: 新增，tenant 定向刷新 pending 数量。
lastTenantRefreshEpochMillis: 新增，最近 tenant refresh 时间。
lastFullReconcileEpochMillis: 新增，最近 full reconcile 时间。
lastRefreshDurationMs: 新增，最近刷新耗时。
maxRefreshDurationMs: 新增，最大刷新耗时。
estimatedSnapshotPayloadBytes: 新增，本地已加载快照 payload 估算字节数。
latestErrorCategory: 已存在，继续只输出安全分类。
```

安全检查：

```text
是否输出 raw key：否。
是否输出完整 keyId：否。
是否输出 Authorization：否。
是否输出 Cookie：否。
是否输出 upstream secret：否。
是否输出 runtimeSecret：否。
是否输出 baseUrl：否。
是否输出 upstreamModelName：否。
是否输出 resourceId：否。
是否输出 leaseId：否。
是否输出 Redis key：否。
是否输出 nonce：否。
是否输出 ciphertext：否。
是否输出 HMAC：否。
是否输出 payload：否。
是否输出 request body：否。
是否输出 response body：否。
```

结论：

```text
/internal/ready 与 /internal/snapshot-status 仅输出数量、状态、时间与安全错误分类。
敏感字段扫描未发现网关日志或 status 输出泄露 Authorization、Cookie、runtimeSecret、baseUrl、
upstreamModelName、leaseId、Redis key、HMAC 或 payload。
```

---

## 12. 测试与验证

执行命令：

```bash
mvn -pl converge-gateway -am test
# 结果：成功。

mvn -pl converge-contract,converge-routing-core -am test
# 结果：成功。

mvn -pl converge-web-service,converge-gateway -am test-compile
# 结果：成功。

mvn test
# 结果：成功，完整 backend reactor 通过。

git diff --check
# 结果：成功；仅有 Git 换行符工作区提示，无 whitespace error。
```

新增 / 修改测试：

```text
测试类：GatewaySnapshotIncrementalRefreshTest
测试方法：
- pubSubChangedEventRefreshesOnlyTargetTenantAndKeepsOtherTenantKeyIndex
- revisionSkipAvoidsPayloadLoadBeforeReadingMissingPayload
- invalidEventDoesNotTriggerFullReconcileAndPeriodicReconcileCanRemoveTenant
覆盖内容：tenant 定向刷新、非目标租户隔离、key rotate 增量生效、revision skip、非法事件忽略、
periodic reconcileAll 删除 tenant 和 key index。
```

必须覆盖场景：

```text
Pub/Sub changed event 只刷新目标 tenant：已覆盖。
非目标 tenant revision 不变化：已覆盖。
非目标 tenant key entries 不丢失：已覆盖。
重复 revision 跳过 payload 加载：已覆盖。
旧 revision 不覆盖新 revision：已通过 revision 比较 fail closed 覆盖。
非法 event 被忽略：已覆盖。
单 tenant payload 损坏保留 old snapshot：由 GatewaySnapshotRuntimeTest 和定向刷新 fail closed 语义覆盖。
单 tenant 损坏不影响其他 tenant：由 tenant 局部替换和 corrupt tenant set 覆盖。
Key create 后目标 tenant key index 更新：由同一索引构建路径覆盖。
Key disable 后认证失败：阶段 06 回归覆盖。
Key revoke 后认证失败：阶段 06 回归覆盖。
Key rotate 后旧 key 失败新 key 成功：已覆盖。
Key expiresAt 生效：阶段 06 回归覆盖。
tenant refresh in-flight 时事件合并：通过 TenantRefreshState single-flight / pending 逻辑实现。
periodic reconcileAll 全量恢复：已覆盖。
tenant-index 删除 tenant 后本地状态删除：已覆盖。
Snapshot V4 runtime policy 校验：阶段 08 回归覆盖。
policyVersion 更新后 runtime governance 使用新版本：阶段 08 回归覆盖。
Chat Completions acquire lease 回归：完整 mvn test 覆盖。
/internal/snapshot-status 不输出敏感信息：代码与扫描覆盖。
Gateway 不引入 PostgreSQL / MyBatis / Flyway / Spring MVC 依赖：扫描覆盖。
```

---

## 13. 阶段边界复核

本阶段是否推翻阶段 04～08：

```text
是 / 否：否。
说明：保留 Snapshot V1/V2/V3/V4 兼容、initial reconcile、periodic reconcileAll、Manifest HMAC、
payload SHA、SecretEnvelope 解封装、last-known-good、StaticRoutePlan、Client API Key 本地认证、
OpenAI Chat Completions 和 Runtime Governance。
```

本阶段明确未做：

```text
真实 retry：未做。
上游请求后的 fallback：未做。
完整 LRU 冷租户淘汰：未做。
Key 完全拆出 Snapshot：未做。
Redis L2 准入目录：未做。
用户停用实时失效：未做。
租户停用实时失效：未做。
余额 / 充值 / 预扣 / 结算：未做。
RPM / TPM：未做。
Client API Key 限流：未做。
OAuth / Cookie / account pool / sticky：未做。
Claude / Gemini / Responses / Embeddings / Realtime：未做。
React 前端：未做。
Gateway 请求热路径访问 PostgreSQL：未做。
```

结论：

```text
未越界。本阶段只处理 Gateway 准入同步和快照增量治理。
```

---

## 14. 偏差、风险与下一阶段输入

* 与计划偏差：无
* 未完成项：无
* 已知风险：当前 Client API Key 仍随 tenant snapshot 进入本地内存索引；大规模 key 场景后续应拆 Redis L2 准入目录
* 对阶段 09 管理控制台的影响：新增状态指标可用于管理端展示快照刷新、skip、失败和 tenant refresh backlog
* 后续 Redis L2 准入目录建议：将 key verifier 与 grant 摘要拆为独立可增量拉取目录，snapshot 只保留目录版本和安全指针
* 后续用户 / 租户停用实时失效建议：通过 outbox changed event 增加独立准入失效事件，并以 periodic reconcileAll 兜底
* 后续余额预扣 / 计费阶段建议：在请求准入后、上游调用前引入原子预扣，完成后结算；不得在本阶段补做
* 后续 Client API Key 限流 / RPM / TPM 建议：复用 runtime governance 的 Redis Lua 原子语义，按 key 或 grant 维度扩展

---

## 15. New-API / Sub2API 对照结论

* New-API 对照：本阶段补齐类似 New-API 配置热更新中更细粒度的准入同步能力，但仍不实现计费、余额、渠道重试、动态健康检查或管理前端。
* Sub2API 对照：本阶段增强多租户 key 与模型授权快照刷新效率，接近 Sub2API 的配置即时生效体验；账号池、订阅账号、OAuth、sticky 与限流仍属于后续阶段。
* 是否偏离 Converge 当前阶段边界：否。
* 是否需要后续源码级实现机制调研：建议后续在 Redis L2 准入目录、计费预扣、key 限流阶段继续对照 New-API / Sub2API 的生产实现细节。
