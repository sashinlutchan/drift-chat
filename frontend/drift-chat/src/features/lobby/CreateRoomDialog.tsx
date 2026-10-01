import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Loader2 } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { roomsApi } from '@/api/rooms'
import type { Room } from '@/api/types'
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
import { createRoomSchema, type CreateRoomValues } from '@/lib/validation'
interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onCreated: (room: Room) => void
}

export function CreateRoomDialog({ open, onOpenChange, onCreated }: Props) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<CreateRoomValues>({
    resolver: zodResolver(createRoomSchema),
    defaultValues: { name: '' },
  })
  const create = useMutation({
    mutationFn: (values: CreateRoomValues) => roomsApi.create(values.name),
    onSuccess: (room) => {
      void queryClient.invalidateQueries({ queryKey: ['rooms'] })
      toast.success('Room created')
      reset()
      onOpenChange(false)
      onCreated(room)
    },
    onError: (error) =>
      toast.error('Could not create the room', { description: errorMessage(error) }),
  })
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit((v) => create.mutate(v))} noValidate className="grid gap-4">
          <DialogHeader>
            <DialogTitle>Create a room</DialogTitle>
            <DialogDescription>
              Give it a name. You can share the link once it’s ready.
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-1.5">
            <Label htmlFor="room-name">Room name</Label>
            <Input
              id="room-name"
              autoFocus
              maxLength={50}
              aria-invalid={!!errors.name}
              {...register('name')}
            />
            {errors.name && (
              <p role="alert" className="text-sm text-destructive">
                {errors.name.message}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending && <Loader2 className="animate-spin" />} Create room
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
