# Codex 执行提示词｜阶段 07

请在 Converge API 仓库根目录，完整实施：

```text
docs/ai-gateway/phase-07-openai-direct-forwarding/README.md
```

## 必读文件

```text
AGENTS.md
docs/ai-gateway/phase-01-gateway-runtime/
docs/ai-gateway/phase-02-control-plane-catalog/
docs/ai-gateway/phase-03-credential-resource/
docs/ai-gateway/phase-04-gateway-snapshot-sync/
docs/ai-gateway/phase-05-static-routing/
docs/ai-gateway/phase-06-client-access/
docs/ai-gateway/progress/phase-01-*.md
docs/ai-gateway/progress/phase-02-*.md
docs/ai-gateway/progress/phase-03-*.md
docs/ai-gateway/progress/phase-04-*.md
docs/ai-gateway/progress/phase-05-*.md
docs/ai-gateway/progress/phase-06-client-access-result.md
docs/ai-gateway/phase-07-openai-direct-forwarding/README.md
docs/ai-gateway/phase-07-openai-direct-forwarding/OPENAI-CHAT-COMPLETIONS-PROTOCOL.md
docs/ai-gateway/phase-07-openai-direct-forwarding/GATEWAY-EXECUTION-ARCHITECTURE.md
docs/ai-gateway/phase-07-openai-direct-forwarding/CAPABILITY-COVERAGE-MATRIX.md
docs/ai-gateway/phase-07-openai-direct-forwarding/SOURCE-REVIEW-2026-07-07.md
docs/ai-gateway/phase-07-openai-direct-forwarding/ACCEPTANCE-CHECKLIST.md
```

## 当前前置状态

当前必须以仓库最新代码为准，不以旧文档猜测。

预期已存在：

```text
9479db6 feat(AI网关): 实现下游访问授权 V3
```

当前 Gateway 已具备：

```text
GatewaySnapshotRuntime
GatewayClientPrincipal
Gateway Client API Key 本地认证
V1 / V2 / V3 Snapshot 兼容
StaticRoutePlan
GET /v1/models
GatewayExecutionResourceSnapshot.baseUrl
GatewayExecutionResourceSnapshot.secretEnvelope 的本地解封装结果
```

当前未具备：

```text
上游 HTTP Client
POST /v1/chat/completions
SSE
retry / fallback
计费
动态调度
React AI Console
```

先审查真实代码、Maven 模块、GatewayConfig、GatewayRuntime、GatewayRouterFactory、SnapshotRuntime、routing-core、现有测试与前置阶段结果。

先简短输出：

```text
当前代码发现
预计修改文件
关键风险
```

随后立即连续实施，不等待确认。

---

## 执行权限

你已获得完成本阶段所需的普通工程操作权限。

可以：

```text
读取、修改、创建仓库文件
新增 Gateway Java 类、测试、配置模板和阶段结果
新增纯 Java routing-core 类和测试
修改 GatewayRuntime / Router / Config
下载 Maven 依赖
运行 Maven、Testcontainers、Docker 测试
查看日志
连续修复失败
Git add / commit
```

不要因为普通取舍、测试红灯、依赖下载、Testcontainers、命名适配或构建问题停止询问。

只有真实无法安全继续的外部阻塞，才允许停止并在最终报告写明。

---

## A. 阶段目标

实现：

```text
POST /v1/chat/completions
  → Client API Key 本地认证
  → CHAT_COMPLETIONS 精确授权
  → StaticRoutePlan 静态选择
  → DIRECT_API + OPENAI_COMPATIBLE 资源解析
  → 上游 Bearer API Key 注入
  → OpenAI Compatible 非流式 JSON
  → OpenAI Compatible SSE
```

始终遵守：

```text
Gateway 不直连 PostgreSQL
Gateway 单请求不查询 Redis
Gateway 不读取 AI_CREDENTIAL_* 数据库加密密钥
不保存 Client raw key
不记录上游 runtime secret
```

---

## B. 真实请求静态选择

1. 在 `converge-routing-core` 新增：

```text
StaticRouteRequestSelector
StaticRouteSelection
```

2. 不得将 `StaticRoutePreviewSelector` 直接用于真实请求。

3. 新 selector 必须复用既有 priority / weight 语义：

```text
最高 RouteTarget priority
→ 同层 weight 选择 Pool
→ 最高 PoolMember priority
→ 同层 weight 选择 Resource
```

4. selector 必须保持纯 Java：

