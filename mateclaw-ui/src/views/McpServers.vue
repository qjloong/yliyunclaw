<template>
  <div class="settings-section mcp-section-page">
    <div class="section-header">
      <div>
        <h2 class="section-title">{{ t('mcp.title') }}</h2>
        <p class="section-desc">{{ t('mcp.subtitle') }}</p>
      </div>
      <div class="section-header__actions">
        <button class="btn-secondary" :disabled="isRefreshing" @click="refreshAll">
          <svg
            width="14"
            height="14"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            :class="{ spin: isRefreshing }"
          >
            <polyline points="23 4 23 10 17 10" />
            <polyline points="1 20 1 14 7 14" />
            <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" />
          </svg>
          {{ t('mcp.refreshAll') }}
        </button>
        <button class="btn-primary" @click="openCreateModal">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          {{ t('mcp.addCustom') }}
        </button>
      </div>
    </div>

    <div class="mcp-filter-bar">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8" />
        <line x1="21" y1="21" x2="16.65" y2="16.65" />
      </svg>
      <input
        v-model="search"
        class="mcp-search-input"
        type="search"
        :placeholder="t('mcp.searchPlaceholder')"
      />
    </div>

    <McpEmptyState
      v-if="!isLoading && installedTotal === 0 && !search"
      kind="initial"
      @add="openCreateModal"
    />

    <section v-if="installedTotal > 0 || search" class="mcp-block">
      <div class="mcp-block-head">
        <div class="mcp-block-title">{{ t('mcp.sections.added') }}</div>
        <div class="mcp-block-count">
          {{ t('mcp.sections.countItems', { n: installedTotal }) }}
        </div>
      </div>
      <McpEmptyState v-if="installedTotal === 0" kind="search" />
      <div v-else class="mcp-grid">
        <McpCard
          v-for="server in pagedInstalled"
          :key="server.id"
          :server="server"
          :testing="testingId === server.id"
          :diagnosing="diagnosingId === server.id"
          @edit="openEditModal"
          @test="testServer"
          @diagnose="openDiagnostics"
          @toggle="toggleServer"
          @set-tier="setServerTier"
        />
      </div>
      <div v-if="installedTotal > pageSize" class="mcp-pager-row">
        <McPagination
          v-model:page="installedPage"
          v-model:size="pageSize"
          :total="installedTotal"
          :sizes="[12, 24, 48]"
        />
      </div>
    </section>

    <section v-if="catalogTotal > 0" class="mcp-block">
      <div class="mcp-block-head">
        <div class="mcp-block-title">{{ t('mcp.sections.recommended') }}</div>
        <div class="mcp-block-count">
          {{ t('mcp.sections.countItems', { n: catalogTotal }) }}
        </div>
      </div>
      <div class="mcp-grid">
        <McpCard
          v-for="entry in pagedCatalog"
          :key="entry.key"
          :catalog-entry="entry"
          @add="openFromCatalog"
          @docs="openDocs"
        />
      </div>
      <div v-if="catalogTotal > pageSize" class="mcp-pager-row">
        <McPagination
          v-model:page="catalogPage"
          v-model:size="pageSize"
          :total="catalogTotal"
          :sizes="[12, 24, 48]"
        />
      </div>
    </section>

    <McpFormModal
      v-model="modalVisible"
      :editing="editingServer"
      :prefill="catalogPrefill"
      @save="onSave"
      @delete="onDelete"
    />

    <el-dialog
      v-model="diagnosticsVisible"
      :title="t('mcp.diagnostics.title')"
      width="min(640px, 92vw)"
      destroy-on-close
    >
      <div v-if="diagnosingId !== null" class="diagnostic-loading">
        {{ t('mcp.diagnostics.running') }}
      </div>
      <template v-else-if="diagnosticResult">
        <div
          class="diagnostic-summary"
          :class="diagnosticResult.success ? 'diagnostic-summary--ok' : 'diagnostic-summary--fail'"
        >
          {{
            diagnosticResult.success
              ? t('mcp.diagnostics.allPassed')
              : t('mcp.diagnostics.hasFailures')
          }}
        </div>
        <div class="diagnostic-trace">
          <span>{{ t('mcp.diagnostics.traceId') }}</span>
          <code>{{ diagnosticResult.traceId }}</code>
        </div>
        <ol class="diagnostic-stages">
          <li
            v-for="stage in diagnosticResult.stages"
            :key="stage.stage"
            class="diagnostic-stage"
          >
            <span
              class="diagnostic-stage__status"
              :class="stage.success ? 'diagnostic-stage__status--ok' : 'diagnostic-stage__status--fail'"
            >
              {{ stage.success ? '✓' : '!' }}
            </span>
            <div class="diagnostic-stage__body">
              <div class="diagnostic-stage__head">
                <strong>{{ diagnosticStageLabel(stage.stage) }}</strong>
                <span v-if="stage.latencyMs">{{ stage.latencyMs }} ms</span>
              </div>
              <p>{{ stage.message }}</p>
              <p v-if="stage.suggestion" class="diagnostic-stage__suggestion">
                {{ t('mcp.diagnostics.suggestion') }}：{{ stage.suggestion }}
              </p>
            </div>
          </li>
        </ol>
      </template>
      <label class="diagnostic-write-option">
        <input v-model="includeWriteDiagnostic" type="checkbox" />
        <span>{{ t('mcp.diagnostics.includeWrite') }}</span>
      </label>
      <template #footer>
        <button class="btn-secondary" @click="diagnosticsVisible = false">
          {{ t('common.close') }}
        </button>
        <button
          class="btn-primary"
          :disabled="diagnosingId !== null || !diagnosticsServer"
          @click="diagnosticsServer && diagnoseServer(diagnosticsServer, includeWriteDiagnostic)"
        >
          {{ t('mcp.diagnostics.rerun') }}
        </button>
      </template>
    </el-dialog>

    <transition name="toast">
      <div
        v-if="testResult"
        class="test-toast"
        :class="testResult.success ? 'toast-ok' : 'toast-fail'"
      >
        <strong>{{
          testResult.success
            ? t('mcp.testResult.success')
            : t('mcp.testResult.failed')
        }}</strong>
        <span v-if="testResult.success">
          {{ t('mcp.testResult.tools', { count: testResult.toolCount }) }} ·
          {{ t('mcp.testResult.latency', { ms: testResult.latencyMs }) }}
        </span>
        <span v-else>{{ testResult.message }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import McPagination from '@/components/common/McPagination.vue'
import { mcConfirm } from '@/components/common/useConfirm'
import { mcToast } from '@/composables/useMcToast'
import { mcpApi } from '@/api/index'
import { useMcpServers } from '@/composables/useMcpServers'
import McpCard from './mcp/McpCard.vue'
import McpFormModal from './mcp/McpFormModal.vue'
import McpEmptyState from './mcp/McpEmptyState.vue'
import type { McpServer, McpServerForm } from './mcp/types'
import type { McpCatalogEntry } from './mcp/catalog'

const { t } = useI18n()

const {
  isLoading,
  isRefreshing,
  testingId,
  testResult,
  diagnosingId,
  diagnosticResult,
  search,
  pageSize,
  installedPage,
  catalogPage,
  filteredInstalled,
  filteredCatalog,
  pagedInstalled,
  pagedCatalog,
  reload,
  refreshAll,
  saveServer,
  removeServer,
  testServer,
  diagnoseServer,
  toggleServer,
} = useMcpServers()

const modalVisible = ref(false)
const editingServer = ref<McpServer | null>(null)
const catalogPrefill = ref<McpCatalogEntry | null>(null)
const diagnosticsVisible = ref(false)
const diagnosticsServer = ref<McpServer | null>(null)
const includeWriteDiagnostic = ref(false)

const installedTotal = computed(() => filteredInstalled.value.length)
const catalogTotal = computed(() => filteredCatalog.value.length)

function openCreateModal() {
  editingServer.value = null
  catalogPrefill.value = null
  modalVisible.value = true
}
function openEditModal(server: McpServer) {
  editingServer.value = server
  catalogPrefill.value = null
  modalVisible.value = true
}
function openFromCatalog(entry: McpCatalogEntry) {
  editingServer.value = null
  catalogPrefill.value = entry
  modalVisible.value = true
}
function openDocs(url: string) {
  window.open(url, '_blank', 'noopener,noreferrer')
}

function openDiagnostics(server: McpServer) {
  diagnosticsServer.value = server
  diagnosticsVisible.value = true
  diagnoseServer(server, false)
}

function diagnosticStageLabel(stage: string) {
  const key = `mcp.diagnostics.stages.${stage}`
  const translated = t(key)
  return translated === key ? stage : translated
}

async function onSave(form: McpServerForm, editing: McpServer | null) {
  const ok = await saveServer(form, editing)
  if (ok) modalVisible.value = false
}

async function onDelete(server: McpServer) {
  const confirmed = await mcConfirm({
    title: t('common.delete'),
    message: t('mcp.messages.deleteConfirm', { name: server.name }),
    tone: 'danger',
  })
  if (!confirmed) return
  const ok = await removeServer(server)
  if (ok) modalVisible.value = false
}

async function setServerTier(server: McpServer, tier: 'core' | 'extension') {
  try {
    await mcpApi.setDisclosureTier(server.id, tier)
    await reload()
  } catch (e: any) {
    mcToast.error(e?.message || t('mcp.messages.tierFailed'))
  }
}

onMounted(reload)
</script>

<style scoped>
/* Mirror the Settings sub-page convention used by Settings/Models/index.vue
   etc. — Settings/Layout.vue already wraps every sub-route in the global
   .mc-page-* frame, so this page renders directly inside the existing
   .settings-content__inner without adding its own shell. */
.settings-section { width: 100%; }
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}
.section-title { margin: 0 0 6px; font-size: 22px; font-weight: 700; color: var(--mc-text-primary); }
.section-desc { margin: 0; font-size: 14px; color: var(--mc-text-secondary); }
.section-header__actions { display: flex; gap: 8px; flex-shrink: 0; }

