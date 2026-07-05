/**
 * 订阅套餐配置页面
 *
 * 超级管理员维护套餐价格、节日折扣和套餐权益。
 * 租户续费下单时以后端保存的套餐配置为准，避免前端篡改金额和周期。
 */

import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { BadgePercent, CheckCircle2, Pencil, Save, XCircle } from 'lucide-react'
import { useMutation, useQuery } from '@/hooks/use-api'
import { get, put } from '@/lib/api-client'
import type { SubscriptionPlan } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import { PageSkeleton } from '@/components/loading-skeleton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** 套餐编辑表单 */
interface PlanForm {
  /** 套餐名称 */
  name: string
  /** 套餐类型 */
  planType: string
  /** 套餐周期（月） */
  durationMonths: number
  /** 原价 */
  originalPrice: string
  /** 折扣名称 */
  discountName: string
  /** 折扣价 */
  discountPrice: string
  /** 折扣开始时间 */
  discountStartAt: string
  /** 折扣结束时间 */
  discountEndAt: string
  /** 套餐权益文本 */
  benefits: string
  /** 是否启用 */
  enabled: boolean
  /** 是否推荐 */
  recommended: boolean
  /** 排序值 */
  sortOrder: number
}

/**
 * 格式化金额。
 * @param amount 金额
 * @returns 人民币金额文本
 */
function formatAmount(amount: number): string {
  return `¥${amount.toFixed(2)}`
}

/**
 * 套餐类型中文标签。
 * @param planType 套餐类型编码
 * @returns 中文标签
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
 * 将接口时间转换为 datetime-local 输入值。
 * @param value 接口返回时间
 * @returns datetime-local 字符串
 */
function toDateTimeInput(value: string | null): string {
  if (!value) return ''
  return value.replace(' ', 'T').slice(0, 16)
}

/**
 * 将 datetime-local 输入值转换为后端日期格式。
 * @param value datetime-local 字符串
 * @returns 后端日期时间字符串
 */
function toApiDateTime(value: string): string | null {
  if (!value) return null
  return `${value.replace('T', ' ')}:00`
}

/**
 * 从套餐响应构造编辑表单。
 * @param plan 套餐配置
 * @returns 编辑表单数据
 */
function toForm(plan: SubscriptionPlan): PlanForm {
  return {
    name: plan.name,
    planType: plan.planType,
    durationMonths: plan.durationMonths,
    originalPrice: String(plan.originalPrice),
    discountName: plan.discountName ?? '',
    discountPrice: plan.discountPrice === null ? '' : String(plan.discountPrice),
    discountStartAt: toDateTimeInput(plan.discountStartAt),
    discountEndAt: toDateTimeInput(plan.discountEndAt),
    benefits: plan.benefits.join('\n'),
    enabled: plan.enabled,
    recommended: plan.recommended,
    sortOrder: plan.sortOrder,
  }
}

