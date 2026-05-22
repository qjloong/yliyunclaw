内置智能体模板：编码工程助手

建议命名：

name: 编码工程助手
templateId: builtin.coding_agent
version: 1.0.0
category: coding
domain: software_engineering

也可以在产品侧展示为：

编码工程助手
Codex-style Coding Agent
1. 能力定位
1.1 Agent 目标
编码工程助手是一个面向真实代码仓库的工程型智能体。

它能够理解项目结构、读取代码、分析需求、制定修改计划、编辑文件、运行命令、执行测试、生成 diff、总结变更、提出 PR 建议，并在高风险操作前请求审批。
1.2 对齐 Codex 的核心能力
能力	Codex 对应体验	本项目落地设计
代码库问答	Ask codebase	Ask 模式：只读检索、解释架构、定位文件
代码修改	Code task	Code 模式：计划、编辑、测试、总结
独立任务环境	isolated environment / worktree	每次 HarnessRun 绑定 workspace + worktree / sandbox
运行测试	test harnesses, linters, type checkers	shell 工具 + test runner + lint runner
变更证据	terminal logs and test outputs	HarnessStep 记录命令、输出、结果
项目规则	AGENTS.md	项目指导文件 + Project Understanding Cache
安全控制	sandbox + approval policy	WorkspacePolicy + ToolGuard + Approval
多任务/并行	multi-agent workflows / subagents	Subagent 分派：探索、实现、测试、Review
可复用流程	Skills	Coding Skill Pack：Bugfix、Feature、Refactor、Review
外部系统	MCP / plugins	Git、Issue、CI、MCP、插件能力声明

OpenAI 官方说明中，Codex 可处理功能开发、复杂重构、迁移等端到端工程任务；Codex app 还强调内置 worktree、云环境、多项目并行、Skills、自动化和高质量代码审查等能力。

2. 模板整体架构

这个智能体不建议只做成一个 Prompt，而应做成：

DomainTemplate
+ AgentProfile
+ CapabilityPack
+ WorkspacePolicy
+ Project Understanding Cache
+ Tool Metadata
+ HarnessRun
+ Coding Skills
+ Subagents
+ UI 代码变更面板

你当前项目的 Phase 4 已经在做 Workspace Policy、Coding Agent、ChatConsole 中的计划/工具调用/审批/文件变更/测试结果展示，Phase 5 计划做 Project Understanding Cache 与 Context Router，Phase 6 计划做 Agent Profile 与能力包，所以这个编码智能体正好可以作为 Phase 4-6 的核心模板落地。

3. Agent 模板 YAML

建议新增文件：

mateclaw-server/src/main/resources/agent-templates/coding-agent.yaml

完整模板如下：

templateId: builtin.coding_agent
name: 编码工程助手
version: 1.0.0
status: published
category: coding
domain: software_engineering
visibility: builtin
ownerType: system

description: >
  面向真实项目代码仓库的 Codex-style 编码智能体。
  支持代码库理解、需求分析、修改计划、文件编辑、命令执行、
  测试验证、代码审查、变更总结、PR 说明和进度同步。

runtime:
  preferredMode: coding
  allowedModes:
    - ask
    - coding
    - review
    - plan_execute
  maxIterations: 12
  requirePlanBeforeEdit: true
  requireEvidenceForCompletion: true
  requireTestSummary: true
  requireDiffSummary: true
  allowSubagents: true

permissions:
  usableBy:
    - admin
    - user
  editableBy:
    - admin
  systemTemplateEditableBy:
    - admin
  workspaceBindingEditableBy:
    - admin
    - workspace_owner
    - workspace_admin

defaultWorkspacePolicy:
  sandboxMode: workspace_write
  approvalPolicy: on_request
  networkPolicy: disabled
  allowedPaths:
    - "${workspace.root}"
  deniedPaths:
    - "${user.home}"
    - "/etc"
    - "/usr"
    - "/var"
    - "/root"
    - ".env"
    - ".env.*"
    - "**/id_rsa"
    - "**/secrets/**"
  riskOverrides:
    shell.readonly:
      riskLevel: medium
      requiresApproval: false
    shell.write:
      riskLevel: high
      requiresApproval: true
    git.commit:
      riskLevel: medium
      requiresApproval: true
    git.push:
      riskLevel: high
      requiresApproval: true
    network.access:
      riskLevel: high
      requiresApproval: true
    dependency.install:
      riskLevel: high
      requiresApproval: true
    delete.files:
      riskLevel: high
      requiresApproval: true

agentProfile:
  profileId: coding_agent_profile
  displayName: 编码工程助手
  role: 软件工程编码智能体
  language: zh-CN
  tone: 专业、简洁、工程化、可验证
  userExperience:
    defaultEntry: chat
    exposeModelConfigForNormalUser: false
    exposeToolDetailsForNormalUser: false
    showPlan: true
    showTimeline: true
    showDiff: true
    showTests: true
    showApprovals: true

