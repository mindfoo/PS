import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { PageHeader } from '../../components/PageHeader'

function renderHeader(props: Parameters<typeof PageHeader>[0]) {
  return render(<MemoryRouter><PageHeader {...props} /></MemoryRouter>)
}

describe('PageHeader', () => {
  it('renders the title', () => {
    renderHeader({ title: 'My Page' })
    expect(screen.getByRole('heading', { name: 'My Page' })).toBeInTheDocument()
  })

  it('renders subtitle when provided', () => {
    renderHeader({ title: 'Tasks', subtitle: 'All reusable tasks' })
    expect(screen.getByText('All reusable tasks')).toBeInTheDocument()
  })

  it('omits subtitle when not provided', () => {
    renderHeader({ title: 'Tasks' })
    expect(screen.queryByRole('paragraph')).not.toBeInTheDocument()
  })

  it('renders back link with default label', () => {
    renderHeader({ title: 'New Task', back: { href: '/tasks' } })
    expect(screen.getByRole('link', { name: '← Back' })).toHaveAttribute('href', '/tasks')
  })

  it('renders back link with custom label', () => {
    renderHeader({ title: 'Edit', back: { href: '/dashboard', label: '← Workflows' } })
    expect(screen.getByRole('link', { name: '← Workflows' })).toBeInTheDocument()
  })

  it('renders actions when provided', () => {
    renderHeader({ title: 'Workflows', actions: <button>+ New</button> })
    expect(screen.getByRole('button', { name: '+ New' })).toBeInTheDocument()
  })

  it('omits actions div when no actions provided', () => {
    const { container } = renderHeader({ title: 'Workflows' })
    expect(container.querySelector('.header-actions')).not.toBeInTheDocument()
  })
})
