import { useEffect, useState, type FormEvent } from 'react'
import { useParams, useNavigate, Link, useSearchParams } from 'react-router-dom'
import { taskApi } from '../api/tasks'
import { Layout } from '../components/Layout'

export function TaskFormPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  const workflowId = searchParams.get('workflowId') ?? ''
  const isEdit = !!id
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [type, setType] = useState('HTTP')
  const [configText, setConfigText] = useState('{}')
  const [loading, setLoading] = useState(false)
  const [fetchLoading, setFetchLoading] = useState(isEdit)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!isEdit) return
    taskApi.getById(id!)
      .then(t => {
        setName(t.name)
        setType(t.type)
        setConfigText(JSON.stringify(t.config, null, 2))
      })
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setFetchLoading(false))
  }, [id, isEdit])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')

    let config: Record<string, unknown> = {}
    try {
      config = JSON.parse(configText)
    } catch {
      setError('Config must be valid JSON')
      return
    }

    setLoading(true)
    try {
      if (isEdit) {
        const task = await taskApi.update(id!, { name, type, config })
        navigate(`/workflows/${task.workflowId}`)
      } else {
        const task = await taskApi.create({ name, type, workflowId, config })
        navigate(`/workflows/${task.workflowId}`)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setLoading(false)
    }
  }

  const backUrl = workflowId ? `/workflows/${workflowId}` : '/dashboard'
  if (fetchLoading) return <Layout><div className="loading">Loading…</div></Layout>

  return (
    <Layout>
      <div className="page-header">
        <div>
          <Link to={backUrl} className="back-link">← Back</Link>
          <h1>{isEdit ? 'Edit Task' : 'New Task'}</h1>
        </div>
      </div>

      <div className="form-card">
        {error && <div className="alert alert-error">{error}</div>}
        <form onSubmit={handleSubmit} className="form">
          <div className="form-group">
            <label>Task name</label>
            <input value={name} onChange={e => setName(e.target.value)} placeholder="Fetch Data" required />
          </div>
          <div className="form-group">
            <label>Type</label>
            <select value={type} onChange={e => setType(e.target.value)}>
              <option>HTTP</option>
              <option>SCRIPT</option>
              <option>EMAIL</option>
              <option>DATABASE</option>
              <option>CUSTOM</option>
            </select>
          </div>
          <div className="form-group">
            <label>Config (JSON)</label>
            <textarea
              value={configText}
              onChange={e => setConfigText(e.target.value)}
              rows={6}
              className="code-textarea"
              placeholder='{}'
            />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-ghost" onClick={() => navigate(backUrl)}>Cancel</button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving…' : isEdit ? 'Save changes' : 'Create task'}
            </button>
          </div>
        </form>
      </div>
    </Layout>
  )
}

