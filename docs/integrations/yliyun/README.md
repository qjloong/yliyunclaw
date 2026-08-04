# 一粒云 Runtime 集成

本目录只维护 MateClaw 如何消费一粒云 AI Runtime 契约，以及 Goal/AI Sites 所需的 Agent、Team、Skill 和 Tool 配置。

## 正式契约主源

`yly-saas-cdms-ai/docs/ai-runtime/mateclaw-contract.md`

当前本地联调位置：

`D:\project\yly-rag\cloud-saas\yly-saas-cdms-ai\docs\ai-runtime\mateclaw-contract.md`

该云盘文档定义 Header、Trusted Context、Conversation、Run、Team、Callback、SSE、HMAC、幂等和错误码。MateClaw 文档只描述消费实现，不另建一份竞争性的正式契约。

AI Sites 当前正式实施与交接主源：

```text
yly-saas-cdms-ai/docs/ai-sites/implementation-plan-v3.0.md
yly-saas-cdms-ai/docs/ai-sites/handover.md
```

本地联调位置为 `D:\project\yly-rag\cloud-saas\yly-saas-cdms-ai\docs\ai-sites\`。MateClaw 只执行其中 `MC-W-*` 和 Runtime Consumer 任务。

## MateClaw 负责

- 接收并校验 Trusted Context，映射 Workspace 与用户身份。
- 执行 Assistant、Agent Team、Skill、Tool/MCP、Conversation、Memory 与 Trace。
- 透传 `bizId`、`runId`、`traceId`，发送签名 Callback/Event。
- 执行 Human Approval 停点，不替代云盘的最终业务审批。

## MateClaw 不负责

- Goal 的 Project/Goal/KR/Task、ACL、Proposal、Acceptance 数据库与普通用户 UI。
- AI Sites 的 Site/Page/Source/Revision、域名、发布、表单、商机和 Analytics 业务事实。
- Site Delivery 的公网路由、静态交付、CDN、证书和回滚执行。

## 文档

- [Runtime Adapter](runtime-adapter.md)
- [Trusted Context Consumer](trusted-context-consumer.md)
- [Callback 与事件](callback-and-events.md)
- [Goal Agent 配置](goal-agent-config.md)
- [AI Sites Agent 配置](ai-sites-agent-config.md)
- [MateClaw 专属实施清单](implementation-checklist.md)

现有一粒云通用 AI 助手的完成状态、联调端口和历史证据继续以 [交接文档](../../handover-yliyun-integration.md) 为准。
