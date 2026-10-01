import { ArrowDown, Loader2 } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import type { DisplayMessage } from '@/api/types'
import { Button } from '@/components/ui/button'
import { formatDayLabel } from '@/lib/format'
import { MessageBubble } from './MessageBubble'
interface Props {
  messages: DisplayMessage[]
  meId: string | undefined
  hasOlder: boolean
  loadingOlder: boolean
  onLoadOlder: () => void
  onImage: (src: string, name: string) => void
  onRetry: (clientId: string) => void
}
const NEAR_BOTTOM = 120
const GROUP_GAP_MS = 5 * 60 * 1000

export function MessageList({
  messages,
  meId,
  hasOlder,
  loadingOlder,
  onLoadOlder,
  onImage,
  onRetry,
}: Props) {
  const scroller = useRef<HTMLDivElement>(null)
  const atBottom = useRef(true)
  const previous = useRef({ count: 0, firstId: '', height: 0 })
  const [unseen, setUnseen] = useState(0)
  const scrollToBottom = useCallback((smooth = false) => {
    const el = scroller.current
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
    setUnseen(0)
  }, [])
  useLayoutEffect(() => {
    const el = scroller.current
    if (!el) return
    const prev = previous.current
    const firstId = messages[0]?.id ?? ''
    const addedOlder = prev.count > 0 && firstId !== prev.firstId && messages.length > prev.count
    if (addedOlder) {
      el.scrollTop += el.scrollHeight - prev.height
    } else if (prev.count === 0) {
      el.scrollTop = el.scrollHeight
    } else if (messages.length > prev.count) {
      const mine = messages[messages.length - 1]?.sender?.id === meId
      if (atBottom.current || mine) requestAnimationFrame(() => scrollToBottom(true))
      else setUnseen((n) => n + (messages.length - prev.count))
    }
    previous.current = { count: messages.length, firstId, height: el.scrollHeight }
  }, [messages, meId, scrollToBottom])
  const onScroll = () => {
    const el = scroller.current
    if (!el) return
    atBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM
    if (atBottom.current) setUnseen(0)
    if (el.scrollTop < 80 && hasOlder && !loadingOlder) onLoadOlder()
  }
  useEffect(() => {
    const el = scroller.current
    if (el && hasOlder && !loadingOlder && el.scrollHeight <= el.clientHeight) onLoadOlder()
  }, [messages, hasOlder, loadingOlder, onLoadOlder])
  return (
    <div className="relative min-h-0 flex-1">
      <div
        ref={scroller}
        onScroll={onScroll}
        className="scrollbar-thin h-full overflow-y-auto px-4 py-4"
        role="log"
        aria-live="polite"
        aria-label="Messages"
      >
        {loadingOlder && (
          <div
            className="flex justify-center py-2"
            role="status"
            aria-label="Loading older messages"
          >
            <Loader2 className="size-4 animate-spin text-muted-foreground" />
          </div>
        )}
        {!hasOlder && messages.length > 0 && (
          <p className="py-4 text-center text-xs text-muted-foreground">
            This is the start of the room.
          </p>
        )}
        {messages.length === 0 && !hasOlder && (
          <p className="py-16 text-center text-muted-foreground">No messages yet. Say hello 👋</p>
        )}
        {messages.map((message, i) => {
          const before = messages[i - 1]
          const day = formatDayLabel(message.createdAt)
          const showDay = !before || formatDayLabel(before.createdAt) !== day
          const first =
            showDay ||
            !before ||
            before.type === 'system' ||
            message.type === 'system' ||
            before.sender?.id !== message.sender?.id ||
            new Date(message.createdAt).getTime() - new Date(before.createdAt).getTime() >
              GROUP_GAP_MS
          return (
            <div key={message.clientId ?? message.id}>
              {showDay && (
                <div className="my-4 flex items-center gap-3 text-xs text-muted-foreground">
                  <span className="h-px flex-1 bg-border" />
                  {day}
                  <span className="h-px flex-1 bg-border" />
                </div>
              )}
              <MessageBubble
                message={message}
                own={!!meId && message.sender?.id === meId}
                first={first}
                onImage={onImage}
                onRetry={onRetry}
              />
            </div>
          )
        })}
      </div>
      {unseen > 0 && (
        <Button
          className="absolute bottom-4 left-1/2 -translate-x-1/2 rounded-full shadow-lg"
          size="sm"
          onClick={() => scrollToBottom(true)}
        >
          <ArrowDown /> {unseen} new {unseen === 1 ? 'message' : 'messages'}
        </Button>
      )}
    </div>
  )
}
