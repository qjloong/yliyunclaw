<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner plugin-agent-bindings-page">
        <div class="mc-page-header">
          <div class="mc-page-header__left">
            <button class="btn-back" type="button" @click="goBackToPlugins">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 12H5M12 19l-7-7 7-7"/>
              </svg>
              返回插件列表
            </button>
            <div>
              <div class="mc-page-kicker">教学出题规则插件</div>
              <h1 class="mc-page-title">已绑定 Agent</h1>
              <p class="mc-page-desc">查看“教学出题规则插件”当前已绑定的 Agent 实例，按工作区和能力包集中确认实际落地情况。</p>
            </div>
          </div>
          <div class="header-actions">
            <button class="btn-secondary" type="button" :disabled="loading" @click="loadBindings">
              {{ loading ? '刷新中...' : '刷新' }}
            </button>
          </div>
        </div>

        <section class="binding-summary-grid">
          <article class="binding-summary-card mc-surface-card">
            <span>工作区</span>
            <strong>{{ workspaceCount }}</strong>
            <p>已扫描存在 Agent 的可访问工作区</p>
          </article>
          <article class="binding-summary-card mc-surface-card">
            <span>绑定实例</span>
            <strong>{{ boundAgents.length }}</strong>
            <p>已绑定“教学出题规则插件”的 Agent 实例</p>
          </article>
          <article class="binding-summary-card mc-surface-card">
            <span>能力包</span>
            <strong>{{ capabilityPackCount }}</strong>
            <p>当前主要展示初中语文出题规则能力包</p>
          </article>
        </section>

        <div v-if="loading" class="loading-state mc-surface-card">
          <div class="loading-spinner"></div>
          <p>正在加载绑定实例...</p>
        </div>

        <div v-else-if="!boundAgents.length" class="empty-state mc-surface-card">
          <p class="empty-title">暂无已绑定 Agent</p>
          <p class="empty-hint">当前没有检测到绑定“教学出题规则插件”的 Agent 实例。</p>
        </div>

        <section v-else class="workspace-groups">
          <article v-for="group in groupedBindings" :key="group.workspaceId" class="workspace-group mc-surface-card">
            <div class="workspace-group__head">
              <div>
                <h2>{{ group.workspaceName }}</h2>
                <p>{{ group.items.length }} 个已绑定实例</p>
              </div>
              <span class="ops-badge">Workspace #{{ group.workspaceId }}</span>
            </div>

            <div class="binding-table-wrap">
              <table class="binding-table">
                <thead>
                  <tr>
                    <th>Agent 实例</th>
                    <th>类型</th>
                    <th>能力包</th>
                    <th>插件键</th>
                    <th>更新时间</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in group.items" :key="`${group.workspaceId}-${item.agent.id}`">
                    <td>
                      <div class="agent-cell">
                        <span class="agent-name">{{ item.agent.name }}</span>
                        <span class="agent-meta">{{ item.agent.description || '无描述' }}</span>
                      </div>
                    </td>
                    <td>{{ item.agent.agentType === 'plan_execute' ? 'Plan-Execute' : 'ReAct' }}</td>
                    <td>{{ capabilityLabel(item.binding) }}</td>
                    <td>{{ item.binding.pluginKey || '—' }}</td>
                    <td>{{ formatTime(item.agent.updateTime || item.binding.updateTime || item.agent.createTime) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { http, workspaceTeamApi } from '@/api'
import type { Agent, AgentPluginBinding, WorkspaceSummary } from '@/types'

type BoundAgentItem = {
  workspaceId: string
  workspaceName: string
  agent: Agent
  binding: AgentPluginBinding
}

const router = useRouter()
const loading = ref(false)
const boundAgents = ref<BoundAgentItem[]>([])
const workspaceCount = computed(() => new Set(boundAgents.value.map(item => item.workspaceId)).size)
const capabilityPackCount = computed(() => new Set(boundAgents.value.map(item => item.binding.capabilityPackId || '')).size)

const groupedBindings = computed(() => {
  const groups = new Map<string, { workspaceId: string; workspaceName: string; items: BoundAgentItem[] }>()
  for (const item of boundAgents.value) {
    const key = item.workspaceId
    if (!groups.has(key)) {
      groups.set(key, {
        workspaceId: key,
        workspaceName: item.workspaceName,
        items: [],
      })
    }
    groups.get(key)!.items.push(item)
  }
  return Array.from(groups.values())
})

onMounted(loadBindings)

function goBackToPlugins() {
  router.push('/plugins')
}

async function loadBindings() {
  loading.value = true
  try {
    const workspaceRes: any = await workspaceTeamApi.list()
    const workspaces = ((workspaceRes.data || []) as WorkspaceSummary[])
      .filter(workspace => workspace?.id !== undefined && workspace?.id !== null)

    const results = await Promise.all(workspaces.map(async (workspace) => {
      try {
        const res: any = await http.get('/agents', {
          headers: {
            'X-Workspace-Id': workspace.id,
          },
        })
        const agents = (res.data || []) as Agent[]
        return agents
          .map(agent => ({
            workspaceId: String(workspace.id),
            workspaceName: workspace.name || `Workspace ${workspace.id}`,
            agent,
            binding: resolvePrimaryEducationBinding(agent),
          }))
          .filter((item): item is BoundAgentItem => Boolean(item.binding))
      } catch {
        return []
      }
    }))

    boundAgents.value = results
      .flat()
      .sort((left, right) => {
        if (left.workspaceName !== right.workspaceName) {
          return left.workspaceName.localeCompare(right.workspaceName, 'zh-CN')
        }
        return left.agent.name.localeCompare(right.agent.name, 'zh-CN')
      })
  } catch (error: any) {
    boundAgents.value = []
    ElMessage.error(error?.message || '加载规则插件绑定实例失败')
  } finally {
    loading.value = false
  }
}

function resolvePrimaryEducationBinding(agent: Agent): AgentPluginBinding | null {
  const bindings = Array.isArray(agent.pluginBindings) ? agent.pluginBindings : []
  return bindings.find(binding => {
    if (!binding || binding.enabled === false) return false
    if (binding.pluginKey === 'builtin.teacher_exam' || binding.pluginKey === 'builtin.education_exam_rules') {
      return true
    }
    return String(binding.capabilityPackId || '').startsWith('capability.education.')
  }) || null
}

function capabilityLabel(binding: AgentPluginBinding | null | undefined): string {
  const capabilityPackId = String(binding?.capabilityPackId || '')
  if (capabilityPackId === 'capability.education.junior_chinese_exam'
    || capabilityPackId === 'capability.education.junior_classics_exam') {
    return '初中语文出题规则'
  }
  return capabilityPackId || '未指定能力包'
}

function formatTime(time?: string | null): string {
  if (!time) return '-'
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
</script>

<style scoped>
.plugin-agent-bindings-page { gap: 18px; }
.mc-page-header__left { display: flex; flex-direction: column; gap: 10px; }
.btn-back {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; background: var(--mc-bg-elevated); color: var(--mc-text-secondary);
  border: 1px solid var(--mc-border); border-radius: 10px;
  font-size: 13px; cursor: pointer; transition: all 0.15s;
  width: fit-content;
}
.btn-back:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.header-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.btn-secondary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 10px; font-size: 14px; cursor: pointer; transition: background 0.15s; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }
.binding-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 14px;
}
.binding-summary-card {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.binding-summary-card span {
  font-size: 13px;
  color: var(--mc-text-secondary);
}
.binding-summary-card strong {
  font-size: 28px;
  color: var(--mc-text-primary);
}
.binding-summary-card p {
  margin: 0;
  color: var(--mc-text-secondary);
  font-size: 13px;
}
.workspace-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.workspace-group {
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.workspace-group__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}
.workspace-group__head h2 {
  margin: 0 0 4px;
}
.workspace-group__head p {
  margin: 0;
  color: var(--mc-text-secondary);
  font-size: 13px;
}
.binding-table-wrap {
  overflow-x: auto;
}
.binding-table {
  width: 100%;
  border-collapse: collapse;
}
.binding-table th,
.binding-table td {
  padding: 12px 10px;
  border-bottom: 1px solid var(--mc-border-light);
  text-align: left;
  font-size: 13px;
}
.binding-table th {
  color: var(--mc-text-secondary);
  font-weight: 600;
}
.agent-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.agent-name {
  color: var(--mc-text-primary);
  font-weight: 600;
}
.agent-meta {
  color: var(--mc-text-secondary);
  font-size: 12px;
}
.empty-state,
.loading-state {
  padding: 28px;
  text-align: center;
}
.empty-title {
  margin: 0 0 6px;
  font-size: 16px;
  color: var(--mc-text-primary);
}
.empty-hint {
  margin: 0;
  color: var(--mc-text-secondary);
}
@media (max-width: 768px) {
  .workspace-group__head {
    flex-direction: column;
  }
}
</style>