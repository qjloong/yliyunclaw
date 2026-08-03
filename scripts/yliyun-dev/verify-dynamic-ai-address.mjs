import { randomBytes } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { request as httpRequest } from 'node:http';
import { request as httpsRequest } from 'node:https';
import { fileURLToPath } from 'node:url';

const cloudBaseUrl = process.env.YLIYUN_CLOUD_BASE_URL || 'http://127.0.0.1:30303';
const tenantId = Number(process.env.YLIYUN_TEST_TENANT_ID || '1');
const userId = Number(process.env.YLIYUN_TEST_OWNER_USER_ID || '100');
const cloudParentOrigin = process.env.YLIYUN_CLOUD_PARENT_ORIGIN || 'http://localhost:8080';
const appKeyPath = fileURLToPath(new URL('../../data/yliyun-dev/mcp-app-key.txt', import.meta.url));
const appKey = (await readFile(appKeyPath, 'utf8')).trim();
const configPrefix = 'app.mateclaw_ai_assistant.';

assert(Number.isSafeInteger(tenantId) && tenantId > 0, 'tenant id is invalid');
assert(Number.isSafeInteger(userId) && userId > 0, 'user id is invalid');
assert(appKey, 'MCP app key is empty');

const original = await loadPlatformConfig();
const initialCapability = await capability();
const changed = [];
let switchedCapability;
let apiSwitchedCapability;

try {
  const rejected = await update('mateclaw_api_url', 'http://127.0.0.1:9', false);
  assert(rejected.code !== 0, 'unhealthy candidate API address was unexpectedly accepted');
  const afterRejected = await loadPlatformConfig();
  assert(afterRejected.mateclaw_api_url === original.mateclaw_api_url,
    'unhealthy candidate API address was not rolled back');
  assert((await capability()).configVersion === initialCapability.configVersion,
    'rejected address unexpectedly changed configVersion');

  const alternatePublic = alternateLoopback(original.mateclaw_public_url, 5173);
  const alternateOrigin = new URL(alternatePublic).origin;
  if (alternatePublic !== original.mateclaw_public_url) {
    await update('mateclaw_public_url', alternatePublic);
    changed.push(['mateclaw_public_url', original.mateclaw_public_url]);
  }
  if (alternateOrigin !== original.allowed_origin) {
    await update('allowed_origin', alternateOrigin);
    changed.push(['allowed_origin', original.allowed_origin]);
  }
  switchedCapability = await capability();
  assert(switchedCapability.launchBaseUrl === alternatePublic, 'capability did not expose new public URL');
  assert(switchedCapability.allowedOrigin === alternateOrigin, 'capability did not expose new allowed origin');
  assert(switchedCapability.configVersion > initialCapability.configVersion,
    'public address change did not advance configVersion');
  await verifySsoThroughLaunchBase(switchedCapability);

  const alternateApi = alternateLoopback(original.mateclaw_api_url, 18088);
  if (alternateApi !== original.mateclaw_api_url) {
    await update('mateclaw_api_url', alternateApi);
    changed.push(['mateclaw_api_url', original.mateclaw_api_url]);
  }
  apiSwitchedCapability = await capability();
  assert(apiSwitchedCapability.configVersion >= switchedCapability.configVersion,
    'API address change moved configVersion backwards');
} finally {
  const rollbackErrors = [];
  for (const [field, value] of changed.reverse()) {
    try {
      await update(field, value);
    } catch (error) {
      rollbackErrors.push(`${field}: ${error.message}`);
    }
  }
  if (rollbackErrors.length) {
    throw new Error(`dynamic address rollback failed: ${rollbackErrors.join('; ')}`);
  }
}

const restored = await loadPlatformConfig();
const finalCapability = await capability();
assert(restored.mateclaw_public_url === original.mateclaw_public_url, 'public URL was not restored');
assert(restored.mateclaw_api_url === original.mateclaw_api_url, 'API URL was not restored');
assert(restored.allowed_origin === original.allowed_origin, 'allowed origin was not restored');
await verifySsoThroughLaunchBase(finalCapability);

console.log(JSON.stringify({
  success: true,
  rejectedUnhealthyAddress: true,
  initialConfigVersion: initialCapability.configVersion,
  switchedConfigVersion: switchedCapability?.configVersion,
  apiSwitchedConfigVersion: apiSwitchedCapability?.configVersion,
  finalConfigVersion: finalCapability.configVersion,
  restoredPublicUrl: restored.mateclaw_public_url,
  restoredApiUrl: restored.mateclaw_api_url,
}, null, 2));

async function loadPlatformConfig() {
  const body = await json(`${cloudBaseUrl}/admin-api/cloud-drive/app-market/instances/`
    + 'mateclaw_ai_assistant/config-groups', { headers: await authenticatedHeaders() });
  assert(body.code === 0 && Array.isArray(body.data), 'failed to read AI assistant config');
  const items = body.data.flatMap((group) => group.items || []);
  return Object.fromEntries(items
    .filter((item) => item.configKey?.startsWith(configPrefix))
    .map((item) => [item.configKey.slice(configPrefix.length), item.configValue]));
}

