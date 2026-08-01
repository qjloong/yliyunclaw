# YliyunClaw V4 Goal Agent 详细可执行落地方案、任务清单与验收标准

> 版本：V3.2  
> 日期：2026-08-01  
> 目标分支：`dev-v2`  
> 方案状态：详细设计与业务模块扩展架构基线，可进入最终评审；冻结后进入实施  
> 核心原则：**会话优先、领域数据为事实源、页面辅助、模块隔离、默认关闭、渐进上线**

---

## 0. 文档目的

本文档将已经确认的产品方向进一步细化为可直接执行的研发方案，覆盖：

1. 产品范围和首期边界；
2. MateClaw 现有能力的复用方式；
3. Goal Agent 独立模块和适配层架构；
4. 项目、需求、目标、任务、Worker、执行记录、调度、验收和报告的数据模型；
5. 项目 AI 助理的完整会话流程；
6. 后端 API、Agent Tool、事件和权限契约；
7. 前端独立模块的页面与组件设计；
8. 对已有 MateClaw 稳定功能的隔离措施；
9. 分阶段任务清单、依赖、优先级和预计工作量；
10. 产品、技术、安全、性能和上线验收标准。

本阶段不实施代码。应先完成本文档评审，冻结 P0 范围和关键技术决策。

---

# 1. 已确认的产品和技术决策

## 1.1 产品核心入口

V4 Goal Agent 的核心入口不是传统项目管理页面，而是：

> **用户与项目 AI 助理持续会话，通过会话创建项目和需求、拆分任务、生成或复用 AI Worker、安排定时分发和提醒、查询项目进度、调整计划、验收成果并导出报告。**

其他页面用于：

- 展示结构化项目数据；
- 批量修改和确认 AI 生成的内容；
- 展示任务树、Worker 状态、图表和报告；
- Agent 或模型不可用时提供手工兜底；
- 管理员审计和排障。

默认入口：

```text
/goal-agent/:projectId?/assistant
```

进入 Goal Agent 后默认打开“项目 AI 助理”。

## 1.2 事实源原则

聊天历史不是项目事实源。

项目当前状态必须来自 Goal Agent 业务数据库：

```text
项目状态
需求状态
目标状态
任务状态
任务分配
Worker 状态
任务执行记录
验收结果
调度状态
成果文件
报告快照
```

AI 助理回答“项目现在怎么样”时，必须调用 Query Tool 查询最新数据，不能仅根据历史消息推测。

## 1.3 后端模块方式

采用：

> **第一方独立 Maven 业务模块 + MateClaw Server Port/Adapter 桥接层 + Goal Agent Tool 层**

不采用现有外置 Plugin 作为完整业务承载方式。

原因：

- 当前 Plugin API 只适合 Tool、模型、Channel、Memory 和 Search 扩展；
- 完整业务需要 Controller、数据库、事务、调度、权限和前端模块；
- 直接把大量代码写入 `mateclaw-server` 会增加后续合并冲突；
- 独立 Maven 模块可以实现代码和配置隔离；
- Port/Adapter 可以避免 Goal Agent 直接依赖 MateClaw Server 内部实现。

## 1.4 对话入口复用策略

| 入口 | 定位 | 本期结论 |
|---|---|---|
| `ChatConsole` | MateClaw 内部完整聊天界面 | P0 核心复用 |
| `CloudAgentEmbed` | 一粒云内部嵌入式聊天 | P1 扩展为 Goal Agent 云盘入口 |
| `mateclaw-webchat` | 外部网站访客嵌入组件 | 不作为 P0 内部入口 |
| Goal Agent 页面 | 项目会话壳层和业务视图 | P0 新增 |

P0 不新增一套聊天 Runtime，也不复制 `/api/v1/chat/stream`。

## 1.5 Worker 策略

- 项目 AI 助理可以提出 Worker 创建建议；
- P0 默认必须由用户确认后创建；
- 优先复用已有 Agent；
- 不允许“一项任务创建一个 Worker”；
- Worker 按能力角色创建，例如“调研 Worker”“文档 Worker”“执行 Worker”；
- 每个 Worker 只能访问被授权的项目和任务；
- Worker 的每次执行必须记录为 `TaskRun`；
- Worker 结果必须写回业务状态和证据，不能只留在聊天记录中。

## 1.6 自治模式

P0 采用：

```text
半自动模式
```

允许自动执行：

- 查询和分析；
- 生成草稿；
- 生成计划建议；
- 更新 AI Worker 自己的运行心跳；
- 定时生成日报和周报草稿；
- 无副作用的状态检查。

必须确认：

- 正式创建项目；
- 批量创建任务；
- 创建或扩权 Worker；
- 修改项目范围；
- 变更任务负责人；
- 删除、取消或覆盖关键数据；
- 对外发送文件；
- 创建公开链接；
- 降低验收标准；
- Level 3/4 阻塞处理。

---

# 2. MateClaw 现有能力复用矩阵

| MateClaw 能力 | Goal Agent 使用方式 | 是否修改现有逻辑 |
|---|---|---:|
| JWT、Workspace | 复用身份和租户隔离 | 否 |
| `ChatController` | 复用内部会话和 SSE | P0 否 |
| `ChatConsole` | 作为项目 AI 助理聊天主体 | P0 否 |
| `ConversationService` | 通过 Adapter 建立和查询会话 | 否 |
| `AgentService` | 通过 Adapter 运行项目助理和 Worker | 否或仅增加稳定 Facade |
| `AgentGenerationService` | 生成 Worker 草稿 | 否 |
| Agent 创建接口/服务 | 确认后创建 Worker | 否 |
| ToolRegistry | 注册 Goal Agent Tools | 否 |
| ToolGuard/审批 | 高风险工具确认 | 否 |
| CronJob | 定时触发、提醒和周期报告 | 否 |
| 通知/Channel | 任务提醒和报告投递 | 否 |
| Wiki/知识库 | 项目资料检索 | 否 |
| MCP/一粒云工具 | 文件搜索、读取、保存和关联 | 否 |
| AuditEventService | 记录平台级操作 | 否 |
| Flyway | Goal Agent 独立迁移实例 | 不修改核心迁移 |
| 前端路由/导航 | 增加 Goal Agent 单入口 | 修改少量稳定入口 |
| Capability | 增加 Goal Agent 粗粒度权限 | 修改常量与角色映射 |

---

# 3. 总体架构

## 3.1 逻辑架构

```text
┌────────────────────────────────────────────────────────────┐
│                    Goal Agent Frontend                     │
│ GoalAgentShell / Assistant / Proposal / Task / Worker /    │
│ Acceptance / Report / Settings                             │
└───────────────────────┬────────────────────────────────────┘
                        │ REST + Goal Event SSE
┌───────────────────────▼────────────────────────────────────┐
│                  mateclaw-goal-agent                       │
│                                                            │
│ Web/API                                                    │
│ ├── Query Controller                                       │
│ ├── Command Controller                                     │
│ ├── Proposal Controller                                    │
│ └── Event/Report Controller                                │
│                                                            │
│ Application                                                │
│ ├── ProjectCommandService / ProjectQueryService            │
│ ├── ProposalService                                        │
│ ├── PlanningService                                        │
│ ├── WorkerOrchestrationService                             │
│ ├── TaskExecutionService                                   │
│ ├── AcceptanceService                                      │
│ ├── ScheduleOrchestrationService                           │
│ └── ReportService                                          │
│                                                            │
│ Domain                                                     │
│ ├── Project / Requirement / Goal / Task                    │
│ ├── WorkerBinding / Assignment / TaskRun                   │
│ ├── Acceptance / Evidence                                  │
│ ├── ScheduleBinding / Reconciliation                       │
│ └── Proposal / OperationLog / Outbox                       │
│                                                            │
│ Agent Tools                                                │
│ ├── Query Tools                                            │
│ ├── Draft Tools                                            │
│ ├── Confirm Tools                                          │
│ ├── Task Tools                                             │
│ └── Report Tools                                           │
│                                                            │
│ Ports                                                      │
│ ├── ConversationRuntimePort                                │
│ ├── AgentCatalogPort / AgentExecutionPort                  │
│ ├── SchedulerPort                                          │
│ ├── WorkspaceAccessPort                                    │
│ ├── AuditPort / NotificationPort                           │
│ └── DocumentPort / KnowledgePort                           │
└───────────────────────┬────────────────────────────────────┘
                        │ Port implementations
┌───────────────────────▼────────────────────────────────────┐
│              mateclaw-server Goal Agent Bridge             │
│ Existing Agent / Conversation / Cron / Audit / Wiki / MCP  │
└────────────────────────────────────────────────────────────┘
```

## 3.2 Maven 模块结构

根目录新增：

```text
mateclaw-goal-agent/
├── pom.xml
└── src/
    ├── main/java/vip/mate/goalagent/
    │   ├── autoconfigure/
    │   ├── web/
    │   ├── application/
    │   ├── domain/
    │   ├── infrastructure/
    │   ├── agent/
    │   ├── port/
    │   ├── event/
    │   └── common/
    ├── main/resources/
    │   ├── META-INF/spring/
    │   │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │   ├── db/goal-agent/h2/
    │   ├── db/goal-agent/mysql/
    │   ├── db/goal-agent/kingbase/
    │   └── goal-agent-defaults.yml
    └── test/
```

Server 仅新增：

```text
mateclaw-server/src/main/java/vip/mate/goalagent/bridge/
├── MateAgentCatalogAdapter.java
├── MateAgentExecutionAdapter.java
├── MateConversationAdapter.java
├── MateSchedulerAdapter.java
├── MateWorkspaceAccessAdapter.java
├── MateAuditAdapter.java
├── MateNotificationAdapter.java
├── MateDocumentAdapter.java
└── MateKnowledgeAdapter.java
```

## 3.3 依赖约束

`mateclaw-goal-agent`：

- 不依赖 `mateclaw-server`；
- 只依赖 Spring Boot、Spring Web、Spring AI Tool、MyBatis Plus、Flyway、Jackson；
- 通过 Port 接口调用 MateClaw；
- 不导入 `vip.mate.agent.*`、`vip.mate.cron.*`、`vip.mate.workspace.*` 的具体类；
- Controller 不直接操作 Mapper；
- Tool 不直接操作 Mapper；
- Adapter 不包含业务规则。

`mateclaw-server`：

- 依赖 `mateclaw-goal-agent`；
- 实现 Port；
- 不把 Goal Agent 业务状态写入现有 MateClaw 表；
- 不给现有核心表新增 Goal Agent 字段。

## 3.4 自动配置

新增：

```java
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "mateclaw.goal-agent",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class GoalAgentAutoConfiguration {
}
```

默认：

```yaml
mateclaw:
  goal-agent:
    enabled: false
```

关闭时：

- 不注册 Goal Agent Controller；
- 不注册 Goal Agent Tool；
- 不运行 Goal Agent Flyway；
- 不启动 Goal Agent 调度消费者；
- 不启动 Goal Agent Event SSE；
- 前端不显示入口。

---

# 4. 核心领域模型

## 4.1 状态机

### Project

```text
DRAFT
  → PLANNING
  → ACTIVE
  → PAUSED
  → COMPLETING
  → COMPLETED

任意非终态 → CANCELLED
```

约束：

- DRAFT 可以修改基础信息；
- PLANNING 已建立需求、目标和初始任务草稿；
- ACTIVE 才允许正式分发任务；
- COMPLETING 不再接受新增普通需求；
- COMPLETED 只能通过变更请求重新打开；
- CANCELLED 不允许恢复，需复制新项目。

### Requirement

```text
DRAFT
  → CLARIFYING
  → CONFIRMED
  → PLANNED
  → CLOSED

DRAFT/CLARIFYING → REJECTED
CONFIRMED/PLANNED → CANCELLED
```

### Goal

```text
DRAFT → ACTIVE → ACHIEVED
                → FAILED
                → CANCELLED

ACTIVE → AT_RISK → ACTIVE/ACHIEVED/FAILED
```

### Task

```text
DRAFT
  → READY
  → DISPATCHED
  → IN_PROGRESS
  → BLOCKED
  → SUBMITTED
  → ACCEPTING
  → ACCEPTED

SUBMITTED/ACCEPTING → REJECTED → IN_PROGRESS
READY/DISPATCHED/IN_PROGRESS/BLOCKED → DEFERRED
非终态 → CANCELLED
```

“执行完成”和“验收通过”严格分离。

### TaskRun

```text
QUEUED
  → RUNNING
  → WAITING_APPROVAL
  → SUCCEEDED

QUEUED/RUNNING/WAITING_APPROVAL
  → FAILED
  → TIMED_OUT
  → CANCELLED
```

### Proposal

```text
DRAFT
  → CONFIRMED
  → APPLYING
  → APPLIED

DRAFT → REJECTED
DRAFT → EXPIRED
CONFIRMED/APPLYING → FAILED
```

### WorkerBinding

```text
PROPOSED → ACTIVE → SUSPENDED → ACTIVE
                    → RETIRED
```

---

# 5. 数据模型

## 5.1 通用字段

除关系表外，业务表统一包含：

```text
id                 BIGINT
workspace_id       BIGINT
project_id         BIGINT（项目级表）
version            INT
deleted            TINYINT
created_by         VARCHAR
updated_by         VARCHAR
create_time        DATETIME/TIMESTAMP
update_time        DATETIME/TIMESTAMP
```

规则：

- ID 使用现有 Snowflake 规则；
- API 中所有 Long ID 作为字符串返回；
- JSON 数据使用 TEXT/CLOB，避免数据库专有 JSON 类型；
- 不对 `mate_agent`、`mate_conversation`、`mate_cron_job` 建物理外键；
- 跨模块引用采用逻辑 ID，并通过 Adapter 校验；
- 业务更新必须带 `expectedVersion`；
- 删除默认逻辑删除；
- 关键记录禁止硬删除。

## 5.2 P0 表清单

| # | 表名 | 用途 |
|---:|---|---|
| 1 | `ga_project` | 项目主表 |
| 2 | `ga_project_member` | Human/Agent 项目成员和 RACI |
| 3 | `ga_project_conversation` | 项目、用户、助理 Agent 和会话绑定 |
| 4 | `ga_proposal` | 会话生成的项目、任务、Worker、调度和变更提案 |
| 5 | `ga_requirement` | 轻量结构化需求 |
| 6 | `ga_goal` | 多级目标 |
| 7 | `ga_key_result` | 目标关键结果 |
| 8 | `ga_work_board` | 工作板块 |
| 9 | `ga_task` | 任务 |
| 10 | `ga_task_dependency` | 任务依赖 |
| 11 | `ga_task_assignment` | Human/Worker 任务分配 |
| 12 | `ga_task_run` | AI Worker 每次执行记录 |
| 13 | `ga_acceptance_rule` | 验收规则 |
| 14 | `ga_acceptance_result` | 验收执行结果 |
| 15 | `ga_evidence` | 文件、消息、日志和人工确认证据 |
| 16 | `ga_daily_plan` | 日计划 |
| 17 | `ga_daily_plan_item` | 日计划任务快照 |
| 18 | `ga_reconciliation` | 日/周/月对账和复盘 |
| 19 | `ga_document_link` | 项目、需求、任务、报告与文件关系 |
| 20 | `ga_agent_binding` | 项目助理和 Worker 与 `mate_agent` 的绑定 |
| 21 | `ga_schedule_binding` | 业务调度与 `mate_cron_job` 的绑定 |
| 22 | `ga_operation_log` | 业务操作审计 |
| 23 | `ga_outbox_event` | 事务事件和可靠推送 |

