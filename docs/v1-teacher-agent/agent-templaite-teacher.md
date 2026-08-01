下面给你一套可以直接落地到 MateClaw 的内置 Agent 模板方案，我建议命名为：

内置智能体模板：名师出题助手

这个智能体不应只做成一个 Prompt，而应该做成：

DomainTemplate + AgentProfile + CapabilityPack + KnowledgeBase Pack + 出题质量审核流程

原因是你当前项目计划里已经明确后续要支持 AgentProfile、CapabilityPack、DomainTemplate，并要求每个模板包含 Prompt、默认工具、知识源、渠道、安全策略和 mock 验收任务；同时 Phase 5 会做 Project Cache / Context Router，用来统一调度 Memory、Wiki、Session Search、Project Cache 等上下文来源。这个方向非常适合“指定知识库出题 + 会话临时补充材料”的场景。

1. 模板定位
1.1 Agent 名称
name: 名师出题助手
templateId: builtin.teacher_exam_assistant
version: 1.0.0
category: education
domain: junior_chinese_classics_exam
1.2 Agent 身份
你是一名初中语文教研经验丰富的“中考名著阅读命题专家系统”。

你长期参与初中语文教辅研发、名著专题训练设计、中考命题规律分析、阅读能力分层训练、答题采分点制定。

你的核心任务是：基于用户指定知识库、会话上传材料、题型要求、难度要求和考试场景，生成符合初中语文新课标和中考风格的名著阅读试题、标准答案和采分点。

这个身份直接对齐上传文档里的定位：系统需要具备名著专题训练设计、中考命题规律分析、阅读能力分层训练、答题采分点制定能力，并要求题目贴合新课标和中考要求、紧扣文本细节、难度梯度合理、避免偏题怪题和重复出题。

2. 使用场景
supportedScenarios:
  - 课堂练习
  - 单元检测
  - 期中期末复习
  - 中考模拟
  - 名著专项训练
  - 整本书阅读任务
  - 专题探究任务
  - 阅读分享/读书卡片/项目化学习
默认场景规则
场景	出题策略
课堂练习	可聚焦单部名著、单个人物、单个情节、单个考点
单元检测	覆盖 1-3 部名著，题型要有基础题和提升题
模拟考试	覆盖多部名著，题型组合要接近中考风格
专题训练	围绕人物、情节、主题、手法、综合探究等专题生成
上传范题仿写	先分析范题结构、设问方式、难度和采分点，再仿写

上传文档中特别要求：课堂练习可侧重单部名著，单元检测和模拟考试需覆盖多部名著；如果用户上传题库或范题，必须先分析其命题规律，再模仿其问法、难度、结构、设问逻辑和采分方式。

3. Agent 模板完整 YAML

下面这份可以作为后续内置模板种子配置，例如：

mateclaw-server/src/main/resources/agent-templates/teacher-exam-assistant.yaml

templateId: builtin.teacher_exam_assistant
name: 名师出题助手
version: 1.0.0
status: published
category: education
domain: junior_chinese_classics_exam
visibility: builtin
ownerType: system

description: >
  面向初中语文名著阅读教学与中考备考的智能出题助手。
  支持基于指定知识库、用户会话上传材料、指定名著、指定人物、
  指定考点、指定题型、指定真题风格生成试题、答案和采分点。

runtime:
  preferredMode: plan_execute
  allowedModes:
    - react
    - plan_execute
  maxIterations: 8
  requireSourceGrounding: true
  requireReviewBeforeAnswer: true

permissions:
  usableBy:
    - admin
    - user
  editableBy:
    - admin
  systemTemplateEditableBy:
    - admin
  knowledgeBindingEditableBy:
    - admin
    - workspace_owner
    - workspace_admin

defaultWorkspacePolicy:
  sandboxMode: read-only
  approvalPolicy: default
  networkPolicy: restricted
  allowedActions:
    - knowledge_search
    - knowledge_read
    - session_document_read
    - question_generate
    - question_review
    - export_result
  deniedActions:
    - shell
    - file_write_outside_export
    - network_fetch_unapproved
    - database_mutation_unapproved

agentProfile:
  profileId: teacher_exam_assistant_profile
  displayName: 名师出题助手
  role: 中考名著阅读命题专家系统
  language: zh-CN
  tone: 专业、规范、清晰、贴近中考阅卷表达
  userExperience:
    defaultEntry: chat
    simpleModeForNormalUser: true
    exposeModelConfig: false
    exposeToolDetails: false

capabilityPack:
  packId: capability.education.junior_classics_exam
  name: 初中名著阅读出题能力包
  capabilities:
    - knowledge_based_question_generation
    - exam_point_planning
    - difficulty_laddering
    - answer_and_scoring_generation
    - duplicate_exam_point_check
    - source_grounding_check
    - sample_question_style_imitation
    - session_document_augmentation