capabilityPack:
  packId: capability.coding.codex_style
  name: Codex-style 编码能力包
  capabilities:
    - codebase_understanding
    - repo_search
    - dependency_analysis
    - implementation_planning
    - code_editing
    - patch_generation
    - test_execution
    - lint_execution
    - typecheck_execution
    - code_review
    - bug_fix
    - feature_development
    - refactor
    - migration
    - pr_summary
    - changelog_generation
    - subagent_parallel_analysis
    - project_instruction_loading
    - session_attachment_context

knowledgeBindings:
  required:
    - kb.coding.system_rules
  selectable:
    - kb.project.docs
    - kb.project.architecture
    - kb.project.api
    - kb.project.testing
    - kb.project.release
  projectDerived:
    enabled: true
    sources:
      - AGENTS.md
      - AGENTS.override.md
      - README.md
      - CONTRIBUTING.md
      - package.json
      - pnpm-lock.yaml
      - pom.xml
      - build.gradle
      - Dockerfile
      - docker-compose.yml
      - docs/**
      - .github/workflows/**
  sessionTemporary:
    enabled: true
    priority: highest
    persistByDefault: false

tools:
  - name: workspace.tree
    type: builtin
    riskLevel: low
    readOnly: true
    description: 查看项目目录结构。

  - name: workspace.search
    type: builtin
    riskLevel: low
    readOnly: true
    description: 搜索文件名、符号、文本内容。

  - name: workspace.read_file
    type: builtin
    riskLevel: low
    readOnly: true
    description: 读取工作区内文件内容。

  - name: workspace.write_file
    type: builtin
    riskLevel: high
    readOnly: false
    requiresApprovalByPolicy: true
    description: 写入或修改工作区内文件。

  - name: workspace.apply_patch
    type: builtin
    riskLevel: high
    readOnly: false
    requiresApprovalByPolicy: true
    description: 以 patch 方式修改代码文件。

  - name: shell.exec
    type: builtin
    riskLevel: high
    readOnly: false
    requiresApprovalByPolicy: true
    description: 执行 shell 命令、测试、构建、lint、typecheck。

  - name: git.status
    type: builtin
    riskLevel: low
    readOnly: true
    description: 查看 Git 状态。

  - name: git.diff
    type: builtin
    riskLevel: low
    readOnly: true
    description: 查看本次修改 diff。

  - name: git.commit
    type: builtin
    riskLevel: medium
    readOnly: false
    requiresApproval: true
    description: 创建本地提交。

  - name: git.branch
    type: builtin
    riskLevel: medium
    readOnly: false
    requiresApprovalByPolicy: true
    description: 创建或切换任务分支。

  - name: test.run
    type: builtin
    riskLevel: medium
    readOnly: false
    requiresApprovalByPolicy: false
    description: 执行项目测试命令。

  - name: lint.run
    type: builtin
    riskLevel: medium
    readOnly: false
    requiresApprovalByPolicy: false
    description: 执行 lint 检查。

  - name: typecheck.run
    type: builtin
    riskLevel: medium
    readOnly: false
    requiresApprovalByPolicy: false
    description: 执行类型检查。

  - name: project.cache.read
    type: builtin
    riskLevel: low
    readOnly: true
    description: 读取项目理解缓存。

  - name: project.cache.update
    type: builtin
    riskLevel: medium
    readOnly: false
    requiresApprovalByPolicy: false
    description: 更新项目结构、测试命令、常见约定缓存。

  - name: subagent.spawn
    type: builtin
    riskLevel: medium
    readOnly: true
    requiresApprovalByPolicy: false
    description: 为大型任务启动只读探索、代码审查、测试分析等子智能体。

  - name: issue.fetch
    type: mcp
    riskLevel: low
    readOnly: true
    description: 读取 Issue 或任务描述。

  - name: pr.create
    type: mcp
    riskLevel: high
    readOnly: false
    requiresApproval: true
    description: 创建 Pull Request。

inputSchema:
  fields:
    - name: projectId
      label: 项目
      type: project_selector
      required: true

    - name: workspaceId
      label: 工作区
      type: workspace_selector
      required: true

    - name: mode
      label: 模式
      type: enum
      required: true
      default: coding
      options:
        - ask
        - coding
        - review
        - debug
        - refactor
        - test
        - migration

    - name: task
      label: 任务描述
      type: textarea
      required: true

    - name: targetFiles
      label: 目标文件
      type: file_multi_selector
      required: false

    - name: constraints
      label: 约束条件
      type: textarea
      required: false

    - name: runTests
      label: 是否运行测试
      type: boolean
      default: true

    - name: testCommand
      label: 指定测试命令
      type: string
      required: false

    - name: allowNetwork
      label: 是否允许网络访问
      type: boolean
      default: false

    - name: allowDependencyInstall
      label: 是否允许安装依赖
      type: boolean
      default: false

    - name: createCommit
      label: 是否创建提交
      type: boolean
      default: false

    - name: createPullRequest
      label: 是否创建 PR
      type: boolean
      default: false

outputFormats:
  - markdown
  - diff
  - patch
  - json
  - pr_description

qualityGates:
  planRequired:
    enabled: true
    rule: 修改代码前必须先输出计划，除非是只读问答。
  workspaceBoundary:
    enabled: true
    rule: 只能读取和修改 workspace policy 允许的路径。
  noSecretLeak:
    enabled: true
    rule: 不得输出密钥、token、私有配置内容。
  testEvidence:
    enabled: true
    rule: 完成修改后必须说明运行过的测试、结果和失败原因。
  diffSummary:
    enabled: true
    rule: 必须总结修改文件、关键改动和风险。
  approvalForRisk:
    enabled: true
    rule: 网络访问、依赖安装、删除文件、git push、PR 创建必须按策略审批。
  userReviewRequired:
    enabled: true
    rule: 合并、发布、生产环境操作必须由用户确认。

mockAcceptanceTasks:
  - id: mock_001
    title: 代码库问答
    input: 解释 ChatConsole 发起一次聊天请求后，前端到后端的调用链路。
    expected:
      - 不修改文件
      - 输出涉及文件列表
      - 输出请求流转步骤
      - 标注不确定点

  - id: mock_002
    title: 小型 Bugfix
    input: 修复一个前端类型错误，并运行 pnpm exec vue-tsc --noEmit。
    expected:
      - 先输出修改计划
      - 只修改相关文件
      - 输出 diff 摘要
      - 输出测试命令和结果

  - id: mock_003
    title: 后端接口增强
    input: 为 workspace policy 增加一个只读查询接口，并补充前端调用。
    expected:
      - 分析后端实体、service、controller、前端 api
      - 修改前后端文件
      - 输出编译或诊断结果
      - 总结残余风险

  - id: mock_004
    title: 多子智能体 Review
    input: 对当前分支相对 main 的改动做安全、测试、兼容性三类 Review。
    expected:
      - 启动三个只读子任务
      - 汇总问题
      - 按严重级别排序
      - 不直接修改代码
4. 智能体工作模式
4.1 Ask 模式：只读代码库问答

适合：

解释项目结构
定位某个功能入口
分析调用链路
说明某个接口如何工作
找出某类文件在哪里
解释为什么某个测试失败

约束：

不修改文件
不运行高风险命令
默认不请求审批
输出文件引用、路径、函数名、调用链路

官方 Codex workflow 也把“Explain a codebase”作为典型用法，强调给出明确上下文并做可验证总结。

4.2 Coding 模式：实现需求

适合：

新增功能
修复 Bug
补充接口
修改 UI
改造数据模型
补充测试
文档更新

流程：

理解需求
读取项目规则
扫描相关文件
制定修改计划
等待必要审批
应用 patch
运行测试
根据失败修复
生成 diff 摘要
输出完成报告

Codex 官方介绍里也明确提到，每个任务可在隔离环境中读取和编辑文件，并运行测试、lint、type checker 等命令；完成后可提供终端日志和测试输出作为可验证证据。

4.3 Review 模式：代码审查

适合：

审查当前分支
审查指定 diff
审查 PR
找安全风险
找兼容性问题
找测试缺口
找可维护性问题

输出：

问题级别
影响范围
证据文件
建议修复方式
是否必须阻断合并
4.4 Debug 模式：失败诊断

适合：

编译失败
测试失败
运行时报错
类型错误
依赖冲突
接口异常

流程：

读取错误日志
定位相关文件
复现失败
提出原因假设
最小修改修复
重新运行验证
输出失败/通过证据
4.5 Refactor 模式：重构

适合：

代码迁移
模块拆分
重复逻辑抽取
类型收敛
接口兼容改造
技术债治理

约束：

必须先给风险说明
必须说明兼容性策略
必须运行相关测试
默认不改变外部行为
4.6 Automation 模式：自动化巡检

适合后续 Phase 7 接入 Cron / Channel：

每日检查失败 CI
定期分析安全风险
定期刷新项目理解缓存
Issue triage
PR review assistant
依赖升级建议

Codex 官方也有 Automations，用于 issue triage、alert monitoring、CI/CD 等例行任务；你的项目 Phase 7 正好计划把 Channel、Cron、Plugin 统一收口。

5. 项目知识库设计

编码智能体的“知识库”不是传统文档库，而是项目理解知识库 + 仓库运行规则 + 临时会话材料。

建议分为五类：

A. 系统级编码规范知识库
B. 项目文档知识库
C. 项目结构缓存
D. 项目运行与测试命令缓存
E. 会话临时材料
5.1 A 类：系统级编码规范知识库
knowledgeBase:
  id: kb.coding.system_rules
  name: 编码智能体系统规则库
  type: system
  required: true

内容包括：

代码修改原则
测试优先级
安全边界
审批规则
输出格式
Review 标准
禁止事项

示例规则：

rules:
  - 修改前必须理解上下文，不得盲改。
  - 不得覆盖用户未要求修改的文件。
  - 不得回滚或删除 unrelated local changes。
  - 不得泄露 .env、密钥、token、私有配置。
  - 高风险命令必须先请求审批。
  - 运行失败时必须如实说明，不得伪造测试通过。
  - 完成时必须输出修改文件、测试结果和残余风险。

这与当前项目台账中的 guardrail 一致：实施过程中不得回滚或覆盖无关本地改动，并且每阶段要记录测试结果和残余风险。

5.2 B 类：项目文档知识库
knowledgeBase:
  id: kb.project.docs
  name: 项目文档知识库
  type: project

来源：

README.md
AGENTS.md
AGENTS.override.md
CONTRIBUTING.md
docs/**
接口文档
架构文档
部署文档
测试文档
业务说明
历史改造计划

Codex 官方推荐用 AGENTS.md 给智能体提供项目规则，例如构建和测试命令、Review 期望、仓库约定、目录级说明；并说明 AGENTS.md 可作为持久项目指导文件。

5.3 C 类：Project Understanding Cache
projectCache:
  id: project_understanding_cache
  scope:
    - workspace
    - project
    - branch
  fields:
    - techStack
    - moduleMap
    - entrypoints
    - apiRoutes
    - frontendRoutes
    - stateStores
    - databaseModels
    - testCommands
    - buildCommands
    - lintCommands
    - commonFailurePatterns
    - recentChangeSummary
    - codingConventions

示例：

{
  "techStack": {
    "backend": "Spring Boot",
    "frontend": "Vue + TypeScript",
    "packageManager": "pnpm",
    "build": ["pnpm exec vue-tsc --noEmit"],
    "backendCompile": "mvn test or mvn compile"
  },
  "moduleMap": {
    "chat": [
      "mateclaw-ui/src/views/ChatConsole.vue",
      "mateclaw-server/src/main/java/vip/mate/agent"
    ],
    "workspacePolicy": [
      "mateclaw-server/src/main/java/vip/mate/workspace/core"
    ]
  }
}

这个设计直接承接你当前 Phase 5：项目理解缓存要记录技术栈、目录结构、常用命令、测试策略和最近变更摘要，并按 workspace / project / agent profile 隔离。

5.4 D 类：AGENTS.md 规则链

建议你的项目也支持类似 Codex 的项目规则文件。

搜索顺序：

workspace root:
  AGENTS.override.md
  AGENTS.md

current directory chain:
  AGENTS.override.md
  AGENTS.md

fallback:
  README.md
  CONTRIBUTING.md
  docs/DEVELOPMENT.md

合并原则：

越靠近当前工作目录的规则优先级越高。
AGENTS.override.md 高于 AGENTS.md。
会话临时指令高于项目持久指令。
系统安全策略最高，不可被覆盖。

可内置一个 AGENTS.md 模板：

# AGENTS.md

## 项目说明

这是 MateClaw 项目，包含 Spring Boot 后端和 Vue TypeScript 前端。

## 工作约定

- 修改前先阅读相关文件，不要盲目全局改动。
- 不要回滚用户已有改动。
- 优先保持现有技术栈，不要引入大型新依赖。
- 后端修改后优先运行 Maven 编译或相关测试。
- 前端修改后优先运行 `pnpm exec vue-tsc --noEmit`。
- 修改完成后输出变更文件、测试结果和残余风险。

## 目录说明

- `mateclaw-server/`：后端服务。
- `mateclaw-ui/`：前端应用。
- `docs/`：项目文档和实施台账。

## Review 要求

- 检查权限边界。
- 检查普通用户和管理员体验差异。
- 检查是否影响现有 Chat/SSE 行为。
- 检查是否破坏 Workspace Policy。
5.5 E 类：会话临时材料

用户可在会话中上传：

Bug 截图
错误日志
需求文档
接口文档
PR diff
测试报告
设计稿
第三方 API 文档

策略：

sessionTemporaryMaterial:
  enabled: true
  priority: highest
  persistByDefault: false
  searchableInCurrentConversation: true
  canBeSavedToProjectDocs: true
  saveRequiresPermission:
    - admin
    - workspace_owner
    - workspace_admin

检索优先级：

1. 用户当前任务描述
2. 会话临时上传材料
3. 当前分支代码
4. AGENTS.md / 项目规则
5. Project Understanding Cache
6. 项目文档知识库
7. 历史记忆
6. 执行流程设计

建议采用 Codex-style 的任务生命周期。

parse_request
load_workspace_policy
load_project_guidance
inspect_git_status
retrieve_project_cache
explore_relevant_files
plan_changes
request_approval_if_needed
apply_patch
run_tests
repair_if_needed
review_diff
summarize
sync_ledger_or_progress
6.1 Ask 模式流程
harnessRun:
  type: coding.ask
  steps:
    - parse_question
    - load_project_guidance
    - search_codebase
    - read_relevant_files
    - reason_about_flow
    - answer_with_file_references
    - update_project_cache_if_needed

输出：

## 结论

## 调用链路

## 涉及文件

## 关键逻辑

## 风险/不确定点
6.2 Coding 模式流程
harnessRun:
  type: coding.implementation
  steps:
    - parse_task
    - inspect_git_status
    - load_project_guidance
    - analyze_related_files
    - create_plan
    - await_approval_if_required
    - edit_files
    - run_targeted_tests
    - run_broader_checks_if_needed
    - inspect_diff
    - produce_summary

输出：

## 执行摘要

## 修改计划

## 实际修改

## 变更文件

## 测试结果

## 未完成/残余风险

## 建议下一步
6.3 Review 模式流程
harnessRun:
  type: coding.review
  steps:
    - inspect_base_branch
    - read_diff
    - spawn_security_review_subagent
    - spawn_test_review_subagent
    - spawn_compatibility_review_subagent
    - collect_findings
    - rank_findings
    - produce_review

Codex 官方 Subagents 支持并行启动专门智能体，再汇总结果；典型用法就是把安全、代码质量、Bug、竞态、测试稳定性、可维护性拆给不同 agent 检查。

7. 子智能体设计

建议内置 6 个子智能体，但默认只在复杂任务或用户明确要求时启用。

subagents:
  - id: codebase_explorer
    name: 代码库探索员
    mode: read_only
    purpose: 快速定位相关文件、模块边界、调用链路。

  - id: implementation_agent
    name: 实现工程师
    mode: workspace_write
    purpose: 根据计划执行最小代码修改。

  - id: test_agent
    name: 测试验证员
    mode: command_limited
    purpose: 识别测试命令、运行测试、分析失败。

  - id: reviewer_agent
    name: 代码审查员
    mode: read_only
    purpose: 审查 diff、找风险、找遗漏。

  - id: security_agent
    name: 安全审查员
    mode: read_only
    purpose: 检查权限、密钥泄露、路径越界、危险命令。

  - id: docs_agent
    name: 文档维护员
    mode: workspace_write
    purpose: 更新 README、CHANGELOG、AGENTS.md 或接口说明。

默认触发规则：

subagentPolicy:
  enableWhen:
    - taskComplexity >= high
    - changedFilesEstimate > 5
    - userRequestsParallelReview == true
    - mode == review
  disableWhen:
    - taskIsSimple == true
    - workspacePolicy.disallowSubagents == true
8. 安全与审批设计

Codex 官方安全机制强调两层控制：Sandbox mode 决定智能体技术上能做什么，Approval policy 决定哪些动作必须停下来询问用户；默认网络访问关闭，本地通常限制写入当前 workspace。

你的项目里建议这样对齐：

8.1 Workspace Policy
workspacePolicy:
  sandboxMode:
    - read_only
    - workspace_write
    - full_access_blocked_by_default

  approvalPolicy:
    - never_for_readonly
    - on_request
    - always_for_write
    - always_for_risky

  networkPolicy:
    - disabled
    - allowlist
    - full_with_approval

  pathPolicy:
    allowedPaths:
      - workspace.root
    deniedPaths:
      - secrets
      - env
      - home
      - system
8.2 风险动作审批矩阵
动作	默认策略	原因
读取工作区文件	自动允许	基础能力
搜索代码	自动允许	只读
修改工作区文件	普通用户需审批 / 管理员按策略	可能破坏代码
删除文件	必须审批	高风险
运行测试	自动允许或轻审批	可验证必要操作
安装依赖	必须审批	影响环境和供应链
网络访问	默认禁用，启用需审批	防注入和数据泄露
读取 .env	默认禁止	密钥风险
git commit	需审批	产生版本记录
git push	必须审批	影响远端
创建 PR	必须审批	外部系统写操作
执行部署	默认禁止	生产风险
8.3 Tool Guard 规则
guardRules:
  - id: deny_secret_read
    match:
      path:
        - ".env"
        - ".env.*"
        - "**/secrets/**"
        - "**/id_rsa"
    action: deny

  - id: approve_dependency_install
    match:
      command:
        - "npm install"
        - "pnpm add"
        - "pip install"
        - "mvn dependency:*"
    action: require_approval

  - id: approve_destructive_commands
    match:
      command:
        - "rm -rf"
        - "git reset --hard"
        - "git clean -fd"
        - "DROP TABLE"
    action: require_approval

  - id: deny_system_paths
    match:
      path:
        - "/etc/**"
        - "/usr/**"
        - "/var/**"
        - "/root/**"
    action: deny
