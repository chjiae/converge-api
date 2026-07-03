# 多租户体系阶段 3 - 前端控制台 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建完整的前端控制台，对接后端全部 API，实现超管/租户管理员/租户用户三种角色的管理界面。采用 HttpOnly Cookie 认证架构，防御 XSS 和 CSRF 攻击。

**Architecture:** React 19 + TypeScript + Vite + Tailwind CSS 4 + shadcn/ui，fetch API 封装 + HttpOnly Cookie 自动携带，角色路由守卫，响应式侧边栏布局。

**Tech Stack:** React 19 + TypeScript 6 + Vite 8 + Tailwind CSS 4 + shadcn/ui + React Router 7

**设计文档:** `docs/design/multi-tenant-proposal.md`
**阶段 2 计划:** `docs/superpowers/plans/2026-07-03-multi-tenant-phase2.md`（HttpOnly Cookie 认证改造）

---

## Token 安全存储方案

### 设计原则

前端 **不存储任何敏感 Token 到 JavaScript 可访问的存储中**（不使用 localStorage、sessionStorage、内存变量存储完整 token），从根本上防御 XSS 窃取。

### 具体方案：HttpOnly Cookie（阶段 2 Task 7 已在后端实现）

| 安全威胁 | 防御机制 | 说明 |
|---------|---------|------|
| **XSS 窃取 Token** | HttpOnly Cookie | JavaScript 无法读取 Cookie 内容，即使 XSS 注入也无法获取 Token |
| **CSRF 攻击** | SameSite=Strict Cookie | 跨站请求不会携带 Cookie，从根本上阻断 CSRF |
| **中间人攻击** | Secure Cookie + HTTPS | Cookie 仅通过 HTTPS 传输（生产环境） |

### 前端认证流程

```
登录请求 POST /api/v1/auth/login
        │
        ▼
后端返回：
  - JSON body: { userInfo: {...} }（不含 token）
  - Set-Cookie: access_token=xxx; HttpOnly; Secure; SameSite=Strict; Path=/
  - Set-Cookie: refresh_token=xxx; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth/refresh
        │
        ▼
前端存储 userInfo 到 React state（非敏感数据）
        │
        ▼
后续请求：fetch(url, { credentials: 'include' })
  - 浏览器自动携带 HttpOnly Cookie
  - 前端无需手动附加 Authorization header
        │
        ▼
Access Token 过期（401 响应）：
  - 前端自动调用 POST /api/v1/auth/refresh（浏览器自动携带 refresh_token Cookie）
  - 刷新成功后重试原请求
  - 刷新失败则跳转登录页
```

### 前端安全加固清单

- Vite 构建时设置 `Content-Security-Policy` 响应头（限制 script-src）
- 所有用户输入做 XSS 过滤（React 默认转义 JSX 输出，dangerouslySetInnerHTML 需额外审查）
- CSP nonce 或 hash 策略（生产部署时配置）
- 敏感操作（删除、支付）需二次确认弹窗

---

## Task 1: API 客户端层与类型定义

**Files:**
- Create: `frontend/src/lib/api-client.ts`
- Create: `frontend/src/lib/types.ts`
- Create: `frontend/src/lib/api-errors.ts`
- Modify: `frontend/vite.config.ts`（开发代理配置）

**步骤:**
- [ ] 在 `vite.config.ts` 添加开发代理：`server.proxy: { '/api': 'http://localhost:8080' }`，避免开发环境跨域问题
- [ ] 创建 `api-client.ts`：基于 `fetch` 封装统一请求函数
  - `request<T>(method, path, body?)` 函数：自动设置 `credentials: 'include'`、`Content-Type: application/json`
  - 统一响应解析：检查 `response.ok`，非 ok 时解析 JSON 提取错误信息
  - **自动 Token 刷新**：当收到 401 响应时，调用 `POST /api/v1/auth/refresh`（Cookie 自动携带），成功后重试原请求；刷新也失败则清除用户状态并跳转 `/login`
  - 并发刷新保护：使用 Promise 单例模式，多个请求同时 401 时只触发一次刷新
  - 导出 `get<T>(path)`、`post<T>(path, body)`、`put<T>(path, body)`、`del<T>(path)` 快捷方法