P1 增加：

```text
ga_report_snapshot
ga_risk
ga_blocker
```

P2 增加：

```text
ga_change_request
ga_release
ga_process_template
ga_worker_productivity
```

## 5.3 关键表字段

### `ga_project`

```text
id
workspace_id
name
code
description
project_type
status
priority
owner_user_id
assistant_agent_id
start_date
target_end_date
actual_end_date
timezone
autonomy_mode
source_type
source_conversation_id
current_phase
health_status
progress
config_json
version
deleted
created_by
updated_by
create_time
update_time
```

索引：

```text
idx_ga_project_workspace_status(workspace_id, status)
idx_ga_project_owner(owner_user_id, status)
uk_ga_project_workspace_code(workspace_id, code, deleted)
```

### `ga_project_conversation`

```text
id
workspace_id
project_id
user_id
assistant_agent_id
conversation_id
status
is_primary
context_version
last_active_at
version
create_time
update_time
```

唯一索引：

```text
uk_ga_project_conv(conversation_id)
uk_ga_project_user_primary(project_id, user_id, is_primary, status)
```

### `ga_proposal`

```text
id
workspace_id
project_id
conversation_id
proposal_type
title
summary
payload_json
diff_json
status
expected_project_version
expires_at
confirmed_by
confirmed_at
applied_at
failure_reason
idempotency_key
version
create_time
update_time
```

`proposal_type`：

```text
PROJECT_SETUP
REQUIREMENT
GOAL_PLAN
TASK_PLAN
WORKER_PLAN
SCHEDULE_PLAN
PROJECT_CHANGE
REPORT_EXPORT
```

### `ga_requirement`

```text
id
workspace_id
project_id
parent_requirement_id
title
raw_text
clarified_description
priority
status
source_type
source_conversation_id
source_document_id
acceptance_summary
confirmed_by
confirmed_at
version
create_time
update_time
```

### `ga_task`

```text
id
workspace_id
project_id
requirement_id
goal_id
board_id
parent_task_id
title
description
status
priority
task_type
planned_start_at
planned_end_at
estimated_minutes
actual_minutes
progress
acceptance_status
is_ai_executable
dispatch_mode
block_reason
defer_reason
version
create_time
update_time
```

### `ga_task_assignment`

```text
id
workspace_id
project_id
task_id
assignee_type        HUMAN / AGENT
assignee_id
agent_binding_id
assignment_role
status
assigned_at
unassigned_at
version
create_time
update_time
```

### `ga_task_run`

```text
id
workspace_id
project_id
task_id
assignment_id
agent_binding_id
agent_id
conversation_id
runtime_run_id
trigger_type
attempt_no
status
request_json
result_summary
result_json
error_code
error_message
prompt_tokens
completion_tokens
started_at
finished_at
timeout_at
idempotency_key
version
create_time
update_time
```

唯一索引：

```text
uk_ga_task_run_idempotency(workspace_id, idempotency_key)
idx_ga_task_run_task_status(task_id, status)
idx_ga_task_run_project_status(project_id, status, create_time)
```

### `ga_agent_binding`

```text
id
workspace_id
project_id
mate_agent_id
binding_type          PROJECT_ASSISTANT / WORKER
scope_type            PROJECT / WORKSPACE_SHARED
role_code
display_name
capability_tags_json
allowed_tool_names_json
status
created_from_proposal_id
max_concurrent_runs
daily_token_budget
version
create_time
update_time
```

### `ga_schedule_binding`

```text
id
workspace_id
project_id
mate_cron_job_id
schedule_type
target_type
target_id
cron_expression
timezone
status
delivery_policy_json
last_dispatch_at
last_success_at
failure_count
version
create_time
update_time
```

---

# 6. 会话驱动业务设计

## 6.1 会话上下文解析

每个 Goal Agent Tool 都从运行上下文解析：

```text
conversationId
workspaceId
userId/username
agentId
channel
projectId（由绑定表解析）
```

项目解析优先级：

1. 当前 `conversationId` 已绑定项目；
2. Goal Agent 页面显式选择的 `projectId`；
3. 当前用户最近活跃项目；
4. 无项目时要求用户选择或创建。

模型传入的 `projectId` 仅作为提示，不能作为授权依据。

## 6.2 项目创建流程

```text
用户：帮我创建一个“V4 Goal Agent”项目……
  ↓
项目 AI 助理调用 ga_get_context
  ↓
调用 ga_draft_project
  ↓
ProposalService 创建 PROJECT_SETUP 提案
  ↓
AI 返回项目摘要、待澄清项和提案编号
  ↓
用户补充信息
  ↓
ga_patch_proposal 更新提案
  ↓
用户：确认创建
  ↓
ga_confirm_proposal
  ↓
事务内创建：
  - ga_project
  - ga_project_member
  - ga_project_conversation
  - 初始 ga_requirement
  - 初始 ga_goal
  - 默认 ga_work_board
  - ga_operation_log
  - ga_outbox_event
  ↓
返回项目创建结果
```

任何步骤失败：

- Proposal 保留；
- 业务数据不部分提交；
- 状态变为 FAILED；
- 用户可以修复后重试；
- 相同 `idempotencyKey` 不重复创建项目。

## 6.3 需求澄清流程

AI 至少检查：

```text
业务目标
交付结果
范围
不做什么
目标用户
时间限制
预算/资源
验收标准
依赖
风险
```

需求确认前：

- 可以反复修改；
- 不生成正式任务；
- 只保存 DRAFT/CLARIFYING。

确认后：

- Requirement 状态变为 CONFIRMED；
- 可以生成 Goal/Task Plan Proposal；
- 后续变更必须产生新的 `PROJECT_CHANGE` Proposal。

## 6.4 任务规划流程

```text
CONFIRMED Requirement
  ↓
ga_draft_task_plan
  ↓
规则校验：
  - 每个任务可追溯需求/目标
  - 每个任务有验收规则
  - AI 任务必须可由允许工具执行
  - 依赖无环
  - 单日容量不超限
  - 截止时间不早于前置任务
  ↓
TASK_PLAN Proposal
  ↓
用户自然语言调整或页面批量修改
  ↓
确认
  ↓
批量创建 ga_task、dependency、acceptance_rule
```

## 6.5 会话查询

必须提供结构化 Query Tool：

```text
ga_get_project_overview
ga_list_requirements
ga_list_goals
ga_list_tasks
ga_get_task_detail
ga_list_workers
ga_list_task_runs
ga_get_schedule_status
ga_get_acceptance_summary
ga_get_report_data
```

Query Tool 结果必须包含：

```text
data_version
generated_at
filters
summary
items
warnings
```

AI 回答中注明数据截止时间。

## 6.6 会话调整

用户说：

```text
把“完成数据库设计”的截止日期改到下周三
```

流程：

```text
AI 查询任务和当前版本
  ↓
生成 PROJECT_CHANGE Proposal
  ↓
显示影响：
  - 受影响依赖任务
  - 项目截止日期风险
  - Worker 排期冲突
  ↓
用户确认
  ↓
乐观锁更新
  ↓
写操作日志和事件
```

普通低风险字段可配置为“确认一次后自动执行”；P0 默认仍确认。

---

# 7. 项目 AI 助理与 Worker

## 7.1 项目 AI 助理模型

P0 推荐：

- 每个 Workspace 创建一个默认“项目 AI 助理” Agent；
- 多个项目共享 Agent 定义；
- Conversation 绑定项目上下文；
- 每个项目允许后续覆盖为专属 Assistant Agent；
- 不为每个新项目自动复制一个 Assistant Agent。

优点：

- 避免 Agent 数量爆炸；
- 统一升级系统提示词和工具；
- 减少模型、Skill、Tool 配置重复；
- 项目隔离由 Conversation Binding 和 Tool Context 保证。

## 7.2 项目 AI 助理系统约束

系统提示必须包含：

1. 每次开始先调用 `ga_get_context`；
2. 不根据聊天历史猜测项目状态；
3. 所有写操作先生成 Proposal；
4. 未确认不得执行高风险写入；
5. 不允许使用用户输入的项目 ID 绕过上下文；
6. 解释每次计划的依据；
7. 对不确定信息明确追问；
8. 调用报告工具时使用最新项目快照；
9. 遇到权限错误不得尝试其他项目；
10. 不直接删除文件、创建公开链接或外发资料。

## 7.3 Worker 复用和创建算法

匹配顺序：

```text
项目内 ACTIVE Worker
  ↓ 无合适
Workspace Shared Worker
  ↓ 无合适
生成 Worker Proposal
```

匹配维度：

```text
能力标签         35%
已绑定工具匹配   25%
历史任务类型     15%
当前负载         15%
项目权限         10%
```

P0 阈值：

```text
匹配分 >= 75：建议复用
50–74：提供复用和新建两个选项
< 50：建议新建
```

限制：

```text
每项目默认最多 5 个 Worker
每 Workspace 默认最多 30 个 Goal Agent Worker
每 Worker 最大并发 1
默认每日 Token 预算可配置
禁止自动授予 manage:security/manage:settings 类能力
```

## 7.4 Worker Proposal

Proposal 内容：

```json
{
  "roleCode": "research_worker",
  "displayName": "项目调研 Worker",
  "goal": "完成项目相关调研任务并提交可验证报告",
  "systemPrompt": "...",
  "agentType": "plan_execute",
  "tools": ["web_search", "document_extract", "ga_worker_submit_result"],
  "skillIds": [],
  "primaryKbId": null,
  "capabilityTags": ["research", "analysis", "report"],
  "projectScope": "...",
  "estimatedMonthlyCost": "...",
  "reusedAgentId": null
}
```

确认时再次校验：

- Agent/Tool/Skill 是否仍存在；
- Tool 是否可用；
- 当前用户是否有创建 Agent 权限；
- 是否超过配额；
- 是否包含禁止工具；
- Workspace 是否匹配。

## 7.5 Worker 执行流程

```text
任务 READY
  ↓
TaskExecutionService.dispatch
  ↓
创建 ga_task_run(QUEUED)
  ↓
并发/预算/依赖检查
  ↓
更新 RUNNING
  ↓
AgentExecutionPort.execute
  ↓
Worker 调用项目查询和执行工具
  ↓
生成结果和成果文件
  ↓
ga_worker_submit_result
  ↓
TaskRun SUCCEEDED
  ↓
Task 状态 SUBMITTED
  ↓
Evidence 写入
  ↓
AcceptanceService 评估
```

失败：

```text
可重试错误 → 指数退避，最多 2 次
权限/工具错误 → 不自动重试，进入 FAILED
模型不可用 → 切换可用模型一次
超时 → TIMED_OUT
人工取消 → CANCELLED
```

每次重试创建同一 Run 的新 attempt，或创建新 Run，并通过 `parent_run_id` 关联；实施时二选一并冻结。

---

# 8. 调度和提醒设计

## 8.1 复用 CronJob

Goal Agent 不复制 Cron 引擎。

`ga_schedule_binding` 负责业务语义，`mate_cron_job` 负责触发。

## 8.2 调度类型

| 类型 | 目标 | 默认行为 |
|---|---|---|
| `TASK_DISPATCH` | AI Task | 到点创建 TaskRun |
| `TASK_REMINDER` | Human Task | 通知负责人 |
| `PROGRESS_CHECK` | Project/Task | 查询状态并生成异常事件 |
| `DAILY_SUMMARY` | Project | 生成昨日对账和今日计划草稿 |
| `WEEKLY_REPORT` | Project | 生成周报草稿 |
| `DEADLINE_SCAN` | Project | 扫描逾期和风险 |
| `ACCEPTANCE_RETRY` | Task | 补充证据后重新验收 |

## 8.3 幂等键

```text
{scheduleBindingId}:{businessDate}:{targetId}:{triggerType}
```

同一幂等键只允许产生一个业务动作。

## 8.4 时间规则

- 所有项目保存 `timezone`；
- CronJob 使用项目时区；
- 日结基于项目本地日期；
- 夏令时切换必须测试；
- 调度误差目标不超过 60 秒；
- 服务停机恢复后补偿最近 24 小时内未执行任务；
- 补偿不重复发送已成功通知。

## 8.5 通知降噪

- 同一任务 30 分钟内只发送一次同类提醒；
- 项目级异常优先聚合；
- 非关键提醒进入每日摘要；
- 用户可以设置免打扰时段；
- Worker 连续失败只发一次升级通知；
- 报告投递失败记录并允许手工重发。

---

# 9. Agent Tool 设计

## 9.1 Tool 分组

### 上下文与查询

```text
ga_get_context
ga_list_projects
ga_get_project_overview
ga_list_requirements
ga_list_goals
ga_list_tasks
ga_get_task_detail
ga_list_workers
ga_list_task_runs
ga_get_acceptance_summary
ga_get_schedule_status
ga_get_report_data
```

### 草稿与提案

```text
ga_draft_project
ga_draft_requirement
ga_draft_goal_plan
ga_draft_task_plan
ga_draft_worker_plan
ga_draft_schedule_plan
ga_draft_project_change
ga_patch_proposal
ga_get_proposal
```

### 确认和命令

```text
ga_confirm_proposal
ga_reject_proposal
ga_update_task_progress
ga_block_task
ga_submit_task
ga_add_evidence
ga_request_acceptance
ga_cancel_task_run
```

### Worker 专用

```text
ga_worker_get_assignment
ga_worker_get_project_context
ga_worker_save_artifact
ga_worker_report_progress
ga_worker_report_blocker
ga_worker_submit_result
```

### 报告

```text
ga_export_project_report
ga_export_task_report
ga_export_acceptance_report
```

## 9.2 Tool 风险等级

| 风险 | Tool 类型 | 策略 |
|---|---|---|
| R0 | 纯查询 | 自动 |
| R1 | 创建/更新 Proposal | 自动 |
| R2 | 更新普通任务进度 | 可配置自动 |
| R3 | 应用 Proposal、创建 Worker、批量任务 | 必须确认 |
| R4 | 删除、外发、公开链接、扩权 | 强制审批 |

## 9.3 Tool 返回规范