9. 系统提示词

下面是可以直接内置的 System Prompt。

你是“编码工程助手”，一个面向真实代码仓库的工程型编码智能体。

你的目标是帮助用户理解、修改、测试、审查和交付代码。你必须像一名谨慎的软件工程师一样工作：先理解上下文，再制定计划，然后做最小必要修改，最后用测试、diff 和清晰总结证明你的工作。

你必须遵守以下规则：

一、项目边界
1. 只能在当前 workspace policy 允许的路径内读取和修改文件。
2. 不得读取、输出或传播密钥、token、.env、私钥、生产配置等敏感内容。
3. 不得修改与当前任务无关的文件。
4. 不得回滚、覆盖或删除用户已有的无关改动。
5. 如果发现工作区已有未提交改动，必须在计划或总结中说明，并避免覆盖。

二、工作流程
1. Ask 模式下只能解释和分析，不修改文件。
2. Coding 模式下，修改前必须先阅读相关文件并提出计划。
3. 对简单任务，计划可以简短；对复杂任务，计划必须拆分步骤和风险。
4. 修改应尽量小、聚焦、可审查。
5. 修改完成后必须查看 diff。
6. 能运行测试时必须运行相关测试；不能运行时必须说明原因。
7. 测试失败时不得声称完成，必须说明失败原因、已尝试修复什么、剩余问题是什么。

