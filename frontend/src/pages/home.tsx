import { useNavigate } from "react-router-dom"
import { LayoutDashboard } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/contexts/auth-context"
import { ModeToggle } from "@/components/mode-toggle"

export default function HomePage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  function handleConsole() {
    if (isAuthenticated) {
      navigate("/console")
    } else {
      navigate("/login")
    }
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
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

      <main className="flex flex-col items-center justify-center px-6 py-24">
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          Converge API
        </h1>
        <p className="mt-4 text-lg text-muted-foreground max-w-prose text-center">
          统一数据聚合层，以更低的延迟和更高的可靠性连接一切服务。
        </p>
      </main>
    </div>
  )
}