/** 订阅套餐配置页面组件 */
export default function SubscriptionPlanListPage() {
  /** 当前编辑的套餐 */
  const [editingPlan, setEditingPlan] = useState<SubscriptionPlan | null>(null)
  /** 编辑表单状态 */
  const [form, setForm] = useState<PlanForm | null>(null)

  /** 获取全部套餐配置 */
  const { data: plans, loading, refetch } = useQuery<SubscriptionPlan[]>(
    () => get<SubscriptionPlan[]>('/api/v1/subscription-plans'),
    [],
  )

  /** 更新套餐配置 */
  const { mutate: updatePlan, loading: saving } = useMutation<SubscriptionPlan, PlanForm>(
    (input) => put<SubscriptionPlan>(`/api/v1/subscription-plans/${editingPlan?.id}`, {
      name: input.name,
      planType: input.planType,
      durationMonths: input.durationMonths,
      originalPrice: Number(input.originalPrice),
      discountName: input.discountName || null,
      discountPrice: input.discountPrice ? Number(input.discountPrice) : null,
      discountStartAt: toApiDateTime(input.discountStartAt),
      discountEndAt: toApiDateTime(input.discountEndAt),
      benefits: input.benefits,
      enabled: input.enabled,
      recommended: input.recommended,
      sortOrder: input.sortOrder,
    }),
  )

  /**
   * 打开编辑弹窗。
   * @param plan 待编辑套餐
   */
  const openEditDialog = (plan: SubscriptionPlan) => {
    setEditingPlan(plan)
    setForm(toForm(plan))
  }

  /** 提交套餐编辑 */
  const handleSave = async () => {
    if (!form) return
    const result = await updatePlan(form)
    if (!result) {
      toast.error('套餐配置保存失败')
      return
    }
    toast.success('套餐配置已保存')
    setEditingPlan(null)
    setForm(null)
    refetch()
  }

  /** 表格列定义 */
  const columns: Column<SubscriptionPlan & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'name',
      header: '套餐',
      render: (item) => (
        <div>
          <div className="flex items-center gap-2">
            <span className="font-medium">{item.name}</span>
            {item.recommended && <Badge>推荐</Badge>}
          </div>
          <p className="mt-1 text-xs text-muted-foreground">{getPlanTypeLabel(item.planType)} · {item.durationMonths} 个月</p>
        </div>
      ),
    },
    {
      key: 'price',
      header: '价格',
      render: (item) => (
        <div>
          <span className="font-medium">{formatAmount(item.finalPrice)}</span>
          {item.hasActiveDiscount && (
            <span className="ml-2 text-xs text-muted-foreground line-through">{formatAmount(item.originalPrice)}</span>
          )}
        </div>
      ),
    },
    {
      key: 'discount',
      header: '折扣',
      render: (item) => item.hasActiveDiscount ? (
        <Badge className="bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300">
          <BadgePercent className="size-3" />
          {item.discountName ?? '限时优惠'}
        </Badge>
      ) : '—',
    },
    {
      key: 'enabled',
      header: '状态',
      render: (item) => item.enabled ? (
        <Badge className="bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300">
          <CheckCircle2 className="size-3" />
          已启用
        </Badge>
      ) : (
        <Badge variant="secondary">
          <XCircle className="size-3" />
          已停用
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <Button size="sm" variant="outline" onClick={() => openEditDialog(item as SubscriptionPlan)}>
          <Pencil className="size-4" />
          编辑
        </Button>
      ),
    },
  ], [])

  if (loading && !plans) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">套餐配置</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          维护租户续费可选套餐、原价、节日折扣和展示权益。
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>订阅套餐</CardTitle>
          <CardDescription>租户续费页面只展示已启用套餐，支付金额以后端配置为准。</CardDescription>
        </CardHeader>
        <CardContent>
          <DataTable
            columns={columns}
            data={(plans ?? []) as (SubscriptionPlan & Record<string, unknown>)[]}
            loading={loading}
            emptyText="暂无套餐配置"
          />
        </CardContent>
      </Card>

      <Dialog open={editingPlan !== null} onOpenChange={(open) => {
        if (!open) {
          setEditingPlan(null)
          setForm(null)
        }
      }}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>编辑套餐</DialogTitle>
            <DialogDescription>折扣价为空时按原价售卖，折扣时间到期后自动恢复原价。</DialogDescription>
          </DialogHeader>

          {form && (
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="plan-name">套餐名称</Label>
                <Input
                  id="plan-name"
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="duration-months">套餐周期（月）</Label>
                <Input
                  id="duration-months"
                  type="number"
                  min={1}
                  value={form.durationMonths}
                  onChange={(event) => setForm({ ...form, durationMonths: Number(event.target.value) })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="original-price">原价</Label>
                <Input
                  id="original-price"
                  type="number"
                  min={0}
                  step="0.01"
                  value={form.originalPrice}
                  onChange={(event) => setForm({ ...form, originalPrice: event.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="discount-price">节日折扣价</Label>
                <Input
                  id="discount-price"
                  type="number"
                  min={0}
                  step="0.01"
                  value={form.discountPrice}
                  onChange={(event) => setForm({ ...form, discountPrice: event.target.value })}
                  placeholder="不填则无折扣"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="discount-name">折扣名称</Label>
                <Input
                  id="discount-name"
                  value={form.discountName}
                  onChange={(event) => setForm({ ...form, discountName: event.target.value })}
                  placeholder="如：春节特惠"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="sort-order">排序值</Label>
                <Input
                  id="sort-order"
                  type="number"
                  value={form.sortOrder}
                  onChange={(event) => setForm({ ...form, sortOrder: Number(event.target.value) })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="discount-start">折扣开始时间</Label>
                <Input
                  id="discount-start"
                  type="datetime-local"
                  value={form.discountStartAt}
                  onChange={(event) => setForm({ ...form, discountStartAt: event.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="discount-end">折扣结束时间</Label>
                <Input
                  id="discount-end"
                  type="datetime-local"
                  value={form.discountEndAt}
                  onChange={(event) => setForm({ ...form, discountEndAt: event.target.value })}
                />
              </div>
              <div className="space-y-2 md:col-span-2">
                <Label htmlFor="benefits">套餐权益</Label>
                <textarea
                  id="benefits"
                  className="min-h-28 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={form.benefits}
                  onChange={(event) => setForm({ ...form, benefits: event.target.value })}
                />
              </div>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.enabled}
                  onChange={(event) => setForm({ ...form, enabled: event.target.checked })}
                />
                启用套餐
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={form.recommended}
                  onChange={(event) => setForm({ ...form, recommended: event.target.checked })}
                />
                设为推荐
              </label>
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditingPlan(null)}>取消</Button>
            <Button disabled={saving} onClick={handleSave}>
              <Save className="size-4" />
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
