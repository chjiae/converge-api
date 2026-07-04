/**
 * 租户申请页面
 *
 * 公开页面，供未绑定租户的用户提交租户入驻申请。
 * 表单字段对应后端 CreateApplicationRequest：
 * - companyName（公司名称，必填）
 * - contactName（联系人姓名，必填）
 * - contactEmail（联系人邮箱，必填，邮箱格式校验）
 * - contactPhone（联系人手机，选填）
 * - applicationType（申请类型，必填，REGISTER / TRIAL）
 * - adminUsername（管理员用户名，必填）
 * - adminEmail（管理员邮箱，必填，邮箱格式校验）
 * - adminPassword（管理员密码，必填，最少 6 位）
 * - description（申请说明，选填）
 */

import { useState } from "react"
import { Link } from "react-router-dom"
import { Loader2, CheckCircle2, Eye, EyeOff } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { ModeToggle } from "@/components/mode-toggle"
import { toast } from "sonner"
import { post } from "@/lib/api-client"

/** 申请类型选项 */
const APPLICATION_TYPES = [
  { value: "REGISTER", label: "正式入驻" },
  { value: "TRIAL", label: "试用申请" },
] as const

/**
 * 申请类型枚举
 *
 * 与后端 ApplicationType 枚举保持一致。
 */
type ApplicationType = "REGISTER" | "TRIAL"

/**
 * 创建申请请求体
 *
 * 对应后端 CreateApplicationRequest DTO。
 */
interface CreateApplicationRequest {
  /** 公司名称，必填 */
  companyName: string
  /** 联系人姓名，必填 */
  contactName: string
  /** 联系人邮箱，必填 */
  contactEmail: string
  /** 联系人手机，选填 */
  contactPhone: string
  /** 申请类型，必填 */
  applicationType: ApplicationType
  /** 管理员用户名，必填 */
  adminUsername: string
  /** 管理员邮箱，必填 */
  adminEmail: string
  /** 管理员密码，必填 */
  adminPassword: string
  /** 申请说明，选填 */
  description: string
}

