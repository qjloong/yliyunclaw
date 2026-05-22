<template>
  <div class="settings-section">
    <div class="section-header">
      <div>
        <h2 class="section-title">{{ t('security.members.title') }}</h2>
        <p class="section-desc">{{ t('security.members.desc') }}</p>
      </div>
      <button class="btn-primary" @click="showAddDialog = true">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        {{ t('security.members.addMember') }}
      </button>
    </div>

    <div class="invite-card">
      <div class="invite-card__copy">
        <div class="invite-card__title">{{ t('security.members.invites.title') }}</div>
        <div class="invite-card__desc">{{ t('security.members.invites.desc') }}</div>
      </div>
      <div class="invite-card__controls">
        <select v-model="inviteRole" class="config-select invite-role-select">
          <option value="admin">{{ t('security.members.roles.admin') }}</option>
          <option value="member">{{ t('security.members.roles.member') }}</option>
          <option value="viewer">{{ t('security.members.roles.viewer') }}</option>
        </select>
        <button class="btn-secondary" @click="createInviteLink" :disabled="inviteLoading">
          {{ inviteLoading ? t('security.members.invites.generating') : t('security.members.invites.generate') }}
        </button>
      </div>
      <div v-if="generatedInviteLink" class="invite-link-box">
        <input :value="generatedInviteLink" class="form-input invite-link-input" readonly />
        <button class="btn-primary" @click="copyInviteLink">{{ t('security.members.invites.copy') }}</button>
      </div>
      <div v-if="generatedInviteMeta?.expiresAt" class="form-hint invite-meta">
        {{ t('security.members.invites.expiresAt', { date: formatDateTime(generatedInviteMeta.expiresAt) }) }}
      </div>
    </div>

    <div class="invite-list-card">
      <div class="invite-list-card__header">
        <div>
          <div class="invite-card__title">{{ t('security.members.invites.activeTitle') }}</div>
          <div class="invite-card__desc">{{ t('security.members.invites.activeDesc') }}</div>
        </div>
        <button class="btn-secondary" @click="fetchInvites" :disabled="invitesLoading">
          {{ invitesLoading ? t('security.members.loading') : t('common.refresh') }}
        </button>
      </div>
      <div v-if="invitesLoading" class="empty-state invite-empty">{{ t('security.members.loading') }}</div>
      <div v-else-if="invites.length === 0" class="empty-state invite-empty">{{ t('security.members.invites.empty') }}</div>
      <table v-else class="rules-table invite-table">
        <thead>
          <tr>
            <th>{{ t('security.members.invites.columns.role') }}</th>
            <th>{{ t('security.members.invites.columns.status') }}</th>
            <th>{{ t('security.members.invites.columns.uses') }}</th>
            <th>{{ t('security.members.invites.columns.inviter') }}</th>
            <th>{{ t('security.members.invites.columns.expires') }}</th>
            <th style="width: 90px;">{{ t('security.members.columns.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="invite in invites" :key="invite.id">
            <td>{{ t(`security.members.roles.${invite.role}`) }}</td>
            <td>
              <span class="invite-status" :class="`is-${invite.status}`">
                {{ t(`security.members.invites.status.${invite.status || 'active'}`) }}
              </span>
            </td>
            <td>{{ invite.useCount || 0 }} / {{ invite.maxUses || 1 }}</td>
            <td>{{ invite.inviterUsername || '-' }}</td>
            <td class="date-cell">{{ formatDateTime(invite.expiresAt) }}</td>
            <td>
              <button
                v-if="invite.status === 'active'"
                class="action-btn danger"
                @click="revokeInvite(invite)"
                :title="t('security.members.invites.revoke')"
              >
                {{ t('security.members.invites.revoke') }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Members Table -->
    <div class="rules-table-wrapper">
      <div v-if="loading" class="empty-state">{{ t('security.members.loading') }}</div>
      <div v-else-if="members.length === 0" class="empty-state">{{ t('security.members.noMembers') }}</div>
      <table v-else class="rules-table">
        <thead>
          <tr>
            <th>{{ t('security.members.columns.user') }}</th>
            <th>{{ t('security.members.columns.role') }}</th>
            <th>{{ t('security.members.columns.joined') }}</th>
            <th style="width: 80px;">{{ t('security.members.columns.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="member in members" :key="member.id">
            <td>
              <div class="member-info">
                <div class="member-avatar">{{ (member.username || member.userId + '').charAt(0).toUpperCase() }}</div>
                <div class="member-detail">
                  <span class="member-name">{{ member.nickname || member.username || ('User #' + member.userId) }}</span>
                  <span v-if="member.username && member.nickname" class="member-username">@{{ member.username }}</span>
                </div>
              </div>
            </td>
            <td>
              <select
                :value="member.role"
                @change="handleRoleChange(member, $event)"
                :disabled="member.role === 'owner'"
                class="config-select"
              >
                <option value="owner" disabled>{{ t('security.members.roles.owner') }}</option>
                <option value="admin">{{ t('security.members.roles.admin') }}</option>
                <option value="member">{{ t('security.members.roles.member') }}</option>
                <option value="viewer">{{ t('security.members.roles.viewer') }}</option>
              </select>
            </td>
            <td class="date-cell">{{ formatDate(member.createTime) }}</td>
            <td>
              <div class="action-btns">
                <button
                  v-if="member.role !== 'owner'"
                  class="action-btn danger"
                  @click="removeMember(member)"
                  :title="t('security.members.actions.remove')"
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
                    <path d="M10 11v6"/><path d="M14 11v6"/>
                  </svg>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Add Member Dialog -->
    <Teleport to="body">
      <div v-if="showAddDialog" class="modal-overlay">
        <div class="modal">
          <div class="modal-header">
            <h3>{{ t('security.members.addDialog.title') }}</h3>
            <button class="modal-close" @click="showAddDialog = false">&times;</button>
          </div>
          <div class="modal-body">
            <div class="form-grid" style="grid-template-columns: 1fr;">
              <div class="form-group">
                <label>{{ t('security.members.addDialog.username') }} <span class="required">*</span></label>
                <input v-model.trim="newMemberForm.username" type="text" class="form-input" :placeholder="t('security.members.addDialog.usernamePlaceholder')" />
                <span class="form-hint">{{ t('security.members.addDialog.usernameHint') }}</span>
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.password') }}</label>
                <input v-model="newMemberForm.password" type="password" class="form-input" :placeholder="t('security.members.addDialog.passwordPlaceholder')" />
                <span class="form-hint">{{ t('security.members.addDialog.passwordHint') }}</span>
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.nickname') }}</label>
                <input v-model.trim="newMemberForm.nickname" type="text" class="form-input" :placeholder="t('security.members.addDialog.nicknamePlaceholder')" />
              </div>
              <div class="form-group">
                <label>{{ t('security.members.addDialog.role') }}</label>
                <select v-model="newMemberForm.role" class="form-input">
                  <option value="admin">{{ t('security.members.roles.admin') }}</option>
                  <option value="member">{{ t('security.members.roles.member') }}</option>
                  <option value="viewer">{{ t('security.members.roles.viewer') }}</option>
                </select>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="showAddDialog = false">{{ t('security.members.actions.cancel') }}</button>
            <button class="btn-primary" @click="addMember" :disabled="!newMemberForm.username">{{ t('security.members.actions.confirm') }}</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { workspaceTeamApi } from '@/api/index'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import { mcConfirm } from '@/components/common/useConfirm'

const { t } = useI18n()

interface Member {
  id: number
  workspaceId: number
  userId: number
  username?: string
  nickname?: string
  role: string
  createTime: string
}

interface WorkspaceInvite {
  id: number
  workspaceId: number
  role: string
  status: string
  maxUses: number
  useCount: number
  inviterUsername?: string
  expiresAt?: string
  createTime?: string
}

const store = useWorkspaceStore()
const members = ref<Member[]>([])
const invites = ref<WorkspaceInvite[]>([])
const loading = ref(false)
const invitesLoading = ref(false)
const showAddDialog = ref(false)
const inviteRole = ref('member')
const inviteLoading = ref(false)
const generatedInviteLink = ref('')
const generatedInviteMeta = ref<{ expiresAt?: string; role?: string } | null>(null)

const defaultForm = () => ({ username: '', password: '', nickname: '', role: 'member' })
const newMemberForm = reactive(defaultForm())

onMounted(() => {
  fetchMembers()
  fetchInvites()
})

watch(() => store.currentWorkspaceId, () => {
  generatedInviteLink.value = ''
  generatedInviteMeta.value = null
  fetchMembers()
  fetchInvites()
})

async function fetchMembers() {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  loading.value = true
  try {
    const res: any = await workspaceTeamApi.listMembers(wsId)
    members.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function fetchInvites() {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  invitesLoading.value = true
  try {
    const res: any = await workspaceTeamApi.listInvites(wsId)
    invites.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || t('security.members.messages.inviteLoadFailed'))
  } finally {
    invitesLoading.value = false
  }
}

async function addMember() {
  const wsId = store.currentWorkspaceId
  if (!wsId || !newMemberForm.username) return
  try {
    await workspaceTeamApi.addMember(wsId, {
      username: newMemberForm.username,
      password: newMemberForm.password || undefined,
      nickname: newMemberForm.nickname || undefined,
      role: newMemberForm.role,
    })
    ElMessage.success(t('security.members.messages.addSuccess'))
    showAddDialog.value = false
    Object.assign(newMemberForm, defaultForm())
    fetchMembers()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || t('security.members.messages.addFailed'))
  }
}

async function updateRole(member: Member, role: string) {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  try {
    await workspaceTeamApi.updateMemberRole(wsId, member.userId, role)
    member.role = role
    ElMessage.success(t('security.members.messages.updateSuccess'))
  } catch {
    ElMessage.error(t('security.members.messages.updateFailed'))
  }
}

function handleRoleChange(member: Member, event: Event) {
  const target = event.target as HTMLSelectElement | null
  if (!target) return
  void updateRole(member, target.value)
}

async function createInviteLink() {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  inviteLoading.value = true
  try {
    const res: any = await workspaceTeamApi.createInviteLink(wsId, { role: inviteRole.value })
    const data = res.data || {}
    if (!data.token) throw new Error('missing token')
    generatedInviteLink.value = `${window.location.origin}/workspace-invite?token=${encodeURIComponent(data.token)}`
    generatedInviteMeta.value = data
    await fetchInvites()
    ElMessage.success(t('security.members.messages.inviteCreated'))
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || t('security.members.messages.inviteCreateFailed'))
  } finally {
    inviteLoading.value = false
  }
}

async function revokeInvite(invite: WorkspaceInvite) {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  const confirmed = await mcConfirm({
    title: t('security.members.invites.revoke'),
    message: t('security.members.messages.inviteRevokeConfirm'),
    confirmText: t('security.members.actions.confirm'),
    cancelText: t('security.members.actions.cancel'),
    tone: 'danger',
  })
  if (!confirmed) return
  try {
    await workspaceTeamApi.revokeInvite(wsId, invite.id)
    ElMessage.success(t('security.members.messages.inviteRevoked'))
    await fetchInvites()
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || t('security.members.messages.inviteRevokeFailed'))
  }
}

async function copyInviteLink() {
  if (!generatedInviteLink.value) return
  try {
    await navigator.clipboard.writeText(generatedInviteLink.value)
    ElMessage.success(t('security.members.messages.inviteCopied'))
  } catch {
    ElMessage.error(t('security.members.messages.inviteCopyFailed'))
  }
}

async function removeMember(member: Member) {
  const wsId = store.currentWorkspaceId
  if (!wsId) return
  const confirmed = await mcConfirm({
    title: t('security.members.actions.remove'),
    message: t('security.members.messages.removeConfirm'),
    confirmText: t('security.members.actions.confirm'),
    cancelText: t('security.members.actions.cancel'),
    tone: 'danger',
  })
  if (!confirmed) return
  try {
    await workspaceTeamApi.removeMember(wsId, member.userId)
    ElMessage.success(t('security.members.messages.removeSuccess'))
    fetchMembers()
  } catch {
    ElMessage.error(t('security.members.messages.removeFailed'))
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}

function formatDateTime(dateStr?: string) {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}
</script>

<style>
@import '../shared.css';
</style>

<style scoped>
.member-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(217, 119, 87, 0.12);
  color: var(--mc-primary, #D97757);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}

.member-detail {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.member-name {
  font-weight: 500;
  color: var(--mc-text-primary);
  font-size: 14px;
}

.member-username {
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.date-cell {
  color: var(--mc-text-tertiary);
  font-size: 13px;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.required {
  color: var(--mc-danger, #e74c3c);
}

.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.invite-card {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--mc-border);
  border-radius: 14px;
  background: var(--mc-bg-elevated);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.invite-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--mc-text-primary);
}

.invite-card__desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--mc-text-secondary);
}

.invite-card__controls {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.invite-role-select {
  min-width: 140px;
}

.invite-link-box {
  display: flex;
  gap: 10px;
  align-items: center;
}

.invite-link-input {
  flex: 1;
}

.invite-meta {
  margin-top: -4px;
}

.invite-list-card {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--mc-border);
  border-radius: 14px;
  background: var(--mc-bg-elevated);
}

.invite-list-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.invite-table {
  margin-top: 8px;
}

.invite-empty {
  padding: 14px 0;
}

.invite-status {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(64, 158, 255, 0.1);
  color: #409eff;
}

.invite-status.is-used,
.invite-status.is-expired {
  background: rgba(148, 163, 184, 0.14);
  color: var(--mc-text-tertiary);
}

.invite-status.is-revoked {
  background: rgba(245, 108, 108, 0.12);
  color: var(--mc-danger, #f56c6c);
}
</style>
