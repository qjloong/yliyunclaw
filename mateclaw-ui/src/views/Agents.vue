<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner agents-page">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Agent Studio</div>
            <h1 class="mc-page-title">{{ t('agents.title') }}</h1>
            <p class="mc-page-desc">{{ t('agents.desc') }}</p>
          </div>
          <button class="btn-primary" @click="openCreateModal">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            {{ t('agents.newAgent') }}
          </button>
        </div>

        <div class="agents-toolbar mc-surface-card">
          <div class="filter-bar">
            <div class="search-box">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input v-model="searchText" :placeholder="t('agents.search')" class="search-input" />
            </div>
            <div class="filter-tabs">
              <button v-for="tab in filterTabs" :key="tab.value" class="filter-tab"
                :class="{ active: activeFilter === tab.value }" @click="activeFilter = tab.value">
                {{ t(tab.key) }}
              </button>
            </div>
          </div>
        </div>

        <!-- Agent card grid -->
        <div class="agent-grid" v-if="showActiveGrid">
          <div
            v-for="agent in filteredAgents"
            :key="agent.id"
            class="agent-card mc-surface-card"
            :class="{ 'agent-card--disabled': !agent.enabled }"
          >
            <div class="agent-card__header">
              <AgentIcon class="agent-card__icon" :value="agent.icon" :size="34" fallback="🤖" :title="agent.name" />
              <label class="toggle-switch toggle-switch--sm">
                <input type="checkbox" :checked="agent.enabled" @change="toggleAgent(agent)" />
                <span class="toggle-slider"></span>
              </label>
            </div>
            <div class="agent-card__body">
              <h3 class="agent-card__name">{{ agent.name }}</h3>
              <p class="agent-card__desc">{{ agent.description || t('agents.messages.noDescription') }}</p>
            </div>
            <div class="agent-card__meta">
              <span class="tag type-tag">{{ agent.agentType === 'react' ? 'ReAct' : 'Plan-Execute' }}</span>
              <span v-if="isCurrentDefault(agent)" class="tag agent-default-tag">{{ t('agents.status.currentDefault') }}</span>
              <div class="tags-cell" v-if="agent.tags">
                <span v-for="tag in parseTags(agent.tags)" :key="tag" class="tag tag-item">{{ tag }}</span>
              </div>
            </div>
            <div class="agent-card__footer">
              <button
                class="agent-card__primary-action"
                type="button"
                :disabled="isCurrentDefault(agent)"
                @click="setDefaultAgent(agent)"
              >
                {{ t('agents.actions.setDefault') }}
              </button>
              <div class="agent-card__actions">
                <button class="action-btn" :title="t('agents.tabs.context')" @click="goToAgentContextFor(agent)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
                  </svg>
                </button>
                <button class="action-btn" :title="t('agents.actions.edit')" @click="openEditModal(agent)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="action-btn danger" :title="t('agents.actions.delete')" @click="deleteAgent(agent)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty state -->
        <div v-else-if="showEmptyState" class="empty-state mc-surface-card">
          <div class="empty-icon">🤖</div>
          <h3>{{ t('agents.emptyTitle') }}</h3>
          <p>{{ t('agents.emptyDesc') }}</p>
          <button class="btn-primary" @click="openCreateModal">{{ t('agents.newAgent') }}</button>
        </div>

        <div v-if="showDeletedSection" class="deleted-agents mc-surface-card">
          <div class="deleted-agents__header">
            <h3>{{ t('agents.deletedTitle') }}</h3>
          </div>
          <div class="deleted-agents__list">
            <div v-for="agent in filteredDeletedAgents" :key="agent.id" class="deleted-agents__item">
              <div class="deleted-agents__meta">
                <AgentIcon :value="agent.icon" :size="20" fallback="🤖" :title="agent.name" />
                <span class="deleted-agents__name">{{ agent.name }}</span>
              </div>
              <button class="btn-secondary" type="button" @click="restoreAgent(agent)">
                {{ t('agents.actions.restore') }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    

    <!-- Template Selector Modal -->
    <div v-if="showTemplateSelector" class="modal-overlay">
      <div class="modal template-modal">
        <div class="modal-header">
          <h2>{{ t('agents.templates.title') }}</h2>
          <button class="modal-close" @click="showTemplateSelector = false">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <p class="template-desc">{{ t('agents.templates.desc') }}</p>
          <div class="template-grid">
            <div
              v-for="tpl in templates"
              :key="tpl.id"
              class="template-card mc-surface-card"
              :class="{ applying: applyingTemplate }"
              @click="!applyingTemplate && applyTemplate(tpl.id)"
            >
              <div class="template-icon">
                <AgentIcon :value="tpl.icon" :size="28" fallback="🤖" :title="templateTitle(tpl)" />
              </div>
              <div class="template-content">
                <div class="template-header-row">
                  <div class="template-info">
                    <div class="template-name-row">
                      <h4 class="template-name">{{ templateTitle(tpl) }}</h4>
                      <span v-if="tpl.featured" class="template-badge">{{ t('agents.templates.featured') }}</span>
                    </div>
                    <p class="template-detail">{{ templateDescription(tpl) }}</p>
                  </div>
                </div>
                <div class="template-tags">
                  <span v-for="tag in templateTags(tpl)" :key="tag" class="tag-chip">{{ tag }}</span>
                </div>
                <div v-if="templateRulePack(tpl)" class="template-rule-pack">
                  <div class="template-rule-pack__head">
                    <span class="template-rule-pack__label">规则包</span>
                    <strong>{{ templateRulePack(tpl)?.name }}</strong>
                    <span>v{{ templateRulePack(tpl)?.version }}</span>
                    <button class="template-rule-pack__detail" type="button" @click.stop="openRulePackDetail(templateRulePack(tpl))">
                      查看规则
                    </button>
                  </div>
                  <div class="template-rule-pack__rules">
                    <span
                      v-for="rule in templateRulePack(tpl)?.questionTypeRules?.slice(0, 4)"
                      :key="rule.type"
                    >
                      {{ rule.displayName }} · {{ rule.defaultScore }}
                    </span>
                  </div>
                </div>
                <div v-if="isTeacherTemplate(tpl) && teacherRulePackList.length" class="template-rule-pack">
                  <div class="template-rule-pack__head">
                    <span class="template-rule-pack__label">初中语文模块</span>
                    <strong>{{ teacherRulePackList.length }} 类规则包</strong>
                  </div>
                  <div class="template-rule-pack__rules">
                    <button
                      v-for="pack in teacherRulePackList"
                      :key="pack.id"
                      class="template-skill-chip"
                      type="button"
                      @click.stop="openRulePackDetail(pack)"
                    >
                      {{ pack.name }}
                    </button>
                  </div>
                </div>
                <div v-if="templateTeacherSkills(tpl).length" class="template-rule-pack template-teacher-skills">
                  <div class="template-rule-pack__head">
                    <span class="template-rule-pack__label">执行 Skill</span>
                    <strong>{{ templateTeacherSkills(tpl).length }} 个流程</strong>
                    <span v-if="teacherSkillBindingView?.overridden" class="rule-pack-mini-state">管理员覆盖</span>
                  </div>
                  <div class="template-rule-pack__rules">
                    <button
                      v-for="skill in templateTeacherSkills(tpl)"
                      :key="skill.id"
                      class="template-skill-chip"
                      type="button"
                      @click.stop="openTeacherSkillDetail(skill)"
                    >
                      {{ skill.name }}
                    </button>
                  </div>
                  <div v-if="isAdmin" class="teacher-skill-binding-editor" @click.stop>
                    <label v-for="skill in Object.values(teacherSkillMap)" :key="skill.id" class="teacher-skill-binding-option">
                      <input v-model="selectedTeacherSkillIds" type="checkbox" :value="skill.id" />
                      <span>{{ skill.name }}</span>
                    </label>
                    <div class="teacher-skill-binding-actions">
                      <button class="template-sync-btn" type="button" :disabled="savingTeacherSkillBindings || selectedTeacherSkillIds.length === 0" @click.stop="saveTeacherSkillBindings">
                        保存 Skill 绑定
                      </button>
                      <button class="template-sync-btn" type="button" :disabled="savingTeacherSkillBindings || !teacherSkillBindingView?.overridden" @click.stop="resetTeacherSkillBindings">
                        恢复默认
                      </button>
                    </div>
                  </div>
                </div>
                <div v-if="isTeacherTemplate(tpl) && isAdmin" class="template-rule-pack teacher-improvement-panel" @click.stop>
                  <div class="template-rule-pack__head">
                    <span class="template-rule-pack__label">自优化草案</span>
                    <strong>{{ pendingTeacherImprovementCount }} 条待审核</strong>
                    <button class="template-rule-pack__detail" type="button" @click.stop="loadTeacherImprovementDrafts">
                      刷新
                    </button>
                  </div>
                  <div class="teacher-improvement-actions">
                    <input
                      v-model="improvementHarnessRunId"
                      class="teacher-improvement-input"
                      placeholder="Harness Run ID"
                    />
                    <button
                      class="template-sync-btn"
                      type="button"
                      :disabled="savingTeacherImprovement || !improvementHarnessRunId"
                      @click.stop="createTeacherImprovementDraft"
                    >
                      生成草案
                    </button>
                  </div>
                  <div v-if="teacherImprovementDrafts.length" class="teacher-improvement-list">
                    <article v-for="draft in teacherImprovementDrafts.slice(0, 3)" :key="draft.id" class="teacher-improvement-item">
                      <div>
                        <strong>{{ draft.title }}</strong>
                        <span>{{ teacherImprovementStatusLabel(draft.status) }} · {{ teacherImprovementDraftMeta(draft) }}</span>
                      </div>
                      <p>{{ draft.summary }}</p>
                      <ul class="teacher-improvement-item__signals">
                        <li v-for="item in teacherImprovementDraftSignals(draft)" :key="`${draft.id}-${item}`">{{ item }}</li>
                      </ul>
                      <div v-if="draft.status === 'pending'" class="teacher-improvement-item__actions">
                        <button class="template-sync-btn" type="button" :disabled="savingTeacherImprovement" @click.stop="acceptTeacherImprovementDraft(draft.id, true, 'workspace')">
                          接受并发布到工作区
                        </button>
                        <button class="template-sync-btn" type="button" :disabled="savingTeacherImprovement" @click.stop="acceptTeacherImprovementDraft(draft.id, false, 'workspace')">
                          仅标记接受
                        </button>
                        <button class="template-sync-btn" type="button" :disabled="savingTeacherImprovement" @click.stop="rejectTeacherImprovementDraft(draft.id)">
                          拒绝
                        </button>
                      </div>
                    </article>
                  </div>
                  <p class="binding-hint">草案来自 Harness 失败或人工反馈，默认不生效；只有管理员接受并发布后才影响新会话。</p>
                </div>
                <div v-if="templateKnowledgeHealthLabel(tpl.id)" class="template-health" :class="{ ready: templateHealth(tpl.id)?.ready }">
                  <span class="template-health-dot"></span>
                  <span>{{ templateKnowledgeHealthLabel(tpl.id) }}</span>
                </div>
                <div v-if="templateHealth(tpl.id)" class="template-checks">
                  {{ templateCaseCheckLabel(tpl.id) }}
                </div>
                <div v-if="templateHealth(tpl.id)" class="template-checks">
                  {{ templateApplicationLabel(tpl.id) }}
                </div>
                <div v-if="templateNeedsDefaultFileSync(tpl.id) || templateCanStartMockTask(tpl)" class="template-actions-row">
                  <button
                    v-if="templateNeedsDefaultFileSync(tpl.id)"
                    class="template-sync-btn"
                    type="button"
                    :disabled="applyingTemplate || syncingTemplateId === tpl.id"
                    @click.stop="syncTemplateDefaultFiles(tpl.id)"
                  >
                    {{ syncingTemplateId === tpl.id ? t('common.loading') : t('agents.templates.syncDefaultFiles') }}
                  </button>
                  <button
                    v-if="templateCanStartMockTask(tpl)"
                    class="template-sync-btn"
                    type="button"
                    :disabled="applyingTemplate || syncingTemplateId === tpl.id"
                    @click.stop="startTemplateMockTask(tpl)"
                  >
                    {{ t('agents.templates.startMockTask') }}
                  </button>
                </div>
                <div v-if="templateCanStartMockTask(tpl)" class="template-mock-task-list" @click.stop>
                  <button
                    v-for="(task, taskIndex) in tpl.mockAcceptanceTasks"
                    :key="String(task.id || taskIndex)"
                    class="template-mock-task-btn"
                    type="button"
                    :disabled="applyingTemplate || syncingTemplateId === tpl.id"
                    @click.stop="startTemplateMockTask(tpl, task, taskIndex)"
                  >
                    <span class="template-mock-task-index">{{ taskIndex + 1 }}</span>
                    <span class="template-mock-task-title">{{ task.title || t('agents.templates.startMockTask') }}</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="openBlankCreateModal">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            {{ t('agents.templates.skip') }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="selectedRulePack" class="modal-overlay" @click.self="closeRulePackDetail">
      <div class="modal rule-pack-modal">
        <div class="modal-header">
          <div>
            <p class="rule-pack-kicker">Teacher 规则包</p>
            <h2>{{ selectedRulePack.name }} v{{ selectedRulePack.version }}</h2>
            <p v-if="selectedRulePackView?.workspaceOverridden" class="rule-pack-override-state">当前工作区使用管理员覆盖版本</p>
            <p v-else-if="selectedRulePackView?.globalOverridden" class="rule-pack-override-state">当前使用全局管理员覆盖版本</p>
          </div>
          <button class="modal-close" @click="closeRulePackDetail">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body rule-pack-body">
          <section v-if="isAdmin" class="rule-pack-section rule-pack-editor">
            <div class="rule-pack-editor__head">
              <h3>管理员覆盖配置</h3>
              <div class="rule-pack-editor__actions">
                <button class="template-sync-btn" type="button" @click="syncRulePackJsonFromForm">
                  从表单生成 JSON
                </button>
                <button class="template-sync-btn" type="button" :disabled="savingRulePack" @click="saveRulePackOverride('workspace')">
                  保存工作区覆盖
                </button>
                <button class="template-sync-btn" type="button" :disabled="savingRulePack" @click="saveRulePackOverride('global')">
                  保存全局覆盖
                </button>
                <button class="template-sync-btn" type="button" :disabled="savingRulePack || !selectedRulePackView?.workspaceOverridden" @click="clearRulePackOverride('workspace')">
                  清除工作区覆盖
                </button>
                <button class="template-sync-btn" type="button" :disabled="savingRulePack || !selectedRulePackView?.globalOverridden" @click="clearRulePackOverride('global')">
                  清除全局覆盖
                </button>
              </div>
            </div>
            <div class="rule-pack-form-editor">
              <div class="rule-pack-form-grid">
                <div class="form-group">
                  <label class="form-label">学段</label>
                  <input v-model="rulePackForm.stage" class="form-input" placeholder="junior" />
                </div>
                <div class="form-group">
                  <label class="form-label">学科</label>
                  <input v-model="rulePackForm.subject" class="form-input" placeholder="chinese" />
                </div>
                <div class="form-group">
                  <label class="form-label">模块</label>
                  <input v-model="rulePackForm.module" class="form-input" />
                </div>
                <div class="form-group">
                  <label class="form-label">名称</label>
                  <input v-model="rulePackForm.name" class="form-input" />
                </div>
                <div class="form-group">
                  <label class="form-label">版本</label>
                  <input v-model="rulePackForm.version" class="form-input" />
                </div>
              </div>
              <div class="rule-pack-form-block">
                <h4>题型比例</h4>
                <div class="rule-pack-form-grid">
                  <div v-for="item in rulePackMixFormItems" :key="item.key" class="form-group">
                    <label class="form-label">{{ item.label }}</label>
                    <input v-model="rulePackForm.defaultQuestionMix[item.key]" class="form-input" />
                  </div>
                </div>
              </div>
              <div class="rule-pack-form-block">
                <h4>题型规则</h4>
                <div class="rule-pack-question-editor">
                  <div v-for="rule in rulePackForm.questionTypeRules" :key="rule.type" class="rule-pack-question-edit-item">
                    <div class="rule-pack-form-grid">
                      <div class="form-group">
                        <label class="form-label">题型</label>
                        <input v-model="rule.displayName" class="form-input" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">默认分值</label>
                        <input v-model="rule.defaultScore" class="form-input" />
                      </div>
                    </div>
                    <div class="form-group full-width">
                      <label class="form-label">生成规则</label>
                      <textarea v-model="rule.generationRule" class="form-textarea" rows="2"></textarea>
                    </div>
                    <div class="rule-pack-checkboxes">
                      <label><input v-model="rule.requiresAnswer" type="checkbox" /> 必须有答案</label>
                      <label><input v-model="rule.requiresScoringRubric" type="checkbox" /> 必须有采分点</label>
                      <label><input v-model="rule.requiresMaterial" type="checkbox" /> 必须有材料/来源</label>
                    </div>
                  </div>
                </div>
              </div>
              <div class="rule-pack-form-block">
                <h4>硬性规则</h4>
                <div class="rule-pack-hard-edit-list">
                  <div v-for="(_, idx) in rulePackForm.hardRules" :key="idx" class="rule-pack-hard-edit-item">
                    <input v-model="rulePackForm.hardRules[idx]" class="form-input" />
                    <button class="provider-pref-btn danger" type="button" @click="removeHardRule(idx)">×</button>
                  </div>
                </div>
                <button class="template-sync-btn" type="button" @click="addHardRule">新增硬性规则</button>
              </div>
              <div class="rule-pack-form-block">
                <h4>资料与课标要求</h4>
                <div class="rule-pack-hard-edit-list">
                  <div v-for="(_, idx) in rulePackForm.sourceRequirements" :key="idx" class="rule-pack-source-edit-item">
                    <div class="rule-pack-form-grid">
                      <div class="form-group">
                        <label class="form-label">类型</label>
                        <input v-model="rulePackForm.sourceRequirements[idx].label" class="form-input" placeholder="课程标准 / 最新教材 / 题型要求" />
                      </div>
                      <div class="form-group">
                        <label class="form-label">识别 Key</label>
                        <input v-model="rulePackForm.sourceRequirements[idx].id" class="form-input" placeholder="curriculum_standard" />
                      </div>
                    </div>
                    <div class="form-group full-width">
                      <label class="form-label">要求</label>
                      <textarea v-model="rulePackForm.sourceRequirements[idx].rule" class="form-textarea" rows="2"></textarea>
                    </div>
                    <button class="provider-pref-btn danger" type="button" @click="removeSourceRequirement(idx)">×</button>
                  </div>
                </div>
                <button class="template-sync-btn" type="button" @click="addSourceRequirement">新增资料要求</button>
              </div>
            </div>
            <textarea v-model="rulePackJsonDraft" class="rule-pack-json-editor" spellcheck="false"></textarea>
            <p class="binding-hint">工作区覆盖优先于全局覆盖；覆盖只影响新会话；请保持 id 不变。普通用户只能使用管理员发布后的有效规则。</p>
          </section>
          <section class="rule-pack-section">
            <h3>适用边界</h3>
            <div class="rule-pack-mix">
              <span>学段：{{ selectedRulePack.stage || 'junior' }}</span>
              <span>学科：{{ selectedRulePack.subject || 'chinese' }}</span>
              <span>模块：{{ selectedRulePack.module }}</span>
            </div>
          </section>
          <section class="rule-pack-section">
            <h3>默认题型比例</h3>
            <div class="rule-pack-mix">
              <span v-for="item in rulePackMixItems(selectedRulePack)" :key="item.key">
                {{ item.label }}：{{ item.value }}
              </span>
            </div>
          </section>
          <section class="rule-pack-section">
            <h3>题型规则</h3>
            <div class="rule-pack-rule-list">
              <article v-for="rule in selectedRulePack.questionTypeRules" :key="rule.type" class="rule-pack-rule-item">
                <div class="rule-pack-rule-item__head">
                  <strong>{{ rule.displayName }}</strong>
                  <span>{{ rule.defaultScore }}</span>
                </div>
                <p>{{ rule.generationRule }}</p>
                <div class="rule-pack-flags">
                  <span v-if="rule.requiresAnswer">必须有答案</span>
                  <span v-if="rule.requiresScoringRubric">必须有采分点</span>
                  <span v-if="rule.requiresMaterial">必须有材料/来源</span>
                </div>
              </article>
            </div>
          </section>
          <section class="rule-pack-section">
            <h3>硬性规则</h3>
            <ul class="rule-pack-hard-rules">
              <li v-for="rule in selectedRulePack.hardRules" :key="rule">{{ rule }}</li>
            </ul>
          </section>
          <section v-if="selectedRulePack.sourceRequirements?.length" class="rule-pack-section">
            <h3>资料与课标要求</h3>
            <div class="rule-pack-acceptance">
              <div v-for="item in selectedRulePack.sourceRequirements" :key="item.id || item.label" class="rule-pack-acceptance__item">
                <strong>{{ item.label || item.id }}</strong>
                <span>{{ item.rule }}</span>
              </div>
            </div>
          </section>
          <section class="rule-pack-section">
            <h3>验收项</h3>
            <div class="rule-pack-acceptance">
              <div v-for="item in selectedRulePack.acceptanceMatrix" :key="item.id || item.label" class="rule-pack-acceptance__item">
                <strong>{{ item.label }}</strong>
                <span>{{ item.rule }}</span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <div v-if="selectedTeacherSkill" class="modal-overlay" @click.self="selectedTeacherSkill = null">
      <div class="modal rule-pack-modal">
        <div class="modal-header">
          <div>
            <p class="rule-pack-kicker">Teacher Skill</p>
            <h2>{{ selectedTeacherSkill.name }} v{{ selectedTeacherSkill.version }}</h2>
          </div>
          <button class="modal-close" @click="selectedTeacherSkill = null">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body rule-pack-body">
          <section class="rule-pack-section">
            <h3>用途</h3>
            <p class="teacher-skill-purpose">{{ selectedTeacherSkill.purpose }}</p>
          </section>
          <section class="rule-pack-section">
            <h3>执行流程</h3>
            <ol class="teacher-skill-steps">
              <li v-for="step in selectedTeacherSkill.workflowSteps" :key="step">{{ step }}</li>
            </ol>
          </section>
          <section class="rule-pack-section">
            <h3>支持规则包</h3>
            <div class="rule-pack-mix">
              <span v-for="id in selectedTeacherSkill.supportedRulePackIds" :key="id">{{ teacherRulePackMap[id]?.name || id }}</span>
            </div>
          </section>
          <section class="rule-pack-section">
            <h3>验收信号</h3>
            <div class="rule-pack-acceptance">
              <div v-for="item in selectedTeacherSkill.acceptanceSignals" :key="item.id || item.label" class="rule-pack-acceptance__item">
                <strong>{{ item.label }}</strong>
                <span>{{ item.rule }}</span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>

    <!-- Create/Edit Modal -->
    <div v-if="showModal" class="modal-overlay">
      <div class="modal" :class="{ 'modal--wide': modalTab === 'home' || modalTab === 'skills' || modalTab === 'tools' || modalTab === 'providers' }">
        <div class="modal-header">
          <h2>{{ editingAgent ? t('agents.modal.editTitle') : t('agents.modal.newTitle') }}</h2>
          <button class="modal-close" @click="closeModal">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <!-- Tab Bar -->
          <div class="modal-tabs">
            <button class="modal-tab" :class="{ active: modalTab === 'basic' }" @click="modalTab = 'basic'">
              {{ t('agents.tabs.basic', 'Basic') }}
            </button>
            <button class="modal-tab" :class="{ active: modalTab === 'home' }" @click="modalTab = 'home'">
              {{ t('agents.tabs.home', 'Chat Home') }}
            </button>
            <button v-if="editingAgent && isAdmin" class="modal-tab" :class="{ active: modalTab === 'skills' }" @click="modalTab = 'skills'">
              {{ t('agents.tabs.skills', 'Skills') }}
              <span v-if="selectedSkillIds.length" class="tab-badge">{{ selectedSkillIds.length }}</span>
            </button>
            <button v-if="editingAgent && isAdmin" class="modal-tab" :class="{ active: modalTab === 'tools' }" @click="modalTab = 'tools'">
              {{ t('agents.tabs.tools', 'Tools') }}
              <span v-if="selectedToolNames.length" class="tab-badge">{{ selectedToolNames.length }}</span>
            </button>
            <button v-if="editingAgent && isAdmin" class="modal-tab" :class="{ active: modalTab === 'providers' }" @click="modalTab = 'providers'">
              {{ t('agents.tabs.providers', 'Providers') }}
              <span v-if="selectedProviderIds.length" class="tab-badge">{{ selectedProviderIds.length }}</span>
            </button>
          </div>

          <!-- Basic Tab -->
          <div v-if="modalTab === 'basic'" class="form-grid">
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.name') }} *</label>
              <input v-model="form.name" class="form-input" :placeholder="t('agents.placeholders.name')" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.icon') }}</label>
              <input v-model="form.icon" class="form-input" :placeholder="t('agents.placeholders.icon')" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.type') }}</label>
              <select v-model="form.agentType" class="form-input">
                <option value="react">{{ t('agents.types.react') }}</option>
                <option value="plan_execute">{{ t('agents.types.planExecute') }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.maxIterations') }}</label>
              <input v-model.number="form.maxIterations" type="number" min="1" max="50" class="form-input" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.defaultThinkingLevel') }}</label>
              <select v-model="form.defaultThinkingLevel" class="form-input">
                <option :value="null">{{ t('agents.thinkingLevels.auto') }}</option>
                <option value="off">{{ t('agents.thinkingLevels.off') }}</option>
                <option value="low">{{ t('agents.thinkingLevels.low') }}</option>
                <option value="medium">{{ t('agents.thinkingLevels.medium') }}</option>
                <option value="high">{{ t('agents.thinkingLevels.high') }}</option>
                <option value="max">{{ t('agents.thinkingLevels.max') }}</option>
              </select>
            </div>
            <div class="form-group full-width">
              <label class="form-label">{{ t('agents.fields.description') }}</label>
              <input v-model="form.description" class="form-input" :placeholder="t('agents.placeholders.description')" />
            </div>
            <div class="form-group full-width">
              <label class="form-label">{{ t('agents.fields.systemPrompt') }}</label>
              <textarea v-model="form.systemPrompt" class="form-textarea" rows="5" :placeholder="t('agents.placeholders.systemPrompt')"></textarea>
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.tags') }}</label>
              <input v-model="form.tags" class="form-input" :placeholder="t('agents.placeholders.tags')" />
            </div>
            <div class="form-group">
              <label class="form-label">{{ t('agents.fields.enabled') }}</label>
              <label class="toggle-switch" style="margin-top: 6px;">
                <input type="checkbox" v-model="form.enabled" />
                <span class="toggle-slider"></span>
              </label>
            </div>
            <div class="form-group full-width">
              <label class="form-label">{{ t('agents.fields.knowledgeBases') }}</label>
              <p class="binding-hint">{{ t('agents.binding.knowledgeHint') }}</p>
              <div v-if="availableKnowledgeBases.length === 0" class="binding-empty">{{ t('agents.binding.noKnowledgeBases') }}</div>
              <el-select
                v-else
                v-model="selectedKnowledgeBaseIds"
                class="knowledge-select"
                multiple
                filterable
                clearable
                popper-class="knowledge-select-popper"
                :placeholder="t('agents.binding.knowledgePlaceholder')"
                :no-match-text="t('agents.binding.noKnowledgeBaseMatch')"
                :no-data-text="t('agents.binding.noKnowledgeBases')"
              >
                <template #label="{ label }">
                  <span class="knowledge-selected-tag">{{ label }}</span>
                </template>
                <el-option
                  v-for="kb in availableKnowledgeBases"
                  :key="knowledgeBaseOptionId(kb)"
                  :label="kb.name"
                  :value="knowledgeBaseOptionId(kb)"
                  class="knowledge-select-option"
                >
                  <div class="knowledge-option-row">
                    <svg class="binding-icon knowledge-base-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H7a3 3 0 0 0-3 3V5.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>
                      <path d="M7 19V3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                      <path d="M10 7h6M10 11h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                    </svg>
                    <div class="knowledge-option-info">
                      <span class="binding-name">{{ kb.name }}</span>
                      <span v-if="kb.description" class="binding-desc">{{ kb.description?.slice(0, 80) }}</span>
                    </div>
                  </div>
                </el-option>
              </el-select>
            </div>
          </div>

          <div v-if="modalTab === 'home'" class="binding-tab binding-tab--home">
            <div class="home-settings">
              <div class="home-config-panel">
                <p class="binding-hint home-config-panel__hint">{{ t('agents.home.hint') }}</p>
                <div class="form-group full-width">
                  <label class="form-label">{{ t('agents.home.subtitle') }}</label>
                  <input v-model="form.homeSubtitle" class="form-input" :placeholder="t('agents.home.subtitlePlaceholder')" />
                  <span class="form-hint">{{ t('agents.home.subtitleHint') }}</span>
                </div>
              </div>
              <div class="home-quick-starts">
                <div v-for="(item, idx) in homeQuickStarts" :key="idx" class="home-quick-start-item">
                  <div class="home-quick-start-item__header">
                    <div class="home-quick-start-item__title-block">
                      <span class="home-quick-start-item__index">{{ t('agents.home.quickStartIndex', { index: idx + 1 }) }}</span>
                      <span class="home-quick-start-item__desc">{{ t('agents.home.quickStartPrompt') }}</span>
                    </div>
                    <button class="provider-pref-btn danger" type="button" @click="clearHomeQuickStart(idx)">
                      {{ t('agents.home.clear') }}
                    </button>
                  </div>
                  <div class="form-group">
                    <label class="form-label">{{ t('agents.home.quickStartTitle') }}</label>
                    <input v-model="item.title" class="form-input" :placeholder="t('agents.home.quickStartTitlePlaceholder')" />
                  </div>
                  <div class="form-group">
                    <label class="form-label">{{ t('agents.home.quickStartPrompt') }}</label>
                    <textarea v-model="item.prompt" class="form-textarea home-quick-start-item__textarea" rows="4" :placeholder="t('agents.home.quickStartPromptPlaceholder')"></textarea>
                  </div>
                </div>
              </div>
              <div class="home-actions">
                <button class="btn-secondary" type="button" @click="clearHomeConfig">
                  {{ t('agents.home.clearAll') }}
                </button>
              </div>
            </div>
          </div>

          <!-- Skills Tab -->
          <div v-if="modalTab === 'skills'" class="binding-tab">
            <p class="binding-hint">{{ t('agents.binding.skillsHint') }}</p>
            <div v-if="availableSkills.length === 0" class="binding-empty">{{ t('agents.binding.noSkills') }}</div>
            <div v-else class="binding-list">
              <label
                v-for="skill in availableSkills"
                :key="skill.id"
                class="binding-item"
                :class="{ selected: selectedSkillIds.includes(skill.id) }"
              >
                <input type="checkbox" :value="skill.id" v-model="selectedSkillIds" class="binding-checkbox" />
                <AgentIcon
                  class="binding-icon"
                  :value="skill.icon"
                  :size="20"
                  fallback="🧩"
                  :title="skill.name"
                />
                <div class="binding-info">
                  <span class="binding-name">{{ skill.name }}</span>
                  <span v-if="skill.description" class="binding-desc">{{ skill.description?.slice(0, 80) }}</span>
                </div>
                <span v-if="skill.version" class="binding-version">v{{ skill.version }}</span>
              </label>
            </div>
          </div>

          <!-- Tools Tab -->
          <div v-if="modalTab === 'tools'" class="binding-tab">
            <p class="binding-hint">{{ t('agents.binding.toolsHint') }}</p>
            <div v-if="availableTools.length === 0" class="binding-empty">{{ t('agents.binding.noTools') }}</div>
            <div v-else class="binding-list">
              <label
                v-for="tool in availableTools"
                :key="tool.name"
                class="binding-item"
                :class="{ selected: selectedToolNames.includes(tool.name) }"
              >
                <input type="checkbox" :value="tool.name" v-model="selectedToolNames" class="binding-checkbox" />
                <AgentIcon
                  class="binding-icon"
                  :value="tool.icon"
                  :size="20"
                  fallback="🛠"
                  :title="tool.displayName || tool.name"
                />
                <div class="binding-info">
                  <span class="binding-name">{{ tool.displayName || tool.name }}</span>
                  <span v-if="tool.description" class="binding-desc">{{ tool.description?.slice(0, 80) }}</span>
                </div>
                <span class="binding-type-badge">{{ tool.toolType }}</span>
              </label>
            </div>
          </div>

          <!-- Providers Tab (RFC-009 PR-3) -->
          <div v-if="modalTab === 'providers'" class="binding-tab">
            <p class="binding-hint">{{ t('agents.binding.providersHint') }}</p>
            <!-- Picked: ordered list with up/down/remove controls -->
            <div v-if="selectedProviderIds.length" class="provider-pref-list">
              <div
                v-for="(pid, idx) in selectedProviderIds"
                :key="pid"
                class="provider-pref-item"
              >
                <span class="provider-pref-rank">{{ idx + 1 }}</span>
                <span class="provider-pref-name">{{ providerNameById(pid) }}</span>
                <span class="provider-pref-id">{{ pid }}</span>
                <button class="provider-pref-btn" :disabled="idx === 0" @click="moveProvider(idx, -1)">↑</button>
                <button class="provider-pref-btn" :disabled="idx === selectedProviderIds.length - 1" @click="moveProvider(idx, 1)">↓</button>
                <button class="provider-pref-btn danger" @click="removeProvider(idx)">×</button>
              </div>
            </div>
            <div v-else class="binding-empty">{{ t('agents.binding.noProviderPreferences') }}</div>

            <!-- Unpicked: click to append -->
            <div v-if="unpickedProviders.length" class="provider-pref-pool">
              <p class="binding-hint" style="margin-top: 14px">{{ t('agents.binding.providersAddHint') }}</p>
              <button
                v-for="p in unpickedProviders"
                :key="p.id"
                class="provider-pref-add-btn"
                @click="addProvider(p.id)"
              >+ {{ p.name }}</button>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn-secondary" @click="closeModal">{{ t('common.cancel') }}</button>
          <button class="btn-primary" @click="saveAgent" :disabled="!form.name">
            {{ editingAgent ? t('agents.actions.update') : t('agents.actions.create') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { agentApi, agentBindingApi, modelApi, skillApi, toolApi, templateApi, teacherImprovementApi, teacherRulePackApi, teacherSkillApi, wikiApi } from '@/api/index'
import AgentIcon from '@/components/common/AgentIcon.vue'
import { isGlobalAdmin } from '@/utils/access'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { Agent, AgentTemplate, TeacherImprovementDraft, TeacherRulePack, TeacherRulePackView, TeacherSkillBindingView, TeacherSkillDefinition, TemplateAppliedAgentHealth, TemplateHealth } from '@/types/index'

const router = useRouter()
const { t, locale } = useI18n()
const workspaceStore = useWorkspaceStore()
const agents = ref<Agent[]>([])
const deletedAgents = ref<Agent[]>([])
const searchText = ref('')
const activeFilter = ref('all')
const isAdmin = computed(() => isGlobalAdmin())
const showModal = ref(false)
const editingAgent = ref<Agent | null>(null)
const modalTab = ref<'basic' | 'home' | 'skills' | 'tools' | 'providers'>('basic')

// Binding state
const availableSkills = ref<any[]>([])
const availableTools = ref<any[]>([])
const selectedSkillIds = ref<number[]>([])
const selectedToolNames = ref<string[]>([])
const availableKnowledgeBases = ref<any[]>([])
const selectedKnowledgeBaseIds = ref<string[]>([])
const homeQuickStarts = ref<Array<{ title: string; prompt: string }>>(createEmptyHomeQuickStarts())
// RFC-009 PR-3: per-agent provider preference order
const availableProviders = ref<{ id: string; name: string }[]>([])
const selectedProviderIds = ref<string[]>([])

// Template selector state
const showTemplateSelector = ref(false)
const templates = ref<AgentTemplate[]>([])
const templateHealthMap = ref<Record<string, TemplateHealth>>({})
const teacherRulePackMap = ref<Record<string, TeacherRulePack>>({})
const teacherRulePackList = computed(() => Object.values(teacherRulePackMap.value))
const teacherSkillMap = ref<Record<string, TeacherSkillDefinition>>({})
const teacherSkillBindingView = ref<TeacherSkillBindingView | null>(null)
const selectedTeacherSkillIds = ref<string[]>([])
const savingTeacherSkillBindings = ref(false)
const selectedRulePack = ref<TeacherRulePack | null>(null)
const selectedRulePackView = ref<TeacherRulePackView | null>(null)
const rulePackJsonDraft = ref('')
const rulePackForm = ref<TeacherRulePack>(createEmptyRulePackForm())
const savingRulePack = ref(false)
const selectedTeacherSkill = ref<TeacherSkillDefinition | null>(null)
const teacherImprovementDrafts = ref<TeacherImprovementDraft[]>([])
const improvementHarnessRunId = ref('')
const savingTeacherImprovement = ref(false)
const pendingTeacherImprovementCount = computed(() =>
  teacherImprovementDrafts.value.filter(draft => draft.status === 'pending').length
)

const rulePackMixLabels: Record<string, string> = {
  fillBlank: '填空',
  choice: '选择',
  shortAnswer: '简答',
  analysis: '分析',
  inquiry: '探究',
  microWriting: '微写作',
}

const rulePackMixFormItems = computed(() => Object.keys(rulePackForm.value.defaultQuestionMix || {}).map(key => ({
  key,
  label: rulePackMixLabels[key] || key,
})))
const applyingTemplate = ref(false)
const syncingTemplateId = ref<string | null>(null)

const filterTabs = computed(() => {
  const tabs = [
    { key: 'agents.tabs.all', value: 'all' },
    { key: 'agents.tabs.react', value: 'react' },
    { key: 'agents.tabs.planExecute', value: 'plan_execute' },
    { key: 'agents.tabs.enabled', value: 'enabled' },
    { key: 'agents.tabs.disabled', value: 'disabled' },
  ]
  if (isAdmin.value) {
    tabs.push({ key: 'agents.tabs.deleted', value: 'deleted' })
  }
  return tabs
})

const defaultForm = (): Partial<Agent> & { name: string; defaultThinkingLevel: string | null } => ({
  name: '',
  description: '',
  agentType: 'react',
  systemPrompt: '',
  maxIterations: 10,
  icon: '🤖',
  tags: '',
  enabled: true,
  defaultThinkingLevel: null,
  homeSubtitle: '',
  homeQuickStartsJson: '',
})

function createEmptyRulePackForm(): TeacherRulePack {
  return {
    id: '',
    stage: 'junior',
    subject: 'chinese',
    module: '',
    name: '',
    version: '',
    defaultQuestionMix: {},
    questionTypeRules: [],
    hardRules: [],
    sourceRequirements: [],
    acceptanceMatrix: [],
  }
}

function cloneRulePack(pack: TeacherRulePack): TeacherRulePack {
  const cloned = JSON.parse(JSON.stringify(pack)) as TeacherRulePack
  cloned.stage = cloned.stage || 'junior'
  cloned.subject = cloned.subject || 'chinese'
  cloned.sourceRequirements = Array.isArray(cloned.sourceRequirements) ? cloned.sourceRequirements : []
  return cloned
}

const form = ref(defaultForm())
const currentDefaultAgentId = computed(() => workspaceStore.getDefaultAgentId())

const filteredAgents = computed(() => {
  if (activeFilter.value === 'deleted') {
    return []
  }
  let list = agents.value
  if (searchText.value) {
    const q = searchText.value.toLowerCase()
    list = list.filter(a =>
      a.name.toLowerCase().includes(q) ||
      a.description?.toLowerCase().includes(q) ||
      a.tags?.toLowerCase().includes(q)
    )
  }
  if (activeFilter.value === 'react') list = list.filter(a => a.agentType === 'react')
  else if (activeFilter.value === 'plan_execute') list = list.filter(a => a.agentType === 'plan_execute')
  else if (activeFilter.value === 'enabled') list = list.filter(a => a.enabled)
  else if (activeFilter.value === 'disabled') list = list.filter(a => !a.enabled)
  return list
})

const filteredDeletedAgents = computed(() => {
  if (!isAdmin.value) return []
  let list = deletedAgents.value
  if (searchText.value) {
    const q = searchText.value.toLowerCase()
    list = list.filter(a =>
      a.name.toLowerCase().includes(q) ||
      a.description?.toLowerCase().includes(q) ||
      a.tags?.toLowerCase().includes(q)
    )
  }
  return list
})

const showDeletedOnly = computed(() => activeFilter.value === 'deleted')
const showActiveGrid = computed(() => !showDeletedOnly.value && filteredAgents.value.length > 0)
const showDeletedSection = computed(() => {
  if (!filteredDeletedAgents.value.length) return false
  return showDeletedOnly.value || activeFilter.value === 'all'
})
const showEmptyState = computed(() => !showActiveGrid.value && !showDeletedSection.value)

onMounted(() => {
  loadAgents()
})

async function loadAgents() {
  try {
    const res: any = await agentApi.list()
    agents.value = res.data || []
    if (isAdmin.value) {
      await loadDeletedAgents()
    } else {
      deletedAgents.value = []
    }
    const defaultAgentId = workspaceStore.getDefaultAgentId()
    if (defaultAgentId && !agents.value.some(agent => String(agent.id) === defaultAgentId)) {
      workspaceStore.setDefaultAgentId(workspaceStore.currentWorkspaceId, null)
    }
  } catch {
    deletedAgents.value = []
    ElMessage.error(t('agents.messages.loadFailed'))
  }
}

async function loadDeletedAgents() {
  try {
    const res: any = await agentApi.listDeleted()
    deletedAgents.value = res.data || []
  } catch {
    deletedAgents.value = []
  }
}

function isCurrentDefault(agent: Agent) {
  return String(agent.id) === currentDefaultAgentId.value
}

function parseTags(tags: string): string[] {
  return tags.split(',').map(s => s.trim()).filter(Boolean)
}

function createEmptyHomeQuickStarts() {
  return Array.from({ length: 4 }, () => ({ title: '', prompt: '' }))
}

function parseHomeQuickStarts(raw?: string | null) {
  if (!raw?.trim()) {
    return createEmptyHomeQuickStarts()
  }
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return createEmptyHomeQuickStarts()
    }
    const normalized = parsed.slice(0, 4).map((item: any) => ({
      title: String(item?.title || '').trim(),
      prompt: String(item?.prompt || item?.title || '').trim(),
    }))
    while (normalized.length < 4) {
      normalized.push({ title: '', prompt: '' })
    }
    return normalized
  } catch {
    return createEmptyHomeQuickStarts()
  }
}

function serializeHomeQuickStarts() {
  const normalized = homeQuickStarts.value
    .map(item => ({
      title: item.title.trim(),
      prompt: item.prompt.trim(),
    }))
    .filter(item => item.title || item.prompt)
    .slice(0, 4)
  return normalized.length ? JSON.stringify(normalized) : ''
}

function clearHomeQuickStart(index: number) {
  homeQuickStarts.value[index] = { title: '', prompt: '' }
}

function clearHomeConfig() {
  form.value.homeSubtitle = ''
  form.value.homeQuickStartsJson = ''
  homeQuickStarts.value = createEmptyHomeQuickStarts()
}

function formatTime(time?: string): string {
  if (!time) return '-'
  const d = new Date(time)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openCreateModal() {
  // Show template selector first
  showTemplateSelector.value = true
  loadTemplates()
}

function openBlankCreateModal() {
  showTemplateSelector.value = false
  editingAgent.value = null
  form.value = defaultForm()
  modalTab.value = 'basic'
  selectedSkillIds.value = []
  selectedToolNames.value = []
  selectedKnowledgeBaseIds.value = []
  homeQuickStarts.value = createEmptyHomeQuickStarts()
  selectedProviderIds.value = []
  loadKnowledgeBases()
  showModal.value = true
}

// RFC-009 PR-3: provider preference helpers
const unpickedProviders = computed(() =>
  availableProviders.value.filter(p => !selectedProviderIds.value.includes(p.id))
)

function providerNameById(id: string): string {
  return availableProviders.value.find(p => p.id === id)?.name || id
}

function addProvider(id: string) {
  if (!selectedProviderIds.value.includes(id)) {
    selectedProviderIds.value.push(id)
  }
}

function removeProvider(idx: number) {
  selectedProviderIds.value.splice(idx, 1)
}

function moveProvider(idx: number, dir: -1 | 1) {
  const next = idx + dir
  if (next < 0 || next >= selectedProviderIds.value.length) return
  const arr = selectedProviderIds.value
  ;[arr[idx], arr[next]] = [arr[next], arr[idx]]
}

async function loadTemplates() {
  try {
    const [templateRes, healthRes, rulePackRes, teacherSkillRes, teacherSkillBindingRes, teacherImprovementRes]: any[] = await Promise.all([
      templateApi.list(),
      templateApi.health(),
      teacherRulePackApi.list().catch(() => ({ data: [] })),
      teacherSkillApi.list().catch(() => ({ data: [] })),
      teacherSkillApi.bindings().catch(() => ({ data: null })),
      teacherImprovementApi.list().catch(() => ({ data: [] })),
    ])
    templates.value = templateRes.data || []
    const healthList = (healthRes.data || []) as TemplateHealth[]
    templateHealthMap.value = healthList.reduce<Record<string, TemplateHealth>>((acc, item) => {
      acc[item.templateId] = item
      return acc
    }, {})
    const rulePacks = (rulePackRes.data || []) as TeacherRulePack[]
    teacherRulePackMap.value = rulePacks.reduce<Record<string, TeacherRulePack>>((acc, item) => {
      acc[item.id] = item
      return acc
    }, {})
    const teacherSkills = (teacherSkillRes.data || []) as TeacherSkillDefinition[]
    teacherSkillMap.value = teacherSkills.reduce<Record<string, TeacherSkillDefinition>>((acc, item) => {
      acc[item.id] = item
      return acc
    }, {})
    teacherSkillBindingView.value = teacherSkillBindingRes.data || null
    selectedTeacherSkillIds.value = teacherSkillBindingView.value?.activeSkillIds?.length
      ? [...teacherSkillBindingView.value.activeSkillIds]
      : teacherSkills.map(skill => skill.id)
    teacherImprovementDrafts.value = teacherImprovementRes.data || []
  } catch {
    // Fallback: skip templates, open blank form
    openBlankCreateModal()
  }
}

function templateTitle(tpl: AgentTemplate): string {
  return (locale.value === 'zh-CN' && tpl.nameZh ? tpl.nameZh : tpl.name) || tpl.name
}

function templateDescription(tpl: AgentTemplate): string {
  return (locale.value === 'zh-CN' && tpl.descriptionZh ? tpl.descriptionZh : tpl.description) || ''
}

function templateTags(tpl: AgentTemplate): string[] {
  return (tpl.tags || '')
    .split(',')
    .map(tag => tag.trim())
    .filter(Boolean)
}

function isTeacherTemplate(tpl: AgentTemplate): boolean {
  return tpl.id === 'builtin.teacher_exam_assistant'
    || tpl.capabilityPack?.defaultRulePackId === 'teacher.rulepack.classic_reading.v2'
}

function templateRulePack(tpl: AgentTemplate): TeacherRulePack | undefined {
  const rulePackId = typeof tpl.capabilityPack?.defaultRulePackId === 'string'
    ? tpl.capabilityPack.defaultRulePackId
    : ''
  return rulePackId ? teacherRulePackMap.value[rulePackId] : undefined
}

async function openRulePackDetail(pack?: TeacherRulePack) {
  if (!pack) return
  selectedRulePack.value = pack
  selectedRulePackView.value = null
  rulePackJsonDraft.value = JSON.stringify(pack, null, 2)
  rulePackForm.value = cloneRulePack(pack)
  try {
    const res: any = await teacherRulePackApi.view(pack.id)
    const view = res.data as TeacherRulePackView
    selectedRulePackView.value = view
    selectedRulePack.value = view.rulePack
    rulePackForm.value = cloneRulePack(view.rulePack)
    rulePackJsonDraft.value = JSON.stringify(view.rulePack, null, 2)
  } catch {
    // Keep the already loaded rule pack visible.
  }
}

function closeRulePackDetail() {
  selectedRulePack.value = null
  selectedRulePackView.value = null
  rulePackJsonDraft.value = ''
  rulePackForm.value = createEmptyRulePackForm()
}

async function saveRulePackOverride(scope: 'workspace' | 'global' = 'workspace') {
  if (!selectedRulePack.value) return
  syncRulePackJsonFromForm()
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
    rulePackForm.value = cloneRulePack(view.rulePack)
    teacherRulePackMap.value[view.rulePack.id] = view.rulePack
    rulePackJsonDraft.value = JSON.stringify(view.rulePack, null, 2)
    ElMessage.success(scope === 'global' ? '全局规则包覆盖已保存' : '工作区规则包覆盖已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '规则包保存失败')
  } finally {
    savingRulePack.value = false
  }
}

async function clearRulePackOverride(scope: 'workspace' | 'global' = 'workspace') {
  if (!selectedRulePack.value) return
  savingRulePack.value = true
  try {
    const res: any = await teacherRulePackApi.clearOverride(selectedRulePack.value.id, scope)
    const view = res.data as TeacherRulePackView
    selectedRulePackView.value = view
    selectedRulePack.value = view.rulePack
    rulePackForm.value = cloneRulePack(view.rulePack)
    teacherRulePackMap.value[view.rulePack.id] = view.rulePack
    rulePackJsonDraft.value = JSON.stringify(view.rulePack, null, 2)
    ElMessage.success(scope === 'global' ? '已清除全局规则包覆盖' : '已清除工作区规则包覆盖')
  } catch (e: any) {
    ElMessage.error(e?.message || '恢复内置规则包失败')
  } finally {
    savingRulePack.value = false
  }
}

function syncRulePackJsonFromForm() {
  if (!selectedRulePack.value) return
  const next = cloneRulePack(rulePackForm.value)
  next.id = selectedRulePack.value.id
  next.module = next.module || selectedRulePack.value.module
  next.stage = next.stage || selectedRulePack.value.stage || 'junior'
  next.subject = next.subject || selectedRulePack.value.subject || 'chinese'
  next.sourceRequirements = Array.isArray(next.sourceRequirements) ? next.sourceRequirements : []
  next.acceptanceMatrix = selectedRulePack.value.acceptanceMatrix || []
  rulePackJsonDraft.value = JSON.stringify(next, null, 2)
}

function addHardRule() {
  rulePackForm.value.hardRules.push('')
}

function removeHardRule(idx: number) {
  rulePackForm.value.hardRules.splice(idx, 1)
}

function addSourceRequirement() {
  if (!Array.isArray(rulePackForm.value.sourceRequirements)) {
    rulePackForm.value.sourceRequirements = []
  }
  rulePackForm.value.sourceRequirements.push({ id: '', label: '', rule: '' })
}

function removeSourceRequirement(idx: number) {
  rulePackForm.value.sourceRequirements?.splice(idx, 1)
}

function templateTeacherSkills(tpl: AgentTemplate): TeacherSkillDefinition[] {
  const activeIds = teacherSkillBindingView.value?.activeSkillIds
  const ids = Array.isArray(activeIds) && activeIds.length > 0
    ? activeIds
    : Array.isArray(tpl.capabilityPack?.defaultTeacherSkillIds)
    ? tpl.capabilityPack.defaultTeacherSkillIds
    : []
  return ids
    .map(id => teacherSkillMap.value[String(id)])
    .filter(Boolean)
}

function openTeacherSkillDetail(skill?: TeacherSkillDefinition) {
  if (!skill) return
  selectedTeacherSkill.value = skill
}

async function saveTeacherSkillBindings() {
  if (!selectedTeacherSkillIds.value.length) {
    ElMessage.error('至少保留一个 Teacher Skill')
    return
  }
  savingTeacherSkillBindings.value = true
  try {
    const res: any = await teacherSkillApi.updateBindings(selectedTeacherSkillIds.value)
    teacherSkillBindingView.value = res.data as TeacherSkillBindingView
    selectedTeacherSkillIds.value = [...teacherSkillBindingView.value.activeSkillIds]
    ElMessage.success('Teacher Skill 绑定已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || 'Teacher Skill 绑定保存失败')
  } finally {
    savingTeacherSkillBindings.value = false
  }
}

async function resetTeacherSkillBindings() {
  savingTeacherSkillBindings.value = true
  try {
    const res: any = await teacherSkillApi.resetBindings()
    teacherSkillBindingView.value = res.data as TeacherSkillBindingView
    selectedTeacherSkillIds.value = [...teacherSkillBindingView.value.activeSkillIds]
    ElMessage.success('已恢复默认 Teacher Skill 绑定')
  } catch (e: any) {
    ElMessage.error(e?.message || '恢复默认 Teacher Skill 绑定失败')
  } finally {
    savingTeacherSkillBindings.value = false
  }
}

async function loadTeacherImprovementDrafts() {
  try {
    const res: any = await teacherImprovementApi.list()
    teacherImprovementDrafts.value = res.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '自优化草案加载失败')
  }
}

async function createTeacherImprovementDraft() {
  if (!improvementHarnessRunId.value) {
    ElMessage.error('请填写 Harness Run ID')
    return
  }
  savingTeacherImprovement.value = true
  try {
    const res: any = await teacherImprovementApi.create(improvementHarnessRunId.value, '从 Agent Studio 手动生成自优化草案')
    teacherImprovementDrafts.value = [res.data as TeacherImprovementDraft, ...teacherImprovementDrafts.value]
    improvementHarnessRunId.value = ''
    ElMessage.success('自优化草案已生成，等待审核发布')
  } catch (e: any) {
    ElMessage.error(e?.message || '自优化草案生成失败')
  } finally {
    savingTeacherImprovement.value = false
  }
}

async function acceptTeacherImprovementDraft(id: string, publish: boolean, scope: 'workspace' | 'global') {
  savingTeacherImprovement.value = true
  try {
    const res: any = await teacherImprovementApi.accept(id, {
      publish,
      scope,
      note: publish ? '管理员接受并发布' : '管理员接受，暂不发布',
    })
    replaceTeacherImprovementDraft(res.data as TeacherImprovementDraft)
    if (publish) {
      await loadTemplates()
    }
    ElMessage.success(publish ? '草案已接受并发布' : '草案已接受')
  } catch (e: any) {
    ElMessage.error(e?.message || '草案审核失败')
  } finally {
    savingTeacherImprovement.value = false
  }
}

async function rejectTeacherImprovementDraft(id: string) {
  savingTeacherImprovement.value = true
  try {
    const res: any = await teacherImprovementApi.reject(id, '管理员拒绝草案')
    replaceTeacherImprovementDraft(res.data as TeacherImprovementDraft)
    ElMessage.success('草案已拒绝')
  } catch (e: any) {
    ElMessage.error(e?.message || '草案拒绝失败')
  } finally {
    savingTeacherImprovement.value = false
  }
}

function replaceTeacherImprovementDraft(draft: TeacherImprovementDraft) {
  teacherImprovementDrafts.value = teacherImprovementDrafts.value.map(item =>
    item.id === draft.id ? draft : item
  )
}

function teacherImprovementStatusLabel(status: string) {
  if (status === 'pending') return '待审核'
  if (status === 'accepted') return '已接受'
  if (status === 'rejected') return '已拒绝'
  return status || '未知'
}

function teacherImprovementDraftMeta(draft: TeacherImprovementDraft) {
  const parts = [
    draft.rulePackId,
    draft.targetArea ? `归因：${draft.targetArea}` : '',
    draft.proposalType ? `建议：${draft.proposalType}` : '',
    draft.riskLevel ? `风险：${draft.riskLevel}` : '',
  ].filter(Boolean)
  return parts.join(' · ')
}

function teacherImprovementDraftSignals(draft: TeacherImprovementDraft) {
  const rows: string[] = []
  const primaryCause = String(draft.diagnosis?.primaryCause || '').trim()
  const action = String(draft.diagnosis?.recommendedAction || '').trim()
  const addedRules = draft.proposedPatch?.addedHardRules
  const skillSteps = Array.isArray(draft.proposedSkillPatch?.steps) ? draft.proposedSkillPatch.steps.length : 0
  const acceptanceChecks = Array.isArray(draft.proposedAcceptanceCase?.checks) ? draft.proposedAcceptanceCase.checks.length : 0
  if (primaryCause) rows.push(`原因：${primaryCause}`)
  if (action) rows.push(`建议：${action}`)
  if (typeof addedRules === 'number') rows.push(`规则补丁：新增 ${addedRules} 条硬规则`)
  if (skillSteps) rows.push(`Skill 草案：${skillSteps} 个执行步骤`)
  if (acceptanceChecks) rows.push(`验收草案：${acceptanceChecks} 个检查点`)
  return rows.slice(0, 5)
}

function rulePackMixItems(pack: TeacherRulePack) {
  const labels: Record<string, string> = {
    fillBlank: '填空',
    choice: '选择',
    shortAnswer: '简答',
    analysis: '分析',
    inquiry: '探究',
    microWriting: '微写作',
  }
  return Object.entries(pack.defaultQuestionMix || {}).map(([key, value]) => ({
    key,
    label: labels[key] || key,
    value: String(value),
  }))
}

function templateHealth(templateId: string): TemplateHealth | undefined {
  return templateHealthMap.value[templateId]
}

function templateKnowledgeHealthLabel(templateId: string): string {
  const health = templateHealth(templateId)
  if (!health) return t('agents.templates.healthUnknown')
  if ((health.seededKnowledgeBaseCount || 0) === 0 && (health.requiredCount || 0) === 0) return ''
  if ((health.seededKnowledgeBaseCount || 0) > 0) {
    return t('agents.templates.defaultKnowledge', {
      matched: health.matchedSeededKnowledgeBaseCount,
      total: health.seededKnowledgeBaseCount,
    })
  }
  if (health.ready) {
    return t('agents.templates.healthReady', {
      matched: health.matchedSeededKnowledgeBaseCount,
      total: health.seededKnowledgeBaseCount,
    })
  }
  return t('agents.templates.healthNeedsSetup', {
    matched: health.matchedRequiredCount,
    total: health.requiredCount,
  })
}

function templateCaseCheckLabel(templateId: string): string {
  const health = templateHealth(templateId)
  if (!health) return ''
  return t('agents.templates.caseChecks', {
    matched: health.passedCheckCount || 0,
    total: health.checkCount || 0,
  })
}

function templateAppliedAgentEvidence(health: TemplateHealth): TemplateAppliedAgentHealth | undefined {
  return health.appliedAgents?.find(item =>
    item.expectedWorkspaceFileCount !== item.matchedWorkspaceFileCount || !item.promptFilesConfigured
  ) || health.appliedAgents?.[0]
}

function templateApplicationLabel(templateId: string): string {
  const health = templateHealth(templateId)
  if (!health) return ''
  if ((health.appliedAgentCount || 0) <= 0) {
    return t('agents.templates.applicationMissing')
  }
  const sample = templateAppliedAgentEvidence(health)
  const payload = {
    matched: sample?.matchedWorkspaceFileCount || 0,
    total: sample?.expectedWorkspaceFileCount || 0,
    count: health.appliedAgentCount || 0,
  }
  if (health.applicationEvidenceReady) {
    return t('agents.templates.applicationReady', {
      ...payload,
    })
  }
  return t('agents.templates.applicationNeedsSetup', payload)
}

function templateNeedsDefaultFileSync(templateId: string): boolean {
  const health = templateHealth(templateId)
  return Boolean(health && (health.appliedAgentCount || 0) > 0 && !health.applicationEvidenceReady)
}

function templateCanStartMockTask(tpl: AgentTemplate): boolean {
  const health = templateHealth(tpl.id)
  return Boolean(health && (health.appliedAgentCount || 0) > 0 && (tpl.mockAcceptanceTasks?.length || 0) > 0)
}

async function applyTemplate(id: string) {
  applyingTemplate.value = true
  try {
    await templateApi.apply(id)
    ElMessage.success(t('agents.templates.applied'))
    showTemplateSelector.value = false
    await Promise.all([loadAgents(), loadTemplates()])
  } catch {
    ElMessage.error(t('agents.messages.saveFailed'))
  } finally {
    applyingTemplate.value = false
  }
}

function buildTemplateMockDraft(tpl: AgentTemplate, task: Record<string, any>): string {
  const expected = Array.isArray(task.expected) ? task.expected.join('\n- ') : ''
  const title = task.title || tpl.nameZh || tpl.name
  const input = task.input || ''
  return [
    `请按内置样例任务执行：${title}`,
    '',
    `任务输入：${input}`,
    expected ? `\n验收要点：\n- ${expected}` : '',
  ].join('\n')
}

function startTemplateMockTask(tpl: AgentTemplate, selectedTask?: Record<string, any>, selectedTaskIndex?: number) {
  const health = templateHealth(tpl.id)
  const agentId = health?.appliedAgents?.[0]?.agentId
  const task = selectedTask || tpl.mockAcceptanceTasks?.[0]
  if (!agentId || !task) {
    return
  }
  const mockExpected = Array.isArray(task.expected) ? task.expected.join('\n- ') : ''
  const taskIndex = String(selectedTaskIndex ?? tpl.mockAcceptanceTasks?.indexOf(task) ?? 0)
  showTemplateSelector.value = false
  const finalMockDraft = buildTemplateMockDraft(tpl, task)
  router.push({
    path: '/chat',
    query: {
      agentId: String(agentId),
      draftMessage: finalMockDraft,
      templateMock: '1',
      templateId: tpl.id,
      templateName: String(tpl.nameZh || tpl.name || ''),
      mockTaskId: String(task.id || ''),
      mockTaskIndex: taskIndex,
      mockTaskTitle: String(task.title || tpl.nameZh || tpl.name || ''),
      mockExpected,
    },
  })
}

async function syncTemplateDefaultFiles(id: string) {
  syncingTemplateId.value = id
  try {
    const res: any = await templateApi.syncDefaultFiles(id)
    const createdFileCount = Number(res?.data?.createdFileCount || 0)
    if (createdFileCount > 0) {
      ElMessage.success(t('agents.templates.syncSuccess', { count: createdFileCount }))
    } else {
      ElMessage.success(t('agents.templates.syncNoop'))
    }
    await loadTemplates()
  } catch {
    ElMessage.error(t('agents.templates.syncFailed'))
  } finally {
    syncingTemplateId.value = null
  }
}

function setDefaultAgent(agent: Agent) {
  if (!workspaceStore.currentWorkspaceId || isCurrentDefault(agent)) return
  workspaceStore.setDefaultAgentId(workspaceStore.currentWorkspaceId, agent.id)
  ElMessage.success(t('agents.messages.defaultSet', { name: agent.name }))
}

async function openEditModal(agent: Agent) {
  editingAgent.value = agent
  const knowledgeBaseIds = parseKnowledgeBaseIds(agent.knowledgeBaseIdsJson)
  form.value = {
    name: agent.name,
    description: agent.description || '',
    agentType: agent.agentType,
    systemPrompt: agent.systemPrompt || '',
    maxIterations: agent.maxIterations,
    icon: agent.icon || '🤖',
    tags: agent.tags || '',
    enabled: agent.enabled,
    defaultThinkingLevel: (agent as any).defaultThinkingLevel || null,
    knowledgeBaseIdsJson: agent.knowledgeBaseIdsJson || '[]',
    homeSubtitle: agent.homeSubtitle || '',
    homeQuickStartsJson: agent.homeQuickStartsJson || '',
  }
  homeQuickStarts.value = parseHomeQuickStarts(agent.homeQuickStartsJson)
  modalTab.value = 'basic'
  await loadKnowledgeBases()
  selectedKnowledgeBaseIds.value = normalizeKnowledgeBaseIds(knowledgeBaseIds)
  showModal.value = true

  if (!isAdmin.value) {
    return
  }

  // Load available skills/tools/providers and current bindings in parallel
  try {
    const [skillsRes, toolsRes, providersRes, boundSkillsRes, boundToolsRes, providerPrefsRes] = await Promise.all([
      // RFC-042: /skills is now paginated; binding dropdown only needs enabled skills,
      // so listEnabled() is both semantically correct and shape-stable (returns array).
      skillApi.listEnabled(),
      toolApi.listBindable(),
      modelApi.listProviders(),
      agentBindingApi.listSkills(agent.id),
      agentBindingApi.listTools(agent.id),
      agentBindingApi.listProviderPreferences(agent.id),
    ])
    availableSkills.value = (skillsRes as any).data || []
    availableTools.value = (toolsRes as any).data || []
    // Pool of providers the user has actually configured — no point letting an
    // agent prefer a provider that doesn't exist on this deployment.
    availableProviders.value = ((providersRes as any).data || [])
      .filter((p: any) => p.configured)
      .map((p: any) => ({ id: p.id, name: p.name }))
    selectedSkillIds.value = ((boundSkillsRes as any).data || [])
      .filter((b: any) => b.enabled)
      .map((b: any) => b.skillId)
    selectedToolNames.value = ((boundToolsRes as any).data || [])
      .filter((b: any) => b.enabled)
      .map((b: any) => b.toolName)
    selectedProviderIds.value = ((providerPrefsRes as any).data || [])
      .filter((b: any) => b.enabled)
      .map((b: any) => b.providerId)
  } catch {
    // Non-blocking: binding data load failure doesn't prevent editing basic info
  }
}

function closeModal() {
  showModal.value = false
  editingAgent.value = null
}

async function saveAgent() {
  try {
    let agentId: string | number
    const payload = {
      ...form.value,
      knowledgeBaseIdsJson: JSON.stringify(selectedKnowledgeBaseIds.value.map(id => String(id)).filter(Boolean)),
      homeSubtitle: form.value.homeSubtitle?.trim() || null,
      homeQuickStartsJson: serializeHomeQuickStarts() || null,
    }
    if (editingAgent.value) {
      await agentApi.update(editingAgent.value.id, payload)
      agentId = editingAgent.value.id
    } else {
      const res: any = await agentApi.create(payload)
      agentId = res.data?.id
    }

    // Save bindings (only for existing agents or after create returns id)
    if (agentId && editingAgent.value && isAdmin.value) {
      await Promise.all([
        agentBindingApi.setSkills(agentId, selectedSkillIds.value),
        agentBindingApi.setTools(agentId, selectedToolNames.value),
        agentBindingApi.setProviderPreferences(agentId, selectedProviderIds.value),
      ])
    }

    ElMessage.success(t('agents.messages.saveSuccess'))
    closeModal()
    await loadAgents()
  } catch {
    ElMessage.error(t('agents.messages.saveFailed'))
  }
}

async function loadKnowledgeBases() {
  try {
    const res: any = await wikiApi.listKBs()
    availableKnowledgeBases.value = res.data || []
    if (selectedKnowledgeBaseIds.value.length > 0) {
      selectedKnowledgeBaseIds.value = normalizeKnowledgeBaseIds(selectedKnowledgeBaseIds.value)
    }
  } catch {
    availableKnowledgeBases.value = []
  }
}

function knowledgeBaseOptionId(kb: any): string {
  return String(kb?.id ?? '')
}

function parseKnowledgeBaseIds(raw?: string | null): string[] {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed.map(item => String(item)).filter(Boolean)
  } catch {
    return []
  }
}

function normalizeKnowledgeBaseIds(ids: string[]): string[] {
  const optionIds = availableKnowledgeBases.value
    .map(knowledgeBaseOptionId)
    .filter(Boolean)
  const normalized = ids
    .map(id => {
      const current = String(id)
      if (optionIds.includes(current)) return current
      return findNearestKnowledgeBaseId(current, optionIds) || current
    })
    .filter(Boolean)
  return Array.from(new Set(normalized))
}

function findNearestKnowledgeBaseId(value: string, optionIds: string[]): string {
  if (!/^\d+$/.test(value)) return ''
  const normalizedTarget = normalizeUnsignedIntegerString(value)
  let best = ''
  let bestDiff = ''
  for (const optionId of optionIds) {
    if (!/^\d+$/.test(optionId)) continue
    const diff = diffUnsignedIntegerStrings(optionId, normalizedTarget)
    if (compareUnsignedIntegerStrings(diff, '10000') > 0) continue
    if (!best || compareUnsignedIntegerStrings(diff, bestDiff) < 0) {
      best = optionId
      bestDiff = diff
    }
  }
  return best
}

function normalizeUnsignedIntegerString(value: string): string {
  const normalized = String(value || '').replace(/^0+(?=\d)/, '')
  return normalized || '0'
}

function compareUnsignedIntegerStrings(left: string, right: string): number {
  const normalizedLeft = normalizeUnsignedIntegerString(left)
  const normalizedRight = normalizeUnsignedIntegerString(right)
  if (normalizedLeft.length !== normalizedRight.length) {
    return normalizedLeft.length - normalizedRight.length
  }
  if (normalizedLeft === normalizedRight) return 0
  return normalizedLeft > normalizedRight ? 1 : -1
}

function diffUnsignedIntegerStrings(left: string, right: string): string {
  let minuend = normalizeUnsignedIntegerString(left)
  let subtrahend = normalizeUnsignedIntegerString(right)
  if (compareUnsignedIntegerStrings(minuend, subtrahend) < 0) {
    ;[minuend, subtrahend] = [subtrahend, minuend]
  }
  const minuendDigits = minuend.split('').map(Number)
  const subtrahendDigits = subtrahend.split('').map(Number)
  const result: number[] = []
  let borrow = 0
  let i = minuendDigits.length - 1
  let j = subtrahendDigits.length - 1
  while (i >= 0) {
    let current = minuendDigits[i] - borrow
    const subtract = j >= 0 ? subtrahendDigits[j] : 0
    if (current < subtract) {
      current += 10
      borrow = 1
    } else {
      borrow = 0
    }
    result.push(current - subtract)
    i -= 1
    j -= 1
  }
  return normalizeUnsignedIntegerString(result.reverse().join(''))
}

async function deleteAgent(agent: Agent) {
  try {
    await ElMessageBox.confirm(t('agents.messages.deleteConfirm'), t('agents.actions.delete'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await agentApi.delete(agent.id)
    ElMessage.success(t('agents.messages.deleteSuccess'))
    await loadAgents()
  } catch {
    ElMessage.error(t('agents.messages.deleteFailed'))
  }
}

async function restoreAgent(agent: Agent) {
  try {
    await agentApi.restore(agent.id)
    ElMessage.success(t('agents.messages.restoreSuccess'))
    await loadAgents()
  } catch {
    ElMessage.error(t('agents.messages.restoreFailed'))
  }
}

function goToAgentContext() {
  const agentId = editingAgent.value?.id
  closeModal()
  router.push({ path: '/settings/agent-context', query: agentId ? { agentId: String(agentId) } : {} })
}

function goToAgentContextFor(agent: Agent) {
  router.push({ path: '/settings/agent-context', query: { agentId: String(agent.id) } })
}

async function toggleAgent(agent: Agent) {
  try {
    await agentApi.update(agent.id, { ...agent, enabled: !agent.enabled })
    ElMessage.success(t('agents.messages.toggleSuccess'))
    await loadAgents()
  } catch {
    ElMessage.error(t('agents.messages.toggleFailed'))
  }
}
</script>

<style scoped>
.agents-page { gap: 18px; }

.btn-primary { display: flex; align-items: center; gap: 6px; padding: 10px 16px; background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover)); color: white; border: none; border-radius: 14px; font-size: 14px; font-weight: 600; cursor: pointer; transition: background 0.15s, transform 0.15s; box-shadow: var(--mc-shadow-soft); }
.btn-primary:hover { background: var(--mc-primary-hover); }
.btn-primary:disabled { background: var(--mc-border); cursor: not-allowed; }
.btn-secondary { padding: 8px 16px; background: var(--mc-bg-elevated); color: var(--mc-text-primary); border: 1px solid var(--mc-border); border-radius: 12px; font-size: 14px; cursor: pointer; }
.btn-secondary:hover { background: var(--mc-bg-sunken); }

.agents-toolbar { padding: 18px; }
.filter-bar { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.search-box { display: flex; align-items: center; gap: 8px; background: var(--mc-bg-muted); border: 1px solid var(--mc-border); border-radius: 14px; padding: 10px 12px; flex: 1; max-width: 360px; }
.search-box svg { color: var(--mc-text-tertiary); flex-shrink: 0; }
.search-input { border: none; outline: none; font-size: 14px; color: var(--mc-text-primary); flex: 1; background: transparent; }
.filter-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
.filter-tab { padding: 8px 14px; border: 1px solid var(--mc-border); background: var(--mc-bg-muted); border-radius: 999px; font-size: 13px; color: var(--mc-text-secondary); cursor: pointer; transition: all 0.15s; font-weight: 600; }
.filter-tab:hover { background: var(--mc-bg-sunken); }
.filter-tab.active { background: var(--mc-primary-bg); border-color: var(--mc-primary); color: var(--mc-primary); font-weight: 500; }

/* Agent Card Grid */
.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.agent-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  transition: all 0.15s;
  cursor: default;
}

.agent-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.agent-card--disabled {
  opacity: 0.55;
}

.agent-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.agent-card__icon {
  font-size: 36px;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--mc-primary-bg);
  border-radius: 14px;
}

.agent-card__body {
  flex: 1;
  min-height: 0;
}

.agent-card__name {
  font-size: 16px;
  font-weight: 700;
  color: var(--mc-text-primary);
  margin: 0 0 4px;
  letter-spacing: -0.02em;
}

.agent-card__desc {
  font-size: 13px;
  color: var(--mc-text-tertiary);
  margin: 0;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.agent-card__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.agent-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--mc-border-light);
}

.agent-card__primary-action {
  min-width: 0;
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid rgba(217, 119, 87, 0.28);
  background: rgba(217, 119, 87, 0.08);
  color: var(--mc-primary);
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}

.agent-card__primary-action:hover {
  background: rgba(217, 119, 87, 0.14);
  border-color: var(--mc-primary);
}

.agent-card__primary-action:disabled {
  opacity: 0.6;
  cursor: default;
}

.agent-default-tag {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}

.agent-card__actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}

