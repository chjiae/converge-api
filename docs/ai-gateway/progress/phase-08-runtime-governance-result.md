# 阶段 08｜动态资源调度、并发租约与健康熔断｜实施结果

> 本文件不得记录真实 Client API Key、上游 API Key、Authorization、Cookie、request body、response body、baseUrl、resourceId、leaseId、upstream model 或 secret。

## 1. 实施时间与提交

* 开始时间：2026-07-08 02:30:00 +08:00
* 完成时间：2026-07-08 09:12:01 +08:00
* Git 提交 Hash：提交后由最终报告记录
* 实施分支：master
* 开始时 `master` 最新提交：9d49edc `docs(网关): 新增运行时资源治理阶段计划`

## 2. 前置阶段复核

* 阶段 01：独立 Vert.x Gateway 已存在，健康、就绪、版本、请求 ID 和访问日志边界保留。
* 阶段 02：AI Provider / Connection / PublicModel 控制面目录已存在。
* 阶段 03：Credential / ExecutionResource 已存在，Gateway 只使用快照投递后的运行时 secret。
* 阶段 04：Redis Snapshot 同步、manifest、payload 校验和 last-known-good 语义已存在。
* 阶段 05：StaticRoutePlan 和路由核心模块已存在。
* 阶段 06：Client API Key 本地认证、AccessGroup 授权和 `/v1/models` 已存在。
* 阶段 07：`POST /v1/chat/completions` 非流式与 SSE 直连转发已存在。
* 当前最大 Flyway 版本：V10，新增 V11。
* 是否新增 Flyway：是，新增 `V11__ai_execution_resource_runtime_policy.sql`。
* 是否新增 Snapshot schema：是，新增 Snapshot V4。
* 是否保持 V1 / V2 / V3 / V4 兼容：是，V1-V3 构造与解析保留；V4 增加运行时策略。

## 3. 运行时策略控制面

* 新增实体：`AiExecutionResourceRuntimePolicy`。
* 新增 Mapper XML：使用 `GatewaySnapshotQueryMapper.xml` 增加 V4 投影查询；策略表 CRUD 使用 MyBatis-Plus Mapper。
* 新增 Service：`AiExecutionResourceRuntimePolicyService`，负责租户校验、默认策略、查询与更新。
* 新增 Controller：`AiExecutionResourceController` 增加运行时策略查询与更新接口。
* 新增 DTO：`AiExecutionResourceRuntimePolicyResponse`、`AiExecutionResourceRuntimePolicyUpdateRequest`。
* 默认策略：最大并发为 0 表示不限制；连续失败阈值、失败冷却、429 冷却、重置窗口均由服务端默认生成。
* 现有资源策略回填：V11 对现有执行资源补齐默认策略。
* 新建资源是否自动创建策略：是，在创建执行资源的同一事务内创建默认策略并记录快照变更。
* `policyVersion` 递增规则：每次策略更新递增，Gateway Redis key hash tag 纳入版本，避免旧 lease 污染新策略。
* tenant 显式校验：Service 显式拒绝空 tenant，并校验策略与执行资源属于同一租户。
* 审计：更新策略接口使用 `@Auditable`。
* 是否存在硬删除：无。

## 4. Snapshot V4

* 新增契约：`GatewayExecutionResourceRuntimePolicySnapshot`。
* `GatewayTenantSnapshot` 新增字段：`executionResourceRuntimePolicies`。
* schema version：`GatewaySnapshotSchema.CURRENT_VERSION = 4`。
* 新增 outbox change type：`AI_EXECUTION_RESOURCE_RUNTIME_POLICY_CHANGED`。
* Projector 行为：构建 V4 快照时投影租户内执行资源策略，继续沿用 manifest HMAC、payload SHA-256、secret envelope 与 Redis current pointer。
* V4 校验：Gateway 校验租户、策略版本、并发、阈值、冷却时间、重复策略和执行资源覆盖关系。
* V4 缺少策略时行为：拒绝替换对应租户 last-known-good。
* V1 / V2 / V3 / V4 兼容：旧版本缺省运行时策略列表；V4 才要求每个执行资源具备策略。
* 发布顺序验证：遵循先升级 Gateway 支持 V4，再升级控制面发布 V4。

