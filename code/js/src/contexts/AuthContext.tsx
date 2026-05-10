import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { authApi } from '../api/auth'
import type { UserAuth } from '../api/auth'

interface AuthContext {
  user: UserAuth | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  refresh: () => Promise<void>
}

export enum RoleType {
  ADMIN = 'ADMIN',
  WRITER = 'WRITER',
  READER = 'READER',
  DEV = 'DEV',
}

const AuthContext = createContext<AuthContext | null>(null)

/** Provider for Authentication */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserAuth | null>(null)
  const [loading, setLoading] = useState(true)

  async function refresh() {
    try {
      const profile = await authApi.profile()
      setUser(profile)
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

/** Hook to access auth context and check if user is logged in */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}

/** Checks if user has any of the specified roles */
export function useHasRole(...roles: string[]) {
  const { user } = useAuth()
  return user != null && roles.includes(user.role)
}

/** Authorities inferred from role, returns all possible permissions from roles */
export function usePermissions() {
  const { user } = useAuth()
  const role = user?.role ?? ''

  const isAdmin = role === RoleType.ADMIN
  const isWrite = isAdmin || role === RoleType.WRITER
  const isRead = isWrite || role === RoleType.READER || role === RoleType.DEV
  const isDev = isAdmin || role === RoleType.DEV

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

