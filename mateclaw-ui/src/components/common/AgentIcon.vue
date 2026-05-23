<template>
  <span class="agent-icon" :style="{ color: toneColor }" :title="title">
    <SkillIcon :value="value" :size="size" :fallback="fallback" :title="title" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import SkillIcon from '@/components/common/SkillIcon.vue'
import { agentIconColor } from '@/utils/agentIconColor'

const props = withDefaults(defineProps<{
  value: string | null | undefined
  size?: number
  fallback?: string
  title?: string
}>(), {
  size: 20,
  fallback: '🤖',
  title: undefined,
})

const toneColor = computed(() => agentIconColor(props.value))
</script>

<style scoped>
.agent-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  flex-shrink: 0;
}
</style>