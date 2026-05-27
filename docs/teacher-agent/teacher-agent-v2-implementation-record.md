# Teacher Agent 教学出题闭环专项 v2 实施记录

> 总控台账：`docs/agent-harness-implementation.md` 是唯一指导文件。  
> 本文件只记录 Teacher Agent v2 的具体实施过程、阶段验收、修改文件和遗留问题。  
> 每完成一个阶段，必须同步更新本文件和总控台账中的 `Teacher Agent 教学出题闭环专项 v2` 状态。

## 0. 当前状态

| 字段 | 内容 |
| --- | --- |
| 专项状态 | `in_progress` |
| 总控台账 | `docs/agent-harness-implementation.md` |
| 当前实施文件 | `docs/teacher-agent/teacher-agent-v2-implementation-record.md` |
| 需求来源 | `docs/teacher-agent/iusses.md`、`docs/teacher-agent/名著题内测.docx`、`docs/teacher-agent/初中名著出题要求.docx`、`docs/teacher-agent/初中文言文出题要求.docx`、`docs/teacher-agent/初中现代文出题要求.docx` |
| 当前优先级 | v2-2 回归优化：模板/实例同步、Skill 隔离、规则实例化配置、自优化草案独立化、首用引导与连续会话衔接 |
| 当前原则 | 不新增独立总计划；不以 prompt-only 方式继续堆规则；规则、状态、展示、验收必须闭环 |

## 1. 执行同步规则

| 时机 | 必须动作 |
| --- | --- |
| 阶段开始前 | 在本文件将阶段状态改为 `in_progress`，在总控台账对应任务同步状态和预计修改模块。 |
| 阶段实施中 | 记录实际修改文件、关键决策、遇到的问题和临时取舍。 |
| 阶段完成后 | 在本文件写入测试命令、测试结果、验收结论、遗留问题；同步总控台账状态。 |
| 阻塞时 | 状态改为 `blocked`，写清阻塞原因、可选方案、推荐方案。 |
| 进入下一阶段前 | 检查 `git status --short`，确认没有误改无关文件。 |

## 2. 当前代码入口

| 模块 | 文件 | 当前作用 | v2 处理方向 |
| --- | --- | --- | --- |
| Teacher 内置模板 | `mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json` | 声明 Teacher Agent、默认 prompt、知识库 seed、工具、mock tasks、输出格式。 | 关联规则包，补齐身份回答、业务模块、规则配置入口，不再只靠 system prompt。 |
| Plan 工作流门控 | `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java` | 当前已有 Teacher + plan 两阶段短路逻辑。 | 抽出 Teacher intent/context/state，升级确认态 metadata。 |
| Harness 验收 | `mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java` | 当前用 heuristic 判断 Teacher 分块、来源、答案、采分点、质量审核。 | 接入 TeacherResultV2 和 RulePack，输出可解释失败原因。 |
| Word 导出接口 | `mateclaw-server/src/main/java/vip/mate/workspace/conversation/controller/ConversationController.java` | 提供 `/teacher-export`。 | 确保从结构化结果导出，仅试题版/完整版内容稳定。 |
| 前端结果展示 | `mateclaw-ui/src/components/chat/MessageBubble.vue` | 当前解析 Teacher 分块，支持复制、Markdown 下载和 Word 导出。 | 支持 `TeacherExamResultV2`，主展示试题，答案/采分点/来源折叠。 |
| 审批输入栏 | `mateclaw-ui/src/components/chat/ChatInput.vue` | 当前显示待审批条和审批范围。 | 升级为 Codex 风格审批卡，显示风险、工作区、命令、范围菜单。 |
| Chat 编排 | `mateclaw-ui/src/views/ChatConsole.vue` | Teacher guide、审批恢复、发送消息、运行状态。 | 注入 Agent 身份、规则包状态、知识库/材料状态，联动审批与资料读取替代路径。 |
| API 类型 | `mateclaw-ui/src/api/index.ts`、`mateclaw-ui/src/types/index.ts` | 当前有 Teacher export API 和基础类型。 | 补 Teacher result/rule pack 类型与接口。 |

## 2.1 RulePack / Skill / Self-Improve 职责边界

本专项采用受控自优化，而不是让 Teacher Agent 直接自改线上规则。Hermes-style learning loop 的可借鉴点是“从经验中生成可复用 Skill/规则草案”，但教育出题必须保留人工审核和发布边界。

| 层级 | 职责 | 落地原则 |
| --- | --- | --- |
| RulePack | 名著、文言文、现代文等模块的硬规则：题型比例、分值、材料要求、答案采分点、禁用项、验收项。 | 作为主配置源；Prompt 只引用 RulePack，不散落硬规则。 |
| Agent Prompt | 角色、交互方式、Plan 两阶段、规则选择说明。 | 只做运行说明，不承担规则唯一来源。 |
| Knowledge/Wiki | 教材、原文、样例、考纲、资料目录导入内容。 | 作为出题依据，不放系统级规则。 |
| Memory | 用户/工作区偏好，如年级、教材版本、常用题量、输出风格。 | 个性化偏好不能覆盖管理员发布 RulePack。 |
| Teacher Skill | 可执行流程：命题、审核、修改、导出、复盘。 | Skill 负责“怎么做”，RulePack 负责“按什么规则做”。 |
| Self-Improve Loop | 从 Harness 失败、用户修正、人工反馈中生成 RulePack/Skill/验收项优化草案。 | 只生成草案；必须由 admin / workspace owner 审核发布后才生效。 |

## 3. 阶段落地清单

