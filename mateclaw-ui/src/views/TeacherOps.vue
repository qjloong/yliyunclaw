<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner teacher-ops-page">
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
              <h1 class="mc-page-title">初中语文出题规则</h1>
              <p class="mc-page-desc">集中管理“教学出题规则插件”下的初中语文 RulePack、执行流程、自优化草案和验收信号。</p>
            </div>
          </div>
          <div class="teacher-ops-header-actions">
            <button class="btn-secondary" type="button" :disabled="loading" @click="loadAll">
              {{ loading ? '刷新中...' : '刷新' }}
            </button>
          </div>
        </div>

        <section class="teacher-ops-summary">
          <article class="teacher-ops-stat mc-surface-card">
            <span>RulePack</span>
            <strong>{{ rulePacks.length }}</strong>
            <p>名著、文言文、现代文、古诗词、基础知识、写作</p>
          </article>
          <article class="teacher-ops-stat mc-surface-card">
            <span>Teacher Skill</span>
            <strong>{{ skills.length }}</strong>
            <p>{{ skillBindingView?.overridden ? '管理员已覆盖执行流程' : '使用系统默认执行流程' }}</p>
          </article>
          <article class="teacher-ops-stat mc-surface-card">
            <span>自优化草案</span>
            <strong>{{ pendingDraftCount }}</strong>
            <p>草案未审核发布前不会影响任何智能体</p>
          </article>
        </section>

        <section class="teacher-ops-grid">
          <article class="teacher-ops-panel mc-surface-card">
            <div class="teacher-ops-panel__head">
              <div>
                <h2>初中语文规则包</h2>
                <p>RulePack 是硬规则来源；知识库负责教材、课标和稿件依据；Prompt 只负责角色和流程。后续其他学段/学科将以新增能力包方式接入同一系统插件。</p>
              </div>
            </div>
            <div class="rule-pack-list">
              <button
                v-for="pack in rulePacks"
                :key="pack.id"
                class="rule-pack-card"
                type="button"
                @click="openRulePack(pack)"
              >
                <div>
                  <strong>{{ pack.name }}</strong>
                  <span>{{ moduleLabel(pack.module) }} · {{ pack.stage || 'junior' }} / {{ pack.subject || 'chinese' }}</span>
                </div>
                <small>v{{ pack.version }}</small>
              </button>
            </div>
          </article>

          <article class="teacher-ops-panel mc-surface-card">
            <div class="teacher-ops-panel__head">
              <div>
                <h2>默认执行流程</h2>
                <p>Skill 定义执行流程，RulePack 定义出题规则。这里调整的是“初中语文出题规则”能力包的系统默认流程。</p>
              </div>
              <span v-if="skillBindingView?.overridden" class="ops-badge">管理员覆盖</span>
            </div>
            <div class="skill-list">
              <label v-for="skill in skills" :key="skill.id" class="skill-row">
                <input v-model="selectedSkillIds" type="checkbox" :value="skill.id" />
                <div>
                  <strong>{{ skill.name }}</strong>
                  <span>{{ skill.purpose }}</span>
                </div>
              </label>
            </div>
            <div class="teacher-ops-actions">
              <button class="btn-primary" type="button" :disabled="savingSkills || selectedSkillIds.length === 0" @click="saveSkillBindings">
                保存 Skill 绑定
              </button>
              <button class="btn-secondary" type="button" :disabled="savingSkills || !skillBindingView?.overridden" @click="resetSkillBindings">
                恢复默认
              </button>
            </div>
          </article>
        </section>

        <section class="teacher-ops-panel mc-surface-card">
          <div class="teacher-ops-panel__head">
            <div>
              <h2>质量改进草案</h2>
              <p>Self-Improve 草案来自 Teacher 会话、Harness 验收或人工反馈；它不是 Skill 或 Tool，发布后才会影响规则覆盖。</p>
            </div>
          </div>
          <div class="draft-create-row">
            <input v-model="harnessRunId" class="ops-input" placeholder="Harness Run ID" />
            <button class="btn-primary" type="button" :disabled="savingDraft || !harnessRunId.trim()" @click="createDraft">
              生成草案
            </button>
          </div>
          <div v-if="!drafts.length" class="ops-empty">暂无自优化草案。</div>
          <div v-else class="draft-list">
            <article v-for="draft in drafts" :key="draft.id" class="draft-card">
              <div class="draft-card__head">
                <div>
                  <strong>{{ draft.title }}</strong>
                  <span>{{ draftStatusLabel(draft.status) }} · {{ draft.rulePackId || '未指定规则包' }}</span>
                </div>
                <span class="ops-badge">{{ draft.targetArea || draft.targetType }}</span>
              </div>
              <p>{{ draft.summary }}</p>
              <div v-if="draft.status === 'pending'" class="teacher-ops-actions">
                <button class="btn-primary" type="button" :disabled="savingDraft" @click="acceptDraft(draft.id, true, 'workspace')">
                  发布到工作区
                </button>
                <button class="btn-secondary" type="button" :disabled="savingDraft" @click="acceptDraft(draft.id, false, 'workspace')">
                  仅采纳记录
                </button>
                <button class="btn-secondary danger" type="button" :disabled="savingDraft" @click="rejectDraft(draft.id)">
                  拒绝
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>

    <div v-if="selectedRulePack" class="modal-overlay" @click.self="closeRulePack">
      <div class="modal rule-pack-modal">
        <div class="modal-header">
          <div>
            <p class="rule-pack-kicker">Teacher 规则包</p>
            <h2>{{ selectedRulePack.name }} v{{ selectedRulePack.version }}</h2>
            <p v-if="selectedRulePackView?.overridden" class="rule-pack-override-state">当前使用自定义配置</p>
            <p v-else class="rule-pack-override-state">当前使用插件统一配置</p>
          </div>
          <button class="modal-close" @click="closeRulePack">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body rule-pack-body">
          <section class="rule-pack-section">
            <h3>题型规则</h3>
            <div class="question-rule-grid">
              <article v-for="rule in selectedRulePack.questionTypeRules" :key="rule.type" class="question-rule-card">
                <div>
                  <strong>{{ rule.displayName }}</strong>
                  <span>{{ rule.defaultScore }}</span>
                </div>
                <p>{{ rule.generationRule }}</p>
              </article>
            </div>
          </section>

          <section class="rule-pack-section">
            <h3>硬性规则</h3>
            <ul class="rule-list">
              <li v-for="rule in selectedRulePack.hardRules" :key="rule">{{ rule }}</li>
            </ul>
          </section>

          <section class="rule-pack-section">
            <h3>管理员覆盖配置</h3>
            <textarea v-model="rulePackJsonDraft" class="rule-pack-json-editor" spellcheck="false"></textarea>
            <p class="binding-hint">修改后将作为自定义配置生效，保持 id 不变。</p>
            <div class="teacher-ops-actions">
              <button class="btn-primary" type="button" :disabled="savingRulePack" @click="saveRulePackOverride('workspace')">
                保存自定义配置
              </button>
              <button class="btn-secondary danger" type="button" :disabled="savingRulePack || !selectedRulePackView?.overridden" @click="clearRulePackOverride('workspace')">
                恢复插件统一配置
              </button>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { teacherImprovementApi, teacherRulePackApi, teacherSkillApi } from '@/api'
