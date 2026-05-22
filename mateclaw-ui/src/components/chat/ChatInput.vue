<template>
  <div
    ref="containerRef"
    class="chat-input-wrapper"
    :class="{
      'is-focused': isFocused,
      'is-disabled': disabled,
      'is-loading': loading,
    }"
  >
    <!-- 附件列表 -->
    <div v-if="attachments.length" class="attachment-list">
      <div
        v-for="attachment in attachments"
        :key="attachment.storedName || attachment.path"
        class="attachment-chip"
        :class="{
          'attachment-chip--dir': attachment.contentType === 'inode/directory',
          'attachment-chip--image': attachment.contentType?.startsWith('image/'),
          'attachment-chip--video': attachment.contentType?.startsWith('video/'),
        }"
      >
        <img
          v-if="attachment.contentType?.startsWith('image/') && (attachment.previewUrl || attachment.url)"
          :src="attachment.previewUrl || attachment.url"
          :alt="attachment.name"
          class="attachment-chip__thumbnail"
          loading="lazy"
        />
        <video
          v-else-if="attachment.contentType?.startsWith('video/') && (attachment.previewUrl || attachment.url)"
          :src="attachment.previewUrl || attachment.url"
          class="attachment-chip__thumbnail"
          preload="metadata"
          muted
        />
        <component
          :is="attachment.url ? 'a' : 'span'"
          :href="attachment.url || undefined"
          target="_blank"
          rel="noreferrer"
          class="attachment-chip__label"
        >
          <span>{{ attachment.contentType === 'inode/directory' ? '📁 ' : '' }}{{ attachment.name }}</span>
          <span v-if="attachment.size">{{ formatFileSize(attachment.size) }}</span>
          <span v-if="attachment.contextHint" class="attachment-chip__hint">{{ attachment.contextHint }}</span>
          <span v-else-if="attachment.contentType === 'inode/directory'" class="attachment-chip__path">{{ attachment.path }}</span>
        </component>
        <button
          type="button"
          class="attachment-chip__remove"
          @click="removeAttachment(attachment.storedName || attachment.path)"
        >
          ×
        </button>
      </div>
    </div>

    <!-- 审批栏：有待审批时替换输入区域 -->
    <div v-if="pendingApproval?.status === 'pending_approval'" class="approval-bar">
      <div class="approval-bar__info">
        <span class="approval-bar__icon">
          <el-icon><WarningFilled /></el-icon>
        </span>
        <span class="approval-bar__label">{{ t('chat.approvalAllow') }}</span>
        <span class="approval-bar__tool">{{ getToolLabel(pendingApproval.toolName) }}</span>
        <span class="approval-bar__label">{{ t('chat.approvalExecute') }}</span>
      </div>
      <label class="approval-bar__scope">
        <span class="approval-bar__scope-label">{{ t('chat.approvalRemember') }}</span>
        <select v-model="approvalScope" class="approval-bar__scope-select">
          <option value="once">{{ t('chat.approvalScopeOnce') }}</option>
          <option value="conversation">{{ t('chat.approvalScopeConversation') }}</option>
          <option value="project">{{ t('chat.approvalScopeProject') }}</option>
        </select>
      </label>
      <div class="approval-bar__actions">
        <button
          type="button"
          class="approval-bar__btn approval-bar__btn--deny"
          @click="emit('deny', pendingApproval.pendingId)"
        >
          <el-icon><CloseBold /></el-icon>
          {{ t('chat.deny') }}
        </button>
        <button
          type="button"
          class="approval-bar__btn approval-bar__btn--approve"
          @click="emit('approve', { pendingId: pendingApproval.pendingId, scope: approvalScope })"
        >
          <el-icon><Select /></el-icon>
          {{ t('chat.approve') }}
        </button>
      </div>
    </div>

    <!-- 输入区域（运行中也可输入） -->
    <div v-else class="input-area-container">
      <div v-if="queuedMessage && queuedMessage.status !== 'cancelled'" class="queued-indicator">
        <div class="queued-indicator__info">
          <el-icon><Timer /></el-icon>
          <span class="queued-indicator__text">
            {{ queuedMessage.status === 'sending' ? t('chat.queuedSending') : t('chat.queuedWillSend') }}
            <span v-if="queueSize > 1" class="queued-indicator__count">({{ queueSize }})</span>
          </span>
        </div>
        <button
          v-if="queuedMessage.status === 'queued'"
          type="button"
          class="queued-indicator__cancel"
          @click="emit('cancel-queued')"
        >{{ t('chat.queuedCancel') }}</button>
      </div>

      <div v-if="shortcutPanelVisible" class="shortcut-panel" role="listbox">
        <div class="shortcut-panel__header">
          {{ activeShortcutKind === 'mention' ? t('chat.shortcuts.mentionHeader') : t('chat.shortcuts.commandHeader') }}
        </div>
        <button
          v-for="(item, index) in filteredShortcutItems"
          :key="item.id"
          type="button"
          class="shortcut-item"
          :class="{ 'is-active': index === activeShortcutIndex }"
          @mousedown.prevent
          @click="applyShortcut(item)"
        >
          <span class="shortcut-item__icon">{{ item.icon || (activeShortcutKind === 'mention' ? '@' : '/') }}</span>
          <span class="shortcut-item__body">
            <span class="shortcut-item__label">{{ item.label }}</span>
            <span v-if="item.description" class="shortcut-item__desc">{{ item.description }}</span>
          </span>
          <span class="shortcut-item__value">{{ item.value }}</span>
        </button>
        <div v-if="filteredShortcutItems.length === 0" class="shortcut-panel__empty">
          {{ t('chat.shortcuts.noMatch') }}
        </div>
      </div>

      <div class="input-area">
        <textarea
          ref="textareaRef"
          v-model="inputValue"
          class="chat-textarea"
          :placeholder="inputPlaceholder"
          :disabled="disabled"
          :maxlength="maxLength"
          rows="1"
          @keydown="handleKeydown"
          @compositionstart="isComposing = true"
          @compositionend="isComposing = false"
          @focus="handleFocus"
          @blur="handleBlur"
          @input="handleInput"
          @click="syncShortcutState"
          @keyup="handleKeyup"
          @paste="handlePaste"
        ></textarea>

        <div class="input-actions">
          <button
            v-if="enableAttachments && showAttachmentButton"
            type="button"
            class="action-btn attach-btn"
            :disabled="disabled || loading || uploading"
            @click="openFilePicker"
          >
            <el-icon><Paperclip /></el-icon>
          </button>

          <button
            v-if="showThinkingButton"
            type="button"
            class="action-btn thinking-btn"
            :class="{ active: thinkingEnabled && thinkingSupported, unsupported: !thinkingSupported }"
            :disabled="disabled || !thinkingSupported"
            @click="thinkingSupported && emit('toggle-thinking')"
            :title="!thinkingSupported
              ? t('chat.thinkingUnsupported')
              : (thinkingEnabled ? t('chat.thinkingOn') : t('chat.thinkingOff'))"
          >
            <el-icon><MagicStick /></el-icon>
          </button>

          <button
            v-if="enableTalkMode && showTalkButton"
            type="button"
            class="action-btn talk-btn"
            :disabled="disabled || loading"
            @click="emit('talk')"
            :title="t('talk.title')"
          >
            <el-icon><Microphone /></el-icon>
          </button>

          <button
            type="button"
            class="action-btn send-btn"
            :class="sendBtnClass"
            :disabled="!canSend && !loading"
            @click="handleSubmit"
          >
            <el-icon v-if="canSend"><Promotion /></el-icon>
            <el-icon v-else-if="loading"><CloseBold /></el-icon>
            <el-icon v-else><Promotion /></el-icon>
          </button>
        </div>
      </div>

      <div v-if="$slots['toolbar-left'] || $slots['toolbar-right']" class="composer-toolbar">
        <div class="composer-toolbar__left">
          <slot name="toolbar-left"></slot>
        </div>
        <div class="composer-toolbar__right">
          <slot name="toolbar-right"></slot>
        </div>
      </div>
    </div>

    <div class="input-footer">
      <div class="input-footer__left">
        <slot name="footer-left">
          <span class="input-hint">{{ hint }}</span>
        </slot>
      </div>
      <div class="input-footer__right">
        <slot name="footer-right"></slot>
        <span v-if="maxLength" class="input-length">
          {{ inputValue.length }}/{{ maxLength }}
        </span>
      </div>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      class="hidden-file-input"
      multiple
      :accept="acceptedFileTypes"
      @change="handleFileChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CloseBold, MagicStick, Microphone, Paperclip, Promotion, Select, Timer, WarningFilled } from '@element-plus/icons-vue'