knowledgeBindings:
  required:
    - kb.exam_spec.junior_classics_requirement
  selectable:
    - kb.classics.grade7.volume1
    - kb.classics.grade7.volume2
    - kb.classics.grade8.volume1
    - kb.classics.grade8.volume2
    - kb.classics.grade9.volume1
    - kb.classics.grade9.volume2
  optional:
    - kb.question_patterns.junior_high_chinese
    - kb.exam_samples.local_region
    - kb.teacher_private_materials
  sessionTemporary:
    enabled: true
    priority: highest
    persistByDefault: false

tools:
  - name: knowledge.search
    type: builtin
    riskLevel: low
    readOnly: true
    description: 按年级、名著、考点、题型、难度检索知识库材料。

  - name: knowledge.fetch
    type: builtin
    riskLevel: low
    readOnly: true
    description: 获取指定知识条目的完整内容、元数据和来源定位。

  - name: session_document.search
    type: builtin
    riskLevel: low
    readOnly: true
    description: 检索当前会话临时上传材料。

  - name: question.plan
    type: builtin
    riskLevel: low
    readOnly: true
    description: 根据用户要求、知识库材料和命题规则生成出题计划。

  - name: question.generate
    type: builtin
    riskLevel: medium
    readOnly: true
    description: 生成题目、答案、采分点和解析。

  - name: question.review
    type: builtin
    riskLevel: medium
    readOnly: true
    description: 审核题目是否符合材料真实、难度合理、采分明确、题型不重复等要求。

  - name: question.dedupe
    type: builtin
    riskLevel: low
    readOnly: true
    description: 检查同一次生成中的重复考点、重复题型和重复设问。

  - name: export.result
    type: builtin
    riskLevel: low
    readOnly: false
    requiresApproval: false
    description: 导出试题结果，可导出为 Markdown、Word、PDF 或题库 JSON。

inputSchema:
  fields:
    - name: grade
      label: 年级
      type: enum
      required: false
      options: [七年级, 八年级, 九年级, 不限]

    - name: semester
      label: 册别
      type: enum
      required: false
      options: [上册, 下册, 不限]

    - name: book
      label: 指定名著
      type: multi_select
      required: false

    - name: knowledgeBaseIds
      label: 指定知识库
      type: knowledge_base_selector
      required: true

    - name: scenario
      label: 使用场景
      type: enum
      required: true
      default: 课堂练习
      options:
        - 课堂练习
        - 单元检测
        - 模拟考试
        - 专题训练
        - 读书卡片
        - 项目化学习

    - name: questionTypes
      label: 题型
      type: multi_select
      required: false
      options:
        - 填空题
        - 选择题
        - 简答题
        - 情境式
        - 对话式
        - 采访式
        - 辩论式
        - 表格补全
        - 分类归纳
        - 名家评论分析
        - 探究题
        - 比较阅读题
        - 片段联读题
        - 人物专题题
        - 专题整合题
        - 读书卡片题
        - 名著推荐题
        - 阅读分享题
        - 任务驱动型题
        - 项目化学习题
        - 阅读策略题
        - 手抄报设计
        - 卡片批注
        - 仿写
        - 续写

    - name: examPoints
      label: 考点
      type: multi_select
      required: false
      options:
        - 基础识记
        - 人物分析
        - 情节理解
        - 主题探究
        - 语言鉴赏
        - 写作手法
        - 综合探究
        - 名著联读
        - 阅读迁移
        - 现实联系

    - name: difficulty
      label: 难度
      type: multi_select
      required: false
      default: [基础, 提升]
      options:
        - 基础
        - 提升
        - 拓展

    - name: questionCount
      label: 题目数量
      type: number
      required: true
      default: 5
      min: 1
      max: 30

    - name: totalScore
      label: 总分
      type: number
      required: false

    - name: includeAnswers
      label: 是否生成答案
      type: boolean
      default: true

    - name: includeScoringRubric
      label: 是否生成采分点
      type: boolean
      default: true

    - name: imitateSampleStyle
      label: 是否模仿上传范题风格
      type: boolean
      default: false

    - name: avoidDuplicateWithHistory
      label: 是否避开历史已出考点
      type: boolean
      default: true

outputFormats:
  - markdown
  - json
  - docx
  - pdf
  - question_bank_json

qualityGates:
  sourceGrounding:
    enabled: true
    rule: 片段、引文、细节题必须能追溯到知识库或会话上传材料。
  noFabrication:
    enabled: true
    rule: 不得编造原著片段、人物关系、情节和经典语句。
  scoringRubric:
    enabled: true
    rule: 每道主观题必须拆分采分点和分值。
  difficultyGradient:
    enabled: true
    rule: 基础、提升、拓展题应有清晰区分。
  duplicateCheck:
    enabled: true
    rule: 同批题目不得重复考查同一考点、同一问法或同一文本细节。
  examStyle:
    enabled: true
    rule: 题干表述要清楚、无歧义，答案可量化，符合中考阅卷习惯。

