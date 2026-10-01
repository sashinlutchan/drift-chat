import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Camera, Loader2 } from 'lucide-react'
import { useEffect, useMemo, useRef } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import { usersApi } from '@/api/users'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { fileUrl } from '@/lib/config'
import { formatDate, timeUntil } from '@/lib/format'
import { profileSchema, type ProfileValues } from '@/lib/validation'
import { useAuth } from '@/stores/auth'
import { useNavigate } from 'react-router'
export function ProfilePage() {
  const user = useAuth((s) => s.user)
  const setUser = useAuth((s) => s.setUser)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const picker = useRef<HTMLInputElement>(null)
  const form = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    mode: 'onChange',
    defaultValues: { name: user?.name ?? '', email: user?.email ?? '' },
  })
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    control,
    formState: { errors, isDirty },
  } = form
  const avatar = useWatch({ control, name: 'avatar' }) as File | undefined
  const preview = useMemo(
    () => (avatar instanceof File ? URL.createObjectURL(avatar) : undefined),
    [avatar],
  )
  useEffect(() => () => (preview ? URL.revokeObjectURL(preview) : undefined), [preview])
  const save = useMutation({
    mutationFn: (values: ProfileValues) =>
      usersApi.update({ name: values.name, email: values.email, avatar: values.avatar }),
    onSuccess: (updated) => {
      setUser(updated)
      reset({ name: updated.name, email: updated.email })
      void queryClient.invalidateQueries({ queryKey: ['members'] })
      toast.success('Profile saved')
    },
    onError: (error) =>
      toast.error('Could not save your profile', { description: errorMessage(error) }),
  })
  if (!user) return null
  return (
    <div className="mx-auto h-full w-full max-w-xl overflow-y-auto px-4 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">Your profile</h1>
      <form
        onSubmit={handleSubmit((v) => save.mutate(v))}
        noValidate
        className="mt-6 grid gap-6 rounded-2xl border bg-card p-6"
      >
        <div className="flex items-center gap-4">
          <button
            type="button"
            onClick={() => picker.current?.click()}
            className="group relative rounded-full focus-visible:ring-[3px] focus-visible:ring-ring"
            aria-label="Change avatar"
          >
            <Avatar className="size-20">
              <AvatarImage src={preview ?? fileUrl(user.avatarFileId)} alt="Your avatar" />
              <AvatarFallback>{user.name.slice(0, 2)}</AvatarFallback>
            </Avatar>
            <span className="absolute inset-0 grid place-items-center rounded-full bg-black/45 text-white opacity-0 transition-opacity group-hover:opacity-100">
              <Camera className="size-5" />
            </span>
          </button>
          <input
            ref={picker}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="sr-only"
            aria-label="Avatar image"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) setValue('avatar', file, { shouldValidate: true, shouldDirty: true })
            }}
          />
          <div>
            <p className="text-lg font-semibold">@{user.username}</p>
            <p className="flex items-center gap-1.5 text-sm text-muted-foreground">
              <span className="size-2 rounded-full bg-online" /> Online
            </p>
            {errors.avatar && (
              <p role="alert" className="text-sm text-destructive">
                {errors.avatar.message as string}
              </p>
            )}
          </div>
        </div>

        <div className="grid gap-1.5">
          <Label htmlFor="username">Username</Label>
          <Input
            id="username"
            value={user.username}
            readOnly
            disabled
            aria-describedby="username-hint"
          />
          <p id="username-hint" className="text-xs text-muted-foreground">
            Usernames can’t be changed.
          </p>
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="name">Name</Label>
          <Input id="name" autoComplete="name" aria-invalid={!!errors.name} {...register('name')} />
          {errors.name && (
            <p role="alert" className="text-sm text-destructive">
              {errors.name.message}
            </p>
          )}
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="email">Email</Label>
          <Input
            id="email"
            type="email"
            autoComplete="email"
            aria-invalid={!!errors.email}
            {...register('email')}
          />
          {errors.email && (
            <p role="alert" className="text-sm text-destructive">
              {errors.email.message}
            </p>
          )}
        </div>

        <p className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
          Session expires in{' '}
          <strong className="text-foreground">{timeUntil(user.sessionExpiresAt)}</strong> (
          {formatDate(user.sessionExpiresAt)}). Your account is removed when it ends.
        </p>

        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              reset({ name: user.name, email: user.email })
              void navigate('/')
            }}
          >
            Cancel
          </Button>
          <Button type="submit" disabled={!isDirty || save.isPending}>
            {save.isPending && <Loader2 className="animate-spin" />} Save
          </Button>
        </div>
      </form>
    </div>
  )
}
