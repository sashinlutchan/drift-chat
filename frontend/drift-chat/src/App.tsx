import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import { Toaster } from 'sonner'
import { AppShell } from '@/components/AppShell'
import { AuthBootstrap, RequireAuth } from '@/components/AuthGate'
import { TooltipProvider } from '@/components/ui/tooltip'
import { SignUpPage } from '@/features/auth/SignUpPage'
import { LobbyPage } from '@/features/lobby/LobbyPage'
import { ProfilePage } from '@/features/profile/ProfilePage'
import { UserProfilePage } from '@/features/profile/UserProfilePage'
import { RoomPage } from '@/features/room/RoomPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ShareLinkPage } from '@/pages/ShareLinkPage'
import { useTheme } from '@/stores/theme'

/** Routes: /signup, / (lobby), /rooms/:id, /r/:slug (share link), /profile, /users/:id, not-found (spec section 22). */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/signup" element={<SignUpPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route index element={<LobbyPage />} />
          <Route path="rooms/:id" element={<RoomPage />} />
          <Route path="r/:slug" element={<ShareLinkPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="users/:id" element={<UserProfilePage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

export default function App() {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { staleTime: 15_000, refetchOnWindowFocus: true, retry: 1 } },
      }),
  )
  const theme = useTheme((s) => s.choice)
  return (
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <BrowserRouter>
          <AuthBootstrap>
            <AppRoutes />
          </AuthBootstrap>
        </BrowserRouter>
        <Toaster theme={theme} position="top-center" richColors closeButton />
      </TooltipProvider>
    </QueryClientProvider>
  )
}
