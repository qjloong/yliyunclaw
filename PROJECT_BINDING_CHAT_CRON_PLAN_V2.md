
# Project Binding / Chat / Cron 迭代计划 V2

## 状态说明
- [x] 已完成
- [ ] 待完成
- [~] 进行中

## 修改要求
- 对现有业务和功能进行扩展增强
- 新增功能不要影响原有代码逻辑和交互逻辑
- 参考codex项目的功能和业务能力，不做代码和ui对齐（codex的源码在这里https://github.com/openai/codex ）

## 第 5 阶段 · 客户端 Agent 交互与项目记忆优化

### 阶段目标
围绕当前客户端项目测试中暴露的 Agent 交互问题，补齐工作区切换后的默认助手恢复、权限说明、Project changes 本地资源定位，以及工作区级项目理解记忆，减少跨工作区状态污染、权限认知偏差和重复扫描工作区的低效体验。

### 阶段清单
- [~] 修复工作区创建后的默认助手未自动选中问题
- [~] 修复新建会话时旧 `agentId` 污染导致的“agent 不属于当前工作区”报错
- [~] 明确并展示默认权限 / 完全访问的实际行为边界
- [~] 补全聊天页与设置页中的权限说明文案
- [ ] 为右侧 Project changes 增加本地资源管理器定位入口
- [ ] 支持当前 project、修改文件、新增文件的定位操作
- [ ] 建立“同一工作区共享”的项目理解记忆层
- [ ] 减少同工作区连续提问时的重复目录扫描与重复文件读取
- [ ] 对齐主流 Agent 的工作区边界、恢复逻辑与访问边界表达
- [ ] 建立该阶段的持续更新机制，后续按子项持续回填状态与验收结果

### 5.1 工作区默认助手恢复与会话状态校正

**涉及前端组件 / 逻辑**
- ChatConsole.vue

**涉及后端校验链路**
- ChatController.java

**涉及接口与数据结构**
- 路由 query 中的 `agentId`
- 当前工作区下的已加载助手列表
- 会话恢复态中的 `conversationId` / `agentId`

**功能要求**
- 新建 workspace 后若自动创建默认助手，前端必须同步将其作为当前有效助手
- 工作区切换后，旧工作区遗留的 `agentId` 不能覆盖新工作区自动选中的助手
- 新建会话前先做前端有效性校验：仅允许当前工作区下存在的 `agentId`
- 路由恢复、页面刷新、会话恢复三种场景使用统一的助手解析优先级

**输出结果**
- 默认助手创建后自动选中，不再出现“有默认助手但未选中”
- 新工作区发起新会话时，不再引用旧工作区助手
- 后端“agent 不属于当前工作区”只保留为兜底保护，不再成为常见用户错误

**验证测试**
- 新建 workspace 后直接发送消息，使用的助手为新 workspace 默认助手
- 从 A workspace 切到 B workspace 后，新建会话不再携带 A 的 `agentId`
- 刷新页面后，若路由中助手无效，自动回退到当前 workspace 可用助手
- 后端仍保持跨工作区助手校验不变

## 第 0 阶段 · 基础能力
- [x] 会话级工作目录持久化与路径校验
- [x] Web Chat / 排队消息 / 重放链路透传工作目录
- [x] 聊天界面工作目录编辑能力
- [x] Cron 执行来源的 workspace 路径透传

## 第 1 阶段 · Agent 工作模式

### 阶段目标
补全聊天场景下的运行时工作模式能力，支持在不新增独立 Agent 的前提下启用 `Plan Mode`，并保证模式在发送、中断、排队、恢复、重放等链路中一致生效。

### 阶段清单
- [x] 补全 Agent 工作模式能力
- [x] 支持从聊天侧启用 `Plan Mode`，不强制依赖单独的计划型 Agent
- [x] 保持所选工作模式在中断 / 排队 / 恢复 / 重放链路中的一致性
- [x] 在聊天头部与输入区域展示当前运行模式

### 1.1 会话级运行模式持久化