```text
无 Vert.x
无 Spring
无 Redis
无 MyBatis
无 JDBC
无 HTTP
无日志
```

5. selection seed 不得包含：

```text
raw Client API Key
Client secret
upstream secret
Authorization
Cookie
```

6. 增加 selector 单元测试。

---

## C. Gateway 执行目标解析

在当前 `GatewaySnapshotRuntime` 增加仅内存执行目标解析能力，例如：

```text
resolveOpenAiChatExecution(
    GatewayClientPrincipal principal,
    String publicModelCode,
    String requestId
)
```

它必须：

```text
校验 Gateway ready
校验 principal 对 (publicModelCode, CHAT_COMPLETIONS) 的 exact grant
查询当前 tenant StaticRoutePlan
调用 StaticRouteRequestSelector
解析 executionResourceId 对应 resource
校验 DIRECT_API + OPENAI_COMPATIBLE + ENABLED
获取 runtime secret
返回内部执行目标
```

约束：

```text
执行目标不能是 Java record
执行目标不得自动打印 secret
不得在 RoutingContext 存 raw Client Key 或 runtime secret
不得新增每请求 Redis / DB 查询
```

先审查现有 Credential 与 ExecutionResource 的真实认证语义。

阶段 07 只支持已确认可安全映射为：

```http
Authorization: Bearer <runtime-secret>
```

的 Direct API 资源。

如果现有快照缺少安全判断认证模式所需的固定元数据，必须 fail closed。不得猜测 Header、Query 或 Cookie 鉴权方式；不得为了方便无理由新建 Snapshot V4。

---

## D. Gateway 配置与共享 HttpClient

在 `GatewayConfig` 中增加：

```text
GATEWAY_UPSTREAM_CONNECT_TIMEOUT_MS
GATEWAY_UPSTREAM_IDLE_TIMEOUT_MS
GATEWAY_UPSTREAM_POOL_MAX_SIZE
GATEWAY_OPENAI_MAX_REQUEST_BYTES
GATEWAY_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES
GATEWAY_OPENAI_MAX_ERROR_RESPONSE_BYTES
GATEWAY_OPENAI_MAX_SSE_EVENT_BYTES
```

要求：

```text
延续既有系统属性 / 环境变量加载方式
启动期严格校验
创建单例共享 Vert.x HttpClient
禁止自动 redirect
禁止每请求创建 HttpClient
```

禁止：

```text
RestTemplate
WebClient
OkHttp
JDK blocking HttpClient
vertx-http-proxy
```

baseUrl 只能来自 Gateway Snapshot，不接受客户端 URL。

---

## E. 路由、认证与数据面错误

在 `GatewayRouterFactory` 注册：

```text
POST /v1/chat/completions
```

不得破坏：

```text
GET /v1/models
/internal/*
V1 / V2 / V3 snapshot 兼容
```

认证逻辑应提取为可复用组件，避免 `/v1/models` 和 chat endpoint 使用不同 Bearer parser。

数据面错误继续采用 OpenAI Compatible error envelope。

Gateway 未 ready、snapshot stale 或 draining 时必须返回 503，不得尝试上游请求。

---

## F. Request 处理

1. 仅接受 `application/json`，允许 charset 参数。
2. 限制 request body 字节数。
3. 仅最小校验：

```text
JSON object
model 非空 string
stream 缺省或 boolean
```

4. 不完整校验 OpenAI 其他字段。
5. 使用 PublicModel + `CHAT_COMPLETIONS` 授权。
6. 无授权返回 403。
7. 无有效静态路线返回 404。
8. request body 顶层 `model` 改为 selected resource 的 `upstreamModelName`。
9. 不转发 Client Authorization、Cookie、Host、Hop-by-hop Header 或客户端自定义上游鉴权 Header。
10. 上游 Header 严格构造：

```text
Authorization: Bearer <runtime secret>
Content-Type: application/json
Accept
User-Agent
X-Request-Id
```

---

## G. 非流式处理

当 `stream != true`：

```text
POST <baseUrl>/chat/completions
→ 受限读取 2xx JSON object
→ 顶层 response.model 改为 publicModelCode
→ 返回下游
```

必须：

```text
限制 response body
限制 upstream error body
拒绝非 JSON 2xx
拒绝非 object JSON
不泄露 upstreamModelName
不回显 upstream error body
```

---

## H. SSE 处理

当 `stream == true`：

