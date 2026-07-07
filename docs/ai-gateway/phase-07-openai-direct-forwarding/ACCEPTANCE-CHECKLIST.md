# 阶段 07｜验收清单

> 实施完成后逐项勾选。  
> 不得在备注中填写真实 Client API Key、上游 API Key、Authorization、Cookie、baseUrl、resourceId、upstream model 或 secret。

## A. 前置与阶段边界

- [ ] 已阅读阶段 01 至阶段 06 的 README、协议与实施结果。
- [ ] 已确认当前 `master` 包含阶段 06 实施提交。
- [ ] 未修改 Client API Key 格式、verifier、AccessGroup、Grant 或 Snapshot V3 原有语义。
- [ ] 未新增 PostgreSQL / Redis 每请求访问。
- [ ] 未新增 Flyway migration，或已在结果文档说明真实必要原因。
- [ ] 未新增 Snapshot V4，或已在结果文档说明真实必要原因。
- [ ] 未修改 React 前端。

## B. 静态路由选择

- [ ] 新增纯 Java `StaticRouteRequestSelector`。
- [ ] 未直接使用仅管理端语义的 `StaticRoutePreviewSelector` 执行真实请求。
- [ ] 真实选择遵守 priority / weight 规则。
- [ ] selection seed 不包含 raw Client API Key 或上游 secret。
- [ ] 不读取健康、并发、RPM、TPM、429、冷却或 sticky session。
- [ ] 无 retry、fallback、failover 或 circuit breaker。

## C. Gateway 执行目标

- [ ] 只执行 DIRECT_API + OPENAI_COMPATIBLE 资源。
- [ ] 只允许有效 StaticRoutePlan 的资源。
- [ ] upstream URI 仅来自 snapshot baseUrl。
- [ ] 客户端不能指定上游 URL、Provider、资源或 Credential。
- [ ] runtime secret 不使用 Java record 或 Lombok 自动 `toString`。
- [ ] runtime secret 不写入 RoutingContext。
- [ ] runtime secret 不进入日志、异常、指标标签、审计或测试输出。

## D. 上游 HTTP

- [ ] Gateway 使用共享单例 Vert.x HttpClient。
- [ ] 未使用 RestTemplate、WebClient、OkHttp 或 JDK blocking HttpClient。
- [ ] 未使用 vertx-http-proxy。
- [ ] HTTP / HTTPS 可按 snapshot baseUrl 执行。
- [ ] 自动 redirect 已关闭。
- [ ] 连接超时、空闲超时、连接池和 body 上限均已启动期校验。
- [ ] Gateway 正常关闭时先 drain，再关闭 HttpClient、SnapshotRuntime、Vert.x。

## E. Request 协议

- [ ] 新增 `POST /v1/chat/completions`。
- [ ] 仅接受 Authorization Bearer Client API Key。
- [ ] 仅接受 application/json。
- [ ] 非法 JSON、model 非字符串、stream 非 boolean 返回 400。
- [ ] 请求过大返回 413。
- [ ] 无 CHAT_COMPLETIONS grant 返回 403。
- [ ] 无 route plan 返回 404。
- [ ] 只重写顶层 request.model。
- [ ] Client Authorization / Cookie / Hop-by-hop Header 不会转发上游。
- [ ] 上游 Authorization 仅由 runtime secret 构造。

## F. 非流式响应

- [ ] 上游 2xx JSON 在受限大小内读取。
- [ ] 顶层 response.model 已改回 publicModelCode。
- [ ] 不会向客户端泄露 upstreamModelName。
- [ ] 非 JSON、非法 JSON、超大响应被安全处理。
- [ ] 上游错误 body 不回显。

## G. SSE

- [ ] `stream: true` 使用 text/event-stream。
- [ ] 下游设置 `Cache-Control: no-cache` 与 `X-Accel-Buffering: no`。
- [ ] 支持 event 分片与 UTF-8 chunk 分片。
- [ ] data JSON event 顶层 model 已改回 publicModelCode。
- [ ] `[DONE]` 正确透传。
- [ ] 单 SSE event 有大小上限。
- [ ] 下游 writeQueueFull 时 pause 上游，drain 后 resume。
- [ ] 客户端断开后 best-effort cancel 上游。
- [ ] Headers 已写出后异常不再写 JSON error。

## H. 错误与安全

- [ ] 400、401、403、404、413、415、429、502、503、504 均返回统一数据面错误 envelope。
- [ ] 上游 401 / 403 映射为上游认证问题，不误报 Client Key 无效。
- [ ] 错误响应不包含 baseUrl、Provider、Connection、Credential、resourceId、upstreamModelName 或 secret。
- [ ] access log 不包含 Authorization、Cookie、x-api-key、请求体、响应体。
- [ ] secret leak scan 通过。

## I. 测试

- [ ] routing-core 单元测试覆盖 priority、weight 与 seed 稳定性。
- [ ] gateway 测试覆盖非流式请求与模型双向重写。
- [ ] gateway 测试覆盖 SSE model 重写、`[DONE]`、分片、UTF-8 分片、背压。
- [ ] gateway 测试覆盖客户端断开与上游取消。
- [ ] gateway 测试覆盖 400、401、403、404、413、415、429、502、503、504。
- [ ] gateway 测试覆盖拒绝路径不发起上游请求。
- [ ] gateway 测试覆盖上游 Authorization 不泄露。
- [ ] gateway 测试覆盖上游错误 body 不回显。
- [ ] gateway 测试覆盖 V1 / V2 / V3 snapshot 回归。
- [ ] backend root `mvn test` 通过。
- [ ] `git diff --check` 通过。
- [ ] 已填写阶段结果文档。
- [ ] 已创建中文 Git 提交。

## 备注

- 实施日期：
- 实施提交：
- 关键测试命令：
- 未完成项：
- 已知风险：