**涉及后端类**
- ConversationEntity.java
- ConversationVO.java
- ConversationService.java
- ConversationController.java

**涉及接口与数据结构**
- `ConversationEntity.runtimeMode`
- `ConversationVO.runtimeMode`
- 会话运行模式更新接口
- Flyway 迁移脚本：mysql / h2 会话表新增 `runtime_mode`

**功能要求**
- 在会话维度保存当前运行模式，支持“默认模式”和“计划模式”
- 会话切换模式后，后续消息默认沿用该模式
- 老会话未配置时自动回退到默认模式
- 不改变 `AgentEntity.agentType` 的持久化配置

**输出结果**
- 会话详情接口可返回 `runtimeMode`
- 会话更新接口可修改 `runtimeMode`
- 服务重启后模式不丢失

**验证测试**
- 更新会话模式后再次查询，会话返回值正确
- 旧会话不带 `runtimeMode` 时仍可正常聊天
- 重启服务后会话模式保持不变

### 1.2 聊天请求链路透传运行模式

**涉及后端类**
- ChatController.java
- ChatStreamTracker.java

**涉及前端组件 / 逻辑**
- useChat.ts
- index.ts
- index.ts

**涉及接口与数据结构**
- 聊天请求体新增 `runtimeMode`
- 队列输入对象新增 `runtimeMode`

**功能要求**
- 聊天发送、中断续发、排队恢复、重连恢复都要保留运行模式
- 本次请求未显式传值时，自动使用会话默认模式
- 审批恢复继续执行时也要保留原始模式

**输出结果**
- SSE、同步聊天、队列恢复三条链路模式一致
- 运行模式不会因中断、排队或重连而丢失

**验证测试**
- 切换到计划模式后发送消息，中断再恢复，仍按计划模式运行
- 连续发送两条消息，第二条进入排队，恢复后模式正确
- 请求不传 `runtimeMode` 时自动回退到会话模式

### 1.3 运行时 Agent 构建支持模式覆盖

**涉及后端类**
- AgentService.java
- AgentGraphBuilder.java
- AgentEntity.java

**涉及接口与数据结构**
- 运行时上下文新增模式覆盖参数
- Agent 缓存需区分“配置类型”和“本次运行模式”

**功能要求**
- 默认模式下保持原 Agent 类型逻辑
- 当会话或请求指定计划模式时，本轮执行切到 `plan_execute`
- 不强制新增单独的计划型 Agent

**输出结果**
- 同一个 Agent 可在不同会话里以不同工作模式运行
- `Plan Mode` 可直接从聊天入口启用

**验证测试**
- 同一 Agent 在默认模式和计划模式下执行，行为不同
- 原有 `plan_execute` Agent 行为不受影响
- 切换模式时不会复用错误缓存实例

### 1.4 前端运行模式切换与展示

**涉及前端组件**
- ChatConsole.vue
- ChatInput.vue
- MessageBubble.vue

**涉及接口与数据结构**
- index.ts
- index.ts
- 国际化：
  - zh-CN.ts
  - en-US.ts

**功能要求**
- 在聊天头部或输入区提供运行模式切换入口
- 显示当前模式状态
- 切换后同步写回会话，并影响后续发送

**输出结果**
- 用户可明确看到当前工作模式
- 计划模式下可稳定展示计划步骤

**验证测试**
- 切换模式后头部标签即时变化
- 发送消息时请求体包含正确的 `runtimeMode`
- 中英文文案完整一致

## 第 2 阶段 · 当前工作 Project 展示与权限模型

### 阶段目标
补齐当前工作 project 的展示能力，并在 workspace 维度建立“有限权限 / 完全权限”的 project 操作权限模型，确保用户可以清楚理解 Agent 当前工作目录与操作边界。

### 阶段清单
- [x] 补全当前工作 project 展示
- [x] 在聊天界面展示当前 workspace / project 上下文
- [x] 展示当前有效 project 目录与 workspace 根目录的关系
- [x] 增加 project 操作权限配置
- [x] 有限权限模式下：高风险 Agent 操作需要显式审批
- [x] 完全权限模式下：Agent 可直接执行原本需审批的受控操作
- [x] 保持对不可逆高危操作的绝对阻断

