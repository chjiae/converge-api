# 阶段 05｜资源池、模型能力绑定与静态路由编译｜实施结果

> 由 Codex 在阶段完成后填写。不要删除章节；无内容时明确写“无”。

## 1. 实施时间与提交

- 开始时间：
- 完成时间：
- Git 提交 Hash：
- 实施分支：

## 2. 前置阶段复核

- 阶段 01：
- 阶段 02：
- 阶段 03：
- 阶段 04：
- 阶段 04 Git 提交是否存在：
- 当前最大 Flyway 版本：

## 3. 新增模型与数据库迁移

- Flyway 迁移：
- `ai_resource_pool`：
- `ai_resource_pool_member`：
- `ai_resource_model_binding`：
- `ai_route_policy`：
- `ai_route_target`：
- 复合约束和索引：
- 新增枚举：
- 租户与状态规则：

## 4. Routing Core

- 模块路径：
- 依赖边界：
- Validator / Compiler / Preview Selector：
- priority / weight 规则：
- StaticRoutePlan 内容：
- 失败类别：
- 是否引用/泄露秘密（必须为否）：

## 5. 控制面 API 与审计

- ResourcePool：
- PoolMember：
- ResourceModelBinding：
- RoutePolicy：
- RouteTarget：
- Preview：
- 权限：
- 审计：
- 无硬删除结论：

## 6. Snapshot V2 与 Gateway

- Contract schema：
- V1 兼容策略：
- V2 结构：
- Outbox change types：
- Projector：
- Gateway compile：
- last-known-good：
- snapshot-status 摘要：
- rollout 顺序文档：

## 7. 测试与验证

```text
在此记录实际命令与结果。
```

- 控制面集成测试：
- Routing Core 单元测试：
- Contract V1/V2 兼容：
- Gateway runtime：
- 多 tenant：
- Secret leak scan：
- backend root `mvn test`：
- 未执行项及原因：

## 8. 阶段边界复核

- `/v1/*`：
- 上游 HTTP / SSE / WebSocket：
- ClientApiKey / AccessGroup / 套餐：
- 价格 / 倍率 / 计费：
- OAuth / account pool：
- 动态限流 / sticky session：
- React：
- gateway DB 访问：
- 结论：

## 9. 偏差、风险与下一阶段输入

- 与计划偏差：
- 未完成项：
- 已知风险：
- 对下一阶段建议：

## 10. New-API / Sub2API 对照结论

- New-API：
- Sub2API：
- 是否偏离：
