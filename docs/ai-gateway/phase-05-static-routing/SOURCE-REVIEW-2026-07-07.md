# 阶段 05｜实时来源、现有实施与设计依据（2026-07-07）

## 1. 已完成阶段输入

阶段 04 实施结果确认：

- `converge-contract` 已作为纯 Java 契约模块存在；
- schema V1 已有 immutable payload、manifest HMAC、payload SHA-256、gateway secret envelope；
- 控制面有 tenant revision + transactional outbox；
- gateway 已有 Redis initial sync、Pub/Sub hint、periodic reconcile、copy-on-write、last-known-good 和 `/internal/snapshot-status`；
- V8 已存在；
- root `mvn test` 为 146 tests / 0 failures。

阶段 05 必须扩展这些现有结构，而不是另造快照、缓存或同步链路。

## 2. New-API 对照

当前 New-API FAQ 说明：

- Channel priority 越高越先使用；
- 同一 priority 的 Channel 按 weight 比例分配请求；
- “no available channel”排查至少涉及 user group、channel group、channel model settings。

Channel management 文档也将 models、groups、priority、weight、model mapping 集中在 channel 配置中。

此外，2026-02 的 global model alias / priority-weight issue 指出：若模型映射和路由优先级耦合在 Channel，会导致同一 Provider 为不同 public alias 创建重复 channel 的规模爆炸。

本项目的拆分对策：

```text
ExecutionResource：连接 + 凭据
ResourceModelBinding：精确 model mapping
ResourcePoolMember：资源组与成员 priority/weight
RouteTarget：pool 的 priority/weight
```

这样不复制 Connection/Credential，也不把未来 user group / multiplier 强塞进上游资源配置。

来源：
- https://github.com/QuantumNous/new-api-docs/blob/main/docs/en/support/faq.md
- https://github.com/QuantumNous/new-api-docs/blob/main/docs/api/fei-channel-management.md
- https://github.com/QuantumNous/new-api/issues/3001

## 3. Sub2API 对照

Sub2API 当前 README 列举：

- OAuth / API Key 多账户；
- API Key 下发；
- 精确 Token 计费；
- sticky session；
- 用户/账户并发；
- 请求 / Token rate limiting。

其 `Account` schema 与 service 进一步表明：

- Account 有 group 多对多关系；
- 有 priority、rate multiplier、concurrency / load factor；
- 有 model mapping；
- 运行时可调度性取决于 status、过期、rate limit、overload、temporary unschedulable、quota 等。

本阶段只实现前置静态结构：

```text
resource pool membership
exact model binding
priority / weight static candidates
```

不会错误把 runtime status、OAuth 生命周期、sticky session、并发与 rate limit 预先塞进 ResourcePool/RoutePolicy。

来源：
- https://github.com/Wei-Shaw/sub2api
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/backend/ent/schema/account.go
- https://raw.githubusercontent.com/Wei-Shaw/sub2api/main/backend/internal/service/account.go

## 4. 阶段结论

阶段 05 是从“资源目录”到“静态路由拓扑”的必要桥梁。

没有它：
- gateway 无法知道公开模型应使用哪些 Resource；
- 未来访问分组、价格/倍率、限流、会话粘性也没有可叠加的默认路线；
- 直接做 `/v1/*` 会重新把映射、路由与资源状态写死在 Handler 中。

本阶段结束后，后续阶段才能以 StaticRoutePlan 为输入，依次叠加消费者策略、动态资源可用性、真正 API 入口和协议适配。