### 2.1 会话返回工作项目上下文

**涉及后端类**
- ConversationVO.java
- ConversationService.java
- WorkspaceEntity.java

**涉及接口与数据结构**
- 会话返回补充：
  - workspace 名称
  - workspace 根目录
  - 当前有效工作目录
  - 是否为根目录运行
  - project 相对路径展示字段

**功能要求**
- 由后端统一输出“当前工作 project 上下文”
- 前端不再自行推导根目录与子目录关系
- 根目录与子目录场景可明确区分

**输出结果**
- 前端可稳定展示当前工作 project
- project 语义统一为“workspace 根目录 + 当前有效工作目录”

**验证测试**
- 根目录会话返回“根目录运行”标识
- 子目录会话返回正确相对路径
- 空工作目录时自动回退为 workspace 根目录

### 2.2 聊天头部和工具栏展示当前 Project

**涉及前端组件**
- ChatConsole.vue
- useWorkspaceStore.ts

**涉及接口与数据结构**
- 会话对象补充 project 上下文字段
- 国际化：
  - zh-CN.ts
  - en-US.ts

**功能要求**
- 展示当前 workspace 名称
- 展示当前 project 路径
- 明确当前是 workspace 根目录还是子目录
- 长路径支持省略显示与悬浮完整查看

**输出结果**
- 用户始终知道 Agent 当前基于哪个目录工作
- project 与 workspace 关系清晰

**验证测试**
- 切换工作目录后，头部展示同步变化
- 根目录与子目录展示样式不同
- 路径过长时不破坏布局

### 2.3 工作目录切换反馈补齐

**涉及前端组件**
- ChatConsole.vue

**涉及后端接口**
- ConversationController.java

**功能要求**
- 成功切换目录后，明确提示已切换到哪个 project
- 非法目录、越界目录、不可访问目录返回可读错误原因
- 前端将后端错误转换为用户可理解文案

**输出结果**
- 用户能感知 project 切换成功与失败原因
- 减少“切换后不知道当前在哪”的不确定性

**验证测试**
- 合法目录切换后提示正确
- 越界目录被拒绝且提示原因
- 空值恢复根目录时展示正确

### 2.4 Workspace 配置中新增 Project 权限模式

**涉及后端类**
- WorkspaceEntity.java
- WorkspaceService.java
- WorkspaceController.java

**涉及前端页面**
- index.vue

**涉及接口与数据结构**
- `settingsJson.projectPermissionMode`
- 枚举建议：
  - `limited`
  - `full`

**功能要求**
- workspace 维度支持配置 project 权限模式
- 后端统一解析与回传，不让前端直接处理原始 JSON 语义
- 历史数据缺失字段时默认有限权限

**输出结果**
- 每个 workspace 可独立配置 project 权限策略
- 设置页可查看和修改当前模式

**验证测试**
- 创建和更新 workspace 后可正确回读权限模式
- 历史 workspace 默认展示为有限权限
- 前端设置页修改后刷新仍一致

### 2.5 工具执行链路接入权限模式

**涉及后端类**
- ToolExecutionExecutor.java
- DefaultToolGuard.java
- ToolGuardResult.java
- WorkspacePathGuard.java
- ApprovalWorkflowService.java
- PendingApproval.java

**涉及接口与数据结构**
- 工具执行上下文读取 `projectPermissionMode`
- 审批决策结合 `projectPermissionMode`

**功能要求**
- `limited` 下，原 `NEEDS_APPROVAL` 行为保持
- `full` 下，审批类操作可直接执行
- `BLOCK` 类高危操作、workspace 越界操作仍必须拦截

**输出结果**
- 有限权限与完全权限行为边界清晰
- 完全权限不等于无限制，仍保留底线防护

