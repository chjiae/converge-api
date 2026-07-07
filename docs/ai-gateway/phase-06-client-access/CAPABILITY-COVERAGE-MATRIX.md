# New-API / Sub2API 完整能力覆盖矩阵（阶段 06 更新）

> 这张表是每阶段规划前都必须复核的完整能力地图。  
> 其中“后续”不是忽略，而是避免在没有前置模型时过早实现。

| 能力域 | 能力项 | 当前状态 | 本阶段处理 | 后续位置 |
|---|---|---|---|---|
| 租户与管理 | 租户、角色、审计、Flyway | 既有系统 | 复用 | 持续 |
| 上游目录 | Provider、Connection、PublicModel | 阶段 02 | 不改 | 持续 |
| 上游密钥 | Credential、Direct Resource | 阶段 03 | 不改 | 持续 |
| 配置分发 | snapshot、outbox、Redis、gateway cache | 阶段 04 | 扩展 V3 | 持续 |
| 静态路由 | pool、binding、policy、priority/weight | 阶段 05 | 授权后复用 | 持续 |
| 下游身份 | Client API Key | 未实现 | **阶段 06** | 当前 |
| 消费授权 | AccessGroup、精确 model-operation grants | 未实现 | **阶段 06** | 当前 |
| 模型目录 | 已授权的 `/v1/models` | 未实现 | **阶段 06** | 当前 |
| 消费策略扩展 | key/user/plan group override、deny、wildcard | 未实现 | 不做 | 消费策略扩展 |
| 定价模型 | 模型价格、输入/输出/cache multiplier、resource cost | 未实现 | 不做 | 定价基础 |
| 财务与用量 | 预扣、结算、余额、账本、配额、退款、支付 | 既有订阅/支付不等于 AI 用量账本 | 不做 | AI 计费阶段 |
| 首个转发 | OpenAI Chat Completions、请求校验、SSE | 未实现 | 不做 | Direct API 首个端到端 |
| 动态调度 | concurrency、RPM/TPM、429、health、cooldown、lease | 未实现 | 不做 | 运行时调度 |
| 授权账户 | OAuth、refresh、cookie、account lifecycle | 未实现 | 不做 | 授权账户 |
| 会话 | sticky session、session window | 未实现 | 不做 | 会话调度 |
| 协议扩展 | Responses、Claude、Gemini、embeddings、tools、多模态 | 枚举/静态能力已预留 | 不做 | 协议阶段 |
| 运维 | metrics、alerts、gray、DR、audit dashboards | 部分既有 | 不做 | 可靠性运营 |
| 前端 | AI 管理台 / 用户 API Key 控制台 | 未实现 | 不改 | 后端模型稳定后 |

## 为什么阶段 06 先做 Client API Key 与授权

New-API 将 token grouping、model restrictions、用户级模型限流、计费和协议转发列为不同能力面；Sub2API 也将平台 API key 分发、账号调度、计费、并发和限流拆分。

本项目已经具备 static route plan，但还无法回答“哪个调用方可使用这条静态路线”。阶段 06 先补 Client Key 和 exact grant，能安全建立 GatewayClientPrincipal，再进入真实请求、计费和动态调度。
