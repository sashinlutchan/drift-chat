import { AlertCircle, Loader2, RotateCcw } from 'lucide-react'
import { Link } from 'react-router'
import type { DisplayMessage } from '@/api/types'
import { UserAvatar } from '@/components/UserAvatar'
import { Button } from '@/components/ui/button'
import { fileUrl } from '@/lib/config'
import { formatClock, splitLinks } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useIsOnline } from '@/stores/presence'
import { AudioPlayer } from './AudioPlayer'

interface Props {
  message: DisplayMessage
  own: boolean
  /** First message of a run from the same sender: shows the avatar and name. */
  first: boolean
  onImage: (src: string, name: string) => void
  onRetry: (clientId: string) => void
}

/** One message: text with clickable links, or an inline image, audio or video (spec sections 7.2 and 7.3). */
export function MessageBubble({ message, own, first, onImage, onRetry }: Props) {
  const sender = message.sender
  const online = useIsOnline(sender?.id, false)
  const src = fileUrl(message.fileId)

  if (message.type === 'system') {
    return (
      <div role="status" className="my-2 text-center text-xs text-muted-foreground">
        {message.text}
      </div>
    )
  }

  return (
    <div
      className={cn(
        'flex gap-2.5',
        own ? 'flex-row-reverse' : 'flex-row',
        first ? 'mt-3' : 'mt-0.5',
      )}
      data-testid="message"
    >
      <div className="w-8 shrink-0">
        {first && sender && (
          <ProfileLink id={sender.id}>
            <UserAvatar
              name={sender.name}
              avatarFileId={sender.avatarFileId}
              userId={sender.id}
              online={online}
              showStatus={!!sender.id}
              className="size-8"
            />
          </ProfileLink>
        )}
      </div>
      <div className={cn('flex max-w-[80%] min-w-0 flex-col', own ? 'items-end' : 'items-start')}>
        {first && sender && (
          <div className={cn('mb-1 flex items-baseline gap-2 text-xs', own && 'flex-row-reverse')}>
            <ProfileLink id={sender.id}>
              <span className="font-medium text-foreground">
                {sender.deleted ? 'Deleted user' : sender.name}
              </span>
            </ProfileLink>
            <time className="text-muted-foreground" dateTime={message.createdAt}>
              {formatClock(message.createdAt)}
            </time>
          </div>
        )}
        <div
          className={cn(
            'rounded-2xl px-3.5 py-2 text-[0.95rem] break-words',
            own
              ? 'rounded-tr-md bg-primary text-primary-foreground'
              : 'rounded-tl-md bg-secondary text-secondary-foreground',
            message.status === 'sending' && 'opacity-70',
            message.status === 'failed' && 'ring-2 ring-destructive',
            message.type !== 'text' && 'p-1.5',
          )}
        >
          {message.type === 'text' && <TextBody text={message.text} own={own} />}
          {message.type === 'image' && src && (
            <button
              type="button"
              className="block overflow-hidden rounded-xl focus-visible:ring-[3px] focus-visible:ring-ring"
              onClick={() => onImage(src, message.text)}
            >
              <img
                src={src}
                alt={message.text}
                loading="lazy"
                className="max-h-72 max-w-full rounded-xl object-cover sm:max-w-xs"
              />
            </button>
          )}
          {message.type === 'audio' && src && (
            <div className="px-2 py-1.5">
              <AudioPlayer src={src} name={message.text} />
            </div>
          )}
          {message.type === 'video' && src && (
            <video
              src={src}
              controls
              preload="metadata"
              aria-label={`Video: ${message.text}`}
              className="max-h-72 max-w-full rounded-xl bg-black sm:max-w-sm"
            />
          )}
        </div>
        {message.status === 'sending' && (
          <span className="mt-0.5 inline-flex items-center gap-1 text-xs text-muted-foreground">
            <Loader2 className="size-3 animate-spin" /> Sending…
          </span>
        )}
        {message.status === 'failed' && message.clientId && (
          <span className="mt-0.5 inline-flex items-center gap-2 text-xs text-destructive">
            <AlertCircle className="size-3" /> Not sent
            <Button
              variant="ghost"
              size="sm"
              className="h-6 px-2 text-xs"
              onClick={() => onRetry(message.clientId!)}
            >
              <RotateCcw className="size-3" /> Retry
            </Button>
          </span>
        )}
      </div>
    </div>
  )
}

function TextBody({ text, own }: { text: string; own: boolean }) {
  return (
    <p className="whitespace-pre-wrap">
      {splitLinks(text).map((part, i) =>
        part.url ? (
          <a
            key={i}
            href={part.text}
            target="_blank"
            rel="noopener noreferrer nofollow"
            className={cn(
              'underline underline-offset-2',
              own ? 'text-primary-foreground' : 'text-primary',
            )}
          >
            {part.text}
          </a>
        ) : (
          <span key={i}>{part.text}</span>
        ),
      )}
    </p>
  )
}

function ProfileLink({ id, children }: { id: string | null; children: React.ReactNode }) {
  if (!id) return <>{children}</>
  return (
    <Link
      to={`/users/${id}`}
      className="rounded-full hover:opacity-80 focus-visible:ring-[3px] focus-visible:ring-ring"
    >
      {children}
    </Link>
  )
}
