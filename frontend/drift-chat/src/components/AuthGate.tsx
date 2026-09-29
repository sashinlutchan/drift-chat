import { Loader2 } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { ensureCsrfCookie, problemOf, setSessionEndedListener } from '@/api/client'
import { usersApi } from '@/api/users'
import { useAuth } from '@/stores/auth'

/** Runs once at start-up: fetch the CSRF cookie, then ask `GET /users/me` whether a session already exists. */
export function AuthBootstrap({ children }: { children: ReactNode }) {
  useEffect(() => {
    setSessionEndedListener(() => useAuth.getState().endSession())
    let cancelled = false
    void (async () => {
      await ensureCsrfCookie().catch(() => undefined)
      try {
        const user = await usersApi.me()
        if (!cancelled) useAuth.getState().signIn(user)
      } catch (error) {
        if (cancelled) return
        // A 401 is the normal "no session yet"; anything else (server down) also lands on sign-up, which can retry
        void problemOf(error)
        useAuth.getState().signedOut()
      }
    })()
    return () => {
      cancelled = true
      setSessionEndedListener(null)
    }
  }, [])
  return children
}

export function Splash() {
  return (
    <div className="grid h-dvh place-items-center" role="status" aria-label="Loading">
      <Loader2 className="size-6 animate-spin text-primary" />
    </div>
  )
}

/** Keeps signed-out visitors away from the app and remembers where they were going. */
export function RequireAuth() {
  const status = useAuth((s) => s.status)
  const location = useLocation()
  if (status === 'loading') return <Splash />
  if (status === 'signed-out') {
    const next = location.pathname + location.search
    return <Navigate to={`/signup?next=${encodeURIComponent(next)}`} replace />
  }
  return <Outlet />
}