import type { TeacherImprovementDraft, TeacherRulePack, TeacherRulePackView, TeacherSkillBindingView, TeacherSkillDefinition } from '@/types'

const router = useRouter()
const loading = ref(false)
const rulePacks = ref<TeacherRulePack[]>([])
const skills = ref<TeacherSkillDefinition[]>([])
const skillBindingView = ref<TeacherSkillBindingView | null>(null)
const selectedSkillIds = ref<string[]>([])
const drafts = ref<TeacherImprovementDraft[]>([])
const harnessRunId = ref('')
const savingSkills = ref(false)
const savingDraft = ref(false)
const savingRulePack = ref(false)
const selectedRulePack = ref<TeacherRulePack | null>(null)
const selectedRulePackView = ref<TeacherRulePackView | null>(null)
const rulePackJsonDraft = ref('')

const pendingDraftCount = computed(() => drafts.value.filter(draft => draft.status === 'pending').length)

onMounted(loadAll)

function goBackToPlugins() {
  router.push('/plugins')
}

async function loadAll() {
  loading.value = true
  try {
    const [rulePackRes, skillRes, bindingRes, draftRes]: any[] = await Promise.all([
      teacherRulePackApi.list(),
      teacherSkillApi.list(),
      teacherSkillApi.bindings(),
      teacherImprovementApi.list(),
    ])
    rulePacks.value = rulePackRes.data || []
    skills.value = skillRes.data || []
    skillBindingView.value = bindingRes.data || null
    selectedSkillIds.value = skillBindingView.value?.activeSkillIds?.length
      ? [...skillBindingView.value.activeSkillIds]
      : skills.value.map(skill => skill.id)
    drafts.value = draftRes.data || []
  } catch (error: any) {
    ElMessage.error(error?.message || '初中语文出题规则数据加载失败')
  } finally {
    loading.value = false
  }
}

