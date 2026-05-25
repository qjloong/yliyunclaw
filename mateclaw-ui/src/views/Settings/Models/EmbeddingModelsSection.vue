<template>
  <div class="provider-group embedding-section">
    <h3 class="group-title">
      <svg class="group-title__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <circle cx="12" cy="12" r="4"/>
        <line x1="4.93" y1="4.93" x2="9.17" y2="9.17"/>
        <line x1="14.83" y1="14.83" x2="19.07" y2="19.07"/>
        <line x1="14.83" y1="9.17" x2="19.07" y2="4.93"/>
        <line x1="4.93" y1="19.07" x2="9.17" y2="14.83"/>
      </svg>
      Embedding 模型
      <span class="group-hint">知识库语义检索使用，与 Chat 模型共享 Provider 的 API Key</span>
    </h3>

    <div v-if="loading" class="loading-state">加载中...</div>
    <template v-else>
      <div class="embedding-add-box">
        <div class="embedding-add-box__title">手动添加 Embedding 模型</div>
        <div class="embedding-add-box__hint">
          选一个已启用的 Provider，然后填写该 Provider 下的向量模型名称。
          例如 Ollama 可填写 <strong>nomic-embed-text</strong>、<strong>bge-m3</strong>。
        </div>
        <div class="embedding-add-grid">
          <select v-model="addProviderId" class="embedding-input" :disabled="adding || selectableProviders.length === 0">
            <option value="" disabled>选择 Provider</option>
            <option v-for="provider in selectableProviders" :key="provider.id" :value="provider.id">
              {{ provider.name }} ({{ provider.id }})
            </option>
          </select>
          <input v-model="addModelId" class="embedding-input" placeholder="模型 ID，例如 nomic-embed-text" :disabled="adding || selectableProviders.length === 0" />
          <input v-model="addModelName" class="embedding-input" placeholder="显示名称（可选）" :disabled="adding || selectableProviders.length === 0" />
          <button class="card-btn test-btn embedding-add-btn" :disabled="adding || !addProviderId || !addModelId.trim()" @click="onAddEmbeddingModel">
            {{ adding ? '添加中...' : '添加 Embedding 模型' }}
          </button>
        </div>
      </div>

      <div v-if="models.length === 0" class="empty-state">
        暂无可用的 Embedding 模型。系统已预置 DashScope Text Embedding v3/v2，
        请在"云端模型"下的 <strong>DashScope</strong> Provider 中配置 API Key。
      </div>

      <div v-else class="embedding-grid">
        <div v-for="model in models" :key="model.id" class="embedding-card">
          <div class="embedding-card-header">
            <div class="embedding-name">
              {{ model.name }}
              <span v-if="String(model.id) === defaultModelId" class="default-badge">默认</span>
            </div>
            <span class="provider-badge">{{ model.provider }}</span>
          </div>
          <div class="embedding-model-id">{{ model.modelName }}</div>
          <div v-if="model.description" class="embedding-desc">{{ model.description }}</div>

          <!-- 测试结果 -->
          <div v-if="testResults[String(model.id)]" class="test-result" :class="testResults[String(model.id)].success ? 'success' : 'error'">
            <span v-if="testResults[String(model.id)].success">
              ✓ 测试通过 · 维度 {{ testResults[String(model.id)].dimensions }}
            </span>
            <span v-else>✗ {{ testResults[String(model.id)].message }}</span>
          </div>

          <div class="embedding-actions">
            <button
              class="card-btn test-btn"
              :disabled="testingId === String(model.id)"
              @click="onTest(model)"
            >
              {{ testingId === String(model.id) ? '测试中...' : '测试连通性' }}
            </button>
            <button
              v-if="String(model.id) !== defaultModelId"
              class="card-btn"
              @click="onSetDefault(model)"
            >
              设为系统默认
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { modelApi } from '@/api'
import type { ProviderInfo } from '@/types'

const props = defineProps<{
  providers?: ProviderInfo[]
}>()

interface EmbeddingModel {
  id: string | number
  name: string
  provider: string
  modelName: string
  description?: string
  enabled?: boolean
  isDefault?: boolean
}

const models = ref<EmbeddingModel[]>([])
const loading = ref(false)
const defaultModelId = ref<string>('')
const testingId = ref<string>('')
const testResults = ref<Record<string, { success: boolean; dimensions?: number; message?: string }>>({})
const addProviderId = ref('')
const addModelId = ref('')
const addModelName = ref('')
const adding = ref(false)

const selectableProviders = computed(() => props.providers || [])

function ensureSelectedProvider() {
  if (!selectableProviders.value.length) {
    addProviderId.value = ''
    return
  }
  if (!addProviderId.value || !selectableProviders.value.some((p: ProviderInfo) => p.id === addProviderId.value)) {
    const preferred = selectableProviders.value.find((p: ProviderInfo) => p.id === 'ollama')
    addProviderId.value = preferred?.id || selectableProviders.value[0].id
  }
}

