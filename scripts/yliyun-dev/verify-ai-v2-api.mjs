import { randomBytes, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { fileURLToPath } from 'node:url';

const cloudBaseUrl = process.env.YLIYUN_CLOUD_BASE_URL || 'http://127.0.0.1:30303';
const mateclawBaseUrl = process.env.MATECLAW_BASE_URL || 'http://127.0.0.1:18088';
const parentOrigin = process.env.YLIYUN_CLOUD_PARENT_ORIGIN || 'http://localhost:8080';
const tenantId = Number(process.env.YLIYUN_TEST_TENANT_ID || '1');
const userId = Number(process.env.YLIYUN_TEST_OWNER_USER_ID || '100');
const appKeyPath = fileURLToPath(new URL('../../data/yliyun-dev/mcp-app-key.txt', import.meta.url));
const appKey = (await readFile(appKeyPath, 'utf8')).trim();
const marker = randomBytes(8).toString('hex');
const fileName = `ai-v2-contract-${marker}.txt`;
const initialContent = `AI V2 contract verification ${marker}`;
const updatedContent = `${initialContent}\nupdated through write-content`;

const exchange = await json(`${cloudBaseUrl}/extends/user-token/get`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', 'tenant-id': String(tenantId) },
  body: JSON.stringify({ tenantId, userId, appKey }),
});
assert(exchange.code === 0 && exchange.data?.token, 'cloud token exchange failed');

const authHeaders = {
  authorization: `Bearer ${exchange.data.token}`,
  'content-type': 'application/json',
  'tenant-id': String(tenantId),
};

let createdFileId;
try {
  const profile = await api('/admin-api/cloud-drive/ai/v2/users/me');
  assert(profile.apiVersion === '2.0', 'user profile API version mismatch');
  assert(Number(profile.userId) === userId, 'user profile identity mismatch');
  assert(Number(profile.tenantId) === tenantId, 'user profile tenant mismatch');

  const spaces = await api('/admin-api/cloud-drive/ai/v2/spaces/context');
  assert(spaces.apiVersion === '2.0', 'space context API version mismatch');
  assert(spaces.spaces?.some((space) => space.type === 'PERSONAL' && Number(space.id) === userId),
    'personal space is missing');

  const created = await api('/admin-api/cloud-drive/ai/v2/files/write-content', {
    method: 'POST',
    body: JSON.stringify({
      parentId: 0,
      spaceType: 'PERSONAL',
      spaceId: userId,
      fileName,
      content: initialContent,
      mimeType: 'text/plain',
      idempotencyKey: randomUUID(),
    }),
  });
  createdFileId = created.file?.id;
  assert(created.apiVersion === '2.0' && created.created === true && createdFileId,
    'write-content did not create a file');

  const firstRead = await api(`/admin-api/cloud-drive/ai/v2/files/text-content?fileId=${createdFileId}&maxChars=5000`);
  assert(firstRead.content === initialContent, 'text-content did not return the created content');
  assert(firstRead.metadata?.extractor === 'plain-text', 'text-content extractor mismatch');

  const search = await api(`/admin-api/cloud-drive/ai/v2/files/search?query=${marker}&maxResults=20`);
  assert(search.apiVersion === '2.0', 'search API version mismatch');
  assert(search.files?.some((file) => Number(file.id) === Number(createdFileId)),
    'global search did not find the created file');

  const { cookie, workspaceId } = await establishMateClawSession();
  const profileTool = await proxyCall('user.profile', {}, cookie, workspaceId);
  assert(Number(profileTool.userId) === userId && Number(profileTool.tenantId) === tenantId,
    'MCP user.profile identity mismatch');
  const spacesTool = await proxyCall('space.context', {}, cookie, workspaceId);
  assert(spacesTool.spaces?.some((space) => space.type === 'PERSONAL' && Number(space.id) === userId),
    'MCP space.context is missing the personal space');
  const searchTool = await proxyCall('file.search', {
    query: marker,
    maxResults: 20,
  }, cookie, workspaceId);
  assert(searchTool.files?.some((file) => Number(file.id) === Number(createdFileId)),
    'MCP file.search did not find the created file');
  const readTool = await proxyCall('file.read', {
    fileId: createdFileId,
    maxChars: 5000,
    format: 'text',
  }, cookie, workspaceId);
  assert(readTool.content === initialContent, 'MCP file.read did not return the created content');
  const saveTool = await proxyCall('file.save', {
    fileId: createdFileId,
    content: updatedContent,
    createVersion: true,
    versionNote: 'V2 contract verification',
    idempotencyKey: randomUUID(),
  }, cookie, workspaceId);
  assert(Number(saveTool.fileId) === Number(createdFileId), 'MCP file.save returned the wrong file');

  const secondRead = await api(`/admin-api/cloud-drive/ai/v2/files/text-content?fileId=${createdFileId}&maxChars=5000`);
  assert(secondRead.content === updatedContent, 'text-content did not return the updated version');

  console.log(JSON.stringify({
    success: true,
    apiVersion: profile.apiVersion,
    tenantId,
    userId,
    tenantName: profile.tenantName,
    tenantAdmin: profile.tenantAdmin,
    visibleSpaces: spaces.spaces.length,
    createdFileId,
    searchMatches: search.files.length,
    extractor: secondRead.metadata?.extractor,
    mcpValidatedTools: ['user.profile', 'space.context', 'file.search', 'file.read', 'file.save'],
  }, null, 2));
} finally {
  if (createdFileId) {
    const cleanupTask = await api('/admin-api/cloud-drive/file/delete', {
      method: 'DELETE',
      body: JSON.stringify({ ids: [createdFileId], operationId: randomUUID() }),
    }).catch((error) => {
      console.error(`cleanup failed for ${createdFileId}: ${error.message}`);
      return null;
    });
    if (cleanupTask?.id) {
      await waitForOperationTask(cleanupTask).catch((error) =>
        console.error(`cleanup task failed for ${createdFileId}: ${error.message}`));
    }
  }
}