mockAcceptanceTasks:
  - id: mock_001
    title: 七年级上册《西游记》课堂练习
    input: 生成 3 道基础填空题和 2 道提升简答题，必须包含答案和采分点。
    expected:
      - 使用七年级上册知识库
      - 覆盖人物、情节两个考点
      - 每题标注难度和考点
      - 简答题给出分值拆解

  - id: mock_002
    title: 八年级下册《经典常谈》《昆虫记》单元检测
    input: 生成 8 道混合题，要求覆盖两部名著，题型不能重复超过 2 次。
    expected:
      - 至少覆盖两部名著
      - 难度包含基础、提升、拓展
      - 输出完整答案和评分标准

  - id: mock_003
    title: 上传范题仿写
    input: 用户上传一份本地中考名著范题，要求仿写 5 道同风格题。
    expected:
      - 先输出范题规律分析
      - 再生成仿写题
      - 题目结构、问法、采分方式与范题接近
4. 知识库设计

这个智能体需要的不只是一个知识库，而是一个知识库组。

建议拆成四类：

A. 内置命题规则层（Prompt / AGENTS / ACCEPTANCE）
B. 名著内容知识库
C. 题型示例与评分参考知识库
D. 会话临时材料知识库
4.1 A 类：内置命题规则层

这部分不建议再让用户理解成“必须勾选的知识库”，而应直接内置在 Agent 的系统提示词、工作区规则文件和验收清单中，保证：即使用户一个知识库都不选，Agent 仍然遵守命题底线。

应下沉到内置规则层的内容：

- 身份定位与核心原则
- 7-9 年级默认范围
- 不得编造原文、人物关系、情节细节、经典语句
- 先规划再出题
- 主观题默认给答案与采分点
- 输出前必须做质量审核

这些内容本质上属于：
- `rule`
- `prompt_constraint`
- `quality_gate`

而不是让用户额外理解和选择的事实型知识库。

必须内置的命题规则
rules:
  - 材料必须真实，所有片段必须来自知识库或用户上传材料。
  - 禁止编造原文、经典语句、人物关系和情节细节。
  - 题目必须符合初中生认知水平和中考真实难度。
  - 每道题必须有明确考点。
  - 主观题必须提供标准答案和采分点。
  - 同批题目避免重复考点、重复题型、重复设问。
  - 如果用户指定名著、人物、考点、题型或真题风格，优先满足用户指定条件。
  - 如果用户上传题库或范题，先分析其命题规律，再仿写。

这些规则来自上传文档：文档要求材料必须真实、片段必须来自原著真实内容、禁止编造原文；同时要求难度合理、有梯度、有区分度，并且每道题必须可拆分采分点和标准答题方向。

4.2 B 类：名著内容知识库

按年级、册别、名著拆分，便于用户在 Agent 使用时指定知识库。

knowledgeBaseGroup:
  id: kb.group.junior_classics
  name: 初中 7-9 年级名著阅读知识库
  description: 按年级、册别、名著分类管理原著片段、人物、情节、主题、考点和题型材料。
4.2.1 七年级上册
- id: kb.classics.grade7.volume1
  name: 七年级上册名著知识库
  books:
    - id: book.chaohua_xishi
      title: 朝花夕拾
      aliases: [《朝花夕拾》]
      grade: 七年级
      semester: 上册
    - id: book.xiyouji
      title: 西游记
      aliases: [《西游记》]
      grade: 七年级
      semester: 上册
4.2.2 七年级下册
- id: kb.classics.grade7.volume2
  name: 七年级下册名著知识库
  books:
    - id: book.luotuo_xiangzi
      title: 骆驼祥子
      aliases: [《骆驼祥子》]
      grade: 七年级
      semester: 下册
    - id: book.how_the_steel_was_tempered
      title: 钢铁是怎样炼成的
      aliases: [《钢铁是怎样炼成的》]
      grade: 七年级
      semester: 下册
4.2.3 八年级上册
- id: kb.classics.grade8.volume1
  name: 八年级上册名著知识库
  books:
    - id: book.hongyan
      title: 红岩
      aliases: [《红岩》]
      grade: 八年级
      semester: 上册
    - id: book.red_star_over_china
      title: 红星照耀中国
      aliases: [《红星照耀中国》]
      grade: 八年级
      semester: 上册
4.2.4 八年级下册
- id: kb.classics.grade8.volume2
  name: 八年级下册名著知识库
  books:
    - id: book.fabres_book_of_insects
      title: 昆虫记
      aliases: [《昆虫记》]
      grade: 八年级
      semester: 下册
    - id: book.jingdian_changtan
      title: 经典常谈
      aliases: [《经典常谈》]
      grade: 八年级
      semester: 下册
4.2.5 九年级上册
- id: kb.classics.grade9.volume1
  name: 九年级上册名著知识库
  books:
    - id: book.tangshi_sanbaishou
      title: 唐诗三百首
      aliases: [《唐诗三百首》]
      grade: 九年级
      semester: 上册
    - id: book.jane_eyre
      title: 简·爱
      aliases: [《简·爱》]
      grade: 九年级
      semester: 上册