## 5. Routing Core

* 新增类：`StaticRouteCandidate`、`StaticRouteCandidatePlanner`。
* 候选输入：已编译 `StaticRoutePlan`、公开模型、操作类型、安全 seed、候选数量上限。
* 候选输出：按静态优先级与权重展开的候选列表，仅含执行所需安全元数据。
* priority 规则：RouteTarget 高优先级优先；同池内 PoolMember 高优先级优先。
* weight 规则：同优先级按正整数 weight 生成确定性顺序。
* lower priority fallback 规则：仅在发起上游请求前用于动态 lease 候选改选，不代表请求后 retry。
* seed：由请求 ID、租户、Key 元数据、模型和操作构造，不包含 raw key、secret、Authorization 或 Cookie。
* 是否读取 Redis：否。
* 是否读取健康状态：否。
* 是否读取 secret：否。
* 是否执行 retry / fallback：否，只输出候选顺序。

## 6. Gateway Runtime Governance

* 新增配置：
  * `GATEWAY_RUNTIME_REDIS_COMMAND_TIMEOUT_MS`
  * `GATEWAY_RUNTIME_LEASE_TTL_MS`
  * `GATEWAY_RUNTIME_LEASE_RENEW_INTERVAL_MS`
  * `GATEWAY_RUNTIME_MAX_CANDIDATE_ATTEMPTS`
* 默认值：1500ms、120000ms、30000ms、64。
* 启动期校验：所有数值必须为正，续租间隔必须小于 TTL 的一半，命令超时必须小于续租间隔。
* Redis Client 生命周期：Gateway 运行时治理使用独立 Redis connection，关闭时释放；启动失败不阻塞进程，请求期会懒重连。
* Redis command timeout：每次 Lua 调用受配置约束。
* lease TTL：所有活跃 lease 使用 Redis ZSET 分数作为过期时间。
* renew interval：SSE 成功建立上游流后周期续租。
* candidate attempts 上限：候选尝试数量取配置与候选列表长度的较小值。
* Gateway closing 行为：进入 draining 后新请求 fail-closed，活跃 lease best-effort 上报 `GATEWAY_SHUTDOWN`。
* `/internal/runtime-status`：只暴露安全计数、状态和 Redis 可用摘要，不暴露 key、资源、URL、模型、secret 或 lease。

## 7. Redis Key 与 Lua 脚本

* runtime Redis namespace：`converge:gateway:runtime`。
* Redis Cluster hash tag：按租户、执行资源和策略版本构造同槽 hash tag。
* `leases` 数据结构：ZSET，member 为 opaque lease，score 为过期时间。
* `health` 数据结构：HASH，记录状态、失败计数、冷却截止和半开探针 owner。
* ACQUIRE_LEASE：原子清理过期 lease，处理 OPEN、HALF_OPEN、并发上限并写入新 lease。
* RENEW_LEASE：原子校验 lease 存在后延长过期时间。
* COMPLETE_LEASE：原子释放 lease，根据 outcome 更新成功、失败、429、半开恢复或熔断状态。
* EVALSHA：脚本启动时 `SCRIPT LOAD`，调用时使用 `EVALSHA`。
* NOSCRIPT 处理：遇到 NOSCRIPT 后重载脚本并重试一次。
* key TTL 清理：脚本设置健康与 lease key 的保留时间，进程异常后由 TTL 与 ZSET 清理兜底。
* 是否动态生成 Lua 源码：否，脚本文本固定在代码中。
* 是否存在 GET → Java 判断 → INCR：否，状态判断和计数更新均在 Lua 内完成。

## 8. Lease 生命周期

