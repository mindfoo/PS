import { api } from './client'

export interface MeResponse {
  id: string
  username: string
  role: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  roleName?: string
}

export const authApi = {
  login: (body: LoginRequest) => api.post<{ message: string }>('/auth/login', body),
  register: (body: RegisterRequest) => api.post<MeResponse>('/auth/register', body),
  logout: () => api.post<void>('/auth/logout', {}),
  me: () => api.get<MeResponse>('/auth/me'),
}

