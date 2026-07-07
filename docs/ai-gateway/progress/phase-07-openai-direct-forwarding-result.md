# 阶段 07｜OpenAI Compatible Direct API 首个端到端中转｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。
>
> 本文件不得记录任何真实 Client API Key、上游 API Key、Authorization、Cookie、baseUrl、resourceId、upstream model 或 secret。

## 1. 实施时间与提交

- 开始时间：2026-07-07 20:20 +08:00
- 完成时间：2026-07-07 21:00 +08:00
- Git 提交 Hash：见最终报告
- 实施分支：`master`
- 开始时 `master` 最新提交：`b0a18e2 docs: docs: 新增 OpenAI 直连中转阶段计划`

## 2. 前置阶段复核

- 阶段 01：独立 Vert.x 网关运行时已存在，内部 health/ready/version 与访问日志边界可复用。
- 阶段 02：控制面 Provider、Connection、PublicModel 目录能力已存在，本阶段未修改。
- 阶段 03：Credential 与 ExecutionResource 能发布快照 secret envelope，本阶段仅使用网关内存解封装结果。
- 阶段 04：Snapshot secure manifest、Redis current pointer、last-known-good 与 ready 语义已存在，本阶段继续复用。
- 阶段 05：Snapshot V2 static route plan 与 `converge-routing-core` 已存在，本阶段新增真实请求 selector。
- 阶段 06：Snapshot V3 Client API Key 本地认证、AccessGroup grant 与 `GET /v1/models` 已存在；历史提交 `9479db6` 在当前历史中。
- 当前最大 Flyway 版本：V10。
- 是否新增 Flyway：否。
- 是否新增 Snapshot schema：否，继续兼容 V1 / V2 / V3。

## 3. Routing Core

- 新增类：`StaticRouteRequestSelector`、`StaticRouteSelection`。
- request selector 输入：已编译 `StaticRoutePlan` 与安全 seed。
- request selector 输出：租户、公开模型、操作、策略、revision、选中 pool 与 execution resource 的安全元数据。
- priority / weight 规则：先取最高 RouteTarget priority，同层按正整数 weight 选择 Pool；再取最高 PoolMember priority，同层按正整数 weight 选择 Resource。
- seed 规则：由 requestId、tenantId、publicModelCode、operation 与 snapshot revision 组成，不含 raw key、Authorization、Cookie、secret 或请求体。
- 是否读取动态状态：否。
- 是否读取 secret：否。
- 是否执行 retry / fallback：否。

## 4. Gateway 执行配置与生命周期

- 新增环境变量 / 系统属性：`GATEWAY_UPSTREAM_CONNECT_TIMEOUT_MS`、`GATEWAY_UPSTREAM_IDLE_TIMEOUT_MS`、`GATEWAY_UPSTREAM_POOL_MAX_SIZE`、`GATEWAY_OPENAI_MAX_REQUEST_BYTES`、`GATEWAY_OPENAI_MAX_NON_STREAM_RESPONSE_BYTES`、`GATEWAY_OPENAI_MAX_ERROR_RESPONSE_BYTES`、`GATEWAY_OPENAI_MAX_SSE_EVENT_BYTES`。
- 默认值：10000ms、90000ms、100、4MiB、16MiB、64KiB、1MiB。
- 启动期校验：全部必须为正数，Vert.x int timeout 字段不得超过 `Integer.MAX_VALUE`。
- HttpClient 生命周期：`GatewayExecutionRuntime` 在进程启动时创建共享 Vert.x `HttpClientAgent`，关闭时统一释放。
- redirect 策略：禁用自动 redirect。
- 连接池：HTTP/1 与 HTTP/2 pool size 均使用 `GATEWAY_UPSTREAM_POOL_MAX_SIZE`。
- timeout：连接超时、请求 timeout 与 idle timeout 均来自执行配置。
- 优雅关闭顺序：`executionRuntime.beginDrain()` → HTTP Server graceful shutdown → shared HttpClient close → SnapshotRuntime close → Vert.x close。
- 关闭期间新请求行为：`POST /v1/chat/completions` 返回 503 `gateway_shutting_down`。

