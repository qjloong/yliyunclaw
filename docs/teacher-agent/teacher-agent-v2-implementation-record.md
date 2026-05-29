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
| 当前优先级 | T2-3-11 canonical source derived views：从稳定切片元数据继续下沉到教材 / 题型衍生视图 |
| 当前原则 | 不新增独立总计划；不以 prompt-only 方式继续堆规则；规则、状态、展示、验收必须闭环；Teacher 规则最终优先级遵循“实例 > 模板 > 系统插件默认” |

## 0.1 2026-05-28 方案确认补充

### 已确认的目标结构

1. Teacher 规则插件调整为系统级公共插件，建议总名称为“教学出题规则插件”。
2. 当前页面命名从“Teacher 运维”收口为能力包配置页，例如“初中语文出题规则”。
3. 插件默认内置 6 类初中语文出题规则；后续小学/高中/其他学科通过新增能力包扩展，而不是复制新的独立 Teacher 运维入口。
4. 模板的意义是减少 Agent 实例初始化操作，因此模板默认规则统一从系统插件能力包引用，而不是维护独立规则事实源。
5. Agent 实例支持绑定/解绑/修改 Teacher 规则插件，并允许实例级 override；实例修改不回写模板，也不回写系统插件公共配置。
6. 多插件策略采用“允许多插件，但同业务域只允许一个主规则插件”。
7. 实际会话规则优先级固定为：`Agent实例规则 > 模板默认规则 > 系统插件默认规则 > 内置兜底`；与 workspace 无关。

### 第二阶段任务清单

| Task | Status | Goal | Planned files |
| --- | --- | --- | --- |
| T2-2-10a | done | 将插件卡片、规则配置页、能力包命名统一为系统级公共规则插件表达，并为后续学段/学科扩展预留能力包入口。 | `Plugins.vue`; `TeacherOps.vue`; `PluginAgentBindings.vue`; `Agents.vue` | 所有 kicker/标题/描述统一为“教学出题规则插件 / 初中语文出题规则”；`vue-tsc --noEmit` passed。 |
| T2-2-10b | done | 将“配置规则/Agent 绑定”改为二级页；规则配置页和绑定实例列表都可返回插件列表。 | `Plugins.vue`; `TeacherOps.vue`; `PluginAgentBindings.vue`; `router/index.ts` | 插件页“配置规则”跳转 `/plugins/education-exam-rules`（`TeacherOps.vue`）、“绑定 Agent”跳转 `/plugins/education-exam-rules/agents`（`PluginAgentBindings.vue`）；两个二级页均支持返回插件列表；绑定列表展示工作区/Agent/能力包/更新时间。 |
| T2-2-10c | done | 模板管理页支持插件绑定/解绑，模板默认规则统一引用系统插件能力包。 | `Agents.vue`; `teacher-exam-assistant.json` | 模板卡片新增“插件绑定”展示区域；`templatePluginBinding` 优先从 `pluginBindings` 解析；`templateRulePack` / `templateTeacherSkills` 优先使用插件绑定中的能力包信息。 |
| T2-2-10d | done | Agent 实例支持插件绑定、解绑、修改和实例级 override，同业务域仅允许一个主规则插件。 | `Agents.vue`; `api/index.ts`; `AgentBindingService` | Teacher 规则 Tab 新增插件绑定状态卡片：未绑定时显示绑定按钮，已绑定时显示解绑按钮；`bindEducationPlugin` / `unbindEducationPlugin` 强制同业务域仅保留一个主插件；`saveAgent` 通过 `agentBindingApi.setPlugins` 持久化；`openEditModal` 加载现有 plugin bindings。 |
| T2-2-10e | done | 运行时 Teacher RulePack / Skill 解析改为实例优先、模板回退、系统插件默认兜底，移除 workspace 优先级。 | `TeacherRulePackService`; `StateGraphPlanExecuteAgent`; `TeacherImprovementDraftService` | `effectiveRulePack(id, workspaceId)` 已改为直接委托 `effectiveRulePack(id)`，workspace 覆盖不再参与运行时优先级；`StateGraphPlanExecuteAgent` 中所有 `promptRules(..., currentWorkspaceId)` 改为无 workspace 版本；`TeacherImprovementDraftService` 同步去 workspace 化。 |

## 0.2 2026-05-28 Teacher 资料与知识库升级启动

### 已确认的第一实施切片

1. 先补“统一知识库的通用业务元数据底座”，不分叉 Teacher 专属知识库产品。
2. 知识库实体新增 `kbKind`、`domainProfileId`，用于区分通用 KB 与业务 KB，并为 Teacher 绑定 `education.exam.junior_chinese` 等业务画像。
3. 原始材料实体新增 `materialType`、`materialMetadataJson`，用于承载课程标准、最新教材、名著稿件、样题等业务语义，替代仅靠标题前缀传递语义。
4. 完整教材入库遵循“canonical source + 结构化切分 + route tags + derived views”，不单页粗粒度入库，也不复制六份镜像。

### 第一切片任务清单

