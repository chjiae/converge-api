/**
 * 订阅续费页面
 *
 * 租户用户在独立页面选择套餐和支付方式。
 * 金额、续费周期和租户 ID 均由后端基于套餐配置计算，前端只负责展示和发起订单。
 */

import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowLeft, BadgePercent, CalendarDays, CheckCircle2, CreditCard, Landmark, QrCode } from 'lucide-react'
import { useMutation, useQuery } from '@/hooks/use-api'
import { get, post } from '@/lib/api-client'
import type { PageResult, PaymentInitiateRequest, PaymentInitiateResponse, Subscription, SubscriptionPlan } from '@/lib/types'
import { cn } from '@/lib/utils'
import { PageSkeleton } from '@/components/loading-skeleton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/** 支付方式选项 */
interface PaymentMethodOption {
  /** 支付方式编码 */
  value: 'OFFLINE' | 'ALIPAY' | 'WECHAT'
  /** 支付方式名称 */
  label: string
  /** 支付方式说明 */
  description: string
  /** 支付方式图标 */
  icon: React.ComponentType<{ className?: string }>
}

/** 支付方式卡片配置 */
const paymentMethods: PaymentMethodOption[] = [
  {
    value: 'ALIPAY',
    label: '支付宝',
    description: '支付成功回调后立即生效',
    icon: QrCode,
  },
  {
    value: 'WECHAT',
    label: '微信支付',
    description: '支付成功回调后立即生效',
    icon: CreditCard,
  },
  {
    value: 'OFFLINE',
    label: '线下支付',
    description: '提交凭证后等待管理员审核',
    icon: Landmark,
  },
]

/**
 * 格式化金额显示。
 * @param amount 金额数值
 * @returns 带人民币符号的金额文本
 */
function formatAmount(amount: number): string {
  return `¥${amount.toFixed(2)}`
}

/**
 * 格式化日期显示。
 * @param date 日期对象
 * @returns 中文本地日期
 */
function formatDate(date: Date): string {
  return date.toLocaleDateString('zh-CN')
}

/**
 * 根据当前订阅和套餐计算续费预估结束日期。
 * 这里仅做页面提示，实际周期以后端订单为准。
 */
