import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link2, Plus, Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { roomsApi } from '@/api/rooms'
import type { Room } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { copyLink } from '@/lib/share'
import { CreateRoomDialog } from './CreateRoomDialog'
import { JoinByLinkDialog } from './JoinByLinkDialog'
import { JoinRoomDialog } from './JoinRoomDialog'
import { RoomCard } from './RoomCard'

function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return debounced
}

/** The main screen: every room, pinned first, live-updating (spec section 5). */
export function LobbyPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const debounced = useDebounced(search, 250)
  const [joining, setJoining] = useState<Room | null>(null)
  const [creating, setCreating] = useState(false)
  const [byLink, setByLink] = useState(false)

  const rooms = useQuery({
    queryKey: ['rooms', debounced],
    queryFn: () => roomsApi.list(debounced),
    placeholderData: (previous) => previous,
  })

  const togglePin = useMutation({
    mutationFn: (room: Room) => (room.pinned ? roomsApi.unpin(room.id) : roomsApi.pin(room.id)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['rooms'] }),
    onError: (error) =>
      toast.error('Could not update the pin', { description: errorMessage(error) }),
  })

  const openRoom = (room: Room) => void navigate(`/rooms/${room.id}`)

  const openBySlug = async (slug: string) => {
    try {
      const room = await roomsApi.bySlug(slug)
      if (room.joined) openRoom(room)
      else setJoining(room)
    } catch {
      toast.error('This room no longer exists')
    }
  }

  return (
    <div className="mx-auto flex h-full w-full max-w-5xl flex-col gap-6 overflow-y-auto px-4 py-8">
      <div className="flex flex-wrap items-center gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Rooms</h1>
          <p className="text-sm text-muted-foreground">Join a room or start your own.</p>
        </div>
        <div className="ml-auto flex gap-2">
          <Button variant="outline" onClick={() => setByLink(true)}>
            <Link2 /> Join by link
          </Button>
          <Button onClick={() => setCreating(true)}>
            <Plus /> Create room
          </Button>
        </div>
      </div>

      <div className="relative">
        <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          className="pl-9"
          placeholder="Search rooms"
          aria-label="Search rooms"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {rooms.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-36" />
          ))}
        </div>
      ) : rooms.isError ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-6 text-center">
          <p className="font-medium">Couldn’t load rooms</p>
          <p className="mb-3 text-sm text-muted-foreground">{errorMessage(rooms.error)}</p>
          <Button variant="outline" onClick={() => void rooms.refetch()}>
            Try again
          </Button>
        </div>
      ) : rooms.data.length === 0 ? (
        <div className="grid place-items-center rounded-2xl border border-dashed py-20 text-center">
          <p className="text-lg font-medium">
            {debounced ? 'No rooms match your search' : 'No rooms yet — create the first one.'}
          </p>
          {!debounced && (
            <Button className="mt-4" onClick={() => setCreating(true)}>
              <Plus /> Create room
            </Button>
          )}
        </div>
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {rooms.data.map((room) => (
            <li key={room.id}>
              <RoomCard
                room={room}
                onOpen={openRoom}
                onJoin={setJoining}
                onTogglePin={(r) => togglePin.mutate(r)}
                onShare={(r) => void copyLink(r.shareUrl)}
              />
            </li>
          ))}
        </ul>
      )}

      <JoinRoomDialog
        room={joining}
        onOpenChange={(open) => !open && setJoining(null)}
        onJoined={openRoom}
      />
      <CreateRoomDialog
        open={creating}
        onOpenChange={setCreating}
        // the room shows the share-link dialog when it opens (spec section 5.3)
        onCreated={(room) => void navigate(`/rooms/${room.id}`, { state: { share: true } })}
      />
      <JoinByLinkDialog
        open={byLink}
        onOpenChange={setByLink}
        onSlug={(slug) => void openBySlug(slug)}
      />
    </div>
  )
}