**验证测试**
- 有限权限下执行 shell、文件写入、cron 创建，确认触发审批
- 完全权限下执行同类操作，确认可直接执行
- 完全权限下尝试越界路径或高危操作，确认仍被阻断

### 2.6 聊天页展示当前权限状态

**涉及前端组件**
- ChatConsole.vue
- ChatInput.vue
- MessageBubble.vue

**涉及前端页面**
- index.vue

**涉及接口与数据结构**
- workspace / conversation 返回中可读当前权限模式
- 中英文文案同步补齐

**功能要求**
- 聊天区显示当前 project 权限模式
- 有限权限时提示“需审批”
- 完全权限时提示“可直接执行受控操作”
- 审批消息展示与当前权限模式保持一致

**输出结果**
- 用户发消息前即可知道 Agent 的操作权限范围
- 审批行为不再是黑盒体验

**验证测试**
- 切换权限模式后，聊天页提示即时变化
- 有限权限模式下仍能看到审批卡片
- 完全权限模式下相同操作不再出现审批卡片

## 第 3 阶段 · 聊天快捷指令

### 阶段目标
补全聊天输入框中的 `/` 和 `@` 快捷交互，先实现前端侧的建议面板与结构化输入体验，再视需要扩展到服务端解析与候选检索。

### 阶段清单
- [x] 斜杠命令交互（`/`）
- [x] mentions / project 引用（`@`）
- [x] 快捷指令建议面板与解析逻辑

### 3.1 `/` 命令输入体验

**涉及前端组件**
- ChatInput.vue
- ChatConsole.vue

**涉及前端逻辑**
- 建议新增输入解析逻辑到 chat

**涉及接口与数据结构**
- 第一阶段先做纯前端命令解析
- 第二阶段如需服务端辅助，再扩展 index.ts

**功能要求**
- 输入 `/` 时弹出命令建议面板
- 首批命令优先覆盖：
  - 切换运行模式
  - 切换工作目录
  - 查看当前 project
  - 查看当前权限模式

**输出结果**
- 聊天输入框具备基础命令式操作能力
- 常见设置不需要频繁打开额外面板

**验证测试**
- 输入 `/` 时出现候选列表
- 支持键盘上下选择和回车确认
- 普通文本输入不受影响

### 3.2 `@` 引用当前上下文对象

**涉及前端组件**
- ChatInput.vue

**涉及接口与数据结构**
- 第一阶段候选项可由前端本地生成
- 第二阶段再考虑增加服务端候选检索接口

**功能要求**
- 输入 `@` 时提供候选项
- 第一阶段优先支持引用：
  - 当前 workspace
  - 当前 project
  - 当前 agent
- 选择后形成稳定引用 token

**输出结果**
- 用户能在输入中显式引用当前上下文
- 为后续更多引用对象扩展打基础

**验证测试**
- 输入 `@` 后出现候选列表
- 选择后内容正确插入输入框
- 移动光标和编辑文本时不会破坏已插入引用片段

## 第 4 阶段 · 定时 Agent 任务

### 阶段目标
让 cron 任务在 project 绑定、工作目录、生效权限模式、执行结果摘要等维度与聊天能力保持一致，形成统一的 Agent 执行语义。

### 阶段清单
- [x] 增强 cron 任务编排的 project 绑定意识
- [x] 展示定时任务的 project 上下文与权限上下文
- [x] 改进 cron 重放与执行摘要

### 4.1 Cron 数据结构补齐 Project 上下文

**涉及后端类**
- CronJobEntity.java
- CronJobDTO.java
- CronJobService.java
- CronChatOriginFactory.java

**涉及前端页面**
- CronJobs.vue

**涉及接口与数据结构**
- cron 任务补充：
  - workspace 上下文
  - `workingDirectory`
  - project 展示字段
  - 生效权限模式

**功能要求**
- cron 创建与执行都能携带 project 上下文
- 未配置工作目录时默认在 workspace 根目录运行
- 执行时沿用与聊天一致的目录解析逻辑

