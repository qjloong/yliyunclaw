import { randomBytes } from 'node:crypto';
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

assert(Number.isSafeInteger(tenantId) && tenantId > 0, 'tenant id is invalid');
assert(Number.isSafeInteger(userId) && userId > 0, 'user id is invalid');
assert(appKey, 'MCP app key is empty');

const tokenExchange = await json(`${cloudBaseUrl}/extends/user-token/get`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', 'tenant-id': String(tenantId) },
  body: JSON.stringify({ tenantId, userId, appKey }),
});
assert(tokenExchange.code === 0 && tokenExchange.data?.token, 'cloud token exchange failed');

const state = `state_${randomBytes(18).toString('base64url')}`;
const nonce = `nonce_${randomBytes(18).toString('base64url')}`;
const ticketResponse = await json(`${cloudBaseUrl}/admin-api/yliyun/ai/ticket`, {
  method: 'POST',
  headers: {
    authorization: `Bearer ${tokenExchange.data.token}`,
    'content-type': 'application/json',
    'tenant-id': String(tenantId),
  },
  body: JSON.stringify({ state, nonce, parentOrigin, launchContext: {} }),
});
assert(ticketResponse.code === 0 && ticketResponse.data?.ticket, 'AI ticket creation failed');
const ticket = ticketResponse.data.ticket;
const payload = JSON.parse(Buffer.from(ticket.split('.', 1)[0], 'base64url').toString('utf8'));
assert(payload.kid, 'ticket does not contain a key id');

const ssoUrl = new URL('/api/v1/auth/yliyun/ticket', mateclawBaseUrl);
ssoUrl.searchParams.set('ticket', ticket);
ssoUrl.searchParams.set('redirect', '/chat');
ssoUrl.searchParams.set('parentOrigin', parentOrigin);
ssoUrl.searchParams.set('state', state);
ssoUrl.searchParams.set('nonce', nonce);
const sso = await request(ssoUrl);
assert(sso.status === 302, `MateClaw SSO failed: HTTP ${sso.status}`);
const cookie = sso.headers['set-cookie']?.[0]?.split(';', 1)[0];
assert(cookie, 'MateClaw SSO did not return a cookie');

const session = await json(`${mateclawBaseUrl}/api/v1/auth/session`, {
  headers: { cookie },
});
assert(session.code === 200, `MateClaw session failed: ${session.code}`);
assert(String(session.data?.cloudTenantId) === String(tenantId), 'session tenant does not match');
assert(String(session.data?.cloudUserId) === String(userId), 'session user does not match');

console.log(JSON.stringify({
  success: true,
  ticketKeyId: payload.kid,
  tenantId,
  userId,
  sessionAuthSource: session.data?.authSource,
}, null, 2));

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
  if (body && headers['content-length'] === undefined) {
    headers['content-length'] = String(body.length);
  }
  const transport = target.protocol === 'https:' ? httpsRequest : httpRequest;
  return new Promise((resolve, reject) => {
    const req = transport(target, {
      method: init.method || 'GET',
      headers,
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
    req.setTimeout(10_000, () => req.destroy(new Error(`request timed out: ${target.pathname}`)));
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