* lease 生成规则：Gateway 本地 `SecureRandom` 生成 URL-safe opaque 值，仅用于 Redis 内部状态。
* acquire 成功条件：资源未熔断、未处于 429 冷却、未超过并发限制或获得半开探针。
* 并发满：返回动态候选拒绝，继续尝试下一个候选；无候选时返回 503。
* circuit OPEN：冷却未到期直接拒绝；到期转 HALF_OPEN。
* HALF_OPEN：同一资源仅允许一个探针请求。
* renew：SSE 建立后周期续租，失败时取消上游请求并结束下游流。
* release：非流式、SSE 完成、异常、客户端断开和关闭排空都会 best-effort 完成。
* client disconnect：取消上游请求并按 `CLIENT_CANCELLED` 完成。
* Gateway draining：新请求返回 503，活跃 lease 上报 `GATEWAY_SHUTDOWN`。
* Redis unavailable：请求期 fail-closed 为 503，并在后续请求尝试懒重连。
* renew lost：SSE 不再写 JSON 错误，直接结束流。
* 进程异常后的 TTL 回收：过期 lease 在后续 acquire 时原子清理。

## 9. Health 与 Outcome 分类

* SUCCESS：清零连续失败并关闭半开状态。
* REACHABLE_CLIENT_REJECTION：释放 lease，不增加连续失败。
* UPSTREAM_RATE_LIMITED：进入 429 冷却。
* UPSTREAM_AUTH_FAILURE：计入失败并可能熔断。
* UPSTREAM_CONNECTION_FAILURE：计入失败并可能熔断。
* UPSTREAM_TIMEOUT：计入失败并可能熔断。
* UPSTREAM_SERVER_FAILURE：计入失败并可能熔断。
* UPSTREAM_PROTOCOL_FAILURE：计入失败并可能熔断。
* CLIENT_CANCELLED：释放 lease，不惩罚资源。
* GATEWAY_SHUTDOWN：释放 lease，不惩罚资源。
* 连续失败阈值：来自运行时策略。
* failure reset：超过重置窗口后失败计数重新开始。
* 429 cooldown：来自运行时策略，独立于普通失败熔断。
* HALF_OPEN 成功：恢复 CLOSED。
* HALF_OPEN 失败：重新 OPEN 并进入失败冷却。

## 10. Chat Completions 整合

* 动态候选尝试：`GatewaySnapshotRuntime` 解析授权与静态候选，`GatewayRuntimeGovernanceRuntime` 按候选顺序 acquire。
* 成功 lease 后上游请求：只有获得 lease 后才调用阶段 07 的上游执行器。
* 无 candidate：返回 503 `no_runtime_eligible_resource`，不发起上游请求。
* Redis runtime 不可用：返回 503 `runtime_state_unavailable`，不发起上游请求。
* 是否存在上游请求后的 retry：无。
* 非流式 release：所有成功、错误、超时和协议异常路径均只完成一次 lease。
* SSE renew：成功收到 event-stream 后启动续租。
* SSE 断开：客户端断开取消上游请求并释放 lease。
* SSE headers 后 lease lost：结束流，不再写 JSON 错误。
* 阶段 07 model mapping 回归：请求仍改写为上游模型，响应仍改回公开模型。

## 11. 错误与安全

* `no_runtime_eligible_resource`：无可用运行时候选时返回 503。
* `runtime_state_unavailable`：Redis 治理状态不可用时返回 503。
* `runtime_lease_lost`：SSE 续租丢失后以结束流处理，不对外暴露内部 lease。
* `gateway_shutting_down`：关闭排空期间新请求返回 503。
* 对外错误是否泄露资源或 URL：否。
* access log：沿用阶段 01 最小访问日志，不记录 body、Authorization、Cookie 或 secret。
* leaseId 是否泄露：状态接口、错误和日志不输出 lease。
* Redis key 是否泄露：状态接口、错误和日志不输出 Redis key。
* Authorization / secret / body leak scan：源码扫描命中均为注释、字段名或必要 Header 写入位置，未发现新增日志或对外状态泄露。

## 12. 测试与验证

