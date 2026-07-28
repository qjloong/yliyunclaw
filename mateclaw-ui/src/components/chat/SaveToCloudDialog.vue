<template>
  <el-dialog
    v-model="visible"
    title="保存到云盘"
    width="520px"
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <div class="save-dialog">
      <div class="save-dialog__field">
        <label>目标位置</label>
        <div class="save-dialog__path">
          <span v-for="(seg, i) in pathSegments" :key="i"
                class="save-dialog__path-seg"
                @click="navigateTo(seg.id)">
            {{ seg.name }}
          </span>
          <span v-if="pathSegments.length > 1" class="save-dialog__path-sep">/</span>
        </div>
        <div class="save-dialog__browser">
          <div v-if="browsing" class="save-dialog__loading">加载目录...</div>
          <ul v-else class="save-dialog__dirs">
            <li v-for="d in directories" :key="d.id"
                class="save-dialog__dir"
                @click="navigateTo(d.id, d.name)">
              📁 {{ d.name }}
            </li>
            <li v-if="directories.length === 0" class="save-dialog__empty">
              此目录下没有子文件夹
            </li>
          </ul>
        </div>
      </div>
      <div class="save-dialog__field">
        <label>文件名</label>
        <el-input v-model="fileName" placeholder="输入文件名" />
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">确认保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

const props = defineProps<{
  content: string;
  suggestedName?: string;
  mimeType?: string;
}>();

const emit = defineEmits<{
  saved: [result: { fileId: number; name: string; url?: string }];
}>();

const visible = ref(false);
const saving = ref(false);
const browsing = ref(false);
const fileName = ref('');
const directories = ref<Array<{ id: number; name: string }>>([]);
const pathSegments = ref<Array<{ id: number; name: string }>>([{ id: 0, name: '根目录' }]);
const currentParentId = ref(0);

function show() { visible.value = true; }
defineExpose({ show });

async function onOpen() {
  fileName.value = props.suggestedName || 'untitled.md';
  pathSegments.value = [{ id: 0, name: '根目录' }];
  await loadDirectories(0);
}

async function loadDirectories(parentId: number) {
  browsing.value = true;
  try {
    const result = await callMcpTool('file.list', {
      parentId,
      maxResults: 100,
      sortBy: 'name',
    });
    directories.value = (result.items || [])
      .filter((f: any) => f.type === 'directory' || f.isFolder);
  } catch { directories.value = []; }
  finally { browsing.value = false; }
}

async function navigateTo(id: number, name?: string) {
  currentParentId.value = id;
  if (id === 0) {
    pathSegments.value = [{ id: 0, name: '根目录' }];
  } else if (name) {
    const idx = pathSegments.value.findIndex(s => s.id === id);
    if (idx >= 0) {
      pathSegments.value = pathSegments.value.slice(0, idx + 1);
    } else {
      pathSegments.value.push({ id, name });
    }
  }
  await loadDirectories(id);
}

async function save() {
  if (!fileName.value.trim()) return;
  saving.value = true;
  try {
    const result = await callMcpTool('file.create', {
      parentId: currentParentId.value,
      name: fileName.value.trim(),
      content: props.content,
      mimeType: props.mimeType || 'text/markdown',
      type: 'file',
    });
    emit('saved', {
      fileId: result.fileId,
      name: result.name,
    });
    visible.value = false;
  } catch (e: any) {
    console.error('Save failed:', e);
  } finally {
    saving.value = false;
  }
}

async function callMcpTool(name: string, args: Record<string, unknown>) {
  const mcpCall = (window as any).__mcpCall;
  if (mcpCall) {
    const result = await mcpCall(name, args);
    return JSON.parse(result.content[0].text);
  }
  const resp = await fetch('http://localhost:18100/mcp', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${localStorage.getItem('token')}`,
    },
    body: JSON.stringify({
      jsonrpc: '2.0', id: Date.now(),
      method: 'tools/call', params: { name, arguments: args },
    }),
  });
  const json = await resp.json();
  return JSON.parse(json.result.content[0].text);
}
</script>

<style scoped>
.save-dialog__field { margin-bottom: 16px; }
.save-dialog__field label { display: block; font-weight: 600; margin-bottom: 6px; font-size: 13px; }
.save-dialog__path { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; margin-bottom: 8px; }
.save-dialog__path-seg { cursor: pointer; color: var(--el-color-primary); font-size: 13px; }
.save-dialog__path-seg:hover { text-decoration: underline; }
.save-dialog__path-sep { color: var(--el-text-color-secondary); }
.save-dialog__browser {
  border: 1px solid var(--el-border-color); border-radius: 6px;
  max-height: 180px; overflow-y: auto;
}
.save-dialog__loading { padding: 20px; text-align: center; }
.save-dialog__dirs { list-style: none; margin: 0; padding: 4px 0; }
.save-dialog__dir { padding: 6px 12px; cursor: pointer; font-size: 13px; }
.save-dialog__dir:hover { background: var(--el-fill-color-light); }
.save-dialog__empty { padding: 16px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }
</style>
