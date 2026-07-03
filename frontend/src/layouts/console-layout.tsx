/**
 * 控制台布局组件
 *
 * 包裹所有 /console 下的子路由页面，提供完整的控制台布局框架：
 * - 桌面端：左侧可折叠的侧边栏 + 右侧内容区（顶栏 + 主内容）
 * - 移动端：通过 Sheet 抽屉展示侧边栏，顶栏提供汉堡菜单按钮
 *
 * 使用 Sheet 组件实现移动端侧边栏抽屉，
 * Toaster 组件挂载在此处以支持全局通知弹出。
 */

import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from '@/components/sidebar'
import TopBar from '@/components/top-bar'
import { Sheet, SheetContent } from '@/components/ui/sheet'
import { Toaster } from '@/components/ui/sonner'

/**
 * 控制台布局组件
 *
 * 响应式布局：
 * - lg 以上：侧边栏常驻显示（支持折叠）
 * - lg 以下：侧边栏通过 Sheet 抽屉显示
 */
export default function ConsoleLayout() {
  /** 移动端侧边栏抽屉是否打开 */
  const [sidebarOpen, setSidebarOpen] = useState(false)
  /** 桌面端侧边栏是否折叠 */
  const [collapsed, setCollapsed] = useState(false)

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* 桌面端侧边栏 */}
      <aside className="hidden lg:block">
        <Sidebar collapsed={collapsed} />
      </aside>

      {/* 移动端侧边栏抽屉 */}
      <Sheet open={sidebarOpen} onOpenChange={setSidebarOpen}>
        <SheetContent side="left" className="w-64 p-0" showCloseButton={false}>
          <Sidebar collapsed={false} />
        </SheetContent>
      </Sheet>

      {/* 右侧主内容区域 */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <TopBar
          onMenuClick={() => setSidebarOpen(true)}
          collapsed={collapsed}
          onToggleCollapse={() => setCollapsed((c) => !c)}
        />
        <main className="flex-1 overflow-y-auto p-4 md:p-6">
          <Outlet />
        </main>
      </div>

      {/* 全局通知弹出层 */}
      <Toaster position="top-right" richColors closeButton />
    </div>
  )
}