```json
{
  "success": true,
  "code": "GA_OK",
  "message": "Proposal created",
  "dataVersion": 12,
  "projectId": "193...",
  "proposalId": "194...",
  "data": {},
  "warnings": [],
  "nextActions": [
    {
      "action": "CONFIRM_PROPOSAL",
      "label": "确认创建"
    }
  ]
}
```

Tool 不返回内部堆栈、数据库 SQL、密钥或跨 Workspace 信息。

---

# 10. REST API 契约

统一前缀：

```text
/api/v1/goal-agent
```

统一 Header：

```text
Authorization
X-Workspace-Id
X-Idempotency-Key（写请求）
If-Match / expectedVersion（更新请求）
```

## 10.1 Project

```text
GET    /projects
GET    /projects/{projectId}
GET    /projects/{projectId}/overview
POST   /projects
PATCH  /projects/{projectId}
POST   /projects/{projectId}/activate
POST   /projects/{projectId}/pause
POST   /projects/{projectId}/complete
POST   /projects/{projectId}/cancel
```

## 10.2 Conversation Binding

```text
GET    /projects/{projectId}/conversations/current
POST   /projects/{projectId}/conversations/bind
POST   /projects/{projectId}/conversations/archive
GET    /conversations/{conversationId}/context
GET    /conversations/{conversationId}/pending-proposals
```

## 10.3 Proposal

```text
GET    /proposals/{proposalId}
PATCH  /proposals/{proposalId}
POST   /proposals/{proposalId}/confirm
POST   /proposals/{proposalId}/reject
```

## 10.4 Requirement/Goal/Task

```text
GET    /projects/{projectId}/requirements
POST   /projects/{projectId}/requirements
PATCH  /requirements/{id}

GET    /projects/{projectId}/goals
POST   /projects/{projectId}/goals
PATCH  /goals/{id}

GET    /projects/{projectId}/tasks
GET    /tasks/{id}
POST   /projects/{projectId}/tasks
PATCH  /tasks/{id}
POST   /tasks/{id}/assign
POST   /tasks/{id}/dispatch
POST   /tasks/{id}/block
POST   /tasks/{id}/submit
POST   /tasks/{id}/cancel
```

## 10.5 Worker/Run

```text
GET    /projects/{projectId}/workers
GET    /workers/{bindingId}
POST   /workers/{bindingId}/suspend
POST   /workers/{bindingId}/resume
POST   /workers/{bindingId}/retire

GET    /projects/{projectId}/runs
GET    /runs/{runId}
POST   /runs/{runId}/cancel
POST   /runs/{runId}/retry
```

## 10.6 Acceptance/Evidence

```text
GET    /tasks/{taskId}/acceptance
POST   /tasks/{taskId}/acceptance/evaluate
POST   /tasks/{taskId}/acceptance/manual
GET    /tasks/{taskId}/evidence
POST   /tasks/{taskId}/evidence
```

## 10.7 Schedule/Report/Event

```text
GET    /projects/{projectId}/schedules
POST   /projects/{projectId}/schedules
PATCH  /schedules/{id}
POST   /schedules/{id}/pause
POST   /schedules/{id}/resume
POST   /schedules/{id}/run-now

POST   /projects/{projectId}/reports/export
GET    /projects/{projectId}/reports
GET    /reports/{reportId}

GET    /projects/{projectId}/events
GET    /projects/{projectId}/operations
```

---

# 11. 事件和实时同步

## 11.1 Outbox 事件

事务内写：

```text
ga_operation_log
ga_outbox_event
```

提交后由 Dispatcher 投递。

事件类型：

```text
project.created
project.updated
requirement.confirmed
goal.updated
task.created
task.updated
task.blocked
task.assigned
task.run.started
task.run.completed
task.run.failed
worker.created
worker.updated
proposal.created
proposal.applied
proposal.failed
acceptance.completed
schedule.triggered
report.generated
```

## 11.2 投递语义

- 至少一次；
- 消费方必须幂等；
- 失败指数退避；
- 最大失败次数后进入 DEAD；
- 管理页面可重放；
- SSE 只负责通知，数据库是事实源。

## 11.3 前端 SSE

```text
GET /api/v1/goal-agent/projects/{projectId}/events?lastEventId=...
```

前端收到事件后：

- 根据事件局部更新 Pinia；
- 版本不连续时重新拉取项目 Overview；
- SSE 断开后指数退避；
- 超过 30 秒无法恢复时退化为轮询；
- 不在 SSE Payload 中传输完整敏感文件内容。

---

# 12. 前端详细设计

## 12.1 目录

```text
mateclaw-ui/src/features/goal-agent/
├── api/
│   ├── project.ts
│   ├── proposal.ts
│   ├── requirement.ts
│   ├── task.ts
│   ├── worker.ts
│   ├── acceptance.ts
│   ├── schedule.ts
│   ├── report.ts
│   └── event.ts
├── components/
│   ├── shell/
│   ├── assistant/
│   ├── proposal/
│   ├── task/
│   ├── worker/
│   ├── acceptance/
│   ├── report/
│   └── adapted/
├── composables/
│   ├── useGoalAgentContext.ts
│   ├── useProjectConversation.ts
│   ├── useProjectEvents.ts
│   ├── useProposal.ts
│   └── useProjectPermission.ts
├── layouts/
│   └── GoalAgentLayout.vue
├── pages/
│   ├── AssistantPage.vue
│   ├── WorkbenchPage.vue
│   ├── GoalsPage.vue
│   ├── RequirementsTasksPage.vue
│   ├── WorkersRunsPage.vue
│   ├── AcceptancePage.vue
│   ├── ReportsPage.vue
│   └── ProjectSettingsPage.vue
├── stores/
│   ├── project.ts
│   ├── proposal.ts
│   ├── task.ts
│   ├── worker.ts
│   └── event.ts
├── styles/
│   └── goal-agent.css
├── types/
└── routes.ts
```

## 12.2 Assistant 页面

```text
┌─────────────────────────────────────────────────────┐
│ 项目选择器 | 项目状态 | 模式 | 运行提醒             │
├────────────────────────────────┬────────────────────┤
│                                │ 项目上下文          │
│ Embedded ChatConsole           │ 待确认 Proposal     │
│                                │ Worker 运行         │
│                                │ 今日风险            │
├────────────────────────────────┴────────────────────┤
│ 可折叠：最近操作、定时任务、成果                    │
└─────────────────────────────────────────────────────┘
```

P0 使用：

```vue
<ChatConsole embedded />
```

通过 Route Query 传递：

```text
agentId
conversationId
projectId
```

Goal Agent Host 独立监听 Goal Agent Event SSE，右侧展示 Proposal 和运行状态，不改 ChatConsole。

## 12.3 Proposal 交互

支持：

- 查看 AI 摘要；
- 展开结构化内容；
- 单项修改；
- 对话中修改；
- 页面表格批量修改；
- 确认；
- 拒绝；
- 过期提示；
- 应用失败重试；
- 显示影响范围。

## 12.4 页面复用原则

```text
直接满足需求 → 复用
只需外围适配 → Wrapper
需要内部行为变化 → 复制到 adapted/
成熟且通用 → 单独 PR 提取 shared
```

禁止：

- 在现有 ChatConsole 中堆积 Goal Agent `if`；
- 修改全局 `.card/.sidebar/.btn`；
- 给通用 Store 加 Goal Agent 专属状态；
- 在现有 API 文件中混入大量 Goal Agent 方法。

---

# 13. 权限与安全

## 13.1 Capability

新增：

```text
view:goal-agent
manage:goal-agent
admin:goal-agent
```

建议角色：

| Role | 权限 |
|---|---|
| viewer | `view:goal-agent` |
| member | view + `manage:goal-agent` |
| admin/owner | view + manage + `admin:goal-agent` |

项目级权限仍需二次校验。

## 13.2 项目角色

```text
OWNER
MANAGER
MEMBER
VIEWER
APPROVER
WORKER
```

RACI 单独保存：

```text
R / A / C / I
```

## 13.3 强制校验

每个写操作：

```text
JWT
→ Workspace Membership
→ Capability
→ Project Membership
→ Resource Workspace/Project
→ Version
→ Idempotency
→ Business State
→ Tool Risk Policy
```

## 13.4 Worker 安全边界

Worker 只能获得：

- 当前项目只读上下文；
- 当前分配任务；
- 被允许的文件范围；
- 被允许的 Tool 白名单；
- 保存成果和回报状态的 Tool。

Worker 不允许：

- 创建其他 Worker；
- 修改项目权限；
- 修改 Workspace 设置；
- 修改模型配置；
- 查看其他项目；
- 删除核心数据；
- 创建公开外链；
- 给自己扩权。

---

# 14. 数据库迁移与回滚

## 14.1 独立 Flyway 历史

Goal Agent 使用独立：

```text
flyway_schema_history_goal_agent
```

迁移目录：

```text
db/goal-agent/h2
db/goal-agent/mysql
db/goal-agent/kingbase
```

优点：

- 不占用 MateClaw 核心迁移版本；
- 不与上游新增迁移产生版本冲突；
- Goal Agent 可以独立校验和升级；
- Feature Flag 关闭时不执行。

## 14.2 实施前技术验证

必须先完成 Spike：

1. dependency JAR 中 AutoConfiguration 是否加载；
2. MyBatis Mapper 是否能被独立模块扫描；
3. 第二个 Flyway 实例是否在核心 Flyway 后运行；
4. H2/MySQL/Kingbase 三套 SQL 是否一致；
5. 关闭 Feature Flag 是否完全不执行迁移；
6. 模块升级失败是否阻止 Goal Agent Bean 启动，但不破坏核心数据。

## 14.3 回滚策略

代码回滚：

```text
mateclaw.goal-agent.enabled=false
```

数据库：

- 不自动删除表；
- 保留数据；
- 修复后重新启用；
- 不执行 destructive down migration；
- 必要时提供独立离线清理脚本；
- 核心 MateClaw 不依赖 `ga_*` 表，因此关闭后不影响原业务。

---

# 15. 稳定性与资源隔离

## 15.1 绝对禁止项

P0 禁止：

- 修改现有 Agent、Conversation、Plan、Cron 表结构；
- 修改 ChatController 既有协议；
- 修改 ChatStreamTracker 既有行为；
- Goal Agent 关闭时启动任何后台任务；
- Worker 无限制并发；
- 失败任务无限重试；
- AI 直接应用未经确认的大范围修改；
- Goal Agent 异常导致整个应用启动失败且无法禁用。

## 15.2 资源配额

默认：

```yaml
mateclaw:
  goal-agent:
    execution:
      max-concurrent-runs-per-node: 8
      max-concurrent-runs-per-workspace: 4
      max-concurrent-runs-per-project: 2
      max-workers-per-project: 5
      max-workers-per-workspace: 30
      task-timeout-minutes: 30
      max-retries: 2
      queue-capacity: 500
    report:
      max-export-rows: 20000
      max-file-size-mb: 50
    scheduler:
      minimum-interval-minutes: 5
```

## 15.3 熔断

以下任一条件触发自动暂停 Goal Agent Worker 调度：

- 连续 10 次模型调用失败；
- DB 连接池使用率持续超过 90%；
- Worker 队列超过 80%；
- 同一 Workspace 1 小时错误率超过 30%；
- Cron backlog 超过配置阈值；
- Outbox DEAD 事件持续增加。

只暂停 Goal Agent，不影响普通 Chat 和其他 Channel。

---

# 16. 分阶段实施计划

## 16.1 人员假设

推荐团队：

```text
后端 2 人
前端 2 人
测试 1 人
产品/UI 0.5 人
DevOps 0.5 人
```

预计：

- P0 MVP：8 周；
- P1 智能增强：4–6 周；
- 单人全栈实施：约 16–20 周。

---

# 17. 详细任务清单

以下任务完成后必须满足统一 Definition of Done：

- 代码已 Review；
- 单元测试通过；
- 集成测试通过；
- API/数据结构文档已更新；
- Feature Flag 关闭场景已测试；
- 无跨 Workspace 数据泄露；
- 无静态扫描高危问题；
- 新增监控和日志；
- 可回滚；
- 不降低现有 MateClaw 回归测试通过率。

---

## Epic A：方案冻结与技术 Spike

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| A-01 | P0 | 冻结产品词典和 P0 边界 | 领域词典、范围表 | 无 | 1d |
| A-02 | P0 | 冻结状态机 | 状态迁移图和错误码 | A-01 | 1d |
| A-03 | P0 | 冻结权限矩阵 | Capability+项目角色矩阵 | A-01 | 1d |
| A-04 | P0 | 验证独立 Maven AutoConfiguration | 可运行 Demo | 无 | 1d |
| A-05 | P0 | 验证独立 Mapper 扫描 | CRUD Test | A-04 | 1d |
| A-06 | P0 | 验证独立 Flyway 实例 | H2/MySQL 测试 | A-04 | 2d |
| A-07 | P0 | 验证后台 Agent 执行调用链 | AgentRun Spike | 无 | 2d |
| A-08 | P0 | 验证 ChatConsole embedded 项目绑定 | 前端 Demo | 无 | 2d |
| A-09 | P0 | 确认报告生成技术路线 | Word/PDF/Excel Spike | 无 | 1d |
| A-10 | P0 | 输出 ADR | ADR-001~006 | A-04~09 | 1d |

验收：

- 独立模块可在 Feature Flag 开启时加载；
- 关闭时无 Controller、Tool、Migration 和 Scheduler；
- 后台能调用一个测试 Agent 并获取结果；
- ChatConsole 可以恢复指定 Agent/Conversation；
- Flyway 不写核心历史表。

---

## Epic B：模块骨架与基础设施

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| B-01 | P0 | 新增 Maven 模块 | `mateclaw-goal-agent` | A-04 | 1d |
| B-02 | P0 | 根 POM 和 Server 依赖 | 可编译 Reactor | B-01 | 0.5d |
| B-03 | P0 | AutoConfiguration 和 Properties | 条件启用 | B-01 | 1d |
| B-04 | P0 | Port 接口 | 8–10 个 Port | A-10 | 2d |
| B-05 | P0 | Server Bridge 包 | Adapter 骨架 | B-04 | 2d |
| B-06 | P0 | 统一错误码 | `GA_*` | A-02 | 1d |
| B-07 | P0 | 统一响应和分页 DTO | API 基础类 | B-01 | 1d |
| B-08 | P0 | Actor/Project Context Resolver | 安全上下文 | B-04 | 2d |
| B-09 | P0 | Idempotency 组件 | 幂等校验 | B-01 | 2d |
| B-10 | P0 | Optimistic Lock 规范 | Version Guard | B-01 | 1d |
| B-11 | P0 | Operation Log | 审计服务 | B-06 | 2d |
| B-12 | P0 | Outbox 基础 | Producer/Dispatcher | B-01 | 3d |
| B-13 | P0 | 模块健康检查 | `/health` 子项 | B-03 | 1d |
| B-14 | P0 | Metrics | 队列/错误/运行指标 | B-12 | 1d |

