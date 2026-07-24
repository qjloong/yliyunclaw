<template>
  <div class="policy-panel">
    <div v-if="loading" class="policy-loading">{{ t('common.loading') }}</div>
    <template v-else>
      <div class="form-grid">
        <div class="form-group">
          <label class="form-label">{{ t('security.workspaces.policy.sandboxMode', '沙箱模式') }}</label>
          <select v-model="form.sandboxMode" class="form-input">
            <option value="OFF">OFF</option>
            <option value="ENFORCED">ENFORCED</option>
            <option value="WARN">WARN</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('security.workspaces.policy.approvalPolicy', '审批策略') }}</label>
          <select v-model="form.approvalPolicy" class="form-input">
            <option value="NONE">NONE</option>
            <option value="AUTO">AUTO</option>
            <option value="ALWAYS">ALWAYS</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ t('security.workspaces.policy.networkPolicy', '网络策略') }}</label>
          <select v-model="form.networkPolicy" class="form-input">
            <option value="ALLOW">ALLOW</option>
            <option value="DENY_EXTERNAL">DENY_EXTERNAL</option>
            <option value="SANDBOX_ONLY">SANDBOX_ONLY</option>
          </select>
        </div>
        <div class="form-group full-width">
          <label class="form-label">{{ t('security.workspaces.policy.allowedActions', '允许动作 (JSON)') }}</label>
          <textarea v-model="form.allowedActionsJson" class="form-textarea" rows="2" placeholder='["tool_a","tool_b"]'></textarea>
        </div>
        <div class="form-group full-width">
          <label class="form-label">{{ t('security.workspaces.policy.deniedActions', '拒绝动作 (JSON)') }}</label>
          <textarea v-model="form.deniedActionsJson" class="form-textarea" rows="2" placeholder='["shell","browser"]'></textarea>
        </div>
        <div class="form-group full-width">
          <label class="form-label">{{ t('security.workspaces.policy.riskOverrides', '风险覆盖 (JSON)') }}</label>
          <textarea v-model="form.riskOverridesJson" class="form-textarea" rows="2" placeholder='{"HIGH":"BLOCK"}'></textarea>
        </div>
      </div>
      <div class="policy-actions">
        <button class="btn-primary" type="button" @click="save">{{ t('common.save') }}</button>
      </div>
    </template>
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
const form = ref({
  sandboxMode: 'OFF',
  approvalPolicy: 'NONE',
  networkPolicy: 'ALLOW',
  allowedActionsJson: '',
  deniedActionsJson: '',
  riskOverridesJson: '',
})

async function load() {
  loading.value = true
  try {
    const res: any = await workspacePolicyApi.getPolicy(props.workspaceId)
    const p = res?.data
    if (p) {
      form.value = {
        sandboxMode: p.sandboxMode || 'OFF',
        approvalPolicy: p.approvalPolicy || 'NONE',
        networkPolicy: p.networkPolicy || 'ALLOW',
        allowedActionsJson: p.allowedActionsJson || '',
        deniedActionsJson: p.deniedActionsJson || '',
        riskOverridesJson: p.riskOverridesJson || '',
      }
    }
  } finally {
    loading.value = false
  }
}

async function save() {
  try {
    await workspacePolicyApi.savePolicy(props.workspaceId, { ...form.value })
    mcToast.success(t('common.saved'))
  } catch {
    mcToast.error(t('common.saveFailed'))
  }
}

onMounted(load)
watch(() => props.workspaceId, load)
</script>

<style scoped>
.policy-panel { display: flex; flex-direction: column; gap: 12px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-group.full-width { grid-column: 1 / -1; }
.form-textarea { width: 100%; font-family: monospace; font-size: 12px; }
.policy-actions { display: flex; justify-content: flex-end; }
</style>