async function loadAll() {
  loading.value = true
  try {
    const [listRes, defaultRes] = await Promise.all([
      modelApi.listByType('embedding'),
      modelApi.getDefaultEmbedding(),
    ])
    models.value = (listRes.data as any[]) || []
    defaultModelId.value = String((defaultRes.data as any)?.defaultModelId || '')
  } catch (e: any) {
    console.error('[EmbeddingModels] Load failed:', e?.message)
  } finally {
    loading.value = false
  }
}

async function onAddEmbeddingModel() {
  if (!addProviderId.value || !addModelId.value.trim()) return
  adding.value = true
  try {
    await modelApi.addProviderModel(addProviderId.value, {
      id: addModelId.value.trim(),
      name: (addModelName.value || addModelId.value).trim(),
      modelType: 'embedding',
    })
    addModelId.value = ''
    addModelName.value = ''
    await loadAll()
  } catch (e: any) {
    console.error('[EmbeddingModels] Add failed:', e?.message)
  } finally {
    adding.value = false
  }
}

async function onTest(model: EmbeddingModel) {
  testingId.value = String(model.id)
  try {
    const res = await modelApi.testEmbedding(model.id)
    const data = res.data as any
    testResults.value[String(model.id)] = {
      success: !!data?.success,
      dimensions: data?.dimensions,
      message: data?.message,
    }
  } catch (e: any) {
    testResults.value[String(model.id)] = {
      success: false,
      message: e?.message || '请求失败',
    }
  } finally {
    testingId.value = ''
  }
}

async function onSetDefault(model: EmbeddingModel) {
  try {
    await modelApi.setDefaultEmbedding(model.id)
    defaultModelId.value = String(model.id)
  } catch (e: any) {
    console.error('[EmbeddingModels] Set default failed:', e?.message)
  }
}

onMounted(async () => {
  ensureSelectedProvider()
  await loadAll()
})

watch(selectableProviders, () => {
  ensureSelectedProvider()
}, { immediate: true })

defineExpose({ refresh: loadAll })
</script>

<style scoped>
.embedding-section {
  margin-top: 24px;
}
/* Mirror the flex layout used by .group-title in index.vue — scoped styles
 * don't cross component boundaries, so without this the icon stacks above
 * the title instead of sitting inline. */
.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 600;
  color: var(--mc-text-primary);
}
.group-title__icon {
  flex-shrink: 0;
  color: var(--mc-text-secondary);
}
.group-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--mc-text-tertiary);
  margin-left: 4px;
}
.loading-state, .empty-state {
  padding: 32px;
  text-align: center;
  color: var(--mc-text-tertiary);
  background: var(--mc-bg-sunken);
  border-radius: 8px;
}
.embedding-add-box {
  margin-bottom: 14px;
  padding: 14px;
  background: var(--mc-bg-surface);
  border: 1px solid var(--mc-border);
  border-radius: 10px;
}
.embedding-add-box__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mc-text-primary);
}
.embedding-add-box__hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-secondary);
}
.embedding-add-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}
.embedding-input {
  width: 100%;
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid var(--mc-border);
  border-radius: 8px;
  background: var(--mc-bg-sunken);
  color: var(--mc-text-primary);
}
.embedding-input:focus {
  outline: none;
  border-color: var(--mc-primary);
}
.embedding-add-btn {
  min-width: 160px;
}
.empty-state strong { color: var(--mc-primary); }

.embedding-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}
.embedding-card {
  padding: 16px;
  background: var(--mc-bg-surface);
  border: 1px solid var(--mc-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.embedding-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.embedding-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--mc-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
}
.default-badge {
  font-size: 10px;
  padding: 2px 6px;
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
  border-radius: 4px;
  font-weight: 600;
}
.provider-badge {
  font-size: 11px;
  padding: 2px 8px;
  background: var(--mc-bg-sunken);
  color: var(--mc-text-secondary);
  border-radius: 999px;
}
.embedding-model-id {
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  color: var(--mc-text-tertiary);
}
.embedding-desc {
  font-size: 12px;
  color: var(--mc-text-secondary);
  line-height: 1.5;
}
.test-result {
  font-size: 12px;
  padding: 6px 8px;
  border-radius: 4px;
}
.test-result.success {
  background: rgba(34, 197, 94, 0.1);
  color: rgb(21, 128, 61);
}
.test-result.error {
  background: var(--mc-danger-bg);
  color: var(--mc-danger);
}
.embedding-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}
.card-btn {
  flex: 1;
  padding: 6px 12px;
  font-size: 12px;
  border-radius: 4px;
  border: 1px solid var(--mc-border);
  background: transparent;
  color: var(--mc-text-primary);
  cursor: pointer;
}
.card-btn:hover:not(:disabled) { background: var(--mc-bg-sunken); }
.card-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.test-btn {
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
  border-color: var(--mc-primary);
}

@media (max-width: 1100px) {
  .embedding-add-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .embedding-add-grid {
    grid-template-columns: 1fr;
  }
}
</style>
