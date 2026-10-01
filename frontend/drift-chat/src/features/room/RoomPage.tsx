import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { errorMessage, problemOf } from '@/api/client'
import { roomsApi } from '@/api/rooms'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ShareDialog } from '@/components/ShareDialog'
import { Button } from '@/components/ui/button'
import { useChat } from '@/hooks/useChat'
import { JoinRoomDialog } from '@/features/lobby/JoinRoomDialog'
import { useAuth } from '@/stores/auth'
import { Composer } from './Composer'
import { ImageViewer } from './ImageViewer'
import { MembersPanel } from './MembersPanel'
import { MessageList } from './MessageList'
import { RoomInfoPanel } from './RoomInfoPanel'
import { RoomTopBar } from './RoomTopBar'
function RoomMessage({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="grid h-full place-items-center px-4 text-center">
      <div className="max-w-sm">
        <h1 className="text-xl font-semibold">{title}</h1>
        <div className="mt-2 text-muted-foreground">{children}</div>
        <Button asChild className="mt-5">
          <Link to="/">Back to rooms</Link>
        </Button>
      </div>
    </div>
  )
}

export function RoomPage() {
  const { id = '' } = useParams()
  const [attempt, setAttempt] = useState(0)
  return <RoomScreen key={`${id}:${attempt}`} id={id} onJoined={() => setAttempt((n) => n + 1)} />
}

function RoomScreen({ id, onJoined }: { id: string; onJoined: () => void }) {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const me = useAuth((s) => s.user)
  const chat = useChat(id)
  const info = useQuery({
    queryKey: ['room-info', id],
    queryFn: () => roomsApi.info(id),
    retry: false,
  })
  const [share, setShare] = useState(
    (
      location.state as {
        share?: boolean
      } | null
    )?.share === true,
  )
  const [showInfo, setShowInfo] = useState(false)
  const [showMembers, setShowMembers] = useState(false)
  const [leaving, setLeaving] = useState(false)
  const [viewing, setViewing] = useState<{
    src: string
    name: string
  } | null>(null)
  const leave = useMutation({
    mutationFn: () => roomsApi.leave(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['rooms'] })
      toast.success('You left the room')
      void navigate('/', { replace: true })
    },
    onError: (error) =>
      toast.error('Could not leave the room', { description: errorMessage(error) }),
  })
  if (info.isPending || chat.state === 'joining') {
    if (info.isError)
      return (
        <RoomMessage title="This room no longer exists">
          It may have been deleted because nobody was inside for a while.
        </RoomMessage>
      )
    return (
      <div className="grid h-full place-items-center" role="status" aria-label="Opening room">
        <Loader2 className="size-6 animate-spin text-primary" />
      </div>
    )
  }
  if (info.isError && problemOf(info.error).status === 404) {
    return (
      <RoomMessage title="This room no longer exists">
        It may have been deleted because nobody was inside for a while.
      </RoomMessage>
    )
  }
  if (info.isError)
    return <RoomMessage title="Couldn’t open this room">{errorMessage(info.error)}</RoomMessage>
  if (chat.state === 'gone')
    return (
      <RoomMessage title="This room no longer exists">
        It may have been deleted because nobody was inside for a while.
      </RoomMessage>
    )
  if (chat.state === 'not-member') {
    return (
      <>
        <RoomMessage title={`Join ${info.data.room.name}?`}>
          You need to be a member to read and send messages.
        </RoomMessage>
        <JoinRoomDialog
          room={info.data.room}
          onOpenChange={(open) => !open && void navigate('/')}
          onJoined={onJoined}
        />
      </>
    )
  }
  const { room } = info.data
  return (
    <div className="flex h-full flex-col">
      <RoomTopBar
        room={room}
        onShare={() => setShare(true)}
        onInfo={() => setShowInfo(true)}
        onMembers={() => setShowMembers(true)}
        onLeave={() => setLeaving(true)}
      />
      {chat.state === 'error' && (
        <p role="alert" className="bg-destructive/10 px-4 py-2 text-sm text-destructive">
          {chat.error ?? 'Could not load this room.'}
        </p>
      )}
      <MessageList
        messages={chat.messages}
        meId={me?.id}
        hasOlder={chat.hasOlder}
        loadingOlder={chat.loadingOlder}
        onLoadOlder={() => void chat.loadOlder()}
        onImage={(src, name) => setViewing({ src, name })}
        onRetry={chat.retry}
      />
      <Composer
        roomId={id}
        typing={chat.typing}
        disabled={chat.state !== 'ready'}
        onSend={chat.send}
        onUploaded={chat.addMessage}
      />

      <ShareDialog open={share} onOpenChange={setShare} roomName={room.name} url={room.shareUrl} />
      <RoomInfoPanel info={info.data} open={showInfo} onOpenChange={setShowInfo} />
      <MembersPanel roomId={id} open={showMembers} onOpenChange={setShowMembers} />
      <ImageViewer
        src={viewing?.src ?? null}
        name={viewing?.name ?? ''}
        onClose={() => setViewing(null)}
      />
      <ConfirmDialog
        open={leaving}
        onOpenChange={setLeaving}
        title={`Leave ${room.name}?`}
        description="You’ll be taken back to the main screen and can rejoin later."
        confirmLabel="Leave room"
        destructive
        busy={leave.isPending}
        onConfirm={() => leave.mutate()}
      />
    </div>
  )
}
