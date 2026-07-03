/**
 * 租户列表页面
 *
 * 超管控制台 — 租户管理主页面。
 * 功能包括：
 * - 分页展示所有租户
 * - 按状态筛选
 * - 创建新租户
 * - 启用/禁用/删除租户
 */

import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { toast } from 'sonner'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, post, del } from '@/lib/api-client'
import type { Tenant, PageResult } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import ConfirmDialog from '@/components/confirm-dialog'
import TenantFormDialog from '@/pages/console/tenant-form-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/** 租户状态列表（用于筛选下拉和标签展示） */
const TENANT_STATUSES = [
  { value: 'ACTIVE', label: '正常' },
  { value: 'TRIAL', label: '试用' },
  { value: 'PENDING', label: '待审核' },
  { value: 'DISABLED', label: '已禁用' },
  { value: 'EXPIRED', label: '已过期' },
  { value: 'DELETED', label: '已删除' },
] as const

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
  return TENANT_STATUSES.find((s) => s.value === status)?.label ?? status
}

/**
 * 格式化日期字符串
 *
 * @param dateStr - ISO 日期时间字符串，null 表示永不过期
 * @returns 格式化后的日期字符串
 */
function formatDate(dateStr: string | null): string {
  if (!dateStr) return '永不过期'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

/**
 * 租户列表页面组件
 *
 * 仅超级管理员可访问，提供租户的完整 CRUD 管理功能。
 */
export default function TenantListPage() {
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)
  /** 状态筛选值，'ALL' 表示全部 */
  const [statusFilter, setStatusFilter] = useState('ALL')

  /** 创建租户对话框显示状态 */
  const [createDialogOpen, setCreateDialogOpen] = useState(false)

  /** 禁用确认对话框 */
  const [disableTarget, setDisableTarget] = useState<Tenant | null>(null)
  /** 删除确认对话框 */
  const [deleteTarget, setDeleteTarget] = useState<Tenant | null>(null)

  /** 构建查询 URL，包含分页和可选的状态筛选参数 */
  const queryUrl = useMemo(() => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    if (statusFilter !== 'ALL') {
      params.set('status', statusFilter)
    }
    return `/api/v1/tenants?${params.toString()}`
  }, [page, size, statusFilter])

  /** 获取租户列表数据 */
  const { data, loading, refetch } = useQuery<PageResult<Tenant>>(
    () => get<PageResult<Tenant>>(queryUrl),
    [queryUrl],
  )

  /** 启用租户 */
  const { mutate: enableTenant, loading: enabling } = useMutation<void, number>(
    (id) => post<void>(`/api/v1/tenants/${id}/enable`),
  )

  /** 禁用租户 */
  const { mutate: disableTenant, loading: disabling } = useMutation<void, number>(
    (id) => post<void>(`/api/v1/tenants/${id}/disable`),
  )

  /** 删除租户 */
  const { mutate: deleteTenant, loading: deleting } = useMutation<void, number>(
    (id) => del<void>(`/api/v1/tenants/${id}`),
  )

  /**
   * 处理启用租户操作
   * @param tenant - 目标租户
   */
  const handleEnable = async (tenant: Tenant) => {
    const result = await enableTenant(tenant.id)
    if (result !== null) {
      toast.success(`已启用租户「${tenant.name}」`)
      refetch()
    } else {
      toast.error('启用租户失败')
    }
  }

  /**
   * 处理禁用租户操作（需二次确认）
   * @param tenant - 目标租户
   */
  const handleDisableConfirm = async () => {
    if (!disableTarget) return
    const result = await disableTenant(disableTarget.id)
    if (result !== null) {
      toast.success(`已禁用租户「${disableTarget.name}」`)
      setDisableTarget(null)
      refetch()
    } else {
      toast.error('禁用租户失败')
    }
  }

  /**
   * 处理删除租户操作（需二次确认）
   * @param tenant - 目标租户
   */
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return
    const result = await deleteTenant(deleteTarget.id)
    if (result !== null) {
      toast.success(`已删除租户「${deleteTarget.name}」`)
      setDeleteTarget(null)
      refetch()
    } else {
      toast.error('删除租户失败')
    }
  }

  /**
   * 处理状态筛选变更
   * 切换筛选条件时自动回到第一页
   */
  const handleStatusFilterChange = (value: string) => {
    setStatusFilter(value)
    setPage(1)
  }

  /** 表格列定义 */
  const columns: Column<Tenant & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'code',
      header: '租户编码',
      render: (item) => (
        <Link
          to={`/console/tenants/${item.id}`}
          className="font-medium text-primary hover:underline"
        >
          {item.code}
        </Link>
      ),
    },
    {
      key: 'name',
      header: '租户名称',
    },
    {
      key: 'status',
      header: '状态',
      render: (item) => (
        <Badge className={getStatusClassName(item.status)}>
          {getStatusLabel(item.status)}
        </Badge>
      ),
    },
    {
      key: 'trialUsed',
      header: '已试用',
      render: (item) => (item.trialUsed ? '是' : '否'),
    },
    {
      key: 'expiredAt',
      header: '过期时间',
      render: (item) => formatDate(item.expiredAt),
    },
    {
      key: 'createdAt',
      header: '创建时间',
      render: (item) => formatDate(item.createdAt),
    },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex items-center justify-end gap-2">
          {/* 启用按钮：仅在非 ACTIVE 状态显示 */}
          {item.status !== 'ACTIVE' && item.status !== 'DELETED' && (
            <Button
              variant="outline"
              size="sm"
              disabled={enabling}
              onClick={() => { void handleEnable(item) }}
            >
              启用
            </Button>
          )}
          {/* 禁用按钮：仅在 ACTIVE 或 TRIAL 状态显示 */}
          {(item.status === 'ACTIVE' || item.status === 'TRIAL') && (
            <Button
              variant="outline"
              size="sm"
              disabled={disabling}
              onClick={() => setDisableTarget(item as Tenant)}
            >
              禁用
            </Button>
          )}
          {/* 删除按钮：DELETED 状态不显示 */}
          {item.status !== 'DELETED' && (
            <Button
              variant="destructive"
              size="sm"
              disabled={deleting}
              onClick={() => setDeleteTarget(item as Tenant)}
            >
              删除
            </Button>
          )}
        </div>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [enabling, disabling, deleting])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题和操作按钮 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">租户管理</h1>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="size-4" />
          创建租户
        </Button>
      </div>

      {/* 筛选区域 */}
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">状态筛选：</span>
          <Select value={statusFilter} onValueChange={handleStatusFilterChange}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">全部</SelectItem>
              {TENANT_STATUSES.map((s) => (
                <SelectItem key={s.value} value={s.value}>
                  {s.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        data={(data?.list ?? []) as (Tenant & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无租户数据"
      />

      {/* 分页组件 */}
      {data && data.total > 0 && (
        <Pagination
          page={page}
          size={size}
          total={data.total}
          onPageChange={setPage}
          onSizeChange={(newSize) => {
            setSize(newSize)
            setPage(1)
          }}
        />
      )}

      {/* 创建租户对话框 */}
      <TenantFormDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        onSuccess={refetch}
      />

      {/* 禁用确认对话框 */}
      <ConfirmDialog
        open={disableTarget !== null}
        onOpenChange={(open) => { if (!open) setDisableTarget(null) }}
        title="确认禁用租户"
        description={`确定要禁用租户「${disableTarget?.name ?? ''}」吗？禁用后该租户下所有用户将无法使用系统。`}
        confirmText="禁用"
        destructive
        onConfirm={handleDisableConfirm}
        loading={disabling}
      />

      {/* 删除确认对话框 */}
      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}
        title="确认删除租户"
        description={`确定要删除租户「${deleteTarget?.name ?? ''}」吗？此操作不可撤销，该租户下所有数据将被永久删除。`}
        confirmText="删除"
        destructive
        onConfirm={handleDeleteConfirm}
        loading={deleting}
      />
    </div>
  )
}
