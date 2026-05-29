<template>
  <div class="mc-page-shell wiki-shell">
    <div class="mc-page-frame wiki-frame">
      <div class="mc-page-inner wiki-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">{{ t('wiki.kicker') }}</div>
            <h1 class="mc-page-title">{{ t('nav.wiki') }}</h1>
            <p class="mc-page-desc">{{ t('wiki.desc') }}</p>
          </div>
          <button class="btn-primary page-cta" @click="showCreateKB = true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            {{ t('wiki.createKB') }}
          </button>
        </div>

        <div class="wiki-layout">
          <!-- Left sidebar: KB list + page list -->
          <div class="wiki-sidebar mc-surface-card">

            <div class="sidebar-nav">
              <button
                class="sidebar-nav-btn"
                :class="{ active: sidebarView === 'knowledge' || !store.currentKB }"
                @click="sidebarView = 'knowledge'"
              >
                {{ t('wiki.knowledgeBases') }}
              </button>
              <button
                v-if="store.currentKB"
                class="sidebar-nav-btn sidebar-nav-btn--current"
                :class="{ active: sidebarView === 'pages' }"
                @click="sidebarView = 'pages'"
              >
                {{ store.currentKB.name }}
              </button>
            </div>

            <!-- Knowledge bases -->
            <div v-if="sidebarView === 'knowledge' || !store.currentKB" class="sidebar-section sidebar-section--fill">
              <h3 class="sidebar-label">{{ t('wiki.knowledgeBases') }}</h3>
              <div v-if="store.loading" class="empty-hint">{{ t('common.loading') }}</div>
              <div v-else-if="store.knowledgeBases.length === 0" class="empty-hint">{{ t('wiki.noKB') }}</div>
              <div v-else class="kb-list kb-list--scroll">
                <div
                  v-for="kb in store.knowledgeBases" :key="kb.id"
                  class="kb-item" :class="{ active: store.currentKB?.id === kb.id }"
                  @click="selectKB(kb.id)"
                >
                  <div class="kb-item-header">
                    <div class="kb-item-name">{{ kb.name }}</div>
                    <div class="kb-item-actions">
                      <button
                        class="kb-edit-btn"
                        :title="t('common.edit')"
                        @click.stop="openEditKB(kb)"
                      >
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                        </svg>
                      </button>
                      <button
                        class="kb-delete-btn"
                        :title="t('wiki.deleteKB')"
                        @click.stop="handleDeleteKB(kb)"
                      >
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="3 6 5 6 21 6"/>
                          <path d="M19 6l-1 14H6L5 6"/>
                          <path d="M10 11v6"/>
                          <path d="M14 11v6"/>
                          <path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                        </svg>
                      </button>
                    </div>
                  </div>
                  <div class="kb-item-stats">
                    <span class="stat-chip">
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                      {{ kb.pageCount }}
                    </span>
                    <span class="stat-chip">
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
                      {{ kb.rawCount }}
                    </span>
                    <span class="kb-status-dot" :class="kb.status" :title="t(`wiki.status.${kb.status}`)"></span>
                  </div>
                  <div v-if="kb.kbKind === 'business' && kb.domainProfileId" class="kb-business-tag">
                    {{ domainProfileMap[kb.domainProfileId] || kb.domainProfileId }}
                  </div>
                  <div v-if="kbStats[kb.id]?.failedJobCount > 0" class="kb-warn">
                    ⚠ {{ t('wiki.stats.failedJobs', { count: kbStats[kb.id].failedJobCount }) }}
                  </div>
                </div>
              </div>
            </div>

            <!-- Page list -->
            <div v-if="store.currentKB && sidebarView === 'pages'" class="sidebar-section sidebar-section--pages">
              <!-- <div class="sidebar-breadcrumb">
                <button class="breadcrumb-back" @click="sidebarView = 'knowledge'">
                  {{ t('wiki.knowledgeBases') }}
                </button>
                <span class="breadcrumb-sep">/</span>
                <span class="breadcrumb-current">{{ store.currentKB.name }}</span>
              </div> -->

              <div class="sidebar-title-row">
                <h3 class="sidebar-label">
                  {{ t('wiki.pages') }}
                  <span class="count-badge">{{ store.pages.length }}</span>
                </h3>
                <div class="sidebar-title-actions">
                  <button
                    v-if="!batchMode"
                    class="icon-btn" :title="t('wiki.batchSelect')"
                    @click="batchMode = true"
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
                  </button>
                  <button v-else class="icon-btn icon-btn--active" @click="exitBatchMode">✕</button>
                </div>
              </div>

              <!-- Search -->
              <div class="search-wrap">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
                <input
                  v-model="pageSearch"
                  type="text"
                  :placeholder="t('wiki.searchPages')"
                  class="search-input"
                />
                <button v-if="pageSearch" class="search-clear" @click="pageSearch = ''">✕</button>
              </div>

              <!-- Batch actions -->
              <div v-if="batchMode" class="batch-bar">
                <label class="batch-check-all">
                  <input type="checkbox" :checked="allSelected" @change="toggleSelectAll" />
                  <span>{{ t('wiki.selectAll') }}</span>
                </label>
                <button
                  class="batch-delete-btn"
                  :disabled="selectedSlugs.length === 0"
                  @click="handleBatchDelete"
                >
                  {{ t('common.delete') }} ({{ selectedSlugs.length }})
                </button>
              </div>

              <!-- Raw material filter banner -->
              <div v-if="store.selectedRawId" class="filter-banner">
                <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M22 3H2l8 9.46V19l4 2V12.46L22 3z"/></svg>
                {{ t('wiki.filteredByRaw') }}
                <button class="filter-clear-btn" @click="store.clearRawFilter(store.currentKB!.id)">✕</button>
              </div>

              <!-- Grouped page list -->
              <div class="page-list" v-if="!pageSearch" ref="pageListEl" @scroll="onPageListScroll">
                <div v-for="group in activePageGroups" :key="group.key" class="page-group">
                  <button
                    class="group-header"
                    @click="toggleGroup(group.key)"
                  >
                    <svg
                      class="group-chevron"
                      :class="{ expanded: !collapsedGroups.has(group.key) }"
                      width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                    ><polyline points="9 18 15 12 9 6"/></svg>
                    <span class="group-text">
                      <span class="group-label">{{ group.title }}</span>
                      <span v-if="group.subtitle" class="group-subtitle">{{ group.subtitle }}</span>
                    </span>
                    <span class="group-count">{{ group.pages.length }}</span>
                  </button>
                  <div v-if="!collapsedGroups.has(group.key)" class="group-items">
                    <div
                      v-for="page in paginatedGroupPages(group)" :key="page.slug"
                      class="page-item"
                      :class="{
                        active: !batchMode && store.currentPage?.slug === page.slug,
                        'page-item--system': page.pageType === 'system'
                      }"
                      @click="batchMode ? toggleSelect(page.slug) : openPage(page.slug)"
                    >
                      <!-- RFC-051 PR-8: batch checkbox is disabled for protected pages so a stray
                           batch-delete can't drag in overview/log or any locked page. -->
                      <input
                        v-if="batchMode"
                        type="checkbox"
                        :checked="selectedSlugs.includes(page.slug)"
                        :disabled="isProtectedPage(page)"
                        class="page-checkbox"
                        @click.stop="!isProtectedPage(page) && toggleSelect(page.slug)"
                      />
                      <div class="page-item-body">
                        <div class="page-item-title">{{ page.title }}</div>
                        <div class="page-item-meta">
                          <span v-if="page.lastUpdatedBy === 'manual'" class="edit-dot manual" title="Manual edit"></span>
                          <span v-if="page.pageType === 'system'" class="page-flag page-flag--system">{{ t('wiki.systemPageBadge') }}</span>
                          <span v-else-if="page.locked === 1" class="page-flag page-flag--locked">{{ t('wiki.lockedPageBadge') }}</span>
                          <span class="meta-text">v{{ page.version }}</span>
                        </div>
                      </div>
                    </div>
                    <!-- Load more within group (visible when more items exist) -->
                    <button
                      v-if="(groupPageLimit[group.key] || PAGE_STEP) < group.pages.length"
                      class="load-more-btn"
                      @click.stop="loadMoreGroup(group.key)"
                    >
                      {{ t('wiki.loadMore', { n: Math.min(PAGE_STEP, group.pages.length - (groupPageLimit[group.key] || PAGE_STEP)) }) }}
                    </button>
                  </div>
                </div>
                <div v-if="activePageGroups.length === 0" class="empty-hint">{{ t('wiki.noPages') }}</div>

                <!-- RFC-051 PR-7 follow-up: archived pages drawer at the bottom of the list. -->
                <div class="archived-section">
                  <button
                    class="archived-toggle"
                    :disabled="archivedLoading"
                    @click="toggleArchived"
                  >
                    <svg
                      class="archived-chevron"
                      :class="{ expanded: archivedOpen }"
                      width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"
                    ><polyline points="9 18 15 12 9 6"/></svg>
                    {{ t('wiki.archivedSection') }}
                    <span v-if="archivedPages.length > 0" class="archived-count">{{ archivedPages.length }}</span>
                  </button>
                  <div v-if="archivedOpen" class="archived-items">
                    <div v-if="archivedLoading" class="archived-empty">{{ t('common.loading') }}</div>
                    <div v-else-if="archivedPages.length === 0" class="archived-empty">{{ t('wiki.noArchived') }}</div>
                    <div v-else
                         v-for="page in archivedPages" :key="'arc:' + page.slug"
                         class="archived-item"
                         @click="openPage(page.slug)"
                    >
                      <div class="archived-item-body">
                        <div class="archived-item-title">{{ page.title }}</div>
                        <div class="archived-item-meta">v{{ page.version }} · {{ page.slug }}</div>
                      </div>
                      <button
                        class="archived-restore-btn"
                        :title="t('wiki.unarchive')"
                        @click.stop="restoreArchivedPage(page.slug)"
                      >
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                          <polyline points="3 7 12 12 21 7"/><path d="M3 7v10l9 5 9-5V7"/>
                        </svg>
                        {{ t('wiki.unarchive') }}
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Flat search results -->
              <div class="page-list" v-else>
                <div
                  v-for="page in paginatedSearch" :key="page.slug"
                  class="page-item"
                  :class="{
                    active: !batchMode && store.currentPage?.slug === page.slug,
                    'page-item--system': page.pageType === 'system'
                  }"
                  @click="batchMode ? toggleSelect(page.slug) : openPage(page.slug)"
                >
                  <input
                    v-if="batchMode"
                    type="checkbox"
                    :checked="selectedSlugs.includes(page.slug)"
                    :disabled="isProtectedPage(page)"
                    class="page-checkbox"
                    @click.stop="!isProtectedPage(page) && toggleSelect(page.slug)"
                  />
                  <div class="page-item-body">
                    <div class="page-item-title">{{ page.title }}</div>
                    <div class="page-item-meta">
                      <span class="type-chip">{{ formatGroupLabel(page.pageType || 'other') }}</span>
                      <span v-if="page.pageType === 'system'" class="page-flag page-flag--system">{{ t('wiki.systemPageBadge') }}</span>
                      <span v-else-if="page.locked === 1" class="page-flag page-flag--locked">{{ t('wiki.lockedPageBadge') }}</span>
                      <span class="meta-text">v{{ page.version }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="filteredPages.length > searchPageLimit" class="pagination-row">
                  <button class="load-more-btn" @click="searchPageLimit += PAGE_STEP">
                    {{ t('wiki.loadMore', { n: Math.min(PAGE_STEP, filteredPages.length - searchPageLimit) }) }}
                  </button>
                </div>
                <div v-if="filteredPages.length === 0" class="empty-hint">{{ t('wiki.noResults') }}</div>
              </div>
            </div>
          </div>

          <!-- Right: Content -->
          <div class="wiki-content mc-surface-card">
            <div v-if="!store.currentKB" class="empty-state">
              <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              </svg>
              <p>{{ t('wiki.selectKB') }}</p>
            </div>

            <div v-else class="wiki-content-body">
              <div class="content-tabs">
                <button
                  v-for="tab in tabs" :key="tab.key"
                  class="tab-btn" :class="{ active: activeTab === tab.key }"
                  @click="activeTab = tab.key"
                >
                  {{ tab.label }}
                </button>
              </div>

              <div v-if="activeTab === 'raw'" class="tab-content">
                <RawMaterialPanel />
              </div>

              <div v-if="activeTab === 'pages'" class="tab-content">
                <WikiPageViewer v-if="store.currentPage" />
                <div v-else class="empty-state">
                  <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/>
                  </svg>
                  <p>{{ t('wiki.selectPage') }}</p>
                </div>
              </div>

              <div v-if="activeTab === 'graph'" class="tab-content tab-content--graph">
                <WikiGraphView :pages="store.pages" @open-page="openPage" />
              </div>

              <div v-if="activeTab === 'config'" class="tab-content tab-content--config">
                <WikiConfig />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create KB Modal -->
    <div v-if="showCreateKB" class="modal-overlay" @click.self="showCreateKB = false">
      <div class="modal-content">
        <h3 class="modal-title">{{ t('wiki.createKB') }}</h3>
        <div class="form-group">
          <label>{{ t('wiki.kbName') }}</label>
          <input v-model="newKBName" type="text" class="form-input" :placeholder="t('wiki.kbNamePlaceholder')" autofocus />
        </div>
        <div class="form-group">
          <label>{{ t('wiki.kbDescription') }}</label>
          <textarea v-model="newKBDesc" class="form-input" rows="3" :placeholder="t('wiki.kbDescPlaceholder')"></textarea>
        </div>
        <div class="form-group">
          <label>知识库类型</label>
          <select v-model="newKBKind" class="form-input">
            <option value="general">通用知识库</option>
            <option value="business">业务知识库</option>
          </select>
        </div>
        <div v-if="newKBKind === 'business'" class="form-group">
          <label>业务画像 ID</label>
          <select v-model="newDomainProfileId" class="form-input">
            <option value="">请选择业务画像</option>
            <option v-for="profile in domainProfiles" :key="profile.id" :value="profile.id">
              {{ profile.displayName }} · {{ profile.id }}
            </option>
          </select>
          <div class="modal-hint">{{ selectedDomainProfileHint }}</div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showCreateKB = false">{{ t('common.cancel') }}</button>
          <button class="btn-primary" @click="handleCreateKB" :disabled="!newKBName.trim() || (newKBKind === 'business' && !newDomainProfileId)">{{ t('common.create') }}</button>
        </div>
      </div>
    </div>

    <!-- Edit KB Modal -->
    <div v-if="showEditKB" class="modal-overlay" @click.self="showEditKB = false">
      <div class="modal-content">
        <h3 class="modal-title">编辑知识库</h3>
        <div class="form-group">
          <label>名称</label>
          <input v-model="editKBName" type="text" class="form-input" placeholder="知识库名称" autofocus />
        </div>
        <div class="form-group">
          <label>描述</label>
          <textarea v-model="editKBDesc" class="form-input" rows="3" placeholder="知识库描述"></textarea>
        </div>
        <div class="form-group">
          <label>知识库类型</label>
          <select v-model="editKBKind" class="form-input">
            <option value="general">通用知识库</option>
            <option value="business">业务知识库</option>
          </select>
        </div>
        <div v-if="editKBKind === 'business'" class="form-group">
          <label>业务画像 ID</label>
          <select v-model="editDomainProfileId" class="form-input">
            <option value="">请选择业务画像</option>
            <option v-for="profile in domainProfiles" :key="profile.id" :value="profile.id">
              {{ profile.displayName }} · {{ profile.id }}
            </option>
          </select>
          <div class="modal-hint">{{ editSelectedDomainProfileHint }}</div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showEditKB = false">{{ t('common.cancel') }}</button>
          <button class="btn-primary" @click="handleEditKB" :disabled="!editKBName.trim() || (editKBKind === 'business' && !editDomainProfileId)">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useWikiStore, isProtectedPage, type WikiKB, type WikiPage } from '@/stores/useWikiStore'
