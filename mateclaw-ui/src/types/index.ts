import type { ChatErrorInfo } from './chatError'

// ==================== 通用 ====================
export interface ApiResult<T = any> {
  code: number
  msg: string
  data: T
}

export interface WikiDomainProfileOption {
  id: string
  displayName: string
  description?: string | null
  kbKind?: string | null
  sourcePluginKey?: string | null
  capabilityPackId?: string | null
}

// ==================== 用户 ====================
export interface User {
  id: string | number
  username: string
  nickname: string
  avatar?: string
  email?: string
  role: 'admin' | 'user'
  enabled: boolean
  createTime: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  nickname: string
  role: string
}

// ==================== Agent ====================
export interface Agent {
  id: string | number
  name: string
  description?: string
  agentType: 'react' | 'plan_execute'
  systemPrompt?: string
  modelName?: string
  maxIterations: number
  enabled: boolean
  icon?: string
  tags?: string
  templateId?: string | null
  templateVersion?: string | null
  templateCategory?: string | null
  templateDomain?: string | null
  profileId?: string | null
  capabilityPackId?: string | null
  templateMetadataJson?: string | null
  pluginBindings?: AgentPluginBinding[] | null
  knowledgeBaseIdsJson?: string | null
  homeSubtitle?: string | null
  homeQuickStartsJson?: string | null
  createTime?: string
  updateTime?: string
}

export interface AgentPluginBinding {
  id?: string | number
  agentId?: string | number
  pluginKey?: string | null
  capabilityPackId?: string | null
  stage?: string | null
  subject?: string | null
  enabled?: boolean | null
  configJson?: string | null
  createTime?: string | null
  updateTime?: string | null
}

export interface WorkspaceSummary {
  id: string | number
  name: string
  slug?: string
  description?: string
  basePath?: string
  ownerId?: number
  settingsJson?: string
  projectPermissionMode?: 'limited' | 'full'
  createTime?: string
  updateTime?: string
}

export interface AgentTemplate {
  id: string
  name: string
  nameZh?: string | null
  version?: string | null
  status?: string | null
  category?: string | null
  domain?: string | null
  visibility?: string | null
  ownerType?: string | null
  description?: string | null
  descriptionZh?: string | null
  icon?: string | null
  tags?: string | null
  featured?: boolean | null
  sortOrder?: number | null
  systemPrompt?: string | null
  runtime?: Record<string, any> | null
  permissions?: Record<string, any> | null
  defaultWorkspacePolicy?: Record<string, any> | null
  agentProfile?: Record<string, any> | null
  capabilityPack?: Record<string, any> | null
  pluginBindings?: Record<string, any>[] | null
  knowledgeBindings?: Record<string, any> | null
  tools?: Record<string, any>[] | null
  inputSchema?: Record<string, any> | null
  outputFormats?: string[] | null
  qualityGates?: Record<string, any> | null
  mockAcceptanceTasks?: Record<string, any>[] | null
  interactionHints?: Record<string, any> | null
  starterPrompts?: Record<string, any>[] | null
  homeSubtitle?: string | null
  homeQuickStarts?: Record<string, any>[] | null
  mvpScope?: Record<string, any> | null
  defaultKnowledgeBases?: AgentTemplateDefaultKnowledgeBase[] | null
}

export interface AgentTemplateDefaultKnowledgeBase {
  name?: string | null
  externalKey?: string | null
  description?: string | null
  required?: boolean | null
  pages?: Record<string, any>[] | null
}

export interface QuestionTypeRule {
  type: string
  displayName: string
  defaultScore: string
  generationRule: string
  requiresAnswer: boolean
  requiresScoringRubric: boolean
  requiresMaterial: boolean
}

export interface TeacherRulePack {
  id: string
  stage?: string
  subject?: string
  module: string
  name: string
  version: string
  defaultQuestionMix: Record<string, any>
  questionTypeRules: QuestionTypeRule[]
  hardRules: string[]
  sourceRequirements: Array<Record<string, string>>
  acceptanceMatrix: Array<Record<string, string>>
}

export interface TeacherRulePackView {
  rulePack: TeacherRulePack
  overridden: boolean
  builtIn: TeacherRulePack
}

export interface TeacherSkillDefinition {
  id: string
  name: string
  version: string
  module: string
  purpose: string
  supportedRulePackIds: string[]
  workflowSteps: string[]
  inputContract: Record<string, any>
  outputContract: Record<string, any>
  acceptanceSignals: Array<Record<string, string>>
}

export interface TeacherSkillBindingView {
  activeSkillIds: string[]
  defaultSkillIds: string[]
  activeSkills: TeacherSkillDefinition[]
  overridden: boolean
}

export interface TeacherImprovementDraft {
  id: string
  status: 'pending' | 'accepted' | 'rejected' | string
  targetType: string
  targetArea?: string | null
  proposalType?: string | null
  riskLevel?: string | null
  title: string
  summary: string
  sourceRunId?: string | null
  sourceConversationId?: string | null
  rulePackId?: string | null
  workspaceId?: string | number | null
  createdAt?: string | null
  reviewedAt?: string | null
  reviewNote?: string | null
  diagnosis?: Record<string, any>
  evidence?: Record<string, any>
  proposedPatch?: Record<string, any>
  proposedSkillPatch?: Record<string, any>
  proposedAcceptanceCase?: Record<string, any>
  proposedRulePack?: TeacherRulePack | null
}

