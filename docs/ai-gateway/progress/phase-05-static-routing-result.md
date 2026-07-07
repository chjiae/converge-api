# 阶段 05｜资源池、模型能力绑定与静态路由编译｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。

## 1. 实施时间与提交

- 开始时间：2026-07-07 上午
- 完成时间：2026-07-07 09:34 CST（代码与测试完成）
- Git 提交 Hash：提交后由最终报告记录；提交对象无法在同一提交内容中稳定自指
- 实施分支：master

## 2. 前置阶段复核

- 阶段 01：已阅读 README 与结果文档，确认 gateway 独立运行、内部健康接口、requestId、访问日志与优雅关闭边界。
- 阶段 02：已阅读 README 与结果文档，确认 AI Provider、Connection、PublicModel 目录和租户隔离实现方式。
- 阶段 03：已阅读 README 与结果文档，确认 Credential、ExecutionResource、AES-GCM 加密和资源状态边界。
- 阶段 04：已阅读 README、SNAPSHOT-PROTOCOL 与结果文档，确认 snapshot manifest、payload checksum、secret envelope、Redis pointer/index/history、gateway last-known-good 语义。
- 阶段 04 Git 提交是否存在：存在，前置记录为 `6a004d7 feat(AI网关): 实现安全快照同步`。
- 当前最大 Flyway 版本：实施前为 V8，本阶段新增 V9。

## 3. 新增模型与数据库迁移

- Flyway 迁移：`backend/converge-web-service/src/main/resources/db/migration/V9__ai_static_routing.sql`。
- `ai_resource_pool`：租户级资源池，包含 code、display_name、description、admin_status、selection_policy、created_at、updated_at。
- `ai_resource_pool_member`：资源池成员，关联 ResourcePool 与 ExecutionResource，包含独立 priority/weight/status。
- `ai_resource_model_binding`：精确模型能力绑定，表达 ExecutionResource + PublicModel + CanonicalOperation -> upstream_model_name。
- `ai_route_policy`：PublicModel + Operation 的租户内默认静态路由策略，默认 DRAFT，启用前必须通过拓扑校验。
- `ai_route_target`：RoutePolicy 到 ResourcePool 的目标层，包含独立 priority/weight/status。
- 复合约束和索引：新增 `(tenant_id, id)` 复合唯一约束补齐 public model/resource 引用；五张新表均有 tenant 复合外键、唯一约束和 tenant 查询索引。
- 新增枚举：`AiCanonicalOperation`、`AiSelectionPolicy`、`AiRoutePolicyStatus`。
- 租户与状态规则：所有 Service 入口显式要求 TenantContext；不新增 tenant ignore；禁止硬删除，使用 ENABLED/DISABLED/DRAFT 管理。

## 4. Routing Core

- 模块路径：`backend/converge-routing-core`。
- 依赖边界：仅依赖 `converge-contract` 和测试依赖；不依赖 Spring、Vert.x、Redis、MyBatis、Flyway、JDBC、Servlet、TenantContext。
- Validator / Compiler / Preview Selector：实现 `StaticTopologyValidator`、`StaticRoutePlanCompiler`、`StaticRoutePreviewSelector`。
- priority / weight 规则：priority 数值越大越优先；只有同 priority 内按正整数 weight 做确定性选择；RouteTarget 与 PoolMember 两层权重互不影响。
- StaticRoutePlan 内容：不可变 plan，仅包含 tenant、public model、operation、target tier、pool candidate、member tier、resource candidate、上游模型名和权重元数据。
- 失败类别：包含 `STATIC_ROUTE_NOT_AVAILABLE`、`NO_ROUTE_POLICY`、`NO_ELIGIBLE_ROUTE_TARGET`、`NO_ELIGIBLE_POOL_MEMBER`、`NO_MODEL_BINDING` 等安全类别。
- 是否引用/泄露秘密（必须为否）：否。route plan 只引用资源 ID 与非敏感元数据，不包含 API Key、密文、nonce、HMAC 或 Redis key。

## 5. 控制面 API 与审计

- ResourcePool：提供创建、分页、详情、更新、启用、停用接口。
- PoolMember：提供创建、分页、详情、更新、启用、停用接口，支持同 Resource 加入多个 Pool，但拒绝同 Pool 重复加入。
- ResourceModelBinding：提供创建、分页、详情、更新、启用、停用接口，仅允许 exact upstream model name。
- RoutePolicy：提供创建、分页、详情、更新、启用、停用接口，创建默认 DRAFT，启用前使用 routing-core 编译校验。
- RouteTarget：提供创建、分页、详情、更新、启用、停用接口，目标层独立 priority/weight。
- Preview：提供 `/api/v1/ai/routes/preview`，返回候选层与 deterministic preview，固定声明不应用动态状态、不执行上游请求。
- 权限：复用 `TENANT_OWNER` / `TENANT_ADMIN`；`TENANT_MEMBER` 集成测试确认无管理权限。
- 审计：写操作复用 `@Auditable`，并通过现有审计切面记录非敏感操作。
- 无硬删除结论：Controller 未提供 DELETE API，集成测试确认 DELETE 不返回成功。

## 6. Snapshot V2 与 Gateway

