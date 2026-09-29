import { ArrowLeft, Info, LogOut, Share2, Users } from 'lucide-react'
import { Link } from 'react-router'
import type { Room } from '@/api/types'
import { Button } from '@/components/ui/button'

interface Props {
  room: Room
  onShare: () => void
  onInfo: () => void
  onMembers: () => void
  onLeave: () => void
}

/** Room name and counts, with Share (always visible), Info, Members and Leave (spec section 7.1). */
export function RoomTopBar({ room, onShare, onInfo, onMembers, onLeave }: Props) {
  return (
    <div className="flex items-center gap-2 border-b px-3 py-2.5">
      <Button asChild variant="ghost" size="icon-sm" aria-label="Back to rooms">
        <Link to="/">
          <ArrowLeft />
        </Link>
      </Button>
      <div className="min-w-0 flex-1">
        <h1 className="truncate leading-tight font-semibold">{room.name}</h1>
        <p className="text-xs text-muted-foreground">
          {room.memberCount} {room.memberCount === 1 ? 'member' : 'members'} ·{' '}
          <span className="text-online">{room.onlineCount} online</span>
        </p>
      </div>
      <Button variant="outline" size="sm" onClick={onShare}>
        <Share2 /> <span className="hidden sm:inline">Share</span>
        <span className="sr-only sm:hidden">Share</span>
      </Button>
      <Button variant="ghost" size="icon-sm" aria-label="Room info" onClick={onInfo}>
        <Info />
      </Button>
      <Button variant="ghost" size="icon-sm" aria-label="Members" onClick={onMembers}>
        <Users />
      </Button>
      <Button variant="ghost" size="icon-sm" aria-label="Leave room" onClick={onLeave}>
        <LogOut />
      </Button>
    </div>
  )
}
