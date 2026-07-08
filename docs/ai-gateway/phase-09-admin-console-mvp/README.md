# 阶段 09｜AI Gateway 管理控制台一期

## 1. 阶段背景

阶段 08 与阶段 08.1 已完成：

```text
阶段 08：
- Snapshot V4
- 执行资源 Runtime Policy
- Redis lease / health
- ACQUIRE_LEASE / RENEW_LEASE / COMPLETE_LEASE Lua
- Chat Completions 请求前动态候选改选
- /internal/runtime-status

阶段 08.1：
- Pub/Sub tenant 定向刷新
- revision skip
- tenant single-flight / debounce
- Client API Key index tenant 增量替换
- /internal/snapshot-status 指标增强
```

当前后端控制面已经具备管理控制台一期所需的大部分 API：

```text
Provider / Connection / PublicModel
Credential / ExecutionResource / RuntimePolicy
ResourcePool / PoolMember
ResourceModelBinding
RoutePolicy / RouteTarget / RoutePreview
AccessGroup / ModelGrant
ClientApiKey / KeyAccessGroup
```

Gateway 数据面已经具备：

```text
GET  /v1/models
POST /v1/chat/completions
GET  /internal/snapshot-status
GET  /internal/runtime-status
```

本阶段目标是在现有前端项目上完成 AI Gateway 管理控制台一期，让租户管理员可以通过页面完成从上游接入、模型配置、资源路由、下游 Key 授权到在线测试的基础闭环。

---

## 2. 阶段目标

本阶段完成一个可用的一期管理控制台：

```text
1. 能管理 AI Provider、上游连接、公开模型；
2. 能管理上游凭据，但不读取、不展示明文；
3. 能管理执行资源与运行时治理策略；
4. 能管理资源池、资源池成员、模型绑定、路由策略、路由目标；
5. 能管理访问组、模型授权、Client API Key 与 Key-AccessGroup 绑定；
6. 能查看 Gateway Snapshot 状态和 Runtime 状态；
7. 能在管理台发起 OpenAI Chat Completions 测试；
8. 能辅助租户管理员完成最小可运行配置闭环。
```

最小闭环：

```text
创建 Provider
→ 创建 Connection
→ 创建 Credential
→ 创建 ExecutionResource
→ 配置 RuntimePolicy
→ 创建 PublicModel
→ 创建 ResourcePool
→ 添加 PoolMember
→ 创建 ResourceModelBinding
→ 创建 RoutePolicy
→ 创建 RouteTarget
→ 启用相关对象
→ 创建 AccessGroup
→ 创建 ModelGrant
→ 创建 ClientApiKey
→ 绑定 Key 到 AccessGroup
→ 查看 Snapshot / Runtime 状态
→ 调用 Chat Completions 测试
```

---

## 3. 设计原则

### 3.1 前端只做管理台，不实现新网关协议

本阶段不新增 Claude、Gemini、Responses、Embeddings、Realtime、WebSocket、订阅账号池、OAuth、Cookie、计费、RPM/TPM。

前端只管理当前后端已经具备的能力。

### 3.2 不绕过控制面权限

管理控制台调用控制面 API：

```text
/api/v1/ai/**
```

不建议前端直接访问 Gateway `/internal/*`，因为 Gateway 内部接口当前只适合内部网络或反向代理保护环境。

本阶段需要补一个很薄的控制面代理层：

```text
GET  /api/v1/ai/gateway/snapshot-status
GET  /api/v1/ai/gateway/runtime-status
POST /api/v1/ai/gateway/test-chat-completions
GET  /api/v1/ai/options
```

这些接口必须复用控制面登录态、租户上下文和角色权限。

### 3.3 不泄露敏感信息

禁止在页面、日志、状态、错误弹窗中展示：

```text
上游 API Key 明文
Credential encryptedSecret
nonce
HMAC
secretFingerprint
secretReference
runtimeSecret
Authorization
Cookie
完整 Client API Key raw secret
Gateway Redis key
resource runtime leaseId
```

Client API Key raw value 只允许在创建和轮换响应中一次性展示，并必须有明确提示：

```text
请立即复制保存。关闭弹窗后无法再次查看。
```

### 3.4 先适配现有前端架构

Codex 必须先检查现有前端：

```text
前端目录
路由系统
登录态管理
请求封装
权限控制
主题系统
表格组件
表单组件
弹窗组件
布局组件
现有设计风格
```

不得另起一个完全独立的前端工程。

如果当前前端已有 React，则继续使用现有 React 技术栈；如果仓库实际已切换为其他框架，以当前仓库为准。

### 3.5 页面体验避免“后台模板味”和“AI 味”

