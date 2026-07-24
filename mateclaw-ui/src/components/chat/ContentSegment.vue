<script setup lang="ts">
import { computed } from 'vue'
import { useStreamingMarkdown } from '@/composables/useStreamingMarkdown'
import { linkifyGeneratedFileUrls } from '@/utils/generatedFileLinks'
import TypingCursor from './TypingCursor.vue'
import TeacherExamResultBlock from './TeacherExamResultBlock.vue'
import type { MessageSegment } from '@/types'

const props = withDefaults(defineProps<{
  segment: MessageSegment
  showCursor?: boolean
  /** id → filename map for rewriting bare generated-file URLs into [name](url). */
  generatedFileNames?: Map<string, string>
}>(), {
  showCursor: false,
  generatedFileNames: undefined,
})

const isRunning = computed(() => props.segment.status === 'running')

// === MetaY custom start === 检测 teacher_exam_result_v2 结构化载荷并渲染专属块
const teacherExamPayload = computed<Record<string, any> | null>(() => {
  const text = props.segment.text || ''
  if (!text.includes('teacher_exam_result_v2')) return null
  // 优先匹配 ```json 围栏块，否则尝试匹配裸 JSON 对象
  const fenced = text.match(/```json\s*([\s\S]*?)```/)
  const candidates = [fenced?.[1], text].filter(Boolean) as string[]
  for (const cand of candidates) {
    try {
      const obj = JSON.parse(cand)
      if (obj && obj.type === 'teacher_exam_result_v2') return obj as Record<string, any>
    } catch {
      // 继续尝试更宽松的对象匹配
      const loose = cand.match(/\{[\s\S]*"type"\s*:\s*"teacher_exam_result_v2"[\s\S]*\}/)
      if (loose) {
        try {
          const obj = JSON.parse(loose[0])
          if (obj?.type === 'teacher_exam_result_v2') return obj as Record<string, any>
        } catch { /* ignore */ }
      }
    }
  }
  return null
})
// === MetaY custom end ===

// Throttle markdown rendering while the segment streams; render once at full
// fidelity the moment it completes.
const { html: renderedContent } = useStreamingMarkdown(
  () => {
    const text = props.segment.text || ''
    return props.generatedFileNames?.size
      ? linkifyGeneratedFileUrls(text, props.generatedFileNames)
      : text
  },
  () => isRunning.value,
)
</script>

<template>
  <div class="seg-content">
    <!-- MetaY custom: Teacher 结构化结果块；命中则替代 Markdown 渲染 -->
    <TeacherExamResultBlock
      v-if="teacherExamPayload"
      :payload="teacherExamPayload"
    />
    <template v-else>
      <div class="markdown-body" v-html="renderedContent"></div>
      <TypingCursor v-if="isRunning && showCursor" />
    </template>
  </div>
</template>

<style scoped>
.seg-content {
  padding: 4px 0;
  margin-top: 4px;
}
</style>
