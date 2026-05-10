import { api } from './client'

export interface UserAuth {
  id: string
  username: string
  role: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  roleName?: string
}

export const authApi = {
  login: (body: LoginRequest) => api.post('/auth/login', body),
  register: (body: RegisterRequest) => api.post<UserAuth>('/auth/register', body),
  logout: () => api.post<void>('/auth/logout', {}),
  profile: () => api.get<UserAuth>('/auth/profile'),
}

