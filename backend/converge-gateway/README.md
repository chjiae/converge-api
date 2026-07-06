# Converge Gateway

`converge-gateway` 是独立 Vert.x 数据面运行时。当前提供内部运维接口、请求 ID、访问日志、统一错误响应、优雅关闭，以及从 Redis 加载控制面安全配置快照的本地只读运行态；仍不实现任何 AI API 中转能力。

## 启动

```bash
cd backend
mvn -pl converge-gateway -am package
java -jar converge-gateway/target/converge-gateway-1.0.0-SNAPSHOT.jar
```

默认监听 `0.0.0.0:8081`。

## 配置

支持环境变量和 JVM 系统属性：

| 环境变量 | 系统属性 | 默认值 | 说明 |
|---|---|---|---|
| `GATEWAY_HOST` | `gateway.host` | `0.0.0.0` | HTTP 监听地址 |
| `GATEWAY_PORT` | `gateway.port` | `8081` | HTTP 监听端口 |
| `GATEWAY_SHUTDOWN_TIMEOUT_MS` | `gateway.shutdown-timeout-ms` | `10000` | 优雅关闭超时时间 |
| `GATEWAY_SERVICE_NAME` | `gateway.service-name` | `converge-gateway` | 服务名称 |
| `GATEWAY_BUILD_VERSION` | `gateway.build-version` | Maven 项目版本 | 构建版本 |
| `GATEWAY_REDIS_URI` | `gateway.redis-uri` | 无 | Redis 连接地址，缺失时启动失败 |
| `AI_GATEWAY_SNAPSHOT_KEY_ID` | `ai.gateway.snapshot.key-id` | 无 | 网关快照投递密钥版本 |
| `AI_GATEWAY_SNAPSHOT_ENCRYPTION_KEY_BASE64` | `ai.gateway.snapshot.encryption-key-base64` | 无 | 网关快照 AES-256-GCM 投递密钥，必须为 32 字节 Base64 |
| `AI_GATEWAY_SNAPSHOT_SIGNING_KEY_BASE64` | `ai.gateway.snapshot.signing-key-base64` | 无 | 网关快照 HMAC-SHA256 签名密钥，至少 32 字节 Base64 |
| `GATEWAY_SNAPSHOT_RECONCILE_INTERVAL_MS` | `gateway.snapshot.reconcile-interval-ms` | `30000` | 周期对账间隔 |
| `GATEWAY_SNAPSHOT_MAX_STALENESS_MS` | `gateway.snapshot.max-staleness-ms` | `120000` | 允许使用 last-known-good 快照的最大陈旧时间 |
| `GATEWAY_SNAPSHOT_HISTORY_RETAIN_COUNT` | `gateway.snapshot.history-retain-count` | `3` | 与控制面一致的历史保留数量 |

网关只读取投递密钥，不读取阶段 03 的数据库凭据加密密钥。非测试环境必须显式配置上述 Redis 与快照密钥，缺失或格式错误会在启动阶段失败，不会退化为明文。

## 快照同步

控制面把租户快照发布到 Redis，网关通过初始对账、Pub/Sub 提示和周期对账加载快照。Pub/Sub 只用于加速刷新；即使漏消息，周期对账仍会恢复到当前 manifest 指向的版本。

Redis key 由共享契约统一生成：

| 用途 | Key 模板 |
|---|---|
| 租户索引 | `converge:gateway:snapshot:tenant-index` |
| immutable payload | `converge:gateway:snapshot:tenant:{tenantId}:revision:{revision}` |
| current manifest | `converge:gateway:snapshot:tenant:{tenantId}:current` |
| 历史版本 | `converge:gateway:snapshot:tenant:{tenantId}:history` |
| 变更通知频道 | `converge:gateway:snapshot:changed` |

网关加载时会校验 manifest HMAC、payload SHA-256、schema、租户、revision、投递密钥版本和每个 SecretEnvelope 的 AES-GCM AAD。校验失败时拒绝新版本并保留 last-known-good；单租户失败不会覆盖其他租户已验证的快照。

## 内部接口

这些接口本阶段不做鉴权，必须部署在内部网络或受反向代理保护的环境中。

```bash
curl -i http://127.0.0.1:8081/internal/health
curl -i http://127.0.0.1:8081/internal/ready
curl -i http://127.0.0.1:8081/internal/version
curl -i http://127.0.0.1:8081/internal/snapshot-status
curl -i -H "X-Request-Id: verify-20260706" http://127.0.0.1:8081/internal/health
```

`/internal/health` 只表示进程存活，不受 Redis 状态影响。`/internal/ready` 表示快照运行态是否已完成首次安全对账；空租户索引视为就绪，Redis 临时失败且未超过最大陈旧时间时返回降级就绪。`/internal/snapshot-status` 只暴露状态、计数和 tenant/revision 摘要，不暴露 Redis key、API Key、密文、nonce、HMAC 或 payload。

## 测试

```bash
cd backend
mvn -pl converge-gateway -am test
```
