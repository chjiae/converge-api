# 阶段 07｜OpenAI Compatible Direct API 首个端到端中转

## 1. 阶段目标

阶段 06 已完成：

```text
Client API Key
  → AccessGroup
    → PublicModel + CanonicalOperation 精确授权
      → Gateway Snapshot V3
        → Gateway 本地 Key 索引
          → GET /v1/models
```

阶段 07 在不改变既有 Client API Key、访问组、静态路由、快照与租户隔离语义的前提下，实现第一个真实数据面调用闭环：

```text
POST /v1/chat/completions
  → Client API Key 本地认证
  → PublicModel + CHAT_COMPLETIONS 精确授权
  → StaticRoutePlan 静态选择
  → Direct API 执行资源解析
  → 上游 OpenAI Compatible /chat/completions
  → 非流式 JSON 或 SSE 响应
```

本阶段只支持：

```text
下游协议：OpenAI Chat Completions
上游协议：OPENAI_COMPATIBLE
资源类型：DIRECT_API
下游认证：Client API Key
规范化操作：CHAT_COMPLETIONS
```

本阶段不做跨协议转换。将下游 `publicModelCode` 替换为绑定的 `upstreamModelName`，以及将响应中的上游模型名改回公开模型名，不属于协议转换。

---

## 2. 前置实现复核

### 阶段 01

- `converge-gateway` 是独立 Vert.x 数据面进程；
- Gateway 不依赖 Spring、MyBatis、JDBC、PostgreSQL 或控制面运行时；
- 已具备 requestId、访问日志、健康检查、就绪检查、统一错误处理和优雅关闭。

### 阶段 02 至阶段 05

- 已具备 Provider、Connection、PublicModel、Credential、ExecutionResource；
- 已具备 ResourcePool、ResourceModelBinding、RoutePolicy、RouteTarget；
- 已具备 `PublicModel + CanonicalOperation → StaticRoutePlan`；
- 已具备优先级优先、同优先级权重选择；
- Gateway 只从安全快照读取执行资源配置与运行时秘密；
- Gateway 不直接访问数据库。

### 阶段 06

- Gateway Snapshot V3 已包含 Client API Key、AccessGroup、精确模型授权；
- Gateway 已构建本地 `keyId → GatewayClientPrincipal` 索引；
- 数据面认证不访问 Redis 或 PostgreSQL；
- `/v1/models` 已只返回已授权且存在有效静态路由计划的模型；
- 当前未实现上游 HTTP、SSE、WebSocket、计费、限流、动态健康或失败切换。

---

## 3. 首个端到端调用链

```text
Client
  POST /v1/chat/completions
  Authorization: Bearer cvg_live_<keyId>_<secret>
  body.model = <publicModelCode>
          │
          ▼
GatewayDataPlaneAuthenticator
          │
          ├─ GatewaySnapshotRuntime.authenticateClientKey()
          ├─ GatewayClientPrincipal
          └─ 不访问 Redis / PostgreSQL
          │
          ▼
GatewayOpenAiChatCompletionsHandler
          │
          ├─ 校验 Gateway ready / draining 状态
          ├─ 校验 Content-Type、JSON、model、stream
          ├─ 校验 CHAT_COMPLETIONS 精确授权
          └─ 生成静态选择 seed
          │
          ▼
GatewaySnapshotRuntime.resolveOpenAiChatExecution(...)
          │
          ├─ 查询当前 tenant 的 StaticRoutePlan
          ├─ StaticRouteRequestSelector 选择执行资源
          ├─ 校验 DIRECT_API + OPENAI_COMPATIBLE
          ├─ 获取 baseUrl、upstreamModelName、runtime secret
          └─ 返回不可打印的内部执行目标
          │
          ▼
GatewayOpenAiDirectExecutor
          │
          ├─ 将 request.model 改为 upstreamModelName
          ├─ 注入上游 Authorization
          ├─ POST <baseUrl>/chat/completions
          ├─ 非流式 JSON 顶层 model 改回 publicModelCode
          └─ SSE event 中的顶层 model 改回 publicModelCode
          │
          ▼
Client
  OpenAI Compatible JSON 或 text/event-stream
```

---

## 4. 数据面接口范围

### 新增接口

```http
POST /v1/chat/completions
Authorization: Bearer <Client API Key>
Content-Type: application/json
```

### 已有接口

```http
GET /v1/models
Authorization: Bearer <Client API Key>
```

### 本阶段不新增

```text
/v1/responses
/v1/embeddings
/v1/images/*
/v1/audio/*
/v1/messages
/v1beta/models/*
Claude Messages
Gemini
WebSocket
Realtime
```

---

## 5. 静态资源选择

阶段 05 的 `StaticRoutePreviewSelector` 仅服务于管理端预览，不能直接承担真实请求选择。

本阶段在 `converge-routing-core` 中新增纯 Java 组件：

