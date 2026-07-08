/**
 * AI Gateway 管理台 API client。
 *
 * 统一封装 /api/v1/ai/** 控制面接口，页面不得直接散落 fetch。
 * 本文件不记录、不持久化 raw key、Authorization、Credential 明文或测试消息内容。
 */

import { get, post, put } from '@/lib/api-client'
import type { PageResult } from '@/lib/types'

/** AI 控制面分页查询参数。 */
export interface AiPageQuery {
  /** 页码，从 1 开始。 */
  page?: number
  /** 每页数量。 */
  size?: number
  /** 关键字，可为空。 */
  keyword?: string
  /** 状态过滤，可为空。 */
  status?: string
}

/** 枚举选项响应项。 */
export interface AiEnumOption {
  /** 枚举编码。 */
  name: string
  /** 页面显示标签。 */
  label: string
  /** 页面显示说明。 */
  description: string
}

/** AI 管理台枚举选项。 */
export interface AiOptions {
  /** Provider 类型选项。 */
  providerKinds: AiEnumOption[]
  /** 上游协议类型选项。 */
  protocolTypes: AiEnumOption[]
  /** 目录对象状态选项。 */
  catalogStatuses: AiEnumOption[]
  /** 执行资源状态选项。 */
  resourceStatuses: AiEnumOption[]
  /** 凭据类型选项。 */
  credentialTypes: AiEnumOption[]
  /** 规范操作选项。 */
  canonicalOperations: AiEnumOption[]
  /** 选择策略选项。 */
  selectionPolicies: AiEnumOption[]
  /** 路由策略状态选项。 */
  routePolicyStatuses: AiEnumOption[]
  /** Client API Key 状态选项。 */
  clientApiKeyStatuses: AiEnumOption[]
}

/** AI 基础实体响应。 */
export interface AiEntity extends Record<string, unknown> {
  /** 实体 ID。 */
  id: number
  /** 对象编码，可为空。 */
  code?: string
  /** 展示名称，可为空。 */
  displayName?: string
  /** 描述，可为空。 */
  description?: string | null
  /** 目录状态，可为空。 */
  status?: string
  /** 管理状态，可为空。 */
  adminStatus?: string
  /** 创建时间，可为空。 */
  createdAt?: string
  /** 更新时间，可为空。 */
  updatedAt?: string
}

/** Provider 响应。 */
export interface AiProvider extends AiEntity {
  /** Provider 类型。 */
  providerKind?: string
}

/** 上游连接响应。 */
export interface AiConnection extends AiEntity {
  /** 所属 Provider ID。 */
  providerId?: number
  /** 上游协议类型。 */
  protocolType?: string
  /** 上游基础 URL，不包含凭据。 */
  baseUrl?: string
}

/** 公开模型响应。 */
export interface AiPublicModel extends AiEntity {
  /** 模型族，可为空。 */
  modelFamily?: string | null
}

/** 上游凭据响应。 */
export interface AiCredential extends AiEntity {
  /** 所属 Provider ID。 */
  providerId?: number
  /** 凭据类型。 */
  credentialType?: string
  /** 安全掩码预览。 */
  maskedPreview?: string
  /** 凭据版本号。 */
  secretVersion?: number
  /** 最近轮换时间。 */
  rotatedAt?: string | null
}

/** 执行资源响应。 */
export interface AiExecutionResource extends AiEntity {
  /** 上游连接 ID。 */
  upstreamConnectionId?: number
  /** 凭据 ID。 */
  credentialId?: number
  /** Provider ID。 */
  providerId?: number
  /** 资源类型。 */
  resourceType?: string
}

/** 执行资源运行时策略响应。 */
export interface AiRuntimePolicy extends Record<string, unknown> {
  /** 策略 ID。 */
  id?: number
  /** 执行资源 ID。 */
  executionResourceId?: number
  /** 最大本地并发，0 表示不限制。 */
  maxConcurrentRequests?: number
  /** 连续失败阈值。 */
  consecutiveFailureThreshold?: number
  /** 失败计数重置毫秒数。 */
  failureResetAfterMs?: number
  /** 失败冷却毫秒数。 */
  failureCooldownMs?: number
  /** 限流冷却毫秒数。 */
  rateLimitCooldownMs?: number
  /** 策略版本，只读。 */
  policyVersion?: number
}

