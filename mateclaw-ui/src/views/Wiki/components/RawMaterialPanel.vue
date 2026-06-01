<template>
  <div class="raw-panel">
    <!-- Upload + Add text row -->
    <div class="upload-row">
      <div
        class="upload-zone"
        :class="{ 'is-dragging': isDragging, 'is-uploading': uploadingFiles.length > 0 }"
        @click="triggerFileInput"
        @dragover.prevent
        @dragenter.prevent="onDragEnter"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="handleDrop"
      >
        <!-- Spinner while uploading -->
        <svg v-if="uploadingFiles.length > 0" class="upload-spinner" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <!-- Arrow-up icon in drag-over state -->
        <svg v-else-if="isDragging" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <polyline points="17 8 12 3 7 8"/>
          <line x1="12" y1="3" x2="12" y2="21"/>
        </svg>
        <!-- Default upload icon -->
        <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
          <polyline points="17 8 12 3 7 8"/>
          <line x1="12" y1="3" x2="12" y2="15"/>
        </svg>
        <div class="upload-text">
          <span class="upload-label">
            <template v-if="uploadingFiles.length > 0">{{ t('wiki.uploading') }}</template>
            <template v-else-if="isDragging">{{ t('wiki.dropToUpload') }}</template>
            <template v-else>{{ t('wiki.dropFiles') }}</template>
          </span>
          <span class="upload-hint">.txt, .md, .pdf, .docx</span>
        </div>
      </div>
      <input ref="fileInput" type="file" style="display:none" accept=".txt,.md,.pdf,.docx,.doc" multiple @change="handleFileSelect" />
      <button class="btn-secondary add-text-btn" @click="showAddText = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        {{ t('wiki.addText') }}
      </button>
    </div>

    <!-- T2-5: 移除批量上传区的结构化标签表单 — 批量上传和文件夹导入时无意义 -->

    <!-- Directory scan -->
    <div class="dir-scan-row">
      <div class="dir-input-wrap">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
        </svg>
        <input
          v-model="dirPath"
          type="text"
          class="dir-input"
          :placeholder="t('wiki.dirPlaceholder')"
          @keyup.enter="handleScanDir"
        />
      </div>
      <button class="btn-secondary" @click="handleScanDir" :disabled="scanning || !dirPath.trim()">
        <svg v-if="!scanning" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
        </svg>
        {{ scanning ? t('wiki.scanning') : t('wiki.scan') }}
      </button>
    </div>
    <div v-if="scanResult" class="scan-result">
      {{ t('wiki.scanResult', { scanned: scanResult.scanned, added: scanResult.added, skipped: scanResult.skipped }) }}
    </div>

    <!-- Raw materials list -->
    <div class="raw-list">
      <h4 class="raw-list-title">
        {{ t('wiki.rawMaterials') }} ({{ store.rawMaterials.length + uploadingFiles.length }})
      </h4>
      <div v-if="store.rawMaterials.length === 0 && uploadingFiles.length === 0" class="empty-hint">
        {{ t('wiki.noRawMaterials') }}
      </div>

      <!-- Optimistic uploading items shown at the top -->
      <div
        v-for="uf in uploadingFiles"
        :key="uf.tempId"
        class="raw-item raw-item--uploading"
      >
        <div class="raw-item-row">
          <div class="raw-item-info">
            <span class="raw-item-title">{{ uf.name }}</span>
          </div>
          <div class="raw-item-meta">
            <span v-if="uf.status === 'error'" class="status-badge failed">{{ t('wiki.status.failed') }}</span>
            <span v-else class="status-badge uploading">{{ t('wiki.status.uploading') }}</span>
            <span
              v-if="uf.status === 'error' && uf.errorMsg"
              class="error-hint" :title="uf.errorMsg"
            >{{ uf.errorMsg }}</span>
          </div>
          <div class="raw-item-actions">
            <!-- Dismiss error item -->
            <button
              v-if="uf.status === 'error'"
              class="btn-icon btn-icon-danger"
              :title="t('common.delete')"
              @click="removeUploadingFile(uf.tempId)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
        </div>
        <!-- HTTP upload progress bar -->
        <div v-if="uf.status !== 'error'" class="raw-progress">
          <div class="raw-progress-track">
            <div
              class="raw-progress-fill"
              :class="{ indeterminate: uf.httpPct === 0 }"
              :style="uf.httpPct > 0 ? { width: uf.httpPct + '%' } : {}"
            ></div>
          </div>
          <span class="raw-progress-label">
            {{ uf.httpPct > 0 ? t('wiki.progress.uploading', { pct: uf.httpPct }) : t('wiki.progress.preparing') }}
          </span>
        </div>
      </div>

      <div
        v-for="raw in store.rawMaterials"
        :key="raw.id"
        class="raw-item"
        :class="{ 'raw-item--active': store.selectedRawId === raw.id }"
        @click="toggleRawFilter(raw.id)"
      >
        <div class="raw-item-row">
          <div class="raw-item-info">
            <span class="raw-item-title">{{ raw.title }}</span>
            <span v-if="raw.materialType && raw.materialType !== 'general'" class="raw-item-business-type" :class="{ 'auto-detected-low': raw.autoDetected && raw.autoDetectConfidence === 'LOW' }">{{ teacherMaterialLabel(raw.materialType) }}</span>
            <span v-if="raw.autoDetected" class="raw-item-auto-badge" :class="raw.autoDetectConfidence?.toLowerCase() || 'low'" :title="raw.autoDetectReason || '系统自动识别'">自动</span>
            <span class="raw-item-type">{{ raw.sourceType }}</span>

          </div>
          <div class="raw-item-meta">
            <span class="status-badge" :class="raw.processingStatus">
              {{ t(`wiki.status.${raw.processingStatus}`) }}
            </span>
            <span v-if="raw.pageCount != null && raw.pageCount > 0" class="page-count-chip">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
              {{ raw.pageCount }}
            </span>
            <span
              v-if="raw.errorMessage && (raw.processingStatus === 'failed' || raw.processingStatus === 'partial')"
              class="error-hint" :title="raw.errorMessage"
            >
              {{ raw.errorMessage }}
            </span>
          </div>
          <div class="raw-item-actions">
            <button
              v-if="raw.processingStatus === 'partial'"
              class="btn-icon btn-icon-resume" :title="t('wiki.resume')"
              @click="reprocess(raw.id)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round">
                <polygon points="6 4 20 12 6 20 6 4"/>
              </svg>
            </button>
            <button
              v-else-if="raw.processingStatus === 'failed' || raw.processingStatus === 'completed'"
              class="btn-icon" :title="t('wiki.reprocess')"
              @click="reprocess(raw.id)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="23 4 23 10 17 10"/>
                <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
              </svg>
            </button>
            <button
              v-if="raw.processingStatus !== 'uploading'"
              class="btn-icon" :title="t('wiki.download')"
              @click="downloadRaw(raw)"
            >
              <el-icon :size="14"><Download /></el-icon>
            </button>
            <button class="btn-icon btn-icon-danger" :title="t('common.delete')" @click="deleteRaw(raw.id)">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
            </button>
          </div>
        </div>
        <div v-if="extractionPipelineHint(raw)" class="raw-item-hint">
          {{ extractionPipelineHint(raw) }}
        </div>
        <div v-if="rawMetadataSummary(raw)" class="raw-item-hint raw-item-hint--metadata">
          {{ rawMetadataSummary(raw) }}
        </div>
        <!-- RFC-033: Job stage bar — show when job has progressed past 'queued' or reached terminal -->
        <JobStageBar
          v-if="rawJobs[raw.id] && (rawJobs[raw.id].stage !== 'queued' || rawJobs[raw.id].status !== 'queued')"
          :stage="rawJobs[raw.id].stage"
          :status="rawJobs[raw.id].status"
          :current-model="rawJobs[raw.id].currentModelName ?? (rawJobs[raw.id].currentModelId ? `Model #${rawJobs[raw.id].currentModelId}` : undefined)"
          :is-fallback-active="rawJobs[raw.id].currentModelId != null && rawJobs[raw.id].currentModelId !== rawJobs[raw.id].primaryModelId"
          :error-code="rawJobs[raw.id].errorCode ?? undefined"
          :error-message="rawJobs[raw.id].errorMessage ?? undefined"
          :done="rawJobs[raw.id].done ?? raw.progressDone"
          :total="rawJobs[raw.id].total ?? raw.progressTotal"
          :started-at="rawJobs[raw.id].startedAt ?? undefined"
          @reprocess="reprocess(raw.id)"
          @repair="handleLocalRepair(raw.id)"
        />
        <!-- Original progress bar: shown during processing when job data is not active -->
        <div v-else-if="raw.processingStatus === 'processing'" class="raw-progress">
          <div class="raw-progress-track">
            <div
              class="raw-progress-fill"
              :class="{ indeterminate: !raw.progressTotal }"
              :style="raw.progressTotal
                ? { width: Math.min(100, Math.round((raw.progressDone / raw.progressTotal) * 100)) + '%' }
                : {}"
            ></div>
          </div>
          <span class="raw-progress-label">
            {{ raw.progressTotal
              ? `${raw.progressDone} / ${raw.progressTotal}`
              : t('wiki.progress.preparing') }}
          </span>
        </div>
      </div>
    </div>

    <!-- Process all button -->
    <button
      v-if="store.currentKB && store.rawMaterials.some(r => r.processingStatus === 'pending')"
      class="btn-primary process-btn"
      @click="processAll"
    >
      {{ t('wiki.processAll') }}
    </button>

    <!-- Add Text Modal -->
    <div v-if="showAddText" class="modal-overlay">
      <div class="modal-content">
        <h3 class="modal-title">{{ t('wiki.addText') }}</h3>
        <div class="form-group">
          <label>{{ t('wiki.materialTitle') }}</label>
          <input v-model="textTitle" type="text" class="form-input" />
        </div>
        <div v-if="store.currentKB?.kbKind === 'business'" class="form-group">
          <label>{{ (currentDomainProfile?.displayName || '业务') + ' 资料类型' }}</label>
          <select v-model="teacherMaterialType" class="form-input">
            <option v-for="item in profileMaterialTypes" :key="item.value" :value="item.value">
              {{ item.label }}
            </option>
          </select>
        </div>
        <template v-if="showTeacherMetadataForm">
          <div class="teacher-metadata-inline-note">
            <div class="teacher-metadata-inline-title">当前结构化标签</div>
            <div v-if="materialMetadataPreview.length > 0" class="teacher-metadata-preview">
              <span v-for="item in materialMetadataPreview" :key="`modal-${item}`" class="teacher-metadata-chip">{{ item }}</span>
            </div>
            <div v-else class="teacher-metadata-inline-empty">未填写结构化标签时，将仅保存资料类型。</div>
          </div>
          <div class="teacher-metadata-grid modal-metadata-grid">
            <div
              v-for="field in currentMaterialTypeConfig?.fields || []"
              :key="field.key"
              class="form-group compact"
              :class="{ 'teacher-metadata-span-2': field.key === 'classicName' || field.key === 'routeTagsInput' }"
            >
              <label>{{ field.label }}</label>
              <input
                v-model="(teacherMaterialMetadata as any)[field.key]"
                type="text"
                class="form-input"
                :placeholder="field.placeholder"
              />
            </div>
          </div>
        </template>
        <div class="form-group">
          <label>{{ t('wiki.materialContent') }}</label>
          <textarea v-model="textContent" class="form-input" rows="12" :placeholder="t('wiki.pasteContent')"></textarea>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showAddText = false">{{ t('common.cancel') }}</button>
          <button class="btn-primary" @click="handleAddText" :disabled="!textTitle.trim() || !textContent.trim()">
            {{ t('common.add') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { useWikiStore } from '@/stores/useWikiStore'
import type { WikiRawMaterial } from '@/stores/useWikiStore'
import { wikiApi } from '@/api/index'
import { mcConfirm } from '@/components/common/useConfirm'
import JobStageBar from './JobStageBar.vue'
import type { WikiProcessingJob } from '@/composables/useWikiJobPoller'
import type { WikiDomainProfileOption, WikiDomainProfileMaterialType } from '@/types/index'

const { t } = useI18n()
const store = useWikiStore()
const fileInput = ref<HTMLInputElement | null>(null)

// Domain profile driven material types (T2-3-12)
const domainProfiles = ref<WikiDomainProfileOption[]>([])

async function loadDomainProfiles() {
  try {
    const res: any = await wikiApi.listDomainProfiles()
    domainProfiles.value = (res.data || res || []) as WikiDomainProfileOption[]
  } catch {
    domainProfiles.value = []
  }
}

onMounted(() => {
  loadDomainProfiles()
})

const currentDomainProfile = computed<WikiDomainProfileOption | null>(() => {
  const profileId = store.currentKB?.domainProfileId
  if (!profileId) return null
  return domainProfiles.value.find(p => p.id === profileId) || null
})

const profileMaterialTypes = computed<WikiDomainProfileMaterialType[]>(() => {
  return currentDomainProfile.value?.materialTypes || [
    { value: 'general', label: '通用资料', fields: [] }
  ]
})

const currentMaterialTypeConfig = computed(() => {
  return profileMaterialTypes.value.find(m => m.value === teacherMaterialType.value) || null
})

// RFC-012 M3：当列表中存在 processing 的材料时，优先订阅后端 SSE 实时进度流，
// 60s 兜底拉取 processingStatus / fetchRawMaterials 作为 SSE 断线降级（DB 是真源）。
// 处理完毕（无 processing 项）自动断开 SSE + 停止兜底轮询；组件卸载时也会清理。
let sse: EventSource | null = null
let fallbackTimer: number | null = null
let activeKbId: number | null = null

const hasProcessing = computed(() =>
  store.rawMaterials.some(r => r.processingStatus === 'processing' || r.processingStatus === 'pending')
)

function applyProgressEvent(payload: any) {
  if (!payload || payload.rawId == null) return
  const raw = store.rawMaterials.find(r => r.id === payload.rawId)
  if (!raw) return
  if (typeof payload.done === 'number') raw.progressDone = payload.done
  if (typeof payload.total === 'number') raw.progressTotal = payload.total
}

function openSse(kbId: number) {
  closeSse()
  activeKbId = kbId
  // Vite 代理 /api → :18088；EventSource 走相对路径即可
  const es = new EventSource(`/api/v1/wiki/knowledge-bases/${kbId}/progress`)
  sse = es

  es.addEventListener('raw.started', (ev: MessageEvent) => {
    try {
      const data = JSON.parse(ev.data)
      const raw = store.rawMaterials.find(r => r.id === data.rawId)
      if (raw) {
        raw.processingStatus = 'processing'
        raw.progressDone = 0
        raw.progressTotal = 0
      }
    } catch { /* ignore */ }
  })
  es.addEventListener('route.done', (ev: MessageEvent) => {
    try { applyProgressEvent(JSON.parse(ev.data)) } catch { /* ignore */ }
  })
  es.addEventListener('chunk.done', (ev: MessageEvent) => {
    try { applyProgressEvent(JSON.parse(ev.data)) } catch { /* ignore */ }
  })
  es.addEventListener('raw.completed', (ev: MessageEvent) => {
    try {
      const data = JSON.parse(ev.data)
      const raw = store.rawMaterials.find(r => r.id === data.rawId)
      if (raw) {
        raw.processingStatus = data.status === 'partial' ? 'partial' : 'completed'
        if (typeof data.totalPages === 'number') {
          raw.progressDone = data.totalPages
          raw.progressTotal = data.totalPages
        }
      }
      // Clear stale job entry so JobStageBar hides
      delete rawJobs[data.rawId]
      if (store.currentKB) store.fetchRawMaterials(store.currentKB.id)
    } catch { /* ignore */ }
  })
  es.addEventListener('raw.failed', (ev: MessageEvent) => {
    try {
      const data = JSON.parse(ev.data)
      const raw = store.rawMaterials.find(r => r.id === data.rawId)
      if (raw) raw.processingStatus = 'failed'
      // Clear stale job entry
      delete rawJobs[data.rawId]
      if (store.currentKB) store.fetchRawMaterials(store.currentKB.id)
    } catch { /* ignore */ }
  })
  es.onerror = () => {
    // Browser EventSource auto-reconnects; just log
    // console.debug('Wiki SSE error/reconnect', kbId)
  }
}

function closeSse() {
  if (sse) {
    sse.close()
    sse = null
  }
  activeKbId = null
}

watch(
  () => [hasProcessing.value, store.currentKB?.id] as const,
  ([active, kbId]) => {
    if (active && kbId != null) {
      // SSE main channel
      if (activeKbId !== kbId) openSse(kbId)
      // 60s fallback polling
      if (fallbackTimer == null) {
        fallbackTimer = window.setInterval(() => {
          if (store.currentKB) store.fetchRawMaterials(store.currentKB.id)
        }, 60000)
      }
    } else {
      closeSse()
      if (fallbackTimer != null) {
        clearInterval(fallbackTimer)
        fallbackTimer = null
      }
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  closeSse()
  if (fallbackTimer != null) {
    clearInterval(fallbackTimer)
    fallbackTimer = null
  }
})

// RFC-033: Job polling per raw material
const rawJobs = reactive<Record<number, WikiProcessingJob>>({})
let jobPoller: ReturnType<typeof setTimeout> | null = null

const TERMINAL_STATUSES = new Set(['completed', 'failed', 'partial', 'cancelled'])
const teacherMaterialType = ref('general')

// 切换材料类型时清空旧元数据，避免字段复用导致旧值残留
watch(teacherMaterialType, () => {
  teacherMaterialMetadata.edition = ''
  teacherMaterialMetadata.source = ''
  teacherMaterialMetadata.grade = ''
  teacherMaterialMetadata.volume = ''
  teacherMaterialMetadata.unit = ''
  teacherMaterialMetadata.chapter = ''
  teacherMaterialMetadata.classicName = ''
  teacherMaterialMetadata.routeTagsInput = ''
})

interface TeacherMaterialMetadataForm {
  edition: string
  source: string
  grade: string
  volume: string
  unit: string
  chapter: string
  classicName: string
  routeTagsInput: string
}

const teacherMaterialMetadata = reactive<TeacherMaterialMetadataForm>({
  edition: '',
  source: '',
  grade: '',
  volume: '',
  unit: '',
  chapter: '',
  classicName: '',
  routeTagsInput: '',
})

const showTeacherMetadataForm = computed(() => teacherMaterialType.value !== 'general' && currentMaterialTypeConfig.value != null)

const materialMetadataPreview = computed(() => {
  const metadata = buildMaterialMetadataObject()
  const preview: string[] = []
  const pushValue = (value?: string) => {
    if (value && !preview.includes(value)) preview.push(value)
  }
  pushValue(teacherMaterialLabel())
  const config = currentMaterialTypeConfig.value
  if (config) {
    for (const field of config.fields) {
      if (field.key === 'routeTagsInput') continue
      pushValue(asDisplayText(metadata[field.key]))
    }
  } else {
    pushValue(asDisplayText(metadata.edition))
    pushValue(asDisplayText(metadata.source))
    pushValue(asDisplayText(metadata.grade))
    pushValue(asDisplayText(metadata.volume))
    pushValue(asDisplayText(metadata.unit))
    pushValue(asDisplayText(metadata.chapter))
    pushValue(asDisplayText(metadata.classicName))
  }
  ;(metadata.routeTags || []).slice(0, 4).forEach((tag: string) => pushValue(tag))
  return preview
})

function teacherMaterialLabel(value = teacherMaterialType.value) {
  return profileMaterialTypes.value.find(item => item.value === value)?.label || '通用资料'
}

function prefixTeacherMaterialTitle(title: string) {
  const normalized = title.trim()
  const label = teacherMaterialLabel()
  if (!normalized || teacherMaterialType.value === 'general' || normalized.startsWith('【')) {
    return normalized
  }
  return `【${label}】${normalized}`
}

function normalizedMetadataValue(value?: string) {
  const normalized = String(value || '').trim()
  return normalized.length > 0 ? normalized : undefined
}

function parseRouteTags(input?: string) {
  return Array.from(new Set(
    String(input || '')
      .split(/[\n,，;；|]/)
      .map(item => item.trim())
      .filter(Boolean)
  ))
}

function asDisplayText(value: unknown) {
  return typeof value === 'string' ? normalizedMetadataValue(value) : undefined
}

function buildMaterialMetadataObject() {
  if (teacherMaterialType.value === 'general') {
    return {} as Record<string, any>
  }
  const metadata: Record<string, any> = {
    materialType: teacherMaterialType.value,
  }
  const config = currentMaterialTypeConfig.value
  if (config) {
    for (const field of config.fields) {
      if (field.key === 'routeTagsInput') continue
      const value = (teacherMaterialMetadata as any)[field.key]
      const normalized = normalizedMetadataValue(value)
      if (normalized) metadata[field.key] = normalized
    }
  } else {
    const push = (key: string, value?: string) => {
      const normalized = normalizedMetadataValue(value)
      if (normalized) metadata[key] = normalized
    }
    push('edition', teacherMaterialMetadata.edition)
    push('source', teacherMaterialMetadata.source)
    push('grade', teacherMaterialMetadata.grade)
    push('volume', teacherMaterialMetadata.volume)
    push('unit', teacherMaterialMetadata.unit)
    push('chapter', teacherMaterialMetadata.chapter)
    push('classicName', teacherMaterialMetadata.classicName)
  }
  const routeTags = new Set<string>(parseRouteTags(teacherMaterialMetadata.routeTagsInput))
  routeTags.add(teacherMaterialType.value)
  for (const key of Object.keys(metadata)) {
    if (key === 'materialType') continue
    const value = asDisplayText(metadata[key])
    if (value) routeTags.add(value)
  }
  if (routeTags.size > 0) {
    metadata.routeTags = Array.from(routeTags)
  }
  return metadata
}

function buildMaterialMetadataJson() {
  // T2-5: 仅手动添加文本时使用，批量上传直接传 undefined 走后端自动分类
  if (teacherMaterialType.value === 'general') {
    return undefined
  }
  const metadata: Record<string, any> = { materialType: teacherMaterialType.value }
  const config = currentMaterialTypeConfig.value
  if (config) {
    for (const field of config.fields) {
      if (field.key === 'routeTagsInput') continue
      const value = (teacherMaterialMetadata as any)[field.key]
      const normalized = normalizedMetadataValue(value)
      if (normalized) metadata[field.key] = normalized
    }
  }
  const routeTags = new Set<string>(parseRouteTags(teacherMaterialMetadata.routeTagsInput))
  routeTags.add(teacherMaterialType.value)
  const keys = Object.keys(metadata)
  if (keys.length <= 1 && !metadata.materialType) return undefined
  if (routeTags.size > 0) metadata.routeTags = Array.from(routeTags)
  return JSON.stringify(metadata)
}

function parseMaterialMetadata(raw: WikiRawMaterial) {
  const text = String(raw.materialMetadataJson || '').trim()
  if (!text) {
    return null
  }
  try {
    const parsed = JSON.parse(text)
    return parsed && typeof parsed === 'object' ? parsed as Record<string, any> : null
  } catch {
    return null
  }
}

function rawMetadataSummary(raw: WikiRawMaterial) {
  const metadata = parseMaterialMetadata(raw)
  if (!metadata) {
    return ''
  }
  const summary: string[] = []
  const push = (value?: string) => {
    if (value && !summary.includes(value)) summary.push(value)
  }
  push(asDisplayText(metadata.edition))
  push(asDisplayText(metadata.source))
  push(asDisplayText(metadata.grade))
  push(asDisplayText(metadata.volume))
  push(asDisplayText(metadata.unit))
  push(asDisplayText(metadata.chapter))
  push(asDisplayText(metadata.classicName))
  if (Array.isArray(metadata.routeTags)) {
    metadata.routeTags.slice(0, 2).forEach((tag: unknown) => push(asDisplayText(tag)))
  }
  return summary.join(' · ')
}

async function pollJobs() {
  if (!store.currentKB) return
  const kbId = store.currentKB.id
  const processingRaws = store.rawMaterials.filter(
    r => r.processingStatus === 'processing' || r.processingStatus === 'pending'
  )
  let anyTerminal = false
  for (const raw of processingRaws) {
    try {
      const res: any = await wikiApi.getWikiJobs(kbId, raw.id)
      const list = res.data || res || []
      if (list.length > 0) {
        const job = list[0]
        rawJobs[raw.id] = job
        if (TERMINAL_STATUSES.has(job.status)) {
          anyTerminal = true
        }
      }
    } catch { /* ignore */ }
  }
  // When any job reaches terminal, refresh raw materials to sync status badges
  if (anyTerminal) {
    await store.fetchRawMaterials(kbId)
  }
  // Continue polling while there are still processing/pending raws
  const stillActive = store.rawMaterials.some(
    r => r.processingStatus === 'processing' || r.processingStatus === 'pending'
  )
  if (stillActive) {
    jobPoller = setTimeout(pollJobs, 3000)
  }
}

watch(hasProcessing, (active) => {
  if (active) pollJobs()
  else if (jobPoller) { clearTimeout(jobPoller); jobPoller = null }
}, { immediate: true })

async function handleLocalRepair(rawId: number) {
  if (!store.currentKB) return
  // For local repair, we'd need a page slug. For now, reprocess the raw material.
  await reprocess(rawId)
}

const showAddText = ref(false)
const textTitle = ref('')
const textContent = ref('')
const dirPath = ref(store.currentKB?.sourceDirectory || '')
const scanning = ref(false)
const scanResult = ref<{ scanned: number; added: number; skipped: number } | null>(null)

// ─── Drag-over state ──────────────────────────────────────────────────────────
// Use a counter to handle nested dragenter/dragleave without flickering.
const isDragging = ref(false)
let dragCounter = 0

function onDragEnter() {
  dragCounter++
  isDragging.value = true
}

function onDragLeave() {
  dragCounter--
  if (dragCounter <= 0) {
    dragCounter = 0
    isDragging.value = false
  }
}

// ─── Optimistic upload items ──────────────────────────────────────────────────
interface UploadingFile {
  tempId: string
  name: string
  httpPct: number
  status: 'uploading' | 'error'
  errorMsg?: string
}

const uploadingFiles = ref<UploadingFile[]>([])
let tempIdCounter = 0

function addUploadingFile(name: string): UploadingFile {
  const item: UploadingFile = {
    tempId: `upload-${++tempIdCounter}`,
    name,
    httpPct: 0,
    status: 'uploading',
  }
  uploadingFiles.value.push(item)
  return item
}

function removeUploadingFile(tempId: string) {
  const idx = uploadingFiles.value.findIndex(f => f.tempId === tempId)
  if (idx >= 0) uploadingFiles.value.splice(idx, 1)
}

// ─── Upload helpers ───────────────────────────────────────────────────────────
async function uploadFile(kbId: number, file: File) {
  const uploadName = file.name
  const item = addUploadingFile(uploadName)
  try {
    await store.uploadRawFile(kbId, file, (pct) => {
      item.httpPct = pct
    }, {
      materialType: 'general',
      materialMetadataJson: undefined,
    })
    // Success: real item was added to store.rawMaterials, remove the optimistic placeholder
    removeUploadingFile(item.tempId)
  } catch (err: any) {
    item.status = 'error'
    item.errorMsg = err?.response?.data?.message || err?.message || t('wiki.uploadFailed', { name: file.name })
    ElMessage.error(t('wiki.uploadFailed', { name: file.name }))
  }
}

function triggerFileInput() {
  fileInput.value?.click()
}

async function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files || !store.currentKB) return
  const kbId = store.currentKB.id
  // Upload all files concurrently
  await Promise.all(Array.from(input.files).map(f => uploadFile(kbId, f)))
  input.value = ''
}

async function handleDrop(event: DragEvent) {
  // Reset drag state
  dragCounter = 0
  isDragging.value = false
  if (!event.dataTransfer?.files || !store.currentKB) return
  const kbId = store.currentKB.id
  await Promise.all(Array.from(event.dataTransfer.files).map(f => uploadFile(kbId, f)))
}

async function handleAddText() {
  if (!store.currentKB) return
  await store.addRawText(store.currentKB.id, prefixTeacherMaterialTitle(textTitle.value), textContent.value, {
    materialType: teacherMaterialType.value,
    materialMetadataJson: buildMaterialMetadataJson(),
  })
  showAddText.value = false
  textTitle.value = ''
  textContent.value = ''
}

async function reprocess(rawId: number) {
  if (!store.currentKB) return
  const kbId = store.currentKB.id
  await wikiApi.reprocessRaw(kbId, rawId)
  // Immediately mark local state as processing so SSE connects and progress bar shows
  const raw = store.rawMaterials.find(r => r.id === rawId)
  if (raw) {
    raw.processingStatus = 'processing'
    raw.progressDone = 0
    raw.progressTotal = 0
  }
  // Clear stale job entry
  delete rawJobs[rawId]
  await Promise.all([
    store.fetchRawMaterials(kbId),
    store.fetchPages(kbId, store.selectedRawId ?? undefined),
  ])
  // Delayed re-fetch to catch final status if processing finishes before SSE connects
  setTimeout(() => {
    store.fetchRawMaterials(kbId)
    store.fetchPages(kbId, store.selectedRawId ?? undefined)
  }, 5000)
  setTimeout(() => {
    store.fetchRawMaterials(kbId)
    store.fetchPages(kbId, store.selectedRawId ?? undefined)
  }, 15000)
}

async function deleteRaw(rawId: number) {
  if (!store.currentKB) return
  // T2-5-8: Confirm before deleting — cascades to exclusive wiki pages
  const confirmed = await mcConfirm({
    title: '确认删除',
    message: '删除后将自动清理该材料的全部关联 Wiki 页面和检索数据。此操作不可撤销，确定要继续吗？',
    tone: 'danger'
  })
  if (!confirmed) return
  await wikiApi.deleteRaw(store.currentKB.id, rawId)
  await store.fetchRawMaterials(store.currentKB.id)
}

async function downloadRaw(raw: { id: number; title?: string }) {
  if (!store.currentKB) return
  try {
    // The http interceptor returns the raw body for non-R-shaped responses,
    // so this resolves directly to the Blob (no .data unwrap needed).
    const blob = (await wikiApi.downloadRaw(store.currentKB.id, raw.id)) as unknown as Blob
    let filename = raw.title && raw.title.trim().length > 0 ? raw.title : `raw-${raw.id}`
    if (!filename.includes('.')) filename += '.txt'
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    // Revoke on next tick — some browsers cancel the in-flight download if we
    // revoke synchronously before the click handler returns.
    setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.error(`${t('wiki.downloadFailed')}: ${msg}`)
  }
}

async function processAll() {
  if (!store.currentKB) return
  const kbId = store.currentKB.id
  await wikiApi.processKB(kbId)
  // Mark all pending materials as processing so SSE connects
  store.rawMaterials
    .filter(r => r.processingStatus === 'pending')
    .forEach(r => { r.processingStatus = 'processing'; r.progressDone = 0; r.progressTotal = 0 })
  await store.fetchRawMaterials(kbId)
  setTimeout(() => { store.fetchRawMaterials(kbId) }, 5000)
}

function toggleRawFilter(rawId: number) {
  if (!store.currentKB) return
  const kbId = store.currentKB.id
  if (store.selectedRawId === rawId) {
    store.clearRawFilter(kbId)
  } else {
    store.filterPagesByRaw(kbId, rawId)
  }
}

async function handleScanDir() {
  if (!store.currentKB || !dirPath.value.trim()) return
  scanning.value = true
  scanResult.value = null
  try {
    // Save directory path first
    await wikiApi.setSourceDirectory(store.currentKB.id, dirPath.value.trim())
    // Trigger scan
    const result = await store.scanDirectory(store.currentKB.id)
    scanResult.value = result
  } catch (e: any) {
    console.error('Scan failed', e)
  } finally {
    scanning.value = false
  }
}

function extractionPipelineHint(raw: { sourceType?: string; processingStatus?: string }) {
  const sourceType = String(raw.sourceType || '').toLowerCase()
  if (sourceType === 'pdf') {
    return t('wiki.extractionHints.pdf')
  }
  if (sourceType === 'docx') {
    return t('wiki.extractionHints.docx')
  }
  if (sourceType === 'text') {
    return t('wiki.extractionHints.text')
  }
  return ''
}
</script>

<style scoped>
.raw-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* Buttons */
.btn-primary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-primary); color: white; border: none; border-radius: 10px; font-size: 14px; font-weight: 500; cursor: pointer; }
.btn-primary:hover { background: var(--mc-primary-hover); }
.btn-primary:disabled { background: var(--mc-border); cursor: not-allowed; }
.btn-secondary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 10px; font-size: 14px; cursor: pointer; white-space: nowrap; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }

