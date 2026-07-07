# Codex 执行提示词｜阶段 05

请在 Converge API 仓库根目录，一次性完整实施：

```text
docs/ai-gateway/phase-05-static-routing/README.md
```

## 必读文件

1. `AGENTS.md`
2. `docs/ai-gateway/phase-01-gateway-runtime/README.md`
3. `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md`
4. `docs/ai-gateway/phase-02-control-plane-catalog/README.md`
5. `docs/ai-gateway/progress/phase-02-control-plane-catalog-result.md`
6. `docs/ai-gateway/phase-03-credential-resource/README.md`
7. `docs/ai-gateway/progress/phase-03-credential-resource-result.md`
8. `docs/ai-gateway/phase-04-gateway-snapshot-sync/README.md`
9. `docs/ai-gateway/phase-04-gateway-snapshot-sync/SNAPSHOT-PROTOCOL.md`
10. `docs/ai-gateway/progress/phase-04-gateway-snapshot-sync-result.md`
11. `docs/ai-gateway/phase-05-static-routing/README.md`
12. `docs/ai-gateway/phase-05-static-routing/STATIC-ROUTING-PROTOCOL.md`
13. `docs/ai-gateway/phase-05-static-routing/CAPABILITY-COVERAGE-MATRIX.md`
14. `docs/ai-gateway/phase-05-static-routing/SOURCE-REVIEW-2026-07-07.md`
15. `docs/ai-gateway/phase-05-static-routing/ACCEPTANCE-CHECKLIST.md`
16. 当前 POM、V8 migration、AI catalog/credential/resource services、GatewaySnapshot Projector、`converge-contract`、gateway snapshot runtime、测试与现有权限/审计代码。

## 执行授权与连续完成要求

我已授予完成本阶段所需的全部操作权限。

你可以自行读写本仓库、创建连续 Flyway 迁移、修改 Maven 模块、下载依赖、运行 Testcontainers/Redis 测试、启动停止本地服务、查看日志、执行 Git add 和 Git commit。

先快速审查，简短说明关键发现与预计改动文件；**不要等待我确认**。然后直接连续完成全部阶段工作：设计、实现、测试、失败修复、文档、验收、提交。

遇到普通实现取舍、构建/测试失败、依赖冲突、Redis 测试、schema V1/V2 兼容、代码风格或 Git 操作问题，优先自行定位、修复、重试。除非存在真实无法继续的阻塞（缺少外部权限、需要损坏/迁移生产数据、文档和代码严重冲突且无法做安全判断），否则不要中止、不要要求批准、不要只给建议。

本阶段文档的安全边界优先于旧草稿。若命名、包结构或精确 API 路径需要适配当前项目，可做最小兼容调整，并在结果文档中记录。

## 本阶段必须完整完成

### A. 领域模型与控制面

1. 新增 `AiCanonicalOperation`。
2. 新增 tenant 级 `AiResourcePool`、`AiResourcePoolMember`、`AiResourceModelBinding`、`AiRoutePolicy`、`AiRouteTarget`。
3. 使用当前最大 Flyway 版本后的连续迁移，不修改既有迁移。
4. 为所有关联建立 tenant 一致性的复合约束/外键和 Service 显式校验。
5. DTO 不允许传入 tenantId、加密字段或其他内部快照字段。
6. 仅复用现有 `TENANT_OWNER` / `TENANT_ADMIN`、`@Auditable`、`Result<T>`、`PageResult<T>`、`AiCatalogTenantGuard`。
7. 禁止硬删除，采用 draft/enable/disable 等状态行为。
8. 实现 ResourcePool、PoolMember、ResourceModelBinding、RoutePolicy、RouteTarget 的控制面 CRUD/状态 API。
9. 实现 exact `PublicModel + Operation → upstream model name` binding；本阶段拒绝 wildcard、正则、条件映射。
10. RoutePolicy 默认 DRAFT，只有静态拓扑通过校验才允许 ENABLED。
11. `DRAINING` ExecutionResource 不得成为新请求静态候选。

### B. 纯 Java routing core

12. 新增 `backend/converge-routing-core` 模块，依赖 `converge-contract`，供 control-plane 和 gateway 共同使用。
13. `converge-routing-core` 不得依赖 Spring、Vert.x、Redis、MyBatis、Flyway、JDBC、Servlet、TenantContext。
14. 实现不可变静态 route plan、topology validator、compiler、带显式 seed 的确定性 weighted preview selector。
15. 高 priority 优先；同 priority 才按正整数 weight；route target 与 pool member 两层权重独立。
16. 输出明确的验证失败原因，不泄露秘密。
17. 控制面新增静态路由 preview API，输出候选层与 deterministic preview，明确标记其不应用动态状态、不执行上游请求。

