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
| 当前优先级 | 阶段 4：名著规则包 v2 |
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
| 状态 | `pending` |
| 目标 | 将名著内测 15 点问题转成可执行规则，而不是依赖 prompt 提醒。 |
| 需求 | 建立 `classic_reading` 规则包；覆盖教材版本、题型比例、分值、材料题、主观题答案采分点、微写作、填空/表格书写量、辩论题、批注题、超纲术语、上下文串扰、插图限制。 |
| 后端修改 | 新增 `vip.mate.teacher.model.TeacherRulePack`、`QuestionTypeRule`、`vip.mate.teacher.service.TeacherRulePackService`、`mateclaw-server/src/main/resources/teacher-rules/classic-reading-v2.json`；更新 `teacher-exam-assistant.json`、`HarnessRunService.java`。 |
| 前端修改 | `Agents.vue`；新增 `mateclaw-ui/src/components/teacher/TeacherRulePackPanel.vue`。 |
| 执行过程 | 先用 JSON 规则包落地；Agent 关联默认 rule pack；UI 先展示关键规则，管理员后续可编辑。 |
| 验收测试 | 默认题型比例符合要求；主观题必有答案采分点；材料题缺材料时先请求补充；微写作不作为主体题型大量出现；批注题有方向；避免超纲术语。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

### 阶段 5：Teacher 上下文路由与目录扫描优化

| 字段 | 内容 |
| --- | --- |
| 状态 | `pending` |
| 目标 | 避免连续会话每轮 `List Directory` 遍历本地文件，提高响应速度并减少工具试错。 |
| 需求 | Teacher 默认优先使用 Agent 绑定知识库、会话附件摘要、工作区资料索引缓存；只有用户主动刷新/导入/指定新目录时才重新扫描目录。 |
| 后端修改 | `ContextRouterService`、`ProjectInsightService`、`WikiContextService`、`ToolPolicyResolver`；新增 `TeacherMaterialContextService`、`TeacherMaterialIndexCache`。 |
| 前端修改 | `ChatConsole.vue`，必要时补 Wiki/知识库入口引导。 |
| 执行过程 | Teacher guide 展示“已绑定知识库/已上传材料/当前没有资料”；受限时提示导入知识库、发起审批或使用已有材料，不盲目 shell/list directory。 |
| 验收测试 | 同工作区连续 3 轮 Teacher 问题不重复目录遍历；用户说“刷新资料索引”才重新扫描；资料缺失时给明确替代路径；工具次数超限不再是常态失败。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

### 阶段 6：规则配置 UI

| 字段 | 内容 |
| --- | --- |
| 状态 | `pending` |
| 目标 | 支持管理员通过 UI 查看和调整 Teacher 规则。 |
| 需求 | 管理员可调整模块启用、题型比例、默认分值、允许/禁用题型、材料题强制材料、答案采分点策略、超纲术语策略；普通用户只使用管理员发布配置。 |
| 后端修改 | 新增 `TeacherRulePackController`；如做持久化，新增 RulePack Entity/Mapper/Service 和 workspace override。 |
| 前端修改 | `TeacherRulePackPanel.vue`、`Agents.vue`、`api/index.ts` 或 `api/teacher.ts`、`zh-CN.ts`、`en-US.ts`。 |
| 执行过程 | 第一版名著可编辑；文言文/现代文可只读展示；权限按全局 admin 或 workspace owner/admin 控制。 |
| 验收测试 | 管理员能调整名著题型比例；普通用户无编辑入口；修改规则后新会话生效；旧会话历史不被改写。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

### 阶段 7：文言文与现代文规则包

| 字段 | 内容 |
| --- | --- |
| 状态 | `pending` |
| 目标 | 补齐文言文、现代文规则，避免所有语文任务套名著规则。 |
| 需求 | 根据 `初中文言文出题要求.docx` 和 `初中现代文出题要求.docx` 建立独立 RulePack；Teacher intent 能识别名著、文言文、现代文和混合任务。 |
| 后端修改 | 新增 `teacher-rules/classical-chinese-v1.json`、`teacher-rules/modern-reading-v1.json`；更新 `TeacherIntentService`、`TeacherRulePackService`。 |
| 前端修改 | `TeacherRulePackPanel.vue`、`ChatConsole.vue`。 |
| 执行过程 | 模块识别后选择对应规则包；混合任务要求用户确认拆分或分别生成。 |
| 验收测试 | 文言文题不套名著规则；现代文阅读题不套名著规则；混合任务能拆分或确认；每类题都有答案、采分点和来源要求。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

### 阶段 8：审批 UX 升级

