import { Copy } from 'lucide-react'
import type { RoomInfo } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { formatDate, timeUntil } from '@/lib/format'
import { copyLink } from '@/lib/share'

interface Props {
  info: RoomInfo
  open: boolean
  onOpenChange: (open: boolean) => void
}

/** Name, creator, created date, member count, share link and the room's deletion countdown (spec section 7.7). */
export function RoomInfoPanel({ info, open, onOpenChange }: Props) {
  const { room } = info
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent>
        <SheetHeader>
          <SheetTitle>Room info</SheetTitle>
          <SheetDescription>{room.name}</SheetDescription>
        </SheetHeader>
        <dl className="grid gap-4 text-sm">
          <Item label="Name">{room.name}</Item>
          <Item label="Created by">{info.createdBy ? info.createdBy.name : 'Deleted user'}</Item>
          <Item label="Created">{formatDate(info.createdAt)}</Item>
          <Item label="Members">
            {room.memberCount} · {room.onlineCount} online
          </Item>
          <Item label="Share link">
            <div className="flex gap-2">
              <Input
                readOnly
                value={room.shareUrl}
                aria-label="Room link"
                onFocus={(e) => e.currentTarget.select()}
              />
              <Button
                variant="outline"
                size="icon"
                aria-label="Copy link"
                onClick={() => void copyLink(room.shareUrl)}
              >
                <Copy />
              </Button>
            </div>
          </Item>
          {info.idleDeleteAt && (
            <Item label="Auto-deletion">
              <p className="rounded-lg bg-amber-500/10 px-3 py-2 text-amber-700 dark:text-amber-300">
                Nobody is inside, so this room will be deleted in {timeUntil(info.idleDeleteAt)}{' '}
                unless someone opens it.
              </p>
            </Item>
          )}
        </dl>
      </SheetContent>
    </Sheet>
  )
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-1">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}
