import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { roomsApi } from '@/api/rooms'
import type { Room } from '@/api/types'
import { ConfirmDialog } from '@/components/ConfirmDialog'

interface Props {
  room: Room | null
  onOpenChange: (open: boolean) => void
  onJoined: (room: Room) => void
}

/** "Do you want to join {room}?" with Yes / Cancel (spec section 5.2). Also used for Rejoin and for share links. */
export function JoinRoomDialog({ room, onOpenChange, onJoined }: Props) {
  const queryClient = useQueryClient()
  const join = useMutation({
    mutationFn: (id: string) => roomsApi.join(id),
    onSuccess: (joined) => {
      void queryClient.invalidateQueries({ queryKey: ['rooms'] })
      onOpenChange(false)
      onJoined(joined)
    },
    onError: (error) =>
      toast.error('Could not join the room', { description: errorMessage(error) }),
  })

  return (
    <ConfirmDialog
      open={room !== null}
      onOpenChange={(open) => !join.isPending && onOpenChange(open)}
      title={
        <>
          Do you want to join <span className="text-primary">{room?.name}</span>?
        </>
      }
      description="You’ll become a member and can chat with everyone inside."
      confirmLabel="Yes, join"
      busy={join.isPending}
      onConfirm={() => room && join.mutate(room.id)}
    />
  )
}
