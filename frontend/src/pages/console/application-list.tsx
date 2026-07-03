/**
 * 申请审核页面
 *
 * 超管控制台 — 租户入驻申请审核管理。
 * 功能包括：
 * - 分页展示所有入驻申请
 * - 通过 PENDING 状态的申请
 * - 拒绝 PENDING 状态的申请（需填写拒绝原因）
 */

import { useState, useMemo } from 'react'
import { toast } from 'sonner'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, post } from '@/lib/api-client'
import type { TenantApplication, PageResult } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import ConfirmDialog from '@/components/confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2 } from 'lucide-react'

/**
 * 申请类型对应的 Badge 样式
 * REGISTER = 蓝色，TRIAL = 绿色
 */
function getApplicationTypeClassName(type: string): string {
  switch (type) {
    case 'REGISTER':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200'
    case 'TRIAL':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    default:
      return ''
  }
}

/**
 * 申请类型中文标签
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

/**
 * 审核状态对应的 Badge 样式
 * PENDING = 黄色，APPROVED = 绿色，REJECTED = 红色
 */
function getStatusClassName(status: string): string {
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
 * 审核状态中文标签
 * @param status - 审核状态字符串
 * @returns 中文状态名称
 */
function getStatusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '待审核'
    case 'APPROVED': return '已通过'
    case 'REJECTED': return '已拒绝'
    default: return status
  }
}

/**
 * 格式化日期时间字符串
 * @param dateStr - ISO 日期时间字符串
 * @returns 格式化后的本地日期时间
 */
function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString('zh-CN')
}

/**
 * 申请审核页面组件
 *
 * 仅超级管理员可访问，提供入驻申请的审核和分页管理功能。
 */
export default function ApplicationListPage() {
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)

  /** 通过确认对话框目标申请 */
  const [approveTarget, setApproveTarget] = useState<TenantApplication | null>(null)
  /** 拒绝对话框是否打开 */
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false)
  /** 拒绝对话框目标申请 */
  const [rejectTarget, setRejectTarget] = useState<TenantApplication | null>(null)
  /** 拒绝原因输入值 */
  const [rejectReason, setRejectReason] = useState('')

  /** 构建查询 URL，包含分页参数 */
  const queryUrl = useMemo(() => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    return `/api/v1/applications?${params.toString()}`
  }, [page, size])

  /** 获取申请列表数据 */
  const { data, loading, refetch } = useQuery<PageResult<TenantApplication>>(
    () => get<PageResult<TenantApplication>>(queryUrl),
    [queryUrl],
  )

  /** 通过申请 */
  const { mutate: approveApplication, loading: approving } = useMutation<TenantApplication, number>(
    (id) => post<TenantApplication>(`/api/v1/applications/${id}/approve`),
  )

  /** 拒绝申请 */
  const { mutate: rejectApplication, loading: rejecting } = useMutation<TenantApplication, { id: number; rejectReason: string }>(
    ({ id, rejectReason: reason }) => post<TenantApplication>(`/api/v1/applications/${id}/reject`, { rejectReason: reason }),
  )

  /**
   * 处理通过申请操作
   * @param application - 目标申请
   */
  const handleApproveConfirm = async () => {
    if (!approveTarget) return
    const result = await approveApplication(approveTarget.id)
    if (result !== null) {
      toast.success(`已通过「${approveTarget.companyName}」的入驻申请`)
      setApproveTarget(null)
      refetch()
    } else {
      toast.error('通过申请失败')
    }
  }

  /**
   * 打开拒绝对话框
   * @param application - 目标申请
   */
  const handleOpenReject = (application: TenantApplication) => {
    setRejectTarget(application)
    setRejectReason('')
    setRejectDialogOpen(true)
  }

  /**
   * 处理拒绝申请操作
   * 需要填写拒绝原因后提交
   */
  const handleRejectConfirm = async () => {
    if (!rejectTarget) return
    if (!rejectReason.trim()) {
      toast.error('请填写拒绝原因')
      return
    }
    const result = await rejectApplication({
      id: rejectTarget.id,
      rejectReason: rejectReason.trim(),
    })
    if (result !== null) {
      toast.success(`已拒绝「${rejectTarget.companyName}」的入驻申请`)
      setRejectDialogOpen(false)
      setRejectTarget(null)
      refetch()
    } else {
      toast.error('拒绝申请失败')
    }
  }

  /** 表格列定义 */
  const columns: Column<TenantApplication & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'companyName',
      header: '公司名称',
      render: (item) => <span className="font-medium">{item.companyName}</span>,
    },
    {
      key: 'contactName',
      header: '联系人',
    },
    {
      key: 'contactEmail',
      header: '联系邮箱',
    },
    {
      key: 'applicationType',
      header: '申请类型',
      render: (item) => (
        <Badge className={getApplicationTypeClassName(item.applicationType)}>
          {getApplicationTypeLabel(item.applicationType)}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: '审核状态',
      render: (item) => (
        <Badge className={getStatusClassName(item.status)}>
          {getStatusLabel(item.status)}
        </Badge>
      ),
    },
    {
      key: 'createdAt',
      header: '申请时间',
      render: (item) => formatDateTime(item.createdAt),
    },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex items-center justify-end gap-2">
          {/* 仅 PENDING 状态显示操作按钮 */}
          {item.status === 'PENDING' && (
            <>
              <Button
                variant="default"
                size="sm"
                disabled={approving || rejecting}
                onClick={() => setApproveTarget(item as TenantApplication)}
              >
                通过
              </Button>
              <Button
                variant="destructive"
                size="sm"
                disabled={approving || rejecting}
                onClick={() => handleOpenReject(item as TenantApplication)}
              >
                拒绝
              </Button>
            </>
          )}
          {/* 已拒绝状态显示拒绝原因 */}
          {item.status === 'REJECTED' && item.rejectReason && (
            <span className="text-xs text-muted-foreground" title={item.rejectReason}>
              原因：{item.rejectReason}
            </span>
          )}
        </div>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [approving, rejecting])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">入驻申请审核</h1>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        data={(data?.list ?? []) as (TenantApplication & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无入驻申请"
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

      {/* 通过确认对话框 */}
      <ConfirmDialog
        open={approveTarget !== null}
        onOpenChange={(open) => { if (!open) setApproveTarget(null) }}
        title="确认通过申请"
        description={`确定要通过「${approveTarget?.companyName ?? ''}」的入驻申请吗？通过后将自动创建租户账号。`}
        confirmText="通过"
        onConfirm={handleApproveConfirm}
        loading={approving}
      />

      {/* 拒绝对话框（包含拒绝原因输入） */}
      <Dialog open={rejectDialogOpen} onOpenChange={setRejectDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>拒绝入驻申请</DialogTitle>
            <DialogDescription>
              拒绝「{rejectTarget?.companyName ?? ''}」的入驻申请，请填写拒绝原因。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <label
              htmlFor="reject-reason"
              className="text-sm font-medium"
            >
              拒绝原因
            </label>
            <textarea
              id="reject-reason"
              className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              rows={3}
              placeholder="请输入拒绝原因..."
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              disabled={rejecting}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRejectDialogOpen(false)}
              disabled={rejecting}
            >
              取消
            </Button>
            <Button
              variant="destructive"
              onClick={() => { void handleRejectConfirm() }}
              disabled={rejecting || !rejectReason.trim()}
            >
              {rejecting && <Loader2 className="size-4 animate-spin" />}
              拒绝
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
