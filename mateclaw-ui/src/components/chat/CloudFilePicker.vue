<template>
  <div v-if="visible" class="cloud-file-picker" role="listbox" @mousedown.stop>
    <div class="picker__header">
      <span class="picker__title">yliyun-mcp</span>
      <span class="picker__hint">选择云盘文件作为对话上下文</span>
      <button class="picker__close" @click="$emit('close')" type="button" title="关闭">✕</button>
    </div>

    <div class="picker__search">
      <input ref="searchRef" v-model="keyword" class="picker__search-input"
        type="text" autocomplete="off" placeholder="搜索文件名..."
        @input="onSearch" @keydown.escape="$emit('close')"
        @keydown.enter.prevent="onEnter" @keydown.down.prevent="onArrowDown" @keydown.up.prevent="onArrowUp" />
    </div>

    <div class="picker__body">
      <div v-if="loading" class="picker__state">加载中...</div>
      <div v-else-if="errorMessage" class="picker__state picker__state--error">
        <strong>云盘搜索失败</strong>
        <span>{{ errorMessage }}</span>
        <button type="button" class="picker__retry" @click="retrySearch">重试</button>
      </div>
      <div v-else-if="items.length === 0" class="picker__state">
        {{ keyword ? '没有匹配的文件' : '输入关键词搜索云盘文件' }}
      </div>
      <ul v-else class="picker__list">
        <li v-for="(item, idx) in items" :key="item.id"
          class="picker__item" :class="{ active: idx === activeIndex, selected: isSelected(item.id) }"
          @click="toggleSelect(item)" @mouseenter="activeIndex = idx">
          <span class="picker__icon">{{ item.isFolder ? '📁' : '📄' }}</span>
          <span class="picker__name">{{ item.name }}</span>
          <span class="picker__size" v-if="!item.isFolder">{{ formatSize(item.size) }}</span>
          <span v-if="isSelected(item.id)" class="picker__check">✅</span>
        </li>
      </ul>
    </div>

    <div class="picker__footer" v-if="selectedFiles.length > 0">
      <span>已选 {{ selectedFiles.length }} 个文件</span>
      <button class="picker__btn" @click="confirm">确认</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { mcpApi } from '@/api'

interface CloudFile {
  id: number; name: string; isFolder: boolean; size: number; mimeType: string;
}

const props = defineProps<{
  visible: boolean
  searchFn?: (query: string) => Promise<CloudFile[]>
}>()
const emit = defineEmits<{ close: []; select: [files: CloudFile[]] }>()

const keyword = ref('')
const loading = ref(false)
const errorMessage = ref('')
const items = ref<CloudFile[]>([])
const selectedFiles = ref<CloudFile[]>([])
const activeIndex = ref(-1)
const searchRef = ref<HTMLInputElement>()
const searchTimer = ref<ReturnType<typeof setTimeout>>()

