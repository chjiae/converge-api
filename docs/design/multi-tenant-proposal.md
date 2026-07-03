# Converge API 多租户体系设计方案

## 1. 业务模型

### 1.1 平台定位

Converge API 是一个统一部署的多租户平台：

- **平台方**：统一部署并维护数据库、缓存、服务等基础设施资源
- **租户**：租用平台资源，相当于一个独立的平台运营方
- **租户自治**：租户内部资源（用户、角色、权限等）由租户自行管理

### 1.2 职责边界

| 角色 | 职责范围 |
|------|----------|
| 超级管理员 | 管理租户生命周期（创建、启用、停用、删除），管理超管账号，不介入租户内部事务 |
| 平台运营 | 审核租户申请、查看租户信息、管理订阅订单（不能删除租户、不能管理超管账号） |
| 租户管理员 | 管理租户内用户、角色、权限、配置、续费等 |
| 租户用户 | 使用租户分配的资源，可自行注册加入租户 |

### 1.3 设计目标

- 所有业务资源（用户、API 配置、日志等）均归属于租户，租户间完全隔离
- 超级管理员仅管理租户本身，不干预租户内部运营
- 租户对其内部资源拥有完全自治权
- 支持多个超级管理员协同管理平台
- 可扩展的权限体系，支持租户自定义角色

---

## 2. 核心模型设计

### 2.1 实体关系

```
┌──────────────────────┐
│  TenantApplication   │ (租户注册申请，自注册时产生)
└──────────┬───────────┘
           │ 审核通过后创建
           ▼
┌──────────────────────┐         ┌─────────────────┐
│       Tenant         │────────>│      User       │
│      (租户)           │    1:N  │     (用户)       │
└──────────┬───────────┘         └─────────────────┘
           │                           │
           │ 1:N                       │ N:N
           ▼                           ▼
┌──────────────────────┐     ┌─────────────────┐
│    Subscription      │     │      Role       │
│     (订阅/订单)       │     │     (角色)       │
└──────────────────────┘     └────────┬────────┘
                                      │ N:N
                                      ▼
                              ┌─────────────────┐
                              │   Permission    │
                              │     (权限)       │
                              └─────────────────┘

┌──────────────────────┐     ┌─────────────────┐
│     AuditLog         │     │   Notification  │
│   (审计日志)          │     │   (站内信)       │
└──────────────────────┘     └─────────────────┘
```

### 2.2 Tenant 租户

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| code | String | 租户编码（唯一，用于 URL/标识） |
| name | String | 租户名称 |
| description | String | 租户描述 |
| status | Enum | 状态：PENDING / TRIAL / ACTIVE / DISABLED / EXPIRED / DELETED |
| trialUsed | Boolean | 是否已使用过试用（每个租户只能试用一次） |
| expiredAt | LocalDateTime | 到期时间（null 表示永不过期） |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| createdBy | Long | 创建人（超管 ID 或自注册申请人 ID） |

**状态流转：**
```
超管创建：直接 ACTIVE

自注册（充值）：PENDING ──> 审核通过 ──> 充值 ──> ACTIVE
自注册（试用）：PENDING ──> 审核通过 ──> 申请试用 ──> TRIAL (7天)
                                              │
                                              ▼
                                    TRIAL ──> 到期 ──> EXPIRED ──> 充值 ──> ACTIVE

运行中：  ACTIVE ──> 到期 ──> EXPIRED ──> 续费 ──> ACTIVE
          ACTIVE ──> 超管停用 ──> DISABLED ──> 超管启用 ──> ACTIVE
          EXPIRED ──> 超管停用 ──> DISABLED

删除：    任意状态 ──> DELETED (软删除，数据永久保留直到超管手动硬删除)
```

