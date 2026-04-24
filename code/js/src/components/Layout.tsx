import { Link, useNavigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth, usePermissions } from '../contexts/AuthContext'

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const perms = usePermissions()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <div className="app-layout">
      <nav className="sidebar">
        <div className="sidebar-logo">
          <span className="logo-icon">⚡</span>
          <span className="logo"><Link to="/dashboard">Workflow Platform</Link></span>
        </div>
        <ul className="nav-links">
          <li><Link to="/dashboard">🗂 Workflows</Link></li>
          <li><Link to="/schedules">🕒 Schedules</Link></li>
          <li><Link to="/profile">👤 Profile</Link></li>
          {perms.canAccessAdmin && <li><Link to="/admin">🛡 Admin</Link></li>}
        </ul>
        <div className="sidebar-footer">
          <span className="user-info">
            {user?.username}
            <span className={`role-badge role-${user?.role?.toLowerCase()}`}>{user?.role}</span>
          </span>
          <button onClick={handleLogout} className="btn btn-ghost btn-sm">Logout</button>
        </div>
      </nav>
      <main className="main-content">{children}</main>
    </div>
  )
}