/** 资源池响应。 */
export interface AiResourcePool extends AiEntity {
  /** 静态选择策略，可为空。 */
  selectionPolicy?: string
}

/** 资源池成员响应。 */
export interface AiPoolMember extends AiEntity {
  /** 执行资源 ID。 */
  executionResourceId?: number
  /** 资源池 ID。 */
  resourcePoolId?: number
  /** 优先级。 */
  priority?: number
  /** 权重。 */
  weight?: number
}

/** 模型绑定响应。 */
export interface AiModelBinding extends AiEntity {
  /** 执行资源 ID。 */
  executionResourceId?: number
  /** 公开模型 ID。 */
  publicModelId?: number
  /** 规范操作。 */
  canonicalOperation?: string
  /** 上游模型名。 */
  upstreamModelName?: string
}

/** 路由策略响应。 */
export interface AiRoutePolicy extends AiEntity {
  /** 公开模型 ID。 */
  publicModelId?: number
  /** 规范操作。 */
  canonicalOperation?: string
}

/** 路由目标响应。 */
export interface AiRouteTarget extends AiEntity {
  /** 路由策略 ID。 */
  routePolicyId?: number
  /** 资源池 ID。 */
  resourcePoolId?: number
  /** 优先级。 */
  priority?: number
  /** 权重。 */
  weight?: number
}

/** 访问组响应。 */
export interface AiAccessGroup extends AiEntity {
  /** 访问组状态。 */
  adminStatus?: string
}

/** 模型授权响应。 */
export interface AiModelGrant extends AiEntity {
  /** 访问组 ID。 */
  accessGroupId?: number
  /** 公开模型 ID。 */
  publicModelId?: number
  /** 规范操作。 */
  canonicalOperation?: string
}

/** Client API Key 安全响应。 */
export interface AiClientApiKey extends AiEntity {
  /** raw key 中公开可索引的 keyId。 */
  keyId?: string
  /** 掩码预览。 */
  maskedPreview?: string
  /** Key 版本。 */
  keyVersion?: number
  /** 过期时间。 */
  expiresAt?: string | null
  /** 最近轮换时间。 */
  rotatedAt?: string | null
  /** 撤销时间。 */
  revokedAt?: string | null
}

/** Client API Key 一次性响应。 */
export interface AiClientApiKeySecret extends AiClientApiKey {
  /** 仅创建或轮换响应中返回一次的 raw key。 */
  rawKey: string
}

/** Key 与访问组绑定响应。 */
export interface AiKeyAccessGroup extends AiEntity {
  /** Client API Key ID。 */
  clientApiKeyId?: number
  /** 访问组 ID。 */
  accessGroupId?: number
}

/** Gateway 安全状态对象。 */
export type AiGatewayStatus = Record<string, unknown>

/** Chat 测试消息。 */
export interface AiGatewayChatMessage {
  /** OpenAI 兼容消息角色。 */
  role: string
  /** 消息内容，仅用于本次请求。 */
  content: string
}

/** Chat 非流式测试请求。 */
export interface AiGatewayChatTestRequest {
  /** 本次测试使用的 Client API Key。 */
  clientApiKey: string
  /** 公开模型编码。 */
  model: string
  /** 消息列表。 */
  messages: AiGatewayChatMessage[]
  /** 采样温度。 */
  temperature?: number
  /** 最大输出 token 数。 */
  maxTokens?: number
}

/** Chat 非流式测试响应。 */
export interface AiGatewayChatTestResponse {
  /** Gateway HTTP 状态码。 */
  status: number
  /** 代理调用耗时。 */
  latencyMs: number
  /** 成功响应 JSON。 */
  data?: Record<string, unknown>
  /** 规范化错误码。 */
  errorCode?: string
  /** 用户可读错误消息。 */
  errorMessage?: string
}

/** 生成分页查询字符串。 */
function toQuery(params: object): string {
  const search = new URLSearchParams()
  Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '' && value !== 'ALL') {
      search.set(key, String(value))
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}

