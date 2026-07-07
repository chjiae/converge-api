# 阶段 06｜验收清单

> 由实施者完成后逐项勾选；备注给出测试、命令或代码证据。禁止填写任何真实 API Key 或 secret。

## A. 前置与阶段边界

- [x] 已阅读阶段 01~05 的 README、协议和实施结果。证据：实施前复核 `docs/ai-gateway/phase-01-gateway-runtime` 到 `phase-05-static-routing` 及 progress 文档。
- [x] 已确认阶段 05 对应 Git commit 与 root `mvn test` 状态。证据：阶段 05 progress 记录 commit `727e535`，本阶段开始时 `master` 为 `ad0479a`。
- [x] 未新增上游 HTTP Client、SSE、WebSocket、协议转换、重试、熔断。证据：`rg "HttpClient|WebClient|RestTemplate|OkHttp|SSE|WebSocket|chat/completions|responses|embeddings" ...` 无命中。
- [x] 未新增 Chat Completions / Responses / Embeddings / Claude / Gemini 数据面端点。证据：仅在 gateway 注册 `GET /v1/models`。
- [x] 未实现价格、倍率、余额、账本、计费、配额或支付。证据：本阶段仅新增 key、access group、grant、binding 与模型列表。
- [x] 未实现限流、并发、429 cooldown、健康检查、sticky session、OAuth 或账户池。证据：未新增相关领域对象或网关运行时逻辑。
- [x] React 前端未修改。证据：`git status` 仅包含 `backend` 与 `docs/ai-gateway` 范围。

## B. Client API Key 安全

- [x] 使用连续 Flyway version，未改既有 migration。证据：新增 `V10__ai_client_access.sql`，未修改 V1~V9。
- [x] Key 由 `SecureRandom` 生成；secret 至少 256 bit。证据：`GatewayClientKeyCrypto` 使用 32 字节 secret。
- [x] Key 格式符合 `cvg_live_<keyId>_<secret>`。证据：契约测试覆盖格式解析。
- [x] `keyId` 全局唯一且不可变。证据：`ai_client_api_key.key_id` 唯一约束，rotate 复用 keyId。
- [x] DB 不保存 raw key / secret / encrypted client secret。证据：V10 仅保存 salt、hash、mask、状态和版本。
- [x] DB 保存随机 verifier salt、固定算法、不可逆 hash、key version、mask、状态和过期信息。证据：`ai_client_api_key` 表字段与集成测试断言。
- [x] verifier fixed encoding 无歧义，比较使用 constant-time API。证据：`GatewayClientKeyCrypto` 使用定长二进制编码与 `MessageDigest.isEqual`。
- [x] create/rotate 只一次返回 raw key，响应带 `Cache-Control: no-store`。证据：`AiClientAccessController` 与集成测试覆盖。
- [x] rotate 递增 version，旧 key 在新 snapshot 生效后不可用。证据：service 更新 salt/hash/version 并写入 outbox。
- [x] revoked key 不可 re-enable 或 rotate。证据：`AiClientAccessIntegrationTest` 覆盖终态语义。
- [x] gateway/config/log/audit/exception/Redis/test output 无 raw key、Authorization、x-api-key 明文泄露。备注：按阶段协议，Redis V3 payload 内包含认证所需的 verifier salt/hash，不包含 raw key 或 secret；内部状态、日志、审计、异常和测试报告不输出 salt/hash 内容。
- [x] 敏感 Controller 入参日志已做 redaction。证据：key create/rotate 只记录安全元数据，不记录 raw key、Authorization、x-api-key 或 verifier。

## C. AccessGroup 与授权

- [x] `AiAccessGroup` 不与 `AiResourcePool` 混用。证据：V10 新建独立 access group 表。
- [x] Key、AccessGroup、Grant、Binding 均 tenant 隔离并有 DB + Service 双重校验。证据：复合外键与 `AiCatalogTenantGuard`/Service 显式校验。
- [x] AccessGroup grant 精确到 `PublicModel + CanonicalOperation`。证据：grant DTO 与 Service 只接受 public model id + canonical operation。
- [x] wildcard/regex/default-all grant 被拒绝。证据：枚举绑定和 400 错误处理测试。
- [x] Key 可绑定多个 group；effective grants 为 enabled union。证据：gateway principal 构建时合并 enabled group grant。
- [x] disabled key/group/grant/model 不产生授权。证据：控制面状态与 gateway V3 编译过滤。
- [x] 无 DELETE hard-delete API。证据：集成测试对 DELETE 返回非 2xx，Controller 未提供删除方法。
- [x] 仅 Tenant Owner/Admin 可管理；Member、未认证、无 tenant super admin 被拒绝。证据：Controller 复用 `@PreAuthorize` 与租户守卫。
- [x] 所有写操作进入当前 revision/outbox 与审计。证据：新增 change type 并在 Service 事务内调用 `GatewaySnapshotChangeRecorder`，Controller 方法带 `@Auditable`。

