import { useState } from "react"
import { useNavigate, Navigate, Link } from "react-router-dom"
import { Eye, EyeOff, ArrowRight, Loader2 } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/contexts/auth-context"
import { ModeToggle } from "@/components/mode-toggle"

export default function LoginPage() {
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState("")
  const [touched, setTouched] = useState({ username: false, password: false })
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()

  // 已登录用户直接重定向到控制台
  if (isAuthenticated) {
    return <Navigate to="/console" replace />
  }

  const usernameError = touched.username && username.length === 0
  const passwordError = touched.password && password.length < 1

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")

    if (!username || !password) {
      setError("请填写所有必填字段")
      return
    }

    setIsLoading(true)
    try {
      await login(username, password)
      setIsLoading(false)
      navigate("/console")
    } catch {
      setIsLoading(false)
      setError("用户名或密码不正确")
    }
  }

  return (
    <div className="min-h-screen flex">
      {/* Left: Brand Showcase Panel */}
      <div className="hidden lg:flex lg:w-[55%] relative overflow-hidden flex-col justify-between p-12 brand-panel">
        {/* Background layers */}
        <div className="absolute inset-0 bg-gradient-to-br from-[oklch(0.15_0.02_260)] via-[oklch(0.18_0.04_270)] to-[oklch(0.12_0.01_250)]" />
        <div className="absolute inset-0 brand-mesh" />
        <div className="absolute inset-0 brand-grain" />

        {/* Floating geometric shapes */}
        <div className="absolute inset-0 overflow-hidden">
          <div className="geo-shape geo-shape-1" />
          <div className="geo-shape geo-shape-2" />
          <div className="geo-shape geo-shape-3" />
          <div className="geo-shape geo-shape-4" />
          <div className="geo-shape geo-shape-5" />
        </div>

        {/* Grid overlay */}
        <div className="absolute inset-0 brand-grid" />

        {/* Content */}
        <div className="relative z-10 flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-[oklch(0.65_0.2_260)] flex items-center justify-center">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="white" strokeWidth="2" strokeLinejoin="round"/>
              <path d="M2 17L12 22L22 17" stroke="white" strokeWidth="2" strokeLinejoin="round"/>
              <path d="M2 12L12 17L22 12" stroke="white" strokeWidth="2" strokeLinejoin="round"/>
            </svg>
          </div>
          <span className="text-[oklch(0.9_0.01_260)] text-lg font-medium tracking-wide">Converge</span>
        </div>

        <div className="relative z-10 space-y-6">
          <h1 className="brand-heading text-5xl xl:text-6xl leading-[1.1] text-[oklch(0.92_0.01_260)]">
            构建下一代
            <br />
            <span className="text-[oklch(0.72_0.15_260)]">智能接口</span>
          </h1>
          <p className="text-[oklch(0.65_0.02_260)] text-lg max-w-md leading-relaxed">
            Converge API 提供统一的数据聚合层，让你的应用以更低延迟、更高可靠性连接一切。
          </p>
        </div>

        <div className="relative z-10 flex items-center gap-8">
          <div className="flex flex-col">
            <span className="text-2xl font-semibold text-[oklch(0.85_0.01_260)]">99.9%</span>
            <span className="text-sm text-[oklch(0.55_0.02_260)]">可用性</span>
          </div>
          <div className="w-px h-10 bg-[oklch(0.3_0.02_260)]" />
          <div className="flex flex-col">
            <span className="text-2xl font-semibold text-[oklch(0.85_0.01_260)]">&lt;50ms</span>
            <span className="text-sm text-[oklch(0.55_0.02_260)]">平均延迟</span>
          </div>
          <div className="w-px h-10 bg-[oklch(0.3_0.02_260)]" />
          <div className="flex flex-col">
            <span className="text-2xl font-semibold text-[oklch(0.85_0.01_260)]">10M+</span>
            <span className="text-sm text-[oklch(0.55_0.02_260)]">日请求量</span>
          </div>
        </div>
      </div>

      {/* Right: Login Form */}
      <div className="flex-1 flex flex-col min-h-screen bg-background relative">
        {/* Theme toggle */}
        <div className="flex justify-end p-4">
          <ModeToggle />
        </div>

        {/* Form area */}
        <div className="flex-1 flex items-center justify-center px-6 pb-12">
          <div className="w-full max-w-[380px] space-y-8 login-form-enter">
            {/* Mobile brand mark */}
            <div className="lg:hidden flex items-center gap-3 mb-2">
              <div className="w-9 h-9 rounded-lg bg-primary flex items-center justify-center">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                  <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                  <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                </svg>
              </div>
              <span className="text-foreground text-lg font-medium tracking-wide">Converge</span>
            </div>

            <div className="space-y-2">
              <h2 className="text-2xl font-semibold tracking-tight text-foreground">
                欢迎回来
              </h2>
              <p className="text-sm text-muted-foreground">
                登录以访问你的控制台
              </p>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">
              {error && (
                <div className="text-sm text-destructive bg-destructive/10 rounded-md px-3 py-2.5 animate-in fade-in slide-in-from-top-1 duration-200">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="username">用户名</Label>
                <Input
                  id="username"
                  type="text"
                  placeholder="输入用户名"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  onBlur={() => setTouched((t) => ({ ...t, username: true }))}
                  className={usernameError ? "border-destructive focus-visible:ring-destructive" : ""}
                  autoComplete="username"
                  disabled={isLoading}
                />
                {usernameError && (
                  <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                    请输入用户名
                  </p>
                )}
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password">密码</Label>
                  <button
                    type="button"
                    className="text-xs text-muted-foreground hover:text-foreground transition-colors"
                  >
                    忘记密码？
                  </button>
                </div>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    placeholder="输入密码"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    onBlur={() => setTouched((t) => ({ ...t, password: true }))}
                    className={`pr-10 ${passwordError ? "border-destructive focus-visible:ring-destructive" : ""}`}
                    autoComplete="current-password"
                    disabled={isLoading}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-0.5"
                    tabIndex={-1}
                    aria-label={showPassword ? "隐藏密码" : "显示密码"}
                  >
                    {showPassword ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                </div>
                {passwordError && (
                  <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                    请输入密码
                  </p>
                )}
              </div>

              <Button
                type="submit"
                className="w-full h-10 group"
                disabled={isLoading}
              >
                {isLoading ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    登录中...
                  </>
                ) : (
                  <>
                    登录
                    <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
                  </>
                )}
              </Button>
            </form>

            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full border-t" />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="bg-background px-3 text-muted-foreground">
                  或
                </span>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Button variant="outline" className="h-10" disabled={isLoading}>
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none">
                  <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4"/>
                  <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                  <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                  <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
                </svg>
                Google
              </Button>
              <Button variant="outline" className="h-10" disabled={isLoading}>
                <svg className="h-4 w-4" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                </svg>
                GitHub
              </Button>
            </div>

            <p className="text-center text-sm text-muted-foreground">
              还没有账号？{" "}
              <Link
                to="/register"
                className="font-medium text-foreground hover:text-primary transition-colors underline-offset-4 hover:underline"
              >
                注册
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}
