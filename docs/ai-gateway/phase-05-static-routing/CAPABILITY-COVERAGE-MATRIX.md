# New-API / Sub2API 完整能力覆盖矩阵（阶段 05 更新）

> 本文件不是当前阶段范围清单，而是后续规划必须持续核对的能力地图。  
> “后续”不代表忽略；每个阶段生成前应重新查仓库实现、当前版本与上阶段结果。

| 能力域 | 能力项 | New-API / Sub2API 参考方向 | 当前状态 | 合理后续位置 |
|---|---|---|---|---|
| 租户与控制面 | 租户、角色、审计、Flyway | 两者均有管理平面 | 已有既有项目复用 | 持续 |
| 上游目录 | Provider、Connection、PublicModel | New-API Channel 的拆分 | 阶段 02 已完成 | 持续 |
| 密钥安全 | API Key 加密、掩码、轮换、去重 | Channel Key / Account Credential | 阶段 03 已完成 | 持续 |
| 静态资源 | Direct API ExecutionResource | New-API Channel / Sub2API Account 的拆分 | 阶段 03 已完成 | 持续 |
| 配置分发 | 版本化快照、Outbox、Redis、网关缓存 | 多实例运行时配置 | 阶段 04 已完成 | 持续 |
| 上游资源组 | ResourcePool、PoolMember | channel group / account group | **阶段 05** | 当前 |
| 模型能力 | PublicModel → upstream model 精确映射 | model mapping / account mapping | **阶段 05** | 当前 |
| 静态路由 | route target、priority、weight、主备静态候选 | channel priority / weight | **阶段 05** | 当前 |
| 路由扩展 | 访问组路由覆盖、条件路由、wildcard mapping、成本优选 | New-API alias / model mapping | 后续 | 消费策略与高级路由 |
| 下游身份 | Client API Key、密钥轮换、权限 | Token/API Key distribution | 后续 | 消费者鉴权阶段 |
| 消费分组 | AccessGroup、用户/API Key/套餐映射、模型可见性 | user group | 后续 | 消费者策略阶段 |
| 商业策略 | 价格、输入/输出倍率、缓存倍率、组倍率、资源成本、折扣 | New-API multiplier / Sub2API rate multiplier | 后续 | 定价计费阶段 |
| 用量财务 | 预扣、结算、退款、账本、配额、支付 | New-API billing / Sub2API usage | 后续 | 计费阶段 |
| 协议入口 | OpenAI/Claude/Gemini/Responses 等 | 两项目多协议 | 后续 | 首个数据面协议阶段 |
| 实际转发 | HTTP、SSE、失败前重试、错误映射 | Gateway forwarding | 后续 | 首个 Direct API 端到端阶段 |
| 动态调度 | 并发、RPM/TPM、429、过载、健康、限流、租约 | Sub2API scheduling | 后续 | 运行时调度阶段 |
| 会话 | sticky session、会话窗口、会话迁移 | Sub2API | 后续 | 会话阶段 |
| 授权账户 | OAuth、Refresh Token、企业/席位授权账户 | Sub2API multi-account | 后续 | 授权账户阶段 |
| 高级协议 | Tool calling、JSON schema、图片/音频/视频、异步任务、Realtime | New-API protocol coverage | 后续 | 协议扩展阶段 |
| 运维 | 探测、余额/配额刷新、告警、报表、灰度、备份、恢复 | 两者运行维护 | 后续 | 可靠性与运营阶段 |
| 前端 | AI 控制台、用户控制台、运行状态页面 | 两者后台能力 | 后续 | 后端模型稳定后 |

## 阶段 05 的边界判断

阶段 05 只实现“上游静态拓扑与静态路由”，不实现消费者权益、价格、运行时动态调度或协议入口。

这不是遗漏：  
- 没有 Client API Key 和 AccessGroup，无法正确设计用户分组/模型权限；  
- 没有用量与账本，无法正确设计倍率/价格/结算；  
- 没有实际请求生命周期，无法正确设计并发/限流/429/粘性会话；  
- 没有协议能力矩阵，无法安全做 wildcard/条件模型映射。  