export default function ApplyPage() {
  const [companyName, setCompanyName] = useState("")
  const [contactName, setContactName] = useState("")
  const [contactEmail, setContactEmail] = useState("")
  const [contactPhone, setContactPhone] = useState("")
  const [applicationType, setApplicationType] = useState<ApplicationType | "">("")
  const [adminUsername, setAdminUsername] = useState("")
  const [adminEmail, setAdminEmail] = useState("")
  const [adminPassword, setAdminPassword] = useState("")
  const [showPassword, setShowPassword] = useState(false)
  const [description, setDescription] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [touched, setTouched] = useState({
    companyName: false,
    contactName: false,
    contactEmail: false,
    applicationType: false,
    adminUsername: false,
    adminEmail: false,
    adminPassword: false,
  })

  /* 字段校验 */
  const companyNameError = touched.companyName && companyName.trim().length === 0
  const contactNameError = touched.contactName && contactName.trim().length === 0
  const contactEmailError = touched.contactEmail && !contactEmail.includes("@")
  const applicationTypeError = touched.applicationType && applicationType.length === 0
  const adminUsernameError = touched.adminUsername && adminUsername.trim().length === 0
  const adminEmailError = touched.adminEmail && !adminEmail.includes("@")
  const adminPasswordError = touched.adminPassword && adminPassword.length < 6

  /**
   * 提交申请
   *
   * 校验必填字段后向后端 POST /api/v1/applications 提交申请数据。
   */
  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    /* 触发所有必填字段的校验 */
    setTouched({
      companyName: true,
      contactName: true,
      contactEmail: true,
      applicationType: true,
      adminUsername: true,
      adminEmail: true,
      adminPassword: true,
    })

    /* 校验必填字段 */
    if (
      !companyName.trim() ||
      !contactName.trim() ||
      !contactEmail.includes("@") ||
      !applicationType ||
      !adminUsername.trim() ||
      !adminEmail.includes("@") ||
      adminPassword.length < 6
    ) {
      toast.error("请填写所有必填字段")
      return
    }

    const payload: CreateApplicationRequest = {
      companyName: companyName.trim(),
      contactName: contactName.trim(),
      contactEmail: contactEmail.trim(),
      contactPhone: contactPhone.trim(),
      applicationType: applicationType as ApplicationType,
      adminUsername: adminUsername.trim(),
      adminEmail: adminEmail.trim(),
      adminPassword,
      description: description.trim(),
    }

    setIsLoading(true)
    try {
      await post("/api/v1/applications", payload)
      setSubmitted(true)
    } catch {
      toast.error("提交失败，请稍后重试")
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex">
      {/* Left: Brand Showcase Panel */}
      <div className="hidden lg:flex lg:w-[55%] relative overflow-hidden flex-col justify-between p-12 brand-panel">
        <div className="absolute inset-0 bg-gradient-to-br from-[oklch(0.15_0.02_260)] via-[oklch(0.18_0.04_270)] to-[oklch(0.12_0.01_250)]" />
        <div className="absolute inset-0 brand-mesh" />
        <div className="absolute inset-0 brand-grain" />

        <div className="absolute inset-0 overflow-hidden">
          <div className="geo-shape geo-shape-1" />
          <div className="geo-shape geo-shape-2" />
          <div className="geo-shape geo-shape-3" />
          <div className="geo-shape geo-shape-4" />
          <div className="geo-shape geo-shape-5" />
        </div>

        <div className="absolute inset-0 brand-grid" />

        {/* 品牌标识 */}
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
            加入
            <br />
            <span className="text-[oklch(0.72_0.15_260)]">Converge</span>
          </h1>
          <p className="text-[oklch(0.65_0.02_260)] text-lg max-w-md leading-relaxed">
            提交入驻申请，让你的团队享受高性能数据聚合服务。我们会在 1-2 个工作日内审核你的申请。
          </p>
        </div>

        <div className="relative z-10 space-y-5">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-[oklch(0.25_0.05_260)] flex items-center justify-center flex-shrink-0">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="oklch(0.65 0.15 260)" strokeWidth="2" strokeLinecap="round">
                <path d="M20 6L9 17l-5-5"/>
              </svg>
            </div>
            <span className="text-[oklch(0.7_0.01_260)] text-sm">快速审核，1-2 个工作日</span>
          </div>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-[oklch(0.25_0.05_260)] flex items-center justify-center flex-shrink-0">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="oklch(0.65 0.15 260)" strokeWidth="2" strokeLinecap="round">
                <path d="M20 6L9 17l-5-5"/>
              </svg>
            </div>
            <span className="text-[oklch(0.7_0.01_260)] text-sm">专属租户空间，数据完全隔离</span>
          </div>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-[oklch(0.25_0.05_260)] flex items-center justify-center flex-shrink-0">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="oklch(0.65 0.15 260)" strokeWidth="2" strokeLinecap="round">
                <path d="M20 6L9 17l-5-5"/>
              </svg>
            </div>
            <span className="text-[oklch(0.7_0.01_260)] text-sm">专业技术支持</span>
          </div>
        </div>
      </div>

      {/* Right: 申请表单 */}
      <div className="flex-1 flex flex-col min-h-screen bg-background relative">
        <div className="flex justify-end p-4">
          <ModeToggle />
        </div>

        <div className="flex-1 flex items-center justify-center px-6 pb-12">
          <div className="w-full max-w-[420px] login-form-enter">
            {/* 移动端品牌标识 */}
            <div className="lg:hidden flex items-center gap-3 mb-6">
              <div className="w-9 h-9 rounded-lg bg-primary flex items-center justify-center">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <path d="M12 2L2 7L12 12L22 7L12 2Z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                  <path d="M2 17L12 22L22 17" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                  <path d="M2 12L12 17L22 12" stroke="currentColor" strokeWidth="2" strokeLinejoin="round"/>
                </svg>
              </div>
              <span className="text-foreground text-lg font-medium tracking-wide">Converge</span>
            </div>

            {submitted ? (
              /* 提交成功状态 */
              <Card>
                <CardHeader className="text-center pb-4">
                  <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-chart-2/15">
                    <CheckCircle2 className="h-7 w-7 text-chart-2" />
                  </div>
                  <CardTitle className="text-xl">申请已提交，请等待审核</CardTitle>
                </CardHeader>
                <CardContent className="text-center space-y-4">
                  <p className="text-sm text-muted-foreground leading-relaxed">
                    我们已收到你的入驻申请，将在 1-2 个工作日内完成审核。审核结果将通过邮件通知。
                  </p>
                  <Button variant="outline" asChild className="w-full">
                    <Link to="/">返回首页</Link>
                  </Button>
                </CardContent>
              </Card>
            ) : (
              /* 申请表单 */
              <div className="space-y-6">
                <div className="space-y-2">
                  <h2 className="text-2xl font-semibold tracking-tight text-foreground">
                    申请入驻
                  </h2>
                  <p className="text-sm text-muted-foreground">
                    填写以下信息，提交你的租户入驻申请
                  </p>
                </div>

                <form onSubmit={handleSubmit} className="space-y-4">
                  {/* 公司名称 */}
                  <div className="space-y-2">
                    <Label htmlFor="companyName">
                      公司名称 <span className="text-destructive">*</span>
                    </Label>
                    <Input
                      id="companyName"
                      placeholder="输入公司全称"
                      value={companyName}
                      onChange={(e) => setCompanyName(e.target.value)}
                      onBlur={() => setTouched((t) => ({ ...t, companyName: true }))}
                      className={companyNameError ? "border-destructive focus-visible:ring-destructive" : ""}
                      disabled={isLoading}
                    />
                    {companyNameError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        请输入公司名称
                      </p>
                    )}
                  </div>

                  {/* 联系人姓名 */}
                  <div className="space-y-2">
                    <Label htmlFor="contactName">
                      联系人姓名 <span className="text-destructive">*</span>
                    </Label>
                    <Input
                      id="contactName"
                      placeholder="输入联系人姓名"
                      value={contactName}
                      onChange={(e) => setContactName(e.target.value)}
                      onBlur={() => setTouched((t) => ({ ...t, contactName: true }))}
                      className={contactNameError ? "border-destructive focus-visible:ring-destructive" : ""}
                      disabled={isLoading}
                    />
                    {contactNameError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        请输入联系人姓名
                      </p>
                    )}
                  </div>

                  {/* 联系人邮箱 */}
                  <div className="space-y-2">
                    <Label htmlFor="contactEmail">
                      联系人邮箱 <span className="text-destructive">*</span>
                    </Label>
                    <Input
                      id="contactEmail"
                      type="email"
                      placeholder="name@example.com"
                      value={contactEmail}
                      onChange={(e) => setContactEmail(e.target.value)}
                      onBlur={() => setTouched((t) => ({ ...t, contactEmail: true }))}
                      className={contactEmailError ? "border-destructive focus-visible:ring-destructive" : ""}
                      autoComplete="email"
                      disabled={isLoading}
                    />
                    {contactEmailError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        请输入有效的邮箱地址
                      </p>
                    )}
                  </div>

                  {/* 联系人手机（选填） */}
                  <div className="space-y-2">
                    <Label htmlFor="contactPhone">联系人手机</Label>
                    <Input
                      id="contactPhone"
                      type="tel"
                      placeholder="选填"
                      value={contactPhone}
                      onChange={(e) => setContactPhone(e.target.value)}
                      autoComplete="tel"
                      disabled={isLoading}
                    />
                  </div>

                  {/* 申请类型 */}
                  <div className="space-y-2">
                    <Label htmlFor="applicationType">
                      申请类型 <span className="text-destructive">*</span>
                    </Label>
                    <Select
                      value={applicationType}
                      onValueChange={(val) => {
                        setApplicationType(val as ApplicationType)
                        setTouched((t) => ({ ...t, applicationType: true }))
                      }}
                    >
                      <SelectTrigger
                        id="applicationType"
                        className={applicationTypeError ? "border-destructive focus-visible:ring-destructive" : ""}
                        disabled={isLoading}
                      >
                        <SelectValue placeholder="选择申请类型" />
                      </SelectTrigger>
                      <SelectContent>
                        {APPLICATION_TYPES.map((type) => (
                          <SelectItem key={type.value} value={type.value}>
                            {type.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {applicationTypeError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        请选择申请类型
                      </p>
                    )}
                  </div>

                  {/* 申请说明（选填） */}
                  <div className="space-y-2">
                    <Label htmlFor="description">申请说明</Label>
                    <textarea
                      id="description"
                      rows={3}
                      placeholder="简要描述你的使用场景和需求（选填）"
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      disabled={isLoading}
                      className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50 resize-none"
                    />
                  </div>

                  {/* 分隔线：管理员信息 */}
                  <div className="pt-2">
                    <p className="text-xs font-medium text-muted-foreground uppercase tracking-wider mb-3">
                      管理员账号信息
                    </p>
                    <div className="h-px bg-border" />
                  </div>

                  {/* 管理员用户名 */}
                  <div className="space-y-2">
                    <Label htmlFor="adminUsername">
                      管理员用户名 <span className="text-destructive">*</span>
                    </Label>
                    <Input
                      id="adminUsername"
                      placeholder="审核通过后用于登录的用户名"
                      value={adminUsername}
                      onChange={(e) => setAdminUsername(e.target.value)}
                      onBlur={() => setTouched((t) => ({ ...t, adminUsername: true }))}
                      className={adminUsernameError ? "border-destructive focus-visible:ring-destructive" : ""}
                      autoComplete="username"
                      disabled={isLoading}
                    />
                    {adminUsernameError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        请输入管理员用户名
                      </p>
                    )}
                  </div>

                  {/* 管理员邮箱 */}
                  <div className="space-y-2">
                    <Label htmlFor="adminEmail">
                      管理员邮箱 <span className="text-destructive">*</span>
                    </Label>
                    <Input
                      id="adminEmail"
                      type="email"
                      placeholder="admin@example.com"
                      value={adminEmail}
                      onChange={(e) => setAdminEmail(e.target.value)}
                      onBlur={() => setTouched((t) => ({ ...t, adminEmail: true }))}
                      className={adminEmailError ? "border-destructive focus-visible:ring-destructive" : ""}
                      autoComplete="email"
                      disabled={isLoading}
                    />
                    {adminEmailError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        请输入有效的邮箱地址
                      </p>
                    )}
                  </div>

                  {/* 管理员密码 */}
                  <div className="space-y-2">
                    <Label htmlFor="adminPassword">
                      管理员密码 <span className="text-destructive">*</span>
                    </Label>
                    <div className="relative">
                      <Input
                        id="adminPassword"
                        type={showPassword ? "text" : "password"}
                        placeholder="至少 6 个字符"
                        value={adminPassword}
                        onChange={(e) => setAdminPassword(e.target.value)}
                        onBlur={() => setTouched((t) => ({ ...t, adminPassword: true }))}
                        className={`pr-10 ${adminPasswordError ? "border-destructive focus-visible:ring-destructive" : ""}`}
                        autoComplete="new-password"
                        disabled={isLoading}
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-2.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors p-0.5"
                        tabIndex={-1}
                        aria-label={showPassword ? "隐藏密码" : "显示密码"}
                      >
                        {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                      </button>
                    </div>
                    {adminPasswordError && (
                      <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-150">
                        密码至少需要 6 个字符
                      </p>
                    )}
                  </div>

                  <Button
                    type="submit"
                    className="w-full h-10"
                    disabled={isLoading}
                  >
                    {isLoading ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" />
                        提交中...
                      </>
                    ) : (
                      "提交申请"
                    )}
                  </Button>
                </form>

                <p className="text-center text-sm text-muted-foreground">
                  已有账号？{" "}
                  <Link
                    to="/login"
                    className="font-medium text-foreground hover:text-primary transition-colors underline-offset-4 hover:underline"
                  >
                    登录
                  </Link>
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
