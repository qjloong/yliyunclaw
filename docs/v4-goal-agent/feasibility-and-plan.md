# MateClaw 项目管理机器人（PM Robot）可行性分析与实施方案 · V2 终稿

> 日期：2026-08-01 | V4 Goal Agent 原型 | 参照禅道(ZenTao)规范 + PMBOK 通用项目管理 | AI 全流程驱动
>
> **术语约定**：
> - **PM Robot**：项目管理大脑，负责需求评审、WBS 分解、智能排期、Worker 分配、阻塞研判、跨项目调度
> - **Agent Worker**：实际执行任务的 AI 员工（如场地 Worker、物料 Worker、执行 Worker 等），由 PM Robot 调度
> - **Human User**：真实用户，审批关键决策、干预执行过程、最终验收
> - **AI 助理**：每个项目默认的助理 Agent，项目级对话入口，理解需求、搭建流程、跟踪进度、驱动 PM Robot
>
> **设计立场**：本项目管理的"项目"是广义的（运营/市场/基建/研发等），非特指软件开发。

---

## 一、通用项目管理规范

### 1.1 模块全景

参照禅道管理思想 + PMBOK 通用项目管理，去软件特化，用通用的阻塞风险管理 + AI 自适应决策替代软件特化模块。

```
通用项目管理模块地图
├── 1. 目标与需求管理
│   ├── 项目目标 / 需求 Backlog / 需求评审 / 需求变更 / 需求跟踪矩阵
│
├── 2. 计划与排期管理
│   ├── WBS 分解 / 依赖关系 / 迭代规划 / 工时估算 / 甘特图 / 资源负载视图
│
├── 3. 风险与阻塞管理 ★ 核心差异化
│   ├── 风险登记册（概率×影响）/ 阻塞点登记 / AI 四级研判 / AI 自动决策
│
├── 4. 验收与质量管理
│   ├── 验收规则 / 证据驱动评分 / 验收报告
│
├── 5. 执行与监控
│   ├── 任务看板 / 偏差分析 / AI 自动重排 / 每日简报
│
├── 6. 交付与发布管理
│   ├── 交付计划 / 交付物清单 / 验收报告
│
├── 7. 知识沉淀
│   ├── 资料复用（母版→派生）/ 经验教训 / 项目模板
│
├── 8. 统计报表
│   ├── 燃尽图 / 阻塞趋势 / AI 决策分布 / 生产力指数
│
└── 9. 组织与权限
    ├── 团队成员（Human + Agent 统一视图）/ RACI / 邀请机制 / 审计日志
```

### 1.2 原型 vs 通用 PM 差距

| 功能域 | 原型覆盖 | 通用 PM 对应 | 决策 |
|---|---|---|---|
| 需求管理 | ❌ | 需求 Backlog/评审/变更/跟踪 | ✅ 新增 |
| 迭代管理 | ❌ | Sprint 规划/容量管理 | ✅ 新增 |
| 风险与阻塞管理 | ⚠️ | 风险登记+阻塞AI研判决策 | ✅ 新增 ★ |
| 验收管理 | ✅ | 证据驱动自动评分 | ✏️ 增强 |
| 变更管理 | ❌ | 变更请求+影响评估+审批 | ✅ 新增 |
| 交付管理 | ❌ | 路线图+交付物清单 | ✅ 新增 |
| AI 自适应决策 | ❌ | 阻塞自动研判→调整方案→重新分配 | ✅ 新增 ★ |
| 项目模板化 | ❌ | 提炼模板+模板市场 | ✅ 新增 |
| 知识沉淀 | ⚠️ | 经验教训+模板学习+母版追踪 | ✏️ 增强 |
| 目标管理 | ✅ | 五级目标树 | ✏️ 保留 |
| 工作对账 | ✅ | 日结/周结 | ✏️ 保留 |

---

## 二、AI 全流程驱动设计

### 2.1 核心流程（六大阶段）

```
Phase 0 项目启动 → Phase 1 规划 → Phase 2 执行 → Phase 3 监控
    ▼ 审批章程        ▼ 审批计划
                                                   ▼
                                              Phase 5 项目收尾
```

### 2.2 各阶段详解

#### Phase 0：项目启动
| 步骤 | 动作 | 产出 |
|---|---|---|
| 项目基础信息 | 名称、类型、周期、负责人、描述、来源（全新/模板/复制/派生） | 项目章程草案 |
| 需求澄清 | AI 助理多轮追问 | 需求澄清纪要 |
| 需求评审 | 分析完整性/一致性/可行性 | 需求评审报告 |
| 需求结构化 | Story 模板引擎 | 需求 Backlog |
| 优先级排序 | MoSCoW + WSJF | 排序表 |
| 风险初评 | 四维风险矩阵 | 风险登记册初版 |
| 流程搭建 | 识别项目类型→加载模板→生成补充提问→完善流程+分工 | 流程定义 |

#### Phase 1：规划阶段

#### Phase 2-3：执行与监控
每日任务分发 → Worker 自主执行 → 阻塞自动发现 → AI 研判决策 → 进度同步 → 偏差分析 → 自动重排


---

## 三、治理模型

### 3.1 三种运行模式

| 模式 | 说明 | Agent 自主权 | 用户参与度 |
|---|---|---|---|
| 🟡 半自动 | AI 生成建议，用户确认后执行 | 中 | 中 |
| 🔴 人工 | AI 退化为建议助手 | 低 | 高 |

### 3.2 用户干预点


---

## 四、AI 自适应阻塞决策 ★ 核心差异化

### 4.1 四级研判

