# Codex 执行提示词｜阶段 06

请在 Converge API 仓库根目录，一次性完整实施：

```text
docs/ai-gateway/phase-06-client-access/README.md
```

## 必读文件

1. `AGENTS.md`
2. 阶段 01～05 的 README、协议文档与 progress 实施结果
3. `docs/ai-gateway/phase-06-client-access/README.md`
4. `docs/ai-gateway/phase-06-client-access/CLIENT-KEY-PROTOCOL.md`
5. `docs/ai-gateway/phase-06-client-access/CAPABILITY-COVERAGE-MATRIX.md`
6. `docs/ai-gateway/phase-06-client-access/SOURCE-REVIEW-2026-07-07.md`
7. `docs/ai-gateway/phase-06-client-access/ACCEPTANCE-CHECKLIST.md`
8. 当前 `master` 的 POM、Flyway、鉴权/租户/审计、Gateway Snapshot V2、Gateway runtime、routing core、测试结构与配置。

## 执行授权与连续完成要求

我已授予你完成本阶段所需的全部操作权限。

你可以自行读写仓库、创建连续 Flyway migration、修改 Maven 模块、下载依赖、运行测试和 Testcontainers、查看日志、执行 Git add/commit。

先审查当前代码和前置结果，简短输出关键发现与预计修改文件；**不要等待确认**。随后连续完成设计、实现、测试、修复、文档、验收与提交。

普通取舍、构建失败、测试失败、Redis/Testcontainers 问题、命名调整、代码风格与 Git 操作均自行处理并重试。只有真实无法安全继续的外部阻塞才停止。

阶段 06 文档高于旧草稿；如实际既有命名需要适配，做最小兼容调整并在 progress 结果中记录。

## 本阶段必须完成

### A. 控制面数据与安全

1. 使用当前最大 Flyway 版本后的连续 migration（预期 V10，不得修改 V1~V9）。
2. 新增 tenant 级：
   - `AiAccessGroup`
   - `AiAccessGroupModelGrant`
   - `AiClientApiKey`
   - `AiClientApiKeyAccessGroup`
3. 所有关系同时具备数据库 tenant 一致性约束和 Service 显式校验。
4. 不创建万能 Group，不混用 `AiAccessGroup` 与 `AiResourcePool`。
5. Client key 必须由服务端 `SecureRandom` 生成，格式与 `CLIENT-KEY-PROTOCOL.md` 一致。
6. 原始 key 只在 create/rotate response 返回一次；持久化只保存随机 salt + SHA-256 verifier。
7. 以 fixed binary encoding 构造 verifier；比较必须 constant-time。
8. Key 可以 enable/disable/revoke/rotate/expire；REVOKED 不得重新启用或轮换。
9. create/rotate response 设置 `Cache-Control: no-store`。
10. 不得在日志、审计、异常、数据库、Redis、测试报告中记录 raw key、secret、Authorization、x-api-key、verifier hash/salt。
11. 用统一 redaction 兼容 AGENTS.md 的 Controller 入参日志要求：敏感接口只记录安全元数据。

### B. 控制面 API、授权与 Outbox

12. 复用 `TENANT_OWNER`/`TENANT_ADMIN`、`AiCatalogTenantGuard`、`@Auditable`、`Result<T>`、`PageResult<T>`。
13. 实现 AccessGroup、精确 ModelGrant、ClientApiKey、Key-Group binding 的 CRUD/状态接口，禁止硬删除。
14. 所有 Key/Group/Grant/Binding 写操作必须在原事务中调用既有 `GatewaySnapshotChangeRecorder`。
15. 增加对应 change types；不得新建另一套 outbox。
16. AccessGroup 多组授权采用 grant union；仅支持 exact `PublicModel + CanonicalOperation` grant，拒绝 wildcard/regex/default-all。

### C. Snapshot V3 与 Gateway

