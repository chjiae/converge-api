/**
 * 支付结果页面
 *
 * 在线支付完成后的回调页面。
 * 根据 URL 参数展示支付成功或失败的结果信息，
 * 并提供返回订阅管理页面的入口。
 *
 * URL 参数：
 * - status: 支付状态（success / fail）
 * - outTradeNo: 商户订单号
 * - message: 附加消息（如失败原因）
 */

import { useSearchParams, useNavigate } from 'react-router-dom'
import { CheckCircle2, XCircle, ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'

/**
 * 支付结果页面组件
 *
 * 从 URL 查询参数中读取支付结果状态，
 * 展示对应的成功/失败卡片，并提供返回按钮。
 */
export default function PaymentResultPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  /** 支付状态：success 或 fail */
  const status = searchParams.get('status') ?? 'fail'
  /** 商户订单号 */
  const outTradeNo = searchParams.get('outTradeNo')
  /** 附加消息 */
  const message = searchParams.get('message')

  /** 是否为支付成功 */
  const isSuccess = status === 'success'

  /**
   * 返回订阅管理页面
   */
  const handleBack = () => {
    navigate('/console/my-subscriptions')
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          {/* 状态图标 */}
          <div className="mx-auto mb-2">
            {isSuccess ? (
              <CheckCircle2 className="size-16 text-green-500" />
            ) : (
              <XCircle className="size-16 text-red-500" />
            )}
          </div>

          <CardTitle className="text-xl">
            {isSuccess ? '支付成功' : '支付失败'}
          </CardTitle>
          <CardDescription>
            {isSuccess
              ? '您的订阅已生效，感谢购买！'
              : message || '支付过程中出现问题，请重试或联系客服。'}
          </CardDescription>
        </CardHeader>

        <CardContent className="space-y-4">
          {/* 订单号展示 */}
          {outTradeNo && (
            <div className="rounded-lg bg-muted p-3">
              <p className="text-xs text-muted-foreground">商户订单号</p>
              <p className="font-mono text-sm">{outTradeNo}</p>
            </div>
          )}

          {/* 返回订阅页面按钮 */}
          <Button className="w-full" onClick={handleBack}>
            <ArrowLeft className="size-4" />
            返回订阅
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
