<template>
  <!-- MetaY custom: Teacher 结构化试卷结果块（teacher_exam_result_v2） -->
  <div class="teacher-exam-result">
    <div class="ter-header">
      <span class="ter-header__icon">📝</span>
      <div class="ter-header__titles">
        <span class="ter-header__module">{{ moduleLabel }}</span>
        <span v-if="paperTitle" class="ter-header__title">{{ paperTitle }}</span>
      </div>
      <button class="ter-header__copy" type="button" @click="copyAll">{{ $t('chat.copy') }}</button>
    </div>

    <!-- 试题 -->
    <section v-if="questions.length" class="ter-section">
      <h4 class="ter-section__title">{{ $t('teacher.exam.questions', '试题') }}（{{ questions.length }}）</h4>
      <div v-for="(q, i) in questions" :key="'q' + i" class="ter-item">
        <button class="ter-item__head" type="button" @click="toggle('q' + i)">
          <span class="ter-item__index">{{ i + 1 }}</span>
          <span class="ter-item__summary">{{ itemSummary(q) }}</span>
          <el-icon class="ter-item__chevron" :class="{ 'is-open': openSet.has('q' + i) }"><ArrowRight /></el-icon>
        </button>
        <div v-show="openSet.has('q' + i)" class="ter-item__body">
          <pre class="ter-pre">{{ pretty(q) }}</pre>
        </div>
      </div>
    </section>

    <!-- 折叠区：答案 / 采分点 / 来源 / 复核 -->
    <section v-if="answers.length" class="ter-section">
      <button class="ter-collapse" type="button" @click="toggle('answers')">
        {{ $t('teacher.exam.answers', '参考答案') }}（{{ answers.length }}）
        <el-icon :class="{ 'is-open': openSet.has('answers') }"><ArrowRight /></el-icon>
      </button>
      <div v-show="openSet.has('answers')" class="ter-collapse__body">
        <pre v-for="(a, i) in answers" :key="'a' + i" class="ter-pre">{{ pretty(a) }}</pre>
      </div>
    </section>

    <section v-if="scoringRubric.length" class="ter-section">
      <button class="ter-collapse" type="button" @click="toggle('rubric')">
        {{ $t('teacher.exam.scoringRubric', '采分点') }}（{{ scoringRubric.length }}）
        <el-icon :class="{ 'is-open': openSet.has('rubric') }"><ArrowRight /></el-icon>
      </button>
      <div v-show="openSet.has('rubric')" class="ter-collapse__body">
        <pre v-for="(r, i) in scoringRubric" :key="'r' + i" class="ter-pre">{{ pretty(r) }}</pre>
      </div>
    </section>

    <section v-if="sources.length" class="ter-section">
      <button class="ter-collapse" type="button" @click="toggle('sources')">
        {{ $t('teacher.exam.sources', '来源依据') }}（{{ sources.length }}）
        <el-icon :class="{ 'is-open': openSet.has('sources') }"><ArrowRight /></el-icon>
      </button>
      <div v-show="openSet.has('sources')" class="ter-collapse__body">
        <pre v-for="(s, i) in sources" :key="'s' + i" class="ter-pre">{{ pretty(s) }}</pre>
      </div>
    </section>

    <section v-if="internalReview.length" class="ter-section">
      <button class="ter-collapse" type="button" @click="toggle('review')">
        {{ $t('teacher.exam.internalReview', '质量复核') }}（{{ internalReview.length }}）
        <el-icon :class="{ 'is-open': openSet.has('review') }"><ArrowRight /></el-icon>
      </button>
      <div v-show="openSet.has('review')" class="ter-collapse__body">
        <pre v-for="(rv, i) in internalReview" :key="'rv' + i" class="ter-pre">{{ pretty(rv) }}</pre>
      </div>
    </section>

    <!-- 导出入口 -->
    <div v-if="hasExport" class="ter-export">
      <button
        v-for="opt in exportOptionsList"
        :key="opt.label"
        class="ter-export__btn"
        type="button"
        @click="$emit('export', opt)"
      >{{ opt.label }}</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'

interface Props {
  /** 已解析的 teacher_exam_result_v2 载荷 */
  payload: Record<string, any>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  export: [option: { label: string; format: string }]
}>()

