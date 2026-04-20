import { useEffect, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { workflowApi, type WorkflowResponse } from '../api/workflows'
import { taskApi, type TaskResponse } from '../api/tasks'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'

export function WorkflowDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const perms = usePermissions()

  const [workflow, setWorkflow] = useState<WorkflowResponse | null>(null)
  const [tasks, setTasks] = useState<TaskResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!id) return
    Promise.all([workflowApi.getById(id), taskApi.listByWorkflow(id)])
      .then(([w, t]) => { setWorkflow(w); setTasks(t) })
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setLoading(false))
  }, [id])

  async function handleDeleteTask(taskId: string) {
    if (!confirm('Delete this task?')) return
    try {
      await taskApi.delete(taskId)
      setTasks(prev => prev.filter(t => t.id !== taskId))
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  if (loading) return <Layout><div className="loading">Loading…</div></Layout>
  if (error) return <Layout><div className="alert alert-error">{error}</div></Layout>
  if (!workflow) return <Layout><div>Not found</div></Layout>

  return (
    <Layout>
      <div className="page-header">
        <div>
          <Link to="/dashboard" className="back-link">← Workflows</Link>
          <h1>{workflow.name}</h1>
          <p className="text-muted">Owner: {workflow.ownerUsername}</p>
        </div>
        <div className="header-actions">
          {perms.canWriteWorkflows && (
            <Link to={`/workflows/${id}/edit`} className="btn btn-secondary">Edit Workflow</Link>
          )}
          {perms.canWriteSchedules && (
            <Link to={`/schedules?workflowId=${id}`} className="btn btn-secondary">🕒 Schedule</Link>
          )}
          {perms.canExecuteWorkflows && (
            <button className="btn btn-success" onClick={async () => {
              try {
                const res = await workflowApi.run(id!)
                alert(`Execution started: ${res.executionId}`)
              } catch (err: unknown) { alert(err instanceof Error ? err.message : 'Run failed') }
            }}>▶ Run now</button>
          )}
        </div>
      </div>

      <div className="section-header">
        <h2>Tasks ({tasks.length})</h2>
        {perms.canWriteTasks && (
          <button className="btn btn-primary" onClick={() => navigate(`/workflows/${id}/tasks/new`)}>+ Add Task</button>
        )}
      </div>

      {tasks.length === 0 ? (
        <div className="empty-state">
          <p>No tasks yet.</p>
          {perms.canWriteTasks && <button className="btn btn-primary" onClick={() => navigate(`/workflows/${id}/tasks/new`)}>Add first task</button>}
        </div>
      ) : (
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Type</th><th>Config</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {tasks.map(t => (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td><span className="badge">{t.type}</span></td>
                  <td><code className="config-preview">{JSON.stringify(t.config)}</code></td>
                  <td className="actions-cell">
                    {perms.canWriteTasks && (
                      <Link to={`/tasks/${t.id}/edit?workflowId=${id}`} className="btn btn-sm btn-secondary">Edit</Link>
                    )}
                    {perms.canDeleteTasks && (
                      <button className="btn btn-sm btn-danger" onClick={() => handleDeleteTask(t.id)}>Delete</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Layout>
  )
}