function calculateEstimatedEndDate(activeSubscription: Subscription | null, plan: SubscriptionPlan | null): string {
  if (!plan) return '请选择套餐'
  const today = new Date()
  const baseDate = activeSubscription?.endDate ? new Date(activeSubscription.endDate) : today
  const startDate = baseDate > today ? baseDate : today
  const endDate = new Date(startDate)
  endDate.setMonth(endDate.getMonth() + plan.durationMonths)
  return formatDate(endDate)
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

/** 订阅续费页面组件 */
export default function SubscriptionRenewPage() {
  /** 路由跳转函数 */
  const navigate = useNavigate()
  /** 当前选中的套餐 ID */
  const [selectedPlanId, setSelectedPlanId] = useState<number | null>(null)
  /** 当前选中的支付方式 */
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethodOption['value']>('ALIPAY')

  /** 获取启用套餐列表 */
  const { data: plans, loading: plansLoading } = useQuery<SubscriptionPlan[]>(
    () => get<SubscriptionPlan[]>('/api/v1/subscription-plans/enabled'),
    [],
  )

  /** 获取当前订阅列表，用于页面预估续费到期日 */
  const { data: subscriptions } = useQuery<PageResult<Subscription>>(
    () => get<PageResult<Subscription>>('/api/v1/my-subscriptions?page=1&size=10'),
    [],
  )

  /** 当前生效订阅 */
  const activeSubscription = useMemo(() => {
    return subscriptions?.list.find((item) => item.status === 'ACTIVE') ?? null
  }, [subscriptions?.list])

  /** 当前选中的套餐 */
  const selectedPlan = useMemo(() => {
    if (!plans?.length) return null
    return plans.find((plan) => plan.id === selectedPlanId) ?? plans.find((plan) => plan.recommended) ?? plans[0]
  }, [plans, selectedPlanId])

  /** 创建续费订单 */
  const { mutate: createRenewal, loading: creating } = useMutation<Subscription, void>(
    () => post<Subscription>('/api/v1/my-subscriptions/renewals', {
      planId: selectedPlan?.id,
      paymentMethod,
      remark: paymentMethod === 'OFFLINE' ? '线下支付续费订单' : '在线支付续费订单',
    }),
  )

  /** 发起在线支付 */
  const { mutate: initiatePayment, loading: paying } = useMutation<PaymentInitiateResponse, PaymentInitiateRequest>(
    (request) => post<PaymentInitiateResponse>('/api/v1/my-subscriptions/pay', request),
  )

  /**
   * 提交续费订单。
   *
   * 线下支付只创建待审核订单；支付宝/微信在创建订单后跳转到支付页面，
   * 后续由支付回调自动激活订阅。
   */
  const handleSubmit = async () => {
    if (!selectedPlan) {
      toast.error('请选择套餐')
      return
    }

    const subscription = await createRenewal()
    if (!subscription) {
      toast.error('创建续费订单失败')
      return
    }

    if (paymentMethod === 'OFFLINE') {
      toast.success('线下支付订单已提交，请等待管理员确认')
      navigate('/console/my-subscriptions')
      return
    }

    const payment = await initiatePayment({
      subscriptionId: subscription.id,
      paymentMethod,
      returnUrl: `${window.location.origin}/console/payment-result`,
    })
    if (!payment) {
      toast.error('发起支付失败，请检查支付配置')
      return
    }
    window.location.href = payment.paymentUrl
  }

  if (plansLoading && !plans) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
        <div>
          <Button variant="ghost" className="mb-2 px-0" onClick={() => navigate('/console/my-subscriptions')}>
            <ArrowLeft className="size-4" />
            返回我的订阅
          </Button>
          <h1 className="text-2xl font-bold">选择订阅套餐</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            已生效租户会从当前有效期后顺延，已过期租户从今天开始计算。
          </p>
        </div>
        <Card className="w-full md:w-72">
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex size-10 items-center justify-center rounded-md bg-primary/10 text-primary">
              <CalendarDays className="size-5" />
            </div>
            <div>
              <p className="text-xs text-muted-foreground">预计续费后到期</p>
              <p className="font-medium">{calculateEstimatedEndDate(activeSubscription, selectedPlan)}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        {(plans ?? []).map((plan) => {
          const active = selectedPlan?.id === plan.id
          return (
            <button
              key={plan.id}
              type="button"
              className={cn(
                'group rounded-lg border bg-card p-0 text-left text-card-foreground shadow-sm transition-all hover:border-primary/60 hover:shadow-md',
                active && 'border-primary ring-2 ring-primary/20',
              )}
              onClick={() => setSelectedPlanId(plan.id)}
            >
              <Card className="h-full border-0 shadow-none">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <CardTitle className="flex items-center gap-2">
                        {plan.name}
                        {plan.recommended && <Badge>推荐</Badge>}
                      </CardTitle>
                      <CardDescription>{getPlanTypeLabel(plan.planType)} · {plan.durationMonths} 个月</CardDescription>
                    </div>
                    {active && <CheckCircle2 className="size-5 text-primary" />}
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div>
                    <div className="flex items-end gap-2">
                      <span className="text-3xl font-bold">{formatAmount(plan.finalPrice)}</span>
                      {plan.hasActiveDiscount && (
                        <span className="pb-1 text-sm text-muted-foreground line-through">
                          {formatAmount(plan.originalPrice)}
                        </span>
                      )}
                    </div>
                    {plan.hasActiveDiscount && (
                      <div className="mt-2 inline-flex items-center gap-1 rounded-md bg-red-50 px-2 py-1 text-xs font-medium text-red-700 dark:bg-red-950 dark:text-red-300">
                        <BadgePercent className="size-3" />
                        {plan.discountName ?? '限时优惠'}
                      </div>
                    )}
                  </div>
                  <div className="space-y-2">
                    {plan.benefits.map((benefit) => (
                      <div key={benefit} className="flex items-start gap-2 text-sm">
                        <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-primary" />
                        <span>{benefit}</span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </button>
          )
        })}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>支付方式</CardTitle>
          <CardDescription>在线支付成功后自动到账，线下支付需要管理员审核。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="grid gap-3 md:grid-cols-3">
            {paymentMethods.map((method) => {
              const Icon = method.icon
              const active = paymentMethod === method.value
              return (
                <button
                  key={method.value}
                  type="button"
                  className={cn(
                    'rounded-lg border bg-background p-4 text-left transition-colors hover:border-primary/60',
                    active && 'border-primary bg-primary/5 ring-2 ring-primary/20',
                  )}
                  onClick={() => setPaymentMethod(method.value)}
                >
                  <div className="flex items-center gap-3">
                    <Icon className="size-5 text-primary" />
                    <div>
                      <p className="font-medium">{method.label}</p>
                      <p className="mt-1 text-xs text-muted-foreground">{method.description}</p>
                    </div>
                  </div>
                </button>
              )
            })}
          </div>

          <div className="flex flex-col gap-3 rounded-lg border bg-muted/30 p-4 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm text-muted-foreground">应付金额</p>
              <p className="text-2xl font-bold">{selectedPlan ? formatAmount(selectedPlan.finalPrice) : '—'}</p>
            </div>
            <Button size="lg" disabled={!selectedPlan || creating || paying} onClick={handleSubmit}>
              <CreditCard className="size-4" />
              {paymentMethod === 'OFFLINE' ? '提交线下支付订单' : '去支付'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