一期页面要清晰、专业、克制：

```text
信息密度适中
层级明确
状态标签统一
危险操作有确认
密钥只一次性展示
空状态有引导
错误提示能指导用户下一步
亮暗主题兼容
多视口可用
```

不要做花哨动画，不要堆砌渐变卡片，不要为了视觉效果牺牲可用性。

---

## 4. 后端小补丁范围

当前大部分 CRUD 已存在。本阶段只允许补少量“管理台支撑接口”。

## 4.1 Gateway 状态代理

新增控制面接口：

```text
GET /api/v1/ai/gateway/snapshot-status
GET /api/v1/ai/gateway/runtime-status
GET /api/v1/ai/gateway/ready
```

用途：

```text
控制台通过 web-service 查看 Gateway 内部安全状态；
web-service 负责鉴权、租户角色判断、超时、错误包装和脱敏；
前端不直连 Gateway /internal/*。
```

配置项建议：

```text
AI_GATEWAY_ADMIN_BASE_URL
AI_GATEWAY_ADMIN_CONNECT_TIMEOUT_MS
AI_GATEWAY_ADMIN_READ_TIMEOUT_MS
AI_GATEWAY_ADMIN_ENABLED
```

要求：

```text
1. 默认开发环境可配置本地 Gateway；
2. 生产环境未配置时页面显示“未配置 Gateway 管理代理”；
3. 代理响应只透传安全字段；
4. 不输出 Gateway 内部 URL；
5. 不输出 Redis key、leaseId、resourceId、baseUrl、secret。
```

## 4.2 Chat Completions 测试代理

新增控制面接口：

```text
POST /api/v1/ai/gateway/test-chat-completions
```

建议请求：

```json
{
  "clientApiKey": "cvg_live_xxx_xxx",
  "model": "public-model-code",
  "messages": [
    { "role": "user", "content": "你好" }
  ],
  "stream": false,
  "temperature": 0.7,
  "maxTokens": 512
}
```

一期可以先支持非流式测试；如果现有前端已有 SSE 封装，可以支持流式测试。

要求：

```text
1. clientApiKey 仅用于本次测试，不落库；
2. 控制面不记录 Authorization、clientApiKey、messages 全量内容；
3. 控制面向 Gateway /v1/chat/completions 转发；
4. 支持超时；
5. 错误提示要区分：
   - gateway_not_ready
   - invalid_api_key
   - model_access_denied
   - model_not_found
   - no_runtime_eligible_resource
   - runtime_state_unavailable
   - upstream_error
6. 不实现计费、不实现真实用户聊天历史。
```

## 4.3 枚举 options 接口

新增控制面接口：

```text
GET /api/v1/ai/options
```

返回前端表单需要的枚举：

```text
providerKinds
protocolTypes
catalogStatuses
resourceStatuses
credentialTypes
canonicalOperations
selectionPolicies
routePolicyStatuses
clientApiKeyStatuses
```

要求：

```text
1. 只返回 name、label、description；
2. 不返回 Java 内部类名；
3. 后续新增枚举时前端不需要硬编码。
```

---

## 5. 前端页面范围

## 5.1 导航结构

建议一级菜单：

```text
AI 网关
├─ 概览
├─ 上游目录
│  ├─ 供应商
│  ├─ 上游连接
│  └─ 公开模型
├─ 凭据与资源
│  ├─ 上游凭据
│  ├─ 执行资源
│  └─ 运行时策略
├─ 路由配置
│  ├─ 资源池
│  ├─ 模型绑定
│  ├─ 路由策略
│  └─ 路由预览
├─ 下游访问
│  ├─ 访问组
│  ├─ 模型授权
│  ├─ Client API Key
│  └─ Key 绑定
├─ 运行状态
│  ├─ 快照状态
│  └─ 资源治理状态
└─ 在线测试
   └─ Chat Completions
```

如果现有布局不适合多级菜单，可以合并为：

```text
AI 网关概览
上游配置
资源与路由
访问控制
运行状态
在线测试
```

## 5.2 概览页

展示：

```text
Gateway ready 状态
Snapshot 状态
Runtime 状态
已加载 tenant 数
Client Key 数
Route Plan 数
Runtime Policy 数
最近全量对账时间
最近 tenant refresh 时间
刷新失败次数
运行时 Redis 可用性
```

提供快捷入口：

```text
新增 Provider
新增 Credential
新增 Resource
新增 Route Policy
新增 Client Key
打开 Chat 测试
```

## 5.3 上游目录页面

### Provider

字段：

```text
code
displayName
providerKind
description
adminStatus
createdAt
updatedAt
```

操作：

```text
创建
编辑
启用
停用
详情
```