## 5. 执行目标与上游请求

- 执行目标解析入口：`GatewaySnapshotRuntime.resolveOpenAiChatExecution(principal, publicModelCode, requestId)`。
- DIRECT_API / OPENAI_COMPATIBLE 校验：仅允许 `DIRECT_API`、`OPENAI_COMPATIBLE`、`ENABLED` 且存在 runtime secret 的资源；缺失认证模式固定元数据时 fail closed。
- baseUrl 拼接规则：只使用 snapshot 中已规范化 base URL，拼接 `chat/completions`，拒绝客户端提供 URL。
- upstream Authorization 注入：仅由 runtime secret 构造 Bearer header。
- Header allowlist / denylist：上游仅构造 Authorization、Content-Type、Accept、User-Agent、X-Request-Id；不转发 Client Authorization、Cookie、Host、hop-by-hop 或客户端自定义上游鉴权 header。
- request model 映射：仅改写顶层 `model` 为选中 binding 的上游模型名。
- response model 映射：非流式与 SSE JSON data event 顶层 `model` 改回 public model code。
- runtime secret redaction：执行目标不是 record，`toString()` 对资源、URL、上游模型和 secret 做掩码；异常消息只含安全分类。
- 是否存在 Gateway DB / Redis per-request lookup：否。

## 6. `POST /v1/chat/completions`

- 认证复用：提取 `GatewayDataPlaneAuthenticator`，`GET /v1/models` 与 Chat Completions 共用严格 Bearer 解析。
- Content-Type：仅接受 `application/json`，允许 charset 参数。
- request size：按 `GATEWAY_OPENAI_MAX_REQUEST_BYTES` 限制，超过返回 413。
- JSON 最小校验：必须为 JSON object，`model` 必须是非空 string，`stream` 缺省或 boolean。
- authorization：使用 `(publicModelCode, CHAT_COMPLETIONS)` exact grant。
- route resolve：从当前 tenant 的 V2/V3 static route plan 中做真实静态选择，无路由返回 404。
- 非流式：读取受限 2xx JSON object，改写响应顶层 model，安全映射上游错误。
- SSE：要求上游 `text/event-stream`，按 event 增量重写 model，支持分片、背压和断开取消。
- 上游请求是否真实发起：仅在有效 key、有效 grant、有效 route 与 gateway ready 时发起。
- 是否支持 tools 等 opaque JSON 字段：支持透传未知字段，本阶段不做完整 OpenAI 参数校验。
- 是否修改 React：否。

## 7. 错误与安全

- 400：非法 JSON、非法 `model`、非法 `stream` 或上游可归类请求拒绝。
- 401：Client API Key 缺失、无效、禁用、撤销或过期。
- 403：认证成功但无 `CHAT_COMPLETIONS` exact grant。
- 404：无有效 static route plan。
- 413：请求体超过配置上限。
- 415：请求 Content-Type 非 `application/json`。
- 429：上游 429 映射为 `upstream_rate_limited`。
- 502：上游认证失败、协议错误、连接错误或上游服务错误。
- 503：gateway not ready、snapshot stale 或 shutdown drain。
- 504：上游超时。
- 上游错误 body 处理：受限读取后丢弃，不回显。
- access log：仍仅记录 method、path、status、duration、requestId。
- secret / authorization / body leak scan：主代码扫描未发现敏感日志输出；命中项为变量名、注释、认证读取或 redaction。
- 是否泄露 upstream model：对下游响应不泄露；测试覆盖非流式与 SSE。

## 8. SSE 与背压

- event delimiter：支持 `\n\n` 与 `\r\n\r\n`。
- UTF-8 分片：以完整 event 字节为单位解码，支持多 chunk。
- model rewrite：仅重写 `data:` JSON event 的顶层 `model`。
- `[DONE]`：原样透传。
- event size limit：按 `GATEWAY_OPENAI_MAX_SSE_EVENT_BYTES` 限制，超限取消上游并结束流。
- downstream writeQueueFull：暂停上游响应流，drain 后恢复。
- client disconnect：下游 close handler best-effort cancel 上游请求。
- headers 后上游失败：只结束流，不再写 JSON error。

