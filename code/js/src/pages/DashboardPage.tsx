import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { workflowApi, type WorkflowResponse } from '../api/workflows'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'
import { PageHeader } from '../components/PageHeader'
import { StatusBadge } from '../components/StatusBadge'
import { EmptyState } from '../components/EmptyState'
import { LoadingSpinner } from '../components/LoadingSpinner'

export function DashboardPage() {
  const [workflows, setWorkflows] = useState<WorkflowResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const perms = usePermissions()
  const navigate = useNavigate()

  async function load() {
    try {
      setWorkflows(await workflowApi.list())
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load workflows')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    let cancelled = false
    workflowApi.list()
      .then(data => { if (!cancelled) setWorkflows(data) })
      .catch(err => { if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load workflows') })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [])

  async function handleDelete(id: string) {
    if (!confirm('Delete this workflow?')) return
    try {
      await workflowApi.delete(id)
      setWorkflows(prev => prev.filter(w => w.id !== id))
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  async function handleRun(id: string) {
    try {
      await workflowApi.run(id)
      await load()
      setTimeout(() => load(), 4000)
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Run failed')
    }
  }

  return (
    <Layout>
      <PageHeader
        title="Workflows"
        actions={perms.canWriteWorkflows
          ? <button className="btn btn-primary" onClick={() => navigate('/workflows/new')}>+ New Workflow</button>
          : undefined
        }
      />

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <LoadingSpinner />
      ) : workflows.length === 0 ? (
        <EmptyState
          message="No workflows yet."
          action={perms.canWriteWorkflows
            ? <button className="btn btn-primary" onClick={() => navigate('/workflows/new')}>Create your first workflow</button>
            : undefined
          }
        />
      ) : (
        <div className="card-grid">
          {workflows.map(w => (
            <div key={w.id} className="card">
              <div className="card-body">
                <h3>{w.name}</h3>
                <p className="text-muted">Owner: {w.ownerUsername}</p>
                {w.isPrivate && <span className="badge badge-private">🔒 Private</span>}
                {w.lastRunStatus && <StatusBadge status={w.lastRunStatus} showIcon />}
              </div>
              <div className="card-actions">
                <Link to={`/workflows/${w.id}`} className="btn btn-sm btn-secondary">View</Link>
                {perms.canWriteWorkflows && (
                  <Link to={`/workflows/${w.id}/edit`} className="btn btn-sm btn-secondary">Edit</Link>
                )}
                {perms.canExecuteWorkflows && (
                  <button className="btn btn-sm btn-success" onClick={() => handleRun(w.id)}>▶ Run</button>
                )}
                {perms.canDeleteWorkflows && (
                  <button className="btn btn-sm btn-danger" onClick={() => handleDelete(w.id)}>Delete</button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </Layout>
  )
}
