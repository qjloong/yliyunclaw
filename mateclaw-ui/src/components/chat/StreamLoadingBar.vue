<template>
  <div v-if="isLoading && !hideBar" class="stream-loading-bar">
    <div class="stream-loading-content">
      <span class="loading-icon" :class="phaseIconClass">{{ phaseIcon }}</span>
      <div class="loading-copy">
        <span class="loading-text" :class="phaseTextClass">{{ statusText }}</span>
        <span v-if="runningToolName" class="loading-tool">{{ runningToolName }}</span>
      </div>
    </div>
    <div class="loading-right">
      <!-- 排队指示器 -->
      <span v-if="hasQueued" class="queued-badge">
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
          <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
        </svg>
        {{ t('chat.queuedBadge', { count: 1 }) }}
      </span>
      <div v-if="showStats" class="loading-stats">
        <span class="stat">{{ elapsedTime }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PhaseEventData, StreamPhase } from '@/types'

interface LifecycleStage {
  stage: 'connecting' | 'started' | 'context_prepared' | 'llm_request_sent' | 'streaming'
  detail?: any
  since: number
}

interface CompactStatus {
  status: 'start' | 'pair_safe' | 'summarize' | 'done' | 'skipped' | 'failed'
  preTokens?: number
  postTokens?: number
  messagesIn?: number
  messagesSummarized?: number
  tailKept?: number
  toolResultsSpilled?: number
  reason?: string
  anchored?: boolean
  fromCache?: boolean
  movedFrom?: number
  movedTo?: number
  summaryBudget?: number
  trigger?: string
  fallbackKept?: number
  timestamp?: number
}

interface Props {
  isLoading: boolean
  toolCount?: number
  hideBar?: boolean
  showStats?: boolean
  completionTokens?: number
  promptTokens?: number
  /** 当前流阶段 */
  phase?: StreamPhase
  /** 最近一次阶段事件 */
  phaseInfo?: PhaseEventData | null
  /** 当前正在执行的工具名称 */
  runningToolName?: string
  /** 是否有排队消息 */
  hasQueued?: boolean
  /** 首 token 前的细粒度生命周期状态 */
  lifecycleStage?: LifecycleStage | null
  /** 最近一次上下文压缩状态 */
  compactStatus?: CompactStatus | null
}

const props = withDefaults(defineProps<Props>(), {
  toolCount: 0,
  hideBar: false,
  showStats: true,
  completionTokens: 0,
  promptTokens: 0,
  phase: 'thinking',
  phaseInfo: null,
  runningToolName: '',
  hasQueued: false,
  lifecycleStage: null,
  compactStatus: null,
})

const { t } = useI18n()

const lifecycleI18nMap: Record<string, string> = {
  connecting: 'chat.streamConnecting',
  started: 'chat.streamStarted',
  context_prepared: 'chat.streamContextPrepared',
  llm_request_sent: 'chat.streamLlmRequestSent',
}

const compactStatusText = computed(() => {
  const compact = props.compactStatus
  if (!compact) return ''
  switch (compact.status) {
    case 'start':
      return compact.preTokens
        ? t('chat.compactStartWithTokens', { tokens: formatTokens(compact.preTokens) })
        : t('chat.compactStart')
    case 'pair_safe':
      return t('chat.compactPairSafe')
    case 'summarize': {
      const count = compact.messagesSummarized ?? compact.messagesIn ?? 0
      return count > 0
        ? t('chat.compactSummarizeWithCount', { count })
        : t('chat.compactSummarize')
    }
    default:
      return ''
  }
})

const isCompactActive = computed(() => {
  const status = props.compactStatus?.status
  return status === 'start' || status === 'pair_safe' || status === 'summarize'
})

const inPreTokenWindow = computed(() => {
  const stage = props.lifecycleStage
  return !!stage && stage.stage !== 'streaming'
})

const nowTs = ref(Date.now())
const stageTickSec = ref(0)

const recentCompactResultText = computed(() => {
  const compact = props.compactStatus
  if (!compact || !compact.timestamp) return ''
  const age = nowTs.value - compact.timestamp
  if (age > 2500) return ''

  switch (compact.status) {
    case 'done':
      if (compact.preTokens && compact.postTokens) {
        return t('chat.compactDoneWithTokens', {
          before: formatTokens(compact.preTokens),
          after: formatTokens(compact.postTokens),
        })
      }
      return t('chat.compactDone')
    case 'skipped':
      if (compact.reason === 'insufficient_messages') {
        return t('chat.compactSkippedInsufficient')
      }
      if (compact.reason === 'pair_boundary_collapsed') {
        return t('chat.compactSkippedPairBoundary')
      }
      return t('chat.compactSkipped')
    case 'failed':
      return t('chat.compactFailed')
    default:
      return ''
  }
})

