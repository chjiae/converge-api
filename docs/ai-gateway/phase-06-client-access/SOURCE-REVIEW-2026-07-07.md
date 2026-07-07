# 阶段 06｜实时来源、仓库状态与设计依据（2026-07-07）

## 1. 当前仓库事实

仓库：`chjiae/converge-api`  
分支：`master`

已确认阶段 05 提交：

```text
727e535 feat(AI网关): 实现静态路由快照 V2
```

阶段 05 实施结果确认：

- V9 已建立 ResourcePool / Member / ResourceModelBinding / RoutePolicy / Target；
- `converge-routing-core` 是纯 Java 模块；
- static route plan 不含 API key、密文、nonce、HMAC 或 Redis key；
- Gateway 支持 V1/V2 snapshot、V2 编译 static route plan；
- 未实现 ClientApiKey / AccessGroup / 套餐、价格/倍率/计费、OAuth、动态限流或实际 `/v1/*`；
- root `mvn test` 已通过。

阶段 06 必须在既有 V2 snapshot、revision/outbox、Redis immutable payload、Gateway last-known-good 之上扩展 V3，不得新建平行同步机制。

## 2. New-API 对照

New-API 当前 README 明确包含：

```text
Token grouping
Model restrictions
User management
User-level model rate limiting
usage-based accounting
multiple API formats
weighted routing and retry
```

本阶段只吸收其“下游 token 分组 + 模型限制”的结构方向，拆为：

```text
ClientApiKey
AccessGroup
AccessGroupModelGrant
ClientApiKeyAccessGroup
```

不复制其源代码，也不把价格、用户限流、路由、协议转换混进 Key/Group 实体。

## 3. Sub2API 对照

Sub2API 当前 README 明确列举：

```text
API Key distribution
authentication
billing
load balancing
request forwarding
account selection with sticky sessions
per-user/per-account concurrency
request/token rate limiting
```

本阶段只建立最小 `Client API Key → tenant → model grant` 链路。未接入 OAuth / account pools / subscription redistribution / sticky session，也不做商业授权之外的上游账户使用。

## 4. Vert.x 与安全设计依据

Vert.x Core 文档说明 HTTP server 可读取请求 header，响应写入是异步的；HTTP client 和 streaming 能力用于后续真正转发阶段，本阶段不引入 HttpClient。

OWASP Secrets Management Cheat Sheet 提醒：secret rotation、访问与 logging/accounting 必须被明确设计。阶段 06 因此采用：

```text
server-generated high entropy key
one-time raw secret exposure
database hash verifier
redacted logs and audit
rotate/revoke lifecycle
```

## 5. 结论

阶段 06 是从“静态路由存在”到“安全地对外暴露模型目录”的必要桥梁：

```text
StaticRoutePlan
  + ClientKey principal
  + exact model/operation grant
  = authorized model inventory
```

阶段 07 应在阶段 06 实际结果基础上选择：
- 先做 OpenAI-compatible Chat Completions 最小端到端转发；或
- 先做 AI 用量/预扣/定价基础。

不得预先固定，取决于阶段 06 的 key/snapshot/interface 实现细节。
