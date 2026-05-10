import { useEffect, useRef, useState } from 'react'
import { useParams, Link, useNavigate } from 'react-router-dom'
import { DragDropContext, Droppable, Draggable, type DropResult } from '@hello-pangea/dnd'
import { workflowApi, type TaskOrderItem } from '../api/workflows'
import { taskApi, type WorkflowTaskEntry, type TaskResponse } from '../api/tasks'
import { executionApi, type ExecutionSummaryResponse, type TaskExecutionSummary } from '../api/executions'
import { usePermissions } from '../contexts/AuthContext'
import { Layout } from '../components/Layout'
import type { WorkflowResponse } from '../api/workflows'
import { TaskType } from './TaskFormPage.tsx'

function configSummary(type: string, config: Record<string, unknown>): string {
  switch (type) {
    case TaskType.HTTP: return `${config.method ?? 'GET'} ${config.url ?? ''}`
    case TaskType.SCRIPT: {
      const cmd  = config.command  ?? ''
      const file = config.fileName ?? ''
      const dir  = config.directory ? `(in ${config.directory}) ` : ''
      const args = Array.isArray(config.args) ? ' ' + (config.args as string[]).join(' ') : ''
      return `${dir}${cmd} ${file}${args}`.trim()
    }
    default: return JSON.stringify(config)
  }
}


function compactStages(entries: WorkflowTaskEntry[]): WorkflowTaskEntry[] {
  const uniqueStages = [...new Set(entries.map(e => e.taskOrder))].sort((a, b) => a - b)
  const stageMap = new Map(uniqueStages.map((s, i) => [s, i + 1]))
  return entries.map(e => ({ ...e, taskOrder: stageMap.get(e.taskOrder)! }))
}

const STAGE_COLOURS = ['#6366f1', '#22c55e', '#f59e0b', '#ef4444', '#06b6d4', '#a855f7']
function stageColour(stage: number): string {
  return STAGE_COLOURS[(stage - 1) % STAGE_COLOURS.length]
}

