# 阶段 06｜下游 Client API Key、访问组与模型授权快照｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。

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
- 当前最大 Flyway 版本：

## 3. 数据库与领域对象

- Flyway 迁移：
- `ai_access_group`：
- `ai_access_group_model_grant`：
- `ai_client_api_key`：
- `ai_client_api_key_access_group`：
- 复合约束、索引与 tenant 双重校验：
- 新增枚举：
- 无硬删除结论：

## 4. Client API Key 安全实现

- 实际 key format：
- keyId / secret 随机长度：
- hash verifier 算法与固定输入：
- salt 长度：
- constant-time compare：
- one-time create/rotate response：
- `Cache-Control`：
- rotate / disable / revoke / expire 语义：
- 日志、审计、异常、响应和 Redis 脱敏结论：
- 是否保存 raw key（必须为否）：

## 5. 控制面 API 与授权

- AccessGroup：
- ModelGrant：
- ClientApiKey：
- Key-Group binding：
- 权限：
- audit：
- grant union：
- wildcard / regex / default-all 处理：

## 6. Snapshot V3 与 Gateway

- Contract V3：
- V1/V2/V3 兼容：
- outbox change types：
- Projector：
- global client key index：
- Gateway auth flow：
- principal：
- V3 last-known-good：
- status 安全摘要：
- rollout 顺序：

## 7. `GET /v1/models`

- 客户端认证入口：
- response envelope：
- 模型过滤规则：
- 401 / 403 / 503：
- 是否发起上游请求（必须为否）：
- 是否写入用量/账本（必须为否）：

## 8. 测试与验证

```text
在此写入实际执行命令与结果。
```

- 控制面集成测试：
- key crypto / verifier tests：
- gateway V1/V2/V3：
- global index multi-tenant：
- `/v1/models`：
- raw key leak scan：
- backend root `mvn test`：
- 未执行项与原因：

## 9. 阶段边界复核

- 上游 HTTP Client / SSE / WebSocket：
- Chat / Responses / Embeddings / Claude / Gemini：
- 协议转换 / retry / circuit breaker：
- 价格 / 倍率 / 账本 / 计费：
- 动态限流 / 并发 / sticky session：
- OAuth / account pool：
- React：
- Gateway DB/Redis per-request lookup：
- 结论：

## 10. 偏差、风险与下一阶段输入

- 与计划偏差：
- 未完成项：
- 已知风险：
- 对下一阶段建议：

## 11. New-API / Sub2API 对照结论

- New-API：
- Sub2API：
- 是否偏离：