function formatTokens(value: number): string {
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k tokens`
  return `${value} tokens`
}

// 将 14 个内部阶段映射为 3 个面向用户的状态：思考中 / 执行中 / 撰写中
const userFacingPhase = computed(() => {
  switch (props.phase) {
    case 'preparing_context':
    case 'reading_memory':
    case 'reasoning':
    case 'thinking':
    case 'summarizing_observations':
    case 'queued':
    case 'reconnecting':
      return 'thinking'
    case 'executing_tool':
    case 'awaiting_approval':
      return 'working'
    case 'drafting_answer':
    case 'streaming':
    case 'finalizing':
      return 'writing'
    case 'failed':
      return 'failed'
    case 'interrupting':
    case 'stopped':
      return 'stopped'
    default:
      return 'thinking'
  }
})

const userPhaseI18nMap: Record<string, string> = {
  thinking: 'chat.streamThinking',
  working: 'chat.streamExecutingTool',
  writing: 'chat.streamGenerating',
  failed: 'chat.streamFailed',
  stopped: 'chat.streamStopped',
}

const statusText = computed(() => {
  if (isCompactActive.value) {
    const text = compactStatusText.value
    if (text) return text
  }
  if (inPreTokenWindow.value && props.lifecycleStage) {
    const key = lifecycleI18nMap[props.lifecycleStage.stage]
    if (key) {
      const base = t(key)
      if (stageTickSec.value >= 5) {
        return base + t('chat.streamElapsedSuffix', { sec: stageTickSec.value })
      }
      return base
    }
  }
  if (recentCompactResultText.value) {
    return recentCompactResultText.value
  }
  const key = userPhaseI18nMap[userFacingPhase.value]
  if (!key) return ''
  return t(key)
})

const phaseIcon = computed(() => {
  switch (userFacingPhase.value) {
    case 'thinking': return '◐'
    case 'working': return '⚙'
    case 'writing': return '▸'
    case 'failed': return '!'
    case 'stopped': return '⊘'
    default: return '◐'
  }
})

const phaseIconClass = computed(() => {
  switch (userFacingPhase.value) {
    case 'failed':
    case 'stopped':
      return 'icon-warning'
    default:
      return 'icon-active'
  }
})

const phaseTextClass = computed(() => {
  switch (userFacingPhase.value) {
    case 'failed':
    case 'stopped':
      return 'text-red'
    default:
      return ''
  }
})


// 耗时统计
const elapsedSeconds = ref(0)
const elapsedTime = ref('0s')


let timerInterval: ReturnType<typeof setInterval> | null = null

watch(() => props.isLoading, (loading) => {
  if (loading) {
    if (timerInterval) clearInterval(timerInterval)
    elapsedSeconds.value = 0
    elapsedTime.value = '0s'
    stageTickSec.value = 0

    timerInterval = setInterval(() => {
      nowTs.value = Date.now()
      elapsedSeconds.value += 1
      if (props.lifecycleStage) {
        stageTickSec.value = Math.floor((Date.now() - props.lifecycleStage.since) / 1000)
      }
      const secs = elapsedSeconds.value
      if (secs < 60) {
        elapsedTime.value = `${secs}s`
      } else {
        const mins = Math.floor(secs / 60)
        const remainSecs = secs % 60
        elapsedTime.value = `${mins}m ${remainSecs}s`
      }
    }, 1000)
  } else {
    if (timerInterval) {
      clearInterval(timerInterval)
      timerInterval = null
    }
    nowTs.value = Date.now()
    stageTickSec.value = 0
  }
}, { immediate: true })

watch(() => props.lifecycleStage?.since, () => {
  stageTickSec.value = 0
})


onBeforeUnmount(() => {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
})
</script>

<style scoped>
.stream-loading-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  font-size: 14px;
  animation: subtle-pulse 1.5s ease-in-out infinite;
  color: var(--mc-text-secondary, #64748b);
}

.stream-loading-content {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  color: var(--mc-primary, #d96d46);
}

.loading-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.loading-icon {
  font-size: 16px;
  font-weight: bold;
}

.icon-active {
  animation: icon-pulse 1.2s ease-in-out infinite;
  color: var(--mc-primary, #d96d46);
}

.icon-warning {
  color: var(--mc-danger, #ef4444);
  animation: none;
}

.loading-text {
  font-weight: 500;
  color: var(--mc-primary, #d96d46);
}

.text-red { color: var(--mc-danger, #ef4444); }

.loading-tool {
  align-self: flex-start;
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  background: var(--mc-primary-light, rgba(217, 119, 87, 0.1));
  padding: 1px 6px;
  border-radius: 4px;
  color: var(--mc-primary, #d96d46);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loading-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.queued-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--mc-info, #3b82f6);
  background: rgba(59, 130, 246, 0.08);
  padding: 2px 8px;
  border-radius: 10px;
}

.loading-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.stat {
  display: flex;
  align-items: center;
}

@keyframes icon-pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

@keyframes subtle-pulse {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.85;
  }
}
</style>
