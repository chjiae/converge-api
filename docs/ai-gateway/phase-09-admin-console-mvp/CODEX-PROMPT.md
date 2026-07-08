# Codex Prompt｜阶段 09：AI Gateway 管理控制台一期

你要在当前仓库中实现：

```text
阶段 09：AI Gateway 管理控制台一期
```

阶段 08 与阶段 08.1 已完成。当前后端已经具备 Gateway Runtime Governance、Snapshot V4、tenant 增量快照刷新、Client API Key index 增量替换、OpenAI Chat Completions 转发等能力。

本阶段目标是实现管理控制台一期，而不是新增新的网关协议能力。

---

## 一、开始前必须阅读

必须先阅读：

```text
docs/ai-gateway/phase-08-runtime-governance/README.md
docs/ai-gateway/progress/phase-08-runtime-governance-result.md

docs/ai-gateway/phase-08-1-admission-snapshot-governance/README.md
docs/ai-gateway/progress/phase-08-1-admission-snapshot-governance-result.md

docs/ai-gateway/phase-09-admin-console-mvp/README.md
```

必须检查现有前端：

```text
前端目录结构
package.json
路由系统
登录态管理
租户上下文
权限控制
请求封装
错误处理
主题系统
布局组件
表格组件
表单组件
弹窗组件
构建命令
测试命令
```

不要新建一个脱离现有项目的前端工程。

---

## 二、本阶段总体目标

实现 AI Gateway 管理控制台一期，覆盖：

```text
1. AI 网关概览；
2. 上游目录：Provider、Connection、PublicModel；
3. 凭据与资源：Credential、ExecutionResource、RuntimePolicy；
4. 路由配置：ResourcePool、PoolMember、ResourceModelBinding、RoutePolicy、RouteTarget、RoutePreview；
5. 下游访问：AccessGroup、ModelGrant、ClientApiKey、KeyAccessGroup；
6. 运行状态：Snapshot Status、Runtime Status；
7. 在线测试：OpenAI Chat Completions 非流式测试。
```

---

## 三、必须遵守的边界

不得实现：

```text
Claude
Gemini
Responses
Embeddings
Realtime
WebSocket
订阅账号池
OAuth
Cookie
sticky session
计费
余额
充值
预扣
结算
账本
RPM / TPM
Client API Key 限流
真实 retry
请求后的 fallback
审计日志页面
平台超级管理员跨租户治理
复杂拓扑编辑器
```

不得破坏：

```text
阶段 08 Runtime Governance
阶段 08.1 tenant 增量刷新
Gateway 不访问 PostgreSQL
Gateway /v1/chat/completions 现有语义
Client API Key raw value 只展示一次
Credential 不可读取明文
```

---

## 四、后端小补丁

当前后端 CRUD 基本足够。本阶段只允许补管理台必要薄接口。

## 4.1 options 接口

新增：

```text
GET /api/v1/ai/options
```

返回：

```json
{
  "providerKinds": [
    { "name": "OPENAI", "label": "OpenAI", "description": "OpenAI 官方或等价供应商" }
  ],
  "protocolTypes": [],
  "catalogStatuses": [],
  "resourceStatuses": [],
  "credentialTypes": [],
  "canonicalOperations": [],
  "selectionPolicies": [],
  "routePolicyStatuses": [],
  "clientApiKeyStatuses": []
}
```

要求：

```text
1. 复用 Result<T>；
2. 仅 TENANT_OWNER / TENANT_ADMIN 可访问；
3. 不返回 Java 类名；
4. 不引入数据库表；
5. 单元或集成测试覆盖。
```

## 4.2 Gateway 管理状态代理

新增：

```text
GET /api/v1/ai/gateway/ready
GET /api/v1/ai/gateway/snapshot-status
GET /api/v1/ai/gateway/runtime-status
```

配置：

```text
AI_GATEWAY_ADMIN_BASE_URL
AI_GATEWAY_ADMIN_CONNECT_TIMEOUT_MS
AI_GATEWAY_ADMIN_READ_TIMEOUT_MS
AI_GATEWAY_ADMIN_ENABLED
```

