import { api } from './client'
import type { Member, Room, RoomInfo } from './types'

export const roomsApi = {
  list: (search?: string) =>
    api.get<Room[]>('/rooms', { params: search ? { search } : undefined }).then((r) => r.data),
  create: (name: string) => api.post<Room>('/rooms', { name }).then((r) => r.data),
  info: (id: string) => api.get<RoomInfo>(`/rooms/${id}`).then((r) => r.data),
  bySlug: (slug: string) =>
    api.get<Room>(`/rooms/by-slug/${encodeURIComponent(slug)}`).then((r) => r.data),
  members: (id: string) => api.get<Member[]>(`/rooms/${id}/members`).then((r) => r.data),
  join: (id: string) => api.post<Room>(`/rooms/${id}/join`).then((r) => r.data),
  leave: (id: string) => api.post(`/rooms/${id}/leave`).then(() => undefined),
  pin: (id: string) => api.put(`/rooms/${id}/pin`).then(() => undefined),
  unpin: (id: string) => api.delete(`/rooms/${id}/pin`).then(() => undefined),
}
