# AI Gateway 完整能力覆盖矩阵｜阶段 07 更新

> 每个阶段开始前必须复核本表。  
> “未实现”不等于忽略，只表示当前阶段不应提前混入该能力。

| 能力域 | 能力项 | 当前状态 | 阶段 07 是否处理 | 后续阶段 |
|---|---|---|---|---|
| 租户与管理 | Tenant、User、Role、Audit、Flyway | 已有 | 复用 | 持续 |
| 上游目录 | Provider、Connection、PublicModel | 阶段 02 | 复用 | 持续 |
| 上游凭据 | Credential、AES-GCM、Direct Resource | 阶段 03 | 使用已下发 runtime secret | OAuth 阶段 |
| 快照分发 | revision、outbox、Redis、HMAC、checksum、LKG | 阶段 04 | 复用 | 持续 |
| 静态路由 | Pool、Binding、Policy、priority、weight | 阶段 05 | 真实请求选择 | 动态调度 |
| 下游身份 | Client API Key | 阶段 06 | 复用 | 持续 |
| 消费授权 | AccessGroup、精确 Model + Operation Grant | 阶段 06 | CHAT_COMPLETIONS 实际校验 | 扩展策略 |
| 模型目录 | GET /v1/models | 阶段 06 | 保持 | 持续 |
| 首个真实转发 | OpenAI Chat Completions 非流式 | 未实现 | 是 | 当前 |
| 首个真实转发 | OpenAI Chat Completions SSE | 未实现 | 是 | 当前 |
| 模型映射 | Public Model ↔ Upstream Model | 静态绑定已具备 | 是 | 当前 |
| 上游连接 | HttpClient、TLS、timeout、连接池 | 未实现 | 是 | 当前 |
| SSE 模型隔离 | Stream event model 重写 | 未实现 | 是 | 当前 |
| 上游错误安全化 | 不泄露 URL、资源、凭据 | 未实现 | 是 | 当前 |
| 动态调度 | health、concurrency、RPM、TPM、429 cooldown | 未实现 | 否 | 后续 |
| 失败治理 | retry、fallback、circuit breaker | 未实现 | 否 | 后续 |
| 计费 | token usage、价格、倍率、预扣、结算 | 未实现 | 否 | 后续 |
| 财务 | 余额、账本、退款、支付 | 既有订阅能力不等于 AI 计费 | 否 | 后续 |
| 协议扩展 | Responses、Embeddings、Claude、Gemini | 枚举已预留 | 否 | 后续 |
| 授权账户 | OAuth、Refresh Token、Cookie | 未实现 | 否 | 后续 |
| 账号池 | account group、sticky session | 未实现 | 否 | 后续 |
| 前端 | AI 管理台、用户 API Key 控制台 | 未实现 | 否 | 后端稳定后 |
| 运维 | 指标、告警、灰度、灾备 | 基础健康接口已有 | 否 | 可靠性阶段 |

## 阶段 07 的边界

阶段 07 验证的最小闭环：

```text
安全 Client Key
+ 精确授权
+ 静态路由
+ 上游 runtime secret
+ OpenAI Compatible HTTP
+ JSON / SSE
= 首个真实可调用模型接口
```

以下能力必须延后：

```text
retry
fallback
动态可用性
并发控制
限流
计费
账号池
协议转换
前端
```

不得将静态模型、动态状态、财务账本、协议语义和资源调度混入同一阶段。