.btn-primary,
.btn-secondary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
  white-space: nowrap;
}
.btn-primary {
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: #fff;
  border: none;
  font-weight: 600;
  box-shadow: var(--mc-shadow-soft);
}
.btn-primary:hover { filter: brightness(1.04); }
.btn-secondary {
  background: var(--mc-bg-elevated);
  color: var(--mc-text-primary);
  border: 1px solid var(--mc-border);
}
.btn-secondary:hover { background: var(--mc-bg-sunken); }
.btn-secondary:disabled { opacity: 0.5; cursor: not-allowed; }

.mcp-filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 14px;
  margin-bottom: 22px;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border-light);
  border-radius: 12px;
}
.mcp-filter-bar > svg { color: var(--mc-text-tertiary); flex-shrink: 0; }
.mcp-search-input {
  flex: 1;
  padding: 4px 0;
  border: none;
  background: transparent;
  color: var(--mc-text-primary);
  font-size: 14px;
  outline: none;
}
.mcp-search-input::placeholder { color: var(--mc-text-tertiary); }

.mcp-block { margin-bottom: 28px; }
.mcp-block:last-child { margin-bottom: 0; }
.mcp-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.mcp-block-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--mc-text-tertiary);
}
.mcp-block-count {
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

/* Auto-fill responsive grid — cards reflow to fit available width.
   `minmax(min(320px, 100%), 1fr)` forces a single column on viewports
   narrower than the min track size, and the `minmax(0, ...)` semantics
   keep `text-overflow: ellipsis` working on long card descriptions. */
.mcp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(320px, 100%), 1fr));
  gap: 12px;
}

