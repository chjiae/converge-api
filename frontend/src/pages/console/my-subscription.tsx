/**
 * 我的订阅页面
 *
 * 租户用户查看自己的订阅列表和续费管理。
 * 功能包括：
 * - 分页展示当前用户的订阅记录
 * - 活跃订阅高亮卡片（展示套餐、到期日期、剩余天数）
 * - 续费按钮打开续费对话框
 * - 按套餐类型和状态展示 Badge 标签
 */

import { useState, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@/hooks/use-api'
import { get } from '@/lib/api-client'
import type { Subscription, PageResult } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Clock, CreditCard, CalendarDays } from 'lucide-react'

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
    case 'MONTHLY': return '月付'
    case 'QUARTERLY': return '季付'
    case 'YEARLY': return '年付'
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
 * 计算距到期日的剩余天数
 * @param endDate - 结束日期字符串
 * @returns 剩余天数（最小为 0）
 */
function daysRemaining(endDate: string): number {
  const end = new Date(endDate)
  const now = new Date()
  const diff = end.getTime() - now.getTime()
  return Math.max(0, Math.ceil(diff / (1000 * 60 * 60 * 24)))
}

/**
 * 我的订阅页面组件
 *
 * 所有已登录用户可访问，查看当前租户的订阅列表，
 * 并跳转到套餐购买页创建新的订阅订单。
 */
export default function MySubscriptionPage() {
  /** 路由跳转函数 */
  const navigate = useNavigate()
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)

  /** 构建查询 URL，包含分页参数 */
  const queryUrl = useMemo(() => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    return `/api/v1/my-subscriptions?${params.toString()}`
  }, [page, size])

  /** 获取订阅列表数据 */
  const { data, loading } = useQuery<PageResult<Subscription>>(
    () => get<PageResult<Subscription>>(queryUrl),
    [queryUrl],
  )

  /** 从订阅列表中查找当前活跃的订阅（ACTIVE 状态） */
  const activeSubscription = useMemo(() => {
    if (!data?.list) return null
    return data.list.find((s) => s.status === 'ACTIVE') ?? null
  }, [data?.list])

  /** 表格列定义 */
  const columns: Column<Subscription & Record<string, unknown>>[] = useMemo(() => [
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
  ], [])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题与续费按钮 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">我的订阅</h1>
        <Button onClick={() => navigate('/console/my-subscriptions/renew')}>
          <CreditCard className="size-4" />
          续费
        </Button>
      </div>

      {/* 活跃订阅高亮卡片 */}
      {activeSubscription && (
        <Card className="border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <CreditCard className="size-5 text-green-600 dark:text-green-400" />
              当前活跃订阅
            </CardTitle>
            <CardDescription>
              您的订阅正在生效中
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              {/* 套餐信息 */}
              <div className="flex items-center gap-3">
                <div className="rounded-lg bg-green-100 p-2 dark:bg-green-900">
                  <CreditCard className="size-4 text-green-600 dark:text-green-400" />
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">套餐类型</p>
                  <p className="font-medium">
                    {getPlanTypeLabel(activeSubscription.planType)}
                    <span className="ml-1 text-sm text-muted-foreground">
                      {formatAmount(activeSubscription.amount)}
                    </span>
                  </p>
                </div>
              </div>

              {/* 到期日期 */}
              <div className="flex items-center gap-3">
                <div className="rounded-lg bg-green-100 p-2 dark:bg-green-900">
                  <CalendarDays className="size-4 text-green-600 dark:text-green-400" />
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">到期日期</p>
                  <p className="font-medium">{formatDate(activeSubscription.endDate)}</p>
                </div>
              </div>

              {/* 剩余天数 */}
              <div className="flex items-center gap-3">
                <div className="rounded-lg bg-green-100 p-2 dark:bg-green-900">
                  <Clock className="size-4 text-green-600 dark:text-green-400" />
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">剩余天数</p>
                  <p className="font-medium">
                    {daysRemaining(activeSubscription.endDate)} 天
                  </p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* 订阅数据表格 */}
      <DataTable
        columns={columns}
        data={(data?.list ?? []) as (Subscription & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无订阅记录，点击「续费」创建您的第一个订阅"
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

    </div>
  )
}
