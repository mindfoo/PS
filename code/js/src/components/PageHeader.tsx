import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

interface PageHeaderProps {
  title: string
  subtitle?: string
  back?: { href: string; label?: string }
  actions?: ReactNode
}

export function PageHeader({ title, subtitle, back, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        {back && (
          <Link to={back.href} className="back-link">
            {back.label ?? '← Back'}
          </Link>
        )}
        <h1>{title}</h1>
        {subtitle && <p className="text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="header-actions">{actions}</div>}
    </div>
  )
}
