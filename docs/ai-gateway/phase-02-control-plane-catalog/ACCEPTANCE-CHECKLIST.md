# 阶段 02｜验收清单

> Codex 完成实现后逐项填写。未完成项必须写明原因，不得勾选后省略。

## 前置检查

- [ ] 已阅读 `AGENTS.md`。
- [ ] 已阅读阶段 01 实施结果；`converge-gateway` 运行骨架已存在且本阶段未修改它。
- [ ] 已阅读现有租户、审计、认证、Flyway、Mapper 和集成测试实现。
- [ ] 已阅读 `TENANT-COMPATIBILITY-REVIEW.md`。
- [ ] 已确认不新增 Workspace、Organization 或其他平行租户模型。
- [ ] 已确认当前 Flyway 最新版本，并使用下一个可用版本新增迁移。

## 领域与迁移

- [ ] 新增 `AiProviderKind`、`AiProtocolType`、`AiCatalogStatus` 或已复用等价枚举。
- [ ] 新增 `ai_provider`，包含 tenant_id、唯一约束、索引和注释。
- [ ] 新增 `ai_upstream_connection`，包含 tenant_id、provider_id、唯一约束、索引和注释。
- [ ] 新增 `ai_public_model`，包含 tenant_id、唯一约束、索引和注释。
- [ ] 三张表均未加入租户拦截忽略表。
- [ ] 创建/更新 DTO 不包含可写 `tenantId`。
- [ ] 每个 AI 控制面 Service 显式要求当前 tenantId 非空，不只依赖 MyBatis 租户拦截器。
- [ ] AI 实体继承已有 `BaseEntity`，未重复定义公共审计字段。
- [ ] 未修改既有 Flyway 迁移。
- [ ] 未创建任何秘密字段或账户字段。

## Provider 管理

- [ ] 创建、分页查询、详情、更新、启用、停用接口已完成。
- [ ] 同租户 Provider code 重复时拒绝。
- [ ] 不同租户可使用相同 Provider code。
- [ ] 没有删除接口。
- [ ] 写操作有审计记录。

## Connection 管理

- [ ] 创建、分页查询、详情、更新、启用、停用接口已完成。
- [ ] providerId 必须属于当前租户。
- [ ] Connection 的 Provider 归属在 Service 层被当前租户范围校验，并尽可能有数据库组合约束。
- [ ] Base URL 仅允许绝对 http/https URL。
- [ ] 拒绝 URL 用户信息、query、fragment。
- [ ] 合法 Base URL 已规范化末尾斜杠。
- [ ] 不进行真实网络探测。
- [ ] 没有删除接口。

## PublicModel 管理

- [ ] 创建、分页查询、详情、更新、启用、停用接口已完成。
- [ ] code 是独立公开模型别名，未绑定上游模型。
- [ ] 没有 Provider/Connection 映射。
- [ ] 没有删除接口。

## 权限、隔离与测试

- [ ] 未认证访问被拒绝。
- [ ] TENANT_MEMBER 无管理权限。
- [ ] TENANT_OWNER / TENANT_ADMIN 可管理自身租户资源。
- [ ] 租户 A 无法读取/修改租户 B 数据。
- [ ] SUPER_ADMIN 无租户上下文时没有隐式跨租户 AI 管理后门。
- [ ] 无 tenantId 的 Service 调用明确失败，未退化为跨租户查询或写入。
- [ ] Base URL 正反例测试通过。
- [ ] Provider 归属校验测试通过。
- [ ] 新增集成测试通过。
- [ ] 现有后端回归测试通过。

## 边界与文档

- [ ] 未修改 `converge-gateway`。
- [ ] 未创建 `converge-contract`。
- [ ] 未新增 `/v1/*`。
- [ ] 未接入 Redis、网关快照、API Key、Credential、OAuth、Account、ResourcePool、RoutePolicy、计费或 SSE。
- [ ] 已填写 `docs/ai-gateway/progress/phase-02-control-plane-catalog-result.md`。
- [ ] 已创建符合 `AGENTS.md` 的中文 Git 提交。
