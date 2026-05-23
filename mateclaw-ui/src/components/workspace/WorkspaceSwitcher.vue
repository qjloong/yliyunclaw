<template>
  <div class="workspace-switcher" :class="{ collapsed }">
    <div v-if="!collapsed" class="ws-label-row">
      <div class="ws-label">{{ t('nav.workspaceEntryLabel') }}</div>
      <!-- <button
        v-if="canManageWorkspaces"
        class="ws-quick-create"
        type="button"
        :title="t('security.workspaces.quickCreate.entry')"
        @click.stop="openQuickCreateDialog"
      >
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
      </button> -->
    </div>
    <button
      ref="triggerRef"
      class="ws-trigger"
      :title="collapsed ? `${t('nav.workspaceEntryLabel')}: ${currentLabel}` : ''"
      @click="toggleOpen"
    >
      <span class="ws-trigger__icon">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
          <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
        </svg>
      </span>
      <template v-if="!collapsed">
        <span class="ws-trigger__name">{{ currentLabel }}</span>
        <svg class="ws-trigger__arrow" :class="{ open }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
      </template>
    </button>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="open" class="ws-backdrop" @click="open = false"></div>
      </Transition>
      <Transition name="ws-dropdown">
        <div v-if="open" class="ws-dropdown" :class="{ 'ws-dropdown--collapsed': collapsed }" :style="dropdownStyle">
        <div
          v-for="ws in workspaces"
          :key="ws.id"
          class="ws-item"
          :class="{ active: ws.id === currentWorkspaceId }"
          @click="onSelect(ws.id)"
        >
          <svg class="ws-item__icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
          </svg>
          <span class="ws-item__name">{{ ws.name }}</span>
          <svg v-if="ws.id === currentWorkspaceId" class="ws-item__check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
        </div>
        <div class="ws-divider"></div>
        <div v-if="canManageWorkspaces" class="ws-item ws-item--create" @click="openQuickCreateDialog">
          <svg class="ws-item__icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          <span class="ws-item__name">{{ t('security.workspaces.quickCreate.entry') }}</span>
        </div>
        <div class="ws-item ws-item--manage" @click="onManage">
          <svg class="ws-item__icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
          <span class="ws-item__name">{{ t('common.manageWorkspaces') }}</span>
        </div>
      </div>
    </Transition>
    </Teleport>

    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showCreateDialog" class="ws-backdrop" @click="closeQuickCreateDialog"></div>
      </Transition>
      <Transition name="ws-dropdown">
        <div v-if="showCreateDialog" class="ws-modal-shell">
          <div class="ws-modal-card" @click.stop>
            <div class="ws-modal-header">
              <div>
                <h3>{{ t('security.workspaces.quickCreate.title') }}</h3>
                <p>{{ t('security.workspaces.quickCreate.subtitle') }}</p>
              </div>
              <button type="button" class="ws-modal-close" @click="closeQuickCreateDialog">&times;</button>
            </div>
            <div class="ws-modal-body">
              <label class="ws-form-field">
                <span>{{ t('security.workspaces.quickCreate.name') }}</span>
                <input
                  v-model="createForm.name"
                  type="text"
                  class="ws-form-input"
                  :placeholder="t('security.workspaces.quickCreate.namePlaceholder')"
                  @input="syncQuickCreateSlug"
                />
              </label>
              <label class="ws-form-field">
                <span>{{ t('security.workspaces.quickCreate.slug') }}</span>
                <input
                  v-model="createForm.slug"
                  type="text"
                  class="ws-form-input ws-form-input--mono"
                  :placeholder="t('security.workspaces.quickCreate.slugPlaceholder')"
                  @input="quickCreateSlugDirty = true"
                />
              </label>
              <label class="ws-form-field">
                <span>{{ t('security.workspaces.quickCreate.description') }}</span>
                <input
                  v-model="createForm.description"
                  type="text"
                  class="ws-form-input"
                  :placeholder="t('security.workspaces.quickCreate.descriptionPlaceholder')"
                />
              </label>
              <label class="ws-form-field">
                <span>{{ t('security.workspaces.quickCreate.basePath') }}</span>
                <input
                  v-model="createForm.basePath"
                  type="text"
                  class="ws-form-input ws-form-input--mono"
                  :placeholder="t('security.workspaces.quickCreate.basePathPlaceholder')"
                />
                <small>{{ t('security.workspaces.quickCreate.basePathHint') }}</small>
              </label>
            </div>
            <div class="ws-modal-footer">
              <button type="button" class="ws-modal-btn ws-modal-btn--ghost" @click="closeQuickCreateDialog">
                {{ t('security.workspaces.actions.cancel') }}
              </button>
              <button
                type="button"
                class="ws-modal-btn ws-modal-btn--primary"
                :disabled="creatingWorkspace || !createForm.name.trim()"
                @click="createWorkspaceQuickly"
              >
                {{ creatingWorkspace ? t('common.loading') : t('security.workspaces.actions.create') }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { workspaceTeamApi } from '@/api/index'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { canAccessAdminConsole } from '@/utils/access'

const props = defineProps<{
  collapsed?: boolean
}>()

const { t } = useI18n()
const store = useWorkspaceStore()
const router = useRouter()
const route = useRoute()
const open = ref(false)
const triggerRef = ref<HTMLElement | null>(null)
const dropdownPos = ref({ top: 0, left: 0 })
const showCreateDialog = ref(false)
const creatingWorkspace = ref(false)
const quickCreateSlugDirty = ref(false)
const createForm = reactive({
  name: '',
  slug: '',
  description: '',
  basePath: '',
})

const dropdownStyle = computed(() => {
  return {
    position: 'fixed' as const,
    top: `${dropdownPos.value.top}px`,
    left: `${dropdownPos.value.left}px`,
    right: 'auto',
    width: props.collapsed ? '200px' : '212px',
  }
})

function toggleOpen() {
  open.value = !open.value
  if (open.value && triggerRef.value) {
    nextTick(() => {
      const triggerRect = triggerRef.value!.getBoundingClientRect()
      if (props.collapsed) {
        const sidebar = triggerRef.value!.closest('.sidebar')
        const sidebarRight = sidebar ? sidebar.getBoundingClientRect().right : triggerRect.right
        dropdownPos.value = {
          top: triggerRect.top,
          left: sidebarRight + 6,
        }
      } else {
        dropdownPos.value = {
          top: triggerRect.bottom + 4,
          left: triggerRect.left,
        }
      }
    })
  }
}
const workspaces = computed(() => store.workspaces)
const currentWorkspaceId = computed(() => store.currentWorkspaceId)
const currentLabel = computed(() => store.currentWorkspace?.name || 'Workspace')
const canManageWorkspaces = computed(() => canAccessAdminConsole())
const BEFORE_WORKSPACE_SWITCH_EVENT = 'mateclaw:before-workspace-switch'

onMounted(() => {
  store.fetchWorkspaces()
})

async function onSelect(id: string | number) {
  open.value = false
  if (id === currentWorkspaceId.value) {
    return
  }

  await prepareWorkspaceSwitch(id)
  store.switchWorkspace(id)
}

async function prepareWorkspaceSwitch(id: string | number) {
  window.dispatchEvent(new CustomEvent(BEFORE_WORKSPACE_SWITCH_EVENT, {
    detail: {
      fromWorkspaceId: currentWorkspaceId.value,
      toWorkspaceId: String(id),
    },
  }))

  if (route.path === '/chat' && (route.query.conversationId || route.query.agentId)) {
    await router.replace({ path: '/chat', query: {} })
  }
}

function onManage() {
  open.value = false
  if (!canAccessAdminConsole()) {
    ElMessage.warning('没有权限访问工作区管理，请联系管理员。')
    return
  }
  router.push('/settings/workspaces')
}

function resetQuickCreateForm() {
  createForm.name = ''
  createForm.slug = ''
  createForm.description = ''
  createForm.basePath = ''
  quickCreateSlugDirty.value = false
}

function openQuickCreateDialog() {
  open.value = false
  if (!canManageWorkspaces.value) {
    ElMessage.warning(t('security.workspaces.quickCreate.noPermission'))
    return
  }
  resetQuickCreateForm()
  showCreateDialog.value = true
}

function closeQuickCreateDialog() {
  showCreateDialog.value = false
  if (!creatingWorkspace.value) {
    resetQuickCreateForm()
  }
}

function buildWorkspaceSlug(name: string) {
  const baseSlug = String(name || 'workspace')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'workspace'
  const existingSlugs = new Set(workspaces.value.map((workspace) => String(workspace.slug || '').toLowerCase()))
  if (!existingSlugs.has(baseSlug)) {
    return baseSlug
  }
  let suffix = 2
  let candidate = `${baseSlug}-${suffix}`
  while (existingSlugs.has(candidate)) {
    suffix += 1
    candidate = `${baseSlug}-${suffix}`
  }
  return candidate
}

function syncQuickCreateSlug() {
  if (quickCreateSlugDirty.value && createForm.slug.trim()) {
    return
  }
  createForm.slug = buildWorkspaceSlug(createForm.name)
}

async function createWorkspaceQuickly() {
  const name = createForm.name.trim()
  const slug = (createForm.slug.trim() || buildWorkspaceSlug(name)).toLowerCase()
  if (!name || !slug) {
    return
  }

  creatingWorkspace.value = true
  try {
    const response: any = await workspaceTeamApi.create({
      name,
      slug,
      description: createForm.description.trim(),
      basePath: createForm.basePath.trim() || null,
      projectPermissionMode: 'limited',
      workspacePolicy: {
        sandboxMode: 'workspace-write',
        approvalPolicy: 'default',
        networkPolicy: 'inherit',
      },
    })
    const workspace = response?.data
    if (!workspace?.id) {
      throw new Error(t('security.workspaces.quickCreate.createFailed'))
    }
    store.upsertWorkspace(workspace)
    await prepareWorkspaceSwitch(workspace.id)
    store.switchWorkspace(workspace.id)
    closeQuickCreateDialog()
    ElMessage.success(t('security.workspaces.quickCreate.createSuccess', { name: workspace.name || name }))
    void store.fetchWorkspaces()
  } catch (error: any) {
    ElMessage.error(error?.message || t('security.workspaces.quickCreate.createFailed'))
  } finally {
    creatingWorkspace.value = false
  }
}
</script>

<style scoped>
.workspace-switcher {
  padding: 8px 12px;
  position: relative;
}

.workspace-switcher.collapsed {
  padding: 8px 6px;
  display: flex;
  justify-content: center;
}

.ws-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ws-label {
  padding: 0 4px 6px;
  color: var(--mc-sidebar-group-title, var(--mc-text-tertiary));
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.ws-quick-create {
  width: 22px;
  height: 22px;
  border: 1px solid var(--mc-sidebar-border, var(--mc-border-light));
  border-radius: 999px;
  background: var(--mc-sidebar-hover, rgba(0, 0, 0, 0.04));
  color: var(--mc-sidebar-text, var(--mc-text-secondary));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
  margin-bottom: 4px;
}

.ws-quick-create:hover {
  color: var(--mc-primary, #d96d46);
  border-color: var(--mc-primary, #d96d46);
  background: var(--mc-primary-bg, rgba(217, 109, 70, 0.1));
}

/* Trigger */
.ws-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--mc-sidebar-border, var(--mc-border-light));
  border-radius: 12px;
  background: var(--mc-sidebar-hover, rgba(0,0,0,0.04));
  color: var(--mc-sidebar-text, var(--mc-text-primary));
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  transition: all 0.15s;
}

.ws-trigger:hover {
  background: var(--mc-sidebar-active, rgba(0,0,0,0.06));
  border-color: var(--mc-primary, #d96d46);
}

.collapsed .ws-trigger {
  width: 36px;
  height: 36px;
  padding: 0;
  justify-content: center;
  border-radius: 10px;
}

.ws-trigger__icon {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  color: var(--mc-accent, var(--mc-primary));
}

.ws-trigger__name {
  flex: 1;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.ws-trigger__arrow {
  flex-shrink: 0;
  color: var(--mc-sidebar-text, var(--mc-text-tertiary));
  opacity: 0.5;
  transition: transform 0.2s;
}

.ws-trigger__arrow.open {
  transform: rotate(180deg);
}

/* Transitions */
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.ws-dropdown-leave-to { opacity: 0; transform: translateY(-4px) scale(0.98); }
</style>

<style>
/* Teleported to body — must be non-scoped */
.ws-backdrop {
  position: fixed;
  inset: 0;
  z-index: 199;
}

.ws-dropdown {
  z-index: 200;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 14px;
  padding: 6px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  max-height: 280px;
  overflow-y: auto;
}

.ws-dropdown .ws-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
  font-size: 13px;
  color: var(--mc-text-primary);
}

.ws-dropdown .ws-item:hover {
  background: var(--mc-bg-sunken);
}

.ws-dropdown .ws-item.active {
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
  font-weight: 600;
}

.ws-dropdown .ws-item__icon {
  flex-shrink: 0;
  opacity: 0.5;
}

.ws-dropdown .ws-item.active .ws-item__icon {
  opacity: 1;
  color: var(--mc-primary);
}

.ws-dropdown .ws-item__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-dropdown .ws-item__check {
  flex-shrink: 0;
  color: var(--mc-primary);
}

.ws-dropdown .ws-divider {
  height: 1px;
  background: var(--mc-border-light);
  margin: 4px 8px;
}

.ws-dropdown .ws-item--manage {
  color: var(--mc-text-secondary);
}

.ws-dropdown .ws-item--create {
  color: var(--mc-primary);
  font-weight: 600;
}

.ws-dropdown .ws-item--manage:hover {
  color: var(--mc-text-primary);
}

.ws-modal-shell {
  position: fixed;
  inset: 0;
  z-index: 201;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.ws-modal-card {
  width: min(100%, 420px);
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 18px;
  box-shadow: 0 20px 48px rgba(15, 23, 42, 0.18);
  overflow: hidden;
}

.ws-modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px 12px;
}

.ws-modal-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--mc-text-primary);
}

.ws-modal-header p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--mc-text-secondary);
}