| Task | Status | Goal | Planned files | Acceptance |
| --- | --- | --- | --- | --- |
| T2-3-1a | done | 落库 KB 类型与业务画像字段。 | `WikiKnowledgeBaseEntity`; `WikiKnowledgeBaseService`; `WikiController`; `db/migration/*/V109__wiki_business_metadata.sql` | 新建/更新 KB 可写入 `kbKind`、`domainProfileId`，列表与详情接口返回新字段。 |
| T2-3-1b | done | 落库原始材料类型字段。 | `WikiRawMaterialEntity`; `WikiRawMaterialService`; `WikiController`; `db/migration/*/V109__wiki_business_metadata.sql` | 文本录入、文件上传接口可写入 `materialType`，原始材料列表接口可返回该字段。 |
| T2-3-1c | done | Wiki UI 补齐基础录入入口。 | `mateclaw-ui/src/views/Wiki/index.vue`; `RawMaterialPanel.vue`; `useWikiStore.ts`; `api/index.ts` | 新建 KB 可填写类型/业务画像；上传或粘贴资料可随材料类型一起保存。 |
| T2-3-2 | done | relevant wiki context 从“只查首个 KB”升级为“聚合所有绑定 KB”，并把 KB 标签注入给 Agent。 | `WikiContextService` | 自动 relevant context 可跨多个绑定知识库命中；注入结果带 KB 名称 / externalKey / `domainProfileId`；`buildWikiContext()` 可显示 KB 范围标签。 |
| T2-3-3 | done | 引入受控业务画像注册表，业务知识库只能选择合法 `domainProfileId`。 | `WikiDomainProfileOption`; `WikiDomainProfileRegistryService`; `WikiController`; `mateclaw-ui/src/api/index.ts`; `mateclaw-ui/src/types/index.ts`; `mateclaw-ui/src/views/Wiki/index.vue` | 后端暴露业务画像选项接口；创建/更新业务 KB 会校验画像合法性；Wiki UI 通过下拉选择注册表画像而不是自由输入。 |
| T2-3-4 | done | 让 relevant context 与 Context Router 具备 `domainProfile` 感知排序，并把画像信息展示到聊天侧的路由摘要。 | `WikiContextService`; `ContextRouterService`; `ContextRouterSummary`; `WikiDomainProfileRegistryService`; `mateclaw-ui/src/types/index.ts`; `ProjectChangesPanel.vue` | 业务 KB 会因画像与查询匹配获得排序加权；聊天 Project 面板可看到 KB 类型与业务画像；相关上下文标签使用更易读的画像显示名。 |
| T2-3-5 | done | 让 `wiki_search_pages` / `wiki_semantic_search` 在多 KB 场景下同样具备画像感知排序，并把业务元数据返回给工具调用方。 | `WikiTool`; `WikiDomainProfileRegistryService` | 教育命题类查询的工具层搜索结果会优先返回“初中语文教学命题”业务 KB；工具结果包含 `kbKind`、`domainProfileId`、`domainProfileDisplayName` 与加权后分数。 |
| T2-3-6 | done | 把材料类型与材料元数据下沉到 `HybridRetriever`，让页面 / chunk 检索都能按教材、课标、名著稿件、样题、评分标准等信号重排。 | `HybridRetriever`; `WikiRawMaterialMapper`; `RawSearchRef` | 教育命题查询会因为原始材料 `materialType` 与 `materialMetadataJson` 匹配获得更高检索分；页面检索理由可显示材料类型命中；chunk 检索也继承同样的材料级加权。 |
| T2-3-7 | done | 让 Teacher 资料录入使用受控结构标签（年级 / 册别 / 单元 / 章节 / 版本 / route tags），并在检索阶段优先消费这些结构字段。 | `RawMaterialPanel.vue`; `WikiRawMaterialService`; `HybridRetriever` | 上传 / 粘贴资料可保存结构化元数据；后端会规范化 `materialMetadataJson`；检索重排与命中理由会显式利用章节、册别、单元和 route tags。 |
| T2-3-8 | done | 把结构化材料标签进一步传播到 page 级 `routeTagsJson`，让页面检索、关键字搜索与命中理由都能直接利用页面级 route tags。 | `WikiPageEntity`; `WikiPageService`; `WikiPageMapper`; `HybridRetriever`; `db/migration/*/V110__wiki_page_route_tags.sql` | 页面创建 / 更新时会缓存来源材料 route tags；关键字搜索可命中 page route tags；检索理由会显示匹配到的 route tags。 |
| T2-3-9 | done | 把教材 / 名著 / 题型的结构提示继续注入 route + create prompt，并把 `purposeHint` 真正落到 page 生命周期中，为 canonical source 切片提供稳定语义锚点。 | `WikiProcessingService`; `WikiPageService`; `prompts/wiki/*.txt` | route 结果会补充 / 继承 `purposeHint`；create / batch-create / repair prompt 会消费结构化材料提示；page 创建与 AI 更新会持久化 `purposeHint`。 |
| T2-3-10 | done | 为 page 增加稳定的结构切片元数据缓存（`structureMetadataJson` / `sliceType`），让 canonical source 的课文 / 单元 / 题型切片具备可检索、可解释的稳定实体语义。 | `WikiPageEntity`; `WikiPageService`; `WikiPageMapper`; `HybridRetriever`; `db/migration/*/V111__wiki_page_structure_metadata.sql` | 页面创建 / 更新会缓存结构元数据；关键字搜索与检索重排可直接使用 `sliceType`、`grade`、`volume`、`unit`、`chapter`、`classicName`；命中理由会显示 `Page structure: ...`。 |
| T2-3-11 | done | 基于稳定切片元数据构建 canonical source derived views，让教材 / 名著 / 课程标准 / 题型规则切片可作为可复用聚合视图输出到 API 与 Wiki UI。 | `WikiDerivedView`; `WikiPageService`; `WikiController`; `mateclaw-ui/src/api/index.ts`; `mateclaw-ui/src/stores/useWikiStore.ts`; `mateclaw-ui/src/views/Wiki/index.vue` | 后端新增 derived views 接口；可按教材同步视图、名著视图、课程标准视图、题型规则视图聚合页面；Wiki 页面列表优先消费 derived views 展示稳定切片聚合结果。 |

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