async function openRulePack(pack: TeacherRulePack) {
  selectedRulePack.value = pack
  selectedRulePackView.value = null
  rulePackJsonDraft.value = JSON.stringify(pack, null, 2)
  try {
    const res: any = await teacherRulePackApi.view(pack.id)
    const view = res.data as TeacherRulePackView
    selectedRulePackView.value = view
    selectedRulePack.value = view.rulePack
    rulePackJsonDraft.value = JSON.stringify(view.rulePack, null, 2)
  } catch {
    // Keep the list version visible.
  }
}

function closeRulePack() {
  selectedRulePack.value = null
  selectedRulePackView.value = null
  rulePackJsonDraft.value = ''
}

async function saveRulePackOverride(scope: 'workspace' | 'global') {
  if (!selectedRulePack.value) return
  let parsed: TeacherRulePack
  try {
    parsed = JSON.parse(rulePackJsonDraft.value)
  } catch {
    ElMessage.error('规则包 JSON 格式不正确')
    return
  }
  if (parsed.id !== selectedRulePack.value.id) {
    ElMessage.error('规则包 id 不能修改')
    return
  }
  savingRulePack.value = true
  try {
    const res: any = await teacherRulePackApi.update(parsed.id, parsed, scope)
    const view = res.data as TeacherRulePackView
    selectedRulePackView.value = view
    selectedRulePack.value = view.rulePack
    rulePackJsonDraft.value = JSON.stringify(view.rulePack, null, 2)
    rulePacks.value = rulePacks.value.map(pack => pack.id === view.rulePack.id ? view.rulePack : pack)
    ElMessage.success(scope === 'global' ? '全局规则包覆盖已保存' : '工作区规则包覆盖已保存')
  } catch (error: any) {
    ElMessage.error(error?.message || '规则包保存失败')
  } finally {
    savingRulePack.value = false
  }
}

async function clearRulePackOverride(scope: 'workspace' | 'global') {
  if (!selectedRulePack.value) return
  savingRulePack.value = true
  try {
    const res: any = await teacherRulePackApi.clearOverride(selectedRulePack.value.id, scope)
    const view = res.data as TeacherRulePackView
    selectedRulePackView.value = view
    selectedRulePack.value = view.rulePack
    rulePackJsonDraft.value = JSON.stringify(view.rulePack, null, 2)
    rulePacks.value = rulePacks.value.map(pack => pack.id === view.rulePack.id ? view.rulePack : pack)
    ElMessage.success(scope === 'global' ? '已清除全局覆盖' : '已清除工作区覆盖')
  } catch (error: any) {
    ElMessage.error(error?.message || '恢复内置规则包失败')
  } finally {
    savingRulePack.value = false
  }
}

