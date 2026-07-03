/**
 * 续费/订阅创建对话框
 *
 * 租户用户用于创建新订阅或续费现有订阅。
 * 功能包括：
 * - 选择套餐类型（月付/季付/年付），自动计算金额和结束日期
 * - 选择支付方式（线下支付/支付宝/微信支付）
 * - 在线支付时自动跳转支付页面
 * - 线下支付时直接创建待支付订阅
 */

import { useState, useMemo, useCallback } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { post } from '@/lib/api-client'
import type { Subscription, PaymentInitiateRequest, PaymentInitiateResponse } from '@/lib/types'
import { useAuth } from '@/contexts/auth-context'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** 续费对话框属性 */
interface RenewalDialogProps {
  /** 是否显示对话框 */
  open: boolean
  /** 显示状态变更回调 */
  onOpenChange: (open: boolean) => void
  /** 创建成功后的回调（用于刷新列表） */
  onSuccess: () => void
}

/** 套餐类型选项定义 */
const PLAN_OPTIONS = [
  { value: 'MONTHLY', label: '月付', amount: 99, months: 1 },
  { value: 'QUARTERLY', label: '季付', amount: 267, months: 3 },
  { value: 'YEARLY', label: '年付', amount: 948, months: 12 },
] as const

/** 支付方式选项定义 */
const PAYMENT_METHOD_OPTIONS = [
  { value: 'OFFLINE', label: '线下支付' },
  { value: 'ALIPAY', label: '支付宝' },
  { value: 'WECHAT', label: '微信支付' },
] as const

/**
 * 计算结束日期
 *
 * 根据开始日期和套餐类型对应的月数，计算订阅结束日期。
 * @param startDate - 开始日期字符串（YYYY-MM-DD）
 * @param months - 订阅月数
 * @returns 结束日期字符串（YYYY-MM-DD）
 */
function calculateEndDate(startDate: string, months: number): string {
  const date = new Date(startDate)
  date.setMonth(date.getMonth() + months)
  return date.toISOString().split('T')[0]
}

/**
 * 获取今天的日期字符串
 * @returns YYYY-MM-DD 格式的日期
 */
function getTodayString(): string {
  return new Date().toISOString().split('T')[0]
}

/**
 * 续费/订阅创建对话框组件
 *
 * 用户选择套餐类型后自动填充金额和结束日期，
 * 也可手动修改金额。提交后根据支付方式决定是否跳转在线支付。
 */