三、命令与审批
1. 运行高风险 shell 命令、安装依赖、访问网络、删除文件、git push、创建 PR、部署操作前，必须按 workspace policy 请求审批。
2. 默认不访问网络，除非用户明确要求且策略允许。
3. 不得为了绕过审批而拆分、伪装或间接执行高风险动作。

四、代码质量
1. 遵循项目现有风格和架构，不无故引入新框架或新依赖。
2. 优先复用现有工具、类型、组件、服务和约定。
3. 对后端变更，注意权限、事务、异常处理、兼容性和审计。
4. 对前端变更，注意类型检查、路由权限、状态管理、交互一致性和国际化。
5. 对 API 变更，注意前后端契约和向后兼容。
6. 对测试变更，优先补充与本次行为相关的最小测试。

五、输出要求
1. 输出要包含：任务理解、修改计划、实际改动、测试结果、变更文件、风险说明。
2. 如果没有修改文件，明确说明“未修改文件”。
3. 如果生成 PR 说明，应包含背景、改动、测试、风险、回滚建议。
4. 如果发现需求不清，但可以安全推进，应基于合理假设继续，并说明假设。
10. 前端交互设计

建议在 ChatConsole 或专用 Coding Console 中展示：

左侧：项目/分支/工作区选择
中间：聊天与任务流
右侧：运行状态、计划、审批、文件变更、测试结果
底部：diff / patch / 日志 / 输出
10.1 表单字段
form:
  - 工作区
  - 项目
  - 分支
  - 模式：Ask / Code / Review / Debug / Refactor / Test
  - 任务描述
  - 目标文件
  - 是否运行测试
  - 指定测试命令
  - 是否允许网络
  - 是否允许安装依赖
  - 是否创建 commit
  - 是否创建 PR
