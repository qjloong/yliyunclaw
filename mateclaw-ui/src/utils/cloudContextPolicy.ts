import type { CloudResourceBinding, CloudResourceState, CloudResourceType } from '@/api'

export interface CloudResourceRefLike {
  refId: string
  path: string
  resourceType: CloudResourceType
  resourceId?: string
  displayName?: string
  versionId?: string
  binding: CloudResourceBinding
  mimeType?: string
  expiresAt?: number
  status?: CloudResourceState
  currentVersionId?: string
  checkedAt?: number
  statusMessage?: string
}

export interface CloudContextLike {
  resourceRefs?: CloudResourceRefLike[]
  fileId?: string
  fileName?: string
  folderId?: string
  folderName?: string
}

export function cloudContextPaths(context: CloudContextLike): string[] {
  const signedPaths = (context.resourceRefs || [])
    .map(ref => ref.path)
    .filter(path => path.startsWith('yliyun-ref://'))
  if (signedPaths.length > 0) return [...new Set(signedPaths)]

  // Compatibility only: new Yliyun embed contexts are sealed as refIds before
  // they reach ChatConsole. Legacy/full-page links can still finish one turn.
  const paths: string[] = []
  if (context.fileId) paths.push(`yliyun://file/${context.fileId}`)
  if (context.folderId) paths.push(`yliyun://folder/${context.folderId}`)
  return paths
}

export function cloudContextKey(context: CloudContextLike): string {
  return cloudContextPaths(context).join('|')
}

export interface ManagedCloudContextDecision {
  context: CloudContextLike
  lastDeliveredKey?: string
  conversationContainsContext?: boolean
}

/**
 * A host-provided cloud context is a one-shot conversational attachment.
 *
 * It is queued for the first turn, and again when the host changes the
 * selected resource. Restoring an existing conversation whose history already
 * contains the resource must not enqueue it a second time.
 */
export function shouldQueueManagedCloudContext({
  context,
  lastDeliveredKey,
  conversationContainsContext = false,
}: ManagedCloudContextDecision): boolean {
  const nextKey = cloudContextKey(context)
  if (!nextKey) return false
  if (lastDeliveredKey) return lastDeliveredKey !== nextKey
  return !conversationContainsContext
}