### 阶段 T2-3-1：Teacher 资料与知识库升级第一切片

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 为 Teacher 资料治理补齐统一知识库元数据底座，支持知识库类型、业务画像和原始材料类型持久化。 |
| 需求 | Teacher 不再只依赖标题前缀传递资料语义；后续教材结构化切分、跨 KB 路由、profile registry 都建立在统一字段之上。 |
| 后端修改 | `WikiKnowledgeBaseEntity.java`；`WikiRawMaterialEntity.java`；`WikiKnowledgeBaseService.java`；`WikiRawMaterialService.java`；`WikiController.java`；`db/migration/mysql/V109__wiki_business_metadata.sql`；`db/migration/h2/V109__wiki_business_metadata.sql`。 |
| 前端修改 | `mateclaw-ui/src/api/index.ts`；`mateclaw-ui/src/stores/useWikiStore.ts`；`mateclaw-ui/src/views/Wiki/index.vue`；`mateclaw-ui/src/views/Wiki/components/RawMaterialPanel.vue`。 |
| 执行过程 | 为 KB 增加 `kbKind` / `domainProfileId`；为原始材料增加 `materialType` / `materialMetadataJson`；上传与文本录入接口透传材料类型；创建 KB 弹窗补充业务字段；原始材料列表展示材料类型标签。 |
| 验收测试 | 新建 Teacher KB 时可保存业务画像；上传“课程标准/最新教材/名著稿件”等材料后刷新列表仍保留类型；接口兼容原有 `general` 默认值。 |
| 测试结果 | 待本轮统一执行前端类型检查与问题面板校验。 |
| 遗留问题 | 业务画像 registry、教材章节/单元结构化元数据和 route tags 仍在后续阶段实现。 |

### 阶段 T2-3-2：跨绑定 KB 自动相关上下文

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 修复 `WikiContextService.buildRelevantContext()` 仅搜索首个绑定 KB 的问题，让 Teacher 与其他业务 Agent 在自动注入时可同时利用多个绑定知识库。 |
| 需求 | 自动注入的 relevant context 应聚合所有绑定 KB 的检索结果，并显式标识知识库来源，避免教材 / 课标 / 名著稿件同时绑定时只有第一个 KB 生效。 |
| 后端修改 | `WikiContextService.java`。 |
| 前端修改 | 无。 |
| 执行过程 | 将 relevant context 搜索改为遍历全部绑定 KB，合并并按分数排序命中结果，再按 `kbId + slug` 去重；注入块增加 KB 标签；`buildWikiContext()` 标注 `kbKind` / `domainProfileId`。 |
| 验收测试 | 多个绑定 KB 同时存在时，自动注入内容能出现来自不同 KB 的结果；注入文案可区分来源知识库；`get_errors` 未报 Java 编译问题。 |
| 测试结果 | `WikiContextService.java` 问题面板校验通过，无新增错误。 |
| 遗留问题 | 当前跨 KB 合并仍使用通用分数排序，尚未引入 `domainProfile` 感知重排；后续可继续对 `ContextRouterService` 和检索排序做 profile-aware 优化。 |