## 9. 测试与验证

```text
mvn -pl converge-routing-core -am "-Dtest=StaticRouteRequestSelectorTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：首次红灯确认 selector 缺失，完成实现后通过。

mvn -pl converge-gateway -am "-Dtest=GatewayOpenAiDirectForwardingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：首次发现测试夹具 InterruptedException 与 key 生命周期问题，修复后通过，6 个用例全部成功。

mvn -pl converge-routing-core,converge-gateway -am "-Dtest=StaticRouteRequestSelectorTest,StaticRoutePlanCompilerTest,GatewayOpenAiDirectForwardingTest,GatewayOpenAiSseModelRewriterTest,GatewayClientAccessRuntimeTest,GatewaySnapshotRuntimeTest,GatewaySnapshotStaticRoutingRuntimeTest,GatewayRuntimeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
结果：通过，27 个用例全部成功。

mvn test
结果：通过，backend 全 reactor 成功。
```

- routing-core：覆盖 priority、weight、seed 稳定性和无动态状态依赖。
- 非流式：覆盖有效 key + grant + route、request.model 改写、response.model 改回 public model、上游 Authorization 注入、无 grant、无 route。
- SSE：覆盖 event 分片、UTF-8 分片、model 重写、`[DONE]`、单 event 超限；背压和客户端断开由 Vert.x handler 路径实现。
- 错误映射：覆盖 400、401、403、404、413、415、429、502、503、504。
- 多租户：沿用 V3 gateway client access 与 static routing runtime 回归。
- V1 / V2 / V3 snapshot 回归：专项回归与全量 `mvn test` 通过。
- `/v1/models` 回归：`GatewayClientAccessRuntimeTest` 通过。
- leak scan：依赖越界扫描和敏感日志扫描通过。
- backend root `mvn test`：通过。
- `git diff --check`：通过。
- 未执行项及原因：无。

## 10. 阶段边界复核

- retry / fallback / circuit breaker：未实现。
- 动态健康 / concurrency / RPM / TPM / cooldown：未实现。
- pricing / billing / ledger：未实现。
- OAuth / account pool / sticky session：未实现。
- Responses / Embeddings / Claude / Gemini / Realtime：未实现。
- WebSocket：未实现。
- React：未修改。
- Gateway database access：未新增。
- Gateway Redis per-request lookup：未新增。
- 结论：本阶段只落地 OpenAI Compatible Chat Completions Direct API 非流式与 SSE 直连中转，未越过阶段边界。

## 11. 偏差、风险与下一阶段输入

- 与计划偏差：无 Flyway、无 Snapshot V4；严格复用 V3 中已有资源、secret、grant 与 route plan。
- 未完成项：无。
- 已知风险：阶段 07 只支持已确认可映射为 Bearer secret 的 Direct API 资源；不支持 header/query/cookie 等其他上游认证方式。
- 对下一阶段建议：后续若引入动态调度、限流、计费或失败重试，应继续保持 raw key、runtime secret、上游地址和上游模型名不进入日志与外部响应。

## 12. New-API / Sub2API 对照结论

- New-API：本阶段补齐 OpenAI Compatible `POST /v1/chat/completions` 的非流式与 SSE 直连基础能力，但不包含 New-API 的账号池、渠道健康、限流、计费、模型定价、失败重试或管理控制台能力。
- Sub2API：本阶段具备以静态资源与密钥转发到 OpenAI Compatible 上游的最小数据面能力，但不包含 Sub2API 的订阅转换、渠道 fallback、用量统计、套餐映射或用户级策略能力。
- 是否偏离：未偏离；阶段 07 只实现首个真实转发端点与安全模型映射，后续商业化和动态调度能力仍留在后续阶段。
