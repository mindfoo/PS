import { api } from './client'
import type { MeResponse } from './auth'

export interface UserResponse {
  id: string
  username: string
  role: string
}

export const usersApi = {
  list: () => api.get<UserResponse[]>('/users'),
  me: () => api.get<MeResponse>('/auth/me'),
}

