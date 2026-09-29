import { messagesApi } from '@/api/messages'
import { errorMessage, problemOf } from '@/api/client'
import type { ChatMessage, UploadStatus } from '@/api/types'
import { useUploads } from '@/stores/uploads'
import { config } from './config'

/**
 * Chunked, resumable uploads of up to 1 GB (spec sections 7.4 and 13.6).
 *
 * The file is cut into the server's chunk size and a few chunks travel at once. A chunk that fails is retried with
 * backoff, and after a dropped connection only the chunks the server hasn't got are sent. The upload id is remembered
 * in localStorage, so after a page refresh choosing the same file again continues where it stopped.
 */

const CONCURRENCY = 3
const MAX_ATTEMPTS = 5

export interface UploadHandle {
  id: string
  /** Resolves with the created media message, rejects with an Error whose message is safe to show. */
  done: Promise<ChatMessage>
  cancel: () => void
}

export class UploadRejected extends Error {}

/** Stops early with a clear message when a file can't be sent (spec section 7.4). */
export function checkFile(file: File): string | null {
  if (file.size === 0) return 'That file is empty.'
  if (file.size > config.maxFileSize)
    return `Files can be at most ${Math.round(config.maxFileSize / 1024 / 1024)} MB.`
  const major = file.type.split('/')[0]
  if (major !== 'image' && major !== 'audio' && major !== 'video')
    return 'Only images, audio and video can be sent.'
  return null
}

export function pendingKey(roomId: string, file: File): string {
  return `drift-upload:${roomId}:${file.name}:${file.size}:${file.lastModified}`
}

function remembered(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function remember(key: string, uploadId: string | null) {
  try {
    if (uploadId) localStorage.setItem(key, uploadId)
    else localStorage.removeItem(key)
  } catch {
    // resuming after a refresh is a bonus; the upload itself does not depend on storage
  }
}

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => (clearTimeout(timer), resolve()), { once: true })
  })

/** Retrying makes sense for network drops and server hiccups, not for "you're not allowed" or "wrong type". */
function retryable(error: unknown): boolean {
  const { status } = problemOf(error)
  return status === 0 || status === 408 || status === 429 || status >= 500
}

export function startUpload(roomId: string, file: File): UploadHandle {
  const id = crypto.randomUUID()
  const controller = new AbortController()
  const { signal } = controller
  const store = useUploads.getState()
  const key = pendingKey(roomId, file)
  let uploadId: string | null = null

  store.upsert({ id, roomId, fileName: file.name, size: file.size, sent: 0, phase: 'uploading' })

  const run = async (): Promise<ChatMessage> => {
    const problem = checkFile(file)
    if (problem) throw new UploadRejected(problem)

    // Continue an earlier attempt if the server still has it, otherwise start fresh
    let status: UploadStatus | null = null
    const earlier = remembered(key)
    if (earlier) {
      try {
        status = await messagesApi.uploadStatus(earlier)
        uploadId = earlier
      } catch {
        remember(key, null)
      }
    }
    if (!status) {
      const started = await messagesApi.startUpload({
        roomId,
        fileName: file.name,
        size: file.size,
        mimeType: file.type,
      })
      uploadId = started.uploadId
      remember(key, uploadId)
      status = {
        uploadId,
        roomId,
        fileName: file.name,
        size: file.size,
        chunkSize: started.chunkSize,
        totalChunks: started.totalChunks,
        received: [],
        missing: Array.from({ length: started.totalChunks }, (_, i) => i),
      }
    }

    const { chunkSize } = status
    const chunkLength = (index: number) => Math.min(chunkSize, file.size - index * chunkSize)
    const inFlight = new Map<number, number>()
    let finished = status.received.reduce((sum, i) => sum + chunkLength(i), 0)
    const report = () => {
      const sent = finished + [...inFlight.values()].reduce((a, b) => a + b, 0)
      store.patch(id, { sent: Math.min(sent, file.size) })
    }
    report()

    const queue = [...status.missing]
    const currentId = status.uploadId

    const sendChunk = async (index: number) => {
      for (let attempt = 1; ; attempt++) {
        try {
          const blob = file.slice(index * chunkSize, index * chunkSize + chunkLength(index))
          await messagesApi.uploadChunk(currentId, index, blob, {
            signal,
            onProgress: (loaded) => {
              inFlight.set(index, Math.min(loaded, chunkLength(index)))
              report()
            },
          })
          inFlight.delete(index)
          finished += chunkLength(index)
          report()
          return
        } catch (error) {
          inFlight.delete(index)
          if (signal.aborted) throw error
          if (attempt >= MAX_ATTEMPTS || !retryable(error)) throw error
          await sleep(500 * 2 ** (attempt - 1), signal)
        }
      }
    }

    const worker = async () => {
      for (let next = queue.shift(); next !== undefined; next = queue.shift()) {
        if (signal.aborted) return
        await sendChunk(next)
      }
    }
    await Promise.all(Array.from({ length: Math.min(CONCURRENCY, queue.length) }, worker))
    if (signal.aborted) throw new UploadRejected('Upload cancelled.')

    const message = await messagesApi.completeUpload(currentId)
    remember(key, null)
    return message
  }

  const done = run().then(
    (message) => {
      store.patch(id, { phase: 'done', sent: file.size })
      setTimeout(() => useUploads.getState().remove(id), 1500)
      return message
    },
    (error: unknown) => {
      if (signal.aborted) {
        store.patch(id, { phase: 'cancelled' })
        setTimeout(() => useUploads.getState().remove(id), 500)
        throw new UploadRejected('Upload cancelled.')
      }
      // Keep the remembered id when the failure was temporary, so trying again resumes instead of restarting
      if (error instanceof UploadRejected || !retryable(error)) remember(key, null)
      const message = error instanceof UploadRejected ? error.message : errorMessage(error)
      store.patch(id, { phase: 'failed', error: message })
      throw new UploadRejected(message)
    },
  )

  return {
    id,
    done,
    cancel: () => {
      controller.abort()
      if (uploadId) messagesApi.cancelUpload(uploadId).catch(() => undefined)
      remember(key, null)
    },
  }
}
