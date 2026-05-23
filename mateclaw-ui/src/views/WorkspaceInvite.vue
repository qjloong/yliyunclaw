<template>
  <div class="invite-page">
    <div class="invite-card">
      <div class="invite-kicker">Meta Y</div>
      <h1 class="invite-title">{{ t('security.workspaceInvite.title') }}</h1>

      <div v-if="loading" class="invite-state">{{ t('security.workspaceInvite.loading') }}</div>
      <div v-else-if="errorMsg" class="invite-error">{{ errorMsg }}</div>
      <template v-else-if="invite">
        <div class="invite-details">
          <div class="invite-workspace">{{ invite.workspaceName }}</div>
          <div v-if="invite.inviterUsername" class="invite-detail">
            {{ t('security.workspaceInvite.invitedBy', { username: invite.inviterUsername }) }}
          </div>
          <div class="invite-detail">
            {{ t('security.workspaceInvite.role', { role: t(`security.members.roles.${invite.role}`) }) }}
          </div>
          <div v-if="invite.expiresAt" class="invite-detail">
            {{ t('security.workspaceInvite.expiresAt', { date: formatDateTime(invite.expiresAt) }) }}
          </div>
        </div>

        <div class="invite-actions">
          <button class="btn-primary" :disabled="accepting" @click="acceptInvite">
            {{ accepting ? t('security.workspaceInvite.accepting') : t('security.workspaceInvite.accept') }}
          </button>
          <button class="btn-secondary" @click="router.replace('/chat')">
            {{ t('security.workspaceInvite.backToChat') }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { workspaceTeamApi } from '@/api/index'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const workspaceStore = useWorkspaceStore()

const loading = ref(false)
const accepting = ref(false)
const errorMsg = ref('')
const invite = ref<any | null>(null)

const token = computed(() => {
  const raw = route.query.token
  return typeof raw === 'string' ? raw.trim() : ''
})

onMounted(() => {
  void loadInvite()
})

watch(token, () => {
  void loadInvite()
})

async function loadInvite() {
  if (!token.value) {
    invite.value = null
    errorMsg.value = t('security.workspaceInvite.invalid')
    loading.value = false
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const res: any = await workspaceTeamApi.getInvite(token.value)
    invite.value = res.data || null
    if (invite.value?.alreadyMember && invite.value?.workspaceId) {
      await workspaceStore.fetchWorkspaces()
      workspaceStore.switchWorkspace(invite.value.workspaceId)
      ElMessage.info(t('security.workspaceInvite.alreadyMember'))
      router.replace('/chat')
    }
  } catch (e: any) {
    invite.value = null
    errorMsg.value = e?.msg || e?.message || t('security.workspaceInvite.invalid')
  } finally {
    loading.value = false
  }
}

async function acceptInvite() {
  if (!token.value) return
  accepting.value = true
  try {
    const res: any = await workspaceTeamApi.acceptInvite(token.value)
    const data = res.data || {}
    await workspaceStore.fetchWorkspaces()
    if (data.workspaceId) {
      workspaceStore.switchWorkspace(data.workspaceId)
    }
    ElMessage.success(t('security.workspaceInvite.acceptSuccess'))
    router.replace('/chat')
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || t('security.workspaceInvite.acceptFailed'))
  } finally {
    accepting.value = false
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}
</script>

<style scoped>
.invite-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: linear-gradient(160deg, #f6f8fb 0%, #e9eef9 100%);
}

:root.dark .invite-page,
html.dark .invite-page {
  background: linear-gradient(160deg, var(--mc-bg) 0%, #0f172a 100%);
}

.invite-card {
  width: min(100%, 460px);
  padding: 28px;
  border-radius: 20px;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.invite-kicker {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.14em;
  color: var(--mc-text-tertiary);
  margin-bottom: 10px;
}

.invite-title {
  margin: 0 0 18px;
  font-size: 28px;
  line-height: 1.1;
  color: var(--mc-text-primary);
}

.invite-state,
.invite-error,
.invite-detail {
  font-size: 14px;
  color: var(--mc-text-secondary);
}

.invite-error {
  color: var(--mc-danger, #e74c3c);
}

.invite-details {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 22px;
}

.invite-workspace {
  font-size: 20px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.invite-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>

