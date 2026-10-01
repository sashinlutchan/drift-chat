import { useState } from 'react'
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
import { Label } from '@/components/ui/label'
import { parseRoomSlug } from '@/lib/validation'
interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSlug: (slug: string) => void
}

export function JoinByLinkDialog({ open, onOpenChange, onSlug }: Props) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const slug = parseRoomSlug(value)
    if (!slug) return setError('That doesn’t look like a room link.')
    setValue('')
    setError(null)
    onOpenChange(false)
    onSlug(slug)
  }
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={submit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>Join by link</DialogTitle>
            <DialogDescription>Paste the link someone shared with you.</DialogDescription>
          </DialogHeader>
          <div className="grid gap-1.5">
            <Label htmlFor="room-link">Room link</Label>
            <Input
              id="room-link"
              autoFocus
              placeholder="https://…/r/movie-night-a1b2c3"
              value={value}
              aria-invalid={!!error}
              onChange={(e) => {
                setValue(e.target.value)
                setError(null)
              }}
            />
            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit">Continue</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
