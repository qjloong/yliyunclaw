<template>
  <div class="desktop-proxy-page">
    <div class="desktop-proxy-card">
      <div class="desktop-proxy-head">
        <div class="desktop-proxy-head__main">
          <h1 class="desktop-proxy-title">{{ t('desktopProxy.title') }}</h1>
          <p>{{ t('desktopProxy.description') }}</p>
        </div>
      </div>

      <div class="proxy-form">
        <label class="proxy-form-label" for="desktop-proxy-input">{{ t('desktopProxy.proxyUrlLabel') }}</label>
        <input
          id="desktop-proxy-input"
          v-model="desktopSettings.proxyUrl"
          type="text"
          class="proxy-input"
          :placeholder="t('desktopProxy.proxyUrlPlaceholder')"
        />
        <div class="proxy-form-hint">{{ t('desktopProxy.proxyHint') }}</div>
        <div v-if="feedback" class="proxy-feedback" :class="feedbackClass">
          {{ feedback }}
        </div>
      </div>

      <div class="proxy-actions">
        <div class="proxy-actions__secondary">
          <button type="button" class="proxy-btn proxy-btn--ghost" :disabled="testing || saving" @click="reloadDesktopSettings">
            {{ t('common.reset') }}
          </button>
          <button type="button" class="proxy-btn proxy-btn--ghost" :disabled="testing || saving" @click="clearProxyUrl">
            {{ t('desktopProxy.clearAction') }}
          </button>
        </div>
        <div class="proxy-actions__primary">
          <button type="button" class="proxy-btn proxy-btn--soft" :disabled="testing || saving" @click="testProxyTarget">
            {{ t('desktopServer.testConnection') }}
          </button>
          <button type="button" class="proxy-btn proxy-btn--primary" :disabled="testing || saving" @click="saveProxyUrl">
            {{ t('common.save') }}
          </button>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getDesktopRuntimeInfo,
  getDesktopServerConfig,
  saveDesktopServerConfig,
  testDesktopServer,
  type DesktopRuntimeInfo,
  type DesktopServerConfig,
  type DesktopServerTestResult,
} from '@/utils/desktop'

const { t } = useI18n()
const saving = ref(false)
const testing = ref(false)
const saveTip = ref('')
const testResult = ref<DesktopServerTestResult | null>(null)
const runtimeInfo = ref<DesktopRuntimeInfo | null>(null)
const desktopSettings = reactive<DesktopServerConfig>({
  backendUrl: 'http://127.0.0.1:18088',
  proxyUrl: '',
  autoStartBackend: false,
  backendCommand: '',
  backendArgs: [],
  backendCwd: '',
  updatedAt: null,
})

const feedback = computed(() => {
  if (saveTip.value) {
    return saveTip.value
  }
  if (testResult.value?.message) {
    return testResult.value.message
  }
  if (testResult.value?.body) {
    return String(testResult.value.body)
  }
  return testResult.value?.url || ''
})

const feedbackClass = computed(() => ({
  'proxy-feedback--error': Boolean(testResult.value && !testResult.value.success),
  'proxy-feedback--success': Boolean(saveTip.value || testResult.value?.success),
}))

onMounted(async () => {
  await reloadDesktopSettings()
})

function applyDesktopConfig(config?: Partial<DesktopServerConfig> | null) {
  if (!config) return
  desktopSettings.backendUrl = config.backendUrl || desktopSettings.backendUrl
  desktopSettings.proxyUrl = typeof config.proxyUrl === 'string' ? config.proxyUrl : desktopSettings.proxyUrl
  desktopSettings.autoStartBackend = Boolean(config.autoStartBackend)
  desktopSettings.backendCommand = config.backendCommand || ''
  desktopSettings.backendArgs = Array.isArray(config.backendArgs) ? config.backendArgs : []
  desktopSettings.backendCwd = config.backendCwd || ''
  desktopSettings.updatedAt = config.updatedAt || null
}

function applyRuntimeInfo(runtime?: DesktopRuntimeInfo | null) {
  if (!runtime) return
  if (runtime.backendUrl) {
    desktopSettings.backendUrl = runtime.backendUrl
  }
  if (typeof runtime.proxyUrl === 'string') {
    desktopSettings.proxyUrl = runtime.proxyUrl
  }
}

async function reloadDesktopSettings() {
  saveTip.value = ''
  testResult.value = null
  const [runtime, config] = await Promise.all([
    getDesktopRuntimeInfo(),
    getDesktopServerConfig(),
  ])
  runtimeInfo.value = (runtime as DesktopRuntimeInfo | null) || null
  applyDesktopConfig(config)
  applyRuntimeInfo(runtimeInfo.value)
}

function buildPayload(): DesktopServerConfig {
  return {
    ...desktopSettings,
    backendUrl: String(desktopSettings.backendUrl || '').trim(),
    proxyUrl: String(desktopSettings.proxyUrl || '').trim(),
  }
}

