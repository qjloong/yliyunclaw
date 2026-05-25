# MateClaw Agent Harness 实施清单（含账号类型与权限体验）

## Summary
- 在正式实施前，先建立唯一实施台账，冻结旧计划并迁移未完成项。
- 新增“账号类型与权限体验”阶段：把全局账号、工作区角色、菜单可见性、后端接口权限统一收口。
- 普通用户默认进入简洁工作台，只保留聊天、知识、记忆、个人工作区内可用能力；供应商、模型、安全策略、插件、系统级配置等高级入口仅管理员可见可用。

## Phase 0：实施基线与任务台账
状态：待实施

- 创建 `docs/agent-harness-implementation.md`。
- 记录当前 `git status` 中已有未提交改动，标注为“实施前已有”。
- 迁移 `PROJECT_BINDING_CHAT_CRON_PLAN_V2.md` 未完成项到新台账，旧计划冻结为历史输入。
- 台账字段固定为：阶段、状态、目标、涉及模块、修改文件、验收标准、测试结果、遗留问题。
- 完成标准：新台账成为后续唯一推进源，每阶段开始/完成都更新状态。

## Phase 1：账号类型、角色矩阵与简洁界面
状态：待实施

- 明确两层权限模型：
  - 全局账号类型：`admin`、`user`。
  - Workspace 角色：`owner`、`admin`、`member`、`viewer`。
- 后端补齐权限边界：
  - `/auth/users` 创建/列表等用户管理接口仅全局 `admin` 可用。
  - 模型供应商、模型配置、系统设置、插件管理、工具守卫、安全审计、MCP/ACP 高级配置默认仅全局 `admin` 或 workspace `owner/admin` 可用。
  - 普通 `user` 只能访问自己有成员身份的 workspace 和允许的业务功能。
- 前端补齐菜单与路由权限：
  - 普通用户默认只显示：Chat、Dashboard、Agents、Wiki、Memory，以及自己有权使用的 Channels/Skills。
  - 隐藏或禁用：Settings Models、System、Image/TTS/STT/Music/Video provider 配置、Tools、Plugins、Security、MCP Servers、Token Usage、成员管理等高级入口。
  - 若普通用户直接访问高级路由，显示无权限页或重定向到 Chat。
- 体验简化：
  - 普通用户 Chat 页面不展示复杂供应商/模型配置入口。
  - 普通用户只看到“当前可用模型/Agent”，不暴露 API Key、provider fallback、模型健康等运维细节。
  - 管理员保留完整控制台。
- 验收：
  - `admin` 登录可看到完整管理能力。
  - `user` 登录界面明显简化，无法进入模型供应商和系统配置页。
  - 后端接口即使绕过前端访问也能正确返回 403。

## Phase 2：Harness 运行模型与状态收口
状态：待实施

- 新增标准运行抽象：`HarnessRun`、`HarnessStep`、`HarnessToolInvocation`、`HarnessApproval`、`HarnessExecutionSummary`。
- 接入 ReAct、Plan-Execute、ACP、Subagent、Cron。
- 不改变现有聊天 API 和 SSE 行为，先增加统一运行记录。
- 验收：ReAct/Plan 一次聊天都能生成统一 run summary，工具调用和审批状态可追踪。

## Phase 3：Tool Metadata 与安全策略统一
状态：待实施

- 扩展工具 metadata：只读、破坏性、风险级别、超时、可重放、并发安全。
- 内置工具、MCP、插件工具、Skill 工具统一进入 ToolMetadata。
- Tool Guard 保留现有正则规则，同时接入 metadata、账号类型、workspace policy。
- 普通用户默认更严格：shell、写文件、cron、外部系统写操作默认需审批或禁用。
- 验收：不同账号类型下同一工具风险策略不同，审计记录完整。

## Phase 4：Workspace Policy 与 Codex 式 Coding Agent
状态：待实施