验收：

- 独立模块通过 `mvn test`；
- Server 启动时能识别所有 Adapter；
- Flag 关闭时 `/api/v1/goal-agent/**` 返回 404；
- 幂等和乐观锁有自动测试；
- Outbox 重复投递不产生重复业务结果。

---

## Epic C：数据库和领域核心

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| C-01 | P0 | Project/Member/Conversation 表 | 迁移+实体+Mapper | A-06 | 2d |
| C-02 | P0 | Proposal 表 | 迁移+实体+Mapper | A-06 | 1d |
| C-03 | P0 | Requirement/Goal/KR 表 | 迁移+实体+Mapper | A-06 | 2d |
| C-04 | P0 | Board/Task/Dependency 表 | 迁移+实体+Mapper | A-06 | 3d |
| C-05 | P0 | Assignment/TaskRun 表 | 迁移+实体+Mapper | A-06 | 2d |
| C-06 | P0 | Acceptance/Evidence 表 | 迁移+实体+Mapper | A-06 | 2d |
| C-07 | P0 | DailyPlan/Reconciliation 表 | 迁移+实体+Mapper | A-06 | 2d |
| C-08 | P0 | Document/Agent/Schedule Binding 表 | 迁移+实体+Mapper | A-06 | 2d |
| C-09 | P0 | Operation/Outbox 表 | 迁移+实体+Mapper | A-06 | 1d |
| C-10 | P0 | H2 SQL | 完整迁移 | C-01~09 | 2d |
| C-11 | P0 | MySQL SQL | 完整迁移 | C-01~09 | 2d |
| C-12 | P0 | Kingbase SQL | 完整迁移 | C-01~09 | 2d |
| C-13 | P0 | 数据库索引评审 | 索引清单 | C-01~12 | 1d |
| C-14 | P0 | 状态机领域测试 | 状态迁移测试 | A-02 | 2d |

验收：

- 三种数据库迁移可重复执行；
- 所有关键唯一约束生效；
- 不创建到 MateClaw 核心表的外键；
- 逻辑删除和 Version 更新正确；
- 依赖关系可检测环；
- 10 万 Task 数据下核心列表查询索引命中。

---

## Epic D：Project、Proposal 和会话闭环

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| D-01 | P0 | ProjectCommandService | 项目命令 | B/C | 3d |
| D-02 | P0 | ProjectQueryService | 项目查询 | C-01 | 2d |
| D-03 | P0 | ProposalService | Draft/Patch/Confirm | C-02 | 4d |
| D-04 | P0 | Proposal Materializer | 多实体事务应用 | D-03 | 4d |
| D-05 | P0 | Conversation Binding Service | 项目会话绑定 | C-01/B-08 | 2d |
| D-06 | P0 | Requirement Service | 澄清/确认 | C-03 | 3d |
| D-07 | P0 | Goal Service | 目标/KR | C-03 | 2d |
| D-08 | P0 | Task Planning Service | 任务草案和校验 | C-04 | 4d |
| D-09 | P0 | Capacity/Dependency Validator | 容量、依赖、日期校验 | D-08 | 3d |
| D-10 | P0 | Project REST API | Project/Overview | D-01/02 | 2d |
| D-11 | P0 | Proposal REST API | Proposal API | D-03/04 | 2d |
| D-12 | P0 | Requirement/Goal/Task API | CRUD+命令 | D-06~09 | 3d |
| D-13 | P0 | 项目创建 E2E | 自动化用例 | D-01~12 | 2d |

验收：

- 从 Proposal 确认到项目落库是单事务；
- 相同 Idempotency Key 不重复创建；
- Proposal 可修改、拒绝、过期和重试；
- 项目会话只能绑定当前 Workspace 项目；
- 未确认需求不能创建正式任务；
- 任务计划无环且都有验收规则。

---

## Epic E：Goal Agent Tools 和项目 AI 助理

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| E-01 | P0 | Goal Agent Tool 基础类 | Context/Error/Result | B-08 | 2d |
| E-02 | P0 | Context/Query Tools | 12 个查询 Tool | D | 4d |
| E-03 | P0 | Proposal Tools | Draft/Patch/Get | D-03 | 4d |
| E-04 | P0 | Confirm/Reject Tools | 应用提案 | D-04 | 2d |
| E-05 | P0 | Task Command Tools | 进度/阻塞/提交 | D-08 | 3d |
| E-06 | P0 | Tool 风险策略 | R0–R4 | A-03 | 2d |
| E-07 | P0 | 项目助理系统 Prompt | Prompt V1 | E-01~06 | 2d |
| E-08 | P0 | 默认项目助理初始化 | Workspace seed | E-07 | 2d |
| E-09 | P0 | Tool 审计 | Tool→Operation Log | B-11 | 1d |
| E-10 | P0 | 会话创建项目 E2E | 对话脚本 | E-01~09 | 3d |

验收：

- Query Tool 不接受可伪造 Workspace；
- 写 Tool 必须生成或应用 Proposal；
- 高风险操作必须进入审批；
- 项目助理可以纯会话完成项目和需求创建；
- Tool 输出不泄露内部异常；
- 会话查询结果与 REST Overview 一致。

---

## Epic F：Worker 编排与任务执行

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| F-01 | P0 | AgentCatalog Adapter | 查询现有 Agent | B-05 | 2d |
| F-02 | P0 | Worker Match Service | 复用评分 | F-01 | 3d |
| F-03 | P0 | Worker Proposal Service | Worker 方案 | D-03/F-02 | 3d |
| F-04 | P0 | AgentGeneration Adapter | 生成 Agent 草稿 | B-05 | 2d |
| F-05 | P0 | Worker Provisioning | 确认后创建/绑定 | F-03/04 | 4d |
| F-06 | P0 | Worker Tool Whitelist | 权限过滤 | E-06 | 2d |
| F-07 | P0 | Assignment Service | Human/Agent 分配 | C-05 | 3d |
| F-08 | P0 | AgentExecution Adapter | 后台执行 | A-07/B-05 | 4d |
| F-09 | P0 | TaskExecution Queue | 并发和队列 | F-08 | 4d |
| F-10 | P0 | TaskRun Service | 状态、重试、取消 | C-05/F-09 | 4d |
| F-11 | P0 | Worker Result Tools | 进度/阻塞/成果 | E-01 | 3d |
| F-12 | P0 | Budget/Quota | Worker/Token 配额 | F-09 | 2d |
| F-13 | P0 | Worker/Run API | 查询和命令 | F-05~12 | 3d |
| F-14 | P0 | Worker 执行 E2E | 成功/失败/超时 | F-01~13 | 4d |

验收：

- 优先复用已有 Agent；
- 未确认不能创建 Worker；
- Worker 不包含禁止工具；
- Worker 不能读取其他项目；
- 每次执行有 TaskRun；
- 重试、超时和取消正确；
- 重复 Dispatch 不产生重复执行；
- Worker 成果自动关联 Task Evidence。

---

## Epic G：调度、提醒和对账

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| G-01 | P0 | Scheduler Adapter | CronJob Port | B-05 | 2d |
| G-02 | P0 | Schedule Service | Binding CRUD | C-08/G-01 | 3d |
| G-03 | P0 | Schedule Proposal | 对话生成调度 | D-03/G-02 | 2d |
| G-04 | P0 | Task Dispatch Handler | 到期运行 | F-10 | 3d |
| G-05 | P0 | Reminder Handler | Human 通知 | B-05 | 2d |
| G-06 | P0 | Daily Summary | 日结草稿 | C-07 | 4d |
| G-07 | P0 | Weekly Report Draft | 周报草稿 | C-07 | 3d |
| G-08 | P0 | Deadline Scan | 逾期/风险 | D-08 | 2d |
| G-09 | P0 | Schedule Idempotency | 防重复 | B-09 | 2d |
| G-10 | P0 | Compensation | 停机补偿 | G-02~09 | 3d |
| G-11 | P0 | Notification Dedup | 通知降噪 | G-05 | 2d |
| G-12 | P0 | 调度 E2E | 时区/重复/补偿 | G-01~11 | 3d |

验收：

- 定时任务误差不超过 60 秒；
- 多实例只执行一次业务动作；
- 停机恢复不重复分发；
- 日结按项目时区生成；
- 同类提醒不会频繁轰炸；
- CronJob 删除/停用同步更新 Binding 状态。

---

## Epic H：验收、证据和报告

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| H-01 | P0 | Evidence Service | 文件/日志/人工证据 | C-06 | 3d |
| H-02 | P0 | Acceptance Rule Engine | 数量/时间/文件/人工 | C-06 | 4d |
| H-03 | P0 | Auto Acceptance | 自动验收 | H-01/02 | 4d |
| H-04 | P0 | Manual Acceptance | 人工确认 | H-02 | 2d |
| H-05 | P0 | Acceptance API/Tools | 验收命令 | H-01~04 | 3d |
| H-06 | P0 | Report Query Model | 项目快照 | D/F/G/H | 3d |
| H-07 | P0 | Markdown/HTML Report | 会话报告 | H-06 | 2d |
| H-08 | P0 | DOCX Export | Word | A-09/H-06 | 3d |
| H-09 | P0 | PDF Export | PDF | A-09/H-06 | 3d |
| H-10 | P0 | Excel Export | 任务/验收表 | A-09/H-06 | 3d |
| H-11 | P0 | Report Tool | 会话导出 | H-07~10 | 2d |
| H-12 | P0 | 报告可追溯 | 文件关联和数据版本 | H-06 | 2d |
| H-13 | P0 | 验收/报告 E2E | 完整闭环 | H-01~12 | 3d |

验收：

- Task 只有验收通过才计入正式完成率；
- 自动无法判断时进入人工确认；
- 报告包含数据截止时间和项目版本；
- 相同参数可重现；
- 导出文件有权限控制；
- 报告内容与项目数据库一致；
- 大数据导出受行数和文件大小限制。

---

## Epic I：前端 Goal Agent 模块

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| I-01 | P0 | Feature 目录和 Route | 独立入口 | B-03 | 1d |
| I-02 | P0 | GoalAgentLayout | 独立业务壳 | I-01 | 3d |
| I-03 | P0 | Project Selector/Context | 项目上下文 | D-10 | 2d |
| I-04 | P0 | Conversation Host | ChatConsole embedded | A-08/D-05 | 4d |
| I-05 | P0 | Proposal Drawer | 查看/修改/确认 | D-11 | 4d |
| I-06 | P0 | Project Overview Store/API | Overview | D-10 | 3d |
| I-07 | P0 | Assistant Page | 主入口 | I-03~06 | 4d |
| I-08 | P0 | Workbench | 今日任务/风险 | D/G | 4d |
| I-09 | P0 | Goals Page | 目标树 | D-12 | 4d |
| I-10 | P0 | Requirement/Task Page | 列表/看板 | D-12 | 5d |
| I-11 | P0 | Worker/Run Page | Worker 和运行 | F-13 | 4d |
| I-12 | P0 | Acceptance Page | 验收和证据 | H-05 | 4d |
| I-13 | P0 | Reports Page | 查询和导出 | H-11 | 3d |
| I-14 | P0 | Project Settings | 模式/时区/通知 | D/G | 3d |
| I-15 | P0 | Goal Event SSE | 实时刷新 | B-12 | 3d |
| I-16 | P0 | Capability/导航/i18n | 核心入口修改 | A-03 | 2d |
| I-17 | P0 | 高保真样式还原 | 原型对齐 | I-02~14 | 5d |
| I-18 | P0 | 响应式和可访问性 | PC/窄屏 | I-17 | 2d |
| I-19 | P0 | Frontend E2E | Playwright/Vitest | I-01~18 | 4d |

验收：

- 默认进入项目 AI 助理；
- 不修改 ChatConsole 主业务逻辑；
- Goal Agent CSS 不污染现有页面；
- Proposal 可以会话修改和页面修改；
- Worker 状态实时刷新；
- 页面刷新后项目和会话可恢复；
- Capability 不足时不可见且 API 返回 403；
- Feature Flag 关闭时菜单和路由均不可用。

---

## Epic J：测试、安全和上线

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| J-01 | P0 | Domain Unit Test | 关键分支覆盖 | C/D/F/G/H | 5d |
| J-02 | P0 | Adapter Contract Test | MateClaw 适配测试 | B-05 | 3d |
| J-03 | P0 | H2 Integration Test | CI | 全后端 | 3d |
| J-04 | P0 | MySQL Testcontainers | CI | 全后端 | 3d |
| J-05 | P0 | Kingbase Staging Test | 测试报告 | C-12 | 3d |
| J-06 | P0 | 权限渗透用例 | 跨租户/越权 | 全后端 | 4d |
| J-07 | P0 | Tool Prompt Injection Test | 安全报告 | E/F | 3d |
| J-08 | P0 | Load Test | Chat/Run/SSE/DB | 全系统 | 4d |
| J-09 | P0 | Feature Flag Off Regression | 原业务回归 | 全系统 | 4d |
| J-10 | P0 | Upstream Merge Drill | 冲突报告 | 全代码 | 2d |
| J-11 | P0 | Backup/Restore Drill | 数据恢复报告 | DB | 2d |
| J-12 | P0 | Rollback Drill | 关闭模块 | 全系统 | 2d |
| J-13 | P0 | Gray Release | 单 Workspace | J-01~12 | 3d |
| J-14 | P0 | Production Gate Review | Go/No-Go | J-13 | 1d |

---

# 18. 推荐排期

## Sprint 0（第 1 周）

```text
A 全部
B-01~08
C 数据模型评审
I-01~04 原型验证
```

里程碑：

> 独立模块、聊天嵌入、后台 Agent 执行和独立 Flyway 技术风险全部解除。

## Sprint 1（第 2 周）

```text
B 剩余
C-01~09
D-01~06
I-02~07
```

里程碑：

> 能通过 REST 创建项目、绑定 Conversation、建立 Requirement 和 Proposal。

## Sprint 2（第 3 周）

```text
C-10~14
D-07~13
E-01~10
I-08~10
```

里程碑：

> 可以纯会话创建项目、确认需求并生成任务。

## Sprint 3（第 4 周）

```text
F-01~07
G-01~03
I-11
J 基础测试
```

里程碑：

> 可以生成 Worker Proposal、确认创建和完成任务分配。

## Sprint 4（第 5 周）

```text
F-08~14
G-04~12
I-15
```

里程碑：

> AI Worker 可以执行任务，定时分发和提醒可用。

## Sprint 5（第 6 周）

```text
H-01~05
G 日结/周报
I-12
```

里程碑：

> 任务提交、证据和验收闭环可用。

## Sprint 6（第 7 周）

