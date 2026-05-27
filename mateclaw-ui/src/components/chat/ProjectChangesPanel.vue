<template>
  <aside class="project-changes-panel" :class="{ 'is-mobile': isMobile }">
    <div class="project-changes-panel__header">
      <div class="project-changes-panel__copy">
        <div class="project-changes-panel__kicker">{{ t('chat.currentProject') }}</div>
        <h3 class="project-changes-panel__title">{{ t('chat.projectChangesTitle') }}</h3>
      </div>
      <button class="project-changes-panel__close" type="button" :title="t('common.close')" @click="$emit('close')">
        <span aria-hidden="true">×</span>
      </button>
    </div>

    <div class="project-changes-panel__body">
      <section class="project-changes-card">
        <div class="project-changes-card__label">{{ t('chat.currentProject') }}</div>
        <div class="project-changes-card__value" :title="projectCardLabel">{{ projectCardLabel }}</div>
        <div v-if="projectCardPath" class="project-changes-card__hint" :title="projectCardPath">{{ projectCardPath }}</div>
        <div v-else-if="workspaceBasePath" class="project-changes-card__hint" :title="workspaceBasePath">{{ workspaceBasePath }}</div>
        <div v-if="desktopActionsAvailable && activeProjectPath" class="project-changes-card__actions">
          <button class="project-changes-action" type="button" @click="handleOpenProject">
            {{ t('chat.projectChangesOpenProject') }}
          </button>
          <button class="project-changes-action project-changes-action--ghost" type="button" @click="handleRevealProject">
            {{ t('chat.projectChangesRevealProject') }}
          </button>
        </div>
      </section>

      <section class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.projectCacheTitle') }}</div>
        <div v-if="loadingProjectInsight" class="project-changes-empty">{{ t('common.loading') }}</div>
        <div v-else-if="projectInsight" class="project-cache-card">
          <div v-if="projectInsight.projectName || projectInsight.relativePath" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheProjectRoot') }}</div>
            <div class="project-cache-values">{{ projectRootLabel }}</div>
          </div>
          <div v-if="projectInsight.workingDirectoryRelativePath" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheWorkingDirectory') }}</div>
            <div class="project-cache-values">{{ workingDirectoryLabel }}</div>
          </div>
          <div v-if="projectInsight.locatorMarkers?.length || projectInsight.locatorType" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheLocator') }}</div>
            <div class="project-cache-badges">
              <span v-if="projectInsight.locatorType" class="project-cache-badge">{{ projectInsight.locatorType }}</span>
              <span v-for="item in projectInsight.locatorMarkers || []" :key="item" class="project-cache-badge">{{ item }}</span>
            </div>
          </div>
          <div v-if="projectInsight.gitRootPath" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheGitRoot') }}</div>
            <div class="project-cache-values">{{ gitRootLabel }}</div>
          </div>
          <div v-if="projectInsight.stackHints?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheStacks') }}</div>
            <div class="project-cache-badges">
              <span v-for="item in projectInsight.stackHints" :key="item" class="project-cache-badge">{{ item }}</span>
            </div>
          </div>
          <div v-if="projectInsight.keyFiles?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheFiles') }}</div>
            <div class="project-cache-values">{{ projectInsight.keyFiles.join(' · ') }}</div>
          </div>
          <div v-if="projectInsight.moduleHints?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheModules') }}</div>
            <div class="project-cache-values">{{ projectInsight.moduleHints.join(' · ') }}</div>
          </div>
          <div v-if="projectInsight.materialIndexHints?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheMaterials') }}</div>
            <div class="project-cache-values project-cache-values--stacked">
              <span v-for="item in projectInsight.materialIndexHints.slice(0, 3)" :key="item">{{ item }}</span>
            </div>
          </div>
          <div v-if="projectInsight.commandHints?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.projectCacheCommands') }}</div>
            <div class="project-cache-command-list">
              <div v-for="command in projectInsight.commandHints" :key="command" class="project-cache-command-chip">
                <code class="project-cache-command">{{ command }}</code>
                <button
                  v-if="desktopActionsAvailable"
                  type="button"
                  class="project-cache-command-run"
                  @click="$emit('run-validation', command)"
                >
                  {{ t('chat.projectCacheRunLocal') }}
                </button>
              </div>
            </div>
            <div v-if="localValidationRequest" class="project-cache-validation-card" :class="`is-${localValidationRequest.status}`">
              <div class="project-cache-validation-card__header">
                <span class="project-cache-validation-card__title">
                  {{ localValidationRequest.status === 'running' ? t('chat.projectCacheValidationRunning') : t('chat.projectCacheValidationPending') }}
                </span>
                <span v-if="localValidationRequest.riskLevel" class="project-cache-badge">{{ localValidationRequest.riskLevel }}</span>
              </div>
              <div class="project-cache-validation-card__command">{{ localValidationRequest.commandText }}</div>
              <div v-if="localValidationRequest.summary" class="project-cache-validation-card__summary">{{ localValidationRequest.summary }}</div>
              <div v-if="localValidationError" class="project-cache-validation-card__error">{{ localValidationError }}</div>
              <div v-if="localValidationRequest.status !== 'running'" class="project-cache-validation-card__actions">
                <button type="button" class="project-changes-action" @click="$emit('approve-local-validation')">
                  {{ t('chat.projectCacheApproveLocal') }}
                </button>
                <button type="button" class="project-changes-action project-changes-action--ghost" @click="$emit('deny-local-validation')">
                  {{ t('chat.projectCacheDenyLocal') }}
                </button>
              </div>
            </div>
            <div v-else-if="localValidationError" class="project-cache-validation-card__error">{{ localValidationError }}</div>
          </div>
          <div class="project-cache-meta">
            <span v-if="projectInsight.packageManager">{{ t('chat.projectCachePackageManager') }}: {{ projectInsight.packageManager }}</span>
            <span v-if="projectInsight.buildSystem">{{ t('chat.projectCacheBuildSystem') }}: {{ projectInsight.buildSystem }}</span>
          </div>
        </div>
        <div v-else class="project-changes-empty">{{ t('chat.projectCacheEmpty') }}</div>
      </section>

      <section class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.contextRouterTitle') }}</div>
        <div v-if="loadingContextRouter" class="project-changes-empty">{{ t('common.loading') }}</div>
        <div v-else-if="contextRouter" class="project-cache-card">
          <div v-if="contextRouter.activeSources?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterSources') }}</div>
            <div class="project-cache-badges">
              <span v-for="source in contextRouter.activeSources" :key="source" class="project-cache-badge">{{ contextSourceLabel(source) }}</span>
            </div>
          </div>
          <div v-if="contextRouter.projectDerivedEnabled || contextRouter.sessionTemporaryEnabled" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterFlags') }}</div>
            <div class="project-cache-badges">
              <span v-if="contextRouter.projectDerivedEnabled" class="project-cache-badge">{{ t('chat.contextRouterProjectDerived') }}</span>
              <span v-if="contextRouter.sessionTemporaryEnabled" class="project-cache-badge">{{ t('chat.contextRouterSessionTemporary') }}</span>
            </div>
          </div>
          <div v-if="contextRouter.templateKnowledgeKeys?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterKnowledgeKeys') }}</div>
            <div class="project-cache-badges">
              <span v-for="item in contextRouter.templateKnowledgeKeys" :key="item" class="project-cache-badge">{{ item }}</span>
            </div>
          </div>
          <div v-if="contextRouter.missingKnowledgeBindings?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterMissingBindings') }}</div>
            <div class="project-cache-values">{{ contextRouter.missingKnowledgeBindings.join(' · ') }}</div>
          </div>
          <div v-if="contextRouter.memoryFiles?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterMemory') }}</div>
            <div class="context-router-list">
              <div v-for="file in contextRouter.memoryFiles" :key="file.filename" class="context-router-item">
                <span class="context-router-item__title">{{ file.filename }}</span>
                <span class="context-router-item__meta">{{ file.enabled ? t('common.enabled') : t('common.disabled') }}</span>
              </div>
            </div>
          </div>
          <div v-if="contextRouter.knowledgeBases?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterWiki') }}</div>
            <div class="context-router-list">
              <div v-for="kb in contextRouter.knowledgeBases" :key="`${kb.id || kb.name}-${kb.externalKey || ''}`" class="context-router-item">
                <span class="context-router-item__title">{{ kb.name }}</span>
                <span class="context-router-item__meta">{{ kb.externalKey || t('chat.contextRouterUnbound') }}</span>
              </div>
            </div>
          </div>
          <div v-if="contextRouter.recentSessions?.length" class="project-cache-row">
            <div class="project-cache-label">{{ t('chat.contextRouterSessions') }}</div>
            <div class="context-router-list">
              <div v-for="session in contextRouter.recentSessions" :key="session.conversationId" class="context-router-item">
                <span class="context-router-item__title">{{ session.title || session.conversationId }}</span>
                <span class="context-router-item__meta">{{ formatContextSessionMeta(session) }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="project-changes-empty">{{ t('chat.contextRouterEmpty') }}</div>
      </section>

      <section v-if="projectChangeStats.length" class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.projectChangesSummary') }}</div>
        <div class="project-changes-stats">
          <span v-for="item in projectChangeStats" :key="item.type" class="project-changes-stats__item" :class="`is-${item.type}`">
            <span>{{ getChangeTypeLabel(item.type) }}</span>
            <strong>{{ item.count }}</strong>
          </span>
        </div>
      </section>

      <section class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.projectChangesFiles') }}</div>
        <div v-if="projectFiles.length" class="project-changes-list">
          <details v-for="file in projectFiles" :key="`${file.changeType}-${file.path}`" class="project-file-row">
            <summary class="project-file-row__summary">
              <span class="project-changes-item__badge" :class="`is-${file.changeType}`">{{ getChangeTypeLabel(file.changeType) }}</span>
              <span class="project-file-row__path" :title="normalizeFilePath(file.path)">{{ normalizeFilePath(file.path) }}</span>
            </summary>
            <div class="project-file-row__body">
              <div v-if="desktopActionsAvailable && resolveTargetPath(file.path)" class="project-changes-item__actions">
                <button class="project-changes-action" type="button" @click.stop="handleOpenFile(file.path)">
                  {{ t('chat.projectChangesOpenFile') }}
                </button>
                <button
                  v-if="isPreviewableProjectFile(file.path)"
                  class="project-changes-action"
                  type="button"
                  @click.stop="handleOpenFile(file.path)"
                >
                  预览
                </button>
                <button class="project-changes-action project-changes-action--ghost" type="button" @click.stop="handleRevealFile(file.path)">
                  {{ t('chat.projectChangesRevealFile') }}
                </button>
              </div>
              <div v-else-if="resolveGeneratedArtifactDownloadUrl(file.path)" class="project-changes-item__actions">
                <button class="project-changes-action" type="button" @click.stop="handleDownloadGeneratedArtifactByPath(file.path)">
                  {{ t('chat.projectChangesDownloadFile') }}
                </button>
              </div>
            </div>
          </details>
        </div>
        <div v-else class="project-changes-empty">{{ t('chat.projectChangesEmpty') }}</div>
      </section>

      <section v-if="latestReplyFiles.length" class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.projectChangesLatestReply') }}</div>
        <div class="project-changes-list">
          <details v-for="file in latestReplyFiles" :key="`latest-${file.path}`" class="project-file-row project-file-row--latest">
            <summary class="project-file-row__summary">
              <span class="project-changes-item__badge" :class="`is-${file.changeType}`">{{ getChangeTypeLabel(file.changeType) }}</span>
              <span class="project-file-row__path" :title="normalizeFilePath(file.path)">{{ normalizeFilePath(file.path) }}</span>
              <span class="project-file-row__meta-inline">{{ latestReplyMetaLabel(file) }}</span>
            </summary>
            <div class="project-file-row__body">
              <div v-if="desktopActionsAvailable && resolveTargetPath(file.path)" class="project-changes-item__actions">
                <button class="project-changes-action" type="button" @click.stop="handleOpenFile(file.path)">
                  {{ t('chat.projectChangesOpenFile') }}
                </button>
                <button
                  v-if="isPreviewableProjectFile(file.path)"
                  class="project-changes-action"
                  type="button"
                  @click.stop="handleOpenFile(file.path)"
                >
                  预览
                </button>
                <button class="project-changes-action project-changes-action--ghost" type="button" @click.stop="handleRevealFile(file.path)">
                  {{ t('chat.projectChangesRevealFile') }}
                </button>
              </div>
              <div v-else-if="resolveGeneratedArtifactDownloadUrl(file.path)" class="project-changes-item__actions">
                <button class="project-changes-action" type="button" @click.stop="handleDownloadGeneratedArtifactByPath(file.path)">
                  {{ t('chat.projectChangesDownloadFile') }}
                </button>
              </div>
              <div class="project-file-row__detail-meta">
                <span>{{ file.toolName }}</span>
                <span v-if="file.bytesWritten">{{ t('chat.reviewBytesWritten', { count: file.bytesWritten }) }}</span>
                <span v-else-if="file.replacements">{{ t('chat.reviewReplacements', { count: file.replacements }) }}</span>
              </div>
              <div v-if="file.summary" class="project-file-row__summary-text">{{ file.summary }}</div>
            </div>
          </details>
        </div>
      </section>

      <section v-if="latestReplyValidations.length" class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.reviewValidationTitle') }}</div>
        <div class="project-changes-list">
          <details v-for="item in latestReplyValidations" :key="`latest-validation-${item.toolName}-${item.command}`" class="project-file-row project-file-row--latest">
            <summary class="project-file-row__summary">
              <span class="project-changes-item__badge" :class="`is-${getValidationStatusTone(item)}`">{{ getValidationStatusLabel(item) }}</span>
              <span class="project-file-row__path" :title="item.command">{{ item.command }}</span>
              <span class="project-file-row__meta-inline">{{ latestReplyValidationMetaLabel(item) }}</span>
            </summary>
            <div class="project-file-row__body">
              <div class="project-file-row__detail-meta">
                <span>{{ item.toolName }}</span>
                <span v-if="typeof item.exitCode === 'number'">exit {{ item.exitCode }}</span>
              </div>
              <div v-if="item.result" class="project-file-row__summary-text">{{ item.result }}</div>
            </div>
          </details>
        </div>
      </section>

      <section v-if="latestGeneratedArtifacts.length" class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.projectChangesGeneratedFiles') }}</div>
        <div class="project-changes-list">
          <details v-for="artifact in latestGeneratedArtifacts" :key="`artifact-${artifact.url || artifact.path || artifact.name}`" class="project-file-row project-file-row--latest">
            <summary class="project-file-row__summary">
              <span class="project-changes-item__badge is-added">AI</span>
              <span class="project-file-row__path" :title="artifactDisplayPath(artifact)">{{ artifactDisplayName(artifact) }}</span>
            </summary>
            <div class="project-file-row__body">
              <div class="project-changes-item__actions">
                <button class="project-changes-action" type="button" :disabled="!artifact.url" @click.stop="handleDownloadGeneratedArtifact(artifact)">
                  {{ t('chat.projectChangesDownloadFile') }}
                </button>
              </div>
              <div class="project-file-row__detail-meta">
                <span v-if="artifact.path">{{ normalizeFilePath(artifact.path) }}</span>
                <span v-if="artifact.mimeType">{{ artifact.mimeType }}</span>
                <span v-if="artifact.source">{{ artifact.source }}</span>
              </div>
            </div>
          </details>
        </div>
      </section>

      <section v-if="recentReviews.length" class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.projectChangesHistory') }}</div>
        <div class="project-review-history">
          <div v-for="review in recentReviews" :key="review.id" class="project-review-history__item">
            <div class="project-review-history__meta">
              <span>{{ review.timeLabel }}</span>
              <span v-if="review.fileCount">{{ t('chat.reviewChangedFilesCount', { count: review.fileCount }) }}</span>
              <span v-if="review.validationCount">{{ t('chat.reviewItemsCount', { count: review.validationCount }) }}</span>
            </div>
            <div class="project-review-history__paths">
              {{ review.preview }}
            </div>
          </div>
        </div>
      </section>

      <section v-if="checkpointCapability" class="project-changes-section">
        <div class="project-changes-section__title">{{ t('chat.reviewCheckpointTitle') }}</div>
        <div class="project-checkpoint" :class="{ 'is-supported': checkpointCapability.supported, 'is-unsupported': !checkpointCapability.supported }">
          <span class="project-checkpoint__badge">
            {{ checkpointCapability.supported ? t('chat.reviewCheckpointSupported') : t('chat.reviewCheckpointUnavailable') }}
          </span>
          <span v-if="checkpointCapability.reason" class="project-checkpoint__reason">{{ checkpointCapability.reason }}</span>
        </div>
      </section>

      <section v-if="internalExecutionAvailable || loadingHarness" class="project-changes-section">
        <details class="internal-execution-panel">
          <summary class="internal-execution-panel__summary">
            <span>{{ t('chat.executionDetailsTitle') }}</span>
            <span v-if="internalExecutionCount" class="internal-execution-panel__count">{{ internalExecutionCount }}</span>
          </summary>

          <div class="internal-execution-panel__body">
            <section class="project-changes-section">
              <div class="project-changes-section__title">{{ t('chat.executionOverviewTitle') }}</div>
              <div v-if="loadingHarness && !harnessRun" class="project-changes-empty">{{ t('common.loading') }}</div>
              <div v-else-if="harnessRun" class="execution-overview">
                <div class="execution-overview__badges">
                  <span class="execution-badge" :class="`is-${executionStatusTone}`">{{ executionStatusLabel }}</span>
                  <span v-if="harnessRun.mode" class="execution-badge is-neutral">{{ harnessRun.mode }}</span>
                  <span v-if="harnessRun.summary?.finishReason" class="execution-badge is-muted">{{ harnessRun.summary.finishReason }}</span>
                </div>
                <div class="execution-overview__meta">
                  <div v-if="harnessRun.agentName"><strong>{{ t('chat.executionAgent') }}:</strong> {{ harnessRun.agentName }}</div>
                  <div v-if="executionRuntimeModel"><strong>{{ t('chat.executionRuntime') }}:</strong> {{ executionRuntimeModel }}</div>
                  <div v-if="executionTokenSummary"><strong>{{ t('chat.executionTokens') }}:</strong> {{ executionTokenSummary }}</div>
                  <div v-if="executionDurationLabel"><strong>{{ t('chat.executionDuration') }}:</strong> {{ executionDurationLabel }}</div>
                  <div v-if="harnessRun.summary?.errorMessage" class="execution-overview__error">
                    <strong>{{ t('chat.executionError') }}:</strong> {{ harnessRun.summary.errorMessage }}
                  </div>
                </div>
              </div>
              <div v-else class="project-changes-empty">{{ t('chat.executionEmpty') }}</div>
            </section>

            <section v-if="timelineItems.length" class="project-changes-section">
              <div class="project-changes-section__title">{{ t('chat.executionTimelineTitle') }}</div>
              <div class="execution-timeline">
                <div v-for="item in timelineItems" :key="item.id" class="execution-timeline__item">
                  <span class="execution-timeline__dot" :class="`is-${item.tone}`"></span>
                  <div class="execution-timeline__body">
                    <div class="execution-timeline__head">
                      <span class="execution-timeline__name">{{ item.label }}</span>
                      <span class="execution-timeline__time">{{ item.time }}</span>
                    </div>
                    <div class="execution-timeline__meta">{{ item.meta }}</div>
                  </div>
                </div>
              </div>
            </section>

            <section v-if="toolActivity.length" class="project-changes-section">
              <div class="project-changes-section__title">{{ t('chat.executionToolsTitle') }}</div>
              <div class="execution-list">
                <div v-for="tool in toolActivity" :key="tool.id" class="execution-list__item">
                  <div class="execution-list__head">
                    <span class="execution-badge" :class="`is-${tool.tone}`">{{ tool.statusLabel }}</span>
                    <span class="execution-list__name">{{ tool.toolName }}</span>
                    <span class="execution-list__time">{{ tool.time }}</span>
                  </div>
                  <div v-if="tool.detail" class="execution-list__detail">{{ tool.detail }}</div>
                  <pre v-if="tool.resultPreview" class="execution-list__result">{{ tool.resultPreview }}</pre>
                </div>
              </div>
            </section>

            <section v-if="approvalItems.length" class="project-changes-section">
              <div class="project-changes-section__title">{{ t('chat.executionApprovalsTitle') }}</div>
              <div class="execution-list">
                <div v-for="approval in approvalItems" :key="approval.id" class="execution-list__item">
                  <div class="execution-list__head">
                    <span class="execution-badge" :class="`is-${approval.tone}`">{{ approval.statusLabel }}</span>
                    <span class="execution-list__name">{{ approval.toolName }}</span>
                    <span class="execution-list__time">{{ approval.time }}</span>
                  </div>
                  <div class="execution-list__detail">{{ approval.detail }}</div>
                </div>
              </div>
            </section>

            <section v-if="commandResults.length" class="project-changes-section">
              <div class="project-changes-section__title">{{ t('chat.executionTestsTitle') }}</div>
              <div class="execution-list">
                <div v-for="result in commandResults" :key="result.id" class="execution-list__item">
                  <div class="execution-list__head">
                    <span class="execution-badge" :class="`is-${result.tone}`">{{ result.kindLabel }}</span>
                    <span class="execution-list__name">{{ result.toolName }}</span>
                    <span class="execution-list__time">{{ result.time }}</span>
                  </div>
                  <div class="execution-list__detail">{{ result.command }}</div>
                  <pre v-if="result.resultPreview" class="execution-list__result">{{ result.resultPreview }}</pre>
                </div>
              </div>
            </section>
          </div>
        </details>
      </section>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { isDesktopRuntime, openDesktopPath, revealDesktopPath } from '@/utils/desktop'
import type { CheckpointCapability, ContextRouterSessionSummary, ContextRouterSummary, FileChangeRecord, GeneratedArtifactRecord, HarnessApproval, HarnessRun, HarnessStep, HarnessToolInvocation, Message, ProjectChangeRecord, ProjectInsightSummary, ReviewSummary, ReviewValidationRecord } from '@/types'

interface Props {
  messages: Message[]
  harnessRun?: HarnessRun | null
  loadingHarness?: boolean
  projectInsight?: ProjectInsightSummary | null
  loadingProjectInsight?: boolean
  contextRouter?: ContextRouterSummary | null
  loadingContextRouter?: boolean
  projectLabel: string
  projectPath?: string
  workspaceBasePath?: string
  localValidationRequest?: {
    requestId: string
    commandText: string
    summary: string
    riskLevel?: string
    expiresAt?: number
    status: 'pending' | 'running'
  } | null
  localValidationError?: string
  isMobile?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  harnessRun: null,
  loadingHarness: false,
  projectInsight: null,
  loadingProjectInsight: false,
  contextRouter: null,
  loadingContextRouter: false,
  projectPath: '',
  workspaceBasePath: '',
  localValidationRequest: null,
  localValidationError: '',
  isMobile: false,
})

