# 阶段 01｜独立 Vert.x 网关运行骨架

## 1. 阶段定位

本阶段在现有 Converge API 项目上新增一个**独立运行的 Vert.x 数据面模块**：`converge-gateway`。

它是未来 API 中转服务的运行基础，但在本阶段仍然只是一个空网关：可独立启动、可被监控、可安全停止、可追踪请求、可自动测试。

现有 `converge-web-service` 继续作为 Spring Boot MVC 控制面，现有租户、用户、角色、订阅、支付、卡密、通知、审计功能不得被重写或迁移。

## 2. 本阶段范围

### 2.1 必须完成

- 在 `backend/pom.xml` 中新增 `converge-gateway` Maven 子模块。
- 新增完全独立的 Vert.x 应用入口，不使用 Spring Boot、不嵌入 `converge-web-service`。
- 使用 Vert.x 5.1.3 BOM 管理 Vert.x 依赖版本。
- 提供以下仅供内部运维使用的 HTTP 接口：
  - `GET /internal/health`：进程存活检查；只代表 HTTP 进程已经启动。
  - `GET /internal/ready`：就绪检查；本阶段不依赖 PostgreSQL、Redis 或上游，因此成功启动即就绪。
  - `GET /internal/version`：返回服务名、构建版本、运行时版本、启动时间。
- 提供独立配置：监听地址、端口、优雅关闭超时、服务名、构建版本。
- 支持 `X-Request-Id`：合法值透传，不合法或缺失时生成；在响应头和日志中返回。
- 为网关请求写入最小访问日志：方法、路径、状态码、耗时、requestId；禁止记录请求 Body、Authorization、Cookie 或其他敏感 Header。
- 未匹配路由返回统一 JSON 404；未处理异常返回统一 JSON 500，并保留 `requestId`。
- 使用 Maven Shade Plugin 打出可独立执行的 JAR。
- 添加 Vert.x JUnit 5 测试，覆盖启动、三个内部接口、404、requestId 回显和优雅关闭。
- 新增阶段结果文件模板；Codex 完成后必须填写。

### 2.2 明确不做

- 不新增 `/v1/*`、`/api/*` 之外的任何对外 AI 调用接口。
- 不实现 API 转发、SSE、WebSocket、上游 HTTP Client、失败重试、超时策略、协议转换。
- 不接入 PostgreSQL、Flyway、MyBatis、Redis、Spring Security、JWT、现有 `TenantContext`。
- 不新增 Provider、PublicModel、Credential、AuthorizedAccount、ResourcePool、路由、计费、下游 API Key 等业务对象或数据库表。
- 不创建 `converge-contract`；共享契约应在控制面与网关存在真实交互需求后再抽取，避免提前抽象。
- 不修改前端。
- 不改变 `converge-web-service` 的端口、现有健康接口、Controller、业务逻辑或数据库迁移。

## 3. 为什么这一阶段先做网关骨架

现有项目只有 Spring MVC 控制面。未来模型中转需承载长连接、流式响应、请求背压、上游连接池和高频路由；这些不应塞入当前业务控制面。

Vert.x 的事件循环要求处理器不能阻塞，因此独立进程可以将未来高频 API 路径与当前 Spring MVC、MyBatis、`ThreadLocal` 租户上下文隔离。本阶段不接入任何阻塞式依赖，先将运行边界固定下来。

## 4. 目标目录与模块边界

完成后目录应至少包含：

```text
backend/
├─ converge-common/              # 现有模块，不改职责
├─ converge-web-service/         # 现有 Spring MVC 控制面，不改职责
└─ converge-gateway/             # 新增：独立 Vert.x 数据面
   ├─ pom.xml
   └─ src/
      ├─ main/java/com/github/chjiae/gateway/
      ├─ main/resources/
      └─ test/java/com/github/chjiae/gateway/
```

### 网关依赖约束

`converge-gateway` 可以依赖：

- JDK；
- Vert.x Core、Vert.x Web；
- SLF4J / Logback；
- 必要的测试依赖。

`converge-gateway` 禁止依赖：

- `converge-web-service`；
- Spring Boot、Spring MVC、Spring Security；
- MyBatis、Flyway、PostgreSQL JDBC；
- Redis 客户端；
- Servlet API；
- 现有 `converge-common`（其当前包含 Spring Web 与 MyBatis 注解依赖）；
- 现有 `TenantContext`。

## 5. 实施工作包

本阶段是一个完整交付，内部包含以下紧密相关的工作包。Codex 可以在一次实施中完成，但必须按顺序实施和验证。

### WP-01：Maven 模块与可执行包

1. 父 POM 增加 `converge-gateway` 模块。
2. 父 POM 新增 `vertx.version` 属性，值为 `5.1.3`。
3. `converge-gateway/pom.xml` 使用 `io.vertx:vertx-stack-depchain` BOM。
4. 添加 `vertx-core`、`vertx-web`、`vertx-junit5`、测试 HTTP 客户端依赖。
5. 使用 Maven Shade Plugin 生成可执行 JAR，Manifest 主类为网关应用入口。
6. 保持现有模块依赖与构建行为不变。

### WP-02：网关应用入口与配置

1. 新增 `GatewayApplication`，由 `public static void main(String[] args)` 启动 Vert.x。
2. 新增不可变 `GatewayConfig`，从环境变量和系统属性读取配置，不引入 Spring 配置体系。
3. 建议配置项：
   - `GATEWAY_HOST`，默认 `0.0.0.0`；
   - `GATEWAY_PORT`，默认 `8081`；
   - `GATEWAY_SHUTDOWN_TIMEOUT_MS`，默认 `10000`；
   - `GATEWAY_SERVICE_NAME`，默认 `converge-gateway`；
   - `GATEWAY_BUILD_VERSION`，默认 Maven 项目版本。
