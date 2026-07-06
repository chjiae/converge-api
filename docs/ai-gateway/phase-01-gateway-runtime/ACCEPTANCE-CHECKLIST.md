# 阶段 01｜验收清单

## 代码与模块

- [ ] `backend/pom.xml` 已注册 `converge-gateway`。
- [ ] `converge-gateway` 是独立可执行 Maven 模块。
- [ ] 网关不依赖 `converge-web-service`、Spring、MyBatis、PostgreSQL、Redis、Servlet 或 `converge-common`。
- [ ] 依赖版本通过 Vert.x BOM 管理，Vert.x 版本为 5.1.3。
- [ ] 不新增 Flyway 迁移和业务表。

## HTTP 行为

- [ ] `/internal/health` 返回 200 JSON。
- [ ] `/internal/ready` 返回 200 JSON。
- [ ] `/internal/version` 返回服务名、版本、Java/Vert.x 运行时信息和启动时间。
- [ ] 不存在的路径返回统一 JSON 404。
- [ ] 未处理异常返回统一 JSON 500，并带 requestId。
- [ ] 合法 `X-Request-Id` 被响应回显。
- [ ] 缺失/非法 `X-Request-Id` 被安全替换。

## 运行质量

- [ ] 访问日志包含 requestId、method、path、status、durationMs。
- [ ] 日志不记录 Authorization、Cookie、请求 Body 或其他秘密。
- [ ] 应用支持优雅关闭，监听端口最终释放。
- [ ] 端口、地址、关闭超时、服务名和版本可通过环境变量/系统属性配置。

## 验证与回归

- [ ] `mvn -pl converge-gateway -am test` 通过。
- [ ] `mvn -pl converge-gateway -am package` 通过。
- [ ] 可执行 JAR 独立运行。
- [ ] 三个内部接口、404、requestId、关闭行为都有自动化测试。
- [ ] 现有 `converge-web-service` 代码和测试未被无关修改。

## 阶段边界

- [ ] 没有 `/v1/*`。
- [ ] 没有上游 HTTP 转发、SSE、WebSocket、API Key、JWT、OAuth、模型、资源池、路由、计费、Redis 快照或数据库访问。
- [ ] 已填写阶段结果文件。
