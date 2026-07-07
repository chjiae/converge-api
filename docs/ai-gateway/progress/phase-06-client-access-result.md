# 阶段 06｜下游 Client API Key、访问组与模型授权快照｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。本文件不记录任何真实 API Key 或 secret。

## 1. 实施时间与提交

- 开始时间：2026-07-07
- 完成时间：2026-07-07
- Git 提交 Hash：见最终报告和 Git 历史
- 实施分支：`master`
- 开始时 `master` 最新提交：`ad0479a docs: 新增下游 API Key 与访问授权阶段计划`

## 2. 前置阶段复核

- 阶段 01：独立 Vert.x gateway runtime、健康检查、requestId、访问日志与优雅关闭已具备。
- 阶段 02：AI provider、upstream connection、public model 控制面目录与租户隔离已具备。
- 阶段 03：上游 credential、execution resource 与控制面加密边界已具备；gateway 未读取 DB 凭据密钥。
- 阶段 04：snapshot outbox、Redis immutable payload/current pointer、manifest HMAC、payload checksum、last-known-good 已具备。
- 阶段 05：静态路由 V2、resource pool、route policy、routing-core 与 gateway V1/V2 兼容已具备。
- 当前最大 Flyway 版本：实施前为 V9，本阶段新增 V10。

## 3. 数据库与领域对象

- Flyway 迁移：新增 `V10__ai_client_access.sql`，未修改既有迁移。
- `ai_access_group`：租户级访问组，保存 code、display_name、description、status 与审计时间。
- `ai_access_group_model_grant`：访问组模型授权，精确绑定 `access_group_id + public_model_id + canonical_operation`。
- `ai_client_api_key`：下游 Client API Key 元数据，保存 keyId、mask、status、version、expires_at、salt/hash，不保存 raw key。
- `ai_client_api_key_access_group`：Key 到 AccessGroup 的租户级绑定。
- 复合约束、索引与 tenant 双重校验：所有关系均有 `(tenant_id, id)` 复合唯一或复合外键，Service 层显式校验当前租户和关联租户一致。
- 新增枚举：`AiClientApiKeyStatus`，包含 `ENABLED`、`DISABLED`、`REVOKED`。
- 无硬删除结论：Controller 未提供 DELETE；状态通过 enable/disable/revoke 管理。

## 4. Client API Key 安全实现

- 实际 key format：`cvg_live_<keyId>_<secret>`。
- keyId / secret 随机长度：keyId 为 16 字节随机数的 Base64URL 无填充结果，secret 为 32 字节随机数的 Base64URL 无填充结果。
- hash verifier 算法与固定输入：`SHA-256`，输入为带域分隔符、长度前缀、keyId、keyVersion、salt、secret 的固定二进制编码。
- salt 长度：16 字节，服务端 `SecureRandom` 生成。
- constant-time compare：gateway 使用 `MessageDigest.isEqual`。
- one-time create/rotate response：raw key 只在 create/rotate 返回一次；持久化对象和列表详情只返回 mask。
- `Cache-Control`：create/rotate 响应使用 `Cache-Control: no-store`。
- rotate / disable / revoke / expire 语义：rotate 复用 keyId、递增 version 并替换 salt/hash；DISABLED 不可认证但可重新启用；REVOKED 为终态，不可启用或轮换；expires_at 过期后 gateway 拒绝认证。
- 日志、审计、异常、响应和 Redis 脱敏结论：日志、审计、异常、status 和测试报告不输出 raw key、Authorization、x-api-key、secret、salt/hash 内容；Redis V3 payload 按协议只包含 verifier salt/hash，不包含 raw key 或 secret。
- 是否保存 raw key（必须为否）：否。

## 5. 控制面 API 与授权

- AccessGroup：实现创建、分页、详情、更新、启用、停用。
- ModelGrant：实现创建、分页、详情、更新、启用、停用，授权精确到 public model 与 canonical operation。
- ClientApiKey：实现创建、分页、详情、更新、轮换、启用、停用、撤销。
- Key-Group binding：实现创建、分页、详情、更新、启用、停用。
- 权限：复用 `TENANT_OWNER` / `TENANT_ADMIN`，并复用 `AiCatalogTenantGuard` 显式拒绝空租户上下文。
- audit：Controller 写接口使用 `@Auditable`；敏感接口只记录安全元数据。
- grant union：gateway 对同一 key 的 enabled access groups 做授权并集。
- wildcard / regex / default-all 处理：未提供相关字段；非法 operation 或非枚举输入返回 400。

## 6. Snapshot V3 与 Gateway

