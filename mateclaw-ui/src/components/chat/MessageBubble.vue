<template>
  <div
    class="message-wrapper"
    :class="[role, { 'is-last': isLast }]"
    :data-role="role"
    :data-status="status"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  >
    <!-- 头像 -->
    <div class="msg-avatar" :class="`${role}-avatar`">
      <slot name="avatar">
        <img v-if="role === 'assistant'" src="/logo/mateclaw_logo_s.png" alt="" class="avatar-logo" />
        <span v-else>{{ avatarIcon }}</span>
      </slot>
    </div>

    <!-- 消息体 -->
    <div class="msg-body" :class="`${role}-body`">
      <div class="msg-bubble" :class="`${role}-bubble`">
        <!-- ===== 分段式渲染模式（Claude Code 风格）===== -->
        <template v-if="useSegmentedView">
          <div class="segments-view">
            <!-- 计划步骤面板（始终显示在 segments 之上） -->
            <PlanStepsPanel v-if="planMeta" :plan="planMeta" :is-generating="isGenerating" />
            <template v-for="seg in displaySegments">
              <ThinkingSegment v-if="seg.type === 'thinking'" :key="seg.id" :segment="seg" />
              <ToolCallSegment v-if="seg.type === 'tool_call'" :key="seg.id" :segment="seg" />
              <ContentSegment v-if="seg.type === 'content'" :key="seg.id" :segment="seg" :show-cursor="showCursor && seg.status === 'running'" />
            </template>
            <div v-if="generatedFileLinks.length" class="generated-file-links">
              <div
                v-for="file in generatedFileLinks"
                :key="`segmented-${file.url}`"
                class="generated-file-link"
              >
                <el-icon class="generated-file-link__icon"><Document /></el-icon>
                <span class="generated-file-link__name">{{ file.name }}</span>
                <button
                  v-if="file.previewUrl"
                  class="generated-file-link__action"
                  type="button"
                  @click.stop.prevent="previewGeneratedFile(file)"
                >预览</button>
                <button
                  class="generated-file-link__action"
                  type="button"
                  @click.stop.prevent="downloadGeneratedFile(file)"
                >下载</button>
              </div>
            </div>
          </div>
        </template>

        <!-- ===== 传统合并渲染模式（降级兼容）===== -->
        <template v-else>

        <!-- 思考面板 -->
        <div v-if="showThinkingPanel" class="thinking-section">
          <button class="thinking-toggle" type="button" @click="toggleThinking">
            <span class="thinking-toggle__indicator" :class="{ active: isGenerating && !hasContent }">
              <el-icon><Opportunity /></el-icon>
            </span>
            <span class="thinking-toggle__label">{{ $t('chat.thinking') }}</span>
            <span class="thinking-toggle__duration" v-if="thinkingDuration">{{ thinkingDuration }}</span>
            <span class="thinking-toggle__arrow" :class="{ expanded: localThinkingExpanded }">
              <el-icon><ArrowDown /></el-icon>
            </span>
          </button>

          <!-- 思考内容（带折叠动画） -->
          <Transition name="thinking-slide">
            <div
              v-if="localThinkingExpanded"
              class="thinking-content markdown-body"
              v-html="renderedThinkingContent"
            ></div>
          </Transition>
        </div>

        <!-- 执行过程面板（折叠式） -->
        <div v-if="showExecutionPanel" class="execution-section">
          <button class="execution-toggle" type="button" @click="executionExpanded = !executionExpanded">
            <span class="execution-toggle__indicator" :class="{ active: isGenerating }">
              <el-icon><Tools /></el-icon>
            </span>
            <span class="execution-toggle__label">{{ executionPhaseLabel }}</span>
            <span class="execution-toggle__count" v-if="toolCallsMeta.length">{{ toolCallsMeta.length }} calls</span>
            <span class="execution-toggle__arrow" :class="{ expanded: executionExpanded }">
              <el-icon><ArrowDown /></el-icon>
            </span>
          </button>

          <Transition name="thinking-slide">
            <div v-if="executionExpanded" class="execution-content">
              <!-- Plan 步骤进度 -->
              <PlanStepsPanel v-if="planMeta" :plan="planMeta" :is-generating="isGenerating" />

              <!-- 工具调用列表 -->
              <div v-if="toolCallsMeta.length" class="tool-calls">
                <details
                  v-for="(tc, i) in toolCallsMeta"
                  :key="i"
                  class="tool-call"
                  :class="{ 'tool-call--running': tc.status === 'running', 'tool-call--awaiting': tc.status === 'awaiting_approval', 'tool-call--error': tc.status === 'completed' && tc.success === false, 'tool-call--expandable': hasToolResult(tc.result) }"
                  :open="tc.status === 'running'"
                >
                  <summary class="tool-call__summary">
                    <span class="tool-call__status">
                      <el-icon v-if="tc.status === 'running'" class="spin"><Loading /></el-icon>
                      <el-icon v-else-if="tc.status === 'awaiting_approval'" class="tc-icon--warning"><WarningFilled /></el-icon>
                      <el-icon v-else-if="tc.success !== false" class="tc-icon--success"><Select /></el-icon>
                      <el-icon v-else class="tc-icon--error"><CloseBold /></el-icon>
                    </span>
                    <span class="tool-call__name">{{ getToolLabel(tc.name) }}</span>
                    <span class="tool-call__args" v-if="tc.arguments">{{ truncateArgs(tc.arguments) }}</span>
                    <span v-if="hasToolResult(tc.result)" class="tool-call__result-hint">{{ t('chat.viewResult') }}</span>
                    <el-icon v-if="hasToolResult(tc.result)" class="tool-call__arrow"><ArrowDown /></el-icon>
                  </summary>
                  <pre v-if="hasToolResult(tc.result)" class="tool-call__result">{{ formatToolResult(tc.result) }}</pre>
                </details>
              </div>

              <div v-if="!toolCallsMeta.length && !planMeta" class="execution-empty">
                {{ currentPhaseName }}...
              </div>
            </div>
          </Transition>
        </div>

        <!-- 浏览器执行时间线 -->
        <BrowserTimeline v-if="browserActionsMeta.length" :actions="browserActionsMeta" />

        <!-- 工具审批状态（极简一行，操作在输入栏） -->
        <div v-if="pendingApproval" class="approval-inline" :class="[approvalSeverityClass, `is-${pendingApproval.status}`]">
          <div class="approval-inline__header">
            <el-icon class="approval-inline__icon"><WarningFilled /></el-icon>
            <span v-if="pendingApproval.status === 'pending_approval'" class="approval-inline__text">
              {{ $t('chat.approvalWaiting') }} <code>{{ getToolLabel(pendingApproval.toolName) }}</code>
            </span>
            <span v-else-if="pendingApproval.status === 'approved'" class="approval-inline__text approval-inline--approved">
              {{ $t('chat.approved') }}: <code>{{ getToolLabel(pendingApproval.toolName) }}</code>
            </span>
            <span v-else class="approval-inline__text approval-inline--denied">
              {{ $t('chat.denied') }}: <code>{{ getToolLabel(pendingApproval.toolName) }}</code>
            </span>
            <span v-if="pendingApproval.maxSeverity" class="approval-inline__severity">{{ pendingApproval.maxSeverity }}</span>
          </div>

          <div v-if="pendingApproval.summary" class="approval-inline__summary">
            <span class="approval-inline__label">{{ t('chat.approvalSummaryLabel') }}</span>
            <span>{{ pendingApproval.summary }}</span>
          </div>

          <div v-if="approvalWorkspaceBoundaryHint" class="approval-inline__summary approval-inline__summary--muted">
            <span class="approval-inline__label">{{ t('chat.approvalBoundaryLabel') }}</span>
            <span>{{ approvalWorkspaceBoundaryHint }}</span>
          </div>

          <ul v-if="approvalFindings.length" class="approval-inline__findings">
            <li v-for="(finding, index) in approvalFindings" :key="`${finding.ruleId}-${index}`" class="approval-inline__finding">
              <div class="approval-inline__finding-head">
                <span class="approval-inline__finding-title">{{ finding.title || finding.ruleId }}</span>
                <span v-if="finding.severity" class="approval-inline__finding-severity">{{ finding.severity }}</span>
              </div>
              <div v-if="finding.description" class="approval-inline__finding-desc">{{ finding.description }}</div>
              <div v-if="finding.context" class="approval-inline__finding-context">{{ finding.context }}</div>
              <div v-if="finding.remediation" class="approval-inline__finding-remediation">
                {{ t('chat.approvalRecoveryLabel') }} {{ finding.remediation }}
              </div>
            </li>
          </ul>
        </div>

        <!-- 主要内容 -->
        <div
          v-if="teacherResultSections.length || displayContent"
          class="msg-content"
          :class="{ 'with-cursor': showCursor }"
        >
          <!--
            User-authored messages render as plain text (no markdown) and
            auto-collapse beyond 8 lines. Assistant content goes through the
            normal markdown pipeline.
          -->
          <UserMessageContent v-if="role === 'user'" :content="displayContent" />
          <template v-else>
            <div v-if="teacherResultSections.length" class="teacher-result">
              <section
                v-for="section in visibleTeacherResultSections"
                :key="section.key"
                class="teacher-result__section"
                :class="{ 'is-primary': section.key === 'questions' }"
              >
                <details :open="section.key === 'questions' || (section.key === 'plan' && !hasTeacherQuestions)" class="teacher-result__details">
                  <summary class="teacher-result__summary">
                    <span class="teacher-result__title">{{ section.title }}</span>
                    <span class="teacher-result__actions">
                      <button class="teacher-result__action" type="button" @click.stop.prevent="copyTeacherSection(section)">
                        复制
                      </button>
                      <button class="teacher-result__action" type="button" @click.stop.prevent="downloadTeacherSection(section)">
                        保存
                      </button>
                    </span>
                  </summary>
                  <div class="teacher-result__body markdown-body" v-html="renderMarkdown(section.content)"></div>
                </details>
              </section>
              <details v-if="internalTeacherResultSections.length" class="teacher-result__internal">
                <summary>内部命题说明与质量审核</summary>
                <section
                  v-for="section in internalTeacherResultSections"
                  :key="`internal-${section.key}`"
                  class="teacher-result__section"
                >
                  <div class="teacher-result__summary">
                    <span class="teacher-result__title">{{ section.title }}</span>
                    <span class="teacher-result__actions">
                      <button class="teacher-result__action" type="button" @click.stop.prevent="copyTeacherSection(section)">
                        复制
                      </button>
                    </span>
                  </div>
                  <div class="teacher-result__body markdown-body" v-html="renderMarkdown(section.content)"></div>
                </section>
              </details>
              <div v-if="teacherGenerationStatus" class="teacher-result__status">
                {{ teacherGenerationStatus }}
              </div>
              <div v-if="showTeacherExportActions" class="teacher-result__export">
                <button type="button" :disabled="!canExportTeacherQuestions" @click="downloadTeacherPaper('questions')">导出仅试题版</button>
                <button type="button" :disabled="!canExportTeacherFull" @click="downloadTeacherPaper('full')">导出完整版</button>
                <button type="button" :disabled="teacherExportingMode === 'questions' || !canExportTeacherQuestions" @click="exportTeacherWord('questions')">导出 Word（仅试题）</button>
                <button type="button" :disabled="teacherExportingMode === 'full' || !canExportTeacherFull" @click="exportTeacherWord('full')">导出 Word（完整版）</button>
              </div>
            </div>
            <div v-else class="markdown-body" v-html="renderedContent"></div>
            <div v-if="generatedFileLinks.length" class="generated-file-links">
              <div
                v-for="file in generatedFileLinks"
                :key="file.url"
                class="generated-file-link"
              >
                <el-icon class="generated-file-link__icon"><Document /></el-icon>
                <span class="generated-file-link__name">{{ file.name }}</span>
                <button
                  v-if="file.previewUrl"
                  class="generated-file-link__action"
                  type="button"
                  @click.stop.prevent="previewGeneratedFile(file)"
                >预览</button>
                <button
                  class="generated-file-link__action"
                  type="button"
                  @click.stop.prevent="downloadGeneratedFile(file)"
                >下载</button>
              </div>
            </div>
            <TypingCursor v-if="showCursor" :typing="isGenerating" />
          </template>
        </div>


        <!-- 停止指示器 -->
        <div v-if="status === 'stopped' || status === 'interrupted'" class="stopped-indicator">
          <el-icon><CloseBold /></el-icon>
          <span>{{ status === 'interrupted' ? $t('chat.interrupted') : $t('chat.stopped') }}</span>
        </div>

        <!-- parse_error content block -->
        <div v-if="parseErrorText" class="parse-error-card">
          <el-icon class="parse-error-card__icon"><WarningFilled /></el-icon>
          <span class="parse-error-card__text">{{ parseErrorText }}</span>
        </div>

        <div v-else-if="showEmptyAssistantResult" class="empty-result-card">
          <el-icon class="empty-result-card__icon"><WarningFilled /></el-icon>
          <span class="empty-result-card__text">{{ t('chat.emptyAssistantResult') }}</span>
        </div>

        <!-- 错误卡片 -->
        <div v-if="status === 'failed'" class="error-card">
          <div class="error-card__header">
            <el-icon class="error-card__icon"><WarningFilled /></el-icon>
            <span class="error-card__title">{{ errorTitle }}</span>
          </div>
          <p class="error-card__description">{{ errorDescription }}</p>
          <p class="error-card__action">{{ errorAction }}</p>
          <div class="error-card__footer">
            <span v-if="errorCode" class="error-card__code">{{ errorCode }}</span>
            <button v-if="errorRetryable" class="error-card__retry" type="button" @click="$emit('regenerate')">
              <el-icon><RefreshRight /></el-icon>
              {{ $t('chat.retry') }}
            </button>
          </div>
        </div>

        </template><!-- /传统合并渲染模式 -->

        <div v-if="showReviewPanel" class="review-section">
          <button class="review-toggle" type="button" @click="reviewExpanded = !reviewExpanded">
            <span class="review-toggle__indicator">
              <el-icon><Document /></el-icon>
            </span>
            <span class="review-toggle__label">{{ t('chat.reviewTitle') }}</span>
            <span class="review-toggle__count">{{ t('chat.reviewItemsCount', { count: reviewDisplayCount }) }}</span>
            <span class="review-toggle__arrow" :class="{ expanded: reviewExpanded }">
              <el-icon><ArrowDown /></el-icon>
            </span>
          </button>

          <Transition name="thinking-slide">
            <div v-if="reviewExpanded" class="review-content">
              <div v-if="visibleReviewFiles.length" class="review-block">
                <div class="review-block__title">{{ t('chat.reviewChangedFiles') }}</div>
                <div class="review-file-list">
                  <div v-for="file in visibleReviewFiles" :key="`review-${file.path}`" class="review-file-row review-file-row--actionable">
                    <button class="review-file-row__summary review-file-row__summary--button" type="button" @click="openProjectPanelFromReview()">
                      <span class="review-file-item__badge" :class="`is-${file.changeType}`">
                        {{ getChangeTypeLabel(file.changeType) }}
                      </span>
                      <span class="review-file-row__path">{{ normalizeFilePath(file.path) }}</span>
                      <span class="review-file-row__meta-inline">{{ getReviewFileInlineMeta(file) }}</span>
                    </button>
                    <div class="review-file-row__body">
                      <div class="review-file-item__meta">
                        <span>{{ getToolLabel(file.toolName) }}</span>
                        <span v-if="file.bytesWritten">{{ t('chat.reviewBytesWritten', { count: file.bytesWritten }) }}</span>
                        <span v-else-if="file.replacements">{{ t('chat.reviewReplacements', { count: file.replacements }) }}</span>
                      </div>
                      <div v-if="file.summary" class="review-file-item__summary">{{ file.summary }}</div>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="generatedFileLinks.length" class="review-block">
                <div class="review-block__title">本次生成文件</div>
                <div class="review-file-list review-file-list--generated">
                  <button
                    v-for="file in generatedFileLinks"
                    :key="`generated-${file.url}`"
                    class="review-generated-row review-generated-row--button"
                    type="button"
                    @click="openProjectPanelFromReview()"
                  >
                    <span class="review-generated-row__name">{{ file.name }}</span>
                    <span class="review-generated-row__action">查看 Project</span>
                  </button>
                </div>
              </div>

              <div v-if="reviewValidationItems.length" class="review-block">
                <div class="review-block__title">
                  {{ t('chat.reviewValidationTitle') }}
                  <span class="review-block__count">{{ t('chat.reviewItemsCount', { count: reviewValidationItems.length }) }}</span>
                </div>
                <div class="review-file-list">
                  <details v-for="item in reviewValidationItems" :key="`validation-${item.toolName}-${item.command}`" class="review-file-row">
                    <summary class="review-file-row__summary">
                      <span class="review-file-item__badge" :class="`is-${getReviewValidationTone(item)}`">
                        {{ getReviewValidationStatusLabel(item) }}
                      </span>
                      <span class="review-file-row__path">{{ item.command }}</span>
                      <span class="review-file-row__meta-inline">{{ getReviewValidationInlineMeta(item) }}</span>
                    </summary>
                    <div class="review-file-row__body">
                      <div class="review-file-item__meta">
                        <span>{{ getToolLabel(item.toolName) }}</span>
                        <span v-if="typeof item.exitCode === 'number'">exit {{ item.exitCode }}</span>
                      </div>
                      <div v-if="item.result" class="review-file-item__summary">{{ item.result }}</div>
                    </div>
                  </details>
                </div>
              </div>

              <div v-if="projectReviewFiles.length" class="review-block">
                <div class="review-block__title">
                  {{ t('chat.reviewProjectFiles') }}
                  <span class="review-block__count">{{ t('chat.reviewChangedFilesCount', { count: projectReviewFiles.length }) }}</span>
                </div>
                <div v-if="projectChangeStats.length" class="review-stats">
                  <span v-for="item in projectChangeStats" :key="item.type" class="review-stats__item" :class="`is-${item.type}`">
                    <span class="review-stats__label">{{ getChangeTypeLabel(item.type) }}</span>
                    <span class="review-stats__value">{{ item.count }}</span>
                  </span>
                </div>
                <div class="review-project-list">
                  <span v-for="file in projectReviewFiles" :key="`project-${file.path}`" class="review-project-pill">
                    <span class="review-project-pill__status" :class="`is-${file.changeType}`"></span>
                    <span class="review-project-pill__badge" :class="`is-${file.changeType}`">{{ getChangeTypeLabel(file.changeType) }}</span>
                    <span class="review-project-pill__path">{{ normalizeFilePath(file.path) }}</span>
                  </span>
                </div>
              </div>

              <div v-if="checkpointCapability" class="review-block">
                <div class="review-block__title">{{ t('chat.reviewCheckpointTitle') }}</div>
                <div class="review-checkpoint" :class="{ 'is-supported': checkpointCapability.supported, 'is-unsupported': !checkpointCapability.supported }">
                  <span class="review-checkpoint__badge">
                    {{ checkpointCapability.supported ? t('chat.reviewCheckpointSupported') : t('chat.reviewCheckpointUnavailable') }}
                  </span>
                  <span v-if="checkpointCapability.reason" class="review-checkpoint__reason">{{ checkpointCapability.reason }}</span>
                </div>
              </div>
            </div>
          </Transition>
        </div>

        <!-- 附件列表 -->
        <div v-if="attachments?.length" class="message-attachments">
          <div
            v-for="attachment in imageAttachments"
            :key="attachment.storedName"
            class="message-attachment-image"
          >
            <img
              :src="getDisplayUrl(attachment)"
              :alt="attachment.name"
              loading="lazy"
              @click="openImage(getDisplayUrl(attachment))"
            />
            <span class="message-attachment-image__name">{{ attachment.name }}</span>
          </div>
          <div
            v-for="attachment in videoAttachments"
            :key="attachment.storedName"
            class="message-attachment-video"
          >
            <video
              :src="getDisplayUrl(attachment)"
              controls
              preload="metadata"
              playsinline
            />
            <span class="message-attachment-video__name">{{ attachment.name }}</span>
          </div>
          <button
            v-for="attachment in fileAttachments"
            :key="attachment.storedName"
            class="message-attachment"
            type="button"
            @click="downloadFile(attachment)"
          >
            <el-icon class="message-attachment__icon"><Document /></el-icon>
            <span class="message-attachment__name">{{ attachment.name }}</span>
            <span class="message-attachment__meta">{{ formatFileSize(attachment.size) }}</span>
          </button>
        </div>
      </div>

      <!-- 消息操作栏：始终占位，hover 时显示 -->
        <div
          class="msg-actions"
          :class="{
            'msg-actions--right': role === 'user',
            'msg-actions--visible': showActions
          }"
        >
          <!-- 复制 -->
          <button
            class="action-btn"
            :class="{ copied: copyState === 'copied' }"
            type="button"
            :title="copyState === 'copied' ? $t('chat.copied') : $t('chat.copy')"
            @click="copyMessage"
          >
            <el-icon v-if="copyState !== 'copied'"><CopyDocument /></el-icon>
            <el-icon v-else><Select /></el-icon>
          </button>
          <!-- 朗读 TTS（仅 assistant） -->
          <button
            v-if="role === 'assistant' && !isGenerating"
            class="action-btn"
            :class="{ 'tts-playing': ttsState === 'playing' }"
            type="button"
            :title="ttsState === 'playing' ? $t('chat.ttsStop') : $t('chat.ttsPlay')"
            :disabled="ttsState === 'loading'"
            @click="handleTts"
          >
            <el-icon v-if="ttsState === 'loading'" class="tts-loading-icon"><Loading /></el-icon>
            <el-icon v-else-if="ttsState === 'playing'"><VideoPause /></el-icon>
            <el-icon v-else><Microphone /></el-icon>
          </button>
          <!-- 重新生成（仅 assistant） -->
          <button
            v-if="role === 'assistant' && !isGenerating"
            class="action-btn"
            type="button"
            :title="$t('chat.regenerate')"
            @click="$emit('regenerate')"
          >
            <el-icon><RefreshRight /></el-icon>
          </button>
          <!-- 时间戳（inline） -->
          <span class="action-time">{{ formattedTime }}</span>
        </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  ArrowDown,
  CloseBold,
  CopyDocument,
  Document,
  Loading,
  Microphone,
  Opportunity,
  RefreshRight,
  Select,
  Tools,
  VideoPause,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useMarkdownRenderer } from '@/composables/useMarkdownRenderer'