```text
H-06~13
I-13~18
```

里程碑：

> 会话查询进度和导出报告可用，核心页面完成。

## Sprint 7（第 8 周）

```text
I-19
J 全部
缺陷修复
灰度上线
```

里程碑：

> P0 Go/No-Go。

---

# 19. 产品验收场景

## AC-01 会话创建项目

**Given**

- 用户已登录；
- 用户有 `manage:goal-agent`；
- Goal Agent 已启用。

**When**

用户对项目 AI 助理说：

```text
创建一个“V4 Goal Agent”项目，目标是 8 周完成 MVP……
```

**Then**

- AI 生成项目 Proposal；
- 未确认前没有正式 Project；
- 用户可补充和修改；
- 用户确认后生成 Project；
- 当前 Conversation 自动绑定 Project；
- 操作日志记录来源会话和确认人；
- 重复确认不会重复创建。

## AC-02 创建并确认需求

- AI 能识别需求缺失项；
- 未明确验收条件时必须追问；
- 确认后 Requirement 状态为 CONFIRMED；
- Requirement 能追溯原始会话和资料；
- 后续修改产生 Change Proposal。

## AC-03 自动拆分任务

- 每个任务关联 Requirement 或 Goal；
- 每个任务有验收规则；
- 依赖无环；
- 时间和容量校验通过；
- 未确认前为 Proposal；
- 确认后批量创建；
- 批量事务失败不会创建一半。

## AC-04 Worker 建议和创建

- AI 先搜索已有 Worker；
- 能复用时不默认新建；
- 新建 Worker 显示工具、技能、模型和权限；
- 用户确认后才创建；
- Worker 不能获得禁止能力；
- 超过配额时拒绝。

## AC-05 任务分发和执行

- 到点只创建一个 TaskRun；
- Worker 执行状态可查询；
- Worker 可回报进度和阻塞；
- 成功后任务进入 SUBMITTED；
- 失败后显示原因；
- 重试次数受限；
- 用户可取消运行。

## AC-06 定时提醒

- 使用项目时区；
- 到点误差小于 60 秒；
- 重启后可补偿；
- 重复触发不重复执行；
- 同类提醒不高频重复；
- 投递失败可查询和重发。

## AC-07 验收

- 任务完成不等于验收通过；
- 自动验收记录每条规则结果；
- 无法判断的规则转人工；
- 验收不通过可退回；
- 验收通过后才计入完成率。

## AC-08 查询实时状态

用户询问：

```text
项目目前进度如何？哪些任务延期？Worker 在做什么？
```

系统必须：

- 查询最新数据库；
- 返回数据截止时间；
- 返回任务、Worker、风险摘要；
- 不根据旧聊天记录编造；
- 页面 Overview 与会话结果一致。

## AC-09 导出报告

用户要求导出周报：

- 系统生成固定数据快照；
- 生成 Word/PDF/Excel 中指定格式；
- 报告包含统计截止时间和版本；
- 文件有权限；
- 报告与数据库数据一致；
- 会话返回可用下载链接；
- 文件关联到项目。

## AC-10 Feature Flag 关闭

- MateClaw 正常启动；
- 普通 Chat 正常；
- Agent 管理正常；
- Cron 正常；
- WebChat 正常；
- Goal Agent 路由不可见；
- Goal Agent Controller/Tool/Scheduler 不加载；
- 不执行 Goal Agent Migration；
- 已有 `ga_*` 数据保留。

---

# 20. 技术验收标准

## 20.1 正确性

- 命令幂等测试 100% 通过；
- 乐观锁冲突返回 409；
- Proposal 应用具备事务原子性；
- 所有项目级查询强制 Workspace 和 Project 校验；
- 任务依赖环检测正确；
- 状态机非法迁移全部拒绝。

## 20.2 测试覆盖

- Domain/Application 关键分支覆盖率不低于 85%；
- Adapter 有 Contract Test；
- H2 和 MySQL CI 必须通过；
- Kingbase 上线前完成独立验证；
- 10 条核心 E2E 全部通过；
- Feature Flag Off 原业务回归全部通过。

## 20.3 性能

不含外部 LLM 响应时间：

```text
Project Overview P95 < 500ms
Task List（1000 条分页）P95 < 700ms
普通 Command P95 < 800ms
Proposal Confirm（100 Task）P95 < 2s
SSE 事件投递延迟 P95 < 2s
调度误差 < 60s
```

容量基线：

```text
单项目 10,000 Task
单 Workspace 100 活跃项目
单节点 50 Chat SSE
单节点 8 Worker Run
Outbox 100,000 历史事件
```

## 20.4 稳定性

- Goal Agent 异常不影响普通 Chat；
- Worker 队列满时拒绝新任务，不拖垮 Server；
- DB 连接无泄漏；
- SSE 断开自动清理；
- 失败重试有上限；
- 模型不可用时可暂停 Goal Agent；
- 灰度期间核心接口错误率低于 1%。

## 20.5 安全

- 跨 Workspace 访问全部返回 403；
- 跨项目访问全部返回 403；
- 伪造 ProjectId 不生效；
- Worker 不可自我扩权；
- 文件操作遵循一粒云权限；
- 日志不记录密钥和完整敏感文件内容；
- 报告下载链接不可猜测且有时效；
- Prompt Injection 用例不能绕过 Tool 权限。

---

# 21. 上线门禁

满足以下全部条件才允许 P0 上线：

1. ADR 已签字；
2. P0 范围冻结；
3. 10 个产品验收场景通过；
4. H2/MySQL/Kingbase 验证通过；
5. Feature Flag Off 回归通过；
6. Upstream Merge Drill 通过；
7. 备份恢复和关闭模块演练通过；
8. 无未处理 P0/P1 安全问题；
9. Worker 并发和 Token 预算已配置；
10. 单 Workspace 灰度运行不少于 5 个工作日；
11. 灰度期间无核心 MateClaw 功能回归；
12. 产品、研发、测试和运维共同 Go。

---

# 22. 建议文档清单

```text
docs/v4-goal-agent/
├── README.md
├── 00-decisions/
│   ├── ADR-001-module-boundary.md
│   ├── ADR-002-conversation-first.md
│   ├── ADR-003-proposal-confirmation.md
│   ├── ADR-004-worker-runtime.md
│   ├── ADR-005-scheduler-reuse.md
│   └── ADR-006-independent-flyway.md
├── 01-product-scope.md
├── 02-system-architecture.md
├── 03-domain-model-and-state-machine.md
├── 04-database-design.md
├── 05-api-contract.md
├── 06-agent-tools-and-prompts.md
├── 07-conversation-flows.md
├── 08-worker-execution.md
├── 09-scheduler-and-notification.md
├── 10-frontend-integration.md
├── 11-security-and-permission.md
├── 12-observability-and-operations.md
├── 13-test-and-acceptance.md
├── 14-release-and-rollback.md
└── 15-implementation-backlog.md
```

原：

```text
feasibility-and-plan.md
```

调整为总体索引和决策摘要，不再承载所有详细设计。

---

# 23. 实施前最后需要确认的决策

| # | 决策 | 推荐值 |
|---:|---|---|
| 1 | P0 默认自治模式 | 半自动 |
| 2 | 默认项目助理 | Workspace 共享 Agent |
| 3 | 每项目 Worker 上限 | 5 |
| 4 | Worker 最大并发 | 1 |
| 5 | 项目最大并发 Run | 2 |
| 6 | 自动重试次数 | 2 |
| 7 | TaskRun 超时 | 30 分钟 |
| 8 | Proposal 有效期 | 7 天 |
| 9 | 日结时间 | 项目时区 00:00 |
| 10 | 早间计划时间 | 项目时区 09:00 |
| 11 | 报告默认格式 | DOCX + PDF |
| 12 | P0 是否支持公开 WebChat | 否 |
| 13 | P0 是否支持跨项目调度 | 否 |
| 14 | P0 是否支持 Worker 自动创建 | 否，必须确认 |
| 15 | P0 是否允许 L2 自动重排 | 否，先提案确认 |
| 16 | Flyway | 独立历史表 |
| 17 | ChatController | P0 不修改 |
| 18 | Goal Agent 默认开关 | false |

---

# 24. 最终实施结论

本方案在不重构 MateClaw 核心 Chat、Agent、Conversation 和 Cron 主链路的前提下，实现：

```text
会话创建项目
→ 需求澄清
→ 任务规划
→ Worker 建议/创建
→ 定时分发
→ Worker 执行
→ 证据与验收
→ 状态查询
→ 日结/周报
→ 会话导出报告
```

实施优先级必须始终遵循：

```text
会话闭环
> 项目事实数据
> Worker 执行可靠性
> 调度和验收
> 报告
> 页面丰富度
> 高级 PM 功能
```

完成本方案评审并冻结第 23 节决策后，可正式进入 Sprint 0。

---

# 25. V3.1 补充决策：管理页面、动态配置、作用域与路线图治理

> 本节替代原第 23 节中与“模块启用、自治模式、配置生效”有关的简单开关结论。

## 25.1 是否建设 Goal Agent 管理页面

**结论：必须建设，而且分为平台、工作空间和项目三个层级。**

Goal Agent 不是只依赖 `application.yml` 的静态模块。除部署级安全开关外，日常运营配置应支持类似 teacher-plugin 管理页的在线修改和动态生效，但配置模型不能只是简单的全局 Key-Value；必须支持作用域、版本、审核、影响预览、回滚和运行快照。

### 25.1.1 平台管理页面

路由建议：

```text
/settings/goal-agent/platform
```

访问权限：

```text
Global Admin
```

页面包含：

1. 模块部署状态和版本；
2. 平台运行总开关；
3. 平台默认配置；
4. 不可被工作空间突破的安全上限；
5. Worker 总并发、队列和 Token 预算；
6. Tool 禁止清单；
7. 调度、Outbox、TaskRun 和报告服务健康度；
8. 数据库迁移版本；
9. 配置发布记录和回滚；
10. P1/P2 已实现功能的 Feature Flag；
11. 全平台紧急暂停和运行中任务处置策略。

### 25.1.2 工作空间管理页面

路由建议：

```text
/settings/goal-agent
```

访问权限：

```text
Workspace Owner / Admin
```

页面包含：

1. 当前工作空间是否启用 Goal Agent；
2. 默认自治模式；
3. 默认项目 AI 助理；
4. 每项目 Worker 上限；
5. 工作空间 Worker 并发；
6. Worker 创建是否必须确认；
7. TaskRun 超时和重试次数；
8. 默认日结、早间计划和周报时间；
9. 通知渠道和免打扰时间；
10. 默认报告格式；
11. 默认验收策略；
12. 允许使用的 Tool、模型和知识库范围；
13. 新建项目继承策略；
14. 是否将某次配置发布应用到已有项目；
15. 配置影响预览、历史版本和回滚。

工作空间配置不能超过平台安全上限。例如：

```text
平台 maxWorkersPerProject = 10
工作空间只能设置 0–10，不能设置 20
```

### 25.1.3 项目设置页面

路由：

```text
/goal-agent/:projectId/settings
```

访问权限：

```text
Project Owner / Manager
```

页面包含：

1. 项目是否允许继续自动分发；
2. 项目自治模式；
3. 项目 AI 助理绑定；
4. 项目 Worker 上限；
5. 项目并发 TaskRun 上限；
6. 项目时区；
7. 日结、日报和周报策略；
8. 项目通知范围；
9. 验收策略；
10. 文件和报告保存位置；
11. 配置继承或固定版本；
12. 当前有效配置来源；
13. 项目配置历史和回滚。

项目不能覆盖平台硬限制，也不能突破工作空间授权范围。

---

## 25.2 四层启用逻辑

Goal Agent 的启用不是“只选平台”或“只选工作空间”，而是四层共同决定。

```text
部署级加载开关
  AND 平台运行总开关
  AND 工作空间启用开关
  AND 项目运行状态/项目开关
  AND 当前用户权限
```

有效性公式：

```text
GoalAgentAvailable =
    deploymentGate
    && platformRuntimeEnabled
    && workspaceEnabled
    && projectEnabled
    && userHasCapability
```

### 25.2.1 第一层：部署级加载开关

配置：

```yaml
mateclaw:
  goal-agent:
    enabled: false
```

性质：

- 平台级；
- 默认关闭；
- 由运维修改；
- 修改后需要重启；
- 决定 Bean、Controller、Tool、Scheduler 和独立 Flyway 是否加载；
- 不能从普通管理页面动态打开。

目的：

- 出现模块级严重问题时彻底隔离；
- 保证 Goal Agent 不影响 MateClaw 核心启动；
- 支持安全回滚。

关闭后的表现：

```text
Goal Agent 页面不注册
Goal Agent API 返回 404
Goal Agent Tool 不注册
Goal Agent 调度器不启动
Goal Agent Flyway 不运行
已有 ga_* 数据保留
普通 Chat/Agent/Cron/WebChat 不受影响
```

### 25.2.2 第二层：平台运行总开关

配置项：

```text
platformRuntimeEnabled
```

性质：

- 平台级；
- 数据库存储；
- Global Admin 动态修改；
- 无需重启；
- 用于运营暂停，不卸载代码和数据库。

关闭时默认行为：

- 禁止新建 Goal Agent 项目；
- 禁止创建新 Proposal；
- 禁止分发新的 Worker TaskRun；
- 暂停 Goal Agent 业务调度；
- 保留只读查询、报告下载和管理诊断；
- 已经运行的 TaskRun 按“平台停用策略”处理。

平台停用策略：

```text
DRAIN：不接收新任务，运行中的任务允许完成（默认）
CANCEL：取消仍在运行的 Goal Agent TaskRun
READ_ONLY：只读，禁用全部写操作
```

### 25.2.3 第三层：工作空间启用开关

配置项：

```text
workspaceEnabled
```

性质：

- 按 Workspace 隔离；
- Workspace Owner/Admin 动态修改；
- 无需重启；
- 不影响其他 Workspace。

关闭某工作空间时：

- 该 Workspace 的 Goal Agent 菜单隐藏或显示为已停用；
- 不允许创建新项目、Proposal、Worker 和 TaskRun；
- 暂停该 Workspace 的业务调度；
- 数据和报告保留；
- 其他 Workspace 正常运行；
- 已运行 TaskRun 根据 Workspace 停用策略 DRAIN/CANCEL 处理。

### 25.2.4 第四层：项目运行开关

项目级不建议再定义一个与 Project Status 重复的简单 `enabled` 字段，建议使用：

```text
project.status
project.execution_enabled
```

规则：

- `ACTIVE + execution_enabled=true`：正常执行；
- `ACTIVE + execution_enabled=false`：允许查询和人工修改，不自动分发；
- `PAUSED`：不自动分发，不创建新 Run；
- `COMPLETED/CANCELLED`：只读和报告；
- 项目暂停不影响其他项目。