要求：

```text
1. 复用控制面鉴权与租户权限；
2. 默认未配置时返回用户可理解错误；
3. 不把 Gateway 内部地址暴露给前端；
4. 只透传安全字段；
5. 不输出 Redis key、leaseId、resourceId、baseUrl、secret；
6. 测试覆盖未配置、正常返回、Gateway 不可达。
```

## 4.3 Chat Completions 测试代理

新增：

```text
POST /api/v1/ai/gateway/test-chat-completions
```

一期支持非流式即可。

请求 DTO：

```json
{
  "clientApiKey": "cvg_live_xxx_xxx",
  "model": "public-model-code",
  "messages": [
    { "role": "user", "content": "你好" }
  ],
  "temperature": 0.7,
  "maxTokens": 512
}
```

要求：

```text
1. clientApiKey 只用于本次请求，不落库；
2. 不记录 clientApiKey；
3. 不记录 Authorization；
4. 不记录完整 request body；
5. 转发到 Gateway /v1/chat/completions；
6. 统一包装响应和错误；
7. 超时可配置；
8. 测试覆盖 invalid_api_key、gateway_not_ready、正常响应代理。
```

如果实现 SSE 会导致前端复杂度明显上升，本阶段不要做流式测试。

---

## 五、前端实现要求

## 5.1 API client

新增或扩展 AI Gateway API client，统一封装：

```text
Provider API
Connection API
PublicModel API
Credential API
ExecutionResource API
RuntimePolicy API
ResourcePool API
PoolMember API
ResourceModelBinding API
RoutePolicy API
RouteTarget API
RoutePreview API
AccessGroup API
ModelGrant API
ClientApiKey API
KeyAccessGroup API
GatewayStatus API
GatewayTest API
Options API
```

要求：

```text
1. 复用现有 request 封装；
2. 复用现有登录态；
3. 复用 Result / PageResult 解包；
4. 错误提示统一；
5. 禁止在 console.log 打印 secret / raw key / Authorization。
```

## 5.2 路由与菜单

新增 AI Gateway 管理菜单。

建议结构：

```text
AI 网关
├─ 概览
├─ 上游配置
├─ 凭据与资源
├─ 路由配置
├─ 下游访问
├─ 运行状态
└─ 在线测试
```

页面多时可拆子页；现有前端菜单系统若不支持多级菜单，可以用页内 Tabs。

权限：

```text
TENANT_OWNER
TENANT_ADMIN
```

`TENANT_MEMBER` 不展示菜单，并且路由守卫拒绝访问。

## 5.3 页面组件

优先复用现有组件：

```text
Table
Form
Modal
Drawer
Tabs
Tag
Alert
Descriptions
Confirm
Empty
CopyButton
```

若缺少组件，先在现有设计系统内补小组件，不引入大型 UI 库替换。

---

## 六、页面清单

## 6.1 概览页

展示：

```text
Gateway Ready
Snapshot Status
Runtime Status
loadedTenantCount
loadedClientKeyCount
loadedRoutePlanCount
loadedRuntimePolicyCount
snapshotRefreshFailedCount
tenantRefreshPendingCount
runtimeStateUnavailableCount
最近全量对账时间
最近 tenant refresh 时间
```

快捷操作：

```text
新增 Provider
新增 Credential
新增 Resource
新增 Route Policy
新增 Client API Key
进入 Chat 测试
```

## 6.2 上游配置

包括：

```text
Provider
Connection
PublicModel
```

每个列表支持：

```text
分页
关键字
状态筛选
创建
编辑
详情
启用
停用
```

Connection 创建时选择 Provider；PublicModel 独立管理。

## 6.3 凭据与资源

包括：

```text
Credential
ExecutionResource
RuntimePolicy
```

Credential：

```text
创建时输入 API Key；
更新只改元数据；
轮换时输入新 API Key；
列表只展示 maskedPreview；
不允许查看明文。
```

ExecutionResource：

