你正在现有 Converge API 仓库根目录工作。请实施“阶段 01｜独立 Vert.x 网关运行骨架”。

## 必读文件

1. 根目录 `AGENTS.md`；
2. `docs/ai-gateway/README.md`；
3. `docs/ai-gateway/phase-01-gateway-runtime/README.md`；
4. `docs/ai-gateway/phase-01-gateway-runtime/SOURCE-REVIEW-2026-07-06.md`；
5. 根 `backend/pom.xml`、`backend/converge-common/pom.xml`、`backend/converge-web-service/pom.xml`；
6. 现有 `converge-web-service` 的应用入口、健康检查、测试基类和日志/配置写法。

## 总原则

- 这是现有项目演进，不是重写。
- `AGENTS.md` 只作为编码规范约束；不要修改 `AGENTS.md`，也不要把业务计划写进去。
- 只实施本阶段，不得提前实施下一阶段。
- 先审查现有实现并给出简要实施清单；随后直接开始实施，不等待确认。
- 发生歧义时，优先选择最小、可回滚、兼容现有模块的实现，并在结果记录中说明取舍。
- 所有注释、日志和 Git 提交信息必须使用中文，遵守 `AGENTS.md`。

## 本阶段必须完成

严格按照 `README.md` 的 WP-01 至 WP-05 实施：

1. 新增独立 Maven 模块 `backend/converge-gateway`；
2. 使用 Vert.x 5.1.3 BOM，提供可执行 fat JAR；
3. 新增独立 Vert.x HTTP Server，不使用 Spring、不依赖 `converge-web-service`；
4. 提供 `GET /internal/health`、`GET /internal/ready`、`GET /internal/version`；
5. 提供 `X-Request-Id` 解析/生成、响应回显、访问日志和统一 JSON 404/500；
6. 提供优雅关闭；
7. 提供 Vert.x JUnit 5 测试和模块 README；
8. 在 `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md` 记录结果。

## 本阶段严格禁止

- 不新增 `/v1/*` API；
- 不做任何 API 中转、SSE、WebSocket、HTTP 上游客户端、重试、协议转换；
- 不接 PostgreSQL、Redis、Flyway、MyBatis、Spring Security、JWT、`TenantContext`；
- 不新增模型、供应商、渠道、凭据、账户、资源池、路由、计费、下游 API Key 表或对象；
- 不新建 `converge-contract`；
- 不修改现有前端；
- 不重构或迁移既有租户、认证、订阅、支付、卡密、审计业务；
- 不修改既有数据库迁移。

## 实施要求

### 先做审查

在开始修改前，简短输出：

1. 现有 Maven 模块和包名事实；
2. 你准备修改/新增的文件及原因；
3. 可能影响现有构建的风险；
4. 最终执行的验证命令。

### 实施时的技术约束

- 网关使用 Vert.x 事件循环；不得在 Handler 中加入阻塞数据库、Redis、文件或网络调用。
- 配置只读环境变量/系统属性，不引入 Spring 配置系统。
- `X-Request-Id` 非法值不得直接写入日志、响应或上下文；必须替换。
- 日志中严禁包含 Authorization、Cookie、请求体、查询参数中的可能秘密。
- 404/500 必须是 JSON，且带 requestId。
- `/internal/*` 本阶段不鉴权，但必须有中文注释说明只能部署在内部网络或受反向代理保护的环境。
- 仅添加本阶段所需的最少依赖；不要顺带接入 Micrometer、OpenTelemetry、Redis、HTTP Client 或其他未来能力。

### 验证与提交

至少执行：

```bash
cd backend
mvn -pl converge-gateway -am test
mvn -pl converge-gateway -am package
```

并在不影响现有服务的前提下，手工或自动验证三个内部接口、404、requestId 回显和优雅关闭。

完成后：

1. 填写 `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md`；
2. 输出已修改文件、关键实现说明、测试结果、未执行项目及原因、风险与下一阶段建议；
3. 明确说明本阶段如何对应 New-API 的网关运行基础和 Sub2API 的未来资源调度承载基础，以及为何没有偏离；
4. 创建粒度适中的中文 Git 提交。建议拆分为：
   - `chore(网关): 新增 Vert.x 网关模块与构建配置`
   - `feat(网关): 添加内部健康检查与请求关联能力`
   - `test(网关): 添加 Vert.x 网关基础测试`
   - `docs(网关): 记录第一阶段实施结果`

提交前先执行 `git diff` 自检，不得包含 IDE 配置、target、dist、node_modules、真实秘密或无关格式化。
