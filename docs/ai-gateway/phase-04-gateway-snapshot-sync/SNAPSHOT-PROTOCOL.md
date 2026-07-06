# 阶段 04｜快照协议与 Redis 键约定

## 1. 目标

本文件固定控制面与网关之间的最小运行时协议，避免后续资源池、路由、模型映射、分组、倍率等能力把快照结构推倒重来。

本阶段快照只表达：

```text
tenant
public model directory
direct execution resources
connection metadata
encrypted upstream secret envelope
```

它**不表达**模型到资源路由，也不表达用户、套餐、分组、价格、倍率、限流、账户池或粘性会话。

---

## 2. Redis 键

```text
converge:gateway:snapshot:tenant-index
```

类型：Redis Set。  
含义：存在快照状态的 tenant ID 集合。  
网关启动和周期性对账时读取。

```text
converge:gateway:snapshot:tenant:{tenantId}:revision:{revision}
```

类型：Redis String / bytes。  
含义：不可变 `GatewayTenantSnapshot` JSON payload。  
规则：仅使用 `SET ... NX` 写入，不得原地更新。

```text
converge:gateway:snapshot:tenant:{tenantId}:current
```

类型：Redis String / JSON。  
含义：当前 `GatewaySnapshotManifest`。  
规则：这是唯一允许更新的 tenant 当前指针。

```text
converge:gateway:snapshot:tenant:{tenantId}:history
```

类型：Redis ZSet。  
含义：已发布 revision 历史，score 为 revision。  
规则：默认保留 current + 最近 2 个旧版本。删除旧 payload 前必须保证它不是 current 指向版本。

```text
converge:gateway:snapshot:changed
```

类型：Redis Pub/Sub channel。  
含义：低延迟刷新提示。  
规则：消息丢失不影响最终正确性；网关必须通过对账补偿。

---

## 3. Payload 与 Manifest

### `GatewayTenantSnapshot`

```json
{
  "schemaVersion": 1,
  "tenantId": "tenant-uuid",
  "revision": 42,
  "generatedAtEpochMillis": 1783390000000,
  "publicModels": [],
  "executionResources": []
}
```

### `GatewaySnapshotManifest`

```json
{
  "schemaVersion": 1,
  "tenantId": "tenant-uuid",
  "revision": 42,
  "payloadRedisKey": "converge:gateway:snapshot:tenant:tenant-uuid:revision:42",
  "payloadSha256Hex": "lowercase-hex",
  "manifestHmacBase64": "base64",
  "gatewayKeyId": "gateway-key-2026-07",
  "publishedAtEpochMillis": 1783390000000
}
```

### `GatewaySnapshotChangedEvent`

```json
{
  "schemaVersion": 1,
  "tenantId": "tenant-uuid",
  "revision": 42,
  "manifestRedisKey": "converge:gateway:snapshot:tenant:tenant-uuid:current",
  "publishedAtEpochMillis": 1783390000000
}
```

Pub/Sub message只能提示“需要刷新”；网关必须重新读取 Redis manifest 和 payload，不能直接信任消息负载。

---

## 4. HMAC 签名输入

`manifestHmacBase64` 使用网关签名密钥进行 HMAC-SHA-256。

签名输入必须是固定 UTF-8 字节序列：

```text
schemaVersion
tenantId
revision
payloadRedisKey
payloadSha256Hex
gatewayKeyId
```

字段之间应使用不会出现在 UUID、整数、hex、key ID 中的明确分隔方式，或采用固定长度编码。两端必须使用同一实现，并有测试。

不允许：

```text
只对 payload 做 SHA-256
只签 tenantId + revision
使用 JSON 字段自然序列化作为签名输入
```

原因：JSON 字段顺序、空字段表达和格式化细节不稳定；签名输入必须显式且可预测。

---

## 5. `GatewaySecretEnvelope`

每个 Direct API resource 产生独立 envelope：

```json
{
  "keyId": "gateway-key-2026-07",
  "algorithm": "AES-256-GCM",
  "nonceBase64": "base64-12-byte-nonce",
  "ciphertextBase64": "base64-ciphertext-with-tag"
}
```

网关使用以下 AAD 解封装：

```text
schemaVersion
tenantId
executionResourceId
credentialId
snapshotRevision
gatewayKeyId
```

控制面和网关必须共用同一个 `GatewaySnapshotAadCodec` 或严格等价实现，且使用跨模块兼容性测试覆盖。

---

## 6. 发布与接收时序

### 控制面发布

```text
DB transaction:
  mutation
  revision++
  outbox append
commit

async projector:
  build snapshot
  serialize bytes
  sha256
  build signed manifest
  SET payload NX
  SET current manifest
  SADD tenant-index tenantId
  ZADD history revision
  prune stale immutable payloads
  PUBLISH changed event
  mark outbox published
```

### 网关接收

```text
startup / periodic reconciliation:
  SMEMBERS tenant-index
  for each tenant:
    GET current manifest
    verify manifest hmac + schema
    GET payload key
    verify sha256
    parse snapshot
    validate tenant/revision/schema
    decrypt all required secret envelopes
    atomically replace tenant local snapshot
```

### 失败原则

```text
新版本验证失败：
  保留旧本地版本
  记录无秘密错误类别
  ready 依据首次同步与最大陈旧时间计算

Redis Pub/Sub 漏消息：
  周期性对账补偿

Redis 失效：
  控制面 Outbox 重试 / 周期性重投影恢复
  网关保留最后有效本地 snapshot，直到超过 max-staleness
```
