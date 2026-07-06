# 阶段 02｜现有租户体系兼容性复核

## 结论

本阶段必须**复用现有 Converge API 多租户体系**，不得创建第二套 Workspace、Tenant、Organization、TenantUser 或角色体系。

本项目现有实现已经提供：

- `tenant`：租户事实表；
- `BaseEntity.tenantId`：租户级实体公共字段；
- `TenantContext`：由 Spring MVC 请求链路维护当前租户；
- `ConvergeTenantLineHandler`：MyBatis-Plus 对租户表自动注入 `tenant_id` 过滤；
- `TENANT_OWNER`、`TENANT_ADMIN`、`TENANT_MEMBER`：现有租户内置角色；
- `@Auditable`、`Result<T>`、`PageResult<T>`、`BusinessException`：现有控制面约定。

因此，`ai_provider`、`ai_upstream_connection`、`ai_public_model` 都是现有租户的普通租户级业务表。

---

## 必须遵守的兼容规则

### 1. 不新增平行租户概念

禁止新增以下任何对象或字段语义：

```text
workspace
organization
project_tenant
ai_tenant
ai_workspace_id
owner_tenant_id
```

所有 AI 控制面对象均使用已有 `tenant_id`。

### 2. 不信任客户端传入 tenantId

创建、更新、查询、启用、停用请求 DTO 中不得包含可写 `tenantId`。

Service 必须：

1. 从当前认证请求取得当前租户；
2. 显式确认 `tenantId != null`；
3. 创建时由服务端写入该 `tenantId`；
4. 查询、更新、状态变更时只在当前 `tenantId` 范围内寻找对象。

不得把客户端请求中的 tenantId、路径参数 tenantId 或 Header tenantId 作为租户归属依据。

### 3. 不只依赖 MyBatis 租户拦截器

当前 `ConvergeTenantLineHandler` 在 `TenantContext.getTenantId() == null` 时会跳过租户条件注入。因此 Service 层必须有显式租户断言，例如复用或新增一个最小的内部辅助方法：

```java
private Long requireCurrentTenantId() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
        throw new BusinessException(...);
    }
    return tenantId;
}
```

实际错误码与项目既有风格保持一致。该断言必须用于所有 AI 控制面 Service 入口，避免平台级请求意外查询或修改所有租户数据。

### 4. 角色沿用既有代码

本阶段仅允许现有角色：

```text
TENANT_OWNER
TENANT_ADMIN
```

`TENANT_MEMBER` 不得管理 AI 控制面数据。

不要新建 `AI_ADMIN`、`AI_OPERATOR` 等角色；权限细分留到后续确有需求的阶段。

### 5. SUPER_ADMIN 不得隐式跨租户

本阶段不为 `SUPER_ADMIN` 开放 AI 目录跨租户管理入口。

原因：当前超管 JWT 没有 `tenantId`，而 `TenantContext` 为空会让 MyBatis 租户拦截器跳过过滤。未来平台治理能力必须单独设计显式目标租户选择、只读/应急权限、审计和防误操作机制。

### 6. 连接必须保证同租户归属

`ai_upstream_connection` 的 `provider_id` 必须指向同一租户的 `ai_provider`：

- Service 层在当前租户范围内查找 Provider；
- 数据库层优先采用 `(tenant_id, provider_id)` 与 Provider 的组合外键，或在数据库约束能力受限时写明原因并以 Service 测试兜底；
- 禁止通过 `TenantContext.setIgnoreTenant(true)` 实现关联校验。

### 7. 继承既有审计和迁移规则

- 新实体继承 `BaseEntity`，不重复定义 `id`、`tenantId`、`createdAt`、`updatedAt`；
- 新表不加入 `ConvergeTenantLineHandler.IGNORE_TABLES`；
- 使用新的 Flyway 迁移，不修改历史迁移；
- 写操作使用现有 `@Auditable`，审计中仅记录对象 ID、code、displayName、状态，不记录将来可能出现的秘密字段。

---

## 必须新增的测试

1. 相同 code 在同租户冲突，在不同租户允许；
2. 租户 A 的管理员不能读取、更新、启停租户 B 的 Provider、Connection、PublicModel；
3. Connection 无法引用租户 B 的 Provider；
4. 无 `tenantId` 的上下文调用 Service 时明确失败，不得返回跨租户数据；
5. `TENANT_MEMBER` 被拒绝；
6. `TENANT_OWNER` 和 `TENANT_ADMIN` 在本租户内可操作；
7. `SUPER_ADMIN` 无租户上下文时不能通过这些接口获得跨租户 AI 目录访问。

---

## 对阶段 02 的影响

这不是新增功能范围，而是将已有租户体系作为强制前置约束。Codex 在实施阶段 02 前必须先阅读本文件，并将其作为 README 与验收清单的一部分执行。
