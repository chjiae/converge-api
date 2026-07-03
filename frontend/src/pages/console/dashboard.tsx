/**
 * Dashboard 概览页
 *
 * 根据当前登录用户的角色，渲染不同的仪表盘布局：
 * - SUPER_ADMIN：展示平台级统计（租户、申请、订阅、审计日志）及最近申请列表
 * - TENANT_OWNER / TENANT_ADMIN：展示租户级统计（用户、订阅、操作日志）及最近通知
 * - TENANT_USER：展示个人统计（订阅、未读通知）及欢迎卡片
 *
 * 通过 paginated list 接口的 total 字段获取统计数据，无需后端新增聚合接口。
 */

import { Link } from 'react-router-dom'
import { useQuery } from '@/hooks/use-api'
import { useAuth } from '@/contexts/auth-context'
import { get } from '@/lib/api-client'
import type {
  Tenant,
  TenantApplication,
  Subscription,
  AuditLog,
  Notification,
  User,
  PageResult,
} from '@/lib/types'
import { PageSkeleton } from '@/components/loading-skeleton'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'
import {
  Building2,
  FileCheck2,
  CreditCard,
  ScrollText,
  Users,
  Bell,
  ArrowRight,
} from 'lucide-react'

/* ========== 工具函数 ========== */

/**
 * 格式化日期时间为本地化短格式
 * @param dateStr - ISO 日期时间字符串，为 null 时返回占位符
 * @returns 格式化后的日期时间字符串
 */
function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/**
 * 获取申请审核状态的中文标签
 * @param status - 审核状态字符串
 * @returns 中文状态名称
 */
function getApplicationStatusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '待审核'
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已拒绝'
    default: return status
  }
}

/**
 * 获取申请审核状态的 Badge 样式类名
 * @param status - 审核状态字符串
 * @returns Tailwind CSS 类名
 */
function getApplicationStatusClass(status: string): string {
  switch (status) {
    case 'PENDING':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
    case 'APPROVED':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    case 'REJECTED':
      return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
    default:
      return ''
  }
}

/**
 * 获取申请类型的中文标签
 * @param type - 申请类型字符串
 * @returns 中文类型名称
 */
function getApplicationTypeLabel(type: string): string {
  switch (type) {
    case 'REGISTER': return '正式注册'
    case 'TRIAL': return '试用申请'
    default: return type
  }
}

/** 通知类型到中文标签的映射 */
const NOTIFICATION_TYPE_LABELS: Record<string, string> = {
  SYSTEM: '系统通知',
  AUDIT_RESULT: '审核结果',
  EXPIRY_WARNING: '到期提醒',
  SUBSCRIPTION: '订阅相关',
}

/** 通知类型到 Badge 样式的映射 */
const NOTIFICATION_TYPE_CLASSES: Record<string, string> = {
  SYSTEM: 'bg-blue-500/10 text-blue-600 border-blue-500/20 dark:text-blue-400',
  AUDIT_RESULT: 'bg-purple-500/10 text-purple-600 border-purple-500/20 dark:text-purple-400',
  EXPIRY_WARNING: 'bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400',
  SUBSCRIPTION: 'bg-green-500/10 text-green-600 border-green-500/20 dark:text-green-400',
}

/* ========== 统计卡片组件 ========== */

/** 统计卡片属性 */
interface StatCardProps {
  /** 图标组件 */
  icon: React.ComponentType<{ className?: string }>
  /** 统计数值 */
  count: number | null
  /** 标签描述 */
  label: string
  /** "查看全部" 链接地址，为空时不显示链接 */
  href?: string
  /** 图标颜色类名 */
  iconColor?: string
}

/**
 * 统计卡片组件
 *
 * 展示单个统计数据，包括图标、数值、标签，
 * 可选展示跳转链接。加载期间显示占位符。
 */
