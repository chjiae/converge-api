# 阶段 09｜AI Gateway 管理控制台一期｜实施结果

> 本文件不得记录真实 API Key、Client API Key raw value、Authorization、Cookie、request body、response body、baseUrl、resourceId、leaseId、Redis key、nonce、ciphertext、HMAC、payload 或 secret。

## 1. 实施时间与提交

* 开始时间：2026-07-08 14:00:00 +08:00
* 完成时间：2026-07-08 14:51:44 +08:00
* Git 提交 Hash：提交后见最终报告
* 实施分支：master
* 开始时 `master` 最新提交：9567572 feat(网关): 优化准入同步与快照增量刷新
* 阶段 08 是否已完成：是，已存在 `phase-08-runtime-governance-result.md`
* 阶段 08.1 是否已完成：是，已存在 `phase-08-1-admission-snapshot-governance-result.md`
* 本阶段是否修改 Gateway 数据面语义：否，仅新增控制面管理台、控制面薄代理与前端页面

---

## 2. 前置复核

* Provider / Connection / PublicModel 控制面是否存在：是
* Credential 控制面是否存在：是
* ExecutionResource 控制面是否存在：是
* RuntimePolicy 控制面是否存在：是
* Static Routing 控制面是否存在：是
* AccessGroup / ClientApiKey 控制面是否存在：是
* Gateway `/internal/snapshot-status` 是否存在：是
* Gateway `/internal/runtime-status` 是否存在：是
* Gateway `/v1/chat/completions` 是否存在：是
* 前端目录：`frontend`
* 前端框架：React 19 + Vite
* 包管理器：npm
* UI 组件库：项目现有 `src/components/ui/*` 与 lucide-react
* 路由方案：react-router-dom
* 请求封装：复用 `src/lib/api.ts`
* 权限方案：复用 `RoleGuard`、`useAuth` 与现有角色模型

---

## 3. 后端小补丁

### 3.1 Options 接口

* 是否新增 `GET /api/v1/ai/options`：是
* 返回枚举：

    * providerKinds：是
    * protocolTypes：是
    * catalogStatuses：是
    * resourceStatuses：是
    * credentialTypes：是
    * canonicalOperations：是
    * selectionPolicies：是
    * routePolicyStatuses：是
    * clientApiKeyStatuses：是
* 是否复用 `Result<T>`：是
* 是否有权限控制：是，仅 `TENANT_OWNER` / `TENANT_ADMIN`
* 是否有测试：是，覆盖租户管理员可访问与普通成员拒绝

### 3.2 Gateway 状态代理

* 是否新增 `GET /api/v1/ai/gateway/ready`：是
* 是否新增 `GET /api/v1/ai/gateway/snapshot-status`：是
* 是否新增 `GET /api/v1/ai/gateway/runtime-status`：是
* 配置项：

    * AI_GATEWAY_ADMIN_BASE_URL：是，服务端读取，未配置时代理不可用
    * AI_GATEWAY_ADMIN_CONNECT_TIMEOUT_MS：是，默认 1000
    * AI_GATEWAY_ADMIN_READ_TIMEOUT_MS：是，默认 3000
    * AI_GATEWAY_ADMIN_ENABLED：是，默认 false
* Gateway 未配置时行为：返回业务错误，前端展示未配置状态
* Gateway 不可达时行为：返回业务错误，前端展示不可达状态
* 是否隐藏 Gateway 内部 URL：是，响应、日志与文档示例均不展示
* 是否过滤敏感字段：是，仅透出安全 allowlist 字段

### 3.3 Chat Test 代理

* 是否新增 `POST /api/v1/ai/gateway/test-chat-completions`：是
* 是否支持非流式：是
* 是否支持流式：否，本阶段只做控制台非流式测试
* 是否记录 Client API Key：否
* 是否记录 Authorization：否
* 是否记录完整 request body：否
* 错误码处理：

    * gateway_not_ready：透传安全错误 envelope 元数据
    * invalid_api_key：透传安全错误 envelope 元数据
    * model_access_denied：透传安全错误 envelope 元数据
    * model_not_found：透传安全错误 envelope 元数据
    * no_runtime_eligible_resource：透传安全错误 envelope 元数据
    * runtime_state_unavailable：透传安全错误 envelope 元数据
    * upstream_error：透传安全错误 envelope 元数据，不回显敏感响应体