.agent-card:hover .agent-card__actions {
  opacity: 1;
}

.toggle-switch--sm { width: 32px; height: 18px; }
.toggle-switch--sm .toggle-slider::before { width: 12px; height: 12px; }
.toggle-switch--sm input:checked + .toggle-slider::before { transform: translateX(14px); }

/* Context link card */
.context-link-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  cursor: pointer;
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  transition: all 0.15s;
}
.context-link-card:hover {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
}
.context-link-card__icon { font-size: 28px; }
.context-link-card__info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.context-link-card__title { font-size: 14px; font-weight: 600; color: var(--mc-text-primary); }
.context-link-card__desc { font-size: 12px; color: var(--mc-text-tertiary); }
.context-link-card__arrow { color: var(--mc-text-tertiary); flex-shrink: 0; }

.tag { padding: 2px 8px; border-radius: 10px; font-size: 11px; font-weight: 500; }
.type-tag { background: var(--mc-primary-bg); color: var(--mc-primary); }
.tag-item { background: var(--mc-bg-sunken); color: var(--mc-text-secondary); }
.tags-cell { display: flex; gap: 4px; flex-wrap: wrap; }
.time-label { font-size: 13px; color: var(--mc-text-tertiary); }
.text-muted { color: var(--mc-text-tertiary); }