```
Level 1 轻微阻塞（可自愈）
  ├─ 特征：单一任务受阻，不波及依赖链
  ├─ AI 动作：自动尝试替代方案
  └─ 通知：每日简报中提及

Level 2 中度阻塞（需调整）→ AI 自动执行+通知
  ├─ 特征：影响 ≤3 个关联任务
  ├─ AI 动作：自动评估→生成调整方案→执行（重排/替换/拆分/降级/并行）
  └─ 通知：实时推送调整方案+决策理由

Level 3 重度阻塞（需升级人工）
  ├─ AI 动作：暂停任务链→生成升级报告（根因+影响+3备选方案+AI推荐）
  └─ 通知：紧急推送审批人

Level 4 关键阻塞（强制人工）
  ├─ 特征：项目目标不可达成/合规风险
  ├─ AI 动作：冻结范围→生成完整评估报告
  └─ 通知：强制通知所有干系人
```

### 4.2 严重度评分算法

```
severityScore = 
    影响广度(0.35) × (受影响任务数 / 总任务数)
  + 时间敏感(0.20) × (剩余缓冲天数 / 总工期天数 的反比)
  + 可替代性(0.15) × (是否有替代方案 ? 0 : 1.0)

阈值：< 0.25→L1 | 0.25~0.5→L2 | 0.5~0.75→L3 | ≥0.75→L4
```

### 4.3 五种自动调整策略

重排、替换 Worker、拆分任务、降级验收、并行化。每次决策记录到 `AgentDecisionLog`。

---

## 五、Agent Worker 生产力评估与跨项目调度

### 5.1 生产力指数 (PI)

| 指标 | 权重 | 计算 |
|---|---|---|
| 完成速度 | 30% | 实际耗时/预估耗时 |
| 完成质量 | 35% | 一次验收通过率 |
| 吞吐量 | 15% | 日均完成任务数 |
| 可用率 | 10% | 在线时间/计划时间 |
| 协作贡献 | 10% | 被@次数×响应率 |

PI ≥ 90→A级 | 70-89→B级 | 50-69→C级 | <50→D级。按任务类型分组评估。

### 5.2 跨项目调度算法

```
任务调度得分 = 项目优先级(30%) + 任务优先级(25%) + 紧急度(20%)
             + 依赖链深度(15%) + 等待时间(10%，防饿死)
```

**抢占规则**：待插入得分 > 当前得分 × 1.5 → 允许抢占。低优先级任务等待 > 48h → 临时提升。

### 5.3 冲突分级处理

| 冲突 | 自动解决 | 需人工 |
|---|---|---|
| 两个 P0 项目竞争同一 Worker | ❌ | ✅ 生成决策辅助报告 |
| Worker 负载 > 90% | ✅ 自动找替代 | 通知 |
| P3 任务饿死 | ✅ 48h 自动提升 | 无需 |

---

## 六、对话式流程搭建 + 项目模板化

### 6.1 三层递进

```
用户输入目标 → PM Robot 识别项目类型（市场/基建/研发/运营/自定义）
  → 生成 5 个补充提问（审批链/验收标准/预算/供应商/分工）
  → 用户回答 → 完善流程 + RACI
```

### 6.2 持续可调整

项目全生命周期支持三种调整方式：
- **会话式调整**：对话中随时改——"把 M2 审批人换成财务总监"
- **模板重新应用**：从模板市场选择新模板覆盖

### 6.3 项目模板化


发布范围：🔒 私有 → 👥 团队 → 🌐 公开模板市场

模板统计优化：使用率/完成率/常见调整→PM Robot 建议模板改进。

### 6.4 项目来源

启动时可选：🆕 全新 / 📋 套用模板 / 🔀 复制项目 / 🌿 派生项目（基于现有项目二次迭代）

---

## 七、AI 工作室双入口设计

| 模块 | 级别 | 视角 | 权限 | 核心场景 |
|---|---|---|---|---|
| **PM Robot 对话** | Workspace 级 | 跨所有项目全局视图 | 仅管理员 | 资源冲突分析、多项目 Worker 调度、全局阻塞研判 |
| **AI 助理** | 项目级 | 当前选中项目上下文 | 项目参与者 | 日常沟通、启动项目、流程调整、跟踪进度、驱动 PM Robot |

**PM Robot 对话**：展示 Workspace 下所有活跃项目的全局概览、Worker 池负载分布、跨项目资源冲突。

**AI 助理**：每个项目默认的助理 Agent，跟随顶部项目选择器切换上下文。全生命周期可用——启动新项目、完善流程、跟踪进度、变更管理、进度评审、驱动 PM Robot 执行后台调度。

---

## 八、团队成员统一视图

### 8.1 设计理由

原"干系人"和"Agent Workers"数据重叠——干系人表有 Agent Worker，Worker 列表也有 Human。统一为一个"团队成员"模块。

### 8.2 统一视图

Human User + Agent Worker 在同一页面管理，PM Robot 和 AI 助理使用同一份成员清单：

| 成员 | 类型 | RACI | 能力标签 | 负载 |
|---|---|---|---|---|
| 周先生 | 👤 Human | A (最终责任) | 项目决策/预算审批 | 30% |
| 产品总监 | 👤 Human | C (咨询) | 签批/合规 | 0% ⚠待激活 |
| 场地 Worker | 🤖 Agent | R (执行) | 供应商管理/合同谈判 | 60% |
| 物料 Worker | 🤖 Agent | R (执行) | 设计协调/质量管理 | 95% ⚠ |
| 执行 Worker | 🤖 Agent | R (执行) | 活动策划/现场执行 | 20% |

### 8.3 邀请机制

现有架构无邀请机制（管理员直接添加，被添加者无感知）。新增 `pm_invitation` 表：

```
发起邀请 → 通知（站内+邮件）→ 接受/拒绝 → 自动加入 Workspace+项目
                                    → 拒绝 → PM Robot 建议替代
                                    → 过期 → 提醒管理员
```

---

## 九、云盘集成 + 资料复用

### 9.1 存储原则

所有文件实际存储于云盘（通过 MCP 接口对接），MateClaw 不存储文件本身，仅管理：
- 文件元数据（路径/类型/大小/版本）
- 分类标签（📥原始资料 / 📋参考模板 / 📦交付物 / 📝阶段文档）
- 母版-派生关系链路
- 版本冲突检测

