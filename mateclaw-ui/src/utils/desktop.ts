export interface DesktopServerConfig {
  backendUrl: string
  proxyUrl: string
  autoStartBackend: boolean
  backendCommand: string
  backendArgs: string[]
  backendCwd: string
  updatedAt: string | null
}

export interface DesktopRuntimeInfo {
  isDesktop: boolean
  isPackaged: boolean
  electronVersion: string
  localAppUrl: string | null
  backendUrl: string
  proxyUrl?: string
  apiTargetUrl?: string
  localToolHost?: {
    available: boolean
    mode: string
    capabilities: string[]
    sharedPayloadSchema?: string
    harnessIngest?: boolean
  }
  platform: string
  arch: string
  homeDir: string
}

export interface DesktopServerTestResult {
  success: boolean
  status?: number
  url: string
  body?: string
  message?: string
}

export interface DesktopLocalSharedPayload {
  schemaVersion: string
  source: string
  timestamp: string
  toolName: string
  mode: string
  status: string
  riskLevel: string
  requiresApproval: boolean
  workspaceRoot: string | null
  cwd: string | null
  summary: string
  evidence: Record<string, any>
  approval: Record<string, any> | null
  truncation: Record<string, any> | null
}

export interface DesktopHarnessIngestResult {
  success: boolean
  status: number
  url: string
  body: any
}

export interface DesktopToolResultBase {
  sharedPayload?: DesktopLocalSharedPayload
}

export interface DesktopWorkspaceTreeOptions {
  rootPath: string
  basePath?: string
  maxDepth?: number
  maxEntries?: number
}

export interface DesktopWorkspaceGlobOptions {
  rootPath: string
  includePattern: string
  excludePatterns?: string[]
  maxResults?: number
}

export interface DesktopWorkspaceGrepOptions {
  rootPath: string
  query: string
  isRegexp?: boolean
  caseSensitive?: boolean
  includePattern?: string
  maxResults?: number
}

export interface DesktopReadSnippetOptions {
  rootPath: string
  filePath: string
  startLine: number
  endLine: number
  maxLines?: number
}

export interface DesktopWritePatchOptions {
  rootPath: string
  filePath: string
  operations: Record<string, any>[]
  expectedSha256?: string
  allowCreate?: boolean
}

export interface DesktopGitStatusOptions {
  rootPath: string
}

export interface DesktopGitDiffOptions {
  rootPath: string
  staged?: boolean
  pathspecs?: string[]
  contextLines?: number
  maxBytes?: number
}

export interface DesktopReadonlyCommandOptions {
  rootPath: string
  cwd?: string
  command: string
  args?: string[]
  timeoutMs?: number
  maxOutputBytes?: number
}

export interface DesktopApprovalCommandOptions extends DesktopReadonlyCommandOptions {}

export interface DesktopHarnessLinkOptions {
  harnessRunId?: string | null
  workspaceId?: string | number | null
  authToken?: string | null
  backendUrl?: string | null
}

export type DesktopToolResultWithIngest<T> = T & {
  harnessIngest?: DesktopHarnessIngestResult | null
}

function getDesktopApi() {
  return window.mateclawDesktop
}

function toDesktopOptionsRecord<T extends object>(options: T): Record<string, unknown> {
  return { ...options } as Record<string, unknown>
}

function getWorkspaceIdForDesktop(link?: DesktopHarnessLinkOptions) {
  if (link?.workspaceId != null && String(link.workspaceId).trim()) {
    return String(link.workspaceId).trim()
  }
  return String(localStorage.getItem('mc-workspace-id') || '').trim() || undefined
}

function getAuthTokenForDesktop(link?: DesktopHarnessLinkOptions) {
  if (link?.authToken != null && String(link.authToken).trim()) {
    return String(link.authToken).trim()
  }
  return String(localStorage.getItem('token') || '').trim() || undefined
}

async function maybeIngestDesktopPayload(result: DesktopToolResultBase | null | undefined, link?: DesktopHarnessLinkOptions) {
  const harnessRunId = String(link?.harnessRunId || '').trim()
  const payload = result?.sharedPayload
  if (!harnessRunId || !payload || !isDesktopRuntime()) {
    return null
  }
  return (await getDesktopApi()?.ingestHarnessToolResult?.({
    runId: harnessRunId,
    payload,
    backendUrl: link?.backendUrl || undefined,
    workspaceId: getWorkspaceIdForDesktop(link),
    authToken: getAuthTokenForDesktop(link),
  })) ?? null
}