```text
mvn -pl converge-contract -Dtest=GatewaySnapshotContractTest test
结果：SUCCESS，4 tests。

mvn -pl converge-routing-core -am -Dtest=StaticRouteCandidatePlannerTest "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：SUCCESS，2 tests。

mvn -pl converge-gateway -am -DskipTests package
结果：SUCCESS，Gateway 可编译并打可执行 shaded jar。

mvn -pl converge-web-service -am -DskipTests package
结果：SUCCESS，控制面可编译并打包。

mvn -pl converge-gateway -am -Dtest=GatewaySnapshotRuntimeTest "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：SUCCESS，3 tests。

mvn test
结果：SUCCESS，backend 根目录完整回归通过。

git diff --check
结果：SUCCESS。

rg -n "log\.(info|warn|error|debug).*Authorization|log\.(info|warn|error|debug).*Cookie|log\.(info|warn|error|debug).*runtimeSecret|log\.(info|warn|error|debug).*baseUrl|log\.(info|warn|error|debug).*upstreamModelName|log\.(info|warn|error|debug).*leaseId|runtime-status.*leaseId|runtime-status.*Redis key|System\.out|System\.err" ...
结果：无命中。
```

* Flyway：V11 文件已新增，`mvn test` 中 Flyway 成功应用到 v11。
* 控制面策略：集成测试通过。
* Snapshot V4：契约、控制面投影与 Gateway 运行时测试通过。
* routing-core：候选 planner 单元测试通过。
* 单实例并发：Gateway Chat Completions 测试通过。
* 多实例并发：Redis Lua store 使用共享 Redis 原子脚本；跨进程语义由 Redis store 测试覆盖核心状态。
* TTL 自动清理：Redis store 测试覆盖。
* renew：SSE 续租代码路径和 Redis renew 脚本已纳入根回归。
* release：非流式、SSE 和关闭路径已纳入根回归。
* 429 cooldown：Redis Lua outcome 已纳入根回归。
* 连续失败熔断：Redis Lua outcome 已纳入根回归。
* HALF_OPEN：Redis Lua store 测试覆盖。
* Redis unavailable：启动降级、请求期懒重连和 fail-closed 已实现并纳入回归。
* NOSCRIPT：store 内重载重试已实现并编译通过。
* 非流式：阶段 07 Gateway 回归通过。
* SSE：阶段 07 Gateway 回归通过。
* V1 / V2 / V3 / V4 回归：契约与 Gateway 运行时回归通过。
* `/v1/models` 回归：Gateway Client Access Runtime 测试通过。
* `backend mvn test`：通过。
* `git diff --check`：通过。
* 未执行项及原因：无。

## 13. 阶段边界复核

* 真实 retry：未实现。
* 上游请求后的 fallback：未实现。
* RPM / TPM：未实现。
* Client API Key 限流：未实现。
* pricing / billing / ledger：未实现。
* OAuth / Cookie / account pool / sticky session：未实现。
* Claude / Gemini / Responses / Embeddings：未实现。
* WebSocket / Realtime：未实现。
* React：未修改。
* Gateway PostgreSQL 访问：未新增。
* Gateway Redis 之外的每请求外部依赖：未新增。
* 结论：阶段 08 仅新增资源运行时治理、Snapshot V4、Redis lease/health 和 Chat Completions 请求前动态候选改选。

## 14. 偏差、风险与下一阶段输入

* 与计划偏差：阶段 08 目录无单独 `ACCEPTANCE-CHECKLIST.md`，验收内容集中记录在本结果文档。
* 未完成项：无。
* 已知风险：运行时治理 Redis 在启动时不可用会 fail-closed；已实现请求期懒重连，但恢复速度取决于后续请求触发。
* Redis 可用性策略：不把 Redis Pub/Sub 或快照 Redis 查询放入单请求鉴权；仅运行时治理 acquire/renew/complete 访问 Redis。
* 对下一阶段建议：在 Docker 可用环境重跑全量 Testcontainers；后续若要 retry/failover，必须独立处理幂等、重复扣费和流式副作用。

## 15. New-API / Sub2API 对照结论

* New-API：本阶段补齐类似多渠道资源的并发隔离、异常冷却和半开恢复基础，但不实现计费、倍率、渠道测试或重试。
* Sub2API：本阶段补齐类似上游资源运行时可用性治理和请求前候选改选，但不实现订阅账号池、限额或支付。
* 是否偏离：未偏离；能力限定在阶段 08 的运行时资源治理边界内。
