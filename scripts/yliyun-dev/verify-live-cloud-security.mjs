import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

const cloudBaseUrl = process.env.YLIYUN_CLOUD_BASE_URL || 'http://127.0.0.1:30303'
const mateclawBaseUrl = process.env.MATECLAW_BASE_URL || 'http://127.0.0.1:18088'
const tenantId = positiveInteger(process.env.YLIYUN_TEST_TENANT_ID || '1', 'YLIYUN_TEST_TENANT_ID')
const ownerUserId = positiveInteger(process.env.YLIYUN_TEST_OWNER_USER_ID || '100', 'YLIYUN_TEST_OWNER_USER_ID')
const attackerUserId = positiveInteger(process.env.YLIYUN_TEST_ATTACKER_USER_ID || '202', 'YLIYUN_TEST_ATTACKER_USER_ID')
const victimUserId = positiveInteger(process.env.YLIYUN_TEST_VICTIM_USER_ID || '117', 'YLIYUN_TEST_VICTIM_USER_ID')
const readableFileId = positiveInteger(process.env.YLIYUN_TEST_READABLE_FILE_ID || '17485', 'YLIYUN_TEST_READABLE_FILE_ID')
const protectedFileId = positiveInteger(process.env.YLIYUN_TEST_PROTECTED_FILE_ID || '17472', 'YLIYUN_TEST_PROTECTED_FILE_ID')
const forgedTenantId = positiveInteger(process.env.YLIYUN_TEST_FORGED_TENANT_ID || '2', 'YLIYUN_TEST_FORGED_TENANT_ID')
const cycles = positiveInteger(process.env.YLIYUN_TEST_READ_CYCLES || '3', 'YLIYUN_TEST_READ_CYCLES')
const burstSize = positiveInteger(process.env.YLIYUN_TEST_RATE_BURST || '105', 'YLIYUN_TEST_RATE_BURST')

const appKeyPath = fileURLToPath(new URL('../../data/yliyun-dev/mcp-app-key.txt', import.meta.url))
const appKey = (await readFile(appKeyPath, 'utf8')).trim()
assert(appKey, `MCP app key is empty: ${appKeyPath}`)

const ownerToken = await exchangeUserToken(ownerUserId, tenantId)
const storageCycles = []
for (let cycle = 1; cycle <= cycles; cycle += 1) {
  const metadata = await cloudJson(
    `/admin-api/cloud-drive/file/preview?id=${readableFileId}`,
    ownerToken,
    tenantId,
  )
  const previewUrlResult = await cloudJson(
    `/admin-api/cloud-drive/file/preview-stream-url?id=${readableFileId}`,
    ownerToken,
    tenantId,
  )
  const previewUrl = new URL(previewUrlResult.data, cloudBaseUrl).toString()
  const previewResponse = await fetch(previewUrl)
  const previewBytes = Buffer.from(await previewResponse.arrayBuffer())
  assert(previewResponse.ok, `preview stream failed: HTTP ${previewResponse.status}`)

  const downloadResponse = await fetch(
    `${cloudBaseUrl}/admin-api/cloud-drive/file/download?id=${readableFileId}`,
    { headers: cloudHeaders(ownerToken, tenantId) },
  )
  const downloadBytes = Buffer.from(await downloadResponse.arrayBuffer())
  assert(downloadResponse.ok, `download failed: HTTP ${downloadResponse.status}`)
  assert(previewBytes.length > 0, 'preview stream returned an empty body')
  assert(downloadBytes.length === previewBytes.length, 'preview/download byte lengths differ')

  const previewHash = sha256(previewBytes)
  const downloadHash = sha256(downloadBytes)
  assert(downloadHash === previewHash, 'preview/download SHA-256 values differ')
  storageCycles.push({
    cycle,
    fileName: metadata.data?.name,
    previewBytes: previewBytes.length,
    downloadBytes: downloadBytes.length,
    sha256: previewHash.slice(0, 16),
  })
}
assert(
  new Set(storageCycles.map((item) => `${item.previewBytes}:${item.sha256}`)).size === 1,
  'read/preview/download cycles returned inconsistent content',
)

const attackerToken = await exchangeUserToken(attackerUserId, tenantId)
const ticketResult = await cloudJson(
  '/admin-api/yliyun/ai/ticket',
  attackerToken,
  tenantId,
  { method: 'POST' },
)
const ssoResponse = await fetch(
  `${mateclawBaseUrl}/api/v1/auth/yliyun/ticket`
    + `?ticket=${encodeURIComponent(ticketResult.data)}&redirect=%2Fchat`,
  { redirect: 'manual' },
)
assert(ssoResponse.status === 302, `MateClaw SSO failed: HTTP ${ssoResponse.status}`)
const authCookie = firstCookie(ssoResponse.headers.get('set-cookie'))
assert(authCookie, 'MateClaw SSO did not return an authentication cookie')

const session = await mateclawJson('/api/v1/auth/session', authCookie)
assert(String(session.data?.cloudUserId) === String(attackerUserId), 'MateClaw session mapped the wrong cloud user')
assert(String(session.data?.cloudTenantId) === String(tenantId), 'MateClaw session mapped the wrong cloud tenant')
const workspaces = await mateclawJson('/api/v1/workspaces', authCookie)
const workspace = Array.isArray(workspaces.data) ? workspaces.data[0] : null
assert(workspace?.id, 'attacker has no MateClaw tenant workspace')

