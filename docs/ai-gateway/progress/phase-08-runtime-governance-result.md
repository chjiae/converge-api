# 阶段 08｜动态资源调度、并发租约与健康熔断｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。
>
> 本文件不得记录真实 Client API Key、上游 API Key、Authorization、Cookie、request body、response body、baseUrl、resourceId、leaseId、upstream model 或 secret。

## 1. 实施时间与提交

* 开始时间：
* 完成时间：
* Git 提交 Hash：
* 实施分支：
* 开始时 `master` 最新提交：

## 2. 前置阶段复核

* 阶段 01：
* 阶段 02：
* 阶段 03：
* 阶段 04：
* 阶段 05：
* 阶段 06：
* 阶段 07：
* 当前最大 Flyway 版本：
* 是否新增 Flyway：
* 是否新增 Snapshot schema：
* 是否保持 V1 / V2 / V3 / V4 兼容：

## 3. 运行时策略控制面

* 新增实体：
* 新增 Mapper XML：
* 新增 Service：
* 新增 Controller：
* 新增 DTO：
* 默认策略：
* 现有资源策略回填：
* 新建资源是否自动创建策略：
* `policyVersion` 递增规则：
* tenant 显式校验：
* 审计：
* 是否存在硬删除：

## 4. Snapshot V4

* 新增契约：
* `GatewayTenantSnapshot` 新增字段：
* schema version：
* 新增 outbox change type：
* Projector 行为：
* V4 校验：
* V4 缺少策略时行为：
* V1 / V2 / V3 / V4 兼容：
* 发布顺序验证：

## 5. Routing Core

* 新增类：
* 候选输入：
* 候选输出：
* priority 规则：
* weight 规则：
* lower priority fallback 规则：
* seed：
* 是否读取 Redis：
* 是否读取健康状态：
* 是否读取 secret：
* 是否执行 retry / fallback：

## 6. Gateway Runtime Governance

* 新增配置：
* 默认值：
* 启动期校验：
* Redis Client 生命周期：
* Redis command timeout：
* lease TTL：
* renew interval：
* candidate attempts 上限：
* Gateway closing 行为：
* `/internal/runtime-status`：

## 7. Redis Key 与 Lua 脚本

* runtime Redis namespace：
* Redis Cluster hash tag：
* `leases` 数据结构：
* `health` 数据结构：
* ACQUIRE_LEASE：
* RENEW_LEASE：
* COMPLETE_LEASE：
* EVALSHA：
* NOSCRIPT 处理：
* key TTL 清理：
* 是否动态生成 Lua 源码：
* 是否存在 GET → Java 判断 → INCR：

## 8. Lease 生命周期

* lease 生成规则：
* acquire 成功条件：
* 并发满：
* circuit OPEN：
* HALF_OPEN：
* renew：
* release：
* client disconnect：
* Gateway draining：
* Redis unavailable：
* renew lost：
* 进程异常后的 TTL 回收：

## 9. Health 与 Outcome 分类

* SUCCESS：
* REACHABLE_CLIENT_REJECTION：
* UPSTREAM_RATE_LIMITED：
* UPSTREAM_AUTH_FAILURE：
* UPSTREAM_CONNECTION_FAILURE：
* UPSTREAM_TIMEOUT：
* UPSTREAM_SERVER_FAILURE：
* UPSTREAM_PROTOCOL_FAILURE：
* CLIENT_CANCELLED：
* GATEWAY_SHUTDOWN：
* 连续失败阈值：
* failure reset：
* 429 cooldown：
* HALF_OPEN 成功：
* HALF_OPEN 失败：

## 10. Chat Completions 整合

* 动态候选尝试：
* 成功 lease 后上游请求：
* 无 candidate：
* Redis runtime 不可用：
* 是否存在上游请求后的 retry：
* 非流式 release：
* SSE renew：
* SSE 断开：
* SSE headers 后 lease lost：
* 阶段 07 model mapping 回归：

## 11. 错误与安全

* `no_runtime_eligible_resource`：
* `runtime_state_unavailable`：
* `runtime_lease_lost`：
* `gateway_shutting_down`：
* 对外错误是否泄露资源或 URL：
* access log：
* leaseId 是否泄露：
* Redis key 是否泄露：
* Authorization / secret / body leak scan：

## 12. 测试与验证

```text
在此填写实际执行命令和结果。
```

* Flyway：
* 控制面策略：
* Snapshot V4：
* routing-core：
* 单实例并发：
* 多实例并发：
* TTL 自动清理：
* renew：
* release：
* 429 cooldown：
* 连续失败熔断：
* HALF_OPEN：
* Redis unavailable：
* NOSCRIPT：
* 非流式：
* SSE：
* V1 / V2 / V3 / V4 回归：
* `/v1/models` 回归：
* `backend mvn test`：
* `git diff --check`：
* 未执行项及原因：

## 13. 阶段边界复核

* 真实 retry：
* 上游请求后的 fallback：
* RPM / TPM：
* Client API Key 限流：
* pricing / billing / ledger：
* OAuth / Cookie / account pool / sticky session：
* Claude / Gemini / Responses / Embeddings：
* WebSocket / Realtime：
* React：
* Gateway PostgreSQL 访问：
* Gateway Redis 之外的每请求外部依赖：
* 结论：

## 14. 偏差、风险与下一阶段输入

* 与计划偏差：
* 未完成项：
* 已知风险：
* Redis 可用性策略：
* 对下一阶段建议：

## 15. New-API / Sub2API 对照结论

* New-API：
* Sub2API：
* 是否偏离：
