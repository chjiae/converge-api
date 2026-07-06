# Converge Gateway

`converge-gateway` 是独立 Vert.x 数据面运行时。本阶段只提供内部运维接口、请求 ID、访问日志、统一错误响应和优雅关闭，不实现任何 AI API 中转能力。

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

## 内部接口

这些接口本阶段不做鉴权，必须部署在内部网络或受反向代理保护的环境中。

```bash
curl -i http://127.0.0.1:8081/internal/health
curl -i http://127.0.0.1:8081/internal/ready
curl -i http://127.0.0.1:8081/internal/version
curl -i -H "X-Request-Id: verify-20260706" http://127.0.0.1:8081/internal/health
```

## 测试

```bash
cd backend
mvn -pl converge-gateway -am test
```
