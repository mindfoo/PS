import { useAuth } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'

const ROLE_DESCRIPTIONS: Record<string, string> = {
  ADMIN: 'Full access — manage users, roles, all workflows and tasks.',
  WRITE: 'Writer — create, edit and delete own workflows and tasks.',
  READ: 'Reader — read-only access to workflows and execution logs.',
  DEV: 'Developer — debug/dev access to logs and schedules.',
}

export function ProfilePage() {
  const { user } = useAuth()

  if (!user) return null

  return (
    <Layout>
      <div className="page-header">
        <h1>Profile</h1>
      </div>

      <div className="form-card profile-card">
        <div className="profile-avatar">{user.username.charAt(0).toUpperCase()}</div>
        <div className="profile-info">
          <h2>{user.username}</h2>
          <span className={`role-badge role-${user.role.toLowerCase()}`}>{user.role}</span>
          <p className="text-muted mt-2">{ROLE_DESCRIPTIONS[user.role] ?? 'Custom role'}</p>
        </div>

        <div className="profile-permissions">
          <h3>Your permissions</h3>
          <ul className="permission-list">
            <PermRow label="Read workflows" granted={['ADMIN','WRITE','READ','DEV'].includes(user.role)} />
            <PermRow label="Create / edit workflows" granted={['ADMIN','WRITE'].includes(user.role)} />
            <PermRow label="Delete workflows" granted={['ADMIN','WRITE'].includes(user.role)} />
            <PermRow label="Execute workflows" granted={['ADMIN','WRITE'].includes(user.role)} />
            <PermRow label="Manage schedules" granted={['ADMIN','WRITE'].includes(user.role)} />
            <PermRow label="Admin panel" granted={user.role === 'ADMIN'} />
          </ul>
        </div>
      </div>
    </Layout>
  )
}

function PermRow({ label, granted }: { label: string; granted: boolean }) {
  return (
    <li className={`perm-row ${granted ? 'granted' : 'denied'}`}>
      <span className="perm-icon">{granted ? '✅' : '🚫'}</span>
      {label}
    </li>
  )
}