### 9.2 资料复用关系追踪

```
活动策划标准母版 v2.3（核心）
├── 客户A活动方案 v1.7（差异 22%）
├── 微信推广文案 v1.2（差异 54%）
├── 内部培训资料 v1.0 ⚠ 版本过期（仍用v1.8母版）
└── 活动微站内容 v0.5（可从母版生成）
```

PM Robot 自动检测版本冲突、生成更新建议、计算复用收益。

---

## 十、数据模型总览

### 10.1 数据库表清单（30 张）

| 序号 | 表名 | 说明 | 来源 |
|---|---|---|---|
| 1 | `pm_project` | 项目主表 | ✏️ 增强 |
| 2 | `pm_goal` | 多级目标 | ✏️ 保留 |
| 3 | `pm_key_result` | 关键结果 | ✏️ 保留 |
| 4 | `pm_story` | 需求/Story | ✅ 新增 |
| 5 | `pm_story_review` | 需求评审记录 | ✅ 新增 |
| 6 | `pm_iteration` | 迭代/Sprint | ✅ 新增 |
| 7 | `pm_board` | 工作板块 | ✏️ 保留 |
| 8 | `pm_task` | 工作任务 | ✏️ 增强 |
| 9 | `pm_risk` | 风险登记册 | ✅ 新增 |
| 10 | `pm_blocker_decision` | 阻塞点+AI决策记录 ★ | ✅ 新增 |
| 11 | `pm_change_request` | 变更请求 | ✅ 新增 |
| 12 | `pm_release` | 交付计划 | ✅ 新增 |
| 13 | `pm_acceptance_rule` | 验收规则 | ✏️ 保留 |
| 14 | `pm_evidence` | 成果证据 | ✏️ 保留 |
| 15 | `pm_work_log` | 工作日志/工时 | ✅ 新增 |
| 16 | `pm_reconciliation` | 对账记录 | ✏️ 保留 |
| 17 | `pm_document_relation` | 资料关联（母版-派生） | ✏️ 增强 |
| 18 | `pm_agent_worker` | Worker 属性（负载/状态/能力/共享策略） | ✅ 新增 |
| 19 | `pm_agent_decision_log` | Agent 决策日志 | ✏️ 增强 |
| 20 | `pm_process_template` | 流程模板 | ✅ 新增 |
| 21 | `pm_template_market` | 模板市场（发布/评分/版本） | ✅ 新增 |
| 22 | `pm_invitation` | 项目邀请 | ✅ 新增 |
| 23 | `pm_worker_allocation` | Worker 跨项目分配 | ✅ 新增 |
| 24 | `pm_worker_productivity` | Worker 生产力快照 | ✅ 新增 |
| 25 | `pm_worker_task_queue` | Worker 任务执行序列 | ✅ 新增 |
| 26 | `pm_project_role` | 项目级角色定义 | ✅ 新增 |
| 27 | `pm_operation_log` | 操作日志（版本控制/冲突检测） | ✅ 新增 |

### 10.2 PM Robot 工具清单（22 个）

| 工具名 | 功能 | 阶段 |
|---|---|---|
| `pm_story_review` | 需求评审 | Phase 0 |
| `pm_risk_identify` | 风险识别 | Phase 0 |
| `pm_wbs_decompose` | WBS 分解 | Phase 1 |
| `pm_estimate_effort` | 工时估算 | Phase 1 |
| `pm_iteration_plan` | 迭代划分 | Phase 1 |
| `pm_worker_assign` | Worker 分配 | Phase 1 |
| `pm_acceptance_generate` | 验收标准生成 | Phase 1 |
| `pm_daily_dispatch` | 每日任务分发 | Phase 2 |
| `pm_blocker_assess` ★ | 阻塞自动研判 | Phase 2-3 |
| `pm_blocker_decide` ★ | 阻塞自动决策执行 | Phase 2-3 |
| `pm_progress_report` | 进度报告 | Phase 2-3 |
| `pm_burn_chart` | 燃尽图 | Phase 3 |
| `pm_deviation_analyze` | 偏差分析 | Phase 3 |
| `pm_change_impact` | 变更影响评估 | Phase 3 |
| `pm_auto_reschedule` | 自动重排 | Phase 3 |
| `pm_final_acceptance` | 最终验收 | Phase 5 |
| `pm_lesson_learned` | 经验提取 | Phase 5 |
| `pm_productivity_calc` | 生产力计算 | 持续 |
| `pm_cross_project_schedule` | 跨项目调度 | 持续 |
| `pm_template_extract` | 模板提取 | Phase 5 |

---

## 十一、与现有系统的集成边界

### 11.1 不修改的模块

`agent/`、`goal/`、`planning/`、`auth/`、`workspace/` 等现有模块不修改。新模块 `pm/` 完全独立。

### 11.2 复用能力

| 现有能力 | PM Robot 使用方式 |
|---|---|
| Agent 运行时（ReAct/Plan-Execute 图引擎） | PM Robot 作为 Agent 运行 |
| ToolRegistry + MCP 工具系统 | PM 工具注册 |
| 一句话生成 Agent（AgentCreateWizard） | 一句话启动项目（PmRobotGenerationService） |
| Workspace 隔离（X-Workspace-Id） | 项目按 Workspace 隔离 |
| JWT + Workspace 角色 | 团队协作权限 |
| Agent 委托机制（delegation/） | PM Robot→Worker 任务分发 |
| 站内通知 | 阻塞告警/审批提醒 |
| @Scheduled 定时任务 | 日结/周报 |
| SSE 流式输出 | PM Robot + AI 助理对话 |
| Vue3 + Element Plus | PM 前端页面 |
| 云盘 MCP 接口 | 文件存储 |

### 11.3 能力矩阵