import { wikiApi } from '@/api/index'
import { mcConfirm } from '@/components/common/useConfirm'
import type { WikiDomainProfileOption } from '@/types'
import RawMaterialPanel from './components/RawMaterialPanel.vue'
import WikiPageViewer from './components/WikiPageViewer.vue'
import WikiConfig from './components/WikiConfig.vue'
import WikiGraphView from './components/WikiGraphView.vue'

const { t } = useI18n()
const store = useWikiStore()
const pageListEl = ref<HTMLElement | null>(null)
const sidebarView = ref<'knowledge' | 'pages'>('knowledge')

// KB health stats
interface KBStats {
  pageCount: number
  enrichedPageCount: number
  rawCount: number
  failedJobCount: number
  runningJobCount: number
}
const kbStats = reactive<Record<number, KBStats>>({})

async function fetchKBStats() {
  for (const kb of store.knowledgeBases) {
    try {
      const res: any = await wikiApi.getKBStats(kb.id)
      kbStats[kb.id] = res.data || res
    } catch { /* ignore */ }
  }
}

watch(() => store.knowledgeBases.length, () => {
  if (store.knowledgeBases.length > 0) fetchKBStats()
})

const showCreateKB = ref(false)
const newKBName = ref('')
const newKBDesc = ref('')
const newKBKind = ref<'general' | 'business'>('general')
const newDomainProfileId = ref('')
const domainProfiles = ref<WikiDomainProfileOption[]>([])
const activeTab = ref('raw')
const pageSearch = ref('')