import { useToolLabel } from '@/composables/useToolLabel'
import type { ApprovalDecisionPayload, ApprovalDecisionScope, ChatAttachment, PendingApprovalMeta, StreamPhase, QueuedMessage, ChatShortcutItem } from '@/types'

interface ActiveShortcutQuery {
  kind: 'command' | 'mention'
  query: string
  start: number
  end: number
}

interface Props {
  /** 输入值 */
  modelValue?: string
  /** 占位符 */
  placeholder?: string
  /** 是否加载中（AI 正在运行） */
  loading?: boolean
  /** 是否禁用（无法输入） */
  disabled?: boolean
  /** 最大长度 */
  maxLength?: number
  /** 是否启用附件 */
  enableAttachments?: boolean
  /** 接受文件类型 */
  acceptedFileTypes?: string
  /** 提示文字 */
  hint?: string
  /** 附件列表 */
  attachments?: ChatAttachment[]
  /** 是否上传中 */
  uploading?: boolean
  /** 待审批数据：存在时将输入框替换为审批栏 */
  pendingApproval?: PendingApprovalMeta | null
  /** 当前流阶段 */
  streamPhase?: StreamPhase
  /** 排队的消息（队首） */
  queuedMessage?: QueuedMessage | null
  /** 排队消息总数 */
  queueSize?: number
  /** 是否启用 Talk Mode 按钮 */
  enableTalkMode?: boolean
  /** 是否显示附件按钮 */
  showAttachmentButton?: boolean
  /** 是否显示深度思考按钮 */
  showThinkingButton?: boolean
  /** 是否显示 Talk Mode 按钮 */
  showTalkButton?: boolean
  /** 深度思考开关状态 */
  thinkingEnabled?: boolean
  /** slash 命令候选 */
  shortcutCommands?: ChatShortcutItem[]
  /** @ 引用候选 */
  shortcutMentions?: ChatShortcutItem[]
  /**
   * RFC-049 PR-1-UI: 当前 runtime 模型是否支持 reasoning_effort。false 时按钮灰掉，
   * 不响应点击，tooltip 提示当前模型不支持深度思考。默认 true 以保持向后兼容。
   */
  thinkingSupported?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '',
  loading: false,
  disabled: false,
  enableAttachments: true,
  acceptedFileTypes: '*/*',
  hint: '',
  attachments: () => [],
  uploading: false,
  pendingApproval: null,
  streamPhase: 'idle',
  queuedMessage: null,
  queueSize: 0,
  enableTalkMode: false,
  showAttachmentButton: true,
  showThinkingButton: true,
  showTalkButton: true,
  thinkingEnabled: false,
  shortcutCommands: () => [],
  shortcutMentions: () => [],
  thinkingSupported: true,
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: [value: string]
  stop: []
  'cancel-queued': []
  'file-select': [files: File[]]
  'attachment-remove': [storedName: string]
  approve: [payload: ApprovalDecisionPayload]
  deny: [pendingId: string]
  talk: []
  'toggle-thinking': []
}>()