.toggle-switch { position: relative; display: inline-block; width: 36px; height: 20px; cursor: pointer; flex-shrink: 0; }
.toggle-switch input { opacity: 0; width: 0; height: 0; }
.toggle-slider { position: absolute; inset: 0; background: var(--mc-border); border-radius: 20px; transition: 0.2s; }
.toggle-slider::before { content: ''; position: absolute; width: 14px; height: 14px; left: 3px; top: 3px; background: var(--mc-bg-elevated); border-radius: 50%; transition: 0.2s; }
.toggle-switch input:checked + .toggle-slider { background: var(--mc-primary); }
.toggle-switch input:checked + .toggle-slider::before { transform: translateX(16px); }

.action-btns { display: flex; gap: 4px; }
.action-btn { width: 30px; height: 30px; border: 1px solid var(--mc-border); background: var(--mc-bg-elevated); border-radius: 6px; display: flex; align-items: center; justify-content: center; cursor: pointer; color: var(--mc-text-secondary); transition: all 0.15s; }
.action-btn:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.action-btn.danger:hover { background: var(--mc-danger-bg); border-color: var(--mc-danger); color: var(--mc-danger); }

/* Empty state */
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80px 20px; text-align: center; }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h3 { font-size: 18px; font-weight: 600; color: var(--mc-text-primary); margin: 0 0 8px; }
.empty-state p { font-size: 14px; color: var(--mc-text-tertiary); margin: 0 0 24px; }

