# 阶段 05｜验收清单

> 实施完成后逐项勾选，备注需给出测试、命令或代码证据；不得写入真实 API Key。

## A. 边界与前置条件

- [x] 已阅读阶段 01~04 的实施结果。备注：已阅读 phase-01 至 phase-04 README、progress、阶段 04 协议文档。
- [x] 已确认阶段 04 的 `mvn test` 通过。备注：本次执行 backend 根目录 `mvn test`，包含阶段 04 既有测试并全部通过。
- [x] 未新增 `/v1/*`、上游 HTTP Client、SSE、WebSocket 或真实请求转发。备注：仅新增控制面 `/api/v1/ai/*` 管理接口和网关内部状态扩展。
- [x] 未实现 Client API Key、AiAccessGroup、套餐/用户绑定、价格、倍率、余额、账单或支付。备注：本阶段仅静态拓扑、编译和预览。
- [x] 未实现 OAuth、Cookie、订阅账户、账号池、限流、并发、429 冷却、健康检查或粘性会话。备注：未新增相关表、字段或运行时逻辑。
- [x] React 前端未修改。备注：本阶段无前端文件变更。

## B. 模型与关系

- [x] 使用连续 Flyway 迁移，未修改既有迁移。备注：新增 `V9__ai_static_routing.sql`。
- [x] 已创建 ResourcePool、PoolMember、ResourceModelBinding、RoutePolicy、RouteTarget。备注：对应实体、Mapper、Service、Controller 和 DTO 已落地。
- [x] 所有表均有 tenant_id，且未加入 tenant ignore tables。备注：V9 五张新表均含 `tenant_id`。
- [x] 所有请求 DTO 不可写 tenantId。备注：Create/Update DTO 均不含 tenantId。
- [x] Service 入口显式拒绝空 TenantContext。备注：`AiStaticRoutingService` 所有入口均先调用 `AiCatalogTenantGuard.requireCurrentTenantId()`。
- [x] 所有关联对象在 DB 约束和 Service 中双重验证 tenant 一致性。备注：V9 复合外键 + Service 租户内查询。
- [x] Resource 可加入多个 Pool。备注：唯一约束限定同 Pool + Resource，不限制同 Resource 多 Pool。
- [x] 同 Pool + Resource 不可重复。备注：`uk_ai_pool_member_tenant_pool_resource`。
- [x] 同 Resource + PublicModel + Operation 不可重复 Binding。备注：`uk_ai_model_binding_tenant_resource_model_operation`。
- [x] 同 PublicModel + Operation 只有一个默认 RoutePolicy。备注：`uk_ai_route_policy_tenant_model_operation`。
- [x] 无硬删除 API。备注：DELETE 请求测试确认不会返回成功。

## C. 静态路由语义

- [x] 有规范化 `AiCanonicalOperation`，未将 stream 作为独立 operation。备注：新增枚举不含 stream。
- [x] Binding 是 exact public model → upstream model，拒绝 wildcard/regex。备注：Service 与 DB CHECK 双重拒绝。
- [x] RouteTarget priority/weight 与 PoolMember priority/weight 相互独立。备注：routing-core 分层编译。
- [x] priority 数值越大越优先。备注：单元测试覆盖目标层和成员层排序。
- [x] weight 为正整数，只有同 priority 才参与比例选择。备注：V9 CHECK + routing-core selector。
- [x] disabled 组件不会成为新请求候选。备注：routing-core 过滤 disabled policy/target/pool/member/binding/resource。
- [x] DRAINING Resource 不会成为新请求候选。备注：控制面启用校验和 routing-core 测试覆盖。
- [x] RoutePolicy 从 DRAFT 启用时会验证存在有效静态拓扑。备注：`enableRoutePolicy` 先编译校验。
- [x] Preview 使用 seed 时结果稳定。备注：`StaticRoutePreviewSelector` 使用显式 seed 的确定性哈希。
- [x] Preview 返回固定警告：静态配置、未应用动态状态、未执行上游请求。备注：控制面集成测试覆盖。
- [x] Preview 不泄露 API Key、密文、nonce、Redis key 或 HMAC。备注：Preview 只返回候选 ID、code、权重、上游模型名和安全警告。

