# 阶段 04｜验收清单

> 由实施者完成后逐项勾选；备注写明测试类、命令、截图或其他证据。不得填入任何真实秘密。

## A. 阶段边界与前置核对

- [x] 已阅读阶段 01、02、03 实施结果。
- [x] 已确认阶段 03 的代码提交存在且未与当前改动冲突。
- [x] 本阶段未新增 `/v1/*`。
- [x] 本阶段未实现上游 HTTP 调用、SSE、WebSocket、协议转换或实际 API 中转。
- [x] 本阶段未实现资源池、路由、模型绑定、优先级、权重、限流、并发、粘性会话、OAuth、用户 API Key、分组、倍率或计费。
- [x] React 前端未修改。

## B. `converge-contract`

- [x] 新增 Maven 模块 `converge-contract` 并注册父 POM。
- [x] 控制面和 gateway 都引用该模块。
- [x] `converge-contract` 不含 Spring、Vert.x、Redis、MyBatis、Flyway、JDBC、Servlet 依赖。
- [x] 契约包含 schema、tenant snapshot、manifest、change event、resource snapshot、secret envelope、sync state。
- [x] 控制面和 gateway 快照序列化/解析存在跨模块兼容性测试。
- [x] schema 不兼容被明确拒绝。

## C. 控制面 revision 与 Outbox

- [x] 使用 V8 或当前连续 Flyway 版本；未改既有迁移。
- [x] 存在 tenant revision 事实表。
- [x] 存在 snapshot outbox 表，包含 revision、状态、次数、重试时间、锁和安全错误摘要。
- [x] AI Provider/Connection/PublicModel/Credential/Resource 所有网关相关写操作与 revision/outbox 同一事务。
- [x] Projector 采用专用显式 tenant 查询，不因空 TenantContext 产生无条件全表读取。
- [x] Redis 失败后 outbox 未被错误标记成功。
- [x] 启动或周期任务可以重投影，Redis 清空后能恢复。
- [x] outbox 错误摘要不含 API Key 或密文。

## D. 快照与 Redis 协议

- [x] Payload key 使用 immutable revision key，写入使用 NX 或等价保护。
- [x] Manifest 是唯一 current pointer。
- [x] 发布顺序为 payload → manifest → tenant index → notify → outbox published。
- [x] 维护 tenant index、history，且 cleanup 不删除 current payload。
- [x] Pub/Sub 只做提示；gateway initial/periodic reconcile 是最终正确性来源。
- [x] Snapshot payload 包含 enabled public model 元数据与 eligible execution resource 元数据。
- [x] disabled provider/connection/credential/resource 不向 snapshot 投递 API Key。
- [x] disabled tenant 会以空快照覆盖旧资源，并继续通过 tenant index 暴露该租户的空快照状态。

## E. 秘密安全

- [x] gateway 从未读取 `AI_CREDENTIAL_*` 数据库凭据密钥。
- [x] gateway delivery AES key、HMAC signing key 与阶段 03 的 AES/HMAC key 不复用。
- [x] 控制面在投影时短暂解密后，使用 gateway delivery AES-256-GCM 重新封装秘密。
- [x] 每个 envelope 使用新的 12 字节随机 nonce 和 128 位 GCM tag。
- [x] AAD 绑定 schema、tenant、resource、credential、revision、keyId。
- [x] Manifest HMAC 固定绑定 schema、tenant、revision、payload key、sha256、keyId。
- [x] Redis、日志、审计、异常、测试输出中无 API Key 明文。
- [x] 篡改 manifest、payload、nonce、密文、AAD 关联或 HMAC 后，网关拒绝新版本。
- [x] 网关拒绝新版本后保留 last-known-good snapshot。
- [x] 非测试环境缺失必要快照密钥时，不会退化为明文。

## F. 网关运行时

- [x] 网关使用 Vert.x Redis Client，不依赖 Spring/JDBC/MyBatis/Flyway/PostgreSQL。
- [x] 网关 initial sync、Pub/Sub、periodic reconcile、重连、优雅关闭均已实现。
- [x] 网关每个 tenant 独立 copy-on-write 原子替换。
- [x] 单 tenant 刷新失败不影响其他 tenant 或本 tenant 旧有效版本。
- [x] `/internal/health` 只反映进程存活。
- [x] `/internal/ready` 遵循首次同步、空 index、损坏、degraded、max-staleness 语义。
- [x] `/internal/snapshot-status` 存在且不泄露秘密、Redis key 或密文。

## G. 测试与交付

- [x] Outbox、snapshot publisher、Redis failure/retry 测试通过。
- [x] Gateway initial sync、Pub/Sub、漏消息对账、损坏版本、multi-tenant、Redis 清空恢复测试通过。
- [x] 完整 backend `mvn test` 通过。
- [x] 文档中已更新配置、环境变量、Redis 键、同步与恢复语义。
- [x] 已填写 `docs/ai-gateway/progress/phase-04-gateway-snapshot-sync-result.md`。
- [x] 已创建中文 Git 提交。

## 备注

- 实施日期：2026-07-07
- Git 提交：本清单随中文提交提交；最终 Hash 见完成报告。
- 主要测试命令与结果：
  - `mvn -pl converge-web-service,converge-gateway -am "-Dtest=GatewaySnapshotControlPlaneIntegrationTest,GatewaySnapshotRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`：先红后绿，用于验证缺失契约/Outbox/运行时后完成实现。
  - `mvn -pl converge-contract,converge-web-service,converge-gateway -am "-Dtest=GatewaySnapshotContractTest,GatewaySnapshotControlPlaneIntegrationTest,GatewaySnapshotOutboxProjectorUnitTest,GatewaySnapshotRuntimeTest,GatewayRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`：通过。
  - `mvn test`（backend 根目录）：通过，Surefire 汇总 `reports=20 tests=146 failures=0 errors=0 skipped=0`。
  - `git diff --check`：通过，仅有 Windows 换行提示。
  - gateway/contract 禁止依赖关键词扫描：未发现禁止项。
- 遗留风险：无真实阻塞；后续阶段仍需实现实际路由、资源池、计费等不属于本阶段的能力。
