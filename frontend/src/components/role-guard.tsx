/**
 * 角色守卫组件
 *
 * 限制特定用户类型才能访问的路由。
 * 根据 allowedRoles 属性检查当前用户是否拥有对应角色，
 * 不匹配时显示 403 无权限页面。
 */

import { Outlet, Link } from 'react-router-dom'
import { useAuth } from '@/contexts/auth-context'

/** 角色守卫属性 */
interface RoleGuardProps {
  /** 允许访问的用户类型列表，如 ['SUPER_ADMIN', 'TENANT_OWNER'] */
  allowedRoles: string[]
}

/**
 * 角色守卫
 *
 * 用作布局路由的 element，检查当前用户类型是否在允许列表中。
 * 不满足条件时展示 403 页面，满足条件时通过 <Outlet /> 渲染子路由。
 *
 * @param props - 组件属性
 * @param props.allowedRoles - 允许访问的用户类型数组
 */
export default function RoleGuard({ allowedRoles }: RoleGuardProps) {
  const { user } = useAuth()

  // 用户类型不在允许列表中，显示 403 无权限页面
  if (!user || !allowedRoles.includes(user.userType)) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4">
        <h1 className="text-4xl font-bold text-destructive">403</h1>
        <p className="text-muted-foreground">无权限访问此页面</p>
        <Link
          to="/console"
          className="text-primary underline hover:no-underline"
        >
          返回控制台
        </Link>
      </div>
    )
  }

  // 角色匹配，渲染子路由
  return <Outlet />
}
