export type GlobalAccountRole = 'admin' | 'user'

export function currentGlobalRole(): GlobalAccountRole {
  return localStorage.getItem('role') === 'admin' ? 'admin' : 'user'
}

export function isGlobalAdmin(): boolean {
  return currentGlobalRole() === 'admin'
}

export function canAccessAdminConsole(): boolean {
  return isGlobalAdmin()
}