### 阶段 0：实施基线与范围冻结

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 固化 Teacher v2 的实施记录，明确总控台账与专项记录的关系。 |
| 需求 | 总控台账是唯一指导；本文件是具体实施记录；v2 以规则包、状态机、结构化输出、前端分块、Harness 验收为主。 |
| 后端修改 | 无。 |
| 前端修改 | 无。 |
| 文档修改 | `docs/agent-harness-implementation.md`、`docs/teacher-agent/teacher-agent-v2-implementation-record.md`。 |
| 执行过程 | 新增专项实施记录；总控台账补充专项记录文件引用和同步规则。 |
| 验收测试 | 检查两个文档均能定位 Teacher v2；`git status --short` 仅包含预期文档变更。 |
| 测试结果 | `git diff --check` 通过；专项实施记录文件已创建；总控台账已添加实施记录引用和同步规则。 |
| 遗留问题 | `docs/teacher-agent/` 当前被 `.gitignore` 忽略，本文件作为本地实施记录默认不进入 git 跟踪。 |

### 阶段 1：Teacher 会话上下文与身份回答

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让 Teacher Agent 能按当前 Agent 身份、能力、知识库和业务模块回答普通问题，并避免错误进入出题流程。 |
| 需求 | 增加 Teacher turn context；识别当前 Agent/template/profile、业务模块、任务类型、运行模式、绑定知识库、会话附件、资料索引状态和 plan 状态。问“你是谁/你能做什么”时，必须基于当前 Agent 身份回答。 |
| 后端修改 | `StateGraphPlanExecuteAgent.java`；新增 `vip.mate.teacher.model.TeacherTurnContext`、`vip.mate.teacher.service.TeacherIntentService`、`vip.mate.teacher.service.TeacherContextService`；更新 `teacher-exam-assistant.json`。 |
| 前端修改 | `ChatConsole.vue`；可能涉及 `mateclaw-ui/src/types/index.ts`、`mateclaw-ui/src/api/index.ts`。 |
| 执行过程 | 新增 `TeacherTurnContext` 和 `TeacherIntentService`；`StateGraphPlanExecuteAgent` 在 Teacher + plan 模式下优先识别身份/使用说明问题并返回 direct answer，不再进入命题方案待确认；出题意图、确认意图、本地目录范围识别统一收口到 Teacher intent 服务。当前 Teacher guide 已有知识库绑定提示，本阶段未额外修改前端。 |
| 验收测试 | Teacher 下问“你是谁/你能做什么”返回 Teacher 专属回答；Coding 下问“你是谁”返回 Coding 专属回答；Teacher 普通解释/总结不进入命题方案。 |
| 测试结果 | 新增 Teacher model/service 通过独立 `javac -encoding UTF-8` 编译；smoke 测试验证 `isTeacherAgent`、`isIdentityOrUsageQuestion`、`isTeacherExamIntent` 和身份回答生成；用本地 JDK + `.m2` classpath 对 `StateGraphPlanExecuteAgent`、`TeacherTurnContext`、`TeacherIntentService` 执行 `javac` 联合编译通过。 |
| 遗留问题 | Coding Agent 的身份回答主要依赖其自身模板/system prompt；本阶段只硬化 Teacher Agent 的入口判断。阶段 2 继续处理 Teacher plan 状态 metadata。 |

### 阶段 2：Plan 状态硬化

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 解决“待确认但继续出题”和连续会话状态不稳定。 |
| 需求 | Teacher 出题类任务 + plan 模式必须进入 `draft_plan`、`awaiting_confirmation`、`plan_revision`、`generating_exam`、`completed` 状态；状态写入 message metadata 或 run metadata；文本 marker 仅保留兼容。 |
| 后端修改 | `StateGraphPlanExecuteAgent.java`、`ConversationService.java`、`HarnessRunService.java`。 |
| 前端修改 | `ChatConsole.vue`、`MessageBubble.vue`。 |
| 执行过程 | `teacher_workflow` SSE 事件现在写入持久化 message metadata；`StateGraphPlanExecuteAgent` 的待确认判断优先读取最近消息实体 metadata，文本 marker 只作为兼容 fallback；避免因 LLM 历史构建移除尾部 assistant 消息导致待确认状态丢失。 |
| 验收测试 | “生成 5 道《西游记》题”第一轮只给方案；“改成 8 道”只更新方案；“确认，开始出题”才生成正式试题；新任务不被旧状态污染。 |
| 测试结果 | `StateGraphPlanExecuteAgent`、`ChatController`、`TeacherTurnContext`、`TeacherIntentService` 用本地 JDK + `.m2` classpath 联合编译通过。 |
| 遗留问题 | 正式生成完成态仍主要依赖最终回答结构识别；阶段 3 将通过 `TeacherExamResultV2` 继续加固正式结果结构。 |

### 阶段 3：结构化输出协议 v2

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让正式出题结果稳定按试题、答案、采分点、来源、审核分开展示和导出。 |
| 需求 | 定义 `TeacherExamResultV2`；后端正式生成时要求结构化协议；前端优先解析结构化字段，Markdown 仅 fallback；命题说明/质量审核默认折叠或内部化。 |
| 后端修改 | 新增 `vip.mate.teacher.model.TeacherExamResultV2`、`vip.mate.teacher.service.TeacherResultNormalizer`；修改 `StateGraphPlanExecuteAgent.java`、`HarnessRunService.java`。 |
| 前端修改 | `MessageBubble.vue`、`mateclaw-ui/src/types/index.ts`。 |
| 执行过程 | 新增 `TeacherExamResultV2` 后端契约；正式生成 prompt 优先要求 `teacher_exam_result_v2` JSON，Markdown 标题分块作为 fallback；前端 `MessageBubble.vue` 增强结构化 JSON 解析，能把 `questions`、`answers`、`scoringRubric`、`sources`、`internalReview` 中的对象数组格式化成可读 Markdown 分块。 |
| 验收测试 | 出题后不再是一整段；试题默认展开；答案、采分点、来源、审核折叠；每块复制可用；导出按结构化字段拼装。 |
| 测试结果 | `StateGraphPlanExecuteAgent`、`ChatController`、`TeacherExamResultV2`、`TeacherTurnContext`、`TeacherIntentService` 用本地 JDK + `.m2` classpath 联合编译通过；`mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` 通过；`git diff --check` 通过。 |
| 遗留问题 | 结构化输出依赖模型遵循契约；阶段 4/9 将继续用规则包和 Harness gate 检查缺失字段、题型比例、答案采分点等质量问题。 |

