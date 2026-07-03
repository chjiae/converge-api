/**
 * 订阅管理页面
 *
 * 超管控制台 — 全局订阅管理。
 * 功能包括：
 * - 分页展示所有租户的订阅记录
 * - 标记 PENDING 状态的订阅为已支付
 * - 按套餐类型和状态展示 Badge 标签
 */

import { useState, useMemo } from 'react'
import { toast } from 'sonner'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, put } from '@/lib/api-client'
import type { Subscription, PageResult } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import ConfirmDialog from '@/components/confirm-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

/**
 * 套餐类型对应的 Badge 样式
 * MONTHLY = 蓝色，QUARTERLY = 紫色，YEARLY = 绿色
 */
function getPlanTypeClassName(planType: string): string {
  switch (planType) {
    case 'MONTHLY':
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200'
    case 'QUARTERLY':
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900 dark:text-purple-200'
    case 'YEARLY':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    default:
      return ''
  }
}

/**
 * 套餐类型中文标签
 * @param planType - 套餐类型字符串
 * @returns 中文套餐名称
 */
function getPlanTypeLabel(planType: string): string {
  switch (planType) {
    case 'MONTHLY': return '月度'
    case 'QUARTERLY': return '季度'
    case 'YEARLY': return '年度'
    default: return planType
  }
}

/**
 * 订阅状态对应的 Badge 样式
 * PENDING = 黄色，ACTIVE = 绿色，EXPIRED = 红色，CANCELLED = 灰色
 */
function getStatusClassName(status: string): string {
  switch (status) {
    case 'PENDING':
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200'
    case 'ACTIVE':
      return 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    case 'EXPIRED':
      return 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
    case 'CANCELLED':
      return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
    default:
      return ''
  }
}

/**
 * 订阅状态中文标签
 * @param status - 订阅状态字符串
 * @returns 中文状态名称
 */
function getStatusLabel(status: string): string {
  switch (status) {
    case 'PENDING': return '待支付'
    case 'ACTIVE': return '已激活'
    case 'EXPIRED': return '已过期'
    case 'CANCELLED': return '已取消'
    default: return status
  }
}

/**
 * 支付方式中文标签
 * @param method - 支付方式字符串
 * @returns 中文支付方式名称
 */
function getPaymentMethodLabel(method: string | null): string {
  switch (method) {
    case 'OFFLINE': return '线下支付'
    case 'ALIPAY': return '支付宝'
    case 'WECHAT': return '微信支付'
    case 'CARD_KEY': return '卡密兑换'
    case null: return '—'
    default: return method
  }
}

/**
 * 格式化日期字符串
 * @param dateStr - ISO 日期字符串
 * @returns 格式化后的本地日期
 */
function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('zh-CN')
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
 * 格式化金额显示
 * @param amount - 金额数值（单位：元）
 * @returns 格式化后的金额字符串，如 ¥123.45
 */
function formatAmount(amount: number): string {
  return `¥${amount.toFixed(2)}`
}

/**
 * 订阅管理页面组件
 *
 * 仅超级管理员可访问，提供全局订阅的查看和支付确认功能。
 */
export default function SubscriptionListPage() {
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)

  /** 标记已支付确认对话框目标订阅 */
  const [payTarget, setPayTarget] = useState<Subscription | null>(null)

  /** 构建查询 URL，包含分页参数 */
  const queryUrl = useMemo(() => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    return `/api/v1/subscriptions?${params.toString()}`
  }, [page, size])

  /** 获取订阅列表数据 */
  const { data, loading, refetch } = useQuery<PageResult<Subscription>>(
    () => get<PageResult<Subscription>>(queryUrl),
    [queryUrl],
  )

  /** 标记订阅为已支付 */
  const { mutate: markPaid, loading: paying } = useMutation<Subscription, number>(
    (id) => put<Subscription>(`/api/v1/subscriptions/${id}/pay`),
  )

  /**
   * 处理标记已支付操作
   * 提交后刷新列表并关闭对话框
   */
  const handlePayConfirm = async () => {
    if (!payTarget) return
    const result = await markPaid(payTarget.id)
    if (result !== null) {
      toast.success(`已将订阅 #${payTarget.id} 标记为已支付`)
      setPayTarget(null)
      refetch()
    } else {
      toast.error('标记支付失败')
    }
  }

  /** 表格列定义 */
  const columns: Column<Subscription & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'tenantId',
      header: '租户 ID',
      render: (item) => <span className="font-mono text-sm">{item.tenantId}</span>,
    },
    {
      key: 'planType',
      header: '套餐类型',
      render: (item) => (
        <Badge className={getPlanTypeClassName(item.planType)}>
          {getPlanTypeLabel(item.planType)}
        </Badge>
      ),
    },
    {
      key: 'amount',
      header: '金额',
      render: (item) => <span className="font-medium">{formatAmount(item.amount)}</span>,
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
      key: 'paymentMethod',
      header: '支付方式',
      render: (item) => getPaymentMethodLabel(item.paymentMethod),
    },
    {
      key: 'dateRange',
      header: '订阅周期',
      render: (item) => (
        <span className="text-xs">
          {formatDate(item.startDate)} ~ {formatDate(item.endDate)}
        </span>
      ),
    },
    {
      key: 'createdAt',
      header: '创建时间',
      render: (item) => formatDateTime(item.createdAt),
    },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex items-center justify-end gap-2">
          {/* 仅 PENDING 状态显示"标记已支付"按钮 */}
          {item.status === 'PENDING' && (
            <Button
              variant="default"
              size="sm"
              disabled={paying}
              onClick={() => setPayTarget(item as Subscription)}
            >
              标记已支付
            </Button>
          )}
        </div>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [paying])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">订阅管理</h1>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        data={(data?.list ?? []) as (Subscription & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无订阅记录"
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

      {/* 标记已支付确认对话框 */}
      <ConfirmDialog
        open={payTarget !== null}
        onOpenChange={(open) => { if (!open) setPayTarget(null) }}
        title="确认标记已支付"
        description={`确定将租户 ${payTarget?.tenantId ?? ''} 的${getPlanTypeLabel(payTarget?.planType ?? '')}订阅（${formatAmount(payTarget?.amount ?? 0)}）标记为已支付吗？`}
        confirmText="确认支付"
        onConfirm={handlePayConfirm}
        loading={paying}
      />
    </div>
  )
}
