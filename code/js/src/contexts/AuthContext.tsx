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

/** Provides authentication state and exposes login/logout/refresh to the component tree. */
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
    void refresh().finally(() => setLoading(false))
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

/** Returns the nearest AuthContext value — throws if used outside AuthProvider. */
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}

/** Returns true if the current user holds any of the specified role names. */
export function useHasRole(...roles: string[]) {
  const { user } = useAuth()
  return user !== null && roles.includes(user.role)
}

/** Maps the current user's role to a flat set of boolean capability flags. */
export function usePermissions() {
  const { user } = useAuth()
  const role = (user?.role ?? '') as RoleType

  const isAdmin  = role === RoleType.ADMIN
  const isWriter = isAdmin || role === RoleType.WRITER
  const isDev    = isAdmin || role === RoleType.DEV
  const isRead   = isWriter || role === RoleType.READER || isDev

  return {
    // Workflows
    canReadWorkflows:    isRead,
    canWriteWorkflows:   isWriter,
    canDeleteWorkflows:  isWriter,
    canExecuteWorkflows: isWriter || isDev,   // DEV has workflow:execute
    // Tasks
    canReadTasks:    isRead,
    canWriteTasks:   isWriter,
    canDeleteTasks:  isWriter,
    // Schedules
    canReadSchedules:   isRead,
    canWriteSchedules:  isWriter,
    canDeleteSchedules: isWriter,
    // Executions
    canReadExecutions: isRead,
    // System
    canAccessAdmin: isAdmin,
    canAccessDev:   isDev,
    isReader: role === RoleType.READER,
  }
}