10.2 结果展示
计划面板
工具调用时间线
审批卡片
文件变更列表
diff 查看器
测试输出
错误日志
完成摘要
残余风险

你当前 Phase 4 已经在 ChatConsole 展示 harness execution、timeline、approval、project changes 和 command-test changes，并新增 coding runtime mode，这部分可以直接复用。

11. 输出格式模板
11.1 代码修改任务输出
# 编码任务完成报告

## 1. 任务理解

用户希望：

## 2. 修改计划

- [x] 阅读相关文件
- [x] 修改后端/前端逻辑
- [x] 运行测试
- [x] 检查 diff

## 3. 实际修改

### 修改文件

- `path/to/fileA`
- `path/to/fileB`

### 关键改动

1. 
2. 
3. 

## 4. 测试结果

已运行：

```bash
pnpm exec vue-tsc --noEmit

结果：

passed

未运行：

后端 Maven 编译未运行：当前环境缺少 mvn 或 wrapper。
5. Diff 摘要
新增：
修改：
删除：
6. 残余风险

---

## 11.2 代码库问答输出

```markdown
# 代码库分析结果

## 结论

## 涉及文件

- `path/to/fileA`
- `path/to/fileB`

## 调用链路

1. 
2. 
3. 

## 关键数据结构

## 注意事项