## D. Snapshot V3 与 Gateway

- [x] Contract V3 已增加 immutable key/group/grant/binding snapshot。证据：`GatewayTenantSnapshot` 新增 V3 列表契约。
- [x] Gateway 支持 V1/V2/V3。证据：schema 最大版本为 V3，旧构造和运行时测试保留 V1/V2 兼容。
- [x] rollout 文档为 Gateway 先、Control Plane 后。证据：progress 结果文档已记录。
- [x] V3 使用既有 manifest HMAC、payload checksum、immutable Redis payload/current pointer。证据：沿用阶段 04 projector 写入路径。
- [x] gateway 不读取 ProviderCredential DB encryption key。证据：gateway 仅依赖契约、routing-core、Vert.x Redis，不依赖 web-service DB 加密配置。
- [x] Gateway local global key index 是 immutable/copy-on-write。证据：`AtomicReference<Map<String, GatewayClientKeyIndexEntry>>` 整体替换。
- [x] Tenant 新 snapshot 成功时同时替换 tenant entries 和其 key entries。证据：`GatewaySnapshotRuntime` 在 V3 校验/编译成功后重建全局 key index。
- [x] 单 tenant V3 无效时保留该 tenant 旧 snapshot/key entries，不影响其他 tenant。证据：gateway malformed V3 测试覆盖 last-known-good。
- [x] request authentication 不访问 DB 或 Redis。证据：`/v1/models` 仅查询本地内存 index。
- [x] snapshot status 只返回安全计数。证据：仅暴露 tenant/model/route/clientKey 计数，不暴露 keyId、hash、salt、group 或 Redis key。

## E. `/v1/models`

- [x] 只新增 `GET /v1/models`。证据：`GatewayRouterFactory` 仅新增该数据面路由。
- [x] Gateway 使用 Client API Key，拒绝控制面 JWT/Cookie。证据：严格解析 `Authorization: Bearer <Client API Key>`。
- [x] 缺失/无效 key 返回统一 401 data-plane envelope。证据：`GatewayClientAccessRuntimeTest` 覆盖。
- [x] 有效 key 无授权时返回安全结果，不泄露其他 tenant 数据。证据：无 grant 返回 403，空授权列表仅返回当前 principal 可见结果。
- [x] model list 仅含有效 grant 且有效 static route plan 的 PublicModel。证据：`authorizedModelCodes` 同时检查 grant union 与 route plan。
- [x] list response 为最小 OpenAI-compatible envelope。证据：响应为 `{"object":"list","data":[...]}`。
- [x] 未向上游发请求，未产生用量/账单。证据：无 HttpClient 命中，无 usage/billing 代码路径。
- [x] snapshot 未 ready/stale 时返回 503。证据：gateway 测试覆盖 NOT_READY 场景。

## F. 测试与交付

- [x] key security/rotation/revoke/expire/redaction test 通过。证据：`AiClientAccessIntegrationTest` 与 `GatewaySnapshotContractTest`。
- [x] role/tenant/grant union test 通过。证据：`AiClientAccessIntegrationTest` 与 `GatewayClientAccessRuntimeTest`。
- [x] V1/V2/V3 compatibility 与 malformed V3 last-known-good test 通过。证据：契约测试、gateway runtime 测试和阶段 04/05 回归。
- [x] multi-tenant global key index test 通过。证据：`GatewayClientAccessRuntimeTest`。
- [x] `/v1/models` 401/403/503/empty/authorized test 通过。证据：`GatewayClientAccessRuntimeTest`。
- [x] root backend `mvn test` 通过。证据：2026-07-07 19:54:12，Reactor 全模块 `BUILD SUCCESS`。
- [x] 已填 `docs/ai-gateway/progress/phase-06-client-access-result.md`。
- [x] 已创建中文 Git commit。备注：Hash 以最终报告和 Git 历史为准。

## 备注

- 实施日期：2026-07-07
- Git 提交：见最终报告和 Git 历史
- 关键测试命令：`mvn test`
- 遗留风险：无真实阻塞；后续阶段仍需实现真实上游请求、计费、限流等未纳入阶段 06 的能力。