**输出结果**
- 定时任务可明确绑定到某个 project 上下文
- 聊天与定时任务的 project 语义保持一致

**验证测试**
- 创建 cron 时保存 project 上下文，查询结果可回显
- 执行 cron 时确认使用预期工作目录
- 历史任务未配置目录时仍按根目录工作

**当前进展（已完成）**
- `CronJobEntity` / `CronJobDTO` 已补充 `workingDirectory`，并回传 workspace / project / permission 展示字段
- `CronJobService` 已在创建、更新、查询时统一校验并归一化工作目录
- `CronChatOriginFactory` 已在 cron 执行时优先使用任务自身的 `workingDirectory`

### 4.2 Cron 列表与执行摘要展示权限和 Project 上下文

**涉及前端页面**
- CronJobs.vue

**涉及接口与数据结构**
- index.ts
- index.ts

**功能要求**
- 在 cron 列表或详情中展示：
  - 当前 project
  - 当前权限模式
  - 最近执行结果摘要
- 明确区分“审批等待”“权限拒绝”“执行失败”“执行成功”

**输出结果**
- 用户可快速理解定时任务的执行环境与失败原因
- 排障成本降低

**验证测试**
- 列表页和详情页都能展示 project 与权限字段
- 四种状态摘要展示正确且可区分
- 执行历史中可看出是否因权限问题失败

**当前进展（部分完成）**
- `CronJobs.vue` 已在列表和详情页展示当前 project、workspace 与 Project 权限模式
- Cron 编辑表单已支持配置 `workingDirectory`

**当前进展（已完成）**
- `mate_cron_job_run` 已补充执行摘要字段，统一持久化最近一次执行的摘要状态与说明文本
- 后端已按“审批等待 / 权限拒绝 / 执行失败 / 执行成功”归类 cron 执行结果，并对历史无摘要数据做回退推断
- `CronJobs.vue` 与 `Dashboard.vue` 已展示新的执行摘要 badge，同时保留最近投递状态用于区分“执行成功但投递失败”等场景

## 实施顺序建议
1. 先完成“第 1 阶段 · Agent 工作模式”，打通 `Plan Mode` 从前端到后端的完整链路。
2. 再完成“第 2 阶段 · 当前工作 Project 展示与权限模型”，补齐当前 project 可见性与执行边界。
3. 接着完成“第 3 阶段 · 聊天快捷指令”，提升输入效率与上下文引用体验。
4. 最后完成“第 4 阶段 · 定时 Agent 任务”，让 cron 与聊天在 project 与权限语义上统一。

## 阶段性输出要求
- 每完成一个阶段，需要输出本阶段已完成项、影响范围、回归验证结果、剩余风险。
- 若某阶段中途拆分为多个子任务，也要在子任务完成后输出阶段小结。
- 后续实施严格按本清单推进；若实现过程中发现结构性偏差，应同步回写更新 PROJECT_BINDING_CHAT_CRON_PLAN_V2.md。

## 当前执行说明
- 当前文档基于已完成的工作目录能力扩展形成。
- 当前版本已将后续任务细化到后端类、前端组件、接口与数据结构层级。
- 后续实施应按阶段推进，并在每个阶段完成后补充总结性输出。

## 已完成阶段小结（2026-05-11）

### 第 1 阶段 · Agent 工作模式
- 已完成会话级 `runtimeMode` 持久化，支持 `default` / `plan`。
- 已完成聊天请求、排队消息、审批重放链路的 `runtimeMode` 透传。
- 已完成运行时 Agent 构建覆盖：同一 Agent 可在聊天中按运行模式切换到 `plan_execute`。
- 已完成前端工作模式切换与展示，聊天头部可见当前模式。

### 第 2 阶段 · 当前工作 Project 展示与权限模型
- 已完成会话返回的结构化 project 上下文补充：workspace 名称、根目录、生效 project 路径、相对路径、是否根目录运行。
- 已完成聊天区当前 project / workspace 展示。
- 已完成 workspace 级 `projectPermissionMode` 配置，支持 `limited` / `full`。
- 已完成工具执行链路接入权限模式：
  - `limited`：风险操作继续走审批；
  - `full`：审批类操作直接执行；
  - `BLOCK` 与 workspace 越界仍保持强拦截。
