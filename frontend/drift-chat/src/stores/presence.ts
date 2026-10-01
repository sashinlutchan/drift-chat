import { create } from 'zustand'
interface PresenceState {
  online: Record<string, boolean>
  set: (userId: string, online: boolean) => void
}

export const usePresence = create<PresenceState>((set) => ({
  online: {},
  set: (userId, online) => set((s) => ({ online: { ...s.online, [userId]: online } })),
}))

export function useIsOnline(userId: string | null | undefined, fallback: boolean): boolean {
  const live = usePresence((s) => (userId ? s.online[userId] : undefined))
  return live ?? fallback
}