### 阶段 4：名著规则包 v2

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 将名著内测 15 点问题转成可执行规则，而不是依赖 prompt 提醒。 |
| 需求 | 建立 `classic_reading` 规则包；覆盖教材版本、题型比例、分值、材料题、主观题答案采分点、微写作、填空/表格书写量、辩论题、批注题、超纲术语、上下文串扰、插图限制。 |
| 后端修改 | 新增 `vip.mate.teacher.model.TeacherRulePack`、`QuestionTypeRule`、`vip.mate.teacher.service.TeacherRulePackService`、`mateclaw-server/src/main/resources/teacher-rules/classic-reading-v2.json`；更新 `teacher-exam-assistant.json`、`StateGraphPlanExecuteAgent.java`、`HarnessRunService.java`。 |
| 前端修改 | 无。规则配置 UI 按计划顺延到阶段 6。 |
| 执行过程 | 用 JSON 规则包固化名著 15 点问题；新增运行时 RulePack 模型和服务；Teacher 模板声明默认规则包 `teacher.rulepack.classic_reading.v2`；正式出题 prompt 注入名著规则摘要；Harness summary 增加题型比例、材料题、开放题、规则包信号。 |
| 验收测试 | 默认题型比例符合要求；主观题必有答案采分点；材料题缺材料时先请求补充；微写作不作为主体题型大量出现；批注题有方向；避免超纲术语。 |
| 测试结果 | `classic-reading-v2.json` 和 `teacher-exam-assistant.json` 均通过 PowerShell `ConvertFrom-Json`；阶段 4 相关 Java 文件用本地 JDK + `.m2` classpath 联合编译通过；`mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` 通过。 |
| 遗留问题 | 规则包 UI 编辑能力按计划在阶段 6 实施；当前阶段先保证内置规则包能参与运行时 prompt 与 Harness 信号。 |

### 阶段 5：Teacher 上下文路由与目录扫描优化

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 避免连续会话每轮 `List Directory` 遍历本地文件，提高响应速度并减少工具试错。 |
| 需求 | Teacher 默认优先使用 Agent 绑定知识库、会话附件摘要、工作区资料索引缓存；只有用户主动刷新/导入/指定新目录时才重新扫描目录。 |
| 后端修改 | `StateGraphPlanExecuteAgent.java`、`ListDirectoryTool.java`、`DirectoryMaterialsIndexTool.java`、`HarnessRunService.java`。 |
| 前端修改 | 无。 |
| 执行过程 | 现有 `ContextRouterService`、`AgentGraphBuilder` 和目录工具已经具备工作区缓存/上下文注入基础。本阶段补强运行约束：`list_directory` 与 `index_directory_materials` 返回 `cacheHit`、`cacheTtlMinutes`、`cachePolicy.stopRepeatingTraversal`、`nextAction`；Teacher plan 和正式出题 prompt 明确优先复用 `<context-router>` 的 Cached material index，工具命中缓存后必须停止重扫同一大目录；资料不足时转为候选文件读取、知识库导入/绑定、审批或刷新提示；Harness summary 增加目录遍历次数、资料索引次数和缓存命中信号。 |
| 验收测试 | 同工作区连续 3 轮 Teacher 问题不重复目录遍历；用户说“刷新资料索引”才重新扫描；资料缺失时给明确替代路径；工具次数超限不再是常态失败。 |
| 测试结果 | 相关 Java 文件用本地 JDK + `.m2` classpath targeted compile passed；`git diff --check` passed。 |
| 遗留问题 | 本阶段通过 prompt/tool policy 和 Harness 信号减少重复扫描，还不是深度文件系统 watcher；后续如需更稳定的跨会话资料快照，可在 Phase 5 Project Cache 中增加持久化 `TeacherMaterialIndexCache`。 |

