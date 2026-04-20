import { useEffect, useState, type FormEvent } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { workflowApi } from '../api/workflows'
import { Layout } from '../components/Layout'

export function WorkflowFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = !!id
  const navigate = useNavigate()

  const [name, setName] = useState('')
  const [loading, setLoading] = useState(false)
  const [fetchLoading, setFetchLoading] = useState(isEdit)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!isEdit) return
    workflowApi.getById(id!)
      .then(w => setName(w.name))
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setFetchLoading(false))
  }, [id, isEdit])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      if (isEdit) {
        await workflowApi.update(id!, { name })
        navigate(`/workflows/${id}`)
      } else {
        const created = await workflowApi.create({ name })
        navigate(`/workflows/${created.id}`)
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Save failed')
    } finally {
      setLoading(false)
    }
  }

  if (fetchLoading) return <Layout><div className="loading">Loading…</div></Layout>

  return (
    <Layout>
      <div className="page-header">
        <div>
          <Link to="/dashboard" className="back-link">← Workflows</Link>
          <h1>{isEdit ? 'Edit Workflow' : 'New Workflow'}</h1>
        </div>
      </div>

      <div className="form-card">
        {error && <div className="alert alert-error">{error}</div>}
        <form onSubmit={handleSubmit} className="form">
          <div className="form-group">
            <label>Workflow name</label>
            <input value={name} onChange={e => setName(e.target.value)} placeholder="My Pipeline" required />
          </div>
          <div className="form-actions">
            <button type="button" className="btn btn-ghost" onClick={() => navigate(isEdit ? `/workflows/${id}` : '/dashboard')}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={loading}>
              {loading ? 'Saving…' : isEdit ? 'Save changes' : 'Create workflow'}
            </button>
          </div>
        </form>
      </div>
    </Layout>
  )
}