async function search(query: string) {
  loading.value = true
  errorMessage.value = ''
  try {
    // 优先使用外部 searchFn，降级走 MateClaw 的认证 MCP 代理。
    if (props.searchFn) {
      items.value = await props.searchFn(query)
    } else {
      items.value = await searchViaHttp(query)
    }
  } catch (error) {
    items.value = []
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}

// 通过共享 API 客户端调用 MateClaw MCP 代理。这样既兼容普通 Bearer
// 登录，也兼容云盘 SSO 写入的 HttpOnly Cookie，不要求前端读取 token。
async function searchViaHttp(query: string): Promise<CloudFile[]> {
  const response = await mcpApi.callYliyunTool('file.search', {
    query,
    maxResults: 20,
  })
  if (!response.data) return []

  let data: unknown = response.data
  try {
    data = typeof response.data === 'string' ? JSON.parse(response.data) : response.data
  } catch {
    throw new Error('云盘 MCP 返回了无法解析的文件列表。')
  }

  const payload = asRecord(data)
  const rawItems = Array.isArray(payload?.files)
    ? payload.files
    : Array.isArray(payload?.items)
      ? payload.items
      : []
  return rawItems
    .map(toCloudFile)
    .filter((file): file is CloudFile => file !== null)
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function toCloudFile(value: unknown): CloudFile | null {
  const file = asRecord(value)
  if (!file) return null

  const id = Number(file.id)
  const name = typeof file.name === 'string' ? file.name : ''
  if (!Number.isSafeInteger(id) || id <= 0 || !name) return null

  const size = Number(file.size)
  return {
    id,
    name,
    isFolder: file.isFolder === true || file.type === 'directory',
    size: Number.isFinite(size) && size > 0 ? size : 0,
    mimeType: typeof file.mimeType === 'string' ? file.mimeType : '',
  }
}

function retrySearch() {
  const query = keyword.value.trim()
  if (query) search(query)
}

function toUserMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  return '请检查 MCP 连接、当前用户身份映射和云盘服务状态。'
}

function onSearch() {
  clearTimeout(searchTimer.value)
  activeIndex.value = -1
  errorMessage.value = ''
  if (!keyword.value.trim()) { items.value = []; return }
  searchTimer.value = setTimeout(() => search(keyword.value.trim()), 300)
}

function onEnter() {
  if (activeIndex.value >= 0 && activeIndex.value < items.value.length) {
    toggleSelect(items.value[activeIndex.value])
  } else if (items.value.length > 0) {
    toggleSelect(items.value[0])
  }
}

function onArrowDown() { activeIndex.value = Math.min(activeIndex.value + 1, items.value.length - 1) }
function onArrowUp() { activeIndex.value = Math.max(activeIndex.value - 1, -1) }

function toggleSelect(item: CloudFile) {
  if (item.isFolder) return // 文件夹不可选
  const idx = selectedFiles.value.findIndex(f => f.id === item.id)
  if (idx >= 0) { selectedFiles.value.splice(idx, 1) }
  else { selectedFiles.value.push(item) }
}
function isSelected(id: number) { return selectedFiles.value.some(f => f.id === id) }

function confirm() {
  emit('select', [...selectedFiles.value])
  selectedFiles.value = []
  emit('close')
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)}KB`
  return `${(bytes / 1048576).toFixed(1)}MB`
}

watch(() => props.visible, async (v) => {
  if (v) {
    selectedFiles.value = []; keyword.value = ''; items.value = []; errorMessage.value = ''; activeIndex.value = -1
    nextTick(() => searchRef.value?.focus())
  }
})
</script>

<style scoped>
.cloud-file-picker {
  position: absolute; bottom: 100%; left: 0; right: 0;
  max-height: 360px; background: var(--el-bg-color);
  border: 1px solid var(--el-border-color); border-radius: 8px;
  box-shadow: 0 4px 24px rgba(0,0,0,.12);
  display: flex; flex-direction: column; z-index: 1000; margin-bottom: 8px;
}
.picker__header { padding: 10px 12px 4px; display: flex; justify-content: space-between; align-items: baseline; }
.picker__title { font-weight: 600; color: var(--el-color-primary); font-size: 13px; }
.picker__hint { font-size: 11px; color: var(--el-text-color-secondary); flex:1; margin-left:8px; }
.picker__close { border:none; background:none; cursor:pointer; font-size:16px; color:var(--el-text-color-secondary); padding:0 4px; line-height:1; }
.picker__close:hover { color: var(--el-color-danger); }
.picker__search { padding: 8px 12px; }
.picker__search-input {
  width: 100%; padding: 6px 10px; border: 1px solid var(--el-border-color);
  border-radius: 6px; font-size: 13px; outline: none;
}
.picker__body { flex: 1; overflow-y: auto; }
.picker__state { padding: 20px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }
.picker__state--error { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--el-color-danger); }
.picker__state--error span { color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.picker__retry {
  margin-top: 4px; padding: 4px 12px; border: 1px solid var(--el-color-primary);
  border-radius: 6px; background: transparent; color: var(--el-color-primary); cursor: pointer;
}
.picker__list { list-style: none; margin: 0; padding: 4px 0; }
.picker__item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 12px; cursor: pointer; font-size: 13px;
}
.picker__item:hover, .picker__item.active { background: var(--el-fill-color-light); }
.picker__item.selected { background: var(--el-color-primary-light-9); }
.picker__icon { font-size: 16px; }
.picker__name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker__size { font-size: 11px; color: var(--el-text-color-secondary); }
.picker__footer {
  padding: 8px 12px; border-top: 1px solid var(--el-border-color);
  display: flex; justify-content: space-between; align-items: center; font-size: 13px;
}
.picker__btn { padding: 4px 16px; background: var(--el-color-primary); color: #fff; border: none; border-radius: 6px; cursor: pointer; }
</style>