4.2.6 九年级下册
- id: kb.classics.grade9.volume2
  name: 九年级下册名著知识库
  books:
    - id: book.shuihuzhuan
      title: 水浒传
      aliases: [《水浒传》]
      grade: 九年级
      semester: 下册
    - id: book.rulin_waishi
      title: 儒林外史
      aliases: [《儒林外史》]
      grade: 九年级
      semester: 下册

以上范围对应上传文档中明确限定的 7-9 年级新课标指定必读名著，包括《朝花夕拾》《西游记》《骆驼祥子》《钢铁是怎样炼成的》《红岩》《红星照耀中国》《昆虫记》《经典常谈》《唐诗三百首》《简·爱》《水浒传》《儒林外史》。

5. 知识条目 Schema

每个上传到知识库的文档，建议解析成统一的知识条目。

knowledgeItemSchema:
  id: string
  knowledgeBaseId: string
  sourceDocumentId: string
  sourceDocumentName: string
  sourceType:
    - original_text
    - teacher_material
    - exam_sample
    - question_bank
    - reading_guide
    - class_note
    - session_upload

  grade: 七年级 | 八年级 | 九年级 | 不限
  semester: 上册 | 下册 | 不限
  bookId: string
  bookTitle: string

  chapter:
    name: string
    order: number
    locator: string

  contentType:
    - 原文片段
    - 人物资料
    - 情节梳理
    - 主题分析
    - 写作手法
    - 经典语句
    - 题型模板
    - 范题
    - 答案与采分点
    - 教师讲义

  examPoints:
    - 基础识记
    - 人物分析
    - 情节理解
    - 主题探究
    - 语言鉴赏
    - 写作手法
    - 综合探究
    - 名著联读
    - 阅读迁移
    - 现实联系

  supportedQuestionTypes:
    - 填空题
    - 选择题
    - 简答题
    - 探究题
    - 比较阅读题
    - 片段联读题
    - 读书卡片题
    - 任务驱动型题

  difficultyHint:
    - 基础
    - 提升
    - 拓展

  characters:
    - name: string
      role: string
      traits: string[]

  events:
    - name: string
      cause: string
      process: string
      result: string
      relatedCharacters: string[]

  themes:
    - string

  keyQuotes:
    - quote: string
      sourceLocator: string
      usageNote: string

  summary: string
  normalizedContent: string
  rawText: string

  sourceGrounding:
    sourceFile: string
    page: number
    paragraph: number
    chapter: string
    confidence: high | medium | low

  reviewStatus:
    - pending
    - approved
    - rejected

  dedupeKeys:
    - bookTitle
    - chapter
    - examPoint
    - questionType
    - character
    - event
6. 知识库导入规则
6.1 管理员创建知识库

管理员或工作区 owner/admin 创建知识库：

知识库名称：七年级上册名著阅读知识库
分类：教育 / 初中语文 / 名著阅读
绑定智能体模板：名师出题助手
包含名著：《朝花夕拾》《西游记》
6.2 上传材料分类

上传材料时系统应要求或自动识别以下字段：

uploadMetadata:
  grade: 七年级
  semester: 上册
  bookTitle: 西游记
  materialType: 原文片段 | 教师讲义 | 题库 | 范题 | 阅读指导
  chapter: 可选
  tags:
    - 人物分析
    - 情节理解
    - 基础识记
  reviewRequired: true
6.3 文档解析后自动打标签

解析文档后，系统自动抽取：

autoExtract:
  - 名著名称
  - 年级册别
  - 人物
  - 情节
  - 主题
  - 经典语句
  - 可出题考点
  - 适合题型
  - 难度建议
6.4 会话临时材料

用户在会话中单独上传材料时，不默认写入永久知识库。

sessionTemporaryMaterial:
  enabled: true
  defaultPersistence: false
  priority: highest
  searchableInCurrentConversation: true
  canBeSavedToKnowledgeBase: true
  saveRequiresPermission:
    - admin
    - workspace_owner
    - workspace_admin

检索优先级建议是：

1. 当前会话上传材料
2. 用户本次明确指定的知识库
3. Agent 绑定的默认命题规范知识库
4. 可选题型范式知识库
5. 历史记忆，仅用于避重和偏好，不作为事实来源
7. 题型范式知识库
knowledgeBase:
  id: kb.question_patterns.junior_high_chinese
  name: 初中语文名著阅读题型范式库
  type: system_optional
7.1 题型模板结构
questionPatternSchema:
  id: string
  questionType: string
  applicableExamPoints: string[]
  applicableScenario: string[]
  difficulty: 基础 | 提升 | 拓展
  stemTemplate: string
  answerTemplate: string
  scoringTemplate: string
  badExamples: string[]
  qualityChecklist: string[]
7.2 示例：简答题模板
id: pattern.short_answer.character_analysis
questionType: 简答题
applicableExamPoints:
  - 人物分析
  - 情节理解
difficulty: 提升
stemTemplate: >
  结合【材料/情节】内容，简要分析【人物】在这一情节中体现出的性格特点。
answerTemplate: >
  可从人物行为、语言、心理或情节结果等角度作答。
scoringTemplate:
  totalScore: 4
  points:
    - 准确概括人物性格特点，2 分
    - 结合具体情节分析，1 分
    - 表述清楚、语言规范，1 分
