import { useEffect, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { executionApi, type ExecutionSummaryResponse, type TaskExecutionSummary } from '../api/executions'
import { workflowApi } from '../api/workflows'
import { taskApi } from '../api/tasks'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'
import { StatusBadge } from '../components/StatusBadge'
import { LoadingSpinner } from '../components/LoadingSpinner'

export function ExecutionDetailPage() {
  const { workflowId, executionId } = useParams<{ workflowId: string; executionId: string }>()
  const navigate = useNavigate()
  const perms = usePermissions()

  const [execution, setExecution] = useState<ExecutionSummaryResponse | null>(null)
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState('')
  const [cancelling, setCancelling] = useState(false)
  const [retrying, setRetrying]   = useState(false)

  useEffect(() => {
    if (!executionId) return

    let mounted = true
    let unsub: (() => void) | undefined

    // Subscribe first, then fetch — guarantees we never miss the terminal event
    unsub = executionApi.subscribeToExecution(executionId, (event) => {
      if (!mounted) return

      setExecution(prev => {
        if (!prev) return prev

        // FIX 2: Stabilize status mappings using defensive fallbacks to preserve running tasks
        const updatedTasks = prev.taskExecutions?.map(te => {
          const incomingStatus = event.taskStatuses[te.taskId ?? '']
          return {
            ...te,
            status: (incomingStatus as any) || te.status
          }
        }) || null

        return {
          ...prev,
          status: event.status,
          taskExecutions: updatedTasks
        }
      })

      if (event.terminal) {
        void executionApi.getById(executionId).then(data => {
          if (mounted) setExecution(data)
        })
        if (unsub) {
          unsub()
          unsub = undefined
        }
      }
    })

    // Initial state fetch — closes SSE if execution is already terminal
    executionApi.getById(executionId)
        .then(data => {
          if (!mounted) return
          setExecution(data)
          if (data.status !== 'PENDING' && data.status !== 'RUNNING') {
            if (unsub) {
              unsub()
              unsub = undefined
            }
          }
        })
        .catch(err => {
          if (mounted) setError(err instanceof Error ? err.message : 'Failed to load execution')
        })
        .finally(() => {
          if (mounted) setLoading(false)
        })

    // FIX 3: Robust teardown on unmount or whenever executionId shifts
    return () => {
      mounted = false
      if (unsub) {
        unsub()
      }
    }
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

  async function handleRetry() {
    if (!workflowId) return
    setRetrying(true)
    setError('')
    try {
      const res = (execution?.type === 'TASK' && execution.taskExecutions?.[0]?.taskId)
          ? await taskApi.run(execution.taskExecutions[0].taskId)
          : await workflowApi.run(workflowId)
      navigate(`/workflows/${workflowId}/executions/${res.executionId}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Retry failed')
    } finally {
      setRetrying(false)
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

  const isActive   = execution.status === 'PENDING' || execution.status === 'RUNNING'
  const isTerminal = !isActive

  return (
      <Layout>
        <div className="back-link-row">
          <Link to={`/workflows/${workflowId}`} className="back-link">← Back to workflow</Link>
        </div>

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
                    onClick={handleCancel}
                    disabled={cancelling}
                >
                  {cancelling ? 'Cancelling…' : 'Cancel execution'}
                </button>
            )}
            {isTerminal && perms.canExecuteWorkflows && (
                <button
                    className="btn btn-secondary"
                    onClick={handleRetry}
                    disabled={retrying}
                >
                  {retrying ? 'Starting…' : '↺ Retry'}
                </button>
            )}
          </div>
        </div>

        {error && <div className="alert alert-error">{error}</div>}

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