### 2.3 TenantApplication 租户注册申请

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| companyName | String | 公司/组织名称 |
| contactName | String | 联系人姓名 |
| contactEmail | String | 联系人邮箱 |
| contactPhone | String | 联系人电话 |
| description | String | 用途描述 |
| applicationType | Enum | 申请类型：REGISTER（注册充值）/ TRIAL（申请试用） |
| status | Enum | 状态：PENDING / APPROVED / REJECTED |
| rejectReason | String | 拒绝原因 |
| reviewedBy | Long | 审核人（超管 ID） |
| reviewedAt | LocalDateTime | 审核时间 |
| createdAt | LocalDateTime | 申请时间 |
| updatedAt | LocalDateTime | 更新时间 |

**流程：**
- **充值路径**：用户提交申请（type=REGISTER）→ 审核通过 → 创建租户（PENDING）→ 用户充值 → ACTIVE
- **试用路径**：用户提交申请（type=TRIAL）→ 审核通过 → 创建租户（TRIAL，7天）→ 到期未充值 → EXPIRED

### 2.4 Subscription 订阅/订单

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| tenantId | Long | 租户 ID |
| planType | Enum | 套餐类型：TRIAL / MONTHLY / QUARTERLY / YEARLY / CUSTOM |
| amount | BigDecimal | 金额（试用为 0） |
| startDate | LocalDate | 生效日期 |
| endDate | LocalDate | 到期日期 |
| status | Enum | 状态：PENDING / PAID / ACTIVE / EXPIRED / CANCELLED |
| paymentMethod | Enum | 支付方式：ALIPAY / WECHAT / CARD_KEY（后期） / OFFLINE（线下联系管理员） |
| paymentRef | String | 支付凭证号 |
| remark | String | 备注 |
| createdBy | Long | 创建人（租户管理员或超管） |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

**续费流程：**
- **试用**：租户申请试用（每个租户仅一次）→ 审核通过 → 创建 TRIAL 订阅（7天）→ 到期后未续费则 EXPIRED
- **线上续费**：租户管理员发起 → 选择支付宝/微信支付 → 支付成功 → 自动延长 `expiredAt`
- **线下续费**：联系管理员 → 管理员创建订单（paymentMethod=OFFLINE）→ 管理员标记已支付 → 自动延长 `expiredAt`
- **卡密续费**（后期）：租户输入卡密 → 系统验证 → 自动延长 `expiredAt`

### 2.5 User 用户

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| tenantId | Long | 所属租户 ID（超管为 null） |
| username | String | 用户名（租户内唯一，超管全局唯一） |
| email | String | 邮箱（租户内唯一，超管全局唯一） |
| passwordHash | String | 密码哈希 |
| phone | String | 手机号 |
| status | Enum | 状态：ACTIVE / DISABLED |
| userType | Enum | 用户类型：SUPER_ADMIN / PLATFORM_OPERATOR / TENANT_USER |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

**用户类型说明：**
- `SUPER_ADMIN`：平台超级管理员，不隶属于任何租户，拥有全部平台管理权限。支持多个超管。
- `PLATFORM_OPERATOR`：平台运营，不隶属于任何租户，可审核申请、查看租户、管理订阅订单。
- `TENANT_USER`：租户用户，隶属于某个租户

### 2.6 Role 角色

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| tenantId | Long | 所属租户 ID（系统角色为 null） |
| code | String | 角色编码（租户内唯一） |
| name | String | 角色名称 |
| description | String | 角色描述 |
| isSystem | Boolean | 是否系统内置角色（不可删除） |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

**系统内置角色（tenantId=null）：**
- `SUPER_ADMIN`：超级管理员，拥有平台级全部管理权限（创建/启停/删除租户、管理超管账号等）
- `PLATFORM_OPERATOR`：平台运营，可审核租户申请、查看租户信息、管理订阅订单（不能删除租户、不能管理超管账号、不能查看审计日志）

**租户内置角色（创建租户时自动生成）：**
- `TENANT_OWNER`：租户所有者，拥有租户内全部权限
- `TENANT_ADMIN`：租户管理员，可管理租户内用户和配置
- `TENANT_MEMBER`：租户成员，基础使用权限

### 2.7 Permission 权限

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| code | String | 权限编码（全局唯一） |
| name | String | 权限名称 |
| resource | String | 资源类型（tenant, user, api, subscription 等） |
| action | String | 操作类型（create, read, update, delete, manage） |