import { useAuthenticatedAttachment } from '@/composables/useAuthenticatedAttachment'
import { useToolLabel } from '@/composables/useToolLabel'
import { conversationApi, fetchAuthenticatedBlob, http } from '@/api'
import TypingCursor from './TypingCursor.vue'
import BrowserTimeline from './BrowserTimeline.vue'
import ToolCallSegment from './ToolCallSegment.vue'
import ThinkingSegment from './ThinkingSegment.vue'
import ContentSegment from './ContentSegment.vue'
import PlanStepsPanel from './PlanStepsPanel.vue'
import UserMessageContent from './UserMessageContent.vue'
import type { GuardFinding } from '@/types'
import type { BrowserAction } from './BrowserTimeline.vue'
import type { CheckpointCapability, FileChangeRecord, Message, MessageSegment, ChatAttachment, ToolCallMeta, PlanMeta, ProjectChangeRecord, ReviewSummary, ReviewValidationRecord } from '@/types'
import type { ChatErrorInfo } from '@/types/chatError'

const { renderMarkdown } = useMarkdownRenderer()
const { t } = useI18n()
const { getToolLabel } = useToolLabel()
const { blobUrls, loadAllImages, loadAllVideos, downloadFile, openImage, getDisplayUrl, revokeAll } = useAuthenticatedAttachment()

interface Props {
  message: Message
  conversationMessages?: Message[]
  isLast?: boolean
  assistantIcon?: string
  userIcon?: string
  showCursor?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  conversationMessages: () => [],
  isLast: false,
  assistantIcon: '🤖',
  userIcon: 'U',
  showCursor: false,
})

const emit = defineEmits<{
  regenerate: []
  'toggle-thinking': [expanded: boolean]
  approve: [pendingId: string]
  deny: [pendingId: string]
  'open-project-panel': []
}>()

// --- 基础计算 ---
const role = computed(() => props.message.role)
const status = computed(() => props.message.status)
const isGenerating = computed(() => status.value === 'generating' || status.value === 'awaiting_approval')
const hovered = ref(false)

const avatarIcon = computed(() => {
  return role.value === 'user' ? props.userIcon : props.assistantIcon
})

// --- 错误卡片 ---
const errorInfo = computed<ChatErrorInfo | undefined>(() => props.message.errorInfo)

const errorTitle = computed(() => {
  if (!errorInfo.value) return t('chat.failed')
  return t(`chat.error.${errorInfo.value.category}.title`)
})

const errorDescription = computed(() => {
  if (!errorInfo.value) return ''
  // 优先展示后端 extractUserFriendlyError 生成的具体消息（比泛化模板更有指向性，
  // 比如"当前模型不支持工具调用，请切换到 qwen3 / qwen2.5 ..."）。
  // 仅当 rawMessage 为空/过短时才回退到分类模板。
  // 阈值用 3：可过滤 "OK"/"fail" 之类无意义短串，又能放行 "无权操作该会话"
  // 这类 7 字中文 / "Forbidden" 这类英文短消息，避免被泛模板覆盖。
  const raw = errorInfo.value.rawMessage?.trim() || ''
  if (raw.length > 3) {
    // 去掉后端冗余前缀，错误卡标题已经表达了类别
    return raw
      .replace(/^Bad request:\s*/i, '')
      .replace(/^LLM 调用失败[:：]\s*/, '')
      .replace(/^认证失败[:：]\s*/, '')
      .replace(/^\[错误]\s*/, '')
  }
  return t(`chat.error.${errorInfo.value.category}.description`)
})

const errorAction = computed(() => {
  if (!errorInfo.value) return ''
  return t(`chat.error.${errorInfo.value.category}.action`)
})

const errorCode = computed(() => {
  if (!errorInfo.value) return ''
  const parts: string[] = []
  if (errorInfo.value.httpStatus) parts.push(`HTTP ${errorInfo.value.httpStatus}`)
  if (errorInfo.value.requestId) parts.push(`ID: ${errorInfo.value.requestId}`)
  return parts.join(' | ')
})

const errorRetryable = computed(() => errorInfo.value?.retryable ?? true)

// --- Thinking 面板 ---
const localThinkingExpanded = ref(props.message.thinkingExpanded || false)

watch(() => props.message.thinkingExpanded, (val) => {
  if (val !== undefined) localThinkingExpanded.value = val
})

const thinkingContent = computed(() => {
  const thinkingPart = props.message.contentParts?.find(p => p.type === 'thinking')
  return thinkingPart?.text || ''
})

const hasContent = computed(() => {
  const textPart = props.message.contentParts?.find(p => p.type === 'text')
  return !!(textPart?.text || props.message.content)
})

const showThinkingPanel = computed(() => !!thinkingContent.value)

// 思考耗时（生成结束后显示）
const thinkingDuration = computed(() => {
  if (isGenerating.value) return ''
  if (!thinkingContent.value) return ''
  // 优先使用 segment 真实时间戳
  const segs = (props.message as any).segments || []
  const thinkSeg = segs.find((s: any) => s.type === 'thinking')
  const contentSeg = segs.find((s: any) => s.type === 'content')
  if (thinkSeg?.timestamp && contentSeg?.timestamp) {
    const sec = Math.max(1, Math.round((contentSeg.timestamp - thinkSeg.timestamp) / 1000))
    return sec >= 60 ? `${Math.floor(sec / 60)}m ${sec % 60}s` : `${sec}s`
  }
  // 回退：从内容长度估算
  const len = thinkingContent.value.length
  if (len < 50) return ''
  const sec = Math.max(1, Math.round(len / 100))
  return sec >= 60 ? `${Math.floor(sec / 60)}m ${sec % 60}s` : `${sec}s`
})

watch(
  [thinkingContent, hasContent, isGenerating],
  ([thinking, content, generating]) => {
    if (!generating) return
    if (thinking && !content) {
      localThinkingExpanded.value = true
    } else if (content) {
      localThinkingExpanded.value = false
    }
  },
  { immediate: true }
)

const toggleThinking = () => {
  localThinkingExpanded.value = !localThinkingExpanded.value
  emit('toggle-thinking', localThinkingExpanded.value)
}

const renderedThinkingContent = computed(() => {
  if (!thinkingContent.value) return ''
  return renderMarkdown(thinkingContent.value)
})

// --- 主内容 ---
const isApprovalPlaceholder = (text: string) => {
  return text.includes('[APPROVAL_PENDING]')
    || text.includes('[⏳ 等待审批]')
    || text.includes('[等待审批]')
}

function normalizeMultilineText(text: string) {
  return String(text || '').replace(/\r\n/g, '\n').trim()
}

