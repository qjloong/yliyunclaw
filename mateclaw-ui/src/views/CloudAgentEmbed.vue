<template>
  <div class="cloud-agent-embed">
    <header v-if="!hostProvidesHeader" class="cloud-agent-embed__header">
      <div class="cloud-agent-embed__identity">
        <span class="cloud-agent-embed__mark">AI</span>
        <div>
          <strong>云盘 AI 助手</strong>
          <span>{{ identityLabel }}</span>
        </div>
      </div>
      <button type="button" class="cloud-agent-embed__full" @click="openFullConsole">
        <span>完整界面</span>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M14 5h5v5M13 11l6-6M19 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h4" />
        </svg>
      </button>
    </header>
    <main class="cloud-agent-embed__body">
      <ChatConsole v-show="contextReady" embedded :cloud-context="currentContext" />
      <div v-if="!contextReady && contextError" class="cloud-agent-embed__context-state is-error">
        <strong>云盘文件上下文加载失败</strong>
        <span>{{ contextError }}</span>
        <button type="button" @click="applyCloudContext(latestRawContext)">重试</button>
      </div>
      <div v-else-if="!contextReady" class="cloud-agent-embed__context-state">
        <span>正在验证云盘文件权限…</span>
      </div>
    </main>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { yliyunResourceRefApi, type CloudResourceBinding } from '@/api'
import type { CloudContextLike, CloudResourceRefLike } from '@/utils/cloudContextPolicy'
import ChatConsole from '@/views/ChatConsole.vue'

defineOptions({ name: 'CloudAgentEmbed' })

const route = useRoute()
const router = useRouter()
type CloudContext = CloudContextLike

const initialContext: CloudContext = {
  fileId: route.query.fileId as string | undefined,
  fileName: route.query.fileName as string | undefined,
  folderId: route.query.folderId as string | undefined,
  folderName: route.query.folderName as string | undefined,
}
const currentContext = ref<CloudContext>({})
const latestRawContext = ref<CloudContext>({ ...initialContext })
const contextReady = ref(false)
const contextError = ref('')
let contextSequence = 0
const channelId = route.query.channelId ? String(route.query.channelId) : ''
const parentOrigin = route.query.parentOrigin ? String(route.query.parentOrigin) : ''
const hostProvidesHeader = computed(() => route.query.hostHeader === '1')

const identityLabel = computed(() => {
  const tenantName = localStorage.getItem('tenantName') || '云盘租户'
  const displayName = localStorage.getItem('displayName')
    || localStorage.getItem('username')
    || '云盘用户'
  const contextName = currentContext.value.fileName
    || currentContext.value.folderName
    || '当前云盘'
  return `${tenantName} · ${displayName} · ${contextName}`
})

const handleCloudContextMessage = (event: MessageEvent) => {
  if (event.source !== window.parent || !channelId) return
  if (parentOrigin && event.origin !== parentOrigin) return
  const message = event.data
  if (
    message?.source !== 'yliyun-cloud-drive'
    || message?.type !== 'cloud-context-change'
    || message?.channelId !== channelId
  ) return
  const context = message.context || {}
  void applyCloudContext({
    fileId: context.fileId != null ? String(context.fileId) : undefined,
    fileName: context.fileName != null ? String(context.fileName) : undefined,
    folderId: context.folderId != null ? String(context.folderId) : undefined,
    folderName: context.folderName != null ? String(context.folderName) : undefined,
  })
}

async function issueResourceRef(
  resourceType: 'file' | 'folder',
  resourceId: string,
  displayName: string | undefined,
  binding: CloudResourceBinding,
): Promise<CloudResourceRefLike> {
  const response = await yliyunResourceRefApi.issue({
    resourceType,
    resourceId,
    displayName,
    binding,
  })
  return response.data
}