// Edit KB modal
const showEditKB = ref(false)
const editKBId = ref<number | null>(null)
const editKBName = ref('')
const editKBDesc = ref('')
const editKBKind = ref<'general' | 'business'>('general')
const editDomainProfileId = ref('')

const domainProfileMap = computed(() => {
  const map: Record<string, string> = {}
  for (const p of domainProfiles.value) {
    map[p.id] = p.displayName || p.id
  }
  return map
})

const selectedDomainProfileHint = computed(() => {
  if (newKBKind.value !== 'business') {
    return '通用知识库无需选择业务画像。'
  }
  const selected = domainProfiles.value.find(item => item.id === newDomainProfileId.value)
  if (!selected) {
    return '当前仅允许从受控注册表中选择业务画像。'
  }
  return selected.description || selected.id
})

const editSelectedDomainProfileHint = computed(() => {
  if (editKBKind.value !== 'business') {
    return '通用知识库无需选择业务画像。'
  }
  const selected = domainProfiles.value.find(item => item.id === editDomainProfileId.value)
  if (!selected) {
    return '当前仅允许从受控注册表中选择业务画像。'
  }
  return selected.description || selected.id
})

watch(newKBKind, (kind) => {
  if (kind !== 'business') {
    newDomainProfileId.value = ''
    return
  }
  if (!newDomainProfileId.value && domainProfiles.value.length > 0) {
    newDomainProfileId.value = domainProfiles.value[0].id
  }
})

