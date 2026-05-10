import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { taskApi, type TaskResponse } from '../api/tasks'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'
import { TaskType } from './TaskFormPage'

function configSummary(type: string, config: Record<string, unknown>): string {
  switch (type) {
    case TaskType.HTTP:   return `${config.method ?? 'GET'} ${config.url ?? ''}`
    case TaskType.SCRIPT: {
      const cmd  = config.command  ?? ''
      const file = config.fileName ?? ''
      return `${cmd} ${file}`.trim()
    }
    default: return JSON.stringify(config)
  }
}

export function TasksPage() {
  const navigate = useNavigate()
  const perms = usePermissions()

  const [tasks, setTasks]   = useState<TaskResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError]     = useState('')

  useEffect(() => {
    taskApi.listAll()
      .then(setTasks)
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load tasks'))
      .finally(() => setLoading(false))
  }, [])

  async function handleDelete(id: string) {
    if (!confirm('Delete this task? This cannot be undone.')) return
    try {
      await taskApi.delete(id)
      setTasks(prev => prev.filter(t => t.id !== id))
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  if (loading) return <Layout><div className="loading">Loading…</div></Layout>
  if (error)   return <Layout><div className="alert alert-error">{error}</div></Layout>

  return (
    <Layout>
      <div className="page-header">
        <div>
          <h1>Tasks</h1>
          <p className="text-muted">All tasks — reusable across multiple workflows</p>
        </div>
        <div className="header-actions">
          {perms.canWriteTasks && (
            <button className="btn btn-primary" onClick={() => navigate('/tasks/new')}>+ New Task</button>
          )}
        </div>
      </div>

      {tasks.length === 0 ? (
        <div className="empty-state">
          <p>No tasks yet.</p>
          {perms.canWriteTasks && (
            <button className="btn btn-primary" onClick={() => navigate('/tasks/new')}>Create first task</button>
          )}
        </div>
      ) : (
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Type</th>
                <th>Config</th>
                <th>Workflow</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {tasks.map(t => {
                const inUse = !!t.workflowId
                return (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td><span className="badge">{t.type}</span></td>
                  <td><code className="config-preview">{configSummary(t.type, t.config)}</code></td>
                  <td>
                    {inUse
                      ? <span className="badge badge-muted">Linked</span>
                      : <span className="text-muted">—</span>}
                  </td>
                  <td className="actions-cell">
                    {perms.canWriteTasks && (
                      <Link to={`/tasks/${t.id}/edit`} className="btn btn-sm btn-secondary">Edit</Link>
                    )}
                    {perms.canDeleteTasks && (
                      <button
                        className="btn btn-sm btn-danger"
                        onClick={() => handleDelete(t.id!)}
                        disabled={inUse}
                        title={inUse ? 'Cannot delete: task is linked to a workflow. Unlink it first.' : undefined}
                      >Delete</button>
                    )}
                  </td>
                </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Layout>
  )
}