export default function RenewalDialog({
  open,
  onOpenChange,
  onSuccess,
}: RenewalDialogProps) {
  const { user } = useAuth()

  /** 套餐类型 */
  const [planType, setPlanType] = useState('MONTHLY')
  /** 支付方式 */
  const [paymentMethod, setPaymentMethod] = useState('OFFLINE')
  /** 金额（支持手动修改） */
  const [amount, setAmount] = useState('99')
  /** 开始日期 */
  const [startDate, setStartDate] = useState(getTodayString)
  /** 结束日期（根据套餐自动计算，也可手动修改） */
  const [endDate, setEndDate] = useState(() => calculateEndDate(getTodayString(), 1))
  /** 备注 */
  const [remark, setRemark] = useState('')
  /** 是否正在提交 */
  const [submitting, setSubmitting] = useState(false)

  /** 当前选中的套餐配置 */
  const selectedPlan = useMemo(
    () => PLAN_OPTIONS.find((p) => p.value === planType) ?? PLAN_OPTIONS[0],
    [planType],
  )

  /**
   * 处理套餐类型变更
   * 切换套餐时自动更新金额和结束日期
   */
  const handlePlanTypeChange = useCallback((value: string) => {
    setPlanType(value)
    const plan = PLAN_OPTIONS.find((p) => p.value === value)
    if (plan) {
      setAmount(String(plan.amount))
      setEndDate(calculateEndDate(startDate, plan.months))
    }
  }, [startDate])

  /**
   * 处理开始日期变更
   * 开始日期变化时重新计算结束日期
   */
  const handleStartDateChange = useCallback((value: string) => {
    setStartDate(value)
    setEndDate(calculateEndDate(value, selectedPlan.months))
  }, [selectedPlan.months])

  /**
   * 重置表单到初始状态
   */
  const resetForm = useCallback(() => {
    setPlanType('MONTHLY')
    setPaymentMethod('OFFLINE')
    setAmount('99')
    setStartDate(getTodayString())
    setEndDate(calculateEndDate(getTodayString(), 1))
    setRemark('')
  }, [])

  /**
   * 处理表单提交
   *
   * 流程：
   * 1. 调用创建订阅接口
   * 2. 若支付方式为支付宝/微信，发起在线支付并跳转
   * 3. 若为线下支付，直接关闭对话框并刷新列表
   */
  const handleSubmit = async () => {
    if (!user?.tenantId) {
      toast.error('无法获取租户信息')
      return
    }

    const parsedAmount = parseFloat(amount)
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      toast.error('请输入有效的金额')
      return
    }

    if (!startDate || !endDate) {
      toast.error('请选择开始日期和结束日期')
      return
    }

    setSubmitting(true)
    try {
      /* 创建订阅 */
      const subscription = await post<Subscription>('/api/v1/my-subscriptions', {
        tenantId: user.tenantId,
        planType,
        amount: parsedAmount,
        startDate,
        endDate,
        paymentMethod,
        remark: remark || null,
      })

      /* 在线支付：发起支付并跳转到支付页面 */
      if (paymentMethod === 'ALIPAY' || paymentMethod === 'WECHAT') {
        const returnUrl = `${window.location.origin}/console/payment-result`
        const payRequest: PaymentInitiateRequest = {
          subscriptionId: subscription.id,
          paymentMethod,
          returnUrl,
        }

        try {
          const payResponse = await post<PaymentInitiateResponse>(
            '/api/v1/my-subscriptions/pay',
            payRequest,
          )
          toast.success('订阅创建成功，正在跳转到支付页面...')
          onOpenChange(false)
          resetForm()
          onSuccess()
          /* 跳转到第三方支付页面 */
          window.location.href = payResponse.paymentUrl
          return
        } catch (payErr) {
          /* 支付发起失败，但订阅已创建 */
          toast.error(
            `支付发起失败：${payErr instanceof Error ? payErr.message : '未知错误'}。订阅已创建，可稍后重试支付。`,
          )
        }
      }

      /* 线下支付或支付发起失败：关闭对话框并刷新列表 */
      toast.success('订阅创建成功')
      onOpenChange(false)
      resetForm()
      onSuccess()
    } catch (err) {
      toast.error(`创建订阅失败：${err instanceof Error ? err.message : '未知错误'}`)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>续费订阅</DialogTitle>
          <DialogDescription>
            选择套餐类型和支付方式，创建新的订阅订单。
          </DialogDescription>
        </DialogHeader>

        {/* 表单区域 */}
        <div className="space-y-4">
          {/* 套餐类型选择 */}
          <div className="space-y-2">
            <Label>套餐类型</Label>
            <Select
              value={planType}
              onValueChange={handlePlanTypeChange}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PLAN_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}（¥{opt.amount}）
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* 支付方式选择 */}
          <div className="space-y-2">
            <Label>支付方式</Label>
            <Select
              value={paymentMethod}
              onValueChange={setPaymentMethod}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAYMENT_METHOD_OPTIONS.map((opt) => (
                  <SelectItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {/* 金额输入（支持手动修改） */}
          <div className="space-y-2">
            <Label htmlFor="renewal-amount">金额（元）</Label>
            <Input
              id="renewal-amount"
              type="number"
              min="0"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="请输入金额"
            />
            <p className="text-xs text-muted-foreground">
              默认价格：{selectedPlan.label} ¥{selectedPlan.amount}，可手动修改
            </p>
          </div>

          {/* 日期范围 */}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="renewal-start-date">开始日期</Label>
              <Input
                id="renewal-start-date"
                type="date"
                value={startDate}
                onChange={(e) => handleStartDateChange(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="renewal-end-date">结束日期</Label>
              <Input
                id="renewal-end-date"
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </div>
          </div>

          {/* 备注（可选） */}
          <div className="space-y-2">
            <Label htmlFor="renewal-remark">备注（可选）</Label>
            <textarea
              id="renewal-remark"
              className="flex min-h-[60px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
              value={remark}
              onChange={(e) => setRemark(e.target.value)}
              placeholder="如有特殊需求可在此备注"
              rows={2}
            />
          </div>
        </div>

        {/* 操作按钮 */}
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              onOpenChange(false)
              resetForm()
            }}
            disabled={submitting}
          >
            取消
          </Button>
          <Button
            onClick={() => { void handleSubmit() }}
            disabled={submitting}
          >
            {submitting && <Loader2 className="size-4 animate-spin" />}
            确认创建
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
