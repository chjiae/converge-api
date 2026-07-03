/**
 * 通知中心页面
 *
 * 所有已登录用户均可访问，功能包括：
 * - 分页展示通知卡片列表
 * - 按通知类型以不同颜色徽章标识
 * - 未读通知左侧高亮提示
 * - 点击卡片标记为已读
 * - 一键全部标记已读
 */

import { useState, useMemo } from 'react'
import { useQuery, useMutation } from '@/hooks/use-api'
import { get, put } from '@/lib/api-client'
import type { Notification, PageResult } from '@/lib/types'
import Pagination from '@/components/pagination'
import { PageSkeleton } from '@/components/loading-skeleton'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { toast } from 'sonner'
import { CheckCheck, Mail, MailOpen, Clock } from 'lucide-react'

/** 通知类型到中文标签的映射 */
const TYPE_LABELS: Record<string, string> = {
  SYSTEM: '系统通知',
  AUDIT_RESULT: '审核结果',
  EXPIRY_WARNING: '到期提醒',
  SUBSCRIPTION: '订阅相关',
}

/** 通知类型到徽章样式的映射（使用 Tailwind 自定义颜色类） */
const TYPE_BADGE_CLASSES: Record<string, string> = {
  SYSTEM: 'bg-blue-500/10 text-blue-600 border-blue-500/20 dark:text-blue-400',
  AUDIT_RESULT: 'bg-purple-500/10 text-purple-600 border-purple-500/20 dark:text-purple-400',
  EXPIRY_WARNING: 'bg-yellow-500/10 text-yellow-700 border-yellow-500/20 dark:text-yellow-400',
  SUBSCRIPTION: 'bg-green-500/10 text-green-600 border-green-500/20 dark:text-green-400',
}

/**
 * 格式化日期时间字符串
 * @param dateStr - ISO 日期时间字符串
 * @returns 格式化后的本地日期时间
 */
function formatDateTime(dateStr: string): string {
  return new Date(dateStr).toLocaleString('zh-CN')
}

/**
 * 通知中心页面组件
 *
 * 以卡片列表形式展示用户通知，支持分页、标记已读和全部标记已读操作。
 */
export default function NotificationListPage() {
  /** 当前页码 */
  const [page, setPage] = useState(1)
  /** 每页条数 */
  const [size, setSize] = useState(10)

  /** 构建分页查询 URL */
  const queryUrl = useMemo(() => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
    })
    return `/api/v1/notifications?${params.toString()}`
  }, [page, size])

  /** 获取通知列表数据 */
  const { data, loading, refetch } = useQuery<PageResult<Notification>>(
    () => get<PageResult<Notification>>(queryUrl),
    [queryUrl],
  )

  /** 标记单条通知为已读 */
  const { mutate: markRead, loading: markingRead } = useMutation<void, number>(
    (id) => put<void>(`/api/v1/notifications/${id}/read`),
  )

  /** 全部标记已读 */
  const { mutate: markAllRead, loading: markingAllRead } = useMutation<void, void>(
    () => put<void>('/api/v1/notifications/read-all'),
  )

  /**
   * 处理点击通知卡片
   * 未读通知点击后标记为已读，并刷新列表
   */
  const handleCardClick = async (notification: Notification) => {
    if (notification.isRead) return

    const result = await markRead(notification.id)
    if (result !== null) {
      // 标记成功，刷新列表以更新状态
      refetch()
    }
  }

  /**
   * 处理全部标记已读
   * 操作成功后刷新列表
   */
  const handleMarkAllRead = async () => {
    const result = await markAllRead(undefined)
    if (result !== null) {
      toast.success('已将所有通知标记为已读')
      refetch()
    }
  }

  /** 首次加载中展示骨架屏 */
  if (loading && !data) {
    return <PageSkeleton />
  }

  return (
    <div className="space-y-6">
      {/* 页面标题和操作按钮 */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">通知中心</h1>
        <Button
          variant="outline"
          onClick={() => { void handleMarkAllRead() }}
          disabled={markingAllRead || !data?.list.some((n) => !n.isRead)}
        >
          <CheckCheck className="mr-1.5 size-4" />
          全部标记已读
        </Button>
      </div>

      {/* 通知卡片列表 */}
      {data?.list.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <Mail className="mb-3 size-10" />
          <p>暂无通知</p>
        </div>
      ) : (
        <div className="space-y-3">
          {data?.list.map((notification) => {
            /** 获取通知类型对应的徽章样式类 */
            const badgeClass = TYPE_BADGE_CLASSES[notification.type] ?? ''
            /** 获取通知类型对应的中文标签 */
            const typeLabel = TYPE_LABELS[notification.type] ?? notification.type

            return (
              <Card
                key={notification.id}
                className={`cursor-pointer transition-colors hover:bg-muted/50 ${
                  !notification.isRead
                    ? 'border-l-4 border-l-primary'
                    : ''
                }`}
                onClick={() => { void handleCardClick(notification) }}
              >
                <CardContent className="flex items-start gap-4 p-4">
                  {/* 已读/未读图标 */}
                  <div className="mt-0.5 shrink-0">
                    {notification.isRead ? (
                      <MailOpen className="size-5 text-muted-foreground" />
                    ) : (
                      <Mail className="size-5 text-primary" />
                    )}
                  </div>

                  {/* 通知内容区域 */}
                  <div className="min-w-0 flex-1 space-y-1.5">
                    {/* 标题行：类型徽章 + 标题 */}
                    <div className="flex items-center gap-2">
                      <Badge
                        variant="outline"
                        className={badgeClass}
                      >
                        {typeLabel}
                      </Badge>
                      <span className={`truncate text-sm font-medium ${
                        !notification.isRead ? 'text-foreground' : 'text-muted-foreground'
                      }`}>
                        {notification.title}
                      </span>
                    </div>

                    {/* 通知内容（截断显示） */}
                    <p className="line-clamp-2 text-sm text-muted-foreground">
                      {notification.content}
                    </p>

                    {/* 创建时间 */}
                    <div className="flex items-center gap-1 text-xs text-muted-foreground">
                      <Clock className="size-3" />
                      <span>{formatDateTime(notification.createdAt)}</span>
                    </div>
                  </div>

                  {/* 已读状态标记 */}
                  {!notification.isRead && !markingRead && (
                    <span className="shrink-0 text-xs text-primary font-medium">未读</span>
                  )}
                </CardContent>
              </Card>
            )
          })}
        </div>
      )}

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
    </div>
  )
}
