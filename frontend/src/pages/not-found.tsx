/**
 * 404 页面
 *
 * 当用户访问不存在的路由时展示。
 * 提供返回首页和控制台的导航链接。
 */

import { Link } from 'react-router-dom'

/**
 * 404 未找到页面组件
 */
export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="text-6xl font-bold text-muted-foreground">404</h1>
      <p className="text-xl text-muted-foreground">页面未找到</p>
      <div className="flex gap-4">
        <Link
          to="/"
          className="text-primary underline hover:no-underline"
        >
          返回首页
        </Link>
        <Link
          to="/console"
          className="text-primary underline hover:no-underline"
        >
          返回控制台
        </Link>
      </div>
    </div>
  )
}