| 能力域 | 满足度 | 需新增 |
|---|---|---|
| Agent 运行时 | ✅ 满足 | 无 |
| 工具注册 | ✅ 满足 | 新增 PM 工具 |
| Agent 创建模式 | ✅ 满足 | 新增 PmRobotGenerationService |
| Workspace 隔离 | ✅ 满足 | 无 |
| 用户认证 | ✅ 满足 | 无 |
| 多 Agent 协作 | ⚠️ 部分满足 | 扩展委托+任务分配模型 |
| 知识库 | ✅ 满足 | 无 |
| 通知 | ✅ 满足 | 无 |
| 定时任务 | ✅ 满足 | 新增 PM 定时器 |
| 对话流 | ✅ 满足 | 无 |
| 前端框架 | ✅ 满足 | 新增 PM 页面 |
| 邀请机制 | ❌ 缺失 | 新增 pm_invitation |
| @mention | ❌ 缺失 | 新增 PmMentionService |

---

## 十二、分阶段实施计划（含具体改动点）

### 阶段 0：基础数据模型（第 1-2 周）

| ID | 任务 | 改动文件 | 影响范围 |
|---|---|---|---|
| P0-1 | 新建 `vip.mate.pm` 包结构 | 新增：`pm/controller/`, `pm/service/`, `pm/model/entity/`, `pm/model/dto/`, `pm/repository/`, `pm/config/`, `pm/agent/`, `pm/event/` | 无影响，独立包 |
| P0-2 | 创建 `PmRobotProperties.java` | 新增：`pm/config/PmRobotProperties.java`（`@ConfigurationProperties("mate.pm-robot")`） | 无影响 |
| P0-3 | 创建 `PmRobotAutoConfiguration.java` | 新增：`pm/config/PmRobotAutoConfiguration.java`（`@ConditionalOnProperty(name="mate.pm-robot.enabled", havingValue="true", matchIfMissing=true)`） | Spring 自动发现，无需修改主类 |
| P0-4 | 添加 `application.yml` 配置 | 修改：`application.yml` 新增 `mate.pm-robot.enabled: true` | 仅新增配置项 |
| P0-5 | Flyway V172 数据库迁移 | 新增：`db/migration/h2/V172__pm_robot_baseline.sql`（30 张表 DDL）<br>新增：`db/migration/kingbase/V172__pm_robot_baseline.sql` | 不影响现有迁移链（V171 之后） |
| P0-6 | Entity + Mapper 层 | 新增：`pm/model/entity/*.java`（27 个实体）<br>新增：`pm/repository/*.java`（27 个 Mapper，继承 `BaseMapper<T>`） | MyBatis-Plus `@MapperScan("vip.mate.**.repository")` 自动扫描，无需修改主类 |
| P0-7 | 集成测试 | 新增：测试类验证 CRUD | 无影响 |

**改动影响**：新增文件约 60+ 个，修改 `application.yml` 1 处，无现有代码修改。

### 阶段 1：核心 CRUD API（第 2-4 周）

| ID | 任务 | 改动文件 | 影响范围 |
|---|---|---|---|
| P1-1 | Project + Story CRUD + 需求评审流程 | 新增：`pm/controller/ProjectController.java`<br>新增：`pm/controller/StoryController.java`<br>新增：`pm/service/ProjectService.java`<br>新增：`pm/service/StoryService.java`<br>新增：`pm/model/dto/ProjectDTO.java`<br>新增：`pm/model/dto/StoryDTO.java` | 所有 Controller 使用 `@RequireWorkspaceRole("member")`，复用现有 Workspace 拦截器，无需修改 SecurityConfig |
| P1-2 | Iteration CRUD | 新增：`pm/controller/IterationController.java`<br>新增对应 Service/DTO | 同上 |
| P1-3 | Task + Board CRUD | 新增：`pm/controller/TaskController.java`<br>新增：`pm/controller/BoardController.java`<br>新增对应 Service/DTO | 同上 |
| P1-4 | Risk + BlockerDecision + ChangeRequest + Release CRUD | 新增：4 个 Controller + 4 个 Service | 同上 |
| P1-5 | TeamMember + Worker CRUD + 邀请 | 新增：`pm/controller/TeamMemberController.java`<br>新增：`pm/service/InvitationService.java`<br>新增：`pm/model/entity/PmInvitationEntity.java` | 邀请接受后写入 `mate_workspace_member`（复用现有表） |
| P1-6 | Acceptance + Evidence + Reconciliation CRUD | 新增：3 个 Controller + 3 个 Service | 同上 |
| P1-7 | DocumentRelation + Template + Market CRUD | 新增：3 个 Controller + 3 个 Service | 同上 |

**改动影响**：新增文件约 40+ 个，0 处现有代码修改。所有 API 路径为 `/api/v1/pm-robot/*`，被 `SecurityConfig` 第 121 行 `.authenticated()` 自动覆盖。

### 阶段 2：PM Robot 引擎（第 4-7 周）

| ID | 任务 | 改动文件 | 影响范围 |
|---|---|---|---|
| P2-1 | `PmRobotGraphBuilder` | 新增：`pm/agent/PmRobotGraphBuilder.java`（复用现有 StateGraphReActAgent 框架，定制系统提示词） | 无影响，复用 `agent/` 包图引擎 |
| P2-2 | `PmRobotGenerationService` | 新增：`pm/service/PmRobotGenerationService.java`（复用 `AgentGenerationService.buildSystemPrompt()+buildUserPrompt()+parseJson()` 模式） | 无影响，独立 Service |
| P2-3 | Phase 0 工具集 | 新增：`pm/agent/tools/PmStoryReviewTool.java`<br>新增：`pm/agent/tools/PmRiskIdentifyTool.java`<br>新增：`pm/agent/tools/PmCharterGenerateTool.java` | 通过 `@Tool` 注解自动注册到 `ToolRegistry`，无需修改注册逻辑 |
| P2-5 | Phase 2-3 工具集 | 新增：7 个 Tool 类（分发/阻塞研判/阻塞决策/进度/偏差/变更影响/重排）★ | 同上 |
| P2-7 | PM Robot SSE 对话接口 | 新增：`pm/controller/PmRobotChatController.java`（SSE 流式输出，复用 `SseEmitter` 模式） | 无影响 |
| P2-8 | 定时调度 | 新增：`pm/config/PmSchedulerConfig.java`（`@Scheduled` 日结/周报/站会摘要） | 复用 `@EnableScheduling`，无修改 |

