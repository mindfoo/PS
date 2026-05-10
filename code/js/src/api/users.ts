import { api } from './client'
import type { MeResponse } from './auth'

export interface UserResponse {
  id: string
  username: string
  role: string
  permissions: string[]
}

export interface RoleSummary {
  name: string
  permissions: string[]
}

export const usersApi = {
  list: () => api.get<UserResponse[]>('/users'),
  listRoles: () => api.get<RoleSummary[]>('/users/roles'),
  updateRole: (userId: string, roleName: string) =>
    api.patch<UserResponse>(`/users/${userId}/role`, { roleName }),
  me: () => api.get<MeResponse>('/auth/me'),
}

