/**
 * 侧边栏导航组件
 *
 * 提供可折叠的侧边导航菜单，包含：
 * - 品牌标识区域（Logo + 名称）
 * - 基于用户角色的动态菜单项
 * - 底部用户信息区域
 * - 折叠/展开切换按钮
 *
 * 宽度：展开 240px，折叠 64px，带有平滑过渡动画。
 */

import { NavLink, useLocation } from 'react-router-dom'
import {
  LayoutDashboard,
  Building2,
  FileCheck2,
  CreditCard,
  BadgePercent,
  ScrollText,
  Users,
  Shield,
  Bell,
  UserCircle,
  Bot,
} from 'lucide-react'
import { useAuth } from '@/contexts/auth-context'
import { cn } from '@/lib/utils'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { Separator } from '@/components/ui/separator'
import type { ReactNode } from 'react'

/** 侧边栏组件属性 */
interface SidebarProps {
  /** 是否处于折叠状态 */
  collapsed: boolean
  /** 自定义类名 */
  className?: string
}

/** 菜单项定义 */
interface MenuItem {
  /** 菜单显示名称 */
  label: string
  /** 路由路径 */
  to: string
  /** 菜单图标 */
  icon: ReactNode
}

/** 菜单分组定义 */
interface MenuGroup {
  /** 分组标题（折叠时隐藏） */
  title: string
  /** 分组下的菜单项 */
  items: MenuItem[]
}

/**
 * 根据当前用户角色构建可见的菜单分组列表
 *
 * @param isSuperAdmin - 是否为超级管理员
 * @param isTenantAdmin - 是否为租户管理员
 * @returns 菜单分组数组
 */
function buildMenuGroups(isSuperAdmin: boolean, isTenantAdmin: boolean): MenuGroup[] {
  const groups: MenuGroup[] = []

  // 所有用户可见的通用菜单
  groups.push({
    title: '概览',
    items: [
      { label: '仪表盘', to: '/console', icon: <LayoutDashboard className="size-4" /> },
    ],
  })

  // 超级管理员专属菜单
  if (isSuperAdmin) {
    groups.push({
      title: '系统管理',
      items: [
        { label: '租户管理', to: '/console/tenants', icon: <Building2 className="size-4" /> },
        { label: '申请审核', to: '/console/applications', icon: <FileCheck2 className="size-4" /> },
        { label: '订阅管理', to: '/console/subscriptions', icon: <CreditCard className="size-4" /> },
        { label: '套餐配置', to: '/console/subscription-plans', icon: <BadgePercent className="size-4" /> },
        { label: '审计日志', to: '/console/audit-logs', icon: <ScrollText className="size-4" /> },
      ],
    })
  }

  // 租户管理员（租户所有者 / 租户管理员）可见菜单
  if (isTenantAdmin) {
    groups.push({
      title: '租户设置',
      items: [
        { label: '用户管理', to: '/console/users', icon: <Users className="size-4" /> },
        { label: '角色管理', to: '/console/roles', icon: <Shield className="size-4" /> },
        { label: 'AI 网关', to: '/console/ai-gateway', icon: <Bot className="size-4" /> },
      ],
    })
  }

  // 所有已登录用户可见的个人菜单
  groups.push({
    title: '个人',
    items: [
      { label: '我的订阅', to: '/console/my-subscriptions', icon: <CreditCard className="size-4" /> },
      { label: '通知中心', to: '/console/notifications', icon: <Bell className="size-4" /> },
      { label: '个人信息', to: '/console/profile', icon: <UserCircle className="size-4" /> },
    ],
  })

  return groups
}

/**
 * 侧边栏导航组件
 *
 * 根据用户角色动态渲染菜单项，支持折叠/展开状态切换。
 * 折叠状态下菜单项显示为图标并通过 Tooltip 展示名称。
 */
export default function Sidebar({ collapsed, className }: SidebarProps) {
  const { user, isSuperAdmin, isTenantAdmin } = useAuth()
  const location = useLocation()
  const menuGroups = buildMenuGroups(isSuperAdmin, isTenantAdmin)

  /**
   * 判断当前菜单项是否处于激活状态
   * 对 /console 首页使用精确匹配，其余路径使用前缀匹配
   */
  const isActive = (to: string) => {
    if (to === '/console') {
      return location.pathname === '/console'
    }
    return location.pathname.startsWith(to)
  }

  return (
    <div
      className={cn(
        'flex h-full flex-col bg-sidebar text-sidebar-foreground transition-all duration-300',
        collapsed ? 'w-16' : 'w-60',
        className,
      )}
    >
      {/* 品牌标识区域 */}
      <div className="flex h-14 items-center gap-3 px-4">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
            <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
            <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
          </svg>
        </div>
        {!collapsed && (
          <span className="text-base font-medium tracking-wide">Converge</span>
        )}
      </div>

      <Separator className="bg-sidebar-border" />

      {/* 导航菜单区域 */}
      <nav className="flex-1 overflow-y-auto px-2 py-3">
        {menuGroups.map((group) => (
          <div key={group.title} className="mb-3">
            {/* 分组标题（折叠时隐藏） */}
            {!collapsed && (
              <p className="mb-1 px-3 text-xs font-medium text-muted-foreground">
                {group.title}
              </p>
            )}
            {group.items.map((item) => {
              const active = isActive(item.to)
              const linkContent = (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/console'}
                  className={cn(
                    'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                    active
                      ? 'bg-sidebar-accent text-sidebar-accent-foreground font-medium'
                      : 'text-muted-foreground hover:bg-sidebar-accent/50 hover:text-sidebar-foreground',
                    collapsed && 'justify-center px-2',
                  )}
                >
                  <span className="shrink-0">{item.icon}</span>
                  {!collapsed && <span>{item.label}</span>}
                </NavLink>
              )

              // 折叠状态下使用 Tooltip 展示菜单名称
              if (collapsed) {
                return (
                  <Tooltip key={item.to}>
                    <TooltipTrigger render={linkContent} />
                    <TooltipContent side="right">{item.label}</TooltipContent>
                  </Tooltip>
                )
              }

              return linkContent
            })}
          </div>
        ))}
      </nav>

      {/* 底部用户信息区域 */}
      <Separator className="bg-sidebar-border" />
      <div className="p-3">
        <div className={cn('flex items-center gap-3', collapsed && 'justify-center')}>
          {/* 用户头像（取用户名首字母） */}
          <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-medium text-primary-foreground">
            {user?.username?.charAt(0)?.toUpperCase() ?? '?'}
          </div>
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium">{user?.username ?? ''}</p>
              <p className="truncate text-xs text-muted-foreground">
                {user?.tenantName ?? '系统管理员'}
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
