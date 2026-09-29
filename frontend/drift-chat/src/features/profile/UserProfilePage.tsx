import { useQuery } from '@tanstack/react-query'
import { Link, Navigate, useParams } from 'react-router'
import { problemOf } from '@/api/client'
import { usersApi } from '@/api/users'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/stores/auth'
import { useIsOnline } from '@/stores/presence'

/** Somebody else's profile, read-only: avatar, username, name and status — never their email (spec section 3.5). */
export function UserProfilePage() {
  const { id = '' } = useParams()
  const me = useAuth((s) => s.user)
  const user = useQuery({ queryKey: ['user', id], queryFn: () => usersApi.get(id), retry: false })
  const online = useIsOnline(id, user.data?.online ?? false)

  if (me?.id === id) return <Navigate to="/profile" replace />

  return (
    <div className="mx-auto grid h-full w-full max-w-md content-start px-4 py-10">
      {user.isPending && <Skeleton className="h-64" />}
      {user.isError && (
        <div className="text-center">
          <h1 className="text-xl font-semibold">
            {problemOf(user.error).status === 404 ? 'User not found' : 'Couldn’t load this profile'}
          </h1>
          <p className="mt-2 text-muted-foreground">
            Their session may have ended, which removes the account.
          </p>
          <Button asChild className="mt-5">
            <Link to="/">Back to rooms</Link>
          </Button>
        </div>
      )}
      {user.data && (
        <div className="flex flex-col items-center gap-3 rounded-2xl border bg-card p-8 text-center">
          <UserAvatar
            name={user.data.name}
            avatarFileId={user.data.avatarFileId}
            userId={id}
            online={user.data.online}
            showStatus
            className="size-24"
          />
          <div>
            <h1 className="text-xl font-semibold">{user.data.name}</h1>
            <p className="text-muted-foreground">@{user.data.username}</p>
          </div>
          <p className="flex items-center gap-2 text-sm">
            <span
              className={
                online
                  ? 'size-2 rounded-full bg-online'
                  : 'size-2 rounded-full bg-muted-foreground/50'
              }
            />
            {online ? 'Online' : 'Offline'}
          </p>
        </div>
      )}
    </div>
  )
}
