import { api } from './client'
import type { OwnUser, PublicUser } from './types'
export interface SignUpInput {
  username: string
  name: string
  email: string
  avatar: File
}

export const usersApi = {
  me: () => api.get<OwnUser>('/users/me').then((r) => r.data),
  signUp: (input: SignUpInput) => {
    const form = new FormData()
    form.append('username', input.username)
    form.append('name', input.name)
    form.append('email', input.email)
    form.append('avatar', input.avatar)
    return api.post<OwnUser>('/users', form).then((r) => r.data)
  },
  update: (input: { name: string; email: string; avatar?: File }) => {
    const form = new FormData()
    form.append('name', input.name)
    form.append('email', input.email)
    if (input.avatar) form.append('avatar', input.avatar)
    return api.patch<OwnUser>('/users/me', form).then((r) => r.data)
  },
  get: (id: string) => api.get<PublicUser>(`/users/${id}`).then((r) => r.data),
  checkUsername: (username: string) =>
    api
      .get<{
        username: string
        available: boolean
      }>('/users/check-username', {
        params: { username },
      })
      .then((r) => r.data.available),
}