.deleted-agents {
  margin-top: 16px;
  padding: 16px;
}

.deleted-agents__header h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: var(--mc-text-primary);
}

.deleted-agents__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.deleted-agents__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--mc-border-light);
  border-radius: 10px;
}

.deleted-agents__meta {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.deleted-agents__name {
  color: var(--mc-text-primary);
  font-size: 13px;
}

/* Modal */
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 1000; padding: 20px; }
.modal { background: var(--mc-bg-elevated); border: 1px solid var(--mc-border); border-radius: 16px; width: 100%; max-width: 600px; max-height: 90vh; display: flex; flex-direction: column; box-shadow: 0 20px 60px rgba(0,0,0,0.15); }
.modal--wide { max-width: 760px; }
.modal-header { display: flex; align-items: center; justify-content: space-between; padding: 20px 24px; border-bottom: 1px solid var(--mc-border-light); }
.modal-header h2 { font-size: 18px; font-weight: 600; color: var(--mc-text-primary); margin: 0; }
.modal-close { width: 32px; height: 32px; border: none; background: none; cursor: pointer; color: var(--mc-text-tertiary); display: flex; align-items: center; justify-content: center; border-radius: 6px; }
.modal-close:hover { background: var(--mc-bg-sunken); color: var(--mc-text-primary); }
.modal-body { flex: 1; overflow-y: auto; padding: 20px 24px; }