- 已完成聊天页权限状态展示与工作区管理页权限配置展示。

### 当前验证结论
- 后端新增改动已通过静态错误检查。
- 前端新增改动已通过主要改动文件静态错误检查。
- 当前前端仍存在少量既有工程诊断（如路径别名解析、旧模板 lint），不属于本轮新增逻辑引入的问题。

### 第 3 阶段 · 聊天快捷指令
- 已完成聊天输入框 `/` 命令建议面板，支持键盘上下选择、回车确认、Tab 补全。
- 已完成纯前端 slash command 解析，支持：
  - `/mode default|plan`
  - `/cwd <path>`
  - `/project`
  - `/permission`
- 已完成 `@` 上下文引用候选，首批支持：
  - 当前 workspace
  - 当前 project
  - 当前 Agent
- 已完成快捷命令 / mention 面板国际化文案补齐。

### 第 4 阶段 · 定时 Agent 任务
- 已完成 cron 任务级 `workingDirectory` 持久化、校验与 project 上下文回传。
- 已完成 cron 列表 / 详情页 project、workspace、权限模式展示，并支持任务级工作目录编辑。
- 已完成 cron 执行来源统一绑定当前 project，上下文解析逻辑与聊天保持一致。
- 已完成 `mate_cron_job_run` 执行摘要字段扩展，支持：
  - `RUNNING`
  - `AWAITING_APPROVAL`
  - `PERMISSION_DENIED`
  - `EXECUTION_FAILED`
  - `EXECUTION_SUCCEEDED`
- 已完成 `CronJobs.vue` 与 `Dashboard.vue` 的执行摘要 badge 展示，并保留投递状态用于补充区分投递失败场景。

## 当前收尾结论（2026-05-12）
- 第 0 ～ 第 4 阶段的目标已全部完成，project 绑定、聊天快捷指令、plan mode、workspace 权限模型、cron 统一执行语义已形成闭环。
- 当前主链路已统一为：`workspace root + conversation / cron workingDirectory + projectPermissionMode + runtimeMode`。
- 会话聊天、审批重放、排队恢复、cron 执行与 dashboard 展示已对齐同一套 project / permission / execution summary 语义。

## 本轮影响范围
- 后端：会话、workspace、agent runtime、tool guard、cron lifecycle、dashboard run history、Flyway 迁移。
- 前端：ChatConsole、ChatInput、会话模型、workspace 设置页、CronJobs、Dashboard、国际化文案。
- 数据结构：会话级 `workingDirectory` / `runtimeMode`，workspace 级 `projectPermissionMode`，cron run 级 `executionSummaryStatus` / `executionSummaryText`。

## 当前验证状态
- [x] 前端类型检查通过：`pnpm exec vue-tsc --noEmit`
- [x] 本轮新增后端关键文件已通过编辑器静态错误检查
- [~] 完整 Maven 命令行构建待在本地具备可用 Maven 环境后补做
- [~] 数据库迁移与端到端手工回归待按部署环境执行

## 剩余风险
- 本轮未完成完整后端命令行编译与集成测试，仍需在真实 Maven 环境下做一次全量验证。
- cron / 审批 / 聊天恢复链路虽已完成静态校验，但仍建议做一次端到端回归，重点覆盖：审批等待、权限拒绝、执行成功但投递失败。
- `ChatConsole.vue` 近期发生过外部改动；后续若继续编辑该文件，应先重新读取最新内容后再修改。

## 下一阶段 · 回归验证与发布准备

### 阶段目标
将当前已完成的 project / chat / cron 能力从“开发完成”推进到“可稳定验收与发布”，补齐真实环境回归、迁移验证、测试资产与交付说明。

### 阶段清单
- [ ] 执行前后端完整构建与数据库迁移验证
- [ ] 补齐关键端到端回归用例
- [ ] 整理发布说明与操作手册

