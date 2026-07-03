# 多租户体系阶段 2 - 支付集成 + 定时任务 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 集成支付宝/微信支付网关，实现订阅自动续费；搭建定时任务体系，自动处理租户到期、状态流转和通知推送；增强认证安全性（Token 黑名单 + HttpOnly Cookie）。

**Architecture:** 抽象支付网关接口 + 具体实现（支付宝/微信），Spring Scheduler 定时扫描，Redis 缓存 + Token 黑名单。

**Tech Stack:** Spring Boot 4.1.0 + JDK 25 + Alipay SDK + WeChatPay SDK + Spring Scheduler + Redis

**设计文档:** `docs/design/multi-tenant-proposal.md`
**阶段 1 计划:** `docs/superpowers/plans/2026-07-03-multi-tenant-phase1.md`

---

## Task 1: 支付网关基础设施与配置

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/PaymentProperties.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/payment/PaymentGateway.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/payment/PaymentResult.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/payment/PaymentOrder.java`
- Modify: `backend/converge-web-service/src/main/resources/application.yml`（支付配置）
- Modify: `backend/converge-web-service/pom.xml`（添加支付 SDK 依赖）

**步骤:**
- [ ] 在 pom.xml 添加依赖：`alipay-sdk-java`（支付宝官方 SDK）、`wechatpay-java`（微信官方 SDK）、`spring-boot-starter-validation`（已有则跳过）
- [ ] 创建 `PaymentProperties` 配置类（`@ConfigurationProperties(prefix = "payment")`），包含 alipay（appId、privateKey、alipayPublicKey、notifyUrl、gateway）和 wechat（appId、mchId、mchSerialNo、apiV3Key、privateKeyPath、notifyUrl）子配置
- [ ] 在 application.yml 添加 `payment.alipay.*` 和 `payment.wechat.*` 配置项，使用占位符（`${ALIPAY_APP_ID:}`），禁止硬编码密钥
- [ ] 创建 `PaymentOrder` 数据类：outTradeNo（商户订单号）、subject（标题）、amount（金额）、returnUrl（前端回跳地址）
- [ ] 创建 `PaymentResult` 数据类：success（布尔）、paymentUrl（支付跳转链接或表单 HTML）、outTradeNo、message
- [ ] 创建 `PaymentGateway` 接口，定义三个方法：`createOrder(PaymentOrder)` 返回 `PaymentResult`、`handleCallback(Map<String, String> params)` 返回处理结果、`queryPaymentStatus(String outTradeNo)` 返回支付状态
- [ ] 提交: `feat(支付): 添加支付网关基础设施和配置`

---

## Task 2: 支付宝支付实现

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/payment/AlipayGateway.java`

**步骤:**
- [ ] 创建 `AlipayGateway` 实现 `PaymentGateway`，注入 `PaymentProperties`
- [ ] 使用 `@ConditionalOnProperty(prefix = "payment.alipay", name = "app-id")` 条件化注册（未配置时不加载）
- [ ] `createOrder` 方法：使用 `AlipayClient` 调用 `alipay.trade.page.pay` 接口，生成电脑网站支付的表单 HTML，设置 `notify_url` 为配置的回调地址，`out_trade_no` 使用订阅 ID 前缀
- [ ] `handleCallback` 方法：验证支付宝签名（`alipay_public_key`），提取 `out_trade_no` 和 `trade_status`，`TRADE_SUCCESS` 或 `TRADE_FINISHED` 表示支付成功
- [ ] `queryPaymentStatus` 方法：调用 `alipay.trade.query` 接口主动查询订单支付状态
- [ ] 添加完整的中文日志：创建订单、回调验签、支付结果
- [ ] 提交: `feat(支付): 实现支付宝电脑网站支付`

---