### 阶段 6：RulePack + Teacher Skill Foundation

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 支持管理员通过 UI 查看和调整 Teacher 规则，并为 Teacher Skill 执行流程、自优化草案生成打基础。 |
| 需求 | 管理员可调整模块启用、题型比例、默认分值、允许/禁用题型、材料题强制材料、答案采分点策略、超纲术语策略；普通用户只使用管理员发布配置。Teacher Skill 负责命题、审核、导出、复盘等可执行流程，Skill 可声明适用 RulePack、输入、输出和验收目标。 |
| 后端修改 | 新增 `TeacherRulePackController`、`TeacherSkillController`、`TeacherImprovementController`；扩展 `TeacherRulePackService` 列表/详情、全局覆盖、工作区覆盖和有效规则解析；新增 `TeacherSkillDefinition`、`TeacherSkillDefinitionService`、`TeacherImprovementDraft`、`TeacherImprovementDraftService`；`StateGraphPlanExecuteAgent` 在正式出题 prompt 中按当前工作区读取有效 RulePack 并注入激活 Skill。 |
| 前端修改 | `Agents.vue`、`api/index.ts`、`types/index.ts`。独立 `TeacherRulePackPanel.vue` 可按后续复杂度再拆。 |
| 执行过程 | 已先落只读基础：后端 `/api/v1/teacher/rule-packs` 暴露内置规则包列表和详情；`/api/v1/teacher/skills` 暴露内置 Teacher Skill 列表和详情；Teacher 模板 `capabilityPack.defaultTeacherSkillIds` 声明默认绑定“名著阅读命题闭环”和“命题质量审核”；前端模板选择页读取规则包和 Skill，并在 Teacher 模板卡片展示规则包与执行 Skill；点击“查看规则”可查看默认题型比例、全部题型规则、硬性规则和验收项；点击 Skill 可查看用途、执行流程、支持规则包和验收信号。本轮补充管理员 RulePack 覆盖：`PUT /api/v1/teacher/rule-packs/{id}?scope=workspace|global` 保存覆盖到 `mate_system_setting`，`DELETE` 清除覆盖；有效规则包优先级为“工作区覆盖 > 全局覆盖 > 内置规则包”；前端管理员可在规则详情中通过可视化表单编辑名称、版本、题型比例、题型分值/规则、答案/采分点/材料开关和硬性规则，也可继续编辑完整 JSON，普通用户只读。继续补充 Teacher Skill 绑定：`GET/PUT/DELETE /api/v1/teacher/skills/bindings` 查看、保存、恢复默认激活 Skill；绑定持久化到 `mate_system_setting`；正式出题 prompt 注入当前激活 Skill 流程。运行时 `StateGraphPlanExecuteAgent` 会捕获当前 `ChatOrigin.workspaceId` 并懒加载工作区 RulePack 覆盖，确保新会话按工作区配置生效。最后补齐受控自优化草案 v1：`/api/v1/teacher/improvements` 可基于 Harness run 生成 RulePack 草案；草案默认 `pending`，只写入草案列表，不影响生产；管理员可接受、拒绝，接受时可选择发布到工作区或全局 RulePack 覆盖。 |
| 验收测试 | 管理员能调整名著题型比例；普通用户无编辑入口；修改工作区规则后当前工作区新会话生效；全局覆盖作为无工作区覆盖时的 fallback；旧会话历史不被改写。Teacher Skill 能绑定 RulePack 并声明输入/输出；自优化草案可生成、接受、拒绝，未发布前不影响新会话。 |
| 测试结果 | `mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` passed；Teacher RulePack/Skill/Improvement/Plan Agent 相关 Java 目标类用 WebStorm JBR `javac` + `.m2` classpath 编译通过。 |
| 遗留问题 | Phase 6 foundation 已完成。后续在阶段 9/10 继续补 Skill patch、acceptance case 草案、更细 diff 视图和规则驱动 Harness scoring。 |

### 阶段 7：文言文与现代文规则包

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 补齐文言文、现代文规则，避免所有语文任务套名著规则。 |
| 需求 | 根据 `初中文言文出题要求.docx` 和 `初中现代文出题要求.docx` 建立独立 RulePack；Teacher intent 能识别名著、文言文、现代文和混合任务。 |
| 后端修改 | 新增 `teacher-rules/classical-chinese-v1.json`、`teacher-rules/modern-reading-v1.json`；更新 `TeacherIntentService`、`TeacherRulePackService`、`TeacherSkillDefinitionService`、`StateGraphPlanExecuteAgent`。 |
| 前端修改 | 无新增页面；复用 Agent Studio 已有 RulePack 展示和编辑入口，新增规则包通过 `/teacher/rule-packs` 自动进入列表。 |
| 执行过程 | 从 `初中文言文出题要求.docx` 和 `初中现代文出题要求.docx` 抽取规则，落成独立 RulePack。文言文规则包覆盖文言字词、句子翻译、断句停顿、内容理解、主旨情感、对比迁移，强调教材优先、课外 150-300 字、12 个常用虚词、禁止高中文言文和复杂语法。现代文规则包覆盖信息提取、内容概括、语言赏析、结构作用、主旨情感、拓展任务，强调先识别文体、文章来源真实、答案源于文本、不默认复用课内课文。`TeacherIntentService.detectBusinessModule` 改为 public 并识别文言文/现代文；`StateGraphPlanExecuteAgent` 在待确认方案中写入 `teacher_exam_module` 和 `teacher_rule_pack_id`，确认轮即使用户只说“确认”，也能从历史方案恢复对应规则包并注入正式出题 prompt。 |
| 验收测试 | 文言文题不套名著规则；现代文阅读题不套名著规则；混合任务能拆分或确认；每类题都有答案、采分点和来源要求。 |
| 测试结果 | `classical-chinese-v1.json`、`modern-reading-v1.json` 逐个 `ConvertFrom-Json` 通过；`mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` passed；Teacher RulePack/Intent/Skill/Plan Agent 相关 Java 目标类用 WebStorm JBR `javac` + `.m2` classpath 编译通过。 |
| 遗留问题 | 混合任务当前只做模块优先级选择，尚未实现多模块拆分确认 UI；后续可在 Phase 9 Harness scoring v2 中加入跨模块拆分验收。 |

### 阶段 8：审批 UX 升级

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 参考 Codex 审批卡，让用户清楚知道批准什么、批准多久、风险是什么。 |
| 需求 | 审批卡展示工具/命令、工作区路径、风险等级、触发原因、替代路径、Allow 下拉范围、Skip/拒绝、自动审批配置入口。 |
| 后端修改 | 复用 `vip.mate.approval.*`、`ToolExecutionGuardHelper.java`、`ToolPolicyResolver.java`；`ApprovalService` hydration 增加 `workspaceId`、`projectPath`、`approvalKey`；`ChatController` SSE pending approval 增加 `workspaceBasePath`、`projectPath`、`approvalKey`。 |
| 前端修改 | `ChatInput.vue`、`ChatConsole.vue`、`src/types/index.ts`。 |
| 执行过程 | 从简单审批条升级为审批卡；卡片展示工具/命令、风险等级、参数预览、工作区/项目路径、触发原因、风险检查项，并保留一次/会话/工作区审批范围和 Skip 拒绝。Teacher 资料读取受限时可通过审批 metadata 显示替代路径上下文，避免用户不知道批准对象。 |
| 验收测试 | 触发受限工具时出现审批卡；可选择一次/会话/工作区范围；拒绝后显示替代路径；Teacher 目录读取受限时不无限重试。 |
| 测试结果 | `mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` passed；`ApprovalService`、`PendingApproval`、`ChatController` 用 WebStorm JBR `javac` + `.m2` classpath targeted compile passed。 |
| 遗留问题 | 自动审批配置页入口尚未做成独立策略页，本阶段先保留审批范围下拉作为可用闭环；后续与 Workspace Policy/Tool Guard 管理页统一设计。 |