async function saveSkillBindings() {
  if (!selectedSkillIds.value.length) {
    ElMessage.error('至少保留一个 Teacher Skill')
    return
  }
  savingSkills.value = true
  try {
    const res: any = await teacherSkillApi.updateBindings(selectedSkillIds.value)
    skillBindingView.value = res.data
    selectedSkillIds.value = [...(skillBindingView.value?.activeSkillIds || [])]
    ElMessage.success('Teacher Skill 绑定已保存')
  } catch (error: any) {
    ElMessage.error(error?.message || 'Teacher Skill 绑定保存失败')
  } finally {
    savingSkills.value = false
  }
}

async function resetSkillBindings() {
  savingSkills.value = true
  try {
    const res: any = await teacherSkillApi.resetBindings()
    skillBindingView.value = res.data
    selectedSkillIds.value = [...(skillBindingView.value?.activeSkillIds || [])]
    ElMessage.success('Teacher Skill 已恢复默认')
  } catch (error: any) {
    ElMessage.error(error?.message || 'Teacher Skill 恢复失败')
  } finally {
    savingSkills.value = false
  }
}

async function createDraft() {
  savingDraft.value = true
  try {
    await teacherImprovementApi.create(harnessRunId.value.trim())
    harnessRunId.value = ''
    ElMessage.success('自优化草案已生成')
    await loadAll()
  } catch (error: any) {
    ElMessage.error(error?.message || '自优化草案生成失败')
  } finally {
    savingDraft.value = false
  }
}

async function acceptDraft(id: string, publish: boolean, scope: 'workspace' | 'global') {
  savingDraft.value = true
  try {
    await teacherImprovementApi.accept(id, { publish, scope })
    ElMessage.success(publish ? '草案已发布' : '草案已采纳记录')
    await loadAll()
  } catch (error: any) {
    ElMessage.error(error?.message || '草案处理失败')
  } finally {
    savingDraft.value = false
  }
}

async function rejectDraft(id: string) {
  savingDraft.value = true
  try {
    await teacherImprovementApi.reject(id)
    ElMessage.success('草案已拒绝')
    await loadAll()
  } catch (error: any) {
    ElMessage.error(error?.message || '草案拒绝失败')
  } finally {
    savingDraft.value = false
  }
}

function moduleLabel(module: string) {
  const labels: Record<string, string> = {
    classic_reading: '名著',
    classical_chinese: '文言文',
    modern_reading: '现代文',
    ancient_poetry: '古诗词',
    basic_knowledge: '基础知识',
    writing: '写作',
  }
  return labels[module] || module
}

function draftStatusLabel(status: string) {
  if (status === 'pending') return '待审核'
  if (status === 'accepted') return '已采纳'
  if (status === 'rejected') return '已拒绝'
  return status || '未知'
}
</script>

<style scoped>
.teacher-ops-page { display: flex; flex-direction: column; gap: 18px; }
.mc-page-header__left { display: flex; flex-direction: column; gap: 10px; }
.btn-back {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 6px 12px; background: var(--mc-bg-elevated); color: var(--mc-text-secondary);
  border: 1px solid var(--mc-border); border-radius: 10px;
  font-size: 13px; cursor: pointer; transition: all 0.15s;
  width: fit-content;
}
.btn-back:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.teacher-ops-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.teacher-ops-stat { padding: 18px; }
.teacher-ops-stat span { color: var(--mc-text-secondary); font-size: 13px; }
.teacher-ops-stat strong { display: block; margin: 8px 0; font-size: 28px; color: var(--mc-text); }
.teacher-ops-stat p,
.teacher-ops-panel p,
.draft-card p { margin: 0; color: var(--mc-text-secondary); line-height: 1.6; }
.teacher-ops-grid { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr); gap: 14px; align-items: start; }
.teacher-ops-panel { padding: 18px; }
.teacher-ops-panel__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; margin-bottom: 14px; }
.teacher-ops-panel h2 { margin: 0 0 6px; font-size: 18px; color: var(--mc-text); }
.rule-pack-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.rule-pack-card,
.skill-row,
.draft-card { border: 1px solid var(--mc-border-light); background: var(--mc-bg); border-radius: 8px; }
.rule-pack-card { display: flex; justify-content: space-between; gap: 12px; padding: 14px; text-align: left; cursor: pointer; color: var(--mc-text); }
.rule-pack-card:hover { border-color: var(--mc-primary); background: var(--mc-bg-muted); }
.rule-pack-card strong,
.skill-row strong,
.draft-card strong { display: block; color: var(--mc-text); }
.rule-pack-card span,
.skill-row span,
.draft-card span,
.rule-pack-card small { color: var(--mc-text-secondary); font-size: 12px; }
.skill-list,
.draft-list { display: flex; flex-direction: column; gap: 10px; }
.skill-row { display: grid; grid-template-columns: auto 1fr; gap: 10px; padding: 12px; align-items: flex-start; }
.teacher-ops-actions,
.draft-create-row { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; align-items: center; }
.ops-input { min-width: 260px; flex: 1; border: 1px solid var(--mc-border); border-radius: 8px; padding: 10px 12px; background: var(--mc-bg); color: var(--mc-text); }
.ops-badge { border: 1px solid var(--mc-border-light); border-radius: 999px; padding: 4px 8px; color: var(--mc-text-secondary); font-size: 12px; white-space: nowrap; }
.ops-empty { padding: 20px; border: 1px dashed var(--mc-border); border-radius: 8px; color: var(--mc-text-secondary); text-align: center; }
.draft-card { padding: 14px; }
.draft-card__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 8px; }