### 2.8 UserRole / RolePermission（关联表）

- `UserRole`：userId + roleId
- `RolePermission`：roleId + permissionId

### 2.9 AuditLog 审计日志

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| tenantId | Long | 租户 ID（平台级操作为 null） |
| userId | Long | 操作人 ID |
| username | String | 操作人用户名（冗余，防止用户删除后丢失） |
| module | String | 模块名称（auth, user, role, tenant, subscription 等） |
| action | String | 操作类型（create, update, delete, login, logout 等） |
| target | String | 操作对象（如 "user:123", "role:456"） |
| detail | JSON | 操作详情（变更前后对比等） |
| ipAddress | String | 操作 IP |
| userAgent | String | 客户端标识 |
| createdAt | LocalDateTime | 操作时间 |

**审计范围：**
- 平台级：租户创建/审核/启停/删除、超管操作
- 租户级：用户管理、角色权限变更、配置变更、登录登出、续费操作

### 2.10 Notification 站内信

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| tenantId | Long | 租户 ID（平台级通知为 null） |
| userId | Long | 接收人 ID（null 表示租户下所有用户） |
| title | String | 通知标题 |
| content | String | 通知内容 |
| type | Enum | 通知类型：SYSTEM / AUDIT_RESULT / EXPIRY_WARNING / SUBSCRIPTION |
| isRead | Boolean | 是否已读 |
| readAt | LocalDateTime | 阅读时间 |
| createdAt | LocalDateTime | 创建时间 |

**通知场景：**
- 申请审核结果通知（通过/拒绝）
- 到期提醒（7天、3天、1天）
- 订阅支付成功通知
- 平台公告（超管发送给所有租户或指定租户）

---

## 3. 数据隔离策略

### 3.1 共享数据库，共享 Schema

采用共享数据库、共享 Schema 的方式，通过 `tenant_id` 字段进行数据隔离。

**实现方式：**
- 所有业务表增加 `tenant_id` 字段（超管操作的数据 tenantId 为 null）
- 使用 MyBatis-Plus 的 `TenantLineInnerInterceptor` 或自定义拦截器
- 自动在 SQL 中注入 `tenant_id` 条件

### 3.2 租户上下文

```java
// 租户上下文，存储当前请求的租户信息
public class TenantContext {
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
```

### 3.3 自动过滤机制

通过拦截器自动注入租户过滤：
1. 请求进入时，从 JWT 解析租户 ID，存入 `TenantContext`
2. MyBatis 拦截器自动为 SQL 添加 `WHERE tenant_id = ?`
3. 请求结束时，清理 `TenantContext`

**例外场景：**
- 超管操作：不注入租户过滤（或注入指定租户 ID 进行跨租户管理）
- 审计日志查询：按租户过滤，超管可查看所有

---

## 4. 租户生命周期

### 4.1 租户创建

**路径 A：超管创建**
1. 超管填写租户信息 + 第一个管理员账号信息
2. 系统创建租户（状态 ACTIVE）+ 创建管理员用户 + 分配 TENANT_OWNER 角色
3. 返回管理员登录凭证

**路径 B：自注册（充值激活）**
1. 用户填写注册申请（公司名、联系人、邮箱等，type=REGISTER）
2. 系统创建 TenantApplication（状态 PENDING）
3. 超管/平台运营审核申请：
   - 通过：系统创建租户（状态 PENDING，需充值激活）+ 创建管理员用户 + 发送通知
   - 拒绝：标记拒绝原因 + 发送通知
4. 用户收到通过通知后，选择套餐并充值（支付宝/微信）
5. 充值成功 → 租户状态变为 ACTIVE，设置 expiredAt

**路径 C：自注册（试用激活）**
1. 用户填写试用申请（type=TRIAL）
2. 系统创建 TenantApplication（状态 PENDING）
3. 超管/平台运营审核申请：
   - 通过：系统创建租户（状态 TRIAL，expiredAt=当前+7天）+ 创建管理员用户 + 创建 TRIAL 订阅 + 发送通知
   - 拒绝：标记拒绝原因 + 发送通知