```text
StaticRouteRequestSelector
StaticRouteSelection
```

选择规则必须与阶段 05 完全一致：

```text
1. 只选择最高 priority 的 RouteTarget 层；
2. 在该层按 weight 选择 ResourcePool；
3. 只选择该 Pool 中最高 priority 的 PoolMember 层；
4. 在该层按 weight 选择 ExecutionResource；
5. 输出 executionResourceId 与 upstreamModelName；
6. 不读取健康、并发、RPM、TPM、429、冷却或 sticky session。
```

建议 seed：

```text
requestId
+ tenantId
+ publicModelCode
+ CHAT_COMPLETIONS
+ snapshotRevision
```

禁止将以下内容纳入 seed：

```text
raw Client API Key
Client API Key secret
Authorization
Cookie
runtime secret
upstream API Key
```

本阶段不做 retry、fallback、资源切换或故障转移。

---

## 6. Gateway 执行配置

在现有 `GatewayConfig` 中新增数据面执行配置，例如：

```text
GATEWAY_UPSTREAM_CONNECT_TIMEOUT_MS
GATEWAY_UPSTREAM_IDLE_TIMEOUT_MS
GATEWAY_UPSTREAM_POOL_MAX_SIZE
GATEWAY_OPENAI_MAX_REQUEST_BYTES
GATEWAY_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES
GATEWAY_OPENAI_MAX_ERROR_RESPONSE_BYTES
GATEWAY_OPENAI_MAX_SSE_EVENT_BYTES
```

建议默认值：

```text
GATEWAY_UPSTREAM_CONNECT_TIMEOUT_MS=10000
GATEWAY_UPSTREAM_IDLE_TIMEOUT_MS=90000
GATEWAY_UPSTREAM_POOL_MAX_SIZE=100
GATEWAY_OPENAI_MAX_REQUEST_BYTES=4194304
GATEWAY_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES=16777216
GATEWAY_OPENAI_MAX_ERROR_RESPONSE_BYTES=65536
GATEWAY_OPENAI_MAX_SSE_EVENT_BYTES=1048576
```

要求：

- 所有配置必须在 Gateway 启动期校验；
- 不允许零、负数、无界 body 上限或非法整数；
- 不新增 Spring 配置体系；
- 不读取 `AI_CREDENTIAL_*`；
- 不允许客户端指定上游 URL、资源、Provider、Credential 或上游鉴权 Header；
- 必须复用单例共享 Vert.x `HttpClient`；
- 禁止自动跟随重定向；
- 禁止每个请求新建 `HttpClient`。

---

## 7. 请求与模型映射

下游请求中的模型必须是公开模型：

```json
{
  "model": "public-model-code",
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ],
  "stream": false
}
```

执行资源的模型绑定可能为：

```text
upstreamModelName = upstream-real-model-name
```

发送上游前，只替换顶层 `model`：

```json
{
  "model": "upstream-real-model-name",
  "messages": [
    {
      "role": "user",
      "content": "你好"
    }
  ],
  "stream": false
}
```

规则：

- `model` 必须是非空字符串；
- `stream` 缺省等价于 `false`；
- 显式 `stream` 必须为 boolean；
- 其余 JSON 字段保持透传；
- 不修改 messages、tools、tool_choice、response_format、reasoning、modalities 等字段；
- 仅接受 `application/json`，可接受 charset 参数；
- 不支持 multipart/form-data；
- 请求体超过上限返回 413；
- 非法 JSON、非法 model、非法 stream 返回 400；
- 没有授权返回 403；
- 没有有效静态路线返回 404。

---

## 8. 上游请求规则

只允许选择满足全部条件的资源：

```text
resourceType == DIRECT_API
protocolType == OPENAI_COMPATIBLE
resource adminStatus == ENABLED
存在 runtime secret
存在 PublicModel + CHAT_COMPLETIONS 精确绑定
存在有效 StaticRoutePlan
```

上游 URI：

```text
normalizedBaseUrl + "/chat/completions"
```

`baseUrl` 只能来自已验证的 Gateway Snapshot。

Gateway 必须通过 URI 组件拼接路径，避免错误双斜杠、拼接 query 或覆盖原始路径；禁止直接相信客户端输入。

上游 Header 由 Gateway 重新构造：

```http
Authorization: Bearer <runtime secret>
Content-Type: application/json
Accept: application/json 或 text/event-stream
User-Agent: converge-gateway/<buildVersion>
X-Request-Id: <gateway requestId>
```

禁止向上游转发：

```text
客户端 Authorization
Cookie
Host
Connection
Keep-Alive
Transfer-Encoding
Content-Length
Upgrade
Proxy-*
X-Forwarded-*
客户端自定义鉴权 Header
```

---

## 9. 非流式响应

当 `stream != true`：