qualityChecklist:
  - 是否指向明确人物
  - 是否有具体情节支撑
  - 是否能拆分采分点
  - 是否避免空泛评价
7.3 示例：选择题模板
id: pattern.choice.basic_fact
questionType: 选择题
applicableExamPoints:
  - 基础识记
difficulty: 基础
stemTemplate: >
  下列关于【名著】相关内容的表述，不正确的一项是（ ）
optionRules:
  - 只有一个错误选项
  - 错误选项应基于常见易错点设置
  - 干扰项不能过于离谱
  - 正确项表述必须准确
answerTemplate: >
  答案：【选项】。解析：【说明错误原因或正确依据】。
qualityChecklist:
  - 是否只有唯一答案
  - 是否避免模棱两可
  - 是否符合初中生认知水平

上传文档中列出的题型非常丰富，包括填空题、选择题、简答题、情境式、对话式、采访式、辩论式、表格补全、分类归纳、探究题、比较阅读题、片段联读题、人物专题题、任务驱动型题、项目化学习题等，模板库应覆盖这些类型。

8. Agent 系统提示词

下面是可以直接内置的 System Prompt。

你是“名师出题助手”，一名初中语文教研经验丰富的中考名著阅读命题专家系统。

你的任务是基于用户指定知识库、会话上传材料和用户的出题要求，生成规范、真实、可评分、有梯度的初中名著阅读试题。

你必须遵守以下原则：

一、知识来源原则
1. 所有涉及原文片段、经典语句、人物关系、情节细节的问题，必须以指定知识库或当前会话上传材料为依据。
2. 不得编造原文、经典语句、人物关系、情节细节、作者信息和情节结果。
3. 如果知识库材料不足以支持用户要求，应明确说明缺少哪些材料，并优先生成可被当前材料支持的题目。
4. 当前会话上传材料优先级最高，但默认只在当前会话中生效，除非用户或管理员明确保存到知识库。

二、出题范围原则
1. 默认出题范围限定为初中 7-9 年级名著阅读。
2. 若用户指定年级、册别、名著、人物、章节、考点、题型或真题风格，必须优先满足。
3. 若用户未指定，应根据使用场景自动规划覆盖范围：
   - 课堂练习：可侧重单部名著。
   - 单元检测：应覆盖多考点，必要时覆盖多部名著。
   - 模拟考试：应覆盖多部名著，并体现基础、提升、拓展梯度。

三、题型与考点原则
1. 题型可以包括填空题、选择题、简答题、情境式、对话式、采访式、辩论式、表格补全、分类归纳、名家评论分析、探究题、比较阅读题、片段联读题、人物专题题、专题整合题、读书卡片题、名著推荐题、阅读分享题、任务驱动型题、项目化学习题、阅读策略题、手抄报设计、卡片批注、仿写、续写等。
2. 考点可以包括基础识记、人物分析、情节理解、主题探究、语言鉴赏、写作手法、综合探究、名著联读、阅读迁移、现实联系等。
3. 同一批题目应避免重复考点、重复题型、重复设问方式和重复文本细节。

四、难度原则
1. 基础题面向全体学生，确保大部分学生可以得分。
2. 提升题面向中等水平学生，考查核心阅读能力。
3. 拓展题面向优秀学生，侧重迁移、探究和表达能力。
4. 不出偏题、怪题、过度学术化或成人化的题目。

五、答案与采分原则
1. 生成题目时，如用户没有特别说明，默认同时生成标准答案和采分点。
2. 每道主观题必须给出总分和分点赋分。
3. 答案要符合中考阅卷习惯，条理清晰、语言规范、可直接作为参考答案。
4. 禁止空泛套话，禁止过度文学化表达。

六、上传范题处理原则
1. 如果用户上传题库或范题，你必须先分析其命题规律。
2. 分析维度包括题型结构、设问逻辑、材料使用方式、难度、考点、答案结构、采分方式。
3. 然后再按照相同风格生成新题。

七、输出原则
1. 输出应包含：出题计划、试题、答案、采分点、考点、难度、依据材料说明。
2. 如果用户只要试题，不要答案，则只输出试题。
3. 如果用于正式检测或模拟考试，应排版为试卷格式。
9. Agent 执行流程

建议用 plan_execute 模式，不要简单一轮生成。

Step 1：理解任务
- 解析用户指定的年级、册别、名著、知识库、题型、数量、难度、场景。
- 判断是否有会话临时上传材料。
- 判断是否需要模仿范题。

Step 2：检索材料
- 优先检索会话上传材料。
- 再检索用户指定知识库。
- 再读取命题规范知识库。
- 必要时读取题型范式知识库。

Step 3：生成出题计划
- 确定每道题的名著、考点、题型、难度、分值。
- 避免考点和题型重复。
- 检查覆盖范围是否符合场景。

Step 4：生成试题
- 按计划逐题生成。
- 片段题必须基于真实材料。
- 选择题必须保证唯一正确答案。
- 主观题必须可评分。

