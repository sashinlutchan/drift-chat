import { useQuery } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { errorMessage, problemOf } from '@/api/client'
import { roomsApi } from '@/api/rooms'
import { Button } from '@/components/ui/button'
import { JoinRoomDialog } from '@/features/lobby/JoinRoomDialog'
export function ShareLinkPage() {
  const { slug = '' } = useParams()
  return <ShareLinkScreen key={slug} slug={slug} />
}

function ShareLinkScreen({ slug }: { slug: string }) {
  const navigate = useNavigate()
  const room = useQuery({
    queryKey: ['room-by-slug', slug],
    queryFn: () => roomsApi.bySlug(slug),
    retry: false,
  })
  const [dialog, setDialog] = useState(true)
  if (room.isPending) {
    return (
      <div className="grid h-full place-items-center" role="status" aria-label="Opening link">
        <Loader2 className="size-6 animate-spin text-primary" />
      </div>
    )
  }
  if (room.isError) {
    const gone = problemOf(room.error).status === 404
    return (
      <div className="grid h-full place-items-center px-4 text-center">
        <div className="max-w-sm">
          <h1 className="text-xl font-semibold">
            {gone ? 'This room no longer exists' : 'Couldn’t open this link'}
          </h1>
          <p className="mt-2 text-muted-foreground">
            {gone
              ? 'Rooms are deleted when nobody has been inside for a while.'
              : errorMessage(room.error)}
          </p>
          <Button asChild className="mt-5">
            <Link to="/">Back to rooms</Link>
          </Button>
        </div>
      </div>
    )
  }
  if (room.data.joined) return <Navigate to={`/rooms/${room.data.id}`} replace />
  return (
    <div className="grid h-full place-items-center px-4 text-center">
      <div className="max-w-sm">
        <h1 className="text-xl font-semibold">{room.data.name}</h1>
        <p className="mt-2 text-muted-foreground">{room.data.memberCount} members</p>
      </div>
      <JoinRoomDialog
        room={dialog ? room.data : null}
        onOpenChange={(open) => {
          setDialog(open)
          if (!open) void navigate('/')
        }}
        onJoined={(joined) => void navigate(`/rooms/${joined.id}`, { replace: true })}
      />
    </div>
  )
}
