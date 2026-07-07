# 阶段 06｜验收清单

> 由实施者完成后逐项勾选；备注给出测试、命令或代码证据。禁止填写任何真实 API Key 或 secret。

## A. 前置与阶段边界

- [ ] 已阅读阶段 01~05 的 README、协议和实施结果。
- [ ] 已确认阶段 05 对应 Git commit 与 root `mvn test` 状态。
- [ ] 未新增上游 HTTP Client、SSE、WebSocket、协议转换、重试、熔断。
- [ ] 未新增 Chat Completions / Responses / Embeddings / Claude / Gemini 数据面端点。
- [ ] 未实现价格、倍率、余额、账本、计费、配额或支付。
- [ ] 未实现限流、并发、429 cooldown、健康检查、sticky session、OAuth 或账户池。
- [ ] React 前端未修改。

## B. Client API Key 安全

- [ ] 使用连续 Flyway version，未改既有 migration。
- [ ] Key 由 `SecureRandom` 生成；secret 至少 256 bit。
- [ ] Key 格式符合 `cvg_live_<keyId>_<secret>`。
- [ ] `keyId` 全局唯一且不可变。
- [ ] DB 不保存 raw key / secret / encrypted client secret。
- [ ] DB 保存随机 verifier salt、固定算法、不可逆 hash、key version、mask、状态和过期信息。
- [ ] verifier fixed encoding 无歧义，比较使用 constant-time API。
- [ ] create/rotate 只一次返回 raw key，响应带 `Cache-Control: no-store`。
- [ ] rotate 递增 version，旧 key 在新 snapshot 生效后不可用。
- [ ] revoked key 不可 re-enable 或 rotate。
- [ ] gateway/config/log/audit/exception/Redis/test output 无 raw key、Authorization、x-api-key、salt/hash 泄露。
- [ ] 敏感 Controller 入参日志已做 redaction。

## C. AccessGroup 与授权

- [ ] `AiAccessGroup` 不与 `AiResourcePool` 混用。
- [ ] Key、AccessGroup、Grant、Binding 均 tenant 隔离并有 DB + Service 双重校验。
- [ ] AccessGroup grant 精确到 `PublicModel + CanonicalOperation`。
- [ ] wildcard/regex/default-all grant 被拒绝。
- [ ] Key 可绑定多个 group；effective grants 为 enabled union。
- [ ] disabled key/group/grant/model 不产生授权。
- [ ] 无 DELETE hard-delete API。
- [ ] 仅 Tenant Owner/Admin 可管理；Member、未认证、无 tenant super admin 被拒绝。
- [ ] 所有写操作进入当前 revision/outbox 与审计。

## D. Snapshot V3 与 Gateway

- [ ] Contract V3 已增加 immutable key/group/grant/binding snapshot。
- [ ] Gateway 支持 V1/V2/V3。
- [ ] rollout 文档为 Gateway 先、Control Plane 后。
- [ ] V3 使用既有 manifest HMAC、payload checksum、immutable Redis payload/current pointer。
- [ ] gateway 不读取 ProviderCredential DB encryption key。
- [ ] Gateway local global key index 是 immutable/copy-on-write。
- [ ] Tenant 新 snapshot 成功时同时替换 tenant entries 和其 key entries。
- [ ] 单 tenant V3 无效时保留该 tenant 旧 snapshot/key entries，不影响其他 tenant。
- [ ] request authentication 不访问 DB 或 Redis。
- [ ] snapshot status 只返回安全计数。

## E. `/v1/models`

- [ ] 只新增 `GET /v1/models`。
- [ ] Gateway 使用 Client API Key，拒绝控制面 JWT/Cookie。
- [ ] 缺失/无效 key 返回统一 401 data-plane envelope。
- [ ] 有效 key 无授权时返回安全结果，不泄露其他 tenant 数据。
- [ ] model list 仅含有效 grant 且有效 static route plan 的 PublicModel。
- [ ] list response 为最小 OpenAI-compatible envelope。
- [ ] 未向上游发请求，未产生用量/账单。
- [ ] snapshot 未 ready/stale 时返回 503。

## F. 测试与交付

- [ ] key security/rotation/revoke/expire/redaction test 通过。
- [ ] role/tenant/grant union test 通过。
- [ ] V1/V2/V3 compatibility 与 malformed V3 last-known-good test 通过。
- [ ] multi-tenant global key index test 通过。
- [ ] `/v1/models` 401/403/503/empty/authorized test 通过。
- [ ] root backend `mvn test` 通过。
- [ ] 已填 `docs/ai-gateway/progress/phase-06-client-access-result.md`。
- [ ] 已创建中文 Git commit。

## 备注

- 实施日期：
- Git 提交：
- 关键测试命令：
- 遗留风险：