const readDenied = await proxyCall(
  'file.read',
  { fileId: protectedFileId, maxChars: 1000 },
  authCookie,
  workspace.id,
)
const deleteDenied = await proxyCall(
  'file.delete',
  {
    fileId: protectedFileId,
    permanent: false,
    idempotencyKey: `tc6-delete-denied-${Date.now()}`,
  },
  authCookie,
  workspace.id,
)
assert(readDenied.errorCode === 'PERMISSION_DENIED', `read denial code was ${readDenied.errorCode}`)
assert(deleteDenied.errorCode === 'PERMISSION_DENIED', `delete denial code was ${deleteDenied.errorCode}`)

const victimToken = await exchangeUserToken(victimUserId, tenantId)
const protectedFileAfter = await cloudJson(
  `/admin-api/cloud-drive/file/preview?id=${protectedFileId}`,
  victimToken,
  tenantId,
)
assert(protectedFileAfter.data?.status === 'ACTIVE', 'protected file changed after denied delete')

const forgedExchange = await exchangeUserTokenResult(attackerUserId, forgedTenantId)
assert(
  !forgedExchange.token,
  `cross-tenant identity forgery unexpectedly issued a token for tenant ${forgedTenantId}`,
)

const rateResults = await Promise.all(
  Array.from({ length: burstSize }, (_, index) => proxyCall(
    'user.profile',
    { sequence: index + 1 },
    authCookie,
    workspace.id,
  )),
)
const rateLimited = rateResults.filter((result) => result.errorCode === 'RATE_LIMITED')
assert(rateLimited.length > 0, `burst of ${burstSize} requests did not trigger RATE_LIMITED`)

console.log(JSON.stringify({
  success: true,
  storage: {
    fileId: readableFileId,
    cycles: storageCycles,
  },
  tc6: {
    attacker: { tenantId, userId: attackerUserId, workspaceRole: workspace.memberRole },
    victim: { tenantId, userId: victimUserId, protectedFileId },
    readDenied: { errorCode: readDenied.errorCode, stage: readDenied.stage },
    deleteDenied: { errorCode: deleteDenied.errorCode, stage: deleteDenied.stage },
    protectedFileStatus: protectedFileAfter.data.status,
    forgedTenant: { tenantId: forgedTenantId, tokenIssued: false, cloudCode: forgedExchange.code },
    rateLimit: {
      burstSize,
      successful: rateResults.filter((result) => result.code === 200).length,
      rateLimited: rateLimited.length,
      errorCode: rateLimited[0].errorCode,
      stage: rateLimited[0].stage,
    },
  },
}, null, 2))

async function exchangeUserToken(userId, requestedTenantId) {
  const result = await exchangeUserTokenResult(userId, requestedTenantId)
  assert(result.token, `token exchange failed for tenant=${requestedTenantId}, user=${userId}: ${result.code} ${result.msg}`)
  return result.token
}

async function exchangeUserTokenResult(userId, requestedTenantId) {
  const response = await fetch(`${cloudBaseUrl}/extends/user-token/get`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'tenant-id': String(requestedTenantId),
    },
    body: JSON.stringify({
      tenantId: requestedTenantId,
      userId,
      appKey,
    }),
  })
  const body = await response.json().catch(() => null)
  assert(response.ok && body, `token endpoint failed: HTTP ${response.status}`)
  return {
    code: body.code,
    msg: body.msg,
    token: body.code === 0 ? body.data?.token : null,
  }
}

async function cloudJson(path, token, requestedTenantId, init = {}) {
  const response = await fetch(`${cloudBaseUrl}${path}`, {
    ...init,
    headers: {
      ...cloudHeaders(token, requestedTenantId),
      ...(init.headers || {}),
    },
  })
  const body = await response.json().catch(() => null)
  assert(response.ok && body, `cloud endpoint ${path} failed: HTTP ${response.status}`)
  assert(body.code === 0, `cloud endpoint ${path} failed: ${body.code} ${body.msg}`)
  return body
}

async function mateclawJson(path, cookie) {
  const response = await fetch(`${mateclawBaseUrl}${path}`, {
    headers: { cookie },
  })
  const body = await response.json().catch(() => null)
  assert(response.ok && body, `MateClaw endpoint ${path} failed: HTTP ${response.status}`)
  assert(body.code === 200, `MateClaw endpoint ${path} failed: ${body.code} ${body.msg}`)
  return body
}

async function proxyCall(toolName, args, cookie, workspaceId) {
  const response = await fetch(
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
  )
  const body = await response.json().catch(() => null)
  assert(response.ok && body, `MCP proxy ${toolName} failed: HTTP ${response.status}`)
  return body
}

function cloudHeaders(token, requestedTenantId) {
  return {
    authorization: `Bearer ${token}`,
    'tenant-id': String(requestedTenantId),
  }
}

function firstCookie(setCookie) {
  return setCookie ? setCookie.split(';', 1)[0] : ''
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex')
}

function positiveInteger(value, name) {
  const parsed = Number(value)
  assert(Number.isSafeInteger(parsed) && parsed > 0, `${name} must be a positive integer`)
  return parsed
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}