const { t } = useI18n()
const { getToolLabel } = useToolLabel()

// 内部状态
const containerRef = ref<HTMLElement | null>(null)
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const isFocused = ref(false)
const isComposing = ref(false)
const activeShortcutQuery = ref<ActiveShortcutQuery | null>(null)
const activeShortcutIndex = ref(0)
const approvalScope = ref<ApprovalDecisionScope>('once')

watch(() => props.pendingApproval?.pendingId, () => {
  approvalScope.value = 'once'
})

// 输入值处理
const inputValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// 是否可以发送
const canSend = computed(() => {
  return inputValue.value.trim().length > 0 || props.attachments.length > 0
})

// 运行中输入的占位符
const inputPlaceholder = computed(() => {
  if (props.loading) {
    if (props.queuedMessage) return t('chat.queuedReplace')
    return props.placeholder
  }
  return props.placeholder
})

const activeShortcutKind = computed<'command' | 'mention' | null>(() => activeShortcutQuery.value?.kind || null)

const filteredShortcutItems = computed(() => {
  const query = activeShortcutQuery.value
  if (!query) return []
  const source = query.kind === 'mention' ? props.shortcutMentions : props.shortcutCommands
  const keyword = query.query.trim().toLowerCase()
  if (!keyword) return source
  return source.filter((item) => {
    const haystacks = [item.label, item.value, item.description || '', ...(item.aliases || [])]
    return haystacks.some((entry) => entry.toLowerCase().includes(keyword))
  })
})