async function withOptionalHarnessIngest<T extends DesktopToolResultBase>(
  promise: Promise<T | null | undefined>,
  link?: DesktopHarnessLinkOptions,
): Promise<DesktopToolResultWithIngest<T> | null> {
  const result = (await promise) ?? null
  if (!result) {
    return null
  }
  const harnessIngest = await maybeIngestDesktopPayload(result, link)
  return {
    ...result,
    harnessIngest,
  }
}

function toPlainDesktopConfig(config: Partial<DesktopServerConfig>) {
  return {
    backendUrl: String(config.backendUrl || '').trim(),
    proxyUrl: String(config.proxyUrl || '').trim(),
    autoStartBackend: Boolean(config.autoStartBackend),
    backendCommand: String(config.backendCommand || '').trim(),
    backendArgs: Array.isArray(config.backendArgs)
      ? config.backendArgs.map(item => String(item)).filter(Boolean)
      : [],
    backendCwd: String(config.backendCwd || '').trim(),
    updatedAt: config.updatedAt || null,
  }
}

export function isDesktopRuntime() {
  return Boolean(getDesktopApi()?.isDesktop)
}

export async function getDesktopRuntimeInfo() {
  return getDesktopApi()?.getRuntimeInfo?.()
}

export async function getDesktopServerConfig(): Promise<DesktopServerConfig | null> {
  return (await getDesktopApi()?.getServerConfig?.()) ?? null
}

export async function saveDesktopServerConfig(config: Partial<DesktopServerConfig>) {
  return (await getDesktopApi()?.saveServerConfig?.(toPlainDesktopConfig(config))) as DesktopServerConfig | null
}

export async function testDesktopServer(config: Partial<DesktopServerConfig>): Promise<DesktopServerTestResult | null> {
  return (await getDesktopApi()?.testServer?.(toPlainDesktopConfig(config))) ?? null
}

export async function selectDesktopDirectory() {
  return (await getDesktopApi()?.selectDirectory?.()) ?? null
}

export async function revealDesktopPath(targetPath: string) {
  if (!targetPath) return false
  return (await getDesktopApi()?.revealPath?.(targetPath)) ?? false
}

export async function openDesktopPath(targetPath: string) {
  if (!targetPath) return false
  return (await getDesktopApi()?.openPath?.(targetPath)) ?? false
}

export async function closeDesktopCurrentWindow() {
  return (await getDesktopApi()?.closeCurrentWindow?.()) ?? false
}

export async function ingestDesktopHarnessToolResult(payload: DesktopLocalSharedPayload, link: DesktopHarnessLinkOptions) {
  const harnessRunId = String(link?.harnessRunId || '').trim()
  if (!harnessRunId || !payload || !isDesktopRuntime()) {
    return null
  }
  return (await getDesktopApi()?.ingestHarnessToolResult?.({
    runId: harnessRunId,
    payload,
    backendUrl: link?.backendUrl || undefined,
    workspaceId: getWorkspaceIdForDesktop(link),
    authToken: getAuthTokenForDesktop(link),
  })) ?? null
}

export async function desktopWorkspaceTree(options: DesktopWorkspaceTreeOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.workspaceTree?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopWorkspaceGlob(options: DesktopWorkspaceGlobOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.workspaceGlob?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopWorkspaceGrep(options: DesktopWorkspaceGrepOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.workspaceGrep?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopReadFileSnippet(options: DesktopReadSnippetOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.readFileSnippet?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopWriteWorkspacePatch(options: DesktopWritePatchOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.writeWorkspacePatch?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopGitStatus(options: DesktopGitStatusOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.gitStatus?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopGitDiff(options: DesktopGitDiffOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.gitDiff?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopRunReadonlyCommand(options: DesktopReadonlyCommandOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.runReadonlyCommand?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopPrepareApprovalCommand(options: DesktopApprovalCommandOptions, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.prepareApprovalCommand?.(toDesktopOptionsRecord(options)) ?? Promise.resolve(null), link)
}

export async function desktopApproveCommandRequest(options: Record<string, any>, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.approveCommandRequest?.(options) ?? Promise.resolve(null), link)
}

export async function desktopDenyCommandRequest(options: Record<string, any>, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.denyCommandRequest?.(options) ?? Promise.resolve(null), link)
}

export async function desktopExecuteApprovedCommand(options: Record<string, any>, link?: DesktopHarnessLinkOptions) {
  return withOptionalHarnessIngest(getDesktopApi()?.executeApprovedCommand?.(options) ?? Promise.resolve(null), link)
}