/* Directory scan */
.dir-scan-row { display: flex; gap: 10px; align-items: center; }
.dir-input-wrap { flex: 1; display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid var(--mc-border); border-radius: 12px; background: var(--mc-bg-elevated); color: var(--mc-text-tertiary); }
.dir-input-wrap:focus-within { border-color: var(--mc-primary); box-shadow: 0 0 0 2px rgba(217,119,87,0.1); }
.dir-input { flex: 1; border: none; background: transparent; font-size: 13px; color: var(--mc-text-primary); outline: none; }
.dir-input::placeholder { color: var(--mc-text-tertiary); }
.scan-result { font-size: 12px; color: var(--mc-text-secondary); padding: 8px 10px; background: rgba(90,138,90,0.1); border-radius: 10px; }

/* Upload row: zone + add text side by side */
.upload-row { display: flex; gap: 12px; align-items: stretch; }
.teacher-material-type-select {
  width: 132px;
  border: 1px solid var(--mc-border);
  border-radius: 8px;
  background: var(--mc-bg-elevated);
  color: var(--mc-text-primary);
  padding: 0 10px;
  font-size: 13px;
}
.upload-zone {
  flex: 1;
  border: 1px dashed var(--mc-border);
  border-radius: 16px;
  padding: 18px 20px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--mc-text-tertiary);
}
.upload-zone:hover { border-color: var(--mc-primary); background: var(--mc-primary-bg); }
.upload-zone.is-dragging {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
  box-shadow: 0 0 0 3px rgba(217,119,87,0.15);
  color: var(--mc-primary);
}
.upload-zone.is-uploading {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
  cursor: default;
  pointer-events: none;
}
.upload-zone svg { flex-shrink: 0; }
.upload-text { display: flex; flex-direction: column; gap: 2px; }
.upload-label { font-size: 14px; color: var(--mc-text-secondary); }
.upload-hint { font-size: 12px; color: var(--mc-text-tertiary); }
.add-text-btn { flex-shrink: 0; }

