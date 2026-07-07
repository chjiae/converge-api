# 阶段 05｜静态路由协议与编译规则

## 1. 路由键

本阶段静态路由唯一键：

```text
tenantId + publicModelCode + canonicalOperation
```

不使用：

```text
用户 ID
下游 API Key
套餐
访问组
价格
倍率
会话 ID
实时并发
余额
上游错误状态
```

这些维度会在后续消费者策略和运行时调度阶段叠加。

---

## 2. 配置拓扑

```text
PublicModel(code = claude-sonnet)
   │
   ├─ ResourceModelBinding
   │      Resource A + CHAT_COMPLETIONS → claude-sonnet-4-20250514
   │
   └─ RoutePolicy(CHAT_COMPLETIONS)
          │
          ├─ RouteTarget(primary-pool, priority=100, weight=100)
          │      ├─ PoolMember(Resource A, priority=100, weight=70)
          │      └─ PoolMember(Resource B, priority=100, weight=30)
          │
          └─ RouteTarget(fallback-pool, priority=50, weight=100)
                 └─ PoolMember(Resource C, priority=100, weight=100)
```

任何具体 Resource 成为候选需要同时满足：

```text
PublicModel ENABLED
RoutePolicy ENABLED
RouteTarget ENABLED
ResourcePool ENABLED
ResourcePoolMember ENABLED
ExecutionResource ENABLED
AiResourceModelBinding ENABLED and exact match
AiProvider ENABLED
AiUpstreamConnection ENABLED
AiCredential ENABLED
```

`ExecutionResource=DRAINING` 不视为新请求候选。

---

## 3. 静态优先级和权重

### RouteTarget 层

```text
1. 取最大 priority 的所有有效 target
2. 同一 priority 内按 weight 提供候选比例
3. 若该 priority 层没有任何有效 target，才尝试下一 priority 层
```

### PoolMember 层

```text
1. 在已选 pool 内，取最大 priority 的有效 member
2. 同一 priority 内按 weight 提供候选比例
3. 若该 priority 层没有有效 member，才尝试下一 priority 层
```

合法值：

```text
priority: -100000 至 100000 的整数
weight: 1 至 100000 的整数
```

不得使用浮点权重。

---

## 4. StaticRoutePlan

`converge-routing-core` 生成不可变 `StaticRoutePlan`，至少包含：

```text
tenantId
publicModelCode
canonicalOperation
policyId
snapshotRevision
validationStatus
routeTargetTiers
```

每个 RouteTarget tier 包含：

```text
priority
pools[]
  - poolId
  - poolCode
  - weight
  - memberTiers[]
      - priority
      - resources[]
          - executionResourceId
          - connectionId
          - providerId
          - protocolType
          - baseUrl
          - upstreamModelName
          - weight
```

该 Plan 只包含运行时安全元数据；不得包含：

```text
API Key 明文
GatewaySecretEnvelope 原始密文或 nonce
Redis key
Manifest HMAC
数据库密钥
审计信息
```

运行时真正需要 API Key 时，仍从已验证的 tenant runtime snapshot 内部资源对象取值，route plan 只引用 Resource ID。

---

## 5. Route Preview

控制面 `POST /api/v1/ai/routes/preview` 使用 routing core。

输入：

```json
{
  "publicModelCode": "claude-sonnet",
  "canonicalOperation": "CHAT_COMPLETIONS",
  "selectionSeed": "optional-deterministic-seed"
}
```

输出必须包括：

```text
validationStatus
candidateTiers
sampledPool
sampledResource
upstreamModelName
warnings
```

固定警告：

```text
STATIC_CONFIGURATION_ONLY
DYNAMIC_STATE_NOT_APPLIED
NO_UPSTREAM_REQUEST_EXECUTED
```

不得展示 Credential 或任何秘密。

---

## 6. Snapshot schema V2

V2 在 V1 基础上追加：

```text
resourcePools
resourceModelBindings
routePolicies
```

V2 payload 包含 PublicModel、ExecutionResource 与 route topology，但不包含业务 HTTP handler 和请求参数转换。

Gateway 加载 V2：

```text
manifest auth -> payload checksum -> schema validation
-> secret envelope validation -> static route compile
-> atomic tenant snapshot swap
```

任何一个步骤失败都不得替换当前 tenant 的 last-known-good snapshot。

---

## 7. 发布升级顺序

```text
1. 部署支持 V1/V2 的 gateway
2. 验证所有 gateway 的 snapshot-status
3. 部署产生 V2 的 control-plane
4. 触发或等待 outbox reproject
5. 验证 gateway 已加载 V2 与 compiled route plan
6. 才允许后续引入实际 /v1 请求入口
```

禁止：

```text
先部署只产生 V2 的 control-plane，
再让仍只支持 V1 的旧 gateway 读取。
```