/* Buttons */
.btn-primary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-primary); color: white; border: none; border-radius: 10px; font-size: 14px; font-weight: 500; cursor: pointer; transition: background 0.15s; }
.btn-primary:hover { background: var(--mc-primary-hover); }
.btn-primary:disabled { background: var(--mc-border); cursor: not-allowed; }
.btn-secondary { display: inline-flex; align-items: center; gap: 6px; padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 10px; font-size: 14px; cursor: pointer; transition: background 0.15s; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }
.btn-secondary:disabled { opacity: 0.55; cursor: not-allowed; }
.danger { color: var(--mc-danger); border-color: color-mix(in srgb, var(--mc-danger) 40%, var(--mc-border)); }

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal { background: var(--mc-bg-elevated); border: 1px solid var(--mc-border); border-radius: 16px; width: 100%; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 20px 60px rgba(0,0,0,0.15); }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border-bottom: 1px solid var(--mc-border-light); }
.modal-header h2 { font-size: 18px; font-weight: 600; color: var(--mc-text-primary); margin: 0; }
.modal-close { width: 32px; height: 32px; border: none; background: none; cursor: pointer; color: var(--mc-text-tertiary); display: flex; align-items: center; justify-content: center; border-radius: 6px; }
.modal-close:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.modal-body { flex: 1; overflow-y: auto; padding: 20px 24px; }

.rule-pack-modal { max-width: 980px; width: min(980px, calc(100vw - 40px)); }
.rule-pack-kicker { margin: 0 0 4px; color: var(--mc-primary); font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }
.rule-pack-override-state,
.binding-hint { color: var(--mc-text-secondary); font-size: 13px; }
.rule-pack-body { display: flex; flex-direction: column; gap: 16px; }
.rule-pack-section { border: 1px solid var(--mc-border-light); border-radius: 8px; padding: 14px; background: var(--mc-bg); }
.rule-pack-section h3 { margin: 0 0 12px; font-size: 15px; color: var(--mc-text); }
.question-rule-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.question-rule-card { border: 1px solid var(--mc-border-light); border-radius: 8px; padding: 12px; }
.question-rule-card div { display: flex; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.rule-list { margin: 0; padding-left: 18px; color: var(--mc-text-secondary); line-height: 1.7; }
.rule-pack-json-editor { width: 100%; min-height: 260px; border: 1px solid var(--mc-border); border-radius: 8px; padding: 12px; background: var(--mc-bg-muted); color: var(--mc-text); font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; line-height: 1.5; resize: vertical; }
@media (max-width: 900px) {
  .teacher-ops-summary,
  .teacher-ops-grid,
  .rule-pack-list,
  .question-rule-grid { grid-template-columns: 1fr; }
}
</style>
