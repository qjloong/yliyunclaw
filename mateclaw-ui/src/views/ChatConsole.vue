<template>
  <div class="mc-page-shell chat-console-shell">
    <div class="mc-page-frame chat-console-frame">
      <div class="chat-layout mc-surface-card">
        <!-- 移动端会话面板遮罩 -->
        <Transition name="fade">
          <div v-if="isMobile && convPanelOpen" class="conv-backdrop" @click="convPanelOpen = false"></div>
        </Transition>
        <Transition name="fade">
          <div v-if="isMobile && projectChangesPanelOpen" class="project-changes-backdrop" @click="projectChangesPanelOpen = false"></div>
        </Transition>

    <!-- 会话侧边栏 -->
    <div class="conversation-panel" :class="{ 'mobile-open': convPanelOpen, 'conv-collapsed': convPanelCollapsed && !isMobile }">
      <div class="panel-header">
        <div v-if="!convPanelCollapsed || isMobile" class="panel-header-copy">
          <div class="panel-kicker">{{ $t('nav.chat') }}</div>
          <h2 class="panel-title">{{ $t('chat.conversations') }}</h2>
        </div>
        <button class="new-chat-btn" @click="newConversation" :title="`${$t('chat.newChat')} (⌘N)`">
          <el-icon><Plus /></el-icon>
        </button>
      </div>
      <!-- 折叠切换按钮 -->
      <button v-if="!isMobile" class="conv-collapse-btn" @click="toggleConvPanel" :title="convPanelCollapsed ? $t('common.expandSidebar') : $t('common.collapseSidebar')">
        <svg v-if="!convPanelCollapsed" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="15 18 9 12 15 6"/></svg>
        <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
      </button>

      <div class="agent-selector">
        <button class="agent-select-trigger" @click="agentDropdownOpen = !agentDropdownOpen" :title="`${$t('chat.selectAgent')} (⌘K)`">
          <AgentIcon class="agent-select-trigger__icon" :value="currentAgent?.icon" :size="20" fallback="🤖" :title="currentAgent?.name || $t('chat.selectAgent')" />
          <span v-if="!convPanelCollapsed || isMobile" class="agent-select-trigger__name">{{ currentAgent?.name || $t('chat.selectAgent') }}</span>
          <svg v-if="!convPanelCollapsed || isMobile" class="agent-select-trigger__arrow" :class="{ open: agentDropdownOpen }" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
        </button>
        <Transition name="fade">
          <div v-if="agentDropdownOpen" class="agent-dropdown-backdrop" @click="agentDropdownOpen = false"></div>
        </Transition>
        <Transition name="agent-dropdown">
          <div v-if="agentDropdownOpen" class="agent-dropdown">
            <div
              v-for="agent in agents"
              :key="agent.id"
              class="agent-dropdown-item"
              :class="{ active: String(agent.id) === String(selectedAgentId) }"
              @click="selectAgent(agent)"
            >
              <AgentIcon class="agent-dropdown-item__icon" :value="agent.icon" :size="26" fallback="🤖" :title="agent.name" />
              <div class="agent-dropdown-item__info">
                <span class="agent-dropdown-item__name">{{ agent.name }}</span>
                <span class="agent-dropdown-item__desc">{{ agent.description || agent.agentType }}</span>
                <div v-if="agentTemplateBadge(agent)" class="agent-dropdown-item__meta">
                  <span class="agent-dropdown-item__tag">{{ agentTemplateBadge(agent) }}</span>
                </div>
              </div>
              <span v-if="String(agent.id) === String(selectedAgentId)" class="agent-dropdown-item__check">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
              </span>
            </div>
            <div v-if="agentsLoading || agentProvisioning" class="agent-dropdown-empty">{{ agentProvisioning ? $t('chat.preparingDefaultAgent') : $t('chat.loadingAgents') }}</div>
            <div v-else-if="agents.length === 0" class="agent-dropdown-empty">{{ $t('chat.noAgentsInWorkspace') }}</div>
          </div>
        </Transition>
      </div>

      <div class="conversation-list">
        <div v-for="group in groupedConversations" :key="group.label" class="conv-group-block">
          <div v-if="!convPanelCollapsed || isMobile" class="conv-group-title">{{ group.label }}</div>
          <div
            v-for="conv in group.items"
            :key="conv.conversationId"
            class="conv-item"
            :class="{
              active: currentConversationId === conv.conversationId,
              'is-running': conv.streamStatus === 'running',
            }"
            @click="selectConversation(conv)"
          >
            <div class="conv-icon">
              <img :src="channelIconUrl(conv.source)" width="14" height="14" alt="" />
              <span
                v-if="conv.streamStatus === 'running'"
                class="conv-running-dot"
                :title="$t('chat.streamGenerating')"
              ></span>
            </div>
            <div v-if="!convPanelCollapsed || isMobile" class="conv-info">
              <input
                v-if="renamingConvId === conv.conversationId"
                v-model="renameText"
                class="conv-title-input"
                @keydown.enter="confirmRename(conv)"
                @keydown.escape="cancelRename"
                @blur="confirmRename(conv)"
                @click.stop
                ref="renameInputRef"
              />
              <div v-else class="conv-title" @dblclick.stop="startRename(conv)">
                <span>{{ conv.title }}</span>
                <span
                  v-if="conv.streamStatus === 'running'"
                  class="conv-running-badge"
                  :title="$t('chat.streamGenerating')"
                >
                  <span class="conv-running-badge-pulse"></span>
                  {{ $t('chat.streamGenerating') }}
                </span>
              </div>
              <div class="conv-meta">
                <span>{{ $t('chat.messages', { count: conv.messageCount }) }}</span>
                <span class="conv-dot">·</span>
                <span>{{ formatConversationTime(conv.lastActiveTime) }}</span>
              </div>
              <div class="conv-project-row">
                <span class="conv-project-tag" :title="formatConversationProjectLabel(conv)">
                  📁 {{ formatConversationProjectLabel(conv) }}
                </span>
              </div>
            </div>
            <button v-if="!convPanelCollapsed || isMobile" class="conv-delete" @click.stop="confirmDeleteConversation(conv.conversationId)" :title="$t('common.delete')">
              <el-icon><Delete /></el-icon>
            </button>
          </div>
        </div>

        <div v-if="conversations.length === 0" class="empty-convs">
          <p>{{ workspaceStore.currentWorkspace ? $t('chat.noConversationsInWorkspace', { workspace: currentWorkspaceDisplayLabel }) : $t('chat.noConversations') }}</p>
          <p>{{ $t('chat.startNewChat') }}</p>
        </div>
      </div>
    </div>

    <!-- 主聊天区域 -->
    <div
      class="chat-area"
      @dragenter.prevent="onDragEnter"
      @dragover.prevent
      @dragleave="onDragLeave"
      @drop.prevent="onDrop"
    >
      <!-- 拖拽上传遮罩 -->
      <Transition name="fade">
        <div v-if="isDragging" class="drop-overlay">
          <div class="drop-overlay__content">
            <el-icon><UploadFilled /></el-icon>
            <span>{{ $t('chat.dropToUpload') }}</span>
          </div>
        </div>
      </Transition>
      <!-- 头部 -->
      <div class="chat-header">
        <div class="chat-header-left">
          <button v-if="isMobile" class="conv-toggle-btn" @click="convPanelOpen = !convPanelOpen" :title="$t('chat.conversations')">
            <el-icon><ChatDotRound /></el-icon>
          </button>
          <div class="chat-stage-copy" v-if="currentAgent">
            <div class="chat-stage-kicker">{{ $t('nav.chat') }}</div>
            <div class="agent-badge" :title="currentAgent.name">
              <AgentIcon class="agent-badge-icon" :value="currentAgent.icon" :size="20" fallback="🤖" :title="currentAgent.name" />
              <span class="agent-badge-name">{{ currentAgent.name }}</span>
              <span class="agent-badge-type">{{ currentAgent.agentType === 'react' ? 'ReAct' : 'Plan-Execute' }}</span>
              <span v-if="currentAgentTemplateBadge" class="agent-badge-template">{{ currentAgentTemplateBadge }}</span>
              <span class="agent-badge-mode">{{ currentRuntimeModeLabel }}</span>
              <span class="status-dot" :class="connectionStatusClass" :title="connectionStatusLabel"></span>
            </div>
          </div>
          <div v-else class="no-agent-hint">{{ agentProvisioning ? $t('chat.preparingDefaultAgent') : $t('chat.selectAgent') }}</div>
        </div>
        <div class="chat-header-right">
          <button class="header-btn" :class="{ active: projectChangesPanelOpen }" @click="toggleProjectChangesPanel" :title="$t('chat.projectChangesTitle')">
            <el-icon><Document /></el-icon>
          </button>
          <!-- Model selector -->
          <ModelSelector
            v-if="eligibleModels.length > 0"
            :providers="availableProviders"
            :active-value="activeModelValue"
            :active-label="activeModelLabel"
            :saving="modelSaving"
            @select="selectModel"
          />
          <button v-else-if="isAdmin" class="header-btn" @click="goToModelSettings" :title="$t('chat.configModel')">
            <el-icon><Setting /></el-icon>
          </button>
          <!-- Overflow menu -->
          <div class="header-overflow-wrap">
            <button class="header-btn" @click="headerMenuOpen = !headerMenuOpen" :title="$t('common.more') || 'More'">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="1.5"/><circle cx="12" cy="12" r="1.5"/><circle cx="12" cy="19" r="1.5"/></svg>
            </button>
            <Transition name="fade">
              <div v-if="headerMenuOpen" class="header-menu-backdrop" @click="headerMenuOpen = false"></div>
            </Transition>
            <Transition name="agent-dropdown">
              <div v-if="headerMenuOpen" class="header-menu">
                <button v-if="isAdmin" class="header-menu-item" @click="headerMenuOpen = false; goToModelSettings()">
                  <el-icon><Setting /></el-icon>
                  <span>{{ $t('chat.configModel') }}</span>
                </button>
                <div v-if="isAdmin" class="header-menu-divider"></div>
                <button class="header-menu-item header-menu-item--danger" @click="handleClearMessages">
                  <el-icon><Delete /></el-icon>
                  <span>{{ $t('chat.clearMessages') }}</span>
                </button>
              </div>
            </Transition>
          </div>
        </div>
      </div>

      <div v-if="isCurrentConversationAgentDeleted" class="conversation-readonly-alert">
        <div class="conversation-readonly-alert__title">{{ $t('chat.deletedAgentReadonlyTitle') }}</div>
        <div class="conversation-readonly-alert__desc">{{ $t('chat.deletedAgentReadonlyHint') }}</div>
      </div>

      <!-- 使用组件化的 MessageList -->
      <MessageList
        ref="messageListRef"
        :messages="messages"
        :loading="isGenerating"
        :assistant-icon="currentAgent?.icon || '🤖'"
        :user-icon="userInitial"
        :title="showModelPrompt ? modelPromptTitle : $t('app.title')"
        :subtitle="showModelPrompt ? modelPromptDesc : chatHomeSubtitle"
        :suggestions="showModelPrompt ? [] : suggestions"
        @regenerate="handleRegenerate"
        @suggestion-click="sendSuggestion"
        @toggle-thinking="handleToggleThinking"
        @approve="handleApprove"
        @deny="handleDeny"
      >
        <!-- 自定义模型提示空状态 -->
        <template v-if="showModelPrompt" #empty>
          <div class="model-prompt">
            <div class="model-prompt-title">{{ modelPromptTitle }}</div>
            <div class="model-prompt-desc">{{ modelPromptDesc }}</div>
            <button v-if="isAdmin" class="btn-primary" @click="goToModelSettings">{{ $t('chat.goToModelSettings') }}</button>
          </div>
        </template>
        <template v-else-if="shouldShowChatEntry" #empty>
          <div class="chat-entry-panel">
            <div class="chat-entry-panel__hero">
              <div class="chat-entry-panel__kicker">{{ currentWorkspaceDisplayLabel }}</div>
              <div class="chat-entry-panel__title">{{ $t('chat.entryTitle') }}</div>
              <div class="chat-entry-panel__subtitle">{{ $t('chat.entrySubtitle') }}</div>
              <div class="chat-entry-panel__actions">
                <button
                  class="chat-entry-panel__primary"
                  type="button"
                  :disabled="!currentAgent && starterEntryAgents.length === 0"
                  @click="beginChatEntry(currentAgent || starterEntryAgents[0])"
                >
                  {{ $t('chat.entryPrimaryStart', { agent: currentAgent?.name || starterEntryAgents[0]?.name || $t('chat.selectAgent') }) }}
                </button>
                <button class="chat-entry-panel__secondary" type="button" @click="openAgentStudio">
                  {{ $t('chat.entryManageAgents') }}
                </button>
              </div>
            </div>

            <div class="chat-entry-section">
              <div class="chat-entry-section__title">{{ $t('chat.entryAgentsTitle') }}</div>
              <div class="chat-entry-section__hint">{{ $t('chat.entryAgentsHint') }}</div>
              <div class="chat-entry-agent-grid">
                <button
                  v-for="agent in starterEntryAgents"
                  :key="agent.id"
                  class="chat-entry-agent-card"
                  :class="{ 'is-active': String(agent.id) === String(selectedAgentId) }"
                  type="button"
                  @click="beginChatEntry(agent)"
                >
                  <div class="chat-entry-agent-card__header">
                    <AgentIcon :value="agent.icon" :size="24" fallback="🤖" :title="agent.name" />
                    <span class="chat-entry-agent-card__type">{{ agent.agentType === 'react' ? 'ReAct' : 'Plan-Execute' }}</span>
                  </div>
                  <div class="chat-entry-agent-card__name">{{ agent.name }}</div>
                  <div class="chat-entry-agent-card__desc">{{ agent.description || agent.agentType }}</div>
                  <div v-if="agentTemplateBadge(agent)" class="chat-entry-agent-card__meta">
                    <span class="chat-entry-agent-card__pill">{{ agentTemplateBadge(agent) }}</span>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </template>
      </MessageList>

      <!-- 流式处理 Loading 栏（消息和输入框之间） -->
      <StreamLoadingBar
        :is-loading="isGenerating && !showModelPrompt"
        :tool-count="toolCallCount"
        :completion-tokens="currentGeneratingTokens"
        :prompt-tokens="currentPromptTokens"
        :phase="streamPhase"
        :phase-info="phaseInfo"
        :running-tool-name="currentRunningToolName"
        :has-queued="hasQueued"
      />

      <Transition name="fade">
        <div v-if="anyComposerMenuOpen" class="composer-menu-backdrop" @click="closeComposerMenus"></div>
      </Transition>

      <!-- 使用组件化的 ChatInput -->
      <section v-if="activeTemplateMockTask" class="mock-task-card">
        <div class="mock-task-card__collapsed">
          <div class="mock-task-card__collapsed-text" :title="mockTaskCollapsedLine">
            {{ mockTaskCollapsedLine }}
          </div>
          <div class="mock-task-card__actions">
            <button class="mock-task-card__toggle" type="button" @click="mockTaskDetailsExpanded = !mockTaskDetailsExpanded">
              {{ mockTaskDetailsExpanded ? $t('chat.mockTask.hideDetails') : $t('chat.mockTask.showDetails') }}
            </button>
            <button class="mock-task-card__close" type="button" :title="$t('common.close')" @click="activeTemplateMockTask = null">×</button>
          </div>
        </div>
        <div v-if="mockTaskDetailsExpanded" class="mock-task-card__details">
          <div class="mock-task-card__main">
            <div class="mock-task-card__kicker">{{ $t('chat.mockTask.kicker') }}</div>
            <div class="mock-task-card__title">{{ activeTemplateMockTask.title }}</div>
            <div class="mock-task-card__meta">
              <span>{{ activeTemplateMockTask.templateName }}</span>
              <span v-if="activeTemplateMockTask.expected.length">{{ $t('chat.mockTask.expectedCount', { count: activeTemplateMockTask.expected.length }) }}</span>
            </div>
            <div v-if="mockTaskAssessment" class="mock-task-card__assessment">
              <span class="mock-task-card__assessment-badge" :class="`is-${mockTaskAssessment.status || 'running'}`">
                {{ $t(`chat.mockTask.status.${mockTaskAssessment.status || 'running'}`) }}
              </span>
              <span class="mock-task-card__assessment-score">
                {{ mockTaskScoreLabel }}
              </span>
            </div>
            <div class="mock-task-card__summary">
              {{ mockTaskCompactSummary }}
            </div>
          </div>
            <div v-if="mockTaskEvidence" class="mock-task-card__preview mock-task-card__preview--evidence">
              <div class="mock-task-card__preview-label">{{ $t('chat.mockTask.runEvidenceLabel') }}</div>
              <div class="mock-task-card__preview-content">{{ mockTaskEvidence }}</div>
            </div>
            <div v-if="mockTaskGateBlockers.length" class="mock-task-card__gate-blockers">
              <div class="mock-task-card__preview-label">{{ $t('chat.mockTask.gateBlockersLabel') }}</div>
              <ul class="mock-task-card__signal-list">
                <li v-for="item in mockTaskGateBlockers" :key="`gate-${item}`">{{ item }}</li>
              </ul>
            </div>
            <ul v-if="activeTemplateMockTask.expected.length" class="mock-task-card__checks">
              <li
                v-for="item in activeTemplateMockTask.expected"
                :key="item"
                :class="{ 'is-matched': mockTaskMatchedItems.includes(item), 'is-missing': mockTaskMissingItems.includes(item) }"
              >
                {{ item }}
              </li>
            </ul>
            <div v-if="mockTaskAssessmentEvidence.length" class="mock-task-card__signals">
              <div class="mock-task-card__preview-label">{{ $t('chat.mockTask.signalLabel') }}</div>
              <ul class="mock-task-card__signal-list">
                <li v-for="item in mockTaskAssessmentEvidence" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div v-if="mockTaskHasStructuredEvidence" class="mock-task-card__signals mock-task-card__signals--structured">
              <div class="mock-task-card__preview-label">{{ $t('chat.mockTask.structuredEvidenceLabel') }}</div>
              <div v-if="mockTaskValidationCommands.length" class="mock-task-card__signal-group">
                <div class="mock-task-card__signal-group-title">{{ $t('chat.mockTask.validationCommandsLabel') }}</div>
                <ul class="mock-task-card__signal-list">
                  <li v-for="item in mockTaskValidationCommands" :key="`cmd-${item}`">{{ item }}</li>
                </ul>
              </div>
              <div v-if="mockTaskValidationResults.length" class="mock-task-card__signal-group">
                <div class="mock-task-card__signal-group-title">{{ $t('chat.mockTask.validationResultsLabel') }}</div>
                <ul class="mock-task-card__signal-list">
                  <li v-for="item in mockTaskValidationResults" :key="`result-${item}`">{{ item }}</li>
                </ul>
              </div>
              <div v-if="mockTaskDiffSummary || mockTaskDiffFiles.length" class="mock-task-card__signal-group">
                <div class="mock-task-card__signal-group-title">{{ $t('chat.mockTask.diffLabel') }}</div>
                <div v-if="mockTaskDiffSummary" class="mock-task-card__signal-summary">{{ mockTaskDiffSummary }}</div>
                <ul v-if="mockTaskDiffFiles.length" class="mock-task-card__signal-list">
                  <li v-for="item in mockTaskDiffFiles" :key="`diff-${item}`">{{ item }}</li>
                </ul>
              </div>
              <div v-if="mockTaskRetrievedSources.length || mockTaskCitedSources.length" class="mock-task-card__signal-group">
                <div class="mock-task-card__signal-group-title">{{ $t('chat.mockTask.sourcesLabel') }}</div>
                <div v-if="mockTaskRetrievedSources.length" class="mock-task-card__signal-subtitle">{{ $t('chat.mockTask.retrievedSourcesLabel') }}</div>
                <ul v-if="mockTaskRetrievedSources.length" class="mock-task-card__signal-list">
                  <li v-for="item in mockTaskRetrievedSources" :key="`retrieved-${item}`">{{ item }}</li>
                </ul>
                <div v-if="mockTaskCitedSources.length" class="mock-task-card__signal-subtitle">{{ $t('chat.mockTask.citedSourcesLabel') }}</div>
                <ul v-if="mockTaskCitedSources.length" class="mock-task-card__signal-list">
                  <li v-for="item in mockTaskCitedSources" :key="`cited-${item}`">{{ item }}</li>
                </ul>
              </div>
            </div>
            <div v-if="mockTaskFinalAnswerPreview" class="mock-task-card__preview">
              <div class="mock-task-card__preview-label">{{ $t('chat.mockTask.previewLabel') }}</div>
              <div class="mock-task-card__preview-content">{{ mockTaskFinalAnswerPreview }}</div>
            </div>
        </div>
      </section>

      <section v-if="visibleTeacherGuideCard" class="teacher-guide-card">
        <div class="teacher-guide-card__header">
          <div class="teacher-guide-card__copy">
            <div class="teacher-guide-card__kicker">{{ teacherGuideKicker }}</div>
            <p class="teacher-guide-card__summary">{{ teacherGuideCompactSummary }}</p>
          </div>
          <div class="teacher-guide-card__actions">
            <button class="teacher-guide-card__toggle" type="button" @click="teacherGuideExpanded = !teacherGuideExpanded">
              {{ teacherGuideExpanded ? teacherGuideHideRulesLabel : teacherGuideShowRulesLabel }}
            </button>
            <button class="teacher-guide-card__dismiss" type="button" :title="$t('chat.teacherGuide.hideBanner')" @click="dismissTeacherGuide">
              ×
            </button>
          </div>
        </div>

        <div v-if="teacherGuideExpanded" class="teacher-guide-card__section teacher-guide-card__section--details">
          <p class="teacher-guide-card__detail">{{ teacherGuideSummary }}</p>
        </div>

        <div v-if="teacherGuideExpanded" class="teacher-guide-card__section teacher-guide-card__section--rules">
          <div class="teacher-guide-card__label">{{ teacherGuideRulesTitle }}</div>
          <ul class="teacher-guide-card__rules">
            <li v-for="rule in teacherGuideRules" :key="rule">{{ rule }}</li>
          </ul>
        </div>
      </section>

      <ChatInput
        ref="chatInputRef"
        v-model="inputText"
        :loading="isGenerating && !hasPendingApproval"
        :disabled="chatInputDisabled"
        :placeholder="$t('chat.messagePlaceholder')"
        :hint="chatInputHint"
        :attachments="pendingAttachments"
        :uploading="uploadingAttachment"
        :max-length="10240"
        :pending-approval="activePendingApproval"
        :stream-phase="streamPhase"
        :queued-message="queuedMessage"
        :queue-size="queueSize"
        :shortcut-commands="chatShortcutCommands"
        :shortcut-mentions="chatShortcutMentions"
        :execution-selection="pendingExecutionSelection"
        @submit="handleSendMessage"
        @stop="handleStopStream"
        @cancel-queued="handleCancelQueued"
        @file-select="handleFileSelect"
        @attachment-remove="removeAttachment"
        @approve="handleApprove"
        @deny="handleDeny"
        :enable-talk-mode="!!selectedAgentId && !agentProvisioning && !isCurrentConversationAgentDeleted"
        :thinking-enabled="thinkingEnabled"
        :thinking-supported="currentModelSupportsThinking"
        @shortcut-select="handleShortcutSelect"
        @clear-execution-selection="pendingExecutionSelection = null"
        @toggle-thinking="thinkingEnabled = !thinkingEnabled"
        @talk="showTalkMode = true"
      >
        <template #footer-left>
          <div class="composer-footer-left">
            <button
              v-if="showTeacherGuideRestore"
              class="teacher-guide-restore"
              type="button"
              @click="teacherGuideDismissed = false"
            >
              {{ $t('chat.teacherGuide.showBanner') }}
            </button>
            <div class="composer-menu-wrap">
              <button
                class="composer-icon-btn"
                :class="{ active: composerMenuOpen }"
                :title="$t('chat.composer.addMenu')"
                @click.stop="toggleComposerMenu()"
              >
                <span class="composer-icon-btn__plus">+</span>
              </button>
              <div v-if="composerMenuOpen" class="composer-popover composer-popover--menu">
                <button class="composer-menu-item" @click="handleComposerAttach">
                  <span class="composer-menu-item__icon">📎</span>
                  <span>{{ $t('chat.composer.addFiles') }}</span>
                </button>
                <button class="composer-menu-item composer-menu-item--toggle" @click="selectComposerRuntimeMode('plan')">
                  <span class="composer-menu-item__icon">≋</span>
                  <span>{{ $t('chat.composer.planMode') }}</span>
                  <span class="composer-switch" :class="{ 'is-on': currentRuntimeMode === 'plan' }">
                    <span class="composer-switch__thumb"></span>
                  </span>
                </button>
                <button v-if="currentAgentSupportsCodingMode" class="composer-menu-item composer-menu-item--toggle" @click="selectComposerRuntimeMode('coding')">
                  <span class="composer-menu-item__icon">⌥</span>
                  <span>{{ $t('chat.composer.codingMode') }}</span>
                  <span class="composer-switch" :class="{ 'is-on': currentRuntimeMode === 'coding' }">
                    <span class="composer-switch__thumb"></span>
                  </span>
                </button>
                <button v-if="isAdmin" class="composer-menu-item" @click="openPluginsPage">
                  <span class="composer-menu-item__icon">⌘</span>
                  <span>{{ $t('chat.composer.plugins') }}</span>
                  <span class="composer-menu-item__arrow">›</span>
                </button>
              </div>
            </div>

            <div class="composer-menu-wrap" v-if="isAdmin && workspaceStore.currentWorkspace">
              <button
                class="composer-pill-btn"
                :class="{ active: permissionMenuOpen, 'is-full-access': projectPermissionMode === 'full' }"
                @click.stop="togglePermissionMenu()"
              >
                <span>{{ currentPermissionButtonLabel }}</span>
                <span class="composer-pill-btn__arrow">⌄</span>
              </button>
              <div v-if="permissionMenuOpen" class="composer-popover composer-popover--list">
                <!-- 暂时隐藏提示 -->
                <!-- <div class="composer-popover__hint composer-popover__hint--compact">
                  {{ $t('chat.composer.permissionBoundaryHint') }}
                </div> -->
                <button
                  class="composer-option"
                  :class="{ active: projectPermissionMode === 'limited' }"
                  :disabled="permissionSaving"
                  @click="updateCurrentWorkspacePermission('limited')"
                >
                  <span class="composer-option__content">
                    <span>{{ $t('chat.composer.defaultPermissions') }}</span>
                    <span class="composer-option__desc">{{ $t('chat.composer.defaultPermissionsHint') }}</span>
                  </span>
                  <span v-if="projectPermissionMode === 'limited'" class="composer-option__check">✓</span>
                </button>
                <button
                  class="composer-option"
                  :class="{ active: projectPermissionMode === 'full', 'composer-option--danger': true }"
                  :disabled="permissionSaving"
                  @click="updateCurrentWorkspacePermission('full')"
                >
                  <span class="composer-option__content">
                    <span>{{ $t('chat.composer.fullAccess') }}</span>
                    <span class="composer-option__desc">{{ $t('chat.composer.fullAccessHint') }}</span>
                  </span>
                  <span v-if="projectPermissionMode === 'full'" class="composer-option__check">✓</span>
                </button>
              </div>
            </div>

            <div v-if="workspaceStore.currentWorkspace" class="composer-scope-actions">
              <div class="composer-menu-wrap">
                <button
                  class="composer-workspace-chip"
                  :class="{ active: workspaceMenuOpen }"
                  @click.stop="toggleWorkspaceMenu()"
                >
                  <span class="composer-workspace-chip__icon">🗂️</span>
                  <span class="composer-workspace-chip__label" :title="currentWorkspaceDisplayLabel">
                    {{ currentWorkspaceDisplayLabel }}
                  </span>
                  <span class="composer-workspace-chip__arrow">⌄</span>
                </button>
                <div v-if="workspaceMenuOpen" class="composer-popover composer-popover--workspace">
                  <div class="composer-popover__section">
                    <div class="composer-popover__label">{{ $t('chat.composer.workspacePopoverTitle') }}</div>
                    <button
                      v-if="workspaceFolderPickerAvailable"
                      class="composer-menu-item"
                      :disabled="workspaceCreating"
                      @click="triggerWorkspaceFolderPicker"
                    >
                      <span class="composer-menu-item__icon">🗂️</span>
                      <span>{{ workspaceCreating ? $t('common.loading') : $t('chat.composer.pickWorkspaceFolder') }}</span>
                    </button>
                    <div v-else class="composer-popover__hint">
                      {{ $t('chat.composer.workspaceFolderClientOnly') }}
                    </div>
                    <button v-if="isAdmin" class="composer-menu-item" @click="openWorkspaceSettings">
                      <span class="composer-menu-item__icon">⚙️</span>
                      <span>{{ $t('chat.composer.workspaceSettings') }}</span>
                      <span class="composer-menu-item__arrow">›</span>
                    </button>
                  </div>
                  <div class="composer-popover__section">
                    <div class="composer-popover__label">{{ $t('chat.composer.workspaceSection') }}</div>
                    <div v-if="switchableWorkspaces.length" class="composer-workspace-list">
                      <button
                        v-for="workspace in switchableWorkspaces"
                        :key="workspace.id"
                        class="composer-workspace-item"
                        :class="{ active: workspace.id === workspaceStore.currentWorkspaceId }"
                        :disabled="workspaceSwitching"
                        @click="handleWorkspaceSwitch(workspace.id)"
                      >
                        <span class="composer-workspace-item__name">{{ workspace.name }}</span>
                        <span v-if="workspace.basePath?.trim()" class="composer-workspace-item__path">{{ workspace.basePath }}</span>
                      </button>
                    </div>
                    <div v-else class="composer-popover__hint">
                      {{ $t('chat.composer.workspaceListEmpty') }}
                    </div>
                  </div>
                </div>
              </div>

            </div>
            <input
              ref="workspaceFolderInputRef"
              class="workspace-folder-input"
              type="file"
              webkitdirectory
              directory
              multiple
              @change="handleWorkspaceFolderSelected"
            />
            <span class="input-hint composer-model-hint">{{ currentRuntimeModel }}</span>
          </div>
        </template>
      </ChatInput>
    </div>

    <Transition name="project-changes-slide">
      <ProjectChangesPanel
        v-if="projectChangesPanelOpen"
        :messages="messages"
        :harness-run="currentHarnessRun"
        :loading-harness="harnessLoading"
        :project-insight="currentProjectInsight"
        :loading-project-insight="projectInsightLoading"
        :context-router="currentContextRouter"
        :loading-context-router="contextRouterLoading"
        :project-label="composerProjectDisplayLabel"
        :project-path="currentWorkingDirectory || workspaceBasePath"
        :workspace-base-path="workspaceBasePath"
        :local-validation-request="desktopValidationRequest"
        :local-validation-error="desktopValidationError"
        :is-mobile="isMobile"
        @run-validation="prepareDesktopValidationCommand"
        @approve-local-validation="approveDesktopValidationCommand"
        @deny-local-validation="denyDesktopValidationCommand"
        @close="projectChangesPanelOpen = false"
      />
    </Transition>

        <!-- Talk Mode 覆盖层 -->
        <TalkMode
          v-if="showTalkMode"
          :visible="showTalkMode"
          :agent-id="selectedAgentId"
          :conversation-id="currentConversationId"
          @close="showTalkMode = false"
        />

        <ImportHubDialog
          :visible="showShortcutSkillImportDialog"
          initial-tab="search"
          :initial-search-query="shortcutSkillImportQuery"
          @update:visible="showShortcutSkillImportDialog = $event"
          @installed="handleShortcutSkillInstalled"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ChatDotRound, Delete, Document, Plus, Setting, UploadFilled } from '@element-plus/icons-vue'