/** 获取 AI 管理台枚举选项。 */
export function getAiOptions(): Promise<AiOptions> {
  return get<AiOptions>('/api/v1/ai/options')
}

/** 查询 Gateway ready 状态。 */
export function getGatewayReady(): Promise<AiGatewayStatus> {
  return get<AiGatewayStatus>('/api/v1/ai/gateway/ready')
}

/** 查询 Gateway snapshot 状态。 */
export function getGatewaySnapshotStatus(): Promise<AiGatewayStatus> {
  return get<AiGatewayStatus>('/api/v1/ai/gateway/snapshot-status')
}

/** 查询 Gateway runtime 状态。 */
export function getGatewayRuntimeStatus(): Promise<AiGatewayStatus> {
  return get<AiGatewayStatus>('/api/v1/ai/gateway/runtime-status')
}

/** 发起 Chat Completions 非流式测试。 */
export function testGatewayChatCompletions(
  request: AiGatewayChatTestRequest,
): Promise<AiGatewayChatTestResponse> {
  return post<AiGatewayChatTestResponse>('/api/v1/ai/gateway/test-chat-completions', request)
}

/** 查询 Provider 列表。 */
export function listProviders(query: AiPageQuery): Promise<PageResult<AiProvider>> {
  return get<PageResult<AiProvider>>(`/api/v1/ai/providers${toQuery(query)}`)
}

/** 创建 Provider。 */
export function createProvider(body: Record<string, unknown>): Promise<AiProvider> {
  return post<AiProvider>('/api/v1/ai/providers', body)
}

/** 更新 Provider。 */
export function updateProvider(id: number, body: Record<string, unknown>): Promise<AiProvider> {
  return put<AiProvider>(`/api/v1/ai/providers/${id}`, body)
}

/** 切换 Provider 状态。 */
export function changeProviderStatus(id: number, action: 'enable' | 'disable'): Promise<AiProvider> {
  return post<AiProvider>(`/api/v1/ai/providers/${id}/${action}`)
}

/** 查询 Provider 下的连接列表。 */
export function listConnections(providerId: number, query: AiPageQuery): Promise<PageResult<AiConnection>> {
  return get<PageResult<AiConnection>>(`/api/v1/ai/providers/${providerId}/connections${toQuery(query)}`)
}

/** 创建上游连接。 */
export function createConnection(providerId: number, body: Record<string, unknown>): Promise<AiConnection> {
  return post<AiConnection>(`/api/v1/ai/providers/${providerId}/connections`, body)
}

/** 更新上游连接。 */
export function updateConnection(id: number, body: Record<string, unknown>): Promise<AiConnection> {
  return put<AiConnection>(`/api/v1/ai/connections/${id}`, body)
}

/** 切换上游连接状态。 */
export function changeConnectionStatus(id: number, action: 'enable' | 'disable'): Promise<AiConnection> {
  return post<AiConnection>(`/api/v1/ai/connections/${id}/${action}`)
}

/** 查询公开模型列表。 */
export function listModels(query: AiPageQuery): Promise<PageResult<AiPublicModel>> {
  return get<PageResult<AiPublicModel>>(`/api/v1/ai/models${toQuery(query)}`)
}

/** 创建公开模型。 */
export function createModel(body: Record<string, unknown>): Promise<AiPublicModel> {
  return post<AiPublicModel>('/api/v1/ai/models', body)
}

/** 更新公开模型。 */
export function updateModel(id: number, body: Record<string, unknown>): Promise<AiPublicModel> {
  return put<AiPublicModel>(`/api/v1/ai/models/${id}`, body)
}

/** 切换公开模型状态。 */
export function changeModelStatus(id: number, action: 'enable' | 'disable'): Promise<AiPublicModel> {
  return post<AiPublicModel>(`/api/v1/ai/models/${id}/${action}`)
}

/** 查询 Provider 下的凭据列表。 */
export function listCredentials(providerId: number, query: AiPageQuery): Promise<PageResult<AiCredential>> {
  return get<PageResult<AiCredential>>(`/api/v1/ai/providers/${providerId}/credentials${toQuery(query)}`)
}

