import { useEffect, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { executionApi, type ExecutionSummaryResponse, type TaskExecutionSummary } from '../api/executions'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'
import { StatusBadge } from '../components/StatusBadge'
import { LoadingSpinner } from '../components/LoadingSpinner'

export function ExecutionDetailPage() {
  const { workflowId, executionId } = useParams<{ workflowId: string; executionId: string }>()
  const navigate = useNavigate()
  const perms = usePermissions()

  const [execution, setExecution] = useState<ExecutionSummaryResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [cancelling, setCancelling] = useState(false)

  useEffect(() => {
    if (!executionId) return
    executionApi.getById(executionId)
      .then(setExecution)
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load execution'))
      .finally(() => setLoading(false))
  }, [executionId])

  async function handleCancel() {
    if (!executionId || !confirm('Cancel this execution?')) return
    setCancelling(true)
    setError('')
    try {
      await executionApi.cancel(executionId)
      setExecution(prev => prev ? { ...prev, status: 'CANCELED' } : prev)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cancel failed')
    } finally {
      setCancelling(false)
    }
  }

  if (loading) return <Layout><LoadingSpinner /></Layout>

  if (error || !execution) {
    return (
      <Layout>
        <div className="page-header">
          <Link to={`/workflows/${workflowId}`} className="back-link">← Back to workflow</Link>
        </div>
        <div className="alert alert-error">{error || 'Execution not found.'}</div>
      </Layout>
    )
  }

  const isActive = execution.status === 'PENDING' || execution.status === 'RUNNING'

  return (
    <Layout>
      <div className="back-link-row">
        <Link to={`/workflows/${workflowId}`} className="back-link">← Back to workflow</Link>
      </div>

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="page-header">
        <div>
          <h1 className="execution-title">
            Execution <code className="execution-id">{execution.id.slice(0, 8)}…</code>
          </h1>
          <p className="text-muted">
            <span className="badge">{execution.triggeredType}</span>
            {' · '}
            {execution.type === 'WORKFLOW' ? 'Full workflow run' : 'Single task run'}
            {' · by '}
            <strong>{execution.triggeredBy}</strong>
          </p>
        </div>
        <div className="header-actions">
          {isActive && perms.canExecuteWorkflows && (
            <button
              className="btn btn-danger"
              onClick={() => { void handleCancel() }}
              disabled={cancelling}
            >
              {cancelling ? 'Cancelling…' : 'Cancel execution'}
            </button>
          )}
          <button className="btn btn-ghost" onClick={() => navigate(`/workflows/${workflowId}`)}>
            Back
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {/* ── Summary card ───────────────────────────────────────────────── */}
      <div className="detail-card">
        <div className="detail-grid">
          <div className="detail-item">
            <span className="detail-label">Status</span>
            <StatusBadge status={execution.status} showIcon />
          </div>
          <div className="detail-item">
            <span className="detail-label">Started</span>
            <span>{new Date(execution.startedAt).toLocaleString()}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Finished</span>
            <span>{execution.finishedAt ? new Date(execution.finishedAt).toLocaleString() : '—'}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Retries</span>
            <span>{execution.retryCount}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Trigger</span>
            <span className="badge">{execution.triggeredType}</span>
          </div>
          <div className="detail-item">
            <span className="detail-label">Type</span>
            <span className="badge">{execution.type}</span>
          </div>
        </div>
      </div>

      {/* ── Task executions ─────────────────────────────────────────────── */}
      {execution.taskExecutions && execution.taskExecutions.length > 0 && (
        <section className="exec-section">
          <h2 className="section-title">Task executions</h2>
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr>
                  <th>Task</th>
                  <th>Status</th>
                  <th>Started</th>
                  <th>Finished</th>
                  <th>Output</th>
                </tr>
              </thead>
              <tbody>
                {execution.taskExecutions.map((te: TaskExecutionSummary) => (
                  <tr key={te.executionId}>
                    <td><strong>{te.taskName ?? te.taskId ?? '—'}</strong></td>
                    <td><StatusBadge status={te.status} showIcon /></td>
                    <td className="td-timestamp">{new Date(te.startedAt).toLocaleString()}</td>
                    <td className="td-timestamp">
                      {te.finishedAt ? new Date(te.finishedAt).toLocaleString() : '—'}
                    </td>
                    <td>
                      {te.output
                        ? <pre className="execution-output-pre">{JSON.stringify(te.output, null, 2)}</pre>
                        : <span className="text-muted">—</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* ── Workflow-level output ───────────────────────────────────────── */}
      {execution.output && (
        <section className="exec-section">
          <h2 className="section-title">Output</h2>
          <pre className="execution-output-pre--full">
            {JSON.stringify(execution.output, null, 2)}
          </pre>
        </section>
      )}

      {!execution.taskExecutions?.length && !execution.output && (
        <p className="text-muted">No output recorded.</p>
      )}
    </Layout>
  )
}
