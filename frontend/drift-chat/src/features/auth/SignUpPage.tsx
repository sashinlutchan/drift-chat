import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery } from '@tanstack/react-query'
import { Camera, CheckCircle2, Loader2, MessagesSquare, XCircle } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { problemOf } from '@/api/client'
import { usersApi } from '@/api/users'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { usernameSchema, signUpSchema, type SignUpValues } from '@/lib/validation'
import { useAuth } from '@/stores/auth'
function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

function useDebounced<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return debounced
}

export function SignUpPage() {
  const status = useAuth((s) => s.status)
  const sessionEnded = useAuth((s) => s.sessionEnded)
  const signIn = useAuth((s) => s.signIn)
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const fileInput = useRef<HTMLInputElement>(null)
  const form = useForm<SignUpValues>({
    resolver: zodResolver(signUpSchema),
    mode: 'onChange',
    defaultValues: { username: '', name: '', email: '' },
  })
  const {
    register,
    handleSubmit,
    setValue,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = form
  const username = useWatch({ control, name: 'username' })
  const avatar = useWatch({ control, name: 'avatar' }) as File | undefined
  const preview = useMemo(
    () => (avatar instanceof File ? URL.createObjectURL(avatar) : undefined),
    [avatar],
  )
  useEffect(() => () => (preview ? URL.revokeObjectURL(preview) : undefined), [preview])
  const candidate = useDebounced(username, 350)
  const formatOk = usernameSchema.safeParse(candidate).success
  const availability = useQuery({
    queryKey: ['username-available', candidate],
    queryFn: () => usersApi.checkUsername(candidate),
    enabled: formatOk,
    staleTime: 10000,
    retry: false,
  })
  const taken = formatOk && availability.data === false
  if (status === 'signed-in') return <Navigate to={next} replace />
  const onSubmit = handleSubmit(async (values) => {
    if (taken) return setError('username', { message: 'This username is taken.' })
    try {
      const user = await usersApi.signUp(values)
      signIn(user)
      toast.success(`Welcome to Drift, ${user.name}!`)
      void navigate(next, { replace: true })
    } catch (error) {
      const problem = problemOf(error)
      if (problem.code === 'USERNAME_TAKEN')
        setError('username', { message: 'This username is taken.' })
      else if (problem.code === 'USERNAME_INVALID')
        setError('username', { message: problem.detail })
      else toast.error('Could not sign up', { description: problem.detail })
    }
  })
  return (
    <div className="relative grid min-h-dvh place-items-center bg-[radial-gradient(60%_50%_at_50%_0%,color-mix(in_oklab,var(--primary)_14%,transparent),transparent)] px-4 py-10">
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center gap-3 text-center">
          <span className="grid size-12 place-items-center rounded-2xl bg-primary text-primary-foreground shadow-lg shadow-primary/30">
            <MessagesSquare className="size-6" />
          </span>
          <h1 className="text-3xl font-semibold tracking-tight">Drop in to Drift</h1>
          <p className="text-muted-foreground">
            Pick a username, chat live. Rooms drift away when everyone’s gone.
          </p>
        </div>

        {sessionEnded && (
          <p
            role="alert"
            className="mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm"
          >
            Your session has ended.
          </p>
        )}

        <form
          onSubmit={onSubmit}
          noValidate
          className="flex flex-col gap-5 rounded-2xl border bg-card p-6 shadow-sm"
        >
          <div className="flex flex-col items-center gap-2">
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              className="group relative rounded-full focus-visible:ring-[3px] focus-visible:ring-ring"
              aria-label="Choose an avatar"
            >
              <Avatar className="size-24 border-2 border-dashed border-border">
                <AvatarImage src={preview} alt="Avatar preview" />
                <AvatarFallback className="bg-muted text-muted-foreground">
                  <Camera className="size-7" />
                </AvatarFallback>
              </Avatar>
              <span className="absolute inset-0 grid place-items-center rounded-full bg-black/40 text-xs font-medium text-white opacity-0 transition-opacity group-hover:opacity-100">
                {preview ? 'Change' : 'Add photo'}
              </span>
            </button>
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="sr-only"
              aria-label="Avatar image"
              data-testid="avatar-input"
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) setValue('avatar', file, { shouldValidate: true })
              }}
            />
            {errors.avatar ? (
              <p role="alert" className="text-sm text-destructive">
                {errors.avatar.message as string}
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">JPEG, PNG or WebP, up to 5 MB</p>
            )}
          </div>

          <Field label="Username" error={errors.username?.message} htmlFor="username">
            <div className="relative">
              <Input
                id="username"
                autoComplete="username"
                autoCapitalize="none"
                aria-invalid={!!errors.username || taken}
                {...register('username')}
              />
              <span className="absolute inset-y-0 right-3 flex items-center" aria-live="polite">
                {formatOk && availability.isFetching && (
                  <Loader2 className="size-4 animate-spin text-muted-foreground" />
                )}
                {formatOk && !availability.isFetching && availability.data === true && (
                  <CheckCircle2 className="size-4 text-online" aria-label="Available" />
                )}
                {taken && <XCircle className="size-4 text-destructive" aria-label="Taken" />}
              </span>
            </div>
            {taken && !errors.username && (
              <p className="text-sm text-destructive">This username is taken.</p>
            )}
            {formatOk && availability.data === true && (
              <p className="text-sm text-online">{candidate} is available</p>
            )}
          </Field>

          <Field label="Name" error={errors.name?.message} htmlFor="name">
            <Input
              id="name"
              autoComplete="name"
              aria-invalid={!!errors.name}
              {...register('name')}
            />
          </Field>

          <Field label="Email" error={errors.email?.message} htmlFor="email">
            <Input
              id="email"
              type="email"
              autoComplete="email"
              aria-invalid={!!errors.email}
              {...register('email')}
            />
          </Field>

          <Button type="submit" size="lg" disabled={isSubmitting || taken}>
            {isSubmitting && <Loader2 className="animate-spin" />} Continue
          </Button>
        </form>
      </div>
    </div>
  )
}

function Field({
  label,
  htmlFor,
  error,
  children,
}: {
  label: string
  htmlFor: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  )
}
