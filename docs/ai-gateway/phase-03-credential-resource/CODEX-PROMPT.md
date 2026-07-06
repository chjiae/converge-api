# Codex 执行提示词｜阶段 03

请在 Converge API 仓库根目录，一次性完整实施：

```text
docs/ai-gateway/phase-03-credential-resource/README.md
```

## 必读文件

1. `AGENTS.md`
2. `docs/ai-gateway/phase-01-gateway-runtime/README.md`
3. `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md`
4. `docs/ai-gateway/phase-02-control-plane-catalog/README.md`
5. `docs/ai-gateway/progress/phase-02-control-plane-catalog-result.md`
6. `docs/ai-gateway/phase-03-credential-resource/README.md`
7. `docs/ai-gateway/phase-03-credential-resource/SOURCE-REVIEW-2026-07-06.md`
8. `docs/ai-gateway/phase-03-credential-resource/ACCEPTANCE-CHECKLIST.md`
9. 当前 AI 目录实体、Mapper XML、Service、Controller、DTO、Flyway、`AiCatalogTenantGuard`、审计、认证、测试实现。

## 执行授权与连续完成要求

我已授予你完成本阶段所需的全部操作权限。

你可以自行读写本仓库内文件、创建 Flyway 迁移、修改 Maven 模块、下载依赖、运行构建与测试、查看日志、启动停止本地服务、执行 Git add 与 Git commit。

先快速审查，简短输出关键发现和预计修改文件；**不要等待我确认**。随后直接完整实施本阶段。

遇到常规设计选择、构建失败、测试失败、依赖问题、代码风格、文件修改、Git 操作或本地服务问题，先自行定位、修复、重试和验证。除非遇到无法自行解决的真实阻塞，例如缺少必要外部权限/密钥、需要删除或迁移现有生产数据、或阶段文档与代码严重矛盾且无法安全判断，否则不要中止、不要问我批准、不要只给建议。

目标是在本次任务中完成阶段 03 的全部范围：设计、实现、测试、修复、文档、验收、提交。

## 本阶段必须完成

1. 在现有 Spring 控制面中新增 API Key 凭据保险库，实体名可使用 `AiCredential`。
2. 凭据必须属于当前租户和当前租户的 `AiProvider`。
3. 本阶段只支持 `API_KEY` 凭据类型；不要实现 OAuth、Refresh Token、Cookie 或订阅账户。
4. API Key 必须使用控制面内部 AES-256-GCM 密文保存：
   - 每次加密使用新的随机 12 字节 nonce；
   - GCM tag 为 128 位；
   - 使用稳定 AAD 绑定租户、Provider、服务端生成的 secretReference、凭据类型与 schemaVersion；
   - 数据库只保存密文、nonce、算法、keyId、指纹和掩码；
   - 不能保存或回显 API Key 明文。
5. 使用独立 HMAC 指纹密钥生成 secret fingerprint，供同租户/同 Provider 去重；不能将数据库加密主密钥复用为指纹密钥。
6. 新增生产无默认值的加密配置；非测试环境缺少密钥必须明确失败。测试可以提供测试专用密钥。
7. 实现凭据创建、查询、列表、元数据更新、启用、停用、原始 API Key 轮换；不实现删除。
8. 新增 `AiExecutionResource`：
   - 本阶段仅支持 `DIRECT_API`；
   - 绑定 Provider、已存在的 `AiUpstreamConnection` 和 `AiCredential`；
   - 支持创建、查询、列表、元数据更新、启用、停用、排空；
   - binding 一经创建不可通过更新接口改变；
   - 不实现模型绑定、资源池、路由、优先级、权重、并发、限流或运行时健康状态。
9. 必须同时通过数据库约束与 Service 显式校验保证：
   - tenant 一致；
   - provider 一致；
   - 同一 connection + credential 不可重复创建 resource；
   - 被禁用的 connection 或 credential 不允许启用 resource。
10. 所有 AI 相关 Service 入口必须继续显式拒绝空 `TenantContext`；不能仅依赖 MyBatis 拦截器。
11. 只允许现有 `TENANT_OWNER`、`TENANT_ADMIN` 管理；复用既有 `@Auditable`、`Result<T>`、`PageResult<T>`、异常和 Flyway 规范。
12. 新增单元/集成测试：
   - AES-GCM 加解密和篡改失败；
   - API Key 不出现在 DB、HTTP 响应、审计、日志或异常消息；
   - 同租户重复 Key 拒绝；
   - 跨租户/跨 Provider 资源绑定拒绝；
   - 权限、无租户上下文、状态约束；
   - 轮换与掩码；
   - 不可修改绑定与无删除接口；
   - 所有现有回归测试。
13. 本阶段完成后填写：
   - `docs/ai-gateway/phase-03-credential-resource/ACCEPTANCE-CHECKLIST.md`
   - `docs/ai-gateway/progress/phase-03-credential-resource-result.md`
14. 创建一个符合 `AGENTS.md` 的中文 Git 提交。

## 严格禁止

- 不修改 `converge-gateway` 的源码、POM 或资源；
- 不创建 `converge-contract`；
- 不新增 `/v1/*`；
- 不发起真实上游请求，不做“测试连接”；
- 不实现上游 HTTP Client、SSE、WebSocket、失败重试或协议转换；
- 不接入 OAuth、Cookie、订阅账号、授权账户；
- 不创建 ResourcePool、RoutePolicy、模型绑定、优先级、权重、并发、RPM、TPM、429 冷却、粘性会话；
- 不创建下游 API Key、访问组、套餐关联、倍率、定价、余额或计费；
- 不修改 React 前端；
- 不改造既有租户、认证、订阅、支付、卡密、审计业务；
- 不把原始 API Key 写入代码、配置、数据库迁移、审计、日志、异常、测试报告或 Git 提交信息。

## 完成标准

仅在以下条件都满足后结束：

- 阶段 README 中全部范围已实现；
- 验收清单逐项填写；
- 相关新测试和完整 Maven 回归测试已执行并尽量修复；
- `git diff --name-only -- backend/converge-gateway` 无输出；
- 实施结果文档已填写；
- 已创建 Git 提交。

最终报告必须包含：

1. 阶段前审查发现；
2. 实际修改的文件；
3. 数据库与安全设计摘要；
4. 执行过的命令与测试结果；
5. 所有未完成项及真实阻塞原因；
6. 与 New-API、Sub2API 的对照结论；
7. Git 提交 Hash。