/** 创建凭据。 */
export function createCredential(providerId: number, body: Record<string, unknown>): Promise<AiCredential> {
  return post<AiCredential>(`/api/v1/ai/providers/${providerId}/credentials`, body)
}

/** 更新凭据元数据。 */
export function updateCredential(id: number, body: Record<string, unknown>): Promise<AiCredential> {
  return put<AiCredential>(`/api/v1/ai/credentials/${id}`, body)
}

/** 轮换凭据明文。 */
export function rotateCredential(id: number, newApiKey: string): Promise<AiCredential> {
  return post<AiCredential>(`/api/v1/ai/credentials/${id}/rotate`, { newApiKey })
}

/** 切换凭据状态。 */
export function changeCredentialStatus(id: number, action: 'enable' | 'disable'): Promise<AiCredential> {
  return post<AiCredential>(`/api/v1/ai/credentials/${id}/${action}`)
}

/** 查询执行资源列表。 */
export function listResources(query: AiPageQuery): Promise<PageResult<AiExecutionResource>> {
  return get<PageResult<AiExecutionResource>>(`/api/v1/ai/resources${toQuery(query)}`)
}

/** 创建执行资源。 */
export function createResource(body: Record<string, unknown>): Promise<AiExecutionResource> {
  return post<AiExecutionResource>('/api/v1/ai/resources', body)
}

/** 更新执行资源元数据。 */
export function updateResource(id: number, body: Record<string, unknown>): Promise<AiExecutionResource> {
  return put<AiExecutionResource>(`/api/v1/ai/resources/${id}`, body)
}

/** 切换执行资源状态。 */
export function changeResourceStatus(id: number, action: 'enable' | 'disable' | 'drain'): Promise<AiExecutionResource> {
  return post<AiExecutionResource>(`/api/v1/ai/resources/${id}/${action}`)
}

/** 查询执行资源运行时策略。 */
export function getRuntimePolicy(resourceId: number): Promise<AiRuntimePolicy> {
  return get<AiRuntimePolicy>(`/api/v1/ai/resources/${resourceId}/runtime-policy`)
}

/** 更新执行资源运行时策略。 */
export function updateRuntimePolicy(resourceId: number, body: Record<string, unknown>): Promise<AiRuntimePolicy> {
  return put<AiRuntimePolicy>(`/api/v1/ai/resources/${resourceId}/runtime-policy`, body)
}

/** 查询资源池列表。 */
export function listResourcePools(query: AiPageQuery): Promise<PageResult<AiResourcePool>> {
  return get<PageResult<AiResourcePool>>(`/api/v1/ai/resource-pools${toQuery(query)}`)
}

/** 创建资源池。 */
export function createResourcePool(body: Record<string, unknown>): Promise<AiResourcePool> {
  return post<AiResourcePool>('/api/v1/ai/resource-pools', body)
}

/** 更新资源池。 */
export function updateResourcePool(id: number, body: Record<string, unknown>): Promise<AiResourcePool> {
  return put<AiResourcePool>(`/api/v1/ai/resource-pools/${id}`, body)
}

/** 切换资源池状态。 */
export function changeResourcePoolStatus(id: number, action: 'enable' | 'disable'): Promise<AiResourcePool> {
  return post<AiResourcePool>(`/api/v1/ai/resource-pools/${id}/${action}`)
}

/** 查询资源池成员列表。 */
export function listPoolMembers(poolId: number, query: AiPageQuery): Promise<PageResult<AiPoolMember>> {
  return get<PageResult<AiPoolMember>>(`/api/v1/ai/resource-pools/${poolId}/members${toQuery(query)}`)
}

/** 创建资源池成员。 */
export function createPoolMember(poolId: number, body: Record<string, unknown>): Promise<AiPoolMember> {
  return post<AiPoolMember>(`/api/v1/ai/resource-pools/${poolId}/members`, body)
}

/** 更新资源池成员。 */
export function updatePoolMember(id: number, body: Record<string, unknown>): Promise<AiPoolMember> {
  return put<AiPoolMember>(`/api/v1/ai/resource-pool-members/${id}`, body)
}

