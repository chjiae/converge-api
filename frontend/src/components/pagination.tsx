/**
 * 分页组件
 *
 * 提供数据分页控件，包含：
 * - 总记录数显示
 * - 上一页 / 下一页按钮
 * - 页码按钮列表
 * - 每页条数选择器
 *
 * 支持响应式布局，小屏幕下隐藏部分页码按钮。
 */

import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

/** 分页组件属性 */
interface PaginationProps {
  /** 当前页码（从 1 开始） */
  page: number
  /** 每页条数 */
  size: number
  /** 总记录数 */
  total: number
  /** 页码变更回调 */
  onPageChange: (page: number) => void
  /** 每页条数变更回调 */
  onSizeChange?: (size: number) => void
}

/** 可选的每页条数选项 */
const PAGE_SIZE_OPTIONS = [10, 20, 50]

/**
 * 计算可见的页码列表
 *
 * 在当前页前后各展示 2 个页码，首尾始终展示，
 * 中间不连续的部分用省略号代替（在渲染层处理）。
 *
 * @param currentPage - 当前页码
 * @param totalPages - 总页数
 * @returns 页码数组（包含边界页和邻近页）
 */
function getVisiblePages(currentPage: number, totalPages: number): number[] {
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, i) => i + 1)
  }

  const pages = new Set<number>([1, totalPages])
  for (let i = Math.max(2, currentPage - 1); i <= Math.min(totalPages - 1, currentPage + 1); i++) {
    pages.add(i)
  }

  return Array.from(pages).sort((a, b) => a - b)
}

/**
 * 分页组件
 *
 * 显示总记录数、页码导航按钮和每页条数选择器。
 * 页码按钮会根据总页数智能显示，避免过多按钮。
 */
export default function Pagination({
  page,
  size,
  total,
  onPageChange,
  onSizeChange,
}: PaginationProps) {
  /** 计算总页数 */
  const totalPages = Math.max(1, Math.ceil(total / size))
  const visiblePages = getVisiblePages(page, totalPages)

  /**
   * 渲染页码按钮列表
   * 不连续的页码之间插入省略号
   */
  const renderPageButtons = () => {
    const buttons: React.ReactNode[] = []
    let lastPage = 0

    for (const p of visiblePages) {
      // 如果与上一个页码不连续，插入省略号
      if (p - lastPage > 1) {
        buttons.push(
          <span
            key={`ellipsis-${p}`}
            className="flex size-8 items-center justify-center text-sm text-muted-foreground"
          >
            ...
          </span>,
        )
      }
      buttons.push(
        <Button
          key={p}
          variant={p === page ? 'default' : 'outline'}
          size="sm"
          className="size-8 p-0"
          onClick={() => onPageChange(p)}
        >
          {p}
        </Button>,
      )
      lastPage = p
    }

    return buttons
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-4 py-4">
      {/* 左侧：总记录数和每页条数选择 */}
      <div className="flex items-center gap-4 text-sm text-muted-foreground">
        <span>共 {total} 条</span>
        {onSizeChange && (
          <div className="flex items-center gap-2">
            <span>每页</span>
            <Select
              value={String(size)}
              onValueChange={(val) => onSizeChange(Number(val))}
            >
              <SelectTrigger size="sm">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PAGE_SIZE_OPTIONS.map((opt) => (
                  <SelectItem key={opt} value={String(opt)}>
                    {opt} 条
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
      </div>

      {/* 右侧：页码导航按钮 */}
      <div className="flex items-center gap-1">
        {/* 上一页 */}
        <Button
          variant="outline"
          size="sm"
          className="size-8 p-0"
          disabled={page <= 1}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft className="size-4" />
          <span className="sr-only">上一页</span>
        </Button>

        {/* 页码按钮列表 */}
        <div className="hidden items-center gap-1 sm:flex">
          {renderPageButtons()}
        </div>

        {/* 移动端当前页码显示 */}
        <span className="flex size-8 items-center justify-center text-sm sm:hidden">
          {page}/{totalPages}
        </span>

        {/* 下一页 */}
        <Button
          variant="outline"
          size="sm"
          className="size-8 p-0"
          disabled={page >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          <ChevronRight className="size-4" />
          <span className="sr-only">下一页</span>
        </Button>
      </div>
    </div>
  )
}