// Batch selection
const batchMode = ref(false)
const selectedSlugs = ref<string[]>([])

// Pagination constants
const PAGE_STEP = 20
const searchPageLimit = ref(PAGE_STEP)

// Per-group pagination limit map
const groupPageLimit = reactive<Record<string, number>>({})

// Which groups are collapsed
const collapsedGroups = reactive<Set<string>>(new Set())

// RFC-051 PR-7 follow-up: archived pages drawer state.
const archivedOpen = ref(false)
const archivedLoading = ref(false)
const archivedPages = ref<WikiPage[]>([])

async function toggleArchived() {
  archivedOpen.value = !archivedOpen.value
  if (archivedOpen.value && archivedPages.value.length === 0 && store.currentKB) {
    archivedLoading.value = true
    try {
      const res: any = await wikiApi.listArchivedPages(store.currentKB.id)
      archivedPages.value = (res?.data || res || []) as WikiPage[]
    } catch (e) {
      console.error('[Wiki] Failed to load archived pages', e)
      archivedPages.value = []
    } finally {
      archivedLoading.value = false
    }
  }
}

async function restoreArchivedPage(slug: string) {
  if (!store.currentKB) return
  try {
    await wikiApi.unarchivePage(store.currentKB.id, slug)
    archivedPages.value = archivedPages.value.filter(p => p.slug !== slug)
    // Bring the page back into the main list cache.
    await store.fetchPages(store.currentKB.id)
  } catch (e: any) {
    console.error('[Wiki] Unarchive failed', e)
    alert(e?.message || 'Unarchive failed')
  }
}

// Reset archived drawer when KB changes so we re-fetch on first open in the new KB.
watch(() => store.currentKB?.id, (kbId) => {
  archivedOpen.value = false
  archivedPages.value = []
  if (!kbId) {
    sidebarView.value = 'knowledge'
  }
})

function toggleGroup(type: string) {
  if (collapsedGroups.has(type)) collapsedGroups.delete(type)
  else collapsedGroups.add(type)
}

function loadMoreGroup(type: string) {
  groupPageLimit[type] = (groupPageLimit[type] || PAGE_STEP) + PAGE_STEP
}

function paginatedGroupPages(group: { key: string; pages: WikiPage[] }) {
  const limit = groupPageLimit[group.key] || PAGE_STEP
  return group.pages.slice(0, limit)
}

function formatGroupLabel(type: string): string {
  if (!type) return t('wiki.pageTypes.other')
  const key = `wiki.pageTypes.${type.toLowerCase()}`
  const translated = t(key)
  // If i18n key not found it returns the key itself; fall back to capitalised type
  return translated === key ? (type.charAt(0).toUpperCase() + type.slice(1)) : translated
}

// Type sort order
const TYPE_ORDER = ['concept', 'technology', 'process', 'person', 'organization', 'product', 'place', 'event', 'term', 'other']

const HIDDEN_PAGE_TYPES = new Set(['system', 'reference_seed'])

const visiblePages = computed(() => store.pages.filter(p => !HIDDEN_PAGE_TYPES.has(p.pageType || '')))

const filteredPages = computed(() => {
  const q = pageSearch.value.toLowerCase()
  if (!q) return visiblePages.value
  return visiblePages.value.filter(
    (p) => p.title.toLowerCase().includes(q) || p.slug.toLowerCase().includes(q)
  )
})

const paginatedSearch = computed(() => filteredPages.value.slice(0, searchPageLimit.value))

const groupedPages = computed(() => {
  const map = new Map<string, typeof store.pages>()
  for (const page of visiblePages.value) {
    const type = (page.pageType || 'other').toLowerCase()
    if (!map.has(type)) map.set(type, [])
    map.get(type)!.push(page)
  }
  // Sort groups by TYPE_ORDER
  return [...map.entries()]
    .sort(([a], [b]) => {
      const ia = TYPE_ORDER.indexOf(a) >= 0 ? TYPE_ORDER.indexOf(a) : 99
      const ib = TYPE_ORDER.indexOf(b) >= 0 ? TYPE_ORDER.indexOf(b) : 99
      return ia - ib
    })
    .map(([type, pages]) => ({ type, pages }))
})

const activePageGroups = computed(() => {
  if (!pageSearch.value && store.derivedViews.length > 0) {
    return store.derivedViews
      .map((view) => ({
        key: view.viewKey,
        title: view.title,
        subtitle: view.subtitle || '',
        pages: view.pages.filter(p => !HIDDEN_PAGE_TYPES.has(p.pageType || '')),
      }))
      .filter(g => g.pages.length > 0)
  }
  return groupedPages.value.map((group) => ({
    key: `type:${group.type}`,
    title: formatGroupLabel(group.type),
    subtitle: '',
    pages: group.pages,
  }))
})

