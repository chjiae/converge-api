# 阶段 07｜OpenAI Compatible Direct API 首个端到端中转｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。
>
> 本文件不得记录任何真实 Client API Key、上游 API Key、Authorization、Cookie、baseUrl、resourceId、upstream model 或 secret。

## 1. 实施时间与提交

- 开始时间：
- 完成时间：
- Git 提交 Hash：
- 实施分支：
- 开始时 `master` 最新提交：

## 2. 前置阶段复核

- 阶段 01：
- 阶段 02：
- 阶段 03：
- 阶段 04：
- 阶段 05：
- 阶段 06：
- 当前最大 Flyway 版本：
- 是否新增 Flyway：
- 是否新增 Snapshot schema：

## 3. Routing Core

- 新增类：
- request selector 输入：
- request selector 输出：
- priority / weight 规则：
- seed 规则：
- 是否读取动态状态：
- 是否读取 secret：
- 是否执行 retry / fallback：

## 4. Gateway 执行配置与生命周期

- 新增环境变量 / 系统属性：
- 默认值：
- 启动期校验：
- HttpClient 生命周期：
- redirect 策略：
- 连接池：
- timeout：
- 优雅关闭顺序：
- 关闭期间新请求行为：

## 5. 执行目标与上游请求

- 执行目标解析入口：
- DIRECT_API / OPENAI_COMPATIBLE 校验：
- baseUrl 拼接规则：
- upstream Authorization 注入：
- Header allowlist / denylist：
- request model 映射：
- response model 映射：
- runtime secret redaction：
- 是否存在 Gateway DB / Redis per-request lookup：

## 6. `POST /v1/chat/completions`

- 认证复用：
- Content-Type：
- request size：
- JSON 最小校验：
- authorization：
- route resolve：
- 非流式：
- SSE：
- 上游请求是否真实发起：
- 是否支持 tools 等 opaque JSON 字段：
- 是否修改 React：

## 7. 错误与安全

- 400：
- 401：
- 403：
- 404：
- 413：
- 415：
- 429：
- 502：
- 503：
- 504：
- 上游错误 body 处理：
- access log：
- secret / authorization / body leak scan：
- 是否泄露 upstream model：

## 8. SSE 与背压

- event delimiter：
- UTF-8 分片：
- model rewrite：
- `[DONE]`：
- event size limit：
- downstream writeQueueFull：
- client disconnect：
- headers 后上游失败：

## 9. 测试与验证

```text
在此写入实际执行命令与结果。
```

- routing-core：
- 非流式：
- SSE：
- 错误映射：
- 多租户：
- V1 / V2 / V3 snapshot 回归：
- `/v1/models` 回归：
- leak scan：
- backend root `mvn test`：
- `git diff --check`：
- 未执行项及原因：

## 10. 阶段边界复核

- retry / fallback / circuit breaker：
- 动态健康 / concurrency / RPM / TPM / cooldown：
- pricing / billing / ledger：
- OAuth / account pool / sticky session：
- Responses / Embeddings / Claude / Gemini / Realtime：
- WebSocket：
- React：
- Gateway database access：
- Gateway Redis per-request lookup：
- 结论：

## 11. 偏差、风险与下一阶段输入

- 与计划偏差：
- 未完成项：
- 已知风险：
- 对下一阶段建议：

## 12. New-API / Sub2API 对照结论

- New-API：
- Sub2API：
- 是否偏离：