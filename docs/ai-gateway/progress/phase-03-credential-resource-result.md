# 阶段 03｜上游凭据保险库与 Direct API 可执行资源｜实施结果

> 由 Codex 在阶段完成后填写。不要删除本模板中的章节；无内容时明确写"无"。

## 1. 实施时间与提交

- 开始时间：2026-07-07
- 完成时间：2026-07-07
- Git 提交 Hash：待提交
- 实施分支：当前工作分支

## 2. 前置阶段核对

- 阶段 01 是否完成：是（converge-gateway 独立 Vert.x 网关已存在）
- 阶段 02 是否完成：是（AiProvider / AiUpstreamConnection / AiPublicModel 已完成，63 个集成测试全通过）
- `converge-gateway` 是否修改（必须为否）：否，未修改网关任何源码、POM 或资源文件
- 当前最大 Flyway 版本：V7

## 3. 实际领域对象与表结构

- Flyway 迁移文件：`V7__ai_credential_and_execution_resource.sql`
- `ai_credential` 实际字段、约束与索引：
  - 字段：id, tenant_id, provider_id, code, display_name, description, credential_type, admin_status, secret_reference, encryption_key_id, encryption_algorithm, encrypted_secret(BYTEA), nonce(BYTEA), secret_fingerprint, masked_preview, secret_version, rotated_at, created_at, updated_at
  - 约束：uk_ai_credential_tenant_provider_type_fingerprint(tenant_id, provider_id, credential_type, secret_fingerprint)、uk_ai_credential_tenant_id(tenant_id, id)、fk_ai_credential_provider_tenant(tenant_id, provider_id → ai_provider)
  - 索引：idx_ai_credential_tenant_provider, idx_ai_credential_tenant_status, idx_ai_credential_tenant_created_at
- `ai_execution_resource` 实际字段、约束与索引：
  - 字段：id, tenant_id, provider_id, upstream_connection_id, credential_id, resource_type, code, display_name, description, admin_status, created_at, updated_at
  - 约束：uk_ai_resource_tenant_connection_credential(tenant_id, upstream_connection_id, credential_id)、fk_ai_resource_connection_tenant(tenant_id, upstream_connection_id → ai_upstream_connection)、fk_ai_resource_credential_tenant(tenant_id, credential_id → ai_credential)
  - 索引：idx_ai_resource_tenant_provider, idx_ai_resource_tenant_status, idx_ai_resource_tenant_created_at
- 使用的枚举：AiCredentialType(API_KEY)、AiResourceType(DIRECT_API)、AiResourceStatus(ENABLED/DISABLED/DRAINING)、AiCatalogStatus(ENABLED/DISABLED，凭据复用)
- 复合外键/Service 一致性校验：数据库复合外键 + Service 层显式校验 tenant_id 和 provider_id 一致性
- 凭据与资源状态枚举：凭据使用 AiCatalogStatus(ENABLED/DISABLED)，资源使用 AiResourceStatus(ENABLED/DISABLED/DRAINING)

## 4. 密码学与密钥配置

- 加密算法与参数：AES/GCM/NoPadding（JDK JCA/JCE），GCM Tag 128 位
- nonce 来源与长度：java.security.SecureRandom，每次加密生成新的 12 字节随机 Nonce
- AAD 实际字段：`tenantId|providerId|secretReference|credentialType|schemaVersion`（schemaVersion = "1"）
- 数据库加密主密钥配置：环境变量 `AI_CREDENTIAL_ACTIVE_KEY_ID` + `AI_CREDENTIAL_ACTIVE_KEY_BASE64`（Base64 编码的 32 字节 AES-256 密钥）
- 指纹 HMAC 密钥配置：环境变量 `AI_CREDENTIAL_FINGERPRINT_KEY_BASE64`（Base64 编码的 32 字节 HMAC-SHA256 密钥），与加密主密钥严格分离
- 非测试环境缺失密钥时的行为：`AiCredentialEncryptionProperties.validateAndInit()` 在 `@PostConstruct` 中严格校验，缺少任何密钥或密钥长度不正确时抛出 `IllegalStateException`，应用启动阶段明确失败
- 测试专用密钥注入方式：`BaseIntegrationTest.@DynamicPropertySource` 注入固定 32 字节测试密钥（加密密钥全 0x01，指纹密钥全 0x02），测试密钥不可写入生产配置
- 数据库中保存的密文相关字段：encrypted_secret(BYTEA, 含 GCM Tag)、nonce(BYTEA, 12 字节)、encryption_key_id、encryption_algorithm、secret_fingerprint(HMAC 十六进制)、masked_preview(掩码预览)

## 5. 实际接口

- Credential：
  - `POST /api/v1/ai/providers/{providerId}/credentials` — 创建凭据（客户端传明文 API Key，服务端加密后丢弃）
  - `GET /api/v1/ai/providers/{providerId}/credentials` — 分页查询凭据列表
  - `GET /api/v1/ai/credentials/{id}` — 查询凭据详情
  - `PUT /api/v1/ai/credentials/{id}` — 更新管理元数据（不变更 API Key）
  - `POST /api/v1/ai/credentials/{id}/rotate` — 轮换凭据（保留 ID，更新密文/指纹/掩码/版本）
  - `POST /api/v1/ai/credentials/{id}/enable` — 启用凭据
  - `POST /api/v1/ai/credentials/{id}/disable` — 停用凭据
