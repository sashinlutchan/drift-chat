import { Copy, Share2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { copyLink, shareLink } from '@/lib/share'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  roomName: string
  url: string
}

/** Shown after creating a room, and from the top bar's Share button. */
export function ShareDialog({ open, onOpenChange, roomName, url }: Props) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Share “{roomName}”</DialogTitle>
          <DialogDescription>
            Anyone with this link can join, once they have signed up.
          </DialogDescription>
        </DialogHeader>
        <div className="flex gap-2">
          <Input
            readOnly
            value={url}
            aria-label="Room link"
            onFocus={(e) => e.currentTarget.select()}
          />
          <Button
            variant="outline"
            size="icon"
            aria-label="Copy link"
            onClick={() => void copyLink(url)}
          >
            <Copy />
          </Button>
        </div>
        <DialogFooter>
          <Button onClick={() => void shareLink(url, roomName)}>
            <Share2 /> Share
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
