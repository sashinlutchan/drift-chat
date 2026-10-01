import { api } from './client'
import type { ChatMessage, MessagePage, UploadStarted, UploadStatus } from './types'
export const messagesApi = {
  history: (roomId: string, before?: string | null, limit?: number) =>
    api
      .get<MessagePage>('/messages', { params: { roomId, before: before ?? undefined, limit } })
      .then((r) => r.data),
  startUpload: (input: { roomId: string; fileName: string; size: number; mimeType: string }) =>
    api.post<UploadStarted>('/messages/uploads', input).then((r) => r.data),
  uploadChunk: (
    uploadId: string,
    index: number,
    chunk: Blob,
    options: {
      signal?: AbortSignal
      onProgress?: (loaded: number) => void
    } = {},
  ) =>
    api
      .put(`/messages/uploads/${uploadId}/chunks/${index}`, chunk, {
        headers: { 'Content-Type': 'application/octet-stream' },
        signal: options.signal,
        onUploadProgress: (event) => options.onProgress?.(event.loaded),
        timeout: 0,
        transformRequest: [(data: unknown) => data],
      })
      .then(() => undefined),
  uploadStatus: (uploadId: string) =>
    api.get<UploadStatus>(`/messages/uploads/${uploadId}`).then((r) => r.data),
  completeUpload: (uploadId: string) =>
    api.post<ChatMessage>(`/messages/uploads/${uploadId}/complete`).then((r) => r.data),
  cancelUpload: (uploadId: string) =>
    api.delete(`/messages/uploads/${uploadId}`).then(() => undefined),
}
