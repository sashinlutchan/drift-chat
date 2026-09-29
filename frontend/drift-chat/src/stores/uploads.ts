import { create } from 'zustand'

export type UploadPhase = 'uploading' | 'failed' | 'done' | 'cancelled'

export interface UploadItem {
  id: string
  roomId: string
  fileName: string
  size: number
  sent: number
  phase: UploadPhase
  error?: string
}

interface UploadsState {
  items: Record<string, UploadItem>
  upsert: (item: UploadItem) => void
  patch: (id: string, patch: Partial<UploadItem>) => void
  remove: (id: string) => void
}

/** What the upload progress bars show. The uploads themselves run in `lib/uploader.ts`, outside React. */
export const useUploads = create<UploadsState>((set) => ({
  items: {},
  upsert: (item) => set((s) => ({ items: { ...s.items, [item.id]: item } })),
  patch: (id, patch) =>
    set((s) => (s.items[id] ? { items: { ...s.items, [id]: { ...s.items[id], ...patch } } } : s)),
  remove: (id) =>
    set((s) => {
      const items = { ...s.items }
      delete items[id]
      return { items }
    }),
}))