1. 上游成功响应必须是 `text/event-stream`。
2. 下游 Header：

```text
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-cache
X-Accel-Buffering: no
```

3. 实现增量 `GatewayOpenAiSseModelRewriter`：

```text
按 SSE event delimiter 分帧
支持 \n\n 与 \r\n\r\n
支持 event 与 UTF-8 字符跨网络 chunk
data: JSON → 重写顶层 model
data: [DONE] → 原样输出
限制单 event 字节数
```

4. 必须实现背压：

```text
downstream writeQueueFull → pause upstream
downstream drainHandler → resume upstream
```

5. 客户端断开时 best-effort `upstreamRequest.cancel()`。
6. Headers 已发送后上游失败，只结束流，不得再写 JSON error。
7. 不解析 token、不写 usage、不计费。

---

## I. 安全与日志

绝不写入：

```text
raw Client API Key
Authorization
x-api-key
Cookie
request messages
request body
response body
runtime secret
baseUrl
upstreamModelName
Credential
secret envelope
HMAC
Redis key
```

允许安全字段：

```text
requestId
tenantId
clientApiKeyId
publicModelCode
operation
stream
routePolicyId
snapshotRevision
status
latency
outcome category
```

---

## J. 优雅关闭

调整 `GatewayRuntime.close()`：

```text
executionRuntime.beginDrain()
→ HTTP server graceful shutdown
→ shared HttpClient close
→ snapshot runtime close
→ Vert.x close
```

关闭期间的新 `/v1/chat/completions` 返回：

```text
503 gateway_shutting_down
```

不得提前关闭 Vert.x 造成已有 SSE 无序中断。

---

## K. 必须覆盖的测试

### Routing Core

```text
priority 选择
weight 选择
seed 稳定
无动态状态依赖
```

### Gateway 非流式

```text
有效 Key + grant + route → 成功
request.model 改为 upstream model
response.model 改回 public model
上游 Authorization 注入但不泄露
无 grant → 403，且无上游请求
无 route → 404，且无上游请求
invalid JSON → 400
invalid stream type → 400
unsupported content type → 415
body too large → 413
invalid / expired / disabled / revoked key → 401
snapshot not ready / stale → 503
DNS / connect / TLS / timeout → 安全 502 / 504
上游 401 / 403 → 502 upstream_authentication_failed
上游 429 → 429 upstream_rate_limited
上游错误 body 不回显
```

### Gateway SSE

```text
SSE 顶层 model 重写
[DONE] 透传
event 多 chunk
UTF-8 多 chunk
SSE event 超限
下游背压 pause / resume
client disconnect → upstream cancel
headers 后错误不写 JSON
```

### 回归

```text
V1 / V2 / V3 snapshot 回归
GET /v1/models 回归
Gateway 依赖边界扫描
secret / authorization leak scan
backend root mvn test
git diff --check
```

测试必须使用本地 mock upstream 或 Testcontainers，不得调用真实外部 Provider。

---

## L. 严格禁止

```text
不实现 retry、fallback、failover、circuit breaker
不实现健康检查、429 cooldown、并发、RPM、TPM
不实现 billing、余额、账本、价格、倍率、预扣、结算
不实现 OAuth、Refresh Token、Cookie、账号池、sticky session
不实现 Responses、Embeddings、Claude、Gemini、Realtime、WebSocket
不实现前端
不增加客户端可控上游 URL 或 Header 透传
不让 Gateway 访问数据库
不让 Gateway 单请求查询 Redis
不暴露 upstream model / URL / Credential
```

---

## M. 最终交付

完成后：

1. 填写：

```text
docs/ai-gateway/phase-07-openai-direct-forwarding/ACCEPTANCE-CHECKLIST.md
docs/ai-gateway/progress/phase-07-openai-direct-forwarding-result.md
```

2. 完整执行：

```bash
cd backend
mvn test
git diff --check
```

3. 建议 Git 提交：

```text
feat(网关): 新增 OpenAI 直连执行与模型映射
feat(路由): 新增真实请求静态选择器
test(网关): 覆盖 Chat Completions 与 SSE 中转
docs: 补充 OpenAI 直连实施结果
```

最终报告必须包含：

```text
前置提交与审查结论
修改文件
配置项
静态选择与执行目标设计
非流式与 SSE 行为
错误映射与 secret redaction
实际测试命令和结果
未完成项与真实阻塞
New-API / Sub2API 对照结论
Git 提交 Hash
```