/** 切换资源池成员状态。 */
export function changePoolMemberStatus(id: number, action: 'enable' | 'disable'): Promise<AiPoolMember> {
  return post<AiPoolMember>(`/api/v1/ai/resource-pool-members/${id}/${action}`)
}

/** 查询模型绑定列表。 */
export function listModelBindings(query: Record<string, unknown>): Promise<PageResult<AiModelBinding>> {
  return get<PageResult<AiModelBinding>>(`/api/v1/ai/resource-model-bindings${toQuery(query)}`)
}

/** 创建模型绑定。 */
export function createModelBinding(body: Record<string, unknown>): Promise<AiModelBinding> {
  return post<AiModelBinding>('/api/v1/ai/resource-model-bindings', body)
}

/** 更新模型绑定。 */
export function updateModelBinding(id: number, body: Record<string, unknown>): Promise<AiModelBinding> {
  return put<AiModelBinding>(`/api/v1/ai/resource-model-bindings/${id}`, body)
}

/** 切换模型绑定状态。 */
export function changeModelBindingStatus(id: number, action: 'enable' | 'disable'): Promise<AiModelBinding> {
  return post<AiModelBinding>(`/api/v1/ai/resource-model-bindings/${id}/${action}`)
}

/** 查询路由策略列表。 */
export function listRoutePolicies(query: Record<string, unknown>): Promise<PageResult<AiRoutePolicy>> {
  return get<PageResult<AiRoutePolicy>>(`/api/v1/ai/route-policies${toQuery(query)}`)
}

/** 创建路由策略。 */
export function createRoutePolicy(body: Record<string, unknown>): Promise<AiRoutePolicy> {
  return post<AiRoutePolicy>('/api/v1/ai/route-policies', body)
}

/** 更新路由策略。 */
export function updateRoutePolicy(id: number, body: Record<string, unknown>): Promise<AiRoutePolicy> {
  return put<AiRoutePolicy>(`/api/v1/ai/route-policies/${id}`, body)
}

/** 切换路由策略状态。 */
export function changeRoutePolicyStatus(id: number, action: 'enable' | 'disable'): Promise<AiRoutePolicy> {
  return post<AiRoutePolicy>(`/api/v1/ai/route-policies/${id}/${action}`)
}

/** 查询路由目标列表。 */
export function listRouteTargets(policyId: number, query: AiPageQuery): Promise<PageResult<AiRouteTarget>> {
  return get<PageResult<AiRouteTarget>>(`/api/v1/ai/route-policies/${policyId}/targets${toQuery(query)}`)
}

/** 创建路由目标。 */
export function createRouteTarget(policyId: number, body: Record<string, unknown>): Promise<AiRouteTarget> {
  return post<AiRouteTarget>(`/api/v1/ai/route-policies/${policyId}/targets`, body)
}

/** 更新路由目标。 */
export function updateRouteTarget(id: number, body: Record<string, unknown>): Promise<AiRouteTarget> {
  return put<AiRouteTarget>(`/api/v1/ai/route-targets/${id}`, body)
}

/** 切换路由目标状态。 */
export function changeRouteTargetStatus(id: number, action: 'enable' | 'disable'): Promise<AiRouteTarget> {
  return post<AiRouteTarget>(`/api/v1/ai/route-targets/${id}/${action}`)
}

/** 预览静态路由。 */
export function previewRoute(body: Record<string, unknown>): Promise<Record<string, unknown>> {
  return post<Record<string, unknown>>('/api/v1/ai/routes/preview', body)
}

/** 查询访问组列表。 */
export function listAccessGroups(query: AiPageQuery): Promise<PageResult<AiAccessGroup>> {
  return get<PageResult<AiAccessGroup>>(`/api/v1/ai/access-groups${toQuery(query)}`)
}

/** 创建访问组。 */
export function createAccessGroup(body: Record<string, unknown>): Promise<AiAccessGroup> {
  return post<AiAccessGroup>('/api/v1/ai/access-groups', body)
}

