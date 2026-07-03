import { useNavigate } from "react-router-dom"
import { LayoutDashboard, Zap, Shield, Building2, ArrowRight } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/contexts/auth-context"
import { ModeToggle } from "@/components/mode-toggle"

/**
 * 首页 — 产品落地页
 *
 * 展示 Converge API 的核心价值主张，引导用户注册或申请入驻。
 */
export default function HomePage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  /** 点击控制台按钮：已登录跳转控制台，否则跳转登录 */
  function handleConsole() {
    if (isAuthenticated) {
      navigate("/console")
    } else {
      navigate("/login")
    }
  }

  return (
    <div className="min-h-screen bg-background text-foreground flex flex-col">
      {/* 顶部导航 */}
      <header className="flex items-center justify-between p-4 border-b">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-md bg-primary flex items-center justify-center">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
            </svg>
          </div>
          <span className="font-medium tracking-wide">Converge</span>
        </div>
        <div className="flex items-center gap-2">
          <ModeToggle />
          <Button variant="outline" size="sm" onClick={handleConsole} className="gap-1.5">
            <LayoutDashboard className="h-3.5 w-3.5" />
            控制台
          </Button>
        </div>
      </header>

      {/* 主内容 */}
      <main className="flex-1">
        {/* Hero 区域 */}
        <section className="relative overflow-hidden">
          {/* 背景渐变层 */}
          <div className="absolute inset-0 bg-gradient-to-b from-[oklch(0.15_0.02_260)] via-[oklch(0.18_0.04_270)] to-background opacity-40" />
          <div className="relative flex flex-col items-center text-center px-6 py-24 sm:py-32">
            <h1 className="text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight text-foreground leading-tight">
              统一数据聚合层
            </h1>
            <p className="mt-6 text-lg sm:text-xl text-muted-foreground max-w-2xl leading-relaxed">
              以更低的延迟和更高的可靠性连接一切服务。Converge API 为多租户场景提供高性能、安全的数据聚合能力。
            </p>
            <div className="mt-10 flex flex-col sm:flex-row items-center gap-4">
              <Button
                size="lg"
                className="gap-2 px-8 h-12 text-base"
                onClick={() => navigate("/register")}
              >
                开始使用
                <ArrowRight className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="lg"
                className="gap-2 px-8 h-12 text-base"
                onClick={() => navigate("/apply")}
              >
                申请入驻
              </Button>
            </div>
          </div>
        </section>

        {/* 特性展示区域 */}
        <section className="px-6 py-20 sm:py-28">
          <div className="max-w-5xl mx-auto">
            <h2 className="text-center text-2xl sm:text-3xl font-semibold tracking-tight text-foreground mb-14">
              为什么选择 Converge
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
              {/* 高性能 */}
              <div className="rounded-xl border bg-card p-8 space-y-4 transition-colors hover:border-primary/40">
                <div className="w-11 h-11 rounded-lg bg-[oklch(0.65_0.2_260)]/15 flex items-center justify-center">
                  <Zap className="h-5 w-5 text-[oklch(0.65_0.2_260)]" />
                </div>
                <h3 className="text-lg font-semibold text-foreground">高性能</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  平均响应延迟低于 50ms，支撑千万级日请求量。异步处理与智能缓存保障极致体验。
                </p>
              </div>

              {/* 安全可靠 */}
              <div className="rounded-xl border bg-card p-8 space-y-4 transition-colors hover:border-primary/40">
                <div className="w-11 h-11 rounded-lg bg-[oklch(0.65_0.2_275)]/15 flex items-center justify-center">
                  <Shield className="h-5 w-5 text-[oklch(0.65_0.2_275)]" />
                </div>
                <h3 className="text-lg font-semibold text-foreground">安全可靠</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  HttpOnly Cookie 认证与 Token 自动刷新机制，数据传输全程加密，99.9% 服务可用性。
                </p>
              </div>

              {/* 多租户 */}
              <div className="rounded-xl border bg-card p-8 space-y-4 transition-colors hover:border-primary/40">
                <div className="w-11 h-11 rounded-lg bg-[oklch(0.65_0.2_285)]/15 flex items-center justify-center">
                  <Building2 className="h-5 w-5 text-[oklch(0.65_0.2_285)]" />
                </div>
                <h3 className="text-lg font-semibold text-foreground">多租户</h3>
                <p className="text-sm text-muted-foreground leading-relaxed">
                  原生多租户架构，租户间数据完全隔离。灵活的租户管理与角色权限体系，快速接入团队。
                </p>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* 页脚 */}
      <footer className="border-t px-6 py-6">
        <p className="text-center text-sm text-muted-foreground">
          &copy; {new Date().getFullYear()} Converge. All rights reserved.
        </p>
      </footer>
    </div>
  )
}