- Contract schema：`GatewaySnapshotSchema` 新增 V1/V2 支持范围，`CURRENT_VERSION` 升级到 V2。
- V1 兼容策略：gateway 接受 V1 manifest/payload，V1 不生成 route plan，继续保持阶段 04 快照就绪语义。
- V2 结构：`GatewayTenantSnapshot` 增加 resource pools、pool members、model bindings、route policies、route targets。
- Outbox change types：新增 `AI_RESOURCE_POOL_CHANGED`、`AI_RESOURCE_POOL_MEMBER_CHANGED`、`AI_RESOURCE_MODEL_BINDING_CHANGED`、`AI_ROUTE_POLICY_CHANGED`、`AI_ROUTE_TARGET_CHANGED`。
- Projector：阶段 04 publisher 继续使用安全 manifest、payload checksum、secret envelope、Redis immutable payload/current pointer/index/history；本阶段扩展 tenant snapshot 查询并发布 V2。
- Gateway compile：gateway 完成 manifest、checksum、schema、secret envelope 校验后，使用 routing-core 编译 V2 static route plan。
- last-known-good：V2 拓扑无效或篡改失败时不替换已有 tenant snapshot；测试覆盖 invalid V2 保留旧快照。
- snapshot-status 摘要：新增 schemaVersion、routePlanCount、compiledRoutePlanCount、invalidRouteTenantCount 等安全计数，不暴露 Redis key、密文、nonce、HMAC 或秘密。
- rollout 顺序文档：必须先部署支持 V1/V2 的 gateway，再部署产生 V2 的 control-plane；否则旧 gateway 会按阶段 04 严格 schema 检查拒绝 V2。

## 7. 测试与验证

```text
mvn -pl converge-routing-core,converge-web-service,converge-gateway -am "-Dtest=StaticRoutePlanCompilerTest,AiStaticRoutingIntegrationTest,GatewaySnapshotStaticRoutingRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：BUILD SUCCESS，控制面 7 项、gateway 静态路由 2 项、routing-core 3 项通过。

mvn test
结果：BUILD SUCCESS，Converge API / Contract / Routing Core / Common / Web Service / Gateway 全部 SUCCESS。
```

- 控制面集成测试：`AiStaticRoutingIntegrationTest` 覆盖 tenant/role/cross-tenant 拒绝、绑定、DRAINING、启用校验、preview、outbox、V2 Redis payload、无硬删除。
- Routing Core 单元测试：`StaticRoutePlanCompilerTest` 覆盖 priority/weight、seed 稳定预览、disabled、DRAINING、model mismatch、依赖扫描。
- Contract V1/V2 兼容：contract 测试在完整回归中通过；gateway 新增 V1 snapshot 加载测试验证 V1 兼容。
- Gateway runtime：`GatewaySnapshotRuntimeTest` 与 `GatewaySnapshotStaticRoutingRuntimeTest` 覆盖 V1/V2、Pub/Sub、periodic、last-known-good、安全状态摘要。
- 多 tenant：控制面 cross-tenant 拒绝和 gateway 多 tenant V2 编译测试通过。
- Secret leak scan：控制面集成测试确认 Redis payload 不包含测试 API Key 明文；route plan 和 status 不包含密文、nonce、HMAC、Redis key。
- backend root `mvn test`：通过，完整 Maven reactor 6 个模块 SUCCESS。
- 未执行项及原因：无。

## 8. 阶段边界复核

- `/v1/*`：未新增。
- 上游 HTTP / SSE / WebSocket：未实现。
- ClientApiKey / AccessGroup / 套餐：未实现。
- 价格 / 倍率 / 计费：未实现。
- OAuth / account pool：未实现。
- 动态限流 / sticky session：未实现。
- React：未修改。
- gateway DB 访问：未新增，gateway 仍只通过 Redis snapshot 加载配置。
- 结论：阶段 05 只落地静态路由控制面、纯 Java 编译核心、V2 snapshot 和 gateway 本地编译，没有越过阶段边界。

## 9. 偏差、风险与下一阶段输入

- 与计划偏差：无真实偏差；为支持复合外键，在 V9 内补充历史表 `(tenant_id, id)` 唯一约束，未修改既有迁移。
- 未完成项：无。
- 已知风险：本阶段 preview 明确为静态配置预览，不代表真实上游可用性；动态健康、限流、账号池、下游鉴权、计费仍属于后续阶段。
- 对下一阶段建议：在引入真实转发前继续保持 gateway 不直连数据库；后续动态状态应与本阶段 static route plan 组合，而不是改写 snapshot 中的静态拓扑。

## 10. New-API / Sub2API 对照结论

- New-API：本阶段覆盖其常见的渠道分组、模型映射、静态权重/优先级路由的控制面基础，但不实现实际 OpenAI 兼容转发、Key 鉴权、计费和动态健康。
- Sub2API：本阶段覆盖订阅/转换系统中常见的模型到上游资源静态绑定和资源池选择基础，但不实现订阅账号、套餐映射、OAuth/Refresh Token 或账号池。
- 是否偏离：未偏离。阶段 05 只实现安全静态路由拓扑与 snapshot 下发，为后续能力铺路，没有提前实现用户访问、上游请求或计费。