import { conversationApi, agentApi, agentBindingApi, modelApi, chatApi, harnessApi, skillApi, toolApi, workspaceTeamApi } from '@/api/index'
import { channelIconUrl } from '@/utils/channelSource'
import { useChat } from '@/composables/chat/useChat'
import { reconstructErrorInfo } from '@/types/chatError'
import { reconcileMessages, extractMessages } from '@/utils/messageReconcile'
import { desktopApproveCommandRequest, desktopDenyCommandRequest, desktopExecuteApprovedCommand, desktopGitDiff, desktopGitStatus, desktopPrepareApprovalCommand, desktopReadFileSnippet, desktopRunReadonlyCommand, desktopWorkspaceGrep, desktopWorkspaceTree, isDesktopRuntime, selectDesktopDirectory } from '@/utils/desktop'
import { isGlobalAdmin } from '@/utils/access'
import { getAuthToken, isAuthRedirectInProgress } from '@/utils/auth'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'
import type { ApprovalDecisionPayload, ApprovalDecisionScope, ChatExecutionSelection, ChatShortcutItem, ContextRouterSummary, Conversation, Agent, ModelConfig, ProviderInfo, ActiveModelsInfo, ChatAttachment, MessageContentPart, Message, Skill, Tool, ToolCallMeta, StreamPhase, ConversationRuntimeMode, HarnessRun, ProjectInsightSummary, ReviewSummary, ReviewValidationRecord } from '@/types'
import ImportHubDialog from '@/components/skill/ImportHubDialog.vue'

interface WorkspaceDirectoryEntry {
  name: string
  path: string
  relativePath: string
}

interface WorkspaceDirectoryListing {
  rootPath: string
  currentPath: string
  relativePath: string
  canGoUp: boolean
  parentPath?: string | null
  entries: WorkspaceDirectoryEntry[]
}

interface DesktopValidationRequestState {
  requestId: string
  commandText: string
  summary: string
  riskLevel?: string
  expiresAt?: number
  status: 'pending' | 'running'
}

interface AgentTemplateRuntimeMeta {
  preferredMode?: string | null
  allowedModes?: string[] | null
}

interface ChatCapabilityPickerState {
  skills: Skill[]
  tools: Tool[]
  skillSource: 'bound' | 'system'
  toolSource: 'bound' | 'system'
}

interface AgentTemplateQualityGateMeta {
  enabled?: boolean | null
  rule?: string | null
}

interface AgentTemplateLocalizedTextMeta {
  text?: string | null
  textEn?: string | null
}

interface AgentTemplateInteractionGuideCardMeta {
  kicker?: string | null
  kickerEn?: string | null
  title?: string | null
  titleEn?: string | null
  summaryBound?: string | null
  summaryBoundEn?: string | null
  summaryUnbound?: string | null
  summaryUnboundEn?: string | null
  quickStartLabel?: string | null
  quickStartLabelEn?: string | null
  rulesTitle?: string | null
  rulesTitleEn?: string | null
  showRulesLabel?: string | null
  showRulesLabelEn?: string | null
  hideRulesLabel?: string | null
  hideRulesLabelEn?: string | null
}

interface AgentTemplateInteractionHintsMeta {
  guideCard?: AgentTemplateInteractionGuideCardMeta | null
  rules?: AgentTemplateLocalizedTextMeta[] | null
}

interface AgentTemplateMockTaskMeta {
  id?: string | null
  title?: string | null
  input?: string | null
}

interface AgentTemplateStarterPromptMeta {
  id?: string | null
  label?: string | null
  labelEn?: string | null
  prompt?: string | null
  promptEn?: string | null
}

interface AgentTemplateManifestMeta {
  runtime?: AgentTemplateRuntimeMeta | null
  interactionHints?: AgentTemplateInteractionHintsMeta | null
  starterPrompts?: AgentTemplateStarterPromptMeta[] | null
  qualityGates?: Record<string, AgentTemplateQualityGateMeta> | null
  mockAcceptanceTasks?: AgentTemplateMockTaskMeta[] | null
}

interface ActiveTemplateMockTask {
  templateId: string
  templateName: string
  taskId: string
  taskIndex: number
  title: string
  expected: string[]
}

interface MockTaskAssessment {
  status?: string
  score?: number
  confidence?: string
  matched?: number
  total?: number
  matchedItems?: string[]
  missingItems?: string[]
  gateSatisfied?: boolean
  gateBlockers?: string[]
  evidence?: string[]
  signals?: Record<string, unknown>
}

// 导入组件化组件
import MessageList from '@/components/chat/MessageList.vue'
import ChatInput from '@/components/chat/ChatInput.vue'
import StreamLoadingBar from '@/components/chat/StreamLoadingBar.vue'
import TalkMode from '@/components/chat/TalkMode.vue'
import ModelSelector from '@/components/chat/ModelSelector.vue'
import ProjectChangesPanel from '@/components/chat/ProjectChangesPanel.vue'
import AgentIcon from '@/components/common/AgentIcon.vue'
import { useEChartsRenderer } from '@/composables/useEChartsRenderer'
import { useKatexRenderer } from '@/composables/useKatexRenderer'
import { useMermaidRenderer } from '@/composables/useMermaidRenderer'

// ============ Talk Mode ============
const showTalkMode = ref(false)

// ============ 移动端 & 响应式状态 ============
const isMobile = ref(false)
const convPanelOpen = ref(false)
const convPanelCollapsed = ref(localStorage.getItem('mc-conv-collapsed') === 'true')
let mobileQuery: MediaQueryList | null = null
let mediumQuery: MediaQueryList | null = null
const userExplicitConvCollapse = ref(localStorage.getItem('mc-conv-collapsed') === 'true')

function handleMobileChange(e: MediaQueryListEvent | MediaQueryList) {
  isMobile.value = e.matches
  if (!e.matches) convPanelOpen.value = false
}

function handleConvMediumChange(e: MediaQueryListEvent | MediaQueryList) {
  if (e.matches && !userExplicitConvCollapse.value) {
    convPanelCollapsed.value = true
  } else if (!e.matches && !userExplicitConvCollapse.value) {
    convPanelCollapsed.value = false
  }
}

function toggleConvPanel() {
  convPanelCollapsed.value = !convPanelCollapsed.value
  userExplicitConvCollapse.value = convPanelCollapsed.value
  localStorage.setItem('mc-conv-collapsed', String(convPanelCollapsed.value))
}

async function toggleProjectChangesPanel() {
  projectChangesPanelOpen.value = !projectChangesPanelOpen.value
  if (projectChangesPanelOpen.value) {
    headerMenuOpen.value = false
    await loadHarnessRun()
    void loadProjectInsight()
    void loadContextRouter()
  }
}

async function syncDesktopProjectEvidence(force = false) {
  if (!isDesktopRuntime() || !projectChangesPanelOpen.value) {
    return
  }
  const harnessRunId = String(currentHarnessRun.value?.id || '').trim()
  const rootPath = String(currentWorkingDirectory.value || workspaceBasePath.value || '').trim()
  if (!harnessRunId || !rootPath || desktopEvidenceSyncing.value) {
    return
  }

  const syncKey = `${harnessRunId}::${rootPath}`
  if (!force && desktopEvidenceSyncKey.value === syncKey) {
    return
  }

  desktopEvidenceSyncing.value = true
  try {
    const harnessLink = {
      harnessRunId,
      workspaceId: workspaceStore.currentWorkspaceId || undefined,
      authToken: getAuthToken() || undefined,
    }

    await desktopWorkspaceTree({
      rootPath,
      basePath: rootPath,
      maxDepth: 2,
      maxEntries: 120,
    }, harnessLink)

    try {
      const gitStatusResult = await desktopGitStatus({ rootPath }, harnessLink)
      if ((gitStatusResult as any)?.clean !== true) {
        await desktopGitDiff({
          rootPath,
          contextLines: 3,
          maxBytes: 32 * 1024,
        }, harnessLink)
      }
    } catch {
      // Non-git workspaces are acceptable; tree evidence is still valuable.
    }

    desktopEvidenceSyncKey.value = syncKey

    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error) {
    console.warn('[ChatConsole] Failed to sync desktop local evidence into harness', error)
  } finally {
    desktopEvidenceSyncing.value = false
  }
}

const desktopSearchStopWords = new Set([
  'the', 'and', 'for', 'with', 'from', 'that', 'this', 'into', 'then', 'than', 'when', 'where', 'what', 'which',
  'read', 'show', 'find', 'check', 'grep', 'search', 'file', 'files', 'code', 'project', 'workspace', 'change',
  'changes', 'update', 'fix', 'bug', 'issue', 'run', 'test', 'tests', 'build', 'please', 'help', 'current',
  '当前', '项目', '工作区', '代码', '文件', '修改', '变更', '问题', '错误', '继续', '处理', '定位', '查看', '搜索',
  '检查', '运行', '测试', '构建', '修复', '优化', '需要', '相关', '这个', '那个',
])

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function getLatestUserMessageSearchText() {
  const lastUserMsg = messages.value.findLast(m => m.role === 'user')
  if (lastUserMsg) {
    const text = lastUserMsg.contentParts
      .filter(part => part.type === 'text')
      .map(part => part.text || '')
      .join('\n')
      .trim()
    if (text) {
      return text
    }
    if (lastUserMsg.content?.trim()) {
      return lastUserMsg.content.trim()
    }
  }
  return inputText.value.trim()
}