4. 试用期内无功能限制，正常使用
5. 试用到期未充值 → 状态变为 EXPIRED（每个租户只能试用一次，trialUsed=true）

### 4.2 到期与续费

**到期提醒：**
- 到期前 7 天、3 天、1 天分别发送通知
- 到期当天状态变为 EXPIRED，租户数据保留但功能受限

**续费方式：**
- **线上续费**：租户管理员在控制台选择套餐 → 在线支付 → 自动续期
- **线下续费**：联系管理员 → 管理员在后台创建订单并标记已支付 → 自动续期

### 4.3 停用与删除

- **停用**：超管可手动停用租户（状态 DISABLED），租户所有功能暂停，数据保留
- **软删除**：超管可删除租户（状态 DELETED），数据保留，可恢复
- **硬删除**：超管手动执行硬删除，彻底清除租户及其所有数据（不可恢复）
- **数据保留**：过期和软删除的租户数据永久保留，不做自动清理

### 4.4 租户内用户注册

租户内的新用户可自行注册加入租户：
1. 用户在注册页面选择目标租户（或输入租户编码）
2. 填写个人信息并注册
3. 注册成功后自动加入该租户，分配 TENANT_MEMBER 角色
4. 租户管理员可将其提升为 TENANT_ADMIN 或 TENANT_OWNER

---

## 5. API 设计

### 5.1 认证接口

```
POST   /api/v1/auth/login                # 登录
POST   /api/v1/auth/logout               # 登出
POST   /api/v1/auth/refresh              # 刷新 Token
POST   /api/v1/auth/register             # 用户注册（加入指定租户，分配 MEMBER 角色）
```

### 5.2 租户注册申请（公开）

```
POST   /api/v1/applications              # 提交申请（type=REGISTER 充值激活 / TRIAL 试用）
GET    /api/v1/applications/{code}       # 查询申请状态
```

### 5.3 租户管理（超管专用）

```
GET    /api/v1/tenants                   # 租户列表（分页）
POST   /api/v1/tenants                   # 创建租户（含首个管理员）
GET    /api/v1/tenants/{id}              # 租户详情
PUT    /api/v1/tenants/{id}              # 更新租户信息
DELETE /api/v1/tenants/{id}              # 删除租户（软删除）
POST   /api/v1/tenants/{id}/enable       # 启用租户
POST   /api/v1/tenants/{id}/disable      # 停用租户
```

### 5.4 申请审核（超管 / 平台运营）

```
GET    /api/v1/applications              # 申请列表（分页）
POST   /api/v1/applications/{id}/approve # 审核通过
POST   /api/v1/applications/{id}/reject  # 审核拒绝
```

### 5.5 订阅管理

```
# 超管 / 平台运营
GET    /api/v1/subscriptions             # 所有订阅列表
POST   /api/v1/subscriptions             # 创建订阅（线下充值）
PUT    /api/v1/subscriptions/{id}/pay    # 标记已支付

# 租户管理员
GET    /api/v1/my-subscriptions          # 我的订阅列表
POST   /api/v1/my-subscriptions          # 发起续费（支付宝/微信）
POST   /api/v1/my-subscriptions/callback # 支付回调（支付宝/微信）
```

### 5.6 租户内用户接口（租户管理员/成员）

```
GET    /api/v1/users/me                  # 当前用户信息
PUT    /api/v1/users/me                  # 更新个人信息
PUT    /api/v1/users/me/password         # 修改密码

# 租户管理员
GET    /api/v1/users                     # 租户内用户列表
POST   /api/v1/users                     # 创建用户
DELETE /api/v1/users/{id}                # 删除用户
PUT    /api/v1/users/{id}/status         # 启用/停用用户
PUT    /api/v1/users/{id}/roles          # 分配角色
```

### 5.7 角色权限接口（租户管理员）

