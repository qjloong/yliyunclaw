<template>
  <!-- MetaY custom: 自定义会话聊天首页（副标题 + 快捷聊天入口） -->
  <div class="chat-home">
    <p v-if="subtitle" class="chat-home__subtitle">{{ subtitle }}</p>
    <div v-if="quickStarts.length" class="chat-home__quick-starts">
      <button
        v-for="(q, i) in quickStarts"
        :key="i"
        class="chat-home__chip"
        :title="q.prompt"
        @click="$emit('select', q.prompt)"
      >
        <span class="chat-home__chip-icon">
          <el-icon><Promotion /></el-icon>
        </span>
        <span class="chat-home__chip-text">{{ q.title }}</span>
      </button>
    </div>
    <p v-else class="chat-home__hint">{{ $t('chat.home.emptyQuickStarts') }}</p>
  </div>
</template>

<script setup lang="ts">
import { Promotion } from '@element-plus/icons-vue'
import type { AgentHomeQuickStart } from '@/types'

interface Props {
  /** 首页副标题 */
  subtitle?: string
  /** 快捷聊天入口 */
  quickStarts: AgentHomeQuickStart[]
}

withDefaults(defineProps<Props>(), {
  subtitle: '',
  quickStarts: () => [],
})

const emit = defineEmits<{
  select: [prompt: string]
}>()
</script>

<style scoped>
.chat-home {
  width: 100%;
  max-width: 720px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.chat-home__subtitle {
  font-size: 14px;
  color: var(--mc-text-secondary, #64748b);
  margin: 0 0 4px;
  max-width: 480px;
  line-height: 1.6;
  text-align: center;
}

.chat-home__quick-starts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  width: 100%;
}

.chat-home__chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px solid var(--mc-border, #e2e8f0);
  border-radius: 12px;
  font-size: 13px;
  color: var(--mc-text-primary, #1e293b);
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.chat-home__chip:hover {
  border-color: var(--mc-primary, #D97757);
  background: var(--mc-primary-bg, rgba(217, 119, 87, 0.06));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(217, 119, 87, 0.08);
}

.chat-home__chip-icon {
  font-size: 16px;
  flex-shrink: 0;
  color: var(--mc-primary, #D97757);
}

.chat-home__chip-text {
  flex: 1;
  overflow: hidden;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.chat-home__hint {
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
  margin: 0;
}

@media (max-width: 768px) {
  .chat-home__quick-starts {
    grid-template-columns: 1fr;
  }
}
</style>