### Connection

字段：

```text
provider
code
displayName
protocolType
baseUrl
adminStatus
description
createdAt
updatedAt
```

操作：

```text
创建
编辑
启用
停用
详情
```

注意：

```text
baseUrl 不是 secret，可以展示；
但不要在错误日志里输出完整上游敏感路径。
```

### Public Model

字段：

```text
code
displayName
modelFamily / capability
adminStatus
description
createdAt
updatedAt
```

操作：

```text
创建
编辑
启用
停用
详情
```

---

## 5.4 凭据与资源页面

### Credential

字段：

```text
provider
code
displayName
credentialType
maskedPreview
secretVersion
rotatedAt
adminStatus
description
createdAt
updatedAt
```

操作：

```text
创建
编辑元数据
轮换
启用
停用
详情
```

交互要求：

```text
创建 / 轮换时输入明文 API Key；
提交后页面不得保存该输入；
响应中不显示上游明文；
不提供“查看密钥”按钮。
```

### Execution Resource

字段：

```text
provider
connection
credential
resourceType
code
displayName
adminStatus
description
createdAt
updatedAt
```

操作：

```text
创建
编辑元数据
启用
停用
排空
查看运行时策略
```

资源绑定关系创建后不可修改，页面上要明确提示。

### Runtime Policy

字段：

```text
maxConcurrentRequests
consecutiveFailureThreshold
failureResetAfterMs
failureCooldownMs
rateLimitCooldownMs
policyVersion
```

操作：

```text
查看
编辑
保存
```

提示：

```text
maxConcurrentRequests = 0 表示不限制并发。
policyVersion 由后端维护，前端只读。
```

---

## 5.5 路由配置页面

### Resource Pool

字段：

```text
code
displayName
selectionPolicy
adminStatus
description
```

操作：

```text
创建
编辑
启用
停用
管理成员
```

### Pool Member

字段：

```text
resource
priority
weight
adminStatus
```

操作：

```text
添加资源
编辑 priority / weight
启用
停用
```

### Resource Model Binding

字段：

```text
executionResource
publicModel
canonicalOperation
upstreamModelName
adminStatus
```

操作：

```text
创建
编辑 upstreamModelName / 状态
启用
停用
```

### Route Policy

字段：

```text
publicModel
canonicalOperation
selectionPolicy
adminStatus
```

操作：

```text
创建
编辑
启用
停用
管理 targets
```

### Route Target

字段：

```text
resourcePool
priority
weight
adminStatus
```

操作：

```text
添加目标池
编辑 priority / weight
启用
停用
```

### Route Preview

输入：

```text
publicModel
canonicalOperation
seed / requestId
```

输出：

```text
候选资源池
候选资源
priority
weight
upstreamModelName
```

明确提示：

```text
路由预览只展示静态配置选择，不代表动态健康、并发租约和熔断后的最终执行资源。
```

---

## 5.6 下游访问页面

### Access Group

字段：

```text
code
displayName
adminStatus
description
```

操作：

```text
创建
编辑
启用
停用
管理模型授权
```

### Model Grant

字段：

```text
accessGroup
publicModel
canonicalOperation
adminStatus
```

操作：

```text
创建
编辑
启用
停用
```

### Client API Key

字段：

```text
displayName
keyId / maskedPreview
adminStatus
expiresAt
keyVersion
createdAt
updatedAt
```

操作：

```text
创建
编辑元数据
轮换
启用
停用
撤销
管理访问组绑定
```

创建和轮换弹窗：

```text
1. 提交后展示 raw key；
2. 一键复制；
3. 显示“只展示一次”警告；
4. 关闭前二次确认；
5. 关闭后不能再查看 raw key。
```

### Key-AccessGroup Binding

字段：

```text
clientApiKey
accessGroup
adminStatus
```

操作：

```text
绑定
编辑
启用
停用
```

---

## 5.7 运行状态页面

### Snapshot Status

通过控制面代理读取：

```text
GET /api/v1/ai/gateway/snapshot-status
```

展示：

```text
status
indexTenantCount
tenantIndexCount
loadedTenantCount
loadedClientKeyCount
loadedAccessGroupCount
loadedGrantCount
loadedRoutePlanCount
loadedRuntimePolicyCount
snapshotRefreshTotalCount
snapshotRefreshSkippedCount
snapshotRefreshFailedCount
tenantRefreshInFlightCount
tenantRefreshPendingCount
lastTenantRefreshEpochMillis
lastFullReconcileEpochMillis
lastRefreshDurationMs
maxRefreshDurationMs
estimatedSnapshotPayloadBytes
latestErrorCategory
```

### Runtime Status

通过控制面代理读取：

