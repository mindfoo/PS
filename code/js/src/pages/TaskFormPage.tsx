import { useEffect, useReducer, useState } from 'react'
import { useParams, useNavigate, Navigate } from 'react-router-dom'
import { taskApi } from '../api/tasks'
import { Layout } from '../components/Layout'
import { PageHeader } from '../components/PageHeader'
import { LoadingSpinner } from '../components/LoadingSpinner'
import { genericFormReducer } from '../utils/form'
import { TaskType } from '../utils/task'
import type { FormEvent } from 'react'

export { TaskType } from '../utils/task'

export interface TaskFormInputs {
  name: string
  type: TaskType
  url: string; method: string
  command: string; fileName: string; directory: string; args: string
  to: string; subject: string; body: string
  query: string
}

const DEFAULT_INPUTS: TaskFormInputs = {
  name: '', type: TaskType.HTTP,
  url: '', method: 'GET',
  command: '', fileName: '', directory: '', args: '',
  to: '', subject: '', body: '',
  query: '',
}

function fromTask(t: { name: string; type: TaskType; config: Record<string, unknown> }): TaskFormInputs {
  const c = t.config as Record<string, string>
  return {
    ...DEFAULT_INPUTS,
    name: t.name,
    type: t.type,
    url: c.url ?? '', method: c.method ?? 'GET',
    command: c.command ?? '', fileName: c.fileName ?? '',
    directory: c.directory ?? '',
    args: Array.isArray(c.args) ? (c.args as unknown as string[]).join(' ') : (c.args ?? ''),
    to: c.to ?? '', subject: c.subject ?? '', body: c.body ?? '',
    query: c.query ?? '',
  }
}

function buildConfig(inputs: TaskFormInputs): Record<string, unknown> {
  switch (inputs.type) {
    case TaskType.HTTP:   return { url: inputs.url, method: inputs.method }
    case TaskType.SCRIPT: {
      const cfg: Record<string, unknown> = { command: inputs.command, fileName: inputs.fileName }
      if (inputs.directory) cfg.directory = inputs.directory
      if (inputs.args)      cfg.args = inputs.args.trim().split(/\s+/)
      return cfg
    }
    default: return {}
  }
}

export function TaskFormPage() {
  const { id, workflowId: routeWorkflowId } = useParams<{ id?: string; workflowId?: string }>()
  const [searchParams] = [new URLSearchParams(window.location.search)]
  const queryWorkflowId = searchParams.get('workflowId') ?? undefined
  const isEdit = !!id

  const [initialInputs, setInitialInputs] = useState<TaskFormInputs | null>(isEdit ? null : DEFAULT_INPUTS)
  const [workflowId, setWorkflowId]       = useState(routeWorkflowId ?? queryWorkflowId ?? '')
  const [initialIsPrivate, setInitialIsPrivate] = useState(false)
  const [error, setError]                 = useState('')

  useEffect(() => {
    if (!isEdit) return
    let cancelled = false
    taskApi.getById(id)
      .then(t => {
        if (cancelled) return
        setWorkflowId(t.workflowId ?? '')
        setInitialIsPrivate(t.isPrivate)
        setInitialInputs(fromTask(t))
      })
      .catch(err => { if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load task') })
    return () => { cancelled = true }
  }, [id, isEdit])

  if (error)          return <Layout><div className="alert alert-error">{error}</div></Layout>
  if (!initialInputs) return <Layout><LoadingSpinner /></Layout>

  return <TaskForm initialInputs={initialInputs} workflowId={workflowId} taskId={id} initialIsPrivate={initialIsPrivate} />
}

function TaskForm({ initialInputs, workflowId, taskId, initialIsPrivate = false }: {
  initialInputs: TaskFormInputs
  workflowId: string
  taskId?: string
  initialIsPrivate?: boolean
}) {
  const isEdit = !!taskId
  const navigate = useNavigate()
  const backUrl = workflowId ? `/workflows/${workflowId}` : '/tasks'
  const [isPrivate, setIsPrivate] = useState(initialIsPrivate)

  const [state, dispatch] = useReducer(
    genericFormReducer<TaskFormInputs>,
    { tag: 'editing', inputs: initialInputs },
  )

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (state.tag !== 'editing') return
    dispatch({ type: 'submit' })
    try {
      const { name, type } = state.inputs
      const config = buildConfig(state.inputs)
      if (isEdit) {
        await taskApi.update(taskId, { name, type, config, isPrivate })
      } else {
        await taskApi.create({ name, type, workflowId: workflowId || undefined, config, isPrivate })
      }
      dispatch({ type: 'success' })
    } catch (err: unknown) {
      dispatch({ type: 'error', message: err instanceof Error ? err.message : 'Save failed' })
    }
  }

  if (state.tag === 'redirect') {
    return <Navigate to={backUrl} replace />
  }

  function field(name: keyof TaskFormInputs) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
      dispatch({ type: 'edit', name, value: e.target.value })
  }

  const { inputs } = state
  const isSubmitting = state.tag === 'submitting'

  return (
    <Layout>
      <PageHeader
        title={isEdit ? 'Edit Task' : 'New Task'}
        back={{ href: backUrl }}
      />

      <div className="form-card">
        {state.tag === 'editing' && state.error && (
          <div className="alert alert-error">{state.error}</div>
        )}

        <form onSubmit={handleSubmit} className="form">
          <fieldset disabled={isSubmitting} style={{ border: 'none', padding: 0, margin: 0 }}>

            <div className="form-group">
              <label>Task name</label>
              <input value={inputs.name} onChange={field('name')} placeholder="Fetch Data" required />
            </div>

            <div className="form-group">
              <label>Type</label>
              <select value={inputs.type} onChange={field('type')}>
                <option value="HTTP">HTTP</option>
                <option value="SCRIPT">SCRIPT</option>
              </select>
            </div>

            {inputs.type === TaskType.HTTP && <>
              <div className="form-group">
                <label>URL</label>
                <input value={inputs.url} onChange={field('url')} placeholder="https://example.com/api" required />
              </div>
              <div className="form-group">
                <label>Method</label>
                <select value={inputs.method} onChange={field('method')}>
                  {['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map(m => <option key={m}>{m}</option>)}
                </select>
              </div>
            </>}

            {inputs.type === TaskType.SCRIPT && <>
              <div className="form-group">
                <label>Command</label>
                <input value={inputs.command} onChange={field('command')} placeholder="ex: 'node'" required />
              </div>
              <div className="form-group">
                <label>File name</label>
                <input value={inputs.fileName} onChange={field('fileName')} placeholder="ex: 'index.js'" required />
              </div>
              <div className="form-group">
                <label>Directory <span className="text-muted">(optional)</span></label>
                <input value={inputs.directory} onChange={field('directory')} placeholder="ex: /home/user/scripts" />
              </div>
              <div className="form-group">
                <label>Extra args <span className="text-muted">(optional, space-separated)</span></label>
                <input value={inputs.args} onChange={field('args')} placeholder="ex: --env prod" />
              </div>
            </>}

            <div className="form-group">
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={isPrivate}
                  onChange={e => setIsPrivate(e.target.checked)}
                />
                Private (visible only to you and admins)
              </label>
            </div>

            <div className="form-actions">
              <button type="button" className="btn btn-ghost" onClick={() => navigate(backUrl)}>Cancel</button>
              <button type="submit" className="btn btn-primary">
                {isSubmitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create task'}
              </button>
            </div>
          </fieldset>
        </form>
      </div>
    </Layout>
  )
}