- [ ] 创建 `types.ts`：定义全部 API 类型（与后端 DTO 一一对应），每个类型和字段必须有中文注释
  - `UserInfo`：id, username, email, phone, userType, tenantId, tenantName, roles
  - `TokenResponse`：userInfo（注意：不含 token，token 在 Cookie 中）
  - `LoginRequest`：username, password
  - `RegisterRequest`：username, email, password, tenantCode
  - `Tenant`：id, code, name, description, status, trialUsed, expiredAt, createdAt, updatedAt
  - `TenantApplication`：id, companyName, contactName, contactEmail, contactPhone, description, applicationType, status, rejectReason, reviewedBy, reviewedAt
  - `Subscription`：id, tenantId, planType, amount, startDate, endDate, status, paymentMethod, paymentRef, remark
  - `User`：id, tenantId, username, email, phone, status, userType, createdAt
  - `Role`：id, tenantId, code, name, description, isSystem, createdAt
  - `Notification`：id, tenantId, userId, title, content, type, isRead, readAt, createdAt
  - `AuditLog`：id, tenantId, userId, username, module, action, target, detail, ipAddress, createdAt
  - `PageResult<T>`：list, total, page, size
  - `Result<T>`：code, message, data
- [ ] 创建 `api-errors.ts`：定义 `ApiError` 类（含 code、message），统一错误处理工具函数
- [ ] 提交: `feat(前端): 搭建 API 客户端层和类型定义`

---

## Task 2: 认证上下文重构

**Files:**
- Modify: `frontend/src/contexts/auth-context.tsx`（替换 mock 实现）
- Create: `frontend/src/hooks/use-api.ts`

**步骤:**
- [ ] 重构 `auth-context.tsx`：
  - `User` 接口替换为 `types.ts` 中的 `UserInfo`（含 userType、tenantId、roles 等）
  - `AuthContextType` 增加：`isLoading`（初始认证检查）、`refreshUser()`（重新获取用户信息）
  - `login(username, password)`：调用 `POST /api/v1/auth/login`，从响应 JSON 提取 userInfo 存入 state（token 在 Cookie 中，前端不处理）
  - `register(username, email, password, tenantCode)`：调用 `POST /api/v1/auth/register`
  - `logout()`：调用 `POST /api/v1/auth/logout`，清除 React state，Cookie 由后端清除
  - `AuthProvider` 初始化时调用 `GET /api/v1/users/me` 尝试恢复登录状态（Cookie 有效时返回用户信息，401 则未登录）
  - 提供 `isSuperAdmin`、`isTenantAdmin`、`isTenantUser` 便捷计算属性
- [ ] 创建 `use-api.ts` hook：封装 `useQuery` 和 `useMutation` 模式
  - `useQuery<T>(fetcher)` 返回 `{ data, loading, error, refetch }`
  - `useMutation<T>(mutator)` 返回 `{ mutate, loading, error, data }`
  - 处理 loading 状态、error 状态、组件卸载后取消请求
- [ ] 提交: `feat(前端): 重构认证上下文，对接真实 API`

---

## Task 3: 路由守卫与权限控制

**Files:**
- Create: `frontend/src/components/auth-guard.tsx`
- Create: `frontend/src/components/role-guard.tsx`
- Modify: `frontend/src/App.tsx`（路由重构）

**步骤:**
- [ ] 创建 `AuthGuard` 组件：
  - 检查 `isAuthenticated`，未登录则 `<Navigate to="/login" />` 并保存 `state.from` 用于登录后回跳
  - 加载中（`isLoading`）显示全局 Loading 骨架屏
- [ ] 创建 `RoleGuard` 组件：
  - Props: `allowedRoles: string[]`（允许的角色编码列表）
  - 检查当前用户是否拥有指定角色之一，不满足则显示 403 页面或跳转
- [ ] 重构 `App.tsx` 路由结构：
  ```
  / → HomePage（公开）
  /login → LoginPage（公开，已登录跳 /console）
  /register → RegisterPage（公开，已登录跳 /console）
  /apply → ApplyPage（公开，租户申请页）
  /console → AuthGuard → ConsoleLayout
    /console → DashboardPage（默认）
    /console/tenants → RoleGuard[SUPER_ADMIN] → TenantListPage
    /console/tenants/:id → RoleGuard[SUPER_ADMIN] → TenantDetailPage
    /console/applications → RoleGuard[SUPER_ADMIN, PLATFORM_OPERATOR] → ApplicationListPage
    /console/subscriptions → RoleGuard[SUPER_ADMIN, PLATFORM_OPERATOR] → SubscriptionListPage
    /console/audit-logs → RoleGuard[SUPER_ADMIN] → AuditLogPage
    /console/users → RoleGuard[TENANT_OWNER, TENANT_ADMIN] → UserListPage
    /console/roles → RoleGuard[TENANT_OWNER, TENANT_ADMIN] → RoleListPage
    /console/my-subscriptions → AuthGuard → MySubscriptionPage
    /console/notifications → AuthGuard → NotificationListPage
    /console/profile → AuthGuard → ProfilePage
  ```
