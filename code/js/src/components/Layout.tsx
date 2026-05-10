import { Link, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { useAuth, usePermissions } from '../contexts/AuthContext'

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const perms = usePermissions()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <div className="app-layout">
      {/* Mobile hamburger button */}
      <button
        className="hamburger-btn"
        aria-label="Toggle navigation"
        onClick={() => setMenuOpen(o => !o)}
      >
        {menuOpen ? '✕' : '☰'}
      </button>

      {menuOpen && (
        <div className="sidebar-backdrop" onClick={() => setMenuOpen(false)} />
      )}

      <nav className={`sidebar${menuOpen ? ' sidebar--open' : ''}`}>
        <div className="sidebar-logo">
          <span className="logo-icon">⚡</span>
          <span className="logo"><Link to="/dashboard" onClick={() => setMenuOpen(false)}>Workflow Platform</Link></span>
        </div>
        <ul className="nav-links">
          <li><Link to="/dashboard" onClick={() => setMenuOpen(false)}>🗂 Workflows</Link></li>
          <li><Link to="/tasks" onClick={() => setMenuOpen(false)}>📋 Tasks</Link></li>
          <li><Link to="/schedules" onClick={() => setMenuOpen(false)}>🕒 Schedules</Link></li>
          <li><Link to="/profile" onClick={() => setMenuOpen(false)}>👤 Profile</Link></li>
          {perms.canAccessAdmin && <li><Link to="/admin" onClick={() => setMenuOpen(false)}>🛡 Admin</Link></li>}
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

