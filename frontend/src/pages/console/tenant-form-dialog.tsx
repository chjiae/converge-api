/**
 * 创建租户对话框
 *
 * 提供创建新租户的表单界面，包含以下字段：
 * - 租户编码（必填）
 * - 租户名称（必填）
 * - 描述（可选）
 * - 管理员用户名（必填）
 * - 管理员邮箱（必填）
 * - 管理员密码（必填，最少 6 位）
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
import type { Tenant } from '@/lib/types'

/** 创建租户请求参数 */
interface CreateTenantRequest {
  /** 租户编码 */
  code: string
  /** 租户名称 */
  name: string
  /** 租户描述 */
  description: string
  /** 管理员用户名 */
  adminUsername: string
  /** 管理员邮箱 */
  adminEmail: string
  /** 管理员密码 */
  adminPassword: string
}

/** 租户表单对话框属性 */
interface TenantFormDialogProps {
  /** 是否显示对话框 */
  open: boolean
  /** 显示状态变更回调 */
  onOpenChange: (open: boolean) => void
  /** 创建成功后的回调（用于刷新列表等） */
  onSuccess?: () => void
}

/**
 * 创建租户对话框组件
 *
 * 包含完整的表单验证和提交逻辑，
 * 提交期间按钮显示加载状态并禁用所有输入。
 */
export default function TenantFormDialog({
  open,
  onOpenChange,
  onSuccess,
}: TenantFormDialogProps) {
  /** 表单字段状态 */
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [adminUsername, setAdminUsername] = useState('')
  const [adminEmail, setAdminEmail] = useState('')
  const [adminPassword, setAdminPassword] = useState('')

  /** 客户端表单验证错误信息 */
  const [errors, setErrors] = useState<Record<string, string>>({})

  /** 创建租户的 mutation */
  const { mutate: createTenant, loading } = useMutation<Tenant, CreateTenantRequest>(
    (data) => post<Tenant>('/api/v1/tenants', data),
  )

  /**
   * 重置表单所有字段和错误信息
   */
  const resetForm = () => {
    setCode('')
    setName('')
    setDescription('')
    setAdminUsername('')
    setAdminEmail('')
    setAdminPassword('')
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
   * 校验所有必填字段和密码长度，
   * 返回验证是否通过。
   */
  const validate = (): boolean => {
    const newErrors: Record<string, string> = {}

    if (!code.trim()) {
      newErrors.code = '请输入租户编码'
    }
    if (!name.trim()) {
      newErrors.name = '请输入租户名称'
    }
    if (!adminUsername.trim()) {
      newErrors.adminUsername = '请输入管理员用户名'
    }
    if (!adminEmail.trim()) {
      newErrors.adminEmail = '请输入管理员邮箱'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(adminEmail)) {
      newErrors.adminEmail = '邮箱格式不正确'
    }
    if (!adminPassword) {
      newErrors.adminPassword = '请输入管理员密码'
    } else if (adminPassword.length < 6) {
      newErrors.adminPassword = '密码长度不能少于 6 位'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  /**
   * 处理表单提交
   *
   * 先进行客户端验证，验证通过后调用 API 创建租户。
   * 成功后关闭对话框并触发回调，失败时显示错误提示。
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!validate()) return

    const result = await createTenant({
      code: code.trim(),
      name: name.trim(),
      description: description.trim(),
      adminUsername: adminUsername.trim(),
      adminEmail: adminEmail.trim(),
      adminPassword,
    })

    if (result !== null) {
      toast.success('租户创建成功')
      handleOpenChange(false)
      onSuccess?.()
    } else {
      toast.error('租户创建失败，请重试')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>创建租户</DialogTitle>
          <DialogDescription>
            填写租户信息和管理员账号信息来创建新租户。
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={(e) => { void handleSubmit(e) }} className="space-y-4">
          {/* 租户编码 */}
          <div className="space-y-2">
            <Label htmlFor="tenant-code">
              租户编码 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="tenant-code"
              placeholder="请输入租户编码（唯一标识）"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              disabled={loading}
            />
            {errors.code && (
              <p className="text-xs text-destructive">{errors.code}</p>
            )}
          </div>

          {/* 租户名称 */}
          <div className="space-y-2">
            <Label htmlFor="tenant-name">
              租户名称 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="tenant-name"
              placeholder="请输入租户名称"
              value={name}
              onChange={(e) => setName(e.target.value)}
              disabled={loading}
            />
            {errors.name && (
              <p className="text-xs text-destructive">{errors.name}</p>
            )}
          </div>

          {/* 描述 */}
          <div className="space-y-2">
            <Label htmlFor="tenant-desc">描述</Label>
            <Input
              id="tenant-desc"
              placeholder="请输入租户描述（可选）"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={loading}
            />
          </div>

          {/* 管理员用户名 */}
          <div className="space-y-2">
            <Label htmlFor="admin-username">
              管理员用户名 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="admin-username"
              placeholder="请输入管理员用户名"
              value={adminUsername}
              onChange={(e) => setAdminUsername(e.target.value)}
              disabled={loading}
            />
            {errors.adminUsername && (
              <p className="text-xs text-destructive">{errors.adminUsername}</p>
            )}
          </div>

          {/* 管理员邮箱 */}
          <div className="space-y-2">
            <Label htmlFor="admin-email">
              管理员邮箱 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="admin-email"
              type="email"
              placeholder="请输入管理员邮箱"
              value={adminEmail}
              onChange={(e) => setAdminEmail(e.target.value)}
              disabled={loading}
            />
            {errors.adminEmail && (
              <p className="text-xs text-destructive">{errors.adminEmail}</p>
            )}
          </div>

          {/* 管理员密码 */}
          <div className="space-y-2">
            <Label htmlFor="admin-password">
              管理员密码 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="admin-password"
              type="password"
              placeholder="请输入管理员密码（至少 6 位）"
              value={adminPassword}
              onChange={(e) => setAdminPassword(e.target.value)}
              disabled={loading}
            />
            {errors.adminPassword && (
              <p className="text-xs text-destructive">{errors.adminPassword}</p>
            )}
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