- [ ] 侧边栏导航根据用户角色动态显示菜单项（超管看到租户管理/申请审核/订阅/审计日志，租户管理员看到用户管理/角色/订阅/通知）
- [ ] 提交: `feat(前端): 实现路由守卫和角色权限控制`

---

## Task 4: 控制台布局框架

**Files:**
- Create: `frontend/src/layouts/console-layout.tsx`
- Create: `frontend/src/components/sidebar.tsx`
- Create: `frontend/src/components/top-bar.tsx`
- Create: `frontend/src/components/loading-skeleton.tsx`
- Create: `frontend/src/components/data-table.tsx`（通用表格组件）
- Create: `frontend/src/components/pagination.tsx`
- Create: `frontend/src/components/confirm-dialog.tsx`
- Create: `frontend/src/components/toast.tsx`（或安装 sonner）

**步骤:**
- [ ] 使用 shadcn/ui 安装所需组件：`dialog`、`table`、`select`、`badge`、`separator`、`avatar`、`tooltip`、`sheet`（移动端侧边栏）、`skeleton`、`tabs`、`card`、`sonner`（toast 通知）
- [ ] 创建 `ConsoleLayout`：左侧可折叠侧边栏 + 顶部导航栏 + 右侧内容区域，响应式（移动端侧边栏变抽屉）
- [ ] 创建 `Sidebar`：品牌 Logo + 导航菜单组 + 底部用户信息，菜单项使用 lucide-react 图标，当前路由高亮，根据 `userInfo.roles` 动态渲染菜单
- [ ] 创建 `TopBar`：面包屑 + 通知铃铛（未读数量 badge）+ 用户头像下拉菜单（个人信息、退出登录）+ 暗色模式切换
- [ ] 创建 `DataTable<T>` 通用表格组件：支持列定义、排序、分页、loading 骨架、空状态
- [ ] 创建 `Pagination` 分页组件：页码导航、每页条数选择
- [ ] 创建 `ConfirmDialog` 确认弹窗：标题 + 描述 + 确认/取消按钮，用于删除等危险操作
- [ ] 创建 `LoadingSkeleton` 全局加载骨架屏
- [ ] 配置 sonner toast 组件，提供全局通知反馈
- [ ] 提交: `feat(前端): 搭建控制台布局框架和通用组件`

---

## Task 5: 超管控制台 - 租户管理

**Files:**
- Create: `frontend/src/pages/console/tenant-list.tsx`
- Create: `frontend/src/pages/console/tenant-detail.tsx`
- Create: `frontend/src/pages/console/tenant-form-dialog.tsx`

**步骤:**
- [ ] `TenantListPage`：分页表格展示所有租户（code、name、status badge、trialUsed、expiredAt、createdAt），支持按状态筛选，操作列包含启用/停用/删除按钮，顶部有"创建租户"按钮
- [ ] `TenantFormDialog`：创建租户表单弹窗（code、name、description、adminUsername、adminEmail、adminPassword），表单验证，提交后刷新列表
- [ ] `TenantDetailPage`：租户详情展示（基本信息、当前订阅、用户列表概览），状态变更操作（启用/停用/删除），确认弹窗保护
- [ ] 状态 badge 颜色映射：ACTIVE=绿色、TRIAL=蓝色、PENDING=黄色、DISABLED=灰色、EXPIRED=红色、DELETED=红色暗色
- [ ] 提交: `feat(前端): 实现超管租户管理页面`

---

## Task 6: 超管控制台 - 申请审核与订阅管理

**Files:**
- Create: `frontend/src/pages/console/application-list.tsx`
- Create: `frontend/src/pages/console/subscription-list.tsx`
- Create: `frontend/src/pages/console/audit-log.tsx`

**步骤:**
- [ ] `ApplicationListPage`：分页表格展示申请列表（companyName、contactName、applicationType badge、status badge、createdAt），操作列包含通过/拒绝按钮，拒绝时弹窗输入拒绝原因，通过后显示创建结果
- [ ] `SubscriptionListPage`：分页表格展示全平台订阅（tenantId/name、planType、amount、status badge、paymentMethod、startDate~endDate），操作列包含标记已支付按钮（仅 PENDING 状态可操作）
- [ ] `AuditLogPage`：分页表格展示审计日志（username、module、action、target、ipAddress、createdAt），支持按模块和操作类型筛选，展开行查看 JSON 详情
- [ ] 提交: `feat(前端): 实现申请审核、订阅管理和审计日志页面`

