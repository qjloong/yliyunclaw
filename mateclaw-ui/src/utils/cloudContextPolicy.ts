export interface CloudContextLike {
  fileId?: string
  folderId?: string
}

export function cloudContextPaths(context: CloudContextLike): string[] {
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
