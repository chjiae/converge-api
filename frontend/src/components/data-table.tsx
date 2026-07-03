/**
 * 通用数据表格组件
 *
 * 基于 shadcn Table 封装的泛型数据表格，支持：
 * - 自定义列渲染
 * - 加载骨架屏状态
 * - 空数据占位展示
 * - 响应式水平滚动
 *
 * @template T - 表格行数据类型
 */

import type { ReactNode } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { InboxIcon } from 'lucide-react'

/** 列定义 */
export interface Column<T> {
  /** 列唯一标识键 */
  key: string
  /** 列头显示文本 */
  header: string
  /** 自定义单元格渲染函数，不提供时默认显示对应字段值 */
  render?: (item: T) => ReactNode
  /** 列自定义类名 */
  className?: string
}

/** 数据表格属性 */
export interface DataTableProps<T> {
  /** 列定义数组 */
  columns: Column<T>[]
  /** 表格数据 */
  data: T[]
  /** 是否正在加载 */
  loading?: boolean
  /** 空数据时显示的提示文本 */
  emptyText?: string
  /** 空数据时显示的图标 */
  emptyIcon?: ReactNode
  /** 加载骨架屏行数，默认 5 */
  skeletonRows?: number
}

/**
 * 通用数据表格组件
 *
 * 使用泛型 T 约束行数据类型，通过 columns 定义列结构和渲染逻辑。
 * 当 loading 为 true 时展示骨架屏，data 为空时展示空状态占位。
 */
export function DataTable<T extends Record<string, unknown>>({
  columns,
  data,
  loading = false,
  emptyText = '暂无数据',
  emptyIcon,
  skeletonRows = 5,
}: DataTableProps<T>) {
  return (
    <div className="overflow-x-auto rounded-md border">
      <Table>
        <TableHeader>
          <TableRow>
            {columns.map((col) => (
              <TableHead key={col.key} className={col.className}>
                {col.header}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {/* 加载状态：展示骨架屏 */}
          {loading ? (
            Array.from({ length: skeletonRows }).map((_, rowIdx) => (
              <TableRow key={rowIdx}>
                {columns.map((col) => (
                  <TableCell key={col.key} className={col.className}>
                    <Skeleton className="h-4 w-full" />
                  </TableCell>
                ))}
              </TableRow>
            ))
          ) : data.length === 0 ? (
            /* 空状态：居中展示图标和提示文本 */
            <TableRow>
              <TableCell
                colSpan={columns.length}
                className="h-32 text-center"
              >
                <div className="flex flex-col items-center gap-2 text-muted-foreground">
                  {emptyIcon ?? <InboxIcon className="size-8" />}
                  <span className="text-sm">{emptyText}</span>
                </div>
              </TableCell>
            </TableRow>
          ) : (
            /* 数据行渲染 */
            data.map((item, rowIdx) => (
              <TableRow key={rowIdx}>
                {columns.map((col) => (
                  <TableCell key={col.key} className={col.className}>
                    {col.render
                      ? col.render(item)
                      : String(item[col.key] ?? '')}
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  )
}
