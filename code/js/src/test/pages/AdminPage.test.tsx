import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AdminPage } from '../../pages/AdminPage'

const mockListUsers     = vi.fn()
const mockListRoles     = vi.fn()
const mockUpdateRole    = vi.fn()
const mockListWorkflows = vi.fn()

const sampleUsers = [
  { id: 'u1', username: 'alice', role: 'ADMIN',  permissions: ['workflow:read', 'workflow:write'] },
  { id: 'u2', username: 'bob',   role: 'READER', permissions: ['workflow:read'] },
]

const sampleRoles = [
  { name: 'ADMIN',  permissions: ['workflow:read', 'workflow:write'] },
  { name: 'READER', permissions: ['workflow:read'] },
]

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u0', username: 'admin', role: 'ADMIN', permissions: [] },
    loading: false,
  }),
  usePermissions: () => ({ canReadWorkflows: true, canWriteWorkflows: true }),
}))

vi.mock('../../api/users', () => ({
  usersApi: {
    list:       (...args: unknown[]) => mockListUsers(...args) as unknown,
    listRoles:  (...args: unknown[]) => mockListRoles(...args) as unknown,
    updateRole: (...args: unknown[]) => mockUpdateRole(...args) as unknown,
  },
}))

vi.mock('../../api/workflows', () => ({
  workflowApi: { list: (...args: unknown[]) => mockListWorkflows(...args) as unknown },
}))

describe('AdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListUsers.mockResolvedValue(sampleUsers)
    mockListRoles.mockResolvedValue(sampleRoles)
    mockListWorkflows.mockResolvedValue([])
  })

  function renderPage() {
    return render(<MemoryRouter><AdminPage /></MemoryRouter>)
  }

  it('renders user count stat card', async () => {
    renderPage()
    await waitFor(() => {
      // stat card shows number of users
      expect(screen.getByText('2')).toBeInTheDocument()
    })
  })

  it('renders both usernames in the table', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByText('alice')).toBeInTheDocument()
      expect(screen.getByText('bob')).toBeInTheDocument()
    })
  })

  it('renders permission badges for each user', async () => {
    renderPage()
    await waitFor(() => {
      const badges = screen.getAllByText('workflow:read')
      expect(badges.length).toBeGreaterThanOrEqual(1)
    })
  })

  it('calls updateRole when a role dropdown changes', async () => {
    const updatedUser = { ...sampleUsers[1], role: 'ADMIN', permissions: sampleRoles[0].permissions }
    mockUpdateRole.mockResolvedValue(updatedUser)

    renderPage()
    await waitFor(() => screen.getByText('bob'))

    const selects = screen.getAllByRole('combobox')
    // selects[1] belongs to bob (second user row)
    await userEvent.selectOptions(selects[1], 'ADMIN')

    await waitFor(() => {
      expect(mockUpdateRole).toHaveBeenCalledWith('u2', 'ADMIN')
    })
  })

  it('shows error message when update fails', async () => {
    mockUpdateRole.mockRejectedValue(new Error('Update failed'))

    renderPage()
    await waitFor(() => screen.getByText('alice'))

    const selects = screen.getAllByRole('combobox')
    await userEvent.selectOptions(selects[0], 'READER')

    await waitFor(() => {
      expect(screen.getByText('Update failed')).toBeInTheDocument()
    })
  })
})