// Reset pagination when KB changes
watch(() => store.currentKB?.id, () => {
  searchPageLimit.value = PAGE_STEP
  Object.keys(groupPageLimit).forEach(k => delete groupPageLimit[k])
  collapsedGroups.clear()
})

watch(() => pageSearch.value, () => {
  searchPageLimit.value = PAGE_STEP
})

const allSelected = computed(() =>
  filteredPages.value.length > 0 && selectedSlugs.value.length === filteredPages.value.length
)

function toggleSelect(slug: string) {
  const idx = selectedSlugs.value.indexOf(slug)
  if (idx >= 0) selectedSlugs.value.splice(idx, 1)
  else selectedSlugs.value.push(slug)
}

function toggleSelectAll() {
  if (allSelected.value) selectedSlugs.value = []
  else selectedSlugs.value = filteredPages.value.map(p => p.slug)
}

function exitBatchMode() {
  batchMode.value = false
  selectedSlugs.value = []
}

async function handleBatchDelete() {
  if (selectedSlugs.value.length === 0 || !store.currentKB) return
  const confirmed = await mcConfirm({
    title: t('common.confirm'),
    message: t('wiki.confirmBatchDelete', { count: selectedSlugs.value.length }),
    confirmText: t('common.delete'),
    cancelText: t('common.cancel'),
    tone: 'danger',
  })
  if (!confirmed) return
  try {
    await wikiApi.batchDeletePages(store.currentKB.id, selectedSlugs.value)
    exitBatchMode()
    await store.fetchPages(store.currentKB.id)
  } catch (e: any) {
    ElMessage.error(e?.message || 'Batch delete failed')
  }
}

const tabs = computed(() => [
  { key: 'raw', label: t('wiki.rawMaterials') },
  { key: 'pages', label: t('wiki.pages') },
  { key: 'graph', label: t('wiki.graph.tab') },
  { key: 'config', label: t('wiki.config') },
])

async function selectKB(id: number) {
  await store.selectKB(id)
  sidebarView.value = 'pages'
  activeTab.value = 'raw'
}

async function openPage(slug: string) {
  if (!store.currentKB) return
  await store.loadPage(store.currentKB.id, slug)
  activeTab.value = 'pages'
}

async function fetchDomainProfiles() {
  try {
    const res: any = await wikiApi.listDomainProfiles()
    domainProfiles.value = (res?.data || res || []) as WikiDomainProfileOption[]
    if (newKBKind.value === 'business' && !newDomainProfileId.value && domainProfiles.value.length > 0) {
      newDomainProfileId.value = domainProfiles.value[0].id
    }
  } catch (e) {
    console.error('[Wiki] Failed to load domain profiles', e)
    domainProfiles.value = []
  }
}

async function handleCreateKB() {
  if (newKBKind.value === 'business' && !newDomainProfileId.value) {
    ElMessage.warning('业务知识库必须选择业务画像')
    return
  }
  await store.createKB({
    name: newKBName.value,
    description: newKBDesc.value,
    kbKind: newKBKind.value,
    domainProfileId: newDomainProfileId.value.trim() || null,
  })
  showCreateKB.value = false
  newKBName.value = ''
  newKBDesc.value = ''
  newKBKind.value = 'general'
  newDomainProfileId.value = ''
}

function openEditKB(kb: WikiKB) {
  editKBId.value = kb.id
  editKBName.value = kb.name || ''
  editKBDesc.value = kb.description || ''
  editKBKind.value = (kb.kbKind as 'general' | 'business') || 'general'
  editDomainProfileId.value = kb.domainProfileId || ''
  showEditKB.value = true
}

async function handleEditKB() {
  if (!editKBId.value) return
  if (editKBKind.value === 'business' && !editDomainProfileId.value) {
    ElMessage.warning('业务知识库必须选择业务画像')
    return
  }
  try {
    await wikiApi.updateKB(editKBId.value, {
      name: editKBName.value,
      description: editKBDesc.value,
      kbKind: editKBKind.value,
      domainProfileId: editDomainProfileId.value.trim() || null,
    })
    ElMessage.success('知识库信息已更新')
    showEditKB.value = false
    await store.fetchKnowledgeBases()
    if (store.currentKB?.id === editKBId.value) {
      const updated = store.knowledgeBases.find(k => k.id === editKBId.value)
      if (updated) store.currentKB = updated
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

async function handleDeleteKB(kb: WikiKB) {
  const confirmed = await mcConfirm({
    title: t('common.confirm'),
    message: t('wiki.confirmDeleteKB', { name: kb.name }),
    confirmText: t('common.delete'),
    cancelText: t('common.cancel'),
    tone: 'danger',
  })
  if (!confirmed) return
  const nextKbId = store.currentKB?.id === kb.id
    ? store.knowledgeBases.find((item) => item.id !== kb.id)?.id
    : null
  try {
    await store.deleteKB(kb.id)
    if (nextKbId) {
      await selectKB(nextKbId)
    } else {
      sidebarView.value = 'knowledge'
    }
  } catch (e: any) {
    ElMessage.error(e?.message || t('wiki.deleteKBFailed'))
  }
}

// Infinite scroll: when the page-list container scrolls near the bottom,
// auto-load more items for the last non-fully-expanded group.
function onPageListScroll() {
  const el = pageListEl.value
  if (!el) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 60) {
    // Find the first group that still has hidden pages and load more
    for (const group of activePageGroups.value) {
      const limit = groupPageLimit[group.key] || PAGE_STEP
      if (limit < group.pages.length) {
        loadMoreGroup(group.key)
        break
      }
    }
  }
}

onMounted(() => {
  store.fetchKnowledgeBases()
  fetchDomainProfiles()
})
</script>

<style scoped>
.wiki-shell { background: transparent; height: 100%; min-height: 0; overflow: hidden; }
.wiki-frame { height: min(calc(100vh - 28px), 100%); min-height: 0; overflow: hidden; }
.wiki-inner { display: flex; flex-direction: column; height: 100%; min-height: 0; }

.btn-primary { display: flex; align-items: center; gap: 6px; padding: 10px 18px; background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover)); color: white; border: none; border-radius: 14px; font-size: 14px; font-weight: 600; cursor: pointer; box-shadow: var(--mc-shadow-soft); transition: opacity 0.15s; }
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { background: var(--mc-border); box-shadow: none; cursor: not-allowed; }
.btn-secondary { padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 12px; font-size: 14px; cursor: pointer; transition: background 0.15s; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }
.modal-hint { margin-top: 6px; font-size: 12px; color: var(--mc-text-tertiary); }

