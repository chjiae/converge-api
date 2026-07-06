# Codex 执行提示词｜阶段 04

请在 Converge API 仓库根目录，一次性完整实施：

```text
docs/ai-gateway/phase-04-gateway-snapshot-sync/README.md
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
10. `docs/ai-gateway/phase-04-gateway-snapshot-sync/SOURCE-REVIEW-2026-07-07.md`
11. `docs/ai-gateway/phase-04-gateway-snapshot-sync/ACCEPTANCE-CHECKLIST.md`
12. 当前根 POM、三个现有 Maven 模块、Flyway、AI catalog/credential/resource Service、Redis 配置、TenantContext/租户拦截、审计、测试与 GatewayRuntime 实现。

## 执行授权与连续完成要求

我已授予你完成本阶段所需的全部操作权限。

你可以自行读写本仓库文件、创建迁移、修改 Maven 模块、下载依赖、运行构建与测试、启动停止本地服务、使用 Testcontainers、查看日志、执行 Git add 与 Git commit。

先快速审查并简短输出关键发现与预计改动文件；**不要等待我确认**。随后直接连续完成本阶段全部工作：设计、实现、测试、修复、文档、验收、提交。

遇到常规设计选择、构建/测试失败、依赖问题、Redis/Testcontainers 问题、代码风格、文件修改或 Git 操作问题，优先自行定位、修复、重试。除非出现无法安全继续的真实阻塞（外部权限、真实生产数据破坏风险、阶段文档与当前代码无法调和的严重冲突），否则不要中止、不要要求我批准、不要只给建议。

本阶段设计文档的目标优先级高于旧阶段文档；发现旧阶段文档与实际代码存在轻微偏差时，以现有代码、AGENTS.md、阶段 04 安全边界为准，做最小兼容修正并在结果文档说明。

## 本阶段必须完整完成

### A. 共享契约

1. 新增 `backend/converge-contract` Maven 模块，并注册到根 POM。
2. 控制面与 `converge-gateway` 都依赖它。
3. 模块仅包含快照相关不可变契约、schema 常量、纯值对象；不得依赖 Spring、Vert.x、Redis、MyBatis、Flyway、数据库驱动、Servlet。
4. 实现本阶段文档定义的 Manifest、TenantSnapshot、SecretEnvelope、ChangedEvent、SyncState 等共享结构。
5. 建立跨模块兼容性测试：控制面生成的 snapshot/manifest 必须能被 gateway 解析和验证。

### B. 控制面事实状态、Outbox 与发布

6. 新增连续 Flyway 迁移（预期 V8；以当前实际最大版本为准），建立：
   - tenant snapshot revision 事实表；
   - snapshot outbox 表；
   - 合理的唯一约束、索引、状态、重试与锁字段。
7. 改造阶段 02、03 的所有影响网关配置的写操作，使它们在**同一事务**内：
   - 递增 tenant revision；
   - 写入 outbox；
   - 不在请求事务中直接写 Redis 或发 Pub/Sub。
8. 实现专用 tenant snapshot 查询：任何后台 Projector 查询必须显式 `WHERE tenant_id = ?`，不得借助空 `TenantContext` 获得全量数据。
9. 实现 `GatewaySnapshotOutboxProjector`：
   - 安全领取、合并、重试 outbox；
   - 构建 tenant 快照；
   - 阶段 03 的数据库密文 API Key 只在控制面短暂解密；
   - 使用单独网关投递 AES-256-GCM 密钥重新封装；
   - 生成 HMAC-signed manifest、immutable payload、Redis current pointer、tenant index、history；
   - Redis 发布失败时不丢 Outbox；
   - 启动和周期性重投影可恢复 Redis 清空。
10. Redis 中不得出现 API Key 明文；日志、审计、异常、outbox error 均不得出现 API Key。
11. 所有非测试密钥配置必须无默认值；缺失时明确失败或使 Publisher 不可运行，不能退化为明文。

### C. 网关快照运行时

12. 在 `converge-gateway` 引入 Vert.x Redis client；不得引入 Spring、JDBC、MyBatis、Flyway 或 PostgreSQL。
13. 扩展网关配置，读取 Redis URI、快照投递 key ID/加密 key/签名 key、对账周期、最大陈旧时间、历史保留量等；不得读取 `AI_CREDENTIAL_*` 数据库加密密钥。
14. 实现：
   - initial reconciliation；
   - dedicated Pub/Sub subscription；
   - periodic reconciliation；
   - manifest HMAC + payload SHA-256 + schema + tenant/revision + secret envelope 验证；
   - copy-on-write tenant local snapshot 原子替换；
   - 失败时保留 last-known-good snapshot；
   - reconnect 与优雅关闭。
15. 调整 `/internal/ready` 为文档指定的快照就绪语义。
16. 新增安全的 `/internal/snapshot-status`，仅暴露状态与计数，不暴露任何 Redis key、API Key、密文、nonce、HMAC 或 payload。
17. `/internal/health` 仍代表进程存活；不得被 Redis 状态影响。

### D. 测试、回归、文档与提交

18. 新增或更新测试，至少覆盖：
   - control-plane mutation 与 revision/outbox 同事务；
   - payload/manifest 无 API Key 明文；
   - payload/manifest/nonce/AAD/HMAC 篡改拒绝；
   - outbox Redis 失败重试；
   - gateway startup initial sync；
   - Pub/Sub 刷新；
   - 漏 Pub/Sub 后 periodic reconciliation 恢复；
   - 损坏新版本不覆盖 last-known-good；
   - 多 tenant 隔离，一个 tenant 失败不影响另一个；
   - Redis 清空后 control-plane 重投影 + gateway 恢复；
   - readiness 的首次失败、空 index 成功、degraded、max-staleness 行为；
   - `converge-contract` 禁止依赖扫描。
19. 执行 backend 根目录的完整 Maven 回归：`mvn test`。
20. 填写：
   - `docs/ai-gateway/phase-04-gateway-snapshot-sync/ACCEPTANCE-CHECKLIST.md`
   - `docs/ai-gateway/progress/phase-04-gateway-snapshot-sync-result.md`
21. 创建符合 `AGENTS.md` 的中文 Git 提交。

## 严格禁止

- 不新增 `/v1/*`；
- 不实现上游 HTTP Client、API 中转、SSE、WebSocket、失败重试、协议转换；
- 不让 gateway 直连 PostgreSQL 或阶段 03 的数据库凭据加密密钥；
- 不创建 ResourcePool、RoutePolicy、RouteTarget、模型绑定、优先级、权重、限流、并发、429 冷却、粘性会话；
- 不实现 OAuth、Refresh Token、Cookie、订阅账号或授权账户；
- 不实现下游 API Key、访问分组、套餐映射、倍率、定价、余额、账单或支付；
- 不修改 React 前端；
- 不把 Redis Pub/Sub 作为唯一可靠同步来源；
- 不将 API Key 明文写入 Redis、数据库、日志、审计、异常、测试报告、文档示例或 Git 提交信息；
- 不破坏阶段 01~03 已完成的租户、权限、审计、加密和网关边界。

## 最终完成标准

只有在以下全部满足后才结束任务：

- 阶段 README 和 SNAPSHOT-PROTOCOL 中所有范围已落地；
- 自动化测试、故障注入测试和完整 `mvn test` 已执行并尽量修复；
- 快照可从控制面安全发布到 Redis；
- 网关可不访问数据库地加载、验证、原子更新本地快照；
- Redis Pub/Sub 丢消息、Redis 清空、损坏 payload 等场景均有恢复或安全失败行为；
- 现有功能未回归；
- 验收清单和结果文档已填写；
- Git 提交已创建。

最终报告必须包含：

1. 前置审查发现；
2. 修改文件清单；
3. 快照协议、加密与恢复机制摘要；
4. 实际执行的命令和测试结果；
5. 未完成项及真实阻塞原因；
6. 与 New-API、Sub2API 的对应结论；
7. Git 提交 Hash。