.mcp-pager-row {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.test-toast {
  position: fixed;
  bottom: 24px;
  right: 24px;
  padding: 12px 18px;
  border-radius: 10px;
  z-index: 2000;
  box-shadow: var(--mc-shadow-medium);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.toast-ok { background: var(--mc-primary); color: #fff; }
.toast-fail { background: var(--mc-danger, #ef4444); color: #fff; }
.toast-enter-active,
.toast-leave-active { transition: all 0.25s ease; }
.toast-enter-from,
.toast-leave-to { opacity: 0; transform: translateY(16px); }

.diagnostic-loading {
  padding: 40px 0;
  text-align: center;
  color: var(--mc-text-secondary);
}
.diagnostic-summary {
  margin-bottom: 16px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
}
.diagnostic-summary--ok { color: #047857; background: #ecfdf5; }
.diagnostic-summary--fail { color: #b91c1c; background: #fef2f2; }
.diagnostic-trace {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--mc-text-tertiary);
  font-size: 12px;
}
.diagnostic-trace code {
  overflow-wrap: anywhere;
  color: var(--mc-text-secondary);
}
.diagnostic-stages {
  list-style: none;
  margin: 0;
  padding: 0;
}
.diagnostic-stage {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--mc-border-light);
}
.diagnostic-stage:last-child { border-bottom: none; }
.diagnostic-stage__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
}
.diagnostic-stage__status--ok { background: #10b981; }
.diagnostic-stage__status--fail { background: #ef4444; }
.diagnostic-stage__body { min-width: 0; flex: 1; }
.diagnostic-stage__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--mc-text-primary);
  font-size: 13px;
}
.diagnostic-stage__head span { color: var(--mc-text-tertiary); font-size: 12px; }
.diagnostic-stage__body p {
  margin: 4px 0 0;
  color: var(--mc-text-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
.diagnostic-stage__suggestion { color: #b45309 !important; }
.diagnostic-write-option {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 14px;
  color: var(--mc-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.diagnostic-write-option input { margin-top: 2px; }

@keyframes spin { to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }

@media (max-width: 720px) {
  .section-header { flex-direction: column; }
  .section-header__actions { width: 100%; }
  .btn-primary,
  .btn-secondary { flex: 1; justify-content: center; }
}
</style>
