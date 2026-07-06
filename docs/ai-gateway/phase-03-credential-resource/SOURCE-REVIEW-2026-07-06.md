# 阶段 03｜实时来源与实现依据（2026-07-06）

## 1. 本地实施结果作为输入

### 阶段 01

`phase-01-gateway-runtime-result.md` 证明：

- `converge-gateway` 已独立为 Vert.x 5.1.3 JVM 服务；
- 该服务不依赖 Spring、MyBatis、Redis、PostgreSQL、JWT 或 `TenantContext`；
- 本阶段不得修改网关。

### 阶段 02

`phase-02-control-plane-catalog-result.md` 证明：

- 已新增 `ai_provider`、`ai_upstream_connection`、`ai_public_model`；
- 所有新 AI 目录均为租户级；
- 通过 `AiCatalogTenantGuard` 显式拒绝空租户；
- 管理角色固定为 `TENANT_OWNER`、`TENANT_ADMIN`；
- 已有 V6，新增迁移必须续接。

## 2. New-API 对照

New-API 当前 README 将其定位为统一 AI 模型聚合与分发网关，覆盖多协议接口、渠道加权随机、失败重试、用户级模型限流和用量计费。

其公开文档说明：

- Channel 聚合了 Key、Base URL、模型、分组、优先级与权重；
- 高优先级渠道优先；同优先级按权重分配；
- 用户组、渠道组和渠道模型同时影响可用性；
- 额度可由组倍率、模型倍率、输入/输出 token 倍率共同计算。

本项目不会照搬聚合 Channel；本阶段将 Connection、Credential、ExecutionResource 分开，避免未来分组、模型映射、权重、倍率和计费被提前耦合进凭据表。

来源：
- https://github.com/QuantumNous/new-api/blob/main/README.md
- https://github.com/QuantumNous/new-api-docs/blob/main/docs/en/support/faq.md
- https://github.com/QuantumNous/new-api-docs/blob/main/docs/api/fei-channel-management.md

## 3. Sub2API 对照

Sub2API 当前 README 声明其核心能力包括多账户（OAuth/API Key）、下游 API Key、Token 计费、粘性会话、用户/账户并发和请求/Token 限流。

其 `Account` schema 及 service 将 API Key/OAuth 凭据、并发、优先级、倍率、状态、过期、429 冷却、过载窗口、临时不可调度、会话窗口、账户分组和模型映射置于单个账户对象。

本项目吸收“最终需要可调度资源”的能力，但不会把所有字段提前塞入一个 Account/Resource 表：

- 本阶段只落地 API Key 密文和静态 `DIRECT_API` 资源；
- OAuth、授权账户、运行时状态、限流、粘性会话、账户组及模型映射后置；
- 将来由 `AiAuthorizedAccount`、运行时状态、资源池和路由策略分别承载。

来源：
- https://github.com/Wei-Shaw/sub2api
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/backend/ent/schema/account.go
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/backend/internal/service/account.go

## 4. 密码学依据

- JDK `GCMParameterSpec` 描述 GCM 所需 IV 与 tag 参数；
- JDK `Cipher` 文档要求同一密钥下的 GCM IV 必须唯一；
- OWASP 建议在静态数据保护中使用经验证的密码学方案，密钥不能与被加密秘密一起存放，并使用认证加密避免篡改。

来源：
- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/spec/GCMParameterSpec.html
- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html
- https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html
- https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html

## 5. 本阶段判断

阶段 03 先建立秘密安全边界和可执行资源静态绑定，既能承接 New-API 的“渠道 API Key”路径，也能为 Sub2API 的“账户最终可被调度”路径准备共同资源概念。

不在本阶段实现分组、倍率、路由、资源池、API Key 下发、OAuth 或网关转发，是有意遵守领域依赖顺序。