const shortcutPanelVisible = computed(() => !!activeShortcutQuery.value)

// 处理提交
const handleSubmit = () => {
  // 有排队消息时，点击按钮取消排队
  if (props.queuedMessage && props.queuedMessage.status === 'queued') {
    // 如果输入框为空，取消排队；如果有新输入，替换排队消息
    if (!inputValue.value.trim()) {
      emit('cancel-queued')
      return
    }
  }

  // 运行中且输入为空时，停止生成 —— 但当用户刚刚追加了一条 queued 消息时，
  // 第二次点击发送/按 Enter 通常是误操作（双击 / 输入法回车 / 连击）。这种情况下
  // 触发 stop 会把用户预期会跑的当前 turn + queued 一起杀掉，前端给出"任务直接结束"
  // 的错觉。检测到 sending 状态的 queued 消息时静默吞掉这次空提交，让用户必须明确
  // 点 cancel-queued 或专用 stop 按钮才能终止。
  if (props.loading && !canSend.value) {
    if (props.queuedMessage && (props.queuedMessage.status === 'queued' || props.queuedMessage.status === 'sending')) {
      return
    }
    emit('stop')
    return
  }

  if (!canSend.value || props.disabled) return

  // 运行中有内容：发送（useChat 会走 interrupt/queue 逻辑）
  // 非运行中有内容：正常发送
  emit('submit', inputValue.value)
}

// 发送按钮样式
const sendBtnClass = computed(() => ({
  'is-loading': props.loading && !canSend.value,
  'is-empty': !canSend.value && !props.loading,
  'is-interrupt': props.loading && canSend.value,
}))

function closeShortcutPanel() {
  activeShortcutQuery.value = null
  activeShortcutIndex.value = 0
}

function getTokenBoundaryEnd(text: string, startIndex: number) {
  let cursor = startIndex
  while (cursor < text.length && !/\s/.test(text[cursor])) {
    cursor += 1
  }
  return cursor
}

function detectShortcutQuery(): ActiveShortcutQuery | null {
  if (props.disabled || props.pendingApproval) return null
  const textarea = textareaRef.value
  const text = inputValue.value || ''
  const cursor = textarea?.selectionStart ?? text.length
  const beforeCursor = text.slice(0, cursor)
  const commandMatch = beforeCursor.match(/(^|\s)(\/[^\s]*)$/)
  if (commandMatch) {
    const token = commandMatch[2]
    const start = cursor - token.length
    return {
      kind: 'command',
      query: token.slice(1),
      start,
      end: getTokenBoundaryEnd(text, cursor),
    }
  }
  const mentionMatch = beforeCursor.match(/(^|\s)(@[^\s@]*)$/)
  if (mentionMatch) {
    const token = mentionMatch[2]
    const start = cursor - token.length
    return {
      kind: 'mention',
      query: token.slice(1),
      start,
      end: getTokenBoundaryEnd(text, cursor),
    }
  }
  return null
}

function syncShortcutState() {
  activeShortcutQuery.value = detectShortcutQuery()
  activeShortcutIndex.value = 0
}

function setCaret(position: number) {
  nextTick(() => {
    const textarea = textareaRef.value
    if (!textarea) return
    textarea.focus()
    textarea.setSelectionRange(position, position)
  })
}

function applyShortcut(item: ChatShortcutItem) {
  const query = activeShortcutQuery.value
  if (!query) return
  const replacement = `${item.value}${item.suffix ?? ' '}`
  const nextValue = `${inputValue.value.slice(0, query.start)}${replacement}${inputValue.value.slice(query.end)}`
  emit('update:modelValue', nextValue)
  closeShortcutPanel()
  setCaret(query.start + replacement.length)
}