**改动影响**：新增文件约 30+ 个。工具类通过 `@Tool` 注解自动注册，`PmRobotGraphBuilder` 独立使用图引擎。0 处现有代码修改。

### 阶段 3：治理与决策（第 7-8 周）

| ID | 任务 | 改动文件 | 影响范围 |
|---|---|---|---|
| P3-1 | 三种运行模式引擎 | 新增：`pm/service/PmModeService.java`（全自动/半自动/人工切换） | 无影响 |
| P3-3 | AI 自适应阻塞决策引擎 | 新增：`pm/service/BlockerDecisionEngine.java`（四级研判算法 + 五种策略执行） | 无影响 |
| P3-4 | 变更影响评估 | 新增：`pm/service/ChangeImpactService.java` | 无影响 |
| P3-5 | Agent Worker 能力注册和分配 | 新增：`pm/service/WorkerAssignmentService.java` | 无影响 |
| P3-6 | @mention 解析服务 | 新增：`pm/service/PmMentionService.java`（解析 @Worker 并转化为任务分配） | 无影响 |
| P3-7 | 跨项目调度引擎 | 新增：`pm/service/CrossProjectScheduler.java`（多因子排序 + 抢占规则 + 冲突检测） | 无影响 |
| P3-8 | 生产力评估 | 新增：`pm/service/ProductivityService.java`（五维 PI 计算 + 趋势分析） | 无影响 |

**改动影响**：新增文件约 12 个。P3-2 需在 Approval 模块新增一个枚举值（`GATE_APPROVAL`），其他无影响。

### 阶段 4：前端实现（第 6-10 周，与阶段 2/3 并行）

| ID | 任务 | 改动文件 | 影响范围 |
|---|---|---|---|
| P4-1 | 路由注册 | **修改**：`mateclaw-ui/src/router/index.ts` 新增 `/pm-robot` 路由组 | ⚠️ 修改 1 个现有文件，新增路由配置 |
| P4-2 | 侧边栏导航 | **修改**：`mateclaw-ui/src/views/layout/MainLayout.vue` 在 `navGroups.core` 添加 PM Robot 菜单项 | ⚠️ 修改 1 个现有文件，新增 1 个菜单项 |
| P4-3 | 能力系统（可选） | **修改**：`capabilities.ts` + `Capability.java` 新增 `manage:pm-robot`<br>**修改**：`RoleCapabilities.java` 将新能力授予 member+ | ⚠️ 修改 3 个现有文件（如不需要独立权限控制则可跳过） |
| P4-4 | API 调用层 | 新增：`mateclaw-ui/src/api/pm/*.ts`（约 10 个文件） | 无影响 |
| P4-5 | Pinia Store | 新增：`mateclaw-ui/src/stores/pm/*.ts`（约 5 个文件） | 无影响 |
| P4-6 | 仪表盘页面 | 新增：`mateclaw-ui/src/views/pm/PmDashboard.vue` | 无影响 |
| P4-7 | 需求/迭代/任务页面 | 新增：`PmStoryBacklog.vue`, `PmIterations.vue`, `PmTasks.vue` | 无影响 |
| P4-9 | 验收/变更/交付页面 | 新增：`PmAcceptance.vue`, `PmChanges.vue`, `PmReleases.vue` | 无影响 |
| P4-10 | 团队成员页面 | 新增：`PmTeamMembers.vue` | 无影响 |
| P4-11 | 资料中心页面 | 新增：`PmDocuments.vue` | 无影响 |
| P4-12 | PM Robot 对话页面 | 新增：`PmRobotChat.vue` | 无影响 |
| P4-13 | AI 助理页面 | 新增：`PmAssistant.vue`（含启动向导） | 无影响 |
| P4-14 | 统计图表页面 | 新增：`PmAnalytics.vue` | 无影响 |
| P4-15 | 公共组件 | 新增：`mateclaw-ui/src/components/pm/*.vue`（约 10 个） | 无影响 |

**改动影响**：修改现有文件 2-5 个（路由、导航、可选能力系统），新增文件约 45+ 个。

### 阶段 5：集成测试与上线（第 10-11 周）

| ID | 任务 | 改动文件 | 影响范围 |
|---|---|---|---|
| P5-1 | 全流程端到端测试 | 新增测试类 | 无影响 |
| P5-2 | PM Robot 种子模板 | 新增数据迁移 SQL（预置 5 类流程模板） | 无影响 |
| P5-3 | 性能测试 | 百级任务并发验证 | 无影响 |
| P5-4 | 用户文档 | 新增 `docs/pm-robot-guide.md` | 无影响 |

---

### 改动影响总结

| 类别 | 数量 | 说明 |
|---|---|---|
| **新增 Java 文件** | ~150 个 | Controller/Service/Entity/Repository/Tool/Config |
| **新增前端文件** | ~45 个 | Views/Components/Stores/API |
| **新增 Flyway 迁移** | 2 个 | H2 + Kingbase（V172） |
| **修改现有 Java 文件** | 0-1 个 | 仅当 P3-2 需要新增审批类型枚举值时修改 Approval 模块 |
| **修改现有前端文件** | 2-5 个 | 路由 1 + 导航 1 + 能力系统 3（可选） |
| **修改 application.yml** | 1 处 | 新增 `mate.pm-robot.enabled` |
| **修改 SecurityConfig** | 0 处 | 不需要，PM Robot 只需认证用户访问 |

**核心原则**：PM Robot 完全独立模块，最大程度复用现有基础设施（Agent 运行时、Workspace 隔离、JWT 认证、工具注册、SSE 流式输出），最小化对现有代码的修改。

---

## 十三、原型页面清单

