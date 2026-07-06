请在 Converge API 仓库根目录，一次性完整实施以下阶段：

`docs/ai-gateway/phase-02-control-plane-catalog/README.md`

必须先阅读：

1. `AGENTS.md`
2. `docs/ai-gateway/README.md`
3. `docs/ai-gateway/phase-01-gateway-runtime/README.md`
4. `docs/ai-gateway/progress/phase-01-gateway-runtime-result.md`（若文件存在）
5. `docs/ai-gateway/phase-02-control-plane-catalog/README.md`
6. `docs/ai-gateway/phase-02-control-plane-catalog/SOURCE-REVIEW-2026-07-06.md`
7. `docs/ai-gateway/phase-02-control-plane-catalog/ACCEPTANCE-CHECKLIST.md`
8. 当前根 POM、`converge-common`、`converge-web-service`、现有实体/Mapper/Service/Controller/DTO、Flyway、租户拦截、审计、认证和集成测试基类。

## 执行授权与连续交付要求

我已授予你完成本阶段所需的全部操作权限。

你可以自行读取、创建、修改和删除本阶段产生的文件；下载/安装 Maven 依赖；运行构建、测试和本地服务；查看日志；修改必要配置；执行 `git add` 和 `git commit`。

不要因为常规实现选择、代码风格、依赖下载、构建失败、测试失败、文件修改、Git 操作或本地服务启动而询问我或要求批准。

先进行必要的简短审查，再直接开始实施，不要等待我确认审查结论。遇到可自行解决的问题，必须自行定位、修复、重试和验证。

除非出现以下真实阻塞，否则不得中途停止、不得只提交建议、不得把本阶段拆成等待下一轮的子任务：

- 缺少外部账号、私有仓库权限、必要许可证或不可替代密钥；
- 阶段 01 明确未完成，且当前仓库缺少其必要的网关模块/边界，导致本阶段与既有实现发生无法安全判断的冲突；
- 当前代码与阶段文档严重矛盾，且无法依据代码、`AGENTS.md` 和本阶段范围做出兼容判断；
- 操作会明确覆盖、删除或迁移现有生产数据，且无法以兼容迁移完成。

若发生真实阻塞，必须写入阶段结果文件，说明已核对内容、阻塞证据和未进行的改动；不要编造完成状态。

## 本阶段必须完整完成

1. 以当前 Flyway 最大版本为基础，新增一条 AI 控制面目录迁移；不得修改既有迁移。
2. 新增或复用等价的：`AiProviderKind`、`AiProtocolType`、`AiCatalogStatus`。
3. 新增租户隔离的 `AiProvider`、`AiUpstreamConnection`、`AiPublicModel` 实体及对应 Mapper、Service、Controller、DTO。
4. 完成 Provider、Connection、PublicModel 的创建、分页列表、详情、更新、启用、停用接口。
5. 复用项目现有 `Result<T>`、`PageResult<T>`、`BusinessException`、Bean Validation、`@Auditable` 和权限风格。
6. Provider/Connection/PublicModel 全部使用当前租户上下文；不加入租户忽略表；不使用跨租户查询捷径。
7. 不新增 Workspace、Organization 或其他平行租户模型；创建/更新 DTO 不得包含可写 tenantId；所有 AI 控制面 Service 必须显式拒绝 tenantId 为空的调用，不能只依赖 MyBatis 租户拦截器。
8. 仅允许现有 `TENANT_OWNER`、`TENANT_ADMIN` 管理这些对象；不得创建新的 AI 专属角色，也不得给无租户上下文的 `SUPER_ADMIN` 增加隐式跨租户 AI 数据读写能力。
9. 对 Connection Base URL 实现文档规定的最小安全校验与末尾斜杠规范化；不进行真实网络探测。
10. 不提供删除接口；通过 `DISABLED` 管理停用。
11. 为本阶段补齐集成测试并运行；优先修复测试失败后再继续。
12. 运行与本阶段相关的后端测试及合理范围内的现有回归测试。
13. 逐项填写 `ACCEPTANCE-CHECKLIST.md`。
14. 填写 `docs/ai-gateway/progress/phase-02-control-plane-catalog-result.md`。
15. 创建符合 `AGENTS.md` 规范的中文 Git 提交。

## 实施约束

- 这是现有项目上的兼容演进，禁止推倒或重写已有租户、认证、订阅、支付、卡密、通知、审计功能。
- 以现有包结构和代码约定为准，做最小必要改动。
- 若需要自定义 SQL，写入 XML；不要新增 SQL 注解。
- 所有新表必须有 `tenant_id`、`created_at`、`updated_at`；所有相关查询必须在当前租户内完成。
- 不得把公开模型名强制等同于上游模型名。
- 不得将 Credential、Account、Resource、Route、Session、价格、余额等后续概念混入本阶段。
- 不得记录或预留任何 API Key、OAuth Token、Refresh Token、Cookie、密码或私钥字段。

## 严格禁止

- 不修改 `converge-gateway`；
- 不创建 `converge-contract`；
- 不向网关下发配置、快照、事件或 Redis 信号；
- 不新增 `/v1/*`；
- 不实现上游 HTTP Client、API 中转、SSE、WebSocket、失败重试、协议转换、健康探测；
- 不接 Redis、API Key、OAuth、Credential、AuthorizedAccount、ExecutionResource、ResourcePool、RoutePolicy、SessionBinding、计费或定价；
- 不修改 React 前端；
- 不增加删除接口；
- 不修改既有 Flyway 迁移。

## 完成标准

只有同时满足以下条件，才结束本次任务：

- 本阶段 README 的所有“必须完成”事项都已实现；
- 验收清单逐项填写，未完成项有真实原因；
- 相关自动化测试已执行，失败项已尽量自行修复；
- 现有控制面功能未被破坏；
- 未越过阶段边界；
- 阶段实施结果已写入 progress 文档；
- Git 提交已创建。

最终只输出完整结果报告，包含：

1. 实施前关键审查发现；
2. 修改文件清单；
3. 接口与表结构摘要；
4. 运行过的命令与测试结果；
5. 验收清单完成情况；
6. 未完成项与真实阻塞原因；
7. 与 New-API、Sub2API 的能力对应关系及未偏离理由；
8. Git 提交 Hash。
