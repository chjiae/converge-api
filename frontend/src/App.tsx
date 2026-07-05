/**
 * 应用根组件
 *
 * 定义全局路由结构：
 * - 公开路由：首页、登录、注册、租户申请
 * - 受保护路由：控制台及其子页面（需登录）
 * - 角色限制路由：根据用户类型控制访问权限
 * - 兜底路由：404 未找到页面
 *
 * 页面组件采用 React.lazy 按需加载，配合 Suspense 实现代码拆分，
 * 减少首屏加载体积。
 */

import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import AuthGuard from '@/components/auth-guard'
import RoleGuard from '@/components/role-guard'
import ConsoleLayout from '@/layouts/console-layout'

/* ====== 懒加载页面组件 ====== */
const HomePage = lazy(() => import('@/pages/home'))
const LoginPage = lazy(() => import('@/pages/login'))
const RegisterPage = lazy(() => import('@/pages/register'))
const ApplyPage = lazy(() => import('@/pages/apply'))
const NotFoundPage = lazy(() => import('@/pages/not-found'))
const DashboardPage = lazy(() => import('@/pages/console/dashboard'))
const TenantListPage = lazy(() => import('@/pages/console/tenant-list'))
const TenantDetailPage = lazy(() => import('@/pages/console/tenant-detail'))
const ApplicationListPage = lazy(() => import('@/pages/console/application-list'))
const SubscriptionListPage = lazy(() => import('@/pages/console/subscription-list'))
const SubscriptionPlanListPage = lazy(() => import('@/pages/console/subscription-plan-list'))
const AuditLogPage = lazy(() => import('@/pages/console/audit-log'))
const UserListPage = lazy(() => import('@/pages/console/user-list'))
const RoleListPage = lazy(() => import('@/pages/console/role-list'))
const MySubscriptionPage = lazy(() => import('@/pages/console/my-subscription'))
const SubscriptionRenewPage = lazy(() => import('@/pages/console/subscription-renew'))
const PaymentResultPage = lazy(() => import('@/pages/payment/payment-result'))
const NotificationListPage = lazy(() => import('@/pages/console/notification-list'))
const ProfilePage = lazy(() => import('@/pages/console/profile'))

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
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <span className="text-muted-foreground">加载中...</span>
        </div>
      }
    >
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
              <Route path="/console/subscription-plans" element={<SubscriptionPlanListPage />} />
              <Route path="/console/audit-logs" element={<AuditLogPage />} />
            </Route>

            {/* 租户管理员路由（租户所有者 + 租户管理员） */}
            <Route element={<RoleGuard allowedRoles={['TENANT_OWNER', 'TENANT_ADMIN']} />}>
              <Route path="/console/users" element={<UserListPage />} />
              <Route path="/console/roles" element={<RoleListPage />} />
            </Route>

            {/* 所有已登录用户可访问的路由 */}
            <Route path="/console/my-subscriptions" element={<MySubscriptionPage />} />
            <Route path="/console/my-subscriptions/renew" element={<SubscriptionRenewPage />} />
            <Route path="/console/notifications" element={<NotificationListPage />} />
            <Route path="/console/profile" element={<ProfilePage />} />
          </Route>

          {/* 支付结果页面（独立布局，不在 ConsoleLayout 内） */}
          <Route path="/console/payment-result" element={<PaymentResultPage />} />
        </Route>

        {/* ====== 兜底路由：404 ====== */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}

export default App
