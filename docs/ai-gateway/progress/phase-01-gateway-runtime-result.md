# 阶段 01｜独立 Vert.x 网关运行骨架｜实施结果

> 由 Codex 在阶段完成后填写。不要删除本模板中的章节；无内容时明确写“无”。

## 1. 实施日期与提交记录

- 实施日期：2026-07-06
- Git 提交：本文件随阶段提交一并提交，最终 Hash 见任务报告。
- 实施人员/Agent：Codex

## 2. 实施前代码审查结论

- 现有模块结构：`backend` 为 Maven 聚合工程，原有模块为 `converge-common` 与 `converge-web-service`；控制面入口仍为 Spring Boot `ConvergeApplication`，现有健康检查为 `/api/health`。
- 本阶段与现有代码的关键兼容点：`converge-gateway` 必须作为独立 JVM 进程运行，不复用 `converge-web-service`、`converge-common`、Spring Web、Spring Security、Servlet、MyBatis、Flyway、PostgreSQL、Redis、JWT 或 `TenantContext`。
- 发现的风险或调整：父 POM 原先把 Lombok 放在聚合级依赖中，会让新网关模块间接获得 Lombok；本阶段已将 Lombok 显式下沉到 `converge-common` 与 `converge-web-service`，避免网关依赖边界被污染。
- 发现的风险或调整：阶段文档中的验收项曾描述非法 `X-Request-Id` 可被替换；本次用户明确要求非法或过长 `X-Request-Id` 必须返回统一 JSON 错误，因此最终实现为缺失时生成、非法或过长时返回 400 JSON，并使用安全生成的 requestId 进入响应和日志。
- 发现的风险或调整：Spring Boot 父 POM 的资源过滤分隔符使用 `@...@`，网关版本资源已按该格式处理，避免 `/internal/version` 返回 Maven 占位符。

## 3. 实际修改内容

