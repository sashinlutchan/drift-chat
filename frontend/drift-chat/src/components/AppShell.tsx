import { LogOut, MessagesSquare, User as UserIcon } from 'lucide-react'
import { useEffect } from 'react'
import { Link, Outlet } from 'react-router'
import { ConnectionBanner } from '@/components/ConnectionBanner'
import { SocketBridge } from '@/components/SocketBridge'
import { ThemeToggle } from '@/components/ThemeToggle'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { timeUntil } from '@/lib/format'
import { useAuth } from '@/stores/auth'
export function AppShell() {
  const user = useAuth((s) => s.user)
  const endSession = useAuth((s) => s.endSession)
  useEffect(() => {
    if (!user) return
    const wait = new Date(user.sessionExpiresAt).getTime() - Date.now()
    const timer = setTimeout(endSession, Math.max(0, Math.min(wait, 2 ** 31 - 1)))
    return () => clearTimeout(timer)
  }, [user, endSession])
  return (
    <div className="flex h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:m-2 focus:rounded-md focus:bg-primary focus:px-3 focus:py-2 focus:text-primary-foreground"
      >
        Skip to content
      </a>
      <header className="flex h-14 shrink-0 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur">
        <Link to="/" className="flex items-center gap-2 font-semibold tracking-tight">
          <span className="grid size-8 place-items-center rounded-lg bg-primary text-primary-foreground">
            <MessagesSquare className="size-4" />
          </span>
          Drift
        </Link>
        <div className="ml-auto flex items-center gap-1">
          <ThemeToggle />
          {user && (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="rounded-full"
                  aria-label="Open profile menu"
                >
                  <UserAvatar
                    name={user.name}
                    avatarFileId={user.avatarFileId}
                    className="size-8"
                  />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-60">
                <DropdownMenuLabel className="flex flex-col gap-0.5">
                  <span className="text-sm font-medium text-foreground">{user.name}</span>
                  <span>@{user.username}</span>
                  <span className="mt-1">
                    Session expires in {timeUntil(user.sessionExpiresAt)}
                  </span>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem asChild>
                  <Link to="/profile">
                    <UserIcon /> Profile
                  </Link>
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={endSession}>
                  <LogOut /> Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          )}
        </div>
      </header>
      <ConnectionBanner />
      <main id="main" className="min-h-0 flex-1">
        <Outlet />
      </main>
      <SocketBridge />
    </div>
  )
}