```text
创建时选择 Provider / Connection / Credential；
创建后绑定关系不可修改；
支持 ENABLED / DISABLED / DRAINING；
RuntimePolicy 可在详情或独立弹窗编辑。
```

## 6.4 路由配置

包括：

```text
ResourcePool
PoolMember
ResourceModelBinding
RoutePolicy
RouteTarget
RoutePreview
```

RoutePreview 必须提示：

```text
仅静态预览，不代表 runtime lease、熔断、并发限制后的真实候选。
```

## 6.5 下游访问

包括：

```text
AccessGroup
ModelGrant
ClientApiKey
KeyAccessGroup
```

Client API Key 创建和轮换：

```text
1. 成功后弹窗展示 raw key；
2. 提供复制按钮；
3. 明确提示只展示一次；
4. 关闭弹窗前确认；
5. 不写入 localStorage / sessionStorage。
```

撤销 Key：

```text
高风险操作；
必须二次确认；
确认文案说明撤销后不可恢复。
```

## 6.6 运行状态

包括：

```text
Snapshot Status
Runtime Status
```

不要展示敏感字段。

时间字段统一格式化为本地时间，并保留原始 epoch millis 可复制或查看。

## 6.7 在线测试

Chat Completions 非流式测试：

```text
输入 Client API Key
选择或输入 model
编辑 messages
temperature
maxTokens
提交
展示响应 JSON、耗时、HTTP 状态、错误码
```

安全：

```text
Client API Key password 输入；
不持久化；
不打印；
不自动保存历史。
```

---

## 七、测试要求

后端：

```bash
mvn -pl converge-web-service -am test
mvn -pl converge-web-service,converge-gateway -am test-compile
mvn test
```

前端命令以实际 package.json 为准，至少执行：

```bash
npm run typecheck
npm run lint
npm run build
```

如果项目使用 pnpm、yarn、bun，以当前仓库为准，不要擅自切换包管理器。

E2E 或 Playwright 至少覆盖：

```text
登录后看到 AI Gateway 菜单；
Provider 列表加载；
Credential 创建弹窗不泄露明文；
Client API Key 创建后一次性展示 raw key；
Snapshot Status 页面加载；
Chat Test 页面能提交并处理错误响应。
```

---

## 八、实现顺序

```text
1. 审查现有前端架构；
2. 补 options 接口；
3. 补 Gateway 状态代理；
4. 补 Chat Test 代理；
5. 实现 AI Gateway API client；
6. 接入菜单与路由；
7. 实现概览页；
8. 实现上游配置；
9. 实现凭据与资源；
10. 实现路由配置；
11. 实现下游访问；
12. 实现运行状态；
13. 实现在线测试；
14. 统一空状态、错误提示、危险确认、复制交互；
15. 运行测试和构建；
16. 填写结果文档；
17. 中文提交。
```

---

## 九、结果文档

完成后填写：

```text
docs/ai-gateway/progress/phase-09-admin-console-mvp-result.md
```

必须说明：

```text
1. 后端新增了哪些薄接口；
2. 前端新增了哪些页面；
3. 哪些页面复用现有后端接口；
4. 是否存在尚未补齐的后端能力；
5. Client API Key raw value 是否只展示一次；
6. Credential 明文是否不可读取；
7. Gateway 内部状态是否通过控制面代理访问；
8. Chat Test 是否非流式；
9. 运行了哪些测试；
10. 阶段边界是否越界。
```

---

## 十、提交示例

```bash
git add backend frontend docs
git diff --cached --check
git commit -m "feat(控制台): 实现 AI 网关管理台一期"
```

如果只提交文档：

```bash
git add \
  docs/ai-gateway/phase-09-admin-console-mvp/README.md \
  docs/ai-gateway/phase-09-admin-console-mvp/CODEX-PROMPT.md \
  docs/ai-gateway/progress/phase-09-admin-console-mvp-result.md

git diff --cached --check
git commit -m "docs(控制台): 新增 AI 网关管理台一期阶段计划"
```