---

## 25.3 配置数据模型

V3.2 最终决策：配置版本、发布、回滚和作用域继承属于所有第一方业务模块的共性能力，
不再由 Goal Agent 单独建设 `ga_config*` 表。统一由 `mateclaw-business-runtime` 提供，
Goal Agent 只提供自己的配置 Schema、默认值、校验器和影响分析扩展。

新增通用表：

### `mate_business_module`

```text
id
module_key                     唯一标识，例如 goal-agent / teacher
display_name
module_type                    FIRST_PARTY / EXTERNAL_BRIDGE
installed_version
config_schema_version
platform_enabled
runtime_state                  ACTIVE / READ_ONLY / DRAINING / DISABLED
health_status
last_health_message
last_health_at
version
create_time
update_time
```

唯一索引：

```text
uk_business_module_key(module_key)
```

### `mate_business_module_config`

```text
id
module_key
scope_type                     PLATFORM / WORKSPACE / RESOURCE
scope_id                       PLATFORM=0；WORKSPACE=workspace_id；RESOURCE=业务资源 ID
resource_type                  PROJECT / SUBJECT / RULE_PACK / NULL
workspace_id                   便于权限和查询；平台配置为空
current_revision_id
inherit_mode                   FOLLOW_PARENT / PINNED
runtime_state                  ACTIVE / READ_ONLY / DRAINING / DISABLED
version
created_by
updated_by
create_time
update_time
```

唯一索引：

```text
uk_module_config_scope(
  module_key,
  scope_type,
  scope_id,
  resource_type
)
```

### `mate_business_module_config_revision`

```text
id
module_key
config_id
revision_no
schema_version
config_json
diff_json
change_type                    DRAFT / PUBLISH / ROLLBACK / MIGRATION
change_reason
impact_summary_json
apply_existing_policy
status                         DRAFT / VALIDATED / PUBLISHED / FAILED / SUPERSEDED
created_by
published_by
published_at
create_time
```

### `mate_business_module_config_application`

记录一次配置发布对 Workspace 或业务资源的实际应用情况：

```text
id
module_key
config_revision_id
workspace_id
resource_type
resource_id
status                         PENDING / APPLYING / APPLIED / SKIPPED / FAILED
before_revision_id
after_revision_id
failure_reason
applied_at
create_time
update_time
```

### Goal Agent 运行快照字段

`ga_task_run` 增加：

```text
business_config_revision_id
config_snapshot_json
```

`ga_project` 增加：

```text
config_binding_mode            FOLLOW_WORKSPACE / PINNED
business_config_revision_id
```

### 表归属

```text
mate_business_module*
  → 由 mateclaw-business-runtime 管理
  → 所有第一方业务模块复用

ga_project / ga_task / ga_task_run 等
  → 由 mateclaw-goal-agent 管理
  → 只保存 Goal Agent 领域数据
```
## 25.4 配置继承和优先级

配置解析顺序：

```text
代码安全默认值
  ↓
平台默认配置
  ↓
平台硬限制
  ↓
工作空间覆盖
  ↓
项目覆盖
  ↓
TaskRun 创建时运行快照
```

优先级规则：

```text
项目覆盖 > 工作空间覆盖 > 平台默认
平台硬限制始终最高，不能被下级突破
```

示例：

```text
平台硬限制：Worker 每项目最多 10
平台默认：Worker 每项目 3
Workspace A：Worker 每项目 5
Project X：Worker 每项目 4
Project X 有效值 = 4
```

如果 Project X 设置为 15：

```text
保存配置时拒绝，返回 GA_CONFIG_LIMIT_EXCEEDED
```

---

## 25.5 配置动态生效分类

所有配置不能简单定义为“保存后全部立即生效”。根据风险分为四类。

### A 类：即时生效

下一次 API 请求、Tool 调用或调度扫描即使用新值。

包括：

- 页面显示；
- 通知开关；
- 报告默认格式；
- 默认筛选条件；
- 免打扰时间；
- 新任务是否允许自动分发；
- 工作空间和项目运行开关；
- 新建项目默认值。

对现有项目：

- 立即影响后续新操作；
- 不修改已保存业务数据；
- 不修改已经生成的报告。

### B 类：下一次执行生效

在创建新的 `TaskRun` 时读取并固化配置快照。

包括：

- TaskRun 超时；
- 重试次数；
- Worker 并发；
- Token 预算；
- 模型路由；
- Tool 白名单；
- Worker Prompt 模板；
- 输出文件限制。

对正在运行的 TaskRun：

- 不热替换；
- 继续使用创建时的 `config_snapshot_json`；
- 新 Run 使用新配置；
- 紧急禁用 Tool 时可以通过平台安全策略强制中止相关 Run。

### C 类：已有项目需显式应用

包括：

- 默认自治模式；
- Worker 创建审批规则；
- 任务自动调整规则；
- 验收策略；
- Goal/Task 模板；
- 日结和复盘流程；
- 项目 Assistant 系统 Prompt；
- 项目工作流状态机扩展。

保存时管理员选择：

```text
仅作为新项目默认值（默认）
应用到所选项目
应用到全部 FOLLOW_WORKSPACE 项目
```

应用到已有项目必须：

1. 生成影响预览；
2. 列出受影响项目；
3. 校验项目状态；
4. 建立 `mate_business_module_config_application`；
5. 分批应用；
6. 写 Operation Log；
7. 失败可重试；
8. 支持回滚到前一 Revision。

`PINNED` 项目不会自动接收 Workspace 行为配置变更。

### D 类：需要重启

包括：

- 部署级模块加载开关；
- 数据源和数据库迁移配置；
- Bean 装配方式；
- Goal Agent 独立线程池实现类型；
- 底层消息中间件切换；
- 加密主密钥；
- 数据存储根路径的结构性迁移。

管理页面只展示当前值和“需要重启”，不假装动态生效。

---

## 25.6 对现有运行项目的影响规则

### 新建项目

新项目创建时：

1. 解析当前平台和 Workspace 有效配置；
2. 创建项目配置快照；
3. 保存 `config_revision_id`；
4. 默认 `config_binding_mode=FOLLOW_WORKSPACE`；
5. 项目 Manager 可以改为 `PINNED`。

### 已有项目

| 配置类型 | 已有项目 | 正在运行 TaskRun |
|---|---|---|
| A 即时类 | 后续请求立即生效 | 通常不影响 |
| B 执行类 | 下一次 Run 生效 | 保持旧快照 |
| C 行为类 | 默认不自动变更 | 保持旧快照 |
| D 启动类 | 重启后生效 | 按停机策略处理 |

### 配置发布保护

发布工作空间配置前，页面必须显示：

```text
配置差异
超过平台限制的字段
受影响的新项目默认值
FOLLOW_WORKSPACE 项目数量
PINNED 项目数量
运行中 TaskRun 数量
待执行调度数量
是否会暂停 Worker
是否需要项目 Manager 再确认
```

### 回滚

配置回滚是发布一个旧快照的新 Revision，不直接覆盖历史。

```text
Revision 6 发布错误
→ 选择回滚到 Revision 5
→ 创建 Revision 7，内容等同 Revision 5
```

这样审计链不会丢失。

---

## 25.7 管理页面保存和发布流程

不采用“修改表单后直接覆盖”的简单模式。

```text
编辑配置
  ↓
前端校验
  ↓
POST /config/drafts
  ↓
后端类型、范围和安全上限校验
  ↓
生成 Diff 和影响预览
  ↓
管理员确认发布
  ↓
POST /config/drafts/{id}/publish
  ↓
生成 Revision
  ↓
发布 config.changed Outbox Event
  ↓
各节点清理缓存/加载新 Revision
  ↓
按即时/下一次执行/显式应用规则生效
```

支持：

- 保存草稿；
- 测试配置；
- 查看 Diff；
- 发布；
- 回滚；
- 导出和导入；
- 复制到其他 Workspace；
- 审计变更原因；
- 查看各节点当前加载 Revision。

---

## 25.8 配置缓存和多节点一致性

建议：

- 数据库是配置事实源；
- 本地使用短 TTL Cache；
- 发布后写 `config.changed` Outbox Event；
- 每个节点收到事件后失效对应 Scope Cache；
- 每次创建 TaskRun 时强制读取当前 Revision；
- 节点错过事件时最多在 TTL 后自动更新；
- 管理页面展示各节点加载版本。

一致性目标：

```text
单节点：发布后 2 秒内生效
多节点：P95 5 秒内生效
```

配置 Revision 未一致前，不创建依赖新配置的高风险 Worker。

---

## 25.9 配置 API

平台：

```text
GET  /api/v1/goal-agent/admin/config/platform
POST /api/v1/goal-agent/admin/config/platform/drafts
GET  /api/v1/goal-agent/admin/config/platform/drafts/{id}/impact
POST /api/v1/goal-agent/admin/config/platform/drafts/{id}/publish
POST /api/v1/goal-agent/admin/config/platform/revisions/{revisionId}/rollback
GET  /api/v1/goal-agent/admin/runtime/status
POST /api/v1/goal-agent/admin/runtime/pause
POST /api/v1/goal-agent/admin/runtime/resume
```

工作空间：

```text
GET  /api/v1/goal-agent/config/workspace
POST /api/v1/goal-agent/config/workspace/drafts
GET  /api/v1/goal-agent/config/workspace/drafts/{id}/impact
POST /api/v1/goal-agent/config/workspace/drafts/{id}/publish
POST /api/v1/goal-agent/config/workspace/revisions/{revisionId}/rollback
GET  /api/v1/goal-agent/config/workspace/revisions
```

项目：

```text
GET   /api/v1/goal-agent/projects/{projectId}/config
PATCH /api/v1/goal-agent/projects/{projectId}/config
POST  /api/v1/goal-agent/projects/{projectId}/config/pin
POST  /api/v1/goal-agent/projects/{projectId}/config/follow-workspace
GET   /api/v1/goal-agent/projects/{projectId}/config/effective
GET   /api/v1/goal-agent/projects/{projectId}/config/history
```

---

## 25.10 配置页面验收标准

### CFG-AC-01 动态修改

- Workspace Admin 修改通知和报告格式；
- 不重启服务；
- 5 秒内读取到新配置；
- 其他 Workspace 不受影响；
- 保存完整审计记录。

### CFG-AC-02 新旧 Run 隔离

- TaskRun A 已开始；
- 管理员修改超时和 Tool 白名单；
- A 继续使用原快照；
- 新建 TaskRun B 使用新配置；
- A/B 均记录各自 Revision。

### CFG-AC-03 已有项目保护

- Workspace 修改自治模式；
- 默认选择“仅新项目”；
- 已有项目配置不变；
- 新建项目继承新值；
- 选择“应用到已有项目”时先展示影响预览。

### CFG-AC-04 Workspace 隔离

- 禁用 Workspace A；
- A 不再创建新 Run；
- Workspace B 正常；
- A 数据和报告仍可查询；
- A 的运行任务按 DRAIN 策略完成。

### CFG-AC-05 平台紧急暂停

- Global Admin 执行平台 DRAIN；
- 所有 Workspace 不再接收新 Run；
- 正在执行的 Run 允许结束；
- 普通 MateClaw Chat 和非 Goal Agent Cron 正常。

### CFG-AC-06 回滚

- Revision 6 发布后发现异常；
- 回滚到 Revision 5；
- 生成 Revision 7；
- 新操作使用 Revision 7；
- 历史 Revision 和操作人完整保留。

---

# 26. P1、P2 和未来计划的记录与治理

## 26.1 不再只写在可行性文档中

`feasibility-and-plan.md` 只保留：

- 背景；
- 总体边界；
- 核心决策；
- 当前阶段摘要；
- 指向详细文档的索引。

P1/P2/Future 必须进入独立、可追踪的 Roadmap 和 Backlog。

## 26.2 推荐目录

```text
docs/v4-goal-agent/
├── README.md
├── roadmap/
│   ├── ROADMAP.md
│   ├── CAPABILITY-MATRIX.md
│   ├── RELEASE-PLAN.md
│   └── FUTURE-IDEAS.md
├── backlog/
│   ├── P0/
│   ├── P1/
│   ├── P2/
│   └── FUTURE/
├── status/
│   ├── IMPLEMENTATION-STATUS.md
│   ├── TEST-EVIDENCE.md
│   └── RELEASE-HISTORY.md
├── decisions/
│   └── ADR-*.md
└── design/
    └── 各详细设计文档
```

## 26.3 ROADMAP.md

采用：

```text
NOW       当前正在实施
NEXT      已批准，等待实施
LATER     已确认方向，未排期
DISCOVERY 仅调研，尚未承诺
DROPPED   已决定不实施
```

每个能力必须记录：

```text
Feature ID
名称
阶段 P0/P1/P2/Future
问题和价值
范围
非范围
依赖
风险
进入条件
验收标准
Feature Flag
目标版本
状态
负责人
关联 Issue/PR/ADR
```

## 26.4 CAPABILITY-MATRIX.md

示例：

| 能力 | P0 | P1 | P2 | Future | Flag |
|---|---:|---:|---:|---:|---|
| 会话创建项目 | ✓ | | | | core |
| Worker 建议/确认创建 | ✓ | | | | worker |
| 自动证据采集 | 基础 | 增强 | | | evidence-auto |
| 风险预测 | | ✓ | | | risk-ai |
| 动态重排 | | ✓ | 增强 | | auto-reschedule |
| 跨项目调度 | | | ✓ | | cross-project |
| 外部 WebChat 项目协作 | | | ✓ | | external-collab |
| 项目数字孪生 | | | | Discovery | digital-twin |

## 26.5 Backlog 文档

一个 Epic 一个文档：

```text
backlog/P1/P1-E01-risk-forecast.md
backlog/P1/P1-E02-auto-reschedule.md
backlog/P2/P2-E01-cross-project-scheduling.md
```

每个文档包含：

1. 用户价值；
2. 业务场景；
3. 领域模型变化；
4. API/Tool/UI 变化；
5. 数据迁移；
6. 安全影响；
7. 任务拆解；
8. 验收标准；
9. 上线和回滚；
10. 是否影响已有项目配置。

## 26.6 GitHub 追踪

文档是设计事实源，GitHub Issue 是执行事实源。

推荐 Label：

```text
goal-agent
phase:p0
phase:p1
phase:p2
phase:future
type:epic
type:task
type:spike
type:adr
area:backend
area:frontend
area:agent
area:scheduler
area:config
area:security
status:blocked
status:ready
```

推荐 Milestone：

```text
Goal Agent P0 MVP
Goal Agent P1 Intelligence
Goal Agent P2 PM Extension
Goal Agent Future Discovery
```

每个任务必须同时有：

```text
文档 Task ID
GitHub Issue
代码 PR
测试证据
发布版本
```

## 26.7 实施状态