function collectDesktopSearchTerms(rawText: string) {
  const text = String(rawText || '').trim().slice(0, 400)
  if (!text) {
    return [] as string[]
  }

  const terms: string[] = []
  const seen = new Set<string>()
  const pushTerm = (value?: string | null) => {
    const next = String(value || '')
      .trim()
      .replace(/^[`"'“”‘’]+|[`"'“”‘’]+$/g, '')
    if (!next || next.length < 2 || next.length > 64) {
      return
    }
    const lowered = next.toLowerCase()
    if (desktopSearchStopWords.has(lowered) || seen.has(lowered)) {
      return
    }
    seen.add(lowered)
    terms.push(next)
  }

  const collectByRegex = (pattern: RegExp, resolver?: (match: RegExpExecArray) => string | undefined) => {
    pattern.lastIndex = 0
    let match: RegExpExecArray | null = null
    while ((match = pattern.exec(text)) !== null) {
      pushTerm(resolver ? resolver(match) : match[0])
    }
  }

  collectByRegex(/`([^`]+)`|"([^"]+)"|'([^']+)'/g, match => match[1] || match[2] || match[3])
  collectByRegex(/[A-Za-z_][A-Za-z0-9_./:-]{2,}/g)
  collectByRegex(/[A-Z]?[a-z]+(?:[A-Z][a-z0-9]+)+/g)
  collectByRegex(/[\u4e00-\u9fff]{2,12}/g)

  return terms.slice(0, 5)
}

async function syncDesktopProjectSearch(summary = currentProjectInsight.value, force = false) {
  if (!isDesktopRuntime() || !projectChangesPanelOpen.value) {
    return
  }

  const harnessRunId = String(currentHarnessRun.value?.id || '').trim()
  const projectRoot = String(summary?.rootPath || '').trim()
  const searchText = getLatestUserMessageSearchText()
  const searchTerms = collectDesktopSearchTerms(searchText)
  if (!harnessRunId || !projectRoot || !searchTerms.length || desktopSearchSyncing.value) {
    return
  }

  const query = searchTerms.map(term => escapeRegex(term)).join('|')
  const syncKey = `${harnessRunId}::${projectRoot}::${query}`
  if (!force && desktopSearchSyncKey.value === syncKey) {
    return
  }

  desktopSearchSyncing.value = true
  try {
    const harnessLink = {
      harnessRunId,
      workspaceId: workspaceStore.currentWorkspaceId || undefined,
      authToken: getAuthToken() || undefined,
    }
    await desktopWorkspaceGrep({
      rootPath: projectRoot,
      query,
      isRegexp: true,
      maxResults: 60,
    }, harnessLink)

    desktopSearchSyncKey.value = syncKey

    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error) {
    console.warn('[ChatConsole] Failed to sync desktop local search evidence into harness', error)
  } finally {
    desktopSearchSyncing.value = false
  }
}

function pickDesktopSnippetFiles(summary: ProjectInsightSummary | null | undefined) {
  const keyFiles = Array.isArray(summary?.keyFiles) ? summary!.keyFiles : []
  const preferred = ['AGENTS.md', 'README.md', 'README_zh.md', 'package.json', 'pom.xml', 'Dockerfile']
  const picked: string[] = []
  for (const name of preferred) {
    if (keyFiles.includes(name) && !picked.includes(name)) {
      picked.push(name)
    }
    if (picked.length >= 3) {
      break
    }
  }
  return picked
}

async function syncDesktopProjectSnippets(summary = currentProjectInsight.value, force = false) {
  if (!isDesktopRuntime() || !projectChangesPanelOpen.value) {
    return
  }
  const harnessRunId = String(currentHarnessRun.value?.id || '').trim()
  const projectRoot = String(summary?.rootPath || '').trim()
  const snippetFiles = pickDesktopSnippetFiles(summary)
  if (!harnessRunId || !projectRoot || !snippetFiles.length || desktopSnippetSyncing.value) {
    return
  }

  const syncKey = `${harnessRunId}::${projectRoot}::${snippetFiles.join('|')}`
  if (!force && desktopSnippetSyncKey.value === syncKey) {
    return
  }

  desktopSnippetSyncing.value = true
  try {
    const harnessLink = {
      harnessRunId,
      workspaceId: workspaceStore.currentWorkspaceId || undefined,
      authToken: getAuthToken() || undefined,
    }
    for (const filePath of snippetFiles) {
      await desktopReadFileSnippet({
        rootPath: projectRoot,
        filePath,
        startLine: 1,
        endLine: 80,
        maxLines: 80,
      }, harnessLink)
    }

    desktopSnippetSyncKey.value = syncKey

    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error) {
    console.warn('[ChatConsole] Failed to sync desktop key-file snippets into harness', error)
  } finally {
    desktopSnippetSyncing.value = false
  }
}

function buildDesktopReadonlyInspectionCommands(summary: ProjectInsightSummary | null | undefined) {
  const packageManager = String(summary?.packageManager || '').trim().toLowerCase()
  const buildSystem = String(summary?.buildSystem || '').trim().toLowerCase()
  const keyFiles = new Set((summary?.keyFiles || []).map(item => String(item || '').trim().toLowerCase()))
  const commands: Array<{ command: string; args: string[] }> = []
  const seen = new Set<string>()

  const pushCommand = (command: string, args: string[]) => {
    const key = `${command} ${args.join(' ')}`.trim().toLowerCase()
    if (!key || seen.has(key)) {
      return
    }
    seen.add(key)
    commands.push({ command, args })
  }

  if (keyFiles.has('package.json') || packageManager === 'pnpm' || packageManager === 'npm') {
    pushCommand('node', ['--version'])
  }
  if (packageManager === 'pnpm') {
    pushCommand('pnpm', ['--version'])
  } else if (packageManager === 'npm') {
    pushCommand('npm', ['--version'])
  }
  if (keyFiles.has('pom.xml') || buildSystem === 'maven') {
    pushCommand('java', ['-version'])
    pushCommand('mvn', ['-v'])
  }

  return commands.slice(0, 3)
}

async function syncDesktopReadonlyEnvironment(summary = currentProjectInsight.value, force = false) {
  if (!isDesktopRuntime() || !projectChangesPanelOpen.value) {
    return
  }

  const harnessRunId = String(currentHarnessRun.value?.id || '').trim()
  const projectRoot = String(summary?.rootPath || '').trim()
  const commands = buildDesktopReadonlyInspectionCommands(summary)
  if (!harnessRunId || !projectRoot || !commands.length || desktopReadonlySyncing.value) {
    return
  }

  const syncKey = `${harnessRunId}::${projectRoot}::${commands.map(item => `${item.command} ${item.args.join(' ')}`).join('|')}`
  if (!force && desktopReadonlySyncKey.value === syncKey) {
    return
  }

  desktopReadonlySyncing.value = true
  try {
    const harnessLink = {
      harnessRunId,
      workspaceId: workspaceStore.currentWorkspaceId || undefined,
      authToken: getAuthToken() || undefined,
    }
    for (const item of commands) {
      await desktopRunReadonlyCommand({
        rootPath: projectRoot,
        cwd: projectRoot,
        command: item.command,
        args: item.args,
        timeoutMs: 12000,
        maxOutputBytes: 12 * 1024,
      }, harnessLink)
    }

    desktopReadonlySyncKey.value = syncKey

    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error) {
    console.warn('[ChatConsole] Failed to sync desktop readonly environment evidence into harness', error)
  } finally {
    desktopReadonlySyncing.value = false
  }
}

async function loadProjectInsight() {
  const workspaceId = workspaceStore.currentWorkspaceId
  if (!workspaceId || !workspaceBasePath.value) {
    currentProjectInsight.value = null
    return
  }

  const requestWorkspaceId = String(workspaceId)
  const requestPath = currentWorkingDirectory.value?.trim() || undefined
  projectInsightLoading.value = true
  try {
    const res: any = await workspaceTeamApi.getProjectInsight(requestWorkspaceId, requestPath)
    if (String(workspaceStore.currentWorkspaceId || '') !== requestWorkspaceId) return
    if ((currentWorkingDirectory.value?.trim() || '') !== (requestPath || '')) return
    currentProjectInsight.value = res?.data || null
    if (projectChangesPanelOpen.value && isDesktopRuntime()) {
      void syncDesktopProjectSearch(currentProjectInsight.value)
      void syncDesktopProjectSnippets(currentProjectInsight.value)
      void syncDesktopReadonlyEnvironment(currentProjectInsight.value)
    }
  } catch {
    if (String(workspaceStore.currentWorkspaceId || '') === requestWorkspaceId) {
      currentProjectInsight.value = null
    }
  } finally {
    if (
      String(workspaceStore.currentWorkspaceId || '') === requestWorkspaceId
      && (currentWorkingDirectory.value?.trim() || '') === (requestPath || '')
    ) {
      projectInsightLoading.value = false
    }
  }
}

async function loadContextRouter() {
  const workspaceId = workspaceStore.currentWorkspaceId
  const agentId = selectedAgentId.value ? String(selectedAgentId.value) : ''
  if (!workspaceId || !workspaceBasePath.value || !agentId) {
    currentContextRouter.value = null
    return
  }

  const requestWorkspaceId = String(workspaceId)
  const requestPath = currentWorkingDirectory.value?.trim() || undefined
  const requestConversationId = currentConversationId.value?.trim() || undefined
  contextRouterLoading.value = true
  try {
    const res: any = await workspaceTeamApi.getContextRouter(requestWorkspaceId, {
      agentId,
      conversationId: requestConversationId,
      path: requestPath,
    })
    if (String(workspaceStore.currentWorkspaceId || '') !== requestWorkspaceId) return
    if ((currentWorkingDirectory.value?.trim() || '') !== (requestPath || '')) return
    if (String(selectedAgentId.value || '') !== agentId) return
    if ((currentConversationId.value?.trim() || '') !== (requestConversationId || '')) return
    currentContextRouter.value = res?.data || null
  } catch {
    if (String(workspaceStore.currentWorkspaceId || '') === requestWorkspaceId && String(selectedAgentId.value || '') === agentId) {
      currentContextRouter.value = null
    }
  } finally {
    if (
      String(workspaceStore.currentWorkspaceId || '') === requestWorkspaceId
      && (currentWorkingDirectory.value?.trim() || '') === (requestPath || '')
      && String(selectedAgentId.value || '') === agentId
      && (currentConversationId.value?.trim() || '') === (requestConversationId || '')
    ) {
      contextRouterLoading.value = false
    }
  }
}

async function loadHarnessRun(conversationId = currentConversationId.value) {
  const targetConversationId = conversationId?.trim()
  if (!targetConversationId) {
    currentHarnessRun.value = null
    hydrateDesktopValidationRequestFromHarness(null)
    return
  }
  harnessLoading.value = true
  try {
    const res: any = await harnessApi.latestForConversation(targetConversationId)
    if (targetConversationId !== currentConversationId.value) return
    currentHarnessRun.value = res?.data || null
    hydrateDesktopValidationRequestFromHarness(currentHarnessRun.value)
    mergeLatestAssistantValidationSummaryFromHarness(currentHarnessRun.value)
    if (projectChangesPanelOpen.value && isDesktopRuntime()) {
      void syncDesktopProjectEvidence()
      if (currentProjectInsight.value) {
        void syncDesktopProjectSearch(currentProjectInsight.value)
        void syncDesktopReadonlyEnvironment(currentProjectInsight.value)
      }
    }
  } catch {
    if (targetConversationId === currentConversationId.value) {
      currentHarnessRun.value = null
      hydrateDesktopValidationRequestFromHarness(null)
    }
  } finally {
    if (targetConversationId === currentConversationId.value) {
      harnessLoading.value = false
    }
  }
}

function parseMessageMetadataObject(metadata: unknown) {
  if (!metadata) return {} as Record<string, any>
  if (typeof metadata === 'object') return metadata as Record<string, any>
  if (typeof metadata === 'string') {
    try {
      let parsed: any = JSON.parse(metadata)
      if (typeof parsed === 'string') {
        try { parsed = JSON.parse(parsed) } catch { /* ignore */ }
      }
      return typeof parsed === 'object' && parsed !== null ? parsed : {}
    } catch {
      return {}
    }
  }
  return {}
}

function extractReviewValidationFromHarnessTool(tool: any): ReviewValidationRecord | null {
  if (!tool) return null
  const normalizedToolName = String(tool.toolName || '').trim().toLowerCase()
  const metadata = tool.metadata || {}
  const commandText = String(metadata.arguments || metadata.command || '').trim()
  const validationLike = normalizedToolName === 'command.run.approval.execute'
    || /command\.run|test|lint|build|check|verify/.test(normalizedToolName)
    || /(^|\s)(test|lint|build|check|verify|pytest|jest|vitest)(\s|$)/.test(commandText.toLowerCase())

  if (!validationLike || !commandText) {
    return null
  }

  const sharedPayload = metadata.sharedPayload || {}
  const evidence = typeof sharedPayload === 'object' && sharedPayload !== null
    ? (sharedPayload as any).evidence || {}
    : {}
  const rawExitCode = evidence.exitCode
  const exitCode = typeof rawExitCode === 'number'
    ? rawExitCode
    : Number.isFinite(Number(rawExitCode))
      ? Number(rawExitCode)
      : undefined
  const statusText = String(tool.status || '').toLowerCase()
  const status: ReviewValidationRecord['status'] = exitCode === 0
    ? 'passed'
    : typeof exitCode === 'number'
      ? 'failed'
      : statusText === 'running'
        ? 'running'
        : statusText === 'awaiting_approval'
          ? 'pending'
          : statusText === 'approved'
            ? 'approved'
            : statusText === 'failed'
              ? 'failed'
              : 'completed'

  return {
    command: commandText,
    toolName: String(tool.toolName || ''),
    status,
    result: String(metadata.result || metadata.summary || '').trim() || undefined,
    exitCode,
    timestamp: tool.completedAt ? new Date(tool.completedAt).getTime() : tool.startedAt ? new Date(tool.startedAt).getTime() : undefined,
  }
}

function buildHarnessValidationReviewSummary(run = currentHarnessRun.value) {
  const tools = Array.isArray(run?.toolInvocations) ? run!.toolInvocations : []
  const latestByKey = new Map<string, ReviewValidationRecord>()
  for (const tool of tools) {
    const record = extractReviewValidationFromHarnessTool(tool)
    if (!record) continue
    latestByKey.set(`${record.toolName}::${record.command}`, record)
  }
  return Array.from(latestByKey.values())
    .sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0))
    .slice(0, 6)
}

function mergeLatestAssistantValidationSummaryFromHarness(run = currentHarnessRun.value) {
  const validationRecords = buildHarnessValidationReviewSummary(run)
  const targetIndex = [...messages.value]
    .map((message, index) => ({ message, index }))
    .reverse()
    .find(entry => entry.message.role === 'assistant')?.index

  if (targetIndex == null) {
    return
  }

  const targetMessage = messages.value[targetIndex]
  const metadata = parseMessageMetadataObject(targetMessage.metadata)
  const currentSummary = metadata.reviewSummary as ReviewSummary | undefined
  const nextSummary: ReviewSummary | undefined = currentSummary
    ? {
        ...currentSummary,
        validations: validationRecords.length ? validationRecords : undefined,
      }
    : validationRecords.length
      ? {
          totalFiles: 0,
          addedCount: 0,
          modifiedCount: 0,
          files: [],
          validations: validationRecords,
        }
      : undefined

  if (!nextSummary) {
    return
  }

  const nextMetadata = {
    ...metadata,
    reviewSummary: nextSummary,
  }

  messages.value = messages.value.map((message, index) => (
    index === targetIndex
      ? { ...message, metadata: nextMetadata }
      : message
  ))
}

function buildDesktopHarnessLink() {
  return {
    harnessRunId: currentHarnessRun.value?.id || undefined,
    workspaceId: workspaceStore.currentWorkspaceId || undefined,
    authToken: getAuthToken() || undefined,
  }
}

function getDesktopValidationRootPath() {
  return String(currentProjectInsight.value?.rootPath || currentWorkingDirectory.value || workspaceBasePath.value || '').trim()
}

function parseDesktopCommandHint(commandHint: string) {
  const tokens = String(commandHint || '').match(/"[^"]*"|'[^']*'|[^\s]+/g) || []
  const normalized = tokens
    .map(token => token.replace(/^['"]|['"]$/g, '').trim())
    .filter(Boolean)
  if (!normalized.length) {
    return null
  }
  return {
    command: normalized[0],
    args: normalized.slice(1),
  }
}

async function prepareDesktopValidationCommand(commandHint: string) {
  if (!isDesktopRuntime()) {
    return
  }
  const rootPath = getDesktopValidationRootPath()
  if (!rootPath) {
    ElMessage.warning(t('chat.desktopValidationProjectMissing'))
    return
  }

  const parsed = parseDesktopCommandHint(commandHint)
  if (!parsed) {
    ElMessage.warning(t('chat.desktopValidationUnsupported'))
    return
  }

  try {
    desktopValidationError.value = ''
    const prepared = await desktopPrepareApprovalCommand({
      rootPath,
      cwd: rootPath,
      command: parsed.command,
      args: parsed.args,
    }, buildDesktopHarnessLink())
    if (!prepared) {
      ElMessage.warning(t('chat.desktopValidationUnsupported'))
      return
    }
    desktopValidationRequest.value = {
      requestId: String((prepared as any).requestId || ''),
      commandText: [parsed.command, ...parsed.args].join(' '),
      summary: String((prepared as any).summary || commandHint || '').trim(),
      riskLevel: String((prepared as any).riskLevel || '').trim() || undefined,
      expiresAt: typeof (prepared as any).expiresAt === 'number' ? (prepared as any).expiresAt : undefined,
      status: 'pending',
    }
    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error: any) {
    desktopValidationError.value = error?.message || String(error || '')
    ElMessage.warning(desktopValidationError.value || t('chat.desktopValidationUnsupported'))
  }
}

async function approveDesktopValidationCommand() {
  const pending = desktopValidationRequest.value
  if (!pending?.requestId) {
    return
  }
  try {
    desktopValidationError.value = ''
    desktopValidationRequest.value = { ...pending, status: 'running' }
    const approved = await desktopApproveCommandRequest({
      requestId: pending.requestId,
    }, buildDesktopHarnessLink())
    const approvalToken = String((approved as any)?.approvalToken || '').trim()
    if (!approvalToken) {
      throw new Error(t('chat.desktopValidationApproveFailed'))
    }
    await desktopExecuteApprovedCommand({
      approvalToken,
    }, buildDesktopHarnessLink())
    desktopValidationRequest.value = null
    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error: any) {
    desktopValidationError.value = error?.message || String(error || '')
    desktopValidationRequest.value = pending ? { ...pending, status: 'pending' } : null
    ElMessage.error(desktopValidationError.value || t('chat.desktopValidationExecuteFailed'))
  }
}

async function denyDesktopValidationCommand() {
  const pending = desktopValidationRequest.value
  desktopValidationError.value = ''
  if (!pending?.requestId) {
    desktopValidationRequest.value = null
    return
  }
  try {
    await desktopDenyCommandRequest({
      requestId: pending.requestId,
    }, buildDesktopHarnessLink())
    desktopValidationRequest.value = null
    const activeConversationId = currentConversationId.value?.trim()
    if (activeConversationId) {
      await loadHarnessRun(activeConversationId)
    }
  } catch (error: any) {
    desktopValidationError.value = error?.message || String(error || '')
    desktopValidationRequest.value = pending
    ElMessage.warning(desktopValidationError.value || t('chat.desktopValidationDenyFailed'))
  }
}

function hydrateDesktopValidationRequestFromHarness(run = currentHarnessRun.value) {
  const toolInvocations = Array.isArray(run?.toolInvocations) ? run!.toolInvocations : []
  const approvals = Array.isArray(run?.approvals) ? run!.approvals : []

  const findInvocationById = (id?: string | null) => toolInvocations.find(tool => tool.id === id)

  const pendingApproval = [...approvals]
    .reverse()
    .find((approval: any) => {
      const linked = findInvocationById(approval?.toolInvocationId)
      return approval?.metadata?.desktopLocal === true
        && String(approval?.status || '').toLowerCase() === 'pending'
        && linked?.toolName === 'command.run.approval.prepare'
    })

  if (pendingApproval) {
    const linked = findInvocationById(pendingApproval.toolInvocationId)
    desktopValidationRequest.value = {
      requestId: String(pendingApproval.id || linked?.metadata?.approval?.requestId || '').trim(),
      commandText: String(linked?.metadata?.arguments || linked?.metadata?.summary || '').trim(),
      summary: String(linked?.metadata?.summary || pendingApproval.metadata?.summary || '').trim(),
      riskLevel: String(linked?.riskLevel || pendingApproval.metadata?.riskLevel || '').trim() || undefined,
      expiresAt: typeof pendingApproval.metadata?.expiresAt === 'number' ? pendingApproval.metadata.expiresAt : undefined,
      status: 'pending',
    }
    return
  }

  const runningInvocation = [...toolInvocations]
    .reverse()
    .find((tool: any) => tool?.metadata?.desktopLocal === true
      && tool?.toolName === 'command.run.approval.execute'
      && String(tool?.status || '').toLowerCase() === 'running')

  if (runningInvocation) {
    desktopValidationRequest.value = {
      requestId: String(runningInvocation.metadata?.approval?.requestId || runningInvocation.id || '').trim(),
      commandText: String(runningInvocation.metadata?.arguments || '').trim(),
      summary: String(runningInvocation.metadata?.summary || runningInvocation.metadata?.result || '').trim(),
      riskLevel: String(runningInvocation.riskLevel || '').trim() || undefined,
      expiresAt: typeof runningInvocation.metadata?.approval?.expiresAt === 'number' ? runningInvocation.metadata.approval.expiresAt : undefined,
      status: 'running',
    }
    return
  }

  desktopValidationRequest.value = null
}

function stopHarnessPolling() {
  if (harnessPollTimer !== null) {
    clearInterval(harnessPollTimer)
    harnessPollTimer = null
  }
}

function resetHarnessState() {
  stopHarnessPolling()
  currentHarnessRun.value = null
  harnessLoading.value = false
  desktopValidationRequest.value = null
  desktopValidationError.value = ''
  desktopEvidenceSyncKey.value = ''
  desktopSearchSyncKey.value = ''
  desktopSnippetSyncKey.value = ''
  desktopReadonlySyncKey.value = ''
}

// ============ 配置和常量 ============
interface AgentHomeQuickStart {
  title: string
  prompt: string
}

function parseAgentHomeQuickStarts(raw?: string | null): AgentHomeQuickStart[] {
  if (!raw?.trim()) return []
  try {
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .map((item: any) => ({
        title: String(item?.title || '').trim(),
        prompt: String(item?.prompt || item?.title || '').trim(),
      }))
      .filter(item => item.title && item.prompt)
      .slice(0, 4)
  } catch {
    return []
  }
}

function agentIdentityText(agent?: Agent | null) {
  const name = agent?.name?.trim() || t('chat.agentFallbackName', '这个智能体')
  const desc = agent?.description?.trim() || t('chat.agentFallbackDescription', '当前配置的能力说明')
  return { name, desc }
}

function buildAgentHomeQuickStarts(agent?: Agent | null): AgentHomeQuickStart[] {
  const { name, desc } = agentIdentityText(agent)
  if (isTeacherExamAssistantAgent(agent)) {
    return [
      { title: '了解这个出题助手能做什么', prompt: `请介绍「${name}」能完成哪些出题、备课、练习设计和质量审核任务，并说明适合输入什么材料。` },
      { title: '告诉我如何开始出题', prompt: `请引导我使用「${name}」开始一次出题任务：我需要提供哪些年级、范围、材料、题型、难度和输出要求？` },
      { title: '生成一组可用试题', prompt: '请基于我接下来提供的材料或知识库，先规划考点、题型、难度和分值，再生成试题、参考答案和采分点。' },
      { title: '给我一个完整出题示例', prompt: `请给出一个完整示例，展示「${name}」如何从材料分析、命题规划、试题生成、答案采分点到质量审核完成一次任务。` },
    ]
  }
  if (agentSupportsCodingMode(agent) || agent?.templateId === 'builtin.coding_agent') {
    return [
      { title: '了解这个编码助手能做什么', prompt: `请介绍「${name}」能处理哪些代码仓库任务，包括项目理解、计划、修改、测试、审查和总结。` },
      { title: '引导我开始开发任务', prompt: `请告诉我如何向「${name}」描述一个开发任务：目标、范围、约束、要修改的文件、验证方式分别应该怎么给。` },
      { title: '理解当前项目并制定计划', prompt: '请先理解当前工作区项目结构、技术栈和可用验证命令，然后给出分阶段实施计划。暂时不要修改文件。' },
      { title: '演示一次典型代码任务', prompt: `请用一个典型示例展示「${name}」会如何阅读代码、定位问题、修改文件、运行验证并总结 diff 与风险。` },
    ]
  }
  return [
    { title: `了解${name}能做什么`, prompt: `请根据你的配置介绍「${name}」的主要能力、适合处理的问题和不适合处理的边界。当前描述：${desc}` },
    { title: '告诉我如何开始使用', prompt: `请给我一份「${name}」的新手使用引导：我应该提供哪些信息、如何描述目标、怎样让结果更准确。` },
    { title: '帮我完成一个核心任务', prompt: `请基于「${name}」的定位，帮我设计并完成一个最常见的核心任务。当前描述：${desc}` },
    { title: '给我一个可参考示例', prompt: `请给出一个使用「${name}」的完整示例，包括用户应该怎么提问、你会如何处理、最终会输出什么。` },
  ]
}

const suggestions = computed(() => {
  const agent = currentAgent.value
  const configured = parseAgentHomeQuickStarts(agent?.homeQuickStartsJson)
  if (configured.length) {
    return configured
  }
  return buildAgentHomeQuickStarts(agent)
})

const chatHomeSubtitle = computed(() => {
  const agent = currentAgent.value
  return agent?.homeSubtitle?.trim()
    || agent?.description?.trim()
    || t('chat.subtitle')
})

const router = useRouter()
const route = useRoute()
const { t, locale } = useI18n()
const workspaceStore = useWorkspaceStore()
const isAdmin = computed(() => isGlobalAdmin())

const agents = ref<Agent[]>([])
const conversations = ref<Conversation[]>([])
const selectedAgentId = ref<string | number>('')
const currentConversationId = ref<string>('')
const inputText = ref('')
const modelSaving = ref(false)
const showModelPrompt = ref(false)
const defaultModel = ref<ModelConfig | null>(null)
const providers = ref<ProviderInfo[]>([])
const activeModels = ref<ActiveModelsInfo | null>(null)
const draftRuntimeModelValue = ref('')
const runtimeModelFallbackNoticeKey = ref('')
const currentWorkingDirectory = ref('')
const workingDirectoryDraft = ref('')
const workingDirectorySaving = ref(false)
const projectDirectoryBrowser = ref<WorkspaceDirectoryListing | null>(null)
const projectDirectoryLoading = ref(false)
const projectDirectoryError = ref('')
const currentHarnessRun = ref<HarnessRun | null>(null)
const activeTemplateMockTask = ref<ActiveTemplateMockTask | null>(null)
const harnessLoading = ref(false)
const desktopValidationRequest = ref<DesktopValidationRequestState | null>(null)
const desktopValidationError = ref('')
const desktopEvidenceSyncing = ref(false)
const desktopEvidenceSyncKey = ref('')
const desktopSearchSyncing = ref(false)
const desktopSearchSyncKey = ref('')
const desktopSnippetSyncing = ref(false)
const desktopSnippetSyncKey = ref('')
const desktopReadonlySyncing = ref(false)
const desktopReadonlySyncKey = ref('')
const currentProjectInsight = ref<ProjectInsightSummary | null>(null)
const projectInsightLoading = ref(false)
const currentContextRouter = ref<ContextRouterSummary | null>(null)
const contextRouterLoading = ref(false)
const workspaceCreating = ref(false)
const currentRuntimeMode = ref<ConversationRuntimeMode>('default')
const runtimeModeSaving = ref(false)
const composerMenuOpen = ref(false)
const permissionMenuOpen = ref(false)
const workspaceMenuOpen = ref(false)
const projectChangesPanelOpen = ref(false)
const permissionSaving = ref(false)
const workspaceSwitching = ref(false)
const workspaceStateReady = ref(false)
const routeSyncSuspended = ref(false)
const pendingAttachments = ref<ChatAttachment[]>([])
const uploadingAttachment = ref(false)
const shortcutCapabilityState = ref<ChatCapabilityPickerState>({
  skills: [],
  tools: [],
  skillSource: 'system',
  toolSource: 'system',
})
const shortcutCapabilityLoading = ref(false)
const pendingExecutionSelection = ref<ChatExecutionSelection | null>(null)
const showShortcutSkillImportDialog = ref(false)
const shortcutSkillImportQuery = ref('')
const agentsLoading = ref(false)
const agentProvisioning = ref(false)
let harnessPollTimer: ReturnType<typeof setInterval> | null = null
let defaultAgentCreationPromise: Promise<Agent | null> | null = null

// 思考模式默认关闭，用户明确开启后再持久化；普通问答优先保证响应速度。
const thinkingEnabled = ref(localStorage.getItem('mateclaw_thinking') === 'on')
const thinkingLevel = computed(() => thinkingEnabled.value ? 'high' : 'off')
watch(thinkingEnabled, (v) => localStorage.setItem('mateclaw_thinking', v ? 'on' : 'off'))
watch([selectedAgentId, () => workspaceStore.currentWorkspaceId], () => {
  void loadShortcutCapabilities()
}, { immediate: true })
watch(selectedAgentId, () => {
  pendingExecutionSelection.value = null
})

// Dropdowns & menus
const agentDropdownOpen = ref(false)

const headerMenuOpen = ref(false)

function selectAgent(agent: Agent) {
  agentDropdownOpen.value = false
  if (String(agent.id) !== String(selectedAgentId.value)) {
    selectedAgentId.value = agent.id
    const preferred = getPreferredConversationForAgent(agent.id)
    if (preferred) {
      void selectConversation(preferred)
    } else {
      newConversation({ preferWorkspaceDefault: false })
    }
  }
}

function beginChatEntry(agent?: Agent | null) {
  if (!agent) return
  agentDropdownOpen.value = false
  if (String(agent.id) !== String(selectedAgentId.value)) {
    selectedAgentId.value = agent.id
  }
  if (!currentConversationId.value) {
    newConversation({ preferWorkspaceDefault: false })
  }
}

async function selectModel(value: string) {
  if (!eligibleModels.value.some((item) => item.value === value)) {
    ElMessage.error(t('chat.switchModelFailed'))
    return
  }
  modelSaving.value = true
  try {
    const targetConversationId = currentConversation.value?.conversationId
      || (currentConversationId.value && !isLocalDraftConversation(currentConversationId.value)
        ? currentConversationId.value
        : '')
    if (!targetConversationId) {
      setDraftRuntimeModelState(value)
      return
    }
    const { providerId, modelName } = parseRuntimeModelValue(value)
    const res: any = await conversationApi.updateRuntimeModel(
      targetConversationId,
      providerId,
      modelName,
    )
    if (currentConversation.value) {
      currentConversation.value.runtimeProviderId = res?.data?.runtimeProviderId || providerId
      currentConversation.value.runtimeModelName = res?.data?.runtimeModelName || modelName
      setDraftRuntimeModelState()
    } else {
      setDraftRuntimeModelState(buildRuntimeModelValue(
        res?.data?.runtimeProviderId || providerId,
        res?.data?.runtimeModelName || modelName,
      ))
    }
  } catch (e: any) {
    ElMessage.error(e?.message || t('chat.switchModelFailed'))
  } finally {
    modelSaving.value = false
  }
}

function handleClearMessages() {
  headerMenuOpen.value = false
  clearMessages()
}

// Conversation rename
const renamingConvId = ref('')
const renameText = ref('')
const renameInputRef = ref<HTMLInputElement | null>(null)

function startRename(conv: Conversation) {
  renamingConvId.value = conv.conversationId
  renameText.value = conv.title || ''
  nextTick(() => {
    renameInputRef.value?.focus()
    renameInputRef.value?.select()
  })
}

async function confirmRename(conv: Conversation) {
  const newTitle = renameText.value.trim()
  renamingConvId.value = ''
  if (!newTitle || newTitle === conv.title) return
  conv.title = newTitle
  try {
    await conversationApi.rename(conv.conversationId, newTitle)
  } catch {
    // revert on fail — reload
    await loadConversations()
  }
}

function cancelRename() {
  renamingConvId.value = ''
}

// Delete with confirmation
function confirmDeleteConversation(conversationId: string) {
  ElMessageBox.confirm(
    t('chat.deleteConfirm') || 'Delete this conversation?',
    t('common.confirm'),
    { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') }
  ).then(() => deleteConversation(conversationId)).catch(() => {})
}

// 拖拽上传
const isDragging = ref(false)
let dragCounter = 0

function onDragEnter(e: DragEvent) {
  dragCounter++
  if (e.dataTransfer?.types.includes('Files')) {
    isDragging.value = true
  }
}

function onDragLeave() {
  dragCounter--
  if (dragCounter === 0) {
    isDragging.value = false
  }
}

async function onDrop(e: DragEvent) {
  dragCounter = 0
  isDragging.value = false

  const dtFiles = Array.from(e.dataTransfer?.files || [])
  const items = Array.from(e.dataTransfer?.items || [])

  const electronDirs: File[] = []
  const webDirEntries: FileSystemDirectoryEntry[] = []
  const regularFiles: File[] = []

  for (let i = 0; i < items.length; i++) {
    const entry = items[i].webkitGetAsEntry?.()
    const file = dtFiles[i]
    if (entry?.isDirectory) {
      if ((file as any)?.path) {
        // Electron: has absolute path
        electronDirs.push(file)
      } else {
        // Web: need to recursively collect files
        webDirEntries.push(entry as FileSystemDirectoryEntry)
      }
    } else if (file) {
      regularFiles.push(file)
    }
  }

  // Electron directories → record path reference
  if (electronDirs.length) {
    handleDirectoryAttach(electronDirs)
  }
  // Web directories → recursively collect files and upload
  if (webDirEntries.length) {
    const collected = await collectFilesFromEntries(webDirEntries)
    if (collected.length) {
      handleFileSelect(collected)
    }
  }
  // Regular files → normal upload
  if (regularFiles.length) {
    handleFileSelect(regularFiles)
  }
}

function handleDirectoryAttach(dirFiles: File[]) {
  if (!currentConversationId.value) {
    newConversation()
  }
  for (const dir of dirFiles) {
    const dirPath = (dir as any).path as string
    pendingAttachments.value.push({
      name: dirPath.split('/').pop() || dir.name,
      size: 0,
      url: '',
      storedName: '',
      path: dirPath,
      contentType: 'inode/directory',
    })
  }
}

async function collectFilesFromEntries(dirEntries: FileSystemDirectoryEntry[]): Promise<File[]> {
  const files: File[] = []

  async function readDir(dir: FileSystemDirectoryEntry) {
    const reader = dir.createReader()
    let batch: FileSystemEntry[]
    do {
      batch = await new Promise<FileSystemEntry[]>((resolve, reject) => {
        reader.readEntries(resolve, reject)
      })
      for (const entry of batch) {
        if (entry.isFile) {
          const file = await new Promise<File>((resolve, reject) => {
            (entry as FileSystemFileEntry).file(resolve, reject)
          })
          files.push(file)
        } else if (entry.isDirectory) {
          await readDir(entry as FileSystemDirectoryEntry)
        }
      }
    } while (batch.length > 0)  // readEntries returns empty when done
  }

  for (const dir of dirEntries) {
    await readDir(dir)
  }
  return files
}

const messageListRef = ref<InstanceType<typeof MessageList> | null>(null)
const chatInputRef = ref<InstanceType<typeof ChatInput> | null>(null)
const workspaceFolderInputRef = ref<HTMLInputElement | null>(null)

// Post-render augmentations (ECharts, KaTeX, Mermaid) all watch the same
// MessageList container — placeholders emitted by useMarkdownRenderer get
// upgraded in place after Vue paints the rendered Markdown HTML.
const echartsContainerRef = computed(() => messageListRef.value?.$el as HTMLElement | null)
const { startObserving: startECharts, dispose: disposeECharts } = useEChartsRenderer(echartsContainerRef)
const { startObserving: startKatex, dispose: disposeKatex } = useKatexRenderer(echartsContainerRef)
const { startObserving: startMermaid, dispose: disposeMermaid } = useMermaidRenderer(echartsContainerRef)

// Last-attempt draft, restored into the input box when the SSE error event
// arrives async (sendChatMessage resolves on connect, the error fires later,
// so the catch in handleSendMessage cannot recover input by itself).
const pendingSendDraft = ref<{ input: string; attachments: any[] } | null>(null)

// 使用 useChat composable
const {
  messages,
  isGenerating,
  streamPhase,
  phaseInfo,
  queuedMessage,
  hasQueued,
  queueSize,
  heartbeat,
  sendMessage: sendChatMessage,
  stopGeneration: stopChatGeneration,
  cancelQueued,
  reconnectStream: reconnectChatStream,
  resetForNewConversation,
} = useChat({
  baseUrl: '',
  thinkingLevel,
  onStreamEnd: async (meta) => {
    // Restore the input/attachments if the turn ended in an error and the
    // user hasn't typed something else in the meantime.
    if (meta.reason === 'error' && pendingSendDraft.value) {
      const draft = pendingSendDraft.value
      if (!inputText.value) inputText.value = draft.input
      if (pendingAttachments.value.length === 0) pendingAttachments.value = draft.attachments
    }
    if (meta.reason !== 'error') {
      pendingSendDraft.value = null
    }
    // 流结束后刷新会话列表（更新 lastActiveTime / 标题等）
    await loadConversations()
    if (meta.conversationId && meta.conversationId === currentConversationId.value) {
      // Skip DB refresh for awaiting_approval / interrupted / error:
      //  - awaiting_approval / interrupted: avoids overwriting local-only state
      //    or breaking message ordering.
      //  - error: the failed turn (e.g. SSE setup failure like "无权操作该会话")
      //    was never persisted, so refreshing would wipe the user's just-sent
      //    bubble and the failed assistant placeholder, leaving no trace of
      //    the attempt in the chat window.
      if (meta.reason !== 'awaiting_approval'
          && meta.reason !== 'interrupted'
          && meta.reason !== 'error') {
        await refreshCurrentConversationMessages(meta.conversationId)
      }
      await loadHarnessRun(meta.conversationId)
    }
  },
})

// ============ 连接状态 ============
const connectionStatusClass = computed(() => {
  if (isGenerating.value) return 'status-streaming'
  if (streamPhase.value === 'failed') return 'status-error'
  return 'status-idle'
})
const connectionStatusLabel = computed(() => {
  if (isGenerating.value) return t('chat.status.streaming', 'Generating...')
  if (streamPhase.value === 'failed') return t('chat.status.error', 'Disconnected')
  return t('chat.status.idle', 'Ready')
})

// ============ 计算属性 ============
const workspaceDefaultAgentId = computed(() => workspaceStore.getDefaultAgentId())
const currentAgent = computed(() => agents.value.find(a => String(a.id) === String(selectedAgentId.value)))
const currentAgentTemplateManifest = computed(() => parseAgentTemplateManifest(currentAgent.value))
const currentConversation = computed(() => conversations.value.find(c => c.conversationId === currentConversationId.value) || null)
const isCurrentConversationAgentDeleted = computed(() => {
  if (!currentConversation.value?.agentId) return false
  return !agents.value.some(agent => String(agent.id) === String(currentConversation.value?.agentId))
})
const chatInputDisabled = computed(() =>
  showModelPrompt.value || agentProvisioning.value || isCurrentConversationAgentDeleted.value,
)
const chatInputHint = computed(() =>
  isCurrentConversationAgentDeleted.value
    ? t('chat.deletedAgentReadonlyHint')
    : currentRuntimeModel.value,
)
const activeAgentConversations = computed(() => {
  if (!selectedAgentId.value) return conversations.value
  return conversations.value.filter(conv => String(conv.agentId || '') === String(selectedAgentId.value))
})
const workspaceBasePath = computed(() => workspaceStore.currentWorkspace?.basePath?.trim() || '')
const currentAgentTemplateBadge = computed(() => agentTemplateBadge(currentAgent.value))
const currentAgentSupportsCodingMode = computed(() => agentSupportsCodingMode(currentAgent.value))
const showTeacherGuideCard = computed(() => isTeacherExamAssistantAgent(currentAgent.value))
const visibleTeacherGuideCard = computed(() => showTeacherGuideCard.value && !teacherGuideDismissed.value)
const showTeacherGuideRestore = computed(() => showTeacherGuideCard.value && teacherGuideDismissed.value)
const teacherBoundKnowledgeBaseCount = computed(() => parseAgentKnowledgeBaseIds(currentAgent.value).length)
const starterEntryAgents = computed(() => {
  const ordered: Agent[] = []
  if (currentAgent.value) {
    ordered.push(currentAgent.value)
  }
  for (const agent of agents.value) {
    if (ordered.some(candidate => String(candidate.id) === String(agent.id))) continue
    ordered.push(agent)
  }
  return ordered.slice(0, 6)
})
const shouldShowChatEntry = computed(() => !currentConversationId.value
  && activeAgentConversations.value.length === 0)
const currentRuntimeModeLabel = computed(() => {
  switch (currentRuntimeMode.value) {
    case 'plan':
      return t('chat.runtimeModePlan')
    case 'coding':
      return t('chat.runtimeModeCoding')
    default:
      return t('chat.runtimeModeDefault')
  }
})
const mockTaskEvidence = computed(() => {
  if (!activeTemplateMockTask.value || !currentHarnessRun.value) return ''
  const stepCount = currentHarnessRun.value.steps?.length || 0
  const toolCount = currentHarnessRun.value.toolInvocations?.length || 0
  const approvalCount = currentHarnessRun.value.approvals?.length || 0
  const expected = activeTemplateMockTask.value.expected || []
  const preview = currentHarnessRun.value.summary?.finalAnswerPreview || ''
  const matched = mockTaskAssessment.value?.matched ?? expected.filter(item => preview.includes(item)).length
  return t('chat.mockTask.evidence', {
    status: currentHarnessRun.value.status || 'RUNNING',
    steps: stepCount,
    tools: toolCount,
    approvals: approvalCount,
    matched,
    total: expected.length,
  })
})
const mockTaskAssessment = computed<MockTaskAssessment | null>(() => {
  const raw = currentHarnessRun.value?.metadata?.mockAcceptance
  if (!raw || typeof raw !== 'object') return null
  return raw as MockTaskAssessment
})
function toMockTaskStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .map(item => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean)
}
function toMockTaskSignalRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? (value as Record<string, unknown>) : {}
}
const mockTaskMatchedItems = computed(() => mockTaskAssessment.value?.matchedItems || [])
const mockTaskMissingItems = computed(() => mockTaskAssessment.value?.missingItems || [])
const mockTaskGateBlockers = computed(() => mockTaskAssessment.value?.gateBlockers || [])
const mockTaskAssessmentEvidence = computed(() => mockTaskAssessment.value?.evidence || [])
const mockTaskSignals = computed(() => toMockTaskSignalRecord(mockTaskAssessment.value?.signals))
const mockTaskValidationCommands = computed(() => toMockTaskStringArray(mockTaskSignals.value.validationCommands))
const mockTaskValidationResults = computed(() => toMockTaskStringArray(mockTaskSignals.value.validationResults))
const mockTaskDiffFiles = computed(() => toMockTaskStringArray(mockTaskSignals.value.diffFiles))
const mockTaskRetrievedSources = computed(() => {
  const merged = [
    ...toMockTaskStringArray(mockTaskSignals.value.retrievedSourceTitles),
    ...toMockTaskStringArray(mockTaskSignals.value.retrievedSourceFiles),
  ]
  return Array.from(new Set(merged))
})
const mockTaskCitedSources = computed(() => toMockTaskStringArray(mockTaskSignals.value.citedSources))
const mockTaskDiffSummary = computed(() => {
  const signals = mockTaskSignals.value
  const diffFileCount = typeof signals.diffFileCount === 'number' ? signals.diffFileCount : 0
  const diffHunks = typeof signals.diffHunks === 'number' ? signals.diffHunks : 0
  const diffAddedLines = typeof signals.diffAddedLines === 'number' ? signals.diffAddedLines : 0
  const diffRemovedLines = typeof signals.diffRemovedLines === 'number' ? signals.diffRemovedLines : 0
  if (!diffFileCount && !diffHunks && !diffAddedLines && !diffRemovedLines) return ''
  return t('chat.mockTask.diffSummary', {
    files: diffFileCount,
    hunks: diffHunks,
    added: diffAddedLines,
    removed: diffRemovedLines,
  })
})
const mockTaskHasStructuredEvidence = computed(() =>
  Boolean(
    mockTaskValidationCommands.value.length ||
      mockTaskValidationResults.value.length ||
      mockTaskDiffSummary.value ||
      mockTaskDiffFiles.value.length ||
      mockTaskRetrievedSources.value.length ||
      mockTaskCitedSources.value.length
  )
)
const mockTaskFinalAnswerPreview = computed(() => {
  const preview = currentHarnessRun.value?.summary?.finalAnswerPreview || ''
  return preview.trim()
})
const mockTaskScoreLabel = computed(() => {
  const assessment = mockTaskAssessment.value
  if (!assessment) return ''
  return t('chat.mockTask.score', {
    score: assessment.score ?? 0,
    confidence: t(`chat.mockTask.confidence.${assessment.confidence || 'low'}`),
  })
})
const mockTaskCompactSummary = computed(() => {
  const task = activeTemplateMockTask.value
  if (!task) return ''
  const preview = currentHarnessRun.value?.summary?.finalAnswerPreview || ''
  const matched = mockTaskAssessment.value?.matched ?? task.expected.filter(item => preview.includes(item)).length
  if (mockTaskGateBlockers.value.length) {
    return t('chat.mockTask.compactSummaryBlocked', {
      blocked: mockTaskGateBlockers.value.length,
      matched,
      total: task.expected.length,
    })
  }
  if (task.expected.length) {
    return t('chat.mockTask.compactSummaryChecks', {
      matched,
      total: task.expected.length,
    })
  }
  return t('chat.mockTask.compactSummaryReady')
})
const mockTaskCollapsedLine = computed(() => {
  const task = activeTemplateMockTask.value
  if (!task) return ''
  const segments = [
    t('chat.mockTask.kicker'),
    task.title,
    t(`chat.mockTask.status.${mockTaskAssessment.value?.status || 'running'}`),
  ]
  if (mockTaskScoreLabel.value) {
    segments.push(mockTaskScoreLabel.value)
  }
  if (mockTaskCompactSummary.value) {
    segments.push(mockTaskCompactSummary.value)
  }
  return segments.join(' · ')
})
const shouldPollHarness = computed(() =>
  Boolean(currentConversationId.value) && (projectChangesPanelOpen.value || isGenerating.value)
)
const projectRelativeLabel = computed(() => {
  const conversation = currentConversation.value
  if (conversation?.usingWorkspaceRoot) {
    return t('chat.workspaceRoot')
  }
  if (conversation?.projectRelativePath) {
    return conversation.projectRelativePath
  }
  const workspaceRoot = workspaceBasePath.value
  const workingDir = currentWorkingDirectory.value || workspaceRoot
  if (!workspaceRoot || !workingDir) return t('chat.workspaceRoot')

  const normalizedRoot = workspaceRoot.replace(/\\/g, '/').replace(/\/+$/, '')
  const normalizedWorkingDir = workingDir.replace(/\\/g, '/').replace(/\/+$/, '')
  if (normalizedRoot.toLowerCase() === normalizedWorkingDir.toLowerCase()) {
    return t('chat.workspaceRoot')
  }
  if (normalizedWorkingDir.toLowerCase().startsWith(`${normalizedRoot.toLowerCase()}/`)) {
    return normalizedWorkingDir.slice(normalizedRoot.length + 1)
  }
  return workingDir
})
const projectPermissionMode = computed<'limited' | 'full'>(() =>
  workspaceStore.currentWorkspace?.projectPermissionMode === 'full' ? 'full' : 'limited'
)
const anyComposerMenuOpen = computed(() =>
  composerMenuOpen.value || permissionMenuOpen.value || workspaceMenuOpen.value
)
const currentPermissionButtonLabel = computed(() =>
  projectPermissionMode.value === 'full' ? t('chat.composer.fullAccess') : t('chat.composer.defaultPermissions')
)
const currentWorkspaceDisplayLabel = computed(() => workspaceStore.currentWorkspace?.name || t('chat.composer.workspaceSection'))
const switchableWorkspaces = computed(() => workspaceStore.workspaces)
const workspaceFolderPickerAvailable = computed(() => {
  if (isDesktopRuntime()) return true
  if (typeof window === 'undefined') return false
  const userAgent = window.navigator.userAgent || ''
  return /Electron/i.test(userAgent)
})
const composerProjectDisplayLabel = computed(() => {
  if (projectRelativeLabel.value === t('chat.workspaceRoot')) {
    return workspaceStore.currentWorkspace?.name || t('chat.workspaceRoot')
  }
  return projectRelativeLabel.value
})

function formatConversationProjectLabel(conv: Conversation) {
  if (conv.usingWorkspaceRoot || !conv.projectRelativePath) {
    return t('chat.workspaceRoot')
  }
  return conv.projectRelativePath
}

function normalizeReferenceToken(value?: string | number | null, fallback = 'current') {
  const normalized = String(value || fallback)
    .trim()
    .toLowerCase()
    .replace(/\\/g, '/')
    .replace(/[^a-z0-9/_-]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || fallback
}

function buildExecutionSelectionToken(type: 'skill' | 'tool', value?: string | number | null) {
  return `@${type}:${normalizeReferenceToken(value, type)}`
}

function getCurrentMentionQueryFromInput() {
  const match = String(inputText.value || '').match(/(^|\s)@([^\s@]*)$/)
  return match?.[2]?.trim() || ''
}

function formatShortcutCapabilityLabel(item: Skill | Tool, type: 'skill' | 'tool') {
  if (type === 'skill') {
    const skill = item as Skill
    return locale.value === 'zh-CN'
      ? (skill.nameZh || skill.name || '')
      : (skill.nameEn || skill.name || '')
  }
  const tool = item as Tool
  return tool.displayName || tool.name
}

async function loadShortcutCapabilities() {
  const agentId = String(selectedAgentId.value || '').trim()
  shortcutCapabilityLoading.value = true
  try {
    const [enabledSkillsRes, enabledToolsRes, boundSkillsRes, boundToolsRes] = await Promise.all([
      skillApi.listEnabled(),
      toolApi.listEnabled(),
      agentId ? agentBindingApi.listSkills(agentId) : Promise.resolve({ data: [] }),
      agentId ? agentBindingApi.listTools(agentId) : Promise.resolve({ data: [] }),
    ])

    const enabledSkills = ((enabledSkillsRes as any)?.data || []) as Skill[]
    const enabledTools = ((enabledToolsRes as any)?.data || []) as Tool[]
    const boundSkillRows = ((boundSkillsRes as any)?.data || []) as Array<{ skillId: string | number; enabled?: boolean }>
    const boundToolRows = ((boundToolsRes as any)?.data || []) as Array<{ toolName: string; enabled?: boolean }>

    const hasSkillBindings = boundSkillRows.length > 0
    const hasToolBindings = boundToolRows.length > 0
    const boundSkillIds = new Set(
      boundSkillRows.filter(item => item.enabled !== false).map(item => String(item.skillId)),
    )
    const boundToolNames = new Set(
      boundToolRows.filter(item => item.enabled !== false).map(item => String(item.toolName)),
    )

    shortcutCapabilityState.value = {
      skills: hasSkillBindings
        ? enabledSkills.filter(skill => boundSkillIds.has(String(skill.id)))
        : enabledSkills,
      tools: hasToolBindings
        ? enabledTools.filter(tool => boundToolNames.has(String(tool.name)))
        : enabledTools,
      skillSource: hasSkillBindings ? 'bound' : 'system',
      toolSource: hasToolBindings ? 'bound' : 'system',
    }
  } catch (error) {
    console.warn('[ChatConsole] Failed to load shortcut capabilities', error)
    shortcutCapabilityState.value = {
      skills: [],
      tools: [],
      skillSource: 'system',
      toolSource: 'system',
    }
  } finally {
    shortcutCapabilityLoading.value = false
  }
}

const chatShortcutCommands = computed<ChatShortcutItem[]>(() => {
  const items: ChatShortcutItem[] = [
    {
    id: 'mode-default',
    label: t('chat.shortcuts.modeDefaultLabel'),
    description: t('chat.shortcuts.modeDefaultDescription'),
    value: '/mode default',
    icon: '⌘',
    aliases: ['default', 'react'],
    suffix: '',
    },
    {
    id: 'mode-plan',
    label: t('chat.shortcuts.modePlanLabel'),
    description: t('chat.shortcuts.modePlanDescription'),
    value: '/mode plan',
    icon: '⌘',
    aliases: ['plan', 'planner'],
    suffix: '',
    },
    {
    id: 'cwd',
    label: t('chat.shortcuts.cwdLabel'),
    description: t('chat.shortcuts.cwdDescription'),
    value: '/cwd',
    icon: '📁',
    aliases: ['directory', 'project', 'path'],
    },
    {
    id: 'project',
    label: t('chat.shortcuts.projectLabel'),
    description: t('chat.shortcuts.projectDescription'),
    value: '/project',
    icon: '📂',
    aliases: ['context', 'workspace'],
    suffix: '',
    },
  ]
  if (currentAgentSupportsCodingMode.value) {
    items.splice(2, 0, {
    id: 'mode-coding',
    label: t('chat.shortcuts.modeCodingLabel'),
    description: t('chat.shortcuts.modeCodingDescription'),
    value: '/mode coding',
    icon: '⌘',
    aliases: ['coding', 'code', 'dev'],
    suffix: '',
    })
  }
  if (isAdmin.value) {
    items.push({
    id: 'permission',
    label: t('chat.shortcuts.permissionLabel'),
    description: t('chat.shortcuts.permissionDescription'),
    value: '/permission',
    icon: '🔐',
    aliases: ['approval', 'access'],
    suffix: '',
    })
  }
  return items
})

const chatShortcutMentions = computed<ChatShortcutItem[]>(() => {
  const items: ChatShortcutItem[] = []
  const workspace = workspaceStore.currentWorkspace
  if (workspace) {
    items.push({
      id: 'mention-workspace',
      label: t('chat.shortcuts.mentionWorkspaceLabel'),
      description: workspace.name,
      value: `@workspace:${normalizeReferenceToken(workspace.name || workspace.id, 'current')}`,
      icon: '@',
      aliases: [workspace.name, workspace.basePath || ''],
      suffix: ' ',
    })
  }
  items.push({
    id: 'mention-project',
    label: t('chat.shortcuts.mentionProjectLabel'),
    description: projectRelativeLabel.value,
    value: `@project:${normalizeReferenceToken(currentConversation.value?.projectRelativePath || 'root', 'root')}`,
    icon: '@',
    aliases: [projectRelativeLabel.value, currentWorkingDirectory.value, workspaceBasePath.value],
    suffix: ' ',
  })
  if (currentAgent.value) {
    items.push({
      id: 'mention-agent',
      label: t('chat.shortcuts.mentionAgentLabel'),
      description: currentAgent.value.name,
      value: `@agent:${normalizeReferenceToken(currentAgent.value.name || currentAgent.value.id, 'current')}`,
      icon: '@',
      aliases: [currentAgent.value.name, currentAgent.value.agentType],
      suffix: ' ',
    })
  }
  const skillGroup = shortcutCapabilityState.value.skillSource === 'bound'
    ? t('chat.shortcuts.groupBoundSkills')
    : t('chat.shortcuts.groupSystemSkills')
  for (const skill of shortcutCapabilityState.value.skills) {
    const label = formatShortcutCapabilityLabel(skill, 'skill')
    items.push({
      id: `mention-skill-${skill.id}`,
      label,
      description: skill.description || skill.name,
      value: buildExecutionSelectionToken('skill', skill.name || skill.id),
      icon: skill.icon || '🧩',
      aliases: [skill.name, skill.nameZh || '', skill.nameEn || '', ...(skill.tags ? skill.tags.split(',') : [])].filter(Boolean),
      skipInsert: true,
      consumeQueryOnSelect: true,
      group: skillGroup,
      kind: 'skill',
      metadata: {
        selection: {
          type: 'skill',
          key: String(skill.name || skill.id),
          label,
          source: shortcutCapabilityState.value.skillSource,
          fallbackAllowed: true,
          boundToAgent: shortcutCapabilityState.value.skillSource === 'bound',
        },
      },
    })
  }
  const toolGroup = shortcutCapabilityState.value.toolSource === 'bound'
    ? t('chat.shortcuts.groupBoundTools')
    : t('chat.shortcuts.groupSystemTools')
  for (const tool of shortcutCapabilityState.value.tools) {
    const label = formatShortcutCapabilityLabel(tool, 'tool')
    items.push({
      id: `mention-tool-${tool.name}`,
      label,
      description: tool.description || tool.name,
      value: buildExecutionSelectionToken('tool', tool.name),
      icon: tool.icon || '🛠',
      aliases: [tool.name, tool.displayName || '', tool.toolType || ''].filter(Boolean),
      skipInsert: true,
      consumeQueryOnSelect: true,
      group: toolGroup,
      kind: 'tool',
      metadata: {
        selection: {
          type: 'tool',
          key: String(tool.name),
          label,
          source: shortcutCapabilityState.value.toolSource,
          fallbackAllowed: true,
          boundToAgent: shortcutCapabilityState.value.toolSource === 'bound',
        },
      },
    })
  }
  const importQuery = getCurrentMentionQueryFromInput()
  items.push({
    id: 'mention-import-skill',
    label: importQuery
      ? t('chat.shortcuts.importSkillWithQuery', { query: importQuery })
      : t('chat.shortcuts.importSkillLabel'),
    description: t('chat.shortcuts.importSkillDescription'),
    value: '@skill-search',
    icon: '📦',
    aliases: ['install skill', 'search skill', '导入 skill', '搜索 skill'],
    group: t('chat.shortcuts.groupExternal'),
    kind: 'skill-import',
    skipInsert: true,
    metadata: { query: importQuery },
  })
  return items
})

function handleShortcutSkillInstalled() {
  showShortcutSkillImportDialog.value = false
  void loadShortcutCapabilities()
}

function handleShortcutSelect(item: ChatShortcutItem) {
  if (item.kind === 'skill' || item.kind === 'tool') {
    const selection = item.metadata?.selection as ChatExecutionSelection | undefined
    if (selection) {
      pendingExecutionSelection.value = { ...selection, fallbackAllowed: true }
      ElMessage.success(
        item.kind === 'skill'
          ? t('chat.shortcuts.selectionSkillToast', { label: selection.label })
          : t('chat.shortcuts.selectionToolToast', { label: selection.label }),
      )
    }
    return
  }
  if (item.kind === 'skill-import') {
    shortcutSkillImportQuery.value = String(item.metadata?.query || '').trim()
    showShortcutSkillImportDialog.value = true
  }
}

function normalizeRuntimeMode(value?: string | null): ConversationRuntimeMode {
  if (value === 'plan') return 'plan'
  if (value === 'coding') return 'coding'
  return 'default'
}

function parseAgentKnowledgeBaseIds(agent?: Agent | null): Array<string | number> {
  const raw = agent?.knowledgeBaseIdsJson?.trim()
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter(item => item !== null && item !== undefined && item !== '') : []
  } catch {
    return []
  }
}

function parseAgentTemplateManifest(agent?: Agent | null): AgentTemplateManifestMeta | null {
  const raw = agent?.templateMetadataJson?.trim()
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed as AgentTemplateManifestMeta : null
  } catch {
    return null
  }
}

function formatTemplateLabel(value?: string | null): string {
  const normalized = value?.trim()
  if (!normalized) return ''
  return normalized
    .split(/[_-]+/)
    .filter(Boolean)
    .map((segment) => segment.length <= 3
      ? segment.toUpperCase()
      : `${segment[0].toUpperCase()}${segment.slice(1)}`)
    .join(' ')
}

function getAgentPreferredRuntimeMode(agent?: Agent | null): ConversationRuntimeMode {
  const preferredMode = normalizeRuntimeMode(parseAgentTemplateManifest(agent)?.runtime?.preferredMode)
  if (preferredMode !== 'default') {
    return preferredMode
  }
  if (
    agent?.templateId === 'builtin.coding_agent'
    || agent?.profileId === 'coding_agent_profile'
    || agent?.capabilityPackId === 'capability.coding.codex_style'
  ) {
    return 'coding'
  }
  return 'default'
}

function isTeacherExamAssistantAgent(agent?: Agent | null): boolean {
  return agent?.templateId === 'builtin.teacher_exam_assistant'
    || agent?.profileId === 'teacher_exam_assistant_profile'
    || agent?.capabilityPackId === 'capability.education.junior_classics_exam'
}

function agentSupportsCodingMode(agent?: Agent | null): boolean {
  const allowedModes = parseAgentTemplateManifest(agent)?.runtime?.allowedModes
  if (Array.isArray(allowedModes)) {
    const normalizedModes = allowedModes
      .map(mode => String(mode || '').trim().toLowerCase())
      .filter(Boolean)
    if (normalizedModes.includes('coding')) {
      return true
    }
  }
  return getAgentPreferredRuntimeMode(agent) === 'coding'
}

function agentTemplateBadge(agent?: Agent | null): string {
  const categoryLabel = formatTemplateLabel(agent?.templateCategory)
  if (categoryLabel) {
    return categoryLabel
  }
  if (agent?.templateId?.startsWith('builtin.')) {
    return t('chat.agentStarterTemplate')
  }
  return ''
}

function resolveTemplateLocalizedValue(primary?: string | null, english?: string | null, fallback = ''): string {
  const preferredLocale = String(locale.value || '').toLowerCase()
  const normalizedPrimary = primary?.trim() || ''
  const normalizedEnglish = english?.trim() || ''
  if (preferredLocale.startsWith('en')) {
    return normalizedEnglish || normalizedPrimary || fallback
  }
  return normalizedPrimary || normalizedEnglish || fallback
}

function applyTemplatePlaceholders(text: string, placeholders: Record<string, string | number>): string {
  return Object.entries(placeholders).reduce(
    (result, [key, value]) => result.split(`{${key}}`).join(String(value)),
    text,
  )
}

function getTeacherRuleText(value: unknown): string {
  if (!value || typeof value !== 'object') return ''
  const record = value as AgentTemplateQualityGateMeta
  return typeof record.rule === 'string' ? record.rule.trim() : ''
}

const teacherGuideExpanded = ref(false)
const teacherGuideDismissed = ref(true)
const mockTaskDetailsExpanded = ref(false)
const teacherGuideCardMeta = computed(() => currentAgentTemplateManifest.value?.interactionHints?.guideCard || null)

const teacherGuideKicker = computed(() => resolveTemplateLocalizedValue(
  teacherGuideCardMeta.value?.kicker,
  teacherGuideCardMeta.value?.kickerEn,
  t('chat.teacherGuide.kicker'),
))

const teacherGuideRulesTitle = computed(() => resolveTemplateLocalizedValue(
  teacherGuideCardMeta.value?.rulesTitle,
  teacherGuideCardMeta.value?.rulesTitleEn,
  t('chat.teacherGuide.rulesTitle'),
))

const teacherGuideShowRulesLabel = computed(() => resolveTemplateLocalizedValue(
  teacherGuideCardMeta.value?.showRulesLabel,
  teacherGuideCardMeta.value?.showRulesLabelEn,
  t('chat.teacherGuide.showRules'),
))

const teacherGuideHideRulesLabel = computed(() => resolveTemplateLocalizedValue(
  teacherGuideCardMeta.value?.hideRulesLabel,
  teacherGuideCardMeta.value?.hideRulesLabelEn,
  t('chat.teacherGuide.hideRules'),
))

const teacherGuideSummary = computed(() => {
  if (teacherBoundKnowledgeBaseCount.value > 0) {
    const summaryBound = resolveTemplateLocalizedValue(
      teacherGuideCardMeta.value?.summaryBound,
      teacherGuideCardMeta.value?.summaryBoundEn,
      t('chat.teacherGuide.summaryBound', { count: teacherBoundKnowledgeBaseCount.value }),
    )
    return applyTemplatePlaceholders(summaryBound, { count: teacherBoundKnowledgeBaseCount.value })
  }
  return resolveTemplateLocalizedValue(
    teacherGuideCardMeta.value?.summaryUnbound,
    teacherGuideCardMeta.value?.summaryUnboundEn,
    t('chat.teacherGuide.summaryUnbound'),
  )
})

const teacherGuideCompactSummary = computed(() => {
  if (teacherBoundKnowledgeBaseCount.value > 0) {
    return t('chat.teacherGuide.compactBound', { count: teacherBoundKnowledgeBaseCount.value })
  }
  return t('chat.teacherGuide.compactUnbound')
})

const teacherGuideRules = computed(() => {
  const interactionRules = currentAgentTemplateManifest.value?.interactionHints?.rules
  if (Array.isArray(interactionRules) && interactionRules.length) {
    const rules = interactionRules
      .map((item) => resolveTemplateLocalizedValue(item?.text, item?.textEn))
      .filter(Boolean)
    if (rules.length) {
      return Array.from(new Set(rules)).slice(0, 4)
    }
  }
  const gates = currentAgentTemplateManifest.value?.qualityGates
  if (gates && typeof gates === 'object') {
    const rules = Object.values(gates)
      .map(getTeacherRuleText)
      .filter(Boolean)
    if (rules.length) {
      return Array.from(new Set(rules)).slice(0, 4)
    }
  }
  return [
    t('chat.teacherGuide.fallbackRules.sourceGrounding'),
    t('chat.teacherGuide.fallbackRules.planFirst'),
    t('chat.teacherGuide.fallbackRules.answerRubric'),
    t('chat.teacherGuide.fallbackRules.reviewRequired'),
  ]
})

function dismissTeacherGuide() {
  teacherGuideExpanded.value = false
  teacherGuideDismissed.value = true
}

function setWorkingDirectoryState(value?: string | null) {
  const next = value?.trim() || workspaceBasePath.value || ''
  currentWorkingDirectory.value = next
  workingDirectoryDraft.value = next
}

function setRuntimeModeState(value?: string | null, agent: Agent | null = currentAgent.value || null) {
  const hasExplicitValue = typeof value === 'string' && value.trim().length > 0
  currentRuntimeMode.value = hasExplicitValue
    ? normalizeRuntimeMode(value)
    : getAgentPreferredRuntimeMode(agent)
}

function normalizeRuntimeModelValue(value?: string | null) {
  const trimmed = value?.trim() || ''
  if (!trimmed) return ''
  const [providerId = '', modelName = ''] = trimmed.split('::')
  return providerId && modelName ? `${providerId}::${modelName}` : ''
}

function buildRuntimeModelValue(providerId?: string | null, modelName?: string | null) {
  return providerId?.trim() && modelName?.trim()
    ? `${providerId.trim()}::${modelName.trim()}`
    : ''
}

function parseRuntimeModelValue(value?: string | null) {
  const normalized = normalizeRuntimeModelValue(value)
  const [providerId = '', modelName = ''] = normalized.split('::')
  return { providerId, modelName }
}

function setDraftRuntimeModelState(value?: string | null) {
  draftRuntimeModelValue.value = normalizeRuntimeModelValue(value)
}

function isEligibleModelValue(value?: string | null) {
  const normalized = normalizeRuntimeModelValue(value)
  return Boolean(normalized) && eligibleModels.value.some((item) => item.value === normalized)
}

function getRuntimeModelLabel(providerId?: string | null, modelName?: string | null) {
  const normalized = buildRuntimeModelValue(providerId, modelName)
  if (!normalized) return ''
  const eligibleMatch = eligibleModels.value.find((item) => item.value === normalized)
  if (eligibleMatch?.label) return eligibleMatch.label
  const provider = providers.value.find((item) => item.id === providerId)
  const allModels = provider ? [...(provider.models || []), ...(provider.extraModels || [])] : []
  const model = allModels.find((item) => item.id === modelName || item.name === modelName)
  if (provider?.name && model?.name) return `${provider.name} / ${model.name}`
  if (provider?.name && modelName) return `${provider.name} / ${modelName}`
  return normalized.replace('::', ' / ')
}

async function reconcileConversationRuntimeModelIfNeeded(conversation?: Conversation | null, notify = true) {
  if (!conversation?.conversationId) return
  if (!providers.value.length) return
  const requestedValue = buildRuntimeModelValue(conversation.runtimeProviderId, conversation.runtimeModelName)
  if (!requestedValue || isEligibleModelValue(requestedValue)) return

  try {
    const res: any = await conversationApi.updateRuntimeModel(
      conversation.conversationId,
      conversation.runtimeProviderId || '',
      conversation.runtimeModelName || '',
    )
    const data = res?.data || {}
    const requestedProviderId = data.requestedRuntimeProviderId || conversation.runtimeProviderId || ''
    const requestedModelName = data.requestedRuntimeModelName || conversation.runtimeModelName || ''
    const effectiveProviderId = data.runtimeProviderId || ''
    const effectiveModelName = data.runtimeModelName || ''

    conversation.runtimeProviderId = effectiveProviderId || undefined
    conversation.runtimeModelName = effectiveModelName || undefined

    if (notify && data.fallbackApplied) {
      const noticeKey = [
        conversation.conversationId,
        requestedProviderId,
        requestedModelName,
        effectiveProviderId,
        effectiveModelName,
      ].join('::')
      if (runtimeModelFallbackNoticeKey.value !== noticeKey) {
        runtimeModelFallbackNoticeKey.value = noticeKey
        const fromLabel = getRuntimeModelLabel(requestedProviderId, requestedModelName)
        if (effectiveProviderId && effectiveModelName) {
          ElMessage.warning(t('chat.runtimeModelFallbackNotice', {
            from: fromLabel || `${requestedProviderId} / ${requestedModelName}`,
            to: getRuntimeModelLabel(effectiveProviderId, effectiveModelName),
          }))
        } else {
          ElMessage.warning(t('chat.runtimeModelFallbackCleared', {
            from: fromLabel || `${requestedProviderId} / ${requestedModelName}`,
          }))
        }
      }
    }
  } catch (e) {
    console.warn('[ChatConsole] Failed to reconcile unavailable conversation model', e)
  }
}

function buildWorkingDirectoryPayload() {
  if (!workspaceBasePath.value) return undefined
  return currentWorkingDirectory.value && currentWorkingDirectory.value !== workspaceBasePath.value
    ? currentWorkingDirectory.value
    : ''
}

function closeComposerMenus() {
  composerMenuOpen.value = false
  permissionMenuOpen.value = false
  workspaceMenuOpen.value = false
}

function toggleComposerMenu() {
  const next = !composerMenuOpen.value
  closeComposerMenus()
  composerMenuOpen.value = next
}

function togglePermissionMenu() {
  if (!isAdmin.value) return
  const next = !permissionMenuOpen.value
  closeComposerMenus()
  permissionMenuOpen.value = next
}

async function toggleWorkspaceMenu() {
  if (!workspaceStore.workspaces.length) {
    await workspaceStore.fetchWorkspaces()
  }
  const next = !workspaceMenuOpen.value
  closeComposerMenus()
  workspaceMenuOpen.value = next
  if (next) {
    await loadProjectDirectoryBrowser(currentWorkingDirectory.value || workspaceBasePath.value)
  }
}

async function loadProjectDirectoryBrowser(path?: string | null) {
  const workspaceId = workspaceStore.currentWorkspaceId
  if (!workspaceId || !workspaceBasePath.value) {
    projectDirectoryBrowser.value = null
    projectDirectoryError.value = ''
    return
  }

  projectDirectoryLoading.value = true
  projectDirectoryError.value = ''
  try {
    const targetPath = path?.trim() || undefined
    if (isDesktopRuntime()) {
      const rootPath = workspaceBasePath.value
      const currentPath = targetPath || rootPath
      const treeResult = await desktopWorkspaceTree({
        rootPath,
        basePath: currentPath,
        maxDepth: 1,
        maxEntries: 200,
      }, {
        harnessRunId: currentHarnessRun.value?.id || undefined,
        workspaceId,
        authToken: getAuthToken() || undefined,
      })
      projectDirectoryBrowser.value = buildDesktopDirectoryListing(rootPath, currentPath, treeResult)
    } else {
      const res: any = await workspaceTeamApi.listDirectories(workspaceId, targetPath)
      projectDirectoryBrowser.value = res.data || null
    }
  } catch (e: any) {
    projectDirectoryError.value = e?.message || String(e || '')
  } finally {
    projectDirectoryLoading.value = false
  }
}

function buildDesktopDirectoryListing(rootPath: string, currentPath: string, treeResult: any): WorkspaceDirectoryListing | null {
  if (!rootPath || !currentPath || !treeResult) {
    return null
  }

  const normalizedRoot = String(rootPath).trim()
  const normalizedCurrent = String(currentPath).trim()
  const relativePath = String(treeResult.basePath || '').trim()
  const currentSegments = relativePath ? relativePath.split(/[\\/]/).filter(Boolean) : []
  const parentRelativePath = currentSegments.length > 0 ? currentSegments.slice(0, -1).join('/') : ''
  const parentPath = currentSegments.length > 0
    ? [normalizedRoot, ...currentSegments.slice(0, -1)].join('/').replace(/\//g, '\\')
    : null

  const entries = Array.isArray(treeResult.nodes)
    ? treeResult.nodes
        .filter((node: any) => node?.type === 'directory')
        .map((node: any) => ({
          name: String(node?.name || '').trim(),
          path: [normalizedRoot, ...String(node?.relativePath || '').split(/[\\/]/).filter(Boolean)].join('/').replace(/\//g, '\\'),
          relativePath: String(node?.relativePath || '').trim(),
        }))
        .filter((entry: WorkspaceDirectoryEntry) => Boolean(entry.name && entry.path))
    : []

  return {
    rootPath: normalizedRoot,
    currentPath: normalizedCurrent,
    relativePath,
    canGoUp: Boolean(parentPath),
    parentPath,
    entries,
  }
}

function handleComposerAttach() {
  closeComposerMenus()
  chatInputRef.value?.openFilePicker?.()
}

async function selectComposerRuntimeMode(nextMode: ConversationRuntimeMode) {
  await applyConversationRuntimeMode(nextMode)
  closeComposerMenus()
}

function openPluginsPage() {
  if (!isAdmin.value) return
  closeComposerMenus()
  router.push('/plugins')
}

function openAgentStudio() {
  closeComposerMenus()
  router.push('/agents')
}

function openWorkspaceSettings() {
  if (!isAdmin.value) return
  closeComposerMenus()
  router.push('/settings/workspaces')
}

async function handlePickWorkspaceDirectory() {
  if (!workspaceFolderPickerAvailable.value) {
    ElMessage.warning(t('chat.composer.workspaceFolderUnsupported'))
    return
  }
  workspaceCreating.value = true
  try {
    let selectedFolderName = ''
    let normalizedBasePath = ''

    if (isDesktopRuntime()) {
      normalizedBasePath = (await selectDesktopDirectory()) || ''
      selectedFolderName = normalizedBasePath.split(/[\\/]/).filter(Boolean).pop() || ''
    } else {
      const input = workspaceFolderInputRef.value
      if (!input) {
        return
      }
      const fileList = Array.from(input.files || [])
      if (!fileList.length) {
        return
      }

      const firstFile = fileList[0] as File & { path?: string; webkitRelativePath?: string }
      const firstPath = firstFile?.path || ''
      const relativePath = firstFile?.webkitRelativePath || ''
      selectedFolderName = relativePath.split('/')[0] || ''
      normalizedBasePath = firstPath
        ? firstPath.replace(/[\\/][^\\/]+$/, '')
        : ''
    }

    if (!normalizedBasePath || !selectedFolderName) {
      ElMessage.warning(t('chat.composer.workspaceFolderUnsupported'))
      return
    }

    const existingWorkspace = workspaceStore.workspaces.find(workspace => {
      const basePath = (workspace.basePath || '').trim().toLowerCase()
      return basePath && basePath === normalizedBasePath.trim().toLowerCase()
    })

    if (existingWorkspace?.id) {
      await handleWorkspaceSwitch(existingWorkspace.id, { forceReload: true, ensureConversation: true })
      return
    }

    const createdRes: any = await workspaceTeamApi.create({
      name: selectedFolderName,
      slug: buildWorkspaceSlug(selectedFolderName),
      description: '',
      basePath: normalizedBasePath,
      projectPermissionMode: 'limited',
    })
    const createdWorkspace = createdRes?.data
    if (!createdWorkspace?.id) {
      return
    }
    workspaceStore.upsertWorkspace(createdWorkspace)
    await handleWorkspaceSwitch(createdWorkspace.id, {
      forceReload: true,
      ensureConversation: true,
      successMessage: t('chat.composer.workspaceCreatedAndSwitched', { name: createdWorkspace.name || selectedFolderName }),
    })
    void workspaceStore.fetchWorkspaces()
  } catch (e: any) {
    ElMessage.error(e?.message || t('chat.composer.workspaceCreateFailed'))
  } finally {
    if (workspaceFolderInputRef.value) {
      workspaceFolderInputRef.value.value = ''
    }
    workspaceCreating.value = false
  }
}

function triggerWorkspaceFolderPicker() {
  if (!workspaceFolderPickerAvailable.value) {
    return
  }
  if (isDesktopRuntime()) {
    void handlePickWorkspaceDirectory()
    return
  }
  workspaceFolderInputRef.value?.click()
}

async function handleWorkspaceFolderSelected() {
  await handlePickWorkspaceDirectory()
}

function buildWorkspaceSlug(name: string) {
  const baseSlug = String(name || 'workspace')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '') || 'workspace'

  const existingSlugs = new Set(workspaceStore.workspaces.map(workspace => String(workspace.slug || '').toLowerCase()))
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

async function updateCurrentWorkspacePermission(nextMode: 'limited' | 'full') {
  const workspace = workspaceStore.currentWorkspace
  if (!workspace) return
  if ((workspace.projectPermissionMode || 'limited') === nextMode) {
    permissionMenuOpen.value = false
    return
  }

  if (nextMode === 'full') {
    try {
      await ElMessageBox.confirm(
        '完全访问会自动放行原本需要审批的受控操作，适合可信项目和明确授权的维护场景。仍会受工作区边界和硬阻断规则限制。确认切换到完全访问？',
        '确认开启完全访问',
        {
          type: 'warning',
          confirmButtonText: '确认开启',
          cancelButtonText: t('common.cancel'),
          confirmButtonClass: 'el-button--danger',
        },
      )
    } catch {
      permissionMenuOpen.value = false
      return
    }
  }

  permissionSaving.value = true
  try {
    await workspaceTeamApi.update(workspace.id, {
      name: workspace.name,
      slug: workspace.slug,
      description: workspace.description || '',
      basePath: workspace.basePath || '',
      projectPermissionMode: nextMode,
    })
    await workspaceStore.fetchWorkspaces()
    ElMessage.success(t('chat.composer.permissionSaved'))
    permissionMenuOpen.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || t('chat.composer.permissionSaveFailed'))
  } finally {
    permissionSaving.value = false
  }
}

async function handleWorkspaceSwitch(
  workspaceId: string | number,
  options: { forceReload?: boolean; ensureConversation?: boolean; successMessage?: string } = {},
) {
  const { forceReload = false, ensureConversation = false, successMessage } = options
  const normalizedWorkspaceId = String(workspaceId)

  if (!forceReload && workspaceStore.currentWorkspaceId === normalizedWorkspaceId) {
    closeComposerMenus()
    return
  }

  workspaceSwitching.value = true
  routeSyncSuspended.value = true
  try {
    await ensureWorkspaceLoaded(normalizedWorkspaceId)
    resetForNewConversation()
    resetHarnessState()
    selectedAgentId.value = ''
    currentConversationId.value = ''
    currentHarnessRun.value = null
    conversations.value = []
    messages.value = []
    pendingAttachments.value = []
    await router.replace({ path: '/chat', query: {} })
    workspaceStore.switchWorkspace(normalizedWorkspaceId)
    await nextTick()
    setWorkingDirectoryState()
    setRuntimeModeState()
    await Promise.all([loadAgents(), loadModelState(), loadConversations()])
    if (ensureConversation) {
      await ensureWorkspaceDefaultAgent({ pollIfMissing: true })
    }
    await hydrateStateFromRoute()
    await loadProjectDirectoryBrowser(currentWorkingDirectory.value || workspaceBasePath.value)
    await syncRouteState()
    ElMessage.success(successMessage || t('chat.composer.workspaceSwitched'))
    closeComposerMenus()
  } catch (e: any) {
    ElMessage.error(e?.message || t('chat.composer.workspaceSwitchFailed'))
  } finally {
    workspaceSwitching.value = false
    routeSyncSuspended.value = false
  }
}

async function ensureWorkspaceLoaded(workspaceId: string | number) {
  const normalizedWorkspaceId = String(workspaceId)
  if (workspaceStore.workspaces.some(workspace => workspace.id === normalizedWorkspaceId)) {
    return
  }
  try {
    const res: any = await workspaceTeamApi.get(normalizedWorkspaceId)
    const workspace = res?.data
    if (workspace?.id) {
      workspaceStore.upsertWorkspace(workspace)
    }
  } catch (e) {
    console.warn('[ChatConsole] Failed to preload workspace before switch:', e)
  }
}

function sleep(ms: number) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

async function ensureWorkspaceDefaultAgent(options: { pollIfMissing?: boolean } = {}) {
  const { pollIfMissing = false } = options

  const existingDefaultAgent = findAgentById(workspaceDefaultAgentId.value)
  if (existingDefaultAgent) {
    return existingDefaultAgent
  }

  if (agents.value.length === 1) {
    const onlyAgent = agents.value[0]
    workspaceStore.setDefaultAgentId(workspaceStore.currentWorkspaceId, onlyAgent.id)
    if (!selectedAgentId.value) {
      selectedAgentId.value = onlyAgent.id
    }
    return onlyAgent
  }

  if (!pollIfMissing) {
    return null
  }

  agentProvisioning.value = true
  try {
    for (let attempt = 0; attempt < 8; attempt += 1) {
      await loadAgents()

      const matchedDefaultAgent = findAgentById(workspaceDefaultAgentId.value)
      if (matchedDefaultAgent) {
        if (!selectedAgentId.value) {
          selectedAgentId.value = matchedDefaultAgent.id
        }
        return matchedDefaultAgent
      }

      if (agents.value.length === 1) {
        const onlyAgent = agents.value[0]
        workspaceStore.setDefaultAgentId(workspaceStore.currentWorkspaceId, onlyAgent.id)
        if (!selectedAgentId.value) {
          selectedAgentId.value = onlyAgent.id
        }
        return onlyAgent
      }

      if (agents.value.length > 1) {
        return null
      }

      await sleep(600)
    }
  } finally {
    agentProvisioning.value = false
  }

  return null
}

function buildWorkspaceDefaultAgentPayload(): Partial<Agent> & { name: string } {
  const workspaceName = workspaceStore.currentWorkspace?.name?.trim()
  const suffix = t('chat.defaultAgentSuffix')
  return {
    name: workspaceName ? `${workspaceName} ${suffix}` : t('chat.defaultAgentName'),
    description: t('chat.defaultAgentDescription'),
    agentType: 'react',
    systemPrompt: '',
    maxIterations: 10,
    icon: '🤖',
    tags: 'default,workspace',
    enabled: true,
  }
}

async function ensureAutoCreatedDefaultAgent() {
  if (!workspaceStore.currentWorkspaceId) {
    return null
  }
  if (agents.value.length > 0) {
    return resolveSelectedAgent({ allowFirstFallback: true })
  }
  if (defaultAgentCreationPromise) {
    return defaultAgentCreationPromise
  }

  agentProvisioning.value = true
  defaultAgentCreationPromise = (async () => {
    try {
      const res: any = await agentApi.create(buildWorkspaceDefaultAgentPayload())
      const createdAgent = res?.data || null
      if (!createdAgent?.id) {
        return null
      }
      if (!agents.value.some(agent => String(agent.id) === String(createdAgent.id))) {
        agents.value = [createdAgent, ...agents.value]
      }
      workspaceStore.setDefaultAgentId(workspaceStore.currentWorkspaceId, createdAgent.id)
      selectedAgentId.value = createdAgent.id
      return createdAgent
    } catch (e: any) {
      console.error('[ChatConsole] Failed to auto create default agent:', e)
      ElMessage.error(e?.message || t('chat.defaultAgentCreateFailed'))
      return null
    } finally {
      agentProvisioning.value = false
      defaultAgentCreationPromise = null
    }
  })()

  return defaultAgentCreationPromise
}

async function applyConversationWorkingDirectory() {
  if (!workspaceBasePath.value) {
    ElMessage.warning(t('chat.workingDirectoryWorkspaceRequired'))
    return
  }
  const next = workingDirectoryDraft.value.trim() || workspaceBasePath.value
  const payload = next === workspaceBasePath.value ? '' : next
  const currentEffectiveDirectory = currentWorkingDirectory.value || workspaceBasePath.value
  const hasExistingConversationContext = !!currentConversationId.value
    && !!currentConversation.value
    && ((currentConversation.value.messageCount || 0) > 0 || messages.value.length > 0)

  if (hasExistingConversationContext && next !== currentEffectiveDirectory) {
    try {
      await ElMessageBox.confirm(
        t('chat.projectSelectorSwitchWarning'),
        t('chat.projectSelectorSwitchWarningTitle'),
        {
          type: 'warning',
          confirmButtonText: t('chat.projectSelectorSwitchNewConversation'),
          cancelButtonText: t('chat.projectSelectorSwitchConfirm'),
          distinguishCancelAndClose: true,
        },
      )
      newConversation()
      setWorkingDirectoryState(next)
      await loadProjectDirectoryBrowser(next)
      ElMessage.success(t('chat.workingDirectoryPending'))
      return
    } catch (action: any) {
      if (action !== 'cancel') {
        return
      }
    }
  }

  if (!currentConversationId.value || !currentConversation.value) {
    setWorkingDirectoryState(next)
    await loadProjectDirectoryBrowser(next)
    ElMessage.success(t('chat.workingDirectoryPending'))
    return
  }

  workingDirectorySaving.value = true
  try {
    const res: any = await conversationApi.updateWorkingDirectory(currentConversationId.value, payload)
    const effective = res?.data?.workingDirectory || workspaceBasePath.value
    currentConversation.value.workingDirectory = payload ? effective : undefined
    setWorkingDirectoryState(effective)
    await loadProjectDirectoryBrowser(effective)
    ElMessage.success(t('chat.workingDirectorySaved'))
  } catch (e: any) {
    ElMessage.error(e?.message || String(e || t('chat.workingDirectorySaveFailed')))
  } finally {
    workingDirectorySaving.value = false
  }
}

async function resetConversationWorkingDirectory() {
  workingDirectoryDraft.value = workspaceBasePath.value
  await applyConversationWorkingDirectory()
}

async function applyConversationRuntimeMode(nextMode: ConversationRuntimeMode) {
  if (nextMode === 'coding' && !currentAgentSupportsCodingMode.value) {
    ElMessage.warning(t('chat.runtimeModeCodingUnavailable'))
    return
  }

  if (currentRuntimeMode.value === nextMode && currentConversation.value?.runtimeMode === nextMode) {
    return
  }

  if (!currentConversationId.value || !currentConversation.value) {
    setRuntimeModeState(nextMode)
    ElMessage.success(t('chat.runtimeModePending'))
    return
  }

  runtimeModeSaving.value = true
  try {
    const res: any = await conversationApi.updateRuntimeMode(currentConversationId.value, nextMode)
    const effective = normalizeRuntimeMode(res?.data?.runtimeMode)
    currentConversation.value.runtimeMode = effective
    setRuntimeModeState(effective)
    ElMessage.success(t('chat.runtimeModeSaved'))
  } catch (e: any) {
    ElMessage.error(e?.message || String(e || t('chat.runtimeModeSaveFailed')))
  } finally {
    runtimeModeSaving.value = false
  }
}

function clearComposer() {
  inputText.value = ''
  chatInputRef.value?.clear?.()
}

async function handleSlashCommand(rawContent: string) {
  const trimmed = rawContent.trim()
  if (!trimmed.startsWith('/')) return false

  const [commandToken] = trimmed.split(/\s+/, 1)
  const command = commandToken.toLowerCase()
  const argument = trimmed.slice(commandToken.length).trim().replace(/^['"]|['"]$/g, '')

  if (command === '/mode') {
    if (pendingAttachments.value.length > 0) {
      ElMessage.warning(t('chat.shortcuts.attachmentsUnsupported'))
      return true
    }
    const supportedModes = currentAgentSupportsCodingMode.value
      ? ['default', 'plan', 'coding']
      : ['default', 'plan']
    if (!supportedModes.includes(argument)) {
      ElMessage.warning(currentAgentSupportsCodingMode.value
        ? t('chat.shortcuts.modeUsage')
        : t('chat.shortcuts.modeUsageNoCoding'))
      return true
    }
    await applyConversationRuntimeMode(argument as ConversationRuntimeMode)
    clearComposer()
    return true
  }

  if (command === '/cwd') {
    if (pendingAttachments.value.length > 0) {
      ElMessage.warning(t('chat.shortcuts.attachmentsUnsupported'))
      return true
    }
    if (!argument) {
      ElMessage.info(`${t('chat.workingDirectory')}: ${currentWorkingDirectory.value || workspaceBasePath.value || t('chat.workspaceRoot')}`)
      clearComposer()
      return true
    }
    workingDirectoryDraft.value = argument
    await applyConversationWorkingDirectory()
    clearComposer()
    return true
  }

  if (command === '/project') {
    if (pendingAttachments.value.length > 0) {
      ElMessage.warning(t('chat.shortcuts.attachmentsUnsupported'))
      return true
    }
    ElMessage.info(t('chat.shortcuts.projectToast', {
      workspace: workspaceStore.currentWorkspace?.name || t('chat.workspaceRoot'),
      project: projectRelativeLabel.value,
    }))
    clearComposer()
    return true
  }

  if (command === '/permission') {
    if (pendingAttachments.value.length > 0) {
      ElMessage.warning(t('chat.shortcuts.attachmentsUnsupported'))
      return true
    }
    ElMessage.info(t('chat.shortcuts.permissionToast', {
      mode: projectPermissionMode.value === 'full' ? t('chat.projectPermissionFull') : t('chat.projectPermissionLimited'),
    }))
    clearComposer()
    return true
  }

  return false
}

// 按日期分组的会话列表
const groupedConversations = computed(() => {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const yesterdayStart = todayStart - 86400000
  const last7Start = todayStart - 7 * 86400000

  const groups: { label: string; items: Conversation[] }[] = [
    { label: t('chat.dateToday'), items: [] },
    { label: t('chat.dateYesterday'), items: [] },
    { label: t('chat.dateLast7Days'), items: [] },
    { label: t('chat.dateEarlier'), items: [] },
  ]

  for (const conv of activeAgentConversations.value) {
    const ts = conv.lastActiveTime ? new Date(conv.lastActiveTime).getTime() : 0
    if (ts >= todayStart) groups[0].items.push(conv)
    else if (ts >= yesterdayStart) groups[1].items.push(conv)
    else if (ts >= last7Start) groups[2].items.push(conv)
    else groups[3].items.push(conv)
  }

  return groups.filter(g => g.items.length > 0)
})

const currentRuntimeModel = computed(() => {
  if (effectiveModelInfo.value) {
    const providerName = effectiveProvider.value?.name || effectiveModelProviderId.value
    const modelName = effectiveModelInfo.value.name || effectiveModelName.value
    return providerName ? `${providerName} (${modelName})` : modelName
  }
  if (defaultModel.value?.name && defaultModel.value?.modelName) {
    return `${defaultModel.value.name} (${defaultModel.value.modelName})`
  }
  return currentAgent.value?.modelName || 'default'
})

/**
 * RFC-049 PR-1-UI: whether the active runtime model supports <em>any</em> form
 * of deep thinking (OpenAI reasoning_effort / Kimi native / DeepSeek-Reasoner
 * native / Anthropic extended thinking). Drives the enable/disable state of
 * the thinking-depth toggle in ChatInput.
 *
 * Reads the broad capability (`supportsThinking`) from ProviderModelInfo,
 * populated server-side in ModelInfoDTO. The narrow `supportsReasoningEffort`
 * only covers OpenAI gpt-5/o1/o3/o4 and would wrongly gray out Kimi K2.x,
 * DeepSeek-Reasoner, and Claude — all of which legitimately support thinking.
 */
const currentModelSupportsThinking = computed<boolean>(() => {
  const providerId = effectiveModelProviderId.value
  const modelName = effectiveModelName.value
  if (!providerId || !modelName) return false
  const provider = effectiveProvider.value
  if (!provider) return false
  return Boolean(effectiveModelInfo.value?.supportsThinking)
})

const currentModelSupportsVision = computed<boolean>(() => {
  const providerId = effectiveModelProviderId.value
  const modelName = effectiveModelName.value
  if (!providerId || !modelName) return false
  const provider = effectiveProvider.value
  if (!provider) return false
  return Boolean(effectiveModelInfo.value?.supportsVision)
})

const userInitial = computed(() => (localStorage.getItem('username') || 'U').charAt(0).toUpperCase())

const activeModelValue = computed(() => {
  const conversationModelValue = buildRuntimeModelValue(
    currentConversation.value?.runtimeProviderId,
    currentConversation.value?.runtimeModelName,
  )
  if (isEligibleModelValue(conversationModelValue)) {
    return conversationModelValue
  }
  if (!currentConversation.value && isEligibleModelValue(draftRuntimeModelValue.value)) {
    return normalizeRuntimeModelValue(draftRuntimeModelValue.value)
  }
  const providerId = activeModels.value?.activeLlm?.providerId
  const model = activeModels.value?.activeLlm?.model
  return providerId && model ? `${providerId}::${model}` : ''
})

const activeModelLabel = computed(() => {
  if (!activeModelValue.value) return ''
  const match = eligibleModels.value.find(m => m.value === activeModelValue.value)
  return match?.label || ''
})

const effectiveModelProviderId = computed(() => activeModelValue.value.split('::')[0] || '')

const effectiveModelName = computed(() => activeModelValue.value.split('::')[1] || '')

const effectiveProvider = computed(() => {
  const providerId = effectiveModelProviderId.value
  return providerId ? providers.value.find((provider) => provider.id === providerId) || null : null
})

const effectiveModelInfo = computed(() => {
  const provider = effectiveProvider.value
  const modelName = effectiveModelName.value
  if (!provider || !modelName) return null
  const all = [...(provider.models || []), ...(provider.extraModels || [])]
  return all.find((model) => model.id === modelName || model.name === modelName) || null
})

const modelPromptTitle = computed(() => {
  if (!effectiveModelProviderId.value || !effectiveModelName.value) {
    return t('chat.configModelFirst')
  }
  if (effectiveProvider.value && !effectiveProvider.value.available) {
    return t('chat.modelUnavailable')
  }
  return t('chat.configModelFirst')
})

const modelPromptDesc = computed(() => {
  if (!effectiveModelProviderId.value || !effectiveModelName.value) {
    return t('chat.noActiveModel')
  }
  if (effectiveProvider.value && !effectiveProvider.value.available) {
    return t('chat.providerNotReady', { name: effectiveProvider.value.name })
  }
  return t('chat.noAvailableModel')
})

const availableProviders = computed(() =>
  providers.value.filter((p) => p.available && [...(p.models || []), ...(p.extraModels || [])].length > 0)
)

const eligibleModels = computed(() => {
  return availableProviders.value.reduce<Array<{ value: string; label: string }>>((acc, provider) => {
    const allModels = [...(provider.models || []), ...(provider.extraModels || [])]
    acc.push(...allModels.map((model) => ({
      value: `${provider.id}::${model.id}`,
      label: `${provider.name} / ${model.name || model.id}`,
    })))
    return acc
  }, [])
})

// ============ 生命周期 ============
function handleKeyboardShortcuts(e: KeyboardEvent) {
  const mod = e.metaKey || e.ctrlKey
  if (mod && e.key === 'n') {
    e.preventDefault()
    newConversation()
    chatInputRef.value?.focus?.()
  }
  if (mod && e.key === 'k') {
    e.preventDefault()
    agentDropdownOpen.value = !agentDropdownOpen.value
  }
}

// 轮询定时器：让 ChatConsole 能实时感知外部渠道（WeChat/DingTalk/…）推进来的新消息，
// 无需 F5 即可看到侧栏列表更新和选中会话的消息/流状态。
let activityPollTimer: number | null = null
const ACTIVITY_POLL_MS = 4000
const BEFORE_WORKSPACE_SWITCH_EVENT = 'mateclaw:before-workspace-switch'

/**
 * 判断当前消息列表的末尾是不是一条"本地仅有的失败气泡"。
 * 典型场景：SSE setup 阶段就抛错（如"无权操作该会话"），
 * 这次 turn 的 user / assistant 消息从未持久化进 DB。
 * 数据库快照不知道它们存在，pollActivity 的对齐会把它们冲掉。
 *
 * 识别条件：末尾 assistant 状态为 failed、带 errorInfo、且 id 不是 DB 数值 id（client uuid）。
 */
function hasLocalOnlyFailedTail(): boolean {
  const last = messages.value[messages.value.length - 1] as any
  if (!last || last.role !== 'assistant') return false
  if (last.status !== 'failed') return false
  if (!last.errorInfo) return false
  return !/^\d+$/.test(String(last.id))
}

function getHttpStatus(error: any): number {
  return Number(error?.response?.status || error?.status || 0)
}

function isForbiddenError(error: any): boolean {
  return getHttpStatus(error) === 403
}

function clearForbiddenConversation(conversationId: string, notify = true) {
  if (!conversationId) return
  if (workspaceStore.getLastConversationId() === conversationId) {
    workspaceStore.setLastConversationId(workspaceStore.currentWorkspaceId, '')
  }
  conversations.value = conversations.value.filter(conv => conv.conversationId !== conversationId)
  if (currentConversationId.value === conversationId) {
    resetForNewConversation()
    currentConversationId.value = ''
    currentHarnessRun.value = null
    messages.value = []
    setWorkingDirectoryState()
    setRuntimeModeState()
  }
  if (notify) {
    ElMessage.warning(t('chat.inaccessibleConversation'))
  }
}

async function pollActivity() {
  // 页面不可见时不轮询，避免切到别的标签还在空耗
  if (typeof document !== 'undefined' && document.hidden) return
  try {
    await loadConversations()
  } catch {
    // 静默失败，下一轮再试
  }
  // 自己没在生成时才刷新当前选中会话的消息 + 探测是否该接入流
  if (currentConversationId.value && !isGenerating.value && streamPhase.value !== 'awaiting_approval') {
    const cid = currentConversationId.value
    try {
      const statusRes: any = await conversationApi.getStatus(cid)
      if (currentConversationId.value !== cid) return
      const running = statusRes?.data?.streamStatus === 'running'
      if (running) {
        // 外部渠道正在跑：
        // 1. 先从 DB 拉消息，把刚插入的 user 消息（"你在干什么"之类）带进来，
        //    否则只接入流的话前端只能看到 assistant content_delta，看不到用户问题。
        // 2. 再接入流，让后续 content_delta 实时累积到 assistant 气泡。
        await refreshCurrentConversationMessages(cid)
        if (currentConversationId.value !== cid || isGenerating.value) return
        await reconnectStream(cid)
      } else if (!hasLocalOnlyFailedTail()) {
        // 不在跑：从 DB 对齐消息（新 user 消息 / 刚落库 assistant 会合并进来）。
        // 但若末尾是本地失败气泡（SSE setup 失败一类，后端从未持久化过），
        // 就跳过对齐 —— 不然这次的 user/失败 assistant 会被 DB 快照覆盖掉，
        // 用户除了上面的 toast 看不到任何痕迹。
        await refreshCurrentConversationMessages(cid)
      }
    } catch (error) {
      if (isForbiddenError(error)) {
        clearForbiddenConversation(cid, false)
        return
      }
      // 忽略探测失败
    }
  }
}

function handleBeforeWorkspaceSwitch() {
  routeSyncSuspended.value = true
  stopHarnessPolling()
  if (activityPollTimer !== null) {
    clearInterval(activityPollTimer)
    activityPollTimer = null
  }
  currentConversationId.value = ''
  currentHarnessRun.value = null
  messages.value = []
  resetForNewConversation()
}

onMounted(async () => {
  document.addEventListener('keydown', handleKeyboardShortcuts)
  document.addEventListener('click', handleCodeCopy)
  window.addEventListener(BEFORE_WORKSPACE_SWITCH_EVENT, handleBeforeWorkspaceSwitch)
  startECharts()
  startKatex()
  startMermaid()
  mobileQuery = window.matchMedia('(max-width: 768px)')
  handleMobileChange(mobileQuery)
  mobileQuery.addEventListener('change', handleMobileChange)
  mediumQuery = window.matchMedia('(max-width: 1200px)')
  handleConvMediumChange(mediumQuery)
  mediumQuery.addEventListener('change', handleConvMediumChange)
  try {
    await workspaceStore.fetchWorkspaces()
    await Promise.all([loadAgents(), loadModelState(), loadConversations()])
    setWorkingDirectoryState()
    await hydrateStateFromRoute()
    activityPollTimer = window.setInterval(pollActivity, ACTIVITY_POLL_MS)
  } finally {
    workspaceStateReady.value = true
  }
})

watch([currentConversationId, shouldPollHarness], ([conversationId, shouldPoll]) => {
  stopHarnessPolling()
  if (!conversationId) {
    currentHarnessRun.value = null
    return
  }
  if (!shouldPoll) {
    return
  }
  void loadHarnessRun(conversationId)
  harnessPollTimer = setInterval(() => {
    void loadHarnessRun(conversationId)
  }, 2000)
})

onBeforeUnmount(() => {
  stopHarnessPolling()
  document.removeEventListener('keydown', handleKeyboardShortcuts)
  document.removeEventListener('click', handleCodeCopy)
  window.removeEventListener(BEFORE_WORKSPACE_SWITCH_EVENT, handleBeforeWorkspaceSwitch)
  disposeECharts()
  disposeKatex()
  disposeMermaid()
  mobileQuery?.removeEventListener('change', handleMobileChange)
  mediumQuery?.removeEventListener('change', handleConvMediumChange)
  if (activityPollTimer !== null) {
    clearInterval(activityPollTimer)
    activityPollTimer = null
  }
  // Switching tabs / route changes / mouse-detach unmount this component, but the
  // backend agent should keep running so the user can reconnect later. Use
  // resetForNewConversation (front-end SSE disconnect only) instead of
  // stopChatGeneration which would POST /stop and abort the in-flight turn.
  resetForNewConversation()
  // 释放所有附件的 ObjectURL，防止内存泄漏
  revokeAllPreviewUrls()
})

watch(() => route.query, () => {
  if (routeSyncSuspended.value || workspaceSwitching.value) return
  void hydrateStateFromRoute()
})

watch(workspaceBasePath, (next) => {
  if (!currentConversation.value) {
    setWorkingDirectoryState(next)
  }
  if (!next) {
    projectDirectoryBrowser.value = null
    projectDirectoryError.value = ''
    currentProjectInsight.value = null
    currentContextRouter.value = null
    return
  }
  if (workspaceMenuOpen.value) {
    void loadProjectDirectoryBrowser(currentWorkingDirectory.value || next)
  }
  if (projectChangesPanelOpen.value) {
    void loadProjectInsight()
    void loadContextRouter()
  }
})

watch(currentWorkingDirectory, () => {
  currentProjectInsight.value = null
  currentContextRouter.value = null
  if (projectChangesPanelOpen.value) {
    void loadProjectInsight()
    void loadContextRouter()
  }
})

watch([selectedAgentId, currentConversationId], () => {
  currentContextRouter.value = null
  if (projectChangesPanelOpen.value) {
    void loadContextRouter()
  }
})

watch([selectedAgentId, currentConversationId], () => {
  if (routeSyncSuspended.value || workspaceSwitching.value) return
  void syncRouteState()
})

watch(currentAgentTemplateManifest, () => {
  teacherGuideExpanded.value = false
  teacherGuideDismissed.value = true
}, { immediate: true })

watch(activeTemplateMockTask, () => {
  mockTaskDetailsExpanded.value = false
}, { immediate: true })

function findAgentById(agentId: string | number | null | undefined) {
  if (agentId === null || agentId === undefined || agentId === '') {
    return null
  }
  return agents.value.find(agent => String(agent.id) === String(agentId)) || null
}

function getConversationRecency(conv: Conversation) {
  const candidate = conv.lastActiveTime || conv.updateTime || conv.createTime || ''
  const timestamp = candidate ? new Date(candidate).getTime() : Number.NaN
  return Number.isFinite(timestamp) ? timestamp : 0
}

function getMostRecentConversation(items: Conversation[]) {
  if (!items.length) {
    return null
  }
  return [...items].sort((a, b) => getConversationRecency(b) - getConversationRecency(a))[0] || null
}

function isLocalDraftConversation(conversationId: string) {
  return /^conv_\d+_[a-z0-9]+$/i.test(conversationId)
    && !conversations.value.some(conv => conv.conversationId === conversationId)
    && messages.value.length === 0
    && !isGenerating.value
}

function getPreferredConversation(items: Conversation[]) {
  if (!items.length) {
    return null
  }

  const lastConversationId = workspaceStore.getLastConversationId()
  if (lastConversationId) {
    const matched = items.find(conv => conv.conversationId === lastConversationId)
    if (matched) {
      return matched
    }
  }

  return getMostRecentConversation(items)
}

function getPreferredConversationForAgent(agentId: string | number | null | undefined) {
  if (agentId === null || agentId === undefined || agentId === '') {
    return getPreferredConversation(conversations.value)
  }
  return getPreferredConversation(
    conversations.value.filter(conv => String(conv.agentId || '') === String(agentId)),
  )
}

async function restorePreferredConversation(options: { matchedRouteAgent?: Agent | null } = {}) {
  const { matchedRouteAgent = null } = options
  const defaultAgent = findAgentById(workspaceDefaultAgentId.value)
  const preferredConversation = getPreferredConversationForAgent(selectedAgentId.value)

  if (preferredConversation) {
    await selectConversation(preferredConversation)
    return
  }

  if (defaultAgent) {
    if (String(selectedAgentId.value) !== String(defaultAgent.id)) {
      selectedAgentId.value = defaultAgent.id
    }
    newConversation()
    return
  }

  currentConversationId.value = ''
  messages.value = []
  currentHarnessRun.value = null
  if (!workspaceDefaultAgentId.value) {
    selectedAgentId.value = matchedRouteAgent?.id || ''
  }
}

function resolveSelectedAgent(options: { allowFirstFallback?: boolean } = {}) {
  const { allowFirstFallback = false } = options
  const matchedCurrent = findAgentById(selectedAgentId.value)

  if (matchedCurrent) {
    selectedAgentId.value = matchedCurrent.id
    return matchedCurrent
  }
  const matchedDefault = findAgentById(workspaceDefaultAgentId.value)
  if (matchedDefault) {
    selectedAgentId.value = matchedDefault.id
    return matchedDefault
  }
  if (allowFirstFallback && agents.value.length > 0) {
    selectedAgentId.value = agents.value[0].id
    return agents.value[0]
  }
  selectedAgentId.value = ''
  return null
}

async function loadAgents() {
  agentsLoading.value = true
  try {
    const res: any = await agentApi.list()
    agents.value = res.data || []
    if (!agents.value.length && workspaceStore.currentWorkspaceId) {
      await ensureAutoCreatedDefaultAgent()
    }
    if (workspaceDefaultAgentId.value && !findAgentById(workspaceDefaultAgentId.value)) {
      workspaceStore.setDefaultAgentId(workspaceStore.currentWorkspaceId, '')
    }
    resolveSelectedAgent()
    if (!currentConversation.value) {
      setRuntimeModeState(undefined, currentAgent.value || null)
    }
  } catch (e) {
    agents.value = []
    selectedAgentId.value = ''
    if (getAuthToken() && !isAuthRedirectInProgress()) {
      ElMessage.error(t('chat.loadAgentsFailed'))
    }
  } finally {
    agentsLoading.value = false
  }
}

async function loadModelState() {
  try {
    const [defaultRes, providersRes, activeRes]: any = await Promise.all([
      modelApi.getDefault(),
      modelApi.listProviders(),
      modelApi.getActive(),
    ])
    defaultModel.value = defaultRes.data || null
    providers.value = providersRes.data || []
    activeModels.value = activeRes.data || null
    if (!currentConversation.value && draftRuntimeModelValue.value && !isEligibleModelValue(draftRuntimeModelValue.value)) {
      setDraftRuntimeModelState()
    }
    if (currentConversation.value) {
      void reconcileConversationRuntimeModelIfNeeded(currentConversation.value, true)
    }
    showModelPrompt.value = !effectiveModelProviderId.value
      || !effectiveModelName.value
      || (Boolean(effectiveModelProviderId.value) && !effectiveProvider.value?.available)
  } catch (e) {
    if (getAuthToken() && !isAuthRedirectInProgress()) {
      ElMessage.error(t('chat.loadModelFailed'))
    }
    showModelPrompt.value = true
  }
}

async function loadConversations() {
  try {
    const res: any = await conversationApi.list()
    conversations.value = res.data || []
    const lastConversationId = workspaceStore.getLastConversationId()
    if (lastConversationId && !conversations.value.some(conv => conv.conversationId === lastConversationId)) {
      workspaceStore.setLastConversationId(workspaceStore.currentWorkspaceId, '')
    }
    if (currentConversationId.value) {
      const active = conversations.value.find(conv => conv.conversationId === currentConversationId.value)
      if (!active) {
        if (!isLocalDraftConversation(currentConversationId.value)) {
          currentConversationId.value = ''
          messages.value = []
          currentHarnessRun.value = null
          setDraftRuntimeModelState()
        }
      }
      if (active && workingDirectoryDraft.value === currentWorkingDirectory.value) {
        setWorkingDirectoryState(active.workingDirectory)
      }
      if (active) {
        setRuntimeModeState(active.runtimeMode)
        setDraftRuntimeModelState()
      }
    }
  } catch (e) {
    if (getAuthToken() && !isAuthRedirectInProgress()) {
      ElMessage.error(t('chat.loadConversationsFailed'))
    }
  }
}

async function refreshCurrentConversationMessages(conversationId: string) {
  if (!conversationId) return
  if (isGenerating.value) return
  if (streamPhase.value === 'awaiting_approval') return
  try {
    const res: any = await conversationApi.listMessages(conversationId)
    // Stale guard：await 返回后确认仍是当前会话
    if (currentConversationId.value !== conversationId) return
    // 二次 isGenerating 检查：如果 await 期间用户已发新消息，不覆盖本地状态
    if (isGenerating.value) return
    const fetched = extractMessages(res).messages.map((msg: Message) => normalizeMessage(msg))
    // 严格过滤：只保留 conversationId 完全匹配的本地消息，orphan（空 conversationId）直接丢弃
    const currentMessages = messages.value.filter(
      (m: any) => m.conversationId === conversationId
    )
    messages.value = reconcileMessages(currentMessages, fetched)
    if (currentHarnessRun.value?.conversationId === conversationId) {
      mergeLatestAssistantValidationSummaryFromHarness(currentHarnessRun.value)
    }
  } catch (e) {
    console.warn('[ChatConsole] Failed to refresh current conversation messages:', e)
  }
}

async function hydrateStateFromRoute() {
  const routeAgentId = route.query.agentId ? String(route.query.agentId) : ''
  const conversationId = String(route.query.conversationId || '')
  const draftMessage = route.query.draftMessage ? String(route.query.draftMessage) : ''
  const routeMockTask = parseTemplateMockTaskFromRoute()
  const matchedRouteAgent = findAgentById(routeAgentId)

  if (matchedRouteAgent) {
    if (String(matchedRouteAgent.id) !== String(selectedAgentId.value)) {
      selectedAgentId.value = matchedRouteAgent.id
    }
  } else {
    resolveSelectedAgent()
  }

  if (draftMessage && !conversationId) {
    newConversation({ preferWorkspaceDefault: false })
    if (routeMockTask) activeTemplateMockTask.value = routeMockTask
    inputText.value = draftMessage
  } else if (draftMessage && !inputText.value.trim()) {
    if (routeMockTask) activeTemplateMockTask.value = routeMockTask
    inputText.value = draftMessage
  }

  if (conversationId && conversationId !== currentConversationId.value) {
    const matchedConversation = conversations.value.find(conv => conv.conversationId === conversationId)
    if (matchedConversation) {
      await selectConversation(matchedConversation)
    } else {
      setDraftRuntimeModelState()
      // 会话不在已加载列表中（可能来自 Sessions 页面跳转），尝试加载消息
      currentConversationId.value = conversationId
      messages.value = []
      let loadedFromDetachedRoute = false
      try {
        const res: any = await conversationApi.listMessages(conversationId)
        if (currentConversationId.value !== conversationId) return
        messages.value = extractMessages(res).messages.map((msg: Message) => normalizeMessage(msg))
        loadedFromDetachedRoute = true
      } catch (error) {
        if (isForbiddenError(error)) {
          clearForbiddenConversation(conversationId)
        }
        currentConversationId.value = ''
        messages.value = []
      }
      if (loadedFromDetachedRoute) {
        try {
          if (currentConversationId.value !== conversationId) return
          const statusRes: any = await conversationApi.getStatus(conversationId)
          if (currentConversationId.value === conversationId && statusRes.data?.streamStatus === 'running') {
            await reconnectStream(conversationId)
          }
        } catch {
          // 忽略
        }
        await loadHarnessRun(conversationId)
        workspaceStore.setLastConversationId(workspaceStore.currentWorkspaceId, conversationId)
      } else {
        workspaceStore.setLastConversationId(workspaceStore.currentWorkspaceId, '')
        if (matchedRouteAgent && String(selectedAgentId.value) !== String(matchedRouteAgent.id)) {
          selectedAgentId.value = matchedRouteAgent.id
        }
        newConversation({ preferWorkspaceDefault: !matchedRouteAgent })
      }
    }
  } else if (!conversationId && !draftMessage) {
    await restorePreferredConversation({ matchedRouteAgent })
  }

  // 如果仍然没选中 agent，优先恢复工作区默认值
  if (!selectedAgentId.value) {
    resolveSelectedAgent()
  }

  const effectiveAgentId = selectedAgentId.value ? String(selectedAgentId.value) : ''
  const effectiveConversationId = currentConversationId.value || ''
  if (routeAgentId !== effectiveAgentId || conversationId !== effectiveConversationId) {
    await syncRouteState()
  }
}

function parseTemplateMockTaskFromRoute(): ActiveTemplateMockTask | null {
  if (String(route.query.templateMock || '') !== '1') return null
  const expected = String(route.query.mockExpected || '')
    .split('\n- ')
    .map(item => item.trim())
    .filter(Boolean)
  return {
    templateId: String(route.query.templateId || ''),
    templateName: String(route.query.templateName || t('chat.mockTask.templateFallback')),
    taskId: String(route.query.mockTaskId || ''),
    taskIndex: Number(route.query.mockTaskIndex || 0),
    title: String(route.query.mockTaskTitle || t('chat.mockTask.titleFallback')),
    expected,
  }
}

async function syncRouteState() {
  const query: Record<string, string> = {}
  if (selectedAgentId.value) query.agentId = String(selectedAgentId.value)
  if (currentConversationId.value) query.conversationId = currentConversationId.value
  await router.replace({ path: '/chat', query })
}

async function selectConversation(conv: Conversation) {
  if (isMobile.value) convPanelOpen.value = false
  // 切换到不同会话：只清理本地 UI/SSE（resetForNewConversation 会 stream.disconnect + 清变量），
  // 但不 POST /chat/{A}/stop —— 让 A 的后台 agent run 跑到完成。
  // 用户之后回到 A：pollActivity / selectConversation 的 /status 探测会自动 reconnect 接回实时流；
  // 若 A 已完成，refreshCurrentConversationMessages 会从 DB 拉完整结果。
  // 点同一个会话则完全不 reset，避免打断正在观察的流。
  const switchingAway = currentConversationId.value !== conv.conversationId
  if (switchingAway) {
    resetForNewConversation()
    activeTemplateMockTask.value = null
  }
  setDraftRuntimeModelState()
  currentConversationId.value = conv.conversationId
  currentHarnessRun.value = null
  selectedAgentId.value = conv.agentId || selectedAgentId.value
  workspaceStore.setLastConversationId(workspaceStore.currentWorkspaceId, conv.conversationId)
  setWorkingDirectoryState(conv.workingDirectory)
  setRuntimeModeState(conv.runtimeMode)
  const requestedConvId = conv.conversationId
  try {
    const res: any = await conversationApi.listMessages(requestedConvId)
    // Stale guard：await 返回后确认仍是当前会话，否则丢弃
    if (currentConversationId.value !== requestedConvId) return
    // 点同一个会话时，若已有 SSE 在跑就不要覆盖本地消息状态
    if (switchingAway || !isGenerating.value) {
      messages.value = extractMessages(res).messages.map((msg: Message) => normalizeMessage(msg))
    }
    await reconcileConversationRuntimeModelIfNeeded(conv, true)

    // Hydrate pending approvals：恢复刷新后丢失的审批卡片（RFC-067 §4.9）
    //
    // Two-way reconciliation between the server's pending list and each
    // message's metadata.pendingApproval:
    //   1. Forward — server pending → align onto the message that already
    //      carries the same pendingId (so multi-pending convs don't have
    //      every banner overwrite the same row); fallback to last assistant
    //      only when no message has that id yet.
    //   2. Reverse — local message metadata still says pending_approval but
    //      the server no longer lists that pendingId → flip to 'expired'
    //      locally. This closes the GC/timeout loop without requiring an
    //      extra server-side broadcast: the next refresh sees a clean state.
    try {
      const approvalRes: any = await chatApi.getPendingApprovals(requestedConvId)
      if (currentConversationId.value !== requestedConvId) return
      const pendingApprovals: any[] = approvalRes.data || []

      // Index existing messages by their embedded pendingId (assistant only).
      const indexById = new Map<string, Message>()
      for (const m of messages.value) {
        if (m.role !== 'assistant') continue
        const pid = (m as any).metadata?.pendingApproval?.pendingId
        if (typeof pid === 'string' && pid) indexById.set(pid, m)
      }

      // Forward direction: align server-known pending onto its owning message.
      for (const pa of pendingApprovals) {
        const enriched = {
          pendingId: pa.pendingId,
          toolName: pa.toolName,
          arguments: pa.toolArguments,
          reason: pa.reason,
          status: 'pending_approval' as const,
          findings: pa.findingsJson ? JSON.parse(pa.findingsJson) : undefined,
          maxSeverity: pa.maxSeverity || undefined,
          summary: pa.summary || undefined,
        }
        const target = indexById.get(pa.pendingId)
        if (target) {
          (target as any).metadata = {
            ...(target as any).metadata,
            currentPhase: 'awaiting_approval',
            pendingApproval: enriched,
          }
        } else {
          // Fallback: no message in the loaded history claims this pendingId
          // (typical when the assistant message hasn't been persisted yet —
          // e.g., approval fired before doOnComplete). Append to the last
          // assistant; same as pre-RFC behavior, but logged so a regression
          // where multiple unmatched pendings collide is observable.
          const assistantMessages = messages.value.filter(m => m.role === 'assistant')
          const lastAssistant = assistantMessages[assistantMessages.length - 1]
          if (lastAssistant) {
            console.warn('[hydrate] pendingId %s has no owning message — falling back to last assistant', pa.pendingId)
            ;(lastAssistant as any).metadata = {
              ...(lastAssistant as any).metadata,
              currentPhase: 'awaiting_approval',
              pendingApproval: enriched,
            }
          }
        }
      }

      // Reverse direction: any local pending_approval whose pendingId is not
      // in the server's list got resolved (timeout / consume) without a UI
      // event — flip to expired so MessageBubble hides the banner.
      const serverIds = new Set<string>(pendingApprovals.map((p: any) => p.pendingId))
      for (const m of messages.value) {
        if (m.role !== 'assistant') continue
        const meta = (m as any).metadata
        const local = meta?.pendingApproval
        if (local?.status === 'pending_approval'
            && local.pendingId
            && !serverIds.has(local.pendingId)) {
          (m as any).metadata = {
            ...meta,
            pendingApproval: { ...local, status: 'expired' },
          }
        }
      }
    } catch {
      // hydration 失败不影响正常使用
    }

    // 决定是否重连 SSE：
    // - 快照 streamStatus==='running' → 直接重连
    // - 否则探测实时状态（兜底处理：渠道消息进入后侧栏快照未刷新时，仍能接入运行中的流）
    let shouldReconnect = conv.streamStatus === 'running'
    if (!shouldReconnect) {
      try {
        const statusRes: any = await conversationApi.getStatus(requestedConvId)
        if (currentConversationId.value !== requestedConvId) return
        shouldReconnect = statusRes?.data?.streamStatus === 'running'
      } catch {
        // 探测失败不阻断主流程
      }
    }
    if (currentConversationId.value === requestedConvId && shouldReconnect) {
      await reconnectStream(requestedConvId)
    }
    await loadHarnessRun(requestedConvId)
  } catch (e) {
    if (isForbiddenError(e)) {
      clearForbiddenConversation(requestedConvId)
      return
    }
    if (workspaceSwitching.value || currentConversationId.value !== requestedConvId) {
      return
    }
    ElMessage.error(t('chat.loadMessagesFailed'))
  }
}

function newConversation(optionsOrEvent: { preferWorkspaceDefault?: boolean } | Event = {}) {
  const options = optionsOrEvent instanceof Event ? {} : optionsOrEvent
  const { preferWorkspaceDefault = true } = options
  resetStreamingState()
  resetHarnessState()
  activeTemplateMockTask.value = null

  if (preferWorkspaceDefault) {
    const defaultAgent = findAgentById(workspaceDefaultAgentId.value)
    if (defaultAgent) {
      selectedAgentId.value = defaultAgent.id
    } else if (!findAgentById(selectedAgentId.value)) {
      resolveSelectedAgent({ allowFirstFallback: true })
    }
  } else if (!findAgentById(selectedAgentId.value)) {
    resolveSelectedAgent({ allowFirstFallback: true })
  }

  currentConversationId.value = `conv_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  workspaceStore.setLastConversationId(workspaceStore.currentWorkspaceId, currentConversationId.value)
  messages.value = []
  setWorkingDirectoryState()
  setRuntimeModeState()
  setDraftRuntimeModelState()
}

async function deleteConversation(conversationId: string) {
  try {
    const deletingConversation = conversations.value.find(c => c.conversationId === conversationId)
    await conversationApi.delete(conversationId)
    conversations.value = conversations.value.filter(c => c.conversationId !== conversationId)
    if (workspaceStore.getLastConversationId() === conversationId) {
      const nextLastConversation = getPreferredConversationForAgent(deletingConversation?.agentId || selectedAgentId.value)
      workspaceStore.setLastConversationId(
        workspaceStore.currentWorkspaceId,
        nextLastConversation?.conversationId || '',
      )
    }
    if (currentConversationId.value === conversationId) {
      resetStreamingState()
      resetHarnessState()
      messages.value = []
      currentConversationId.value = ''
      setWorkingDirectoryState()
      setRuntimeModeState()
      setDraftRuntimeModelState()
    }
  } catch (e) {
    ElMessage.error(t('chat.deleteConversationFailed'))
  }
}

async function clearMessages() {
  if (!currentConversationId.value) return
  try {
    resetStreamingState()
    await conversationApi.clearMessages(currentConversationId.value)
    messages.value = []
    currentHarnessRun.value = null
  } catch {
    messages.value = []
    currentHarnessRun.value = null
  }
}

// onAgentChange removed — replaced by selectAgent()

// onModelChange removed — replaced by selectModel()

function goToModelSettings() {
  if (!isAdmin.value) return
  router.push('/settings/models')
}

// ============ 计算属性：是否有待审批 ============
const hasPendingApproval = computed(() => {
  return messages.value.some(
    m => m.role === 'assistant' && (m as any).metadata?.pendingApproval?.status === 'pending_approval'
  )
})

// 当前待审批的那条数据（传给 ChatInput 用于渲染审批栏）
const activePendingApproval = computed(() => {
  const msg = messages.value.findLast(
    m => m.role === 'assistant' && (m as any).metadata?.pendingApproval?.status === 'pending_approval'
  )
  return (msg as any)?.metadata?.pendingApproval ?? null
})

// 当前工具调用数
const toolCallCount = computed(() => {
  const lastMsg = messages.value.findLast(m => m.role === 'assistant')
  return lastMsg?.metadata?.toolCalls?.length ?? 0
})

// 当前正在执行的工具名称
const currentRunningToolName = computed(() => {
  if (!isGenerating.value) return ''
  const lastMsg = messages.value.findLast(m => m.role === 'assistant')
  const metadata = lastMsg?.metadata
  if (metadata?.runningToolName) return metadata.runningToolName
  const runningTool = metadata?.toolCalls?.findLast((tc: any) => tc.status === 'running')
  return runningTool?.name || heartbeat.value?.runningToolName || ''
})

// 当前正在生成的消息的 token 数据
const currentGeneratingTokens = computed(() => {
  if (!isGenerating.value) return 0
  // 找到最后一条 assistant 消息（可能仍在生成）
  const lastMsg = messages.value.findLast(m => m.role === 'assistant')
  // 返回 completionTokens（从服务器响应中获取）
  return (lastMsg as any)?.completionTokens ?? 0
})

const currentPromptTokens = computed(() => {
  if (!isGenerating.value) return 0
  const lastMsg = messages.value.findLast(m => m.role === 'assistant')
  return (lastMsg as any)?.promptTokens ?? 0
})

// ============ 消息发送和处理 ============
async function handleSendMessage(content: string, options: { approvalScope?: ApprovalDecisionScope } = {}) {
  // 允许在等待审批时发送审批命令
  const isApprovalCommand = /^\/(approve|deny)$/i.test(content.trim())

  if ((!content && pendingAttachments.value.length === 0) || showModelPrompt.value) return
  if (isCurrentConversationAgentDeleted.value) {
    ElMessage.warning(t('chat.deletedAgentReadonlyHint'))
    return
  }
  if (!selectedAgentId.value && !isApprovalCommand) {
    ElMessage.warning(t('chat.selectAgent'))
    return
  }
  // 不再阻止运行中发送 — useChat 会自动走 interrupt/queue 路径

  // 拦截 /approve 和 /deny 命令 —— 通过 SSE 流发送（和普通消息相同通道）
  const trimmed = content.trim().toLowerCase()
  if (trimmed === '/approve' || trimmed === '/deny') {
    if (!currentConversationId.value) {
      ElMessage.warning('No active conversation')
      inputText.value = ''
      chatInputRef.value?.clear?.()
      return
    }

    // 检查是否有 pending approval
    const pendingMsg = messages.value.findLast(
      m => m.role === 'assistant' && (m as any).metadata?.pendingApproval?.status === 'pending_approval'
    )
    if (!pendingMsg) {
      ElMessage.warning('No pending approval to process')
      inputText.value = ''
      chatInputRef.value?.clear?.()
      return
    }

    // 乐观更新审批状态
    const decision = trimmed === '/approve' ? 'approved' : 'denied'
    ;(pendingMsg as any).metadata.pendingApproval.status = decision

    inputText.value = ''
    chatInputRef.value?.clear?.()

    // 通过正常 SSE 流发送（复用聊天通道，replay 结果实时流式推送）
    try {
      await sendChatMessage(trimmed, {
        conversationId: currentConversationId.value,
        agentId: selectedAgentId.value,
        contentParts: [],
        workingDirectory: buildWorkingDirectoryPayload(),
        runtimeMode: currentRuntimeMode.value,
        runtimeProviderId: effectiveModelProviderId.value || undefined,
        runtimeModelName: effectiveModelName.value || undefined,
        approvalScope: options.approvalScope,
        mockTaskMetadata: activeTemplateMockTask.value ? {
          templateId: activeTemplateMockTask.value.templateId,
          templateName: activeTemplateMockTask.value.templateName,
          taskId: activeTemplateMockTask.value.taskId,
          taskIndex: activeTemplateMockTask.value.taskIndex,
          title: activeTemplateMockTask.value.title,
          expected: activeTemplateMockTask.value.expected,
        } : undefined,
      })
    } catch (e: any) {
      console.error('Approval stream failed:', e)
      // 回滚乐观更新
      ;(pendingMsg as any).metadata.pendingApproval.status = 'pending_approval'
      ElMessage.error(e?.message || 'Approval failed')
    }
    return
  }

  if (await handleSlashCommand(content)) {
    return
  }

  if (!currentConversationId.value) {
    currentConversationId.value = `conv_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
  }

  if (!effectiveModelProviderId.value || !effectiveModelName.value) {
    showModelPrompt.value = true
    return
  }
  if (!effectiveProvider.value?.available) {
    showModelPrompt.value = true
    return
  }

  const outgoingAttachments = pendingAttachments.value.map((attachment) => ({ ...attachment }))
  const contentParts = buildOutgoingParts(content, outgoingAttachments)

  // 先暂存，发送成功后再清空（失败时恢复）
  const savedInput = inputText.value
  const savedAttachments = [...pendingAttachments.value]
  // Stash for async-error recovery in onStreamEnd (sync catch can't reach this).
  pendingSendDraft.value = { input: savedInput, attachments: savedAttachments }
  inputText.value = ''
  chatInputRef.value?.clear?.()
  pendingAttachments.value = []

  try {
    await sendChatMessage(content, {
      conversationId: currentConversationId.value,
      agentId: selectedAgentId.value,
      contentParts,
      thinkingLevel: thinkingLevel.value,
      workingDirectory: buildWorkingDirectoryPayload(),
      runtimeMode: currentRuntimeMode.value,
      runtimeProviderId: effectiveModelProviderId.value || undefined,
      runtimeModelName: effectiveModelName.value || undefined,
      mockTaskMetadata: activeTemplateMockTask.value ? {
        templateId: activeTemplateMockTask.value.templateId,
        templateName: activeTemplateMockTask.value.templateName,
        taskId: activeTemplateMockTask.value.taskId,
        taskIndex: activeTemplateMockTask.value.taskIndex,
        title: activeTemplateMockTask.value.title,
        expected: activeTemplateMockTask.value.expected,
      } : undefined,
      executionSelection: pendingExecutionSelection.value || undefined,
      attachments: outgoingAttachments.map(a => ({
        type: 'file' as const,
        fileUrl: a.url,
        fileName: a.name,
        storedName: a.storedName,
        contentType: a.contentType,
        fileSize: a.size,
        path: a.path,
        contextStrategy: a.contextStrategy,
        contextHint: a.contextHint,
      })),
    })
    pendingExecutionSelection.value = null
    // 发送成功后释放 ObjectURL
    revokeAllPreviewUrls()
  } catch (e) {
    console.error('Send message failed:', e)
    // 发送失败：恢复输入和附件，用户不丢失已上传的文件
    if (!inputText.value) inputText.value = savedInput
    if (pendingAttachments.value.length === 0) pendingAttachments.value = savedAttachments
  }
}

function handleStopStream() {
  stopChatGeneration()
}

function handleRegenerate(message: Message) {
  if (isGenerating.value) return
  const idx = messages.value.indexOf(message)
  if (idx >= 0) {
    messages.value.splice(idx, 1)
  }
  const lastUserMsg = messages.value.findLast(m => m.role === 'user')
  if (!lastUserMsg) return

  const text = lastUserMsg.contentParts
    .filter(p => p.type === 'text')
    .map(p => p.text || '')
    .join('\n') || lastUserMsg.content || ''

  handleSendMessage(text)
}

function sendSuggestion(text: string) {
  inputText.value = text
  void handleSendMessage(text)
}

function handleToggleThinking(message: Message, expanded: boolean) {
  message.thinkingExpanded = expanded
}

// ============ 审批处理 ============
async function handleApprove(payload: string | ApprovalDecisionPayload) {
  if (!currentConversationId.value) return
  const scope = typeof payload === 'string' ? 'once' : (payload.scope || 'once')
  await handleSendMessage('/approve', { approvalScope: scope })
}

async function handleDeny(pendingId: string) {
  if (!currentConversationId.value) return
  await handleSendMessage('/deny')
}

// 重连到运行中的流
async function reconnectStream(conversationId: string) {
  if (isGenerating.value) return
  try {
    await reconnectChatStream(conversationId)
  } catch (e) {
    console.error('[ChatConsole] Reconnect failed:', e)
    ElMessage.warning(t('chat.reconnectFailed') || 'Stream reconnection failed')
  }
}

function handleCancelQueued() {
  cancelQueued()
}

// 简化版重置函数
function resetStreamingState() {
  // 先通知后端停止旧流（fire-and-forget），再彻底清理前端状态
  stopChatGeneration()
  resetForNewConversation()
}

// ============ 附件处理 ============
async function handleFileSelect(files: File[]) {
  if (!currentConversationId.value) {
    newConversation()
  }

  const hasImage = files.some(file => (file.type || '').startsWith('image/'))
  if (hasImage && !currentModelSupportsVision.value) {
    ElMessage.warning(t('chat.imageVisionUnsupported', {
      model: activeModelLabel.value || activeModels.value?.activeLlm?.model || 'current model'
    }))
  }

  uploadingAttachment.value = true
  try {
    for (const file of files) {
      const res: any = await chatApi.uploadFile(currentConversationId.value, file)
      const data = res.data || {}
      // 图片/视频使用本地 ObjectURL 预览（避免 /api/v1/chat/files/ 需要 JWT 认证导致加载失败）
      const ct = data.contentType || file.type || ''
      const isPreviewable = ct.startsWith('image/') || ct.startsWith('video/')
      const previewUrl = isPreviewable ? URL.createObjectURL(file) : data.url
      pendingAttachments.value.push({
        name: data.fileName || file.name,
        size: data.size || file.size,
        url: data.url,
        storedName: data.storedName,
        path: data.path,
        contentType: data.contentType || file.type,
        contextStrategy: data.contextStrategy,
        contextHint: data.contextHint,
        previewUrl,
      })
    }
  } catch (e) {
    ElMessage.error(t('chat.uploadFailed'))
  } finally {
    uploadingAttachment.value = false
  }
}

function removeAttachment(key: string) {
  // revoke 被移除附件的 ObjectURL，防止内存泄漏
  const removed = pendingAttachments.value.find(a => a.storedName === key || a.path === key)
  if (removed?.previewUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(removed.previewUrl)
  }
  pendingAttachments.value = pendingAttachments.value.filter(
    a => a.storedName !== key && a.path !== key
  )
}

/** 释放所有 pending 附件的 ObjectURL */
function revokeAllPreviewUrls() {
  for (const a of pendingAttachments.value) {
    if (a.previewUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(a.previewUrl)
    }
  }
}

function buildOutgoingParts(text: string, attachments: ChatAttachment[]): MessageContentPart[] {
  const parts: MessageContentPart[] = []
  if (text) parts.push({ type: 'text', text })
  for (const attachment of attachments) {
    const ct = attachment.contentType || ''
    const partType: MessageContentPart['type'] = ct.startsWith('video/') ? 'video'
      : ct.startsWith('image/') ? 'image'
      : 'file'
    parts.push({
      type: partType,
      fileUrl: attachment.url,
      fileName: attachment.name,
      storedName: attachment.storedName,
      contentType: attachment.contentType,
      fileSize: attachment.size,
      path: attachment.path,
      contextStrategy: attachment.contextStrategy,
      contextHint: attachment.contextHint,
    })
  }
  return parts
}

// ============ 工具函数 ============
function normalizeMessage(raw: Message): Message {
  const msg: Message = { ...raw, contentParts: raw.contentParts ? [...raw.contentParts] : [] }

  // 统一解析 metadata：确保是对象而非 JSON 字符串
  // 注意：后端 metadata 在 DB 中是 JSON 字符串，Jackson 序列化时可能双重编码
  if (typeof msg.metadata === 'string') {
    try {
      let parsed = JSON.parse(msg.metadata)
      // 处理双重编码：parse 后仍然是字符串的情况
      if (typeof parsed === 'string') {
        try { parsed = JSON.parse(parsed) } catch { /* ignore */ }
      }
      msg.metadata = parsed
    } catch { msg.metadata = {} as any }
  }

  // 保留后端返回的 token 字段（MessageVO 新增）
  if ((raw as any).promptTokens) msg.promptTokens = (raw as any).promptTokens
  if ((raw as any).completionTokens) msg.completionTokens = (raw as any).completionTokens

  if (msg.contentParts.length === 0 && msg.content) {
    if (msg.role === 'assistant') {
      const parsed = parseThinkingContent(msg.content)
      // thinkingLevel=off 时不展示 thinking 内容，直接剥离 <think> 标签
      if (parsed.thinking && thinkingLevel.value !== 'off') {
        msg.contentParts.push({ type: 'thinking', text: parsed.thinking })
      }
      if (parsed.content) msg.contentParts.push({ type: 'text', text: parsed.content })
      msg.content = parsed.content
    } else {
      msg.contentParts.push({ type: 'text', text: msg.content })
    }
  }

  // 从 tool_call contentParts 还原 metadata.toolCalls
  const toolCallParts = msg.contentParts.filter(p => p.type === 'tool_call')
  if (toolCallParts.length > 0) {
    const toolCalls: ToolCallMeta[] = []
    for (const part of toolCallParts) {
      try {
        const parsed = JSON.parse(part.text || '{}')
        toolCalls.push({
          name: parsed.name || '',
          arguments: parsed.arguments,
          result: parsed.result,
          success: parsed.success,
          sourceSkillName: parsed.sourceSkillName,
          sourceSkillKey: parsed.sourceSkillKey,
          // 历史消息中不应有 running 状态的工具调用（流已结束）
          status: 'completed',
        })
      } catch {
        // skip malformed tool_call parts
      }
    }
    if (toolCalls.length > 0) {
      msg.metadata = { ...msg.metadata, toolCalls }
    }
    msg.contentParts = msg.contentParts.filter(p => p.type !== 'tool_call')
  }

  // 历史消息的 metadata.toolCalls 也需要清理 running 状态
  if (msg.metadata?.toolCalls) {
    const cleaned = msg.metadata.toolCalls.map((tc: ToolCallMeta) => ({
      ...tc,
      status: tc.status === 'running' ? 'completed' as const : tc.status,
    }))
    msg.metadata = { ...msg.metadata, toolCalls: cleaned }
  }

  if (msg.status === 'generating') msg.status = 'failed'
  // interrupted 是合法的历史状态（interrupt-with-followup），不映射为 stopped
  if (!msg.status) msg.status = 'completed'

  // 从 file/image/video contentParts 恢复 attachments（历史消息 API 不返回单独的 attachments 字段）
  const fileParts = msg.contentParts.filter(p => (p.type === 'file' || p.type === 'image' || p.type === 'video') && p.fileUrl)
  if (fileParts.length > 0 && (!msg.attachments || msg.attachments.length === 0)) {
    msg.attachments = fileParts.map(p => ({
      name: p.fileName || 'unknown',
      size: typeof p.fileSize === 'number' ? p.fileSize : Number(p.fileSize) || 0,
      url: p.fileUrl!,
      storedName: p.storedName || '',
        path: p.path || '',
        contentType: p.contentType,
        contextStrategy: p.contextStrategy,
        contextHint: p.contextHint,
      }))
  }

  // 从持久化的 [错误] 文本重建 errorInfo，使刷新后也能显示错误卡片
  if (msg.role === 'assistant' && !msg.errorInfo) {
    const text = msg.content || msg.contentParts?.find(p => p.type === 'text')?.text || ''
    const rebuilt = reconstructErrorInfo(text)
    if (rebuilt) {
      msg.errorInfo = rebuilt
      msg.status = 'failed'
    }
  }

  return msg
}

function parseThinkingContent(raw: string): { content: string; thinking: string; hasThinking: boolean } {
  if (!raw) return { content: '', thinking: '', hasThinking: false }

  const normalized = raw.replace(/<thinking>/gi, '<think>').replace(/<\/thinking>/gi, '</think>')
  const thinkingParts: string[] = []
  const content = normalized.replace(/<think>([\s\S]*?)<\/think>/gi, (_, thinkingText: string) => {
    const cleanText = thinkingText.trim()
    if (cleanText) thinkingParts.push(cleanText)
    return ''
  }).trim()

  return {
    content,
    thinking: thinkingParts.join('\n\n').trim(),
    hasThinking: thinkingParts.length > 0,
  }
}

function formatConversationTime(time?: string) {
  if (!time) return t('chat.timeJustNow')
  const date = new Date(time)
  const diff = Date.now() - date.getTime()
  if (diff < 60 * 60 * 1000) return t('chat.timeMinutesAgo', { n: Math.max(1, Math.floor(diff / (60 * 1000))) })
  if (diff < 24 * 60 * 60 * 1000) return t('chat.timeHoursAgo', { n: Math.floor(diff / (60 * 60 * 1000)) })
  return date.toLocaleDateString()
}

function handleCodeCopy(e: MouseEvent) {
  const btn = (e.target as HTMLElement).closest('.code-block__copy') as HTMLElement | null
  if (!btn) return
  // The copy button now sits inside <details><summary> for collapsible code
  // blocks. Without preventDefault the click would also toggle the details
  // open state — a regression introduced when we wrapped long blocks in
  // <details>. stopPropagation guards against any future ancestor handlers.
  e.preventDefault()
  e.stopPropagation()
  const encoded = btn.getAttribute('data-code')
  if (!encoded) return
  const code = decodeURIComponent(encoded)
  navigator.clipboard.writeText(code).then(() => {
    btn.classList.add('copied')
    const textEl = btn.querySelector('.code-block__copy-text')
    if (textEl) textEl.textContent = t('chat.copied')
    setTimeout(() => {
      btn.classList.remove('copied')
      if (textEl) textEl.textContent = t('chat.copy')
    }, 1500)
  }).catch(() => {
    ElMessage.error(t('chat.copyFailed'))
  })
}
</script>

<style scoped>
.chat-console-shell {
  background: transparent;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.chat-console-frame {
  height: min(calc(100vh - 28px), 100%);
  min-height: 0;
  overflow: hidden;
}

.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
  min-height: 0;
}

.conversation-panel {
  width: 248px;
  min-width: 248px;
  background: linear-gradient(180deg, var(--mc-panel-top), var(--mc-panel-bottom));
  border-right: 1px solid var(--mc-border-light);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.25s ease, min-width 0.25s ease;
}

.conversation-panel.conv-collapsed {
  width: 54px;
  min-width: 54px;
}

.conversation-panel.conv-collapsed .panel-header {
  justify-content: center;
  padding: 14px 8px 12px;
}

.conversation-panel.conv-collapsed .agent-selector {
  padding: 10px 6px 12px;
}

.conversation-panel.conv-collapsed .agent-select-trigger {
  justify-content: center;
  padding: 8px;
}

.conversation-panel.conv-collapsed .agent-dropdown {
  position: fixed;
  top: auto;
  left: 62px;
  right: auto;
  min-width: 260px;
}

.conversation-panel.conv-collapsed .conv-item {
  justify-content: center;
  padding: 10px 6px;
}

.conversation-panel.conv-collapsed .conv-icon {
  margin: 0;
}

.conv-collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 28px;
  border: none;
  border-bottom: 1px solid var(--mc-border-light);
  background: transparent;
  color: var(--mc-text-tertiary);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.conv-collapse-btn:hover {
  background: var(--mc-bg-muted);
  color: var(--mc-text-primary);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 12px;
  border-bottom: 1px solid var(--mc-border-light);
}

.panel-header-copy {
  min-width: 0;
}

.panel-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--mc-accent);
  margin-bottom: 4px;
}

.panel-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--mc-text-primary);
  margin: 0;
  letter-spacing: -0.03em;
}

.new-chat-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--mc-border);
  background: var(--mc-panel-raised);
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mc-text-primary);
  transition: all 0.15s;
}

.new-chat-btn:hover {
  background: var(--mc-primary);
  border-color: var(--mc-primary);
  color: white;
}

.agent-selector {
  padding: 10px 12px 12px;
  border-bottom: 1px solid var(--mc-border-light);
  position: relative;
}

.agent-select-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  font-size: 13px;
  color: var(--mc-text-primary);
  background: var(--mc-bg-sunken);
  cursor: pointer;
  outline: none;
  transition: all 0.15s;
}

.agent-select-trigger:hover {
  border-color: var(--mc-primary);
  background: var(--mc-bg-elevated);
}

.agent-select-trigger__icon {
  font-size: 18px;
  line-height: 1;
}

.agent-select-trigger__name {
  flex: 1;
  text-align: left;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-select-trigger__arrow {
  flex-shrink: 0;
  color: var(--mc-text-tertiary);
  transition: transform 0.2s;
}

.agent-select-trigger__arrow.open {
  transform: rotate(180deg);
}

.agent-dropdown-backdrop,
.model-dropdown-backdrop,
.header-menu-backdrop {
  position: fixed;
  inset: 0;
  z-index: 99;
}

.project-changes-backdrop {
  display: none;
}

.agent-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 12px;
  right: 12px;
  min-width: 240px;
  z-index: 100;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 14px;
  padding: 6px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  max-height: 320px;
  overflow-y: auto;
}

.agent-dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
}

.agent-dropdown-item:hover {
  background: var(--mc-bg-sunken);
}

.agent-dropdown-item.active {
  background: var(--mc-primary-bg);
}

.agent-dropdown-item__icon {
  font-size: 24px;
  line-height: 1;
  flex-shrink: 0;
}

.agent-dropdown-item__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.agent-dropdown-item__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-text-primary);
}

.agent-dropdown-item__desc {
  font-size: 11px;
  color: var(--mc-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-dropdown-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.agent-dropdown-item__tag {
  font-size: 10px;
  line-height: 1.2;
  color: var(--mc-primary);
  background: var(--mc-primary-bg);
  border-radius: 999px;
  padding: 2px 7px;
}

.agent-dropdown-item__check {
  flex-shrink: 0;
  color: var(--mc-primary);
}

.agent-dropdown-empty {
  padding: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--mc-text-tertiary);
}

.agent-dropdown-enter-active {
  transition: all 0.15s ease-out;
}
.agent-dropdown-leave-active {
  transition: all 0.1s ease-in;
}
.agent-dropdown-enter-from {
  opacity: 0;
  transform: translateY(-6px) scale(0.97);
}
.agent-dropdown-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conv-group-title {
  padding: 10px 10px 6px;
  font-size: 10px;
  font-weight: 700;
  color: var(--mc-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.12em;
}

.conv-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 11px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.conv-item:hover {
  background: var(--mc-bg-sunken);
  transform: translateY(-1px);
}

.conv-item.active {
  background: var(--mc-primary-bg);
}

.conv-item:hover .conv-delete {
  opacity: 1;
}

.conv-icon {
  color: var(--mc-text-tertiary);
  flex-shrink: 0;
  position: relative;
}

.conv-item.active .conv-icon {
  color: var(--mc-primary);
}

/* 正在执行：图标右上角脉冲小点（折叠与展开态均可见） */
.conv-running-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #fbbf24;
  box-shadow: 0 0 4px rgba(251, 191, 36, 0.6), 0 0 0 2px var(--mc-bg-primary, #fff);
  animation: pulse-dot 1.2s infinite;
  pointer-events: none;
}

.conv-item.is-running {
  background: color-mix(in srgb, #fbbf24 8%, transparent);
}

.conv-item.is-running:hover {
  background: color-mix(in srgb, #fbbf24 14%, var(--mc-bg-sunken));
}

.conv-item.is-running.active {
  background: var(--mc-primary-bg);
}

/* 展开态：标题右侧"生成中..."小徽章 */
.conv-running-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 500;
  color: #b45309;
  background: rgba(251, 191, 36, 0.15);
  border: 1px solid rgba(251, 191, 36, 0.3);
  padding: 1px 6px 1px 5px;
  border-radius: 10px;
  line-height: 1.3;
  white-space: nowrap;
}

.conv-running-badge-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #f59e0b;
  animation: pulse-dot 1.2s infinite;
}

.conv-info {
  flex: 1;
  overflow: hidden;
}

.conv-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--mc-text-primary);
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

/* 标题文本本身承担省略号；flex 父级上的 overflow:hidden 会阻止 ellipsis 正常工作 */
.conv-title > span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  flex: 1 1 auto;
}

.conv-item.active .conv-title {
  color: var(--mc-primary);
}

.conv-meta {
  font-size: 11px;
  color: var(--mc-text-tertiary);
  margin-top: 1px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.conv-project-row {
  margin-top: 6px;
  min-width: 0;
}

.conv-project-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(217, 119, 87, 0.08);
  color: var(--mc-primary, #D97757);
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conv-item.active .conv-project-tag {
  background: rgba(217, 119, 87, 0.12);
}

.conv-dot {
  color: var(--mc-text-tertiary);
}

.conv-title-input {
  width: 100%;
  font-size: 13px;
  font-weight: 500;
  color: var(--mc-text-primary);
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-primary);
  border-radius: 6px;
  padding: 2px 6px;
  outline: none;
  box-shadow: 0 0 0 2px rgba(217, 119, 87, 0.15);
}

.conv-delete {
  opacity: 0;
  width: 22px;
  height: 22px;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--mc-text-tertiary);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  padding: 0;
  flex-shrink: 0;
  transition: all 0.15s;
}

.conv-delete:hover {
  background: var(--mc-danger-bg);
  color: var(--mc-danger);
}

.empty-convs {
  text-align: center;
  padding: 32px 16px;
  color: var(--mc-text-tertiary);
  font-size: 13px;
  line-height: 1.8;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(180deg, var(--mc-chat-header-bg), var(--mc-chat-bg));
  position: relative;
  min-height: 0;
}

/* 拖拽上传遮罩 */
.drop-overlay {
  position: absolute;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(217, 119, 87, 0.06);
  backdrop-filter: blur(2px);
}

.drop-overlay__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 60px;
  border: 2px dashed var(--mc-primary, #D97757);
  border-radius: 16px;
  background: var(--mc-bg-elevated, #f8fafc);
  color: var(--mc-primary, #D97757);
  font-size: 16px;
  font-weight: 500;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.composer-menu-backdrop {
  position: absolute;
  inset: 0;
  z-index: 8;
}

.mock-task-card {
  margin: 10px 16px 0;
  padding: 12px 14px;
  border: 1px solid rgba(217, 119, 87, 0.22);
  border-radius: 10px;
  background: rgba(217, 119, 87, 0.06);
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.mock-task-card__main {
  min-width: 0;
  flex: 1;
}

.mock-task-card__collapsed {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.mock-task-card__collapsed-text {
  min-width: 0;
  flex: 1;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mc-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mock-task-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.mock-task-card__kicker {
  font-size: 12px;
  color: var(--mc-primary);
  font-weight: 700;
  margin-bottom: 3px;
}

.mock-task-card__title {
  font-size: 14px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.mock-task-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 5px;
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.mock-task-card__assessment {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.mock-task-card__assessment-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(64, 158, 255, 0.12);
  color: #409eff;
}

.mock-task-card__assessment-badge.is-passed {
  background: rgba(103, 194, 58, 0.14);
  color: #67c23a;
}

.mock-task-card__assessment-badge.is-partial,
.mock-task-card__assessment-badge.is-running {
  background: rgba(230, 162, 60, 0.14);
  color: #e6a23c;
}

.mock-task-card__assessment-badge.is-failed {
  background: rgba(245, 108, 108, 0.14);
  color: #f56c6c;
}

.mock-task-card__assessment-score {
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.mock-task-card__summary {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-secondary);
}

.mock-task-card__details {
  width: 100%;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(217, 119, 87, 0.2);
}

.mock-task-card__checks {
  margin: 8px 0 0;
  padding-left: 18px;
  color: var(--mc-text-secondary);
  font-size: 12px;
  line-height: 1.55;
}

.mock-task-card__checks li.is-matched {
  color: #67c23a;
}

.mock-task-card__checks li.is-missing {
  color: var(--mc-text-secondary);
}

.mock-task-card__gate-blockers {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(245, 108, 108, 0.08);
  border: 1px solid rgba(245, 108, 108, 0.18);
}

.mock-task-card__gate-blockers .mock-task-card__preview-label {
  color: #f56c6c;
}

.mock-task-card__signals {
  margin-top: 10px;
}

.mock-task-card__signals--structured {
  padding-top: 2px;
}

.mock-task-card__signal-group + .mock-task-card__signal-group {
  margin-top: 8px;
}

.mock-task-card__signal-group-title,
.mock-task-card__signal-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-secondary);
}

.mock-task-card__signal-subtitle {
  margin-top: 6px;
}

.mock-task-card__signal-summary {
  margin-top: 6px;
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.mock-task-card__signal-list {
  margin: 6px 0 0;
  padding-left: 18px;
  color: var(--mc-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.mock-task-card__preview {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(217, 119, 87, 0.14);
}

.mock-task-card__preview-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--mc-primary);
  margin-bottom: 4px;
}

.mock-task-card__preview-content {
  font-size: 12px;
  color: var(--mc-text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 168px;
  overflow: auto;
}

.mock-task-card__toggle {
  border: 1px solid rgba(217, 119, 87, 0.22);
  border-radius: 999px;
  padding: 5px 10px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--mc-primary);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.mock-task-card__toggle:hover {
  border-color: rgba(217, 119, 87, 0.4);
  background: rgba(255, 255, 255, 0.9);
}

.mock-task-card__close {
  width: 24px;
  height: 24px;
  border: 1px solid var(--mc-border-light);
  border-radius: 6px;
  background: var(--mc-bg);
  color: var(--mc-text-tertiary);
  cursor: pointer;
  flex-shrink: 0;
}

.mock-task-card__close:hover {
  color: var(--mc-text-primary);
  border-color: var(--mc-border);
}

.teacher-guide-card {
  margin: 10px 16px 0;
  padding: 12px 14px;
  border: 1px solid rgba(64, 158, 255, 0.18);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(64, 158, 255, 0.07), rgba(64, 158, 255, 0.02));
}

.teacher-guide-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.teacher-guide-card__actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.teacher-guide-card__copy {
  min-width: 0;
  flex: 1;
}

.teacher-guide-card__kicker {
  font-size: 12px;
  font-weight: 700;
  color: #409eff;
}

.teacher-guide-card__summary {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--mc-text-secondary);
}

.teacher-guide-card__toggle {
  flex-shrink: 0;
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.teacher-guide-card__dismiss,
.teacher-guide-restore {
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  color: #409eff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.teacher-guide-card__dismiss {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
}

.teacher-guide-restore {
  min-height: 30px;
  padding: 0 12px;
  flex-shrink: 0;
}

.teacher-guide-card__toggle:hover,
.teacher-guide-card__dismiss:hover,
.teacher-guide-restore:hover {
  background: rgba(64, 158, 255, 0.1);
}

.teacher-guide-card__section {
  margin-top: 12px;
}

.teacher-guide-card__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-secondary);
}

.teacher-guide-card__detail {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-tertiary);
}

.teacher-guide-card__rules {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--mc-text-tertiary);
}

.composer-left-tools,
.composer-footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.composer-footer-left {
  flex-wrap: nowrap;
  width: 100%;
}

.composer-menu-wrap {
  position: relative;
  z-index: 12;
}

.composer-scope-actions {
  display: inline-flex;
  align-items: flex-end;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
}

.composer-scope-field {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.composer-scope-field__header {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 0 2px;
}

.composer-scope-field__title,
.composer-scope-field__meta {
  font-size: 10px;
  line-height: 1.1;
}

.composer-scope-field__title {
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-scope-field__meta {
  color: var(--mc-text-secondary, #64748b);
  white-space: nowrap;
}

.composer-scope-field__meta.is-unset {
  color: rgba(217, 119, 87, 0.92);
}

.composer-icon-btn,
.composer-pill-btn,
.composer-workspace-chip,
.composer-project-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  border: 1px solid rgba(148, 163, 184, 0.22);
  background: var(--mc-bg-elevated, #f8fafc);
  color: var(--mc-text-secondary, #64748b);
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.composer-icon-btn:hover,
.composer-pill-btn:hover,
.composer-workspace-chip:hover,
.composer-project-chip:hover,
.composer-icon-btn.active,
.composer-pill-btn.active,
.composer-workspace-chip.active,
.composer-project-chip.active {
  border-color: rgba(217, 119, 87, 0.28);
  background: color-mix(in srgb, var(--mc-primary, #D97757) 10%, var(--mc-bg-elevated, #f8fafc));
  color: var(--mc-text-primary, #1e293b);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.composer-pill-btn.is-full-access {
  border-color: color-mix(in srgb, var(--mc-danger, #ef4444) 40%, transparent);
  background: color-mix(in srgb, var(--mc-danger, #ef4444) 12%, var(--mc-bg-elevated, #f8fafc));
  color: var(--mc-danger, #ef4444);
  font-weight: 700;
}

.composer-pill-btn.is-full-access:hover,
.composer-pill-btn.is-full-access.active {
  border-color: color-mix(in srgb, var(--mc-danger, #ef4444) 55%, transparent);
  color: var(--mc-danger, #ef4444);
  box-shadow: 0 8px 24px rgba(220, 38, 38, 0.12);
}

.composer-icon-btn {
  width: 28px;
  justify-content: center;
  padding: 0;
}

.composer-icon-btn__plus {
  font-size: 16px;
  line-height: 1;
}


.composer-workspace-chip,
.composer-pill-btn,
.composer-project-chip {
  padding: 0 10px;
  font-size: 12px;
}

.composer-workspace-chip__icon,
.composer-project-chip__icon {
  font-size: 12px;
}

.composer-workspace-chip__label,
.composer-project-chip__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-project-chip.is-unset {
  border-style: dashed;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-workspace-chip__arrow,
.composer-pill-btn__arrow,
.composer-project-chip__arrow {
  font-size: 10px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-project-chip {
  max-width: min(100%, 220px);
}

.composer-popover--project-browser {
  min-width: 320px;
  max-width: min(92vw, 360px);
}

.composer-popover__hero {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px 10px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--mc-primary, #D97757) 10%, var(--mc-bg-elevated, #f8fafc));
  border: 1px solid color-mix(in srgb, var(--mc-primary, #D97757) 24%, transparent);
}

.composer-popover__hero-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--mc-text-primary, #1e293b);
}

.composer-popover__hero-desc {
  font-size: 11px;
  line-height: 1.45;
  color: var(--mc-text-secondary, #64748b);
}

.composer-project-summary {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 12px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px solid var(--mc-border-light, rgba(226, 232, 240, 0.9));
}

.composer-project-summary.is-unset {
  background: color-mix(in srgb, var(--mc-primary, #D97757) 10%, var(--mc-bg-elevated, #f8fafc));
  border-color: color-mix(in srgb, var(--mc-primary, #D97757) 24%, transparent);
}

.composer-project-summary__label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-project-summary__path {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
  word-break: break-word;
}

.composer-primary-btn {
  width: 100%;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(217, 119, 87, 0.96), rgba(236, 131, 93, 0.96));
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  padding: 10px 12px;
  cursor: pointer;
  box-shadow: 0 10px 24px rgba(217, 119, 87, 0.22);
}

.composer-primary-btn:disabled {
  cursor: not-allowed;
  opacity: 0.65;
  box-shadow: none;
}

.composer-inline-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.composer-model-hint {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  min-width: 0;
  flex: 1;
}

.composer-popover {
  position: absolute;
  left: 0;
  bottom: calc(100% + 10px);
  min-width: 196px;
  padding: 6px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--mc-bg-elevated, #f8fafc) 92%, transparent);
  border: 1px solid var(--mc-border-light, rgba(226, 232, 240, 0.95));
  box-shadow: 0 16px 32px rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(16px);
}

.composer-popover--menu {
  min-width: 188px;
}

.composer-popover--list {
  min-width: 176px;
}

.composer-popover--workspace {
  min-width: 280px;
  max-width: min(90vw, 320px);
}

.composer-menu-item,
.composer-option,
.composer-workspace-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
  color: var(--mc-text-primary, #1e293b);
}

.composer-menu-item,
.composer-option {
  padding: 8px 10px;
  font-size: 12px;
}

.composer-option {
  justify-content: space-between;
  align-items: flex-start;
}

.composer-option__content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.composer-option__desc {
  font-size: 11px;
  line-height: 1.4;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-menu-item:hover,
.composer-option:hover,
.composer-workspace-item:hover,
.composer-option.active,
.composer-workspace-item.active {
  background: var(--mc-bg-muted, #f9f7f5);
}

.composer-option--danger {
  color: var(--mc-danger, #ef4444);
}

.composer-option--danger .composer-option__desc {
  color: color-mix(in srgb, var(--mc-danger, #ef4444) 72%, var(--mc-text-secondary, #64748b));
}

.composer-option--danger:hover,
.composer-option--danger.active {
  background: color-mix(in srgb, var(--mc-danger, #ef4444) 12%, var(--mc-bg-elevated, #f8fafc));
}

.composer-menu-item:disabled,
.composer-workspace-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-menu-item__icon,
.composer-option__check,
.composer-menu-item__arrow {
  flex-shrink: 0;
}

.composer-popover__hint {
  padding: 6px 10px 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-popover__hint--compact {
  padding-top: 0;
  padding-bottom: 6px;
}

.workspace-folder-input {
  display: none;
}

.composer-menu-item--toggle {
  justify-content: space-between;
}

.composer-switch {
  width: 30px;
  height: 18px;
  border-radius: 999px;
  background: rgba(203, 213, 225, 0.95);
  padding: 2px;
  display: inline-flex;
  align-items: center;
  transition: background 0.18s ease;
}

.composer-switch.is-on {
  background: rgba(217, 119, 87, 0.92);
}

.composer-switch__thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.18s ease;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.16);
}

.composer-switch.is-on .composer-switch__thumb {
  transform: translateX(12px);
}

.composer-popover__section + .composer-popover__section {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--mc-border-light, rgba(226, 232, 240, 0.9));
}

.composer-popover__label {
  padding: 4px 10px 8px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-workspace-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 180px;
  overflow-y: auto;
}

.composer-workspace-item {
  padding: 8px 10px;
  flex-direction: column;
  align-items: flex-start;
}

.composer-workspace-item:disabled,
.composer-option:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-workspace-item__name {
  font-size: 12px;
  font-weight: 600;
}

.composer-workspace-item__path {
  font-size: 10px;
  color: var(--mc-text-tertiary, #94a3b8);
  word-break: break-all;
}

.composer-workdir-input {
  width: 100%;
}

.composer-workdir-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}

.composer-directory-browser {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.composer-directory-browser__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.composer-directory-browser__current {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 10px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px solid var(--mc-border-light, rgba(226, 232, 240, 0.92));
}

.composer-directory-browser__current-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-directory-browser__current-path {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
  word-break: break-word;
}

.composer-directory-browser__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 180px;
  overflow-y: auto;
}

.composer-directory-browser__status {
  padding: 10px;
  border-radius: 10px;
  background: var(--mc-bg-elevated, #f8fafc);
  border: 1px dashed var(--mc-border-light, rgba(226, 232, 240, 0.92));
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-directory-browser__status.is-error {
  color: var(--mc-danger, #ef4444);
  border-style: solid;
  background: color-mix(in srgb, var(--mc-danger, #ef4444) 12%, var(--mc-bg-elevated, #f8fafc));
}

.composer-directory-entry {
  width: 100%;
  border: none;
  background: var(--mc-bg-elevated, #f8fafc);
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--mc-border-light, rgba(226, 232, 240, 0.9));
}

.composer-directory-entry:hover {
  border-color: color-mix(in srgb, var(--mc-primary, #D97757) 32%, transparent);
  background: color-mix(in srgb, var(--mc-primary, #D97757) 10%, var(--mc-bg-elevated, #f8fafc));
}

.composer-directory-entry__icon {
  font-size: 14px;
}

.composer-directory-entry__name {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
}

.composer-directory-entry__path {
  font-size: 10px;
  color: var(--mc-text-tertiary, #94a3b8);
  word-break: break-word;
}

.composer-secondary-btn,
.composer-link-btn {
  border: none;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
}

.composer-secondary-btn {
  background: var(--mc-bg-muted, #f9f7f5);
  color: var(--mc-text-secondary, #475569);
}

.composer-secondary-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.composer-link-btn {
  background: transparent;
  color: var(--mc-primary, #D97757);
  padding-left: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: linear-gradient(180deg, var(--mc-panel-raised), var(--mc-surface-overlay));
  border-bottom: 1px solid var(--mc-border);
  min-height: 52px;
  backdrop-filter: blur(12px);
  gap: 10px;
}

.chat-header-left {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.chat-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.chat-stage-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.chat-stage-kicker {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--mc-accent);
}

.agent-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: var(--mc-primary-bg);
  border-radius: 999px;
  max-width: 100%;
}

.agent-badge-icon {
  font-size: 14px;
}

.agent-badge-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--mc-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.agent-badge-type {
  font-size: 11px;
  color: var(--mc-primary-light);
  background: var(--mc-bg-elevated);
  padding: 1px 6px;
  border-radius: 10px;
}

.agent-badge-template {
  font-size: 11px;
  color: var(--mc-primary);
  background: rgba(37, 99, 235, 0.08);
  padding: 1px 6px;
  border-radius: 10px;
}

.agent-badge-mode {
  font-size: 11px;
  color: var(--mc-accent);
  background: rgba(124, 63, 30, 0.08);
  padding: 1px 6px;
  border-radius: 10px;
}

.status-dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; margin-left: 2px;
  transition: background 0.3s;
}
.status-idle { background: #34d399; box-shadow: 0 0 4px rgba(52, 211, 153, 0.5); }
.status-streaming { background: #fbbf24; box-shadow: 0 0 4px rgba(251, 191, 36, 0.5); animation: pulse-dot 1.2s infinite; }
.status-error { background: #f87171; box-shadow: 0 0 4px rgba(248, 113, 113, 0.5); }
@keyframes pulse-dot { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.no-agent-hint {
  font-size: 13px;
  color: var(--mc-text-tertiary);
}

.conversation-readonly-alert {
  margin: 12px 18px 0;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--mc-warning, #d97706) 35%, transparent);
  background: color-mix(in srgb, var(--mc-warning, #d97706) 10%, var(--mc-bg-elevated));
  border-radius: 12px;
}

.conversation-readonly-alert__title {
  font-size: 13px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.conversation-readonly-alert__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--mc-text-secondary);
}

/* Model selector */
/* Model selector styles moved to ModelSelector.vue */

/* Header overflow menu */
.header-overflow-wrap {
  position: relative;
}

.header-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 100;
  min-width: 180px;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  padding: 4px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.header-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  border: none;
  background: none;
  border-radius: 8px;
  font-size: 13px;
  color: var(--mc-text-primary);
  cursor: pointer;
  transition: background 0.12s;
}

.header-menu-item:hover {
  background: var(--mc-bg-sunken);
}

.header-menu-item--danger:hover {
  background: var(--mc-danger-bg);
  color: var(--mc-danger);
}

.header-menu-divider {
  height: 1px;
  background: var(--mc-border-light);
  margin: 2px 8px;
}

.header-btn {
  width: 30px;
  height: 30px;
  border: 1px solid var(--mc-border);
  background: var(--mc-panel-raised);
  border-radius: 10px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--mc-text-secondary);
  transition: all 0.15s;
}

.header-btn:hover {
  border-color: var(--mc-danger);
  color: var(--mc-danger);
}

.header-btn.active {
  border-color: var(--mc-primary);
  color: var(--mc-primary);
  background: rgba(217, 119, 87, 0.08);
}

.project-changes-slide-enter-active,
.project-changes-slide-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.project-changes-slide-enter-from,
.project-changes-slide-leave-to {
  opacity: 0;
  transform: translateX(18px);
}

.chat-session-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--mc-border);
  background: var(--mc-bg-elevated);
  flex-wrap: wrap;
}

.working-directory-copy {
  display: flex;
  flex-direction: column;
  min-width: 120px;
}

.runtime-mode-copy {
  display: flex;
  flex-direction: column;
  min-width: 150px;
}

.project-context-card {
  display: flex;
  flex-direction: column;
  min-width: 180px;
  max-width: min(320px, 100%);
}

.project-context-path {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-context-workspace {
  font-size: 11px;
  color: var(--mc-text-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.permission-context-label {
  font-size: 12px;
  font-weight: 600;
}

.permission-context-label--limited {
  color: #b45309;
}

.permission-context-label--full {
  color: #047857;
}

.runtime-mode-btn {
  width: auto;
  min-width: 72px;
  padding: 0 12px;
  font-size: 12px;
}

.runtime-mode-btn.active {
  border-color: var(--mc-primary);
  background: rgba(124, 63, 30, 0.08);
  color: var(--mc-primary);
}

.working-directory-divider {
  width: 1px;
  height: 28px;
  background: var(--mc-border-light);
}

.working-directory-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary);
}

.working-directory-hint {
  font-size: 11px;
  color: var(--mc-text-tertiary);
}

.working-directory-input {
  flex: 1;
}

.working-directory-btn {
  width: auto;
  min-width: 64px;
  padding: 0 12px;
  font-size: 12px;
}

.working-directory-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.model-prompt {
  margin: 24px auto 0;
  max-width: 540px;
  padding: 20px;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 16px;
  text-align: center;
  box-shadow: 0 8px 24px rgba(124, 63, 30, 0.06);
}

.model-prompt-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--mc-text-primary);
  margin-bottom: 8px;
}

.model-prompt-desc {
  font-size: 14px;
  color: var(--mc-text-secondary);
  line-height: 1.6;
  margin-bottom: 16px;
}

.chat-entry-panel {
  width: min(920px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.chat-entry-panel__hero,
.chat-entry-section {
  padding: 20px;
  border: 1px solid var(--mc-border);
  border-radius: 18px;
  background: var(--mc-bg-elevated);
  box-shadow: 0 8px 24px rgba(124, 63, 30, 0.06);
}

.chat-entry-panel__hero {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: linear-gradient(180deg, rgba(217, 119, 87, 0.08), rgba(217, 119, 87, 0.03));
}

.chat-entry-panel__kicker {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--mc-primary);
}

.chat-entry-panel__title {
  font-size: 28px;
  font-weight: 800;
  color: var(--mc-text-primary);
  letter-spacing: -0.02em;
}

.chat-entry-panel__subtitle,
.chat-entry-section__hint {
  font-size: 14px;
  line-height: 1.6;
  color: var(--mc-text-secondary);
}

.chat-entry-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.chat-entry-panel__primary,
.chat-entry-panel__secondary,
.chat-entry-recent-item,
.chat-entry-agent-card {
  border: 1px solid var(--mc-border);
  background: var(--mc-bg-elevated);
  border-radius: 14px;
  transition: all 0.15s ease;
}

.chat-entry-panel__primary,
.chat-entry-panel__secondary {
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.chat-entry-panel__primary {
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  border-color: transparent;
  color: #fff;
}

.chat-entry-panel__primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat-entry-panel__secondary {
  color: var(--mc-text-primary);
}

.chat-entry-panel__secondary:hover,
.chat-entry-recent-item:hover,
.chat-entry-agent-card:hover {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
}

.chat-entry-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-entry-section__title {
  font-size: 15px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.chat-entry-recent-list,
.chat-entry-agent-grid,
.chat-entry-template-grid {
  display: grid;
  gap: 10px;
}

.chat-entry-recent-list {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.chat-entry-agent-grid {
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

.chat-entry-template-grid {
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.chat-entry-recent-item,
.chat-entry-agent-card {
  padding: 14px;
  text-align: left;
  cursor: pointer;
}

.chat-entry-recent-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chat-entry-recent-item__title,
.chat-entry-agent-card__name {
  font-size: 14px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.chat-entry-recent-item__meta,
.chat-entry-agent-card__desc,
.chat-entry-agent-card__type {
  font-size: 12px;
  color: var(--mc-text-tertiary);
}

.chat-entry-agent-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-entry-template-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--mc-border);
  background: var(--mc-bg-elevated);
  border-radius: 14px;
}

.chat-entry-template-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chat-entry-template-card__icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--mc-bg-muted);
}

.chat-entry-template-card__badge,
.chat-entry-template-card__tag {
  font-size: 12px;
  color: var(--mc-text-tertiary);
  background: var(--mc-bg-muted);
  border-radius: 999px;
  padding: 3px 8px;
}

.chat-entry-template-card__name {
  font-size: 14px;
  font-weight: 700;
  color: var(--mc-text-primary);
}

.chat-entry-template-card__desc {
  font-size: 12px;
  color: var(--mc-text-secondary);
  line-height: 1.5;
}

.chat-entry-template-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chat-entry-template-card__action {
  margin-top: auto;
  border: 1px solid var(--mc-primary);
  background: var(--mc-primary-bg);
  color: var(--mc-primary);
  border-radius: 12px;
  padding: 9px 12px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s ease;
}

.chat-entry-template-card__action:hover:not(:disabled) {
  background: color-mix(in srgb, var(--mc-primary-bg) 60%, white 40%);
}

.chat-entry-template-card__action:disabled {
  opacity: 0.6;
  cursor: wait;
}

.chat-entry-agent-card.is-active {
  border-color: var(--mc-primary);
  background: var(--mc-primary-bg);
}

.chat-entry-agent-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.chat-entry-agent-card__type {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--mc-bg-muted);
}

.chat-entry-agent-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.chat-entry-agent-card__pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--mc-primary);
  background: var(--mc-primary-bg);
  border-radius: 999px;
  padding: 4px 9px;
}

.btn-primary {
  padding: 8px 16px;
  background: linear-gradient(135deg, var(--mc-primary), var(--mc-primary-hover));
  color: white;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.btn-primary:hover {
  background: var(--mc-primary-hover);
}

/* ===== 移动端元素（桌面端隐藏） ===== */
.conv-backdrop {
  display: none;
}

.conv-toggle-btn {
  display: none;
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .chat-console-shell {
    padding: 0 !important;
    height: 100dvh !important;
    height: 100vh !important;
    overflow: hidden !important;
    min-height: 0 !important;
  }

  .chat-console-frame {
    height: 100% !important;
    min-height: 0 !important;
    overflow: hidden !important;
    border-radius: 0;
    border: none;
  }

  .conversation-panel {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    width: 272px;
    min-width: 272px;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    box-shadow: none;
  }

  .conversation-panel.mobile-open {
    transform: translateX(0);
    box-shadow: 4px 0 16px rgba(0, 0, 0, 0.1);
  }

  .conv-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 99;
    background: rgba(0, 0, 0, 0.3);
  }

  .project-changes-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 119;
    background: rgba(15, 23, 42, 0.24);
  }

  .conv-toggle-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border: 1px solid var(--mc-border);
    background: var(--mc-bg-elevated);
    border-radius: 6px;
    cursor: pointer;
    color: var(--mc-text-secondary);
    flex-shrink: 0;
    transition: all 0.15s;
  }

  .conv-toggle-btn:hover {
    border-color: var(--mc-primary);
    color: var(--mc-primary);
  }

  .chat-header {
    padding: 9px 12px;
    gap: 8px;
  }

  .composer-popover--workspace {
    min-width: min(92vw, 320px);
    max-width: min(92vw, 320px);
  }

  .teacher-guide-card__header {
    flex-wrap: wrap;
  }

  .composer-left-tools,
  .composer-footer-left {
    width: 100%;
    flex-wrap: wrap;
  }

  .composer-scope-actions {
    width: 100%;
    flex-wrap: wrap;
    align-items: stretch;
  }

  .composer-scope-field {
    flex: 1 1 180px;
  }

  .composer-model-hint {
    width: 100%;
  }

  .chat-entry-panel__hero,
  .chat-entry-section {
    padding: 16px;
  }

  .chat-entry-panel__title {
    font-size: 24px;
  }

  .chat-entry-agent-grid,
  .chat-entry-recent-list {
    grid-template-columns: 1fr;
  }

  .composer-workspace-chip,
  .composer-project-chip {
    max-width: 100%;
  }

  .chat-session-bar {
    padding: 10px 12px;
    flex-wrap: wrap;
  }

  .runtime-mode-copy,
  .project-context-card,
  .working-directory-copy {
    width: 100%;
  }

  .working-directory-divider {
    display: none;
  }

  .working-directory-input {
    width: 100%;
  }

  .agent-badge {
    padding: 4px 8px;
  }

  .chat-stage-kicker {
    display: none;
  }

  .agent-badge-name,
  .agent-badge-type,
  .agent-badge-template,
  .agent-badge-mode {
    display: none;
  }

  .model-select-trigger {
    max-width: 160px;
  }

  .model-dropdown {
    min-width: 200px;
  }

  .drop-overlay__content {
    padding: 24px 32px;
    font-size: 14px;
  }
}

@media (max-width: 640px) {
  .teacher-guide-card__toggle {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .chat-header {
    padding: 6px 8px;
    min-height: 44px;
  }

  .composer-popover {
    left: -6px;
  }

  .composer-pill-btn,
  .composer-workspace-chip,
  .composer-project-chip {
    max-width: calc(100vw - 44px);
  }

  .composer-popover--project-browser {
    min-width: min(92vw, 320px);
    max-width: min(92vw, 320px);
  }

  .chat-session-bar {
    padding: 8px;
    gap: 8px;
  }

  .chat-header-right {
    gap: 4px;
  }

  .model-select-trigger {
    max-width: 120px;
    height: 30px;
    padding: 0 8px;
    font-size: 12px;
  }

  .header-btn {
    width: 28px;
    height: 28px;
  }

  .working-directory-btn {
    min-width: 56px;
  }
}

</style>