async function testProxyTarget() {
  testing.value = true
  saveTip.value = ''
  testResult.value = null
  try {
    testResult.value = await testDesktopServer(buildPayload())
  } catch (error: any) {
    testResult.value = {
      success: false,
      url: desktopSettings.proxyUrl || desktopSettings.backendUrl,
      message: error?.message || t('desktopServer.testFailed'),
    }
  } finally {
    testing.value = false
  }
}

async function saveProxyUrl() {
  saving.value = true
  saveTip.value = ''
  testResult.value = null
  try {
    await saveDesktopServerConfig(buildPayload())
    const [runtime, config] = await Promise.all([
      getDesktopRuntimeInfo(),
      getDesktopServerConfig(),
    ])
    runtimeInfo.value = (runtime as DesktopRuntimeInfo | null) || runtimeInfo.value
    applyDesktopConfig(config)
    applyRuntimeInfo(runtimeInfo.value)
    saveTip.value = t('desktopProxy.saveSuccess')
  } catch (error: any) {
    testResult.value = {
      success: false,
      url: desktopSettings.proxyUrl || desktopSettings.backendUrl,
      message: error?.message || t('desktopProxy.saveFailed'),
    }
  } finally {
    saving.value = false
  }
}

async function clearProxyUrl() {
  desktopSettings.proxyUrl = ''
  await saveProxyUrl()
}
</script>

<style scoped>
.desktop-proxy-page {
  min-height: 100vh;
  display: flex;
  align-items: stretch;
  justify-content: stretch;
  padding: 0;
  background: var(--mc-bg);
}

:root.dark .desktop-proxy-page,
html.dark .desktop-proxy-page {
  background: var(--mc-bg);
}

.desktop-proxy-card {
  width: 100%;
  min-height: 100vh;
  background: var(--mc-bg-elevated);
  border: none;
  border-radius: 0;
  box-shadow: none;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-sizing: border-box;
}

.desktop-proxy-head {
  display: block;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--mc-border-light);
}

.desktop-proxy-head__main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.desktop-proxy-title {
  margin: 0;
  font-size: 17px;
  line-height: 1.35;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.desktop-proxy-head p {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-secondary);
}

.proxy-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
}

.proxy-form-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-text-primary);
}

.proxy-input {
  width: 100%;
  min-height: 40px;
  border: 1px solid var(--mc-border);
  border-radius: 10px;
  padding: 9px 12px;
  font-size: 13px;
  background: var(--mc-bg-sunken);
  color: var(--mc-text-primary);
  user-select: text;
  -webkit-user-select: text;
  -webkit-app-region: no-drag;
}

.proxy-input:focus {
  outline: none;
  border-color: var(--mc-primary);
  box-shadow: 0 0 0 3px rgba(217, 119, 87, 0.12);
  background: var(--mc-bg-elevated);
}

.proxy-form-hint {
  font-size: 11px;
  color: var(--mc-text-secondary);
  line-height: 1.55;
}

.proxy-feedback {
  margin-top: 6px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(59, 130, 246, 0.16);
  background: rgba(59, 130, 246, 0.08);
  font-size: 13px;
  color: #1d4ed8;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.proxy-feedback--success {
  background: rgba(34, 197, 94, 0.1);
  border-color: rgba(34, 197, 94, 0.22);
  color: #15803d;
}

.proxy-feedback--error {
  background: rgba(239, 68, 68, 0.1);
  border-color: rgba(239, 68, 68, 0.22);
  color: #b91c1c;
}

.proxy-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--mc-border-light);
}

.proxy-actions__secondary,
.proxy-actions__primary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.proxy-btn {
  min-width: 88px;
  height: 34px;
  border-radius: 10px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.18s ease;
}

.proxy-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.proxy-btn--ghost {
  background: var(--mc-bg-sunken);
  border-color: var(--mc-border);
  color: var(--mc-text-secondary);
}

.proxy-btn--ghost:hover:not(:disabled) {
  border-color: rgba(217, 119, 87, 0.28);
  color: var(--mc-primary);
}

.proxy-btn--soft {
  background: rgba(217, 119, 87, 0.1);
  color: var(--mc-primary);
  border-color: rgba(217, 119, 87, 0.18);
}

.proxy-btn--soft:hover:not(:disabled) {
  background: rgba(217, 119, 87, 0.14);
}

.proxy-btn--primary {
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: #fff;
  box-shadow: 0 6px 14px rgba(217, 119, 87, 0.18);
}

.proxy-btn--primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(217, 119, 87, 0.22);
}

@media (max-width: 640px) {
  .desktop-proxy-card {
    padding: 14px;
  }

  .proxy-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .proxy-actions__secondary,
  .proxy-actions__primary {
    width: 100%;
  }

  .proxy-btn {
    flex: 1 1 0;
  }
}
</style>