## Task 3: 微信支付实现

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/payment/WechatPayGateway.java`

**步骤:**
- [ ] 创建 `WechatPayGateway` 实现 `PaymentGateway`，注入 `PaymentProperties`
- [ ] 使用 `@ConditionalOnProperty(prefix = "payment.wechat", name = "app-id")` 条件化注册
- [ ] `createOrder` 方法：使用微信 `NativePayService` 创建 Native 支付订单（生成二维码链接），设置 `notify_url`，`out_trade_no` 规则同支付宝
- [ ] `handleCallback` 方法：验证微信签名（APIv3），解密通知报文，提取 `out_trade_no` 和 `trade_state`，`SUCCESS` 表示支付成功
- [ ] `queryPaymentStatus` 方法：调用微信支付查询接口主动查询
- [ ] 添加完整的中文日志
- [ ] 提交: `feat(支付): 实现微信 Native 扫码支付`

---

## Task 4: 支付回调与订阅自动激活

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/PaymentCallbackController.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/PaymentService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/payment/PaymentInitiateRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/payment/PaymentInitiateResponse.java`
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/SubscriptionController.java`（添加发起支付端点）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/SubscriptionService.java`（添加发起支付逻辑）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/SecurityConfig.java`（公开回调端点）

**步骤:**
- [ ] 创建 `PaymentInitiateRequest` DTO：subscriptionId、paymentMethod（ALIPAY / WECHAT）、returnUrl（支付完成后前端回跳地址）
- [ ] 创建 `PaymentInitiateResponse` DTO：paymentUrl（跳转链接或 HTML 表单）、outTradeNo
- [ ] 创建 `PaymentService`：注入 `Map<String, PaymentGateway>`（Spring 自动收集所有实现），根据 paymentMethod 路由到对应网关
- [ ] `PaymentService.initiatePayment`：校验订阅状态为 PENDING → 生成商户订单号 → 调用网关创建订单 → 将订单号写入 subscription.paymentRef → 返回支付链接
- [ ] `PaymentService.handlePaymentCallback`：根据支付渠道路由到对应网关验签 → 提取 outTradeNo → 查找订阅 → 调用 `SubscriptionService.markAsPaid` 自动激活租户 → 发送站内信通知（复用 NotificationService）→ 记录审计日志
- [ ] 创建 `PaymentCallbackController`：`POST /api/v1/payment/alipay/notify`（接收支付宝异步通知，返回 `success` 字符串）、`POST /api/v1/payment/wechat/notify`（接收微信异步通知，返回 JSON `{"code":"SUCCESS","message":""}`）
- [ ] 在 `SubscriptionController` 添加 `POST /api/v1/my-subscriptions/pay` 端点（租户管理员发起在线支付）
- [ ] 在 SecurityConfig 中将 `/api/v1/payment/*/notify` 加入公开端点列表（支付平台回调不携带 JWT）
- [ ] 添加幂等性保护：回调处理前检查订阅状态，已 ACTIVE 则直接返回成功（防止重复回调）
- [ ] 提交: `feat(支付): 实现支付回调处理和订阅自动激活`

---

## Task 5: 定时任务基础设施与租户到期扫描

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/scheduler/TenantExpiryScheduler.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/SchedulerConfig.java`
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/ConvergeApplication.java`（添加 `@EnableScheduling`）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/TenantService.java`（抽取到期判断逻辑）

**步骤:**
- [ ] 创建 `SchedulerConfig` 配置类，添加 `@EnableScheduling`（或加在主启动类上）
- [ ] 创建 `TenantExpiryScheduler`，注入 `TenantMapper`、`NotificationService`
- [ ] 实现 `scanExpiringTenants` 方法（`@Scheduled(cron = "0 0 2 * * ?")` 每天凌晨 2 点执行）：
  - 查询所有 ACTIVE/TRIAL 状态且 `expired_at` 不为 null 的租户
  - 到期前 7 天：发送 `EXPIRY_WARNING` 通知给租户管理员（"您的租户将于 7 天后到期，请及时续费"）
  - 到期前 3 天：再次发送通知
  - 到期前 1 天：再次发送通知
  - 已到期（`expired_at < now`）：将租户状态改为 EXPIRED，发送到期通知，记录日志
- [ ] 实现 `scanExpiredSubscriptions` 方法（`@Scheduled(cron = "0 0 3 * * ?")` 每天凌晨 3 点执行）：
  - 查询所有 ACTIVE 状态且 `end_date < today` 的订阅
  - 将订阅状态改为 EXPIRED
- [ ] 所有定时任务方法内必须设置 `TenantContext.setIgnoreTenant(true)` 并在 finally 中清除
- [ ] 添加分布式锁保护（使用 Redis `SET NX EX`）防止多实例重复执行（如未引入 Redisson，可用简单 Redis SETNX 实现）
- [ ] 添加完整的中文日志：任务开始、处理数量、任务完成
- [ ] 提交: `feat(定时任务): 实现租户到期扫描和到期提醒通知`

---

## Task 6: Token 黑名单与登出增强

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/TokenBlacklistService.java`
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/JwtAuthenticationFilter.java`（验证黑名单）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/AuthController.java`（logout 添加黑名单）

**步骤:**
- [ ] 创建 `TokenBlacklistService`，注入 `StringRedisTemplate`，提供 `blacklist(String token, long ttlMillis)` 和 `isBlacklisted(String token)` 方法
- [ ] 黑名单 key 格式：`token:blacklist:{jti}`（使用 JWT 的 jti claim），TTL 设为 token 剩余有效期（过期后自动清除）
- [ ] 修改 `JwtTokenProvider`：确保生成的 JWT 包含唯一 `jti` claim（UUID）
- [ ] 修改 `JwtAuthenticationFilter`：验证 token 有效后，额外调用 `isBlacklisted` 检查，若在黑名单中则拒绝认证
- [ ] 修改 `AuthController.logout`：调用 `TokenBlacklistService.blacklist` 将 access token 和 refresh token 加入黑名单
- [ ] 添加日志：token 加入黑名单、黑名单命中拒绝
- [ ] 提交: `feat(安全): 实现 Token 黑名单和服务端登出`

---

## Task 7: HttpOnly Cookie 认证改造（为阶段 3 前端安全铺路）

**Files:**
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/AuthController.java`（设置 HttpOnly Cookie）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/JwtAuthenticationFilter.java`（从 Cookie 读取 Token）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/SecurityConfig.java`（CSRF 配置调整）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/CorsConfig.java`（allowCredentials）
- Modify: `backend/converge-web-service/src/main/resources/application.yml`（Cookie 配置）

**步骤:**
- [ ] 在 application.yml 添加 cookie 配置：`auth.cookie.secure`（生产 true，开发 false）、`auth.cookie.same-site`（Strict）、`auth.cookie.domain`（可选）
- [ ] 修改 `AuthController` 的 login、register、refresh 端点：
  - JSON 响应体中 **不再返回** accessToken 和 refreshToken
  - 改为通过 `ResponseCookie` 设置两个 HttpOnly Cookie：`access_token`（MaxAge=2h, Path=/, HttpOnly, Secure, SameSite=Strict）和 `refresh_token`（MaxAge=7d, Path=/api/v1/auth/refresh, HttpOnly, Secure, SameSite=Strict）
  - refresh_token 的 Path 限制为刷新端点，减少暴露面
  - userInfo 仍然在 JSON 响应体中返回
- [ ] 修改 `AuthController.logout`：通过 `ResponseCookie` 清除两个 Cookie（MaxAge=0）
- [ ] 修改 `JwtAuthenticationFilter`：优先从 `Cookie` 请求头读取 `access_token`，向后兼容 `Authorization: Bearer xxx`（阶段 1 集成测试仍用 Bearer）
- [ ] 修改 SecurityConfig：由于使用 SameSite=Strict Cookie 已天然防御 CSRF，可保持 CSRF 禁用（SameSite=Strict 阻止跨站请求携带 Cookie）；如需额外防护可启用 Spring CSRF token
- [ ] 修改 CorsConfig：添加 `allowCredentials(true)`，`allowedOrigins` 改为前端实际域名（不用 `*`，因为 allowCredentials 不兼容 `*`）
- [ ] 确保现有集成测试仍可通过（Bearer Token 向后兼容）
- [ ] 提交: `feat(安全): 认证改用 HttpOnly Cookie，防御 XSS 和 CSRF`

---

## Task 8: Redis 租户配置缓存

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/cache/TenantCacheService.java`
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/JwtAuthenticationFilter.java`（使用缓存）
- Modify: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/AuthService.java`（登录时使用缓存）

**步骤:**
- [ ] 创建 `TenantCacheService`，注入 `StringRedisTemplate`，提供：
  - `getTenantStatus(Long tenantId)` 返回缓存的租户状态
  - `refreshTenantCache(Long tenantId)` 从数据库加载并写入缓存
  - `evictTenantCache(Long tenantId)` 清除缓存
- [ ] 缓存 key 格式：`tenant:status:{tenantId}`，TTL 30 分钟，使用 JSON 存储状态信息
- [ ] 在 `JwtAuthenticationFilter` 中使用缓存查询租户状态（避免每次请求都查数据库）
- [ ] 在 `TenantService` 的 updateTenant、enableTenant、disableTenant、deleteTenant 方法中调用 `evictTenantCache`
- [ ] 添加日志：缓存命中、缓存未命中、缓存刷新
- [ ] 提交: `feat(缓存): 添加 Redis 租户状态缓存`

---

## Task 9: 卡密系统（Card Key）

**Files:**
- Create: Flyway migration `V4__card_key.sql`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/CardKey.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/mapper/CardKeyMapper.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/CardKeyService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/CardKeyController.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/cardkey/`（GenerateCardKeyRequest, CardKeyResponse, RedeemCardKeyRequest）

**步骤:**
- [ ] 创建 Flyway 迁移脚本 `V4__card_key.sql`：`card_key` 表（id, code, plan_type, duration_days, amount, status[UNUSED/REDEEMED/EXPIRED], generated_by, redeemed_by, redeemed_at, tenant_id, expired_at, created_at）
- [ ] 创建 `CardKey` 实体和 `CardKeyMapper`
- [ ] 创建 DTO：`GenerateCardKeyRequest`（planType, durationDays, amount, count）、`CardKeyResponse`（全部字段）、`RedeemCardKeyRequest`（code）
- [ ] 创建 `CardKeyService`：
  - `generateCardKeys`：超管批量生成卡密（code 为随机 16 位大写字母+数字，确保唯一），设置卡密有效期（如 1 年）
  - `redeemCardKey`：租户管理员输入卡密兑换 → 验证卡密状态 → 创建订阅记录 → 调用 markAsPaid 逻辑激活租户 → 标记卡密已兑换
  - `listCardKeys`：超管查看卡密列表（分页、按状态筛选）
- [ ] 创建 `CardKeyController`：
  - `POST /api/v1/card-keys/generate`（超管生成卡密）
  - `GET /api/v1/card-keys`（超管查看卡密列表）
  - `POST /api/v1/my-subscriptions/redeem`（租户管理员兑换卡密）
- [ ] 记录审计日志：卡密生成、卡密兑换
- [ ] 提交: `feat(卡密): 实现卡密生成和兑换功能`

---

## Task 10: 阶段 2 集成测试

**Files:**
- Create: `backend/converge-web-service/src/test/java/com/github/chjiae/service/SchedulerIntegrationTest.java`
- Create: `backend/converge-web-service/src/test/java/com/github/chjiae/service/PaymentIntegrationTest.java`
- Create: `backend/converge-web-service/src/test/java/com/github/chjiae/service/TokenBlacklistIntegrationTest.java`
- Create: `backend/converge-web-service/src/test/java/com/github/chjiae/service/CardKeyIntegrationTest.java`

**步骤:**
- [ ] `SchedulerIntegrationTest`：验证到期扫描逻辑（手动调用 scheduler 方法，检查租户状态变更和通知生成）
- [ ] `PaymentIntegrationTest`：验证 PaymentService 路由逻辑（mock PaymentGateway）、回调幂等性、Cookie 设置
- [ ] `TokenBlacklistIntegrationTest`：验证登出后 token 失效、黑名单 TTL 正确
- [ ] `CardKeyIntegrationTest`：验证卡密生成唯一性、兑换流程、重复兑换拒绝
- [ ] 确保所有现有测试仍然通过（`mvn test`）
- [ ] 提交: `test(阶段2): 添加支付、定时任务、Token 黑名单、卡密集成测试`

---

## 验证清单

完成所有 Task 后执行以下验证：

1. `mvn clean compile` — 编译通过
2. `mvn test` — 全部测试通过（阶段 1 + 阶段 2）
3. 启动应用 — Flyway V4 迁移成功，定时任务调度器初始化正常
4. POST /api/v1/auth/login — 验证返回 HttpOnly Cookie（Set-Cookie 头）
5. GET /api/v1/users/me — 验证 Cookie 认证正常工作
6. POST /api/v1/auth/logout — 验证 Cookie 被清除，再次请求返回 401
7. POST /api/v1/my-subscriptions/pay — 验证支付链接生成（需配置支付宝沙箱）
8. 手动触发 TenantExpiryScheduler — 验证到期通知发送
9. POST /api/v1/card-keys/generate — 验证卡密生成
10. POST /api/v1/my-subscriptions/redeem — 验证卡密兑换激活租户
11. 提交: `chore: 阶段 2 支付集成与定时任务完成`
