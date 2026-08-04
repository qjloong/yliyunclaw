# MateClaw Runtime Adapter

Runtime Adapter 是云盘 AI Runtime 契约在 MateClaw 侧的实现边界。它应复用 MateClaw 通用 Conversation、Agent、Team、Skill、Tool/MCP 和 Trace 主链，不创建 Goal 或 AI Sites 业务域。

## 请求入口

1. 校验服务身份、HMAC、时间戳、Nonce 和幂等键。
2. 解析 Trusted Context，并与当前 Workspace/用户映射交叉校验。
3. 根据 `appCode`、`bizType`、配置版本和授权能力选择已注册 Assistant/Team。
4. 创建或恢复通用 Conversation/Run，保留云盘 Binding 标识。
5. 调用 Tool/MCP 时透传受控业务上下文，不允许模型自报 tenant/user/biz 范围。

## 执行上下文

最小关联键包括 `tenantId`、`workspaceId`、`userId`、`appCode`、`bizType`、`bizId`、`runId` 和 `traceId`。字段命名及必填规则以云盘契约主源为准。

## Human Approval

- MateClaw 可以暂停 Run 并发出 approval-required 事件。
- 云盘保存业务 Proposal/Approval 事实并决定继续、拒绝或取消。
- MateClaw 收到已签名决定后恢复或终止通用 Run，不复制业务审批表。

## Workspace 隔离

- 一粒云租户映射到独立 Workspace。
- 用户角色来自可信映射和云盘授权，不能由请求正文或 Prompt 提升。
- Agent/Provider/Tool 配置按 Workspace 和应用 entitlement 校验。