defineEmits<{
  close: []
  'run-validation': [command: string]
  'approve-local-validation': []
  'deny-local-validation': []
}>()

const { t } = useI18n()
const desktopActionsAvailable = isDesktopRuntime()

const normalizeFilePath = (path: string) => path?.replace(/\\/g, '/') || ''

const activeProjectPath = computed(() => props.projectInsight?.rootPath?.trim() || props.projectPath?.trim() || props.workspaceBasePath?.trim() || '')
const projectCardLabel = computed(() => props.projectInsight?.projectName?.trim() || props.projectLabel)
const projectCardPath = computed(() => props.projectInsight?.rootPath?.trim() || props.projectPath?.trim() || '')
const projectRootLabel = computed(() => {
  if (!props.projectInsight) return ''
  return props.projectInsight.relativePath?.trim() || props.projectInsight.rootPath || props.projectInsight.projectName || ''
})
const workingDirectoryLabel = computed(() => {
  const relative = props.projectInsight?.workingDirectoryRelativePath?.trim() || ''
  return relative || t('chat.workspaceRoot')
})
const gitRootLabel = computed(() => {
  if (!props.projectInsight?.gitRootPath) return ''
  const relative = props.projectInsight.gitRootRelativePath?.trim() || ''
  return relative ? `${props.projectInsight.gitRootPath} · ${relative}` : props.projectInsight.gitRootPath
})

