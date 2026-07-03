/**
 * 顶部导航栏组件
 *
 * 提供页面顶部工具栏，包含：
 * - 左侧：移动端汉堡菜单按钮 / 桌面端折叠切换按钮 + 面包屑导航
 * - 右侧：主题切换 + 用户下拉菜单
 *
 * 面包屑根据当前路由路径自动推导显示。
 */

import { useLocation, Link } from 'react-router-dom'
import {
  Menu,
  PanelLeftClose,
  PanelLeftOpen,
  LogOut,
  UserCircle,
  ChevronRight,
} from 'lucide-react'
import { useAuth } from '@/contexts/auth-context'
import { ModeToggle } from '@/components/mode-toggle'
import NotificationBell from '@/components/notification-bell'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Badge } from '@/components/ui/badge'

/** 顶部导航栏属性 */
interface TopBarProps {
  /** 移动端菜单按钮点击回调 */
  onMenuClick: () => void
  /** 当前侧边栏是否折叠 */
  collapsed: boolean
  /** 桌面端折叠切换回调 */
  onToggleCollapse: () => void
}

/** 路由路径到面包屑标签的映射表 */
const BREADCRUMB_MAP: Record<string, string> = {
  console: '控制台',
  tenants: '租户管理',
  applications: '申请审核',
  subscriptions: '订阅管理',
  'audit-logs': '审计日志',
  users: '用户管理',
  roles: '角色管理',
  'my-subscriptions': '我的订阅',
  notifications: '通知中心',
  profile: '个人信息',
}

/** 用户类型中文映射 */
const USER_TYPE_LABELS: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  TENANT_OWNER: '租户所有者',
  TENANT_ADMIN: '租户管理员',
  TENANT_USER: '租户用户',
}

/**
 * 根据当前路径生成面包屑导航段
 *
 * @param pathname - 当前路由路径
 * @returns 面包屑标签数组
 */
function getBreadcrumbs(pathname: string): string[] {
  const segments = pathname.split('/').filter(Boolean)
  return segments.map((seg) => BREADCRUMB_MAP[seg] ?? seg)
}

/**
 * 顶部导航栏组件
 *
 * 响应式设计：移动端显示汉堡菜单按钮，桌面端显示折叠切换按钮。
 * 面包屑根据路由自动推导，用户下拉菜单包含个人信息和退出操作。
 */
export default function TopBar({ onMenuClick, collapsed, onToggleCollapse }: TopBarProps) {
  const { user, logout } = useAuth()
  const location = useLocation()
  const breadcrumbs = getBreadcrumbs(location.pathname)

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b bg-background px-4">
      {/* 左侧：菜单切换 + 面包屑 */}
      <div className="flex items-center gap-2">
        {/* 移动端：汉堡菜单按钮 */}
        <Button
          variant="ghost"
          size="icon"
          className="lg:hidden"
          onClick={onMenuClick}
        >
          <Menu className="size-5" />
          <span className="sr-only">打开菜单</span>
        </Button>

        {/* 桌面端：侧边栏折叠/展开切换按钮 */}
        <Button
          variant="ghost"
          size="icon"
          className="hidden lg:flex"
          onClick={onToggleCollapse}
        >
          {collapsed ? (
            <PanelLeftOpen className="size-5" />
          ) : (
            <PanelLeftClose className="size-5" />
          )}
          <span className="sr-only">{collapsed ? '展开侧边栏' : '折叠侧边栏'}</span>
        </Button>

        {/* 面包屑导航 */}
        <nav className="hidden items-center gap-1 text-sm sm:flex">
          {breadcrumbs.map((crumb, index) => (
            <span key={index} className="flex items-center gap-1">
              {index > 0 && <ChevronRight className="size-3.5 text-muted-foreground" />}
              {index === breadcrumbs.length - 1 ? (
                <span className="font-medium text-foreground">{crumb}</span>
              ) : (
                <Link
                  to={index === 0 ? '/' : `/${breadcrumbs.slice(0, index + 1).join('/')}`}
                  className="text-muted-foreground hover:text-foreground transition-colors"
                >
                  {crumb}
                </Link>
              )}
            </span>
          ))}
        </nav>
      </div>

      {/* 右侧：主题切换 + 用户下拉菜单 */}
      <div className="flex items-center gap-2">
        <ModeToggle />
        <NotificationBell />

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="rounded-full">
              {/* 用户头像（取用户名首字母） */}
              <div className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-medium text-primary-foreground">
                {user?.username?.charAt(0)?.toUpperCase() ?? '?'}
              </div>
              <span className="sr-only">用户菜单</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            {/* 用户信息标签 */}
            <DropdownMenuLabel className="font-normal">
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium">{user?.username ?? ''}</span>
                <Badge variant="secondary" className="w-fit text-xs">
                  {USER_TYPE_LABELS[user?.userType ?? ''] ?? user?.userType ?? ''}
                </Badge>
              </div>
            </DropdownMenuLabel>
            <Separator />
            {/* 个人信息链接 */}
            <DropdownMenuItem asChild>
              <Link to="/console/profile" className="flex items-center gap-2 cursor-pointer">
                <UserCircle className="size-4" />
                个人信息
              </Link>
            </DropdownMenuItem>
            {/* 退出登录 */}
            <DropdownMenuItem
              onClick={() => { void logout() }}
              className="flex items-center gap-2 cursor-pointer text-destructive focus:text-destructive"
            >
              <LogOut className="size-4" />
              退出登录
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