4. 端口或超时配置非法时必须在启动期失败并输出清晰中文日志。
5. 注册 JVM shutdown hook，先停止接收新连接，再在超时内关闭 Vert.x。

### WP-03：内部运维路由与统一错误响应

1. 使用 `Router` 注册内部路由。
2. 三个接口只返回不含敏感信息的 JSON。
3. 统一 JSON 响应至少包含：`code`、`message`、`requestId`、`timestamp`；`/internal/version` 额外包含版本信息。
4. 404 与 500 使用同一响应结构。
5. 本阶段不做认证；但 `/internal/*` 必须在代码中标注“仅限内部网络/反向代理保护”，部署层隔离在未来部署阶段处理。

### WP-04：请求关联与最小访问日志

1. 在最前置 Handler 解析 `X-Request-Id`。
2. 推荐合法格式：长度 8–128，仅允许字母、数字、`-`、`_`、`.`；其他值一律替换为新生成值。
3. 将最终 requestId 写入响应 Header 与路由上下文。
4. 请求结束后记录一条 INFO 日志：requestId、method、path、status、durationMs。
5. 异常日志使用 ERROR 并带 requestId 和堆栈；不得输出敏感 Header、Cookie、请求 Body。
6. 所有新增日志、注释、提交信息必须遵守根目录 `AGENTS.md` 的中文要求。

### WP-05：测试、启动说明与阶段结果模板

1. 使用 Vert.x JUnit 5 测试真实随机端口，不固定占用 8081。
2. 至少覆盖：
   - 应用可启动；
   - health 返回 200；
   - ready 返回 200；
   - version 返回预期字段；
   - 合法 requestId 被回显；
   - 非法 requestId 被替换；
   - 未匹配路径返回 JSON 404；
   - 异常路径返回 JSON 500（可通过专用测试路由或可测试的异常处理器验证，但不得将测试路由暴露到正式运行配置）；
   - 关闭后监听端口被释放。
3. 在模块 README 中写明启动与测试命令。
4. 填写 `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md` 模板。

## 6. 建议文件清单

以下是建议，不是死板文件名；Codex 需以现有包结构和 AGENTS.md 为准选择最小实现。

```text
修改：backend/pom.xml
新增：backend/converge-gateway/pom.xml
新增：backend/converge-gateway/README.md
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/GatewayApplication.java
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/config/GatewayConfig.java
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/GatewayRouterFactory.java
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/RequestIdHandler.java
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/AccessLogHandler.java
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/GatewayErrorHandler.java
新增：backend/converge-gateway/src/main/java/com/github/chjiae/gateway/http/InternalStatusHandler.java
新增：backend/converge-gateway/src/test/java/com/github/chjiae/gateway/... 测试
新增：docs/ai-gateway/progress/phase-01-gateway-runtime-result.md
```

## 7. 验收标准

### 构建与运行

```bash
cd backend
mvn -pl converge-gateway -am test
mvn -pl converge-gateway -am package
java -jar converge-gateway/target/converge-gateway-*.jar
```

启动后，默认监听 `8081`，以下请求可用：

```bash
curl -i http://127.0.0.1:8081/internal/health
curl -i http://127.0.0.1:8081/internal/ready
curl -i http://127.0.0.1:8081/internal/version
curl -i -H 'X-Request-Id: verify-20260706' http://127.0.0.1:8081/internal/health
curl -i http://127.0.0.1:8081/not-found
```

### 架构验收

- 网关可独立启动与停止；`converge-web-service` 不启动也不影响网关内部接口。
- 控制面与网关没有 Maven 反向依赖。
- 网关依赖树中没有 Spring、MyBatis、PostgreSQL、Redis 或 Servlet。
- 不出现新增业务表、Flyway 迁移、AI 资源表或 `/v1/*` 接口。
- 现有 `converge-web-service` 相关测试不被修改或破坏。
- 没有明文秘密、请求 Body、Authorization、Cookie 写入日志。

## 8. 与 New-API / Sub2API 的实时核验

### New-API 对照

New-API 已将模型网关、上游渠道、多协议适配、加权路由、失败重试、模型限流和用量计费组织为单一产品能力。其 `Channel` 同时保存上游 Key、Base URL、模型、分组、优先级、权重、状态、模型映射及多 Key 状态。新系统不直接复刻这个聚合模型：本阶段只建立承载未来高频中转的独立运行时，不创建 `Channel` 或任何路由实体。

**核验结论：合理。** 先隔离数据面，后续再将连接、凭据、资源、模型绑定与路由规则拆开，避免 Java 项目照搬巨型 Channel。

### Sub2API 对照

Sub2API 的核心复杂度在账户资源的授权状态、并发、限流、过载、会话粘性和账号池调度。本阶段不建立 Account 或 OAuth，也不建立粘性会话，因为这些都需要先有统一资源领域模型与真实网关请求链路。

**核验结论：合理。** 当前只交付可承载未来调度逻辑的 Vert.x 进程边界，不会提前把账号池实现错误地硬编码进 HTTP 层。

### 反偏离规则

若 Codex 在实施中开始新增模型、渠道、账户、凭据、Redis 快照、SSE、API Key、计费或数据库表，说明已越过本阶段，必须停止并报告。

## 9. 下一阶段前置条件

只有本阶段验收通过后，才讨论下一阶段。

下一阶段候选方向是“AI 资源控制面领域建模”，但必须根据本阶段的真实模块结构、打包方式和测试结果重新生成，不提前固定。
