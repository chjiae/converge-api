# 阶段 04｜实时来源、现有实施与设计依据（2026-07-07）

## 1. 现有实施结果

### 阶段 01

阶段 01 已交付独立 Vert.x Gateway，具备内部健康接口、requestId、访问日志、错误响应、优雅关闭和独立打包。它明确不依赖 Spring、MyBatis、Flyway、PostgreSQL、Redis、JWT 或 `TenantContext`。  
阶段 04 允许仅为配置快照同步新增 Vert.x Redis Client；不得引入数据库访问或业务转发。

### 阶段 02

阶段 02 已交付租户级 Provider、Connection、PublicModel 目录，使用现有角色、审计、Flyway、`AiCatalogTenantGuard`，并通过 Service 显式校验防止空 tenant context 绕过隔离。  
阶段 04 的 Projector 必须使用显式 `tenant_id` SQL，不能把无 TenantContext 解释为允许全表扫描。

### 阶段 03

阶段 03 已交付 `AiCredential` 与 `AiExecutionResource(DIRECT_API)`：

- 数据库 API Key 使用 AES/GCM/NoPadding；
- 12 字节随机 nonce、128 bit tag；
- AAD 绑定 tenant / provider / secretReference / credentialType / schemaVersion；
- 数据库密钥、指纹密钥分离；
- 控制面不暴露明文；
- V7 已存在。

阶段 04 不让 gateway 使用阶段 03 数据库加密密钥，而是采用独立网关投递加密密钥和签名密钥。

## 2. New-API 对照

New-API README 当前说明其包含渠道重试、Redis 缓存，并指出多机部署需要共享 Redis 和 crypto secret，否则加密数据无法解密。  
本项目借鉴“网关运行时需要共享配置与缓存”的方向，但不把控制面数据库密钥直接复制给 Gateway：

```text
数据库凭据密钥：只在 Spring 控制面
网关投递密钥：控制面封装 + Gateway 内存解封装
```

这降低 gateway 被攻破后扩大到数据库历史凭据的风险，并保留未来独立密钥轮换的空间。

New-API 的 Channel 聚合 Base URL、Key、模型、分组、优先级、权重；本项目继续坚持拆分：
Provider / Connection / Credential / ExecutionResource / 未来 ResourcePool / RoutePolicy。
因此阶段 04 snapshot 不包含未实现的分组、权重、路由或价格。

参考：
- QuantumNous/new-api README
- QuantumNous/new-api-docs FAQ / channel management documentation

## 3. Sub2API 对照

Sub2API 当前 README 列出多账户、API Key 分发、精确计费、粘性会话、账户/用户并发和 RPM/TPM 限流等能力。其账户调度需依赖账户状态、会话和资源数据。

阶段 04 先建立可靠运行时配置视图：

```text
控制面事实数据
  → 安全、版本化 gateway snapshot
  → 后续资源池、账户池、粘性会话、限流、路由调度
```

没有可靠快照基础，未来多 gateway 实例会分别读取不同配置，账户组、sticky session、限流状态和路由规则将难以一致。

参考：
- Wei-Shaw/sub2api README
- account schema / account service

## 4. Redis 与 Vert.x 依据

Redis 官方文档指出 Pub/Sub 是 at-most-once：订阅者断连或处理失败时消息可能永久丢失。  
因此本阶段明确采用：

```text
Redis key 中的 manifest + immutable payload：事实快照
Redis Pub/Sub：低延迟通知
Gateway initial + periodic reconciliation：遗漏补偿
PostgreSQL outbox：控制面发布可靠性
```

Vert.x 官方 Redis Client 文档提供非阻塞 Redis client；网关必须使用该客户端而不是 JDBC 或阻塞 Redis SDK。

参考：
- Redis Pub/Sub official documentation
- Eclipse Vert.x Redis Client official documentation

## 5. 结论

阶段 04 不是简单“Redis 缓存”。它是本项目首次把控制面与数据面连接起来的可靠配置通道：

- 对 New-API：为渠道、模型、路由、重试、计费前的网关配置读取做基础；
- 对 Sub2API：为账户、资源组、粘性会话、限流与调度前的多实例一致性做基础；
- 对本项目：保持网关不直连 PostgreSQL，保持阶段 03 数据库凭据密钥不进入 gateway。
