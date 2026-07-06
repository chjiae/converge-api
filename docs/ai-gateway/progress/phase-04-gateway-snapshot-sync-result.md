# 阶段 04｜控制面到 Vert.x 网关的安全配置快照同步｜实施结果

> 由 Codex 在阶段完成后填写。不要删除本模板章节；无内容时明确写“无”。

## 1. 实施时间与提交

- 开始时间：2026-07-07 约 01:00 +08:00
- 完成时间：2026-07-07 02:39 +08:00
- Git 提交 Hash：见最终完成报告；Git 提交无法在同一提交内容中稳定自指。
- 实施分支：master

## 2. 前置阶段复核

- 阶段 01：已阅读 runtime README 与实施结果，确认 `converge-gateway` 为独立 Vert.x 进程，已有内部健康、就绪、版本、请求 ID、访问日志和优雅关闭边界。
- 阶段 02：已阅读 catalog README 与实施结果，确认 Provider、Connection、PublicModel 均为租户内控制面对象。
- 阶段 03：已阅读 credential/resource README 与实施结果，确认 Credential 与 ExecutionResource 已具备租户隔离、加密与审计能力。
- 阶段 03 Git 提交是否存在：存在，`7b02f75 feat(AI网关): 新增上游凭据保险库与 Direct API 可执行资源`。
- 本阶段新增/修改的 Maven 模块：新增 `converge-contract`；修改根 POM、`converge-web-service` 与 `converge-gateway`。

## 3. `converge-contract` 实际实现

- 模块路径：`backend/converge-contract`。
- 依赖检查：主代码仅依赖 Jackson；禁止 Spring、Vert.x、Redis、MyBatis、Flyway、JDBC、Servlet、JWT、PostgreSQL 依赖，已通过测试与关键词扫描。
- 契约 records/enums：`GatewaySnapshotSchema`、`GatewaySnapshotSyncState`、`GatewaySecretEnvelope`、`GatewayPublicModelSnapshot`、`GatewayExecutionResourceSnapshot`、`GatewayTenantSnapshot`、`GatewaySnapshotManifest`、`GatewaySnapshotChangedEvent`、`GatewaySnapshotRedisKeys`、`GatewaySnapshotJson`、`GatewaySnapshotCrypto`。
- 序列化与跨模块兼容性测试：`GatewaySnapshotContractTest` 覆盖 contract JSON/HMAC/SecretEnvelope；`GatewaySnapshotControlPlaneIntegrationTest` 生成控制面快照并由共享契约解析校验；`GatewaySnapshotRuntimeTest` 覆盖 gateway 解析与验证。
- schema 版本策略：当前 `CURRENT_VERSION = 1`；gateway 验证 manifest、payload、event 和 envelope 均必须匹配当前 schema，不兼容版本直接拒绝。

## 4. Revision、Outbox 与 Projector

- Flyway 迁移：新增 `V8__ai_gateway_snapshot_outbox.sql`，未修改既有迁移。
- revision 表：`ai_gateway_snapshot_revision`，以 `tenant_id` 为主键，保存租户当前连续 revision。
- outbox 表：`ai_gateway_snapshot_outbox`，包含 `tenant_id`、`revision`、`change_type`、`status`、`attempt_count`、`next_attempt_at`、`locked_at`、`lock_owner`、`last_error_summary`、`published_at` 与必要索引、唯一约束。
- 触发 outbox 的 AI 写操作：Provider、Connection、PublicModel、Credential、ExecutionResource 的创建、更新、启用、停用、轮换、泄流等影响网关配置的写操作。
- 同事务保证方式：`GatewaySnapshotChangeRecorder` 使用 `MANDATORY` 事务传播，在各业务 Service 原事务内递增 revision 并写入 outbox。
- Projector 领取、合并、重试策略：`GatewaySnapshotOutboxProjector` 使用 `FOR UPDATE SKIP LOCKED` 安全领取 due outbox，按租户合并到最新 revision，发布成功后标记 `PUBLISHED`，失败保留/递增 `FAILED` 并设置下次重试时间。
- 显式 tenant 查询方式：`GatewaySnapshotQueryMapper.xml` 所有 Projector 查询均显式 `WHERE tenant_id = #{tenantId}`，不依赖空 `TenantContext` 扫全表。
- Redis 清空/启动重投影策略：Projector 启动延迟后及周期性调用 `reprojectAllTenantsOnce`，遍历 revision 事实表重建 Redis 当前快照；Redis 发布失败不会丢弃 outbox。

## 5. 快照安全与 Redis 协议

- Gateway 投递 AES key 配置：控制面与 gateway 均通过 `AI_GATEWAY_SNAPSHOT_ENCRYPTION_KEY_BASE64` / `ai.gateway.snapshot.encryption-key-base64` 读取 32 字节 Base64 密钥，无生产默认值。
- Gateway 签名 HMAC key 配置：控制面与 gateway 均通过 `AI_GATEWAY_SNAPSHOT_SIGNING_KEY_BASE64` / `ai.gateway.snapshot.signing-key-base64` 读取至少 32 字节 Base64 密钥，无生产默认值。
- 与阶段 03 密钥隔离证明：控制面启动校验 gateway 投递 AES/HMAC 与阶段 03 active/fingerprint key 不得相同；gateway 代码扫描未读取 `AI_CREDENTIAL_*` 或阶段 03 凭据加密配置。
- Secret envelope 算法 / nonce / AAD：AES-256-GCM，12 字节随机 nonce，128 位 tag；AAD 绑定 schema、tenant、executionResource、credential、revision、keyId。
- Manifest HMAC 输入：HMAC-SHA256 固定绑定 schema、tenant、revision、payload Redis key、payload SHA-256、gateway keyId。
- Redis 键：`converge:gateway:snapshot:tenant-index`、`converge:gateway:snapshot:tenant:{tenantId}:revision:{revision}`、`converge:gateway:snapshot:tenant:{tenantId}:current`、`converge:gateway:snapshot:tenant:{tenantId}:history`、`converge:gateway:snapshot:changed`。
- 发布顺序：payload 使用 NX 写入 immutable revision key，随后写 current manifest、tenant index、history、Pub/Sub changed event，最后标记 outbox published。
- history / 清理策略：使用 sorted set 维护历史 revision，只裁剪超过保留数量且不是 current 的 payload。
- 任何 plaintext secret 是否进入 Redis（必须为否）：否。控制面只在内存中短暂解密阶段 03 数据库密文，随后用 gateway 投递密钥重新封装；测试覆盖 Redis payload/manifest 不含明文。