- Contract V3：`GatewayTenantSnapshot` 新增 access group、model grant、client API key、key-group binding 四类不可变契约。
- V1/V2/V3 兼容：gateway 最大支持 V3，同时保留 V1/V2 字段默认与旧测试；control-plane 当前发布 V3。
- outbox change types：新增 `AI_ACCESS_GROUP_CHANGED`、`AI_ACCESS_GROUP_MODEL_GRANT_CHANGED`、`AI_CLIENT_API_KEY_CHANGED`、`AI_CLIENT_API_KEY_ACCESS_GROUP_CHANGED`。
- Projector：阶段 04 projector 继续使用同一 revision/outbox、manifest HMAC、payload checksum、secret envelope、Redis immutable payload/current pointer 发布 V3。
- global client key index：gateway 从所有已验证 tenant snapshot 构建不可变 `keyId -> GatewayClientPrincipal` 本地索引。
- Gateway auth flow：每次请求只解析 Bearer key、在内存 index 中按 keyId 查找、constant-time 校验 verifier、检查状态和过期时间，不访问 DB 或 Redis。
- principal：包含 tenantId、client key id、access group id 集合和 enabled grant union；`RoutingContext` 不保存 raw key。
- V3 last-known-good：单 tenant V3 校验或拓扑编译失败时保留该 tenant 旧 snapshot 和旧 key entries，不覆盖其他 tenant。
- status 安全摘要：`/internal/snapshot-status` 仅增加 client key 计数，不暴露 keyId、hash/salt、group、Redis key、HMAC 或 payload。
- rollout 顺序：先升级 gateway，使其支持 V1/V2/V3；再升级 control-plane 发布 V3。

## 7. `GET /v1/models`

- 客户端认证入口：仅接受 `Authorization: Bearer <Client API Key>`，不接受控制面 JWT/Cookie 作为数据面身份。
- response envelope：最小 OpenAI-compatible envelope，形如 `{"object":"list","data":[{"id":"...","object":"model","owned_by":"converge"}]}`。
- 模型过滤规则：只返回当前 principal 授权、public model enabled、且存在有效 static route plan 的模型。
- 401 / 403 / 503：缺失或无效 key 返回 401；认证成功但无可用授权返回 403；snapshot not ready 或 stale 返回 503。
- 是否发起上游请求（必须为否）：否。
- 是否写入用量/账本（必须为否）：否。

## 8. 测试与验证

```text
mvn -pl converge-contract,converge-web-service,converge-gateway -am "-Dtest=AiClientAccessIntegrationTest,GatewayClientAccessRuntimeTest,GatewaySnapshotContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：首次红灯，确认新增测试能暴露未实现的 key crypto、entity、mapper 等缺口。

mvn -pl converge-gateway,converge-web-service -am test-compile
结果：通过。

mvn -pl converge-contract,converge-routing-core -am test
结果：通过；契约测试 3 个、routing-core 测试 3 个均成功。

mvn -pl converge-web-service -am "-Dtest=AiClientAccessIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：通过；7 tests，0 failures，0 errors。

mvn -pl converge-gateway -am "-Dtest=GatewayClientAccessRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：通过；3 tests，0 failures，0 errors。

mvn test
结果：通过；Reactor 全模块 SUCCESS，Tests run: 19, Failures: 0, Errors: 0, Skipped: 0，Finished at 2026-07-07T19:54:12+08:00。

rg "HttpClient|WebClient|RestTemplate|OkHttp|SSE|WebSocket|chat/completions|responses|embeddings" ...
结果：无命中。

rg "spring-boot|spring-web|redis|mybatis|flyway|postgresql|servlet|jdbc" backend/converge-contract/pom.xml backend/converge-routing-core/pom.xml backend/converge-gateway/pom.xml
结果：仅 gateway 命中允许的 vertx-redis-client。
```

- 控制面集成测试：通过。
- key crypto / verifier tests：通过。
- gateway V1/V2/V3：通过阶段 04/05/06 gateway 回归。
- global index multi-tenant：通过。
- `/v1/models`：通过 401、403、503、空列表、授权列表测试。
- raw key leak scan：通过边界扫描；命中仅为变量名、一次性响应构造和内存校验路径，不是日志/status 输出。
- backend root `mvn test`：通过。
- 未执行项与原因：无。

## 9. 阶段边界复核

- 上游 HTTP Client / SSE / WebSocket：未实现。
- Chat / Responses / Embeddings / Claude / Gemini：未实现。
- 协议转换 / retry / circuit breaker：未实现。
- 价格 / 倍率 / 账本 / 计费：未实现。
- 动态限流 / 并发 / sticky session：未实现。
- OAuth / account pool：未实现。
- React：未修改。
- Gateway DB/Redis per-request lookup：未实现；请求只读本地内存索引。
- 结论：未越过阶段 06 边界。

## 10. 偏差、风险与下一阶段输入

- 与计划偏差：无实质偏差；因 `_` 属于 Base64URL 字符集，raw key 解析采用固定长度定位分隔符，避免按字符 split 的歧义。
- 未完成项：无。
- 已知风险：阶段 06 只提供 `/v1/models` 和静态授权可见性，真实请求转发、计费、限流、动态健康状态仍需后续阶段完成。
- 对下一阶段建议：在实现真实数据面请求前，继续保持认证、授权、路由选择、上游调用和计费的边界分层，避免将 raw key 或上游凭据写入日志。

## 11. New-API / Sub2API 对照结论

- New-API：本阶段覆盖其“下游 key、模型可见性、模型列表”基础能力，但不实现 chat/responses/embedding、上游转发、计费、倍率、余额、失败重试或动态健康。
- Sub2API：本阶段覆盖其“客户端 key + 分组授权 + 模型列表”基础入口，但不实现订阅套餐映射、额度账本、账号池、并发/RPM/TPM 或支付联动。
- 是否偏离：未偏离；所有差异均属于阶段 06 明确禁止或后续阶段范围。
