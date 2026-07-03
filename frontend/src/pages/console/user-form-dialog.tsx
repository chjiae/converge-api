/**
 * 添加用户对话框
 *
 * 提供创建新用户的表单界面，包含以下字段：
 * - 用户名（必填）
 * - 邮箱（必填，需邮箱格式校验）
 * - 密码（必填，最少 6 位）
 * - 手机号（可选）
 *
 * 提交成功后关闭对话框并触发 onSuccess 回调。
 */

import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useMutation } from '@/hooks/use-api'
import { post } from '@/lib/api-client'
import type { User } from '@/lib/types'

/** 创建用户请求参数 */
interface CreateUserRequest {
  /** 用户名 */
  username: string
  /** 邮箱地址 */
  email: string
  /** 密码 */
  password: string
  /** 手机号 */
  phone: string
}

/** 添加用户对话框属性 */
interface UserFormDialogProps {
  /** 是否显示对话框 */
  open: boolean
  /** 显示状态变更回调 */
  onOpenChange: (open: boolean) => void
  /** 创建成功后的回调（用于刷新列表等） */
  onSuccess?: () => void
}

/**
 * 添加用户对话框组件
 *
 * 包含完整的表单验证和提交逻辑，
 * 提交期间按钮显示加载状态并禁用所有输入。
 */
export default function UserFormDialog({
  open,
  onOpenChange,
  onSuccess,
}: UserFormDialogProps) {
  /** 表单字段状态 */
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [phone, setPhone] = useState('')

  /** 客户端表单验证错误信息 */
  const [errors, setErrors] = useState<Record<string, string>>({})

  /** 创建用户的 mutation */
  const { mutate: createUser, loading } = useMutation<User, CreateUserRequest>(
    (data) => post<User>('/api/v1/users', data),
  )

  /**
   * 重置表单所有字段和错误信息
   */
  const resetForm = () => {
    setUsername('')
    setEmail('')
    setPassword('')
    setPhone('')
    setErrors({})
  }

  /**
   * 处理对话框打开/关闭状态变化
   * 关闭时自动重置表单
   */
  const handleOpenChange = (nextOpen: boolean) => {
    if (!nextOpen) {
      resetForm()
    }
    onOpenChange(nextOpen)
  }

  /**
   * 客户端表单验证
   *
   * 校验用户名、邮箱（格式）、密码（最少 6 位）等必填字段，
   * 返回验证是否通过。
   */
  const validate = (): boolean => {
    const newErrors: Record<string, string> = {}

    if (!username.trim()) {
      newErrors.username = '请输入用户名'
    }
    if (!email.trim()) {
      newErrors.email = '请输入邮箱地址'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      newErrors.email = '邮箱格式不正确'
    }
    if (!password) {
      newErrors.password = '请输入密码'
    } else if (password.length < 6) {
      newErrors.password = '密码长度不能少于 6 位'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  /**
   * 处理表单提交
   *
   * 先进行客户端验证，验证通过后调用 API 创建用户。
   * 成功后关闭对话框并触发回调，失败时显示错误提示。
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!validate()) return

    const result = await createUser({
      username: username.trim(),
      email: email.trim(),
      password,
      phone: phone.trim(),
    })

    if (result !== null) {
      toast.success('用户创建成功')
      handleOpenChange(false)
      onSuccess?.()
    } else {
      toast.error('用户创建失败，请重试')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>添加用户</DialogTitle>
          <DialogDescription>
            填写用户信息来创建新账户。
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={(e) => { void handleSubmit(e) }} className="space-y-4">
          {/* 用户名 */}
          <div className="space-y-2">
            <Label htmlFor="user-username">
              用户名 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="user-username"
              placeholder="请输入用户名"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              disabled={loading}
            />
            {errors.username && (
              <p className="text-xs text-destructive">{errors.username}</p>
            )}
          </div>

          {/* 邮箱 */}
          <div className="space-y-2">
            <Label htmlFor="user-email">
              邮箱 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="user-email"
              type="email"
              placeholder="请输入邮箱地址"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              disabled={loading}
            />
            {errors.email && (
              <p className="text-xs text-destructive">{errors.email}</p>
            )}
          </div>

          {/* 密码 */}
          <div className="space-y-2">
            <Label htmlFor="user-password">
              密码 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="user-password"
              type="password"
              placeholder="请输入密码（至少 6 位）"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={loading}
            />
            {errors.password && (
              <p className="text-xs text-destructive">{errors.password}</p>
            )}
          </div>

          {/* 手机号（可选） */}
          <div className="space-y-2">
            <Label htmlFor="user-phone">手机号</Label>
            <Input
              id="user-phone"
              placeholder="请输入手机号（可选）"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              disabled={loading}
            />
          </div>

          {/* 底部操作按钮 */}
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => handleOpenChange(false)}
              disabled={loading}
            >
              取消
            </Button>
            <Button type="submit" disabled={loading}>
              {loading && <Loader2 className="size-4 animate-spin" />}
              创建
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