## 6. Gateway 快照运行时

- Redis Client 与连接策略：gateway 引入 Vert.x Redis Client，维护普通命令 client 与 dedicated Pub/Sub connection；失败后通过 periodic reconcile 恢复，不接 PostgreSQL 或 Spring。
- initial sync：启动时先从 tenant index 对账并加载 current manifest/payload，空 index 视为成功就绪。
- Pub/Sub hint：订阅 `converge:gateway:snapshot:changed`，收到合法 change event 后触发对应租户刷新；非法 event 被忽略，正确性依赖对账。
- periodic reconcile：按 `GATEWAY_SNAPSHOT_RECONCILE_INTERVAL_MS` 全量对账 tenant index，恢复漏 Pub/Sub 与 Redis 清空后的重新发布。
- local snapshot 原子替换：每个 tenant 成功验证后构造不可变 runtime snapshot，使用 copy-on-write map 原子替换。
- last-known-good 行为：新 manifest/payload/envelope 校验失败时拒绝新版本，保留旧有效 snapshot；单 tenant 失败不影响其他 tenant。
- ready/health/snapshot-status 行为：`/internal/health` 只表示进程存活；`/internal/ready` 覆盖首次失败、空 index、损坏、degraded、max-staleness；`/internal/snapshot-status` 只暴露状态与计数，不暴露 Redis key、API Key、密文、nonce、HMAC 或 payload。
- graceful shutdown：关闭 timer、Pub/Sub connection、Redis client 与 Vert.x HTTP server，不遗留网关事件循环线程。

## 7. 测试与验证

```text
mvn -pl converge-web-service,converge-gateway -am "-Dtest=GatewaySnapshotControlPlaneIntegrationTest,GatewaySnapshotRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：先失败，缺失 contract/outbox/gateway runtime 类型，符合 TDD 红灯预期。

mvn -pl converge-contract,converge-web-service,converge-gateway -am "-Dtest=GatewaySnapshotContractTest,GatewaySnapshotControlPlaneIntegrationTest,GatewaySnapshotOutboxProjectorUnitTest,GatewaySnapshotRuntimeTest,GatewayRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：通过。

mvn test
结果：backend 根目录通过，Surefire 汇总 reports=20 tests=146 failures=0 errors=0 skipped=0。

git diff --check
结果：通过，仅有 Windows 换行提示。

gateway/contract 禁止依赖关键词扫描
结果：未发现禁止项。
```

- 控制面 tests：`GatewaySnapshotControlPlaneIntegrationTest`、`GatewaySnapshotOutboxProjectorUnitTest` 通过。
- gateway tests：`GatewaySnapshotRuntimeTest`、`GatewayRuntimeTest` 通过。
- cross-module contract tests：`GatewaySnapshotContractTest` 通过。
- Redis failure/retry tests：覆盖 Redis 发布失败后 outbox 保留并重试成功。
- corruption/tamper tests：覆盖 manifest、payload/envelope 相关篡改拒绝与 last-known-good 保留。
- multi-tenant isolation tests：覆盖单租户损坏不影响其他租户加载。
- backend root `mvn test`：通过，`reports=20 tests=146 failures=0 errors=0 skipped=0`。
- 未执行测试及原因：无。

## 8. 阶段边界复核

- `/v1/*`：未新增数据面 `/v1/*`；仅保留既有控制面 `/api/v1`。
- 上游 HTTP Client / SSE / WebSocket：未实现。
- PostgreSQL gateway direct access：gateway 未引入 JDBC/PostgreSQL/MyBatis/Flyway。
- ResourcePool / Route / Model binding：未实现。
- OAuth / account pool：未实现。
- Client API Key / groups / pricing / billing：未实现。
- React 前端：未修改。
- 结论：阶段边界未越界。

## 9. 偏差、风险与下一阶段输入

- 与计划偏差：无实质偏差；disabled tenant 采用空快照覆盖旧资源并保留 tenant index 的方式，便于 gateway 明确清空本地资源。
- 未完成项：无。
- 已知风险：无真实阻塞；本阶段仍未实现实际数据面转发、路由、资源池、计费等后续阶段能力。
- 后续建议：下一阶段接入实际路由前，继续复用当前 snapshot 契约进行只读配置分发，并保持 Pub/Sub 仅作为提示。

## 10. New-API / Sub2API 对照结论

- New-API：本阶段对应其集中配置变更后向数据面分发模型、上游凭据与通道元数据的能力，但不实现 API 中转、模型路由、计费或客户端 API Key。
- Sub2API：本阶段对应其订阅/上游账号配置向运行时同步的基础机制，但不引入授权账号池、Cookie/OAuth、模型绑定或倍率计费。
- 是否偏离：未偏离；本阶段只交付安全快照同步、加密封装、Redis 发布与 gateway 本地加载。
