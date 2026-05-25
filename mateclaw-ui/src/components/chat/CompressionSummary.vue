<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'
import type { Message } from '@/types'

const { t } = useI18n()

const props = defineProps<{
  message: Message
}>()

const expanded = ref(false)

const metadata = computed(() => {
  try {
    return typeof props.message.metadata === 'string'
      ? JSON.parse(props.message.metadata)
      : (props.message.metadata || {})
  } catch {
    return {}
  }
})

const compressedCount = computed(() => {
  return Number(metadata.value?.compressedCount || 0)
})

const detailItems = computed(() => {
  const items: Array<{ key: string; label: string; value: string }> = []
  const meta = metadata.value as Record<string, any>

  if (Number.isFinite(meta.preTokens) && Number.isFinite(meta.postTokens)) {
    items.push({
      key: 'tokens',
      label: t('chat.compressionTokens'),
      value: `${formatTokens(meta.preTokens)} → ${formatTokens(meta.postTokens)}`,
    })
  }

  if (Number.isFinite(meta.messagesSummarized)) {
    items.push({
      key: 'messages',
      label: t('chat.compressionMessages'),
      value: String(meta.messagesSummarized),
    })
  }

  if (Number.isFinite(meta.tailKept)) {
    items.push({
      key: 'tail',
      label: t('chat.compressionTailKept'),
      value: String(meta.tailKept),
    })
  }

  if (Number.isFinite(meta.toolResultsSpilled)) {
    items.push({
      key: 'spills',
      label: t('chat.compressionToolSpills'),
      value: String(meta.toolResultsSpilled),
    })
  }

  if (typeof meta.anchored === 'boolean') {
    items.push({
      key: 'anchored',
      label: t('chat.compressionAnchored'),
      value: meta.anchored ? t('common.yes') : t('common.no'),
    })
  }

  if (meta.trigger) {
    items.push({
      key: 'trigger',
      label: t('chat.compressionTrigger'),
      value: formatTrigger(meta.trigger),
    })
  }

  if (meta.summaryId) {
    items.push({
      key: 'summaryId',
      label: t('chat.compressionSummaryId'),
      value: String(meta.summaryId),
    })
  }

  return items
})

function formatTokens(value: number): string {
  if (!Number.isFinite(value)) return '0'
  if (value >= 1000) return `${(value / 1000).toFixed(1)}k`
  return String(value)
}

function formatTrigger(trigger: string): string {
  switch (trigger) {
    case 'token_threshold':
      return t('chat.compressionTriggerTokenThreshold')
    default:
      return trigger
  }
}
</script>

<template>
  <div class="seg-compression">
    <div class="seg-compression__header" @click="expanded = !expanded">
      <span class="seg-compression__icon">
        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="4 14 10 14 10 20"/><polyline points="20 10 14 10 14 4"/><line x1="14" y1="10" x2="21" y2="3"/><line x1="3" y1="21" x2="10" y2="14"/></svg>
      </span>
      <span v-if="compressedCount > 0" class="seg-compression__label">
        {{ t('chat.compressionWithCount', { count: compressedCount }) }}
      </span>
      <span v-else class="seg-compression__label">{{ t('chat.compressionSummary') }}</span>
      <el-icon class="seg-compression__arrow" :class="{ 'is-open': expanded }" :size="12"><ArrowDown /></el-icon>
    </div>
    <div v-if="expanded" class="seg-compression__body">
      <div v-if="detailItems.length" class="seg-compression__meta">
        <div v-for="item in detailItems" :key="item.key" class="seg-compression__meta-item">
          <span class="seg-compression__meta-label">{{ item.label }}</span>
          <span class="seg-compression__meta-value">{{ item.value }}</span>
        </div>
      </div>
      <div class="markdown-body">{{ message.content }}</div>
    </div>
  </div>
</template>

<style scoped>
.seg-compression {
  margin: 6px 0;
  border-radius: 8px;
  border: 1px dashed var(--mc-border);
  overflow: hidden;
}
.seg-compression__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  color: var(--mc-text-tertiary);
  user-select: none;
  transition: color 0.15s;
}
.seg-compression__header:hover {
  color: var(--mc-text-secondary);
  background: var(--mc-bg-muted);
}
.seg-compression__icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.seg-compression__label {
  flex: 1;
}
.seg-compression__arrow {
  color: var(--mc-text-tertiary);
  transition: transform 0.2s;
}
.seg-compression__arrow.is-open {
  transform: rotate(180deg);
}
.seg-compression__body {
  padding: 0 12px 10px;
  font-size: 13px;
  color: var(--mc-text-secondary);
  line-height: 1.6;
}
.seg-compression__meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
  margin: 2px 0 10px;
}
.seg-compression__meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--mc-bg-muted);
  border: 1px solid var(--mc-border);
}
.seg-compression__meta-label {
  font-size: 11px;
  color: var(--mc-text-tertiary);
}
.seg-compression__meta-value {
  font-size: 12px;
  color: var(--mc-text-primary);
  font-weight: 500;
  word-break: break-word;
}
</style>
