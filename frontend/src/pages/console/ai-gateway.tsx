/**
 * AI Gateway 管理控制台一期页面。
 *
 * 页面覆盖上游目录、凭据资源、静态路由、下游访问、运行状态和非流式 Chat 测试。
 * 所有请求统一走控制面 API client，不直接访问 Gateway 内部地址。
 */

import { useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import {
  Activity,
  Bot,
  CheckCircle2,
  Copy,
  KeyRound,
  Network,
  Pencil,
  Play,
  Plus,
  Power,
  RefreshCw,
  Route,
  ShieldCheck,
  TestTube2,
  TriangleAlert,
  XCircle,
} from 'lucide-react'
import { DataTable } from '@/components/data-table'
import type { Column } from '@/components/data-table'
import ConfirmDialog from '@/components/confirm-dialog'
import { useQuery } from '@/hooks/use-api'
import {
  changeAccessGroupStatus,
  changeClientApiKeyStatus,
  changeConnectionStatus,
  changeCredentialStatus,
  changeKeyAccessGroupStatus,
  changeModelBindingStatus,
  changeModelGrantStatus,
  changeModelStatus,
  changePoolMemberStatus,
  changeProviderStatus,
  changeResourcePoolStatus,
  changeResourceStatus,
  changeRoutePolicyStatus,
  changeRouteTargetStatus,
  createAccessGroup,
  createClientApiKey,
  createConnection,
  createCredential,
  createKeyAccessGroup,
  createModel,
  createModelBinding,
  createModelGrant,
  createPoolMember,
  createProvider,
  createResource,
  createResourcePool,
  createRoutePolicy,
  createRouteTarget,
  getAiOptions,
  getGatewayReady,
  getGatewayRuntimeStatus,
  getGatewaySnapshotStatus,
  getRuntimePolicy,
  listAccessGroups,
  listClientApiKeys,
  listConnections,
  listCredentials,
  listKeyAccessGroups,
  listModelBindings,
  listModelGrants,
  listModels,
  listPoolMembers,
  listProviders,
  listResourcePools,
  listResources,
  listRoutePolicies,
  listRouteTargets,
  previewRoute,
  rotateClientApiKey,
  rotateCredential,
  testGatewayChatCompletions,
  updateAccessGroup,
  updateClientApiKey,
  updateConnection,
  updateCredential,
  updateKeyAccessGroup,
  updateModel,
  updateModelBinding,
  updateModelGrant,
  updatePoolMember,
  updateProvider,
  updateResource,
  updateResourcePool,
  updateRoutePolicy,
  updateRouteTarget,
  updateRuntimePolicy,
} from '@/lib/ai-gateway-api'
import type {
  AiAccessGroup,
  AiClientApiKey,
  AiClientApiKeySecret,
  AiConnection,
  AiCredential,
  AiEntity,
  AiEnumOption,
  AiExecutionResource,
  AiGatewayChatMessage,
  AiGatewayChatTestResponse,
  AiGatewayStatus,
  AiKeyAccessGroup,
  AiModelBinding,
  AiModelGrant,
  AiOptions,
  AiPoolMember,
  AiProvider,
  AiPublicModel,
  AiResourcePool,
  AiRoutePolicy,
  AiRouteTarget,
  AiRuntimePolicy,
} from '@/lib/ai-gateway-api'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

/** 默认分页大小。 */
const PAGE_SIZE = 20

/** 空分页结果。 */
const EMPTY_PAGE = { list: [], total: 0, page: 1, size: PAGE_SIZE }

/** 页内 Tab 编码。 */
type ConsoleTab = 'overview' | 'catalog' | 'resources' | 'routing' | 'access' | 'status' | 'test'

/** 表单字段类型。 */
type FieldType = 'text' | 'textarea' | 'number' | 'password' | 'select' | 'datetime-local'

/** 表单字段值类型。 */
type FieldValueType = 'string' | 'number' | 'datetime'

/** 下拉选项。 */
interface SelectChoice {
  /** 选项值。 */
  value: string
  /** 选项标签。 */
  label: string
}

/** 通用表单字段配置。 */
interface FieldConfig {
  /** 字段名。 */
  name: string
  /** 字段标签。 */
  label: string
  /** 字段类型。 */
  type?: FieldType
  /** 提交时的值类型。 */
  valueType?: FieldValueType
  /** 输入占位提示。 */
  placeholder?: string
  /** 是否必填。 */
  required?: boolean
  /** 下拉选项。 */
  options?: SelectChoice[]
  /** 是否只读。 */
  readOnly?: boolean
  /** 跨两列表单显示。 */
  wide?: boolean
}

/** 通用表单弹窗状态。 */
interface FormDialogState {
  /** 弹窗标题。 */
  title: string
  /** 弹窗说明。 */
  description: string
  /** 字段配置。 */
  fields: FieldConfig[]
  /** 初始值。 */
  initialValues: Record<string, string>
  /** 提交处理函数。 */
  onSubmit: (values: Record<string, unknown>) => Promise<void>
}

/** 危险或状态操作确认状态。 */
interface ConfirmState {
  /** 确认标题。 */
  title: string
  /** 确认描述。 */
  description: string
  /** 是否危险操作。 */
  destructive?: boolean
  /** 确认执行函数。 */
  onConfirm: () => Promise<void>
}

/** 一次性 raw key 展示状态。 */
interface RawKeyState {
  /** 弹窗标题。 */
  title: string
  /** raw key 明文，仅本次展示。 */
  rawKey: string
  /** 掩码预览。 */
  maskedPreview?: string
}

/** Chat 测试表单。 */
interface ChatForm {
  /** 本次测试 Client API Key。 */
  clientApiKey: string
  /** 公开模型编码。 */
  model: string
  /** 消息 JSON 文本。 */
  messages: string
  /** 采样温度。 */
  temperature: string
  /** 最大输出 token。 */
  maxTokens: string
}

/** 路由预览表单。 */
interface PreviewForm {
  /** 公开模型编码。 */
  publicModelCode: string
  /** 规范操作。 */
  canonicalOperation: string
  /** 选择 seed。 */
  selectionSeed: string
}

/** JSON 展示组件属性。 */
interface JsonBlockProps {
  /** 要展示的 JSON 数据。 */
  value: unknown
  /** 空状态文本。 */
  emptyText?: string
}

/** 状态字段组件属性。 */
interface StatusGridProps {
  /** 字段对象。 */
  data: AiGatewayStatus | null
  /** 加载状态。 */
  loading: boolean
  /** 错误对象。 */
  error: Error | null
}

/** 通用表单弹窗属性。 */
interface EntityDialogProps {
  /** 弹窗状态。 */
  state: FormDialogState | null
  /** 关闭回调。 */
  onClose: () => void
}

/** 一次性 raw key 弹窗属性。 */
interface RawKeyDialogProps {
  /** raw key 状态。 */
  state: RawKeyState | null
  /** 关闭回调。 */
  onClose: () => void
}

/**
 * 将枚举选项转换为通用下拉选项。
 * @param options 枚举选项
 * @returns 下拉选项
 */
function enumChoices(options?: AiEnumOption[]): SelectChoice[] {
  return (options ?? []).map((item) => ({ value: item.name, label: item.label }))
}

/**
 * 将实体列表转换为 ID 下拉选项。
 * @param items 实体列表
 * @returns 下拉选项
 */
function entityChoices(items: AiEntity[]): SelectChoice[] {
  return items.map((item) => ({
    value: String(item.id),
    label: item.displayName ? `${item.displayName} (${item.code ?? item.id})` : String(item.code ?? item.id),
  }))
}

/**
 * 格式化时间字段。
 * @param value 时间值
 * @returns 本地时间文本
 */
function formatTime(value: unknown): string {
  if (typeof value !== 'string' || value.length === 0) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

/**
 * 转换 datetime-local 输入为 ISO 字符串。
 * @param value 输入值
 * @returns ISO 时间或 null
 */
function toIsoOrNull(value: unknown): string | null {
  if (typeof value !== 'string' || !value) {
    return null
  }
  return new Date(value).toISOString()
}

/**
 * 将接口时间转换为 datetime-local 输入值。
 * @param value 接口时间
 * @returns datetime-local 字符串
 */
function toDateTimeInput(value: unknown): string {
  if (typeof value !== 'string' || !value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const offsetMs = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}

/**
 * 获取实体状态。
 * @param item 实体
 * @returns 状态编码
 */
function entityStatus(item: AiEntity): string {
  return String(item.adminStatus ?? item.status ?? 'UNKNOWN')
}

/**
 * 获取实体显示名称。
 * @param item 实体
 * @returns 显示名称
 */
function entityTitle(item: AiEntity): string {
  return String(item.displayName ?? item.code ?? item.id)
}

/**
 * 获取错误消息。
 * @param error 错误对象
 * @returns 用户可读错误
 */
function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  return '操作失败，请稍后重试'
}

/**
 * 渲染状态标签。
 * @param status 状态编码
 * @returns 状态标签
 */
function renderStatusBadge(status: string) {
  if (status === 'ENABLED' || status === 'ACTIVE' || status === 'READY') {
    return (
      <Badge className="bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300">
        <CheckCircle2 className="size-3" />
        {status}
      </Badge>
    )
  }
  if (status === 'DISABLED' || status === 'REVOKED' || status === 'DRAINING') {
    return (
      <Badge variant="secondary">
        <XCircle className="size-3" />
        {status}
      </Badge>
    )
  }
  return <Badge variant="outline">{status}</Badge>
}

/**
 * JSON 展示块。
 * @param props 组件属性
 */
function JsonBlock({ value, emptyText = '暂无数据' }: JsonBlockProps) {
  if (value === null || value === undefined) {
    return <div className="rounded-md border p-4 text-sm text-muted-foreground">{emptyText}</div>
  }
  return (
    <pre className="max-h-96 overflow-auto rounded-md border bg-muted/40 p-4 text-xs leading-5">
      {JSON.stringify(value, null, 2)}
    </pre>
  )
}

/**
 * Gateway 状态字段网格。
 * @param props 组件属性
 */
function StatusGrid({ data, loading, error }: StatusGridProps) {
  if (loading) {
    return <div className="rounded-md border p-4 text-sm text-muted-foreground">加载状态中...</div>
  }
  if (error) {
    return <div className="rounded-md border p-4 text-sm text-destructive">{error.message}</div>
  }
  if (!data || Object.keys(data).length === 0) {
    return <div className="rounded-md border p-4 text-sm text-muted-foreground">暂无状态数据</div>
  }
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {Object.entries(data).map(([key, value]) => (
        <div key={key} className="rounded-md border p-3">
          <div className="text-xs text-muted-foreground">{key}</div>
          <div className="mt-1 break-all text-sm font-medium" title={String(value ?? '')}>
            {key.toLowerCase().includes('epoch') ? formatTime(value) : String(value ?? '—')}
          </div>
        </div>
      ))}
    </div>
  )
}

/**
 * 将表单字段值转换为提交值。
 * @param fields 字段配置
 * @param values 输入值
 * @returns 提交对象
 */
function normalizeValues(fields: FieldConfig[], values: Record<string, string>): Record<string, unknown> {
  const normalized: Record<string, unknown> = {}
  fields.forEach((field) => {
    const rawValue = values[field.name] ?? ''
    if (!field.required && rawValue === '') {
      normalized[field.name] = null
      return
    }
    if (field.valueType === 'number') {
      normalized[field.name] = Number(rawValue)
      return
    }
    if (field.valueType === 'datetime') {
      normalized[field.name] = toIsoOrNull(rawValue)
      return
    }
    normalized[field.name] = rawValue
  })
  return normalized
}

/**
 * 通用实体表单弹窗。
 * @param props 组件属性
 */
function EntityDialog({ state, onClose }: EntityDialogProps) {
  const [values, setValues] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    setValues(state?.initialValues ?? {})
  }, [state])

  /** 提交表单。 */
  const handleSubmit = async () => {
    if (!state) return
    setSaving(true)
    try {
      await state.onSubmit(normalizeValues(state.fields, values))
      onClose()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={state !== null} onOpenChange={(open) => {
      if (!open) onClose()
    }}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{state?.title}</DialogTitle>
          <DialogDescription>{state?.description}</DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 md:grid-cols-2">
          {(state?.fields ?? []).map((field) => (
            <div key={field.name} className={field.wide ? 'space-y-2 md:col-span-2' : 'space-y-2'}>
              <Label htmlFor={`ai-${field.name}`}>{field.label}</Label>
              {field.type === 'textarea' ? (
                <textarea
                  id={`ai-${field.name}`}
                  className="min-h-24 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={values[field.name] ?? ''}
                  onChange={(event) => setValues({ ...values, [field.name]: event.target.value })}
                  placeholder={field.placeholder}
                  readOnly={field.readOnly}
                />
              ) : field.type === 'select' ? (
                <Select
                  value={values[field.name] ?? ''}
                  onValueChange={(value) => setValues({ ...values, [field.name]: value ?? '' })}
                  disabled={field.readOnly}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue placeholder={field.placeholder ?? '请选择'} />
                  </SelectTrigger>
                  <SelectContent>
                    {(field.options ?? []).map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  id={`ai-${field.name}`}
                  type={field.type ?? 'text'}
                  value={values[field.name] ?? ''}
                  onChange={(event) => setValues({ ...values, [field.name]: event.target.value })}
                  placeholder={field.placeholder}
                  readOnly={field.readOnly}
                />
              )}
            </div>
          ))}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>取消</Button>
          <Button onClick={() => { void handleSubmit() }} disabled={saving}>
            {saving ? <RefreshCw className="size-4 animate-spin" /> : <CheckCircle2 className="size-4" />}
            保存
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * raw key 一次性展示弹窗。
 * @param props 组件属性
 */
function RawKeyDialog({ state, onClose }: RawKeyDialogProps) {
  /** 复制 raw key。 */
  const handleCopy = async () => {
    if (!state) return
    await navigator.clipboard.writeText(state.rawKey)
    toast.success('Client API Key 已复制')
  }

  /** 关闭前确认，避免误关后无法再次查看。 */
  const handleClose = () => {
    const confirmed = window.confirm('关闭后无法再次查看 raw key。请确认你已经保存。')
    if (confirmed) {
      onClose()
    }
  }

  return (
    <Dialog open={state !== null} onOpenChange={(open) => {
      if (!open) handleClose()
    }}>
      <DialogContent showCloseButton={false} className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{state?.title}</DialogTitle>
          <DialogDescription>raw key 只展示一次。关闭弹窗后只能通过轮换生成新的 key。</DialogDescription>
        </DialogHeader>
        <div className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
          请立即复制保存，不要截图发给无关人员。
        </div>
        <div className="space-y-2">
          <Label>raw key</Label>
          <div className="flex gap-2">
            <Input value={state?.rawKey ?? ''} readOnly />
            <Button type="button" variant="outline" onClick={() => { void handleCopy() }}>
              <Copy className="size-4" />
              复制
            </Button>
          </div>
          {state?.maskedPreview && (
            <p className="text-xs text-muted-foreground">掩码预览：{state.maskedPreview}</p>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose}>我已保存，关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/**
 * AI Gateway 管理台页面。
 */
export default function AiGatewayPage() {
  const [activeTab, setActiveTab] = useState<ConsoleTab>('overview')
  const [selectedProviderId, setSelectedProviderId] = useState<number | null>(null)
  const [selectedPoolId, setSelectedPoolId] = useState<number | null>(null)
  const [selectedPolicyId, setSelectedPolicyId] = useState<number | null>(null)
  const [selectedAccessGroupId, setSelectedAccessGroupId] = useState<number | null>(null)
  const [selectedClientKeyId, setSelectedClientKeyId] = useState<number | null>(null)
  const [selectedResourceId, setSelectedResourceId] = useState<number | null>(null)
  const [formDialog, setFormDialog] = useState<FormDialogState | null>(null)
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null)
  const [rawKeyState, setRawKeyState] = useState<RawKeyState | null>(null)
  const [previewForm, setPreviewForm] = useState<PreviewForm>({
    publicModelCode: '',
    canonicalOperation: 'CHAT_COMPLETIONS',
    selectionSeed: 'preview-seed',
  })
  const [previewResult, setPreviewResult] = useState<Record<string, unknown> | null>(null)
  const [chatForm, setChatForm] = useState<ChatForm>({
    clientApiKey: '',
    model: '',
    messages: '[{"role":"user","content":"你好"}]',
    temperature: '0.7',
    maxTokens: '512',
  })
  const [chatResult, setChatResult] = useState<AiGatewayChatTestResponse | null>(null)
  const [chatLoading, setChatLoading] = useState(false)

  const pageQuery = useMemo(() => ({ page: 1, size: PAGE_SIZE }), [])
  const optionsQuery = useQuery<AiOptions>(getAiOptions, [])
  const providersQuery = useQuery(() => listProviders(pageQuery), [])
  const modelsQuery = useQuery(() => listModels(pageQuery), [])
  const resourcesQuery = useQuery(() => listResources(pageQuery), [])
  const poolsQuery = useQuery(() => listResourcePools(pageQuery), [])
  const bindingsQuery = useQuery(() => listModelBindings(pageQuery), [])
  const policiesQuery = useQuery(() => listRoutePolicies(pageQuery), [])
  const accessGroupsQuery = useQuery(() => listAccessGroups(pageQuery), [])
  const clientKeysQuery = useQuery(() => listClientApiKeys(pageQuery), [])
  const readyQuery = useQuery(getGatewayReady, [])
  const snapshotQuery = useQuery(getGatewaySnapshotStatus, [])
  const runtimeStatusQuery = useQuery(getGatewayRuntimeStatus, [])
  const connectionsQuery = useQuery(
    () => selectedProviderId
      ? listConnections(selectedProviderId, pageQuery)
      : Promise.resolve(EMPTY_PAGE as { list: AiConnection[]; total: number; page: number; size: number }),
    [selectedProviderId],
  )
  const credentialsQuery = useQuery(
    () => selectedProviderId
      ? listCredentials(selectedProviderId, pageQuery)
      : Promise.resolve(EMPTY_PAGE as { list: AiCredential[]; total: number; page: number; size: number }),
    [selectedProviderId],
  )
  const poolMembersQuery = useQuery(
    () => selectedPoolId
      ? listPoolMembers(selectedPoolId, pageQuery)
      : Promise.resolve(EMPTY_PAGE as { list: AiPoolMember[]; total: number; page: number; size: number }),
    [selectedPoolId],
  )
  const routeTargetsQuery = useQuery(
    () => selectedPolicyId
      ? listRouteTargets(selectedPolicyId, pageQuery)
      : Promise.resolve(EMPTY_PAGE as { list: AiRouteTarget[]; total: number; page: number; size: number }),
    [selectedPolicyId],
  )
  const modelGrantsQuery = useQuery(
    () => selectedAccessGroupId
      ? listModelGrants(selectedAccessGroupId, pageQuery)
      : Promise.resolve(EMPTY_PAGE as { list: AiModelGrant[]; total: number; page: number; size: number }),
    [selectedAccessGroupId],
  )
  const keyAccessGroupsQuery = useQuery(
    () => selectedClientKeyId
      ? listKeyAccessGroups(selectedClientKeyId, pageQuery)
      : Promise.resolve(EMPTY_PAGE as { list: AiKeyAccessGroup[]; total: number; page: number; size: number }),
    [selectedClientKeyId],
  )
  const runtimePolicyQuery = useQuery(
    () => selectedResourceId
      ? getRuntimePolicy(selectedResourceId)
      : Promise.resolve({} as AiRuntimePolicy),
    [selectedResourceId],
  )

  const providers = useMemo(() => providersQuery.data?.list ?? [], [providersQuery.data])
  const connections = useMemo(() => connectionsQuery.data?.list ?? [], [connectionsQuery.data])
  const models = useMemo(() => modelsQuery.data?.list ?? [], [modelsQuery.data])
  const credentials = useMemo(() => credentialsQuery.data?.list ?? [], [credentialsQuery.data])
  const resources = useMemo(() => resourcesQuery.data?.list ?? [], [resourcesQuery.data])
  const pools = useMemo(() => poolsQuery.data?.list ?? [], [poolsQuery.data])
  const bindings = useMemo(() => bindingsQuery.data?.list ?? [], [bindingsQuery.data])
  const policies = useMemo(() => policiesQuery.data?.list ?? [], [policiesQuery.data])
  const accessGroups = useMemo(() => accessGroupsQuery.data?.list ?? [], [accessGroupsQuery.data])
  const clientKeys = useMemo(() => clientKeysQuery.data?.list ?? [], [clientKeysQuery.data])
  const poolMembers = useMemo(() => poolMembersQuery.data?.list ?? [], [poolMembersQuery.data])
  const routeTargets = useMemo(() => routeTargetsQuery.data?.list ?? [], [routeTargetsQuery.data])
  const modelGrants = useMemo(() => modelGrantsQuery.data?.list ?? [], [modelGrantsQuery.data])
  const keyAccessGroups = useMemo(() => keyAccessGroupsQuery.data?.list ?? [], [keyAccessGroupsQuery.data])
  const options = optionsQuery.data

  useEffect(() => {
    if (!selectedProviderId && providers.length > 0) setSelectedProviderId(providers[0].id)
  }, [providers, selectedProviderId])

  useEffect(() => {
    if (!selectedPoolId && pools.length > 0) setSelectedPoolId(pools[0].id)
  }, [pools, selectedPoolId])

  useEffect(() => {
    if (!selectedPolicyId && policies.length > 0) setSelectedPolicyId(policies[0].id)
  }, [policies, selectedPolicyId])

  useEffect(() => {
    if (!selectedAccessGroupId && accessGroups.length > 0) setSelectedAccessGroupId(accessGroups[0].id)
  }, [accessGroups, selectedAccessGroupId])

  useEffect(() => {
    if (!selectedClientKeyId && clientKeys.length > 0) setSelectedClientKeyId(clientKeys[0].id)
  }, [clientKeys, selectedClientKeyId])

  useEffect(() => {
    if (!selectedResourceId && resources.length > 0) setSelectedResourceId(resources[0].id)
  }, [resources, selectedResourceId])

  /** 刷新全部页面数据。 */
  const refreshAll = () => {
    providersQuery.refetch()
    modelsQuery.refetch()
    resourcesQuery.refetch()
    poolsQuery.refetch()
    bindingsQuery.refetch()
    policiesQuery.refetch()
    accessGroupsQuery.refetch()
    clientKeysQuery.refetch()
    readyQuery.refetch()
    snapshotQuery.refetch()
    runtimeStatusQuery.refetch()
    connectionsQuery.refetch()
    credentialsQuery.refetch()
    poolMembersQuery.refetch()
    routeTargetsQuery.refetch()
    modelGrantsQuery.refetch()
    keyAccessGroupsQuery.refetch()
    runtimePolicyQuery.refetch()
  }

  /** 执行保存类操作并刷新页面。 */
  const runSave = async (message: string, action: () => Promise<unknown>) => {
    try {
      await action()
      toast.success(message)
      refreshAll()
    } catch (error) {
      toast.error(getErrorMessage(error))
      throw error
    }
  }

  /** 执行状态类确认操作。 */
  const confirmAction = (state: ConfirmState) => {
    setConfirmState(state)
  }

  /** 设置一次性 raw key 展示状态。 */
  const showRawKey = (title: string, response: AiClientApiKeySecret) => {
    setRawKeyState({
      title,
      rawKey: response.rawKey,
      maskedPreview: response.maskedPreview,
    })
  }

  /** 打开 Provider 表单。 */
  const openProviderDialog = (provider?: AiProvider) => {
    setFormDialog({
      title: provider ? '编辑 Provider' : '新增 Provider',
      description: '维护上游供应商元数据，不包含任何凭据。',
      fields: [
        { name: 'code', label: '编码', required: true, placeholder: 'openai-main' },
        { name: 'displayName', label: '名称', required: true, placeholder: 'OpenAI 主账号' },
        { name: 'providerKind', label: '类型', type: 'select', required: true, options: enumChoices(options?.providerKinds) },
        { name: 'status', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: provider?.code ?? '',
        displayName: provider?.displayName ?? '',
        providerKind: provider?.providerKind ?? options?.providerKinds[0]?.name ?? '',
        status: provider?.status ?? 'ENABLED',
        description: provider?.description ?? '',
      },
      onSubmit: (values) => runSave('Provider 已保存', () => (
        provider ? updateProvider(provider.id, values) : createProvider(values)
      )),
    })
  }

  /** 打开上游连接表单。 */
  const openConnectionDialog = (connection?: AiConnection) => {
    if (!selectedProviderId) {
      toast.error('请先创建 Provider')
      return
    }
    setFormDialog({
      title: connection ? '编辑上游连接' : '新增上游连接',
      description: 'Base URL 会展示在目录页面，但不会进入 Gateway 状态代理。',
      fields: [
        { name: 'code', label: '编码', required: true, placeholder: 'openai-cn' },
        { name: 'displayName', label: '名称', required: true },
        { name: 'protocolType', label: '协议', type: 'select', required: true, options: enumChoices(options?.protocolTypes) },
        { name: 'baseUrl', label: 'Base URL', required: true, placeholder: 'https://api.example.com/v1', wide: true },
        { name: 'status', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: connection?.code ?? '',
        displayName: connection?.displayName ?? '',
        protocolType: connection?.protocolType ?? options?.protocolTypes[0]?.name ?? '',
        baseUrl: connection?.baseUrl ?? '',
        status: connection?.status ?? 'ENABLED',
        description: connection?.description ?? '',
      },
      onSubmit: (values) => runSave('上游连接已保存', () => (
        connection ? updateConnection(connection.id, { ...values, providerId: selectedProviderId }) : createConnection(selectedProviderId, values)
      )),
    })
  }

  /** 打开公开模型表单。 */
  const openModelDialog = (model?: AiPublicModel) => {
    setFormDialog({
      title: model ? '编辑公开模型' : '新增公开模型',
      description: '公开模型名不强制等于上游模型名，上游映射由模型绑定维护。',
      fields: [
        { name: 'code', label: '编码', required: true, placeholder: 'gpt-4o-mini' },
        { name: 'displayName', label: '名称', required: true },
        { name: 'modelFamily', label: '模型族', placeholder: 'openai-compatible' },
        { name: 'status', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: model?.code ?? '',
        displayName: model?.displayName ?? '',
        modelFamily: model?.modelFamily ?? '',
        status: model?.status ?? 'ENABLED',
        description: model?.description ?? '',
      },
      onSubmit: (values) => runSave('公开模型已保存', () => (
        model ? updateModel(model.id, values) : createModel(values)
      )),
    })
  }

  /** 打开凭据表单。 */
  const openCredentialDialog = (credential?: AiCredential) => {
    if (!selectedProviderId) {
      toast.error('请先创建 Provider')
      return
    }
    const fields: FieldConfig[] = [
      { name: 'code', label: '编码', required: true },
      { name: 'displayName', label: '名称', required: true },
      { name: 'description', label: '描述', type: 'textarea', wide: true },
    ]
    if (!credential) {
      fields.splice(2, 0, { name: 'apiKey', label: '上游 API Key', type: 'password', required: true, wide: true })
    }
    setFormDialog({
      title: credential ? '编辑凭据元数据' : '新增上游凭据',
      description: '凭据明文仅在创建或轮换请求中使用，列表只显示掩码预览。',
      fields,
      initialValues: {
        code: credential?.code ?? '',
        displayName: credential?.displayName ?? '',
        description: credential?.description ?? '',
        apiKey: '',
      },
      onSubmit: (values) => runSave('凭据已保存', () => (
        credential ? updateCredential(credential.id, values) : createCredential(selectedProviderId, values)
      )),
    })
  }

  /** 打开执行资源表单。 */
  const openResourceDialog = (resource?: AiExecutionResource) => {
    setFormDialog({
      title: resource ? '编辑执行资源' : '新增执行资源',
      description: resource ? '资源绑定关系创建后不可修改，只允许编辑管理元数据。' : '创建时选择连接与凭据，后续绑定关系不可修改。',
      fields: [
        { name: 'code', label: '编码', required: true },
        { name: 'displayName', label: '名称', required: true },
        ...(!resource ? [
          { name: 'upstreamConnectionId', label: '上游连接', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(connections) },
          { name: 'credentialId', label: '凭据', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(credentials) },
          { name: 'adminStatus', label: '状态', type: 'select' as const, required: true, options: enumChoices(options?.resourceStatuses) },
        ] : []),
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: resource?.code ?? '',
        displayName: resource?.displayName ?? '',
        upstreamConnectionId: String(resource?.upstreamConnectionId ?? connections[0]?.id ?? ''),
        credentialId: String(resource?.credentialId ?? credentials[0]?.id ?? ''),
        adminStatus: resource?.adminStatus ?? 'ENABLED',
        description: resource?.description ?? '',
      },
      onSubmit: (values) => runSave('执行资源已保存', () => (
        resource ? updateResource(resource.id, values) : createResource(values)
      )),
    })
  }

  /** 打开运行时策略表单。 */
  const openRuntimePolicyDialog = () => {
    if (!selectedResourceId) {
      toast.error('请先选择执行资源')
      return
    }
    const policy = runtimePolicyQuery.data
    setFormDialog({
      title: '编辑运行时策略',
      description: 'maxConcurrentRequests = 0 表示不限制本地并发；policyVersion 由后端维护。',
      fields: [
        { name: 'maxConcurrentRequests', label: '最大并发', type: 'number', valueType: 'number', required: true },
        { name: 'consecutiveFailureThreshold', label: '连续失败阈值', type: 'number', valueType: 'number', required: true },
        { name: 'failureResetAfterMs', label: '失败重置毫秒', type: 'number', valueType: 'number', required: true },
        { name: 'failureCooldownMs', label: '失败冷却毫秒', type: 'number', valueType: 'number', required: true },
        { name: 'rateLimitCooldownMs', label: '限流冷却毫秒', type: 'number', valueType: 'number', required: true },
        { name: 'policyVersion', label: '策略版本', type: 'number', readOnly: true },
      ],
      initialValues: {
        maxConcurrentRequests: String(policy?.maxConcurrentRequests ?? 0),
        consecutiveFailureThreshold: String(policy?.consecutiveFailureThreshold ?? 3),
        failureResetAfterMs: String(policy?.failureResetAfterMs ?? 60_000),
        failureCooldownMs: String(policy?.failureCooldownMs ?? 30_000),
        rateLimitCooldownMs: String(policy?.rateLimitCooldownMs ?? 30_000),
        policyVersion: String(policy?.policyVersion ?? ''),
      },
      onSubmit: (values) => {
        const body = { ...values }
        delete body.policyVersion
        return runSave('运行时策略已保存', () => updateRuntimePolicy(selectedResourceId, body))
      },
    })
  }

  /** 打开资源池表单。 */
  const openPoolDialog = (pool?: AiResourcePool) => {
    setFormDialog({
      title: pool ? '编辑资源池' : '新增资源池',
      description: '资源池用于静态路由目标分组，停用后不会成为新请求候选。',
      fields: [
        { name: 'code', label: '编码', required: true },
        { name: 'displayName', label: '名称', required: true },
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: pool?.code ?? '',
        displayName: pool?.displayName ?? '',
        adminStatus: pool?.adminStatus ?? 'ENABLED',
        description: pool?.description ?? '',
      },
      onSubmit: (values) => runSave('资源池已保存', () => (
        pool ? updateResourcePool(pool.id, values) : createResourcePool(values)
      )),
    })
  }

  /** 打开资源池成员表单。 */
  const openPoolMemberDialog = (member?: AiPoolMember) => {
    if (!selectedPoolId) {
      toast.error('请先创建资源池')
      return
    }
    setFormDialog({
      title: member ? '编辑资源池成员' : '新增资源池成员',
      description: '同优先级内按正整数权重选择资源，DRAINING 资源不会成为新请求静态候选。',
      fields: [
        ...(!member ? [{ name: 'executionResourceId', label: '执行资源', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(resources) }] : []),
        { name: 'priority', label: '优先级', type: 'number', valueType: 'number', required: true },
        { name: 'weight', label: '权重', type: 'number', valueType: 'number', required: true },
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
      ],
      initialValues: {
        executionResourceId: String(member?.executionResourceId ?? resources[0]?.id ?? ''),
        priority: String(member?.priority ?? 100),
        weight: String(member?.weight ?? 1),
        adminStatus: member?.adminStatus ?? 'ENABLED',
      },
      onSubmit: (values) => runSave('资源池成员已保存', () => (
        member ? updatePoolMember(member.id, values) : createPoolMember(selectedPoolId, values)
      )),
    })
  }

  /** 打开模型绑定表单。 */
  const openBindingDialog = (binding?: AiModelBinding) => {
    setFormDialog({
      title: binding ? '编辑模型绑定' : '新增模型绑定',
      description: '只支持精确 PublicModel + Operation 到上游模型名映射。',
      fields: [
        ...(!binding ? [
          { name: 'executionResourceId', label: '执行资源', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(resources) },
          { name: 'publicModelId', label: '公开模型', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(models) },
          { name: 'canonicalOperation', label: '操作', type: 'select' as const, required: true, options: enumChoices(options?.canonicalOperations) },
        ] : []),
        { name: 'upstreamModelName', label: '上游模型名', required: true },
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
      ],
      initialValues: {
        executionResourceId: String(binding?.executionResourceId ?? resources[0]?.id ?? ''),
        publicModelId: String(binding?.publicModelId ?? models[0]?.id ?? ''),
        canonicalOperation: binding?.canonicalOperation ?? 'CHAT_COMPLETIONS',
        upstreamModelName: binding?.upstreamModelName ?? '',
        adminStatus: binding?.adminStatus ?? 'ENABLED',
      },
      onSubmit: (values) => runSave('模型绑定已保存', () => (
        binding ? updateModelBinding(binding.id, values) : createModelBinding(values)
      )),
    })
  }

  /** 打开路由策略表单。 */
  const openRoutePolicyDialog = (policy?: AiRoutePolicy) => {
    setFormDialog({
      title: policy ? '编辑路由策略' : '新增路由策略',
      description: '路由策略默认 DRAFT，只有拓扑校验通过后才能启用。',
      fields: [
        ...(!policy ? [
          { name: 'publicModelId', label: '公开模型', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(models) },
          { name: 'canonicalOperation', label: '操作', type: 'select' as const, required: true, options: enumChoices(options?.canonicalOperations) },
        ] : []),
        { name: 'displayName', label: '名称', required: true },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        publicModelId: String(policy?.publicModelId ?? models[0]?.id ?? ''),
        canonicalOperation: policy?.canonicalOperation ?? 'CHAT_COMPLETIONS',
        displayName: policy?.displayName ?? '',
        description: policy?.description ?? '',
      },
      onSubmit: (values) => runSave('路由策略已保存', () => (
        policy ? updateRoutePolicy(policy.id, values) : createRoutePolicy(values)
      )),
    })
  }

  /** 打开路由目标表单。 */
  const openRouteTargetDialog = (target?: AiRouteTarget) => {
    if (!selectedPolicyId) {
      toast.error('请先创建路由策略')
      return
    }
    setFormDialog({
      title: target ? '编辑路由目标' : '新增路由目标',
      description: '高优先级目标池先选中，同层按权重选择。',
      fields: [
        ...(!target ? [{ name: 'resourcePoolId', label: '资源池', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(pools) }] : []),
        { name: 'priority', label: '优先级', type: 'number', valueType: 'number', required: true },
        { name: 'weight', label: '权重', type: 'number', valueType: 'number', required: true },
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
      ],
      initialValues: {
        resourcePoolId: String(target?.resourcePoolId ?? pools[0]?.id ?? ''),
        priority: String(target?.priority ?? 100),
        weight: String(target?.weight ?? 1),
        adminStatus: target?.adminStatus ?? 'ENABLED',
      },
      onSubmit: (values) => runSave('路由目标已保存', () => (
        target ? updateRouteTarget(target.id, values) : createRouteTarget(selectedPolicyId, values)
      )),
    })
  }

  /** 打开访问组表单。 */
  const openAccessGroupDialog = (group?: AiAccessGroup) => {
    setFormDialog({
      title: group ? '编辑访问组' : '新增访问组',
      description: '访问组采用授权并集，只支持精确模型和操作授权。',
      fields: [
        { name: 'code', label: '编码', required: true },
        { name: 'displayName', label: '名称', required: true },
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: group?.code ?? '',
        displayName: group?.displayName ?? '',
        adminStatus: group?.adminStatus ?? 'ENABLED',
        description: group?.description ?? '',
      },
      onSubmit: (values) => runSave('访问组已保存', () => (
        group ? updateAccessGroup(group.id, values) : createAccessGroup(values)
      )),
    })
  }

  /** 打开模型授权表单。 */
  const openModelGrantDialog = (grant?: AiModelGrant) => {
    if (!selectedAccessGroupId) {
      toast.error('请先创建访问组')
      return
    }
    setFormDialog({
      title: grant ? '编辑模型授权' : '新增模型授权',
      description: '只支持 exact PublicModel + CanonicalOperation，不支持 wildcard。',
      fields: [
        ...(!grant ? [
          { name: 'publicModelId', label: '公开模型', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(models) },
          { name: 'canonicalOperation', label: '操作', type: 'select' as const, required: true, options: enumChoices(options?.canonicalOperations) },
        ] : []),
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
      ],
      initialValues: {
        publicModelId: String(grant?.publicModelId ?? models[0]?.id ?? ''),
        canonicalOperation: grant?.canonicalOperation ?? 'CHAT_COMPLETIONS',
        adminStatus: grant?.adminStatus ?? 'ENABLED',
      },
      onSubmit: (values) => runSave('模型授权已保存', () => (
        grant ? updateModelGrant(grant.id, values) : createModelGrant(selectedAccessGroupId, values)
      )),
    })
  }

  /** 打开 Client API Key 表单。 */
  const openClientKeyDialog = (key?: AiClientApiKey) => {
    setFormDialog({
      title: key ? '编辑 Client API Key' : '新增 Client API Key',
      description: key ? '只能编辑元数据，无法查看 raw key。' : '创建成功后 raw key 只展示一次。',
      fields: [
        { name: 'code', label: '编码', required: true },
        { name: 'displayName', label: '名称', required: true },
        { name: 'expiresAt', label: '过期时间', type: 'datetime-local', valueType: 'datetime' },
        { name: 'description', label: '描述', type: 'textarea', wide: true },
      ],
      initialValues: {
        code: key?.code ?? '',
        displayName: key?.displayName ?? '',
        expiresAt: toDateTimeInput(key?.expiresAt),
        description: key?.description ?? '',
      },
      onSubmit: async (values) => {
        if (key) {
          await runSave('Client API Key 已保存', () => updateClientApiKey(key.id, values))
          return
        }
        try {
          const created = await createClientApiKey(values)
          showRawKey('Client API Key 已创建', created)
          toast.success('Client API Key 已创建')
          refreshAll()
        } catch (error) {
          toast.error(getErrorMessage(error))
          throw error
        }
      },
    })
  }

  /** 打开 Key 访问组绑定表单。 */
  const openKeyAccessGroupDialog = (binding?: AiKeyAccessGroup) => {
    if (!selectedClientKeyId) {
      toast.error('请先创建 Client API Key')
      return
    }
    setFormDialog({
      title: binding ? '编辑 Key 访问组绑定' : '新增 Key 访问组绑定',
      description: 'Key 可绑定多个访问组，实际授权取访问组授权并集。',
      fields: [
        ...(!binding ? [{ name: 'accessGroupId', label: '访问组', type: 'select' as const, valueType: 'number' as const, required: true, options: entityChoices(accessGroups) }] : []),
        { name: 'adminStatus', label: '状态', type: 'select', required: true, options: enumChoices(options?.catalogStatuses) },
      ],
      initialValues: {
        accessGroupId: String(binding?.accessGroupId ?? accessGroups[0]?.id ?? ''),
        adminStatus: binding?.adminStatus ?? 'ENABLED',
      },
      onSubmit: (values) => runSave('Key 访问组绑定已保存', () => (
        binding ? updateKeyAccessGroup(binding.id, values) : createKeyAccessGroup(selectedClientKeyId, values)
      )),
    })
  }

  /** 执行路由预览。 */
  const handlePreview = async () => {
    try {
      const result = await previewRoute({ ...previewForm })
      setPreviewResult(result)
      toast.success('路由预览已生成')
    } catch (error) {
      toast.error(getErrorMessage(error))
    }
  }

  /** 执行非流式 Chat 测试。 */
  const handleChatTest = async () => {
    setChatLoading(true)
    try {
      const parsedMessages = JSON.parse(chatForm.messages) as AiGatewayChatMessage[]
      const response = await testGatewayChatCompletions({
        clientApiKey: chatForm.clientApiKey,
        model: chatForm.model,
        messages: parsedMessages,
        temperature: chatForm.temperature ? Number(chatForm.temperature) : undefined,
        maxTokens: chatForm.maxTokens ? Number(chatForm.maxTokens) : undefined,
      })
      setChatResult(response)
      toast.success('Chat 测试请求已完成')
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setChatLoading(false)
    }
  }

  /** 通用实体状态操作。 */
  const askStatusChange = (
    title: string,
    description: string,
    action: () => Promise<unknown>,
    destructive = false,
  ) => {
    confirmAction({
      title,
      description,
      destructive,
      onConfirm: () => runSave('状态已更新', action),
    })
  }

  /** 轮换 Client API Key。 */
  const askRotateClientKey = (key: AiClientApiKey) => {
    confirmAction({
      title: '轮换 Client API Key',
      description: '轮换后旧 key 将失效，新 raw key 只展示一次。请确认当前操作者可以立即保存。',
      destructive: true,
      onConfirm: async () => {
        try {
          const rotated = await rotateClientApiKey(key.id)
          showRawKey('Client API Key 已轮换', rotated)
          toast.success('Client API Key 已轮换')
          refreshAll()
        } catch (error) {
          toast.error(getErrorMessage(error))
          throw error
        }
      },
    })
  }

  /** Provider 表格列。 */
  const providerColumns: Column<AiProvider>[] = [
    { key: 'displayName', header: 'Provider', render: (item) => <EntityName item={item} /> },
    { key: 'providerKind', header: '类型', render: (item) => item.providerKind ?? '—' },
    { key: 'status', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    { key: 'updatedAt', header: '更新时间', render: (item) => formatTime(item.updatedAt) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openProviderDialog(item)}
          onEnable={() => askStatusChange('启用 Provider', `确认启用 ${entityTitle(item)}？`, () => changeProviderStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用 Provider', `停用后相关连接可能不可用。确认停用 ${entityTitle(item)}？`, () => changeProviderStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** Connection 表格列。 */
  const connectionColumns: Column<AiConnection>[] = [
    { key: 'displayName', header: '连接', render: (item) => <EntityName item={item} /> },
    { key: 'protocolType', header: '协议', render: (item) => item.protocolType ?? '—' },
    { key: 'baseUrl', header: 'Base URL', render: (item) => item.baseUrl ?? '—' },
    { key: 'status', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openConnectionDialog(item)}
          onEnable={() => askStatusChange('启用连接', `确认启用 ${entityTitle(item)}？`, () => changeConnectionStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用连接', `确认停用 ${entityTitle(item)}？`, () => changeConnectionStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** 模型表格列。 */
  const modelColumns: Column<AiPublicModel>[] = [
    { key: 'displayName', header: '公开模型', render: (item) => <EntityName item={item} /> },
    { key: 'modelFamily', header: '模型族', render: (item) => item.modelFamily ?? '—' },
    { key: 'status', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openModelDialog(item)}
          onEnable={() => askStatusChange('启用公开模型', `确认启用 ${entityTitle(item)}？`, () => changeModelStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用公开模型', `确认停用 ${entityTitle(item)}？`, () => changeModelStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** 凭据表格列。 */
  const credentialColumns: Column<AiCredential>[] = [
    { key: 'displayName', header: '凭据', render: (item) => <EntityName item={item} /> },
    { key: 'maskedPreview', header: '掩码', render: (item) => item.maskedPreview ?? '—' },
    { key: 'secretVersion', header: '版本', render: (item) => item.secretVersion ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => openCredentialDialog(item)}>
            <Pencil className="size-4" />
            编辑
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              setFormDialog({
                title: '轮换上游凭据',
                description: '新 API Key 只用于本次请求，后端加密后不会返回明文。',
                fields: [{ name: 'newApiKey', label: '新 API Key', type: 'password', required: true, wide: true }],
                initialValues: { newApiKey: '' },
                onSubmit: (values) => runSave('凭据已轮换', () => rotateCredential(item.id, String(values.newApiKey ?? ''))),
              })
            }}
          >
            <KeyRound className="size-4" />
            轮换
          </Button>
          <StatusButtons
            onEnable={() => askStatusChange('启用凭据', `确认启用 ${entityTitle(item)}？`, () => changeCredentialStatus(item.id, 'enable'))}
            onDisable={() => askStatusChange('停用凭据', `确认停用 ${entityTitle(item)}？`, () => changeCredentialStatus(item.id, 'disable'), true)}
          />
        </div>
      ),
    },
  ]

  /** 执行资源表格列。 */
  const resourceColumns: Column<AiExecutionResource>[] = [
    { key: 'displayName', header: '执行资源', render: (item) => <EntityName item={item} /> },
    { key: 'upstreamConnectionId', header: '连接', render: (item) => item.upstreamConnectionId ?? '—' },
    { key: 'credentialId', header: '凭据', render: (item) => item.credentialId ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => openResourceDialog(item)}>
            <Pencil className="size-4" />
            编辑
          </Button>
          <Button size="sm" variant="outline" onClick={() => setSelectedResourceId(item.id)}>
            <Activity className="size-4" />
            策略
          </Button>
          <StatusButtons
            onEnable={() => askStatusChange('启用资源', `确认启用 ${entityTitle(item)}？`, () => changeResourceStatus(item.id, 'enable'))}
            onDisable={() => askStatusChange('停用资源', `确认停用 ${entityTitle(item)}？`, () => changeResourceStatus(item.id, 'disable'), true)}
          />
          <Button
            size="sm"
            variant="outline"
            onClick={() => askStatusChange('排空资源', '排空后该资源不再承接新请求，确认继续？', () => changeResourceStatus(item.id, 'drain'), true)}
          >
            排空
          </Button>
        </div>
      ),
    },
  ]

  /** 资源池列。 */
  const poolColumns: Column<AiResourcePool>[] = [
    { key: 'displayName', header: '资源池', render: (item) => <EntityName item={item} /> },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => setSelectedPoolId(item.id)}>成员</Button>
          <RowActions
            onEdit={() => openPoolDialog(item)}
            onEnable={() => askStatusChange('启用资源池', `确认启用 ${entityTitle(item)}？`, () => changeResourcePoolStatus(item.id, 'enable'))}
            onDisable={() => askStatusChange('停用资源池', `确认停用 ${entityTitle(item)}？`, () => changeResourcePoolStatus(item.id, 'disable'), true)}
          />
        </div>
      ),
    },
  ]

  /** 资源池成员列。 */
  const poolMemberColumns: Column<AiPoolMember>[] = [
    { key: 'executionResourceId', header: '资源 ID', render: (item) => item.executionResourceId ?? '—' },
    { key: 'priority', header: '优先级', render: (item) => item.priority ?? '—' },
    { key: 'weight', header: '权重', render: (item) => item.weight ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openPoolMemberDialog(item)}
          onEnable={() => askStatusChange('启用成员', '确认启用该成员？', () => changePoolMemberStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用成员', '确认停用该成员？', () => changePoolMemberStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** 模型绑定列。 */
  const bindingColumns: Column<AiModelBinding>[] = [
    { key: 'publicModelId', header: '公开模型 ID', render: (item) => item.publicModelId ?? '—' },
    { key: 'executionResourceId', header: '资源 ID', render: (item) => item.executionResourceId ?? '—' },
    { key: 'canonicalOperation', header: '操作', render: (item) => item.canonicalOperation ?? '—' },
    { key: 'upstreamModelName', header: '上游模型', render: (item) => item.upstreamModelName ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openBindingDialog(item)}
          onEnable={() => askStatusChange('启用绑定', '确认启用该模型绑定？', () => changeModelBindingStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用绑定', '确认停用该模型绑定？', () => changeModelBindingStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** 路由策略列。 */
  const policyColumns: Column<AiRoutePolicy>[] = [
    { key: 'displayName', header: '路由策略', render: (item) => <EntityName item={item} /> },
    { key: 'publicModelId', header: '公开模型 ID', render: (item) => item.publicModelId ?? '—' },
    { key: 'canonicalOperation', header: '操作', render: (item) => item.canonicalOperation ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => setSelectedPolicyId(item.id)}>目标</Button>
          <RowActions
            onEdit={() => openRoutePolicyDialog(item)}
            onEnable={() => askStatusChange('启用路由策略', '启用前后端会校验静态拓扑。确认继续？', () => changeRoutePolicyStatus(item.id, 'enable'), true)}
            onDisable={() => askStatusChange('停用路由策略', '确认停用该路由策略？', () => changeRoutePolicyStatus(item.id, 'disable'), true)}
          />
        </div>
      ),
    },
  ]

  /** 路由目标列。 */
  const targetColumns: Column<AiRouteTarget>[] = [
    { key: 'resourcePoolId', header: '资源池 ID', render: (item) => item.resourcePoolId ?? '—' },
    { key: 'priority', header: '优先级', render: (item) => item.priority ?? '—' },
    { key: 'weight', header: '权重', render: (item) => item.weight ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openRouteTargetDialog(item)}
          onEnable={() => askStatusChange('启用目标池', '确认启用该目标池？', () => changeRouteTargetStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用目标池', '确认停用该目标池？', () => changeRouteTargetStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** 访问组列。 */
  const accessGroupColumns: Column<AiAccessGroup>[] = [
    { key: 'displayName', header: '访问组', render: (item) => <EntityName item={item} /> },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => setSelectedAccessGroupId(item.id)}>授权</Button>
          <RowActions
            onEdit={() => openAccessGroupDialog(item)}
            onEnable={() => askStatusChange('启用访问组', `确认启用 ${entityTitle(item)}？`, () => changeAccessGroupStatus(item.id, 'enable'))}
            onDisable={() => askStatusChange('停用访问组', `确认停用 ${entityTitle(item)}？`, () => changeAccessGroupStatus(item.id, 'disable'), true)}
          />
        </div>
      ),
    },
  ]

  /** 模型授权列。 */
  const grantColumns: Column<AiModelGrant>[] = [
    { key: 'publicModelId', header: '公开模型 ID', render: (item) => item.publicModelId ?? '—' },
    { key: 'canonicalOperation', header: '操作', render: (item) => item.canonicalOperation ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openModelGrantDialog(item)}
          onEnable={() => askStatusChange('启用授权', '确认启用该模型授权？', () => changeModelGrantStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用授权', '确认停用该模型授权？', () => changeModelGrantStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  /** Client API Key 列。 */
  const clientKeyColumns: Column<AiClientApiKey>[] = [
    { key: 'displayName', header: 'Client Key', render: (item) => <EntityName item={item} subText={item.maskedPreview} /> },
    { key: 'keyVersion', header: '版本', render: (item) => item.keyVersion ?? '—' },
    { key: 'expiresAt', header: '过期时间', render: (item) => formatTime(item.expiresAt) },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => setSelectedClientKeyId(item.id)}>绑定</Button>
          <Button size="sm" variant="outline" onClick={() => openClientKeyDialog(item)}>
            <Pencil className="size-4" />
            编辑
          </Button>
          <Button size="sm" variant="outline" onClick={() => askRotateClientKey(item)}>
            <RefreshCw className="size-4" />
            轮换
          </Button>
          <StatusButtons
            onEnable={() => askStatusChange('启用 Key', `确认启用 ${entityTitle(item)}？`, () => changeClientApiKeyStatus(item.id, 'enable'))}
            onDisable={() => askStatusChange('停用 Key', `确认停用 ${entityTitle(item)}？`, () => changeClientApiKeyStatus(item.id, 'disable'), true)}
          />
          <Button
            size="sm"
            variant="destructive"
            onClick={() => askStatusChange('撤销 Client API Key', '撤销后不可恢复，也不能重新启用或轮换。确认继续？', () => changeClientApiKeyStatus(item.id, 'revoke'), true)}
          >
            撤销
          </Button>
        </div>
      ),
    },
  ]

  /** Key 访问组绑定列。 */
  const keyAccessGroupColumns: Column<AiKeyAccessGroup>[] = [
    { key: 'accessGroupId', header: '访问组 ID', render: (item) => item.accessGroupId ?? '—' },
    { key: 'adminStatus', header: '状态', render: (item) => renderStatusBadge(entityStatus(item)) },
    {
      key: 'actions',
      header: '操作',
      className: 'text-right',
      render: (item) => (
        <RowActions
          onEdit={() => openKeyAccessGroupDialog(item)}
          onEnable={() => askStatusChange('启用绑定', '确认启用该 Key 访问组绑定？', () => changeKeyAccessGroupStatus(item.id, 'enable'))}
          onDisable={() => askStatusChange('停用绑定', '确认停用该 Key 访问组绑定？', () => changeKeyAccessGroupStatus(item.id, 'disable'), true)}
        />
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <h1 className="text-2xl font-bold">AI 网关</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            管理上游接入、静态路由、下游访问授权、运行状态和非流式 Chat 测试。
          </p>
        </div>
        <Button variant="outline" onClick={refreshAll}>
          <RefreshCw className="size-4" />
          刷新
        </Button>
      </div>

      <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as ConsoleTab)}>
        <TabsList className="!h-auto min-h-8 w-full flex-wrap justify-start">
          <TabsTrigger value="overview"><Activity className="size-4" />概览</TabsTrigger>
          <TabsTrigger value="catalog"><Bot className="size-4" />上游配置</TabsTrigger>
          <TabsTrigger value="resources"><KeyRound className="size-4" />凭据与资源</TabsTrigger>
          <TabsTrigger value="routing"><Route className="size-4" />路由配置</TabsTrigger>
          <TabsTrigger value="access"><ShieldCheck className="size-4" />下游访问</TabsTrigger>
          <TabsTrigger value="status"><Network className="size-4" />运行状态</TabsTrigger>
          <TabsTrigger value="test"><TestTube2 className="size-4" />在线测试</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard title="Gateway Ready" value={String(readyQuery.data?.status ?? '未知')} />
            <MetricCard title="Loaded Tenants" value={String(snapshotQuery.data?.loadedTenantCount ?? '—')} />
            <MetricCard title="Client Keys" value={String(snapshotQuery.data?.loadedClientKeyCount ?? snapshotQuery.data?.clientKeyCount ?? '—')} />
            <MetricCard title="Runtime Redis" value={String(runtimeStatusQuery.data?.redisAvailable ?? '未知')} />
          </div>
          <Card>
            <CardHeader>
              <CardTitle>配置快捷入口</CardTitle>
              <CardDescription>按最小闭环顺序创建对象，启用前请先完成静态路由预览。</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => openProviderDialog()}><Plus className="size-4" />新增 Provider</Button>
              <Button variant="outline" onClick={() => openCredentialDialog()}><Plus className="size-4" />新增 Credential</Button>
              <Button variant="outline" onClick={() => openResourceDialog()}><Plus className="size-4" />新增 Resource</Button>
              <Button variant="outline" onClick={() => openRoutePolicyDialog()}><Plus className="size-4" />新增 Route Policy</Button>
              <Button variant="outline" onClick={() => openClientKeyDialog()}><Plus className="size-4" />新增 Client Key</Button>
              <Button onClick={() => setActiveTab('test')}><Play className="size-4" />Chat 测试</Button>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>状态摘要</CardTitle>
              <CardDescription>状态由控制面代理读取，页面不直连 Gateway 内部接口。</CardDescription>
            </CardHeader>
            <CardContent>
              <StatusGrid data={snapshotQuery.data} loading={snapshotQuery.loading} error={snapshotQuery.error} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="catalog" className="grid gap-4 xl:grid-cols-3">
          <EntityCard title="Provider" description="供应商目录元数据。" actionText="新增 Provider" onAction={() => openProviderDialog()}>
            <DataTable columns={providerColumns} data={providers} loading={providersQuery.loading} emptyText="暂无 Provider" />
          </EntityCard>
          <EntityCard
            title="Connection"
            description="按 Provider 管理上游连接。"
            actionText="新增 Connection"
            onAction={() => openConnectionDialog()}
            extra={<ParentSelect value={selectedProviderId} items={providers} onChange={setSelectedProviderId} />}
          >
            <DataTable columns={connectionColumns} data={connections} loading={connectionsQuery.loading} emptyText="暂无上游连接" />
          </EntityCard>
          <EntityCard title="Public Model" description="公开模型目录。" actionText="新增 Model" onAction={() => openModelDialog()}>
            <DataTable columns={modelColumns} data={models} loading={modelsQuery.loading} emptyText="暂无公开模型" />
          </EntityCard>
        </TabsContent>

        <TabsContent value="resources" className="space-y-4">
          <div className="grid gap-4 xl:grid-cols-2">
            <EntityCard
              title="Credential"
              description="只展示掩码预览，不提供查看明文。"
              actionText="新增 Credential"
              onAction={() => openCredentialDialog()}
              extra={<ParentSelect value={selectedProviderId} items={providers} onChange={setSelectedProviderId} />}
            >
              <DataTable columns={credentialColumns} data={credentials} loading={credentialsQuery.loading} emptyText="暂无凭据" />
            </EntityCard>
            <EntityCard title="Execution Resource" description="资源绑定创建后不可修改。" actionText="新增 Resource" onAction={() => openResourceDialog()}>
              <DataTable columns={resourceColumns} data={resources} loading={resourcesQuery.loading} emptyText="暂无执行资源" />
            </EntityCard>
          </div>
          <Card>
            <CardHeader>
              <CardTitle>Runtime Policy</CardTitle>
              <CardDescription>选择执行资源后查看或编辑运行时治理策略。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex flex-wrap items-center gap-2">
                <ParentSelect value={selectedResourceId} items={resources} onChange={setSelectedResourceId} />
                <Button variant="outline" onClick={openRuntimePolicyDialog}><Pencil className="size-4" />编辑策略</Button>
              </div>
              <JsonBlock value={runtimePolicyQuery.data} />
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="routing" className="space-y-4">
          <div className="grid gap-4 xl:grid-cols-2">
            <EntityCard title="Resource Pool" description="静态候选资源池。" actionText="新增 Pool" onAction={() => openPoolDialog()}>
              <DataTable columns={poolColumns} data={pools} loading={poolsQuery.loading} emptyText="暂无资源池" />
            </EntityCard>
            <EntityCard
              title="Pool Member"
              description="资源池成员权重与优先级。"
              actionText="新增 Member"
              onAction={() => openPoolMemberDialog()}
              extra={<ParentSelect value={selectedPoolId} items={pools} onChange={setSelectedPoolId} />}
            >
              <DataTable columns={poolMemberColumns} data={poolMembers} loading={poolMembersQuery.loading} emptyText="暂无成员" />
            </EntityCard>
            <EntityCard title="Model Binding" description="精确模型与上游模型名绑定。" actionText="新增 Binding" onAction={() => openBindingDialog()}>
              <DataTable columns={bindingColumns} data={bindings} loading={bindingsQuery.loading} emptyText="暂无模型绑定" />
            </EntityCard>
            <EntityCard title="Route Policy" description="静态路由策略。" actionText="新增 Policy" onAction={() => openRoutePolicyDialog()}>
              <DataTable columns={policyColumns} data={policies} loading={policiesQuery.loading} emptyText="暂无路由策略" />
            </EntityCard>
            <EntityCard
              title="Route Target"
              description="策略目标池。"
              actionText="新增 Target"
              onAction={() => openRouteTargetDialog()}
              extra={<ParentSelect value={selectedPolicyId} items={policies} onChange={setSelectedPolicyId} />}
            >
              <DataTable columns={targetColumns} data={routeTargets} loading={routeTargetsQuery.loading} emptyText="暂无路由目标" />
            </EntityCard>
            <Card>
              <CardHeader>
                <CardTitle>Route Preview</CardTitle>
                <CardDescription>仅静态预览，不代表 runtime lease、熔断、并发限制后的真实候选。</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                <Input
                  value={previewForm.publicModelCode}
                  onChange={(event) => setPreviewForm({ ...previewForm, publicModelCode: event.target.value })}
                  placeholder="公开模型编码"
                />
                <Select value={previewForm.canonicalOperation} onValueChange={(value) => setPreviewForm({ ...previewForm, canonicalOperation: value ?? '' })}>
                  <SelectTrigger className="w-full"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {enumChoices(options?.canonicalOperations).map((item) => (
                      <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Input
                  value={previewForm.selectionSeed}
                  onChange={(event) => setPreviewForm({ ...previewForm, selectionSeed: event.target.value })}
                  placeholder="selection seed"
                />
                <Button onClick={() => { void handlePreview() }}><Play className="size-4" />执行预览</Button>
                <JsonBlock value={previewResult} emptyText="尚未执行预览" />
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="access" className="space-y-4">
          <div className="grid gap-4 xl:grid-cols-2">
            <EntityCard title="Access Group" description="下游访问组。" actionText="新增 Group" onAction={() => openAccessGroupDialog()}>
              <DataTable columns={accessGroupColumns} data={accessGroups} loading={accessGroupsQuery.loading} emptyText="暂无访问组" />
            </EntityCard>
            <EntityCard
              title="Model Grant"
              description="访问组模型授权。"
              actionText="新增 Grant"
              onAction={() => openModelGrantDialog()}
              extra={<ParentSelect value={selectedAccessGroupId} items={accessGroups} onChange={setSelectedAccessGroupId} />}
            >
              <DataTable columns={grantColumns} data={modelGrants} loading={modelGrantsQuery.loading} emptyText="暂无模型授权" />
            </EntityCard>
            <EntityCard title="Client API Key" description="raw key 仅创建和轮换后展示一次。" actionText="新增 Client Key" onAction={() => openClientKeyDialog()}>
              <DataTable columns={clientKeyColumns} data={clientKeys} loading={clientKeysQuery.loading} emptyText="暂无 Client API Key" />
            </EntityCard>
            <EntityCard
              title="Key Access Group"
              description="Client Key 与访问组绑定。"
              actionText="新增绑定"
              onAction={() => openKeyAccessGroupDialog()}
              extra={<ParentSelect value={selectedClientKeyId} items={clientKeys} onChange={setSelectedClientKeyId} />}
            >
              <DataTable columns={keyAccessGroupColumns} data={keyAccessGroups} loading={keyAccessGroupsQuery.loading} emptyText="暂无绑定" />
            </EntityCard>
          </div>
        </TabsContent>

        <TabsContent value="status" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Snapshot Status</CardTitle>
              <CardDescription>已过滤 Redis key、resourceId、leaseId、baseUrl、secret 等敏感字段。</CardDescription>
            </CardHeader>
            <CardContent><StatusGrid data={snapshotQuery.data} loading={snapshotQuery.loading} error={snapshotQuery.error} /></CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Runtime Status</CardTitle>
              <CardDescription>仅展示运行治理汇总指标，不展示租约细节。</CardDescription>
            </CardHeader>
            <CardContent><StatusGrid data={runtimeStatusQuery.data} loading={runtimeStatusQuery.loading} error={runtimeStatusQuery.error} /></CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="test" className="grid gap-4 xl:grid-cols-[420px_1fr]">
          <Card>
            <CardHeader>
              <CardTitle>Chat Completions 测试</CardTitle>
              <CardDescription>一期仅支持非流式测试，Client API Key 不会持久化。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="chat-key">Client API Key</Label>
                <Input
                  id="chat-key"
                  type="password"
                  value={chatForm.clientApiKey}
                  onChange={(event) => setChatForm({ ...chatForm, clientApiKey: event.target.value })}
                  placeholder="cvg_live_..."
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="chat-model">Model</Label>
                <Input
                  id="chat-model"
                  value={chatForm.model}
                  onChange={(event) => setChatForm({ ...chatForm, model: event.target.value })}
                  placeholder="公开模型编码"
                />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="chat-temperature">temperature</Label>
                  <Input
                    id="chat-temperature"
                    type="number"
                    step="0.1"
                    value={chatForm.temperature}
                    onChange={(event) => setChatForm({ ...chatForm, temperature: event.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="chat-max-tokens">maxTokens</Label>
                  <Input
                    id="chat-max-tokens"
                    type="number"
                    value={chatForm.maxTokens}
                    onChange={(event) => setChatForm({ ...chatForm, maxTokens: event.target.value })}
                  />
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="chat-messages">messages JSON</Label>
                <textarea
                  id="chat-messages"
                  className="min-h-40 w-full rounded-md border border-input bg-transparent px-3 py-2 font-mono text-xs shadow-sm outline-none focus-visible:ring-1 focus-visible:ring-ring"
                  value={chatForm.messages}
                  onChange={(event) => setChatForm({ ...chatForm, messages: event.target.value })}
                />
              </div>
              <Button onClick={() => { void handleChatTest() }} disabled={chatLoading}>
                {chatLoading ? <RefreshCw className="size-4 animate-spin" /> : <Play className="size-4" />}
                发送非流式测试
              </Button>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>测试结果</CardTitle>
              <CardDescription>显示控制面代理包装后的状态、耗时、错误码和响应 JSON。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {chatResult && (
                <div className="grid gap-3 sm:grid-cols-3">
                  <MetricCard title="HTTP 状态" value={String(chatResult.status)} />
                  <MetricCard title="耗时" value={`${chatResult.latencyMs} ms`} />
                  <MetricCard title="错误码" value={chatResult.errorCode ?? '—'} />
                </div>
              )}
              <JsonBlock value={chatResult} emptyText="尚未发送测试请求" />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <EntityDialog state={formDialog} onClose={() => setFormDialog(null)} />
      <RawKeyDialog state={rawKeyState} onClose={() => setRawKeyState(null)} />
      <ConfirmDialog
        open={confirmState !== null}
        onOpenChange={(open) => {
          if (!open) setConfirmState(null)
        }}
        title={confirmState?.title ?? ''}
        description={confirmState?.description ?? ''}
        destructive={confirmState?.destructive}
        onConfirm={async () => {
          await confirmState?.onConfirm()
          setConfirmState(null)
        }}
      />
    </div>
  )
}

/** 实体名称组件属性。 */
interface EntityNameProps {
  /** 实体对象。 */
  item: AiEntity
  /** 次级文本。 */
  subText?: string
}

/**
 * 实体名称组件。
 * @param props 组件属性
 */
function EntityName({ item, subText }: EntityNameProps) {
  return (
    <div>
      <div className="font-medium">{entityTitle(item)}</div>
      <div className="mt-1 text-xs text-muted-foreground">{subText ?? item.code ?? `ID ${item.id}`}</div>
    </div>
  )
}

/** 行内操作组件属性。 */
interface RowActionsProps {
  /** 编辑回调。 */
  onEdit: () => void
  /** 启用回调。 */
  onEnable: () => void
  /** 停用回调。 */
  onDisable: () => void
}

/**
 * 行内基础操作组件。
 * @param props 组件属性
 */
function RowActions({ onEdit, onEnable, onDisable }: RowActionsProps) {
  return (
    <div className="flex justify-end gap-2">
      <Button size="sm" variant="outline" onClick={onEdit}>
        <Pencil className="size-4" />
        编辑
      </Button>
      <StatusButtons onEnable={onEnable} onDisable={onDisable} />
    </div>
  )
}

/** 状态按钮组件属性。 */
interface StatusButtonsProps {
  /** 启用回调。 */
  onEnable: () => void
  /** 停用回调。 */
  onDisable: () => void
}

/**
 * 启停按钮组件。
 * @param props 组件属性
 */
function StatusButtons({ onEnable, onDisable }: StatusButtonsProps) {
  return (
    <>
      <Button size="sm" variant="outline" onClick={onEnable}>
        <Power className="size-4" />
        启用
      </Button>
      <Button size="sm" variant="outline" onClick={onDisable}>
        <XCircle className="size-4" />
        停用
      </Button>
    </>
  )
}

/** 实体卡片组件属性。 */
interface EntityCardProps {
  /** 卡片标题。 */
  title: string
  /** 卡片说明。 */
  description: string
  /** 操作按钮文本。 */
  actionText: string
  /** 操作按钮回调。 */
  onAction: () => void
  /** 右上角额外内容。 */
  extra?: ReactNode
  /** 卡片内容。 */
  children: ReactNode
}

/**
 * 实体列表卡片。
 * @param props 组件属性
 */
function EntityCard({ title, description, actionText, onAction, extra, children }: EntityCardProps) {
  return (
    <Card>
      <CardHeader className="gap-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <CardTitle>{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          <div className="flex flex-wrap gap-2">
            {extra}
            <Button size="sm" onClick={onAction}>
              <Plus className="size-4" />
              {actionText}
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

/** 父级实体选择器属性。 */
interface ParentSelectProps {
  /** 当前选择 ID。 */
  value: number | null
  /** 可选实体列表。 */
  items: AiEntity[]
  /** 变更回调。 */
  onChange: (value: number) => void
}

/**
 * 父级实体选择器。
 * @param props 组件属性
 */
function ParentSelect({ value, items, onChange }: ParentSelectProps) {
  if (items.length === 0) {
    return (
      <Button size="sm" variant="outline" disabled>
        <TriangleAlert className="size-4" />
        无可选项
      </Button>
    )
  }
  return (
    <Select value={value ? String(value) : String(items[0].id)} onValueChange={(next) => onChange(Number(next))}>
      <SelectTrigger size="sm" className="w-44">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {entityChoices(items).map((item) => (
          <SelectItem key={item.value} value={item.value}>{item.label}</SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}

/** 指标卡片属性。 */
interface MetricCardProps {
  /** 指标名称。 */
  title: string
  /** 指标值。 */
  value: string
}

/**
 * 指标卡片。
 * @param props 组件属性
 */
function MetricCard({ title, value }: MetricCardProps) {
  return (
    <Card size="sm">
      <CardContent>
        <div className="text-xs text-muted-foreground">{title}</div>
        <div className="mt-1 break-all text-xl font-semibold">{value}</div>
      </CardContent>
    </Card>
  )
}