async function waitForOperationTask(initialTask) {
  let task = initialTask;
  const deadline = Date.now() + 30_000;
  while (!['SUCCESS', 'FAILED', 'PARTIAL_SUCCESS'].includes(task.status)) {
    assert(Date.now() < deadline, `cleanup task ${task.id} timed out`);
    await new Promise((resolve) => setTimeout(resolve, 100));
    task = await api(`/admin-api/cloud-drive/operation-task/${task.id}`);
  }
  assert(task.status === 'SUCCESS',
    `cleanup task ${task.id} ended with ${task.status}: ${task.errorMessage || task.message || ''}`);
}

async function establishMateClawSession() {
  const state = `state_${randomBytes(18).toString('base64url')}`;
  const nonce = `nonce_${randomBytes(18).toString('base64url')}`;
  const ticket = await api('/admin-api/yliyun/ai/ticket', {
    method: 'POST',
    body: JSON.stringify({ state, nonce, parentOrigin, launchContext: {} }),
  });
  assert(ticket?.ticket, 'AI ticket creation failed');

  const ssoUrl = new URL('/api/v1/auth/yliyun/ticket', mateclawBaseUrl);
  ssoUrl.searchParams.set('ticket', ticket.ticket);
  ssoUrl.searchParams.set('redirect', '/chat');
  ssoUrl.searchParams.set('parentOrigin', parentOrigin);
  ssoUrl.searchParams.set('state', state);
  ssoUrl.searchParams.set('nonce', nonce);
  const sso = await request(ssoUrl);
  assert(sso.status === 302, `MateClaw SSO failed: HTTP ${sso.status}`);
  const cookie = firstCookie(sso.headers['set-cookie']);
  assert(cookie, 'MateClaw SSO did not return a cookie');

  const session = await mateclawJson('/api/v1/auth/session', cookie);
  assert(Number(session.data?.cloudTenantId) === tenantId, 'MateClaw session tenant mismatch');
  assert(Number(session.data?.cloudUserId) === userId, 'MateClaw session user mismatch');
  const workspaces = await mateclawJson('/api/v1/workspaces', cookie);
  const workspace = Array.isArray(workspaces.data) ? workspaces.data[0] : null;
  assert(workspace?.id, 'MateClaw tenant workspace is missing');
  return { cookie, workspaceId: workspace.id };
}

async function proxyCall(toolName, args, cookie, workspaceId) {
  const response = await request(
    `${mateclawBaseUrl}/api/v1/mcp/proxy/${encodeURIComponent(toolName)}`,
    {
      method: 'POST',
      headers: {
        cookie,
        'content-type': 'application/json',
        'X-Workspace-Id': String(workspaceId),
      },
      body: JSON.stringify(args),
    },
  );
  const body = parseJson(response.body);
  assert(response.ok && body, `MCP proxy ${toolName} failed: HTTP ${response.status}`);
  assert(body.code === 200, `MCP proxy ${toolName} failed: ${body.errorCode || body.msg}`);
  const payload = parseJson(body.data);
  assert(payload, `MCP proxy ${toolName} returned a non-JSON payload`);
  return payload;
}

async function mateclawJson(path, cookie) {
  const response = await request(`${mateclawBaseUrl}${path}`, { headers: { cookie } });
  const body = parseJson(response.body);
  assert(response.ok && body, `MateClaw endpoint ${path} failed: HTTP ${response.status}`);
  assert(body.code === 200, `MateClaw endpoint ${path} failed: ${body.code} ${body.msg || ''}`);
  return body;
}

function firstCookie(setCookie) {
  const value = Array.isArray(setCookie) ? setCookie[0] : setCookie;
  return value ? value.split(';', 1)[0] : '';
}

async function api(path, init = {}) {
  const response = await request(new URL(path, cloudBaseUrl), {
    ...init,
    headers: { ...authHeaders, ...(init.headers || {}) },
  });
  const body = parseJson(response.body);
  assert(response.ok && body, `request failed: ${path} HTTP ${response.status}`);
  assert(body.code === 0, `cloud business error: ${body.code} ${body.msg || ''}`);
  return body.data;
}

async function json(url, init) {
  const response = await request(url, init);
  const body = parseJson(response.body);
  assert(response.ok && body, `request failed: ${new URL(url).pathname} HTTP ${response.status}`);
  return body;
}

function request(url, init = {}) {
  const target = new URL(url);
  const body = init.body ? Buffer.from(init.body) : null;
  const headers = { ...(init.headers || {}) };
  if (body && headers['content-length'] === undefined) headers['content-length'] = String(body.length);
  const transport = target.protocol === 'https:' ? httpsRequest : httpRequest;
  return new Promise((resolve, reject) => {
    const req = transport(target, { method: init.method || 'GET', headers }, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolve({
        status: res.statusCode || 0,
        ok: (res.statusCode || 0) >= 200 && (res.statusCode || 0) < 300,
        headers: res.headers,
        body: Buffer.concat(chunks).toString('utf8'),
      }));
    });
    req.setTimeout(30_000, () => req.destroy(new Error(`request timed out: ${target.pathname}`)));
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

function parseJson(value) {
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}
