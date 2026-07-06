# 阶段 01｜验收清单

## 代码与模块

- [x] `backend/pom.xml` 已注册 `converge-gateway`。
- [x] `converge-gateway` 是独立可执行 Maven 模块。
- [x] 网关不依赖 `converge-web-service`、Spring、MyBatis、PostgreSQL、Redis、Servlet 或 `converge-common`。
- [x] 依赖版本通过 Vert.x BOM 管理，Vert.x 版本为 5.1.3。
- [x] 不新增 Flyway 迁移和业务表。

## HTTP 行为

- [x] `/internal/health` 返回 200 JSON。
- [x] `/internal/ready` 返回 200 JSON。
- [x] `/internal/version` 返回服务名、版本、Java/Vert.x 运行时信息和启动时间。
- [x] 不存在的路径返回统一 JSON 404。
- [x] 未处理异常返回统一 JSON 500，并带 requestId。
- [x] 合法 `X-Request-Id` 被响应回显。
- [x] 缺失 `X-Request-Id` 被安全生成；非法或过长 `X-Request-Id` 返回统一 JSON 400，并使用安全 requestId。

## 运行质量

- [x] 访问日志包含 requestId、method、path、status、durationMs。
- [x] 日志不记录 Authorization、Cookie、请求 Body 或其他秘密。
- [x] 应用支持优雅关闭，监听端口最终释放。
- [x] 端口、地址、关闭超时、服务名和版本可通过环境变量/系统属性配置。

## 验证与回归

- [x] `mvn -pl converge-gateway -am test` 通过。
- [x] `mvn -pl converge-gateway -am package` 通过。
- [x] 可执行 JAR 独立运行。
- [x] 三个内部接口、404、requestId、关闭行为都有自动化测试。
- [x] 现有 `converge-web-service` 代码和测试未被无关修改。

## 阶段边界

- [x] 没有 `/v1/*`。
- [x] 没有上游 HTTP 转发、SSE、WebSocket、API Key、JWT、OAuth、模型、资源池、路由、计费、Redis 快照或数据库访问。
- [x] 已填写阶段结果文件。