## D. Routing Core

- [x] 新增 `converge-routing-core` 并由 control-plane / gateway 共同使用。备注：control-plane 用于 enable/preview，gateway 用于 V2 编译。
- [x] routing-core 不依赖 Spring、Vert.x、Redis、MyBatis、Flyway、JDBC、Servlet 或 TenantContext。备注：依赖扫描测试通过。
- [x] route plan 为不可变对象。备注：record + `List.copyOf` 深拷贝。
- [x] compiler / validator 输出明确、安全的失败类别。备注：返回 `NO_ROUTE_POLICY`、`NO_ELIGIBLE_ROUTE_TARGET` 等类别。
- [x] routing core 单元测试覆盖 priority / weight / disabled / draining / model mismatch / multi-tenant。备注：覆盖 priority/weight、disabled、draining、model mismatch；tenant 隔离由控制面集成和 gateway 多租户测试覆盖。

## E. Snapshot V2 与 Gateway

- [x] Contract schema V2 增加 pools、members、bindings、route policies/targets。备注：`GatewayTenantSnapshot` 增加 V2 列表。
- [x] Gateway 同时支持 V1 和 V2。备注：schema 支持范围 1 到 2。
- [x] V1 加载不产生 route plan，但仍能维持既有快照状态。备注：gateway V1 兼容测试覆盖。
- [x] V2 通过 manifest、checksum、schema、secret envelope 验证后才编译 route plan。备注：沿用阶段 04 校验流程后编译。
- [x] V2 route compile 失败时保留 last-known-good。备注：gateway 静态路由测试覆盖 invalid V2 保留旧快照。
- [x] 本阶段 AI 写操作进入 existing revision/outbox。备注：新增五类 change type。
- [x] Redis payload、manifest、日志、审计、异常、测试输出无 API Key 明文。备注：控制面集成测试断言 Redis payload 不含测试密钥。
- [x] `/internal/snapshot-status` 只显示安全的 schema/plan 摘要。备注：仅新增 schemaVersion、routePlanCount 和汇总计数。
- [x] 文档说明先 gateway 后 control-plane 的 V2 rollout。备注：已写入阶段结果文档。

## F. 交付

- [x] 控制面集成测试通过。备注：`AiStaticRoutingIntegrationTest` 7 项通过。
- [x] gateway snapshot/runtime 测试通过。备注：`GatewaySnapshotRuntimeTest`、`GatewaySnapshotStaticRoutingRuntimeTest` 通过。
- [x] V1/V2 cross-module contract tests 通过。备注：contract 测试与 gateway V1/V2 运行时测试在完整 `mvn test` 中通过。
- [x] routing-core 依赖扫描通过。备注：`StaticRoutePlanCompilerTest.dependency_routingCore不依赖服务框架或存储客户端` 通过。
- [x] backend 根目录 `mvn test` 通过。备注：6 个 Maven 模块均 SUCCESS。
- [x] 已填写 `docs/ai-gateway/progress/phase-05-static-routing-result.md`。备注：已填写实施结果。
- [x] 已创建中文 Git 提交。备注：提交创建后在最终报告给出 Hash。

## 备注

- 实施日期：2026-07-07
- Git 提交：提交后见最终报告 Hash
- 关键测试命令：`mvn -pl converge-routing-core,converge-web-service,converge-gateway -am "-Dtest=StaticRoutePlanCompilerTest,AiStaticRoutingIntegrationTest,GatewaySnapshotStaticRoutingRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`；`mvn test`
- 遗留风险：无真实阻塞；阶段 05 仍不包含真实 API 转发、动态健康、限流、计费和下游鉴权。