function tryParseJsonText(text: string) {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function parseMessageMetadataValue(raw: unknown) {
  if (!raw) return {} as any
  if (typeof raw === 'string') {
    try {
      let parsed = JSON.parse(raw)
      if (typeof parsed === 'string') {
        try { parsed = JSON.parse(parsed) } catch { /* ignore */ }
      }
      return parsed
    } catch {
      return {}
    }
  }
  return raw
}

function escapeRegExp(text: string) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function formatToolResult(result: unknown) {
  if (result === null || result === undefined) return ''
  if (typeof result === 'string') {
    const trimmed = normalizeMultilineText(result)
    if (!trimmed) return ''
    try {
      return JSON.stringify(JSON.parse(trimmed), null, 2)
    } catch {
      return trimmed
    }
  }
  if (typeof result === 'object') {
    try {
      return JSON.stringify(result, null, 2)
    } catch {
      return String(result)
    }
  }
  return String(result)
}

function hasToolResult(result: unknown) {
  return formatToolResult(result).length > 0
}

function hasToolPayloadShape(value: unknown) {
  if (!value || typeof value !== 'object') return false
  const record = value as Record<string, unknown>
  return Array.isArray(record.pages)
    || Array.isArray(record.kbIds)
    || typeof record.kbId === 'number'
    || typeof record.requestedCount === 'number'
    || typeof record.matchCount === 'number'
    || typeof record.query === 'string'
 }

function isStructuredToolPayload(text: string) {
  const normalized = normalizeMultilineText(text)
  if (!normalized) return false
  const parsed = tryParseJsonText(normalized)
  if (parsed !== undefined) {
    return hasToolPayloadShape(parsed)
      || (typeof parsed === 'object' && parsed !== null)
      || Array.isArray(parsed)
  }
  if ((normalized.startsWith('{') && normalized.endsWith('}')) || (normalized.startsWith('[') && normalized.endsWith(']'))) {
    return true
  }
  const lineCount = normalized.split('\n').length
  return normalized.length >= 120 && lineCount >= 4 && /[{}\[\]"]/.test(normalized)
}

function buildToolPayloadCandidates(toolResults: unknown[]) {
  const candidates = new Set<string>()

  for (const result of toolResults) {
    if (result === null || result === undefined) continue
    if (typeof result === 'string') {
      const raw = normalizeMultilineText(result)
      if (raw && isStructuredToolPayload(raw)) {
        candidates.add(raw)
      }
      const parsed = tryParseJsonText(raw)
      if (parsed !== undefined) {
        candidates.add(normalizeMultilineText(JSON.stringify(parsed)))
        candidates.add(normalizeMultilineText(JSON.stringify(parsed, null, 2)))
      }
      continue
    }
    if (typeof result === 'object') {
      candidates.add(normalizeMultilineText(JSON.stringify(result)))
      candidates.add(normalizeMultilineText(JSON.stringify(result, null, 2)))
    }
  }

  return [...candidates].filter(candidate => candidate && isStructuredToolPayload(candidate))
}

function extractLeadingJsonBlock(text: string): { raw: string; endIndex: number } | null {
  const source = String(text || '')
  const startIndex = source.search(/\S/)
  if (startIndex < 0) return null
  const opening = source[startIndex]
  const closing = opening === '{' ? '}' : opening === '[' ? ']' : ''
  if (!closing) return null

  let depth = 0
  let inString = false
  let escaped = false

  for (let index = startIndex; index < source.length; index++) {
    const char = source[index]
    if (inString) {
      if (escaped) {
        escaped = false
      } else if (char === '\\') {
        escaped = true
      } else if (char === '"') {
        inString = false
      }
      continue
    }

    if (char === '"') {
      inString = true
      continue
    }

    if (char === opening) {
      depth++
      continue
    }

    if (char === closing) {
      depth--
      if (depth === 0) {
        return {
          raw: source.slice(startIndex, index + 1),
          endIndex: index + 1,
        }
      }
    }
  }

  return null
}

function stripLeadingToolPayloads(content: string, candidates: string[]) {
  let cleaned = String(content || '').replace(/\r\n/g, '\n')
  for (let round = 0; round < 3; round++) {
    const block = extractLeadingJsonBlock(cleaned)
    if (!block) break
    const normalizedBlock = normalizeMultilineText(block.raw)
    const parsedBlock = tryParseJsonText(block.raw)
    const shouldStrip = candidates.includes(normalizedBlock) || hasToolPayloadShape(parsedBlock)
    if (!shouldStrip) break
    cleaned = cleaned.slice(block.endIndex).replace(/^\s*\n*/, '')
  }
  return cleaned
}

function stripDuplicatedToolPayloads(content: string, toolResults: unknown[]) {
  let cleaned = String(content || '').replace(/\r\n/g, '\n')
  if (!cleaned) return ''

  const candidates = buildToolPayloadCandidates(toolResults).sort((a, b) => b.length - a.length)

  if (!candidates.length) {
    return normalizeMultilineText(cleaned)
  }

  cleaned = stripLeadingToolPayloads(cleaned, candidates)

  for (const candidate of candidates) {
    const compact = normalizeMultilineText(candidate)
    if (!compact) continue
    const exactBlock = new RegExp(`(?:^|\\n{2,})${escapeRegExp(compact)}(?=\\n{2,}|$)`, 'g')
    cleaned = cleaned.replace(exactBlock, '\n\n')
    if (cleaned.includes(compact)) cleaned = cleaned.replace(compact, '')
  }

  return cleaned.replace(/\n{3,}/g, '\n\n').trim()
}

function normalizeComparisonText(content: string) {
  return normalizeMultilineText(String(content || ''))
    .replace(/\s+/g, ' ')
    .trim()
}

function preferCanonicalAssistantText(content: string, stepResults: Array<{ result?: string; status?: string }> | undefined, generating: boolean) {
  const cleaned = String(content || '').trim()
  if (!cleaned || generating || !Array.isArray(stepResults) || !stepResults.length) {
    return cleaned
  }

  const normalizedContent = normalizeComparisonText(stripTeacherWorkflowComments(cleaned))
  if (!normalizedContent) {
    return cleaned
  }

  const candidates = stepResults
    .filter(item => item?.result && (!item.status || item.status === 'completed'))
    .map(item => String(item.result || '').trim())
    .filter(Boolean)
    .sort((a, b) => b.length - a.length)

  for (const candidate of candidates) {
    const normalizedCandidate = normalizeComparisonText(stripTeacherWorkflowComments(candidate))
    if (!normalizedCandidate || normalizedCandidate.length < 40) continue

    if (normalizedContent === normalizedCandidate) {
      return candidate
    }

    const isPrefixMatch = normalizedContent.startsWith(normalizedCandidate)
    const isEmbeddedMatch = normalizedContent.includes(normalizedCandidate)
    const hasSignificantTail = normalizedContent.length > normalizedCandidate.length + 60
    const hasNoticeableExpansion = normalizedContent.length >= Math.round(normalizedCandidate.length * 1.15)

    if ((isPrefixMatch || isEmbeddedMatch) && hasSignificantTail && hasNoticeableExpansion) {
      return candidate
    }
  }

  return cleaned
}

const displayContent = computed(() => {
  const textPart = props.message.contentParts?.find(p => p.type === 'text')
  const rawText = textPart?.text || props.message.content || ''
  const metadata = parseMessageMetadataValue(props.message.metadata)
  const planStepResults = Array.isArray(metadata?.plan?.stepResults)
    ? metadata.plan.stepResults
    : []
  const toolResults = [
    ...(Array.isArray(metadata?.toolCalls) ? metadata.toolCalls.map((tc: ToolCallMeta) => tc.result) : []),
    ...(Array.isArray(metadata?.segments)
      ? metadata.segments
          .filter((segment: MessageSegment) => segment.type === 'tool_call')
          .map((segment: MessageSegment) => segment.toolResult)
      : []),
  ]
  const sanitizedText = role.value === 'assistant'
    ? stripDuplicatedToolPayloads(rawText, toolResults)
    : rawText
  const canonicalText = role.value === 'assistant'
    ? preferCanonicalAssistantText(sanitizedText, planStepResults, isGenerating.value)
    : sanitizedText
  const text = stripTeacherWorkflowComments(canonicalText)
  if (isGenerating.value && !text) return ''
  // 过滤审批占位文本 — 这些消息由审批面板展示，不应作为正文显示
  if (text && isApprovalPlaceholder(text)) return ''
  // 有错误卡片时隐藏 [错误] 原始文本，避免重复展示
  if (status.value === 'failed' && errorInfo.value && text.startsWith('[错误]')) return ''
  return text
})

interface TeacherResultSection {
  key: 'plan' | 'questions' | 'answers' | 'scoringRubric' | 'qualityReview' | 'sources'
  title: string
  content: string
}

const teacherSectionOrder: TeacherResultSection['key'][] = ['plan', 'questions', 'answers', 'scoringRubric', 'qualityReview', 'sources']
const teacherSectionTitles: Record<TeacherResultSection['key'], string> = {
  plan: '命题方案',
  questions: '试题',
  answers: '参考答案',
  scoringRubric: '采分点',
  qualityReview: '命题质量审核',
  sources: '来源依据',
}

function stripTeacherWorkflowComments(text: string) {
  return String(text || '').replace(/<!--\s*teacher_exam_plan_state:[\s\S]*?-->/gi, '').trim()
}

function normalizeTeacherHeading(value: string) {
  return value
    .replace(/^[\s\d一二三四五六七八九十百零]+[、.．）)]\s*/, '')
    .replace(/^第[\d一二三四五六七八九十百零]+[章节部分篇]\s*/, '')
    .replace(/[：:]/g, '')
    .trim()
}

function teacherSectionKey(title: string): TeacherResultSection['key'] | null {
  const normalized = normalizeTeacherHeading(title)
  if (/命题质量审核|质量审核|审核/.test(normalized)) return 'qualityReview'
  if (/命题方案|出题方案|出题说明|命题说明/.test(normalized)) return 'plan'
  if (/^试题$|试题内容|试题与材料|文言文试题|现代文试题|名著.*试题|题目|练习题|试卷|正式试题/.test(normalized)) return 'questions'
  if (/参考答案|答案解析|答案/.test(normalized)) return 'answers'
  if (/采分点|评分标准|评分细则|rubric/i.test(normalized)) return 'scoringRubric'
  if (/来源依据|来源|依据|grounding/i.test(normalized)) return 'sources'
  return null
}

function normalizeTeacherHeadingSyntax(text: string) {
  return String(text || '').replace(
    /^([^\n#*][^\n]{0,60}?)\n([=-]{3,})\s*$/gm,
    (fullMatch: string, title: string) => {
      if (!teacherSectionKey(title)) return fullMatch
      return `## ${title.trim()}`
    }
  )
}

function splitTeacherAnswerRubricContent(content: string) {
  const source = String(content || '').trim()
  if (!source) {
    return { answers: '', scoringRubric: '' }
  }

  const questionBlocks = source.split(/\n(?=###\s+第\s*\d+\s*题|###\s*第\s*\d+\s*题)/g)
  const answerBlocks: string[] = []
  const rubricBlocks: string[] = []

  for (const rawBlock of questionBlocks) {
    const block = rawBlock.trim()
    if (!block) continue
    const lines = block.split('\n')
    const heading = lines[0] || ''
    const body = lines.slice(1).join('\n').trim()
    if (!body) continue

    const rubricMatch = body.match(/(?:^|\n)\*\*(?:采分点|评分标准|评分细则)\*\*[：:]?([\s\S]*)$/)
    const answerBody = rubricMatch
      ? body.slice(0, rubricMatch.index).trim()
      : body
    const rubricBody = rubricMatch
      ? String(rubricMatch[1] || '').trim()
      : ''

    if (answerBody) {
      answerBlocks.push([heading, answerBody].filter(Boolean).join('\n'))
    }
    if (rubricBody) {
      rubricBlocks.push([heading, `**采分点**：`, rubricBody].filter(Boolean).join('\n'))
    }
  }

  return {
    answers: answerBlocks.join('\n\n---\n\n').trim(),
    scoringRubric: rubricBlocks.join('\n\n---\n\n').trim(),
  }
}

function stripTeacherAuxiliaryLabels(text: string) {
  let result = String(text || '').trim()
  if (!result) return ''
  const labelPattern = /^([【\[][^【\]】\n]{1,32}(?:基础|提升|拓展|情节|人物|细节|主旨|分析|理解|辨识|考点|难度|来源依据|命题|规则|审核)[^【\]】\n]{0,32}[】\]]\s*)+/
  result = result.replace(labelPattern, '').trim()
  result = result.replace(/^(?:题型|考点|难度|来源依据)[：:]\s*/i, '').trim()
  return result
}

function normalizeTeacherVisibleText(value: unknown) {
  return stripTeacherAuxiliaryLabels(String(value || '').trim())
}

function formatTeacherQuestionItem(value: unknown, index: number) {
  if (typeof value === 'string') return normalizeTeacherVisibleText(value)
  if (!value || typeof value !== 'object') return normalizeTeacherVisibleText(String(value || ''))
  const item = value as Record<string, unknown>
  const no = teacherFieldValue(item, ['questionNo', 'number', 'index', 'id']) || String(index + 1)
  const title = normalizeTeacherVisibleText(teacherFieldValue(item, ['title']))
  const stem = normalizeTeacherVisibleText(teacherFieldValue(item, ['stem', 'question', 'content', 'text', 'prompt']))
  const material = normalizeTeacherVisibleText(teacherFieldValue(item, ['material', 'passage']))
  const options = teacherFieldValue(item, ['options', 'choices'])
  const score = teacherFieldValue(item, ['score', 'pointsValue', 'pointValue'])
  const lines: string[] = [`### 第 ${no} 题${title ? `：${title}` : ''}${score ? `（${score}分）` : ''}`]
  if (material) lines.push(`**材料**：${material}`)
  if (stem) lines.push(stem)
  if (options) lines.push(`**选项**：${options}`)
  return lines.join('\n').trim()
}

function formatTeacherAnswerItem(value: unknown, index: number) {
  if (typeof value === 'string') return value.trim()
  if (!value || typeof value !== 'object') return String(value || '').trim()
  const item = value as Record<string, unknown>
  const no = teacherFieldValue(item, ['questionNo', 'number', 'index', 'id']) || String(index + 1)
  const answer = teacherFieldValue(item, ['answer', 'referenceAnswer', 'content', 'text'])
  if (!answer) return ''
  return [`### 第 ${no} 题`, answer].join('\n').trim()
}

function formatTeacherRubricItem(value: unknown, index: number) {
  if (typeof value === 'string') return value.trim()
  if (!value || typeof value !== 'object') return String(value || '').trim()
  const item = value as Record<string, unknown>
  const no = teacherFieldValue(item, ['questionNo', 'number', 'index', 'id']) || String(index + 1)
  const rubric = teacherFieldValue(item, ['rubric', 'scoringPoints', 'points', 'criteria', 'content', 'text'])
  if (!rubric) return ''
  return [`### 第 ${no} 题`, rubric].join('\n').trim()
}

function formatTeacherSourceItem(value: unknown, index: number) {
  if (typeof value === 'string') return value.trim()
  if (!value || typeof value !== 'object') return String(value || '').trim()
  const item = value as Record<string, unknown>
  const no = teacherFieldValue(item, ['questionNo', 'number', 'index', 'id']) || ''
  const source = teacherFieldValue(item, ['source', 'sources', 'basis', 'content', 'text'])
  if (!source) return ''
  return no ? [`### 第 ${no} 题`, source].join('\n').trim() : source.trim()
}

function normalizeTeacherSectionContent(value: unknown, sectionKey?: TeacherResultSection['key']) {
  if (Array.isArray(value)) {
    const renderer = sectionKey === 'questions'
      ? formatTeacherQuestionItem
      : sectionKey === 'answers'
        ? formatTeacherAnswerItem
        : sectionKey === 'scoringRubric'
          ? formatTeacherRubricItem
          : sectionKey === 'sources'
            ? formatTeacherSourceItem
            : formatTeacherStructuredItem
    return value.map((item, index) => renderer(item, index)).filter(Boolean).join('\n\n')
  }
  if (value && typeof value === 'object') return formatTeacherStructuredObject(value as Record<string, unknown>)
  return sectionKey === 'questions'
    ? normalizeTeacherVisibleText(String(value || '').trim())
    : String(value || '').trim()
}

function teacherFieldValue(item: Record<string, unknown>, keys: string[]) {
  for (const key of keys) {
    const value = item[key]
    if (value === null || value === undefined || value === '') continue
    if (Array.isArray(value)) return value.map(v => typeof v === 'string' ? v : JSON.stringify(v)).join('；')
    if (typeof value === 'object') return JSON.stringify(value)
    return String(value)
  }
  return ''
}

function formatTeacherStructuredObject(value: Record<string, unknown>) {
  const lines: string[] = []
  const known = new Set(['title', 'grade', 'scenario', 'totalScore', 'module'])
  const title = teacherFieldValue(value, ['title', 'name'])
  if (title) lines.push(`**${title}**`)
  const grade = teacherFieldValue(value, ['grade'])
  const scenario = teacherFieldValue(value, ['scenario'])
  const totalScore = teacherFieldValue(value, ['totalScore', 'score'])
  const meta = [
    grade ? `年级：${grade}` : '',
    scenario ? `场景：${scenario}` : '',
    totalScore ? `总分：${totalScore}` : '',
  ].filter(Boolean)
  if (meta.length) lines.push(meta.join('；'))

  for (const [key, raw] of Object.entries(value)) {
    if (known.has(key) || raw === null || raw === undefined || raw === '') continue
    const rendered = Array.isArray(raw)
      ? raw.map((item, index) => formatTeacherStructuredItem(item, index)).filter(Boolean).join('\n')
      : typeof raw === 'object'
        ? JSON.stringify(raw, null, 2)
        : String(raw)
    if (rendered) lines.push(`- ${key}：${rendered}`)
  }
  return lines.join('\n').trim() || JSON.stringify(value, null, 2)
}

function formatTeacherStructuredItem(value: unknown, index: number) {
  if (typeof value === 'string') return value.trim()
  if (!value || typeof value !== 'object') return String(value || '').trim()
  const item = value as Record<string, unknown>
  const no = teacherFieldValue(item, ['questionNo', 'number', 'index', 'id']) || String(index + 1)
  const title = teacherFieldValue(item, ['title'])
  const stem = teacherFieldValue(item, ['stem', 'question', 'content', 'text', 'prompt'])
  const answer = teacherFieldValue(item, ['answer', 'referenceAnswer'])
  const rubric = teacherFieldValue(item, ['rubric', 'scoringPoints', 'points', 'criteria'])
  const source = teacherFieldValue(item, ['source', 'sources', 'basis'])
  const material = teacherFieldValue(item, ['material', 'passage'])
  const options = teacherFieldValue(item, ['options', 'choices'])
  const type = teacherFieldValue(item, ['type', 'questionType'])
  const examPoint = teacherFieldValue(item, ['examPoint', 'examPoints', 'knowledgePoint'])
  const difficulty = teacherFieldValue(item, ['difficulty'])
  const score = teacherFieldValue(item, ['score', 'pointsValue', 'pointValue'])

  const lines: string[] = [`### 第 ${no} 题${title ? `：${title}` : ''}`]
  const meta = [
    type ? `题型：${type}` : '',
    examPoint ? `考点：${examPoint}` : '',
    difficulty ? `难度：${difficulty}` : '',
    score ? `分值：${score}` : '',
  ].filter(Boolean)
  if (meta.length) lines.push(meta.join('；'))
  if (material) lines.push(`**材料**：${material}`)
  if (stem) lines.push(stem)
  if (options) lines.push(`**选项**：${options}`)
  if (answer) lines.push(`**参考答案**：${answer}`)
  if (rubric) lines.push(`**采分点**：${rubric}`)

  const consumed = new Set([
    'questionNo', 'number', 'index', 'id', 'title', 'stem', 'question', 'content', 'text', 'prompt',
    'answer', 'referenceAnswer', 'rubric', 'scoringPoints', 'points', 'criteria', 'source', 'sources',
    'basis', 'material', 'passage', 'options', 'choices', 'type', 'questionType', 'examPoint',
    'examPoints', 'knowledgePoint', 'difficulty', 'score', 'pointsValue', 'pointValue',
  ])
  for (const [key, raw] of Object.entries(item)) {
    if (consumed.has(key) || raw === null || raw === undefined || raw === '') continue
    const rendered = Array.isArray(raw)
      ? raw.map(v => typeof v === 'string' ? v : JSON.stringify(v)).join('；')
      : typeof raw === 'object'
        ? JSON.stringify(raw)
        : String(raw)
    if (rendered) lines.push(`- ${key}：${rendered}`)
  }
  return lines.join('\n').trim()
}

function parseTeacherJsonSections(text: string): TeacherResultSection[] {
  const trimmed = text.trim()
  const fenced = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i)
  const candidate = fenced?.[1] || trimmed
  const start = candidate.indexOf('{')
  const end = candidate.lastIndexOf('}')
  if (start < 0 || end <= start) return []
  try {
    const parsed = JSON.parse(candidate.slice(start, end + 1))
    const sections: TeacherResultSection[] = []
    if (parsed?.type === 'teacher_exam_result_v2') {
      const paper = normalizeTeacherSectionContent(parsed.paper, 'questions')
      const questions = normalizeTeacherSectionContent(parsed.questions, 'questions')
      if (questions) {
        sections.push({
          key: 'questions',
          title: teacherSectionTitles.questions,
          content: [paper, questions].filter(Boolean).join('\n\n'),
        })
      }
      const answers = normalizeTeacherSectionContent(parsed.answers, 'answers')
      if (answers) sections.push({ key: 'answers', title: teacherSectionTitles.answers, content: answers })
      const scoringRubric = normalizeTeacherSectionContent(parsed.scoringRubric, 'scoringRubric')
      if (scoringRubric) sections.push({ key: 'scoringRubric', title: teacherSectionTitles.scoringRubric, content: scoringRubric })
      const sources = normalizeTeacherSectionContent(parsed.sources, 'sources')
      if (sources) sections.push({ key: 'sources', title: teacherSectionTitles.sources, content: sources })
      const qualityReview = normalizeTeacherSectionContent(parsed.internalReview || parsed.qualityReview, 'qualityReview')
      if (qualityReview) sections.push({ key: 'qualityReview', title: teacherSectionTitles.qualityReview, content: qualityReview })
      return sections
    }
    for (const key of teacherSectionOrder) {
      const content = normalizeTeacherSectionContent(parsed[key], key)
      if (!content) continue
      sections.push({ key, title: teacherSectionTitles[key], content })
    }
    return sections
  } catch {
    return []
  }
}

function normalizeTeacherSections(sections: TeacherResultSection[]) {
  const merged = new Map<TeacherResultSection['key'], TeacherResultSection>()
  for (const section of sections) {
    const existing = merged.get(section.key)
    if (existing) {
      existing.content = mergeTeacherSectionContent(existing.content, section.content)
    } else {
      merged.set(section.key, {
        ...section,
        title: teacherSectionTitles[section.key] || section.title,
        content: dedupeTeacherSectionContent(section.content),
      })
    }
  }
  const values = [...merged.values()]
  const uniqueKeys = new Set(values.map(section => section.key))
  if (!uniqueKeys.has('questions') && !uniqueKeys.has('plan')) return []
  return values.sort((a, b) => teacherSectionOrder.indexOf(a.key) - teacherSectionOrder.indexOf(b.key))
}

function normalizeTeacherContentComparable(content: string) {
  return normalizeMultilineText(content)
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function dedupeTeacherSectionContent(content: string) {
  const normalized = normalizeTeacherContentComparable(content)
  if (!normalized) return ''

  const paragraphs = normalized.split(/\n{2,}/)
  if (paragraphs.length <= 1) return normalized

  const kept: string[] = []
  const seen = new Set<string>()
  for (const paragraph of paragraphs) {
    const block = paragraph.trim()
    if (!block) continue
    const key = block.replace(/\s+/g, ' ').trim()
    if (!key || seen.has(key)) continue
    seen.add(key)
    kept.push(block)
  }
  return kept.join('\n\n').trim()
}

function mergeTeacherSectionContent(existingContent: string, nextContent: string) {
  const existing = dedupeTeacherSectionContent(existingContent)
  const next = dedupeTeacherSectionContent(nextContent)
  if (!existing) return next
  if (!next) return existing
  if (existing === next || existing.includes(next)) return existing
  if (next.includes(existing)) return next
  return dedupeTeacherSectionContent(`${existing}\n\n${next}`)
}

function buildTeacherSectionsFromMatches(source: string, matches: RegExpMatchArray[]) {
  const sections: TeacherResultSection[] = []
  for (let index = 0; index < matches.length; index++) {
    const match = matches[index]
    const rawTitle = match[1].trim()
    const normalizedTitle = normalizeTeacherHeading(rawTitle)
    const start = (match.index || 0) + match[0].length
    const end = index + 1 < matches.length ? matches[index + 1].index || source.length : source.length
    const content = source.slice(start, end).trim()
    if (!content) continue

    if (/参考答案.*采分点|采分点.*参考答案/.test(normalizedTitle)) {
      const split = splitTeacherAnswerRubricContent(content)
      if (split.answers) {
        sections.push({ key: 'answers', title: teacherSectionTitles.answers, content: split.answers })
      }
      if (split.scoringRubric) {
        sections.push({ key: 'scoringRubric', title: teacherSectionTitles.scoringRubric, content: split.scoringRubric })
      }
      if (!split.answers && !split.scoringRubric) {
        sections.push({ key: 'answers', title: teacherSectionTitles.answers, content })
      }
      continue
    }

    const key = teacherSectionKey(normalizedTitle)
    if (!key) continue
    sections.push({
      key,
      title: rawTitle,
      content,
    })
  }
  return normalizeTeacherSections(sections)
}

function parseTeacherSections(text: string): TeacherResultSection[] {
  const source = normalizeTeacherHeadingSyntax(stripTeacherWorkflowComments(text))
  if (!source) return []
  const jsonSections = parseTeacherJsonSections(source)
  if (jsonSections.length) return normalizeTeacherSections(jsonSections)
  const headingPattern = /^(?:#{1,4}\s*|\*\*)\s*((?:第[\d一二三四五六七八九十百零]+[章节部分篇]\s*)?(?:[\d一二三四五六七八九十百零]+[、.．）)]\s*)?(?:(?:文言文|现代文|名著阅读|名著)\s*)?(?:命题方案|出题方案|出题说明|命题说明|试题内容|试题与材料|正式试题|试题|题目|练习题|试卷|参考答案|答案解析|答案|采分点|评分标准|评分细则|命题质量审核|质量审核|来源依据|来源|依据)(?:\s*(?:（[^）\n]{1,20}）|\([^)\n]{1,20}\)|[、及与和/\-\s]*(?:待确认|内部|复核|建议|清单|结果|说明|材料|原文|采分点|评分标准|依据|追溯|grounding))){0,3})\s*(?:\*\*)?\s*[:：]?\s*$/gmi
  const matches = [...source.matchAll(headingPattern)]
  if (!matches.length) return []
  return buildTeacherSectionsFromMatches(source, matches)
}

function scoreTeacherSourceSections(sections: TeacherResultSection[]) {
  if (!sections.length) return 0
  const keys = new Set(sections.map(section => section.key))
  const customerKeys = ['questions', 'answers', 'scoringRubric', 'sources']
  const internalKeys = ['plan', 'qualityReview']
  const customerCount = customerKeys.filter(key => keys.has(key as TeacherResultSection['key'])).length
  const internalCount = internalKeys.filter(key => keys.has(key as TeacherResultSection['key'])).length
  const contentLength = sections.reduce((total, section) => total + (section.content?.length || 0), 0)
  return customerCount * 100000 + internalCount * 10000 + sections.length * 1000 + contentLength
}

const teacherResultSource = computed(() => {
  if (role.value !== 'assistant') return ''

  const candidates: string[] = []
  const seen = new Set<string>()
  const addCandidate = (value: unknown) => {
    const candidate = String(value || '').trim()
    if (!candidate || seen.has(candidate)) return
    seen.add(candidate)
    candidates.push(candidate)
  }
  const metadata = parseMessageMetadataValue(props.message.metadata)

  const directContent = String(displayContent.value || '').trim()
  const directSections = directContent ? parseTeacherSections(directContent) : []
  const directCustomerCount = directSections.filter(section => ['questions', 'answers', 'scoringRubric', 'sources'].includes(section.key)).length
  if (directCustomerCount >= 3 || directSections.some(section => section.key === 'questions')) {
    return directContent
  }

  const stepResults = Array.isArray(metadata?.plan?.stepResults)
    ? metadata.plan.stepResults
    : []
  for (const item of stepResults) {
    if (item?.result) {
      addCandidate(item.result)
    }
  }

  if (directContent) {
    addCandidate(directContent)
  }

  let bestCandidate = ''
  let bestScore = 0
  for (const candidate of candidates) {
    const sections = parseTeacherSections(candidate)
    const score = scoreTeacherSourceSections(sections)
    if (score > bestScore) {
      bestScore = score
      bestCandidate = candidate
    }
  }
  return bestCandidate
})

function scoreTeacherSections(sections: TeacherResultSection[]) {
  if (!sections.length) return 0
  return sections.length * 10000 + sections.reduce((total, section) => total + (section.content?.length || 0), 0)
}

const teacherResultSectionsRaw = computed(() => {
  if (role.value !== 'assistant' || !teacherResultSource.value) return []
  return parseTeacherSections(teacherResultSource.value)
})

const teacherResultSectionsCache = ref<TeacherResultSection[]>([])

function customerTeacherSectionCount(sections: TeacherResultSection[]) {
  return sections.filter(section => ['questions', 'answers', 'scoringRubric', 'sources'].includes(section.key)).length
}

function shouldReplaceTeacherSections(next: TeacherResultSection[], cached: TeacherResultSection[]) {
  if (!cached.length) return next.length > 0
  if (!next.length) return false
  const nextCustomerCount = customerTeacherSectionCount(next)
  const cachedCustomerCount = customerTeacherSectionCount(cached)
  if (cachedCustomerCount > 0 && nextCustomerCount < cachedCustomerCount) {
    return false
  }
  return scoreTeacherSections(next) >= scoreTeacherSections(cached)
}

watch(
  [
    () => props.message.id,
    teacherResultSectionsRaw,
    status,
  ],
  ([messageId, sections, messageStatus], [prevMessageId]) => {
    if (messageId !== prevMessageId) {
      teacherResultSectionsCache.value = Array.isArray(sections) ? [...sections] : []
      return
    }

    if (!Array.isArray(sections) || !sections.length) {
      return
    }

    if (shouldReplaceTeacherSections(sections, teacherResultSectionsCache.value)) {
      teacherResultSectionsCache.value = [...sections]
    }
  },
  { immediate: true }
)

const teacherResultSections = computed(() => {
  return teacherResultSectionsCache.value
})

const hasTeacherQuestions = computed(() => teacherResultSections.value.some(section => section.key === 'questions'))

const internalTeacherResultSections = computed(() => {
  if (!hasTeacherQuestions.value) {
    return teacherResultSections.value.filter(section => section.key === 'qualityReview')
  }
  return teacherResultSections.value.filter(section => section.key === 'plan' || section.key === 'qualityReview')
})

const visibleTeacherResultSections = computed(() => {
  const hidden = new Set(internalTeacherResultSections.value.map(section => section.key))
  return teacherResultSections.value.filter(section => !hidden.has(section.key))
})

const teacherGenerationStatus = computed(() => {
  if (!isGenerating.value || !teacherResultSections.value.length) return ''
  const keys = new Set(teacherResultSections.value.map(section => section.key))
  if (!keys.has('questions')) return '正在生成试题...'
  if (!keys.has('answers')) return '试题已生成，正在生成参考答案...'
  if (!keys.has('scoringRubric')) return '参考答案已生成，正在生成采分点...'
  if (!keys.has('sources')) return '采分点已生成，正在整理来源依据...'
  if (pendingApproval.value?.status === 'pending_approval') return '内容已保留，等待审批后继续...'
  return '正在整理结果...'
})

const exportableTeacherResultSections = computed(() => {
  return teacherResultSections.value.filter(section =>
    ['questions', 'answers', 'scoringRubric', 'sources'].includes(section.key)
  )
})

const showTeacherExportActions = computed(() => {
  return exportableTeacherResultSections.value.length > 0 && status.value === 'completed' && !isGenerating.value
})

const canExportTeacherQuestions = computed(() => hasTeacherQuestions.value)

const canExportTeacherFull = computed(() => {
  const keys = new Set(exportableTeacherResultSections.value.map(section => section.key))
  return ['questions', 'answers', 'scoringRubric', 'sources'].every(key => keys.has(key as TeacherResultSection['key']))
})

const teacherExportingMode = ref<'questions' | 'full' | null>(null)

function copyTeacherSection(section: TeacherResultSection) {
  void copyTextToClipboard(`## ${section.title}\n\n${section.content}`)
}

function downloadTextFile(filename: string, content: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function downloadTeacherSection(section: TeacherResultSection) {
  downloadTextFile(`${section.title}.md`, `## ${section.title}\n\n${section.content}\n`)
}

function buildTeacherPaperContent(mode: 'questions' | 'full') {
  const hasQuestions = teacherResultSections.value.some(section => section.key === 'questions')
  if (mode === 'questions' && !hasQuestions) {
    return ''
  }
  const sections = mode === 'questions'
    ? teacherResultSections.value.filter(section => section.key === 'questions')
    : exportableTeacherResultSections.value
  return sections.map(section => `## ${section.title}\n\n${section.content}`).join('\n\n')
}

function downloadTeacherPaper(mode: 'questions' | 'full') {
  const content = buildTeacherPaperContent(mode)
  if (!content.trim()) {
    ElMessage.warning('暂无结构化试题内容，无法导出')
    return
  }
  downloadTextFile(mode === 'questions' ? '试题版.md' : '完整版.md', `${content}\n`)
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

function previewGeneratedFile(file: { previewUrl: string | null; url: string }) {
  const previewUrl = normalizeGeneratedFileUrl(file.previewUrl || `${file.url.replace(/\/inline$/, '')}/inline`)
  if (!previewUrl) {
    ElMessage.error('预览地址无效')
    return
  }
  openGeneratedPreviewUrl(previewUrl)
}

function openGeneratedPreviewUrl(url: string) {
  const absoluteUrl = new URL(url, window.location.origin).href
  const opened = window.open('', '_blank')
  if (opened) {
    opened.opener = null
    opened.location.href = absoluteUrl
    return
  }
  window.open(absoluteUrl, '_blank', 'noopener,noreferrer')
}

async function downloadGeneratedFile(file: { name: string; url: string }) {
  const url = normalizeGeneratedFileUrl(file.url).replace(/\/inline$/, '')
  if (!url) {
    ElMessage.error('下载地址无效')
    return
  }
  try {
    const blob = await fetchAuthenticatedBlob(url)
    triggerBlobDownload(blob, file.name || '生成文件')
  } catch (error: any) {
    ElMessage.error(`下载失败：${error?.message || error || 'unknown error'}`)
  }
}

async function exportTeacherWord(mode: 'questions' | 'full') {
  const content = buildTeacherPaperContent(mode)
  if (!content.trim()) {
    ElMessage.warning('暂无结构化试题内容，无法导出 Word')
    return
  }
  const sectionKeys = mode === 'questions'
    ? ['questions']
    : exportableTeacherResultSections.value.map(section => section.key)
  teacherExportingMode.value = mode
  try {
    const response: any = await conversationApi.exportTeacherPaper(props.message.conversationId, {
      markdown: `${content}\n`,
      filename: mode === 'questions' ? '试题版' : '完整版',
      format: 'docx',
      pageSize: 'A4',
      mode,
      sectionKeys,
    })
    const data = response?.data || response
    if (!data?.downloadUrl) {
      throw new Error('missing download url')
    }
    const blob = await fetchAuthenticatedBlob(data.downloadUrl)
    triggerBlobDownload(blob, data.fileName || (mode === 'questions' ? '试题版.docx' : '完整版.docx'))
    if (data.savedPath) {
      ElMessage.success(`Word 已导出，并保存到 ${data.savedPath}`)
    } else {
      ElMessage.success('Word 已导出')
    }
  } catch (error: any) {
    ElMessage.error(`Word 导出失败：${error?.message || error || 'unknown error'}`)
  } finally {
    teacherExportingMode.value = null
  }
}

// --- parse_error detection ---
const parseErrorText = computed(() => {
  const errorPart = props.message.contentParts?.find(p => p.type === 'parse_error')
  return errorPart?.text || ''
})

/**
 * Remove duplicate "文件路径" summary blocks from AI text, keeping only the first occurrence.
 * The AI sometimes echoes the write_file tool result twice (once as a detailed summary,
 * once as a compact overview at the end of the message).
 */
function deduplicateFilePathSections(content: string): string {
  if (!content) return content
  // Split on headings or bold "文件路径" markers
  const markerRe = /(?=(?:\*{1,2}文件路径[\*：:：]|^文件路径[\s：:：]))/m
  const idx = content.search(markerRe)
  if (idx < 0) return content
  // Find second occurrence
  const secondIdx = content.indexOf(content.slice(idx, idx + 8), idx + 5)
  if (secondIdx > idx) {
    // Remove everything from the second occurrence onwards if it looks like a duplicate block
    const tail = content.slice(secondIdx)
    // Only trim if the tail is clearly a short duplicate summary (< 40% of remaining)
    if (tail.length < (content.length - idx) * 0.7) {
      return content.slice(0, secondIdx).replace(/[\s\n]+$/, '')
    }
  }
  return content
}

function stripGeneratedFileAnchorHtml(text: string) {
  return String(text || '').replace(
    /<a\b[^>]*href=["']((?:https?:\/\/[^"']+)?\/api\/v1\/files\/generated\/(?:disk\/)?[^"']+|[^"']+\.html?(?:[?#][^"']*)?)["'][^>]*>([\s\S]*?)<\/a>/gi,
    '$2'
  )
}

function buildAssistantBodyText(content: string) {
  let text = String(content || '')
  if (!text) return ''
  // Strip markdown links pointing to local absolute paths (Windows/Unix) — these are write_file echoes
  text = text.replace(/\[([^\]]*)\]\(((?:[A-Za-z]:[\\/]|file:\/\/\/)[^)]*)\)/g, '$1')
  // Strip bare Windows absolute paths that appear as inline text or in parentheses
  text = text.replace(/\(?[A-Za-z]:\\[^\s)>"'\n]{3,}\)?/g, '')
  // Strip generated-file API links from inline markdown (they render in the file entry area below)
  text = text.replace(
    /\[([^\]]+)]\(((?:https?:\/\/[^)\s]+)?\/api\/v1\/files\/generated\/(?:disk\/)?[^)\s]+(?:\/inline)?)\)/g,
    '$1'
  )
  // Strip raw HTML anchors for generated-file links / exported HTML previews.
  text = stripGeneratedFileAnchorHtml(text)
  // Deduplicate 文件路径 summary sections: keep only first occurrence
  text = deduplicateFilePathSections(text)
  // If this assistant message is mainly a generated-file acknowledgement, the file card below
  // is the canonical representation; hide the verbose text to avoid duplicate output.
  if (generatedFileLinks.value.length > 0 && shouldHideGeneratedFileNarrative(text)) {
    return ''
  }
  // If write_file for the generated file failed (for example due to truncated JSON / max_tokens),
  // do not render a success-looking narrative that only repeats a guessed path.
  if (generatedFileLinks.value.length === 0 && hasFailedGeneratedFileWrite.value && shouldHideGeneratedFileNarrative(text)) {
    return ''
  }
  return text.trim()
}

const renderedContent = computed(() => {
  if (!displayContent.value) return ''
  const text = buildAssistantBodyText(displayContent.value)
  if (!text) return ''
  return renderMarkdown(text)
})

function shouldHideGeneratedFileNarrative(text: string) {
  const normalized = normalizeMultilineText(String(text || ''))
  if (!normalized) return false
  if (looksLikeGeneratedFileOnlyNarrative(normalized)) return true
  const looksLikeExportAck = /(?:已生成|已导出|生成(?:了|完成)|导出为|试题报表|报表内容|文件路径|支持浏览器直接打开|支持打印)/.test(normalized)
  const hasPathEcho = /(?:[A-Za-z]:\\|\/output\/|\/api\/v1\/files\/generated\/)/.test(normalized)
  const hasReportAck = /(?:HTML|html|Word|docx|报表|预览|下载|点击)/i.test(normalized)
  const hasVeryLittleExtra = normalized.length < 520 || normalized.split('\n').length <= 14
  return looksLikeExportAck && (hasPathEcho || hasReportAck) && hasVeryLittleExtra
}

function looksLikeGeneratedFileOnlyNarrative(normalized: string) {
  const compact = normalized
    .replace(/\[([^\]]+)]\(((?:https?:\/\/[^)\s]+)?\/api\/v1\/files\/generated\/(?:disk\/)?[^)\s]+(?:\/inline)?)\)/g, '$1')
    .replace(/(?:https?:\/\/[^)\s]+)?\/api\/v1\/files\/generated\/(?:disk\/)?[^)\s]+(?:\/inline)?/g, '')
    .replace(/[A-Za-z]:[\\/][^\s)>"']+/g, '')
    .trim()
  if (!compact || compact.length > 900) return false
  const hasGeneratedFile = /(?:\.html?\b|\.docx\b|HTML|Word|download|preview)/i.test(compact)
  const hasAck = ['已生成', '已导出', '生成', '报表', '预览', '下载', '点击', '文件'].some(term => compact.includes(term))
  return hasGeneratedFile && hasAck
}

