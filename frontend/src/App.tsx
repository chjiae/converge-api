/**
 * 应用根组件
 *
 * 定义全局路由结构：
 * - 公开路由：首页、登录、注册、租户申请
 * - 受保护路由：控制台及其子页面（需登录）
 * - 角色限制路由：根据用户类型控制访问权限
 * - 兜底路由：404 未找到页面
 */

import { Routes, Route } from 'react-router-dom'
import AuthGuard from '@/components/auth-guard'
import RoleGuard from '@/components/role-guard'
import ConsoleLayout from '@/layouts/console-layout'
import HomePage from '@/pages/home'
import LoginPage from '@/pages/login'
import RegisterPage from '@/pages/register'
import ApplyPage from '@/pages/apply'
import NotFoundPage from '@/pages/not-found'
import DashboardPage from '@/pages/console/dashboard'
import TenantListPage from '@/pages/console/tenant-list'
import TenantDetailPage from '@/pages/console/tenant-detail'
import ApplicationListPage from '@/pages/console/application-list'
import SubscriptionListPage from '@/pages/console/subscription-list'
import AuditLogPage from '@/pages/console/audit-log'
import UserListPage from '@/pages/console/user-list'
import RoleListPage from '@/pages/console/role-list'
import MySubscriptionPage from '@/pages/console/my-subscription'
import PaymentResultPage from '@/pages/payment/payment-result'
import NotificationListPage from '@/pages/console/notification-list'
import ProfilePage from '@/pages/console/profile'

/**
 * 应用入口组件
 *
 * 路由层级说明：
 * 1. 公开页面 — 无需登录即可访问
 * 2. 控制台 — AuthGuard 保护，ConsoleLayout 包裹
 * 3. 超级管理员路由 — RoleGuard['SUPER_ADMIN'] 限制
 * 4. 租户管理路由 — RoleGuard['TENANT_OWNER', 'TENANT_ADMIN'] 限制
 * 5. 通用认证路由 — 任何已登录用户可访问
 * 6. 通配路由 — 匹配所有未定义路径，显示 404
 */
function App() {
  return (
    <Routes>
      {/* ====== 公开路由 ====== */}
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/apply" element={<ApplyPage />} />

      {/* ====== 受保护的控制台路由 ====== */}
      <Route element={<AuthGuard />}>
        <Route element={<ConsoleLayout />}>
          {/* 控制台首页（默认仪表盘） */}
          <Route path="/console" element={<DashboardPage />} />

          {/* 超级管理员专属路由 */}
          <Route element={<RoleGuard allowedRoles={['SUPER_ADMIN']} />}>
            <Route path="/console/tenants" element={<TenantListPage />} />
            <Route path="/console/tenants/:id" element={<TenantDetailPage />} />
            <Route path="/console/applications" element={<ApplicationListPage />} />
            <Route path="/console/subscriptions" element={<SubscriptionListPage />} />
            <Route path="/console/audit-logs" element={<AuditLogPage />} />
          </Route>

          {/* 租户管理员路由（租户所有者 + 租户管理员） */}
          <Route element={<RoleGuard allowedRoles={['TENANT_OWNER', 'TENANT_ADMIN']} />}>
            <Route path="/console/users" element={<UserListPage />} />
            <Route path="/console/roles" element={<RoleListPage />} />
          </Route>

          {/* 所有已登录用户可访问的路由 */}
          <Route path="/console/my-subscriptions" element={<MySubscriptionPage />} />
          <Route path="/console/notifications" element={<NotificationListPage />} />
          <Route path="/console/profile" element={<ProfilePage />} />
        </Route>

        {/* 支付结果页面（独立布局，不在 ConsoleLayout 内） */}
        <Route path="/console/payment-result" element={<PaymentResultPage />} />
      </Route>

      {/* ====== 兜底路由：404 ====== */}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

export default App
