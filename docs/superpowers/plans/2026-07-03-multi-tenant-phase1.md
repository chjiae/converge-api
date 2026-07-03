# 多租户体系阶段 1 - 后端基础与核心 API 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建多租户后端基础设施，实现租户管理、用户认证、申请审核、订阅管理、审计日志等核心 API。

**Architecture:** 共享数据库行级隔离（tenant_id），Spring Security + JWT 认证，MyBatis-Plus ORM + 多租户拦截器，RBAC 权限控制。

**Tech Stack:** Spring Boot 4.1.0 + JDK 25 + MyBatis-Plus + MySQL 8.0 + Flyway + JJWT + Redis

**设计文档:** `docs/design/multi-tenant-proposal.md`

---

## Task 1: 项目依赖与数据库 Schema

**Files:**
- Modify: `backend/pom.xml` (父 POM 添加版本号属性)
- Modify: `backend/converge-common/pom.xml` (添加 MyBatis-Plus 注解依赖)
- Modify: `backend/converge-web-service/pom.xml` (添加 MyBatis-Plus, MySQL, Flyway, Security, JJWT, Redis)
- Create: `backend/converge-web-service/src/main/resources/db/migration/V1__init_schema.sql`
- Modify: `backend/converge-web-service/src/main/resources/application.yml` (数据库配置)

**步骤:**
- [ ] 在父 POM 添加依赖版本号属性（mybatis-plus, mysql, flyway, jjwt 等）
- [ ] 在 converge-common 添加 MyBatis-Plus 注解依赖（@TableField 等）
- [ ] 在 converge-web-service 添加 MyBatis-Plus Spring Boot 3 Starter、MySQL Driver、Flyway、Spring Security Starter、JJWT (api/impl/jackson)、Spring Data Redis、Lettuce
- [ ] 创建 Flyway 初始化迁移脚本 V1__init_schema.sql，包含所有核心表：tenant, tenant_application, user, role, permission, user_role, role_permission, subscription, audit_log, notification
- [ ] 更新 application.yml 添加 datasource, flyway, mybatis-plus, redis 配置
- [ ] 确保 MySQL 数据库已创建（converge_api），执行 mvn compile 验证依赖解析
- [ ] 提交: `chore: 添加多租户基础设施依赖和数据库初始化脚本`

---

## Task 2: 枚举类型与 BaseEntity 扩展

**Files:**
- Modify: `backend/converge-common/src/main/java/com/github/chjiae/common/model/entity/BaseEntity.java` (添加 tenantId 字段)
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/TenantStatus.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/UserType.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/UserStatus.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/ApplicationType.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/ApplicationStatus.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/SubscriptionPlanType.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/SubscriptionStatus.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/PaymentMethod.java`
- Create: `backend/converge-common/src/main/java/com/github/chjiae/common/enums/NotificationType.java`

**步骤:**
- [ ] 扩展 BaseEntity 添加 tenantId 字段（Long，可为 null，超管数据无租户）
- [ ] 创建 TenantStatus 枚举: PENDING, TRIAL, ACTIVE, DISABLED, EXPIRED, DELETED
- [ ] 创建 UserType 枚举: SUPER_ADMIN, PLATFORM_OPERATOR, TENANT_USER
- [ ] 创建 UserStatus 枚举: ACTIVE, DISABLED
- [ ] 创建 ApplicationType 枚举: REGISTER, TRIAL
- [ ] 创建 ApplicationStatus 枚举: PENDING, APPROVED, REJECTED
- [ ] 创建 SubscriptionPlanType 枚举: TRIAL, MONTHLY, QUARTERLY, YEARLY, CUSTOM
- [ ] 创建 SubscriptionStatus 枚举: PENDING, PAID, ACTIVE, EXPIRED, CANCELLED
- [ ] 创建 PaymentMethod 枚举: ALIPAY, WECHAT, CARD_KEY, OFFLINE
- [ ] 创建 NotificationType 枚举: SYSTEM, AUDIT_RESULT, EXPIRY_WARNING, SUBSCRIPTION
- [ ] 所有枚举和类必须有中文注释，枚举值必须有中文描述字段
- [ ] 执行 mvn compile 验证编译通过
- [ ] 提交: `feat(公共模块): 添加多租户相关枚举类型`

---

## Task 3: 核心实体类

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/Tenant.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/TenantApplication.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/User.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/Role.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/Permission.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/UserRole.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/RolePermission.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/Subscription.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/AuditLog.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/entity/Notification.java`