const hasFailedGeneratedFileWrite = computed(() => {
  const toolCalls: ToolCallMeta[] = Array.isArray(parsedMetadata.value?.toolCalls) ? parsedMetadata.value.toolCalls : []
  return toolCalls.some(tc => {
    if (tc?.name !== 'write_file') return false
    const resultText = String(tc.result || '')
    return tc.success === false
      && /truncated mid-stream|max_tokens|same tool now/i.test(resultText)
  })
})

function normalizeGeneratedFileUrl(rawUrl: string) {
  const trimmed = String(rawUrl || '').trim()
  if (!trimmed) return ''
  try {
    const parsed = new URL(trimmed, window.location.origin)
    if (parsed.pathname.startsWith('/api/v1/files/generated/')) {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`
    }
  } catch {
    // keep the fallback below for malformed but still usable relative URLs
  }
  return trimmed
}

function artifactIdentityProbe(file: { name?: string; url?: string; previewUrl?: string | null }) {
  return normalizeGeneratedFileUrl(String(file.url || file.previewUrl || '')).replace(/\/inline$/, '')
    || String(file.name || '').trim().toLowerCase()
}

function normalizeArtifactComparablePath(value: string) {
  const normalizedUrl = normalizeGeneratedFileUrl(value).replace(/\/inline$/, '')
  if (!normalizedUrl) return ''
  const generatedMatch = normalizedUrl.match(/\/api\/v1\/files\/generated\/(?:disk\/)?([^?#]+)/i)
  const candidate = generatedMatch?.[1] || normalizedUrl
  try {
    return decodeURIComponent(candidate).replace(/\\/g, '/').toLowerCase()
  } catch {
    return candidate.replace(/\\/g, '/').toLowerCase()
  }
}

function artifactComparablePath(file: { name?: string; url?: string; previewUrl?: string | null; path?: string; source?: string }) {
  return normalizeFilePath(String(file.path || '')).toLowerCase()
    || normalizeArtifactComparablePath(String(file.url || file.previewUrl || ''))
    || normalizeFilePath(String(file.name || '')).toLowerCase()
}

function artifactComparableDirectory(value: string) {
  const normalized = String(value || '').replace(/\/+/g, '/').replace(/\/$/, '')
  const index = normalized.lastIndexOf('/')
  return index >= 0 ? normalized.slice(0, index) : ''
}

function artifactComparableStem(value: string) {
  const normalized = String(value || '').replace(/\/+/g, '/').replace(/\/$/, '')
  const basename = normalized.slice(normalized.lastIndexOf('/') + 1)
  return basename.replace(/\.[^.]+$/, '').toLowerCase()
}

function sharesGeneratedArtifactLineage(
  candidate: { name?: string; url?: string; previewUrl?: string | null; path?: string; source?: string },
  finals: Array<{ name?: string; url?: string; previewUrl?: string | null; path?: string; source?: string }>,
) {
  const candidatePath = artifactComparablePath(candidate)
  if (!candidatePath) return false
  const candidateStem = artifactComparableStem(candidatePath)
  const candidateDirectory = artifactComparableDirectory(candidatePath)
  return finals.some(finalArtifact => {
    const finalPath = artifactComparablePath(finalArtifact)
    if (!finalPath) return false
    if (candidatePath === normalizeFilePath(String(finalArtifact.source || '')).toLowerCase()) {
      return true
    }
    const finalStem = artifactComparableStem(finalPath)
    const finalDirectory = artifactComparableDirectory(finalPath)
    return !!candidateStem && candidateStem === finalStem
      && (!!candidateDirectory && candidateDirectory === finalDirectory || candidatePath.includes('/output/') || finalPath.includes('/output/'))
  })
}

function isFinalGeneratedArtifact(file: { name?: string; url?: string; previewUrl?: string | null }) {
  const probe = [file.name, file.url, file.previewUrl].map(item => String(item || '')).join(' ')
  return /(?:\.html?|\.xhtml|\.docx|\.pdf|\.xlsx?|\.pptx)(?:\b|$)/i.test(probe)
}

function isTemporaryGeneratedArtifact(file: { name?: string; url?: string; previewUrl?: string | null }) {
  const probe = [file.name, file.url, file.previewUrl].map(item => String(item || '')).join(' ')
  return /(?:\.md|\.markdown|\.txt|\.json|\.csv|\.log)(?:\b|$)/i.test(probe)
}

function filterPrimaryGeneratedFileLinks<T extends { name?: string; url?: string; previewUrl?: string | null }>(files: T[]) {
  if (!files.length) return files
  const finals = files.filter(file => isFinalGeneratedArtifact(file))
  if (!finals.length) return files
  return files.filter(file => isFinalGeneratedArtifact(file) || !isTemporaryGeneratedArtifact(file) || !sharesGeneratedArtifactLineage(file, finals))
}

const generatedFileLinks = computed(() => {
  const seen = new Set<string>()
  const result: Array<{ name: string; url: string; previewUrl: string | null }> = []

  function addEntry(name: string, url: string) {
    const baseUrl = normalizeGeneratedFileUrl(url).replace(/\/inline$/, '')
    if (!baseUrl || seen.has(baseUrl)) return
    seen.add(baseUrl)
    const isPreviewable = /\.(html?|xhtml|txt|md|json|csv|log)$/i.test(name)
    result.push({
      name: name || '生成文件',
      url: baseUrl,
      previewUrl: isPreviewable ? `${baseUrl}/inline` : null,
    })
  }

  function tryAddFromToolResult(rawResult: unknown) {
    if (!rawResult) return
    if (typeof rawResult === 'string') {
      try {
        const parsed = JSON.parse(rawResult)
        if (parsed?.apiUrl && parsed?.filename && !parsed?.error) {
          addEntry(parsed.filename, parsed.previewUrl || parsed.apiUrl)
          return
        }
      } catch {
        // fall through to markdown-link parsing
      }
      const matches = Array.from(rawResult.matchAll(/\[([^\]]+)]\(((?:https?:\/\/[^)\s]+)?\/api\/v1\/files\/generated\/(?:disk\/)?[^)\s]+(?:\/inline)?)\)/g))
      for (const match of matches) {
        const name = (match[1] || '生成文件').trim()
        const url = match[2]
        addEntry(name, url)
      }
      return
    }
    if (typeof rawResult === 'object') {
      const parsed: any = rawResult
      if (parsed?.apiUrl && parsed?.filename && !parsed?.error) {
        addEntry(parsed.filename, parsed.previewUrl || parsed.apiUrl)
      }
    }
  }

  // 1. Parse from toolCalls metadata (any tool result with apiUrl — most reliable)
  const metadata = parseMessageMetadataValue(props.message.metadata)
  const toolCalls: ToolCallMeta[] = Array.isArray(metadata?.toolCalls) ? metadata.toolCalls : []
  for (const tc of toolCalls) {
    tryAddFromToolResult(tc.result)
  }

  // 2. Also scan AI message text for generated-file markdown links (legacy / fallback)
  const content = displayContent.value || ''
  const matches = Array.from(content.matchAll(/\[([^\]]+)]\(((?:https?:\/\/[^)\s]+)?\/api\/v1\/files\/generated\/(?:disk\/)?[^)\s]+(?:\/inline)?)\)/g))
  for (const match of matches) {
    const name = (match[1] || '生成文件').trim()
    const url = match[2]
    addEntry(name, url)
  }

  const filtered = filterPrimaryGeneratedFileLinks(result)
  const deduped: Array<{ name: string; url: string; previewUrl: string | null }> = []
  const filteredSeen = new Set<string>()
  for (const item of filtered) {
    const key = artifactIdentityProbe(item)
    if (!key || filteredSeen.has(key)) continue
    filteredSeen.add(key)
    deduped.push(item)
  }
  return deduped
})

const showLoadingIndicator = computed(() => {
  return isGenerating.value && !displayContent.value
})

const showEmptyAssistantResult = computed(() => {
  if (role.value !== 'assistant') return false
  if (isGenerating.value) return false
  if (status.value !== 'completed') return false
  if (displayContent.value) return false
  if (parseErrorText.value) return false
  if (attachments.value.length > 0) return false
  if (showThinkingPanel.value) return false
  if (showExecutionPanel.value) return false
  if (showReviewPanel.value) return false
  if (useSegmentedView.value && segments.value.length > 0) return false
  return true
})


// --- 操作栏 ---
const showActions = computed(() => {
  if (isGenerating.value) return false
  return hovered.value && (displayContent.value || status.value === 'stopped' || status.value === 'interrupted' || status.value === 'failed')
})

const copyState = ref<'idle' | 'copied'>('idle')
let copyTimer: ReturnType<typeof setTimeout> | null = null

async function copyTextToClipboard(text: string) {
  const normalized = String(text || '')
  if (!normalized) {
    return false
  }

  const fallbackCopy = () => {
    const textarea = document.createElement('textarea')
    textarea.value = normalized
    textarea.setAttribute('readonly', 'true')
    textarea.style.position = 'fixed'
    textarea.style.top = '-9999px'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.focus()
    textarea.select()
    textarea.setSelectionRange(0, textarea.value.length)
    const copied = document.execCommand('copy')
    document.body.removeChild(textarea)
    return copied
  }

  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(normalized)
      return true
    }
  } catch {
    // Fall through to the legacy copy path.
  }

  return fallbackCopy()
}

async function copyMessage() {
  const text = displayContent.value || props.message.content || ''
  if (!text) return
  const copied = await copyTextToClipboard(text)
  if (!copied) {
    ElMessage.warning('复制失败，请检查浏览器权限')
    return
  }
  copyState.value = 'copied'
  if (copyTimer) clearTimeout(copyTimer)
  copyTimer = setTimeout(() => { copyState.value = 'idle' }, 2000)
}

// --- TTS 朗读 ---
const ttsState = ref<'idle' | 'loading' | 'playing'>('idle')
let ttsAudio: HTMLAudioElement | null = null

async function handleTts() {
  if (ttsState.value === 'playing') {
    // 停止播放
    ttsAudio?.pause()
    ttsAudio = null
    ttsState.value = 'idle'
    return
  }

  const text = displayContent.value || props.message.content || ''
  if (!text) return

  const conversationId = props.message.conversationId
  if (!conversationId) return

  ttsState.value = 'loading'
  try {
    const result: any = await http.post('/tts/synthesize', {
      conversationId,
      text,
    })
    if (result?.success && result?.audioUrl) {
      const blob = await fetchAuthenticatedBlob(result.audioUrl)
      const blobUrl = URL.createObjectURL(blob)
      ttsAudio = new Audio(blobUrl)
      ttsAudio.onended = () => {
        ttsState.value = 'idle'
        URL.revokeObjectURL(blobUrl)
        ttsAudio = null
      }
      ttsAudio.onerror = () => {
        ttsState.value = 'idle'
        URL.revokeObjectURL(blobUrl)
        ttsAudio = null
      }
      ttsState.value = 'playing'
      await ttsAudio.play()
    } else {
      ttsState.value = 'idle'
      ElMessage.warning(result?.message || '朗读失败：当前未返回可播放音频')
    }
  } catch (error) {
    ttsState.value = 'idle'
    ElMessage.warning(typeof error === 'string' ? error : '朗读失败，请检查 TTS 配置')
  }
}

onBeforeUnmount(() => {
  if (copyTimer) clearTimeout(copyTimer)
  if (ttsAudio) { ttsAudio.pause(); ttsAudio = null }
  revokeAll()
})

// --- 附件 ---
const attachments = computed(() => props.message.attachments || [])
const imageAttachments = computed(() => attachments.value.filter(a => a.contentType?.startsWith('image/')))
const videoAttachments = computed(() => attachments.value.filter(a => a.contentType?.startsWith('video/')))
const fileAttachments = computed(() => attachments.value.filter(a =>
  !a.contentType?.startsWith('image/') && !a.contentType?.startsWith('video/')
))

// 增量加载图片/视频附件的鉴权 blob URL（watch 覆盖首次 + 后续变化）
watch(imageAttachments, (atts) => {
  if (atts.length > 0) loadAllImages(atts)
}, { immediate: true })
watch(videoAttachments, (atts) => {
  if (atts.length > 0) loadAllVideos(atts)
}, { immediate: true })

// --- 时间 ---
const formattedTime = computed(() => {
  if (!props.message.createTime) return ''
  return new Date(props.message.createTime).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })
})

const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

// openAttachment 已由 useAuthenticatedAttachment 的 openImage / downloadFile 替代

// --- 执行过程面板 ---
const executionExpanded = ref(false)
const reviewExpanded = ref(false)

// --- 分段式渲染（Claude Code 风格） ---
const parsedMetadata = computed(() => {
  return parseMessageMetadataValue(props.message.metadata)
})

const normalizeFilePath = (path: string) => path?.replace(/\\/g, '/') || ''

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

const reviewSummary = computed<ReviewSummary | undefined>(() => {
  const summary = parsedMetadata.value?.reviewSummary
  const hasFiles = Array.isArray(summary?.files) && summary.files.length > 0
  const hasValidations = Array.isArray(summary?.validations) && summary.validations.length > 0
  const hasProjectSnapshot = Array.isArray(summary?.projectChangedFiles) && summary.projectChangedFiles.length > 0
  const hasCheckpoint = !!summary?.checkpointCapability
  if (!summary || (!hasFiles && !hasValidations && !hasProjectSnapshot && !hasCheckpoint)) {
    return undefined
  }
  return summary
})

const visibleReviewFiles = computed<FileChangeRecord[]>(() => {
  const files = Array.isArray(reviewSummary.value?.files) ? reviewSummary.value!.files! : []
  const finalArtifacts = generatedFileLinks.value.filter(file => isFinalGeneratedArtifact(file))
  if (!finalArtifacts.length) {
    return files
  }
  return files.filter(file => {
    const path = normalizeFilePath(file.path || '').toLowerCase()
    if (!path.includes('/output/')) return true
    if (/\.(html?|docx|pdf)$/i.test(path)) return true
    return !sharesGeneratedArtifactLineage({ path }, finalArtifacts)
  })
})

const checkpointCapability = computed<CheckpointCapability | undefined>(() => reviewSummary.value?.checkpointCapability)

const reviewValidationItems = computed<ReviewValidationRecord[]>(() => {
  return []
})

const reviewDisplayCount = computed(() => {
  const fileCount = visibleReviewFiles.value.length
  if (fileCount > 0) {
    return fileCount
  }
  if (generatedFileLinks.value.length > 0) {
    return generatedFileLinks.value.length
  }
  return projectReviewFiles.value.length
})

const projectReviewFiles = computed<Array<ProjectChangeRecord | FileChangeRecord>>(() => {
  const serverSnapshot = Array.isArray(reviewSummary.value?.projectChangedFiles)
    ? reviewSummary.value!.projectChangedFiles!
    : []
  if (serverSnapshot.length > 0) {
    return [...serverSnapshot].sort((a, b) => normalizeFilePath(a.path).localeCompare(normalizeFilePath(b.path)))
  }

  return []
})

const projectChangeStats = computed<Array<{ type: string; count: number }>>(() => {
  const counts = new Map<string, number>()
  for (const file of projectReviewFiles.value) {
    const type = file.changeType || 'modified'
    counts.set(type, (counts.get(type) || 0) + 1)
  }
  return Array.from(counts.entries())
    .map(([type, count]) => ({ type, count }))
    .sort((a, b) => {
      const order = ['added', 'modified', 'deleted', 'renamed', 'untracked']
      return order.indexOf(a.type) - order.indexOf(b.type)
    })
})

function getReviewFileInlineMeta(file: FileChangeRecord) {
  if (file.bytesWritten) return t('chat.reviewBytesWritten', { count: file.bytesWritten })
  if (file.replacements) return t('chat.reviewReplacements', { count: file.replacements })
  return getToolLabel(file.toolName)
}

function openProjectPanelFromReview() {
  emit('open-project-panel')
}

function getReviewValidationTone(item: ReviewValidationRecord) {
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

function getReviewValidationStatusLabel(item: ReviewValidationRecord) {
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

function getReviewValidationInlineMeta(item: ReviewValidationRecord) {
  if (typeof item.exitCode === 'number') {
    return `exit ${item.exitCode}`
  }
  return getToolLabel(item.toolName)
}

const showReviewPanel = computed(() => {
  return role.value === 'assistant'
    && (
      visibleReviewFiles.value.length > 0
      || generatedFileLinks.value.length > 0
      || (props.isLast && projectReviewFiles.value.length > 0)
    )
})

const segments = computed<MessageSegment[]>(() => {
  if (props.message.role !== 'assistant') return []
  const meta = parsedMetadata.value

  // 优先：使用 metadata.segments（流式时由前端写入，历史时由后端持久化）
  if (meta?.segments && Array.isArray(meta.segments) && meta.segments.length > 0
      && typeof meta.segments[0] === 'object' && meta.segments[0]?.type) {
    const segs = [...meta.segments] as MessageSegment[]

    // 补充：如果后端 segments 没有 thinking 但 contentParts 有（非原生 thinking 模型）
    const hasThinking = segs.some(s => s.type === 'thinking')
    if (!hasThinking) {
      const thinkingPart = props.message.contentParts?.find(p => p.type === 'thinking')
      if (thinkingPart?.text) {
        segs.unshift({ id: 'th-fb', type: 'thinking', status: 'completed', thinkingText: thinkingPart.text })
      }
    }

    // 去重：相同 toolName + toolArgs 的 tool_call segment 只保留第一个
    const seenToolCalls = new Set<string>()
    const deduped = segs.filter(seg => {
      if (seg.type !== 'tool_call') return true
      const key = `${seg.toolName}::${seg.toolArgs || ''}`
      if (seenToolCalls.has(key)) return false
      seenToolCalls.add(key)
      return true
    })
    segs.length = 0
    segs.push(...deduped)

    // 修复历史消息顺序：如果 thinking 被落在 content 后面，提到首个 content 前
    // 只处理单个 thinking 段的常见场景，避免破坏复杂交错时间线
    const thinkingIndices = segs
      .map((seg, index) => seg.type === 'thinking' ? index : -1)
      .filter(index => index >= 0)
    const firstNonThinkingIdx = segs.findIndex((seg: MessageSegment) => seg.type !== 'thinking')
    if (thinkingIndices.length === 1 && firstNonThinkingIdx >= 0 && thinkingIndices[0] > firstNonThinkingIdx) {
      const [thinkingSeg] = segs.splice(thinkingIndices[0], 1)
      segs.splice(0, 0, thinkingSeg)
    }

    return segs
  }

  // Fallback：从 toolCalls + contentParts 做 best-effort 重建（旧消息兼容）
  // 注意：这会丢失事件交错顺序（所有 thinking 在前，所有 tool calls 在中，content 在后）
  const segs: MessageSegment[] = []
  const thinkingPart = props.message.contentParts?.find(p => p.type === 'thinking')
  if (thinkingPart?.text) {
    segs.push({ id: 'th-0', type: 'thinking', status: 'completed', thinkingText: thinkingPart.text })
  }
  const toolCalls = meta?.toolCalls || []
  toolCalls.forEach((tc: ToolCallMeta, i: number) => {
    segs.push({
      id: `tc-${i}`, type: 'tool_call', status: 'completed',
      toolName: tc.name, toolArgs: tc.arguments,
      toolResult: tc.result, toolSuccess: tc.success,
      sourceSkillName: tc.sourceSkillName,
      sourceSkillKey: tc.sourceSkillKey,
    })
  })
  if (props.message.content) {
    segs.push({ id: 'ct-0', type: 'content', status: 'completed', text: props.message.content })
  }
  return segs
})

const displaySegments = computed<MessageSegment[]>(() => {
  const contentSegments = segments.value.filter(seg => seg.type === 'content')
  const lastContentId = contentSegments.length ? contentSegments[contentSegments.length - 1].id : ''
  return segments.value.reduce<MessageSegment[]>((acc, seg) => {
    if (seg.type !== 'content') {
      acc.push(seg)
      return acc
    }
    const nextText = seg.id === lastContentId
      ? buildAssistantBodyText(displayContent.value || seg.text || '')
      : String(seg.text || '')
    if (!nextText.trim()) {
      return acc
    }
    if (nextText === String(seg.text || '')) {
      acc.push(seg)
      return acc
    }
    acc.push({ ...seg, text: nextText })
    return acc
  }, [])
})

/** 是否使用分段模式渲染（有 segments 数据且包含多个分段） */
const useSegmentedView = computed(() => segments.value.length > 1 && teacherResultSections.value.length === 0)

const toolCallsMeta = computed<ToolCallMeta[]>(() => {
  return parsedMetadata.value?.toolCalls || []
})

const browserActionsMeta = computed<BrowserAction[]>(() => {
  return parsedMetadata.value?.browserActions || []
})

const planMeta = computed<PlanMeta | undefined>(() => {
  return parsedMetadata.value?.plan
})

const currentPhaseName = computed(() => {
  const phase = parsedMetadata.value?.currentPhase
  switch (phase) {
    case 'reasoning': return 'Reasoning'
    case 'action': return 'Executing tools'
    case 'planning': return 'Planning'
    case 'summarizing': return 'Summarizing'
    case 'awaiting_approval': return 'Waiting for approval'
    case 'executing': return 'Executing'
    case 'replaying': return 'Resuming execution'
    case 'resumed_execution': return 'Resumed'
    default: return 'Processing'
  }
})

const truncateArgs = (args: string) => {
  if (!args) return ''
  const clean = args.replace(/\s+/g, ' ').trim()
  return clean.length > 60 ? clean.slice(0, 60) + '...' : clean
}

// --- 审批面板 ---
const pendingApproval = computed(() => {
  const approval = parsedMetadata.value?.pendingApproval
  if (!approval || approval.status === 'expired') return null
  return approval
})

const approvalSeverityClass = computed(() => {
  const sev = pendingApproval.value?.maxSeverity?.toLowerCase()
  if (!sev) return ''
  return 'approval-severity-' + sev
})

type ApprovalFindingView = {
  ruleId: string
  title: string
  severity?: string
  description?: string
  remediation?: string
  context?: string
}

function asStringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    : []
}

function formatWorkspacePolicyContext(finding: GuardFinding): string {
  const metadata = finding.metadata || {}
  const policy = metadata.workspacePolicy || {}
  const allowedPaths = asStringList(policy.allowedPaths)
  const deniedPaths = asStringList(policy.deniedPaths)
  const parts: string[] = []

  if (finding.ruleId === 'WORKSPACE_DENIED_PATH' && finding.snippet) {
    parts.push(`${finding.snippet} ∈ deniedPaths`)
  } else if (finding.ruleId === 'WORKSPACE_ALLOWED_PATH_MISS') {
    if (allowedPaths.length) {
      parts.push(`${t('chat.approvalAllowedPathsLabel')}: ${allowedPaths.slice(0, 3).join(', ')}`)
      if (allowedPaths.length > 3) {
        parts[parts.length - 1] += ` +${allowedPaths.length - 3}`
      }
    }
    if (finding.snippet) {
      parts.push(`${t('chat.approvalAttemptedPathLabel')}: ${finding.snippet}`)
    }
  } else if (finding.ruleId === 'WORKSPACE_SANDBOX_READ_ONLY' && policy.sandboxMode) {
    parts.push(`sandboxMode=${policy.sandboxMode}`)
  } else if (finding.ruleId === 'WORKSPACE_NETWORK_POLICY' && finding.matchedPattern) {
    parts.push(`networkPolicy=${finding.matchedPattern}`)
  } else if (finding.ruleId === 'WORKSPACE_APPROVAL_STRICT' && policy.approvalPolicy) {
    parts.push(`approvalPolicy=${policy.approvalPolicy}`)
  }

  if (!parts.length && deniedPaths.length && finding.ruleId === 'WORKSPACE_DENIED_PATH') {
    parts.push(`${t('chat.approvalDeniedPathsLabel')}: ${deniedPaths.slice(0, 3).join(', ')}`)
  }

  if (!parts.length && metadata.workspaceBasePath) {
    parts.push(`${t('chat.approvalWorkspaceRootLabel')}: ${String(metadata.workspaceBasePath)}`)
  }
  return parts.join(' · ')
}

const approvalFindings = computed<ApprovalFindingView[]>(() => {
  const findings = pendingApproval.value?.findings || []
  return findings.slice(0, 3).map((finding: GuardFinding) => ({
    ruleId: finding.ruleId,
    title: finding.title,
    severity: finding.severity,
    description: finding.description,
    remediation: finding.remediation,
    context: formatWorkspacePolicyContext(finding),
  }))
})

const approvalWorkspaceBoundaryHint = computed(() => {
  const findings = pendingApproval.value?.findings || []
  const workspaceFinding = findings.find((finding: GuardFinding) =>
    finding.ruleId?.startsWith('WORKSPACE_') || finding.category === 'PATH_TRAVERSAL'
  )
  if (!workspaceFinding) {
    return ''
  }
  const metadata = workspaceFinding.metadata || {}
  const workspaceRoot = metadata.workspaceBasePath ? String(metadata.workspaceBasePath) : ''
  const policy = metadata.workspacePolicy || {}
  const hints: string[] = []
  if (workspaceRoot) {
    hints.push(`${t('chat.approvalWorkspaceRootLabel')}: ${workspaceRoot}`)
  }
  if (policy.sandboxMode) {
    hints.push(`sandbox=${policy.sandboxMode}`)
  }
  if (policy.approvalPolicy) {
    hints.push(`approval=${policy.approvalPolicy}`)
  }
  return hints.join(' · ')
})

const executionPhaseLabel = computed(() => {
  if (pendingApproval.value?.status === 'pending_approval') {
    return 'Waiting for approval'
  }
  if (pendingApproval.value?.status === 'approved') {
    return 'Approved - Resuming'
  }
  if (planMeta.value) {
    const done = planMeta.value.stepResults?.filter(r => r?.status === 'completed').length || 0
    return `Plan-Execute (${done}/${planMeta.value.steps.length})`
  }
  if (toolCallsMeta.value.length) {
    const done = toolCallsMeta.value.filter(t => t.status === 'completed').length
    return `Tool Calls (${done}/${toolCallsMeta.value.length})`
  }
  return currentPhaseName.value
})

const showExecutionPanel = computed(() => {
  if (role.value !== 'assistant') return false
  // 审批卡片有独立的渲染区域，但 execution panel 也应该在审批阶段展示上下文
  return toolCallsMeta.value.length > 0 || !!planMeta.value
    || (isGenerating.value && parsedMetadata.value?.currentPhase)
    || !!pendingApproval.value
})

// 自动展开执行面板（工具调用时、审批时或计划创建时）
watch(toolCallsMeta, (calls) => {
  if (calls.length > 0 && isGenerating.value) {
    executionExpanded.value = true
  }
}, { deep: true })

watch(planMeta, (plan) => {
  if (plan && plan.steps?.length > 0 && isGenerating.value) {
    executionExpanded.value = true
  }
})

watch(pendingApproval, (approval) => {
  if (approval?.status === 'pending_approval') {
    executionExpanded.value = true
  }
})

// 生成结束后自动折叠（但审批等待中不折叠）
watch(isGenerating, (generating) => {
  if (!generating && executionExpanded.value && !pendingApproval.value) {
    executionExpanded.value = false
  }
})
</script>

<style scoped>
/* 分段式渲染容器 */
.segments-view {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 0;
}

.message-wrapper {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  max-width: 920px;
  margin-bottom: 6px;
}

.message-wrapper.user {
  flex-direction: row-reverse;
  margin-left: auto;
}

.message-wrapper.assistant {
  margin-right: auto;
}

/* 头像 */
.msg-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}

.assistant-avatar {
  background: transparent;
}

.avatar-logo {
  width: 30px;
  height: 30px;
  object-fit: contain;
  border-radius: 50%;
}

.user-avatar {
  background: linear-gradient(135deg, var(--mc-success), #3D7A3D);
  color: white;
  font-size: 14px;
  font-weight: 600;
}

/* 消息体 */
.msg-body {
  max-width: calc(100% - 44px);
  min-width: 0;
}

.user-body {
  align-items: flex-end;
  display: flex;
  flex-direction: column;
}

/* 气泡 */
.msg-bubble {
  padding: 14px 16px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.7;
  word-break: break-word;
}

.assistant-bubble {
  background: none;
  border: none;
  border-radius: 0;
  padding: 4px 0;
  color: var(--mc-assistant-bubble-color, #1e293b);
}

.user-bubble {
  background: var(--mc-user-bubble-bg, #D97757);
  color: var(--mc-user-bubble-color, white);
  border-radius: 18px 4px 18px 18px;
}

/* ==================== Thinking 面板 ==================== */
.thinking-section {
  margin-bottom: 12px;
}

.thinking-toggle {
  width: 100%;
  border: 0;
  background: var(--mc-thinking-bg, rgba(217, 119, 87, 0.06));
  border-radius: 10px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mc-thinking-text, #475569);
  cursor: pointer;
  transition: background 0.15s ease;
  font-family: inherit;
}

.thinking-toggle:hover {
  background: var(--mc-thinking-hover, rgba(217, 119, 87, 0.1));
}

.thinking-toggle__indicator {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--mc-thinking-icon-bg, rgba(217, 119, 87, 0.12));
  color: var(--mc-primary, #D97757);
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.thinking-toggle__indicator.active {
  animation: think-pulse 1.5s ease-in-out infinite;
}

@keyframes think-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.92); }
}

.thinking-toggle__label {
  font-size: 13px;
  font-weight: 600;
  flex: 1;
  text-align: left;
}

.thinking-toggle__duration {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
  font-weight: 400;
}

.thinking-toggle__arrow {
  display: flex;
  align-items: center;
  color: var(--mc-text-tertiary, #94a3b8);
  transition: transform 0.2s ease;
}

.thinking-toggle__arrow.expanded {
  transform: rotate(180deg);
}

/* Thinking 内容折叠动画 */
.thinking-slide-enter-active,
.thinking-slide-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}

.thinking-slide-enter-from,
.thinking-slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.thinking-slide-enter-to,
.thinking-slide-leave-from {
  opacity: 1;
  max-height: 2000px;
}

.thinking-content {
  padding: 10px 14px 6px;
  color: var(--mc-text-secondary, #64748b);
  font-size: 13px;
  line-height: 1.65;
  border-left: 2px solid var(--mc-thinking-border, rgba(217, 119, 87, 0.2));
  margin-left: 12px;
  margin-top: 8px;
}

.thinking-content :deep(*) {
  max-width: 100%;
}

/* ==================== 执行过程面板 ==================== */
.execution-section {
  margin-bottom: 12px;
}

.execution-toggle {
  width: 100%;
  border: 0;
  background: var(--mc-thinking-bg, rgba(217, 119, 87, 0.06));
  border-radius: 10px;
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mc-thinking-text, #475569);
  cursor: pointer;
  transition: background 0.15s ease;
  font-family: inherit;
  font-size: 13px;
}

.execution-toggle:hover {
  background: var(--mc-thinking-hover, rgba(217, 119, 87, 0.1));
}

.execution-toggle__indicator {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--mc-thinking-icon-bg, rgba(217, 119, 87, 0.12));
  color: var(--mc-primary, #D97757);
  flex-shrink: 0;
}

.execution-toggle__indicator.active {
  animation: think-pulse 1.5s ease-in-out infinite;
}

.execution-toggle__label {
  font-weight: 600;
  flex: 1;
  text-align: left;
}

.execution-toggle__count {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.execution-toggle__arrow {
  display: flex;
  align-items: center;
  color: var(--mc-text-tertiary, #94a3b8);
  transition: transform 0.2s ease;
}

.execution-toggle__arrow.expanded {
  transform: rotate(180deg);
}

.execution-content {
  padding: 10px 14px 6px;
  margin-top: 8px;
  border-left: 2px solid rgba(217, 119, 87, 0.2);
  margin-left: 12px;
}

.tool-calls {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tool-call {
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 12px;
  background: var(--mc-bg-elevated, #f8fafc);
}

.tool-call[open] {
  padding-bottom: 8px;
}

.tool-call--running {
  background: rgba(217, 119, 87, 0.06);
}

.tool-call--awaiting {
  background: rgba(245, 158, 11, 0.06);
}

.tool-call--error {
  background: rgba(239, 68, 68, 0.06);
}

.tool-call__summary {
  display: flex;
  align-items: center;
  gap: 8px;
  list-style: none;
  cursor: default;
}

.tool-call__summary::-webkit-details-marker {
  display: none;
}

.tool-call--expandable .tool-call__summary {
  cursor: pointer;
}

.tool-call__status {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.tc-icon--warning { color: var(--mc-warning, #f59e0b); }
.tc-icon--success { color: var(--mc-success, #10b981); }
.tc-icon--error { color: var(--mc-danger, #ef4444); }

.tool-call__name {
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
}

.tool-call__args {
  color: var(--mc-text-tertiary, #94a3b8);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}

.tool-call__result-hint {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.tool-call__arrow {
  color: var(--mc-text-tertiary, #94a3b8);
  transition: transform 0.2s ease;
}

.tool-call[open] .tool-call__arrow {
  transform: rotate(180deg);
}

.tool-call__result {
  margin: 8px 0 0 24px;
  padding: 8px 10px;
  background: var(--mc-bg-sunken, #f1f5f9);
  border-radius: 6px;
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  color: var(--mc-text-secondary, #475569);
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
  line-height: 1.5;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

/* plan-steps 样式已迁移到 PlanStepsPanel.vue 组件 */

.execution-empty {
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
  padding: 4px 0;
}

/* ==================== Review 面板 ==================== */
.review-section {
  margin-bottom: 12px;
}

.review-toggle {
  width: 100%;
  border: 0;
  background: color-mix(in srgb, var(--mc-primary, #D97757) 5%, var(--mc-bg-elevated, #fff));
  border-radius: 10px;
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--mc-text-secondary, #475569);
  cursor: pointer;
  transition: background 0.15s ease;
  font-family: inherit;
  font-size: 13px;
}

.review-toggle:hover {
  background: color-mix(in srgb, var(--mc-primary, #D97757) 9%, var(--mc-bg-elevated, #fff));
}

.review-toggle__indicator {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: rgba(217, 119, 87, 0.12);
  color: var(--mc-primary, #D97757);
  flex-shrink: 0;
}

.review-toggle__label {
  font-weight: 600;
  flex: 1;
  text-align: left;
}

.review-toggle__count {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.review-toggle__arrow {
  display: flex;
  align-items: center;
  color: var(--mc-text-tertiary, #94a3b8);
  transition: transform 0.2s ease;
}

.review-toggle__arrow.expanded {
  transform: rotate(180deg);
}

.review-content {
  padding: 10px 14px 6px;
  margin-top: 8px;
  border-left: 2px solid rgba(217, 119, 87, 0.2);
  margin-left: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-block__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-secondary, #64748b);
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.review-block__count {
  font-size: 11px;
  font-weight: 500;
  color: var(--mc-text-tertiary, #94a3b8);
}

.review-file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-file-item {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
}

.review-file-item__main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.review-file-item__badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.review-file-item__badge.is-added {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.review-file-item__badge.is-modified {
  color: var(--mc-primary, #D97757);
  background: rgba(217, 119, 87, 0.1);
}

.review-file-item__badge.is-success {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.review-file-item__badge.is-danger {
  color: var(--mc-danger, #ef4444);
  background: rgba(239, 68, 68, 0.1);
}

.review-file-item__badge.is-warning {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.review-file-item__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--mc-text-primary, #1e293b);
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
  font-size: 12px;
}

.review-file-item__meta {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.review-file-item__summary {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mc-text-secondary, #64748b);
  line-height: 1.5;
}

.review-project-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-stats__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  font-size: 12px;
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  background: var(--mc-bg-elevated, #f8fafc);
}

.review-stats__label {
  color: var(--mc-text-secondary, #64748b);
}

.review-stats__value {
  min-width: 18px;
  text-align: center;
  font-weight: 700;
  color: var(--mc-text-primary, #1e293b);
}

.review-stats__item.is-added {
  border-color: rgba(16, 185, 129, 0.18);
}

.review-stats__item.is-modified {
  border-color: rgba(217, 119, 87, 0.18);
}

.review-stats__item.is-deleted {
  border-color: rgba(239, 68, 68, 0.18);
}

.review-stats__item.is-renamed {
  border-color: rgba(245, 158, 11, 0.2);
}

.review-stats__item.is-untracked {
  border-color: rgba(148, 163, 184, 0.22);
}

.review-project-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--mc-bg-muted, #f9f7f5);
  font-size: 12px;
  color: var(--mc-text-secondary, #64748b);
  max-width: 100%;
}

.review-project-pill__status {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--mc-text-tertiary, #94a3b8);
}

.review-project-pill__status.is-added {
  background: var(--mc-success, #10b981);
}

.review-project-pill__status.is-modified {
  background: var(--mc-primary, #D97757);
}

.review-project-pill__status.is-deleted {
  background: var(--mc-danger, #ef4444);
}

.review-project-pill__status.is-renamed {
  background: var(--mc-warning, #f59e0b);
}

.review-project-pill__status.is-untracked {
  background: var(--mc-text-tertiary, #94a3b8);
}

.review-project-pill__badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.review-project-pill__badge.is-added {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.review-project-pill__badge.is-modified {
  color: var(--mc-primary, #D97757);
  background: rgba(217, 119, 87, 0.1);
}

.review-project-pill__badge.is-deleted {
  color: var(--mc-danger, #ef4444);
  background: rgba(239, 68, 68, 0.1);
}

.review-project-pill__badge.is-renamed {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.review-project-pill__badge.is-untracked {
  color: var(--mc-text-secondary, #64748b);
  background: rgba(148, 163, 184, 0.12);
}

.review-project-pill__path {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
}

.review-file-row {
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
  border-radius: 10px;
  background: var(--mc-bg-elevated, #f8fafc);
}

.review-file-row--actionable {
  overflow: hidden;
}

.review-file-row__summary {
  list-style: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  cursor: pointer;
}

.review-file-row__summary--button {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  font: inherit;
}

.review-file-row__summary::-webkit-details-marker {
  display: none;
}

.review-file-row__path {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--mc-font-mono, 'SF Mono', 'Menlo', 'Consolas', monospace);
  font-size: 12px;
  color: var(--mc-text-primary, #1e293b);
}

.review-file-row__meta-inline {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.review-file-row__body {
  padding: 0 10px 10px;
}

.review-checkpoint {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px solid var(--mc-border-light, rgba(148, 163, 184, 0.18));
}

.review-checkpoint__badge {
  width: fit-content;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.review-checkpoint.is-supported .review-checkpoint__badge {
  color: var(--mc-success, #10b981);
  background: rgba(16, 185, 129, 0.1);
}

.review-checkpoint.is-unsupported .review-checkpoint__badge {
  color: var(--mc-warning, #f59e0b);
  background: rgba(245, 158, 11, 0.12);
}

.review-checkpoint__reason {
  font-size: 12px;
  color: var(--mc-text-secondary, #64748b);
  line-height: 1.5;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ==================== parse_error card ==================== */
.parse-error-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--mc-warning, #f59e0b) 8%, var(--mc-bg-elevated, #f8fafc));
  border: 1px solid color-mix(in srgb, var(--mc-warning, #f59e0b) 25%, transparent);
  font-size: 13px;
  line-height: 1.5;
  color: var(--mc-text-secondary, #64748b);
}

.parse-error-card__icon {
  flex-shrink: 0;
  color: var(--mc-warning, #f59e0b);
  margin-top: 1px;
}

.parse-error-card__text {
  word-break: break-word;
}

.empty-result-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.empty-result-card__icon {
  flex-shrink: 0;
  margin-top: 1px;
}

.empty-result-card__text {
  word-break: break-word;
}

/* ==================== 审批面板 ==================== */
/* 极简审批状态（一行式） */
.approval-inline {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--mc-text-secondary, #64748b);
  background: var(--mc-bg-muted, #f9f7f5);
  border-radius: 8px;
  border: 1px solid rgba(245, 158, 11, 0.18);
}

.approval-inline__header {
  display: flex;
  align-items: center;
  gap: 6px;
}

.approval-inline__severity {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.approval-inline__icon {
  color: var(--mc-warning, #f59e0b);
  flex-shrink: 0;
}

.approval-inline__summary {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  line-height: 1.5;
}

.approval-inline__summary--muted {
  color: var(--mc-text-tertiary, #94a3b8);
}

.approval-inline__label {
  font-weight: 600;
  color: var(--mc-text-primary, #334155);
}

.approval-inline__findings {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.approval-inline__finding {
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.approval-inline__finding-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.approval-inline__finding-title {
  font-weight: 600;
  color: var(--mc-text-primary, #334155);
}

.approval-inline__finding-severity {
  margin-left: auto;
  color: var(--mc-text-tertiary, #94a3b8);
  font-size: 11px;
  font-weight: 700;
}

.approval-inline__finding-desc,
.approval-inline__finding-context,
.approval-inline__finding-remediation {
  margin-top: 4px;
  line-height: 1.5;
}

.approval-inline__finding-context {
  color: var(--mc-text-tertiary, #94a3b8);
}

.approval-inline__finding-remediation {
  color: var(--mc-text-secondary, #64748b);
}

.approval-inline__text code {
  font-size: 12px;
  background: var(--mc-inline-code-bg, #f1f5f9);
  padding: 1px 5px;
  border-radius: 4px;
  font-weight: 500;
}

.approval-inline--approved {
  color: var(--mc-success, #10b981);
}
.approval-inline--approved .approval-inline__icon {
  color: var(--mc-success, #10b981);
}

.approval-inline--denied {
  color: var(--mc-danger, #ef4444);
}
.approval-inline--denied .approval-inline__icon {
  color: var(--mc-danger, #ef4444);
}

.approval-inline.is-approved {
  border-color: rgba(16, 185, 129, 0.18);
}

.approval-inline.is-denied {
  border-color: rgba(239, 68, 68, 0.18);
}

.approval-severity-critical .approval-inline__severity {
  background: rgba(239, 68, 68, 0.14);
  color: #b91c1c;
}

.approval-severity-high .approval-inline__severity {
  background: rgba(249, 115, 22, 0.14);
  color: #c2410c;
}

/* ==================== 操作栏 ==================== */
.teacher-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.teacher-result__section {
  border: 1px solid var(--mc-border-light, #e2e8f0);
  border-radius: 8px;
  background: var(--mc-bg-elevated, #f8fafc);
  overflow: hidden;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.teacher-result__section.is-primary {
  border-color: color-mix(in srgb, var(--mc-primary, #D97757) 28%, transparent);
  background: color-mix(in srgb, var(--mc-primary, #D97757) 10%, var(--mc-bg-elevated, #f8fafc));
}

.teacher-result__summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
  padding: 10px 12px;
  cursor: pointer;
  list-style: none;
  font-weight: 650;
  color: var(--mc-text-primary, #1e293b);
}

.teacher-result__summary::-webkit-details-marker {
  display: none;
}

.teacher-result__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.teacher-result__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.teacher-result__action,
.teacher-result__export button {
  border: 1px solid var(--mc-border-light, #dbe3ef);
  background: var(--mc-bg-elevated, #f8fafc);
  color: var(--mc-text-secondary, #475569);
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
}

.teacher-result__action:hover,
.teacher-result__export button:hover {
  border-color: color-mix(in srgb, var(--mc-primary, #D97757) 42%, transparent);
  background: color-mix(in srgb, var(--mc-primary, #D97757) 12%, var(--mc-bg-elevated, #f8fafc));
  color: var(--mc-primary, #D97757);
}

.teacher-result__body {
  border-top: 1px solid var(--mc-border-light, #e2e8f0);
  padding: 12px;
  background: var(--mc-bg-elevated, #f8fafc);
}

.teacher-result__internal {
  border: 1px dashed var(--mc-border-light, #dbe3ef);
  border-radius: 8px;
  background: color-mix(in srgb, var(--mc-bg-elevated, #f8fafc) 84%, transparent);
  color: var(--mc-text-secondary, #64748b);
  padding: 8px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.teacher-result__internal > summary {
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
  color: var(--mc-text-secondary, #64748b);
  list-style-position: inside;
}

.teacher-result__internal .teacher-result__section {
  margin-top: 8px;
  background: var(--mc-bg-elevated, #f8fafc);
}

.teacher-result__status {
  align-self: flex-start;
  border: 1px solid rgba(217, 119, 87, 0.2);
  border-radius: 999px;
  background: color-mix(in srgb, var(--mc-primary, #D97757) 9%, var(--mc-bg-elevated, #f8fafc));
  color: var(--mc-primary, #D97757);
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 650;
}

.teacher-result__export {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.generated-file-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 10px;
}

.generated-file-link {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 4px 10px;
  gap: 6px;
  border: 1px solid var(--mc-border-light, #dbe3ef);
  border-radius: 6px;
  background: var(--mc-bg-elevated, #f8fafc);
  font-size: 12px;
}

.generated-file-link__icon {
  color: var(--mc-text-secondary, #8a9ab0);
  flex-shrink: 0;
}

.generated-file-link__name {
  flex: 1;
  color: var(--mc-text-primary, #1a2332);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.generated-file-link__action {
  flex-shrink: 0;
  border: 0;
  background: transparent;
  color: var(--mc-primary, #D97757);
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  text-decoration: none;
  padding: 2px 6px;
  border-radius: 4px;
  transition: background 0.15s;
}

.generated-file-link__action:hover {
  background: var(--mc-primary-light, rgba(217, 119, 87, 0.08));
}

.review-file-list--generated {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.review-generated-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 28px;
  padding: 2px 0;
}

.review-generated-row--button {
  width: 100%;
  border: 0;
  background: transparent;
  padding: 4px 0;
  text-align: left;
  cursor: pointer;
}

.review-generated-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--mc-text-primary, #1a2332);
}

.review-generated-row__action {
  color: var(--mc-primary, #D97757);
  font-weight: 600;
  font-size: 12px;
  text-decoration: none;
  padding: 0;
}

.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 0 0 4px;
  /* 始终占位，防止出现/消失时引起布局抖动 */
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.15s ease;
}

.msg-actions--visible {
  opacity: 1;
  pointer-events: auto;
}

.msg-actions--right {
  justify-content: flex-end;
  padding: 4px 4px 0 0;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--mc-text-tertiary, #94a3b8);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action-btn:hover {
  background: var(--mc-bg-tertiary, rgba(0, 0, 0, 0.05));
  color: var(--mc-text-secondary, #64748b);
}

.action-btn.copied {
  color: #10b981;
}
.action-btn.tts-playing {
  color: var(--mc-primary);
}
.tts-loading-icon {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.action-time {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
  margin-left: 4px;
  user-select: none;
}

/* ==================== 主内容区域 ==================== */
.msg-content {
  position: relative;
  width: 100%;
  min-width: 0;
}

.msg-content.with-cursor {
  display: inline;
}

/* 状态指示器 */
.stopped-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0 2px;
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
}

/* 错误卡片 */
.error-card {
  margin-top: 8px;
  padding: 12px 16px;
  border-radius: 8px;
  background: var(--mc-danger-bg);
  border: 1px solid color-mix(in srgb, var(--mc-danger) 25%, transparent);
  font-size: 13px;
  max-width: 480px;
  line-height: 1.5;
}

.error-card__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.error-card__icon {
  flex-shrink: 0;
  color: var(--mc-danger);
}

.error-card__title {
  font-weight: 600;
  color: var(--mc-danger);
  font-size: 14px;
}

.error-card__description {
  margin: 4px 0;
  color: var(--mc-text-primary);
  font-size: 13px;
  opacity: 0.85;
}

.error-card__action {
  margin: 4px 0 8px;
  color: var(--mc-text-secondary);
  font-size: 12px;
}

.error-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.error-card__code {
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, monospace;
  font-size: 11px;
  color: var(--mc-danger);
  opacity: 0.6;
}

.error-card__retry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 6px;
  border: 1px solid color-mix(in srgb, var(--mc-danger) 30%, transparent);
  background: color-mix(in srgb, var(--mc-danger) 8%, var(--mc-bg-elevated));
  color: var(--mc-danger);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}

.error-card__retry:hover {
  background: color-mix(in srgb, var(--mc-danger) 15%, var(--mc-bg-elevated));
  border-color: color-mix(in srgb, var(--mc-danger) 50%, transparent);
}

/* ==================== 附件 ==================== */
.message-attachments {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.message-attachment-image {
  border-radius: 12px;
  overflow: hidden;
}

.message-attachment-image img {
  max-width: 280px;
  max-height: 200px;
  border-radius: 12px;
  cursor: pointer;
  object-fit: cover;
}

.message-attachment-image__name {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.76;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-attachment-video {
  border-radius: 12px;
  overflow: hidden;
}

.message-attachment-video video {
  max-width: 400px;
  max-height: 280px;
  border-radius: 12px;
}

.message-attachment-video__name {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  opacity: 0.76;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-attachment {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  color: inherit;
  text-decoration: none;
  border: none;
  font: inherit;
  cursor: pointer;
  width: 100%;
}

.user-bubble .message-attachment {
  background: rgba(255, 255, 255, 0.2);
}

.message-attachment__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.message-attachment__meta {
  flex-shrink: 0;
  opacity: 0.76;
  font-size: 12px;
}

.message-attachment__icon {
  flex-shrink: 0;
  opacity: 0.76;
}

/* ==================== Markdown 样式 ==================== */
.markdown-body :deep(p) {
  margin: 0 0 10px;
  line-height: 1.7;
}

.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 10px 0;
  padding-left: 24px;
}

.markdown-body :deep(ul) { list-style-type: disc; }
.markdown-body :deep(ol) { list-style-type: decimal; }

.markdown-body :deep(li) {
  margin: 4px 0;
  line-height: 1.7;
}

.markdown-body :deep(li > ul),
.markdown-body :deep(li > ol) {
  margin: 4px 0;
}

.markdown-body :deep(input[type="checkbox"]) {
  margin-right: 8px;
  vertical-align: middle;
}

.markdown-body :deep(blockquote) {
  margin: 14px 0;
  padding: 12px 16px;
  border-left: 4px solid var(--mc-primary, #D97757);
  background: var(--mc-bg-elevated, #f8fafc);
  border-radius: 0 8px 8px 0;
  color: var(--mc-text-secondary, #64748b);
}

.markdown-body :deep(blockquote p) { margin: 0; }

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0;
  font-size: 14px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 10px 12px;
  border: 1px solid var(--mc-border, #e2e8f0);
  text-align: left;
}

.markdown-body :deep(th) {
  background: var(--mc-bg-elevated, #f8fafc);
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
}

.markdown-body :deep(tr:nth-child(even)) {
  background: var(--mc-bg-sunken, #f1f5f9);
}

.markdown-body :deep(a) {
  color: var(--mc-primary, #D97757);
  text-decoration: none;
  border-bottom: 1px solid transparent;
  transition: border-color 0.15s ease;
}

.markdown-body :deep(a:hover) {
  border-bottom-color: var(--mc-primary, #D97757);
}

.user-bubble .markdown-body :deep(a) {
  color: var(--mc-user-bubble-color, white);
  border-bottom: 1px solid rgba(255, 255, 255, 0.5);
}

.user-bubble .markdown-body :deep(a:hover) {
  border-bottom-color: var(--mc-user-bubble-color, white);
}

.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--mc-border, #e2e8f0);
  margin: 20px 0;
}

.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4),
.markdown-body :deep(h5),
.markdown-body :deep(h6) {
  margin: 20px 0 12px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--mc-text-primary, #1e293b);
}

.markdown-body :deep(h1) { font-size: 1.5em; }
.markdown-body :deep(h2) { font-size: 1.3em; }
.markdown-body :deep(h3) { font-size: 1.15em; }
.markdown-body :deep(h4) { font-size: 1em; }

/* 代码块 */
.markdown-body :deep(pre) {
  background: var(--mc-code-bg, #1e293b);
  border-radius: 12px;
  padding: 16px;
  overflow-x: auto;
  margin: 14px 0;
}

.markdown-body :deep(code) {
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
}

.markdown-body :deep(pre code) {
  color: #e2e8f0;
  background: transparent;
  padding: 0;
}

.markdown-body :deep(:not(pre) > code) {
  background: var(--mc-inline-code-bg, #f1f5f9);
  color: var(--mc-inline-code-color, #ef4444);
  padding: 2px 6px;
  border-radius: 6px;
  font-size: 0.92em;
}

/* Code-block CSS lives globally in main.css now (.markdown-body .code-block*)
   so the rules apply consistently across MessageBubble, AgentContext, and
   any future markdown-body context, and don't depend on Vue's per-component
   scope hash. Keep this comment as a breadcrumb so future edits don't get
   re-added here by reflex. */

/* ===== Mermaid block ===== */
.markdown-body :deep(.mermaid-block) {
  margin: 14px 0;
  padding: 16px;
  border-radius: 12px;
  background: var(--mc-mermaid-bg, #f8fafc);
  border: 1px solid var(--mc-mermaid-border, #e2e8f0);
  text-align: center;
  overflow-x: auto;
}
.markdown-body :deep(.mermaid-block svg) {
  max-width: 100%;
  height: auto;
}
.markdown-body :deep(.mermaid-block.mermaid-error) {
  background: #fef2f2;
  border-color: #fecaca;
  color: #b91c1c;
  text-align: left;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

/* ===== KaTeX inline / block ===== */
.markdown-body :deep(.katex-inline) {
  font-size: 1em;
}
.markdown-body :deep(.katex-block) {
  display: block;
  margin: 12px 0;
  text-align: center;
  overflow-x: auto;
}
.markdown-body :deep(.katex-error) {
  color: #b91c1c;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.92em;
}

.markdown-body :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 10px 0;
}

.markdown-body :deep(del),
.markdown-body :deep(s) {
  text-decoration: line-through;
  opacity: 0.7;
}

.markdown-body :deep(strong),
.markdown-body :deep(b) {
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .message-wrapper {
    max-width: 100%;
    gap: 8px;
  }

  .msg-body {
    max-width: calc(100% - 40px);
  }

  .msg-avatar {
    width: 28px;
    height: 28px;
    font-size: 12px;
  }

  .error-card {
    max-width: 100%;
  }
}
</style>