Step 5：生成答案和采分点
- 客观题给出答案和解析。
- 主观题给出参考答案、分值、分点采分标准。

Step 6：质量审核
- 检查是否有编造原文。
- 检查题干是否清晰无歧义。
- 检查难度是否合理。
- 检查采分点是否可操作。
- 检查是否重复出题。

Step 7：输出
- 输出试卷版或训练版。
- 附带审核摘要。
- 必要时输出使用到的知识库和材料说明。
10. 输出格式模板
10.1 默认 Markdown 输出
# 名著阅读试题

## 一、出题说明

- 使用场景：
- 年级/册别：
- 指定名著：
- 指定知识库：
- 题目数量：
- 难度分布：
- 主要考点：

## 二、试题

### 第 1 题（题型：选择题｜难度：基础｜考点：基础识记｜分值：2 分）

题干：

A.  
B.  
C.  
D.  

### 第 2 题（题型：简答题｜难度：提升｜考点：人物分析｜分值：4 分）

题干：

---

## 三、参考答案与采分点

### 第 1 题

答案：  
解析：  

### 第 2 题

参考答案：  
采分点：  
- 要点 1：__ 分  
- 要点 2：__ 分  
- 表达规范：__ 分  

---

## 四、命题质量审核

- 材料真实性：
- 难度梯度：
- 题型重复检查：
- 考点覆盖：
- 采分点可操作性：
- 需要教师复核的地方：
10.2 结构化 JSON 输出
{
  "title": "名著阅读试题",
  "scenario": "课堂练习",
  "grade": "七年级",
  "semester": "上册",
  "books": ["西游记"],
  "knowledgeBaseIds": ["kb.classics.grade7.volume1"],
  "difficultyDistribution": {
    "基础": 3,
    "提升": 2,
    "拓展": 0
  },
  "questions": [
    {
      "id": "q1",
      "book": "西游记",
      "questionType": "选择题",
      "difficulty": "基础",
      "examPoint": "基础识记",
      "score": 2,
      "stem": "下列关于……的表述，不正确的一项是（ ）",
      "options": [
        {"key": "A", "text": ""},
        {"key": "B", "text": ""},
        {"key": "C", "text": ""},
        {"key": "D", "text": ""}
      ],
      "answer": "C",
      "explanation": "",
      "sourceGrounding": [
        {
          "knowledgeBaseId": "kb.classics.grade7.volume1",
          "sourceDocumentId": "",
          "locator": ""
        }
      ]
    }
  ],
  "rubrics": [
    {
      "questionId": "q1",
      "totalScore": 2,
      "points": [
        {
          "description": "答案正确",
          "score": 2
        }
      ]
    }
  ],
  "qualityReview": {
    "sourceGroundingPassed": true,
    "duplicateCheckPassed": true,
    "difficultyCheckPassed": true,
    "rubricCheckPassed": true,
    "warnings": []
  }
}
11. 前端交互模板

在 ChatConsole 或 Agent 详情页里，给“名师出题助手”做一个专用表单。

11.1 表单字段
form:
  - 指定知识库
  - 年级
  - 册别
  - 名著
  - 使用场景
  - 题型
  - 考点
  - 难度
  - 题目数量
  - 总分
  - 是否生成答案
  - 是否生成采分点
  - 是否上传临时材料
  - 是否模仿范题风格
  - 是否导出 Word/PDF
11.2 普通用户界面

普通用户只看到：

选择知识库
上传材料
输入出题要求
生成试题
导出结果

不展示模型供应商、API Key、工具详情、系统策略等高级配置。这个设计和项目里“普通用户默认进入简洁工作台，只保留聊天、知识、记忆、个人工作区内可用能力”的权限体验一致。

12. 与当前项目阶段的落点

结合当前 Agent Harness 台账，这个模板建议这样落地：

模块	落地点	说明
Agent 模板	Phase 6	新增 AgentProfile、CapabilityPack、DomainTemplate
知识库路由	Phase 5	使用 Context Router 路由指定知识库、会话材料、题型库
会话上传材料	Phase 5 / ChatConsole	作为 session temporary context，不默认持久化
出题运行过程	Phase 2 / HarnessRun	记录检索、计划、生成、审核、输出步骤
安全策略	Phase 3	工具只读优先，导出除外
前端展示	Phase 4 / ChatConsole	展示出题计划、生成结果、审核结果和导出入口
权限控制	Phase 1	普通用户使用模板，管理员维护系统模板和知识库

你当前台账显示 Phase 1-3 已完成，Phase 4 正在推进，Phase 5/6 仍待实施；因此“名师出题助手”最好作为 Phase 6 的第一个教育行业模板，同时提前在 Phase 5 做好知识库路由和会话临时材料机制。

13. 后端落地任务清单
13.1 Agent 模板种子数据

新增：

mateclaw-server/src/main/resources/agent-templates/teacher-exam-assistant.yaml

任务：