**步骤:**
- [ ] 创建 Tenant 实体（extends BaseEntity），字段对照设计文档 2.2 节，使用 @TableName("tenant")，枚举字段用 @EnumValue 或 IEnum 处理
- [ ] 创建 TenantApplication 实体，字段对照设计文档 2.3 节
- [ ] 创建 User 实体，字段对照设计文档 2.5 节，注意 userType 和 tenantId 字段
- [ ] 创建 Role 实体，字段对照设计文档 2.6 节，含 isSystem 布尔字段
- [ ] 创建 Permission 实体，字段对照设计文档 2.7 节
- [ ] 创建 UserRole 实体（userId + roleId，无主键或联合主键）
- [ ] 创建 RolePermission 实体（roleId + permissionId，无主键或联合主键）
- [ ] 创建 Subscription 实体，字段对照设计文档 2.4 节，含 amount (BigDecimal)
- [ ] 创建 AuditLog 实体，字段对照设计文档 2.9 节
- [ ] 创建 Notification 实体，字段对照设计文档 2.10 节
- [ ] 所有实体类和字段必须有完整中文注释
- [ ] 执行 mvn compile 验证编译通过
- [ ] 提交: `feat(实体): 添加多租户核心实体类`

---

## Task 4: Mapper 层与 MyBatis-Plus 配置

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/mapper/` (10 个 Mapper 接口)
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/MyBatisPlusConfig.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/tenant/TenantContext.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/tenant/TenantLineHandler.java`

**步骤:**
- [ ] 创建 TenantContext（ThreadLocal），提供 setTenantId / getTenantId / clear 静态方法，加中文注释
- [ ] 创建 TenantLineHandler 实现 MyBatis-Plus 的 TenantLineHandler 接口，从 TenantContext 获取 tenantId，忽略不需要租户过滤的表（permission, audit_log 查询时需特殊处理）
- [ ] 创建 MyBatisPlusConfig 配置类，注册 MybatisPlusInterceptor，添加 TenantLineInnerInterceptor + PaginationInnerInterceptor
- [ ] 为每个实体创建 Mapper 接口（extends BaseMapper<T>），加 @Mapper 注解
- [ ] 在 ConvergeApplication 添加 @MapperScan("com.github.chjiae.service.mapper")
- [ ] 启动应用验证 MyBatis-Plus 初始化成功，Flyway 迁移执行成功
- [ ] 提交: `feat(数据层): 配置 MyBatis-Plus 多租户拦截器和 Mapper`

---

## Task 5: 租户上下文过滤器与全局异常处理

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/filter/TenantContextFilter.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/filter/FilterConfig.java`
- Modify: `backend/converge-common/src/main/java/com/github/chjiae/common/result/ResultCode.java` (添加多租户相关错误码)

**步骤:**
- [ ] 在 ResultCode 添加新错误码：TENANT_NOT_FOUND, TENANT_DISABLED, TENANT_EXPIRED, TENANT_PENDING, APPLICATION_NOT_FOUND, SUBSCRIPTION_NOT_FOUND, PERMISSION_DENIED
- [ ] 创建 TenantContextFilter (implements Filter)，在请求结束后调用 TenantContext.clear() 防止内存泄漏
- [ ] 创建 FilterConfig 注册 TenantContextFilter，匹配 /* 路径
- [ ] 启动验证无报错
- [ ] 提交: `feat(过滤器): 添加租户上下文清理过滤器和错误码扩展`

---

## Task 6: Spring Security + JWT 认证基础设施

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/JwtTokenProvider.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/JwtAuthenticationFilter.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/UserPrincipal.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/config/SecurityConfig.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/security/CustomUserDetailsService.java`
- Modify: `backend/converge-web-service/src/main/resources/application.yml` (JWT 配置)