### 阶段 T2-3-3：受控业务画像注册表

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 为业务知识库引入受控 `domainProfileId` 注册表，避免自由字符串继续扩散，并为后续插件贡献式画像注册打底。 |
| 需求 | 业务 KB 只能使用系统认可的业务画像；首个内置画像落到初中语文教学命题域；前端创建 KB 时改为下拉选择。 |
| 后端修改 | `WikiDomainProfileOption.java`；`WikiDomainProfileRegistryService.java`；`WikiController.java`。 |
| 前端修改 | `mateclaw-ui/src/types/index.ts`；`mateclaw-ui/src/api/index.ts`；`mateclaw-ui/src/views/Wiki/index.vue`。 |
| 执行过程 | 新增业务画像 DTO 与注册表服务，首个内置画像为 `education.exam.junior_chinese`；`/api/v1/wiki/domain-profiles` 返回受控画像列表；创建/更新 KB 时若提交未知 `domainProfileId` 则拒绝；Wiki 创建弹窗改为注册表下拉选择，并在业务 KB 场景自动默认首个合法画像。 |
| 验收测试 | 业务知识库创建弹窗只能选择注册表中画像；未选画像时无法创建业务 KB；后端对未知画像返回错误；前端类型检查通过。 |
| 测试结果 | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` 通过；新增 Java 文件与 `WikiController.java` 问题面板校验无新增错误。 |
| 遗留问题 | 当前注册表仍为后端内置常量，后续可升级为插件/能力包声明式贡献，并继续把 `ContextRouterService` / 检索排序升级为 profile-aware。 |

### 阶段 T2-3-4：`domainProfile` 感知的检索与路由排序

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让业务知识库不只是“被绑定”，还会在相关上下文和路由摘要中因为画像与查询匹配而获得更高优先级。 |
| 需求 | `WikiContextService` 聚合多 KB 命中后，需对业务画像与查询意图做轻量加权；`ContextRouterService` 需把 KB 类型 / 业务画像暴露给前端，并在 Wiki route hints 中优先展示更匹配的业务 KB。 |
| 后端修改 | `WikiDomainProfileRegistryService.java`；`WikiContextService.java`；`ContextRouterService.java`；`ContextRouterSummary.java`。 |
| 前端修改 | `mateclaw-ui/src/types/index.ts`；`mateclaw-ui/src/components/chat/ProjectChangesPanel.vue`。 |
| 执行过程 | 为业务画像注册表增加 `displayNameOrDefault()` / `matchScore()`；relevant context 合并时把 KB 名称、描述、externalKey 与 `domainProfile` 匹配分数纳入轻量加权；Context Router 的知识库摘要新增 `kbKind`、`domainProfileId`、`domainProfileDisplayName`，Wiki route hints 会优先展示与当前问题更匹配的业务 KB；聊天 Project 面板同步展示这些元数据。 |
| 验收测试 | 当 Agent 同时绑定通用 KB 与“初中语文教学命题”业务 KB 时，教育命题类问题的 relevant context / Wiki route hints 会优先展示业务 KB；Project 面板可见业务画像信息；前端类型检查通过。 |
| 测试结果 | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` 通过；`WikiContextService.java`、`ContextRouterService.java`、`ContextRouterSummary.java`、`WikiDomainProfileRegistryService.java` 问题面板校验无新增错误。 |
| 遗留问题 | 目前仍是基于画像元数据的轻量启发式匹配，后续可继续下沉到 `wiki_search_pages` / `HybridRetriever` / 原始材料结构标签，实现章节、教材版本、资料类型联合重排。 |

### 阶段 T2-3-5：工具层 `wiki_search_pages` / `wiki_semantic_search` 画像感知排序

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让 Agent 主动调用 Wiki 工具时，也能获得与 relevant context / Context Router 一致的业务画像优先级，而不是回退为纯跨 KB 原始分数排序。 |
| 需求 | `wiki_search_pages`、`wiki_semantic_search` 在聚合多个绑定 KB 的结果时，应对业务画像匹配做轻量加权；同时返回 KB 类型和业务画像元数据，便于 Agent 与调试面板理解来源边界。 |
| 后端修改 | `WikiTool.java`；`WikiDomainProfileRegistryService.java`。 |
| 前端修改 | 无。 |
| 执行过程 | `WikiTool` 注入业务画像注册表服务；绑定 KB 解析结果扩展为 `kbKind` / `domainProfileId` / `domainProfileDisplayName`；`wiki_search_pages` 与 `wiki_semantic_search` 在跨 KB 合并排序时叠加画像匹配分；工具结果新增业务元数据，并同时返回加权后 `score` 与原始 `retrievalScore`。 |
| 验收测试 | 多 KB 绑定时，教育命题类查询的 `wiki_search_pages` / `wiki_semantic_search` 结果优先来自“初中语文教学命题”业务 KB；工具返回中可直接看到业务元数据；`WikiTool.java` 问题面板无新增错误。 |
| 测试结果 | `WikiTool.java`、`WikiDomainProfileRegistryService.java` 问题面板校验通过，无新增错误。 |
| 遗留问题 | 当前工具层仍只使用 KB / 画像元数据加权，尚未把 `materialType`、教材章节结构、route tags 以及来源材料粒度信号纳入排序；这将作为下一阶段继续下沉。 |

### 阶段 T2-3-6：材料类型 / 元数据驱动的检索重排

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 把 `materialType` 与 `materialMetadataJson` 真正用于检索排序，而不只停留在录入与展示层。 |
| 需求 | `HybridRetriever.search()` 与 `searchChunks()` 都应利用来源原始材料的 `materialType`、标题与元数据，对教材、课标、名著稿件、样题、答案评分标准等查询做细粒度加权。 |
| 后端修改 | `HybridRetriever.java`；`WikiRawMaterialMapper.java`；新增 `RawSearchRef.java`。 |
| 前端修改 | 无。 |
| 执行过程 | 为原始材料新增轻量投影 `RawSearchRef`；Mapper 支持批量读取原始材料标题、类型与元数据；`HybridRetriever` 在页面检索聚合后，对候选 page 的来源原始材料做类型匹配和元数据匹配加权，并在 `reason` 中补充材料命中说明；chunk 检索直接按 `rawId` 读取材料元数据并加权，使 `wiki_semantic_search` 与编译 / 研究等基于 chunk 的路径同步受益。 |
| 验收测试 | 涉及教材、课标、名著、样题、评分标准的查询会优先命中对应材料类型来源的 page / chunk；`HybridRetriever.java`、`WikiRawMaterialMapper.java`、`RawSearchRef.java` 问题面板无新增错误。 |
| 测试结果 | 上述新增 / 修改 Java 文件问题面板校验通过，无新增错误。 |
| 遗留问题 | 当前材料元数据仍以自由 JSON 文本匹配为主，尚未形成受控的章节 / 册别 / 单元 / 版本字段；下一阶段继续推进教材结构标签与 route tags。 |