- 新增 Workspace Policy：`sandboxMode`、`approvalPolicy`、`networkPolicy`、`allowedPaths`、`deniedPaths`、`riskOverrides`。
- 内置一组可直接使用的默认 Agent / Starter Templates，至少覆盖：通用助手、代码审查员、研究分析师、客服助理、数据分析师、产品助理。
- 模板选择器默认优先展示这些官方模板，要求一键创建后即可直接对话，而不是只创建一个空壳 Agent。
- Chat 首次进入时，将“选 Agent / 新建对话 / 继续最近会话”收口为统一起始入口，降低用户第一次开聊的心智成本。
- 统一起始入口继续下沉官方 Starter Library：用户在 Chat 空状态下也能直接创建官方模板并立即开始，不必先跳去 Agent 管理页。
- Coding Agent Profile 支持探索、计划、修改、测试、总结。
- `coding mode` 运行时开关仅作为过渡期兼容/调试能力，不应成为长期主入口。
- 面向用户的正式形态应是“Coding Agent / Coding Profile / Capability Pack / 模板化 Agent”，由绑定的 Prompt、工具、Skill、知识源、安全策略共同定义，而不是把通用产品收敛成单一 coding 按钮体验。
- Git 写操作、危险 shell、跨路径写入按账号和 workspace policy 触发审批。
- 前端 ChatConsole 展示计划、工具调用、审批、文件变更、测试结果。
- 验收：默认 Agent 模板可直接创建并开始使用，且会带上完整 Prompt / 工作区文件 / 图标等预设；Chat 首次进入即可通过统一入口选择 Agent、直接创建官方 Starter 并开始对话，或继续最近会话；绑定 Coding Profile 的 Agent 可在项目目录内完成小型任务，普通用户不能绕过边界；运行时 `coding mode` 只作为兼容覆盖层存在。

## Phase 5：Project Understanding Cache 与上下文路由
状态：待实施

- 建立项目理解缓存：技术栈、目录结构、常用命令、测试策略、最近变更摘要。
- Context Router 统一调度 Workspace Memory、Fact Memory、Wiki、Session Search、Project Cache。
- 缓存按 workspace / project / agent profile 隔离。
- 验收：同项目连续提问减少重复扫描，切换 workspace 后上下文不串扰。

## Phase 6：Agent Profile 与行业能力包
状态：待实施

- 新增 `AgentProfile`、`CapabilityPack`、`DomainTemplate`。
- 将 Coding 能力产品化为标准 Profile/Pack：默认 Prompt、工具白名单、Skill 组合、Project Cache 策略、审批策略、可视化验收任务。
- 把 Phase 4 的官方默认 Agent 沉淀为正式 Starter Library：既能直接创建使用，也能作为 `AgentProfile` / `CapabilityPack` 的参考实现。
- 运行时模式切换只保留为内部 override：例如 `default / plan / coding`，用于调试、回放、实验，不作为最终主要产品心智。
- 模板覆盖：编程、庄园管理、农业、酒店运营、医学研究、制造业、教育。
- 每个模板包含 Prompt、默认工具、知识源、渠道、安全策略、mock 验收任务。
- 普通用户可使用管理员发布的模板；只有管理员可创建/修改系统级模板和能力包。
- 验收：模板创建的 Agent 只能看到绑定能力，mock connector 能跑通端到端任务。

## Phase 7：渠道、Cron、插件生态收口
状态：待实施

- 渠道事件统一进入 `InboundEvent`，绑定 workspace / agent / channel identity。
- Chat、WebChat、IM、Cron 共享 HarnessRun。
- 插件 API 扩展声明 tools、memory providers、channels、domain connectors、policies、health checks。
- Skill Market 区分 coding、research、domain、channel、connector。
- 权限要求：
  - 普通用户只能启用管理员允许的 channel/skill。
  - 管理员管理全局插件、MCP、外部连接器和密钥。
- 验收：多渠道状态一致，Cron 运行可审计，插件能力可禁用、可诊断。

## 状态更新流程
- 每阶段开始前：台账标记 `in_progress`，记录预计修改模块。
- 每阶段完成后：台账标记 `done`，记录实际修改文件、测试命令、测试结果。
- 阻塞时：标记 `blocked`，写明原因、可选方案、推荐方案。
- 进入下一阶段前：确认 `git status`，确保没有误改无关文件。

## 修改边界
- 不重写 Spring Boot / Vue / Electron 技术栈。
- 不删除现有 ReAct、Plan-Execute、MCP、ACP、Memory、Wiki、Tool Guard。
- 不回滚实施前已有未提交改动。
- 权限改造必须前后端同时做，不能只隐藏 UI。
- 普通用户简化界面是默认行为；管理员完整控制台保持可用。
