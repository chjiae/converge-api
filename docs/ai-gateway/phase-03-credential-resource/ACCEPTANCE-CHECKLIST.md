# 阶段 03｜验收清单

> 由实施者完成后逐项勾选，并在备注中填写证据、测试名称或命令。

## A. 前置与边界

- [ ] 已阅读阶段 01、02 实施结果并确认其已完成。
- [ ] 未修改 `backend/converge-gateway` 的源码、POM 或资源。
- [ ] 未新增 `/v1/*`、上游 HTTP 调用、SSE、WebSocket、协议转换。
- [ ] 未新增 OAuth、Cookie、订阅账号、账户池、资源池、路由、模型绑定、价格、倍率、计费或前端。

## B. Flyway 与领域模型

- [ ] 使用 V7 或当前最大版本后的连续 Flyway 迁移；未改既有迁移。
- [ ] 已新增 `ai_credential`，包含租户、Provider、密文、nonce、算法、keyId、指纹、掩码、秘密版本与状态。
- [ ] 已新增 `ai_execution_resource`，包含租户、Provider、Connection、Credential、DIRECT_API、管理状态。
- [ ] DB 约束支持同租户/Provider 一致性、唯一资源绑定、秘密指纹去重。
- [ ] 新表未加入 `ConvergeTenantLineHandler.IGNORE_TABLES`。
- [ ] 请求 DTO 没有可写 `tenantId` 或加密内部字段。

## C. 凭据安全

- [ ] API Key 只在入参处理的必要瞬间以明文存在。
- [ ] 使用 AES-256-GCM，随机 12 字节 nonce，128 位 tag。
- [ ] AAD 绑定 tenant、Provider、secretReference、credentialType 和 schemaVersion。
- [ ] 数据库加密主密钥与指纹 HMAC 密钥分离。
- [ ] 生产配置没有密钥默认值；缺失时明确失败。
- [ ] 响应、审计、普通日志、异常、测试报告均无原始 API Key。
- [ ] 密文、nonce 或 AAD 关联被篡改后解密失败。
- [ ] 轮换 API Key 能更新密文/指纹/掩码/版本，且不暴露旧 Key。
- [ ] 没有读取明文或删除凭据接口。

## D. 租户、权限与资源一致性

- [ ] Service 层显式拒绝空 `TenantContext`。
- [ ] 仅 `TENANT_OWNER` / `TENANT_ADMIN` 可管理。
- [ ] `TENANT_MEMBER`、未认证、无租户 `SUPER_ADMIN` 均被拒绝。
- [ ] Credential 的 Provider 属于当前租户。
- [ ] Resource 的 Provider、Connection、Credential 同租户且同 Provider。
- [ ] 禁用 Connection 或 Credential 时不能启用 Resource。
- [ ] Resource binding 创建后不可变更。
- [ ] 同一 Connection + Credential 的重复 Resource 被拒绝。
- [ ] 资源支持启用、停用、排空，未引入运行时健康状态。

## E. 测试与交付

- [ ] 密码学单元测试通过。
- [ ] 凭据/资源集成测试通过。
- [ ] 完整 `mvn test` 通过。
- [ ] 所有本阶段配置要求已写入文档或示例，且没有真实密钥。
- [ ] 已填写 `docs/ai-gateway/progress/phase-03-credential-resource-result.md`。
- [ ] 已创建中文 Git 提交。

## 备注

- 实施日期：
- Git 提交：
- 测试命令与结果：
- 遗留问题：