function StatCard({ icon: Icon, count, label, href, iconColor = 'text-muted-foreground' }: StatCardProps) {
  return (
    <Card>
      <CardContent className="flex items-center gap-4">
        {/* 图标容器 */}
        <div className={`flex size-12 shrink-0 items-center justify-center rounded-lg bg-muted ${iconColor}`}>
          <Icon className="size-6" />
        </div>
        {/* 数值与标签 */}
        <div className="min-w-0 flex-1">
          <p className="text-2xl font-bold tracking-tight">
            {count !== null ? count.toLocaleString() : '—'}
          </p>
          <p className="text-sm text-muted-foreground">{label}</p>
        </div>
        {/* 跳转链接 */}
        {href && (
          <Button variant="ghost" size="icon-sm" asChild>
            <Link to={href} aria-label={`查看${label}`}>
              <ArrowRight className="size-4" />
            </Link>
          </Button>
        )}
      </CardContent>
    </Card>
  )
}

/* ========== SUPER_ADMIN Dashboard ========== */

/** 超管统计数据类型 */
interface SuperAdminStats {
  /** 租户总数 */
  tenantCount: number
  /** 申请总数（近似待审数） */
  applicationCount: number
  /** 平台订阅总数 */
  subscriptionCount: number
  /** 审计日志总数 */
  auditLogCount: number
  /** 最近 5 条申请 */
  recentApplications: TenantApplication[]
}

/**
 * 超级管理员 Dashboard
 *
 * 展示平台级统计数据及最近入驻申请列表。
 * 通过 Promise.all 并行获取所有数据，减少加载时间。
 */
function SuperAdminDashboard() {
  const { data: stats, loading } = useQuery(async () => {
    const [tenants, apps, subs, logs] = await Promise.all([
      get<PageResult<Tenant>>('/api/v1/tenants?page=1&size=1'),
      get<PageResult<TenantApplication>>('/api/v1/applications?page=1&size=5'),
      get<PageResult<Subscription>>('/api/v1/subscriptions?page=1&size=1'),
      get<PageResult<AuditLog>>('/api/v1/audit-logs?page=1&size=1'),
    ])
    return {
      tenantCount: tenants.total,
      applicationCount: apps.total,
      subscriptionCount: subs.total,
      auditLogCount: logs.total,
      recentApplications: apps.list,
    } as SuperAdminStats
  }, [])

  if (loading || !stats) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">平台概览</h1>
        <p className="text-muted-foreground">欢迎回来，以下是平台运营概况。</p>
      </div>

      {/* 统计卡片网格 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={Building2}
          count={stats.tenantCount}
          label="租户总数"
          href="/console/tenants"
          iconColor="text-blue-600 dark:text-blue-400"
        />
        <StatCard
          icon={FileCheck2}
          count={stats.applicationCount}
          label="申请待审"
          href="/console/applications"
          iconColor="text-amber-600 dark:text-amber-400"
        />
        <StatCard
          icon={CreditCard}
          count={stats.subscriptionCount}
          label="平台订阅"
          href="/console/subscriptions"
          iconColor="text-green-600 dark:text-green-400"
        />
        <StatCard
          icon={ScrollText}
          count={stats.auditLogCount}
          label="审计日志"
          href="/console/audit-logs"
          iconColor="text-purple-600 dark:text-purple-400"
        />
      </div>

      <Separator />

      {/* 最近申请列表 */}
      <Card>
        <CardHeader>
          <CardTitle>最近申请</CardTitle>
          <CardDescription>最新的租户入驻申请记录</CardDescription>
        </CardHeader>
        <CardContent>
          {stats.recentApplications.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">暂无申请记录</p>
          ) : (
            <div className="space-y-3">
              {stats.recentApplications.map((app) => (
                <div
                  key={app.id}
                  className="flex items-center justify-between rounded-lg border p-3"
                >
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{app.companyName}</span>
                      <Badge variant="outline" className="text-xs">
                        {getApplicationTypeLabel(app.applicationType)}
                      </Badge>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {app.contactName} · {formatDateTime(app.createdAt)}
                    </p>
                  </div>
                  <Badge className={getApplicationStatusClass(app.status)}>
                    {getApplicationStatusLabel(app.status)}
                  </Badge>
                </div>
              ))}
            </div>
          )}
          {/* 底部查看全部链接 */}
          <div className="mt-4 flex justify-center">
            <Button variant="outline" size="sm" asChild>
              <Link to="/console/applications">
                查看全部申请
                <ArrowRight className="ml-1 size-4" />
              </Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