/* Layout */
.wiki-layout { display: flex; gap: 16px; flex: 1; min-height: 0; overflow: hidden; }

/* Sidebar */
.wiki-sidebar {
  width: 272px;
  min-width: 272px;
  padding: 14px 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

.sidebar-section { display: flex; flex-direction: column; gap: 8px; }
.sidebar-section--fill { flex: 1; min-height: 0; overflow: hidden; }
.sidebar-section--pages { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 6px; overflow: hidden; }

.sidebar-nav {
  display: flex;
  gap: 8px;
  padding: 4px;
  border-radius: 14px;
  background: var(--mc-bg-sunken);
  border: 1px solid var(--mc-border-light);
}

.sidebar-nav-btn {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--mc-text-secondary);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, box-shadow 0.15s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-nav-btn:hover { color: var(--mc-text-primary); }

.sidebar-nav-btn.active {
  background: var(--mc-bg-elevated);
  color: var(--mc-primary);
  box-shadow: var(--mc-shadow-soft);
}

.sidebar-nav-btn--current {
  text-align: left;
}

.sidebar-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 0 4px;
}

.breadcrumb-back {
  border: none;
  background: none;
  padding: 0;
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.breadcrumb-back:hover { text-decoration: underline; }

.breadcrumb-sep {
  color: var(--mc-text-tertiary);
  font-size: 12px;
}

.breadcrumb-current {
  min-width: 0;
  flex: 1;
  color: var(--mc-text-secondary);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar-label {
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--mc-text-tertiary);
  padding: 0 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 16px;
  padding: 0 5px;
  background: var(--mc-bg-sunken);
  color: var(--mc-text-secondary);
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0;
}

/* Search */
.search-wrap {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  background: var(--mc-bg-muted);
  border: 1px solid var(--mc-border-light);
  border-radius: 10px;
  color: var(--mc-text-tertiary);
  transition: border-color 0.15s;
}
.search-wrap:focus-within { border-color: var(--mc-primary); }
.search-input { flex: 1; border: none; background: transparent; font-size: 12px; color: var(--mc-text-primary); outline: none; }
.search-input::placeholder { color: var(--mc-text-tertiary); }
.search-clear { border: none; background: none; cursor: pointer; color: var(--mc-text-tertiary); font-size: 11px; padding: 0; line-height: 1; }
.search-clear:hover { color: var(--mc-text-secondary); }

/* KB list */
.kb-list { display: flex; flex-direction: column; gap: 4px; }
.kb-list--scroll { flex: 1; min-height: 0; overflow-y: auto; padding-right: 2px; }
.kb-item {
  position: relative;
  padding: 12px 12px 12px 52px;
  min-height: 58px;
  border-radius: 16px;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s, transform 0.15s;
  border: 1px solid var(--mc-border-light);
  background: color-mix(in srgb, var(--mc-bg-elevated) 78%, transparent);
  flex-shrink: 0;
}

.kb-item::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 13px;
  width: 28px;
  height: 28px;
  border-radius: 11px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--mc-primary) 15%, transparent), color-mix(in srgb, var(--mc-accent) 10%, transparent)),
    var(--mc-bg-elevated);
  border: 1px solid color-mix(in srgb, var(--mc-primary) 18%, var(--mc-border-light));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, #fff 50%, transparent);
}

.kb-item::after {
  content: '';
  position: absolute;
  left: 19px;
  top: 19px;
  width: 14px;
  height: 14px;
  color: var(--mc-primary);
  background: currentColor;
  mask: url("data:image/svg+xml,%3Csvg viewBox='0 0 24 24' xmlns='http://www.w3.org/2000/svg'%3E%3Cpath fill='black' d='M6.5 2A2.5 2.5 0 0 0 4 4.5v15A2.5 2.5 0 0 0 6.5 22H20V2H6.5Zm0 17H18v1H6.5a.5.5 0 0 1 0-1ZM6 4.5A.5.5 0 0 1 6.5 4H18v13H6.5c-.17 0-.34.02-.5.05V4.5ZM8 7h8v2H8V7Zm0 4h6v2H8v-2Z'/%3E%3C/svg%3E") center / contain no-repeat;
}

.kb-item:hover {
  background: var(--mc-bg-muted);
  border-color: color-mix(in srgb, var(--mc-primary) 16%, var(--mc-border-light));
  transform: translateY(-1px);
}