/* Modal Tabs */
.modal-tabs { display: flex; gap: 4px; margin-bottom: 20px; border-bottom: 1px solid var(--mc-border-light); padding-bottom: 0; }
.modal-tab {
  padding: 8px 16px; border: none; background: none; cursor: pointer;
  font-size: 13px; font-weight: 500; color: var(--mc-text-tertiary);
  border-bottom: 2px solid transparent; margin-bottom: -1px; transition: all 0.15s;
  display: inline-flex; align-items: center; gap: 6px;
}
.modal-tab:hover { color: var(--mc-text-primary); }
.modal-tab.active { color: var(--mc-primary); border-bottom-color: var(--mc-primary); }
.tab-badge {
  display: inline-flex; align-items: center; justify-content: center;
  min-width: 18px; height: 18px; padding: 0 5px;
  border-radius: 9px; background: var(--mc-primary); color: white;
  font-size: 11px; font-weight: 600;
}

/* Binding Tab */
.binding-tab { min-height: 200px; }
.binding-tab--home { display: flex; flex-direction: column; }
.binding-hint { font-size: 13px; color: var(--mc-text-tertiary); margin: 0 0 16px; }
.binding-empty { padding: 40px; text-align: center; color: var(--mc-text-tertiary); font-size: 14px; }
.binding-list { display: flex; flex-direction: column; gap: 6px; }
.binding-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  border: 1px solid var(--mc-border-light); border-radius: 8px;
  cursor: pointer; transition: all 0.15s; background: var(--mc-bg);
}
.binding-item:hover { border-color: var(--mc-primary-light, rgba(217,119,87,0.3)); background: var(--mc-bg-elevated); }
.binding-item.selected { border-color: var(--mc-primary); background: rgba(217,119,87,0.04); }
.binding-checkbox { flex-shrink: 0; accent-color: var(--mc-primary); width: 16px; height: 16px; }
.binding-icon { font-size: 20px; flex-shrink: 0; }
.binding-info { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.binding-name { font-size: 14px; font-weight: 500; color: var(--mc-text-primary); }
.binding-desc { font-size: 12px; color: var(--mc-text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.binding-version { font-size: 11px; color: var(--mc-text-tertiary); flex-shrink: 0; }
.binding-type-badge {
  font-size: 10px; padding: 2px 6px; border-radius: 4px; flex-shrink: 0;
  background: var(--mc-bg-sunken); color: var(--mc-text-tertiary); text-transform: uppercase;
}

/* Provider preference list (RFC-009 PR-3) */
.provider-pref-list { display: flex; flex-direction: column; gap: 6px; }
.provider-pref-item {
  display: flex; align-items: center; gap: 10px; padding: 8px 12px;
  border: 1px solid var(--mc-border-light); border-radius: 8px; background: var(--mc-bg-elevated);
}
.provider-pref-rank {
  width: 22px; height: 22px; border-radius: 50%;
  display: inline-flex; align-items: center; justify-content: center;
  background: var(--mc-primary); color: white; font-size: 11px; font-weight: 700; flex-shrink: 0;
}
.provider-pref-name { font-size: 14px; color: var(--mc-text-primary); flex: 1; }
.provider-pref-id { font-size: 12px; color: var(--mc-text-tertiary); font-family: ui-monospace, monospace; }
.provider-pref-btn {
  border: 1px solid var(--mc-border-light); background: var(--mc-bg);
  width: 26px; height: 26px; border-radius: 6px; cursor: pointer;
  font-size: 12px; color: var(--mc-text-secondary);
}
.provider-pref-btn:disabled { opacity: 0.35; cursor: not-allowed; }
.provider-pref-btn:not(:disabled):hover { border-color: var(--mc-primary); color: var(--mc-primary); }
.provider-pref-btn.danger:not(:disabled):hover { border-color: var(--mc-danger); color: var(--mc-danger); }
.provider-pref-pool { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 6px; }
.provider-pref-pool .binding-hint { width: 100%; }
.provider-pref-add-btn {
  border: 1px dashed var(--mc-border); background: transparent;
  padding: 4px 10px; border-radius: 6px; font-size: 12px; cursor: pointer;
  color: var(--mc-text-secondary);
}
.provider-pref-add-btn:hover { border-color: var(--mc-primary); color: var(--mc-primary); border-style: solid; }

.knowledge-select {
  width: 100%;
}

.knowledge-option-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
  width: 100%;
  padding: 10px 12px;
}

.knowledge-option-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  overflow: hidden;
}

.knowledge-option-row .binding-icon {
  margin-top: 2px;
}

:deep(.knowledge-select .el-select__wrapper) {
  min-height: 44px;
  height: auto;
  padding-top: 6px;
  padding-bottom: 6px;
  border-radius: 12px;
  background: var(--mc-bg-sunken);
  box-shadow: 0 0 0 1px var(--mc-border) inset;
  align-items: center;
}

:deep(.knowledge-select .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--mc-primary) inset, 0 0 0 3px rgba(217,119,87,0.1);
}

