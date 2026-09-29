import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { errorMessage } from '@/api/client'
import { roomsApi } from '@/api/rooms'
import { UserAvatar } from '@/components/UserAvatar'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { useIsOnline } from '@/stores/presence'
import type { Member } from '@/api/types'

interface Props {
  roomId: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Everyone in the room, online first, live as people come and go (spec section 7.6). */
export function MembersPanel({ roomId, open, onOpenChange }: Props) {
  const members = useQuery({
    queryKey: ['members', roomId],
    queryFn: () => roomsApi.members(roomId),
    enabled: open,
  })
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Members</SheetTitle>
          <SheetDescription>
            {members.data ? `${members.data.length} in this room` : 'Loading…'}
          </SheetDescription>
        </SheetHeader>
        <div className="scrollbar-thin -mx-2 flex-1 overflow-y-auto px-2">
          {members.isPending && (
            <div className="grid gap-2">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="h-12" />
              ))}
            </div>
          )}
          {members.isError && (
            <p className="text-sm text-destructive">{errorMessage(members.error)}</p>
          )}
          <ul className="grid gap-1">
            {members.data?.map((m) => (
              <MemberRow key={m.userId} member={m} onNavigate={() => onOpenChange(false)} />
            ))}
          </ul>
        </div>
      </SheetContent>
    </Sheet>
  )
}

function MemberRow({ member, onNavigate }: { member: Member; onNavigate: () => void }) {
  const online = useIsOnline(member.userId, member.online)
  return (
    <li>
      <Link
        to={`/users/${member.userId}`}
        onClick={onNavigate}
        className="flex items-center gap-3 rounded-lg p-2 transition-colors hover:bg-accent"
      >
        <UserAvatar
          name={member.name}
          avatarFileId={member.avatarFileId}
          userId={member.userId}
          online={member.online}
          showStatus
        />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium">{member.name}</span>
          <span className="block text-xs text-muted-foreground">
            {online ? 'Online' : 'Offline'}
          </span>
        </span>
      </Link>
    </li>
  )
}