### 5.1 构建与迁移验证
- 在可用 Maven 环境中执行 `mateclaw-server` 全量编译与测试。
- 在 H2 / MySQL 环境验证 `V61` ～ `V64` 迁移可重复执行且结果正确。
- 确认旧数据升级后：会话、workspace、cron job、cron run 均能正确回填默认值与展示值。

### 5.2 端到端回归清单
- 聊天场景：默认模式 / plan 模式、`/mode`、`/cwd`、`/project`、`/permission`、`@workspace` / `@project` / `@agent`。
- project 场景：
  - 新会话绑定 project 子目录；
  - 未手动新建会话时先选 project 再发送首条消息；
  - 已有会话切换时 project 回显与隔离；
  - project 重置为 workspace root；
  - workspace 越界 / 不存在目录 / 非目录 / 符号链接逃逸阻断；
  - Review 面板与 Project Changes 侧边面板一致性；
  - Git 场景真实 changed-files 快照与非 Git 场景 fallback；
  - 移动端 Project Changes 抽屉交互。
- 权限场景：`limited` 下审批、`full` 下直接执行、越界路径阻断。
- cron 场景：workspace 根目录执行、子目录执行、审批等待、权限拒绝、执行成功、执行成功但投递失败。
- dashboard 场景：最近执行摘要与 cron 列表摘要一致。

### 5.3 交付与文档整理
- 补充 README / 操作手册中的 project、plan mode、project permission、cron workingDirectory 说明。
- 输出面向验收的测试结果清单与已知限制。
- 如需提交 PR，再按功能边界整理 commit / changelog。

### 5.4 补齐“选择本地文件夹作为 Project”需求
- 明确区分两类能力：
  - **已支持**：将服务端可访问的本地目录配置为 workspace 根目录，并在会话 / cron 维度切换到其子目录执行。
  - **待补齐**：提供更直观的“选择文件夹”交互，而不是仅靠手动填写目录路径。
- 当前真实语义应统一表述为：
  - 用户选择或配置一个本地文件夹；
  - Agent 后续基于该目录中的文件进行读取、分析、修改、生成与回答；
  - 所有文件操作必须受 workspace 边界与权限模式约束。
- 下一步补齐建议：
  - 已在聊天输入区的 Project 菜单中补了目录浏览器，可在 workspace 边界内逐级点击选择子目录；
  - workspace 设置页仍可继续补“选择目录 / 浏览目录”入口（若运行环境支持）；
  - 若部署形态是纯 Web，需要明确该目录必须是**服务端所在机器可访问的本地路径**；
  - 若未来提供桌面端 / 本机部署增强，可进一步补文件夹选择器与最近 Project 列表。
- 验收标准：
  - 用户无需记忆完整路径，也能完成 project 绑定；
  - 绑定后 Agent 可围绕该目录内文件完成解析、修改、问答；
  - 越界目录、不可访问目录、符号链接逃逸仍会被拒绝。
  - 若在已有多轮上下文的会话中切换 Project，前端会明确提示“建议新建会话再切换”，降低上下文 / memory 混用风险。
  - 在该风险提示中，用户可直接选择“新建会话并切换”，把目录变更与旧会话上下文隔离开。

### 5.5 当前权限交互逻辑复核
- 当前权限模型以 `workspace.projectPermissionMode` 为入口，分为：
  - `limited`：命中安全风险的操作进入审批；
  - `full`：原本“需要审批”的操作可直接执行；
  - 无论哪种模式，只要命中 `BLOCK` / workspace 越界 / 符号链接逃逸，仍直接拒绝。
- 当前审批采用 **findings-driven** 逻辑，而不是“按工具类型一刀切全部审批”：
  - 无风险发现 → 直接允许；
  - `MEDIUM` / `HIGH` → 需要审批；
  - `CRITICAL` → 直接阻断。
