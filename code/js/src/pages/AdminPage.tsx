import { useEffect, useState } from 'react'
import { workflowApi, type WorkflowResponse } from '../api/workflows'
import { Layout } from '../components/Layout'

interface UserRow { id: string; username: string; role: string }

export function AdminPage() {
  const [users, setUsers] = useState<UserRow[]>([])
  const [workflows, setWorkflows] = useState<WorkflowResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [tab, setTab] = useState<'users' | 'workflows'>('users')

  useEffect(() => {
    Promise.all([
      fetch('/api/users', { credentials: 'include' }).then(r => r.json()).catch(() => []),
      workflowApi.list().catch(() => []),
    ]).then(([u, w]) => {
      setUsers(Array.isArray(u) ? u : [])
      setWorkflows(Array.isArray(w) ? w : [])
    }).catch(err => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setLoading(false))
  }, [])

  return (
    <Layout>
      <div className="page-header">
        <h1>🛡 Admin Panel</h1>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="stats-row">
        <div className="stat-card">
          <div className="stat-value">{users.length}</div>
          <div className="stat-label">Users</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{workflows.length}</div>
          <div className="stat-label">Workflows</div>
        </div>
      </div>

      <div className="tabs">
        <button className={`tab ${tab === 'users' ? 'active' : ''}`} onClick={() => setTab('users')}>Users</button>
        <button className={`tab ${tab === 'workflows' ? 'active' : ''}`} onClick={() => setTab('workflows')}>All Workflows</button>
      </div>

      {loading ? <div className="loading">Loading…</div> : (
        <>
          {tab === 'users' && (
            <div className="table-wrapper">
              <table className="table">
                <thead><tr><th>Username</th><th>Role</th></tr></thead>
                <tbody>
                  {users.length === 0
                    ? <tr><td colSpan={2} className="text-muted">No users returned (endpoint may require different permissions)</td></tr>
                    : users.map((u: UserRow) => (
                      <tr key={u.id}>
                        <td>{u.username}</td>
                        <td><span className={`role-badge role-${u.role?.toLowerCase()}`}>{u.role}</span></td>
                      </tr>
                    ))
                  }
                </tbody>
              </table>
            </div>
          )}

          {tab === 'workflows' && (
            <div className="table-wrapper">
              <table className="table">
                <thead><tr><th>Name</th><th>Owner</th></tr></thead>
                <tbody>
                  {workflows.map(w => (
                    <tr key={w.id}>
                      <td>{w.name}</td>
                      <td>{w.ownerUsername}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </Layout>
  )
}

