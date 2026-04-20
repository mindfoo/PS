import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { authApi } from '../api/auth'
import type { MeResponse } from '../api/auth'

interface AuthContextValue {
  user: MeResponse | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    try {
      const me = await authApi.me()
      setUser(me)
    } catch {
      setUser(null)
    }
  }

  useEffect(() => {
    refresh().finally(() => setLoading(false))
  }, [])

  async function login(username: string, password: string) {
    await authApi.login({ username, password })
    await refresh()
  }

  async function logout() {
    await authApi.logout()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

/** Check if user has at least one of the given roles */
export function useHasRole(...roles: string[]) {
  const { user } = useAuth()
  return user != null && roles.includes(user.role)
}

/** Authorities inferred from role */
export function usePermissions() {
  const { user } = useAuth()
  const role = user?.role ?? ''

  const isAdmin = role === 'ADMIN'
  const isWrite = isAdmin || role === 'WRITE'
  const isRead = isWrite || role === 'READ' || role === 'DEV'
  const isDev = isAdmin || role === 'DEV'

  return {
    canReadWorkflows: isRead,
    canWriteWorkflows: isWrite,
    canDeleteWorkflows: isWrite,
    canExecuteWorkflows: isWrite,
    canReadTasks: isRead,
    canWriteTasks: isWrite,
    canDeleteTasks: isWrite,
    canReadSchedules: isRead,
    canWriteSchedules: isWrite,
    canDeleteSchedules: isWrite,
    canAccessAdmin: isAdmin,
    canAccessDev: isDev,
  }
}

