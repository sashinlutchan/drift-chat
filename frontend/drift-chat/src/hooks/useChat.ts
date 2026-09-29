import { useCallback, useEffect, useRef, useState } from 'react'
import { messagesApi } from '@/api/messages'
import { errorMessage } from '@/api/client'
import type { ChatMessage, DisplayMessage, Sender } from '@/api/types'
import { emitWithAck, socket, SocketError } from '@/socket/client'
import { useSocketEvent } from '@/socket/hooks'
import { useAuth } from '@/stores/auth'

type RoomState = 'joining' | 'ready' | 'gone' | 'not-member' | 'error'

/** Oldest first, no duplicates, and a real message replaces the placeholder that was standing in for it. */
export function mergeMessages(
  current: DisplayMessage[],
  incoming: ChatMessage[],
): DisplayMessage[] {
  const byId = new Map<string, DisplayMessage>()
  for (const m of current) if (!m.status) byId.set(m.id, m)
  for (const m of incoming) byId.set(m.id, m)
  const real = [...byId.values()].sort(
    (a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id),
  )
  const realIds = new Set(real.map((m) => m.id))
  // Pending and failed messages stay at the end until the server confirms them
  const pending = current.filter((m) => m.status && !realIds.has(m.id))
  return [...real, ...pending]
}

/**
 * Everything a room screen needs about its chat: history with "load older", live messages over the socket, sending
 * with retry, the typing indicator and whether the room could be entered at all.
 */
export function useChat(roomId: string) {
  const me = useAuth((s) => s.user)
  const [messages, setMessages] = useState<DisplayMessage[]>([])
  const [state, setState] = useState<RoomState>('joining')
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [cursor, setCursor] = useState<string | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [typing, setTyping] = useState<Sender[]>([])
  const [error, setError] = useState<string | null>(null)
  const cursorRef = useRef<string | null>(null)

  const loadLatest = useCallback(async () => {
    const page = await messagesApi.history(roomId)
    setMessages((current) => mergeMessages(current, [...page.messages].reverse()))
    if (!cursorRef.current) {
      cursorRef.current = page.nextCursor
      setCursor(page.nextCursor)
    }
    setLoaded(true)
  }, [roomId])

  /** Enters the room over the socket and (re)loads recent history; runs again after every reconnect to fill any gap. */
  useEffect(() => {
    let cancelled = false
    const enter = async () => {
      try {
        await emitWithAck('room:enter', roomId)
        await loadLatest()
        if (!cancelled) {
          setState('ready')
          setError(null)
        }
      } catch (e) {
        if (cancelled) return
        if (e instanceof SocketError && e.code === 'NOT_FOUND') setState('gone')
        else if (e instanceof SocketError && e.code === 'FORBIDDEN') setState('not-member')
        else {
          setState('error')
          setError(errorMessage(e))
        }
      }
    }
    if (socket.connected) void enter()
    socket.on('connect', enter)
    return () => {
      cancelled = true
      socket.off('connect', enter)
      if (socket.connected) socket.emit('room:exit', roomId)
    }
  }, [roomId, loadLatest])

  useSocketEvent<ChatMessage>('message:new', (message) => {
    if (message.roomId !== roomId) return
    setMessages((current) => {
      // the sender's own message replaces its "sending…" placeholder
      const placeholder = current.find(
        (m) =>
          m.status === 'sending' && m.text === message.text && m.sender?.id === message.sender?.id,
      )
      const rest = placeholder ? current.filter((m) => m !== placeholder) : current
      return mergeMessages(rest, [message])
    })
  })

  // The ack says which real message a "sending…" placeholder became; if that message already arrived, drop the placeholder
  useSocketEvent<{ clientId: string; id: string }>('message:ack', (ack) => {
    setMessages((current) =>
      current.some((m) => m.id === ack.id)
        ? current.filter((m) => m.clientId !== ack.clientId)
        : current,
    )
  })

  useSocketEvent<{ roomId: string; users: Sender[] }>('typing:update', (update) => {
    if (update.roomId === roomId) setTyping(update.users.filter((u) => u.id !== me?.id))
  })

  // "Sipho is typing…" clears itself if no fresh update arrives
  useEffect(() => {
    if (typing.length === 0) return
    const timer = setTimeout(() => setTyping([]), 7000)
    return () => clearTimeout(timer)
  }, [typing])

  useSocketEvent<{ roomId: string }>('room:deleted', (e) => {
    if (e.roomId === roomId) setState('gone')
  })

  const loadOlder = useCallback(async () => {
    if (!cursorRef.current || loadingOlder) return
    setLoadingOlder(true)
    try {
      const page = await messagesApi.history(roomId, cursorRef.current)
      cursorRef.current = page.nextCursor
      setCursor(page.nextCursor)
      setMessages((current) => mergeMessages(current, [...page.messages].reverse()))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setLoadingOlder(false)
    }
  }, [roomId, loadingOlder])

  const transmit = useCallback(
    async (clientId: string, text: string) => {
      try {
        await emitWithAck('message:send', { roomId, text, clientId })
      } catch (e) {
        const reason = e instanceof SocketError ? e.message : errorMessage(e)
        setError(reason)
        setMessages((current) =>
          current.map((m) => (m.clientId === clientId ? { ...m, status: 'failed' as const } : m)),
        )
      }
    },
    [roomId],
  )

  const send = useCallback(
    (text: string) => {
      const clientId = crypto.randomUUID()
      const pending: DisplayMessage = {
        id: `pending-${clientId}`,
        clientId,
        status: 'sending',
        roomId,
        type: 'text',
        text,
        fileId: null,
        createdAt: new Date().toISOString(),
        sender: me
          ? {
              id: me.id,
              name: me.name,
              username: me.username,
              avatarFileId: me.avatarFileId,
              deleted: false,
            }
          : null,
      }
      setMessages((current) => [...current, pending])
      void transmit(clientId, text)
    },
    [me, roomId, transmit],
  )

  /** Sends a failed message again with the same client id, so it can never appear twice. */
  const retry = useCallback(
    (clientId: string) => {
      const failed = messages.find((m) => m.clientId === clientId)
      if (!failed) return
      setMessages((current) =>
        current.map((m) => (m.clientId === clientId ? { ...m, status: 'sending' as const } : m)),
      )
      void transmit(clientId, failed.text)
    },
    [messages, transmit],
  )

  const addMessage = useCallback(
    (message: ChatMessage) => setMessages((current) => mergeMessages(current, [message])),
    [],
  )

  return {
    messages,
    state,
    loaded,
    hasOlder: cursor !== null,
    loadingOlder,
    loadOlder,
    send,
    retry,
    typing,
    error,
    addMessage,
  }
}