| 文件 | 修改类型 | 说明 |
|---|---|---|
| `backend/pom.xml` | 修改 | 注册 `converge-gateway` 模块，新增 `vertx.version=5.1.3`，移除聚合级 Lombok 依赖以保持网关依赖纯净。 |
| `backend/converge-common/pom.xml` | 修改 | 显式声明 Lombok `provided` 依赖，保持原有实体和 DTO 编译能力。 |
| `backend/converge-web-service/pom.xml` | 修改 | 显式声明 Lombok `provided` 依赖，保持原有控制面编译能力。 |
| `backend/converge-gateway/pom.xml` | 新增 | 新增独立 Maven 模块，使用 Vert.x BOM 5.1.3，配置可执行 JAR 与 Shade 打包。 |
| `backend/converge-gateway/README.md` | 新增 | 说明网关模块职责、运行方式、配置项和阶段边界。 |
| `backend/converge-gateway/src/main/resources/gateway-version.properties` | 新增 | 通过 Maven 资源过滤写入构建版本与 Vert.x 版本。 |
| `backend/converge-gateway/src/main/resources/logback.xml` | 新增 | 配置网关独立日志输出格式。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/GatewayApplication.java` | 新增 | 独立 JVM 启动入口，加载配置、启动运行时并注册关闭钩子。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/GatewayRuntime.java` | 新增 | 管理 Vert.x、HTTP Server 启动和关闭，支持端口释放验证。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/config/GatewayConfig.java` | 新增 | 从环境变量和系统属性加载端口、地址、关闭超时、服务名和版本。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/support/GatewayBuildInfo.java` | 新增 | 读取构建版本与 Vert.x 版本资源。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/GatewayRouterFactory.java` | 新增 | 注册 `/internal/health`、`/internal/ready`、`/internal/version`、404 和失败处理器。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/InternalStatusHandler.java` | 新增 | 实现内部健康、就绪和版本接口。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/RequestIdHandler.java` | 新增 | 实现 `X-Request-Id` 校验、生成、回写和非法请求 400 JSON。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/AccessLogHandler.java` | 新增 | 实现最小访问日志，记录 method、path、status、durationMs、requestId。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/GatewayErrorHandler.java` | 新增 | 实现 404、500 和非法 requestId 的统一 JSON 错误响应。 |
| `backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/GatewayJsonResponses.java` | 新增 | 统一成功和错误 JSON 响应结构。 |
| `backend/converge-gateway/src/test/java/com/github/chjiae/gateway/GatewayRuntimeTest.java` | 新增 | 覆盖内部接口、requestId、404、500、非法 requestId、配置和关闭释放端口。 |
| `docs/ai-gateway/phase-01-gateway-runtime/ACCEPTANCE-CHECKLIST.md` | 修改 | 按实际验收结果逐项勾选并记录 requestId 行为差异。 |
| `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md` | 修改 | 填写本阶段实施结果。 |

## 4. 未修改但已评估的文件

| 文件 | 未修改原因 |
|---|---|
| `backend/converge-web-service/src/main/java/com/github/chjiae/service/ConvergeApplication.java` | 控制面启动入口保持不变，网关不得嵌入现有 Spring Boot 应用。 |
| `backend/converge-web-service/src/test/java/com/github/chjiae/service/BaseIntegrationTest.java` | 现有集成测试框架保持不变，使用全量 `mvn test` 验证未回归。 |
| `backend/converge-web-service/src/test/java/com/github/chjiae/service/HealthCheckTest.java` | 现有 `/api/health` 属于控制面接口，本阶段不迁移、不重构。 |
| `backend/converge-web-service/src/main/resources/application.yml` | 网关配置独立通过环境变量和系统属性加载，不写入控制面 Spring 配置。 |
| `.gitignore` | 已覆盖 Maven `target/` 等构建产物，无需新增忽略项。 |

## 5. 验证结果

| 验证项 | 命令或方法 | 结果 |
|---|---|---|
| 网关模块测试 | `mvn -pl converge-gateway -am test` | 通过；最终自动化测试覆盖 11 项，失败 0、错误 0。 |
| 网关模块打包 | `mvn -pl converge-gateway -am package` | 通过；生成 `backend/converge-gateway/target/converge-gateway-1.0.0-SNAPSHOT.jar`。Shade 插件报告 `module-info.class` 与许可证/清单重叠警告，不影响可执行 JAR 产物。 |
| 禁止依赖检查 | `mvn -pl converge-gateway dependency:tree "-Dincludes=org.springframework,com.baomidou,org.postgresql,org.flywaydb,redis.clients,io.jsonwebtoken,jakarta.servlet,org.projectlombok"` | 通过；命令成功且没有输出匹配依赖条目。 |
| 全量后端回归 | `mvn test` | 通过；`converge-web-service` 87 项测试通过，`converge-gateway` 11 项测试通过。 |
| 可执行 JAR 清单 | `jar tf ...` 与读取 `META-INF/MANIFEST.MF` | 通过；包含 `Main-Class: com.github.chjiae.gateway.GatewayApplication`。 |
| health | 独立 JAR 启动后请求 `GET /internal/health` | 通过；HTTP 200。 |
| ready | 独立 JAR 启动后请求 `GET /internal/ready` | 通过；HTTP 200。 |
| version | 独立 JAR 启动后请求 `GET /internal/version` | 通过；HTTP 200，返回 `serviceName=converge-gateway-verify`、`buildVersion=1.0.0-SNAPSHOT`、`vertxVersion=5.1.3`、Java 版本和启动时间。 |
| 404 JSON | 独立 JAR 启动后请求 `GET /not-found` | 通过；HTTP 404，返回统一 JSON。 |
| requestId | 独立 JAR 启动后携带 `X-Request-Id: verify-20260706` 请求 `GET /internal/health` | 通过；响应头回显 `X-Request-Id: verify-20260706`。 |
| 非法 requestId | 独立 JAR 启动后携带 `X-Request-Id: bad request id` 请求 `GET /internal/health` | 通过；HTTP 400，返回统一 JSON，未回显非法输入。 |
| 优雅关闭 | `GatewayRuntimeTest` 关闭运行时并验证端口释放；独立 JAR 验证脚本停止后检查端口 | 通过；运行时关闭后端口释放，Vert.x 已关闭。 |
| 阶段边界扫描 | `rg -n "(/v1|Spring|springframework|mybatis|postgres|redis|jsonwebtoken|TenantContext|Servlet|servlet|Authorization|Cookie|password|token|body\\()" backend/converge-gateway` | 通过；未发现实际 `/v1` 路由或禁止技术实现，命中项为边界说明注释和测试断言。 |

## 6. 未执行项及原因

- 无。

## 7. 阶段边界复核

- 是否新增了 `/v1/*`：否。
- 是否新增数据库表/Flyway：否。
- 是否接入 Redis、PostgreSQL、Spring、MyBatis 或现有 TenantContext：否。
- 是否新增模型、账户、资源池、路由、计费、API Key：否。
- 结论：本阶段仅交付独立 Vert.x 网关运行骨架、内部运维接口、requestId、访问日志、统一错误响应、优雅关闭、可执行 JAR 和自动化测试，未越过阶段边界。

## 8. New-API / Sub2API 对照复核

- New-API 对照：本阶段只建立未来 New-API 兼容入口所需的独立网关进程、基础运维接口、requestId、访问日志和统一错误结构；未实现 `/v1/chat/completions`、上游 Provider、模型、Credential、API Key、计费或协议转换。
- Sub2API 对照：本阶段只建立未来 Sub2API 订阅转发入口的运行底座；未实现订阅路由、账号池、资源池、下游密钥、用量统计、SSE、WebSocket 或重试。
- 是否存在偏离：不存在越界实现；非法 `X-Request-Id` 行为按用户最新要求采用 400 JSON，而不是简单替换后继续处理。

## 9. 遗留问题与下一阶段输入

- 遗留问题：无阶段内未完成项。
- 对下一阶段的影响：下一阶段可以在该独立网关模块内继续添加受控路由和上游调用能力，不需要改造控制面启动方式。
- 建议下一阶段讨论项：正式引入 `/v1/*` 前应先确定认证边界、租户授权来源、上游 Provider 配置同步方式、敏感日志脱敏策略和流式响应处理规范。