:deep(.knowledge-select .el-select__selection) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  align-content: center;
  gap: 6px;
}

:deep(.knowledge-select .el-select__tags) {
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}

:deep(.knowledge-select .el-select__selected-item) {
  max-width: 100%;
}

.knowledge-selected-tag {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(217, 119, 87, 0.18);
  background: rgba(217, 119, 87, 0.08);
  color: var(--mc-primary);
  line-height: 1.35;
  white-space: normal;
  word-break: break-word;
}

:deep(.knowledge-select .el-select__input-wrapper) {
  flex: 1 0 96px;
  display: flex;
  align-items: center;
  min-height: 28px;
}

:deep(.knowledge-select .el-select__placeholder) {
  color: var(--mc-text-tertiary);
  display: inline-flex;
  align-items: center;
  min-height: 28px;
}

:deep(.knowledge-select .el-select__suffix) {
  display: inline-flex;
  align-items: center;
}

:deep(.knowledge-select .el-select__caret) {
  align-self: center;
}

:deep(.knowledge-select-popper .el-select-dropdown__item) {
  height: auto;
  line-height: normal;
  padding: 0;
  white-space: normal;
}

:deep(.knowledge-select-popper .el-select-dropdown__item.is-hovering),
:deep(.knowledge-select-popper .el-select-dropdown__item.hover) {
  background: var(--mc-bg-elevated);
}

