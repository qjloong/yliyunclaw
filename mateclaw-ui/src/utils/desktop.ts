type LegacyDesktopBridge = {
  isDesktop?: boolean
  openPath?: (targetPath: string) => Promise<boolean>
  revealPath?: (targetPath: string) => Promise<boolean>
}

function getLegacyDesktopBridge(): LegacyDesktopBridge | undefined {
  return (window as Window & { mateclawDesktop?: LegacyDesktopBridge }).mateclawDesktop
}

export function isDesktopRuntime(): boolean {
  return Boolean(getLegacyDesktopBridge()?.isDesktop || window.mateClawAPI)
}

export async function openDesktopPath(targetPath: string): Promise<boolean> {
  if (!targetPath) return false
  return (await getLegacyDesktopBridge()?.openPath?.(targetPath)) ?? false
}

export async function revealDesktopPath(targetPath: string): Promise<boolean> {
  if (!targetPath) return false
  return (await getLegacyDesktopBridge()?.revealPath?.(targetPath)) ?? false
}