function moveShortcutSelection(offset: number) {
  const total = filteredShortcutItems.value.length
  if (!total) return
  activeShortcutIndex.value = (activeShortcutIndex.value + offset + total) % total
}

function handleFocus() {
  isFocused.value = true
  syncShortcutState()
}

function handleBlur() {
  isFocused.value = false
  closeShortcutPanel()
}

function handleInput() {
  autoResize()
  syncShortcutState()
}

function handleKeyup(event: KeyboardEvent) {
  if (['ArrowUp', 'ArrowDown', 'Enter', 'Tab', 'Escape'].includes(event.key)) {
    return
  }
  syncShortcutState()
}

// 统一处理键盘事件
const handleKeydown = (event: KeyboardEvent) => {
  const isPlainEnter = event.key === 'Enter' && !event.shiftKey && !event.altKey && !event.ctrlKey && !event.metaKey

  if (shortcutPanelVisible.value) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      moveShortcutSelection(1)
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      moveShortcutSelection(-1)
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      closeShortcutPanel()
      return
    }
    if (event.key === 'Tab' || isPlainEnter) {
      const selected = filteredShortcutItems.value[activeShortcutIndex.value]
      if (selected) {
        event.preventDefault()
        applyShortcut(selected)
        return
      }
    }
  }

  if (!isPlainEnter) return
  event.preventDefault()
  if (isComposing.value) return
  handleSubmit()
}

// 自动调整高度
const autoResize = () => {
  nextTick(() => {
    const textarea = textareaRef.value
    if (!textarea) return

    textarea.style.height = 'auto'
    const newHeight = Math.min(textarea.scrollHeight, 160)
    textarea.style.height = newHeight + 'px'
  })
}

// 监听输入值变化，调整高度
watch(inputValue, () => {
  autoResize()
  syncShortcutState()
})

watch(filteredShortcutItems, (items) => {
  if (!items.length) {
    activeShortcutIndex.value = 0
    return
  }
  if (activeShortcutIndex.value >= items.length) {
    activeShortcutIndex.value = 0
  }
})

// 文件处理
const openFilePicker = () => {
  fileInputRef.value?.click()
}

const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  
  if (files.length) {
    emit('file-select', files)
  }
  
  // 清空 input 以便重复选择同一文件
  input.value = ''
}

const removeAttachment = (storedName: string) => {
  emit('attachment-remove', storedName)
}

// 粘贴处理
const handlePaste = (event: ClipboardEvent) => {
  if (!props.enableAttachments) return

  const items = Array.from(event.clipboardData?.items || [])
  const files = items
    .filter(item => item.kind === 'file')
    .map(item => item.getAsFile())
    .filter((file): file is File => file !== null)

  if (files.length > 0) {
    emit('file-select', files)
    event.preventDefault()
  }
}

// 文件大小格式化
const formatFileSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

// 暴露方法给父组件
defineExpose({
  focus: () => textareaRef.value?.focus(),
  blur: () => textareaRef.value?.blur(),
  openFilePicker,
  clear: () => {
    emit('update:modelValue', '')
    nextTick(() => {
      if (textareaRef.value) {
        textareaRef.value.style.height = 'auto'
      }
    })
  },
})
</script>

<style scoped>
.chat-input-wrapper {
  padding: 10px 14px 12px;
  background: var(--mc-bg-elevated, #f8fafc);
  flex-shrink: 0;
}

/* 附件列表 */
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 8px;
}