原型文件：`docs/v4-goal-agent/goal-agent-prototype-v2.html`

### 左侧菜单结构

```
项目工作台
├── 我的工作（Dashboard）
├── 我的目标
├── 工作任务（带数字角标）
└── 目标规划

风险与质量
├── 阻塞 & AI 决策（带数字角标）
├── 风险登记册
├── 验收中心
└── 变更管理

分析与交付
├── 统计图表
├── 交付管理
├── 团队成员
└── 资料中心

AI 工作室
├── PM Robot 对话
└── AI 助理
```

### 页面详情

| 页面 | 内容 | 交互 |
|---|---|---|
| 我的工作 | KPI四卡片 + 今日任务执行(左) + 阻塞看板/燃尽趋势/AI决策动态(右纵向) | 板块Tab切换任务 / 点击弹窗 |
| 我的目标 | 五级目标树 + 目标详情（关键结果/进度） | 点击展开目标详情 |
| 工作任务 | 列表/看板双视图 + 搜索/板块/状态/优先级筛选 | 切换视图 / 点击弹窗 |
| 目标规划 | 目标拆解链路 + 本周智能规划草案 + 规划校验 | 生成计划 / 确认发布 |
| 阻塞 & AI 决策 | 四级阻塞看板 + AI决策方案 | 采纳/拒绝 |
| 风险登记册 | 风险列表 + 概率×影响矩阵 | 全部展示 |
| 验收中心 | 验收项列表 + 逐条规则评分 | 评分展示 |
| 变更管理 | 变更请求 + 影响评估 | 批准/拒绝 |
| 交付管理 | 交付节点 + 统计 | 全部展示 |
| 团队成员 | Human+Agent统一视图 + 分类筛选 + RACI | Tab切换 + 邀请 |
| 资料中心 | 文件树 + 母版关系图 + 版本冲突 | 分类切换 + 详情 |
| PM Robot 对话 | Workspace级全局视角 + 跨项目调度 | 发送消息 |
| AI 助理 | 项目级对话 + 启动向导 + 能力面板 | 发送 + 快捷操作 + 向导 |
| 统计图表 | 燃尽图/阻塞趋势/AI决策分布/生产力 | 全部展示 |

---

## 十四、待确认的关键设计决策

| # | 决策点 | 建议 |
|---|---|---|
| 1 | 需求管理粒度 | 先 Story→Task 二级 |
| 2 | 阻塞 L2 自动决策 | 自动执行+通知用户 |
| 3 | 阻塞严重度阈值 | 先固定(0.25/0.50/0.75) |
| 4 | 项目启动入口 | 对话+引导式表单混用 |
| 5 | MVP 范围 | 先精简 15 表+核心闭环 |
| 6 | Worker 分配 | PM Robot 自动分配+人工可调整 |
| 7 | 团队协作粒度 | 任务级分配 |
| 8 | Agent Worker 共享 | 同一 Workspace 共享+负载均衡 |
| 9 | @mention | 新增 PmMentionService |
| 10 | 流程模板 | 预置 5 类+对话补充 |
| 11 | Worker 与 mate_agent | Worker 是 mate_agent 的子类型 |
| 12 | 模板市场 | 先私有+团队共享 |
| 13 | 抢占式调度 | P0 可抢占低优先（1.5×限制） |
| 14 | 生产力评估周期 | 每日快照 |
| 15 | P0 冲突超时 | 30 分钟无响应自动降级 |
| 16 | 并发冲突策略 | 乐观锁+操作日志 |
| 17 | 同步通知 | 项目内广播，仅通知受影响用户 |

---

## 十五、多人协作数据同步与并发冲突方案

### 15.1 现有对话模型分析

经过对 MateClaw 代码的深度分析，**两个不同用户与同一个 Agent 聊天时，各自拥有完全独立的 Conversation**：

```
用户 A → AI 助理 → conv_A（username="A", agentId=AI_ASSISTANT）
用户 B → AI 助理 → conv_B（username="B", agentId=AI_ASSISTANT）
```

关键发现：
- 前端 `newConversation()` 生成唯一的 `conv_<timestamp>_<random>` conversationId
- 后端 `getOrCreateConversation` 按 conversationId+username 隔离，不同用户互不可见
- `isConversationOwner` 检查 username 匹配，跨用户访问直接 403
- 例外：IM 渠道（飞书/钉钉）使用 `username="system"` 的共享会话

**对 PM Robot 的影响**：用户 A 和用户 B 各自有独立的 AI 助理对话，但**共享同一个项目数据**。对话上下文隔离，但项目状态共享。

### 15.2 问题场景

同一 Workspace 下，多个 Human User 通过各自的 AI 助理对话，共享项目数据：

```
Workspace（租户）
├── Human A → AI 助理（conv_A）→ "把 M2 审批人改成财务总监"
├── Human B → AI 助理（conv_B）→ "把 M2 审批人改成产品总监"
├── PM Robot → 自动调整 M2 任务排期
└── 场地 Worker → 更新 M2 关联任务的进度为 95%

冲突：M2 的审批人字段被两次写入，任务排期被三方同时修改。
```

### 15.3 核心设计原则

> **参考 Git 的协作模型：每条操作记录都有"版本号"，冲突时按规则自动合并或提示人工处理。**

| Git 概念 | PM Robot 对应 |
|---|---|
| 工作区 (Working Directory) | 用户/AI 助理的对话上下文 |
| 暂存区 (Staging) | 确认前的操作草案 |
| 提交 (Commit) | 写入数据库的操作记录 |
| 分支 (Branch) | 项目操作日志的版本链 |
| 合并 (Merge) | 多用户操作的自动合并 |
| 冲突 (Conflict) | 无法自动合并时的人工介入 |

### 15.4 乐观锁 + 操作日志 方案

每条可修改的项目记录都有 `version` 字段：

```
读取时获取 version → 写入时检查 version → 
  ├─ 匹配 → 写入成功，version+1
  └─ 不匹配 → 已被他人修改 → 自动合并或提示冲突
```

