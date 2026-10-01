import { messagesApi } from '@/api/messages'
import { errorMessage, problemOf } from '@/api/client'
import type { ChatMessage, UploadStatus } from '@/api/types'
import { useUploads } from '@/stores/uploads'
import { config, MEDIA_TYPES } from './config'
import { formatBytes } from './format'
const CONCURRENCY = 3
const MAX_ATTEMPTS = 5

export interface UploadHandle {
  id: string
  done: Promise<ChatMessage>
  cancel: () => void
}

export class UploadRejected extends Error {}

function baseType(type: string): string {
  return type.split(';')[0].trim().toLowerCase()
}

export function checkFile(file: File): string | null {
  if (file.size === 0) return 'That file is empty.'
  if (file.size > config.maxFileSize)
    return `Files can be at most ${formatBytes(config.maxFileSize)}.`
  if (!MEDIA_TYPES.includes(baseType(file.type)))
    return 'Only images (JPEG, PNG, GIF, WebP, AVIF, BMP), audio (MP3, OGG, WAV, WebM, M4A, AAC, FLAC) and video (MP4, WebM, OGG, MOV) can be sent.'
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
  } catch {}
}

const sleep = (ms: number, signal: AbortSignal) =>
  new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms)
    signal.addEventListener('abort', () => (clearTimeout(timer), resolve()), { once: true })
  })

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
        mimeType: baseType(file.type),
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
