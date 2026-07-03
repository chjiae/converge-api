/**
 * 加载骨架屏组件
 *
 * 提供两种预定义的骨架屏布局：
 * - PageSkeleton：整页加载占位（顶栏 + 内容块）
 * - TableSkeleton：表格加载占位（可配置行数）
 *
 * 用于数据加载期间展示占位内容，提升用户体验。
 */

import { Skeleton } from '@/components/ui/skeleton'

/**
 * 页面级加载骨架屏
 *
 * 模拟页面顶栏 + 多行内容块的布局，
 * 适用于整个页面数据加载中的场景。
 */
export function PageSkeleton() {
  return (
    <div className="space-y-6">
      {/* 模拟页面标题区域 */}
      <div className="flex items-center justify-between">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-9 w-24" />
      </div>

      {/* 模拟统计卡片区域 */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="rounded-lg border p-4">
            <Skeleton className="mb-2 h-4 w-20" />
            <Skeleton className="h-7 w-28" />
          </div>
        ))}
      </div>

      {/* 模拟内容列表区域 */}
      <div className="space-y-3">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    </div>
  )
}

/** 表格骨架屏属性 */
interface TableSkeletonProps {
  /** 骨架行数，默认 5 */
  rows?: number
  /** 列数，默认 5 */
  columns?: number
}

/**
 * 表格加载骨架屏
 *
 * 模拟表头 + 多行数据的表格布局，
 * 可配置行数和列数以匹配不同表格结构。
 */
export function TableSkeleton({ rows = 5, columns = 5 }: TableSkeletonProps) {
  return (
    <div className="overflow-x-auto rounded-md border">
      {/* 模拟表头 */}
      <div className="flex items-center gap-4 border-b px-4 py-3">
        {Array.from({ length: columns }).map((_, i) => (
          <Skeleton key={i} className="h-4 flex-1" />
        ))}
      </div>

      {/* 模拟数据行 */}
      {Array.from({ length: rows }).map((_, rowIdx) => (
        <div
          key={rowIdx}
          className="flex items-center gap-4 border-b px-4 py-3 last:border-0"
        >
          {Array.from({ length: columns }).map((_, colIdx) => (
            <Skeleton key={colIdx} className="h-4 flex-1" />
          ))}
        </div>
      ))}
    </div>
  )
}
