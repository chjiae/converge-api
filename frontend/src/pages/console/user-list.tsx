/**
 * 用户管理页面
 *
 * 租户控制台 — 用户管理主页面。
 * 功能包括：
 * - 分页展示租户内所有用户
 * - 启用/禁用用户状态
 * - 分配角色（通过对话框选择角色复选框）
 * - 删除用户（二次确认）
 * - 添加新用户
 */

import { useState, useMemo } from 'react'
import { Plus, UserCog } from 'lucide-react'
import { toast } from 'sonner'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, put, del } from '@/lib/api-client'
import type { User, Role, PageResult } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import ConfirmDialog from '@/components/confirm-dialog'
import UserFormDialog from '@/pages/console/user-form-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2 } from 'lucide-react'

/**
 * 格式化日期字符串
 *
 * @param dateStr - ISO 日期时间字符串
 * @returns 格式化后的中文日期字符串
 */
function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleDateString('zh-CN')
}

/**
 * 获取用户状态的 Badge 样式
 *
 * @param status - 用户状态（ACTIVE | DISABLED）
 * @returns Tailwind CSS 类名
 */
function getStatusBadgeClass(status: string): string {
  return status === 'ACTIVE'
    ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200'
    : 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200'
}

/**
 * 获取用户状态的中文标签
 *
 * @param status - 用户状态
 * @returns 中文状态名称
 */
function getStatusLabel(status: string): string {
  return status === 'ACTIVE' ? '正常' : '已禁用'
}

/**
 * 获取用户类型的中文标签
 *
 * @param userType - 用户类型枚举值
 * @returns 中文用户类型名称
 */
function getUserTypeLabel(userType: string): string {
  const map: Record<string, string> = {
    SUPER_ADMIN: '超级管理员',
    TENANT_OWNER: '租户所有者',
    TENANT_ADMIN: '租户管理员',
    TENANT_USER: '租户用户',
  }
  return map[userType] ?? userType
}

/**
 * 用户管理页面组件
 *
 * 供租户所有者和租户管理员使用，
 * 提供租户内用户的完整管理功能。
 */
