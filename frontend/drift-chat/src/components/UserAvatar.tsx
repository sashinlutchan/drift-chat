import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { fileUrl } from '@/lib/config'
import { initials } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useIsOnline } from '@/stores/presence'
interface Props {
  name: string
  avatarFileId?: string | null
  userId?: string | null
  online?: boolean
  showStatus?: boolean
  className?: string
}

export function UserAvatar({
  name,
  avatarFileId,
  userId,
  online = false,
  showStatus = false,
  className,
}: Props) {
  const live = useIsOnline(userId, online)
  return (
    <span className="relative inline-flex shrink-0">
      <Avatar className={className}>
        <AvatarImage src={fileUrl(avatarFileId)} alt={`${name}'s avatar`} />
        <AvatarFallback>{initials(name) || '?'}</AvatarFallback>
      </Avatar>
      {showStatus && (
        <span
          role="img"
          aria-label={live ? 'Online' : 'Offline'}
          className={cn(
            'absolute right-0 bottom-0 size-2.5 rounded-full ring-2 ring-background',
            live ? 'bg-online' : 'bg-muted-foreground/50',
          )}
        />
      )}
    </span>
  )
}