/** 更新访问组。 */
export function updateAccessGroup(id: number, body: Record<string, unknown>): Promise<AiAccessGroup> {
  return put<AiAccessGroup>(`/api/v1/ai/access-groups/${id}`, body)
}

/** 切换访问组状态。 */
export function changeAccessGroupStatus(id: number, action: 'enable' | 'disable'): Promise<AiAccessGroup> {
  return post<AiAccessGroup>(`/api/v1/ai/access-groups/${id}/${action}`)
}

/** 查询访问组模型授权列表。 */
export function listModelGrants(accessGroupId: number, query: AiPageQuery): Promise<PageResult<AiModelGrant>> {
  return get<PageResult<AiModelGrant>>(`/api/v1/ai/access-groups/${accessGroupId}/model-grants${toQuery(query)}`)
}

/** 创建访问组模型授权。 */
export function createModelGrant(accessGroupId: number, body: Record<string, unknown>): Promise<AiModelGrant> {
  return post<AiModelGrant>(`/api/v1/ai/access-groups/${accessGroupId}/model-grants`, body)
}

/** 更新访问组模型授权。 */
export function updateModelGrant(id: number, body: Record<string, unknown>): Promise<AiModelGrant> {
  return put<AiModelGrant>(`/api/v1/ai/access-group-model-grants/${id}`, body)
}

/** 切换访问组模型授权状态。 */
export function changeModelGrantStatus(id: number, action: 'enable' | 'disable'): Promise<AiModelGrant> {
  return post<AiModelGrant>(`/api/v1/ai/access-group-model-grants/${id}/${action}`)
}

/** 查询 Client API Key 列表。 */
export function listClientApiKeys(query: AiPageQuery): Promise<PageResult<AiClientApiKey>> {
  return get<PageResult<AiClientApiKey>>(`/api/v1/ai/client-api-keys${toQuery(query)}`)
}

/** 创建 Client API Key。 */
export function createClientApiKey(body: Record<string, unknown>): Promise<AiClientApiKeySecret> {
  return post<AiClientApiKeySecret>('/api/v1/ai/client-api-keys', body)
}

/** 更新 Client API Key 元数据。 */
export function updateClientApiKey(id: number, body: Record<string, unknown>): Promise<AiClientApiKey> {
  return put<AiClientApiKey>(`/api/v1/ai/client-api-keys/${id}`, body)
}

/** 轮换 Client API Key。 */
export function rotateClientApiKey(id: number): Promise<AiClientApiKeySecret> {
  return post<AiClientApiKeySecret>(`/api/v1/ai/client-api-keys/${id}/rotate`)
}

/** 切换或撤销 Client API Key。 */
export function changeClientApiKeyStatus(
  id: number,
  action: 'enable' | 'disable' | 'revoke',
): Promise<AiClientApiKey> {
  return post<AiClientApiKey>(`/api/v1/ai/client-api-keys/${id}/${action}`)
}

/** 查询 Key 访问组绑定列表。 */
export function listKeyAccessGroups(keyId: number, query: AiPageQuery): Promise<PageResult<AiKeyAccessGroup>> {
  return get<PageResult<AiKeyAccessGroup>>(`/api/v1/ai/client-api-keys/${keyId}/access-groups${toQuery(query)}`)
}

/** 创建 Key 访问组绑定。 */
export function createKeyAccessGroup(keyId: number, body: Record<string, unknown>): Promise<AiKeyAccessGroup> {
  return post<AiKeyAccessGroup>(`/api/v1/ai/client-api-keys/${keyId}/access-groups`, body)
}

/** 更新 Key 访问组绑定。 */
export function updateKeyAccessGroup(id: number, body: Record<string, unknown>): Promise<AiKeyAccessGroup> {
  return put<AiKeyAccessGroup>(`/api/v1/ai/client-api-key-access-groups/${id}`, body)
}

/** 切换 Key 访问组绑定状态。 */
export function changeKeyAccessGroupStatus(id: number, action: 'enable' | 'disable'): Promise<AiKeyAccessGroup> {
  return post<AiKeyAccessGroup>(`/api/v1/ai/client-api-key-access-groups/${id}/${action}`)
}
