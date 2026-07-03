/**
 * 通知铃铛组件
 *
 * 用于顶部导航栏，显示未读通知数量徽章。
 * 功能包括：
 * - 定时轮询未读通知数量（每 30 秒一次）
 * - 点击跳转到通知中心页面
 * - 未读数量大于 0 时显示红色徽章
 */

import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell } from 'lucide-react'
import { get } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

/** 轮询间隔：30 秒 */
const POLL_INTERVAL_MS = 30_000

/**
 * 通知铃铛组件
 *
 * 在挂载时立即获取一次未读数，随后每 30 秒轮询一次。
 * 组件卸载时自动清除定时器，避免内存泄漏。
 */
export default function NotificationBell() {
  const navigate = useNavigate()
  /** 未读通知数量 */
  const [unreadCount, setUnreadCount] = useState(0)

  /** 用于组件卸载时清除定时器 */
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  /** 获取未读通知数量 */
  const fetchUnreadCount = useCallback(async () => {
    try {
      const count = await get<number>('/api/v1/notifications/unread-count')
      setUnreadCount(count)
    } catch {
      // 获取失败时静默处理，不影响正常使用
    }
  }, [])

  /** 挂载时立即获取一次，并启动定时轮询 */
  useEffect(() => {
    // 立即获取
    void fetchUnreadCount()

    // 启动轮询定时器
    timerRef.current = setInterval(() => {
      void fetchUnreadCount()
    }, POLL_INTERVAL_MS)

    // 组件卸载时清除定时器
    return () => {
      if (timerRef.current !== null) {
        clearInterval(timerRef.current)
        timerRef.current = null
      }
    }
  }, [fetchUnreadCount])

  /** 点击铃铛跳转到通知中心 */
  const handleClick = () => {
    navigate('/console/notifications')
  }

  return (
    <Button
      variant="ghost"
      size="icon"
      className="relative"
      onClick={handleClick}
    >
      <Bell className="size-5" />
      {/* 未读数量大于 0 时显示红色徽章 */}
      {unreadCount > 0 && (
        <Badge
          variant="destructive"
          className="absolute -top-1 -right-1 flex size-5 items-center justify-center rounded-full p-0 text-[10px]"
        >
          {/* 超过 99 条显示 99+ */}
          {unreadCount > 99 ? '99+' : unreadCount}
        </Badge>
      )}
      <span className="sr-only">通知中心</span>
    </Button>
  )
}