---

## Task 7: 租户管理控制台 - 用户与角色管理

**Files:**
- Create: `frontend/src/pages/console/user-list.tsx`
- Create: `frontend/src/pages/console/user-form-dialog.tsx`
- Create: `frontend/src/pages/console/role-list.tsx`
- Create: `frontend/src/pages/console/role-form-dialog.tsx`

**步骤:**
- [ ] `UserListPage`：分页表格展示租户内用户（username、email、phone、status badge、roles tags），操作列包含编辑状态（启用/停用）、分配角色、删除，顶部有"添加用户"按钮
- [ ] `UserFormDialog`：创建用户表单（username、email、password、confirmPassword），表单验证（密码强度、确认一致），提交后刷新列表
- [ ] `RoleListPage`：表格展示租户角色（code、name、description、isSystem badge），操作列包含编辑、删除（系统角色禁止删除和编辑），顶部有"创建角色"按钮
- [ ] `RoleFormDialog`：创建/编辑角色表单（code、name、description）+ 权限配置（复选框组，按 resource 分组展示 permission 列表）
- [ ] 角色分配弹窗：选择用户后弹出角色多选框，支持批量分配
- [ ] 提交: `feat(前端): 实现租户内用户管理和角色管理页面`

---

## Task 8: 租户管理控制台 - 订阅与续费

**Files:**
- Create: `frontend/src/pages/console/my-subscription.tsx`
- Create: `frontend/src/pages/console/renewal-dialog.tsx`
- Create: `frontend/src/pages/console/payment-result.tsx`

**步骤:**
- [ ] `MySubscriptionPage`：展示当前租户的订阅列表（分页表格），高亮当前生效的订阅，显示租户到期时间倒计时
- [ ] `RenewalDialog`：续费弹窗，选择套餐类型（月付/季付/年付），选择支付方式（支付宝/微信/卡密），显示价格和有效期
  - 支付宝/微信：调用 `POST /api/v1/my-subscriptions/pay` 获取支付链接，新窗口打开支付页面或展示二维码
  - 卡密：调用 `POST /api/v1/my-subscriptions/redeem` 提交卡密兑换
- [ ] `PaymentResultPage`：支付回调落地页（`/console/payment-result?status=success`），展示支付结果，自动刷新订阅状态
- [ ] 提交: `feat(前端): 实现订阅管理和在线续费页面`

---

## Task 9: 通知中心与个人中心

**Files:**
- Create: `frontend/src/pages/console/notification-list.tsx`
- Create: `frontend/src/pages/console/profile.tsx`
- Create: `frontend/src/components/notification-bell.tsx`
- Modify: `frontend/src/layouts/console-layout.tsx`（集成通知铃铛）

**步骤:**
- [ ] `NotificationListPage`：分页展示通知列表，未读通知视觉区分（加粗标题 + 左侧蓝点），支持"标记已读"和"全部已读"操作，按通知类型筛选（SYSTEM/AUDIT_RESULT/EXPIRY_WARNING/SUBSCRIPTION）
- [ ] `NotificationBell` 组件：嵌入 TopBar 的通知铃铛图标，定时轮询 `GET /api/v1/notifications/unread-count`（30 秒间隔），未读数量 badge，点击下拉展示最近 5 条通知预览，点击"查看全部"跳转通知列表页
- [ ] `ProfilePage`：个人信息展示和编辑（用户名只读、邮箱、手机号），修改密码表单（旧密码 + 新密码 + 确认新密码，密码强度指示器）
- [ ] 提交: `feat(前端): 实现通知中心和个人中心`

---

## Task 10: 首页与申请页优化

**Files:**
- Modify: `frontend/src/pages/home.tsx`（增加产品介绍内容）
- Create: `frontend/src/pages/apply.tsx`（租户申请页面）
- Modify: `frontend/src/pages/login.tsx`（对接真实 API）
- Modify: `frontend/src/pages/register.tsx`（对接真实 API，增加租户编码字段）

**步骤:**
- [ ] 优化 `HomePage`：增加产品介绍区域（功能特点、定价方案、申请入口），已登录用户显示控制台入口
- [ ] 创建 `ApplyPage`：租户申请公开页面（companyName、contactName、contactEmail、contactPhone、description、applicationType 单选 REGISTER/TRIAL），提交后展示申请状态跟踪页（可输入申请编码查询状态）
- [ ] 修改 `LoginPage`：
  - 将 email 字段改为 username（后端用 username 登录）
  - 对接 `auth-context` 的真实 login 方法
  - 登录成功后根据 userType 跳转到对应页面（超管→租户管理，租户用户→控制台首页）
  - 移除 Google/GitHub 第三方登录按钮（暂时不支持）