async function applyCloudContext(
  context: CloudContext,
  binding: CloudResourceBinding = 'current-preview',
) {
  latestRawContext.value = { ...context }
  contextReady.value = false
  contextError.value = ''
  const sequence = ++contextSequence
  const refs: CloudResourceRefLike[] = []
  try {
    if (context.fileId) {
      refs.push(await issueResourceRef('file', context.fileId, context.fileName, binding))
    }
    if (context.folderId) {
      refs.push(await issueResourceRef('folder', context.folderId, context.folderName, binding))
    }
  } catch (error) {
    if (sequence !== contextSequence) return
    console.warn('[CloudAgentEmbed] Failed to seal cloud context:', error)
    currentContext.value = {}
    contextError.value = error instanceof Error ? error.message : '请重新打开 AI 助手'
    return
  }
  if (sequence !== contextSequence) return
  currentContext.value = { ...context, resourceRefs: refs }
  contextReady.value = true
  notifyHostConversation()
}

const notifyHostConversation = () => {
  if (window.parent === window || !channelId || !parentOrigin) return
  const conversationId = route.query.conversationId
    ? String(route.query.conversationId)
    : ''
  if (!conversationId) return
  window.parent.postMessage({
    source: 'mateclaw-cloud-agent',
    type: 'conversation-change',
    channelId,
    conversationId,
    agentId: route.query.agentId ? String(route.query.agentId) : undefined,
    context: { ...currentContext.value },
  }, parentOrigin)
}

const openFullConsole = () => {
  const query: Record<string, string> = { authSource: 'yliyun' }
  if (route.query.agentId) query.agentId = String(route.query.agentId)
  if (route.query.conversationId) query.conversationId = String(route.query.conversationId)
  const resourceRef = currentContext.value.resourceRefs?.[0]
  if (resourceRef) {
    query.resourceRef = resourceRef.refId
    query.resourceType = resourceRef.resourceType
    if (resourceRef.displayName) query.resourceName = resourceRef.displayName
    if (resourceRef.mimeType) query.resourceMimeType = resourceRef.mimeType
    query.resourceBinding = resourceRef.binding
    if (resourceRef.expiresAt) query.resourceExpiresAt = String(resourceRef.expiresAt)
  }
  const href = router.resolve({ path: '/chat', query }).href
  window.open(href, '_blank', 'noopener,noreferrer')
}

watch(
  () => [route.query.conversationId, route.query.agentId],
  notifyHostConversation,
  { immediate: true }
)

onMounted(() => {
  window.addEventListener('message', handleCloudContextMessage)
  void applyCloudContext(initialContext)
  notifyHostConversation()
})
onBeforeUnmount(() => window.removeEventListener('message', handleCloudContextMessage))
</script>

<style scoped>
.cloud-agent-embed {
  width: 100%;
  height: 100vh;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f7fb;
}

.cloud-agent-embed__header {
  height: 48px;
  flex: 0 0 48px;
  padding: 0 52px 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e2e8f2;
  background: rgb(255 255 255 / 96%);
}

.cloud-agent-embed__identity {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.cloud-agent-embed__identity > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.cloud-agent-embed__identity strong {
  color: #283650;
  font-size: 13px;
}

.cloud-agent-embed__identity span:not(.cloud-agent-embed__mark) {
  max-width: 280px;
  overflow: hidden;
  color: #8792a6;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cloud-agent-embed__mark {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: linear-gradient(135deg, #4f8cff, #7c4dff);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

.cloud-agent-embed__full {
  height: 30px;
  padding: 0 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid #d6e0ee;
  border-radius: 8px;
  background: #fff;
  color: #52627c;
  cursor: pointer;
  font-size: 12px;
}

.cloud-agent-embed__full:hover {
  border-color: #9bbcf1;
  color: #315fba;
}

.cloud-agent-embed__full svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentcolor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.cloud-agent-embed__body {
  min-height: 0;
  flex: 1;
  overflow: hidden;
}

.cloud-agent-embed__context-state {
  width: 100%;
  height: 100%;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: #718096;
  font-size: 13px;
  text-align: center;
}

.cloud-agent-embed__context-state.is-error strong {
  color: #c2413b;
}

.cloud-agent-embed__context-state button {
  padding: 6px 12px;
  border: 1px solid #cbd8eb;
  border-radius: 7px;
  background: #fff;
  color: #356fc5;
  cursor: pointer;
}
</style>
