import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const cloudBaseUrl = process.env.YLIYUN_CLOUD_BASE_URL || 'http://127.0.0.1:30303';
const mateclawBaseUrl = process.env.MATECLAW_BASE_URL || 'http://127.0.0.1:18088';
const parentOrigin = process.env.YLIYUN_CLOUD_PARENT_ORIGIN || 'http://localhost:8080';
const tenantId = Number(process.env.YLIYUN_TEST_TENANT_ID || '1');
const userId = Number(process.env.YLIYUN_TEST_OWNER_USER_ID || '100');
let folderId = String(process.env.YLIYUN_TEST_FIXTURE_FOLDER_ID || '');
let folderName = process.env.YLIYUN_TEST_FIXTURE_FOLDER_NAME || '_mateclaw_p0_fixtures';
const appKeyPath = fileURLToPath(new URL('../../data/yliyun-dev/mcp-app-key.txt', import.meta.url));
const appKey = (await readFile(appKeyPath, 'utf8')).trim();
const conversationId = `conv_folder_verify_${Date.now()}_${randomBytes(4).toString('hex')}`;

let cookie;
let workspaceId;
try {
  const tokenExchange = await json(`${cloudBaseUrl}/extends/user-token/get`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'tenant-id': String(tenantId) },
    body: JSON.stringify({ tenantId, userId, appKey }),
  });
  assert(tokenExchange.code === 0 && tokenExchange.data?.token, 'cloud token exchange failed');

  if (!folderId) {
    const folderSearch = await json(
      `${cloudBaseUrl}/admin-api/cloud-drive/file/list?parentId=0&keyword=${encodeURIComponent(folderName)}&pageNo=1&pageSize=100`,
      {
        headers: {
          authorization: `Bearer ${tokenExchange.data.token}`,
          'tenant-id': String(tenantId),
        },
      },
    );
    let match = (folderSearch.data?.list || []).find(item =>
      item.name === folderName && (item.fileType === 'FOLDER' || item.nodeType === 'FOLDER'));
    if (!match) {
      const root = await json(
        `${cloudBaseUrl}/admin-api/cloud-drive/file/list?parentId=0&pageNo=1&pageSize=200`,
        {
          headers: {
            authorization: `Bearer ${tokenExchange.data.token}`,
            'tenant-id': String(tenantId),
          },
        },
      );
      for (const candidate of root.data?.list || []) {
        if (candidate.fileType !== 'FOLDER' && candidate.nodeType !== 'FOLDER') continue;
        const children = await json(
          `${cloudBaseUrl}/admin-api/cloud-drive/file/list?parentId=${candidate.id}&pageNo=1&pageSize=20`,
          {
            headers: {
              authorization: `Bearer ${tokenExchange.data.token}`,
              'tenant-id': String(tenantId),
            },
          },
        );
        const readableFiles = (children.data?.list || []).filter(item =>
          item.fileType !== 'FOLDER' && item.nodeType !== 'FOLDER');
        if (readableFiles.length >= 3) {
          match = candidate;
          folderName = candidate.name;
          break;
        }
      }
    }
    folderId = match?.id != null ? String(match.id) : '';
    assert(folderId, 'no readable folder with at least three files was found');
  }

  const state = `state_${randomBytes(18).toString('base64url')}`;
  const nonce = `nonce_${randomBytes(18).toString('base64url')}`;
  const ticketResponse = await json(`${cloudBaseUrl}/admin-api/yliyun/ai/ticket`, {
    method: 'POST',
    headers: {
      authorization: `Bearer ${tokenExchange.data.token}`,
      'content-type': 'application/json',
      'tenant-id': String(tenantId),
    },
    body: JSON.stringify({
      state,
      nonce,
      parentOrigin,
      launchContext: { folderId, folderName },
    }),
  });
  assert(ticketResponse.code === 0 && ticketResponse.data?.ticket, 'AI ticket creation failed');

  const ssoUrl = new URL('/api/v1/auth/yliyun/ticket', mateclawBaseUrl);
  ssoUrl.searchParams.set('ticket', ticketResponse.data.ticket);
  ssoUrl.searchParams.set('redirect', '/chat');
  ssoUrl.searchParams.set('parentOrigin', parentOrigin);
  ssoUrl.searchParams.set('state', state);
  ssoUrl.searchParams.set('nonce', nonce);
  const sso = await fetch(ssoUrl, { redirect: 'manual' });
  assert(sso.status === 302, `MateClaw SSO failed: HTTP ${sso.status}`);
  cookie = sso.headers.getSetCookie?.()[0]?.split(';', 1)[0]
    || sso.headers.get('set-cookie')?.split(';', 1)[0];
  assert(cookie, 'MateClaw SSO did not return a cookie');

  const workspaces = await json(`${mateclawBaseUrl}/api/v1/workspaces`, {
    headers: { cookie },
  });
  assert(workspaces.code === 200 && Array.isArray(workspaces.data), 'workspace list failed');

  let agent;
  for (const workspace of workspaces.data) {
    const agents = await json(`${mateclawBaseUrl}/api/v1/agents?enabled=true`, {
      headers: { cookie, 'x-workspace-id': String(workspace.id) },
    });
    agent = agents.data?.find(item => item.templateId === 'builtin.yliyun_assistant');
    if (agent) {
      workspaceId = workspace.id;
      break;
    }
  }
  assert(agent && workspaceId, 'enabled Yliyun assistant agent was not found');

  const issued = await json(`${mateclawBaseUrl}/api/v1/auth/yliyun/resource-refs`, {
    method: 'POST',
    headers: { cookie, 'content-type': 'application/json' },
    body: JSON.stringify({
      resourceType: 'folder',
      resourceId: folderId,
      displayName: folderName,
      binding: 'current-preview',
    }),
  });
  assert(issued.code === 200 && issued.data?.path?.startsWith('yliyun-ref://'),
    'signed folder ref issuance failed');

  const prompt = '请分析当前测试文件夹，优先概括项目说明、数据和风险相关资料，回答简短。';
  const chatResponse = await fetch(`${mateclawBaseUrl}/api/v1/chat/stream`, {
    method: 'POST',
    headers: {
      cookie,
      accept: 'text/event-stream',
      'content-type': 'application/json',
      'x-workspace-id': String(workspaceId),
    },
    body: JSON.stringify({
      agentId: agent.id,
      conversationId,
      message: prompt,
      contentParts: [
        { type: 'text', text: prompt },
        {
          type: 'file',
          fileName: folderName,
          contentType: 'inode/directory',
          storedName: `cloud-ref:${issued.data.refId}`,
          path: issued.data.path,
        },
      ],
    }),
    signal: AbortSignal.timeout(180_000),
  });
  const stream = await chatResponse.text();
  assert(chatResponse.ok, `folder chat failed: HTTP ${chatResponse.status}`);
  for (const toolName of [
    'yliyun_attachment_list',
    'yliyun_attachment_summarize',
    'yliyun_attachment_read_selected',
  ]) {
    assert(stream.includes(toolName), `SSE stream is missing ${toolName}`);
  }

  const messages = await json(
    `${mateclawBaseUrl}/api/v1/conversations/${encodeURIComponent(conversationId)}/messages`,
    { headers: { cookie, 'x-workspace-id': String(workspaceId) } },
  );
  const serializedMessages = JSON.stringify(messages.data || []);
  assert(serializedMessages.includes('本轮按提问相关性读取的文件'),
    'persisted folder attachment does not contain progressive read content');

  const completedMatch = stream.match(/已按相关性读取[^\r\n"]+/);
  console.log(JSON.stringify({
    success: true,
    tenantId,
    userId,
    workspaceId,
    agentId: agent.id,
    folderId,
    progressiveRead: completedMatch?.[0] || 'completed',
    traceStages: ['list', 'summarize', 'read_selected'],
  }, null, 2));
} finally {
  if (cookie && workspaceId) {
    await fetch(`${mateclawBaseUrl}/api/v1/conversations/${encodeURIComponent(conversationId)}`, {
      method: 'DELETE',
      headers: { cookie, 'x-workspace-id': String(workspaceId) },
    }).catch(() => undefined);
  }
}

async function json(url, init) {
  const response = await fetch(url, init);
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  assert(response.ok && body, `request failed: ${new URL(url).pathname} HTTP ${response.status}`);
  return body;
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
