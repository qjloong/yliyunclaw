/**
 * 认证工具函数
 * 统一处理 token 失效跳转和自动续期
 */

const AUTH_REDIRECT_FLAG = 'mc-auth-redirecting'

export interface AuthSessionSnapshot {
  token: string
  userId?: string | number | null
  username?: string | null
  role?: string | null
}

let isRedirecting = sessionStorage.getItem(AUTH_REDIRECT_FLAG) === '1'

export function isAuthRedirectInProgress() {
  return isRedirecting || sessionStorage.getItem(AUTH_REDIRECT_FLAG) === '1'
}

export function getAuthToken() {
  return localStorage.getItem('token')
}

export function saveAuthSession(session: AuthSessionSnapshot) {
  localStorage.setItem('token', session.token)
  if (session.userId !== undefined && session.userId !== null) {
    localStorage.setItem('userId', String(session.userId))
  }
  if (session.username) {
    localStorage.setItem('username', session.username)
  }
  if (session.role) {
    localStorage.setItem('role', session.role)
  }
  isRedirecting = false
  sessionStorage.removeItem(AUTH_REDIRECT_FLAG)
}

export function clearAuthSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
}

export function logoutAndRedirect(target = '/login') {
  clearAuthSession()
  if (window.location.pathname === target) {
    isRedirecting = false
    sessionStorage.removeItem(AUTH_REDIRECT_FLAG)
    return
  }
  if (!isRedirecting) {
    isRedirecting = true
    sessionStorage.setItem(AUTH_REDIRECT_FLAG, '1')
    window.location.replace(target)
  }
}

/**
 * 处理认证失败：清除 token 并跳转登录页
 * 使用 isRedirecting 标记防止多个并发请求同时触发跳转
 */
export function handleAuthFailure() {
  logoutAndRedirect('/login')
}

/**
 * 从响应头中提取新 token 并更新 localStorage
 * 支持 fetch Headers 和 Axios headers（对象格式）
 */
export function updateTokenFromHeader(headers: any) {
  if (!headers) return
  const newToken =
    typeof headers.get === 'function'
      ? headers.get('x-new-token')
      : headers['x-new-token']
  if (newToken && typeof newToken === 'string') {
    const currentUserId = localStorage.getItem('userId')
    const currentUsername = localStorage.getItem('username')
    const currentRole = localStorage.getItem('role')
    saveAuthSession({
      token: newToken,
      userId: currentUserId,
      username: currentUsername,
      role: currentRole,
    })
  }
}