export default function UserListPage() {
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)

  /** 添加用户对话框显示状态 */
  const [createDialogOpen, setCreateDialogOpen] = useState(false)

  /** 删除确认对话框目标用户 */
  const [deleteTarget, setDeleteTarget] = useState<User | null>(null)

  /** 角色分配对话框目标用户 */
  const [roleAssignTarget, setRoleAssignTarget] = useState<User | null>(null)
  /** 角色分配对话框中选中的角色 ID 集合 */
  const [selectedRoleIds, setSelectedRoleIds] = useState<Set<number>>(new Set())
  /** 角色分配提交加载状态 */
  const [roleAssignLoading, setRoleAssignLoading] = useState(false)

  /** 构建分页查询 URL */
  const queryUrl = useMemo(
    () => `/api/v1/users?page=${page}&size=${size}`,
    [page, size],
  )

  /** 获取用户列表数据 */
  const { data, loading, refetch } = useQuery<PageResult<User>>(
    () => get<PageResult<User>>(queryUrl),
    [queryUrl],
  )

  /** 获取所有可用角色（用于角色分配对话框） */
  const { data: allRoles } = useQuery<Role[]>(
    () => get<Role[]>('/api/v1/roles'),
    [],
  )

  /** 切换用户状态（启用/禁用） */
  const { mutate: toggleStatus, loading: toggling } = useMutation<User, { id: number; status: string }>(
    ({ id, status }) => put<User>(`/api/v1/users/${id}/status`, { status }),
  )

  /** 分配用户角色 */
  const { mutate: assignRoles } = useMutation<User, { id: number; roleIds: number[] }>(
    ({ id, roleIds }) => put<User>(`/api/v1/users/${id}/roles`, { roleIds }),
  )

  /** 删除用户 */
  const { mutate: deleteUser, loading: deleting } = useMutation<void, number>(
    (id) => del<void>(`/api/v1/users/${id}`),
  )

  /**
   * 处理切换用户状态操作
   *
   * ACTIVE 用户切换为 DISABLED，DISABLED 用户切换为 ACTIVE。
   * @param user - 目标用户
   */
  const handleToggleStatus = async (user: User) => {
    const newStatus = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
    const result = await toggleStatus({ id: user.id, status: newStatus })
    if (result !== null) {
      toast.success(newStatus === 'ACTIVE' ? `已启用用户「${user.username}」` : `已禁用用户「${user.username}」`)
      refetch()
    } else {
      toast.error('操作失败，请重试')
    }
  }

  /**
   * 处理删除用户操作（需二次确认）
   */
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return
    const result = await deleteUser(deleteTarget.id)
    if (result !== null) {
      toast.success(`已删除用户「${deleteTarget.username}」`)
      setDeleteTarget(null)
      refetch()
    } else {
      toast.error('删除用户失败')
    }
  }

  /**
   * 打开角色分配对话框
   *
   * 根据目标用户当前已有的角色名称，预选对应的角色复选框。
   * @param user - 目标用户
   */
  const openRoleAssignDialog = (user: User) => {
    setRoleAssignTarget(user)

    // 根据用户已有的角色名称匹配角色 ID
    const roleNames = new Set(user.roles ?? [])
    const preselected = new Set<number>()
    for (const role of allRoles ?? []) {
      if (roleNames.has(role.name) || roleNames.has(role.code)) {
        preselected.add(role.id)
      }
    }
    setSelectedRoleIds(preselected)
  }

  /**
   * 切换角色复选框选中状态
   *
   * @param roleId - 角色 ID
   */
  const handleRoleToggle = (roleId: number) => {
    setSelectedRoleIds((prev) => {
      const next = new Set(prev)
      if (next.has(roleId)) {
        next.delete(roleId)
      } else {
        next.add(roleId)
      }
      return next
    })
  }

  /**
   * 提交角色分配
   *
   * 将选中的角色 ID 列表提交到后端，更新用户角色关联。
   */
  const handleRoleAssignSubmit = async () => {
    if (!roleAssignTarget) return

    setRoleAssignLoading(true)
    const result = await assignRoles({
      id: roleAssignTarget.id,
      roleIds: Array.from(selectedRoleIds),
    })
    setRoleAssignLoading(false)

    if (result !== null) {
      toast.success(`已更新用户「${roleAssignTarget.username}」的角色`)
      setRoleAssignTarget(null)
      setSelectedRoleIds(new Set())
      refetch()
    } else {
      toast.error('角色分配失败，请重试')
    }
  }

  /** 表格列定义 */
  const columns: Column<User & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'username',
      header: '用户名',
      render: (item) => (
        <span className="font-medium">{item.username}</span>
      ),
    },
    {
      key: 'email',
      header: '邮箱',
      render: (item) => item.email ?? '—',
    },
    {
      key: 'phone',
      header: '手机号',
      render: (item) => item.phone ?? '—',
    },
    {
      key: 'status',
      header: '状态',
      render: (item) => (
        <Badge className={getStatusBadgeClass(item.status)}>
          {getStatusLabel(item.status)}
        </Badge>
      ),
    },
    {
      key: 'userType',
      header: '用户类型',
      render: (item) => getUserTypeLabel(item.userType),
    },
    {
      key: 'roles',
      header: '角色',
      render: (item) => {
        const roles = item.roles ?? []
        if (roles.length === 0) return <span className="text-muted-foreground">—</span>
        return (
          <div className="flex flex-wrap gap-1">
            {roles.map((role) => (
              <Badge key={role} variant="secondary" className="text-xs">
                {role}
              </Badge>
            ))}
          </div>
        )
      },
    },
    {
      key: 'createdAt',
      header: '创建时间',
      render: (item) => formatDate(item.createdAt),
    },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex items-center justify-end gap-2">
          {/* 启用/禁用切换按钮 */}
          <Button
            variant="outline"
            size="sm"
            disabled={toggling}
            onClick={() => { void handleToggleStatus(item as User) }}
          >
            {item.status === 'ACTIVE' ? '禁用' : '启用'}
          </Button>

          {/* 分配角色按钮 */}
          <Button
            variant="outline"
            size="sm"
            onClick={() => openRoleAssignDialog(item as User)}
          >
            <UserCog className="size-4" />
            角色
          </Button>

          {/* 删除按钮 */}
          <Button
            variant="destructive"
            size="sm"
            disabled={deleting}
            onClick={() => setDeleteTarget(item as User)}
          >
            删除
          </Button>
        </div>
      ),
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [toggling, deleting, allRoles])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题和操作按钮 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">用户管理</h1>
        <Button onClick={() => setCreateDialogOpen(true)}>
          <Plus className="size-4" />
          添加用户
        </Button>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        data={(data?.list ?? []) as (User & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无用户数据"
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

      {/* 添加用户对话框 */}
      <UserFormDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        onSuccess={refetch}
      />

      {/* 角色分配对话框 */}
      <Dialog
        open={roleAssignTarget !== null}
        onOpenChange={(open) => {
          if (!open) {
            setRoleAssignTarget(null)
            setSelectedRoleIds(new Set())
          }
        }}
      >
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>分配角色</DialogTitle>
            <DialogDescription>
              为用户「{roleAssignTarget?.username ?? ''}」选择要分配的角色。
            </DialogDescription>
          </DialogHeader>

          {/* 角色复选框列表 */}
          <div className="space-y-3 py-2">
            {(allRoles ?? []).length === 0 ? (
              <p className="text-sm text-muted-foreground">暂无可用角色</p>
            ) : (
              (allRoles ?? []).map((role) => (
                <div
                  key={role.id}
                  className="flex items-center gap-3 rounded-md border p-3 hover:bg-muted/50"
                >
                  <input
                    type="checkbox"
                    id={`role-${role.id}`}
                    checked={selectedRoleIds.has(role.id)}
                    onChange={() => handleRoleToggle(role.id)}
                    disabled={roleAssignLoading}
                    className="size-4 rounded border-gray-300 accent-primary"
                  />
                  <Label
                    htmlFor={`role-${role.id}`}
                    className="flex flex-1 cursor-pointer flex-col gap-0.5"
                  >
                    <span className="text-sm font-medium">
                      {role.name}
                      {role.isSystem && (
                        <Badge variant="outline" className="ml-2 text-xs">
                          系统
                        </Badge>
                      )}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {role.code}{role.description ? ` — ${role.description}` : ''}
                    </span>
                  </Label>
                </div>
              ))
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setRoleAssignTarget(null)
                setSelectedRoleIds(new Set())
              }}
              disabled={roleAssignLoading}
            >
              取消
            </Button>
            <Button
              onClick={() => { void handleRoleAssignSubmit() }}
              disabled={roleAssignLoading}
            >
              {roleAssignLoading && <Loader2 className="size-4 animate-spin" />}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认对话框 */}
      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}
        title="确认删除用户"
        description={`确定要删除用户「${deleteTarget?.username ?? ''}」吗？此操作不可撤销。`}
        confirmText="删除"
        destructive
        onConfirm={handleDeleteConfirm}
        loading={deleting}
      />
    </div>
  )
}