### C. Snapshot V2 与 Gateway

18. 扩展 `converge-contract` 快照 schema 到 V2，增加 resource pools、members、model bindings、route policies/targets 的不可变契约。
19. gateway 必须支持读取 V1 和 V2；control-plane 产生 V2 的升级方式必须安全，文档要写清先 gateway 后 control-plane 的部署顺序。
20. 改造 Stage 04 projector，将所有本阶段写操作作为 snapshot change 类型写入现有 revision/outbox。
21. 控制面 V2 snapshot 继续使用阶段 04 的 secure manifest、payload checksum、secret envelope、Redis immutable payload 与 current pointer；不得破坏这些安全性质。
22. gateway 加载 V2 后使用 routing core 编译 static route plan；验证失败时保留对应 tenant last-known-good snapshot。
23. `/internal/snapshot-status` 仅增加安全状态摘要，不泄露 API Key、ciphertext、nonce、Redis key、manifest HMAC 或 payload。
24. 不新增 `/v1/*` 或任何实际 API 中转端点。

### D. 测试、交付与提交

25. 新增测试至少覆盖：
   - tenant / role / cross-tenant 关系拒绝；
   - exact model binding、disabled component、DRAINING resource 排除；
   - route policy enable validation；
   - route target 和 pool member priority/weight 的稳定层次与 seed 选择；
   - no hard-delete endpoint；
   - control-plane mutation → revision/outbox → V2 snapshot；
   - V1 gateway compatibility；
   - V2 snapshot route compilation；
   - malformed V2 / invalid topology retains last-known-good；
   - multi-tenant isolation；
   - snapshot Redis payload/log/audit/exception 不含 API Key 明文；
   - routing core 禁止依赖扫描；
   - full backend root `mvn test`.
26. 填写：
   - `docs/ai-gateway/phase-05-static-routing/ACCEPTANCE-CHECKLIST.md`
   - `docs/ai-gateway/progress/phase-05-static-routing-result.md`
27. 创建符合 `AGENTS.md` 的中文 Git 提交。

## 严格禁止

- 不新增 `/v1/*`；
- 不做上游 HTTP Client、实际 API 中转、SSE、WebSocket、重试或协议转换；
- 不让 gateway 访问 PostgreSQL；
- 不让 gateway 读取阶段 03 的 `AI_CREDENTIAL_*` 数据库凭据密钥；
- 不实现 Client API Key、用户鉴权、AiAccessGroup、用户/套餐绑定、模型权限覆盖；
- 不实现价格、成本、倍率、余额、账单、支付；
- 不实现 OAuth、Refresh Token、Cookie、订阅账户、授权账户、账号池；
- 不实现并发租约、RPM/TPM、429 冷却、健康检查、余额探测、粘性会话；
- 不实现 wildcard/正则/条件模型映射；
- 不修改 React 前端；
- 不把 route preview 表述为真实请求执行；
- 不把 Redis Pub/Sub 当作唯一可靠配置来源；
- 不把 API Key 明文写入 Redis、数据库、日志、审计、异常、测试报告、文档示例或 Git 提交信息；
- 不破坏阶段 01~04 已完成的租户、审计、凭据、快照、网关边界。

## 完成标准

仅当下列全部满足后，才结束本次任务：

- README 与 STATIC-ROUTING-PROTOCOL 的全部范围落地；
- 静态拓扑能从控制面安全发布为 V2 snapshot；
- gateway 可同时读取 V1 / V2，且 V2 可编译不可变 static route plan；
- route plan 只引用 Resource ID，不泄露秘密；
- 失效 V2 不替换任何 tenant 的 last-known-good；
- root `backend/mvn test` 成功；
- 验收清单、实施结果文档已填写；
- Git 提交已创建。

最终报告必须包含：

1. 前置审查发现；
2. 修改文件清单；
3. 表结构与路由语义摘要；
4. V1 → V2 快照兼容和部署说明；
5. 实际命令与测试结果；
6. 未完成项和真实阻塞；
7. 与 New-API、Sub2API 的完整能力对照结论；
8. Git 提交 Hash。
