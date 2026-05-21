import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ExecutionDetailPage } from '../../pages/ExecutionDetailPage'

const mockGetById = vi.fn()
const mockCancel  = vi.fn()

const sampleExecution = {
  id: 'exec-1234-abcd',
  triggeredType: 'MANUAL',
  type: 'WORKFLOW',
  status: 'SUCCESS',
  startedAt: '2026-05-01T09:00:00',
  finishedAt: '2026-05-01T09:05:00',
  triggeredBy: 'alice',
  retryCount: 0,
  output: null,
  taskExecutions: [
    {
      executionId: 'te1',
      taskId: 't1',
      taskName: 'Compile code',
      status: 'SUCCESS',
      startedAt: '2026-05-01T09:00:10',
      finishedAt: '2026-05-01T09:04:50',
      output: null,
    },
  ],
}

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', username: 'alice', role: 'WRITER' }, loading: false }),
  usePermissions: () => ({ canExecuteWorkflows: true, isReader: false }),
}))

vi.mock('../../api/executions', () => ({
  executionApi: {
    getById: (...args: unknown[]) => mockGetById(...args) as unknown,
    cancel:  (...args: unknown[]) => mockCancel(...args)  as unknown,
  },
}))

vi.mock('../../components/Layout', () => ({
  Layout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

describe('ExecutionDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetById.mockResolvedValue(sampleExecution)
  })

  function renderPage(executionId = 'exec-1234-abcd', workflowId = 'wf1') {
    return render(
      <MemoryRouter initialEntries={[`/workflows/${workflowId}/executions/${executionId}`]}>
        <Routes>
          <Route path="/workflows/:workflowId/executions/:executionId" element={<ExecutionDetailPage />} />
        </Routes>
      </MemoryRouter>
    )
  }

  it('renders execution status after load', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText(/SUCCESS/i).length).toBeGreaterThan(0)
    })
  })

  it('renders task executions table', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('Compile code')).toBeDefined()
    })
  })

  it('renders back link to workflow', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText(/back to workflow/i)).toBeDefined()
    })
  })

  it('shows error message when getById fails', async () => {
    mockGetById.mockRejectedValue(new Error('Not found'))
    renderPage()
    await waitFor(() => {
      expect(screen.getByText(/Not found/i)).toBeDefined()
    })
  })

  it('shows cancel button for active executions', async () => {
    mockGetById.mockResolvedValue({ ...sampleExecution, status: 'RUNNING' })
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /cancel execution/i })).toBeDefined()
    })
  })

  it('does not show cancel button for finished executions', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /cancel execution/i })).toBeNull()
    })
  })
})
