/**
 * 审计日志页面
 *
 * 超管控制台 — 系统审计日志查看。
 * 功能包括：
 * - 分页展示所有审计日志记录
 * - 按操作模块筛选
 * - 查看日志详细信息（展开详情对话框）
 */

import { useState, useMemo } from 'react'
import { useQuery } from '@/hooks/use-api'
import { get } from '@/lib/api-client'
import type { AuditLog, PageResult } from '@/lib/types'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Eye } from 'lucide-react'

/**
 * 格式化日期时间字符串
 * @param dateStr - ISO 日期时间字符串
 * @returns 格式化后的本地日期时间
 */
function formatDateTime(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString('zh-CN')
}

/**
 * 审计日志页面组件
 *
 * 仅超级管理员可访问，提供系统操作日志的分页查看和筛选功能。
 */
export default function AuditLogPage() {
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)
  /** 模块筛选值，'ALL' 表示全部 */
  const [moduleFilter, setModuleFilter] = useState('ALL')
  /** 详情对话框目标日志 */
  const [detailTarget, setDetailTarget] = useState<AuditLog | null>(null)

  /** 构建查询 URL，包含分页和可选的模块筛选参数 */
  const queryUrl = useMemo(() => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    if (moduleFilter !== 'ALL') {
      params.set('module', moduleFilter)
    }
    return `/api/v1/audit-logs?${params.toString()}`
  }, [page, size, moduleFilter])

  /** 获取审计日志列表数据 */
  const { data, loading } = useQuery<PageResult<AuditLog>>(
    () => get<PageResult<AuditLog>>(queryUrl),
    [queryUrl],
  )

  /**
   * 从当前页数据中提取唯一的模块列表
   * 用于筛选下拉选项
   */
  const moduleOptions = useMemo(() => {
    if (!data?.list) return []
    const modules = new Set<string>()
    for (const log of data.list) {
      if (log.module) {
        modules.add(log.module)
      }
    }
    return Array.from(modules).sort()
  }, [data?.list])

  /**
   * 处理模块筛选变更
   * 切换筛选条件时自动回到第一页
   */
  const handleModuleFilterChange = (value: string) => {
    setModuleFilter(value)
    setPage(1)
  }

  /** 表格列定义 */
  const columns: Column<AuditLog & Record<string, unknown>>[] = useMemo(() => [
    {
      key: 'username',
      header: '操作用户',
      render: (item) => (
        <span className="font-medium">{item.username ?? '—'}</span>
      ),
    },
    {
      key: 'module',
      header: '操作模块',
      render: (item) => (
        <span className="inline-block rounded bg-muted px-2 py-0.5 text-xs font-medium">
          {item.module}
        </span>
      ),
    },
    {
      key: 'action',
      header: '操作动作',
    },
    {
      key: 'target',
      header: '操作目标',
      render: (item) => item.target ?? '—',
    },
    {
      key: 'ipAddress',
      header: 'IP 地址',
      render: (item) => (
        <span className="font-mono text-xs">{item.ipAddress ?? '—'}</span>
      ),
    },
    {
      key: 'createdAt',
      header: '操作时间',
      render: (item) => formatDateTime(item.createdAt),
    },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setDetailTarget(item as AuditLog)}
        >
          <Eye className="size-4" />
          详情
        </Button>
      ),
    },
  ], [])

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题和筛选区域 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">审计日志</h1>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground">模块筛选：</span>
          <Select value={moduleFilter} onValueChange={handleModuleFilterChange}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">全部</SelectItem>
              {moduleOptions.map((mod) => (
                <SelectItem key={mod} value={mod}>
                  {mod}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* 数据表格 */}
      <DataTable
        columns={columns}
        data={(data?.list ?? []) as (AuditLog & Record<string, unknown>)[]}
        loading={loading}
        emptyText="暂无审计日志"
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

      {/* 日志详情对话框 */}
      <Dialog
        open={detailTarget !== null}
        onOpenChange={(open) => { if (!open) setDetailTarget(null) }}
      >
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>日志详情</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            {/* 基本信息 */}
            <div className="grid grid-cols-2 gap-2 text-sm">
              <div>
                <span className="text-muted-foreground">操作用户：</span>
                <span className="font-medium">{detailTarget?.username ?? '—'}</span>
              </div>
              <div>
                <span className="text-muted-foreground">操作模块：</span>
                <span className="font-medium">{detailTarget?.module ?? '—'}</span>
              </div>
              <div>
                <span className="text-muted-foreground">操作动作：</span>
                <span className="font-medium">{detailTarget?.action ?? '—'}</span>
              </div>
              <div>
                <span className="text-muted-foreground">操作目标：</span>
                <span className="font-medium">{detailTarget?.target ?? '—'}</span>
              </div>
              <div>
                <span className="text-muted-foreground">IP 地址：</span>
                <span className="font-mono text-xs">{detailTarget?.ipAddress ?? '—'}</span>
              </div>
              <div>
                <span className="text-muted-foreground">操作时间：</span>
                <span>{formatDateTime(detailTarget?.createdAt ?? null)}</span>
              </div>
            </div>
            {/* 详细信息 */}
            {detailTarget?.detail && (
              <div className="space-y-1">
                <span className="text-sm text-muted-foreground">详细信息：</span>
                <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs whitespace-pre-wrap">
                  {tryFormatJson(detailTarget.detail)}
                </pre>
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}

/**
 * 尝试将字符串格式化为 JSON 缩进显示
 * 如果解析失败则返回原始文本
 *
 * @param text - 原始文本内容
 * @returns 格式化后的 JSON 或原始文本
 */
function tryFormatJson(text: string): string {
  try {
    const parsed = JSON.parse(text) as unknown
    return JSON.stringify(parsed, null, 2)
  } catch {
    // 非 JSON 格式，直接返回原始文本
    return text
  }
}
