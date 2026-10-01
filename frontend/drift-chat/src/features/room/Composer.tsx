import { Paperclip, RotateCw, Send, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Progress } from '@/components/ui/progress'
import { Textarea } from '@/components/ui/textarea'
import { config, MEDIA_ACCEPT } from '@/lib/config'
import { formatBytes, typingLabel } from '@/lib/format'
import { checkFile, startUpload, type UploadHandle } from '@/lib/uploader'
import { socket } from '@/socket/client'
import { useUploads } from '@/stores/uploads'
import type { ChatMessage, Sender } from '@/api/types'
interface Props {
  roomId: string
  typing: Sender[]
  disabled?: boolean
  onSend: (text: string) => void
  onUploaded: (message: ChatMessage) => void
}
const TYPING_REFRESH_MS = 2500
const TYPING_IDLE_MS = 3000

export function Composer({ roomId, typing, disabled, onSend, onUploaded }: Props) {
  const [text, setText] = useState('')
  const input = useRef<HTMLTextAreaElement>(null)
  const picker = useRef<HTMLInputElement>(null)
  const lastTyping = useRef(0)
  const idle = useRef<ReturnType<typeof setTimeout> | undefined>(undefined)
  const handles = useRef(
    new Map<
      string,
      {
        handle: UploadHandle
        file: File
      }
    >(),
  )
  const uploads = useUploads((s) => s.items)
  const inRoom = Object.values(uploads).filter((u) => u.roomId === roomId)
  const emitTyping = (on: boolean) => {
    socket.emit(on ? 'typing:start' : 'typing:stop', roomId)
  }
  const onChange = (value: string) => {
    setText(value)
    clearTimeout(idle.current)
    if (!value.trim()) {
      lastTyping.current = 0
      return emitTyping(false)
    }
    const now = Date.now()
    if (now - lastTyping.current > TYPING_REFRESH_MS) {
      lastTyping.current = now
      emitTyping(true)
    }
    idle.current = setTimeout(() => {
      lastTyping.current = 0
      emitTyping(false)
    }, TYPING_IDLE_MS)
  }
  useEffect(() => () => clearTimeout(idle.current), [])
  const submit = () => {
    const value = text.trim()
    if (!value || disabled) return
    clearTimeout(idle.current)
    lastTyping.current = 0
    onSend(value)
    setText('')
    input.current?.focus()
  }
  const begin = (file: File) => {
    const problem = checkFile(file)
    if (problem) return toast.error('Can’t send that file', { description: problem })
    const handle = startUpload(roomId, file)
    handles.current.set(handle.id, { handle, file })
    handle.done.then(onUploaded).catch((error: Error) => {
      if (error.message !== 'Upload cancelled.')
        toast.error('Upload failed', { description: error.message })
    })
  }
  const retry = (id: string) => {
    const entry = handles.current.get(id)
    if (!entry) return
    useUploads.getState().remove(id)
    handles.current.delete(id)
    begin(entry.file)
  }
  const cancel = (id: string) => {
    handles.current.get(id)?.handle.cancel()
    handles.current.delete(id)
  }
  const label = typingLabel(typing.map((t) => t.name))
  return (
    <div className="border-t bg-background px-4 pt-2 pb-4">
      <div
        className="h-5 text-xs text-muted-foreground"
        aria-live="polite"
        data-testid="typing-indicator"
      >
        {label}
      </div>

      {inRoom.map((u) => (
        <div key={u.id} className="mb-2 rounded-xl border bg-card p-3" data-testid="upload">
          <div className="mb-1.5 flex items-center gap-2 text-sm">
            <span className="min-w-0 flex-1 truncate font-medium">{u.fileName}</span>
            {u.phase === 'failed' ? (
              <>
                <Button size="sm" variant="outline" onClick={() => retry(u.id)}>
                  <RotateCw /> Retry
                </Button>
                <Button
                  size="icon-sm"
                  variant="ghost"
                  aria-label="Dismiss"
                  onClick={() => (cancel(u.id), useUploads.getState().remove(u.id))}
                >
                  <X />
                </Button>
              </>
            ) : (
              <Button
                size="icon-sm"
                variant="ghost"
                aria-label={`Cancel upload of ${u.fileName}`}
                onClick={() => cancel(u.id)}
              >
                <X />
              </Button>
            )}
          </div>
          {u.phase === 'failed' ? (
            <p role="alert" className="text-xs text-destructive">
              {u.error}
            </p>
          ) : (
            <>
              <Progress
                value={Math.round((u.sent / u.size) * 100)}
                aria-label={`Uploading ${u.fileName}`}
              />
              <p className="mt-1 text-xs text-muted-foreground tabular-nums">
                {Math.round((u.sent / u.size) * 100)}% · {formatBytes(u.sent)} of{' '}
                {formatBytes(u.size)}
              </p>
            </>
          )}
        </div>
      ))}

      <div className="flex items-end gap-2">
        <input
          ref={picker}
          type="file"
          accept={MEDIA_ACCEPT}
          className="sr-only"
          aria-label="Attach a file"
          data-testid="attach-input"
          onChange={(e) => {
            const file = e.target.files?.[0]
            if (file) begin(file)
            e.target.value = ''
          }}
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label="Attach an image, audio or video"
          onClick={() => picker.current?.click()}
          disabled={disabled}
        >
          <Paperclip />
        </Button>
        <Textarea
          ref={input}
          rows={1}
          value={text}
          maxLength={config.maxMessageLength}
          disabled={disabled}
          placeholder="Write a message"
          aria-label="Message"
          className="max-h-40 min-h-10 flex-1 field-sizing-content"
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault()
              submit()
            }
          }}
        />
        <Button
          type="button"
          size="icon"
          aria-label="Send message"
          onClick={submit}
          disabled={disabled || !text.trim()}
        >
          <Send />
        </Button>
      </div>
    </div>
  )
}
