# 阶段 07｜来源、仓库现状与设计依据

## 1. 当前仓库状态

实施前必须确认当前 `master` 包含：

```text
9479db6 feat(AI网关): 实现下游访问授权 V3
```

阶段 06 已实现：

```text
Gateway Snapshot V3
Client API Key 本地认证
AccessGroup grant union
GET /v1/models
V1 / V2 / V3 snapshot compatibility
Gateway request 不访问 PostgreSQL / Redis
```

阶段 07 必须复用这些能力，不能重新设计 Client Key 或授权体系。

---

## 2. OpenAI Compatible Chat Completions

本阶段仅采用最小 OpenAI Compatible Chat Completions 语义：

```text
POST /chat/completions
model
messages
stream
JSON 非流式响应
text/event-stream 流式响应
```

不实现：

```text
Responses
Realtime
Assistants
Files
Images
Audio
Embeddings
Batch
Webhook
```

---

## 3. Vert.x 设计依据

本阶段的关键设计原则：

```text
HTTP Client 应复用连接池
HTTP 流需要处理背压
长 SSE 不应聚合完整响应
请求和响应都必须受 body 上限保护
客户端中断应 best-effort 取消上游
关闭阶段应停止新请求并等待已有请求
```

因此：

```text
使用共享 Vert.x HttpClient
不使用阻塞 HTTP Client
SSE 使用 event 级增量重写
下游写队列满时暂停上游
客户端断开后取消上游
```

---

## 4. New-API 对照

New-API 的能力面包含：

```text
Token grouping
Model restrictions
OpenAI Compatible 接口
权重路由
失败重试
多协议转换
计费与额度
```

阶段 07 只借鉴：

```text
授权后的 OpenAI Compatible 请求
静态 priority / weight 路由的真实执行
```

本项目不复制其代码，也不在本阶段实现：

```text
重试
计费
多协议转换
用户级限流
全接口覆盖
```

---

## 5. Sub2API 对照

Sub2API 的能力面包括：

```text
认证
API 转发
计费
负载均衡
账号选择
粘性会话
并发控制
限流
```

阶段 07 仅处理：

```text
合法 Direct API Key 资源的首个中转闭环
```

不得实现任何未经授权的订阅账户、Cookie、账号池或规避上游规则的能力。

---

## 6. 结论

阶段 07 是从静态配置正确走向真实接口可调用的最小必要步骤：

```text
阶段 06：谁可以调用
阶段 07：如何安全调用一个 OpenAI Compatible 上游
后续阶段：如何在动态状态、计费、失败与多协议条件下持续调用
```