`IMPLEMENTATION-STATUS.md` 不能只写百分比，应记录：

| ID | 状态 | PR | 测试 | 环境 | 备注 |
|---|---|---|---|---|---|
| F-08 | IN_PROGRESS | #xxx | Contract 待补 | dev | AgentExecution Adapter |

状态：

```text
NOT_STARTED
DESIGNING
READY
IN_PROGRESS
IN_REVIEW
TESTING
DONE
BLOCKED
DEFERRED
DROPPED
```

## 26.8 P1 建议内容

P1：智能增强，预计 P0 稳定后 4–6 周。

```text
P1-E01 风险和阻塞模型
P1-E02 自动证据采集
P1-E03 规则 + LLM 混合验收
P1-E04 动态重排 Proposal
P1-E05 周/月/季度计划
P1-E06 报告模板和固定快照
P1-E07 项目资料自动复用
P1-E08 Workspace PM Robot
P1-E09 Goal Agent 富消息卡片
P1-E10 CloudAgentEmbed 项目上下文
P1-E11 配置灰度发布
P1-E12 项目运行成本分析
```

P1 进入条件：

- P0 连续稳定运行 4 周；
- 核心错误率低于 1%；
- 无 P0/P1 安全问题；
- TaskRun 成功率达到目标；
- 配置和回滚机制验证通过；
- 产品确认 P1 优先顺序。

## 26.9 P2 建议内容

P2：项目管理扩展。

```text
P2-E01 Sprint/Iteration
P2-E02 Risk Register
P2-E03 Blocker Governance
P2-E04 Change Request
P2-E05 Release Management
P2-E06 Process Template
P2-E07 跨项目 Worker 调度
P2-E08 Worker 生产力分析
P2-E09 外部参与者和 WebChat
P2-E10 项目邀请和协作
P2-E11 多项目管理驾驶舱
P2-E12 开放 Goal Agent Extension API
```

P2 进入条件：

- P1 核心功能稳定；
- 有真实多项目调度需求；
- 明确权限和数据合规要求；
- 现有 Workspace 规模证明需要扩展；
- 不因追求 PM 功能而破坏会话优先原则。

## 26.10 Future Ideas

未来创意放入 `FUTURE-IDEAS.md`，默认不进入承诺排期。

每条标记：

```text
IDEA
DISCOVERY
VALIDATED
PROMOTED_TO_P2
REJECTED
```

只有完成用户验证、技术 Spike 和成本评估后，才可从 Future 提升为正式 Epic。

---

# 27. 新增实施任务：配置中心与路线图治理

## Epic K：Goal Agent 配置中心

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| K-01 | P0 | 定义配置 Schema 和分类 | A/B/C/D 类配置清单 | A-01 | 2d |
| K-02 | P0 | `mate_business_module_config` 数据模型 | Migration/Entity/Mapper | C | 2d |
| K-03 | P0 | `mate_business_module_config_revision` | 版本和 Diff | K-02 | 2d |
| K-04 | P0 | `mate_business_module_config_application` | 既有项目应用记录 | K-03 | 2d |
| K-05 | P0 | EffectiveConfigResolver | 多层继承和硬限制 | K-01~04 | 4d |
| K-06 | P0 | 配置草稿和发布服务 | Draft/Impact/Publish | K-05 | 4d |
| K-07 | P0 | 配置回滚 | Revision Rollback | K-06 | 2d |
| K-08 | P0 | 配置缓存和失效事件 | 多节点刷新 | B-12/K-06 | 3d |
| K-09 | P0 | TaskRun 配置快照 | Run Revision | F-10/K-05 | 2d |
| K-10 | P0 | 平台运行开关 | DRAIN/CANCEL/READ_ONLY | K-05 | 3d |
| K-11 | P0 | Workspace 启用隔离 | Workspace Runtime Gate | K-05 | 2d |
| K-12 | P0 | 项目继承/固定 | FOLLOW/PINNED | K-05 | 2d |
| K-13 | P0 | 平台管理页 | Status/Limit/Health | K-06~10 | 5d |
| K-14 | P0 | Workspace 配置页 | 配置/影响/发布 | K-06~12 | 5d |
| K-15 | P0 | 项目配置页 | Override/History | K-12 | 4d |
| K-16 | P0 | 配置权限和审计 | Admin/Owner/Manager | K-06 | 2d |
| K-17 | P0 | 配置 E2E | 六项 CFG 验收 | K-01~16 | 4d |
| K-18 | P0 | 配置运维手册 | 发布/回滚/紧急暂停 | K-17 | 1d |

预计新增工作量：

```text
后端约 28–32 人日
前端约 12–14 人日
测试约 6–8 人日
```

对原 8 周排期的影响：

- 若团队仍为 2 后端 + 2 前端 + 1 测试，建议 P0 调整为 9–10 周；
- 或将平台高级健康面板和批量应用已有项目放到 P0.1；
- 工作空间启用、有效配置解析、TaskRun 快照和基础配置页必须保留在 P0。

## Epic L：路线图和交付治理

| ID | P | 任务 | 产出 | 估算 |
|---|---|---|---|---:|
| L-01 | P0 | 建立 ROADMAP | NOW/NEXT/LATER | 1d |
| L-02 | P0 | 建立 Capability Matrix | P0/P1/P2/Future | 1d |
| L-03 | P0 | 建立 P1 Epic 文档 | 12 个 Epic | 2d |
| L-04 | P0 | 建立 P2 Epic 文档 | 12 个 Epic | 2d |
| L-05 | P0 | 建立 GitHub Label/Milestone | 执行追踪 | 1d |
| L-06 | P0 | 建立 Implementation Status | PR/Test/Release 追踪 | 1d |
| L-07 | P0 | 建立 Roadmap 评审节奏 | 月度评审规则 | 0.5d |

---

# 28. 修订后的关键决策表

| # | 决策 | 最终推荐 |
|---:|---|---|
| 1 | 是否有 Goal Agent 管理页面 | 有，平台/Workspace/项目三级 |
| 2 | 是否支持动态配置 | 支持，按 A/B/C/D 分类生效 |
| 3 | 部署开关是否动态 | 否，需要重启 |
| 4 | 平台运行开关是否动态 | 是，无需重启 |
| 5 | 是否按 Workspace 隔离 | 是，Workspace 独立启停和覆盖 |
| 6 | 是否允许项目覆盖 | 允许安全子集，不能突破上级限制 |
| 7 | 已有项目是否自动变更 | 行为类默认不变，需显式应用 |
| 8 | 运行中 TaskRun 是否热变更 | 否，使用创建时配置快照 |
| 9 | 新项目如何继承 | 使用当前 Workspace 有效 Revision |
| 10 | 项目配置模式 | 默认 FOLLOW_WORKSPACE，可 PINNED |
| 11 | 配置是否可回滚 | 可以，通过新 Revision 回滚 |
| 12 | P1/P2 在哪里记录 | Roadmap + Backlog + GitHub Milestone |
| 13 | Future 是否承诺排期 | 否，验证后升级为正式 Epic |
| 14 | P0 排期 | 加配置中心后建议 9–10 周 |

---

# 29. V3.2 最终架构决策：统一业务模块体系与入口

## 29.1 Goal Agent 的模块定位

Goal Agent 的最终定位是：

```text
第一方业务应用模块
```

它不是：

```text
普通 JAR Plugin
系统基础设置页面
单纯 Agent 模板
仅供管理员使用的 Ops 页面
```

原因：

- 有独立领域模型和数据库；
- 有日常业务操作页面；
- 有会话入口；
- 有 Worker、调度、验收和报告；
- 有 Workspace 和 Project 两级业务配置；
- 需要独立 Capability；
- 需要平台治理，但平台治理并不是主要业务入口。

## 29.2 三类扩展边界

### 技术插件 Plugin

入口：

```text
/plugins
```

负责：

- Tool；
- Provider；
- Channel；
- Memory；
- Search；
- 简单 `configSchema` 键值配置；
- 外部 JAR 生命周期。

不承载完整业务系统。

### 第一方业务应用 Business App

入口：

```text
/apps/{moduleKey}
```

例如：

```text
/apps/goal-agent
/apps/teacher
/apps/approval-assistant
/apps/content-studio
```

负责：

- 业务使用；
- 业务工作台；
- 业务会话；
- Workspace 配置；
- 资源级配置；
- 业务报表。

### 平台业务模块治理

入口：

```text
/settings/modules
/settings/modules/{moduleKey}
```

负责：

- 平台启停；
- 平台硬限制；
- 模块健康；
- 版本；
- 全局 Feature Flag；
- 队列和运行状态；
- 平台级配置；
- 紧急暂停。

## 29.3 Goal Agent 路由冻结

正式路由：

```text
/apps/goal-agent
/apps/goal-agent/new
/apps/goal-agent/settings
/apps/goal-agent/:projectId/assistant
/apps/goal-agent/:projectId/workbench
/apps/goal-agent/:projectId/goals
/apps/goal-agent/:projectId/tasks
/apps/goal-agent/:projectId/workers
/apps/goal-agent/:projectId/acceptance
/apps/goal-agent/:projectId/reports
/apps/goal-agent/:projectId/settings
```

平台入口：

```text
/settings/modules
/settings/modules/goal-agent
```

兼容路由：

```text
/goal-agent/*
→ 301/前端 redirect 到 /apps/goal-agent/*
```

## 29.4 导航冻结

主导航增加：

```text
业务应用 / Apps
```

建议结构：

```text
核心
├── Dashboard
├── Chat
├── Agents
├── Wiki
└── Memory

业务应用
├── Goal Agent
├── Teacher（后续迁移）
└── Future Business Modules

连接与扩展
├── Channels
├── Skills
├── Plugins
└── Activity

系统
├── Settings
├── Security
└── Docs
```

Settings 左侧只增加一个项目：

```text
业务模块
```

不在 Settings 左侧逐个列出 Goal Agent、Teacher、审批助手等业务。

---

# 30. 通用业务模块后端架构

## 30.1 Maven 模块

建议最终 Reactor：

```text
mateclaw-plugin-api
mateclaw-business-api
mateclaw-business-runtime
mateclaw-goal-agent
mateclaw-server
mateclaw-plugin-sample
mateclaw-plugin-search-sample
```

## 30.2 `mateclaw-business-api`

只提供稳定契约，不依赖 Server：

```text
BusinessModuleDescriptor
BusinessModuleProvider
BusinessModuleFeature
BusinessModuleScope
BusinessModuleRuntimeState
BusinessModuleConfigSchema
BusinessModuleConfigValidator
BusinessModuleImpactAnalyzer
BusinessModuleHealthProvider
BusinessModuleLifecycleListener
```

Descriptor 最小字段：

```json
{
  "key": "goal-agent",
  "displayName": "Goal Agent",
  "category": "PROJECT_MANAGEMENT",
  "version": "1.0.0",
  "defaultRoute": "/apps/goal-agent",
  "supportedScopes": ["PLATFORM", "WORKSPACE", "RESOURCE"],
  "resourceTypes": ["PROJECT"],
  "capabilities": {
    "view": "view:goal-agent",
    "manage": "manage:goal-agent",
    "admin": "admin:goal-agent"
  }
}
```

## 30.3 `mateclaw-business-runtime`

提供通用能力：

```text
模块注册
模块清单
平台启停
Workspace 启停
有效配置解析
配置草稿
Schema 校验
影响预览
Revision 发布
Revision 回滚
配置应用记录
缓存失效
模块健康
Feature Flag
通用模块管理 API
Operation Log
```

不提供：

```text
远程下载业务模块
运行任意第三方前端 JS
模块市场
热插拔 Maven 模块
跨版本自动升级业务数据库
```

上述能力不进入 P0。

## 30.4 `mateclaw-goal-agent`

提供：

```text
Goal Agent Descriptor
Goal Agent Config Schema
Goal Agent Config Validator
Goal Agent Impact Analyzer
Goal Agent Health Provider
Goal Agent Domain/Application/API/Tools
```

## 30.5 Server 职责

`mateclaw-server`：

```text
依赖 business-runtime 和 goal-agent
实现 Workspace/User/Agent/Cron/File 等 Adapter
暴露统一 Module Runtime
组合第一方业务模块
```

禁止 Business Runtime 直接依赖 Goal Agent 领域类。

---

# 31. 通用业务模块前端架构

## 31.1 编译时注册，运行时启停

采用：

```text
Vue 页面与路由：编译时注册
模块可见性和运行状态：后端运行时决定
```

不采用后端下发任意 JS URL 动态加载。

## 31.2 目录

```text
mateclaw-ui/src/business-modules/
├── types.ts
├── registry.ts
├── route-builder.ts
├── useBusinessModules.ts
├── BusinessAppsHome.vue
├── BusinessModulesSettings.vue
└── BusinessModuleStatusGuard.vue
```

Goal Agent：

```text
mateclaw-ui/src/features/goal-agent/
├── manifest.ts
├── routes.ts
├── api/
├── pages/
├── components/
├── stores/
├── types/
└── styles/
```

## 31.3 Manifest

```ts
export const goalAgentManifest: BusinessModuleManifest = {
  key: 'goal-agent',
  titleKey: 'goalAgent.title',
  basePath: '/apps/goal-agent',
  platformConfigPath: '/settings/modules/goal-agent',
  workspaceConfigPath: '/apps/goal-agent/settings',
  requiredCapability: 'view:goal-agent',
  routes: goalAgentRoutes,
}
```

增加新第一方业务时，常规情况下只需要：

1. 新增 `features/{module}`；
2. 提供 Manifest；
3. 在 Registry 注册；
4. 后端提供 Descriptor。

不应继续修改多个导航数组。

## 31.4 运行时显示条件

模块在 Workspace 中可见必须同时满足：

```text
前端已编译该模块
AND 后端已注册模块
AND 部署级模块存在
AND 平台已启用
AND 当前 Workspace 已启用
AND 当前用户有 view Capability
```

直接访问禁用模块路由：

- 未登录：跳登录；
- 无权限：403；
- 平台未启用：模块不可用页；
- Workspace 未启用：引导 Workspace Admin 启用；
- 模块异常：健康异常页；
- 只读模式：页面可查询，隐藏写操作。

## 31.5 Settings 模块总览

```text
/settings/modules
```

使用模块卡片展示：

```text
名称和版本
平台状态
当前 Workspace 状态
健康状态
当前配置 Revision
运行中任务数
待处理告警
打开应用
平台配置
Workspace 配置
启用/停用
```

---

# 32. Goal Agent 配置中心入口与页面归属

## 32.1 Workspace 配置主入口

```text
/apps/goal-agent/settings
```

这是业务管理员最常使用的配置入口，包含：

```text
基本设置
AI 助理
Worker
自动化和调度
验收
报告
通知
安全与配额
配置版本
```

权限：

```text
admin:goal-agent
或 Workspace Owner/Admin
```