### 阶段 T2-3-7：教材章节 / 册别结构元数据

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让 Teacher 资料录入从“只选材料类型”升级为“材料类型 + 结构化教材标签”，并让检索重排优先消费这些结构字段。 |
| 需求 | 上传 / 文本录入需要可维护的 `edition`、`grade`、`volume`、`unit`、`chapter`、`classicName`、`routeTags`；后端需清洗元数据 JSON；`HybridRetriever` 不再只把 `materialMetadataJson` 当自由文本，而是读取结构字段做联合加权。 |
| 后端修改 | `WikiRawMaterialService.java`；`HybridRetriever.java`。 |
| 前端修改 | `mateclaw-ui/src/views/Wiki/components/RawMaterialPanel.vue`。 |
| 执行过程 | `RawMaterialPanel` 新增 Teacher 结构化资料标签区，按材料类型显示教材版本、适用年级、册别、单元、章节、名著名称和 route tags 等字段；上传和粘贴文本会一并提交 `materialMetadataJson`。`WikiRawMaterialService` 新增元数据规范化逻辑，统一清洗空值、route tags 和结构字段。`HybridRetriever` 增加结构化元数据解析，对 `grade`、`volume`、`unit`、`chapter`、`classicName` 与 `routeTags` 做显式匹配加权，并把命中理由细化为“材料类型 + 结构标签”。 |
| 验收测试 | Teacher 在上传教材、课标、样题或评分标准时可填写结构化标签；原始材料列表可展示关键信息摘要；检索对章节 / 册别 / 单元 / 名著名称等查询具有更强命中倾向。 |
| 测试结果 | 待本轮统一执行前端类型检查与问题面板校验。 |
| 遗留问题 | 当前结构化标签仍存于 `materialMetadataJson`，后续若要继续下沉到 page 级 route tags / 章节切片，可再评估是否增设专用索引字段。 |

### 阶段 T2-3-8：页面级 route tags 传播

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让结构化材料标签不只停留在 raw material，而是继续沉淀到 page 级缓存字段，供关键字搜索、混合检索和路由解释直接使用。 |
| 需求 | 页面创建 / AI 更新 / 手动更新 / 来源合并后，都要依据 `sourceRawIds` 与材料元数据重新生成 `routeTagsJson`；检索需把 `routeTagsJson` 纳入快路径和命中理由。 |
| 后端修改 | `WikiPageEntity.java`；`WikiPageService.java`；`WikiPageMapper.java`；`HybridRetriever.java`；`db/migration/mysql/V110__wiki_page_route_tags.sql`；`db/migration/h2/V110__wiki_page_route_tags.sql`。 |
| 前端修改 | `mateclaw-ui/src/stores/useWikiStore.ts`。 |
| 执行过程 | 为 `mate_wiki_page` 新增 `route_tags_json` 字段；`WikiPageService` 在页面创建、AI 更新、手动编辑和来源合并时，根据页面标题、`pageType` 和来源 raw 的结构化元数据重建 route tags；`WikiPageMapper` 的关键字快搜将 `route_tags_json` 纳入匹配；`HybridRetriever` 新增 page route tag boost 与 route tag reason，结果理由会显示命中的 `grade` / `volume` / `unit` / `chapter` / `classicName` 等标签。 |
| 验收测试 | 页面在生成后能直接携带 route tags；按教材册别、单元、章节、名著名称等查询时，即使标题未完全覆盖，也能通过页面级 route tags 命中；结果理由会显示 `Route tags: ...`。 |
| 测试结果 | Java 问题面板对 `WikiPageService.java`、`HybridRetriever.java`、`WikiPageMapper.java`、`WikiPageEntity.java` 校验通过；前端 `useWikiStore.ts` 无新增错误。 |
| 遗留问题 | 目前 page route tags 仍由页面服务按 raw 元数据即时生成，尚未把 canonical source 的课文 / 单元 / 文体切片结构单独建模；下一阶段继续推进教材结构化切分。 |

### 阶段 T2-3-9：canonical source 结构提示下沉

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 让教材 / 课标 / 名著 / 题型材料的结构提示不只停留在 route tags，而是进入 route / create prompt 和 page `purposeHint` 生命周期，为后续 canonical source 切片提供稳定语义锚点。 |
| 需求 | route 阶段如果未给 `purposeHint`，系统需根据材料类型和标题自动补齐；create / batch-create / repair prompt 需消费结构化材料提示和 `purposeHint`；page 创建 / AI 更新需真正持久化 `purposeHint`。 |
| 后端修改 | `WikiProcessingService.java`；`WikiPageService.java`；`route-system.txt`；`route-user.txt`；`create-page-user.txt`；`batch-create-user.txt`。 |
| 前端修改 | 无。 |
| 执行过程 | `WikiProcessingService` 新增结构化材料提示拼装与 `purposeHint` 推导逻辑：会把教材版本、年级、册别、单元、章节、名著名称、题型来源和 route tags 注入 route / create prompt，并为教材单元页、课文章节页、名著人物 / 情节页、题型规则页、评分标准页自动生成 `purposeHint`。`WikiPageService` 新增带 `purposeHint` 的 create / AI update 重载，确保这些提示真正写入 page。 |
| 验收测试 | route 输出中的 create metadata 可包含 `purposeHint`；repair / retry / batch-create 不会丢失该提示；page 的 `purposeHint` 能在后续处理和人工排查中作为稳定切片语义锚点。 |
| 测试结果 | `WikiProcessingService.java`、`WikiPageService.java` 问题面板校验通过，无新增错误。 |
| 遗留问题 | 当前 canonical source 仍主要依赖 prompt 约束和 `purposeHint` 语义锚点，尚未把教材课文 / 单元 / 文体切片显式建模成专用实体；下一阶段继续推进稳定切片实体与 derived views。 |

