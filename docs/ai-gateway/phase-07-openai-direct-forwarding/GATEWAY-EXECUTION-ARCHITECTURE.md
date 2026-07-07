# 阶段 07｜Gateway 执行架构

## 1. 模块职责

```text
converge-contract
  └─ 不引入 Vert.x、Spring、Redis、JDBC 依赖
  └─ 本阶段原则上不新增 Snapshot schema

converge-routing-core
  ├─ StaticRouteRequestSelector
  └─ StaticRouteSelection
  └─ 纯 Java，无网络、Redis、数据库、日志

converge-gateway
  ├─ GatewayExecutionConfig
  ├─ GatewayExecutionRuntime
  ├─ GatewayDataPlaneAuthenticator
  ├─ GatewayOpenAiChatCompletionsHandler
  ├─ GatewayOpenAiDirectExecutor
  ├─ GatewayOpenAiSseModelRewriter
  ├─ GatewayUpstreamErrorMapper
  └─ GatewaySnapshotRuntime.resolveOpenAiChatExecution(...)
```

不得把真实 HTTP 调用放入：

```text
converge-contract
converge-routing-core
converge-web-service
Spring Controller
GatewaySnapshotRuntime 的 Redis 对账流程
```

---

## 2. `StaticRouteRequestSelector`

位置：

```text
backend/converge-routing-core
```

职责：

```text
输入：StaticRoutePlan + request selection seed
输出：StaticRouteSelection
```

必须遵守：

```text
最高 RouteTarget priority
→ 同层按 weight 选择 Pool
→ 最高 PoolMember priority
→ 同层按 weight 选择 ExecutionResource
```

不得：

```text
读取 Redis
读取数据库
读取动态健康
读取并发状态
读取 Credential
读取 secret
调用网络
写业务日志
实现 retry
```

---

## 3. `GatewaySnapshotRuntime.resolveOpenAiChatExecution(...)`

建议入口：

```text
resolveOpenAiChatExecution(
    GatewayClientPrincipal principal,
    String publicModelCode,
    String requestId
)
```

职责：

```text
校验 Gateway ready
校验 CHAT_COMPLETIONS exact grant
查询 tenant snapshot
查询 StaticRoutePlan
调用 StaticRouteRequestSelector
解析 ExecutionResource
校验 DIRECT_API + OPENAI_COMPATIBLE + ENABLED
获取已解封装的 runtime secret
返回内部执行目标
```

不得：

```text
发起 HTTP 请求
写 Header
把 runtime secret 放入 RoutingContext
查询 Redis
查询 PostgreSQL
记录 secret
```

---

## 4. `GatewayOpenAiExecutionTarget`

此对象包含 runtime secret，要求：

```text
不是 Java record
不是 Lombok @Data
没有自动生成的 toString
toString 必须完全脱敏
equals/hashCode 不包含 runtime secret
不得序列化
不得写日志
不得放入 RoutingContext
```

建议字段：

```text
tenantId
publicModelCode
snapshotRevision
routePolicyId
executionResourceId
baseUrl
upstreamModelName
runtimeSecret
stream
```

只有 `GatewayOpenAiDirectExecutor` 可以短暂读取 `runtimeSecret` 来构造上游 Authorization Header。

---

## 5. 共享 HttpClient 生命周期

必须在 `GatewayExecutionRuntime` 创建单例共享 `HttpClient`：

```text
GatewayExecutionRuntime
  └─ Vertx.createHttpClient(...)
```

禁止：

```text
每个请求创建 HttpClient
每个资源创建 HttpClient
每个 SSE event 创建连接
RestTemplate
WebClient
OkHttp
JDK blocking HttpClient
vertx-http-proxy
```

共享 Client 需要：

```text
支持 HTTP 与 HTTPS
禁用自动 redirect
连接获取超时
上游 idle timeout
最大连接池大小
关闭时统一释放
```

---

## 6. 非流式处理

非流式需要重写顶层 `model`，允许有限缓冲：

```text
max bytes = GATEWAY_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES
```

处理：

```text
读取上游 response
  → 每个 chunk 检查累计字节数
  → 超限取消上游
  → 返回安全错误
  → JSON object parse
  → 重写 model
  → 返回下游
```

禁止：

```text
无上限 body 聚合
仅相信 Content-Length
原样透传上游 JSON
```

---

## 7. SSE 背压

SSE 的上游响应为读取端，下游 HTTP Response 为写入端。

必须处理：

```text
上游 chunk 到达
  → SSE rewriter 输出 bytes
  → 写入下游 response
  → 下游 writeQueueFull
       → pause 上游
  → downstream drainHandler
       → resume 上游
```

禁止：

```text
无界 List
无界 StringBuilder
聚合完整 SSE response
逐网络 chunk 直接当作完整 SSE event 处理
```

---

## 8. 客户端断开

客户端断开后：

```text
1. 标记 exchange cancelled；
2. best-effort 调用 upstream request.cancel()；
3. 不再向下游写入数据；
4. 记录安全结果分类；
5. 不触发 retry。
```

HTTP/1.x 与 HTTP/2 的实际取消行为可能不同，但实现不得依赖取消必然即时终止远端计算。

---

## 9. 优雅关闭

`GatewayRuntime.close()` 调整为：

```text
executionRuntime.beginDrain()
  → server.shutdown(...)
  → executionRuntime.close()
  → snapshotRuntime.close()
  → vertx.close()
```

要求：

```text
draining 后新 Chat 请求返回 503 gateway_shutting_down
现有 SSE 在 shutdown timeout 内尽可能完成
超时后关闭 HttpClient 与 Vert.x
关闭流程不阻塞 event loop
```

---

## 10. 不使用 vertx-http-proxy 的原因

本阶段需要：

```text
重写 request.model
重写非流式 response.model
重写 SSE event 中的 response.model
注入运行时上游 Authorization
执行 Client Key 身份与授权
执行静态路由选择
进行安全错误映射
处理 SSE 背压与取消
```

因此本阶段不是透明反向代理。

`vertx-http-proxy` 未来只可作为完全透明路由场景的可选评估项，不得成为 Gateway 核心执行抽象。