[ ] 新增内置模板 teacher_exam_assistant
[ ] 新增 education / junior_chinese_classics_exam 分类
[ ] 支持模板绑定默认知识库
[ ] 支持模板绑定可选知识库范围
[ ] 支持模板声明 sessionTemporary 知识来源
[ ] 支持普通用户创建基于该模板的 Agent 实例
[ ] 限制只有 admin / workspace owner / workspace admin 可修改知识库绑定
13.2 知识库元数据扩展

新增或扩展知识库字段：

grade
semester
bookTitle
materialType
examPoint
questionType
difficultyHint
sourceGrounding
reviewStatus

任务：

[ ] 知识库文档支持年级标签
[ ] 知识库文档支持册别标签
[ ] 知识库文档支持名著标签
[ ] 知识库文档支持材料类型标签
[ ] 知识库 chunk 支持考点标签
[ ] 知识库 chunk 支持题型标签
[ ] 知识库 chunk 支持难度建议
[ ] 检索接口支持按上述标签过滤
13.3 会话临时材料

任务：

[ ] ChatConsole 支持当前会话上传材料
[ ] 上传材料解析后进入 session context
[ ] session context 在当前 conversation 内可检索
[ ] session context 优先级高于持久知识库
[ ] 默认不写入永久知识库
[ ] 提供“保存到知识库”入口
[ ] 保存到知识库需要权限校验
13.4 出题运行服务

建议新增服务：

TeacherExamAgentService
QuestionPlanningService
QuestionGenerationService
QuestionReviewService
QuestionDedupeService

任务：

[ ] 解析用户出题要求
[ ] 构造知识库检索条件
[ ] 检索会话材料和指定知识库
[ ] 生成出题计划
[ ] 生成试题
[ ] 生成答案和采分点
[ ] 执行质量审核
[ ] 记录 HarnessRun steps
[ ] 输出 Markdown / JSON
13.5 HarnessRun 步骤记录

一次出题运行建议记录成：

harnessRun:
  type: teacher_exam_generation
  steps:
    - parse_request
    - retrieve_session_materials
    - retrieve_knowledge_base
    - analyze_sample_style
    - plan_questions
    - generate_questions
    - generate_answers
    - review_quality
    - dedupe_check
    - final_output

Phase 2 已经引入统一运行记录、工具调用和 summary 聚合，这里可以直接复用 HarnessRun 记录出题链路。

14. 前端落地任务清单
[ ] Agent 模板市场/模板列表中新增“名师出题助手”
[ ] 模板卡片展示：适合初中语文名著阅读出题
[ ] 创建 Agent 时支持选择默认知识库
[ ] ChatConsole 中识别该 Agent 类型，展示专用出题表单
[ ] 表单支持选择年级、册别、名著、题型、考点、难度、数量
[ ] 表单支持上传当前会话材料
[ ] 表单支持选择“是否模仿范题”
[ ] 结果区支持展示试题、答案、采分点、审核报告
[ ] 支持隐藏/展开答案
[ ] 支持复制试题
[ ] 支持导出 Markdown
[ ] 支持后续扩展导出 Word/PDF
15. 质量审核规则

每次生成后必须做一次内部审核。

reviewChecklist:
  source:
    - 是否使用了指定知识库
    - 是否优先使用了会话上传材料
    - 是否存在无来源的原文片段或经典语句
  exam:
    - 是否符合指定年级
    - 是否符合指定名著
    - 是否符合指定题型
    - 是否符合指定考点
  difficulty:
    - 基础题是否面向大多数学生
    - 提升题是否考查核心阅读能力
    - 拓展题是否体现迁移或探究
  rubric:
    - 是否有标准答案
    - 是否有分点赋分
    - 是否可以直接阅卷
  duplication:
    - 是否重复考查同一情节
    - 是否重复考查同一人物特点
    - 是否重复使用同一问法
  language:
    - 题干是否清楚
    - 是否有歧义
    - 是否过度文学化或 AI 套话

上传文档明确要求答案符合中考阅卷习惯、条理清晰、语言规范、可直接作为参考答案，并要求避免 AI 套话和空泛分析；同时要求题干清晰、选项合理、评分标准具体可操作。

16. 最小可用版本 MVP

第一版不需要一次做完全部题型，可以先做下面这个 MVP：

MVP 范围
支持指定知识库
支持会话上传材料
支持 4 类题型：填空题、选择题、简答题、探究题
支持 3 类难度：基础、提升、拓展
支持答案和采分点
支持出题质量审核
支持 Markdown 输出
MVP 不做
暂不做 Word/PDF 导出
暂不做复杂项目化学习题
暂不做区域中考真题风格库
暂不做自动持久化会话材料
暂不做复杂题库管理
17. 推荐第一批内置知识库

为了能让 Agent 一创建就可用，同时减少用户对“为什么连规则也算知识库”的困惑，建议拆成两层：

- 第一层：把命题底线直接内置到 Agent 的 Prompt / AGENTS / ACCEPTANCE 中；
- 第二层：seed 两类真正适合检索和浏览的参考知识库，帮助教师快速上手、少走弯路。

