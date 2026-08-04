# Goal Agent 的 MateClaw 配置

本文件只描述 MateClaw 专属配置，不定义 `ga_project`、`ga_goal`、`ga_task` 等云盘业务表。

## 配置对象

- Goal Assistant Seed：入口 Agent、版本和默认说明。
- Goal Skill：理解目标、规划建议、进展总结与验收建议。
- Worker Skill：研究、文档处理、分析和报告等通用执行能力。
- Goal Tool/MCP Binding：只调用云盘授权的 Goal/文件/审计 Tool。
- Goal Team Template：Planner、Executor、Reviewer 等角色和停点。
- Callback/Trace：统一 Run、Step、Proposal 与业务 `bizId` 关联。

## 配置原则

- 不按业务 ID 硬编码裁剪通用工具；能力由 Agent/Skill 绑定、Workspace 和云盘 entitlement 共同控制。
- Seed 必须版本化、可重复安装、可升级和可回滚。
- Agent 输出变更建议时只形成 Proposal，由云盘 Human 治理决定是否落库。