- [ ] 修改 `RegisterPage`：
  - 增加 tenantCode 输入字段（用户注册需指定租户编码）
  - 对接 `auth-context` 的真实 register 方法
  - 注册成功后跳转控制台
- [ ] 提交: `feat(前端): 优化首页，新增租户申请页，对接登录注册`

---

## Task 11: Dashboard 概览页

**Files:**
- Create: `frontend/src/pages/console/dashboard.tsx`

**步骤:**
- [ ] 超管 Dashboard：统计卡片（租户总数、活跃租户、待审核申请数、本月订阅收入），最近申请列表（前 5 条），最近审计日志（前 10 条）
  - 数据通过现有 API 获取（listTenants size=1 取 total、listApplications、listAuditLogs）
- [ ] 租户管理员 Dashboard：统计卡片（租户用户数、活跃订阅、未读通知数、到期倒计时），当前订阅信息卡片，最近通知列表
- [ ] 租户用户 Dashboard：简化视图（未读通知数、个人信息卡片、最近通知）
- [ ] 使用 shadcn Card 组件构建统计卡片，lucide-react 图标
- [ ] 提交: `feat(前端): 实现角色化 Dashboard 概览页`

---

## Task 12: 构建优化与安全加固

**Files:**
- Modify: `frontend/vite.config.ts`（构建配置）
- Create: `frontend/src/lib/route-config.ts`（路由配置集中管理）
- Create: `frontend/public/_headers`（部署安全头）或 Nginx 配置示例

**步骤:**
- [ ] 路由代码分割：使用 `React.lazy` + `Suspense` 对控制台页面做懒加载，减小首屏 bundle
- [ ] 配置 Vite 构建优化：手动 chunk 分离（vendor chunk 包含 react/react-router/shadcn，业务代码单独 chunk）
- [ ] 安全响应头配置（生产部署时使用）：
  ```
  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; connect-src 'self' https://api.example.com
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: strict-origin-when-cross-origin
  ```
- [ ] 全局 ErrorBoundary 组件：捕获渲染异常，展示友好错误页面
- [ ] 404 Not Found 页面
- [ ] 运行 `npm run build` 验证构建成功，`npm run lint` 无告警
- [ ] 提交: `chore(前端): 构建优化和安全加固`

---

## Task 13: 端到端验证与联调

**步骤:**
- [ ] 启动后端（`mvn spring-boot:run`）+ 前端（`npm run dev`），验证代理配置正常
- [ ] 完整流程测试 1：超管登录 → 创建租户 → 租户列表查看 → 租户详情
- [ ] 完整流程测试 2：租户申请（TRIAL）→ 超管审核通过 → 租户管理员登录 → 查看控制台
- [ ] 完整流程测试 3：租户管理员 → 创建用户 → 分配角色 → 用户登录验证
- [ ] 完整流程测试 4：租户管理员 → 发起续费 → 支付流程（卡密兑换）→ 订阅激活
- [ ] 完整流程测试 5：通知中心 → 收到到期提醒 → 标记已读
- [ ] 安全验证：检查浏览器 DevTools → Application → Cookies 确认 access_token 和 refresh_token 为 HttpOnly（JavaScript 不可读）
- [ ] 安全验证：检查浏览器 DevTools → Application → Storage 确认 localStorage/sessionStorage 中无 token
- [ ] 安全验证：XSS 测试 — 在输入框尝试注入 `<script>alert(1)</script>`，确认被转义
- [ ] 响应式测试：Chrome DevTools 模拟移动端，验证侧边栏抽屉、表格横向滚动
- [ ] 提交: `chore: 阶段 3 前端控制台完成`

---

## 验证清单

完成所有 Task 后执行以下验证：

1. `cd frontend && npm run build` — 构建成功，无 TypeScript 错误
2. `npm run lint` — oxlint 无告警
3. 启动前后端联调，完成验证清单中的 5 个完整流程测试
4. 浏览器 DevTools 安全检查：HttpOnly Cookie ✓、无 localStorage Token ✓、XSS 防护 ✓
5. 响应式布局：桌面端和移动端均可正常使用
6. 提交: `chore: 阶段 3 前端控制台完成`