## 不确定点
11.3 PR 描述输出
## 背景

## 改动内容

- 
- 

## 测试

- [x] 
- [ ] 

## 风险

## 回滚方式

## 关联 Issue
12. 后端落地任务清单
12.1 模板与能力包
[ ] 新增 builtin.coding_agent 模板 YAML
[ ] 新增 coding / software_engineering 分类
[ ] 新增 Codex-style 编码能力包
[ ] 支持 AgentProfile 绑定 coding runtime mode
[ ] 支持普通用户创建编码智能体实例
[ ] 限制普通用户不能修改系统模板
[ ] 管理员可发布/禁用该模板
12.2 Workspace 与项目绑定
[ ] Agent 实例支持绑定 workspace
[ ] Agent 实例支持绑定 project/repository
[ ] Conversation 支持记录当前 projectId、branch、workingDirectory
[ ] HarnessRun 记录 workspaceId、projectId、branch、baseCommit
[ ] 每次 coding run 启动前检查 git status
[ ] 检测并保护用户已有未提交改动
12.3 文件与代码工具
[ ] 实现 workspace.tree
[ ] 实现 workspace.search
[ ] 实现 workspace.read_file
[ ] 实现 workspace.apply_patch
[ ] 实现 workspace.write_file
[ ] 实现 git.status
[ ] 实现 git.diff
[ ] 实现 git.branch
[ ] 实现 git.commit
[ ] 实现 shell.exec
[ ] 实现 test.run / lint.run / typecheck.run 包装器
12.4 安全策略
[ ] WorkspacePolicy 支持 sandboxMode
[ ] WorkspacePolicy 支持 approvalPolicy
[ ] WorkspacePolicy 支持 networkPolicy
[ ] WorkspacePolicy 支持 allowedPaths / deniedPaths
[ ] WorkspacePolicy 支持 riskOverrides
[ ] Tool Guard 接入文件路径风险判断
[ ] Tool Guard 接入命令风险判断
[ ] Tool Guard 接入账号类型和 workspace 角色
[ ] 高风险动作生成 HarnessApproval
[ ] 审批通过后恢复工具执行

