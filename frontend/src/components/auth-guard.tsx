/**
 * 认证守卫组件
 *
 * 保护需要登录才能访问的路由。
 * - 加载中：显示全局骨架屏
 * - 未登录：重定向到 /login 并保存来源路径
 * - 已登录：渲染子组件
 */

import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '@/contexts/auth-context'

/**
 * 认证守卫
 *
 * 用作布局路由的 element，通过 <Outlet /> 渲染子路由。
 * 在认证状态检测中时显示居中加载指示器，
 * 未认证时重定向至登录页并携带来源路径。
 */
export default function AuthGuard() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  // 初始认证状态检测中，显示加载指示器
  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    )
  }

  // 未登录，重定向到登录页并保存来源路径
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  // 已登录，渲染子路由
  return <Outlet />
}
