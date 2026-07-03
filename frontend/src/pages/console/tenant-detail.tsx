/**
 * 租户详情页面
 *
 * 超管控制台 — 租户详情视图。
 * 功能包括：
 * - 展示租户基本信息（编码、名称、描述、状态、过期时间等）
 * - 启用/禁用/删除操作（含二次确认）
 * - 返回列表页
 */

import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { toast } from 'sonner'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, post, del } from '@/lib/api-client'
import type { Tenant } from '@/lib/types'
import { PageSkeleton } from '@/components/loading-skeleton'
import ConfirmDialog from '@/components/confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

/**
 * 根据租户状态获取对应的 Badge 样式类名
 *
 * @param status - 租户状态字符串
 * @returns Tailwind CSS 类名
 */
function getStatusClassName(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    case 'TRIAL':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200'
    case 'PENDING':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
    case 'DISABLED':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
    case 'EXPIRED':
      return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
    case 'DELETED':
      return 'bg-red-200 text-red-900 dark:bg-red-950 dark:text-red-300'
    default:
      return ''
  }
}

/**
 * 根据租户状态获取中文标签
 *
 * @param status - 租户状态字符串
 * @returns 中文状态名称
 */
function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    ACTIVE: '正常',
    TRIAL: '试用',
    PENDING: '待审核',
    DISABLED: '已禁用',
    EXPIRED: '已过期',
    DELETED: '已删除',
  }
  return map[status] ?? status
}

/**
 * 格式化日期时间字符串
 *
 * @param dateStr - ISO 日期时间字符串
 * @param fallback - 值为 null 时的兜底文本
 * @returns 格式化后的日期时间字符串
 */
function formatDateTime(dateStr: string | null, fallback = '—'): string {
  if (!dateStr) return fallback
  return new Date(dateStr).toLocaleString('zh-CN')
}

/**
 * 租户详情页面组件
 *
 * 通过路由参数获取租户 ID，加载并展示租户详细信息。
 * 提供启用、禁用、删除等管理操作。
 */