```text
1. 上游必须返回 2xx + JSON；
2. Gateway 在最大响应大小内读取完整 body；
3. 解析 JSON object；
4. 将顶层 model 强制改回 publicModelCode；
5. 返回 OpenAI Compatible JSON。
```

不得向客户端暴露：

```text
upstreamModelName
baseUrl
Provider
Connection
Credential
ResourcePool
RoutePolicy
executionResourceId
```

若上游返回非 JSON、非法 JSON、非 object、超大 body 或协议错误，返回安全 502。

---

## 10. SSE 流式响应

当 `stream == true`：

```text
1. 上游必须返回 2xx + text/event-stream；
2. Gateway 设置下游 SSE Header；
3. 按 SSE event 边界增量处理；
4. 对 data: JSON event 的顶层 model 改回 publicModelCode；
5. data: [DONE] 原样透传；
6. 不解析 token，不写 usage，不计费。
```

下游 Header：

```http
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-cache
X-Accel-Buffering: no
```

要求：

- 不聚合整个 SSE 响应；
- 支持一个 UTF-8 字符被多个网络 chunk 拆分；
- 支持一个 SSE event 被多个网络 chunk 拆分；
- 支持 `\n\n` 与 `\r\n\r\n` event 分隔；
- 单个 event 超过配置上限时中止上游并结束流；
- 下游写队列满时暂停上游读取，drain 后恢复；
- 客户端断开时 best-effort 取消上游请求；
- Headers 已写出后发生上游异常时，只结束 SSE 流，不再写 JSON 错误响应。

---

## 11. 数据面错误协议

所有数据面错误统一使用：

```json
{
  "error": {
    "message": "Invalid API key",
    "type": "invalid_request_error",
    "param": null,
    "code": "invalid_api_key"
  }
}
```

最小错误映射：

| 场景 | HTTP | code |
|---|---:|---|
| 缺失或无效 Client API Key | 401 | `invalid_api_key` |
| Gateway 未 ready 或 snapshot stale | 503 | `gateway_not_ready` |
| Gateway 正在关闭 | 503 | `gateway_shutting_down` |
| Content-Type 非 JSON | 415 | `unsupported_media_type` |
| JSON、model、stream 非法 | 400 | `invalid_request` |
| 请求过大 | 413 | `request_too_large` |
| 无 CHAT_COMPLETIONS 授权 | 403 | `model_access_denied` |
| 无有效静态路线 | 404 | `model_not_found` |
| 连接、DNS、TLS、协议错误 | 502 | `upstream_connection_error` 或 `upstream_protocol_error` |
| 上游空闲超时 | 504 | `upstream_timeout` |
| 上游 401 / 403 | 502 | `upstream_authentication_failed` |
| 上游 429 | 429 | `upstream_rate_limited` |
| 上游 400 / 404 / 409 / 422 | 400 | `upstream_rejected_request` |
| 上游 5xx | 502 | `upstream_server_error` |

禁止直接回显上游错误 body。

---

## 12. 日志、秘密与可观测性

允许记录：

```text
requestId
tenantId
clientApiKeyId
publicModelCode
canonicalOperation
stream
snapshotRevision
routePolicyId
HTTP status
耗时
安全错误分类
```

禁止记录：

```text
raw Client API Key
Authorization
Cookie
x-api-key
请求 body
响应 body
messages 内容
upstreamModelName
baseUrl
runtime secret
Credential
secret envelope
nonce
HMAC
Redis key
```

含 runtime secret 的执行目标：

- 不得使用 Java record；
- 不得使用 Lombok 自动 `toString`；
- `toString()` 必须脱敏；
- 不得放入 `RoutingContext`；
- 生命周期仅限于从执行目标解析到写入上游 Authorization Header 的短路径。

---

## 13. 优雅关闭

Gateway 关闭顺序必须调整为：

```text
1. GatewayExecutionRuntime 进入 draining；
2. 拒绝新的 /v1/chat/completions；
3. HTTP Server graceful shutdown；
4. 等待已有请求与 SSE 流；
5. 关闭共享 HttpClient；
6. 关闭 SnapshotRuntime；
7. 关闭 Vert.x。
```

禁止在仍有转发中的 SSE 时直接关闭 Vert.x。

---

## 14. 本阶段明确不做

```text
不新增控制面 CRUD
不修改 React 前端
不重构 Client API Key、AccessGroup、Grant 或 StaticRoutePlan
不做 retry、fallback、failover、circuit breaker
不做健康检查、429 cooldown、并发、RPM、TPM
不做价格、倍率、余额、账本、预扣、结算或支付
不做 OAuth、Refresh Token、Cookie、账号池或 sticky session
不做 Responses、Embeddings、Claude、Gemini、Image、Audio、Realtime
不做 WebSocket
不使用 vertx-http-proxy
不让 Gateway 访问 PostgreSQL
不在每个请求中查询 Redis
```