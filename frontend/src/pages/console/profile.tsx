/**
 * 个人资料页面
 *
 * 所有已登录用户均可访问，功能包括：
 * - 查看和编辑个人信息（邮箱、手机号）
 * - 修改登录密码（旧密码验证 + 新密码确认）
 */

import { useState } from 'react'
import { useAuth } from '@/contexts/auth-context'
import { useMutation } from '@/hooks/use-api'
import { put } from '@/lib/api-client'
import type { UserInfo } from '@/lib/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { toast } from 'sonner'
import { Shield, User } from 'lucide-react'

/** 用户类型到中文标签的映射 */
const USER_TYPE_LABELS: Record<string, string> = {
  SUPER_ADMIN: '超级管理员',
  TENANT_OWNER: '租户所有者',
  TENANT_ADMIN: '租户管理员',
  TENANT_USER: '租户用户',
}

/** 新密码最小长度 */
const MIN_PASSWORD_LENGTH = 6

/**
 * 个人资料页面组件
 *
 * 包含两个卡片区域：
 * 1. 个人信息编辑卡片：可修改邮箱和手机号
 * 2. 修改密码卡片：旧密码 + 新密码 + 确认密码
 */
export default function ProfilePage() {
  const { user, refreshUser } = useAuth()

  // ===== 个人信息编辑状态 =====
  /** 编辑中的邮箱地址 */
  const [email, setEmail] = useState(user?.email ?? '')
  /** 编辑中的手机号 */
  const [phone, setPhone] = useState(user?.phone ?? '')

  /** 保存个人信息 */
  const { mutate: saveProfile, loading: savingProfile } = useMutation<UserInfo, { email: string; phone: string }>(
    (body) => put<UserInfo>('/api/v1/users/me', body),
  )

  /**
   * 处理保存个人信息
   * 成功后刷新全局用户状态
   */
  const handleSaveProfile = async () => {
    const result = await saveProfile({ email: email.trim(), phone: phone.trim() })
    if (result !== null) {
      toast.success('个人信息已更新')
      // 刷新全局用户信息，使顶栏等位置同步更新
      await refreshUser()
    }
  }

  // ===== 修改密码状态 =====
  /** 旧密码 */
  const [oldPassword, setOldPassword] = useState('')
  /** 新密码 */
  const [newPassword, setNewPassword] = useState('')
  /** 确认密码 */
  const [confirmPassword, setConfirmPassword] = useState('')
  /** 密码表单校验错误信息 */
  const [passwordError, setPasswordError] = useState<string | null>(null)

  /** 修改密码 */
  const { mutate: changePassword, loading: changingPassword } = useMutation<void, { oldPassword: string; newPassword: string }>(
    (body) => put<void>('/api/v1/users/me/password', body),
  )

  /**
   * 处理修改密码
   * 包含前端校验：新密码长度、两次密码一致性
   * 成功后清空表单
   */
  const handleChangePassword = async () => {
    // 前端校验
    if (!oldPassword) {
      setPasswordError('请输入当前密码')
      return
    }
    if (newPassword.length < MIN_PASSWORD_LENGTH) {
      setPasswordError(`新密码长度不得少于 ${MIN_PASSWORD_LENGTH} 位`)
      return
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('两次输入的密码不一致')
      return
    }

    setPasswordError(null)
    const result = await changePassword({ oldPassword, newPassword })
    if (result !== null) {
      toast.success('密码修改成功')
      // 清空表单
      setOldPassword('')
      setNewPassword('')
      setConfirmPassword('')
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">个人信息</h1>

      {/* ===== 个人信息卡片 ===== */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <User className="size-5" />
            基本信息
          </CardTitle>
          <CardDescription>查看和编辑你的个人基本信息</CardDescription>
        </CardHeader>
        <Separator />
        <CardContent className="space-y-4 pt-6">
          {/* 用户名（只读） */}
          <div className="space-y-2">
            <Label>用户名</Label>
            <Input
              value={user?.username ?? ''}
              disabled
              className="bg-muted"
            />
            <p className="text-xs text-muted-foreground">用户名创建后不可修改</p>
          </div>

          {/* 用户类型（只读徽章） */}
          <div className="space-y-2">
            <Label>用户类型</Label>
            <div>
              <Badge variant="secondary">
                {USER_TYPE_LABELS[user?.userType ?? ''] ?? user?.userType ?? '—'}
              </Badge>
            </div>
          </div>

          {/* 邮箱（可编辑） */}
          <div className="space-y-2">
            <Label htmlFor="profile-email">邮箱</Label>
            <Input
              id="profile-email"
              type="email"
              placeholder="请输入邮箱地址"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          {/* 手机号（可编辑） */}
          <div className="space-y-2">
            <Label htmlFor="profile-phone">手机号</Label>
            <Input
              id="profile-phone"
              type="tel"
              placeholder="请输入手机号"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
            />
          </div>

          {/* 保存按钮 */}
          <div className="flex justify-end">
            <Button
              onClick={() => { void handleSaveProfile() }}
              disabled={savingProfile}
            >
              {savingProfile ? '保存中…' : '保存修改'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* ===== 修改密码卡片 ===== */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Shield className="size-5" />
            修改密码
          </CardTitle>
          <CardDescription>定期修改密码有助于保护账户安全</CardDescription>
        </CardHeader>
        <Separator />
        <CardContent className="space-y-4 pt-6">
          {/* 当前密码 */}
          <div className="space-y-2">
            <Label htmlFor="old-password">当前密码</Label>
            <Input
              id="old-password"
              type="password"
              placeholder="请输入当前密码"
              value={oldPassword}
              onChange={(e) => {
                setOldPassword(e.target.value)
                setPasswordError(null)
              }}
            />
          </div>

          {/* 新密码 */}
          <div className="space-y-2">
            <Label htmlFor="new-password">新密码</Label>
            <Input
              id="new-password"
              type="password"
              placeholder={`不少于 ${MIN_PASSWORD_LENGTH} 位`}
              value={newPassword}
              onChange={(e) => {
                setNewPassword(e.target.value)
                setPasswordError(null)
              }}
            />
          </div>

          {/* 确认新密码 */}
          <div className="space-y-2">
            <Label htmlFor="confirm-password">确认新密码</Label>
            <Input
              id="confirm-password"
              type="password"
              placeholder="请再次输入新密码"
              value={confirmPassword}
              onChange={(e) => {
                setConfirmPassword(e.target.value)
                setPasswordError(null)
              }}
            />
          </div>

          {/* 校验错误提示 */}
          {passwordError && (
            <p className="text-sm text-destructive">{passwordError}</p>
          )}

          {/* 提交按钮 */}
          <div className="flex justify-end">
            <Button
              onClick={() => { void handleChangePassword() }}
              disabled={changingPassword}
            >
              {changingPassword ? '提交中…' : '修改密码'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