---

## 4. 前端页面

### 4.1 菜单与路由

* 是否新增 AI Gateway 菜单：是
* 菜单路径：`/console/ai-gateway`
* 是否仅 TENANT_OWNER / TENANT_ADMIN 可见：是
* TENANT_MEMBER 是否不可访问：是，E2E 覆盖 403 提示
* 是否支持亮暗主题：是，使用现有主题 token
* 是否支持常用视口：是，页面采用响应式 grid、wrap tabs 与可横向滚动表格

### 4.2 概览页

* 路由：`/console/ai-gateway`
* 展示 Gateway Ready：是
* 展示 Snapshot 摘要：是
* 展示 Runtime 摘要：是
* 快捷入口：是，支持跳转到配置、访问、运行状态与在线测试
* 空状态 / 未配置 Gateway 状态：是

### 4.3 上游配置

Provider：

* 列表：是
* 创建：是
* 编辑：是
* 详情：以表格行信息和编辑弹窗形式提供
* 启用：是
* 停用：是

Connection：

* 列表：是
* 创建：是
* 编辑：是
* 详情：以表格行信息和编辑弹窗形式提供
* 启用：是
* 停用：是

PublicModel：

* 列表：是
* 创建：是
* 编辑：是
* 详情：以表格行信息和编辑弹窗形式提供
* 启用：是
* 停用：是

### 4.4 凭据与资源

Credential：

* 列表：是
* 创建：是
* 编辑元数据：是
* 轮换：是
* 启用：是
* 停用：是
* 是否不展示明文：是，仅 password 输入，不在列表展示
* 是否不保存明文输入：是，表单关闭后清空，不写本地存储

ExecutionResource：

* 列表：是
* 创建：是
* 编辑元数据：是
* 启用：是
* 停用：是
* 排空：是
* 是否提示绑定关系不可修改：是

RuntimePolicy：

* 查询：是
* 编辑：是
* policyVersion 是否只读：是
* maxConcurrentRequests=0 说明：是

### 4.5 路由配置

ResourcePool：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

PoolMember：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

ResourceModelBinding：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

RoutePolicy：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

RouteTarget：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

RoutePreview：

* 是否可用：是
* 是否提示仅静态预览：是

### 4.6 下游访问

AccessGroup：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

ModelGrant：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

ClientApiKey：

* 列表：是
* 创建：是
* 编辑：是
* 轮换：是
* 启用：是
* 停用：是
* 撤销：是
* raw key 是否只展示一次：是，仅创建/轮换响应弹窗展示
* 是否提供复制按钮：是
* 关闭弹窗是否有提示：是

KeyAccessGroup：

* 列表：是
* 创建：是
* 编辑：是
* 启用：是
* 停用：是

### 4.7 运行状态

Snapshot Status：

* 是否通过控制面代理：是
* 展示字段：就绪状态、schema 版本、租户数量、revision 摘要、陈旧状态等安全元数据
* 是否过滤敏感字段：是

Runtime Status：

* 是否通过控制面代理：是
* 展示字段：运行态就绪、租户数量、资源计数、状态计数、摘要时间等安全元数据
* 是否过滤敏感字段：是

### 4.8 在线测试

Chat Completions：

* 路由：`/console/ai-gateway` 的“在线测试”标签
* 是否非流式：是
* 是否支持 model 输入：是
* 是否支持 messages 编辑：是
* 是否支持 temperature：是
* 是否支持 maxTokens：是
* 是否展示响应 JSON：是
* 是否展示耗时：是
* 是否展示错误码：是
* Client API Key 是否 password 输入：是
* 是否不持久化测试 Key：是

---

## 5. 安全检查