:deep(.knowledge-select-popper .el-select-dropdown__item.is-selected) {
  color: inherit;
  font-weight: inherit;
}

:deep(.knowledge-select-popper .el-select-dropdown__item .binding-name) {
  line-height: 1.35;
}

:deep(.knowledge-select-popper .el-select-dropdown__item .binding-desc) {
  white-space: normal;
  overflow: hidden;
  text-overflow: unset;
  display: -webkit-box;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.35;
}

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.form-group { display: flex; flex-direction: column; gap: 6px; }
.form-group.full-width { grid-column: 1 / -1; }
.form-label { font-size: 13px; font-weight: 500; color: var(--mc-text-secondary); }
.form-input, .form-textarea { display: block; width: 100%; box-sizing: border-box; padding: 8px 12px; border: 1px solid var(--mc-border); border-radius: 8px; font-size: 14px; line-height: 1.5; color: var(--mc-text-primary); outline: none; transition: border-color 0.15s; background: var(--mc-bg-sunken); }
.form-input:focus, .form-textarea:focus { border-color: var(--mc-primary); box-shadow: 0 0 0 2px rgba(217,119,87,0.1); }
.form-textarea { resize: vertical; min-height: 92px; font-family: inherit; }
.form-hint { font-size: 12px; line-height: 1.5; color: var(--mc-text-tertiary); }
.modal-footer { display: flex; justify-content: flex-end; gap: 10px; padding: 16px 24px; border-top: 1px solid var(--mc-border-light); }