### 阶段 T2-3-10：稳定切片元数据实体

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 把 canonical source 的切片边界从 `purposeHint` 进一步沉淀为页面级稳定结构元数据，让课文 / 单元 / 名著人物 / 情节 / 题型规则 / 评分标准具备统一的稳定检索语义。 |
| 需求 | `mate_wiki_page` 需要新增 `structure_metadata_json`；页面创建 / 更新 / 来源合并时需基于 raw material 元数据、`purposeHint`、标题推导 `sliceType` 与结构字段；关键字搜索与 `HybridRetriever` 要直接消费这些字段。 |
| 后端修改 | `WikiPageEntity.java`；`WikiPageService.java`；`WikiPageMapper.java`；`HybridRetriever.java`；`db/migration/mysql/V111__wiki_page_structure_metadata.sql`；`db/migration/h2/V111__wiki_page_structure_metadata.sql`。 |
| 前端修改 | `mateclaw-ui/src/stores/useWikiStore.ts`。 |
| 执行过程 | 为 page 增加 `structureMetadataJson`，由 `WikiPageService` 在 create / AI update / manual update / source lineage merge 时重建；结构字段包含 `sliceType`、`grade`、`volume`、`unit`、`chapter`、`classicName`、`source`、`materialTypes`、`routeTags` 等。`sliceType` 会按 `purposeHint`、标题和材料类型归一为 `unit`、`lesson_or_chapter`、`character`、`theme_or_plot`、`curriculum_requirement`、`question_rule`、`sample_question`、`answer_rubric` 等稳定语义。`WikiPageMapper` 与 `HybridRetriever` 继续下沉消费这些字段，并在结果理由里补充 `Page structure: ...`。 |
| 验收测试 | 按册别 / 单元 / 章节 / 名著人物 / 情节 / 题型规则 / 评分标准检索时，可通过页面稳定切片元数据命中；结果解释可显示结构原因；页面结构字段在后续 derived views 中可复用。 |
| 测试结果 | `WikiPageService.java`、`HybridRetriever.java`、`WikiPageMapper.java`、`WikiPageEntity.java`、`useWikiStore.ts` 问题面板校验通过，无新增错误。 |
| 遗留问题 | 目前稳定切片实体仍以 page 上的 JSON 缓存形式承载，尚未单独拆出 canonical source / derived view 模型；下一阶段继续推进教材同步视图、题型视图等衍生视图。 |

### 阶段 T2-3-11：canonical source derived views