```text
是否展示上游 API Key 明文：否
是否展示 Credential encryptedSecret：否
是否展示 nonce：否
是否展示 HMAC：否
是否展示 secretFingerprint：否
是否展示 secretReference：否
是否展示 runtimeSecret：否
是否展示 Authorization：否
是否展示 Cookie：否
是否展示完整 Client API Key raw value：仅创建/轮换 no-store 响应弹窗一次性展示
是否在创建/轮换之外展示 raw key：否
是否展示 Gateway Redis key：否
是否展示 leaseId：否
是否展示 resourceId：否
是否展示上游 baseUrl 于状态接口：否
是否在 console.log 打印敏感信息：否
是否写入 localStorage / sessionStorage：否
```

结论：

```text
本阶段管理台只展示安全元数据；敏感字段由后端代理 allowlist 过滤，前端不持久化测试 Key 与一次性 raw key。
```

---

## 6. 测试与验证

后端执行：

```bash
mvn -pl converge-web-service -am test
# 结果：通过

mvn -pl converge-web-service,converge-gateway -am test-compile
# 结果：通过

mvn test
# 结果：通过
```

前端执行：

```bash
# package.json 无独立 typecheck 脚本，npm run build 已执行 tsc -b
npm run build
# 结果：通过

npm run lint
# 结果：通过，存在既有 fast-refresh 类规则警告，不影响本阶段

npm run e2e
# 结果：通过，2 个用例全部通过
```

E2E / Playwright：

```text
登录后看到 AI Gateway 菜单：通过
Provider 列表加载：通过
Credential 创建弹窗不泄露明文：通过
Client API Key 创建后一次性展示 raw key：通过
Snapshot Status 页面加载：通过
Runtime Status 页面加载：通过
Chat Test 页面能提交并处理响应：通过
TENANT_MEMBER 无法访问：通过
```

---

## 7. 阶段边界复核

本阶段是否实现以下内容：

```text
Claude：否
Gemini：否
Responses：否
Embeddings：否
Realtime：否
WebSocket：否
订阅账号池：否
OAuth：否
Cookie：否
sticky session：否
计费：否
余额：否
充值：否
预扣：否
结算：否
账本：否
RPM / TPM：否
Client API Key 限流：否
真实 retry：否
请求后的 fallback：否
审计日志页面：否
平台超级管理员跨租户治理：否
复杂拓扑编辑器：否
Gateway 请求热路径访问 PostgreSQL：否
```

结论：

```text
未越过阶段 09 边界；本阶段只做租户管理员一期管理台、控制面安全薄代理与非流式在线测试入口。
```

---

## 8. 已知问题与后续输入

* 与计划偏差：无实质偏差；由于 `package.json` 无 `typecheck` 脚本，使用 `npm run build` 完成 TypeScript 校验
* 未完成项：无
* 已知风险：控制台为一期表单化管理体验，复杂拓扑的可视化编排仍留给后续阶段
* 需要后端后续补齐：后续阶段可继续提供更细粒度统计接口，避免前端聚合大量分页
* 需要前端后续优化：可在后续阶段补充拓扑图、批量操作、审计日志页和更细的状态说明
* 对阶段 10 Claude Messages 的输入：现有 API client 与页面标签结构可复用，新增协议类型时保持安全字段过滤
* 对后续计费阶段的输入：当前页面未混入计费概念，后续可在独立账单页面接入
* 对后续订阅账号池阶段的输入：当前 Resource/RuntimePolicy 页面只管理 Direct API 资源，不包含账号池语义

---

## 9. New-API / Sub2API 对照结论

* New-API 对照：本阶段覆盖其管理台一期中的上游、模型、凭据、资源、路由、下游访问和测试入口雏形，但未实现计费、渠道策略、账号池和多协议完整控制台
* Sub2API 对照：本阶段覆盖订阅式访问管理的一部分下游 Key 与分组授权管理，但未实现订阅账号、套餐映射、余额或账单
* 是否偏离 Converge 当前阶段边界：否
* 后续是否需要继续源码级实现机制调研：是，后续计费、账号池、多协议和审计可继续对照 New-API / Sub2API 做能力拆分
