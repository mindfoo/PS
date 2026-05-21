import { Link, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { useAuth, usePermissions } from '../contexts/AuthContext'

const STORAGE_KEY = 'sidebar-collapsed'

function readCollapsed(): boolean {
  return localStorage.getItem(STORAGE_KEY) === 'true'
}

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()
  const perms = usePermissions()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(readCollapsed)

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  function toggleCollapsed() {
    setCollapsed(prev => {
      const next = !prev
      localStorage.setItem(STORAGE_KEY, String(next))
      return next
    })
  }

  const sidebarClass = [
    'sidebar',
    menuOpen    ? 'sidebar--open'      : '',
    collapsed   ? 'sidebar--collapsed' : '',
  ].filter(Boolean).join(' ')

  return (
    <div className={`app-layout${collapsed ? ' app-layout--sidebar-collapsed' : ''}`}>
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

      <nav className={sidebarClass} aria-label="Main navigation">
        <div className="sidebar-logo">
          <span className="logo-icon">⚡</span>
          <span className="logo">
            <Link to="/dashboard" onClick={() => setMenuOpen(false)}>Workflow Platform</Link>
          </span>
        </div>

        <ul className="nav-links">
          <li>
            <Link to="/dashboard" onClick={() => setMenuOpen(false)} title="Workflows">
              <span className="nav-icon">🗂</span>
              <span className="nav-label">Workflows</span>
            </Link>
          </li>
          <li>
            <Link to="/tasks" onClick={() => setMenuOpen(false)} title="Tasks">
              <span className="nav-icon">📋</span>
              <span className="nav-label">Tasks</span>
            </Link>
          </li>
          <li>
            <Link to="/schedules" onClick={() => setMenuOpen(false)} title="Schedules">
              <span className="nav-icon">🕒</span>
              <span className="nav-label">Schedules</span>
            </Link>
          </li>
          <li>
            <Link to="/profile" onClick={() => setMenuOpen(false)} title="Profile">
              <span className="nav-icon">👤</span>
              <span className="nav-label">Profile</span>
            </Link>
          </li>
          {perms.canAccessAdmin && (
            <li>
              <Link to="/admin" onClick={() => setMenuOpen(false)} title="Admin">
                <span className="nav-icon">🛡</span>
                <span className="nav-label">Admin</span>
              </Link>
            </li>
          )}
        </ul>

        <div className="sidebar-footer">
          <div className="user-info">
            <span className="nav-icon user-avatar">👤</span>
            <span className="nav-label">
              {user?.username}
              <span className={`role-badge role-${user?.role?.toLowerCase()}`}>{user?.role}</span>
            </span>
          </div>
          <button onClick={handleLogout} className="btn btn-ghost btn-sm nav-label">Logout</button>
        </div>

        {/* Desktop collapse toggle — hidden on mobile */}
        <button
          className="sidebar-toggle-btn"
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          onClick={toggleCollapsed}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {collapsed ? '›' : '‹'}
        </button>
      </nav>

      <main className="main-content">{children}</main>
    </div>
  )
}