async function capability() {
  const body = await json(`${cloudBaseUrl}/admin-api/yliyun/ai/capability`, {
    headers: await authenticatedHeaders(),
  });
  assert(body.code === 0 && body.data?.installed, 'AI assistant capability is unavailable');
  return body.data;
}

async function update(field, value, expectSuccess = true) {
  const body = await json(`${cloudBaseUrl}/admin-api/cloud-drive/app-market/instances/`
    + 'mateclaw_ai_assistant/config-item', {
    method: 'PUT',
    headers: await authenticatedHeaders(),
    body: JSON.stringify({ configKey: `${configPrefix}${field}`, configValue: value }),
  });
  if (expectSuccess) assert(body.code === 0, `failed to update ${field}: ${body.code}`);
  return body;
}

async function verifySsoThroughLaunchBase(currentCapability) {
  const state = `state_${randomBytes(18).toString('base64url')}`;
  const nonce = `nonce_${randomBytes(18).toString('base64url')}`;
  const issued = await json(`${cloudBaseUrl}/admin-api/yliyun/ai/ticket`, {
    method: 'POST',
    headers: await authenticatedHeaders(),
    body: JSON.stringify({ state, nonce, parentOrigin: cloudParentOrigin, launchContext: {} }),
  });
  assert(issued.code === 0 && issued.data?.ticket, 'AI ticket creation failed');
  const ssoUrl = new URL('/api/v1/auth/yliyun/ticket', currentCapability.launchBaseUrl);
  ssoUrl.searchParams.set('ticket', issued.data.ticket);
  ssoUrl.searchParams.set('redirect', '/chat');
  ssoUrl.searchParams.set('parentOrigin', cloudParentOrigin);
  ssoUrl.searchParams.set('state', state);
  ssoUrl.searchParams.set('nonce', nonce);
  const sso = await request(ssoUrl);
  assert(sso.status === 302, `SSO through launch base failed: HTTP ${sso.status}`);
  const cookie = sso.headers['set-cookie']?.[0]?.split(';', 1)[0];
  assert(cookie, 'SSO through launch base did not return a cookie');
  const session = await json(new URL('/api/v1/auth/session', currentCapability.launchBaseUrl), {
    headers: { cookie },
  });
  assert(session.code === 200, `session through launch base failed: ${session.code}`);
  assert(String(session.data?.cloudTenantId) === String(tenantId), 'session tenant mismatch');
  assert(String(session.data?.cloudUserId) === String(userId), 'session user mismatch');
}

async function authenticatedHeaders() {
  // 平台配置更新会主动撤销 MCP 专用 delegation，管理验收不能复用上一步的旧 Token。
  const exchange = await json(`${cloudBaseUrl}/extends/user-token/get`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'tenant-id': String(tenantId) },
    body: JSON.stringify({ tenantId, userId, appKey }),
  });
  assert(exchange.code === 0 && exchange.data?.token, 'cloud token exchange failed');
  return {
    authorization: `Bearer ${exchange.data.token}`,
    'content-type': 'application/json',
    'tenant-id': String(tenantId),
  };
}

function alternateLoopback(value, fallbackPort) {
  const url = new URL(value);
  if (fallbackPort === 5173) {
    // 本机 Vite 默认只绑定 localhost/::1；127.0.0.1 在该环境明确不可达。
    url.hostname = url.hostname === 'localhost' ? '[::1]' : 'localhost';
  } else {
    url.hostname = url.hostname === 'localhost' ? '127.0.0.1' : 'localhost';
  }
  if (!url.port) url.port = String(fallbackPort);
  return url.toString().replace(/\/$/, '');
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
  const requestHeaders = { ...(init.headers || {}) };
  if (body && requestHeaders['content-length'] === undefined) {
    requestHeaders['content-length'] = String(body.length);
  }
  const transport = target.protocol === 'https:' ? httpsRequest : httpRequest;
  return new Promise((resolve, reject) => {
    const req = transport(target, {
      method: init.method || 'GET',
      headers: requestHeaders,
      // 本机 Vite 绑定 ::1，而 Node 16 默认可能先把 localhost 解析到 127.0.0.1。
      family: target.hostname === 'localhost' && target.port === '5173' ? 6 : undefined,
    }, (res) => {
      const chunks = [];
      res.on('data', (chunk) => chunks.push(chunk));
      res.on('end', () => resolve({
        status: res.statusCode || 0,
        ok: (res.statusCode || 0) >= 200 && (res.statusCode || 0) < 300,
        headers: res.headers,
        body: Buffer.concat(chunks).toString('utf8'),
      }));
    });
    req.setTimeout(12_000, () => req.destroy(new Error(`request timed out: ${target.pathname}`)));
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
