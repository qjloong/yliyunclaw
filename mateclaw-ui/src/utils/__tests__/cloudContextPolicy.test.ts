import { describe, expect, it } from 'vitest'
import {
  cloudContextKey,
  cloudContextPaths,
  shouldQueueManagedCloudContext,
} from '../cloudContextPolicy'

describe('cloud context attachment policy', () => {
  it('builds stable typed paths and keys', () => {
    const context = { fileId: '17485', folderId: '200' }
    expect(cloudContextPaths(context)).toEqual([
      'yliyun://file/17485',
      'yliyun://folder/200',
    ])
    expect(cloudContextKey(context)).toBe('yliyun://file/17485|yliyun://folder/200')
  })

  it('queues a new context for its first turn', () => {
    expect(shouldQueueManagedCloudContext({
      context: { fileId: '17485' },
    })).toBe(true)
  })

  it('does not queue the same context on follow-up turns', () => {
    expect(shouldQueueManagedCloudContext({
      context: { fileId: '17485' },
      lastDeliveredKey: 'yliyun://file/17485',
    })).toBe(false)
  })

  it('queues again when the host switches resources', () => {
    expect(shouldQueueManagedCloudContext({
      context: { fileId: '17486' },
      lastDeliveredKey: 'yliyun://file/17485',
      conversationContainsContext: true,
    })).toBe(true)
  })

  it('does not queue when restoring history that already contains the context', () => {
    expect(shouldQueueManagedCloudContext({
      context: { fileId: '17485' },
      conversationContainsContext: true,
    })).toBe(false)
  })
})