export function WorkflowDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const perms = usePermissions()

  const [workflow, setWorkflow] = useState<WorkflowResponse | null>(null)
  const [tasks, setTasks]       = useState<WorkflowTaskEntry[]>([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState('')
  const [saving, setSaving]     = useState(false)
  const [editingStage, setEditingStage] = useState<string | null>(null)
  const [stageInput, setStageInput]     = useState('')
  const [editingRetry, setEditingRetry] = useState<string | null>(null)
  const [retryInput, setRetryInput]     = useState('')

  // Execution history
  const [executions, setExecutions]         = useState<ExecutionSummaryResponse[]>([])
  const [execLoading, setExecLoading]       = useState(false)
  const [execOpen, setExecOpen]             = useState(true)
  const [expandedExec, setExpandedExec]     = useState<string | null>(null)

  // Per-task live status during an active run (taskId → status)
  const [taskStatuses, setTaskStatuses]     = useState<Record<string, string>>({})
  const pollingRef                          = useRef<ReturnType<typeof setInterval> | null>(null)

  // Link existing task picker
  const [showLinkPicker, setShowLinkPicker] = useState(false)
  const [allTasks, setAllTasks]             = useState<TaskResponse[]>([])
  const [linkLoading, setLinkLoading]       = useState(false)

  useEffect(() => {
    if (!id) return
    Promise.all([workflowApi.getById(id), taskApi.listByWorkflow(id)])
      .then(([w, t]) => { setWorkflow(w); setTasks(compactStages(t)) })
      .catch(err => setError(err instanceof Error ? err.message : 'Failed to load'))
      .finally(() => setLoading(false))
    loadExecutions()
  }, [id])


  async function persistOrder(updated: WorkflowTaskEntry[]) {
    if (!id) return
    const items: TaskOrderItem[] = updated
      .filter(t => t.orderId != null)
      .map(t => ({ orderId: t.orderId!, taskOrder: t.taskOrder }))
    if (items.length === 0) return
    setSaving(true)
    try { await workflowApi.reorderTasks(id, items) }
    catch (err) { alert(err instanceof Error ? err.message : 'Failed to save order') }
    finally { setSaving(false) }
  }

  // ── Drag-and-drop ─────────────────────────────────────────────────────────

  function onDragEnd(result: DropResult) {
    if (!result.destination) return
    const from = result.source.index
    const to   = result.destination.index
    if (from === to) return

    const reordered = Array.from(tasks)
    const [moved] = reordered.splice(from, 1)
    reordered.splice(to, 0, moved)

    const renumbered = reordered.map((t, i) => ({ ...t, taskOrder: i + 1 }))
    setTasks(renumbered)
    persistOrder(renumbered)
  }


  function beginEditStage(taskId: string, current: number) {
    setEditingStage(taskId)
    setStageInput(String(current))
  }

  function commitStageEdit(taskId: string) {
    const newStage = parseInt(stageInput, 10)
    if (isNaN(newStage) || newStage < 1) { setEditingStage(null); return }

    const updated = tasks.map(t => t.taskId === taskId ? { ...t, taskOrder: newStage } : t)
    // Sort by new stage, then compact and renormalise
    const sorted  = [...updated].sort((a, b) => a.taskOrder - b.taskOrder)
    const compact = compactStages(sorted)
    setTasks(compact)
    persistOrder(compact)
    setEditingStage(null)
  }

  function beginEditRetry(taskId: string, current: number) {
    setEditingRetry(taskId)
    setRetryInput(String(current))
  }

  async function commitRetryEdit(taskId: string) {
    const value = parseInt(retryInput, 10)
    if (isNaN(value) || value < 0) { setEditingRetry(null); return }
    if (!id) { setEditingRetry(null); return }
    try {
      await workflowApi.updateRetryPolicy(id, taskId, value)
      setTasks(prev => prev.map(t => t.taskId === taskId ? { ...t, retryPolicy: value } : t))
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to update retry policy')
    } finally {
      setEditingRetry(null)
    }
  }

  // ── Task delete ───────────────────────────────────────────────────────────

  async function handleDeleteTask(taskId: string) {
    if (!confirm('Delete this task?')) return
    try {
      await taskApi.delete(taskId)
      const updated = compactStages(tasks.filter(t => t.taskId !== taskId))
      setTasks(updated)
      persistOrder(updated)
    } catch (err) { alert(err instanceof Error ? err.message : 'Delete failed') }
  }

  // ── Task run ──────────────────────────────────────────────────────────────

  async function handleRunTask(taskId: string) {
    try {
      const res = await taskApi.run(taskId)
      // Mark this task as RUNNING immediately for visual feedback
      setTaskStatuses(prev => ({ ...prev, [taskId]: 'RUNNING' }))
      // Show new execution entry immediately
      loadExecutions()
      // Poll the single-task execution until it finishes
      const poll = setInterval(async () => {
        try {
          const exec = await executionApi.getById(res.executionId)
          setTaskStatuses(prev => ({ ...prev, [taskId]: exec.status }))
          if (exec.status === 'SUCCESS' || exec.status === 'ERROR') {
            clearInterval(poll)
            loadExecutions()
          }
        } catch { clearInterval(poll) }
      }, 1500)
    } catch (err) { alert(err instanceof Error ? err.message : 'Run failed') }
  }

  // ── Workflow run with polling ─────────────────────────────────────────────

  function stopPolling() {
    if (pollingRef.current) { clearInterval(pollingRef.current); pollingRef.current = null }
  }

  function startPolling(executionId: string) {
    stopPolling()
    // Show the new execution entry immediately
    loadExecutions()
    pollingRef.current = setInterval(async () => {
      try {
        const exec = await executionApi.getById(executionId)
        // Update per-task statuses from child executions
        if (exec.taskExecutions) {
          const map: Record<string, string> = {}
          exec.taskExecutions.forEach(te => { if (te.taskId) map[te.taskId] = te.status })
          setTaskStatuses(map)
        }
        if (exec.status === 'SUCCESS' || exec.status === 'ERROR' || exec.status === 'CANCELED') {
          stopPolling()
          loadExecutions()
        }
      } catch { stopPolling() }
    }, 1500)
  }

  // Clean up polling on unmount
  useEffect(() => () => stopPolling(), [])

  // ── Execution cancel / rerun ──────────────────────────────────────────────

  async function handleCancelExecution(executionId: string) {
    try {
      await executionApi.cancel(executionId)
      stopPolling()
      setTaskStatuses({})
      loadExecutions()
    } catch (err) { alert(err instanceof Error ? err.message : 'Cancel failed') }
  }

  async function handleRerunWorkflow() {
    if (!id) return
    try {
      const pending: Record<string, string> = {}
      tasks.forEach(t => { if (t.taskId) pending[t.taskId] = 'PENDING' })
      setTaskStatuses(pending)
      const res = await workflowApi.run(id)
      startPolling(res.executionId)
    } catch (err) { alert(err instanceof Error ? err.message : 'Rerun failed') }
  }

  async function handleRerunTask(taskId: string) {
    try {
      const res = await taskApi.run(taskId)
      setTaskStatuses(prev => ({ ...prev, [taskId]: 'RUNNING' }))
      loadExecutions()
      const poll = setInterval(async () => {
        try {
          const exec = await executionApi.getById(res.executionId)
          setTaskStatuses(prev => ({ ...prev, [taskId]: exec.status }))
          if (exec.status === 'SUCCESS' || exec.status === 'ERROR' || exec.status === 'CANCELED') {
            clearInterval(poll)
            loadExecutions()
          }
        } catch { clearInterval(poll) }
      }, 1500)
    } catch (err) { alert(err instanceof Error ? err.message : 'Rerun failed') }
  }

  // ── Execution history ─────────────────────────────────────────────────────

  async function loadExecutions() {
    if (!id) return
    setExecLoading(true)
    try {
      const data = await executionApi.listByWorkflow(id)
      setExecutions(data)
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to load executions')
    } finally {
      setExecLoading(false)
    }
  }

  function toggleExecutions() {
    if (!execOpen) loadExecutions()
    setExecOpen(o => !o)
  }

  // ── Link existing task picker ─────────────────────────────────────────────

  async function openLinkPicker() {
    setLinkLoading(true)
    setShowLinkPicker(true)
    try {
      const all = await taskApi.listAll()
      const linkedIds = new Set(tasks.map(t => t.taskId))
      setAllTasks(all.filter(t => !linkedIds.has(t.id)))
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Failed to load tasks')
      setShowLinkPicker(false)
    } finally {
      setLinkLoading(false)
    }
  }

  async function handleLinkTask(taskId: string) {
    if (!id) return
    try {
      await workflowApi.linkTask(id, taskId)
      // Reload the task list to get the new WorkflowTaskEntry with orderId
      const updated = compactStages(await taskApi.listByWorkflow(id))
      setTasks(updated)
      setShowLinkPicker(false)
    } catch (err) { alert(err instanceof Error ? err.message : 'Link failed') }
  }

  async function handleUnlinkTask(taskId: string) {
    if (!id || !confirm('Remove this task from the workflow? The task itself will not be deleted.')) return
    try {
      await workflowApi.unlinkTask(id, taskId)
      const updated = compactStages(tasks.filter(t => t.taskId !== taskId))
      setTasks(updated)
      if (updated.length > 0) persistOrder(updated)
    } catch (err) { alert(err instanceof Error ? err.message : 'Unlink failed') }
  }

  // ── Render ────────────────────────────────────────────────────────────────

  if (loading) return <Layout><div className="loading">Loading…</div></Layout>
  if (error)   return <Layout><div className="alert alert-error">{error}</div></Layout>
  if (!workflow) return <Layout><div>Not found</div></Layout>

  // Group tasks by stage for visual parallel indicators
  const stageCounts = tasks.reduce<Record<number, number>>((acc, t) => {
    acc[t.taskOrder] = (acc[t.taskOrder] ?? 0) + 1
    return acc
  }, {})

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
                // Reset all task statuses to PENDING immediately
                const pending: Record<string, string> = {}
                tasks.forEach(t => { if (t.taskId) pending[t.taskId] = 'PENDING' })
                setTaskStatuses(pending)
                const res = await workflowApi.run(id!)
                startPolling(res.executionId)
              } catch (err) { alert(err instanceof Error ? err.message : 'Run failed') }
            }}>▶ Run workflow</button>
          )}
        </div>
      </div>

      <div className="section-header">
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <h2>Tasks ({tasks.length})</h2>
          {saving && <span className="text-muted" style={{ fontSize: '0.8rem' }}>Saving…</span>}
        </div>
        {perms.canWriteTasks && (
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button className="btn btn-secondary" onClick={openLinkPicker}>Link existing</button>
            <button className="btn btn-primary" onClick={() => navigate(`/workflows/${id}/tasks/new`)}>+ New Task</button>
          </div>
        )}
      </div>

      {perms.canWriteTasks && tasks.length > 1 && (
        <p className="text-muted" style={{ fontSize: '0.8rem', marginBottom: '0.75rem' }}>
          Drag rows to reorder. Click a stage number to set it — tasks sharing the same stage run <strong>in parallel</strong>.
        </p>
      )}

      {tasks.length === 0 ? (
        <div className="empty-state">
          <p>No tasks yet.</p>
          {perms.canWriteTasks && (
            <button className="btn btn-primary" onClick={() => navigate(`/workflows/${id}/tasks/new`)}>Add first task</button>
          )}
        </div>
      ) : (
        <div className="table-wrapper">
          <DragDropContext onDragEnd={onDragEnd}>
            <table className="table">
              <thead>
                <tr>
                  {perms.canWriteTasks && <th style={{ width: '1.5rem' }}></th>}
                  <th>Stage</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Config</th>
                  <th>Retries</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <Droppable droppableId="tasks">
                {(provided) => (
                  <tbody ref={provided.innerRef} {...provided.droppableProps}>
                    {tasks.map((t, index) => {
                      const isParallel = stageCounts[t.taskOrder] > 1
                      const colour = stageColour(t.taskOrder)
                      return (
                        <Draggable
                          key={t.taskId}
                          draggableId={t.taskId}
                          index={index}
                          isDragDisabled={!perms.canWriteTasks}
                        >
                          {(drag, snapshot) => (
                            <tr
                              ref={drag.innerRef}
                              {...drag.draggableProps}
                              style={{
                                ...drag.draggableProps.style,
                                // Restore table-row layout when lifted out of <table> context
                                display: snapshot.isDragging ? 'table' : undefined,
                                background: snapshot.isDragging ? '#22263a' : undefined,
                                borderLeft: isParallel ? `3px solid ${colour}` : undefined,
                              }}
                            >
                              {perms.canWriteTasks && (
                                <td {...drag.dragHandleProps} style={{ cursor: 'grab', color: '#8892a4', userSelect: 'none' }} title="Drag to reorder">⠿</td>
                              )}
                              <td>
                                {perms.canWriteTasks ? (
                                  editingStage === t.taskId ? (
                                    <input
                                      className="stage-input"
                                      type="number"
                                      min={1}
                                      value={stageInput}
                                      autoFocus
                                      onChange={e => setStageInput(e.target.value)}
                                      onBlur={() => commitStageEdit(t.taskId)}
                                      onKeyDown={e => { if (e.key === 'Enter') commitStageEdit(t.taskId); if (e.key === 'Escape') setEditingStage(null) }}
                                      style={{ width: '3rem' }}
                                    />
                                  ) : (
                                    <span
                                      className="stage-badge"
                                      style={{ background: colour, cursor: 'pointer' }}
                                      title={isParallel ? `Parallel group — stage ${t.taskOrder}` : `Stage ${t.taskOrder} — click to change`}
                                      onClick={() => beginEditStage(t.taskId, t.taskOrder)}
                                    >
                                      {t.taskOrder}{isParallel && ' ∥'}
                                    </span>
                                  )
                                ) : (
                                  <span className="stage-badge" style={{ background: colour }}>
                                    {t.taskOrder}{isParallel && ' ∥'}
                                  </span>
                                )}
                              </td>
                              <td>{t.name}</td>
                              <td><span className="badge">{t.type}</span></td>
                              <td><code className="config-preview">{configSummary(t.type, t.config)}</code></td>
                              <td>
                                {perms.canWriteTasks ? (
                                  editingRetry === t.taskId ? (
                                    <input
                                      className="stage-input"
                                      type="number"
                                      min={0}
                                      value={retryInput}
                                      autoFocus
                                      onChange={e => setRetryInput(e.target.value)}
                                      onBlur={() => commitRetryEdit(t.taskId)}
                                      onKeyDown={e => { if (e.key === 'Enter') commitRetryEdit(t.taskId); if (e.key === 'Escape') setEditingRetry(null) }}
                                      style={{ width: '3rem' }}
                                    />
                                  ) : (
                                    <span
                                      className="retry-badge"
                                      title="Click to edit retry count"
                                      onClick={() => beginEditRetry(t.taskId, t.retryPolicy)}
                                      style={{ cursor: 'pointer' }}
                                    >
                                      {t.retryPolicy}
                                    </span>
                                  )
                                ) : (
                                  <span className="retry-badge">{t.retryPolicy}</span>
                                )}
                              </td>
                              <td>
                                {t.taskId && taskStatuses[t.taskId]
                                  ? <span className={`badge badge-status-${taskStatuses[t.taskId].toLowerCase()}`}>{taskStatuses[t.taskId]}</span>
                                  : <span className="badge badge-muted">—</span>}
                              </td>
                              <td className="actions-cell">
                                {perms.canExecuteWorkflows && (
                                  <button className="btn btn-sm btn-success" onClick={() => handleRunTask(t.taskId)} title="Run this task now">▶</button>
                                )}
                                {perms.canWriteTasks && (
                                  <Link to={`/tasks/${t.taskId}/edit?workflowId=${id}`} className="btn btn-sm btn-secondary">Edit</Link>
                                )}
                                {perms.canWriteTasks && (
                                  <button className="btn btn-sm btn-secondary" onClick={() => handleUnlinkTask(t.taskId)} title="Remove from this workflow">Unlink</button>
                                )}
                                {perms.canDeleteTasks && (
                                  <button className="btn btn-sm btn-danger" onClick={() => handleDeleteTask(t.taskId)}>Delete</button>
                                )}
                              </td>
                            </tr>
                          )}
                        </Draggable>
                      )
                    })}
                    {provided.placeholder}
                  </tbody>
                )}
              </Droppable>
            </table>
          </DragDropContext>
        </div>
      )}

      {/* ── Link existing task picker ───────────────────────────────────── */}
      {showLinkPicker && (
        <div className="modal-backdrop" onClick={() => setShowLinkPicker(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Link existing task</h3>
              <button className="btn btn-ghost btn-sm" onClick={() => setShowLinkPicker(false)}>✕</button>
            </div>
            {linkLoading ? (
              <div className="loading">Loading tasks…</div>
            ) : allTasks.length === 0 ? (
              <p className="text-muted">No available tasks to link. <Link to="/tasks/new">Create one</Link> first.</p>
            ) : (
              <table className="table">
                <thead><tr><th>Name</th><th>Type</th><th></th></tr></thead>
                <tbody>
                  {allTasks.map(t => (
                    <tr key={t.id}>
                      <td>{t.name}</td>
                      <td><span className="badge">{t.type}</span></td>
                      <td>
                        <button className="btn btn-sm btn-primary" onClick={() => handleLinkTask(t.id!)}>Link</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {/* ── Execution history ───────────────────────────────────────────── */}
      <div className="section-header" style={{ marginTop: '2rem' }}>
        <h2>Execution History</h2>
        <button className="btn btn-secondary btn-sm" onClick={toggleExecutions}>
          {execOpen ? 'Hide' : 'Show'}
        </button>
      </div>

      {execOpen && (
        execLoading ? <div className="loading">Loading…</div> :
        executions.length === 0 ? (
          <div className="empty-state"><p>No executions yet.</p></div>
        ) : (
          <div className="table-wrapper">
            <table className="table">
              <thead>
                <tr><th>Status</th><th>Trigger</th><th>Started</th><th>Finished</th><th>By</th><th>Retries</th><th>Actions</th><th></th></tr>
              </thead>
              <tbody>
                {executions.map(ex => (
                  <>
                    <tr key={ex.id} style={{ cursor: 'pointer' }} onClick={() => setExpandedExec(expandedExec === ex.id ? null : ex.id)}>
                      <td><span className={`badge badge-status-${ex.status.toLowerCase()}`}>{ex.status}</span></td>
                      <td><span className="badge">{ex.triggeredType}</span></td>
                      <td style={{ fontSize: '0.82rem' }}>{new Date(ex.startedAt).toLocaleString()}</td>
                      <td style={{ fontSize: '0.82rem' }}>{ex.finishedAt ? new Date(ex.finishedAt).toLocaleString() : '—'}</td>
                      <td>{ex.triggeredBy}</td>
                      <td>{ex.retryCount}</td>
                      <td className="actions-cell" onClick={e => e.stopPropagation()}>
                        {(ex.status === 'PENDING' || ex.status === 'RUNNING') && perms.canExecuteWorkflows && (
                          <button className="btn btn-sm btn-danger" onClick={() => handleCancelExecution(ex.id)}>Cancel</button>
                        )}
                        {(ex.status === 'SUCCESS' || ex.status === 'ERROR' || ex.status === 'CANCELED') && perms.canExecuteWorkflows && (
                          ex.type === 'WORKFLOW'
                            ? <button className="btn btn-sm btn-secondary" onClick={handleRerunWorkflow}>Rerun</button>
                            : ex.taskExecutions?.[0]?.taskId
                              ? <button className="btn btn-sm btn-secondary" onClick={() => handleRerunTask(ex.taskExecutions![0].taskId!)}>Rerun</button>
                              : null
                        )}
                      </td>
                      <td style={{ color: '#8892a4' }}>{expandedExec === ex.id ? '▲' : '▼'}</td>
                    </tr>
                    {expandedExec === ex.id && (
                      <tr key={`${ex.id}-output`}>
                        <td colSpan={8} style={{ background: '#0f1117', padding: '0.75rem 1rem' }}>
                          {ex.taskExecutions && ex.taskExecutions.length > 0 ? (
                            <table className="table" style={{ fontSize: '0.8rem', margin: 0 }}>
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
                                {ex.taskExecutions.map((te: TaskExecutionSummary) => (
                                  <tr key={te.executionId}>
                                    <td>{te.taskName ?? te.taskId ?? '—'}</td>
                                    <td><span className={`badge badge-status-${te.status.toLowerCase()}`}>{te.status}</span></td>
                                    <td style={{ fontSize: '0.75rem' }}>{new Date(te.startedAt).toLocaleTimeString()}</td>
                                    <td style={{ fontSize: '0.75rem' }}>{te.finishedAt ? new Date(te.finishedAt).toLocaleTimeString() : '—'}</td>
                                    <td>
                                      {te.output
                                        ? <pre style={{ margin: 0, fontSize: '0.72rem', color: '#e2e8f0', whiteSpace: 'pre-wrap', wordBreak: 'break-word', maxHeight: '6rem', overflow: 'auto' }}>{JSON.stringify(te.output, null, 2)}</pre>
                                        : '—'}
                                    </td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          ) : ex.output ? (
                            <pre style={{ margin: 0, fontSize: '0.78rem', color: '#e2e8f0', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                              {JSON.stringify(ex.output, null, 2)}
                            </pre>
                          ) : <span className="text-muted">No output.</span>}
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </Layout>
  )
}