export interface TemplateKnowledgeBindingHealth {
  externalKey?: string | null
  name?: string | null
  required: boolean
  matched: boolean
  knowledgeBaseId?: string | number | null
  expectedPageCount: number
  matchedPageCount: number
}

export interface TemplateCaseCheckHealth {
  key: string
  label: string
  passed: boolean
  detail?: string | null
}

export interface TemplateAppliedAgentHealth {
  agentId?: string | number | null
  name?: string | null
  enabled: boolean
  expectedWorkspaceFileCount: number
  matchedWorkspaceFileCount: number
  memoryFilePresent: boolean
  acceptanceFilePresent: boolean
  promptFilesConfigured: boolean
}

export interface TemplateHealth {
  templateId: string
  workspaceId?: string | number
  ready: boolean
  requiredCount: number
  matchedRequiredCount: number
  seededKnowledgeBaseCount: number
  matchedSeededKnowledgeBaseCount: number
  knowledgeBindings: TemplateKnowledgeBindingHealth[]
  checkCount: number
  passedCheckCount: number
  caseChecks: TemplateCaseCheckHealth[]
  applicationEvidenceReady: boolean
  appliedAgentCount: number
  appliedAgents: TemplateAppliedAgentHealth[]
}

// 兼容旧代码
export type AgentEntity = Agent
export type AgentState = 'IDLE' | 'RUNNING' | 'PAUSED' | 'ERROR' | 'COMPLETED'
export type ConversationRuntimeMode = 'default' | 'plan' | 'coding'
export type ChatExecutionSelectionType = 'skill' | 'tool'
export type ChatExecutionSelectionSource = 'bound' | 'system' | 'imported'

export interface ChatExecutionSelection {
  type: ChatExecutionSelectionType
  key: string
  label: string
  source: ChatExecutionSelectionSource
  fallbackAllowed?: boolean
  boundToAgent?: boolean
}

export interface ChatShortcutItem {
  id: string
  label: string
  value: string
  description?: string
  icon?: string
  aliases?: string[]
  suffix?: string
  group?: string
  kind?: 'reference' | 'skill' | 'tool' | 'skill-import'
  skipInsert?: boolean
  consumeQueryOnSelect?: boolean
  metadata?: Record<string, any>
}

export interface ProjectInsightSummary {
  projectName?: string
  rootPath: string
  relativePath?: string
  workingDirectoryPath?: string
  workingDirectoryRelativePath?: string
  locatorType?: string
  locatorMarkers?: string[]
  stackHints: string[]
  keyFiles: string[]
  moduleHints: string[]
  materialIndexHints?: string[]
  commandHints: string[]
  packageManager?: string
  buildSystem?: string
  gitRootPath?: string
  gitRootRelativePath?: string
  changedFileCount?: number
  changedFiles?: ProjectChangeRecord[]
  lastScannedAt?: string
}

export interface ContextRouterMemoryFileSummary {
  filename: string
  enabled: boolean
  fileSize?: number
}

export interface ContextRouterKnowledgeBaseSummary {
  id?: string | number
  name: string
  externalKey?: string
  kbKind?: string
  domainProfileId?: string
  domainProfileDisplayName?: string
  templateMatched: boolean
}

export interface ContextRouterSessionSummary {
  conversationId: string
  title?: string
  messageCount?: number
  lastActiveTime?: string
}

export interface ContextRouterSummary {
  rootPath: string
  relativePath?: string
  activeSources: string[]
  templateKnowledgeKeys: string[]
  missingKnowledgeBindings: string[]
  sessionTemporaryEnabled: boolean
  projectDerivedEnabled: boolean
  memoryFiles: ContextRouterMemoryFileSummary[]
  knowledgeBases: ContextRouterKnowledgeBaseSummary[]
  recentSessions: ContextRouterSessionSummary[]
  generatedAt?: string
}

// ==================== 会话与消息 ====================
export interface Conversation {
  id?: string | number
  conversationId: string
  title: string
  agentId?: string | number | null
  agentName?: string
  agentIcon?: string
  username?: string
  messageCount: number
  lastMessage?: string
  status?: 'active' | 'closed'
  streamStatus?: 'idle' | 'running'
  source?: string
  workingDirectory?: string
  runtimeMode?: ConversationRuntimeMode
  runtimeProviderId?: string
  runtimeModelName?: string
  workspaceName?: string
  workspaceBasePath?: string
  effectiveProjectPath?: string
  projectRelativePath?: string
  usingWorkspaceRoot?: boolean
  lastActiveTime?: string
  updateTime?: string
  createTime?: string
}

export interface Message {
  id?: string | number
  conversationId: string
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  contentParts: MessageContentPart[]
  thinkingExpanded?: boolean
  toolName?: string
  status?: 'generating' | 'completed' | 'stopped' | 'failed' | 'awaiting_approval' | 'interrupted'
  createTime?: string
  // Token 统计
  promptTokens?: number
  completionTokens?: number
  // 前端临时字段
  streaming?: boolean  // 内部动画控制，UI 渲染以 status 为准
  attachments?: ChatAttachment[]
  // Agent 事件元数据
  metadata?: MessageMetadata
  // 结构化错误信息（status === 'failed' 时可用）
  errorInfo?: ChatErrorInfo
}

export interface ChatAttachment {
  name: string
  size: number
  url: string
  storedName: string
  path: string
  contentType?: string
  contextStrategy?: string
  contextHint?: string
  /** 本地预览 URL（ObjectURL），图片附件用于避免 JWT 认证问题 */
  previewUrl?: string
}