```
GET    /api/v1/roles                     # 角色列表
POST   /api/v1/roles                     # 创建角色
PUT    /api/v1/roles/{id}                # 更新角色
DELETE /api/v1/roles/{id}                # 删除角色（系统角色不可删）
PUT    /api/v1/roles/{id}/permissions    # 配置角色权限
```

### 5.8 审计日志

```
# 超管
GET    /api/v1/audit-logs                # 全平台审计日志
GET    /api/v1/tenants/{id}/audit-logs   # 指定租户审计日志

# 租户管理员
GET    /api/v1/my-audit-logs             # 本租户审计日志
```

### 5.9 站内信

```
# 所有用户
GET    /api/v1/notifications             # 我的通知列表
PUT    /api/v1/notifications/{id}/read   # 标记已读
PUT    /api/v1/notifications/read-all    # 全部标记已读
GET    /api/v1/notifications/unread-count # 未读数量

# 超管
POST   /api/v1/notifications             # 发送平台公告（给所有租户或指定租户）
```

---

## 6. 认证与鉴权流程

### 6.1 登录流程

```
用户提交登录请求
        │
        ▼
┌─────────────────┐
│ 验证用户名密码   │
└─────────────────┘
        │
        ├── 超管 ──> 直接通过
        │
        ▼
┌─────────────────┐     否     ┌─────────────────┐
│ 检查租户状态     │──────────>│ 返回错误信息     │
│ ACTIVE?         │           │ (已停用/已过期/   │
└─────────────────┘           │  待充值等)        │
        │ 是                  └─────────────────┘
        ▼
┌─────────────────┐
│ 生成 JWT Token   │
│ (包含 tenantId,  │
│  userId, roles)  │
└─────────────────┘
        │
        ▼
返回 Token + 用户信息
```

### 6.2 请求鉴权流程

```
请求进入
    │
    ▼
┌─────────────────┐
│ JWT 解析过滤器   │
│ 提取 tenantId,  │
│ userId, roles   │
└─────────────────┘
    │
    ▼
┌─────────────────┐
│ 设置租户上下文   │
│ TenantContext   │
└─────────────────┘
    │
    ▼
┌─────────────────┐     否     ┌─────────────────┐
│ 租户状态校验     │──────────>│ 返回 403        │
│ (ACTIVE?)       │           │ (已停用/已过期)  │
└─────────────────┘           └─────────────────┘
    │ 是
    ▼
┌─────────────────┐     否     ┌─────────────────┐
│ 权限检查         │──────────>│ 返回 403        │
│ (RBAC)          │           └─────────────────┘
└─────────────────┘
    │ 是
    ▼
执行业务逻辑
    │
    ▼
记录审计日志 + 清理上下文
```

---

## 7. 关键设计决策

### 7.1 租户标识传递

- JWT 中存储当前租户 ID
- 超管可通过 Header `X-Tenant-Id` 切换租户上下文（管理平台或查看指定租户）

### 7.2 数据隔离

- 行级隔离（`tenant_id` 字段），通过 MyBatis-Plus 拦截器自动过滤
- 平台级数据（租户表、申请记录、全局审计日志）tenantId 为 null

### 7.3 过期处理策略

- 到期后状态变为 EXPIRED，读操作正常，写操作拒绝
- 过期数据永久保留，不做自动清理
- 超管可手动硬删除彻底清除数据（不可恢复）
- 定时任务每天扫描即将到期的租户，触发站内信通知

---

## 8. 技术栈选型

| 组件 | 选型 | 说明 |
|------|------|------|
| ORM | MyBatis-Plus | 内置多租户插件、分页插件 |
| 认证 | Spring Security + JWT | 标准方案 |
| 缓存 | Redis | 租户配置缓存、Token 黑名单 |
| 数据库 | MySQL 8.0 | 支持 JSON 字段 |
| 定时任务 | Spring Scheduler | 到期扫描、通知发送 |
| 支付 | Alipay SDK + WeChatPay SDK | 支付宝、微信支付（卡密后期） |

---

## 9. 下一步

1. 确认上述设计方案
2. 如有调整，继续讨论
3. 制定详细实施计划，开始编码
