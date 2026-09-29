import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { usersApi } from '@/api/users'
import { problemOf } from '@/api/client'
import { connectSocket, disconnectSocket, socket } from '@/socket/client'
import { useSocketEvent } from '@/socket/hooks'
import { useAuth } from '@/stores/auth'
import { usePresence } from '@/stores/presence'

/**
 * Connects the socket while signed in and turns live events into fresh data (spec sections 4, 5.1 and 15): rooms
 * appear, disappear and change counts, members come and go, online dots flip — without a refresh.
 */
export function SocketBridge() {
  const queryClient = useQueryClient()
  const me = useAuth((s) => s.user)

  useEffect(() => {
    connectSocket()
    return () => disconnectSocket()
  }, [])

  // The server closes the socket when the session's JWT expires; a failed reconnect then means the session is over
  useEffect(() => {
    const check = () => {
      usersApi.me().catch((error: unknown) => {
        if (problemOf(error).status === 401) useAuth.getState().endSession()
      })
    }
    socket.on('connect_error', check)
    return () => {
      socket.off('connect_error', check)
    }
  }, [])

  const refreshRooms = () => void queryClient.invalidateQueries({ queryKey: ['rooms'] })

  useSocketEvent('room:created', refreshRooms)
  useSocketEvent<{ roomId: string }>('room:updated', (e) => {
    refreshRooms()
    void queryClient.invalidateQueries({ queryKey: ['room-info', e.roomId] })
  })
  useSocketEvent<{ roomId: string }>('room:deleted', (e) => {
    refreshRooms()
    void queryClient.invalidateQueries({ queryKey: ['room-info', e.roomId] })
  })
  useSocketEvent<{ roomId: string }>('member:joined', (e) => {
    void queryClient.invalidateQueries({ queryKey: ['members', e.roomId] })
    refreshRooms()
  })
  useSocketEvent<{ roomId: string }>('member:left', (e) => {
    void queryClient.invalidateQueries({ queryKey: ['members', e.roomId] })
    refreshRooms()
  })
  useSocketEvent<{ userId: string; online: boolean }>('presence:update', (e) => {
    usePresence.getState().set(e.userId, e.online)
    void queryClient.invalidateQueries({ queryKey: ['members'] })
    refreshRooms()
  })
  useSocketEvent<{ userId: string }>('user:updated', (e) => {
    void queryClient.invalidateQueries({ queryKey: ['members'] })
    void queryClient.invalidateQueries({ queryKey: ['user', e.userId] })
    if (e.userId === me?.id) void usersApi.me().then((u) => useAuth.getState().setUser(u))
  })
  useSocketEvent<{ userId: string }>('user:deleted', (e) => {
    void queryClient.invalidateQueries({ queryKey: ['members'] })
    void queryClient.invalidateQueries({ queryKey: ['user', e.userId] })
  })

  return null
}
