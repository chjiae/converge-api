/**
 * 角色表单对话框
 *
 * 支持创建和编辑两种模式：
 * - 创建模式：所有字段可编辑，提交 POST /api/v1/roles
 * - 编辑模式：预填字段值，角色编码只读，提交 PUT /api/v1/roles/{id}
 *
 * 表单字段：
 * - 角色编码（必填，编辑时只读）
 * - 角色名称（必填）
 * - 描述（可选）
 *
 * 提交成功后关闭对话框并触发 onSuccess 回调。
 */

import { useState, useEffect } from 'react'
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
import { post, put } from '@/lib/api-client'
import type { Role } from '@/lib/types'

/** 角色表单请求参数 */
interface RoleFormRequest {
  /** 角色编码 */
  code: string
  /** 角色名称 */
  name: string
  /** 角色描述 */
  description: string
}

/** 角色表单对话框属性 */
interface RoleFormDialogProps {
  /** 是否显示对话框 */
  open: boolean
  /** 显示状态变更回调 */
  onOpenChange: (open: boolean) => void
  /** 编辑模式下的目标角色，为 null 时表示创建模式 */
  editingRole?: Role | null
  /** 操作成功后的回调（用于刷新列表等） */
  onSuccess?: () => void
}

/**
 * 角色表单对话框组件
 *
 * 根据 editingRole 是否为 null 自动切换创建/编辑模式。
 * 包含完整的表单验证和提交逻辑，
 * 提交期间按钮显示加载状态并禁用所有输入。
 */
export default function RoleFormDialog({
  open,
  onOpenChange,
  editingRole = null,
  onSuccess,
}: RoleFormDialogProps) {
  /** 是否为编辑模式 */
  const isEditing = editingRole !== null

  /** 表单字段状态 */
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  /** 客户端表单验证错误信息 */
  const [errors, setErrors] = useState<Record<string, string>>({})

  /** 创建角色的 mutation */
  const { mutate: createRole, loading: creating } = useMutation<Role, RoleFormRequest>(
    (data) => post<Role>('/api/v1/roles', data),
  )

  /** 更新角色的 mutation */
  const { mutate: updateRole, loading: updating } = useMutation<Role, { id: number; data: RoleFormRequest }>(
    ({ id, data }) => put<Role>(`/api/v1/roles/${id}`, data),
  )

  /** 当前是否处于加载状态 */
  const loading = creating || updating

  /**
   * 当对话框打开且处于编辑模式时，预填角色数据
   * 创建模式则重置为空表单
   */
  useEffect(() => {
    if (open) {
      if (editingRole) {
        setCode(editingRole.code)
        setName(editingRole.name)
        setDescription(editingRole.description ?? '')
      } else {
        setCode('')
        setName('')
        setDescription('')
      }
      setErrors({})
    }
  }, [open, editingRole])

  /**
   * 重置表单所有字段和错误信息
   */
  const resetForm = () => {
    setCode('')
    setName('')
    setDescription('')
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
   * 校验角色编码和角色名称必填，
   * 返回验证是否通过。
   */
  const validate = (): boolean => {
    const newErrors: Record<string, string> = {}

    if (!code.trim()) {
      newErrors.code = '请输入角色编码'
    }
    if (!name.trim()) {
      newErrors.name = '请输入角色名称'
    }

    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }

  /**
   * 处理表单提交
   *
   * 根据当前模式调用创建或更新接口。
   * 成功后关闭对话框并触发回调，失败时显示错误提示。
   */
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!validate()) return

    const formData: RoleFormRequest = {
      code: code.trim(),
      name: name.trim(),
      description: description.trim(),
    }

    let result: Role | null

    if (isEditing && editingRole) {
      // 编辑模式：更新角色
      result = await updateRole({ id: editingRole.id, data: formData })
    } else {
      // 创建模式：新建角色
      result = await createRole(formData)
    }

    if (result !== null) {
      toast.success(isEditing ? '角色更新成功' : '角色创建成功')
      handleOpenChange(false)
      onSuccess?.()
    } else {
      toast.error(isEditing ? '角色更新失败，请重试' : '角色创建失败，请重试')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{isEditing ? '编辑角色' : '创建角色'}</DialogTitle>
          <DialogDescription>
            {isEditing ? '修改角色信息。角色编码不可更改。' : '填写角色信息来创建新角色。'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={(e) => { void handleSubmit(e) }} className="space-y-4">
          {/* 角色编码 */}
          <div className="space-y-2">
            <Label htmlFor="role-code">
              角色编码 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="role-code"
              placeholder="请输入角色编码（如 ADMIN）"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              disabled={loading || isEditing}
              readOnly={isEditing}
            />
            {errors.code && (
              <p className="text-xs text-destructive">{errors.code}</p>
            )}
          </div>

          {/* 角色名称 */}
          <div className="space-y-2">
            <Label htmlFor="role-name">
              角色名称 <span className="text-destructive">*</span>
            </Label>
            <Input
              id="role-name"
              placeholder="请输入角色名称"
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
            <Label htmlFor="role-desc">描述</Label>
            <Input
              id="role-desc"
              placeholder="请输入角色描述（可选）"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
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
              {isEditing ? '保存' : '创建'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