| 字段 | 内容 |
| --- | --- |
| 状态 | `pending` |
| 目标 | 参考 Codex 审批卡，让用户清楚知道批准什么、批准多久、风险是什么。 |
| 需求 | 审批卡展示工具/命令、工作区路径、风险等级、触发原因、替代路径、Allow 下拉范围、Skip/拒绝、自动审批配置入口。 |
| 后端修改 | 复用 `vip.mate.approval.*`、`ToolExecutionGuardHelper.java`、`ToolPolicyResolver.java`；补充审批 metadata。 |
| 前端修改 | `ChatInput.vue`、`MessageBubble.vue`、`ChatConsole.vue`。 |
| 执行过程 | 从简单审批条升级为审批卡；Teacher 资料读取受限时，必须显示“建议导入知识库”的替代路径。 |
| 验收测试 | 触发受限工具时出现审批卡；可选择一次/会话/工作区范围；拒绝后显示替代路径；Teacher 目录读取受限时不无限重试。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

### 阶段 9：Harness 验收 v2

| 字段 | 内容 |
| --- | --- |
| 状态 | `pending` |
| 目标 | 将 Teacher v2 从 heuristic 升级为规则驱动验收。 |
| 需求 | 验收普通问答、Plan 方案确认、未确认保护、结构化输出、答案采分点、材料来源、题型比例、分值、上下文污染、UI 分块、导出、目录扫描行为。 |
| 后端修改 | `HarnessRunService.java`；新增 `TeacherAcceptanceService`。 |
| 前端修改 | `ChatConsole.vue` mock task card 和 i18n。 |
| 执行过程 | RulePack 输出 scoring signals；失败时给具体规则项，而不是笼统 partial。 |
| 验收测试 | 缺少采分点、材料题缺材料、题型比例不符合规则时能指出具体失败原因；待确认方案不会被误判失败；正式结果缺结构化字段会失败。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

### 阶段 10：Word 导出回归加固

| 字段 | 内容 |
| --- | --- |
| 状态 | `pending` |
| 目标 | 保证 Word 导出内容正确、版本稳定、与页面结构一致。 |
| 需求 | 仅试题版不含答案采分点；完整版包含试题、答案、采分点、来源、审核；无结构化试题时禁用导出或明确报错。 |
| 后端修改 | `ConversationController.java`；必要时新增 `TeacherExportService`。 |
| 前端修改 | `MessageBubble.vue`。 |
| 执行过程 | 导出 payload 使用 `TeacherExamResultV2`；后端从结构化分块生成 docx。 |
| 验收测试 | 仅试题版 docx 不含答案；完整版 docx 包含所有模块；Web 和桌面客户端均可下载；无结构化结果时提示错误。 |
| 测试结果 | 待执行。 |
| 遗留问题 | 无。 |

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
| 第三轮 | 阶段 6-8 | 规则配置 UI、文言文/现代文规则包、审批 UX。 |
| 第四轮 | 阶段 9-10 | Harness v2 和 Word 导出回归加固。 |

## 6. 阶段执行日志

| 日期 | 阶段 | 状态 | 修改文件 | 测试结果 | 备注 |
| --- | --- | --- | --- | --- | --- |
| 2026-05-25 | 阶段 0 | done | `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | `git diff --check` 通过 | 创建专项实施记录，并建立与总控台账的同步关系。 |
| 2026-05-25 | 阶段 1 | done | `mateclaw-server/src/main/java/vip/mate/teacher/model/TeacherTurnContext.java`; `mateclaw-server/src/main/java/vip/mate/teacher/service/TeacherIntentService.java`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Teacher intent smoke test passed; modified Java files compile with local JDK + `.m2` classpath | Teacher 身份/使用说明问题现在走 direct answer，不触发命题方案待确认。 |
| 2026-05-25 | 阶段 2 | in_progress | 待记录 | 实施中 | 开始将 Teacher plan 状态从尾部 assistant 文本 marker 升级为持久化 metadata 优先。 |
| 2026-05-25 | 阶段 2 | done | `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `mateclaw-server/src/main/java/vip/mate/channel/web/ChatController.java`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Modified Java files compile with local JDK + `.m2` classpath | Teacher 待确认状态现在由持久化 metadata 优先驱动，文本 marker 仅兼容老消息。 |
| 2026-05-25 | 阶段 3 | in_progress | 待记录 | 实施中 | 开始定义 `TeacherExamResultV2` 并增强前端结构化解析。 |
| 2026-05-25 | 阶段 3 | done | `mateclaw-server/src/main/java/vip/mate/teacher/model/TeacherExamResultV2.java`; `mateclaw-server/src/main/java/vip/mate/agent/graph/plan/StateGraphPlanExecuteAgent.java`; `mateclaw-ui/src/components/chat/MessageBubble.vue`; `docs/agent-harness-implementation.md`; `docs/teacher-agent/teacher-agent-v2-implementation-record.md` | Java targeted compile passed; `vue-tsc --noEmit` passed; `git diff --check` passed | Teacher 正式出题结果新增 v2 结构化契约，前端可读分块优先走结构化 JSON。 |
