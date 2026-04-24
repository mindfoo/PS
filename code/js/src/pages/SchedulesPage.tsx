import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { scheduleApi, type ScheduleResponse } from '../api/schedules'
import { workflowApi, type WorkflowResponse } from '../api/workflows'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'

export function SchedulesPage() {
  const [schedules, setSchedules] = useState<ScheduleResponse[]>([])
  const [workflows, setWorkflows] = useState<WorkflowResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editTarget, setEditTarget] = useState<ScheduleResponse | null>(null)
  const [error, setError] = useState('')
  const perms = usePermissions()
  const [searchParams] = useSearchParams()
  const preselectedWorkflowId = searchParams.get('workflowId') ?? ''
  const navigate = useNavigate()

  // Form state
  const [workflowId, setWorkflowId] = useState(preselectedWorkflowId)
  const [cron, setCron] = useState('0 * * * *')
  const [timezone, setTimezone] = useState('UTC')
  const [enabled, setEnabled] = useState(true)
  const [saving, setSaving] = useState(false)

  async function load() {
    try {
      const [s, w] = await Promise.all([scheduleApi.list(), workflowApi.list()])
      setSchedules(s)
      setWorkflows(w)
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  function openCreate() {
    setEditTarget(null)
    setWorkflowId(preselectedWorkflowId)
    setCron('0 * * * *')
    setTimezone('UTC')
    setEnabled(true)
    setShowForm(true)
  }

  function openEdit(s: ScheduleResponse) {
    setEditTarget(s)
    setWorkflowId(s.workflowId)
    setCron(s.cronExpression)
    setTimezone(s.timezone)
    setEnabled(s.enabled)
    setShowForm(true)
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setSaving(true)
    try {
      if (editTarget) {
        const updated = await scheduleApi.update(editTarget.id, { cronExpression: cron, timezone, enabled })
        setSchedules(prev => prev.map(s => s.id === updated.id ? updated : s))
      } else {
        const created = await scheduleApi.create({ workflowId, cronExpression: cron, timezone, enabled })
        setSchedules(prev => [...prev, created])
      }
      setShowForm(false)
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: string) {
    if (!confirm('Delete this schedule?')) return
    try {
      await scheduleApi.delete(id)
      setSchedules(prev => prev.filter(s => s.id !== id))
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : 'Delete failed')
    }
  }

  return (
    <Layout>
      <div className="page-header">
        <h1>Schedules</h1>
        {perms.canWriteSchedules && <button className="btn btn-primary" onClick={openCreate}>+ New Schedule</button>}
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {showForm && (
        <div className="form-card">
          <h2>{editTarget ? 'Edit Schedule' : 'New Schedule'}</h2>
          <form onSubmit={handleSubmit} className="form">
            {!editTarget && (
              <div className="form-group">
                <label>Workflow</label>
                <select value={workflowId} onChange={e => setWorkflowId(e.target.value)} required>
                  <option value="">Select workflow…</option>
                  {workflows.map(w => <option key={w.id} value={w.id}>{w.name}</option>)}
                </select>
              </div>
            )}
            <div className="form-group">
              <label>find a way of doing a schedule</label>
              <input value={cron} onChange={e => setCron(e.target.value)} placeholder="whatever format" required />
              <small className="hint">Format: minute hour day month weekday</small>
            </div>
            <div className="form-group">
              <label>Timezone</label>
              <input value={timezone} onChange={e => setTimezone(e.target.value)} placeholder="UTC" required />
            </div>
            <div className="form-group form-check">
              <input type="checkbox" id="enabled" checked={enabled} onChange={e => setEnabled(e.target.checked)} />
              <label htmlFor="enabled">Enabled</label>
            </div>
            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => setShowForm(false)}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : editTarget ? 'Save changes' : 'Create schedule'}
              </button>
            </div>
          </form>
        </div>
      )}

      {loading ? (
        <div className="loading">Loading…</div>
      ) : schedules.length === 0 ? (
        <div className="empty-state"><p>No schedules yet.</p></div>
      ) : (
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th>Workflow</th><th>Cron</th><th>Timezone</th>
                <th>Status</th><th>Next run</th><th>Last run</th>
                {perms.canWriteSchedules && <th>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {schedules.map(s => (
                <tr key={s.id}>
                  <td>
                    <button className="link-btn" onClick={() => navigate(`/workflows/${s.workflowId}`)}>
                      {s.workflowName}
                    </button>
                  </td>
                  <td><code>{s.cronExpression}</code></td>
                  <td>{s.timezone}</td>
                  <td><span className={`badge ${s.enabled ? 'badge-success' : 'badge-muted'}`}>{s.enabled ? 'Active' : 'Disabled'}</span></td>
                  <td>{new Date(s.nextRunAt).toLocaleString()}</td>
                  <td>{s.lastRunAt ? new Date(s.lastRunAt).toLocaleString() : '—'}</td>
                  {perms.canWriteSchedules && (
                    <td className="actions-cell">
                      <button className="btn btn-sm btn-secondary" onClick={() => openEdit(s)}>Edit</button>
                      {perms.canDeleteSchedules && (
                        <button className="btn btn-sm btn-danger" onClick={() => handleDelete(s.id)}>Delete</button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Layout>
  )
}