/* ========== TENANT_OWNER / TENANT_ADMIN Dashboard ========== */

/** 租户管理员统计数据类型 */
interface TenantAdminStats {
  /** 租户用户总数 */
  userCount: number
  /** 订阅总数 */
  subscriptionCount: number
  /** 操作日志总数 */
  auditLogCount: number
  /** 最近 5 条通知 */
  recentNotifications: Notification[]
}

/**
 * 租户管理员 Dashboard
 *
 * 展示租户级统计数据及最近通知列表。
 * 适用于 TENANT_OWNER 和 TENANT_ADMIN 角色。
 */
function TenantAdminDashboard() {
  const { user } = useAuth()
  const isOwner = user?.userType === 'TENANT_OWNER'

  const { data: stats, loading } = useQuery(async () => {
    const [users, subs, logs, notifications] = await Promise.all([
      get<PageResult<User>>('/api/v1/users?page=1&size=1'),
      get<PageResult<Subscription>>('/api/v1/my-subscriptions?page=1&size=1'),
      get<PageResult<AuditLog>>('/api/v1/my-audit-logs?page=1&size=1'),
      get<PageResult<Notification>>('/api/v1/notifications?page=1&size=5'),
    ])
    return {
      userCount: users.total,
      subscriptionCount: subs.total,
      auditLogCount: logs.total,
      recentNotifications: notifications.list,
    } as TenantAdminStats
  }, [])

  if (loading || !stats) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">
          {isOwner ? '租户管理' : '租户概览'}
        </h1>
        <p className="text-muted-foreground">
          {user?.tenantName ?? '—'} — 以下是租户运营概况。
        </p>
      </div>

      {/* 统计卡片网格 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <StatCard
          icon={Users}
          count={stats.userCount}
          label="租户用户"
          href="/console/users"
          iconColor="text-blue-600 dark:text-blue-400"
        />
        <StatCard
          icon={CreditCard}
          count={stats.subscriptionCount}
          label="我的订阅"
          href="/console/my-subscriptions"
          iconColor="text-green-600 dark:text-green-400"
        />
        <StatCard
          icon={ScrollText}
          count={stats.auditLogCount}
          label="操作日志"
          href="/console/my-audit-logs"
          iconColor="text-purple-600 dark:text-purple-400"
        />
      </div>

      <Separator />

      {/* 最近通知列表 */}
      <Card>
        <CardHeader>
          <CardTitle>最近通知</CardTitle>
          <CardDescription>最新 5 条通知消息</CardDescription>
        </CardHeader>
        <CardContent>
          {stats.recentNotifications.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">暂无通知</p>
          ) : (
            <div className="space-y-3">
              {stats.recentNotifications.map((n) => (
                <div
                  key={n.id}
                  className={`flex items-start gap-3 rounded-lg border p-3 ${
                    !n.isRead ? 'border-l-4 border-l-blue-500' : ''
                  }`}
                >
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">{n.title}</span>
                      <Badge
                        variant="outline"
                        className={NOTIFICATION_TYPE_CLASSES[n.type] ?? ''}
                      >
                        {NOTIFICATION_TYPE_LABELS[n.type] ?? n.type}
                      </Badge>
                      {/* 未读标记 */}
                      {!n.isRead && (
                        <span className="size-2 shrink-0 rounded-full bg-blue-500" />
                      )}
                    </div>
                    <p className="line-clamp-2 text-xs text-muted-foreground">{n.content}</p>
                    <p className="text-xs text-muted-foreground">{formatDateTime(n.createdAt)}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
          {/* 底部查看全部链接 */}
          <div className="mt-4 flex justify-center">
            <Button variant="outline" size="sm" asChild>
              <Link to="/console/notifications">
                查看全部通知
                <ArrowRight className="ml-1 size-4" />
              </Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

/* ========== TENANT_USER Dashboard ========== */

/** 租户用户统计数据类型 */
interface TenantUserStats {
  /** 订阅总数 */
  subscriptionCount: number
  /** 未读通知数 */
  unreadCount: number
}

/**
 * 租户普通用户 Dashboard
 *
 * 展示个人级统计数据及欢迎卡片。
 * 适用于 TENANT_USER 角色。
 */
function TenantUserDashboard() {
  const { user } = useAuth()

  const { data: stats, loading } = useQuery(async () => {
    const [subs, unreadCount] = await Promise.all([
      get<PageResult<Subscription>>('/api/v1/my-subscriptions?page=1&size=1'),
      get<number>('/api/v1/notifications/unread-count'),
    ])
    return {
      subscriptionCount: subs.total,
      unreadCount,
    } as TenantUserStats
  }, [])

  if (loading || !stats) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight">我的概览</h1>
        <p className="text-muted-foreground">欢迎回来，以下是您的账户概况。</p>
      </div>

      {/* 统计卡片网格 */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <StatCard
          icon={CreditCard}
          count={stats.subscriptionCount}
          label="我的订阅"
          href="/console/my-subscriptions"
          iconColor="text-green-600 dark:text-green-400"
        />
        <StatCard
          icon={Bell}
          count={stats.unreadCount}
          label="未读通知"
          href="/console/notifications"
          iconColor="text-amber-600 dark:text-amber-400"
        />
      </div>

      <Separator />

      {/* 欢迎卡片 */}
      <Card>
        <CardHeader>
          <CardTitle>欢迎使用 Converge 平台</CardTitle>
          <CardDescription>以下是您的账户信息</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-muted-foreground">用户名</span>
              <span className="font-medium">{user?.username ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-muted-foreground">所属租户</span>
              <span className="font-medium">{user?.tenantName ?? '—'}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-muted-foreground">邮箱</span>
              <span className="font-medium">{user?.email ?? '未设置'}</span>
            </div>
            <div className="flex items-center justify-between rounded-lg border p-3">
              <span className="text-sm text-muted-foreground">手机号</span>
              <span className="font-medium">{user?.phone ?? '未设置'}</span>
            </div>
          </div>
          {/* 快捷操作 */}
          <div className="mt-4 flex gap-2">
            <Button variant="outline" size="sm" asChild>
              <Link to="/console/profile">
                个人资料
                <ArrowRight className="ml-1 size-4" />
              </Link>
            </Button>
            <Button variant="outline" size="sm" asChild>
              <Link to="/console/notifications">
                通知中心
                <ArrowRight className="ml-1 size-4" />
              </Link>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}

/* ========== 主页面组件 ========== */

/**
 * Dashboard 概览页入口组件
 *
 * 根据当前用户的 userType 自动选择对应的仪表盘布局：
 * - SUPER_ADMIN → SuperAdminDashboard
 * - TENANT_OWNER / TENANT_ADMIN → TenantAdminDashboard
 * - TENANT_USER → TenantUserDashboard
 *
 * 加载中或用户信息未就绪时显示骨架屏。
 */
export default function DashboardPage() {
  const { user, isLoading } = useAuth()

  // 认证状态加载中，显示骨架屏
  if (isLoading || !user) {
    return <PageSkeleton />
  }

  // 根据用户类型分发到不同的仪表盘
  switch (user.userType) {
    case 'SUPER_ADMIN':
      return <SuperAdminDashboard />
    case 'TENANT_OWNER':
    case 'TENANT_ADMIN':
      return <TenantAdminDashboard />
    case 'TENANT_USER':
      return <TenantUserDashboard />
    default:
      // 未知角色显示默认提示
      return (
        <div className="flex items-center justify-center py-20">
          <p className="text-muted-foreground">未知用户类型，无法加载仪表盘。</p>
        </div>
      )
  }
}
