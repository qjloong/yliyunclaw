<template>
  <div class="pp-panel">
    <div class="pp-add">
      <input v-model="draft.memberUserId" class="form-input" :placeholder="t('security.workspaces.projectPermission.memberUserId', '成员用户 ID')" />
      <input v-model="draft.projectPath" class="form-input" :placeholder="t('security.workspaces.projectPermission.projectPath', '项目相对路径')" />
      <select v-model="draft.accessLevel" class="form-input">
        <option value="READ">READ</option>
        <option value="WRITE">WRITE</option>
        <option value="ADMIN">ADMIN</option>
      </select>
      <button class="btn-primary" type="button" @click="add">{{ t('common.add') }}</button>
    </div>

    <div v-if="loading" class="pp-loading">{{ t('common.loading') }}</div>
    <table v-else-if="permissions.length" class="pp-table">
      <thead>
        <tr>
          <th>{{ t('security.workspaces.projectPermission.member', '成员') }}</th>
          <th>{{ t('security.workspaces.projectPermission.path', '项目路径') }}</th>
          <th>{{ t('security.workspaces.projectPermission.level', '级别') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in permissions" :key="p.id">
          <td>{{ p.memberUserId }}</td>
          <td class="pp-path">{{ p.projectPath }}</td>
          <td>{{ p.accessLevel }}</td>
          <td>
            <button class="pp-del" type="button" @click="remove(p.id)">×</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div v-else class="pp-empty">{{ t('security.workspaces.projectPermission.empty', '暂无项目权限') }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { workspacePolicyApi } from '@/api/index'
import { mcToast } from '@/composables/useMcToast'

const props = defineProps<{ workspaceId: string | number }>()
const { t } = useI18n()
const loading = ref(false)
const permissions = ref<any[]>([])
const draft = ref({ memberUserId: '', projectPath: '', accessLevel: 'READ' })

async function load() {
  loading.value = true
  try {
    const res: any = await workspacePolicyApi.listProjectPermissions(props.workspaceId)
    permissions.value = res?.data || []
  } finally {
    loading.value = false
  }
}

async function add() {
  if (!draft.value.projectPath) {
    mcToast.error(t('security.workspaces.projectPermission.pathRequired', '请填写项目路径'))
    return
  }
  try {
    await workspacePolicyApi.addProjectPermission(props.workspaceId, { ...draft.value })
    draft.value = { memberUserId: '', projectPath: '', accessLevel: 'READ' }
    await load()
  } catch {
    mcToast.error(t('common.saveFailed'))
  }
}

async function remove(id: number | string) {
  try {
    await workspacePolicyApi.deleteProjectPermission(props.workspaceId, id)
    await load()
  } catch {
    mcToast.error(t('common.deleteFailed'))
  }
}

onMounted(load)
watch(() => props.workspaceId, load)
</script>

<style scoped>
.pp-panel { display: flex; flex-direction: column; gap: 12px; }
.pp-add { display: flex; gap: 8px; flex-wrap: wrap; }
.pp-add .form-input { flex: 1; min-width: 120px; }
.pp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.pp-table th, .pp-table td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--mc-border, #e2e8f0); }
.pp-path { font-family: monospace; }
.pp-del {
  width: 26px; height: 26px; border-radius: 8px;
  border: 1px solid var(--mc-border, #e2e8f0); background: transparent;
  color: #ef4444; cursor: pointer;
}
.pp-del:hover { background: rgba(239,68,68,0.08); }
.pp-empty { color: var(--mc-text-tertiary, #94a3b8); font-size: 13px; }
</style>
