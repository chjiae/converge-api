# 阶段 05｜验收清单

> 实施完成后逐项勾选，备注需给出测试、命令或代码证据；不得写入真实 API Key。

## A. 边界与前置条件

- [ ] 已阅读阶段 01~04 的实施结果。
- [ ] 已确认阶段 04 的 `mvn test` 通过。
- [ ] 未新增 `/v1/*`、上游 HTTP Client、SSE、WebSocket 或真实请求转发。
- [ ] 未实现 Client API Key、AiAccessGroup、套餐/用户绑定、价格、倍率、余额、账单或支付。
- [ ] 未实现 OAuth、Cookie、订阅账户、账号池、限流、并发、429 冷却、健康检查或粘性会话。
- [ ] React 前端未修改。

## B. 模型与关系

- [ ] 使用连续 Flyway 迁移，未修改既有迁移。
- [ ] 已创建 ResourcePool、PoolMember、ResourceModelBinding、RoutePolicy、RouteTarget。
- [ ] 所有表均有 tenant_id，且未加入 tenant ignore tables。
- [ ] 所有请求 DTO 不可写 tenantId。
- [ ] Service 入口显式拒绝空 TenantContext。
- [ ] 所有关联对象在 DB 约束和 Service 中双重验证 tenant 一致性。
- [ ] Resource 可加入多个 Pool。
- [ ] 同 Pool + Resource 不可重复。
- [ ] 同 Resource + PublicModel + Operation 不可重复 Binding。
- [ ] 同 PublicModel + Operation 只有一个默认 RoutePolicy。
- [ ] 无硬删除 API。

## C. 静态路由语义

- [ ] 有规范化 `AiCanonicalOperation`，未将 stream 作为独立 operation。
- [ ] Binding 是 exact public model → upstream model，拒绝 wildcard/regex。
- [ ] RouteTarget priority/weight 与 PoolMember priority/weight 相互独立。
- [ ] priority 数值越大越优先。
- [ ] weight 为正整数，只有同 priority 才参与比例选择。
- [ ] disabled 组件不会成为新请求候选。
- [ ] DRAINING Resource 不会成为新请求候选。
- [ ] RoutePolicy 从 DRAFT 启用时会验证存在有效静态拓扑。
- [ ] Preview 使用 seed 时结果稳定。
- [ ] Preview 返回固定警告：静态配置、未应用动态状态、未执行上游请求。
- [ ] Preview 不泄露 API Key、密文、nonce、Redis key 或 HMAC。

## D. Routing Core

- [ ] 新增 `converge-routing-core` 并由 control-plane / gateway 共同使用。
- [ ] routing-core 不依赖 Spring、Vert.x、Redis、MyBatis、Flyway、JDBC、Servlet 或 TenantContext。
- [ ] route plan 为不可变对象。
- [ ] compiler / validator 输出明确、安全的失败类别。
- [ ] routing core 单元测试覆盖 priority / weight / disabled / draining / model mismatch / multi-tenant。

## E. Snapshot V2 与 Gateway

- [ ] Contract schema V2 增加 pools、members、bindings、route policies/targets。
- [ ] Gateway 同时支持 V1 和 V2。
- [ ] V1 加载不产生 route plan，但仍能维持既有快照状态。
- [ ] V2 通过 manifest、checksum、schema、secret envelope 验证后才编译 route plan。
- [ ] V2 route compile 失败时保留 last-known-good。
- [ ] 本阶段 AI 写操作进入 existing revision/outbox。
- [ ] Redis payload、manifest、日志、审计、异常、测试输出无 API Key 明文。
- [ ] `/internal/snapshot-status` 只显示安全的 schema/plan 摘要。
- [ ] 文档说明先 gateway 后 control-plane 的 V2 rollout。

## F. 交付

- [ ] 控制面集成测试通过。
- [ ] gateway snapshot/runtime 测试通过。
- [ ] V1/V2 cross-module contract tests 通过。
- [ ] routing-core 依赖扫描通过。
- [ ] backend 根目录 `mvn test` 通过。
- [ ] 已填写 `docs/ai-gateway/progress/phase-05-static-routing-result.md`。
- [ ] 已创建中文 Git 提交。

## 备注

- 实施日期：
- Git 提交：
- 关键测试命令：
- 遗留风险：