### 阶段 9：Harness 验收 v2

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 将 Teacher v2 从 heuristic 升级为规则驱动验收。 |
| 需求 | 验收普通问答、Plan 方案确认、未确认保护、结构化输出、答案采分点、材料来源、题型比例、分值、上下文污染、UI 分块、导出、目录扫描行为。 |
| 后端修改 | `HarnessRunService.java`；新增 `TeacherAcceptanceService.java`。 |
| 前端修改 | `ChatConsole.vue` mock task card 和 i18n。 |
| 执行过程 | 新增规则包驱动验收服务，从 Teacher 结果中识别有效 RulePack、业务模块、题型规则、分值标签、材料/来源要求、开放题答案方向、微写作附加约束、文言翻译采分约束、现代文来源真实性和目录扫描控制；`HarnessRunService` 合并规则包评分信号、分数调整和具体 gate blockers；Chat 样例任务详情新增 Teacher 规则包证据展示。 |
| 验收测试 | 缺少采分点、材料题缺材料、题型比例不符合规则时能指出具体失败原因；待确认方案不会被误判失败；正式结果缺结构化字段会失败。 |
| 测试结果 | `mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` passed；`TeacherAcceptanceService`、`TeacherRulePackService`、`HarnessRunService` 用 WebStorm JBR `javac` + `.m2` classpath targeted compile passed。 |
| 遗留问题 | 当前仍是规则/文本信号驱动评分，不调用模型做语义判分；混合模块拆分、导出内容差异和更细的题型比例数学校验留到后续专项。 |

### 阶段 10：Teacher Self-Improve 草案循环

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 借鉴 Hermes-style learning loop，把 Teacher 使用中的失败和人工修正沉淀成可审核的规则/Skill/验收优化草案。 |
| 需求 | 从 Harness 失败、用户修改方案、人工反馈、导出失败、资料缺失等信号中生成复盘摘要；自动归因到 RulePack、Teacher Skill、Knowledge/Wiki、Memory 或 UI/Export 问题；生成 RulePack patch、Teacher Skill patch 或 acceptance case 草案。 |
| 后端修改 | 复用并增强 `TeacherImprovementDraftService`、`TeacherImprovementDraft`、`TeacherImprovementController`；接入 `HarnessRunService` 输出的 `mockAcceptance.signals` 和 Teacher RulePack 服务。 |
| 前端修改 | `Agents.vue`、`types/index.ts`。 |
| 执行过程 | 草案默认不生效；只有 global admin 或 workspace owner/admin 审核发布后，才影响新会话。草案现在会自动归因到 `rule_pack`、`teacher_skill`、`knowledge_context` 或 `acceptance_case`，并同时生成 RulePack patch、Teacher Skill patch 草案和 acceptance case 草案；前端展示归因、建议类型、风险、主要原因、建议动作、规则补丁、Skill 步骤数和验收检查点。 |
| 验收测试 | 缺答案/采分点、材料题缺材料、题型比例错误等失败能生成具体优化建议；草案可查看差异和风险；未发布草案不影响新会话；发布后新会话使用新规则或 Skill。 |
| 测试结果 | `mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` passed；`TeacherImprovementDraftService`、`TeacherImprovementDraft`、`TeacherImprovementController` 用 WebStorm JBR `javac` + `.m2` classpath targeted compile passed。 |
| 遗留问题 | Skill patch 和 acceptance case 当前仍是可审核草案，不自动写入运行时 Skill 或模板 mock tasks；发布闭环先保持 RulePack 覆盖生效，避免自优化直接影响生产行为。 |

### 阶段 11：Word 导出回归加固

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 保证 Word 导出内容正确、版本稳定、与页面结构一致。 |
| 需求 | 仅试题版不含答案采分点；完整版包含试题、答案、采分点、来源、审核；无结构化试题时禁用导出或明确报错。 |
| 后端修改 | `ConversationController.java`。 |
| 前端修改 | `MessageBubble.vue`。 |
| 执行过程 | 前端导出只基于已解析出的结构化 Teacher 分块：仅试题版只允许 `questions` 区块，不再回退导出命题方案；完整版只包含试题、参考答案、采分点和来源依据，不包含命题说明/出题说明和命题质量审核。命题说明与质量审核从客户主展示中移出，放入“内部命题说明与质量审核”折叠区，需要时可展开查看但不参与 Word/Markdown 完整版导出。导出请求增加 `mode` 和 `sectionKeys`，后端 `/teacher-export` 做二次校验：仅试题版不能包含答案、采分点、来源或审核标题；完整版缺试题、答案、采分点或来源任一必要区块直接返回错误。DOCX 仍复用现有 `DocxExportService` 从 Markdown 分块生成，保证 Web/桌面下载链路一致。 |
| 验收测试 | 仅试题版 docx 不含答案；完整版 docx 包含试题、答案、采分点、来源，不包含命题说明和命题质量审核；Web 和桌面客户端均可下载；无结构化结果时提示错误。 |
| 测试结果 | `mateclaw-ui/node_modules/.bin/vue-tsc.cmd --noEmit` passed；`ConversationController`、`DocxExportService` 用 WebStorm JBR `javac` + `.m2` classpath targeted compile passed；2026-05-26 补充复测：调整命题说明/质量审核为内部折叠区且排除完整版导出后，`vue-tsc --noEmit` 和 ConversationController targeted `javac` 继续通过。 |
| 遗留问题 | 当前回归加固以导出 payload 和标题分块为校验边界，尚未加入自动打开 docx 检查段落内容；后续若做端到端测试，可增加 docx 解包 XML 内容断言。 |

