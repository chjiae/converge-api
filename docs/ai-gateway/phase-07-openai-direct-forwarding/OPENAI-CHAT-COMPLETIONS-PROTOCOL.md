# 阶段 07｜OpenAI Chat Completions 数据面协议

## 1. 对外接口

```http
POST /v1/chat/completions
Authorization: Bearer cvg_live_<keyId>_<secret>
Content-Type: application/json
```

客户端只能使用 Converge Client API Key。

不得接受：

```text
控制面 JWT
控制面 Cookie
上游 Provider API Key
Provider OAuth Token
资源 ID
Provider ID
Credential ID
上游 URL
```

---

## 2. 请求处理顺序

```text
RequestId
  → Gateway readiness / draining 判断
  → Bearer Client API Key 认证
  → Content-Type 校验
  → request body 大小限制
  → JSON 解析
  → model / stream 最小校验
  → CHAT_COMPLETIONS 精确授权
  → StaticRoutePlan 选择
  → 上游请求
  → JSON 或 SSE 返回
```

禁止：

```text
先请求上游再认证
先查 Redis / PostgreSQL 再认证
先完整读取无限 body 再判断上限
Gateway not ready 时仍尝试上游请求
```

---

## 3. Client API Key 认证

必须复用阶段 06 的：

```text
GatewaySnapshotRuntime.authenticateClientKey()
GatewayClientPrincipal
GatewayClientKeyCrypto
```

不得重新实现另一套：

```text
Key 格式
Key 哈希
salt
verifier
认证缓存
访问组授权
```

认证成功后，仅允许保存：

```text
tenantId
clientApiKeyId
accessGroupIds
effectiveGrants
```

不得保存 raw key。

---

## 4. 最小请求校验

允许最小请求：

```json
{
  "model": "public-model-code",
  "messages": [],
  "stream": false
}
```

本阶段只强制校验：

```text
请求 body 为 JSON object
model 是非空 string
stream 缺省或为 boolean
Content-Type 是 application/json，可包含 charset
body 不超过配置限制
```

本阶段不完整校验：

```text
messages role
messages content
tools
tool_choice
response_format
reasoning_effort
temperature
top_p
max_tokens
max_completion_tokens
modalities
audio
metadata
service_tier
```

除顶层 `model` 外，其余 JSON 字段保持透传。

---

## 5. 授权与路由

对于：

```text
model = M
operation = CHAT_COMPLETIONS
principal = P
```

必须同时满足：

```text
P 的 enabled effective grants 中存在 (M, CHAT_COMPLETIONS)
当前 tenant snapshot 中存在 M|CHAT_COMPLETIONS 的有效 StaticRoutePlan
StaticRouteRequestSelector 能选择到资源
资源属于当前 tenant
资源可被阶段 07 执行
```

资源必须满足：

```text
resourceType == DIRECT_API
protocolType == OPENAI_COMPATIBLE
resource adminStatus == ENABLED
runtime secret 已成功解封装
存在模型精确绑定
```

失败映射：

```text
无授权                  → 403 model_access_denied
无 StaticRoutePlan       → 404 model_not_found
资源不可执行             → 502 upstream_protocol_error
```

---

## 6. 上游 URI 与 Header

上游 URI：

```text
normalizedBaseUrl + "/chat/completions"
```

约束：

```text
baseUrl 只能来自已验证 Gateway Snapshot
仅允许 http / https
禁止 username/password
禁止 query
禁止 fragment
禁止客户端覆盖
禁止自动 redirect
```

上游 Header 必须由 Gateway 新建：

```http
Authorization: Bearer <runtime secret>
Content-Type: application/json
Accept: application/json
User-Agent: converge-gateway/<buildVersion>
X-Request-Id: <requestId>
```

流式时：

```http
Accept: text/event-stream
```

禁止透传：

```text
Authorization
Cookie
Host
Connection
Transfer-Encoding
Content-Length
Upgrade
Proxy-*
X-Forwarded-*
客户端自定义上游鉴权 Header
```

---

## 7. 请求模型映射

输入：

```json
{
  "model": "public-model-code",
  "stream": true,
  "messages": []
}
```

若绑定的上游模型为：

```text
upstreamModelName = upstream-provider-model
```

则上游请求为：

```json
{
  "model": "upstream-provider-model",
  "stream": true,
  "messages": []
}
```

只允许修改顶层 `model`。

不得：

```text
修改 messages
修改 tools
修改 tool_call_id
修改 function arguments
修改 reasoning
修改其他 JSON 字段
```

---

## 8. 非流式响应

上游成功响应必须满足：

```text
HTTP 2xx
Content-Type 为 application/json
body 小于等于非流式响应上限
body 为 JSON object
```

处理流程：

```text
读取受限 body
  → JSON object parse
  → 顶层 model 改为 publicModelCode
  → 返回下游
```

输入：

```json
{
  "id": "chatcmpl-upstream",
  "model": "upstream-provider-model",
  "choices": []
}
```

输出：

```json
{
  "id": "chatcmpl-upstream",
  "model": "public-model-code",
  "choices": []
}
```

不得泄露 `upstreamModelName`。

---

## 9. SSE 流式响应

客户端：

```json
{
  "model": "public-model-code",
  "stream": true,
  "messages": []
}
```

下游 Header：

```http
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-cache
X-Accel-Buffering: no
```

上游输入：

```text
data: {"id":"chatcmpl-upstream","model":"upstream-provider-model","choices":[...]}

data: [DONE]
```

下游输出：

```text
data: {"id":"chatcmpl-upstream","model":"public-model-code","choices":[...]}

data: [DONE]
```

规则：

```text
按完整 SSE event 解析
data: [DONE] 原样保留
data: JSON object 时只重写顶层 model
event / id / retry / 注释行保持语义
支持 \n\n 与 \r\n\r\n
支持网络 chunk 分片
单 event 有大小上限
```

异常规则：

```text
Headers 前上游失败      → JSON 错误响应
Headers 后上游失败      → 结束 SSE 流
event 非法或超过上限    → 结束 SSE 流
客户端断开              → best-effort cancel 上游请求
```

---

## 10. 上游错误处理

禁止直接透传上游错误 body。

安全映射：

| 上游情况 | 下游结果 |
|---|---|
| 401 / 403 | 502 `upstream_authentication_failed` |
| 429 | 429 `upstream_rate_limited` |
| 400 / 404 / 409 / 422 | 400 `upstream_rejected_request` |
| 5xx | 502 `upstream_server_error` |
| 非 JSON 错误 body | 502 `upstream_protocol_error` |
| 连接、DNS、TLS 失败 | 502 `upstream_connection_error` |
| 空闲超时 | 504 `upstream_timeout` |

允许读取受限错误 body 进行内部分类。

禁止：

```text
回显错误 body
记录错误 body
记录完整上游 Header
记录 URL
记录 Authorization
记录 Provider 堆栈
```