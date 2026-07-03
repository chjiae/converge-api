/**
 * 确认对话框组件
 *
 * 基于 shadcn Dialog 封装的二次确认弹窗，用于：
 * - 删除操作确认
 * - 危险操作二次确认
 * - 通用操作确认
 *
 * 支持 destructive 模式（红色确认按钮）和异步确认操作（加载状态）。
 */

import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

/** 确认对话框属性 */
interface ConfirmDialogProps {
  /** 是否显示对话框 */
  open: boolean
  /** 显示状态变更回调 */
  onOpenChange: (open: boolean) => void
  /** 对话框标题 */
  title: string
  /** 对话框描述内容 */
  description: string
  /** 确认按钮文本，默认"确认" */
  confirmText?: string
  /** 取消按钮文本，默认"取消" */
  cancelText?: string
  /** 是否为危险操作（确认按钮使用 destructive 样式） */
  destructive?: boolean
  /** 确认操作回调（支持异步） */
  onConfirm: () => void | Promise<void>
  /** 是否正在执行确认操作（外部控制的加载状态） */
  loading?: boolean
}

/**
 * 确认对话框组件
 *
 * 展示标题和描述信息，提供取消和确认两个操作按钮。
 * 当 destructive 为 true 时，确认按钮使用红色 destructive 样式。
 * 支持异步确认操作，执行期间按钮显示加载状态并禁用。
 */
export default function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmText = '确认',
  cancelText = '取消',
  destructive = false,
  onConfirm,
  loading: externalLoading = false,
}: ConfirmDialogProps) {
  const [internalLoading, setInternalLoading] = useState(false)
  const isLoading = externalLoading || internalLoading

  /**
   * 处理确认按钮点击
   * 自动管理内部加载状态，支持异步回调
   */
  const handleConfirm = async () => {
    try {
      setInternalLoading(true)
      await onConfirm()
      onOpenChange(false)
    } catch {
      // 确认操作失败时保持对话框打开，由调用方处理错误提示
    } finally {
      setInternalLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent showCloseButton={false}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isLoading}
          >
            {cancelText}
          </Button>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            onClick={() => { void handleConfirm() }}
            disabled={isLoading}
          >
            {isLoading && <Loader2 className="size-4 animate-spin" />}
            {confirmText}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