**步骤:**
- [ ] 在 application.yml 添加 JWT 配置（secret-key, access-token-expiration, refresh-token-expiration）
- [ ] 创建 UserPrincipal 实现 UserDetails，封装 userId, tenantId, username, userType, roles
- [ ] 创建 JwtTokenProvider，实现 generateAccessToken / generateRefreshToken / validateToken / getUserIdFromToken / getTenantIdFromToken，使用 JJWT 库
- [ ] 创建 JwtAuthenticationFilter (extends OncePerRequestFilter)，从 Authorization header 解析 JWT，验证并设置 SecurityContext，同时将 tenantId 写入 TenantContext
- [ ] 创建 CustomUserDetailsService 实现 UserDetailsService，从数据库加载用户
- [ ] 创建 SecurityConfig 配置 Spring Security：禁用 CSRF、stateless session、配置公开端点（/api/v1/auth/**, /api/v1/applications POST/GET, /api/v1/health）、添加 JWT 过滤器
- [ ] 启动验证 Security 配置生效，/api/health 可公开访问
- [ ] 提交: `feat(安全): 实现 Spring Security + JWT 认证框架`

---

## Task 7: 认证 API（登录、注册、刷新 Token）

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/auth/LoginRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/auth/RegisterRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/auth/TokenResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/auth/UserInfoResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/AuthService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/AuthController.java`
- Modify: SecurityConfig（将 /api/v1/auth/** 加入公开端点）

**步骤:**
- [ ] 创建 LoginRequest DTO（username, password），RegisterRequest DTO（username, email, password, tenantCode），TokenResponse DTO（accessToken, refreshToken, userInfo），UserInfoResponse DTO（id, username, email, userType, tenantId, tenantName, roles）
- [ ] 创建 AuthService：login 方法（验证凭证 → 检查租户状态 ACTIVE/TRIAL → 生成 Token → 记录审计日志），register 方法（根据 tenantCode 查找租户 → 检查租户状态 → 检查用户名邮箱唯一 → 创建用户 MEMBER 角色 → 返回 Token）
- [ ] 创建 AuthController：POST /api/v1/auth/login, POST /api/v1/auth/register, POST /api/v1/auth/refresh, POST /api/v1/auth/logout
- [ ] 登录时需检查租户状态：ACTIVE/TRIAL 允许登录，PENDING/DISABLED/EXPIRED/DELETED 拒绝并返回对应错误信息
- [ ] 密码使用 BCryptPasswordEncoder 加密存储
- [ ] 启动验证登录/注册接口可用，返回正确 Token
- [ ] 提交: `feat(认证): 实现登录、注册、Token 刷新接口`

---

## Task 8: 租户管理 API（超管专用）

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/tenant/CreateTenantRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/tenant/UpdateTenantRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/tenant/TenantResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/TenantService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/TenantController.java`

**步骤:**
- [ ] 创建 DTO：CreateTenantRequest（code, name, description, adminUsername, adminEmail, adminPassword），UpdateTenantRequest（name, description），TenantResponse（全部字段）
- [ ] 创建 TenantService：createTenant（创建租户 + 管理员用户 + 分配 TENANT_OWNER 角色，事务），listTenants（分页），getTenant，updateTenant，deleteTenant（软删除），enableTenant，disableTenant
- [ ] 创建 TenantController，全部接口加 @PreAuthorize 限制 SUPER_ADMIN 角色
- [ ] 路由：POST/GET/PUT/DELETE /api/v1/tenants, POST /api/v1/tenants/{id}/enable, POST /api/v1/tenants/{id}/disable
- [ ] 创建租户时自动创建三个租户内置角色（TENANT_OWNER, TENANT_ADMIN, TENANT_MEMBER）
- [ ] 所有操作记录审计日志
- [ ] 启动验证 CRUD 功能正常
- [ ] 提交: `feat(租户): 实现租户管理 CRUD 接口`

---

## Task 9: 申请审核 API

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/application/CreateApplicationRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/application/ReviewApplicationRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/application/ApplicationResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/ApplicationService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/ApplicationController.java`

**步骤:**
- [ ] 创建 DTO：CreateApplicationRequest（companyName, contactName, contactEmail, contactPhone, description, applicationType, adminUsername, adminEmail, adminPassword），ReviewApplicationRequest（rejectReason），ApplicationResponse（全部字段）
- [ ] 创建 ApplicationService：submitApplication（公开接口），getByCode（公开查询），listApplications（分页），approveApplication（审核通过 → 创建租户 + 管理员 + 内置角色 + 通知，type=TRIAL 时设置 7 天试用期），rejectApplication（标记拒绝原因 + 通知）
- [ ] 创建 ApplicationController：POST /api/v1/applications（公开），GET /api/v1/applications/{code}（公开），GET /api/v1/applications（超管/运营），POST /api/v1/applications/{id}/approve（超管/运营），POST /api/v1/applications/{id}/reject（超管/运营）
- [ ] 审核操作需要 SUPER_ADMIN 或 PLATFORM_OPERATOR 角色
- [ ] 启动验证申请和审核流程
- [ ] 提交: `feat(申请): 实现租户注册申请和审核接口`

---

## Task 10: 订阅管理 API

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/subscription/CreateSubscriptionRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/subscription/SubscriptionResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/SubscriptionService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/SubscriptionController.java`

**步骤:**
- [ ] 创建 DTO：CreateSubscriptionRequest（tenantId, planType, amount, startDate, endDate, paymentMethod, remark），SubscriptionResponse（全部字段）
- [ ] 创建 SubscriptionService：createSubscription（超管/运营创建线下订阅），markAsPaid（标记已支付 → 自动延长租户 expiredAt），listSubscriptions（全平台），listMySubscriptions（租户视角），initiateRenewal（租户发起续费，预留支付回调）
- [ ] 创建 SubscriptionController：超管视角 /api/v1/subscriptions（GET/POST/PUT），租户视角 /api/v1/my-subscriptions（GET/POST）
- [ ] 支付成功后自动更新租户 expiredAt 和状态为 ACTIVE
- [ ] 启动验证订阅管理功能
- [ ] 提交: `feat(订阅): 实现订阅管理和续费接口`

---

## Task 11: 用户管理 API（租户内）

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/user/UserResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/user/CreateUserRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/user/UpdateUserRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/user/AssignRolesRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/UserService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/UserController.java`

**步骤:**
- [ ] 创建 DTO：UserResponse, CreateUserRequest, UpdateUserRequest, AssignRolesRequest
- [ ] 创建 UserService：getCurrentUser（当前用户信息），updateCurrentUser，changePassword，listUsers（租户内用户列表），createUser（租户管理员创建用户），deleteUser，updateUserStatus，assignRoles
- [ ] 创建 UserController：GET /api/v1/users/me, PUT /api/v1/users/me, PUT /api/v1/users/me/password, GET/POST /api/v1/users, DELETE /api/v1/users/{id}, PUT /api/v1/users/{id}/status, PUT /api/v1/users/{id}/roles
- [ ] 用户管理操作限制为同租户内（TenantContext 自动过滤）
- [ ] 启动验证用户管理功能
- [ ] 提交: `feat(用户): 实现租户内用户管理接口`

---

## Task 12: 角色权限管理 API

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/role/RoleResponse.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/role/CreateRoleRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/dto/role/AssignPermissionsRequest.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/RoleService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/RoleController.java`

**步骤:**
- [ ] 创建 DTO：RoleResponse, CreateRoleRequest, AssignPermissionsRequest
- [ ] 创建 RoleService：listRoles, createRole, updateRole, deleteRole（系统角色不可删）, assignPermissions
- [ ] 创建 RoleController：GET/POST /api/v1/roles, PUT/DELETE /api/v1/roles/{id}, PUT /api/v1/roles/{id}/permissions
- [ ] 内置角色（isSystem=true）不允许删除和修改
- [ ] 启动验证角色权限管理
- [ ] 提交: `feat(角色): 实现角色权限管理接口`

---

## Task 13: 审计日志与站内信

**Files:**
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/AuditLogService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/AuditLogController.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/service/NotificationService.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/controller/NotificationController.java`
- Create: `backend/converge-web-service/src/main/java/com/github/chjiae/service/aspect/AuditLogAspect.java`

**步骤:**
- [ ] 创建 AuditLogService：log 方法（异步写入审计日志），listAuditLogs（分页查询，超管查全部/租户查本租户）
- [ ] 创建 AuditLogAspect（@Aspect），通过自定义注解 @Auditable 自动记录方法调用的审计日志
- [ ] 创建 AuditLogController：GET /api/v1/audit-logs（超管），GET /api/v1/tenants/{id}/audit-logs（超管），GET /api/v1/my-audit-logs（租户管理员）
- [ ] 创建 NotificationService：send（发送通知），listNotifications（我的通知列表），markAsRead，markAllAsRead，getUnreadCount，sendPlatformAnnouncement（超管发公告）
- [ ] 创建 NotificationController：GET /api/v1/notifications, PUT /api/v1/notifications/{id}/read, PUT /api/v1/notifications/read-all, GET /api/v1/notifications/unread-count, POST /api/v1/notifications（超管）
- [ ] 启动验证审计日志和站内信功能
- [ ] 提交: `feat(日志): 实现审计日志和站内信功能`

---

## 验证清单

完成所有 Task 后执行以下验证：

1. `mvn clean compile` — 编译通过
2. 启动应用 — Flyway 迁移成功，MyBatis-Plus 初始化正常
3. POST /api/v1/auth/register — 注册用户加入租户
4. POST /api/v1/auth/login — 登录获取 Token
5. GET /api/v1/users/me — 携带 Token 获取当前用户信息
6. POST /api/v1/applications — 提交租户申请（公开）
7. POST /api/v1/applications/{id}/approve — 超管审核通过
8. POST /api/v1/tenants — 超管创建租户
9. GET /api/v1/tenants — 租户列表（多租户过滤验证）
10. POST /api/v1/subscriptions — 创建订阅
11. GET /api/v1/audit-logs — 审计日志查询
12. GET /api/v1/notifications — 站内信查询
13. 提交: `chore: 阶段 1 后端核心 API 完成`