17. 扩展 `converge-contract` 为 V3，保留 V1/V2 兼容。
18. V3 增加 client API key、access group、model grant、key-group binding 的 immutable contract。
19. 阶段 04 Projector 在现有 manifest/checksum/secret envelope/Redis immutable payload 基础上发布 V3。
20. Gateway 必须支持 V1/V2/V3，并安全 rollout：先升级 Gateway，再升级 Control Plane。
21. Gateway 基于全部已验证 tenant snapshot 建立 immutable global `keyId → GatewayClientPrincipal` index。
22. tenant snapshot V3 成功验证/编译后，copy-on-write 同时替换 tenant snapshot 和相应 global key index entries。
23. V3 更新失败/篡改/无效时，保留该 tenant 旧 key entries 与 last-known-good。
24. 单个请求验证不得访问 PostgreSQL 或 Redis；不得读取 `AI_CREDENTIAL_*`。
25. 实现 strict bearer key extractor、verifier、principal 和 model authorizer；RoutingContext 不得保存 raw key。
26. 调整安全 access log，保证 Authorization/x-api-key 未被记录。
27. `/internal/snapshot-status` 只能增加安全汇总计数，不能泄露 keyId、hash/salt、group、secret、Redis key 或 HMAC。

### D. 首个公开端点

28. 新增仅一个公开数据面 endpoint：`GET /v1/models`。
29. 只接受有效 Client API Key，返回授权且有有效 static route plan 的 PublicModel。
30. 使用最小 OpenAI-compatible model list 成功 envelope。
31. 认证失败统一 401；认证成功但无权限才 403；Gateway not ready/stale 为 503。
32. 不调用上游、不使用 HttpClient、不写 usage/billing、不做缓存外 Redis 查询。
33. 管理端 `/api/v1/*` 错误格式与数据面 `/v1/*` 错误格式必须分离。

### E. 测试、文档、提交

34. 最少覆盖：
   - key one-time generation、hash/salt、constant-time verifier、rotation、disable/revoke/expire；
   - secret / Authorization / verifier leak scan；
   - tenant/role/cross-tenant 拒绝；
   - group grant union、disabled group/grant/model；
   - V1/V2/V3 contract compatibility；
   - V3 outbox/projector；
   - global key index multi-tenant atomic swap；
   - malformed V3 preserves last-known-good;
   - `/v1/models` 401/403/503/empty list/authorized list；
   - no upstream HTTP invocation；
   - gateway/routing/contract dependency constraints；
   - root backend `mvn test`.
35. 填写：
   - `docs/ai-gateway/phase-06-client-access/ACCEPTANCE-CHECKLIST.md`
   - `docs/ai-gateway/progress/phase-06-client-access-result.md`
36. 按 `AGENTS.md` 创建中文 Git 提交。

## 严格禁止

- 不实现 POST `/v1/chat/completions`、`/v1/responses`、Embedding、Claude、Gemini、SSE 或任何上游 HTTP 调用；
- 不做协议转换、重试、熔断或 HttpClient；
- 不做价格、倍率、余额、账本、配额、支付或套餐映射；
- 不做 RPM/TPM、并发、429 冷却、健康检查、sticky session；
- 不做 OAuth、Refresh Token、Cookie、授权账户或账号池；
- 不做 wildcard/regex grant、deny rule、AccessGroup route override；
- 不修改 React 前端；
- 不让 Gateway 访问 PostgreSQL 或从 Redis 为每个请求查询 Key；
- 不让 Gateway 使用阶段 03 DB credential key；
- 不在任何位置保存/显示 raw Client API Key 除 create/rotate 的本次 no-store response；
- 不破坏 V1/V2 snapshot、static routing、tenant isolation、审计和 last-known-good 语义。

## 最终完成标准

全部满足才可结束：

- V10（或正确连续版本）与阶段实体/API/权限/审计完成；
- snapshot V3 可安全分发；
- Gateway 可无 DB/Redis per-request 地认证 Client API Key；
- `/v1/models` 可只返回授权且静态可路由模型；
- raw key 无泄露，rotation/revocation 语义有测试；
- V1/V2/V3 兼容与 last-known-good 有测试；
- root `backend/mvn test` 通过；
- 验收清单、progress 结果填写完成；
- 中文 Git 提交已创建。

最终报告必须包含：

1. 前置审查发现与实际最新 commit；
2. 修改文件清单；
3. key storage/verifier/redaction 设计摘要；
4. V3 rollout 与 gateway global index 摘要；
5. `/v1/models` 的认证/授权行为；
6. 实际执行命令和测试结果；
7. 未完成项与真实阻塞；
8. New-API/Sub2API 完整能力对照结论；
9. Git 提交 Hash。