.kb-item.active {
  background: var(--mc-primary-bg);
  border-color: color-mix(in srgb, var(--mc-primary) 24%, var(--mc-border-light));
  box-shadow: inset 3px 0 0 var(--mc-primary);
}
.kb-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.kb-item-name { font-size: 13px; font-weight: 700; color: var(--mc-text-primary); margin-bottom: 5px; line-height: 1.35; }
.kb-item-header .kb-item-name { margin-bottom: 0; flex: 1; min-width: 0; }
.kb-item-actions { display: flex; align-items: center; gap: 2px; }
.kb-edit-btn,
.kb-delete-btn {
  width: 22px;
  height: 22px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--mc-text-tertiary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s, border-color 0.15s;
}
.kb-item:hover .kb-edit-btn,
.kb-item.active .kb-edit-btn,
.kb-item:hover .kb-delete-btn,
.kb-item.active .kb-delete-btn { opacity: 1; }
.kb-edit-btn:hover {
  background: rgba(59, 130, 246, 0.08);
  border-color: rgba(59, 130, 246, 0.16);
  color: var(--mc-primary);
}
.kb-delete-btn:hover {
  background: rgba(220, 38, 38, 0.08);
  border-color: rgba(220, 38, 38, 0.16);
  color: var(--mc-danger);
}
.kb-external-key {
  display: inline-flex;
  max-width: 100%;
  margin-bottom: 7px;
  padding: 2px 6px;
  border-radius: 6px;
  background: var(--mc-bg-muted);
  color: var(--mc-text-tertiary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kb-item-stats { display: flex; align-items: center; gap: 6px; }
.stat-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--mc-text-tertiary);
}
.kb-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  margin-left: auto;
}
.kb-status-dot.active { background: var(--mc-success); }
.kb-status-dot.processing { background: var(--mc-primary); animation: pulse 1.4s ease-in-out infinite; }
.kb-status-dot.error { background: var(--mc-danger); }
.kb-warn { font-size: 11px; color: var(--mc-danger); margin-top: 3px; }
.kb-business-tag { display: inline-flex; align-items: center; gap: 4px; margin-top: 4px; padding: 2px 7px; background: rgba(217,119,87,0.12); border-radius: 6px; color: var(--mc-primary); font-size: 10px; font-weight: 600; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* Page list and groups */
.sidebar-title-row { display: flex; justify-content: space-between; align-items: center; padding: 0 4px; }
.sidebar-title-actions { display: flex; gap: 4px; }
.icon-btn {
  width: 24px;
  height: 24px;
  border: 1px solid var(--mc-border-light);
  background: var(--mc-bg-elevated);
  border-radius: 7px;
  cursor: pointer;
  color: var(--mc-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  transition: all 0.15s;
}
.icon-btn:hover { background: var(--mc-bg-sunken); color: var(--mc-primary); }
.icon-btn--active { color: var(--mc-primary); border-color: var(--mc-primary); }

.page-list { flex: 1; overflow-y: auto; display: flex; flex-direction: column; gap: 2px; }

.page-group { display: flex; flex-direction: column; }
.group-header {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 6px;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.12s;
  width: 100%;
  text-align: left;
}
.group-header:hover { background: var(--mc-bg-muted); }
.group-chevron { color: var(--mc-text-tertiary); transition: transform 0.18s; flex-shrink: 0; }
.group-chevron.expanded { transform: rotate(90deg); }
.group-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.group-label { font-size: 11px; font-weight: 600; color: var(--mc-text-secondary); }
.group-subtitle {
  font-size: 10px;
  color: var(--mc-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.group-count {
  font-size: 10px;
  padding: 1px 5px;
  background: var(--mc-bg-sunken);
  color: var(--mc-text-tertiary);
  border-radius: 9999px;
  font-variant-numeric: tabular-nums;
}
.group-items { display: flex; flex-direction: column; gap: 1px; padding-left: 12px; }

.page-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
  border: 1px solid transparent;
}
.page-item:hover { background: var(--mc-bg-muted); }
.page-item.active { background: var(--mc-primary-bg); border-color: rgba(217, 109, 70, 0.12); }
.page-item-body { flex: 1; min-width: 0; }
.page-item-title { font-size: 12px; color: var(--mc-text-primary); font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.page-item-meta { display: flex; align-items: center; gap: 4px; margin-top: 2px; }
.meta-text { font-size: 10px; color: var(--mc-text-tertiary); }
.type-chip { font-size: 10px; padding: 1px 5px; background: var(--mc-bg-sunken); border-radius: 4px; color: var(--mc-text-tertiary); }
.edit-dot { width: 5px; height: 5px; border-radius: 9999px; flex-shrink: 0; }
.edit-dot.manual { background: var(--mc-primary); }
.page-checkbox { flex-shrink: 0; cursor: pointer; }
.page-checkbox:disabled { cursor: not-allowed; opacity: 0.4; }

/* RFC-051 PR-8: protection flag chips inside the page-item meta line. */
.page-flag { font-size: 10px; padding: 1px 6px; border-radius: 99px; font-weight: 600; }
.page-flag--system { background: var(--mc-primary-bg); color: var(--mc-primary); border: 1px solid var(--mc-primary); }
.page-flag--locked { background: var(--mc-bg-elevated); color: var(--mc-text-secondary); border: 1px solid var(--mc-border-light); }

/* Subtle accent on the row itself so system pages read as "managed by the system". */
.page-item--system { border-left: 2px solid var(--mc-primary); padding-left: 6px; }

/* RFC-051 PR-7 follow-up: archived drawer at the bottom of the page list. */
.archived-section { margin-top: 12px; border-top: 1px dashed var(--mc-border-light); padding-top: 8px; }
.archived-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 4px;
  background: none;
  border: none;
  font-size: 11px;
  color: var(--mc-text-tertiary);
  cursor: pointer;
  text-align: left;
}
.archived-toggle:hover { color: var(--mc-text-secondary); }
.archived-toggle:disabled { cursor: wait; opacity: 0.5; }
.archived-chevron { transition: transform 0.15s; flex-shrink: 0; }
.archived-chevron.expanded { transform: rotate(90deg); }
.archived-count {
  margin-left: auto;
  padding: 0 6px;
  background: var(--mc-bg-sunken);
  border-radius: 99px;
  font-weight: 600;
  font-size: 10px;
}

.archived-items { display: flex; flex-direction: column; gap: 2px; padding-top: 4px; }
.archived-empty { padding: 6px 4px; font-size: 11px; font-style: italic; color: var(--mc-text-tertiary); }
.archived-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.1s, background 0.1s;
}
.archived-item:hover { opacity: 1; background: var(--mc-bg-muted); }
.archived-item-body { flex: 1; min-width: 0; }
.archived-item-title { font-size: 12px; font-weight: 500; color: var(--mc-text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.archived-item-meta { font-size: 10px; color: var(--mc-text-tertiary); margin-top: 1px; }

.archived-restore-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--mc-border-light);
  border-radius: 6px;
  background: none;
  color: var(--mc-text-secondary);
  font-size: 11px;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.15s;
}
.archived-restore-btn:hover { color: var(--mc-primary); border-color: var(--mc-primary); background: var(--mc-primary-bg); }

.load-more-btn {
  width: 100%;
  padding: 6px;
  border: 1px dashed var(--mc-border-light);
  background: none;
  border-radius: 8px;
  font-size: 11px;
  color: var(--mc-text-tertiary);
  cursor: pointer;
  transition: all 0.15s;
  margin-top: 2px;
}
.load-more-btn:hover { border-color: var(--mc-primary); color: var(--mc-primary); background: var(--mc-primary-bg); }

.pagination-row { padding: 4px 0; }

/* Batch */
.batch-bar { display: flex; justify-content: space-between; align-items: center; padding: 5px 8px; background: var(--mc-bg-muted); border-radius: 8px; }
.batch-check-all { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--mc-text-secondary); cursor: pointer; }
.batch-check-all input { cursor: pointer; }
.batch-delete-btn { padding: 3px 10px; border: none; border-radius: 6px; font-size: 11px; font-weight: 600; cursor: pointer; background: rgba(245, 108, 108, 0.1); color: var(--el-color-danger, #f56c6c); }
.batch-delete-btn:hover:not(:disabled) { background: rgba(245, 108, 108, 0.2); }
.batch-delete-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.empty-hint { font-size: 13px; color: var(--mc-text-tertiary); padding: 12px 4px; }

.filter-banner {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 10px;
  background: var(--mc-primary-bg);
  border: 1px solid rgba(217,109,70,0.2);
  border-radius: 8px;
  font-size: 11px;
  color: var(--mc-primary);
  font-weight: 500;
}
.filter-clear-btn {
  margin-left: auto;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--mc-primary);
  font-size: 11px;
  padding: 0 2px;
  opacity: 0.7;
  line-height: 1;
}
.filter-clear-btn:hover { opacity: 1; }

/* Content area */
.wiki-content { flex: 1; overflow: hidden; min-width: 0; padding: 16px; display: flex; flex-direction: column; min-height: 0; }
.wiki-content-body { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.content-tabs { display: inline-flex; gap: 4px; padding: 4px; background: var(--mc-bg-muted); border-radius: 14px; margin-bottom: 14px; border: 1px solid var(--mc-border-light); align-self: flex-start; }
.tab-btn { padding: 7px 14px; border: none; background: none; cursor: pointer; font-size: 13px; color: var(--mc-text-secondary); border-radius: 10px; transition: all 0.15s; font-weight: 500; }
.tab-btn:hover { color: var(--mc-text-primary); }
.tab-btn.active { color: var(--mc-primary); background: var(--mc-bg-elevated); box-shadow: 0 1px 4px rgba(0,0,0,0.08); font-weight: 600; }
.tab-content { flex: 1; min-height: 0; overflow-y: auto; padding-right: 2px; }
.tab-content--config {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 0;
  padding-bottom: 20px;
}
.tab-content--graph { overflow: hidden; padding: 0; }
.template-binding-panel {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(260px, 1.4fr);
  gap: 16px;
  align-items: start;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--mc-border-light);
  border-radius: 12px;
  background: var(--mc-bg-muted);
}
.template-binding-panel h3 { margin: 0 0 4px; font-size: 14px; color: var(--mc-text-primary); }
.template-binding-panel p { margin: 0; font-size: 12px; line-height: 1.5; color: var(--mc-text-tertiary); }
.template-binding-form { display: flex; gap: 8px; align-items: center; min-width: 0; }
.template-binding-form .form-input { min-width: 0; }
.template-binding-form .btn-primary { flex: 0 0 auto; box-shadow: none; border-radius: 10px; padding: 9px 14px; }

.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; min-height: 200px; color: var(--mc-text-tertiary); text-align: center; }
.empty-state p { font-size: 14px; }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); backdrop-filter: blur(4px); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal-content { background: var(--mc-bg-elevated); border: 1px solid var(--mc-border); border-radius: 18px; width: 100%; max-width: 520px; padding: 24px; box-shadow: 0 24px 64px rgba(0,0,0,0.18); }
.modal-title { font-size: 17px; font-weight: 700; color: var(--mc-text-primary); margin: 0 0 18px; }
.form-group { margin-bottom: 14px; }
.form-group label { display: block; font-size: 12px; font-weight: 600; margin-bottom: 6px; color: var(--mc-text-secondary); text-transform: uppercase; letter-spacing: 0.04em; }
.form-input { width: 100%; padding: 9px 12px; border: 1px solid var(--mc-border); border-radius: 10px; font-size: 14px; background: var(--mc-bg-muted); color: var(--mc-text-primary); outline: none; font-family: inherit; box-sizing: border-box; transition: border-color 0.15s; }
.form-input:focus { border-color: var(--mc-primary); box-shadow: 0 0 0 2px rgba(217,119,87,0.1); }
.form-help { margin: 6px 0 0; color: var(--mc-text-tertiary); font-size: 12px; line-height: 1.45; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }

@media (max-width: 980px) {
  .wiki-frame { height: 100%; min-height: calc(100vh - 28px); }
  .wiki-layout { flex-direction: column; overflow: visible; }
  .wiki-sidebar { width: 100%; min-width: 0; max-height: 320px; }
  .wiki-content { overflow: visible; }
  .tab-content { overflow: visible; }
  .tab-content--config { overflow: visible; }
  .template-binding-panel { grid-template-columns: 1fr; }
  .template-binding-form { flex-direction: column; align-items: stretch; }
}
</style>