.attachment-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  background: var(--mc-attachment-bg, #f1f5f9);
  border: 1px solid var(--mc-attachment-border, #e2e8f0);
  border-radius: 999px;
  padding: 6px 8px 6px 12px;
}

.attachment-chip--image,
.attachment-chip--video {
  padding: 4px 6px;
}

.attachment-chip__thumbnail {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.attachment-chip__label {
  display: inline-flex;
  gap: 8px;
  min-width: 0;
  color: var(--mc-attachment-color, #1e293b);
  text-decoration: none;
  font-size: 13px;
}

.attachment-chip__label span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.attachment-chip__label span:last-child {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--mc-primary, #D97757);
}

.attachment-chip__hint {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--mc-text-tertiary, #64748b) !important;
}

.attachment-chip__remove {
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 999px;
  background: rgba(217, 119, 87, 0.16);
  color: var(--mc-primary-hover, #C1572B);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.attachment-chip__remove:hover {
  background: rgba(217, 119, 87, 0.24);
}

.attachment-chip--dir {
  border-style: dashed;
}

.attachment-chip__path {
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 输入区域 */
.input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  background: var(--mc-input-bg, #ffffff);
  border: none;
  border-radius: 16px;
  padding: 8px 10px 8px 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08), 0 0 0 1px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.15s;
}

.chat-input-wrapper.is-focused .input-area {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1), 0 0 0 2px rgba(217, 119, 87, 0.25);
}

.chat-textarea {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  outline: none;
  font-size: 14px;
  line-height: 1.6;
  color: var(--mc-input-text, #1e293b);
  min-height: 38px;
  max-height: 160px;
  font-family: inherit;
}

.chat-textarea::placeholder {
  color: var(--mc-text-tertiary, #94a3b8);
}

.chat-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 操作按钮 */
.input-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.action-btn {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
  background: transparent;
  color: var(--mc-text-secondary, #64748b);
}

.action-btn:hover:not(:disabled) {
  background: var(--mc-bg-sunken, #f1f5f9);
  color: var(--mc-text-primary, #1e293b);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.thinking-btn {
  position: relative;
}
.thinking-btn.active {
  color: var(--el-color-primary, #409eff);
}
.thinking-btn.active::after {
  content: '';
  position: absolute;
  bottom: 4px;
  left: 50%;
  transform: translateX(-50%);
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--el-color-primary, #409eff);
}
/* RFC-049 PR-1-UI: model doesn't support reasoning_effort — stronger grayed state */
.thinking-btn.unsupported {
  opacity: 0.35;
  cursor: not-allowed;
}
.thinking-btn:hover:not(:disabled) {
  color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, rgba(64, 158, 255, 0.08));
}

.talk-btn:hover:not(:disabled) {
  color: var(--mc-primary, #D97757);
  background: var(--mc-primary-light, rgba(217, 119, 87, 0.08));
}

.send-btn {
  background: var(--mc-primary, #D97757);
  color: white;
}

.send-btn:hover:not(:disabled) {
  background: var(--mc-primary-hover, #C1572B);
}

.send-btn.is-loading {
  background: var(--mc-danger, #ef4444);
}

.send-btn.is-loading:hover:not(:disabled) {
  background: var(--mc-danger-hover, #dc2626);
}

.send-btn.is-empty:not(.is-loading) {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 底部信息 */
.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  padding: 0 4px;
  gap: 10px;
}

.input-footer__left,
.input-footer__right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.input-hint {
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.input-length {
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
}

/* 隐藏的文件输入 */
.hidden-file-input {
  position: absolute;
  width: 0;
  height: 0;
  opacity: 0;
  pointer-events: none;
}

/* 审批栏 */
.approval-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--mc-input-bg, #ffffff);
  border-radius: 16px;
  padding: 8px 8px 8px 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08), 0 0 0 1px rgba(217, 119, 87, 0.3);
  min-height: 50px;
}

.approval-bar__info {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--mc-text-secondary, #64748b);
}

.approval-bar__icon {
  display: flex;
  align-items: center;
  color: var(--mc-primary, #D97757);
  flex-shrink: 0;
}

.approval-bar__label {
  flex-shrink: 0;
}

.approval-bar__tool {
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  background: var(--mc-bg-sunken, #f1f5f9);
  padding: 1px 7px;
  border-radius: 5px;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
}

.approval-bar__scope {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.approval-bar__scope-label {
  font-size: 12px;
  color: var(--mc-text-tertiary, #94a3b8);
  white-space: nowrap;
}

.approval-bar__scope-select {
  min-width: 156px;
  height: 32px;
  border-radius: 10px;
  border: 1px solid var(--mc-border, #e2e8f0);
  background: var(--mc-bg-sunken, #f8fafc);
  color: var(--mc-text-primary, #1e293b);
  padding: 0 10px;
  font-size: 12px;
  outline: none;
}

.approval-bar__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.approval-bar__btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 14px;
  border: none;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1;
}

.approval-bar__btn--approve {
  background: var(--mc-primary, #D97757);
  color: #fff;
}

.approval-bar__btn--approve:hover {
  background: var(--mc-primary-hover, #C1572B);
}

.approval-bar__btn--deny {
  background: var(--mc-bg-sunken, #f1f5f9);
  color: var(--mc-text-secondary, #64748b);
  border: 1px solid var(--mc-border, #e2e8f0);
}

.approval-bar__btn--deny:hover {
  background: var(--mc-danger-bg, #fee2e2);
  color: var(--mc-danger, #ef4444);
  border-color: var(--mc-danger-border, #fca5a5);
}

/* 输入区域容器 */
.input-area-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  position: relative;
}

.shortcut-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(100% + 8px);
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 6px;
  border-radius: 12px;
  background: var(--mc-input-bg, #ffffff);
  border: 1px solid var(--mc-border, #e2e8f0);
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.12);
}

.shortcut-panel__header {
  padding: 2px 8px 5px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--mc-text-tertiary, #94a3b8);
}

.shortcut-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  border-radius: 9px;
  background: transparent;
  padding: 7px 9px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease;
}

.shortcut-item:hover,
.shortcut-item.is-active {
  background: rgba(217, 119, 87, 0.08);
}

.shortcut-item__icon {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: rgba(217, 119, 87, 0.12);
  color: var(--mc-primary, #D97757);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.shortcut-item__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.shortcut-item__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--mc-text-primary, #1e293b);
}

.shortcut-item__desc {
  font-size: 11px;
  color: var(--mc-text-secondary, #64748b);
}

.shortcut-item__value {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
  font-family: ui-monospace, 'SFMono-Regular', Consolas, monospace;
}

.shortcut-panel__empty {
  padding: 8px 10px;
  font-size: 11px;
  color: var(--mc-text-tertiary, #94a3b8);
}

.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-height: 32px;
}

.composer-toolbar__left,
.composer-toolbar__right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

/* 排队指示器 */
.queued-indicator {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  margin-bottom: 3px;
  border-radius: 10px;
  background: rgba(59, 130, 246, 0.06);
  border: 1px solid rgba(59, 130, 246, 0.15);
}

.queued-indicator__info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--mc-info, #3b82f6);
}

@media (max-width: 768px) {
  .chat-input-wrapper {
    padding: 8px 10px 10px;
  }

  .input-area {
    gap: 8px;
    padding: 7px 8px 7px 10px;
    border-radius: 14px;
  }

  .shortcut-item {
    align-items: flex-start;
  }

  .shortcut-item__value {
    display: none;
  }

  .composer-toolbar,
  .input-footer {
    flex-wrap: wrap;
  }

  .chat-textarea {
    min-height: 34px;
    line-height: 1.55;
  }

  .action-btn {
    width: 32px;
    height: 32px;
  }

  .attachment-chip__label span:first-child {
    max-width: 180px;
  }
}

.queued-indicator__text {
  font-weight: 500;
}

.queued-indicator__cancel {
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
  background: none;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 2px 8px;
  cursor: pointer;
  transition: all 0.15s;
}

.queued-indicator__cancel:hover {
  color: var(--mc-danger, #ef4444);
  border-color: var(--mc-danger-border, #fca5a5);
  background: var(--mc-danger-bg, #fee2e2);
}

/* 中断发送按钮样式 */
.send-btn.is-interrupt {
  background: var(--mc-warning, #f59e0b);
  color: white;
}

.send-btn.is-interrupt:hover:not(:disabled) {
  background: var(--mc-warning-hover, #d97706);
}

/* ===== 移动端适配 ===== */
@media (max-width: 768px) {
  .chat-input-wrapper {
    padding: 10px 12px 14px;
  }

  .approval-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    padding: 10px 12px;
  }

  .approval-bar__info {
    flex-wrap: wrap;
  }

  .approval-bar__scope {
    justify-content: space-between;
  }

  .approval-bar__scope-select {
    flex: 1;
    min-width: 0;
  }

  .approval-bar__actions {
    justify-content: flex-end;
  }

  .approval-bar__tool {
    max-width: 180px;
  }

  .attachment-chip__label span:first-child {
    max-width: 180px;
  }
}
</style>