- ExecutionResource：
  - `POST /api/v1/ai/resources` — 创建资源（静态绑定连接与凭据）
  - `GET /api/v1/ai/resources` — 分页查询资源列表
  - `GET /api/v1/ai/resources/{id}` — 查询资源详情
  - `PUT /api/v1/ai/resources/{id}` — 更新资源元数据（绑定不可变）
  - `POST /api/v1/ai/resources/{id}/enable` — 启用资源
  - `POST /api/v1/ai/resources/{id}/disable` — 停用资源
  - `POST /api/v1/ai/resources/{id}/drain` — 排空资源
- 是否存在读取明文接口（必须为否）：否
- 是否存在删除接口（必须为否）：否

## 6. 权限、租户、审计与脱敏

- 允许角色：TENANT_OWNER、TENANT_ADMIN（通过类级 `@PreAuthorize("hasAnyRole('TENANT_OWNER', 'TENANT_ADMIN')")`）
- 拒绝角色：TENANT_MEMBER、未认证用户、无租户上下文的 SUPER_ADMIN
- Service 显式租户校验：`AiCatalogTenantGuard.requireCurrentTenantId()` 在每个 Service 入口校验 tenantId 非空
- 跨租户/跨 Provider 资源绑定保护：
  - 数据库复合外键保证 tenant_id + provider_id/connection_id/credential_id 一致性
  - Service 层显式校验 connection.tenant_id = credential.tenant_id、connection.provider_id = credential.provider_id
  - 已禁用的 Connection 或 Credential 不允许创建资源
  - 资源绑定一经创建不可修改 connectionId/credentialId/providerId
- 审计接入：所有写操作（create/update/rotate/enable/disable/drain）均通过 `@Auditable` 注解接入审计日志
- 日志/响应/异常脱敏结论：
  - API 响应不含 apiKey、encryptedSecret、nonce、secretFingerprint、secretReference 字段
  - 日志不记录原始 API Key，仅记录掩码预览
  - 异常消息不含明文密钥，仅提示"凭据解密失败，密文可能被篡改"
  - 审计目标仅记录凭据编码/ID，不记录敏感数据

## 7. 测试与命令

```text
# 密码学单元测试（9 个测试全通过）
mvn test -pl converge-web-service -Dtest="AiCredentialCryptoUnitTest"

# 凭据与资源集成测试（21 个测试全通过）
mvn test -pl converge-web-service -Dtest="AiCredentialAndResourceIntegrationTest"

# 全量回归测试（125 个测试全通过）
mvn test -pl converge-web-service
```

- 新增测试：
  - `AiCredentialCryptoUnitTest`：9 个密码学单元测试（加密解密往返、不同 Nonce、篡改检测、AAD 绑定验证、HMAC 指纹一致性、跨租户指纹隔离、密钥校验）
  - `AiCredentialAndResourceIntegrationTest`：21 个集成测试（权限、CRUD、加密存储、指纹去重、跨租户隔离、轮换、资源一致性、绑定不可变、状态管理、无删除接口、无明文读取接口）
- 全量回归：125 个测试全部通过，BUILD SUCCESS
- `converge-gateway` 未修改校验：已确认 converge-gateway 目录无任何改动
- 未执行测试及原因：无

## 8. 阶段边界复核

- 是否修改网关：否
- 是否新增 `/v1/*`：否（所有新端点均在 `/api/v1/ai/` 控制面路径下）
- 是否发起真实上游请求：否
- 是否实现 OAuth/账号池/资源池/路由：否
- 是否实现下游 API Key/分组/价格/倍率/计费：否
- 结论：严格遵循阶段边界，未实现任何超范围功能

## 9. 偏差、风险与下一阶段输入

- 与阶段计划的偏差：
  - 凭据使用 AiCatalogStatus(ENABLED/DISABLED) 而非独立状态枚举，因凭据不涉及 DRAINING 语义
  - 创建了两个独立 Controller（AiCredentialController + AiExecutionResourceController）替代扩展现有 AiCatalogController，代码更清晰
  - V7 迁移补充添加了 `ai_upstream_connection(tenant_id, id)` 复合唯一约束（V6 遗漏）
- 未完成项：无
- 已知风险：
  - 生产部署前必须配置三个 AI 凭据加密环境变量，否则应用无法启动
  - 本阶段不实现密钥轮换，后续需要时可利用已保存的 encryption_key_id 实现
- 对下一阶段的输入：
  - 阶段 04 可基于 AiExecutionResource(DIRECT_API) 实现调度器选择上游
  - 阶段 04 可基于 AiCredential 解密能力实现运行时 API Key 注入
  - AiResourceStatus.DRAINING 为优雅下线预留了扩展点

## 10. New-API / Sub2API 对照结论

- New-API：Channel 聚合 Key/BaseURL/模型/分组/优先级/权重，本项目刻意拆分为 Connection(非敏感地址) + Credential(加密密钥) + ExecutionResource(静态绑定)，防止过早耦合
- Sub2API：Account 聚合凭据/并发/优先级/倍率/状态/429 冷却，本项目吸收"可调度资源"概念但只做静态 DIRECT_API 绑定，运行时调度留待后续
- 是否偏离：未偏离。本阶段是 Direct API 和 OAuth 两种上游模式的共同底座，路由、分组、倍率和计费均在拥有真实资源与模型绑定后再设计