## 4. 名著 15 点问题执行映射

| # | 问题 | 落地阶段 | 规则/验收 |
| --- | --- | --- | --- |
| 1 | 名著范围/教材版本错误 | 阶段 4 | RulePack 声明版本范围，超范围先确认或要求补充材料。 |
| 2 | 连续会话上下文串扰 | 阶段 1、2 | 每次出题生成独立 `TeacherTurnContext`，只继承用户明确保留约束。 |
| 3 | 主观题缺少参考答案 | 阶段 3、4、9 | 主观题 `answers` 必填，缺失即 gate fail。 |
| 4 | 材料题缺少原文/材料 | 阶段 4、5、9 | 材料题必须包含材料或来源引用；没有材料先请求补充。 |
| 5 | 题型比例失衡 | 阶段 4、6、9 | 默认比例：填空 10%、选择 20%、简答 15%、分析 30%、探究 25%；微写作只作附加。 |
| 6 | 分值不符合阅卷习惯 | 阶段 4、6、9 | 填空/表格每空 1-2 分，选择 2 分，简答 3-4 分，分析/探究 4-8 分。 |
| 7 | 选择题形式单一 | 阶段 4 | 支持排序、匹配、比较、辨析、情节理解等设问。 |
| 8 | 微写作偏作文训练 | 阶段 4 | 微写作只考名著理解和阅读体验，不扩大成作文训练。 |
| 9 | 填空/表格书写量过大 | 阶段 4 | 控制空格数和每空字数，必须有示例或明确作答格式。 |
| 10 | 辩论/开放题缺答案 | 阶段 4 | 必须给参考方向和采分点；教材有答案点时优先采用。 |
| 11 | 批注题方向不明确 | 阶段 4 | 必须指定批注角度。 |
| 12 | 超纲术语/学术化 | 阶段 4 | 禁止默认使用超纲术语，必须使用时需解释并降阶表达。 |
| 13 | UI 主结果混入命题说明 | 阶段 3 | 主结果优先展示试题，命题说明/质量审核折叠或内部化。 |
| 14 | 插图问题 | 阶段 4 | 本轮不默认生成插图；涉及插图要求用户上传或确认素材来源。 |
| 15 | 其他模块缺失 | 阶段 7 | 文言文、现代文按独立 RulePack 扩展。 |

## 5. 当前推荐执行顺序

| 轮次 | 阶段 | 目标 |
| --- | --- | --- |
| 第一轮 | 阶段 1-3 | 先解决身份上下文、Plan 状态硬化、结构化输出。 |
| 第二轮 | 阶段 4-5 | 落地名著规则包，优化资料上下文和目录扫描。 |
| 第三轮 | 阶段 6-8 | RulePack + Teacher Skill foundation、文言文/现代文规则包、审批 UX。 |
| 第四轮 | 阶段 9-11 | Harness v2、Teacher Self-Improve 草案循环和 Word 导出回归加固。 |

## 6. 阶段执行日志