const openSet = ref<Set<string>>(new Set(['q0']))

function toggle(key: string) {
  const next = new Set(openSet.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  openSet.value = next
}

const moduleLabel = computed(() => props.payload?.module || props.payload?.paper?.module || '')
const paperTitle = computed(() => props.payload?.paper?.title || props.payload?.paper?.name || props.payload?.paper?.paperTitle || '')
const questions = computed<any[]>(() => props.payload?.questions || [])
const answers = computed<any[]>(() => props.payload?.answers || [])
const scoringRubric = computed<any[]>(() => props.payload?.scoringRubric || [])
const sources = computed<any[]>(() => props.payload?.sources || [])
const internalReview = computed<any[]>(() => props.payload?.internalReview || [])
const hasExport = computed(() => !!props.payload?.exportOptions)
const exportOptionsList = computed<{ label: string; format: string }[]>(() => {
  const eo = props.payload?.exportOptions
  if (!eo) return []
  return Object.entries(eo).map(([format, label]) => ({ format, label: String(label) }))
})

function itemSummary(item: any): string {
  if (!item) return ''
  if (typeof item === 'string') return item
  return item.title || item.question || item.stem || item.name || JSON.stringify(item).slice(0, 80)
}

function pretty(obj: any): string {
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

function copyAll() {
  const text = pretty(props.payload)
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(text).catch(() => {})
  }
}
</script>

<style scoped>
.teacher-exam-result {
  border: 1px solid var(--mc-border, #e2e8f0);
  border-radius: 14px;
  background: var(--mc-bg-elevated, #f8fafc);
  padding: 14px 16px;
  margin: 8px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ter-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ter-header__icon { font-size: 20px; }
.ter-header__titles { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.ter-header__module {
  font-size: 12px;
  color: var(--mc-primary, #D97757);
  font-weight: 600;
}
.ter-header__title {
  font-size: 14px;
  color: var(--mc-text-primary, #1e293b);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ter-header__copy {
  flex-shrink: 0;
  font-size: 12px;
  border: 1px solid var(--mc-border, #e2e8f0);
  background: transparent;
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
  color: var(--mc-text-secondary, #64748b);
}
.ter-header__copy:hover { border-color: var(--mc-primary, #D97757); color: var(--mc-primary, #D97757); }

.ter-section { display: flex; flex-direction: column; gap: 6px; }
.ter-section__title {
  margin: 0;
  font-size: 13px;
  color: var(--mc-text-primary, #1e293b);
}
.ter-item {
  border: 1px solid var(--mc-border, #e2e8f0);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}
.ter-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  background: transparent;
  border: none;
  cursor: pointer;
  text-align: left;
}
.ter-item__index {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--mc-primary-bg, rgba(217,119,87,0.1));
  color: var(--mc-primary, #D97757);
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}
.ter-item__summary {
  flex: 1;
  font-size: 13px;
  color: var(--mc-text-primary, #1e293b);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ter-item__chevron, .ter-collapse :deep(.el-icon) {
  transition: transform 0.2s ease;
  color: var(--mc-text-tertiary, #94a3b8);
}
.ter-item__chevron.is-open, .ter-collapse .el-icon.is-open {
  transform: rotate(90deg);
}
.ter-item__body { padding: 0 12px 12px; }

.ter-collapse {
  display: flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--mc-text-primary, #1e293b);
  font-weight: 600;
  padding: 4px 0;
}
.ter-collapse__body { display: flex; flex-direction: column; gap: 8px; }

.ter-pre {
  margin: 0;
  padding: 10px 12px;
  background: #fff;
  border: 1px solid var(--mc-border, #e2e8f0);
  border-radius: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-secondary, #475569);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow: auto;
}

.ter-export { display: flex; gap: 8px; flex-wrap: wrap; }
.ter-export__btn {
  font-size: 12px;
  border: 1px solid var(--mc-primary, #D97757);
  color: var(--mc-primary, #D97757);
  background: transparent;
  border-radius: 8px;
  padding: 4px 12px;
  cursor: pointer;
}
.ter-export__btn:hover { background: var(--mc-primary-bg, rgba(217,119,87,0.08)); }
</style>
