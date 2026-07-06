# Converge API｜统一 AI 网关演进计划

本目录只保存“按阶段交付”的 AI 网关演进文档，不替代根目录 `AGENTS.md` 的编码规范，也不覆盖既有 `docs/design` 与 `docs/superpowers` 内容。

## 执行方式

1. 每次只引入一个阶段目录。
2. 将阶段目录解压到仓库根目录后，使用该阶段的 `CODEX-PROMPT.md` 驱动 Codex 实施。
3. Codex 完成后，将结果写入 `docs/ai-gateway/progress/`。
4. 完成验收后，再根据实际代码、测试结果和实时仓库调研生成下一阶段。

## 阶段索引

| 阶段 | 状态 | 目标 |
|---|---|---|
| `phase-01-gateway-runtime` | 前置阶段 | 新增独立 Vert.x 网关运行骨架；不实现任何 AI API 中转业务。 |
| `phase-02-control-plane-catalog` | 当前阶段 | 新增 AI 控制面的供应商、上游连接元数据、公开模型目录；严格复用现有租户、角色、审计与 MyBatis 租户隔离；不保存凭据、不路由、不接入网关。 |

## 当前阶段

- `phase-02-control-plane-catalog`：控制面中可审计、租户隔离的非敏感 AI 配置目录。