export interface ToolCallMeta {
  toolCallId?: string
  name: string
  arguments?: string
  status: 'running' | 'completed' | 'awaiting_approval'
  result?: string
  success?: boolean
  startTime?: number
  sourceSkillName?: string
  sourceSkillKey?: string
}

export type FileChangeType = 'added' | 'modified'
export type ProjectChangeType = 'added' | 'modified' | 'deleted' | 'renamed' | 'untracked'

export interface FileChangeRecord {
  path: string
  changeType: FileChangeType
  toolName: string
  summary?: string
  bytesWritten?: number
  replacements?: number
  timestamp?: number
}

export interface ProjectChangeRecord {
  path: string
  changeType: ProjectChangeType
}

export interface CheckpointCapability {
  supported: boolean
  reason?: string
}

export interface ReviewValidationRecord {
  command: string
  toolName: string
  status: 'passed' | 'failed' | 'running' | 'pending' | 'approved' | 'denied' | 'completed'
  result?: string
  exitCode?: number
  timestamp?: number
}

export interface GeneratedArtifactRecord {
  name: string
  url?: string
  previewUrl?: string
  path?: string
  mimeType?: string
  source?: string
}

export interface ReviewSummary {
  totalFiles: number
  addedCount: number
  modifiedCount: number
  files: FileChangeRecord[]
  validations?: ReviewValidationRecord[]
  projectChangedFiles?: ProjectChangeRecord[]
  generatedArtifacts?: GeneratedArtifactRecord[]
  checkpointCapability?: CheckpointCapability
}

export type HarnessRunStatus = 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED'

export interface HarnessStep {
  id: string
  name: string
  phase: string
  status: string
  startedAt?: string
  completedAt?: string
  metadata?: Record<string, any>
}

export interface HarnessToolInvocation {
  id: string
  stepId?: string
  toolName: string
  status: string
  riskLevel?: string
  startedAt?: string
  completedAt?: string
  metadata?: Record<string, any>
}

export interface HarnessApproval {
  id: string
  toolInvocationId?: string
  status: string
  scope?: string
  requestedBy?: string
  resolvedBy?: string
  requestedAt?: string
  resolvedAt?: string
  metadata?: Record<string, any>
}

export interface HarnessExecutionSummary {
  promptTokens?: number
  completionTokens?: number
  runtimeModelName?: string
  runtimeProviderId?: string
  finishReason?: string
  errorMessage?: string
  finalAnswerPreview?: string
}

export interface HarnessRun {
  id: string
  conversationId: string
  agentId?: string
  agentName?: string
  mode?: string
  status?: HarnessRunStatus
  startedAt?: string
  completedAt?: string
  steps?: HarnessStep[]
  toolInvocations?: HarnessToolInvocation[]
  approvals?: HarnessApproval[]
  summary?: HarnessExecutionSummary
  metadata?: Record<string, any>
}

export interface PlanMeta {
  planId: string | number
  steps: string[]
  currentStep: number
  stepResults?: { result: string; status: string }[]
}

export type ApprovalDecisionScope = 'once' | 'conversation' | 'project'

export interface ApprovalDecisionPayload {
  pendingId: string
  scope?: ApprovalDecisionScope
}

export interface PendingApprovalMeta {
  pendingId: string
  toolName: string
  arguments: string
  reason: string
  /**
   * pending_approval / approved / denied are server-authoritative terminal states.
   * 'expired' is a frontend-only local synthesis used by hydrate reverse-convergence
   * (RFC-067 §4.9): when a message's metadata still says pending_approval but the
   * server's getPendingApprovals no longer lists that pendingId, the UI flips to
   * 'expired' so the banner clears without waiting for a fresh stream event.
   */
  status: 'pending_approval' | 'approved' | 'denied' | 'expired'
  // 增强字段（Phase 6: 结构化风险信息）
  findings?: GuardFinding[]
  maxSeverity?: GuardSeverity
  summary?: string
  workspaceBasePath?: string
  projectPath?: string
  approvalKey?: string
  alternativePath?: string
}

/** 单个展示分段（Claude Code 风格分段式渲染） */
export interface MessageSegment {
  id: string
  type: 'thinking' | 'tool_call' | 'content' | 'phase' | 'approval' | 'plan'
  status: 'running' | 'completed' | 'error'
  /** type=thinking */
  thinkingText?: string
  /** type=tool_call */
  toolName?: string
  toolArgs?: string
  toolResult?: string
  toolSuccess?: boolean
  /** LLM-provided tool call id, used to pair tool_call_started ↔ tool_call_completed */
  toolCallId?: string
  sourceSkillName?: string
  sourceSkillKey?: string
  /** type=content */
  text?: string
  /** type=phase */
  phaseName?: string
  /** type=approval */
  approval?: PendingApprovalMeta
  /** type=plan */
  plan?: PlanMeta
  /** 时间戳 */
  timestamp?: number
}

export interface MessageMetadata {
  type?: string
  compressedCount?: number
  summaryId?: number
  trigger?: string
  preTokens?: number
  postTokens?: number
  messagesSummarized?: number
  tailKept?: number
  toolResultsSpilled?: number
  anchored?: boolean
  fromCache?: boolean
  currentPhase?: string
  toolCalls?: ToolCallMeta[]
  plan?: PlanMeta
  pendingApproval?: PendingApprovalMeta
  reviewSummary?: ReviewSummary
  /** 当前正在执行的工具名称 */
  runningToolName?: string
  /** 服务端警告列表 */
  warnings?: string[]
  /** 分段式展示数据（新版渲染用） */
  segments?: MessageSegment[]
  /** 浏览器执行操作记录 */
  browserActions?: Array<{
    action: string
    success: boolean
    url?: string
    title?: string
    screenshot?: string
    durationMs: number
    timestamp: number
  }>
}