.teacher-metadata-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--mc-border-light);
  border-radius: 14px;
  background: linear-gradient(180deg, var(--mc-bg-elevated), var(--mc-bg-muted));
}
.teacher-metadata-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}
.teacher-metadata-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-text-primary);
}
.teacher-metadata-subtitle {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mc-text-tertiary);
}
.teacher-metadata-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.teacher-metadata-span-2 {
  grid-column: span 2;
}
.teacher-metadata-preview {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.teacher-metadata-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(217,119,87,0.12);
  color: var(--mc-primary);
  font-size: 11px;
  line-height: 1.2;
}
.teacher-metadata-inline-note {
  margin-bottom: 16px;
  padding: 12px;
  border: 1px dashed var(--mc-border);
  border-radius: 12px;
  background: var(--mc-bg-muted);
}
.teacher-metadata-inline-title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-secondary);
}
.teacher-metadata-inline-empty {
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

/* Spinner animation for uploading state */
.upload-spinner {
  flex-shrink: 0;
  animation: spin 1s linear infinite;
  color: var(--mc-primary);
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* Raw list */
.raw-list { display: flex; flex-direction: column; gap: 8px; padding-top: 4px; }
.raw-list-title { font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: var(--mc-text-tertiary); margin-bottom: 4px; }
.empty-hint { text-align: center; padding: 24px 0; font-size: 14px; color: var(--mc-text-tertiary); }

.raw-item { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; background: linear-gradient(180deg, var(--mc-bg-elevated), var(--mc-bg-muted)); border: 1px solid var(--mc-border-light); border-radius: 14px; font-size: 13px; transition: border-color 0.15s, transform 0.15s; cursor: pointer; }
.raw-item:hover { border-color: var(--mc-border); transform: translateY(-1px); }
.raw-item--active { border-color: var(--mc-primary) !important; background: var(--mc-primary-bg) !important; transform: translateY(-1px); }
.raw-item--uploading { cursor: default; opacity: 0.85; }
.raw-item--uploading:hover { transform: none; border-color: var(--mc-border-light); }
.raw-item-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }

.raw-item-info { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.raw-item-title { font-weight: 500; color: var(--mc-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.raw-item-business-type { font-size: 10px; padding: 2px 6px; background: rgba(217,119,87,0.14); border-radius: 999px; color: var(--mc-primary); letter-spacing: 0.02em; }
.raw-item-business-type.auto-detected-low { background: rgba(234,179,8,0.14); color: #b45309; }
.raw-item-auto-badge { font-size: 10px; padding: 1px 5px; border-radius: 999px; letter-spacing: 0.02em; font-weight: 500; }
.raw-item-auto-badge.high { background: rgba(34,197,94,0.14); color: #15803d; }
.raw-item-auto-badge.medium { background: rgba(234,179,8,0.14); color: #b45309; }
.raw-item-auto-badge.low { background: rgba(239,68,68,0.12); color: #b91c1c; }
.raw-item-type { font-size: 10px; padding: 2px 6px; background: var(--mc-bg-sunken); border-radius: 4px; text-transform: uppercase; color: var(--mc-text-tertiary); letter-spacing: 0.02em; }

.raw-item-hint { font-size: 11px; line-height: 1.5; color: var(--mc-text-tertiary); }
.raw-item-hint--metadata { color: var(--mc-text-secondary); }
.raw-item-meta { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.raw-item-actions { display: flex; gap: 4px; flex-shrink: 0; }
.error-hint { font-size: 11px; color: var(--mc-danger); max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.page-count-chip { display: inline-flex; align-items: center; gap: 3px; font-size: 11px; font-weight: 500; color: var(--mc-text-secondary); background: var(--mc-bg-sunken); border-radius: 9999px; padding: 2px 7px; }

/* Two-phase digest progress bar (RFC-012 M2 v2 UI) */
.raw-progress { display: flex; align-items: center; gap: 10px; padding-top: 2px; }
.raw-progress-track { flex: 1; height: 4px; background: var(--mc-bg-sunken); border-radius: 9999px; overflow: hidden; position: relative; }
.raw-progress-fill { height: 100%; background: var(--mc-primary); border-radius: 9999px; transition: width 0.3s ease; }
.raw-progress-fill.indeterminate {
  width: 30%;
  position: absolute;
  left: 0;
  animation: raw-progress-slide 1.6s ease-in-out infinite;
}
@keyframes raw-progress-slide {
  0%   { transform: translateX(-100%); }
  50%  { transform: translateX(170%); }
  100% { transform: translateX(330%); }
}
.raw-progress-label { font-size: 11px; color: var(--mc-text-tertiary); font-variant-numeric: tabular-nums; flex-shrink: 0; min-width: 56px; text-align: right; }

/* Icon button */
.btn-icon { width: 30px; height: 30px; border: 1px solid var(--mc-border-light); background: var(--mc-bg-elevated); cursor: pointer; border-radius: 8px; color: var(--mc-text-secondary); transition: all 0.15s; display: flex; align-items: center; justify-content: center; }
.btn-icon:hover { background: var(--mc-bg-sunken); color: var(--mc-primary); border-color: var(--mc-border); }
.btn-icon-danger:hover { background: var(--mc-danger-bg); color: var(--mc-danger); border-color: var(--mc-danger); }
.btn-icon-resume { color: var(--mc-primary); border-color: var(--mc-primary); background: var(--mc-primary-bg); }
.btn-icon-resume:hover { background: var(--mc-primary); color: #fff; border-color: var(--mc-primary); }

/* Status badges */
.status-badge { font-size: 10px; padding: 2px 8px; border-radius: 9999px; text-transform: uppercase; font-weight: 500; letter-spacing: 0.02em; }
.status-badge.pending { background: var(--mc-bg-sunken); color: var(--mc-text-tertiary); }
.status-badge.uploading { background: rgba(59, 130, 246, 0.12); color: #3b82f6; }
.status-badge.processing { background: var(--mc-primary-bg); color: var(--mc-primary); }
.status-badge.completed { background: rgba(90, 138, 90, 0.15); color: var(--mc-success); }
.status-badge.partial { background: rgba(217, 119, 87, 0.15); color: var(--mc-primary); }
.status-badge.failed { background: var(--mc-danger-bg); color: var(--mc-danger); }

/* Process button */
.process-btn { width: 100%; justify-content: center; margin-top: 16px; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal-content { background: var(--mc-bg-elevated); border: 1px solid var(--mc-border); border-radius: 16px; width: 100%; max-width: 640px; padding: 24px; max-height: 80vh; overflow-y: auto; box-shadow: 0 20px 60px rgba(0,0,0,0.15); }
.modal-title { font-size: 18px; font-weight: 600; color: var(--mc-text-primary); margin: 0 0 16px; }

/* Form */
.form-group { margin-bottom: 16px; }
.form-group.compact { margin-bottom: 0; }
.form-group label { display: block; font-size: 13px; font-weight: 500; margin-bottom: 6px; color: var(--mc-text-secondary); }
.form-input { width: 100%; padding: 8px 12px; border: 1px solid var(--mc-border); border-radius: 8px; font-size: 14px; background: var(--mc-bg-sunken); color: var(--mc-text-primary); outline: none; font-family: inherit; }
.form-input:focus { border-color: var(--mc-primary); box-shadow: 0 0 0 2px rgba(217,119,87,0.1); }

.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }

@media (max-width: 768px) {
  .upload-row,
  .dir-scan-row {
    flex-direction: column;
  }

  .teacher-metadata-header,
  .teacher-metadata-grid {
    display: flex;
    flex-direction: column;
  }

  .teacher-metadata-preview {
    justify-content: flex-start;
  }

  .teacher-metadata-span-2 {
    grid-column: auto;
  }

  .add-text-btn,
  .dir-scan-row > .btn-secondary,
  .process-btn {
    width: 100%;
    justify-content: center;
  }

  .raw-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .raw-item-meta,
  .raw-item-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
