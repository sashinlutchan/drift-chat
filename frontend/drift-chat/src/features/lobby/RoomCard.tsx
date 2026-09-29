import { Check, Pin, PinOff, Share2, Users } from 'lucide-react'
import type { Room } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardTitle } from '@/components/ui/card'

interface Props {
  room: Room
  onOpen: (room: Room) => void
  onJoin: (room: Room) => void
  onTogglePin: (room: Room) => void
  onShare: (room: Room) => void
}

/** One room in the lobby: name, counts, badges and the Join / Open / Rejoin, Pin and Share actions (spec 5.1–5.2). */
export function RoomCard({ room, onOpen, onJoin, onTogglePin, onShare }: Props) {
  const action = room.joined ? 'Open' : room.left ? 'Rejoin' : 'Join'
  return (
    <Card className="gap-3 transition-shadow hover:shadow-md" data-testid="room-card">
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <CardTitle className="flex items-center gap-2 text-base">
            <span className="truncate">{room.name}</span>
            {room.pinned && (
              <Pin className="size-3.5 shrink-0 fill-primary text-primary" aria-label="Pinned" />
            )}
          </CardTitle>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Users className="size-3.5" /> {room.memberCount}{' '}
              {room.memberCount === 1 ? 'member' : 'members'}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span className="size-2 rounded-full bg-online" aria-hidden="true" />{' '}
              {room.onlineCount} online
            </span>
          </div>
        </div>
        {room.joined && (
          <Badge variant="success">
            <Check className="size-3" /> Joined
          </Badge>
        )}
      </div>
      <div className="flex items-center gap-2">
        <Button
          className="flex-1"
          size="sm"
          variant={room.joined ? 'default' : 'outline'}
          onClick={() => (room.joined ? onOpen(room) : onJoin(room))}
        >
          {action}
        </Button>
        <Button
          size="icon-sm"
          variant="ghost"
          aria-label={room.pinned ? `Unpin ${room.name}` : `Pin ${room.name}`}
          onClick={() => onTogglePin(room)}
        >
          {room.pinned ? <PinOff /> : <Pin />}
        </Button>
        <Button
          size="icon-sm"
          variant="ghost"
          aria-label={`Share ${room.name}`}
          onClick={() => onShare(room)}
        >
          <Share2 />
        </Button>
      </div>
    </Card>
  )
}