export interface MessageContentPart {
  type: 'text' | 'thinking' | 'image' | 'file' | 'audio' | 'video' | 'tool_call' | 'parse_error'
  text?: string
  fileUrl?: string
  fileName?: string
  storedName?: string
  contentType?: string
  fileSize?: number
  path?: string
  contextStrategy?: string
  contextHint?: string
  /** 前端流式渲染用：已显示的字符数。undefined 表示全部显示。 */
  visibleLength?: number
}

// ==================== 技能 ====================
export interface Skill {
  id: string | number
  /** Slug / immutable identifier */
  name: string
  /** RFC-042 §2.2 — locale display names; null = fall back to `name` */
  nameZh?: string
  nameEn?: string
  description?: string
  skillType: string
  icon?: string
  version?: string
  author?: string
  config?: string
  configJson?: string
  sourceCode?: string
  skillContent?: string
  enabled: boolean
  builtin?: boolean
  tags?: string
  createTime: string
  /** RFC-023: 来源对话 ID（AI 合成时记录） */
  sourceConversationId?: string
  /** RFC-023: 安全扫描状态 (PASSED / FAILED / null) */
  securityScanStatus?: string
  /** RFC-042 §2.3 — JSON-serialised SkillSecurityFinding[] from last scan */
  securityScanResult?: string
  /** RFC-042 §2.3 — wall-clock time of the last scan */
  securityScanTime?: string
}

/** 运行时解析状态（来自 /runtime/status） */
export interface SkillRuntimeStatus {
  name: string
  description?: string
  source: string  // "directory" | "database"
  configuredSkillDir?: string | null
  skillDirPath?: string | null
  runtimeAvailable: boolean
  resolutionError?: string | null
  references: Record<string, any>
  scripts: Record<string, any>
  enabled: boolean
  icon?: string
  // Security scan fields
  securityBlocked?: boolean
  securitySeverity?: string | null
  securitySummary?: string | null
  securityFindings?: SkillSecurityFinding[]
  securityWarnings?: string[]
  // Dependency check fields
  dependencyReady?: boolean
  missingDependencies?: string[]
  dependencySummary?: string | null
  // Computed label
  runtimeStatusLabel?: string
}

/** 安全扫描发现 */
export interface SkillSecurityFinding {
  ruleId: string
  severity: string
  category: string
  title: string
  description?: string
  filePath?: string
  lineNumber?: number
  snippet?: string
  remediation?: string
}

// 兼容旧代码
export type SkillEntity = Skill

// ==================== Skill 安装 ====================
export interface InstallRequest {
  bundleUrl: string
  version?: string
  enable?: boolean
  targetName?: string
  overwrite?: boolean
}

export interface InstallTask {
  taskId: string
  bundleUrl: string
  status: 'PENDING' | 'INSTALLING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  error?: string
  result?: InstallResult
  createdAt: string
  updatedAt: string
}

export interface InstallResult {
  name: string
  enabled: boolean
  sourceUrl: string
  sourceType: string
}

export interface HubSkillInfo {
  name: string
  slug: string
  description: string
  author: string
  version: string
  icon?: string
  tags?: string[]
  downloads?: number
  bundleUrl: string
}

// ==================== 工具 ====================
export interface Tool {
  id: string | number
  name: string
  displayName?: string
  description?: string
  beanName?: string
  toolType: string
  icon?: string
  mcpEndpoint?: string
  paramsSchema?: string
  enabled: boolean
  bindable?: boolean
  builtin?: boolean
  createTime: string
}

// ==================== 渠道 ====================
export interface Channel {
  id: string | number
  name: string
  channelType: string
  agentId?: string | number
  botPrefix?: string
  configJson?: string
  identityJson?: string
  enabled: boolean
  description?: string
  // 前端扩展字段
  icon?: string
  color?: string
  createTime?: string
}

/** 渠道配置字段定义 */
export interface ChannelFieldDef {
  key: string
  label: string
  placeholder: string
  required?: boolean
  sensitive?: boolean
  readOnly?: boolean
  tooltip?: string
  type: 'text' | 'password' | 'select' | 'switch' | 'number'
  options?: { label: string; value: string }[]
  defaultValue?: string | boolean | number
  /** 条件显示：仅当指定字段等于指定值时才显示此字段 */
  showIf?: { field: string; value: string | boolean | number }
}