function isAbsolutePath(path: string) {
  return /^[a-zA-Z]:[\\/]/.test(path) || path.startsWith('\\\\') || path.startsWith('/')
}

function resolveTargetPath(rawPath?: string) {
  const target = rawPath?.trim() || ''
  if (!target) return ''
  if (isAbsolutePath(target)) return target
  const base = activeProjectPath.value
  if (!base) return ''
  const normalizedBase = base.replace(/[\\/]+$/, '')
  const separator = normalizedBase.includes('\\') ? '\\' : '/'
  const normalizedTarget = target.replace(/^([\\/]+)/, '').replace(/[\\/]+/g, separator)
  return `${normalizedBase}${separator}${normalizedTarget}`
}

async function executeDesktopAction(targetPath: string, action: 'open' | 'reveal') {
  if (!desktopActionsAvailable || !targetPath) return
  try {
    if (action === 'open') {
      await openDesktopPath(targetPath)
      return
    }
    await revealDesktopPath(targetPath)
  } catch (error) {
    console.error('[ProjectChangesPanel] Desktop action failed:', error)
    ElMessage.error(t('chat.projectChangesOpenFailed'))
  }
}

function handleOpenProject() {
  void executeDesktopAction(activeProjectPath.value, 'open')
}

function handleRevealProject() {
  void executeDesktopAction(activeProjectPath.value, 'reveal')
}