.home-settings { display: flex; flex-direction: column; gap: 16px; }
.home-config-panel { padding: 16px; border: 1px solid var(--mc-border-light); border-radius: 14px; background: linear-gradient(180deg, rgba(217,119,87,0.03), rgba(217,119,87,0.01)); }
.home-config-panel__hint { margin-bottom: 14px; }
.home-quick-starts { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; }
.home-quick-start-item { display: flex; flex-direction: column; gap: 14px; padding: 16px; border: 1px solid var(--mc-border); border-radius: 14px; background: var(--mc-bg-elevated); box-shadow: 0 10px 24px rgba(15, 23, 42, 0.04); }
.home-quick-start-item__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; font-size: 13px; font-weight: 600; color: var(--mc-text-secondary); }
.home-quick-start-item__title-block { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.home-quick-start-item__index { font-size: 14px; font-weight: 700; color: var(--mc-text-primary); }
.home-quick-start-item__desc { font-size: 12px; font-weight: 500; line-height: 1.4; color: var(--mc-text-tertiary); }
.home-quick-start-item__textarea { min-height: 120px; }
.home-actions { display: flex; justify-content: flex-end; }

@media (max-width: 760px) {
  .modal--wide {
    max-width: 100%;
  }
}

@media (max-width: 900px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-box {
    max-width: none;
  }
}

/* Template Selector */
.template-modal { max-width: 640px; }
.template-desc { font-size: 14px; color: var(--mc-text-secondary); margin: 0 0 18px; }

.template-grid { display: flex; flex-direction: column; gap: 10px; }

.template-card {
  display: grid; grid-template-columns: 52px minmax(0, 1fr); gap: 14px; padding: 16px; cursor: pointer;
  border: 1px solid var(--mc-border); border-radius: 12px; transition: all 0.15s;
}
.template-card:hover { border-color: var(--mc-primary); background: var(--mc-primary-bg); }
.template-card.applying { opacity: 0.5; pointer-events: none; }

.template-icon {
  width: 52px; height: 52px; display: flex; align-items: center; justify-content: center;
  background: var(--mc-bg-muted); border-radius: 14px; flex-shrink: 0;
  color: var(--mc-primary);
}

.template-content { min-width: 0; display: flex; flex-direction: column; gap: 10px; }
.template-header-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.template-info { flex: 1; min-width: 0; }
.template-name-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 4px; }
.template-name { font-size: 15px; font-weight: 600; color: var(--mc-text-primary); margin: 0 0 4px; }
.template-detail { font-size: 13px; color: var(--mc-text-secondary); margin: 0; line-height: 1.5; }
.template-badge {
  display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px;
  background: rgba(217,119,87,0.12); color: var(--mc-primary); font-size: 11px; font-weight: 600;
}

.template-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-chip { font-size: 11px; padding: 2px 8px; background: var(--mc-bg-sunken); color: var(--mc-text-tertiary); border-radius: 999px; white-space: nowrap; }
.template-rule-pack {
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-secondary);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.template-rule-pack__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--mc-text-secondary);
}
.template-rule-pack__head strong {
  color: var(--mc-text-primary);
  font-size: 13px;
}
.template-rule-pack__label {
  color: var(--mc-primary);
  font-weight: 600;
}
.template-rule-pack__detail {
  margin-left: auto;
  border: 0;
  background: transparent;
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 2px 0;
}
.template-rule-pack__detail:hover {
  text-decoration: underline;
}
.template-rule-pack__rules {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.template-rule-pack__rules span {
  font-size: 12px;
  line-height: 1.4;
  color: var(--mc-text-secondary);
  background: var(--mc-bg-primary);
  border: 1px solid var(--mc-border-light);
  border-radius: 6px;
  padding: 3px 7px;
}
.template-teacher-skills {
  background: var(--mc-bg-primary);
}
.template-skill-chip {
  font-size: 12px;
  line-height: 1.4;
  color: var(--mc-primary);
  background: rgba(217, 119, 87, 0.08);
  border: 1px solid rgba(217, 119, 87, 0.22);
  border-radius: 6px;
  padding: 3px 7px;
  cursor: pointer;
}
.template-skill-chip:hover {
  border-color: var(--mc-primary);
  background: rgba(217, 119, 87, 0.14);
}
.teacher-skill-binding-editor {
  border-top: 1px solid var(--mc-border-light);
  padding-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.teacher-skill-binding-option {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--mc-text-secondary);
  font-size: 12px;
  cursor: pointer;
}
.teacher-skill-binding-option input {
  width: 14px;
  height: 14px;
  accent-color: var(--mc-primary);
}
.teacher-skill-binding-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.template-health {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(230, 162, 60, 0.12);
  color: #b7791f;
  font-size: 11px;
  font-weight: 600;
}
.template-health.ready {
  background: rgba(34, 197, 94, 0.12);
  color: #15803d;
}
.template-health-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.template-checks {
  font-size: 11px;
  color: var(--mc-text-tertiary);
  line-height: 1.4;
}
.template-actions-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.template-sync-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(217, 119, 87, 0.28);
  background: rgba(217, 119, 87, 0.08);
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s;
}
.template-sync-btn:hover {
  background: rgba(217, 119, 87, 0.14);
  border-color: var(--mc-primary);
}
.template-sync-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.template-mock-task-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 6px;
  margin-top: 4px;
}
.template-mock-task-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 30px;
  padding: 6px 9px;
  border-radius: 7px;
  border: 1px solid var(--mc-border);
  background: var(--mc-bg);
  color: var(--mc-text-secondary);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
}
.template-mock-task-btn:hover {
  color: var(--mc-primary);
  border-color: rgba(217, 119, 87, 0.32);
  background: rgba(217, 119, 87, 0.07);
}
.template-mock-task-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.template-mock-task-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border-radius: 50%;
  background: rgba(217, 119, 87, 0.1);
  color: var(--mc-primary);
  font-size: 11px;
  font-weight: 700;
}
.template-mock-task-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rule-pack-modal {
  max-width: 860px;
}
.rule-pack-kicker {
  margin: 0 0 4px;
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
}
.rule-pack-override-state {
  margin: 4px 0 0;
  color: #b7791f;
  font-size: 12px;
  font-weight: 600;
}
.rule-pack-mini-state {
  color: #b7791f;
  font-size: 12px;
  font-weight: 600;
}
.rule-pack-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.rule-pack-editor {
  border: 1px solid rgba(217, 119, 87, 0.22);
  border-radius: 8px;
  background: rgba(217, 119, 87, 0.06);
  padding: 12px;
}
.rule-pack-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.rule-pack-editor__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.rule-pack-json-editor {
  width: 100%;
  min-height: 220px;
  resize: vertical;
  border: 1px solid var(--mc-border);
  border-radius: 8px;
  background: var(--mc-bg-primary);
  color: var(--mc-text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.5;
  padding: 10px;
}
.rule-pack-form-editor {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.rule-pack-form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}
.rule-pack-form-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rule-pack-form-block h4 {
  margin: 0;
  color: var(--mc-text-primary);
  font-size: 13px;
}
.rule-pack-question-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rule-pack-question-edit-item {
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-primary);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rule-pack-checkboxes {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  color: var(--mc-text-secondary);
  font-size: 12px;
}
.rule-pack-checkboxes label,
.rule-pack-hard-edit-item {
  display: flex;
  align-items: center;
  gap: 7px;
}
.rule-pack-checkboxes input {
  accent-color: var(--mc-primary);
}
.rule-pack-hard-edit-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rule-pack-hard-edit-item .form-input {
  flex: 1;
}

.rule-pack-source-edit-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-sunken);
}
.rule-pack-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rule-pack-section h3 {
  margin: 0;
  font-size: 15px;
  color: var(--mc-text-primary);
}
.rule-pack-mix,
.rule-pack-flags,
.rule-pack-acceptance {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.rule-pack-mix span,
.rule-pack-flags span {
  border: 1px solid var(--mc-border-light);
  border-radius: 999px;
  background: var(--mc-bg-secondary);
  color: var(--mc-text-secondary);
  font-size: 12px;
  padding: 4px 9px;
}
.rule-pack-rule-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}
.rule-pack-rule-item {
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-secondary);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rule-pack-rule-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.rule-pack-rule-item__head strong {
  color: var(--mc-text-primary);
  font-size: 14px;
}
.rule-pack-rule-item__head span {
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 700;
}
.rule-pack-rule-item p {
  margin: 0;
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}
.rule-pack-hard-rules {
  margin: 0;
  padding-left: 18px;
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.teacher-skill-purpose {
  margin: 0;
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.teacher-skill-steps {
  margin: 0;
  padding-left: 20px;
  color: var(--mc-text-secondary);
  font-size: 13px;
  line-height: 1.7;
}
.rule-pack-acceptance__item {
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-secondary);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: calc(50% - 4px);
  min-width: 220px;
}
.rule-pack-acceptance__item strong {
  color: var(--mc-text-primary);
  font-size: 13px;
}
.rule-pack-acceptance__item span {
  color: var(--mc-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.teacher-improvement-panel {
  border-color: rgba(37, 99, 235, 0.24);
}
.teacher-improvement-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.teacher-improvement-input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-primary);
  color: var(--mc-text-primary);
  font-size: 12px;
  padding: 7px 9px;
}
.teacher-improvement-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.teacher-improvement-item {
  border: 1px solid var(--mc-border-light);
  border-radius: 8px;
  background: var(--mc-bg-primary);
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.teacher-improvement-item > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.teacher-improvement-item strong {
  color: var(--mc-text-primary);
  font-size: 13px;
}
.teacher-improvement-item span,
.teacher-improvement-item p {
  margin: 0;
  color: var(--mc-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.teacher-improvement-item__signals {
  margin: 0;
  padding-left: 16px;
  color: var(--mc-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.teacher-improvement-item__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .modal-body {
    padding: 18px;
  }

  .modal-footer,
  .modal-header {
    padding-left: 18px;
    padding-right: 18px;
  }

  .home-quick-starts {
    grid-template-columns: 1fr;
  }

  .template-card {
    grid-template-columns: 1fr;
  }

  .template-icon {
    width: 44px;
    height: 44px;
  }
}
</style>