export default function TenantDetailPage() {
  /** 从路由参数获取租户 ID */
  const { id } = useParams<{ id: string }>()
  /** 页面导航 */
  const navigate = useNavigate()

  /** 禁用确认对话框显示状态 */
  const [disableDialogOpen, setDisableDialogOpen] = useState(false)
  /** 删除确认对话框显示状态 */
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)

  /** 获取租户详情数据 */
  const { data: tenant, loading, refetch } = useQuery<Tenant>(
    () => get<Tenant>(`/api/v1/tenants/${id}`),
    [id],
  )

  /** 启用租户 */
  const { mutate: enableTenant, loading: enabling } = useMutation<void, number>(
    (tenantId) => post<void>(`/api/v1/tenants/${tenantId}/enable`),
  )

  /** 禁用租户 */
  const { mutate: disableTenant, loading: disabling } = useMutation<void, number>(
    (tenantId) => post<void>(`/api/v1/tenants/${tenantId}/disable`),
  )

  /** 删除租户 */
  const { mutate: deleteTenant, loading: deleting } = useMutation<void, number>(
    (tenantId) => del<void>(`/api/v1/tenants/${tenantId}`),
  )

  /**
   * 处理启用操作
   */
  const handleEnable = async () => {
    if (!tenant) return
    const result = await enableTenant(tenant.id)
    if (result !== null) {
      toast.success('已启用该租户')
      refetch()
    } else {
      toast.error('启用租户失败')
    }
  }

  /**
   * 处理禁用确认
   */
  const handleDisableConfirm = async () => {
    if (!tenant) return
    const result = await disableTenant(tenant.id)
    if (result !== null) {
      toast.success('已禁用该租户')
      setDisableDialogOpen(false)
      refetch()
    } else {
      toast.error('禁用租户失败')
    }
  }

  /**
   * 处理删除确认
   * 删除成功后跳转回列表页
   */
  const handleDeleteConfirm = async () => {
    if (!tenant) return
    const result = await deleteTenant(tenant.id)
    if (result !== null) {
      toast.success('已删除该租户')
      setDeleteDialogOpen(false)
      navigate('/console/tenants')
    } else {
      toast.error('删除租户失败')
    }
  }

  /** 加载中展示骨架屏 */
  if (loading || !tenant) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 顶部导航和操作按钮 */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => navigate('/console/tenants')}
          >
            <ArrowLeft className="size-5" />
          </Button>
          <h1 className="text-2xl font-bold">租户详情</h1>
        </div>

        {/* 操作按钮组 */}
        <div className="flex items-center gap-2">
          {/* 启用按钮：非 ACTIVE 且非 DELETED 状态可见 */}
          {tenant.status !== 'ACTIVE' && tenant.status !== 'DELETED' && (
            <Button
              variant="outline"
              disabled={enabling}
              onClick={() => { void handleEnable() }}
            >
              启用
            </Button>
          )}
          {/* 禁用按钮：ACTIVE 或 TRIAL 状态可见 */}
          {(tenant.status === 'ACTIVE' || tenant.status === 'TRIAL') && (
            <Button
              variant="outline"
              disabled={disabling}
              onClick={() => setDisableDialogOpen(true)}
            >
              禁用
            </Button>
          )}
          {/* 删除按钮：DELETED 状态不显示 */}
          {tenant.status !== 'DELETED' && (
            <Button
              variant="destructive"
              disabled={deleting}
              onClick={() => setDeleteDialogOpen(true)}
            >
              删除
            </Button>
          )}
        </div>
      </div>

      {/* 基本信息卡片 */}
      <Card>
        <CardHeader>
          <CardTitle>基本信息</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2">
            {/* 租户编码 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">租户编码</dt>
              <dd className="font-medium">{tenant.code}</dd>
            </div>

            {/* 租户名称 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">租户名称</dt>
              <dd className="font-medium">{tenant.name}</dd>
            </div>

            {/* 租户描述 */}
            <div className="space-y-1 sm:col-span-2">
              <dt className="text-sm text-muted-foreground">描述</dt>
              <dd className="font-medium">{tenant.description || '—'}</dd>
            </div>

            <Separator className="sm:col-span-2" />

            {/* 状态 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">状态</dt>
              <dd>
                <Badge className={getStatusClassName(tenant.status)}>
                  {getStatusLabel(tenant.status)}
                </Badge>
              </dd>
            </div>

            {/* 是否已试用 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">已使用试用</dt>
              <dd className="font-medium">{tenant.trialUsed ? '是' : '否'}</dd>
            </div>

            {/* 过期时间 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">过期时间</dt>
              <dd className="font-medium">
                {formatDateTime(tenant.expiredAt, '永不过期')}
              </dd>
            </div>

            <Separator className="sm:col-span-2" />

            {/* 创建时间 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">创建时间</dt>
              <dd className="font-medium">{formatDateTime(tenant.createdAt)}</dd>
            </div>

            {/* 更新时间 */}
            <div className="space-y-1">
              <dt className="text-sm text-muted-foreground">更新时间</dt>
              <dd className="font-medium">{formatDateTime(tenant.updatedAt)}</dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      {/* 禁用确认对话框 */}
      <ConfirmDialog
        open={disableDialogOpen}
        onOpenChange={setDisableDialogOpen}
        title="确认禁用租户"
        description={`确定要禁用租户「${tenant.name}」吗？禁用后该租户下所有用户将无法使用系统。`}
        confirmText="禁用"
        destructive
        onConfirm={handleDisableConfirm}
        loading={disabling}
      />

      {/* 删除确认对话框 */}
      <ConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        title="确认删除租户"
        description={`确定要删除租户「${tenant.name}」吗？此操作不可撤销，该租户下所有数据将被永久删除。`}
        confirmText="删除"
        destructive
        onConfirm={handleDeleteConfirm}
        loading={deleting}
      />
    </div>
  )
}