.ws-modal-close {
  border: none;
  background: transparent;
  color: var(--mc-text-tertiary);
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}

.ws-modal-body {
  padding: 0 20px 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ws-form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--mc-text-secondary);
}

.ws-form-field small {
  color: var(--mc-text-tertiary);
  line-height: 1.5;
}

.ws-form-input {
  width: 100%;
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  padding: 10px 12px;
  box-sizing: border-box;
  background: var(--mc-bg-sunken);
  color: var(--mc-text-primary);
  font-size: 14px;
}

.ws-form-input:focus {
  outline: none;
  border-color: var(--mc-primary);
  box-shadow: 0 0 0 3px rgba(217, 109, 70, 0.12);
}

.ws-form-input--mono {
  font-family: ui-monospace, SFMono-Regular, SFMono-Regular, Menlo, Consolas, monospace;
}

.ws-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 20px 20px;
}

.ws-modal-btn {
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.ws-modal-btn--ghost {
  background: var(--mc-bg-sunken);
  color: var(--mc-text-secondary);
}

.ws-modal-btn--primary {
  background: var(--mc-primary);
  border-color: var(--mc-primary);
  color: #fff;
}

.ws-modal-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.ws-dropdown-enter-active { transition: all 0.15s ease-out; }
.ws-dropdown-leave-active { transition: all 0.1s ease-in; }
.ws-dropdown-enter-from { opacity: 0; transform: translateY(-6px) scale(0.97); }
.ws-dropdown-leave-to { opacity: 0; transform: translateY(-4px) scale(0.98); }
</style>