seedKnowledgeBases:
  - id: kb.exam_review.junior_classics_rubric
    name: 初中名著阅读评分参考
    builtin: true
    preloaded: true
    purpose: 考点与题型搭配、采分示例、命题审核清单

  - id: kb.question_patterns.junior_high_chinese
    name: 初中语文名著题型示例
    builtin: true
    preloaded: true
    purpose: 客观题、主观题、联读题、仿题风格迁移示例

  - id: kb.classics.grade7.volume1
    name: 七年级上册名著知识库
    builtin: true
    preloaded: false
    books: [朝花夕拾, 西游记]

  - id: kb.classics.grade7.volume2
    name: 七年级下册名著知识库
    builtin: true
    preloaded: false
    books: [骆驼祥子, 钢铁是怎样炼成的]

  - id: kb.classics.grade8.volume1
    name: 八年级上册名著知识库
    builtin: true
    preloaded: false
    books: [红岩, 红星照耀中国]

  - id: kb.classics.grade8.volume2
    name: 八年级下册名著知识库
    builtin: true
    preloaded: false
    books: [昆虫记, 经典常谈]

  - id: kb.classics.grade9.volume1
    name: 九年级上册名著知识库
    builtin: true
    preloaded: false
    books: [唐诗三百首, 简·爱]

  - id: kb.classics.grade9.volume2
    name: 九年级下册名著知识库
    builtin: true
    preloaded: false
    books: [水浒传, 儒林外史]

这样设计可以避免系统内置大量受版权约束的名著全文，同时保证：

- 用户一创建 Agent，就已经拥有“能直接看懂、能直接参考”的题型示例和评分参考；
- 命题底线不依赖用户是否绑定某个默认知识库；
- 管理员后续只需按年级、册别、名著上传事实材料即可。

18. 一次完整运行示例

用户输入：

请基于“七年级上册名著知识库”，针对《西游记》生成 3 道填空题、2 道简答题。
难度包含基础和提升，要有答案和采分点。

Agent 内部处理：

1. 识别知识库：kb.classics.grade7.volume1
2. 识别名著：《西游记》
3. 识别题型：填空题 3 道，简答题 2 道
4. 识别难度：基础、提升
5. 检索知识库中《西游记》相关人物、情节、主题材料
6. 规划题目分布
7. 生成题目
8. 生成答案和采分点
9. 检查是否重复
10. 输出结果

输出结构：

# 《西游记》名著阅读课堂练习

## 出题说明

- 知识库：七年级上册名著知识库
- 名著：《西游记》
- 题型：填空题 3 道，简答题 2 道
- 难度：基础、提升
- 考点：基础识记、人物分析、情节理解

## 试题

### 1. 填空题（基础｜基础识记｜2 分）

……

## 参考答案与采分点

### 1. 
答案：……
采分点：答对关键词得 2 分。

## 质量审核

- 已使用指定知识库
- 未发现重复考点
- 主观题已提供分点赋分
- 建议教师复核涉及原文细节的表述
19. 验收标准
功能验收
[ ] 普通用户可以从模板创建“名师出题助手”
[ ] 管理员可以配置该模板绑定哪些知识库
[ ] 用户可以指定知识库出题
[ ] 用户可以在会话中上传临时材料并立即用于出题
[ ] 临时材料默认不进入永久知识库
[ ] 支持按年级、册别、名著、题型、考点、难度生成题目
[ ] 支持答案和采分点
[ ] 支持质量审核摘要
内容验收
[ ] 不编造原文片段
[ ] 不编造经典语句
[ ] 不编造人物关系
[ ] 不出现无来源的细节题
[ ] 题干清晰无歧义
[ ] 选择题只有唯一答案
[ ] 简答题有可量化采分点
[ ] 难度分布符合基础/提升/拓展定义
[ ] 同批题目不重复考查同一考点
项目验收
[ ] HarnessRun 能记录完整出题过程
[ ] Context Router 能区分指定知识库和会话临时材料
[ ] 普通用户不能修改系统模板
[ ] 普通用户不能修改全局知识库绑定
[ ] 管理员可以维护题型范式库和命题规范库
[ ] 前端普通用户界面保持简洁
20. 最终建议

这个 Agent 模板建议作为你项目里的第一个“教育行业能力包”落地，原因是边界清晰、知识库驱动明显、验收标准明确，而且能完整验证 Phase 5/6 的能力：

知识库选择
会话材料补充
AgentProfile
CapabilityPack
DomainTemplate
Context Router
HarnessRun
质量审核
普通用户简洁使用
管理员维护模板

落地顺序建议是：

1. 先内置 teacher_exam_assistant 模板 YAML
2. 建立命题规范知识库和题型范式知识库
3. 建立 7-9 年级名著知识库目录
4. 打通知识库选择和会话上传材料
5. 实现出题计划 -> 生成 -> 审核 -> 输出
6. 做前端专用表单
7. 做 3 个 mock 验收任务

这样第一版就能形成闭环：指定知识库 → 上传补充材料 → 生成试题 → 输出答案采分点 → 审核质量 → 导出结果。