#### 15.4.1 数据模型

```json
// pm_operation_log（操作日志表）
{
  "id": "op-001",
  "projectId": "proj-001",
  "entityType": "MILESTONE",       // 被操作实体类型
  "entityId": "ms-m2",
  "fieldName": "approverUserId",   // 被修改字段
  "oldValue": "user-002",
  "newValue": "user-003",
  "sourceType": "AI_ASSISTANT",    // HUMAN / AI_ASSISTANT / PM_ROBOT / AGENT_WORKER
  "sourceUserId": "user-001",      // 谁发起的
  "sourceSessionId": "sess-abc",   // 哪个对话会话
  "version": 5,                    // 操作时的版本号
  "mergedFrom": null,              // 是否来自冲突合并
  "timestamp": "2026-08-06T15:30:00+08:00"
}
```

#### 15.4.2 自动合并规则

| 冲突场景 | 自动合并策略 | 示例 |
|---|---|---|
| **不同字段** | 自动合并，无冲突 | A 改审批人 + B 改截止日期 → 两个都生效 |
| **同一字段，不同值** | 时间戳后写入者覆盖，前写入者收到通知 | A 先改审批人→B 后改审批人→B 的值生效，A 收到覆盖通知 |
| **同一字段，同一值** | 自动去重 | A 和 B 都改成"产品总监"→ 只执行一次 |
| **依赖链断裂** | 自动检测 + 暂停操作 + 通知 | A 删除任务 X，B 正在给 X 添加依赖→ B 的操作被暂停 |
| **PM Robot 操作 vs 人工操作** | 人工操作优先级 > PM Robot | 人改的覆盖 PM Robot 改的，但记录决策日志 |

#### 15.4.3 冲突处理流程

```
用户 A 操作 → 读取 version=5
用户 B 操作 → 读取 version=5

用户 A 先写入 → version 匹配 5 → 写入成功 → version 变为 6
用户 B 后写入 → version 5 ≠ 6 → 冲突检测：
  │
  ├─ 不同字段 → 自动合并 → 写入成功 → version 7
  ├─ 同字段 → B 的值覆盖 A → A 收到通知："你设置的审批人已被 B 修改为..."
  └─ 无法自动判断 → 生成冲突报告 → 推送给两个用户 → 人工选择保留哪个版本
```

### 15.5 同步通知机制

| 事件 | 通知范围 | 通知内容 |
|---|---|---|
| 流程变更 | 项目所有活跃成员 | "周先生 将 M2 审批人改为 财务总监" |
| 任务状态变更 | 任务负责人 + 依赖方 | "物料 Worker 将 物料验收 状态改为 已完成" |
| 冲突发生 | 冲突双方 | "你的修改与 周先生 的修改冲突，请确认" |
| PM Robot 自动调整 | 受影响方 | "PM Robot 已将 3 个任务重排到 8/13" |

### 15.6 AI 助理的"草稿→确认"模式

AI 助理的建议**不直接写入数据库**，而是先生成草案：

```
用户："把 M2 审批人改成财务总监"
  → AI 助理生成草案（存储在对话上下文中，不写入 DB）
  → 用户确认："好的，确认修改"
  → 写入数据库（带 version 检查）
```

**只有以下操作直接写入**（无需确认）：
- PM Robot 的自动阻塞决策（Level 2 自动调整）
- Agent Worker 的任务进度更新
- 定时任务的日结/周报生成

### 15.7 数据同步架构

```
┌─────────────────────────────────────────────────────────────┐
│                      操作日志总线                            │
│                                                              │
│  Human A ──→ AI 助理 ──→ 操作日志 ──→ DB (version check)    │
│  Human B ──→ AI 助理 ──→ 操作日志 ──→ DB (version check)    │
│  PM Robot ──→ 自动操作 ──→ 操作日志 ──→ DB (version check)   │
│  Worker   ──→ 状态更新 ──→ 操作日志 ──→ DB (version check)   │
│                                                              │
│  所有写入通过操作日志 → version 校验 → 冲突检测 → 自动/人工  │
│                                                              │
│  ProjectEventBus 广播 → 所有活跃 session 收到事件 → 刷新     │
└─────────────────────────────────────────────────────────────┘
```

### 15.8 不同 Session 间的状态同步方案

#### 15.8.1 现有能力分析

MateClaw 已有成熟的 SSE 基础设施，可直接复用：

| 现有机制 | 能力 | 对 PM Robot 的适用性 |
|---|---|---|
| **ChatStreamTracker** | 聊天流 SSE broadcast + 断线重连回放 | ✅ 直接复用 —— 当前 AI 助理对话已在使用 |
| **WikiProgressBus** | CopyOnWriteArrayList 订阅表 | ✅ 模式可借鉴 —— 按 projectId 订阅 |
| **DreamEventBroadcaster** | Spring @EventListener + SSE | ✅ 模式可借鉴 —— 事件驱动推送 |
| **Notification 模块** | 纯轮询（无 SSE） | ⚠️ 不够实时 —— 需补充 SSE 推送 |
| **EventSource 限制** | 不支持 Authorization header | ⚠️ 前端需用 fetch API 替代 |

**核心发现**：`ChatStreamTracker` 已有成熟的 broadcast 机制 —— 但它是按 `conversationId` 隔离的。同一项目的不同 session 需要一个新的**按 projectId 订阅**的广播机制。

#### 15.8.2 新增：ProjectEventBus

**设计**：借鉴 `WikiProgressBus` 的 CopyOnWriteArrayList 订阅表模式，新增 `ProjectEventBus`：

```
ProjectEventBus（新增组件）
  ├── 订阅表：{projectId → SseEmitter[]}
  ├── 事件类型：task.updated / story.updated / milestone.updated / blocker.updated / acceptance.updated / project.modeChanged / operation.conflict
  └── 生命周期：session 建立时注册，断开时移除
```

