import { Loader2, WifiOff } from 'lucide-react'
import { useConnection } from '@/stores/connection'
export function ConnectionBanner() {
  const status = useConnection((s) => s.status)
  if (status === 'connected') return null
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center justify-center gap-2 bg-amber-500/15 px-4 py-1.5 text-sm text-amber-700 dark:text-amber-300"
    >
      {status === 'offline' ? (
        <WifiOff className="size-4" />
      ) : (
        <Loader2 className="size-4 animate-spin" />
      )}
      {status === 'connecting' ? 'Connecting…' : 'Reconnecting…'}
    </div>
  )
}
