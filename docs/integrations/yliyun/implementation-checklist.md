# MateClaw 一粒云适配实施清单

> 范围：只列 MateClaw 代码和配置任务。实时负责人、Sprint 与状态应迁移到 MateClaw 仓库 Issue；跨仓库阶段以云盘 `docs/ai-program/master-roadmap.md` 为准。

## 前置契约

| ID | 任务 | 当前状态 | 依赖 |
|---|---|---|---|
| MC-RT-001 | 评审云盘 AI Runtime Draft 契约与现有 API 的兼容差异 | 待实施 | RT Contract Draft |
| MC-RT-002 | 补齐签名、Nonce、幂等与错误码契约测试向量 | 待实施 | MC-RT-001 |
| MC-RT-003 | 将双方契约状态提升为 Frozen v1 | 待实施 | MC-RT-002 |

## Runtime Adapter

| ID | 任务 | 当前状态 | 依赖 |
|---|---|---|---|
| MC-RT-010 | Trusted Context Consumer 与 Workspace/用户交叉校验 | 待实施 | MC-RT-003、RT-003 |
| MC-RT-011 | Conversation/Run/Team API 适配与幂等 | 待实施 | MC-RT-003、RT-004 |
| MC-RT-012 | Callback/Event Outbox、签名、退避和乱序保护 | 待实施 | MC-RT-011 |
| MC-RT-013 | SSE 恢复、Run 查询和最终状态对账 | 待实施 | MC-RT-011 |
| MC-RT-014 | Human Approval 暂停/恢复适配 | 待实施 | MC-RT-011 |

## Goal 配置

| ID | 任务 | 当前状态 | 依赖 |
|---|---|---|---|
| MC-G-001 | Goal Assistant Seed | 待实施 | MC-RT-011 |
| MC-G-002 | Goal Skill 与 Worker Skill | 待实施 | MC-G-001 |
| MC-G-003 | Goal Tool/MCP Binding | 待实施 | 云盘 Goal Tool |
| MC-G-004 | Goal Team Template | 待实施 | MC-G-002/003 |
| MC-G-005 | Proposal/Human Approval 事件适配 | 待实施 | MC-RT-014 |
| MC-G-006 | 租户隔离、重试、审计和 E2E 验收 | 待实施 | MC-G-001～005 |

## AI Sites 配置

| ID | 任务 | 当前状态 | 依赖 |
|---|---|---|---|
| MC-W-001 | Website Assistant Seed | 待实施 | MC-RT-011 |
| MC-W-002 | Generation Team 与 Worker Skills | 待实施 | MC-W-001 |
| MC-W-003 | AI Sites Tool Binding | 待实施 | 云盘 AI Sites Tool |
| MC-W-004 | SEO/QA 与 Page DSL 校验流程 | 待实施 | MC-W-002/003 |
| MC-W-005 | Artifact/Proposal Callback 适配 | 待实施 | MC-RT-012 |
| MC-W-006 | Human Lock、租户隔离和 E2E 验收 | 待实施 | MC-W-001～005 |

## 不纳入本清单

- Goal/AI Sites 数据库、领域服务、权限和普通用户 UI。
- Site Delivery 的 Revision、Route、Domain Provider、CDN 与公网 API。
- 现有一粒云通用 AI 助手的历史任务；它们继续在 `docs/handover-yliyun-integration.md` 维护。