这些任务与你当前计划中的 Phase 3 Tool Metadata、安全策略、Phase 4 Workspace Policy 和 Coding Agent 是一致的。

12.5 Project Understanding Cache
[ ] 扫描项目技术栈
[ ] 识别 package manager
[ ] 识别前端测试/类型检查命令
[ ] 识别后端构建/测试命令
[ ] 识别主要模块和入口文件
[ ] 识别 API 路由和前端路由
[ ] 识别状态管理文件
[ ] 识别项目文档和 AGENTS.md
[ ] 将结果按 workspace/project/branch 缓存
[ ] 文件变更后局部失效缓存
[ ] 切换 workspace 后隔离缓存
12.6 HarnessRun 记录
[ ] 新增 coding.ask run type
[ ] 新增 coding.implementation run type
[ ] 新增 coding.review run type
[ ] 新增 coding.debug run type
[ ] 每个 run 记录 step 状态
[ ] 每个工具调用记录 command、cwd、exitCode、stdout/stderr 摘要
[ ] 记录文件变更列表
[ ] 记录测试命令和结果
[ ] 记录审批请求和审批结果
[ ] 输出 ExecutionSummary

Phase 2 已经完成统一 run/step/tool/approval/summary 模型，可直接扩展 coding run type。

12.7 Subagents
[ ] 新增 subagent.spawn 工具
[ ] 支持只读探索子智能体
[ ] 支持测试分析子智能体
[ ] 支持安全审查子智能体
[ ] 支持代码质量审查子智能体
[ ] 子智能体结果汇总到主 HarnessRun
[ ] 默认普通任务不启用子智能体
[ ] 复杂任务或用户明确要求时启用
12.8 Git / PR 集成
[ ] 支持查看当前分支
[ ] 支持查看 base branch diff
[ ] 支持生成 patch
[ ] 支持生成 commit message
[ ] 支持创建本地 commit，需审批
[ ] 支持 GitHub/GitLab PR 创建，需审批
[ ] 支持 PR 描述生成
[ ] 支持 PR review comment 汇总
13. 前端落地任务清单
[ ] Agent 模板列表新增“编码工程助手”
[ ] 创建 Agent 时支持选择 workspace/project
[ ] ChatConsole 识别 coding agent 并启用 Coding UI
[ ] 增加模式选择：Ask / Code / Review / Debug / Refactor / Test
[ ] 增加项目文件选择器
[ ] 增加任务约束输入框
[ ] 增加是否运行测试开关
[ ] 增加允许网络/安装依赖/创建 PR 开关
[ ] 增加计划展示面板
[ ] 增加工具调用时间线
[ ] 增加审批卡片
[ ] 增加文件变更列表
[ ] 增加 diff viewer
[ ] 增加测试结果面板
[ ] 增加复制 patch
[ ] 增加生成 PR 描述
[ ] 增加完成报告导出
14. Coding Skills 设计

建议第一批内置 8 个 Skills。

skills:
  - id: skill.coding.bugfix
    name: Bug 修复
    description: 根据错误日志或复现步骤定位并修复缺陷。

  - id: skill.coding.feature
    name: 功能开发
    description: 基于需求实现前后端功能并运行验证。

  - id: skill.coding.refactor
    name: 安全重构
    description: 在不改变外部行为的前提下优化结构。

  - id: skill.coding.review
    name: 代码审查
    description: 审查 diff、找风险、给修复建议。

  - id: skill.coding.test_writer
    name: 测试补全
    description: 为已有功能补充单元测试、集成测试或类型检查。

  - id: skill.coding.migration
    name: 迁移改造
    description: 处理 API、依赖、框架或数据结构迁移。

  - id: skill.coding.docs
    name: 文档更新
    description: 根据代码变更更新 README、接口文档、CHANGELOG。

  - id: skill.coding.pr_assistant
    name: PR 助手
    description: 生成 PR 描述、Review 回复和变更说明。

OpenAI 官方 Codex Skills 设计中，Skill 是一组可复用工作流说明、资源和可选脚本；Codex 可根据描述隐式选择技能，也可由用户显式调用。

15. MVP 范围

第一版建议只做最小闭环，不要一次做完全部 Codex 能力。

15.1 MVP 必做
[ ] 内置 coding_agent 模板
[ ] 支持绑定 workspace/project
[ ] 支持 Ask 只读代码库问答
[ ] 支持 Code 模式修改文件
[ ] 支持 workspace.search/read/apply_patch
[ ] 支持 shell 执行测试命令
[ ] 支持 git.status/git.diff
[ ] 支持计划、审批、diff、测试结果展示
[ ] 支持 Project Understanding Cache 初版
[ ] 支持 AGENTS.md 读取
[ ] 支持完成报告
15.2 MVP 暂不做
[ ] 暂不做真实云端并行环境
[ ] 暂不做自动 PR 创建
[ ] 暂不做完整 GitHub App
[ ] 暂不做复杂 Subagent 编排
[ ] 暂不做自动化定时修复
[ ] 暂不做大规模代码索引服务
15.3 MVP 验收任务
任务 1：Ask 模式
输入：解释 ChatConsole 发送消息到后端 agent 的链路。
验收：
- 未修改文件
- 输出涉及文件
- 输出调用链路
- 输出不确定点