function handleOpenFile(filePath: string) {
  void executeDesktopAction(resolveTargetPath(filePath), 'open')
}

function handleRevealFile(filePath: string) {
  void executeDesktopAction(resolveTargetPath(filePath), 'reveal')
}

function isPreviewableProjectFile(filePath?: string) {
  return /\.(html?|md|txt|json|csv|log)$/i.test(filePath || '')
}

function resolveArtifactUrl(url: string) {
  const trimmed = String(url || '').trim()
  if (!trimmed) return ''
  if (/^https?:\/\//i.test(trimmed)) return trimmed
  if (trimmed.startsWith('/')) return `${window.location.origin}${trimmed}`
  return trimmed
}

function artifactDisplayName(artifact: GeneratedArtifactRecord) {
  const explicit = String(artifact.name || '').trim()
  if (explicit) return explicit
  if (artifact.path) return normalizeFilePath(artifact.path)
  return normalizeFilePath(String(artifact.url || 'generated-file'))
}

function artifactDisplayPath(artifact: GeneratedArtifactRecord) {
  return normalizeFilePath(String(artifact.path || artifact.url || artifact.name || ''))
}

function handleDownloadGeneratedArtifact(artifact: GeneratedArtifactRecord) {
  const rawUrl = resolveArtifactUrl(String(artifact.url || ''))
  if (!rawUrl) {
    ElMessage.error(t('chat.downloadFailed'))
    return
  }
  const filename = artifactDisplayName(artifact)
  const isPreviewable = /\.(html?|xhtml|txt|md|json|csv|log)$/i.test(filename)
  const baseUrl = rawUrl.replace(/\/inline$/, '')
  const link = document.createElement('a')
  if (isPreviewable) {
    link.href = `${baseUrl}/inline`
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
  } else {
    link.href = baseUrl
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    link.download = filename
  }
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const generatedArtifactDownloadByPath = computed(() => {
  const map = new Map<string, string>()

  function tryCollect(rawResult: unknown) {
    if (!rawResult) return
    try {
      const parsed = typeof rawResult === 'string' ? JSON.parse(rawResult) : rawResult
      if (parsed && typeof parsed === 'object' && (parsed as any).apiUrl && (parsed as any).filePath && !(parsed as any).error) {
        const key = normalizeFilePath(String((parsed as any).filePath)).toLowerCase()
        if (key) map.set(key, String((parsed as any).apiUrl))
      }
    } catch {
      // ignore non-JSON tool results
    }
  }

  // 1. From ReviewSummary.generatedArtifacts (harness-based artifacts)
  const artifacts = latestReviewSummary.value?.generatedArtifacts
  if (Array.isArray(artifacts)) {
    for (const artifact of artifacts) {
      if (!artifact?.url || !artifact.path) continue
      const key = normalizeFilePath(String(artifact.path)).toLowerCase()
      if (!key) continue
      map.set(key, String(artifact.url))
    }
  }

  // 2. From tool call results in messages (apiUrl returned by export/write tools)
  for (const msg of props.messages) {
    const rawMeta = msg?.metadata
    let meta: any = null
    try { meta = typeof rawMeta === 'string' ? JSON.parse(rawMeta) : rawMeta } catch { /* ignore */ }
    const toolCalls: any[] = Array.isArray(meta?.toolCalls) ? meta.toolCalls : []
    for (const tc of toolCalls) {
      tryCollect(tc?.result)
    }
  }

  return map
})

function resolveGeneratedArtifactDownloadUrl(filePath?: string) {
  const normalizedPath = normalizeFilePath(String(filePath || '')).toLowerCase()
  if (!normalizedPath) return ''
  const direct = generatedArtifactDownloadByPath.value.get(normalizedPath)
  if (direct) return direct
  for (const [key, url] of generatedArtifactDownloadByPath.value.entries()) {
    if (key.endsWith(normalizedPath) || normalizedPath.endsWith(key)) {
      return url
    }
  }
  return ''
}

function handleDownloadGeneratedArtifactByPath(filePath?: string) {
  const url = resolveGeneratedArtifactDownloadUrl(filePath)
  if (!url) {
    ElMessage.error(t('chat.downloadFailed'))
    return
  }
  handleDownloadGeneratedArtifact({
    name: normalizeFilePath(String(filePath || 'generated-file')),
    path: filePath,
    url,
  })
}

const getChangeTypeLabel = (changeType?: string) => {
  switch (changeType) {
    case 'added':
      return t('chat.reviewAdded')
    case 'deleted':
      return t('chat.reviewDeleted')
    case 'renamed':
      return t('chat.reviewRenamed')
    case 'untracked':
      return t('chat.reviewUntracked')
    default:
      return t('chat.reviewModified')
  }
}

function safeParseMeta(metadata: unknown): Record<string, any> {
  if (!metadata) return {}
  if (typeof metadata === 'object') return metadata as Record<string, any>
  if (typeof metadata === 'string') {
    try {
      let parsed: any = JSON.parse(metadata)
      if (typeof parsed === 'string') {
        parsed = JSON.parse(parsed)
      }
      return typeof parsed === 'object' && parsed !== null ? parsed : {}
    } catch {
      return {}
    }
  }
  return {}
}

const reviewEntries = computed(() => {
  return [...props.messages]
    .filter(message => message.role === 'assistant')
    .map((message, index) => {
      const meta = safeParseMeta(message.metadata)
      const reviewSummary = meta.reviewSummary as ReviewSummary | undefined
      return {
        id: String(message.id || `assistant-${index}`),
        message,
        reviewSummary,
      }
    })
    .filter(entry => entry.reviewSummary)
})

const latestReviewSummary = computed<ReviewSummary | undefined>(() => {
  for (let i = reviewEntries.value.length - 1; i >= 0; i--) {
    const review = reviewEntries.value[i].reviewSummary
    if (review) return review
  }
  return undefined
})

const executionStatusTone = computed(() => {
  switch ((props.harnessRun?.status || '').toUpperCase()) {
    case 'COMPLETED':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'INTERRUPTED':
      return 'warning'
    default:
      return 'running'
  }
})

const executionStatusLabel = computed(() => {
  switch ((props.harnessRun?.status || '').toUpperCase()) {
    case 'COMPLETED':
      return t('chat.executionStatusCompleted')
    case 'FAILED':
      return t('chat.executionStatusFailed')
    case 'INTERRUPTED':
      return t('chat.executionStatusInterrupted')
    default:
      return t('chat.executionStatusRunning')
  }
})

const executionRuntimeModel = computed(() => {
  const summary = props.harnessRun?.summary
  const model = summary?.runtimeModelName?.trim()
  const provider = summary?.runtimeProviderId?.trim()
  if (model && provider) return `${provider} / ${model}`
  return model || provider || ''
})

const executionTokenSummary = computed(() => {
  const prompt = props.harnessRun?.summary?.promptTokens || 0
  const completion = props.harnessRun?.summary?.completionTokens || 0
  if (!prompt && !completion) return ''
  return `${prompt} + ${completion} = ${prompt + completion}`
})

const executionDurationLabel = computed(() => {
  const started = props.harnessRun?.startedAt ? new Date(props.harnessRun.startedAt).getTime() : 0
  const completed = props.harnessRun?.completedAt ? new Date(props.harnessRun.completedAt).getTime() : 0
  if (!started) return ''
  const durationMs = Math.max(0, (completed || Date.now()) - started)
  if (durationMs < 1000) return `${durationMs} ms`
  const seconds = durationMs / 1000
  if (seconds < 60) return `${seconds.toFixed(seconds >= 10 ? 0 : 1)} s`
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = Math.round(seconds % 60)
  return `${minutes}m ${remainSeconds}s`
})

function formatDateTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function contextSourceLabel(source: string) {
  switch (source) {
    case 'project_cache':
      return t('chat.contextRouterSourceProject')
    case 'workspace_memory':
      return t('chat.contextRouterSourceMemory')
    case 'wiki':
      return t('chat.contextRouterSourceWiki')
    case 'session_search':
      return t('chat.contextRouterSourceSession')
    default:
      return source
  }
}

function formatContextSessionMeta(session: ContextRouterSessionSummary) {
  const parts: string[] = []
  if (typeof session.messageCount === 'number') {
    parts.push(t('chat.messages', { count: session.messageCount }))
  }
  const time = formatDateTime(session.lastActiveTime)
  if (time) {
    parts.push(time)
  }
  return parts.join(' · ')
}

function formatPhaseLabel(step: HarnessStep) {
  if (step.phase === 'planning' || step.name === 'plan_created') return t('chat.executionPlanning')
  if (step.phase === 'plan') return t('chat.executionPlanStep')
  if (step.phase === 'lifecycle') return t('chat.executionLifecycle')
  if (step.phase === 'phase') return step.name
  return step.name || step.phase || t('chat.executionStep')
}

const timelineItems = computed(() => {
  const steps = Array.isArray(props.harnessRun?.steps) ? props.harnessRun.steps : []
  return steps
    .map((step, index) => {
      const status = (step.status || '').toLowerCase()
      const tone = status === 'completed'
        ? 'success'
        : status === 'failed'
          ? 'danger'
          : status === 'running'
            ? 'running'
            : 'warning'
      return {
        id: step.id || `step-${index}`,
        label: formatPhaseLabel(step),
        meta: [step.phase, step.status].filter(Boolean).join(' · '),
        time: formatDateTime(step.completedAt || step.startedAt),
        tone,
      }
    })
    .slice(-10)
    .reverse()
})

function stringifyValue(value: unknown) {
  if (value == null) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function truncateText(value: string, max = 220) {
  if (!value) return ''
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function resolveToolResult(invocation: HarnessToolInvocation) {
  return stringifyValue(invocation.metadata?.result || invocation.metadata?.directResult || '')
}

function toNumericValue(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : undefined
}

function extractPatchedFileChange(invocation: HarnessToolInvocation): FileChangeRecord | null {
  const toolName = String(invocation.toolName || '').trim()
  if (toolName !== 'workspace.write_patch') {
    return null
  }
  const metadata = invocation.metadata || {}
  const filePath = String(metadata.filePath || '').trim()
  if (!filePath) {
    return null
  }
  const operationsApplied = Array.isArray(metadata.operationsApplied)
    ? metadata.operationsApplied.length
    : toNumericValue(metadata.operationsApplied)
  const bytesWritten = toNumericValue(metadata.afterLength)
  return {
    path: filePath,
    changeType: metadata.created === true ? 'added' : 'modified',
    toolName,
    summary: typeof metadata.summary === 'string' ? metadata.summary : undefined,
    bytesWritten,
    replacements: operationsApplied,
    timestamp: invocation.completedAt ? new Date(invocation.completedAt).getTime() : undefined,
  }
}

function resolveToolDetail(invocation: HarnessToolInvocation) {
  const parts = [
    invocation.metadata?.arguments ? truncateText(String(invocation.metadata.arguments), 120) : '',
    invocation.riskLevel || '',
  ].filter(Boolean)
  return parts.join(' · ')
}

const toolActivity = computed(() => {
  const tools = Array.isArray(props.harnessRun?.toolInvocations) ? props.harnessRun.toolInvocations : []
  return tools
    .map((tool, index) => {
      const status = (tool.status || '').toLowerCase()
      const tone = status === 'completed'
        ? 'success'
        : status === 'failed'
          ? 'danger'
          : status === 'awaiting_approval'
            ? 'warning'
            : 'running'
      return {
        id: tool.id || `tool-${index}`,
        toolName: tool.toolName,
        statusLabel: status || t('chat.executionStatusRunning'),
        tone,
        time: formatDateTime(tool.completedAt || tool.startedAt),
        detail: resolveToolDetail(tool),
        resultPreview: truncateText(resolveToolResult(tool), 320),
      }
    })
    .slice(-8)
    .reverse()
})

function resolveApprovalToolName(approval: HarnessApproval) {
  const linked = (props.harnessRun?.toolInvocations || []).find(tool => tool.id === approval.toolInvocationId)
  return linked?.toolName || String(approval.metadata?.toolName || t('chat.executionApprovalUnknown'))
}

const approvalItems = computed(() => {
  const approvals = Array.isArray(props.harnessRun?.approvals) ? props.harnessRun.approvals : []
  return approvals
    .map((approval, index) => {
      const status = (approval.status || '').toLowerCase()
      const tone = status === 'approved'
        ? 'success'
        : status === 'denied'
          ? 'danger'
          : 'warning'
      const detailParts = [approval.scope, approval.requestedBy, approval.resolvedBy].filter(Boolean)
      return {
        id: approval.id || `approval-${index}`,
        toolName: resolveApprovalToolName(approval),
        statusLabel: status || t('chat.executionApprovalPending'),
        tone,
        time: formatDateTime(approval.resolvedAt || approval.requestedAt),
        detail: detailParts.join(' · '),
      }
    })
    .reverse()
})

const COMMAND_TOOL_PATTERN = /(terminal|shell|command|task|snippet)/i
const TEST_COMMAND_PATTERN = /\b(test|pytest|jest|vitest|mocha|mvn test|gradle test|go test|cargo test|pnpm test|npm test|unittest)\b/i
const FAILURE_PATTERN = /\b(fail(?:ed|ure)?|error|exception|traceback|not ok|panic)\b/i
const SUCCESS_PATTERN = /\b(pass(?:ed)?|success|ok|completed|finished)\b/i

function resolveCommandText(invocation: HarnessToolInvocation) {
  return String(invocation.metadata?.arguments || invocation.metadata?.command || invocation.toolName || '')
}

function resolveCommandTone(result: string, invocation: HarnessToolInvocation) {
  if (invocation.status === 'failed' || FAILURE_PATTERN.test(result)) return 'danger'
  if (SUCCESS_PATTERN.test(result) || invocation.status === 'completed') return 'success'
  return 'warning'
}

const commandResults = computed(() => {
  const tools = Array.isArray(props.harnessRun?.toolInvocations) ? props.harnessRun.toolInvocations : []
  return tools
    .filter(tool => COMMAND_TOOL_PATTERN.test(tool.toolName || '') || COMMAND_TOOL_PATTERN.test(resolveCommandText(tool)))
    .map((tool, index) => {
      const command = truncateText(resolveCommandText(tool), 160)
      const result = resolveToolResult(tool)
      const isTest = TEST_COMMAND_PATTERN.test(command) || TEST_COMMAND_PATTERN.test(result)
      return {
        id: `${tool.id || index}-cmd`,
        toolName: tool.toolName,
        kindLabel: isTest ? t('chat.executionTestResult') : t('chat.executionCommandResult'),
        tone: resolveCommandTone(result, tool),
        command,
        time: formatDateTime(tool.completedAt || tool.startedAt),
        resultPreview: truncateText(result, 360),
      }
    })
    .slice(-6)
    .reverse()
})

const internalExecutionAvailable = computed(() =>
  Boolean(props.harnessRun)
  || timelineItems.value.length > 0
  || toolActivity.value.length > 0
  || approvalItems.value.length > 0
  || commandResults.value.length > 0
)

const internalExecutionCount = computed(() => {
  let count = 0
  if (timelineItems.value.length) count += 1
  if (toolActivity.value.length) count += 1
  if (approvalItems.value.length) count += 1
  if (commandResults.value.length) count += 1
  if (props.harnessRun) count += 1
  return count
})

function latestReplyMetaLabel(file: FileChangeRecord) {
  if (file.bytesWritten) return t('chat.reviewBytesWritten', { count: file.bytesWritten })
  if (file.replacements) return t('chat.reviewReplacements', { count: file.replacements })
  return file.toolName || ''
}

function extractValidationRecord(invocation: HarnessToolInvocation): ReviewValidationRecord | null {
  const toolName = String(invocation.toolName || '').trim()
  const metadata = invocation.metadata || {}
  const command = String(metadata.arguments || metadata.command || '').trim()
  if (!toolName || !command) {
    return null
  }
  const normalizedToolName = toolName.toLowerCase()
  const normalizedCommand = command.toLowerCase()
  const validationLike = normalizedToolName === 'command.run.approval.execute'
    || /command\.run|test|lint|build|check|verify/.test(normalizedToolName)
    || /(^|\s)(test|lint|build|check|verify|pytest|jest|vitest)(\s|$)/.test(normalizedCommand)
  if (!validationLike) {
    return null
  }

  const sharedPayload = metadata.sharedPayload || {}
  const evidence = typeof sharedPayload === 'object' && sharedPayload !== null
    ? (sharedPayload as Record<string, any>).evidence || {}
    : {}
  const numericExitCode = toNumericValue(evidence.exitCode)
  const statusText = String(invocation.status || '').toLowerCase()
  const status: ReviewValidationRecord['status'] = numericExitCode === 0
    ? 'passed'
    : typeof numericExitCode === 'number'
      ? 'failed'
      : statusText === 'running'
        ? 'running'
        : statusText === 'awaiting_approval'
          ? 'pending'
          : statusText === 'approved'
            ? 'approved'
            : statusText === 'failed'
              ? 'failed'
              : 'completed'

  return {
    command,
    toolName,
    status,
    result: typeof metadata.result === 'string'
      ? metadata.result
      : typeof metadata.summary === 'string'
        ? metadata.summary
        : undefined,
    exitCode: numericExitCode,
    timestamp: invocation.completedAt ? new Date(invocation.completedAt).getTime() : undefined,
  }
}

function getValidationStatusTone(item: ReviewValidationRecord) {
  switch (item.status) {
    case 'passed':
    case 'approved':
    case 'completed':
      return 'success'
    case 'failed':
    case 'denied':
      return 'danger'
    default:
      return 'warning'
  }
}

function getValidationStatusLabel(item: ReviewValidationRecord) {
  switch (item.status) {
    case 'passed':
      return t('chat.reviewValidationPassed')
    case 'failed':
      return t('chat.reviewValidationFailed')
    case 'running':
      return t('chat.reviewValidationRunning')
    case 'pending':
      return t('chat.reviewValidationPending')
    case 'approved':
      return t('chat.reviewValidationApproved')
    case 'denied':
      return t('chat.reviewValidationDenied')
    default:
      return t('chat.reviewValidationCompleted')
  }
}

function latestReplyValidationMetaLabel(item: ReviewValidationRecord) {
  if (typeof item.exitCode === 'number') {
    return `exit ${item.exitCode}`
  }
  return item.toolName || ''
}

const checkpointCapability = computed<CheckpointCapability | undefined>(() => latestReviewSummary.value?.checkpointCapability)

const latestReplyFiles = computed<FileChangeRecord[]>(() => {
  const files = latestReviewSummary.value?.files
  if (Array.isArray(files) && files.length > 0) {
    return files
  }
  const tools = Array.isArray(props.harnessRun?.toolInvocations) ? props.harnessRun.toolInvocations : []
  return tools
    .map(extractPatchedFileChange)
    .filter((file): file is FileChangeRecord => Boolean(file))
    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
})

const latestReplyValidations = computed<ReviewValidationRecord[]>(() => {
  const validations = latestReviewSummary.value?.validations
  if (Array.isArray(validations) && validations.length > 0) {
    return validations
  }
  const tools = Array.isArray(props.harnessRun?.toolInvocations) ? props.harnessRun.toolInvocations : []
  const latestByKey = new Map<string, ReviewValidationRecord>()
  for (const tool of tools) {
    const record = extractValidationRecord(tool)
    if (!record) continue
    latestByKey.set(`${record.toolName}::${record.command}`, record)
  }
  return Array.from(latestByKey.values())
    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
    .slice(0, 6)
})

const latestGeneratedArtifacts = computed<GeneratedArtifactRecord[]>(() => {
  const dedup = new Map<string, GeneratedArtifactRecord>()

  function tryCollect(rawResult: unknown) {
    if (!rawResult) return
    try {
      const parsed = typeof rawResult === 'string' ? JSON.parse(rawResult) : rawResult
      if (parsed && typeof parsed === 'object' && (parsed as any).apiUrl && (parsed as any).filename && !(parsed as any).error) {
        const key = String((parsed as any).apiUrl)
        dedup.set(key, {
          name: String((parsed as any).filename),
          path: (parsed as any).filePath,
          url: String((parsed as any).apiUrl),
        })
      }
    } catch {
      // ignore non-JSON tool results
    }
  }

  // 1. From ReviewSummary.generatedArtifacts
  const artifacts = latestReviewSummary.value?.generatedArtifacts
  if (Array.isArray(artifacts)) {
    for (const artifact of artifacts) {
      if (!artifact) continue
      const key = String(artifact.url || artifact.path || artifact.name || '').trim()
      if (!key) continue
      dedup.set(key, artifact)
    }
  }

  // 2. From export/write tool results in the latest few assistant messages
  const recentMessages = [...props.messages].reverse().slice(0, 10)
  for (const msg of recentMessages) {
    const rawMeta = msg?.metadata
    let meta: any = null
    try { meta = typeof rawMeta === 'string' ? JSON.parse(rawMeta) : rawMeta } catch { /* ignore */ }
    const toolCalls: any[] = Array.isArray(meta?.toolCalls) ? meta.toolCalls : []
    for (const tc of toolCalls) {
      tryCollect(tc?.result)
    }
  }

  return Array.from(dedup.values())
})

const projectFiles = computed<Array<ProjectChangeRecord | FileChangeRecord>>(() => {
  const insightChanges = props.projectInsight?.changedFiles
  if (Array.isArray(insightChanges) && insightChanges.length > 0) {
    return [...insightChanges].sort((a, b) => normalizeFilePath(a.path).localeCompare(normalizeFilePath(b.path)))
  }

  const snapshot = latestReviewSummary.value?.projectChangedFiles
  if (Array.isArray(snapshot) && snapshot.length > 0) {
    return [...snapshot].sort((a, b) => normalizeFilePath(a.path).localeCompare(normalizeFilePath(b.path)))
  }

  const latestByPath = new Map<string, FileChangeRecord>()
  for (const entry of reviewEntries.value) {
    const files = Array.isArray(entry.reviewSummary?.files) ? entry.reviewSummary!.files : []
    for (const file of files) {
      if (!file?.path) continue
      latestByPath.set(file.path, file)
    }
  }
  const tools = Array.isArray(props.harnessRun?.toolInvocations) ? props.harnessRun.toolInvocations : []
  for (const tool of tools) {
    const file = extractPatchedFileChange(tool)
    if (!file?.path) continue
    latestByPath.set(file.path, file)
  }
  return Array.from(latestByPath.values())
    .sort((a, b) => normalizeFilePath(a.path).localeCompare(normalizeFilePath(b.path)))
})

const projectChangeStats = computed<Array<{ type: string; count: number }>>(() => {
  const counts = new Map<string, number>()
  for (const file of projectFiles.value) {
    const type = file.changeType || 'modified'
    counts.set(type, (counts.get(type) || 0) + 1)
  }
  const order = ['added', 'modified', 'deleted', 'renamed', 'untracked']
  return Array.from(counts.entries())
    .map(([type, count]) => ({ type, count }))
    .sort((a, b) => order.indexOf(a.type) - order.indexOf(b.type))
})

const recentReviews = computed(() => {
  return [...reviewEntries.value]
    .reverse()
    .filter(entry => {
      const fileCount = Array.isArray(entry.reviewSummary?.files) ? entry.reviewSummary!.files.length : 0
      const validationCount = Array.isArray(entry.reviewSummary?.validations) ? entry.reviewSummary!.validations!.length : 0
      return fileCount > 0 || validationCount > 0
    })
    .slice(0, 6)
    .map(entry => {
      const files = Array.isArray(entry.reviewSummary!.files) ? entry.reviewSummary!.files : []
      const validations = Array.isArray(entry.reviewSummary!.validations) ? entry.reviewSummary!.validations : []
      const filePreview = files.slice(0, 2).map(file => normalizeFilePath(file.path))
      const validationPreview = validations.slice(0, Math.max(0, 2 - filePreview.length)).map(item => item.command)
      const previewItems = [...filePreview, ...validationPreview].filter(Boolean)
      const extraCount = Math.max(0, files.length + validations.length - previewItems.length)
      const timeLabel = entry.message.createTime
        ? new Date(entry.message.createTime).toLocaleString()
        : t('chat.projectChangesRecentUnknown')
      return {
        id: entry.id,
        count: files.length + validations.length,
        fileCount: files.length,
        validationCount: validations.length,
        preview: `${previewItems.join(' · ')}${extraCount > 0 ? ` +${extraCount}` : ''}`,
        timeLabel,
      }
    })
})
</script>

<style scoped>
.project-changes-panel {
  width: 340px;
  min-width: 340px;
  border-left: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: linear-gradient(180deg, var(--mc-panel-top, #fff), var(--mc-panel-bottom, #f8fafc));
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.project-changes-panel.is-mobile {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 120;
  width: min(92vw, 360px);
  min-width: min(92vw, 360px);
  box-shadow: -8px 0 24px rgba(15, 23, 42, 0.14);
}

.project-changes-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
}

.project-changes-panel__copy {
  min-width: 0;
}

.project-changes-panel__kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--mc-accent, #7c3f1e);
  margin-bottom: 4px;
}

.project-changes-panel__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--mc-text-primary, #1e293b);
}

.project-changes-panel__close {
  width: 28px;
  height: 28px;
  border: 1px solid var(--mc-border, rgba(148, 163, 184, 0.24));
  border-radius: 10px;
  background: var(--mc-panel-raised, #fff);
  color: var(--mc-text-secondary, #64748b);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
}

.project-changes-panel__body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.project-changes-card,
.project-checkpoint,
.project-changes-item,
.project-review-history__item {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: var(--mc-bg-elevated, #f8fafc);
}

.project-changes-card__label,
.project-changes-section__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--mc-text-secondary, #64748b);
}

.project-changes-card__value {
  margin-top: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
  word-break: break-word;
}

.project-changes-card__hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
  word-break: break-word;
}

.project-cache-card {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: var(--mc-bg-elevated, #f8fafc);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.project-cache-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.project-cache-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--mc-text-secondary, #64748b);
}

.project-cache-badges,
.project-cache-command-list,
.project-cache-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.project-cache-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(217, 119, 87, 0.1);
  color: var(--mc-primary, #d97757);
  font-size: 11px;
  font-weight: 700;
}

.project-cache-values,
.project-cache-meta {
  font-size: 12px;
  line-height: 1.5;
  color: var(--mc-text-secondary, #64748b);
}

.project-cache-values--stacked {
  display: grid;
  gap: 4px;
}

.project-cache-command {
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 8px;
  background: var(--mc-panel-raised, #fff);
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  color: var(--mc-text-primary, #1e293b);
}

.project-cache-command-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.project-cache-command-run {
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: var(--mc-panel-raised, #fff);
  color: var(--mc-primary, #d97757);
  border-radius: 8px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.project-cache-validation-card {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: var(--mc-panel-raised, #fff);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-cache-validation-card.is-running {
  border-color: rgba(217, 119, 87, 0.3);
}

.project-cache-validation-card__header,
.project-cache-validation-card__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.project-cache-validation-card__title {
  font-size: 12px;
  font-weight: 700;
  color: var(--mc-text-primary, #1e293b);
}

.project-cache-validation-card__command {
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  color: var(--mc-text-primary, #1e293b);
  word-break: break-all;
}

.project-cache-validation-card__summary {
  font-size: 12px;
  color: var(--mc-text-secondary, #64748b);
}

.project-cache-validation-card__error {
  margin-top: 8px;
  font-size: 12px;
  color: var(--mc-danger, #dc2626);
}

.context-router-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.context-router-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--mc-panel-raised, #fff);
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
}

.context-router-item__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
  word-break: break-word;
}

.context-router-item__meta {
  font-size: 11px;
  color: var(--mc-text-secondary, #64748b);
  text-align: right;
}

.project-changes-card__actions,
.project-changes-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.project-changes-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-changes-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.project-changes-stats__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--mc-panel-raised, #fff);
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  font-size: 12px;
  color: var(--mc-text-secondary, #64748b);
}

.project-changes-stats__item strong {
  color: var(--mc-text-primary, #1e293b);
}

.project-changes-list,
.project-review-history {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-file-row {
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  border-radius: 12px;
  background: var(--mc-bg-elevated, #f8fafc);
}

.project-file-row[open] {
  background: var(--mc-panel-raised, #fff);
}

.project-file-row__summary {
  list-style: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
}

.project-file-row__summary::-webkit-details-marker {
  display: none;
}

.project-file-row__path {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
  color: var(--mc-text-primary, #1e293b);
}

.project-file-row__meta-inline {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.project-file-row__body {
  padding: 0 12px 12px;
}

.project-file-row__detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.project-file-row__summary-text {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mc-text-secondary, #64748b);
}

.project-changes-item__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.project-changes-action {
  border: 1px solid rgba(217, 119, 87, 0.22);
  background: rgba(217, 119, 87, 0.1);
  color: var(--mc-primary, #d97757);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.project-changes-action--ghost {
  background: transparent;
  color: var(--mc-text-secondary, #64748b);
  border-color: var(--mc-border-light, rgba(148, 163, 184, 0.22));
}

.project-changes-item__badge,
.project-checkpoint__badge {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.project-changes-item__badge.is-added,
.project-changes-stats__item.is-added {
  border-color: rgba(16, 185, 129, 0.18);
}

.project-changes-item__badge.is-added {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.project-changes-item__badge.is-modified {
  color: var(--mc-primary, #d97757);
  background: rgba(217, 119, 87, 0.1);
}

.project-changes-item__badge.is-success {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.project-changes-item__badge.is-danger {
  color: var(--mc-danger, #ef4444);
  background: rgba(239, 68, 68, 0.1);
}

.project-changes-item__badge.is-warning {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.project-changes-item__badge.is-deleted {
  color: var(--mc-danger, #ef4444);
  background: rgba(239, 68, 68, 0.1);
}

.project-changes-item__badge.is-renamed {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.project-changes-item__badge.is-untracked {
  color: var(--mc-text-secondary, #64748b);
  background: rgba(148, 163, 184, 0.12);
}

.project-changes-item__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
  color: var(--mc-text-primary, #1e293b);
}

.project-changes-item__meta,
.project-review-history__meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.project-changes-item__summary,
.project-review-history__paths,
.project-checkpoint__reason {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mc-text-secondary, #64748b);
}

.project-changes-empty {
  padding: 12px;
  border-radius: 12px;
  border: 1px dashed var(--mc-border-light, rgba(148, 163, 184, 0.22));
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
  text-align: center;
}

.project-checkpoint.is-supported .project-checkpoint__badge {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.project-checkpoint.is-unsupported .project-checkpoint__badge {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.execution-overview,
.execution-list__item,
.execution-timeline__item {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: var(--mc-bg-elevated, #f8fafc);
}

.execution-overview {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.execution-overview__badges,
.execution-overview__meta,
.execution-list,
.execution-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.execution-overview__badges {
  flex-direction: row;
  flex-wrap: wrap;
}

.execution-overview__meta {
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-secondary, #64748b);
}

.execution-overview__error {
  color: var(--mc-danger, #ef4444);
}

.execution-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  border: 1px solid transparent;
}

.execution-badge.is-success {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.execution-badge.is-danger {
  color: var(--mc-danger, #ef4444);
  background: rgba(239, 68, 68, 0.1);
}

.execution-badge.is-warning {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.execution-badge.is-running,
.execution-badge.is-neutral {
  color: var(--mc-primary, #d97757);
  background: rgba(217, 119, 87, 0.1);
}

.execution-badge.is-muted {
  color: var(--mc-text-secondary, #64748b);
  background: rgba(148, 163, 184, 0.12);
}

.execution-timeline__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.execution-timeline__dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  margin-top: 5px;
  flex-shrink: 0;
  background: rgba(148, 163, 184, 0.38);
}

.execution-timeline__dot.is-success { background: rgba(16, 185, 129, 0.9); }
.execution-timeline__dot.is-danger { background: rgba(239, 68, 68, 0.9); }
.execution-timeline__dot.is-warning { background: rgba(245, 158, 11, 0.9); }
.execution-timeline__dot.is-running { background: rgba(217, 119, 87, 0.9); }

.execution-timeline__body,
.execution-list__item {
  min-width: 0;
}

.execution-timeline__head,
.execution-list__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.execution-timeline__name,
.execution-list__name {
  min-width: 0;
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
  word-break: break-word;
}

.execution-timeline__time,
.execution-list__time,
.execution-timeline__meta,
.execution-list__detail {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.execution-list__detail {
  margin-top: 6px;
  word-break: break-word;
}

.execution-list__result {
  margin: 8px 0 0;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.04);
  color: var(--mc-text-secondary, #475569);
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
}

.internal-execution-panel {
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  border-radius: 12px;
  background: var(--mc-bg-elevated, #f8fafc);
}

.internal-execution-panel__summary {
  list-style: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  color: var(--mc-text-secondary, #64748b);
}

.internal-execution-panel__summary::-webkit-details-marker {
  display: none;
}

.internal-execution-panel__count {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
  background: rgba(148, 163, 184, 0.12);
}

.internal-execution-panel__body {
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