- 在当前实现下，`limited` 模式里典型会触发审批的场景包括：
  - 高风险 shell 命令（如删除、危险 SQL、权限放宽、下载即执行等）；
  - 命令参数中出现敏感凭据；
  - 访问敏感路径但未越过 workspace 边界时。
- 在当前实现下，以下场景通常**不会单独弹审批**：
  - project 边界内的普通 `read_file`；
  - project 边界内、未命中额外风险规则的普通 `write_file` / `edit_file`；
  - project 边界内、未命中风险规则的普通 shell 查询类命令。
- 当前 Web 交互已从“单次批准”补齐为 **作用域化批准**：
  - 输入区审批卡支持 `仅这次` / `本次对话中的同类操作` / `当前 Project 中的同类操作`；
  - 后端会把命中审批的调用归一化为 `toolName + severity + findings` 指纹；
  - 同一 conversation / 同一 project 内再次命中相同风险指纹时，会直接复用已授予的批准，不再重复弹窗。
- 当前这轮实现仍有边界：
  - IM 渠道的 `/approve` 仍按单次批准处理，尚未补齐作用域选择；
  - 还没有“查看 / 撤销已记住权限”的独立管理界面；
  - 尚未支持按工具类型 / 风险类别批量批准。
- 若后续继续对齐更完整的 Codex 风格权限体验，建议再补：
  - 已授权范围列表与撤销入口；
  - grant 命中的前端可视化提示（让用户知道本次为何未再弹审批）；
  - 更细粒度的授权 scope（如 workspace 级、带过期时间的 project 级）。

### 5.6 Review / Changes / Checkpoint 体验补齐
- 已补齐 assistant 回复级别的文件改动记录：
  - 后端会在 `write_file` / `edit_file` 成功后，把本轮新增 / 修改文件写入消息 `metadata.reviewSummary.files`；
  - 前端消息气泡增加 Review 展开区，可查看本轮改动文件、字节写入量、替换次数等信息。
- 已将“当前 Project 内修改文件列表”从单纯会话聚合，升级为**优先显示真实 project 快照**：
  - 后端会基于当前 conversation 的生效 `workingDirectory` 解析 project 目录；
  - 若该目录位于 Git 仓库内，则通过 `git status --porcelain` 采集当前 project 子树中的改动文件；
  - 前端 Review 面板优先展示该真实快照；若后端暂时拿不到快照，再回退到会话内已追踪文件聚合结果。
- 前端 Review 面板已补一层 project 变更摘要：
  - 除逐文件列表外，还会按 `added / modified / deleted / renamed / untracked` 聚合显示数量；
  - 用户可先快速判断这轮 project 改动规模，再决定是否继续下钻查看具体文件。
- 已补一个独立的 **Project Changes 侧边面板**：
  - 从聊天页头部可直接打开，不必依赖某一条消息的 Review 折叠区；
  - 面板内集中展示当前 project 路径、聚合变更摘要、当前变更文件列表、最近一次回复改动以及最近若干条 review 记录；
  - 在移动端以右侧抽屉形式显示，在桌面端作为独立右侧边栏显示。
- 当前变更快照覆盖的状态包括：
  - `added`
  - `modified`
  - `deleted`
  - `renamed`
  - `untracked`
- 关于 checkpoint / restore：
  - 当前系统**不支持通用的 restore checkpoint**（即不能把整个 agent run / project 文件状态一键恢复到某个历史检查点）；
  - 当前更接近的是审批 replay、plan replay、消息 / 元数据时间线回放，而非完整项目级快照恢复；
  - Review 面板已增加 Checkpoint 状态提示，明确向用户表达“当前暂不支持完整 restore checkpoint”。
- 当前边界与后续建议：
  - 若 project 目录不在 Git 仓库内，当前无法产出真实 changed-files 快照，只能展示本轮工具写入记录；
  - 若后续要实现真正 checkpoint restore，需要额外设计：
    - 文件级快照 / patch 存储；
    - checkpoint 命名与保留策略；
    - restore 前审批与冲突处理；
    - 与计划执行 / 审批 replay 的关系与 UI 展示。