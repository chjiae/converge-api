/**
 * 应用入口
 *
 * 初始化 React 根节点，挂载全局 Provider 层级：
 * - StrictMode：启用严格模式检查
 * - BrowserRouter：客户端路由
 * - ThemeProvider：主题管理（暗色/亮色/跟随系统）
 * - TooltipProvider：全局 Tooltip 支持
 * - AuthProvider：认证状态管理
 */

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import ErrorBoundary from '@/components/error-boundary'
import { ThemeProvider } from '@/components/theme-provider'
import { TooltipProvider } from '@/components/ui/tooltip'
import { AuthProvider } from '@/contexts/auth-context'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <ThemeProvider defaultTheme="dark" storageKey="vite-ui-theme">
        <TooltipProvider>
          <AuthProvider>
            <ErrorBoundary>
              <App />
            </ErrorBoundary>
          </AuthProvider>
        </TooltipProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
