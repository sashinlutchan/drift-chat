import { create } from 'zustand'

/** Live online/offline flags pushed over the socket (`presence:update`), layered over what the API returned. */
interface PresenceState {
  online: Record<string, boolean>
  set: (userId: string, online: boolean) => void
}

export const usePresence = create<PresenceState>((set) => ({
  online: {},
  set: (userId, online) => set((s) => ({ online: { ...s.online, [userId]: online } })),
}))

/** The live flag if we have one, otherwise the one that came with the data. */
export function useIsOnline(userId: string | null | undefined, fallback: boolean): boolean {
  const live = usePresence((s) => (userId ? s.online[userId] : undefined))
  return live ?? fallback
}