```java
// ProjectEventBus.java（新增）
@Component
public class ProjectEventBus {
    // projectId → 所有正在监听此项目的 SseEmitter
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    /**
     * 用户打开 AI 助理页面时注册
     */
    public void subscribe(String projectId, SseEmitter emitter) {
        subscribers.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> unsubscribe(projectId, emitter));
        emitter.onTimeout(() -> unsubscribe(projectId, emitter));
    }

    /**
     * 项目数据变更时广播
     * 任何 Service 写入 DB 后调用此方法
     */
    public void broadcast(String projectId, String eventType, String jsonPayload) {
        var list = subscribers.get(projectId);
        if (list == null) return;
        for (var emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(jsonPayload));
            } catch (Exception e) {
                list.remove(emitter); // 自动清理断开的连接
            }
        }
    }

    private void unsubscribe(String projectId, SseEmitter emitter) {
        var list = subscribers.get(projectId);
        if (list != null) list.remove(emitter);
    }
}
```

#### 15.8.3 前端 SSE 订阅

AI 助理页面打开时，自动建立 SSE 连接：

```typescript
// mateclaw-ui/src/composables/pm/useProjectSync.ts
export function useProjectSync(projectId: string) {
    let abortController: AbortController | null = null;

    async function connect() {
        abortController = new AbortController();
        const response = await fetch(`/api/v1/pm-robot/projects/${projectId}/events`, {
            signal: abortController.signal,
            headers: { Authorization: `Bearer ${token}` }
        });
        const reader = response.body!.getReader();
        // 解析 SSE 事件...
        // task.updated → 更新 Pinia store → 页面自动刷新
        // operation.conflict → 弹出冲突提示
    }

    function disconnect() {
        abortController?.abort();
    }

    return { connect, disconnect };
}
```

#### 15.8.4 状态同步完整链路

```
用户 A 在 session_A 中：
  "把 M2 审批人改成财务总监"
    → AI 助理确认 → 写入 DB (version check)
    → ProjectEventBus.broadcast("proj-001", "milestone.updated", {...})
    
用户 B 在 session_B 中（正在查看同一个项目）：
    ← 收到 SSE 事件 "project.updated"
    ← Pinia store 自动更新 M2 审批人 → "财务总监"
```

**同步粒度**：

| 事件类型 | 触发时机 | 影响页面区域 |
|---|---|---|
| `task.updated` | 任务状态/进度/负责人变更 | 我的工作、工作任务、仪表盘 |
| `story.updated` | 需求状态/优先级变更 | 我的目标 |
| `blocker.updated` | 阻塞状态变更、AI 决策执行 | 阻塞 & AI 决策、仪表盘 |
| `acceptance.updated` | 验收规则通过/失败 | 验收中心 |
| `project.modeChanged` | 全自动/半自动/人工切换 | 顶栏模式指示器 |
| `operation.conflict` | 并发写入冲突 | 弹窗提示，让用户选择保留版本 |

**AI 助理对话上下文同步**：收到项目事件后，AI 助理的系统提示词自动注入最新状态：

```
系统提示词更新：
  "当前项目状态已更新：M2 审批人已由 周先生 改为 财务总监（操作人：周先生，08-06 15:30）"
```

### 15.10 新增数据表

| 表名 | 说明 |
|---|---|
| `pm_operation_log` | 操作日志（实体/字段/旧值/新值/来源/版本号/会话） |

### 15.11 与现有架构的集成

| 现有能力 | 使用方式 |
|---|---|
| 数据库行锁（MyBatis-Plus `@Version`） | 乐观锁 version 字段 |
| SSE 流式推送 | 实时同步通知 |
| 站内通知模块 | 冲突/变更通知 |
| Agent 决策日志（`pm_agent_decision_log`） | PM Robot 自动操作可追溯 |
| `ChatStreamTracker` broadcast 模式 | `ProjectEventBus` 借鉴其 CopyOnWriteArrayList 订阅表 |
| `WikiProgressBus` SSE 推送模式 | `ProjectEventBus` 借鉴其 per-resource 订阅设计 |



PM Robot 方案核心要点：

1. **术语体系**：PM Robot（大脑）+ Agent Worker（执行员工）+ Human User（真实用户）+ AI 助理（项目助理 Agent）
2. **通用项目管理**：去软件特化，Risk + BlockerDecision 体系覆盖任何类型项目
3. **AI 自适应阻塞决策** ★：四级研判 × 五种策略，Level 2 自动执行+通知
4. **AI 全流程驱动**：用户给目标→AI 完成需求评审→拆分→排期→Worker 分配→执行→阻塞处理→验收→收尾
5. **三模式治理**：全自动/半自动/人工，随时切换
6. **对话式流程搭建**：识别类型→加载模板→补充提问→完善流程+分工，全程可调整
7. **项目模板化+模板市场**：提炼→去个性化→发布（私有/团队/公开）
8. **AI 工作室双入口**：PM Robot 对话（Workspace 级/管理员）+ AI 助理（项目级/全参与者）
9. **团队成员统一视图**：Human+Agent 同一清单，RACI+能力标签+负载
10. **Agent Worker 共享模型**：同一 Workspace 共享，PM Robot 按能力+负载分配
11. **跨项目调度**：多因子排序+抢占规则+冲突分级处理
12. **生产力评估**：五维 PI 指数+任务类型分组+负载-生产力曲线
13. **云盘 MCP 集成**：文件存云盘，MateClaw 管引用元数据+母版-派生关系
14. **资料复用追踪**：版本冲突检测+更新建议+复用统计
15. **邀请机制**：token+过期+确认/拒绝，待激活→活跃状态流转
16. **多人协作同步**：乐观锁+操作日志+ProjectEventBus实时广播
17. **仪表盘左右布局**：今日任务执行(左) + 阻塞看板/燃尽趋势/AI决策动态(右纵向)

**数据库 30 张表（含 `pm_operation_log` 操作日志表），PM Robot 工具 22 个，六大阶段全链路覆盖。**
