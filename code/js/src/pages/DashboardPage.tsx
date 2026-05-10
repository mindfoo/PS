import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { workflowApi, type WorkflowResponse } from '../api/workflows'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'
import type {ExecutionStatus} from "../api/executions.ts";

function LastRunBadge({ status }: { status?: string | null }) {
  if (!status) return null
  const map: Record<ExecutionStatus, { icon: string; cls: string }> = {
    SUCCESS: { icon: '✅', cls: 'badge-status-success' },
    ERROR:   { icon: '❌', cls: 'badge-status-error' },
    RUNNING: { icon: '⏳', cls: 'badge-status-running' },
    PENDING: { icon: '🕐', cls: 'badge-status-pending' },
    CANCELED: { icon: '-', cls: 'badge-status-canceled' },
  }
  const entry = map[status as ExecutionStatus] ?? { icon: '•', cls: 'badge-muted' }
  return <span className={`badge ${entry.cls}`} title={`Last run: ${status}`}>{entry.icon} {status}</span>
}

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

  useEffect(() => { load() }, [])

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
      <div className="page-header">
        <h1>Workflows</h1>
        {perms.canWriteWorkflows && (
          <button className="btn btn-primary" onClick={() => navigate('/workflows/new')}>+ New Workflow</button>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="loading">Loading…</div>
      ) : workflows.length === 0 ? (
        <div className="empty-state">
          <p>No workflows yet.</p>
          {perms.canWriteWorkflows && <button className="btn btn-primary" onClick={() => navigate('/workflows/new')}>Create your first workflow</button>}
        </div>
      ) : (
        <div className="card-grid">
          {workflows.map(w => (
            <div key={w.id} className="card">
              <div className="card-body">
                <h3>{w.name}</h3>
                <p className="text-muted">Owner: {w.ownerUsername}</p>
                <LastRunBadge status={w.lastRunStatus} />
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

