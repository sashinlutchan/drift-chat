import { io, type Socket } from 'socket.io-client'
import { config } from '@/lib/config'
import { useConnection } from '@/stores/connection'

/**
 * The one Socket.IO connection (spec section 15). WebSocket only, so no sticky sessions are needed. `withCredentials`
 * sends the session cookie with the handshake; nothing goes in the `auth` payload.
 */
export const socket: Socket = io(config.socketUrl, {
  autoConnect: false,
  transports: ['websocket'],
  withCredentials: true,
  reconnection: true,
  reconnectionDelay: 1000,
  reconnectionDelayMax: 10_000,
})

const HEARTBEAT_MS = 25_000
let heartbeat: ReturnType<typeof setInterval> | undefined

socket.on('connect', () => {
  useConnection.getState().setStatus('connected')
  clearInterval(heartbeat)
  heartbeat = setInterval(() => socket.emit('presence:heartbeat'), HEARTBEAT_MS)
})
socket.on('disconnect', () => {
  useConnection.getState().setStatus('reconnecting')
  clearInterval(heartbeat)
})
socket.on('connect_error', () => useConnection.getState().setStatus('reconnecting'))
socket.io.on('reconnect_attempt', () => useConnection.getState().setStatus('reconnecting'))

export function connectSocket() {
  if (!socket.connected && !socket.active) {
    useConnection.getState().setStatus('connecting')
    socket.connect()
  }
}

export function disconnectSocket() {
  clearInterval(heartbeat)
  socket.disconnect()
}

export interface AckOk<T = unknown> {
  ok: true
  data?: T
}
export interface AckError {
  ok: false
  error: { event: string; code: string; message: string }
}
export type Ack<T = unknown> = AckOk<T> | AckError

/** Emits an event and resolves with the server's ack; rejects on an error ack or a timeout. */
export function emitWithAck<T = unknown>(
  event: string,
  payload: unknown,
  timeoutMs = 10_000,
): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    socket.timeout(timeoutMs).emit(event, payload, (timeout: Error | null, ack?: Ack<T>) => {
      if (timeout) return reject(new SocketError('TIMEOUT', 'The server took too long to answer.'))
      if (!ack) return reject(new SocketError('NO_ACK', 'No answer from the server.'))
      if (ack.ok) return resolve(ack.data)
      reject(new SocketError(ack.error.code, ack.error.message))
    })
  })
}

export class SocketError extends Error {
  readonly code: string
  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}