## 32.2 项目配置入口

```text
/apps/goal-agent/:projectId/settings
```

包含：

```text
自治模式
项目 Assistant
项目时区
Worker 上限
项目调度
通知对象
验收策略
报告位置
FOLLOW_WORKSPACE / PINNED
配置历史
```

权限：

```text
manage:goal-agent
AND Project OWNER/MANAGER
```

## 32.3 平台配置入口

```text
/settings/modules/goal-agent
```

只允许 Global Admin，包含：

```text
平台运行状态
DRAIN / READ_ONLY / DISABLED
平台硬限制
总并发和队列
全局禁止 Tool
模块健康
TaskRun/Outbox backlog
配置 Schema 版本
P1/P2 Feature Flag
紧急操作
```

## 32.4 Goal Agent 内部快捷入口

Goal Agent Shell 中增加：

```text
右上角：Goal Agent 设置
项目菜单：项目设置
项目选择器菜单：管理 Goal Agent
```

用户不必离开业务应用进入通用 Settings 才能修改业务配置。

---

# 33. Capability 与权限扩展清单

## 33.1 前端 Capability 类型

从固定枚举调整为：

```ts
type CoreCapability =
  | 'chat'
  | 'view:wiki'
  | 'manage:agents'
  | 'manage:settings'
  | ...

type BusinessCapability =
  | `view:${string}`
  | `manage:${string}`
  | `admin:${string}`

export type Capability = CoreCapability | BusinessCapability
```

## 33.2 Goal Agent 权限

```text
view:goal-agent
manage:goal-agent
admin:goal-agent
```

## 33.3 入口权限

| 入口 | 权限 |
|---|---|
| `/apps/goal-agent` | `view:goal-agent` |
| Workspace Goal Agent 设置 | `admin:goal-agent` 或 Workspace Admin |
| 项目设置 | `manage:goal-agent` + Project Manager |
| `/settings/modules` | Global Admin |
| `/settings/modules/goal-agent` | Global Admin |

## 33.4 后端二次校验

前端隐藏不等于授权。

所有模块 API 必须再次校验：

```text
模块已启用
Workspace 已启用
Capability
项目成员
资源归属
运行状态
```

---

# 34. 修订后的新增实施任务

## Epic M：通用 Business Module 后端基础

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| M-01 | P0 | 定义 Business Module 边界 ADR | Plugin/App/Settings 边界 | A-10 | 1d |
| M-02 | P0 | 新增 `mateclaw-business-api` | Maven 模块 | M-01 | 1d |
| M-03 | P0 | 定义 Descriptor/Provider 契约 | Java API | M-02 | 2d |
| M-04 | P0 | 定义 Scope/RuntimeState/Feature | 枚举和 DTO | M-03 | 1d |
| M-05 | P0 | 新增 `mateclaw-business-runtime` | Maven 模块 | M-02 | 1d |
| M-06 | P0 | 模块注册中心 | Provider Discovery | M-03/05 | 2d |
| M-07 | P0 | `mate_business_module` 表 | Migration/Mapper | M-05 | 2d |
| M-08 | P0 | 通用 Config 表 | Config/Revision/Application | M-05 | 4d |
| M-09 | P0 | Effective Config Resolver | 多级继承 | M-08 | 4d |
| M-10 | P0 | Draft/Validate/Impact/Publish | 配置发布服务 | M-09 | 5d |
| M-11 | P0 | Revision Rollback | 通用回滚 | M-10 | 2d |
| M-12 | P0 | 平台/Workspace Runtime Gate | 启停和只读 | M-07/09 | 3d |
| M-13 | P0 | 配置缓存失效 | Outbox + TTL | M-10/B-12 | 3d |
| M-14 | P0 | 模块健康聚合 | Health Provider | M-06 | 2d |
| M-15 | P0 | 通用 Module REST API | 清单/状态/配置 | M-06~14 | 3d |
| M-16 | P0 | Module Operation Log | 启停/发布审计 | M-10~12 | 2d |
| M-17 | P0 | 架构约束测试 | ArchUnit | M-02~16 | 2d |
| M-18 | P0 | Business Runtime 集成测试 | H2/MySQL | M-01~17 | 4d |

验收：

- Goal Agent 和未来模块不重复建设配置 Revision；
- Runtime 不依赖 Goal Agent；
- 关闭模块不注册业务入口；
- Workspace A/B 启停隔离；
- 配置发布和回滚可审计；
- 新模块可以只实现 Provider 接入 Runtime。

## Epic N：前端业务应用注册与导航

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| N-01 | P0 | 定义 Frontend Module Manifest | TS 类型 | M-03 | 1d |
| N-02 | P0 | 建立 Registry | 静态模块注册 | N-01 | 1d |
| N-03 | P0 | Route Builder | `/apps` 路由生成 | N-02 | 2d |
| N-04 | P0 | `useBusinessModules` | 后端状态合并 | M-15/N-02 | 2d |
| N-05 | P0 | 新增主导航 Apps 分组 | 动态模块菜单 | N-04 | 2d |
| N-06 | P0 | 新增 `/apps` 总览 | 应用卡片 | N-04 | 2d |
| N-07 | P0 | 新增 Settings Modules 单入口 | 左侧单菜单 | N-04 | 1d |
| N-08 | P0 | `/settings/modules` 总览 | 模块治理卡片 | M-15 | 3d |
| N-09 | P0 | Module Status Guard | Disabled/403/ReadOnly | N-04 | 2d |
| N-10 | P0 | Capability 通配类型 | Business Capability | A-03 | 1d |
| N-11 | P0 | Workspace 切换刷新模块 | 状态重载 | N-04 | 1d |
| N-12 | P0 | 路由 Chunk 预热兼容 | Apps routes | N-03 | 1d |
| N-13 | P0 | i18n 模块命名空间 | 独立翻译 | N-01 | 1d |
| N-14 | P0 | `/goal-agent` 兼容重定向 | 旧路径兼容 | N-03 | 1d |
| N-15 | P0 | 前端单元测试 | Registry/Guard | N-01~14 | 3d |
| N-16 | P0 | 前端 E2E | 启停/权限/切换 | N-01~15 | 3d |

验收：

- MainLayout 不再逐个硬编码新业务模块；
- Settings 左侧只有“业务模块”一个入口；
- Workspace 切换后模块菜单正确刷新；
- 禁用模块不能通过直链绕过；
- Goal Agent 路由按 Capability 控制。

## Epic K（修订）：Goal Agent 配置接入 Business Runtime

原 K-02～K-08 中的通用配置基础能力移动到 Epic M。Goal Agent 只实现业务特有部分。

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| K-01 | P0 | Goal Agent 配置 Schema | 字段、类型、分类 | M-03 | 2d |
| K-02 | P0 | Goal Agent 默认值 | 平台安全默认 | K-01 | 1d |
| K-03 | P0 | Goal Agent Config Validator | 上限和组合校验 | K-01 | 3d |
| K-04 | P0 | Goal Agent Impact Analyzer | Project/Run 影响 | K-03/M-10 | 3d |
| K-05 | P0 | Goal Agent Module Provider | Descriptor/Health | M-03/K-01 | 2d |
| K-06 | P0 | TaskRun 配置快照 | Revision + Snapshot | M-09/F-10 | 2d |
| K-07 | P0 | Project FOLLOW/PINNED | 绑定模式 | M-09/D-01 | 2d |
| K-08 | P0 | Goal Agent Runtime Gate | Tool/Run/Schedule 校验 | M-12 | 3d |
| K-09 | P0 | Workspace 配置页 | `/apps/goal-agent/settings` | N/K/M | 5d |
| K-10 | P0 | 项目配置页 | Project Settings | K-07 | 4d |
| K-11 | P0 | 平台模块页 | `/settings/modules/goal-agent` | N-08/K-05 | 4d |
| K-12 | P0 | 配置发布 E2E | A/B/C/D 生效规则 | K-01~11 | 4d |

## Epic O：Teacher 兼容和渐进迁移

Teacher 迁移不阻塞 Goal Agent P0，但必须确保新架构不会破坏现有 `/teacher-ops`。

| ID | P | 任务 | 产出 | 依赖 | 估算 |
|---|---|---|---|---|---:|
| O-01 | P0 | Teacher 回归基线 | 路由/API/配置用例 | 无 | 1d |
| O-02 | P0 | 新 Registry 中保留 Teacher 外部链接 | 兼容入口 | N-02 | 1d |
| O-03 | P0 | Plugins Teacher 卡片保持可用 | 回归验证 | O-01 | 1d |
| O-04 | P0.1 | Teacher Module Manifest | `/apps/teacher` | N-01 | 2d |
| O-05 | P0.1 | Teacher Provider Adapter | Descriptor/Health | M-03 | 2d |
| O-06 | P1 | Teacher 配置迁移至 Runtime | Revision/Scope | M-08 | 4d |
| O-07 | P1 | `/teacher-ops` 兼容重定向 | 新旧入口 | O-04 | 1d |
| O-08 | P1 | 移除 Plugins 硬编码卡片 | 清理特例 | O-04~07 | 1d |

---

# 35. 修订后的 P0 范围和排期

## 35.1 P0 必须实现的业务模块基础

必须在 P0 实现：

```text
Business Module Descriptor
静态 Registry
平台/Workspace 启停
三级配置作用域
配置 Revision
配置发布和回滚
/apps 入口
/settings/modules 入口
Capability Guard
Goal Agent Manifest
```

不进入 P0：

```text
远程模块下载
模块市场
第三方业务前端动态加载
Maven 模块热插拔
可视化 Schema Builder
通用低代码页面生成
Teacher 全量迁移
```

## 35.2 修订排期

在原业务计划前增加 Business Runtime 基础，P0 调整为约 10 周。

### Sprint 0：架构与 Spike

```text
A
M-01~06
N-01~04
独立 Flyway 和 Agent Execution Spike
```

里程碑：

> Plugin、Business App、Settings 边界冻结；Business Module 可以被注册并查询。

### Sprint 1：Business Runtime

```text
M-07~18
N-05~10
B 基础模块
```

里程碑：

> 平台和 Workspace 可以启停业务模块，通用配置可以发布和回滚。

### Sprint 2：Goal Agent 领域基础

```text
C
D-01~06
K-01~05
N-11~16
```

里程碑：

> Goal Agent 出现在 Apps，Workspace 配置可用，项目和 Proposal 可创建。

### Sprint 3：会话闭环

```text
D 剩余
E
I Assistant/Proposal
K-06~10
```

里程碑：

> 可通过会话创建项目、需求和任务。

### Sprint 4：Worker 编排

```text
F-01~07
I Worker 页面
```

### Sprint 5：Worker 执行和调度

```text
F-08~14
G
```

### Sprint 6：验收和对账

```text
H-01~05
G 日结/周报
```

### Sprint 7：报告和业务页面

```text
H-06~13
I 剩余页面
K-11~12
```

### Sprint 8：系统测试

```text
J
M/N/K 集成测试
O-01~03
```

### Sprint 9：灰度和发布

```text
缺陷修复
上游合并演练
单 Workspace 灰度
Go/No-Go
```

---

# 36. 新增验收标准

## BM-AC-01 模块注册

- Goal Agent 通过 `BusinessModuleProvider` 注册；
- 后端模块清单可查询；
- 前端 Registry 能匹配后端 Descriptor；
- 未编译的模块不会产生可访问路由。

## BM-AC-02 平台禁用

- Global Admin 禁用 Goal Agent；
- 所有 Workspace 不再接收 Goal Agent 写请求；
- 普通 Chat、Agent、Cron、Plugin 不受影响；
- Goal Agent 历史数据仍可按策略只读查询。

## BM-AC-03 Workspace 隔离

- Workspace A 启用 Goal Agent；
- Workspace B 禁用 Goal Agent；
- A 显示 Apps 菜单并可运行；
- B 不显示或显示“未启用”；
- B 的直链请求不能绕过。

## BM-AC-04 新业务接入成本

使用一个测试 First-party Module 验证：

- 提供 Provider；
- 提供前端 Manifest；
- 注册后出现在 Apps 和 Modules；
- 不需要修改 MainLayout 的业务菜单数组；
- 不需要在 Settings 左侧增加专属项；
- 不需要新建一套配置 Revision 表。

## BM-AC-05 Goal Agent 配置入口

- Workspace 配置从 `/apps/goal-agent/settings` 进入；
- 项目配置从项目内部进入；
- 平台配置从 `/settings/modules/goal-agent` 进入；
- 三类入口权限正确；
- 配置内容不会混淆。

## BM-AC-06 Teacher 兼容

- `/plugins` Teacher 卡片仍正常；
- `/teacher-ops` 仍可访问；
- RulePack 和 Skill 配置不受 Goal Agent 改造影响；
- 新 Business Runtime 关闭时 Teacher 原功能正常。

---

# 37. 更新后的实施前确认清单

在进入 Sprint 0 前，确认以下内容：

- [ ] Goal Agent 定位为第一方 Business App；
- [ ] 正式路由使用 `/apps/goal-agent`；
- [ ] 主导航新增“业务应用”分组；
- [ ] Settings 左侧仅增加“业务模块”单入口；
- [ ] Goal Agent Workspace 配置放在业务应用内部；
- [ ] 项目配置放在项目内部；
- [ ] 平台治理放在 `/settings/modules/goal-agent`；
- [ ] Plugin 继续只管理技术扩展；
- [ ] 新增 `mateclaw-business-api`；
- [ ] 新增 `mateclaw-business-runtime`；
- [ ] 采用通用 Business Module 配置表；
- [ ] Goal Agent 不再建设独立 `ga_config*` 表；
- [ ] 前端采用编译时 Manifest Registry；
- [ ] 后端运行时决定模块启停和可见性；
- [ ] P0 不做远程动态业务模块加载；
- [ ] Teacher P0 只做兼容，不强制迁移；
- [ ] Goal Agent P0 调整为约 10 周；
- [ ] Feature Flag Off 和原业务回归仍是上线硬门禁。

---

# 38. V3.2 最终任务规模摘要

原 A～J 业务任务继续有效；配置中心 K 已按通用 Business Runtime 重构，并新增 M、N、O。

建议工作包：

```text
A-L：Goal Agent 产品、领域、执行、配置和交付治理
M：通用 Business Module 后端基础
N：前端 Apps/Modules Registry
O：Teacher 兼容和后续迁移
```

优先级：

```text
M/N 最小基础
→ Goal Agent 会话闭环
→ Worker 执行
→ 调度和验收
→ 报告
→ 高级配置治理
→ Teacher 迁移
→ P1/P2 扩展
```

最核心的架构验收结果是：

> 增加下一个第一方业务模块时，不再复制 Goal Agent 的配置中心、版本表、启停逻辑和导航改造，只需要实现模块 Provider、配置 Schema、业务代码和前端 Manifest。