```text
GET /api/v1/ai/gateway/runtime-status
```

展示：

```text
redisAvailable
activeLocalLeases
leaseAcquireGrantedCount
leaseAcquireRejectedCount
renewFailureCount
runtimeStateUnavailableCount
```

不得展示：

```text
resourceId
leaseId
Redis key
secret
baseUrl
```

---

## 5.8 在线测试页面

### Chat Completions

输入：

```text
Client API Key
model
messages
temperature
maxTokens
stream
```

一期建议默认非流式：

```text
stream = false
```

输出：

```text
响应 JSON
耗时
HTTP 状态
错误码
错误消息
```

安全要求：

```text
1. Client API Key 输入框使用 password 类型；
2. 不持久化测试 Key；
3. 不把测试消息写入本地存储；
4. 不在控制台打印 Authorization；
5. 错误提示不包含上游 URL 或 secret。
```

---

## 6. API 接入约定

### 6.1 前端请求封装

所有控制面请求统一走现有请求封装：

```text
baseURL: 当前 web-service API base
认证：复用现有登录态
错误：统一 Result / BusinessException 处理
分页：统一 PageResult
```

不得在页面散落 fetch / axios 原始调用。

### 6.2 状态操作

启用、停用、排空、撤销、轮换等操作必须：

```text
1. 二次确认；
2. 成功后刷新列表；
3. 乐观更新只允许用于低风险状态；
4. 高风险操作必须等待后端成功。
```

高风险操作：

```text
Credential rotate
Client API Key rotate
Client API Key revoke
Execution Resource drain
Route Policy enable
```

### 6.3 枚举

前端优先调用：

```text
GET /api/v1/ai/options
```

不要硬编码枚举 label。若 options 接口尚未实现，本阶段先补后端。

---

## 7. 权限边界

本阶段页面只面向：

```text
TENANT_OWNER
TENANT_ADMIN
```

`TENANT_MEMBER` 不展示 AI 网关管理菜单。

如果当前前端已有菜单权限系统，应接入现有权限；如果没有，至少要在路由守卫中阻止无权限用户访问。

---

## 8. 明确不做

```text
不做平台超级管理员跨租户治理界面
不做用户停用 / 租户停用实时失效
不做余额、充值、预扣、结算、账本
不做 RPM / TPM
不做 Client API Key 限流
不做上游真实健康检测页面
不做真实 retry / fallback 配置
不做 Claude / Gemini / Responses / Embeddings / Realtime
不做订阅账号池、OAuth、Cookie、sticky session
不做复杂可视化拓扑编辑器
不做拖拽式路由编排
不做审计日志页面
不做多环境发布管理
```

---

## 9. 验收标准

### 9.1 功能验收

```text
Provider / Connection / Model 可创建、查询、编辑、启停；
Credential 可创建、查询、编辑、轮换、启停，且不展示明文；
Execution Resource 可创建、查询、编辑、启停、排空；
Runtime Policy 可查询和更新；
Resource Pool / Member 可管理；
Resource Model Binding 可管理；
Route Policy / Target 可管理；
Route Preview 可使用；
Access Group / Model Grant 可管理；
Client API Key 可创建、轮换、启停、撤销；
Key-AccessGroup Binding 可管理；
Snapshot Status 可查看；
Runtime Status 可查看；
Chat Completions 非流式测试可用。
```

### 9.2 安全验收

```text
页面不展示上游 API Key 明文；
页面不展示 Credential 密文、nonce、HMAC；
页面不展示 runtimeSecret；
页面不展示 Gateway Redis key；
页面不展示 leaseId；
Client API Key raw value 只在创建/轮换后一次性展示；
状态接口通过控制面权限访问；
TENANT_MEMBER 无法进入管理台。
```

### 9.3 回归验收

```text
backend mvn test 通过；
frontend typecheck 通过；
frontend lint 通过；
frontend build 通过；
Playwright / e2e 至少覆盖一条最小配置闭环；
现有登录、租户、权限页面不回归。
```

---

## 10. 建议实施顺序

```text
1. 审查现有前端架构、路由、权限、请求封装和 UI 组件；
2. 补后端 options 与 Gateway 状态/测试代理；
3. 建立 AI Gateway API client；
4. 建立 AI Gateway 菜单和路由；
5. 做概览页；
6. 做上游目录页面；
7. 做凭据与资源页面；
8. 做路由配置页面；
9. 做下游访问页面；
10. 做运行状态页面；
11. 做 Chat Completions 测试页；
12. 完成一次最小配置闭环测试；
13. 修复交互、错误提示、空状态、权限和响应式问题；
14. 填写实施结果；
15. 中文 Git 提交。
```