/** 各渠道的表单字段定义 */
export const CHANNEL_FIELD_DEFS: Record<string, ChannelFieldDef[]> = {
  dingtalk: [
    { key: 'client_id', label: 'AppKey', placeholder: 'dingxxxxxxxx', required: true, type: 'text', tooltip: '钉钉开放平台应用的 AppKey（Client ID）' },
    { key: 'client_secret', label: 'AppSecret', placeholder: 'xxxxxxxxxxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: '钉钉开放平台应用的 AppSecret' },
    { key: 'connection_mode', label: '接入模式', placeholder: '', type: 'select', defaultValue: 'stream', tooltip: 'Stream 长连接无需公网 IP（推荐）；Webhook 需要公网回调地址', options: [{ label: 'Stream（长连接，推荐）', value: 'stream' }, { label: 'Webhook（HTTP 回调）', value: 'webhook' }] },
    { key: 'message_type', label: '消息格式', placeholder: '', type: 'select', defaultValue: 'markdown', tooltip: 'markdown: 普通消息；card: AI 流式卡片（需配置模板 ID）', options: [{ label: 'Markdown', value: 'markdown' }, { label: 'AI Card（流式卡片）', value: 'card' }] },
    { key: 'card_template_id', label: '卡片模板 ID', placeholder: 'dt_card_1234', required: true, type: 'text', tooltip: '钉钉 AI Card 模板 ID', showIf: { field: 'message_type', value: 'card' } },
    { key: 'robot_code', label: '机器人编码', placeholder: '留空将自动使用 AppKey（适用于自建应用机器人）', type: 'text', tooltip: '钉钉机器人 robotCode，用于发送附件（图片 / DOCX）和 AI Card。绝大多数自建应用机器人 robotCode == AppKey，不填会自动 fallback；只有第三方应用 / 单独申请的机器人才必须显式填' },
  ],
  feishu: [
    { key: 'app_id', label: 'App ID', placeholder: 'cli_xxxxxxxx', required: true, type: 'text', tooltip: '飞书开放平台应用的 App ID' },
    { key: 'app_secret', label: 'App Secret', placeholder: 'xxxxxxxxxxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: '飞书开放平台应用的 App Secret' },
    { key: 'connection_mode', label: '接入模式', placeholder: '', type: 'select', defaultValue: 'websocket', tooltip: 'WebSocket 长连接无需公网 IP（推荐，本地开发和内网部署都能直接用）；Webhook 需要公网回调地址', options: [{ label: 'WebSocket（长连接，推荐）', value: 'websocket' }, { label: 'Webhook（HTTP 回调）', value: 'webhook' }] },
    { key: 'domain', label: '服务区域', placeholder: '', type: 'select', defaultValue: 'feishu', tooltip: '国内版使用 feishu（open.feishu.cn），国际版使用 lark（open.larksuite.com）', options: [{ label: '飞书（国内版）', value: 'feishu' }, { label: 'Lark（国际版）', value: 'lark' }] },
    { key: 'verification_token', label: '验证 Token', placeholder: 'xxxxxxxx', type: 'text', tooltip: '事件订阅的 Verification Token', showIf: { field: 'connection_mode', value: 'webhook' } },
    { key: 'encrypt_key', label: '加密密钥', placeholder: '事件加密密钥（webhook 模式必填）', sensitive: true, type: 'password', tooltip: 'Encrypt Key，用于 webhook 事件回调的消息解密（webhook 模式下必填，否则启动时会拒绝）', showIf: { field: 'connection_mode', value: 'webhook' } },
    { key: 'enable_reaction', label: '消息反应', placeholder: '', type: 'switch', defaultValue: true, tooltip: '收到消息后自动添加 👍 表情反应，让用户知道消息已收到' },
    { key: 'enable_nickname_cache', label: '昵称获取', placeholder: '', type: 'switch', defaultValue: true, tooltip: '通过联系人 API 获取用户真实昵称（需要 contact:user.base:readonly 权限）' },
    { key: 'enable_quoted_context', label: '引用消息上下文', placeholder: '', type: 'switch', defaultValue: true, tooltip: '用户引用某条消息回复时，自动拉取被引用消息内容注入到 prompt，agent 才能理解"解释一下"这种缺主语的引用' },
    { key: 'media_download_enabled', label: '媒体下载', placeholder: '', type: 'switch', defaultValue: false, tooltip: '下载消息中的图片和文件到本地（保存至 ~/.mateclaw/media/feishu/）' },
  ],
  telegram: [
    { key: 'bot_token', label: 'Bot Token', placeholder: '123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11', required: true, sensitive: true, type: 'password', tooltip: '从 @BotFather 获取的 Bot Token' },
    { key: 'show_typing', label: '显示输入状态', placeholder: '', type: 'switch', defaultValue: true, tooltip: '发送回复前持续显示"正在输入..."状态（每 4 秒刷新）' },
    { key: 'connection_mode', label: '接入模式', placeholder: '', type: 'select', defaultValue: 'polling', tooltip: 'Long-Polling 无需公网 IP（推荐）；Webhook 需要公网回调地址', options: [{ label: 'Long-Polling（轮询，推荐）', value: 'polling' }, { label: 'Webhook（HTTP 回调）', value: 'webhook' }] },
    { key: 'polling_timeout', label: '轮询超时（秒）', placeholder: '20', type: 'number', defaultValue: 20, tooltip: 'Long-Polling 超时时间，服务器在有新消息时立即返回', showIf: { field: 'connection_mode', value: 'polling' } },
    { key: 'webhook_url', label: 'Webhook URL', placeholder: 'https://your-domain.com/api/v1/channels/webhook/telegram', type: 'text', tooltip: '公网可访问的回调地址，系统会自动调用 setWebhook 注册', showIf: { field: 'connection_mode', value: 'webhook' } },
    { key: 'http_proxy', label: 'HTTP 代理', placeholder: 'http://127.0.0.1:7890', type: 'text', tooltip: 'HTTP 代理地址（国内访问 Telegram API 需要）' },
  ],
  discord: [
    { key: 'bot_token', label: 'Bot Token', placeholder: 'MTxxxxxxxx.xxxxxxxx.xxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: 'Discord Developer Portal 中获取的 Bot Token' },
    { key: 'accept_bot_messages', label: '接收 Bot 消息', placeholder: '', type: 'switch', defaultValue: false, tooltip: '是否接收来自其他 Bot 的消息' },
    { key: 'http_proxy', label: 'HTTP 代理', placeholder: 'http://127.0.0.1:7890', type: 'text', tooltip: 'HTTP 代理地址（可选，用于 Gateway WebSocket 和 REST API 连接）' },
  ],
  wecom: [
    { key: 'bot_id', label: '机器人 ID', placeholder: 'bot_xxxxxxxxxx', required: true, type: 'text', tooltip: '企业微信智能机器人的 Bot ID（在企业微信后台创建智能机器人后获取）' },
    { key: 'secret', label: 'Secret', placeholder: 'xxxxxxxxxxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: '企业微信智能机器人的 Secret' },
    { key: 'welcome_text', label: '欢迎消息', placeholder: '你好！我是你的 AI 助手', type: 'text', tooltip: '用户首次进入对话时自动发送的欢迎消息（留空则不发送）' },
    { key: 'media_download_enabled', label: '媒体下载', placeholder: '', type: 'switch', defaultValue: false, tooltip: '下载消息中的图片和文件到本地并解密（需要本地磁盘空间）' },
    { key: 'media_dir', label: '媒体目录', placeholder: 'data/media', type: 'text', tooltip: '媒体文件保存目录（默认 data/media）' },
    { key: 'max_reconnect_attempts', label: '最大重连次数', placeholder: '-1 表示无限重连', type: 'number', defaultValue: -1, tooltip: 'WebSocket 断线后最大重连次数，-1 为无限重连' },
  ],
  weixin: [
    { key: 'bot_token', label: 'Bot Token', placeholder: '扫码登录后自动获取', required: true, sensitive: true, type: 'password', tooltip: '微信 iLink Bot Token，通过扫描二维码登录获取' },
    { key: 'base_url', label: 'API 地址', placeholder: 'https://ilinkai.weixin.qq.com', type: 'text', defaultValue: 'https://ilinkai.weixin.qq.com', tooltip: 'iLink Bot API 基础地址（通常无需修改）' },
    { key: 'media_download_enabled', label: '媒体下载', placeholder: '', type: 'switch', defaultValue: false, tooltip: '下载消息中的图片、文件、视频到本地并解密' },
    { key: 'media_dir', label: '媒体目录', placeholder: 'data/media', type: 'text', tooltip: '媒体文件保存目录（默认 data/media）' },
  ],
  qq: [
    { key: 'app_id', label: 'AppID', placeholder: '102xxxxxx', required: true, type: 'text', tooltip: 'QQ 开放平台机器人的 AppID' },
    { key: 'client_secret', label: 'AppSecret', placeholder: 'xxxxxxxxxxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: 'QQ 开放平台机器人的 AppSecret' },
    { key: 'markdown_enabled', label: 'Markdown 消息', placeholder: '', type: 'switch', defaultValue: true, tooltip: '发送消息时使用 Markdown 格式（部分场景下 QQ 可能不支持，可关闭回退到纯文本）' },
    { key: 'max_reconnect_attempts', label: '最大重连次数', placeholder: '100', type: 'number', defaultValue: 100, tooltip: 'WebSocket 断线后最大重连次数' },
  ],
  slack: [
    { key: 'bot_token', label: 'Bot Token', placeholder: 'xoxb-xxxxxxxxxxxx-xxxxxxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: 'Slack Bot User OAuth Token（在 Slack App → OAuth & Permissions 获取）' },
    { key: 'app_token', label: 'App Token', placeholder: 'xapp-xxxxxxxxxxxx', required: true, sensitive: true, type: 'password', tooltip: 'Slack App-Level Token（需要 connections:write scope，在 Slack App → Basic Information → App-Level Tokens 生成）' },
    { key: 'signing_secret', label: 'Signing Secret', placeholder: 'xxxxxxxxxxxxxxxx', sensitive: true, type: 'password', tooltip: 'Slack App Signing Secret（用于 Webhook 模式验证请求签名，Socket Mode 可选）' },
  ],
  webchat: [
    { key: 'api_key', label: 'API Key', placeholder: '保存后由平台自动生成', required: true, sensitive: true, readOnly: true, type: 'password', tooltip: '由平台自动生成的嵌入式 WebChat 渠道密钥，创建后可复制使用' },
    { key: 'title', label: '标题', placeholder: 'Meta Y', type: 'text', defaultValue: 'Meta Y', tooltip: '聊天面板顶部显示的标题' },
    { key: 'placeholder', label: '输入框占位文案', placeholder: 'Type a message...', type: 'text', defaultValue: 'Type a message...', tooltip: '输入框默认提示文案' },
    { key: 'primary_color', label: '主题色', placeholder: '#3b88ff', type: 'text', defaultValue: '#3b88ff', tooltip: '聊天气泡与头部使用的主色，建议使用十六进制颜色值' },
    { key: 'welcome_message', label: '欢迎语', placeholder: '你好，我可以帮你处理什么？', type: 'text', tooltip: '前端 SDK 初始化后可读取并展示的欢迎语（当前主要供配置接口返回）' },
    { key: 'allowed_origins', label: '允许嵌入域名', placeholder: 'https://example.com, https://app.example.com', type: 'text', tooltip: '预留给嵌入来源白名单校验的域名列表，多个域名用逗号分隔' },
  ],
}

// ==================== 流控制 ====================

/** 流阶段（前后端统一命名） */
export type StreamPhase =
  | 'preparing_context' // 正在准备上下文
  | 'reading_memory'    // 正在读取记忆/历史
  | 'reasoning'         // 正在推理分析
  | 'drafting_answer'   // 正在起草答案
  | 'summarizing_observations' // 正在整理工具结果
  | 'thinking'         // 模型推理中
  | 'streaming'        // 正在输出文本
  | 'executing_tool'   // 正在执行工具
  | 'awaiting_approval' // 等待审批
  | 'finalizing'       // 正在收尾
  | 'failed'           // 已失败
  | 'interrupting'     // 正在中断
  | 'queued'           // 有排队消息
  | 'reconnecting'     // 正在重连
  | 'stopped'          // 已停止
  | 'completed'        // 已完成
  | 'idle'             // 空闲

/** 阶段事件数据 */
export interface PhaseEventData {
  phase: StreamPhase | string
  timestamp?: number
  toolName?: string
  toolCount?: number
  observationCount?: number
  summaryChars?: number
  iteration?: number
}

/** 排队的用户消息 */
export interface QueuedMessage {
  /** 消息内容 */
  content: string
  /** 入队时间 */
  enqueuedAt: number
  /** 状态 */
  status: 'queued' | 'sending' | 'cancelled'
  /** 内容块（延迟创建用户消息时使用） */
  contentParts?: MessageContentPart[]
  /** 所属会话 ID */
  conversationId?: string
}

/** 心跳事件数据 */
export interface HeartbeatData {
  conversationId: string
  currentPhase: string
  waitingReason: string
  runningToolName: string
  queueLength: number
  timestamp: number
}

/** 中断响应 */
export interface InterruptResponse {
  interrupted: boolean
  queued: boolean
  reason: string
}

// ==================== 计划 ====================
export interface SubPlan {
  id: string | number
  planId: string | number
  stepIndex: number
  description: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  result?: string
  startTime?: string
  endTime?: string
}

export interface Plan {
  id: string | number
  agentId: string
  goal: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  totalSteps: number
  completedSteps: number
  summary?: string
  steps?: SubPlan[]
  createTime: string
}

// ==================== 工作区文件 ====================
export interface WorkspaceFile {
  id: string | number
  agentId: string | number
  filename: string
  content?: string
  fileSize: number
  enabled: boolean
  sortOrder: number
  createTime: string
  updateTime: string
}

// ==================== 通用分页 ====================
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

// ==================== 模型与设置 ====================
export interface ModelConfig {
  id: string | number
  name: string
  provider: string
  modelName: string
  description?: string
  temperature?: number
  maxTokens?: number
  topP?: number
  enableSearch?: boolean
  searchStrategy?: string
  enabled: boolean
  isDefault: boolean
  createTime?: string
  updateTime?: string
}

export interface SystemSettings {
  language: 'zh-CN' | 'en-US'
  streamEnabled: boolean
  debugMode: boolean
  // 搜索服务配置
  searchEnabled: boolean
  searchProvider: 'serper' | 'tavily'
  searchFallbackEnabled: boolean
  serperApiKey?: string
  serperBaseUrl: string
  tavilyApiKey?: string
  tavilyBaseUrl: string
  serperApiKeyMasked?: string
  tavilyApiKeyMasked?: string
  // Keyless 搜索 provider
  duckduckgoEnabled: boolean
  searxngBaseUrl: string
  // 视频生成配置
  videoEnabled?: boolean
  videoProvider?: string
  videoFallbackEnabled?: boolean
  zhipuApiKey?: string
  zhipuBaseUrl?: string
  zhipuApiKeyMasked?: string
  falApiKey?: string
  falApiKeyMasked?: string
  klingAccessKey?: string
  klingSecretKey?: string
  klingAccessKeyMasked?: string
  klingSecretKeyMasked?: string
}

export interface ProviderModelInfo {
  id: string
  name: string
  /** Discovery probe result (backend `probeOk` field). True = verified reachable, false = probe failed, undefined = not probed */
  probeOk?: boolean
  /** Short error message when probeOk=false */
  probeError?: string
  /**
   * RFC-049 PR-1-UI (narrow): whether the model accepts the OpenAI
   * `reasoning_effort` parameter. True only for OpenAI reasoning family.
   */
  supportsReasoningEffort?: boolean
  /**
   * RFC-049 PR-1-UI (broad): whether the model supports any form of deep
   * thinking (OpenAI reasoning_effort, Kimi/DeepSeek native thinking,
   * Anthropic extended thinking). This is the field the UI "thinking depth"
   * toggle should gate on.
   */
  supportsThinking?: boolean
  /** Whether the model natively supports image input. */
  supportsVision?: boolean
}

/**
 * RFC-073: combined runtime state of a provider.
 * - LIVE         pool member, not in cooldown — usable
 * - COOLDOWN     pool member, transient backoff after consecutive failures
 * - REMOVED      probed and HARD-removed (auth/billing/init-probe failure)
 * - UNPROBED     startup window, decision not made yet
 * - UNCONFIGURED user hasn't supplied required credentials
 */
export type Liveness = 'LIVE' | 'COOLDOWN' | 'REMOVED' | 'UNPROBED' | 'UNCONFIGURED'

export interface ProviderInfo {
  id: string
  name: string
  protocol?: string
  apiKeyPrefix?: string
  chatModel?: string
  models: ProviderModelInfo[]
  extraModels: ProviderModelInfo[]
  isCustom: boolean
  isLocal: boolean
  supportModelDiscovery: boolean
  supportConnectionCheck: boolean
  freezeUrl: boolean
  requireApiKey: boolean
  configured: boolean
  available: boolean
  apiKey?: string
  baseUrl?: string
  generateKwargs?: Record<string, unknown>
  authType?: string
  oauthConnected?: boolean
  oauthExpiresAt?: number
  /** RFC-009 P3.5: position in the multi-model failover chain (0 = excluded). */
  fallbackPriority?: number
  /** RFC-073: runtime state — UI source of truth for whether this provider is usable now. */
  liveness?: Liveness
  /** Populated only when liveness ∈ {REMOVED, COOLDOWN}. */
  unavailableReason?: string
  /** Epoch ms of the most recent removal, populated only when liveness == REMOVED. */
  lastProbedAtMs?: number
  /** Remaining cooldown window in ms, populated only when liveness == COOLDOWN. */
  cooldownRemainingMs?: number
  /** RFC-074: whether the user has explicitly opted this provider into the dropdown. */
  enabled?: boolean
}

/**
 * RFC-074: response payload from POST /models/{id}/enable | disable.
 * Frontend reads this to decide whether to fire a "switched default to X" toast.
 */
export interface EnableResult {
  defaultSwitched: boolean
  newDefaultProviderId?: string | null
  newDefaultModel?: string | null
}

export interface ActiveModelsInfo {
  activeLlm?: {
    providerId: string
    model: string
  }
}

export interface DiscoverResult {
  discoveredModels: ProviderModelInfo[]
  newModels: ProviderModelInfo[]
  totalDiscovered: number
  newCount: number
}

export interface TestResult {
  success: boolean
  latencyMs: number
  message?: string
  errorMessage?: string
}

// ==================== 安全 ====================

export type GuardSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO'
export type GuardCategory =
  | 'COMMAND_INJECTION'
  | 'DATA_EXFILTRATION'
  | 'PATH_TRAVERSAL'
  | 'SENSITIVE_FILE_ACCESS'
  | 'NETWORK_ABUSE'
  | 'CREDENTIAL_EXPOSURE'
  | 'RESOURCE_ABUSE'
  | 'CODE_EXECUTION'
  | 'PRIVILEGE_ESCALATION'
export type GuardDecision = 'ALLOW' | 'NEEDS_APPROVAL' | 'BLOCK'

export interface GuardFinding {
  ruleId: string
  severity: GuardSeverity
  category: GuardCategory
  title: string
  description?: string
  remediation?: string
  toolName?: string
  paramName?: string
  matchedPattern?: string
  snippet?: string
  metadata?: Record<string, any>
}

export interface GuardRule {
  id: string | number
  ruleId: string
  name: string
  description?: string
  toolName?: string
  paramName?: string
  category: string
  severity: string
  decision: string
  pattern: string
  excludePattern?: string
  remediation?: string
  builtin: boolean
  enabled: boolean
  priority: number
  createTime?: string
  updateTime?: string
}

export interface GuardConfig {
  id: string | number
  enabled: boolean
  guardScope: string
  guardedToolsJson?: string
  deniedToolsJson?: string
  fileGuardEnabled: boolean
  sensitivePathsJson?: string
  auditEnabled?: boolean
  auditMinSeverity?: string
  auditRetentionDays?: number
}

export interface AuditLogEntry {
  id: string | number
  conversationId?: string
  agentId?: string
  userId?: string
  channelType?: string
  toolName: string
  toolParamsJson?: string
  decision: string
  maxSeverity?: string
  findingsJson?: string
  pendingId?: string
  createTime: string
}

export interface AuditStats {
  total: number
  blocked: number
  needsApproval: number
  allowed: number
}

// ==================== 定时任务 ====================
export interface CronJob {
  id: string | number
  name: string
  cronExpression: string
  timezone: string
  agentId: string | number
  agentName?: string
  taskType: 'text' | 'agent'
  triggerMessage?: string
  requestBody?: string
  workingDirectory?: string
  enabled: boolean
  nextRunTime?: string
  lastRunTime?: string
  createTime?: string
  updateTime?: string
  workspaceName?: string
  workspaceBasePath?: string
  effectiveProjectPath?: string
  projectRelativePath?: string
  usingWorkspaceRoot?: boolean
  projectPermissionMode?: 'limited' | 'full'
  // RFC-063r §2.9 / §2.14: channel binding + most-recent delivery snapshot.
  // channelId / deliveryConfig: round-trippable on create/update.
  // lastDeliveryStatus / lastDeliveryError: read-only, populated by
  // selectListWithDeliveryStatus / selectByIdWithDeliveryStatus on the backend.
  channelId?: number | null
  channelName?: string | null
  deliveryConfig?: { targetId?: string | null; threadId?: string | null; accountId?: string | null } | null
  lastDeliveryStatus?: 'NONE' | 'PENDING' | 'DELIVERED' | 'NOT_DELIVERED'
  lastDeliveryError?: string | null
  lastExecutionSummaryStatus?: 'NONE' | 'RUNNING' | 'AWAITING_APPROVAL' | 'PERMISSION_DENIED' | 'EXECUTION_FAILED' | 'EXECUTION_SUCCEEDED'
  lastExecutionSummaryText?: string | null
}

export interface CronJobRun {
  id: string | number
  cronJobId: string | number
  conversationId?: string
  status: string
  triggerType?: string
  startedAt?: string
  finishedAt?: string
  errorMessage?: string | null
  tokenUsage?: number | null
  deliveryStatus?: string | null
  deliveryTarget?: string | null
  deliveryError?: string | null
  executionSummaryStatus?: 'NONE' | 'RUNNING' | 'AWAITING_APPROVAL' | 'PERMISSION_DENIED' | 'EXECUTION_FAILED' | 'EXECUTION_SUCCEEDED'
  executionSummaryText?: string | null
  createTime?: string
}