| 字段 | 内容 |
| --- | --- |
| 状态 | `done` |
| 目标 | 基于 `structureMetadataJson` 把 canonical source 的稳定切片继续聚合成可复用 derived views，而不是只停留在 page JSON 缓存层。 |
| 需求 | 教材同步、名著、课程标准、题型规则等切片需要有统一聚合出口，便于 UI 和后续 Teacher 检索 / 编排直接消费教材 / 题型视图。 |
| 后端修改 | `WikiDerivedView.java`；`WikiPageService.java`；`WikiController.java`。 |
| 前端修改 | `mateclaw-ui/src/api/index.ts`；`mateclaw-ui/src/stores/useWikiStore.ts`；`mateclaw-ui/src/views/Wiki/index.vue`。 |
| 执行过程 | 新增 `GET /api/v1/wiki/knowledge-bases/{kbId}/derived-views`，从页面稳定切片元数据聚合出 `textbook_sync`、`classic_focus`、`curriculum_view`、`assessment_view`、`slice_view` 等 derived views；每个视图聚合其页面集合、材料类型和 route tags。Wiki store 在拉取页面列表时同步拉取 derived views，Wiki 左侧页面列表在无搜索场景下优先按 derived view 展示，并显示册别 / 单元 / 章节等副标题。 |
| 验收测试 | 选中含教材 / 名著 / 题型页面的 KB 时，页面侧栏可按教材同步视图、名著视图、课程标准视图、题型规则视图等聚合页面；按原始材料过滤时 derived views 也同步收窄。 |
| 测试结果 | 待本轮统一执行问题面板、前端类型检查与 diff hygiene 校验。 |
| 遗留问题 | 当前 derived views 仍是运行时聚合 DTO，尚未落成独立持久化实体；后续可继续给 Teacher 检索、组卷和教材同步流程直接消费这些视图。 |

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
| 2026-05-27 | v2-2 回归优化计划 | done | `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md`; `docs/teacher-agent/iusses.md` | 已完成问题归类与计划同步，待实施。 | 针对 `iusses.md` 的 v2-2 问题新增下一阶段任务：模板更新与旧实例同步边界、Teacher Skill/RulePack 可见性隔离、RulePack 配置从模板页迁移到 Agent 实例配置优先、自优化草案独立化、默认列表隐藏已删除 Agent、首用槽位补全和“需要/确认”连续会话承接、Teacher 首页引导重写与回归验收。 |
| 2026-05-29 | T2-2-7 v2-2 回归验收 | done | `Agents.vue`; `TeacherIntentService.java`; `StateGraphPlanExecuteAgent.java`; `TeacherAcceptanceService.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；TeacherAcceptanceService / TeacherIntentService / StateGraphPlanExecuteAgent targeted `javac` passed；`git diff --check` passed（仅现有 CRLF warning）。 | 完成五项回归验收：①旧实例同步：`syncTeacherHomeFromTemplate` 仅同步 `homeSubtitle`/`homeQuickStarts`，不覆盖 Prompt/知识库/RulePack/Skill；②Skill 隔离：`templateTeacherSkills` 对非 Teacher 模板返回空，`isTeacherTemplate` 按 `templateId`/`profileId`/`capabilityPackId`/`pluginKey` 多维度识别；③连续会话承接：`isTeacherPlanConfirmation` 覆盖“需要/好的/按这个/继续”等短回复，等待确认态直接走 `buildConfirmedTeacherExamPrompt`，历史 `teacher_exam_module` 恢复模块避免重置；④删除列表隔离：`showDeletedSection` 仅在 `activeFilter === 'deleted'` 时渲染；⑤无 KB 资料边界：`buildIdentityAnswer` 明确告知无绑定知识库时不会凭空编造，`buildConfirmedTeacherExamPrompt` 要求最新教材/课标/稿件缺失时必须在试题和来源依据中标注替代路径。 |
| 2026-05-27 | T2-2-2 / T2-2-5 | done | `mateclaw-ui/src/views/Agents.vue`; `mateclaw-server/src/main/java/vip/mate/teacher/service/TeacherIntentService.java`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；TeacherIntentService / TeacherTurnContext targeted `javac` passed；`git diff --check` passed（仅 CRLF warning）。 | 修复非 Teacher 模板显示 Teacher Skill 的问题：非 Teacher 模板 `templateTeacherSkills` 直接返回空，模板卡片外层也加 `isTeacherTemplate`；Teacher 模板识别补充新的初中语文能力包。增强连续会话承接：等待确认态下“需要/好的/按这个/继续”等短回复视为确认；方案修订时从历史 `teacher_exam_module` 恢复业务模块，避免用户补充“七年级上册”后重置为 unknown。 |
| 2026-05-27 | T2-2-4 / T2-2-6 | done | `mateclaw-ui/src/views/Agents.vue`; `mateclaw-server/src/main/resources/templates/teacher-exam-assistant.json`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；`teacher-exam-assistant.json` `ConvertFrom-Json` passed；`git diff --check` passed（仅 CRLF warning）。 | Agent 默认列表不再展示已删除智能体；已删除项只在“已删除”筛选下显示。Teacher 模板首页副标题改为“初中语文命题助手”，4 个快捷入口改为快速开始槽位补全、按年级册别生成方案、基于材料/知识库出题、组一套综合卷；旧实例同步新首页由 T2-2-1 继续承接。 |
| 2026-05-27 | T2-2-1 / T2-2-3 + T2.5-4 回归 | done | `mateclaw-ui/src/views/Agents.vue`; `mateclaw-ui/src/components/chat/ProjectChangesPanel.vue`; `mateclaw-ui/src/types/index.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；`teacher-exam-assistant.json` `ConvertFrom-Json` passed；`git diff --check` passed（仅 CRLF warning）。 | 已创建 Teacher 实例可在编辑页“首页”Tab 一键同步模板默认首页副标题和 4 个快捷入口，用户保存后生效且不覆盖 Prompt/知识库/RulePack/Skill；Teacher 自优化草案从模板卡片内迁移到页面顶部管理员运营面板，普通模板选择流程不再暴露草案。浏览器环境下 Project 面板增强生成文件路径映射：新增/变更文件可按 `app/output`、`output`、文件名映射到生成文件，HTML 提供 `/inline` 预览，下载走原始文件接口，点击不跳转会话。 |
| 2026-05-27 | T2-2-8 Teacher 运维入口与实例规则配置修正 | done | `mateclaw-ui/src/views/TeacherOps.vue`; `mateclaw-ui/src/views/Agents.vue`; `mateclaw-ui/src/router/index.ts`; `mateclaw-ui/src/views/layout/MainLayout.vue`; `mateclaw-ui/src/i18n/locales/zh-CN.ts`; `mateclaw-ui/src/i18n/locales/en-US.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；`git diff --check` passed（仅 CRLF warning）。 | 自优化草案定位为 Teacher 质量改进/运营诊断模块，已从智能体列表页移除并归入独立 `/teacher-ops` 管理员页面。Teacher 运维页集中展示 6 类初中语文 RulePack、Teacher Skill 绑定和自优化草案审核发布；Teacher 实例编辑页新增 `Teacher 规则` Tab，显示 6 类有效 RulePack，管理员可进入规则详情调整工作区/全局覆盖，普通用户只读；非 Teacher Agent 不显示该 Tab。 |
| 2026-05-27 | T2-2-9 Teacher 内置插件化入口修正 | done | `mateclaw-ui/src/views/Plugins.vue`; `mateclaw-ui/src/views/layout/MainLayout.vue`; `mateclaw-ui/src/views/Agents.vue`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `pnpm --dir mateclaw-ui exec vue-tsc --noEmit` passed；`git diff --check` passed（仅 CRLF warning）。 | 撤销左侧工作空间通用导航中的 Teacher 运维入口，避免没有 Teacher Agent 或教育业务的工作空间出现业务菜单。插件页新增“系统内置插件”分组和“Teacher 教学命题插件”卡片，集中展示 RulePack、Teacher Skill、自优化草案、Harness 验收能力，并提供“配置插件”“绑定 Agent”入口；`/teacher-ops` 保留为管理员隐藏配置路由。Teacher Agent 实例规则 Tab 保留，并明确规则来自系统内置 Teacher 教学命题插件。 |
| 2026-05-28 | T2-2-10 Teacher 插件能力包与学段学科扩展模型 | done | `mateclaw-server/src/main/java/vip/mate/agent/binding/model/AgentPluginBinding.java`; `mateclaw-server/src/main/java/vip/mate/agent/binding/repository/AgentPluginBindingMapper.java`; `mateclaw-server/src/main/java/vip/mate/agent/binding/service/AgentBindingService.java`; `mateclaw-server/src/main/java/vip/mate/agent/binding/controller/AgentBindingController.java`; `mateclaw-server/src/main/java/vip/mate/agent/service/TemplateService.java`; `mateclaw-server/src/main/java/vip/mate/agent/AgentGraphBuilder.java`; `mateclaw-server/src/main/java/vip/mate/teacher/service/TeacherIntentService.java`; `mateclaw-server/src/main/resources/db/schema.sql`; `mateclaw-server/src/main/resources/db/schema-mysql.sql`; `mateclaw-server/src/main/resources/db/migration/h2/V108__agent_plugin_binding.sql`; `mateclaw-server/src/main/resources/db/migration/mysql/V108__agent_plugin_binding.sql`; `mateclaw-ui/src/api/index.ts`; `mateclaw-ui/src/views/Agents.vue`; `mateclaw-ui/src/views/ChatConsole.vue`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | 已完成：目标文件 `get_errors` 无新增 Java 报错；`vue-tsc --project mateclaw-ui/tsconfig.json --noEmit` passed；`git diff --check` passed（仅现有 CRLF warning）。当前环境未提供 `mvn` 命令，服务端整包编译留待本地/CI Maven 环境补跑。 | Teacher 插件模型从“模板元数据闭环”推进到“实例级真实绑定闭环”：新增 `mate_agent_plugin` 表和 H2/MySQL `V108` 迁移；模板应用时将 `pluginBindings` 写入 Agent 绑定；旧 Teacher 实例自动回填 `builtin.teacher_exam` + 能力包默认绑定；运行时 Teacher 判断优先按 `pluginKey + capabilityPackId` 识别，前端 Teacher UI 识别同步优先读取插件绑定元数据，仍保留旧字段兼容。 |
| 2026-05-28 | T2-2-10a~e 第二阶段推进 | done | `TeacherOps.vue`; `PluginAgentBindings.vue`; `Plugins.vue`; `Agents.vue`; `TeacherRulePackService.java`; `StateGraphPlanExecuteAgent.java`; `TeacherImprovementDraftService.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `vue-tsc --noEmit` passed；Java lint 无新增报错；`git diff --check` passed（仅现有 CRLF warning）。 | T2-2-10a: 统一插件页/二级页/Agent Tab 文案为“教学出题规则插件 / 初中语文出题规则”。T2-2-10b: 确认二级页面与返回链路已完备。T2-2-10c: 模板卡片增加插件绑定展示，`templateRulePack`/`templateTeacherSkills` 优先从 `pluginBindings` 解析。T2-2-10d: Agent 编辑页 Teacher 规则 Tab 新增插件绑定/解绑卡片，`saveAgent` 通过 `agentBindingApi.setPlugins` 持久化，`teacherPluginBinding` 兼容新旧 pluginKey。T2-2-10e: `effectiveRulePack(id, workspaceId)` 去 workspace 化，运行时直接委托无 workspace 版本；`StateGraphPlanExecuteAgent` 与 `TeacherImprovementDraftService` 同步移除 workspaceId 参数。 |
| 2026-05-29 | T2-3-12 业务画像驱动的材料类型过滤与动态元数据表单 | planned | `WikiDomainProfileOption`; `WikiDomainProfileRegistryService`; `RawMaterialPanel.vue`; `types/index.ts`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | 待实施 | 将前端硬编码的 `teacherMaterialTypes`（`RawMaterialPanel.vue:469`）迁移到业务画像注册表 `WikiDomainProfileOption` 中。每个画像声明自己支持的材料类型列表及每种类型对应的元数据字段；前端根据当前 KB 绑定的 `domainProfileId` 动态渲染材料类型下拉和元数据表单。实现画像级材料类型隔离，避免后续扩展其他业务时所有 `business` KB 共享同一份庞大下拉列表。**图谱影响确认**：新增材料类型/页面不会破坏 `WikiRelationService` 关系计算；当前阶段维持通用 `pageType`，不扩展业务专属图谱节点类型。 |
| — | 【后续优化】Wiki 图谱业务语义着色 | future | `WikiGraphView.vue`; `WikiGraphToolbar.vue`; `batch-create-system.txt`; `WikiPageService.java` | — | 当前图谱按通用 `pageType`（`concept`/`person`/`event` 等）着色，无法区分教材作者与名著人物、文体知识与课标要求等业务语义。后续若需在图谱可视化中按业务类型过滤/着色，可将 `purposeHint` 或新增 `businessPageType` 下沉到图谱节点，与通用 `pageType` 并存。 |