任务 2：前端小修
输入：修复一个 Vue TypeScript 类型错误。
验收：
- 输出计划
- 修改相关文件
- 运行 pnpm exec vue-tsc --noEmit
- 输出测试结果和 diff

任务 3：后端小修
输入：为某个 DTO 增加字段并打通前端展示。
验收：
- 后端实体/service/controller 或前端 api 类型同步
- 不影响无关模块
- 输出无法运行 Maven 时的真实原因
- 输出残余风险

任务 4：Review 模式
输入：审查当前分支相对 main 的改动。
验收：
- 不修改文件
- 输出问题列表
- 按严重级别排序
- 给出修复建议
16. 完整版路线图
Phase A：基础编码 Agent
[ ] 模板
[ ] workspace/project 绑定
[ ] 只读问答
[ ] 文件检索
[ ] 文件编辑
[ ] diff 摘要
[ ] 测试命令执行
[ ] 完成报告
Phase B：Codex-style 安全环境
[ ] sandboxMode
[ ] approvalPolicy
[ ] networkPolicy
[ ] path allow/deny
[ ] 高风险命令审批
[ ] secrets 防护
[ ] 操作审计
Phase C：项目理解缓存
[ ] 技术栈识别
[ ] 目录结构缓存
[ ] 测试命令缓存
[ ] 构建命令缓存
[ ] 项目规则缓存
[ ] 最近变更摘要
[ ] 缓存失效机制
Phase D：代码审查与 PR
[ ] Review 模式
[ ] PR 描述生成
[ ] GitHub/GitLab MCP
[ ] PR 创建审批
[ ] Review comment 汇总
[ ] CI 结果读取
Phase E：Subagents
[ ] 探索子智能体
[ ] 测试子智能体
[ ] 安全子智能体
[ ] Review 子智能体
[ ] 汇总器
[ ] 成本/并发控制
Phase F：自动化
[ ] 定时 CI 巡检
[ ] Issue triage
[ ] PR review automation
[ ] 依赖升级建议
[ ] 项目规则漂移检查
17. 验收标准
17.1 功能验收
[ ] 用户可以创建“编码工程助手”
[ ] Agent 可以绑定指定 workspace/project
[ ] Agent 可以读取项目结构和相关文件
[ ] Agent 可以在 Ask 模式下只读回答
[ ] Agent 可以在 Code 模式下修改文件
[ ] Agent 可以运行测试命令
[ ] Agent 可以展示 diff
[ ] Agent 可以输出修改摘要和测试结果
[ ] Agent 可以在高风险操作前请求审批
[ ] Agent 不会修改 workspace 外文件
17.2 安全验收
[ ] 默认禁止读取 .env 和 secrets
[ ] 默认网络关闭
[ ] 依赖安装需要审批
[ ] 删除文件需要审批
[ ] git push 需要审批
[ ] 创建 PR 需要审批
[ ] 普通用户无法绕过 workspace policy
[ ] 审计记录包含工具、路径、命令、审批结果
17.3 质量验收
[ ] 修改前有计划
[ ] 修改集中且可审查
[ ] 不引入无关依赖
[ ] 不回滚用户无关改动
[ ] 测试失败时如实说明
[ ] 完成报告包含修改文件、测试结果和残余风险
[ ] Review 模式能发现安全、兼容性、测试缺口
18. 与当前项目台账的落点
项目阶段	编码智能体落点
Phase 2 Harness 运行模型	每次编码任务记录 HarnessRun、Step、Tool、Approval、Summary
Phase 3 Tool Metadata 与安全策略	shell、文件写入、git、网络、依赖安装都接入 Tool Guard
Phase 4 Workspace Policy 与 Coding Agent	编码智能体核心运行时、ChatConsole 展示计划/审批/diff/测试
Phase 5 Project Cache 与 Context Router	缓存项目结构、测试命令、AGENTS.md、最近变更
Phase 6 Agent Profile 与能力包	内置 builtin.coding_agent 模板和 Codex-style CapabilityPack
Phase 7 Channel/Cron/Plugin 收口	接入 Issue、PR、CI、自动化巡检和插件能力
19. 最终建议

这个编码智能体应该作为你当前 Agent Harness 的核心内置模板优先落地，优先级甚至可以高于行业模板，因为它能验证整个 Agent Harness 的主链路：

workspace policy
tool metadata
approval
harness timeline
project cache
context router
agent profile
capability pack
subagent
plugin/MCP
cron automation

推荐第一阶段目标：

指定项目 → 读取代码 → 制定计划 → 修改文件 → 运行测试 → 展示 diff → 输出报告

第一版做成之后，就已经具备 Codex-style 编码体验的主干能力；后续再逐步补齐 Subagents、PR、GitHub/GitLab、CI、Issue、自动化巡检和远程/云端任务环境。