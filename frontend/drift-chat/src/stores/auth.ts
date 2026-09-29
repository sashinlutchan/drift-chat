import { create } from 'zustand'
import type { OwnUser } from '@/api/types'

/**
 * Who is signed in. Never holds a token: the session is an HttpOnly cookie JavaScript cannot read. The expiry time
 * comes from `GET /users/me` so the UI can show "session expires in X hours".
 */
interface AuthState {
  status: 'loading' | 'signed-in' | 'signed-out'
  user: OwnUser | null
  /** Set when a session that existed has ended, so the sign-up screen can say so. */
  sessionEnded: boolean
  signIn: (user: OwnUser) => void
  setUser: (user: OwnUser) => void
  signedOut: () => void
  endSession: () => void
}

export const useAuth = create<AuthState>((set, get) => ({
  status: 'loading',
  user: null,
  sessionEnded: false,
  signIn: (user) => set({ status: 'signed-in', user, sessionEnded: false }),
  setUser: (user) => set({ user }),
  signedOut: () => set({ status: 'signed-out', user: null }),
  endSession: () => {
    if (get().status === 'signed-in') set({ status: 'signed-out', user: null, sessionEnded: true })
  },
}))
