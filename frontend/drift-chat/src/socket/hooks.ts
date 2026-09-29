import { useEffect, useRef } from 'react'
import { socket } from './client'

/** Subscribes to a socket event for as long as the component is mounted, always calling the latest handler. */
export function useSocketEvent<T = unknown>(event: string, handler: (payload: T) => void) {
  const latest = useRef(handler)
  useEffect(() => {
    latest.current = handler
  })
  useEffect(() => {
    const listener = (payload: T) => latest.current(payload)
    socket.on(event, listener)
    return () => {
      socket.off(event, listener)
    }
  }, [event])
}