| 日期 | 阶段 | 状态 | 修改文件 | 测试结果 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-05-25 | 阶段 0 | done | `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `git diff --check` 通过 | 创建专项实施记录，并建立与总控台账的同步关系。 |
| 2026-05-25 | 阶段 1 | done | `mateclaw-server/src/main/java/vip/mate/teacher/model/TeacherTurnContext.java`; `mateclaw-server/src/main/java/vip/mate/teacher/service/TeacherIntentService.java`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Teacher intent smoke test passed; modified Java files compile with local JDK + `.m2` classpath | Teacher 身份/使用说明问题现在走 direct answer，不触发命题方案待确认。 |
| 2026-05-25 | 阶段 2 | in_progress | 待记录 | 实施中 | 开始将 Teacher plan 状态从尾部 assistant 文本 marker 升级为持久化 metadata 优先。 |
| 2026-05-25 | 阶段 2 | done | `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `mateclaw-server/src/main/java/vip/mate/channel/web/ChatController.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Modified Java files compile with local JDK + `.m2` classpath | Teacher 待确认状态现在由持久化 metadata 优先驱动，文本 marker 仅兼容老消息。 |
| 2026-05-25 | 阶段 3 | in_progress | 待记录 | 实施中 | 开始定义 `TeacherExamResultV2` 并增强前端结构化解析。 |
| 2026-05-25 | 阶段 3 | done | `mateclaw-server/src/main/java/vip/mate/teacher/model/TeacherExamResultV2.java`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `mateclaw-ui/src/components/chat/MessageBubble.vue`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Java targeted compile passed; `vue-tsc --noEmit` passed; `git diff --check` passed | Teacher 正式出题结果新增 v2 结构化契约，前端可读分块优先走结构化 JSON。 |
| 2026-05-25 | 阶段 4 | in_progress | 待记录 | 实施中 | 开始名著规则包 v2 落地。 |
| 2026-05-25 | 阶段 4 | done | `mateclaw-server/src/main/resources/teacher-rules/classic-reading-v2.json`; `mateclaw-server/src/main/java/vip/mate/teacher/model/TeacherRulePack.java`; `mateclaw-server/src/main/java/vip/mate/teacher/model/QuestionTypeRule.java`; `mateclaw-server/src/main/java/vip/mate/teacher/service/TeacherRulePackService.java`; `mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | JSON validation passed; Java targeted compile passed; `vue-tsc --noEmit` passed | 名著规则包 v2 已固化为资源和运行时 prompt 规则，并暴露 Harness 评分信号。 |
| 2026-05-26 | 阶段 5 | done | `mateclaw-server/src/main/java/vip/mate/tool/filesystem/ListDirectoryTool.java`; `mateclaw-server/src/main/java/vip/mate/tool/filesystem/DirectoryMaterialsIndexTool.java`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `mateclaw-server/src/main/java/vip/mate/harness/service/HarnessRunService.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Java targeted compile passed; `git diff --check` passed | Teacher 资料目录链路增加缓存命中、停止重复遍历和替代路径提示信号。 |
| 2026-05-26 | 阶段 6 | done | `TeacherRulePackController`; `TeacherSkillController`; `TeacherImprovementController`; `TeacherRulePackService`; `TeacherSkillDefinitionService`; `TeacherImprovementDraftService`; `StateGraphPlanExecuteAgent`; `SecurityConfig`; `Agents.vue`; `api/index.ts`; `types/index.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed; Teacher RulePack/Skill/Improvement/Plan Agent 相关 Java 目标类用 WebStorm JBR `javac` + `.m2` classpath 编译通过 | 已完成工作区/全局 RulePack 覆盖、有效规则优先级、Teacher Skill 绑定、运行时注入和自优化草案审核发布 v1。 |
| 2026-05-26 | 阶段 7 | done | `mateclaw-server/src/main/resources/teacher-rules/classical-chinese-v1.json`; `mateclaw-server/src/main/resources/teacher-rules/modern-reading-v1.json`; `TeacherRulePackService`; `TeacherIntentService`; `TeacherSkillDefinitionService`; `StateGraphPlanExecuteAgent`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | 新增 JSON 逐个 `ConvertFrom-Json` passed; `vue-tsc --noEmit` passed; Teacher RulePack/Intent/Skill/Plan Agent 相关 Java targeted compile passed | 文言文/现代文已具备独立规则包和运行时规则选择，确认轮可从历史方案恢复模块。 |
| 2026-05-26 | 阶段 8 | done | `ChatInput.vue`; `ChatConsole.vue`; `types/index.ts`; `ApprovalService`; `ChatController`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed; ApprovalService / ChatController targeted `javac` passed | 审批条升级为审批卡，展示工具、参数、风险、项目路径、审批范围和替代路径上下文。 |
| 2026-05-26 | 阶段 9 | done | `TeacherAcceptanceService.java`; `HarnessRunService.java`; `ChatConsole.vue`; `zh-CN.ts`; `en-US.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed; TeacherAcceptanceService / TeacherRulePackService / HarnessRunService targeted `javac` passed | Harness scoring v2 接入 RulePack 规则信号和具体 gate blockers，Chat 样例任务详情展示 Teacher 规则包证据。 |
| 2026-05-26 | 阶段 10 | done | `TeacherImprovementDraft.java`; `TeacherImprovementDraftService.java`; `TeacherImprovementController.java`; `Agents.vue`; `types/index.ts`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed; TeacherImprovementDraftService / Draft / Controller targeted `javac` passed | 自优化草案新增归因、建议类型、风险、RulePack patch、Skill patch 草案和 acceptance case 草案；未审核发布前不影响新会话。 |
| 2026-05-26 | 阶段 11 | done | `ConversationController.java`; `MessageBubble.vue`; `api/index.ts`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md`; `docs/agent-harness-implementation.md` | `vue-tsc --noEmit` passed; ConversationController / DocxExportService targeted `javac` passed | Word 导出回归加固：仅试题版只导出试题区块，完整版要求试题、答案、采分点、来源四个客户交付区块；命题说明/质量审核进入内部折叠区，不进主展示和完整版导出。 |
| 2026-05-26 | 回归修复 | done | `ToolExecutionExecutor.java`; `MessageBubble.vue`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed; ToolExecutionExecutor / ToolPolicyResolver targeted `javac` passed | 修复 admin 发起工具审批时因 requesterId 为用户名而未解析账号角色，导致被误判为普通用户；Teacher 流式分块增加“正在生成哪一部分”提示，并防止审批/中间态用更少区块覆盖已生成的试题、答案、采分点缓存。 |
| 2026-05-26 | 回归修复 2 | done | `MessageBubble.vue`; `NodeStreamingChatHelper.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed; `git diff --check` passed；当前环境没有 `mvn`，`NodeStreamingChatHelper.java` 单文件 `javac` 受既有 `AssistantMessage.builder()` classpath 解析问题阻断，非本次修改行。 | 修复 Teacher 最终分块优先拿 plan stepResults 半成品的问题：现在按客户交付区块数量、内部区块数量和内容长度选择最完整候选；放宽“命题质量审核（内部）/复核建议”等标题解析，并让质量审核优先于命题说明匹配；流式 usage 只在 prompt/completion token 非 0 时打印 DEBUG，usage null 降到 TRACE，避免空 usage chunk 高频刷屏和 0 值覆盖有效统计。 |
| 2026-05-26 | 回归修复 3 | done | `ToolExecutionExecutor.java`; `ToolPolicyResolver.java`; `ChatController.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | ToolExecutionExecutor / ToolPolicyResolver / ChatController targeted `javac` passed；`git diff --check` passed；Teacher 手工回归待测试环境复测。 | 修复 Plan 工具执行路径传空 requesterId 导致 admin 再次被判为普通用户：执行器入口现在以 `ChatOrigin.requesterId` 兜底，并兼容 `ROLE_ADMIN` / `global_admin`。修复用户拒绝 `recall_structured` 后回答直接结束的问题：`/deny` 现在会触发一次受约束的替代回答，要求 Agent 不再调用被拒工具，基于已有上下文继续回答或说明替代路径。 |
| 2026-05-27 | 回归修复 4 | done | `StateGraphPlanExecuteAgent.java`; `MessageBubble.vue`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed；StateGraphPlanExecuteAgent targeted `javac` passed；Teacher 手工回归待测试环境复测。 | 修复文言文/现代文可能没有按名著同样分块展示、展开收起和导出的问题：后端明确所有 Teacher 模块共用 `teacher_exam_result_v2` 或固定 Markdown 分块协议；文言文/现代文原文材料进入 `questions` 区块；前端解析补充 `文言文试题`、`现代文试题`、`试题与材料` 标题别名。 |
| 2026-05-27 | v2.5-1/v2.5-3 | done | `TeacherRulePack.java`; `TeacherRulePackService.java`; `TeacherIntentService.java`; `TeacherSkillDefinitionService.java`; `TeacherAcceptanceService.java`; `StateGraphPlanExecuteAgent.java`; `teacher-exam-assistant.json`; `teacher-rules/ancient-poetry-v1.json`; `teacher-rules/basic-knowledge-v1.json`; `teacher-rules/writing-v1.json`; `Agents.vue`; `types/index.ts`; `ChatConsole.vue`; `RawMaterialPanel.vue`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；6 个 `teacher-rules/*.json` 与 `teacher-exam-assistant.json` `ConvertFrom-Json` passed；Teacher service targeted `javac -proc:none` passed；`git diff --check` passed。Plan Agent 单文件 javac 仍受项目 Lombok/annotation-processing classpath 基线影响，未发现本轮新增语法问题。 | Teacher v2.5 已落地：RulePack 增加 `stage`、`subject`、`sourceRequirements`；内置规则包扩展为初中语文 6 类：名著、文言文、现代文、古诗词、基础知识、写作；意图识别、Teacher Skill 绑定、Harness 规则包识别和前端规则包配置入口同步扩展；旧 `capability.education.junior_classics_exam` 保持兼容，新模板能力包改为 `capability.education.junior_chinese_exam`。资料与课标要求进入 RulePack sourceRequirements、知识库上传类型前缀和 Teacher plan/正式出题提示约束：要求最新教材/课标/稿件但未绑定知识库时，不得假称已按最新资料出题。 |
| 2026-05-27 | T2.5-4 生成产物预览下载 | done | `GeneratedFileController.java`; `MessageBubble.vue`; `ProjectChangesPanel.vue`; `useMarkdownRenderer.ts`; `types/index.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `Push-Location "d:\project\ai\mateclaw-dev\mateclaw-ui"; .\node_modules\.bin\vue-tsc.cmd --noEmit; Pop-Location` passed。 | 保留后端生成文件 inline 白名单端点用于受控预览；聊天区生成文件入口改为文件链接区，HTML 报表使用 `/inline` 在新页签打开，非 HTML 保持下载；会话正文剥离 generated markdown 链接，避免同页跳转和源码正文展示；右侧 Project 面板支持按路径映射下载生成产物，Web 端点击不跳会话，App 端保留本地打开/定位能力。 |
| 2026-05-27 | 回归修复 5 | done | `MessageBubble.vue`; `ProjectChangesPanel.vue`; `useMarkdownRenderer.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `get_errors` 复核显示 `useMarkdownRenderer.ts` 无新增问题；`MessageBubble.vue`/`ProjectChangesPanel.vue` 当前诊断仍为工程基线 alias/导出噪声。 | 修复“点击生成文件导致会话跳转”体验：聊天区对 generated 链接统一新页策略，HTML 报表直接新页预览；Project 面板在 Web 端提供下载按钮并阻断会话跳转，桌面端仍走本地打开/定位流程。 |
| 2026-05-27 | T2.5-5 组卷编排 | done | `TeacherIntentService.java`; `TeacherRulePackService.java`; `StateGraphPlanExecuteAgent.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | 代码路径复核通过：`paper_assembly` 意图识别、六类 RulePack 注入和组卷约束保持生效；本轮前端类型检查 `vue-tsc --noEmit` passed。 | 新增 `paper_assembly` 业务模块识别：“组卷/一套卷/综合卷/模拟卷”等请求进入组卷编排；正式生成 prompt 注入初中语文 6 类 RulePack，要求主 Agent 拆分模块、生成结构化题块并合并卷面、答案、采分点和来源。第一版不做复杂 subagent UI。 |
| 2026-05-27 | T2.5-6 HTML 报表模板与高级工具曝光 | done | `mateclaw-server/src/main/java/vip/mate/tool/document/HtmlExportService.java`; `mateclaw-server/src/main/resources/messages.properties`; `mateclaw-server/src/main/resources/messages_en.properties`; `mateclaw-server/src/main/resources/db/migration/h2/V107__register_html_render_tools.sql`; `mateclaw-server/src/main/resources/db/migration/mysql/V107__register_html_render_tools.sql`; `mateclaw-ui/src/views/Tools.vue`; `mateclaw-ui/src/i18n/locales/zh-CN.ts`; `mateclaw-ui/src/i18n/locales/en-US.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md`; `docs/teacher-agent/iusses.md` | `Push-Location "d:\project\ai\mateclaw-dev\mateclaw-ui"; .\node_modules\.bin\vue-tsc.cmd --noEmit; Pop-Location` passed；`git -C "d:\project\ai\mateclaw-dev" diff --check` passed（仅现有 CRLF 警告）。 | 维持系统 HTML 导出为默认稳定链路，同时升级默认模板视觉样式；3 个 HTML 导出工具补齐中文产品名、中文描述、图标和 `bindable=true`，便于在 Agent 配置中按需手动绑定；后台工具列表优先显示 `displayName`，避免继续暴露原始方法名；“纯模型直写 HTML”仅保留为用户显式要求时的高级路径。 |
| 2026-05-27 | v2-2 回归优化计划 | pending | `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md`; `docs/teacher-agent/iusses.md` | 已完成问题归类与计划同步，待实施。 | 针对 `iusses.md` 的 v2-2 问题新增下一阶段任务：模板更新与旧实例同步边界、Teacher Skill/RulePack 可见性隔离、RulePack 配置从模板页迁移到 Agent 实例配置优先、自优化草案独立化、默认列表隐藏已删除 Agent、首用槽位补全和“需要/确认”连续会话承接、Teacher 首页引导重写与回归验收。 |
