/**
 * 控制台布局（占位）
 *
 * 待 Task 4 实现完整的侧边栏和顶栏。
 * 当前仅作为路由嵌套的容器使用。
 */

import { Outlet } from 'react-router-dom'

/**
 * 控制台布局组件
 *
 * 包裹所有 /console 下的子路由页面，
 * 后续将添加侧边导航栏和顶部工具栏。
 */
export default function ConsoleLayout() {
  return (
    <div className="min-h-screen">
      <Outlet />
    </div>
  )
}
