import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { workspaceTeamApi } from '@/api/index'

export interface Workspace {
  id: string
  name: string
  slug: string
  description?: string
  basePath?: string
  ownerId?: number
  settingsJson?: string
  projectPermissionMode?: 'limited' | 'full'
  workspacePolicy?: WorkspacePolicy
  createTime?: string
  updateTime?: string
}

export interface WorkspacePolicy {
  sandboxMode?: 'workspace-write' | 'read-only' | 'full-access'
  approvalPolicy?: 'default' | 'strict'
  networkPolicy?: 'inherit' | 'restricted' | 'disabled'
  allowedPaths?: string[]
  deniedPaths?: string[]
  riskOverrides?: Record<string, string>
}

interface WorkspacePreferences {
  defaultAgentId?: string
  lastConversationId?: string
}

type WorkspacePreferenceMap = Record<string, WorkspacePreferences>

const WORKSPACE_PREFERENCES_KEY = 'mc-workspace-preferences'

function normalizeWorkspace(raw: Workspace | null | undefined): Workspace | null {
  if (!raw || raw.id === null || raw.id === undefined) {
    return null
  }
  return {
    ...raw,
    id: String(raw.id),
  }
}

function normalizeWorkspaceId(value: string | number | null | undefined): string | null {
  if (value === null || value === undefined) return null
  const normalized = String(value).trim()
  return normalized || null
}

function getPreferenceOwnerId(): string {
  const userId = localStorage.getItem('userId')
  if (userId && userId.trim()) return userId.trim()
  const username = localStorage.getItem('username')
  if (username && username.trim()) return `u:${username.trim()}`
  return 'anonymous'
}

function buildPreferenceKey(workspaceId: string | number | null | undefined) {
  const normalizedWorkspaceId = normalizeWorkspaceId(workspaceId)
  if (!normalizedWorkspaceId) return null
  return `${getPreferenceOwnerId()}::${normalizedWorkspaceId}`
}

function readWorkspacePreferenceMap(): WorkspacePreferenceMap {
  try {
    const raw = localStorage.getItem(WORKSPACE_PREFERENCES_KEY)
    if (!raw) return {}
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      return {}
    }
    return parsed as WorkspacePreferenceMap
  } catch {
    return {}
  }
}

function writeWorkspacePreferenceMap(value: WorkspacePreferenceMap) {
  localStorage.setItem(WORKSPACE_PREFERENCES_KEY, JSON.stringify(value))
}

export const useWorkspaceStore = defineStore('workspace', () => {
  const workspaces = ref<Workspace[]>([])
  const currentWorkspaceId = ref<string | null>(normalizeWorkspaceId(localStorage.getItem('mc-workspace-id')))
  const loading = ref(false)
  const workspacePreferences = ref<WorkspacePreferenceMap>(readWorkspacePreferenceMap())

  const currentWorkspace = computed(() => {
    if (currentWorkspaceId.value !== null) {
      return workspaces.value.find((ws) => ws.id === currentWorkspaceId.value) || null
    }
    return workspaces.value[0] || null
  })

  async function fetchWorkspaces() {
    loading.value = true
    try {
      const previousWorkspaces = [...workspaces.value]
      const res: any = await workspaceTeamApi.list()
      const fetchedWorkspaces = ((res.data || []) as Workspace[])
        .map(normalizeWorkspace)
        .filter((workspace): workspace is Workspace => Boolean(workspace))
      if (currentWorkspaceId.value) {
        const selectedStillMissing = !fetchedWorkspaces.find((ws: Workspace) => ws.id === currentWorkspaceId.value)
        if (selectedStillMissing) {
          currentWorkspaceId.value = null
          localStorage.removeItem('mc-workspace-id')
        }
      }
      workspaces.value = fetchedWorkspaces
      if (!currentWorkspaceId.value) {
        if (workspaces.value.length > 0) {
          switchWorkspace(workspaces.value[0].id)
        }
      }
    } catch (e) {
      console.warn('Failed to fetch workspaces:', e)
    } finally {
      loading.value = false
    }
  }

  function switchWorkspace(id: string | number) {
    const normalizedId = normalizeWorkspaceId(id)
    if (!normalizedId) return
    currentWorkspaceId.value = normalizedId
    localStorage.setItem('mc-workspace-id', normalizedId)
  }

  function upsertWorkspace(workspace: Workspace | null | undefined) {
    const normalizedWorkspace = normalizeWorkspace(workspace)
    if (!normalizedWorkspace?.id) return
    const index = workspaces.value.findIndex((ws) => ws.id === normalizedWorkspace.id)
    if (index >= 0) {
      workspaces.value[index] = {
        ...workspaces.value[index],
        ...normalizedWorkspace,
      }
      return
    }
    workspaces.value = [...workspaces.value, normalizedWorkspace]
  }

  function getWorkspacePreferences(workspaceId?: string | number | null): WorkspacePreferences {
    const normalizedWorkspaceId = normalizeWorkspaceId(workspaceId ?? currentWorkspaceId.value)
    if (!normalizedWorkspaceId) return {}
    const scopedKey = buildPreferenceKey(normalizedWorkspaceId)
    if (!scopedKey) return {}
    return workspacePreferences.value[scopedKey] || {}
  }

  function patchWorkspacePreferences(
    workspaceId: string | number | null | undefined,
    patch: Partial<WorkspacePreferences>,
  ) {
    const normalizedWorkspaceId = normalizeWorkspaceId(workspaceId)
    const preferenceKey = buildPreferenceKey(workspaceId)
    if (!normalizedWorkspaceId || !preferenceKey) return
    const next: WorkspacePreferences = {
      ...getWorkspacePreferences(workspaceId),
      ...patch,
    }
    if (!next.defaultAgentId) {
      delete next.defaultAgentId
    }
    if (!next.lastConversationId) {
      delete next.lastConversationId
    }
    const updatedMap = { ...workspacePreferences.value }
    delete updatedMap[normalizedWorkspaceId]
    if (!next.defaultAgentId && !next.lastConversationId) {
      delete updatedMap[preferenceKey]
    } else {
      updatedMap[preferenceKey] = next
    }
    workspacePreferences.value = updatedMap
    writeWorkspacePreferenceMap(updatedMap)
  }

  function getDefaultAgentId(workspaceId?: string | number | null) {
    return getWorkspacePreferences(workspaceId).defaultAgentId || ''
  }

  function setDefaultAgentId(workspaceId: string | number | null | undefined, agentId?: string | number | null) {
    patchWorkspacePreferences(workspaceId, {
      defaultAgentId: normalizeWorkspaceId(agentId) || undefined,
    })
  }

  function getLastConversationId(workspaceId?: string | number | null) {
    return getWorkspacePreferences(workspaceId).lastConversationId || ''
  }

  function setLastConversationId(workspaceId: string | number | null | undefined, conversationId?: string | null) {
    patchWorkspacePreferences(workspaceId, {
      lastConversationId: normalizeWorkspaceId(conversationId) || undefined,
    })
  }

  return {
    workspaces,
    currentWorkspaceId,
    currentWorkspace,
    loading,
    workspacePreferences,
    fetchWorkspaces,
    switchWorkspace,
    upsertWorkspace,
    getWorkspacePreferences,
    getDefaultAgentId,
    setDefaultAgentId,
    getLastConversationId,
    setLastConversationId,
  }
})
