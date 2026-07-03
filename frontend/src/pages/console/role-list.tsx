/**
 * 角色管理页面
 *
 * 租户控制台 — 角色管理主页面。
 * 功能包括：
 * - 展示租户内所有角色（不分页）
 * - 创建新角色
 * - 编辑自定义角色（系统角色不可编辑）
 * - 删除自定义角色（系统角色不可删除，需二次确认）
 */

import { useState, useMemo } from 'react'
import { Plus } from 'lucide-react'
import { toast } from 'sonner'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, del } from '@/lib/api-client'
import type { Role } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import { PageSkeleton } from '@/components/loading-skeleton'
import ConfirmDialog from '@/components/confirm-dialog'
import RoleFormDialog from '@/pages/console/role-form-dialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

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
 * 角色管理页面组件
 *
 * 供租户所有者和租户管理员使用，
 * 提供租户内角色的完整管理功能。
 */
export default function RoleListPage() {
  /** 角色表单对话框显示状态 */
  const [formDialogOpen, setFormDialogOpen] = useState(false)
  /** 当前编辑的角色，null 表示创建模式 */
  const [editingRole, setEditingRole] = useState<Role | null>(null)
  /** 删除确认对话框目标角色 */
  const [deleteTarget, setDeleteTarget] = useState<Role | null>(null)

  /** 获取角色列表数据（不分页） */
  const { data, loading, refetch } = useQuery<Role[]>(
    () => get<Role[]>('/api/v1/roles'),
    [],
  )

  /** 删除角色 */
  const { mutate: deleteRole, loading: deleting } = useMutation<void, number>(
    (id) => del<void>(`/api/v1/roles/${id}`),
  )

  /**
   * 打开创建角色对话框
   */
  const openCreateDialog = () => {
    setEditingRole(null)
    setFormDialogOpen(true)
  }

  /**
   * 打开编辑角色对话框
   *
   * @param role - 要编辑的角色
   */
  const openEditDialog = (role: Role) => {
    setEditingRole(role)
    setFormDialogOpen(true)
  }

  /**
   * 处理删除角色操作（需二次确认）
   */
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return
    const result = await deleteRole(deleteTarget.id)
    if (result !== null) {
      toast.success(`已删除角色「${deleteTarget.name}」`)
      setDeleteTarget(null)
      refetch()
    } else {
      toast.error('删除角色失败')
    }
  }

  /** 表格列定义 */
  const columns: Column<Role & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'code',
      header: '角色编码',
      render: (item) => (
        <span className="font-mono text-sm">{item.code}</span>
      ),
    },
    {
      key: 'name',
      header: '角色名称',
      render: (item) => (
        <span className="font-medium">{item.name}</span>
      ),
    },
    {
      key: 'description',
      header: '描述',
      render: (item) => item.description ?? '—',
    },
    {
      key: 'isSystem',
      header: '类型',
      render: (item) => (
        item.isSystem
          ? <Badge className="bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200">系统角色</Badge>
          : <Badge variant="secondary">自定义</Badge>
      ),
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
      render: (item) => {
        // 系统角色不可编辑和删除
        if (item.isSystem) {
          return (
            <span className="text-xs text-muted-foreground">系统角色不可操作</span>
          )
        }

        return (
          <div className="flex items-center justify-end gap-2">
            {/* 编辑按钮 */}
            <Button
              variant="outline"
              size="sm"
              onClick={() => openEditDialog(item as Role)}
            >
              编辑
            </Button>

            {/* 删除按钮 */}
            <Button
              variant="destructive"
              size="sm"
              disabled={deleting}
              onClick={() => setDeleteTarget(item as Role)}
            >
              删除
            </Button>
          </div>
        )
      },
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
  ], [deleting])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题和操作按钮 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">角色管理</h1>
        <Button onClick={openCreateDialog}>
          <Plus className="size-4" />
          创建角色
        </Button>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        data={(data ?? []) as (Role & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无角色数据"
      />

      {/* 创建/编辑角色对话框 */}
      <RoleFormDialog
        open={formDialogOpen}
        onOpenChange={setFormDialogOpen}
        editingRole={editingRole}
        onSuccess={refetch}
      />

      {/* 删除确认对话框 */}
      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}
        title="确认删除角色"
        description={`确定要删除角色「${deleteTarget?.name ?? ''}」吗？删除后已分配该角色的用户将失去对应权限。`}
        confirmText="删除"
        destructive
        onConfirm={handleDeleteConfirm}
        loading={deleting}
      />
    </div>
  )
}
