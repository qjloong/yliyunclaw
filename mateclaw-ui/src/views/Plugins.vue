<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner plugins-page">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Extension</div>
            <h1 class="mc-page-title">{{ t('plugins.title') }}</h1>
            <p class="mc-page-desc">{{ t('plugins.desc') }}</p>
          </div>
          <button class="btn-secondary" @click="refresh">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10"/>
              <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/>
            </svg>
            {{ t('plugins.refresh') }}
          </button>
        </div>

        <section class="builtin-plugin-section">
          <div class="section-heading">
            <div>
              <h2>系统内置插件</h2>
              <p>业务能力包通过插件统一管理，按需绑定到智能体实例。</p>
            </div>
          </div>
          <div class="plugins-grid">
            <div class="plugin-card plugin-card--builtin mc-surface-card">
              <div class="plugin-header">
                <div class="plugin-icon-wrap plugin-icon-wrap--teacher">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                    <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                    <path d="M9 7h7M9 11h5M9 15h4"/>
                  </svg>
                </div>
                <div class="plugin-meta">
                  <div class="plugin-name">Teacher 教学命题插件</div>
                  <div class="plugin-version">builtin · junior-chinese</div>
                </div>
                <span class="status-badge status-enabled">Enabled</span>
              </div>

              <p class="plugin-desc">面向初中语文命题场景，提供 6 类 RulePack、Teacher Skill、质量改进草案和 Harness 验收能力。绑定到 Teacher Agent 后在实例配置中展示规则入口。</p>

              <div class="plugin-details">
                <div class="plugin-detail-row">
                  <span class="detail-label">{{ t('plugins.type') }}</span>
                  <span class="type-badge type-builtin">BUILTIN</span>
                </div>
                <div class="plugin-detail-row">
                  <span class="detail-label">{{ t('plugins.status') }}</span>
                  <span class="detail-value">系统内置，默认可用</span>
                </div>
              </div>

              <div class="plugin-capabilities">
                <div class="capability-section">
                  <span class="capability-label">RulePack:</span>
                  <span class="capability-tag">名著</span>
                  <span class="capability-tag">文言文</span>
                  <span class="capability-tag">现代文</span>
                  <span class="capability-tag">古诗词</span>
                  <span class="capability-tag">基础知识</span>
                  <span class="capability-tag">写作</span>
                </div>
                <div class="capability-section">
                  <span class="capability-label">Capabilities:</span>
                  <span class="capability-tag">Teacher Skill</span>
                  <span class="capability-tag">Self-Improve Draft</span>
                  <span class="capability-tag">Harness Acceptance</span>
                </div>
              </div>

              <div class="plugin-actions">
                <button class="btn-secondary" type="button" @click="openTeacherPluginConfig">配置插件</button>
                <button class="btn-secondary" type="button" @click="openAgentBinding">绑定 Agent</button>
              </div>
            </div>
          </div>
        </section>

        <!-- Loading -->
        <div v-if="loading" class="loading-state mc-surface-card">
          <div class="loading-spinner"></div>
          <p>{{ t('plugins.loading') }}</p>
        </div>

        <!-- External Plugin Cards -->
        <div v-else class="plugins-grid">
          <div v-for="plugin in plugins" :key="plugin.name" class="plugin-card mc-surface-card">
            <div class="plugin-header">
              <div class="plugin-icon-wrap">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
                  <path d="M16 3h-8v4h8V3z"/>
                </svg>
              </div>
              <div class="plugin-meta">
                <div class="plugin-name">{{ plugin.displayName || plugin.name }}</div>
                <div class="plugin-version">v{{ plugin.version }}</div>
              </div>
              <label class="toggle-switch" :class="{ disabled: toggling === plugin.name }">
                <input type="checkbox" :checked="plugin.enabled"
                       :disabled="toggling === plugin.name"
                       @change="togglePlugin(plugin)" />
                <span class="toggle-slider"></span>
              </label>
            </div>

            <p class="plugin-desc">{{ plugin.description || t('plugins.noDescription') }}</p>

            <div class="plugin-details">
              <div class="plugin-detail-row">
                <span class="detail-label">{{ t('plugins.type') }}</span>
                <span class="type-badge" :class="'type-' + plugin.type">{{ plugin.type }}</span>
              </div>
              <div class="plugin-detail-row" v-if="plugin.author">
                <span class="detail-label">{{ t('plugins.author') }}</span>
                <span class="detail-value">{{ plugin.author }}</span>
              </div>
              <div class="plugin-detail-row">
                <span class="detail-label">{{ t('plugins.status') }}</span>
                <span class="status-badge" :class="'status-' + (plugin.status || '').toLowerCase()">
                  {{ plugin.status }}
                </span>
              </div>
            </div>

            <!-- Registered capabilities -->
            <div class="plugin-capabilities" v-if="hasCapabilities(plugin)">
              <div class="capability-section" v-if="plugin.registeredTools?.length">
                <span class="capability-label">{{ t('plugins.tools') }}:</span>
                <span class="capability-tag" v-for="tool in plugin.registeredTools" :key="tool">{{ tool }}</span>
              </div>
              <div class="capability-section" v-if="plugin.registeredChannels?.length">
                <span class="capability-label">{{ t('plugins.channels') }}:</span>
                <span class="capability-tag" v-for="ch in plugin.registeredChannels" :key="ch">{{ ch }}</span>
              </div>
              <div class="capability-section" v-if="plugin.registeredProvider">
                <span class="capability-label">{{ t('plugins.provider') }}:</span>
                <span class="capability-tag">{{ plugin.registeredProvider }}</span>
              </div>
              <div class="capability-section" v-if="plugin.registeredMemoryProvider">
                <span class="capability-label">{{ t('plugins.memoryProvider') }}:</span>
                <span class="capability-tag">{{ plugin.registeredMemoryProvider }}</span>
              </div>
            </div>

            <!-- Error message -->
            <div class="plugin-error" v-if="plugin.errorMessage">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="15" y1="9" x2="9" y2="15"/>
                <line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
              {{ plugin.errorMessage }}
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="plugins.length === 0 && !loading" class="empty-state mc-surface-card">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="empty-icon">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
            <path d="M16 3h-8v4h8V3z"/>
          </svg>
          <p class="empty-title">{{ t('plugins.emptyTitle') }}</p>
          <p class="empty-hint">{{ t('plugins.emptyHint') }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { pluginApi } from '@/api'

const { t } = useI18n()
const router = useRouter()

interface PluginInfo {
  name: string
  version: string
  type: string
  displayName: string
  description: string
  author: string
  enabled: boolean
  status: string
  errorMessage?: string
  jarPath?: string
  registeredTools?: string[]
  registeredChannels?: string[]
  registeredProvider?: string
  registeredMemoryProvider?: string
}

const plugins = ref<PluginInfo[]>([])
const loading = ref(false)
const toggling = ref<string | null>(null)

async function loadPlugins() {
  loading.value = true
  try {
    const res = await pluginApi.list()
    plugins.value = res.data || []
  } catch (e: any) {
    ElMessage.error(t('plugins.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function togglePlugin(plugin: PluginInfo) {
  toggling.value = plugin.name
  try {
    if (plugin.enabled) {
      await pluginApi.disable(plugin.name)
      ElMessage.success(t('plugins.disabled', { name: plugin.displayName || plugin.name }))
    } else {
      await pluginApi.enable(plugin.name)
      ElMessage.success(t('plugins.enabled', { name: plugin.displayName || plugin.name }))
    }
    await loadPlugins()
  } catch (e: any) {
    ElMessage.error(e.message || t('plugins.toggleFailed'))
    await loadPlugins()
  } finally {
    toggling.value = null
  }
}

function refresh() {
  loadPlugins()
}

function hasCapabilities(plugin: PluginInfo): boolean {
  return !!(
    plugin.registeredTools?.length ||
    plugin.registeredChannels?.length ||
    plugin.registeredProvider ||
    plugin.registeredMemoryProvider
  )
}

function openTeacherPluginConfig() {
  router.push('/teacher-ops')
}

function openAgentBinding() {
  router.push('/agents')
}

onMounted(() => {
  loadPlugins()
})
</script>

<style scoped>
.plugins-page { gap: 18px; }

.builtin-plugin-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.section-heading h2 {
  margin: 0 0 4px;
  font-size: 17px;
  color: var(--mc-text-primary);
}

.section-heading p {
  margin: 0;
  color: var(--mc-text-secondary);
  font-size: 13px;
}

.plugins-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  margin-top: 8px;
}

.plugin-card {
  padding: 20px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.plugin-card--builtin {
  border-color: color-mix(in srgb, var(--mc-accent, #6366f1) 26%, var(--mc-border, #e5e7eb));
}

.plugin-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.plugin-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mc-accent-bg, #f0f0ff);
  color: var(--mc-accent, #6366f1);
  flex-shrink: 0;
}

.plugin-icon-wrap--teacher {
  background: color-mix(in srgb, var(--mc-accent, #6366f1) 12%, white);
}

.plugin-meta {
  flex: 1;
  min-width: 0;
}

.plugin-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--mc-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.plugin-version {
  font-size: 12px;
  color: var(--mc-text-tertiary);
  margin-top: 1px;
}

.plugin-desc {
  font-size: 13px;
  color: var(--mc-text-secondary);
  line-height: 1.5;
  margin: 0;
}

.plugin-details {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.plugin-detail-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.detail-label {
  color: var(--mc-text-tertiary);
  min-width: 60px;
}

.detail-value {
  color: var(--mc-text-secondary);
}

.type-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
  text-transform: uppercase;
}
.type-tool { background: #dbeafe; color: #1d4ed8; }
.type-provider { background: #fef3c7; color: #92400e; }
.type-channel { background: #d1fae5; color: #065f46; }
.type-memory { background: #ede9fe; color: #5b21b6; }
.type-builtin { background: #e0f2fe; color: #075985; }

.status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}
.status-enabled { background: #d1fae5; color: #065f46; }
.status-disabled { background: #f3f4f6; color: #6b7280; }
.status-error { background: #fee2e2; color: #991b1b; }
.status-loaded { background: #dbeafe; color: #1d4ed8; }

.plugin-capabilities {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 4px;
  border-top: 1px solid var(--mc-border);
}

.capability-section {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
}

.capability-label {
  color: var(--mc-text-tertiary);
  font-weight: 500;
}

.capability-tag {
  background: var(--mc-surface-hover, #f5f5f5);
  color: var(--mc-text-secondary);
  padding: 1px 6px;
  border-radius: 3px;
  font-family: var(--mc-font-mono, monospace);
  font-size: 11px;
}

.plugin-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  background: #fef2f2;
  border-radius: 6px;
  font-size: 12px;
  color: #991b1b;
  line-height: 1.4;
}
.plugin-error svg { flex-shrink: 0; margin-top: 1px; }

.plugin-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-top: 4px;
}

/* Toggle switch (reuse pattern from Tools.vue) */
.toggle-switch { position: relative; display: inline-block; width: 36px; height: 20px; flex-shrink: 0; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider {
  position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0;
  background: var(--mc-border, #d1d5db); border-radius: 20px; transition: 0.2s;
}
.toggle-slider:before {
  position: absolute; content: ""; height: 16px; width: 16px; left: 2px; bottom: 2px;
  background: white; border-radius: 50%; transition: 0.2s;
}
.toggle-switch input:checked + .toggle-slider { background: var(--mc-accent, #6366f1); }
.toggle-switch input:checked + .toggle-slider:before { transform: translateX(16px); }
.toggle-switch.disabled { opacity: 0.5; pointer-events: none; }

/* Loading state */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  text-align: center;
  border-radius: 12px;
  color: var(--mc-text-tertiary);
}
.loading-spinner {
  width: 28px; height: 28px;
  border: 3px solid var(--mc-border, #e5e7eb);
  border-top-color: var(--mc-accent, #6366f1);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  margin-bottom: 12px;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Empty state */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px;
  text-align: center;
  border-radius: 12px;
}
.empty-icon { color: var(--mc-text-tertiary); margin-bottom: 12px; }
.empty-title { font-weight: 600; color: var(--mc-text-primary); margin: 0 0 4px; }
.empty-hint { font-size: 13px; color: var(--mc-text-tertiary); margin: 0; }

/* btn-secondary reuse */
.btn-secondary {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 8px; font-size: 13px; font-weight: 500;
  background: var(--mc-surface-hover, #f5f5f5); color: var(--mc-text-primary);
  border: 1px solid var(--mc-border, #e5e7eb); cursor: pointer; transition: 0.15s;
}
.btn-secondary:hover { background: var(--mc-surface-active, #ebebeb); }

/* Dark mode overrides */
:root.dark .type-tool { background: #1e3a5f; color: #93c5fd; }
:root.dark .type-provider { background: #451a03; color: #fcd34d; }
:root.dark .type-channel { background: #064e3b; color: #6ee7b7; }
:root.dark .type-memory { background: #2e1065; color: #c4b5fd; }
:root.dark .type-builtin { background: #082f49; color: #7dd3fc; }
:root.dark .status-enabled { background: #064e3b; color: #6ee7b7; }
:root.dark .status-disabled { background: #374151; color: #9ca3af; }
:root.dark .status-error { background: #450a0a; color: #fca5a5; }
:root.dark .status-loaded { background: #1e3a5f; color: #93c5fd; }
:root.dark .plugin-error { background: #450a0a; color: #fca5a5; }
:root.dark .plugin-icon-wrap { background: #2e1065; }
:root.dark .plugin-icon-wrap--teacher { background: #082f49; }
</style>
