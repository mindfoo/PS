import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './contexts/AuthContext'
import { ProtectedRoute } from './components/ProtectedRoute'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'
import { WorkflowDetailPage } from './pages/WorkflowDetailPage'
import { WorkflowFormPage } from './pages/WorkflowFormPage'
import { TaskFormPage } from './pages/TaskFormPage'
import { TasksPage } from './pages/TasksPage'
import { SchedulesPage } from './pages/SchedulesPage'
import { ProfilePage } from './pages/ProfilePage'
import { AdminPage } from './pages/AdminPage'
import { ExecutionDetailPage } from './pages/ExecutionDetailPage'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public */}
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Protected — all roles */}
          <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
          <Route path="/tasks" element={<ProtectedRoute><TasksPage /></ProtectedRoute>} />
          <Route path="/tasks/new" element={<ProtectedRoute roles={['ADMIN','WRITER']}><TaskFormPage /></ProtectedRoute>} />
          <Route path="/tasks/:id/edit" element={<ProtectedRoute roles={['ADMIN','WRITER']}><TaskFormPage /></ProtectedRoute>} />
          <Route path="/workflows/new" element={<ProtectedRoute roles={['ADMIN','WRITER']}><WorkflowFormPage /></ProtectedRoute>} />
          <Route path="/workflows/:id" element={<ProtectedRoute><WorkflowDetailPage /></ProtectedRoute>} />
          <Route path="/workflows/:id/edit" element={<ProtectedRoute roles={['ADMIN','WRITER']}><WorkflowFormPage /></ProtectedRoute>} />
          <Route path="/workflows/:workflowId/executions/:executionId" element={<ProtectedRoute><ExecutionDetailPage /></ProtectedRoute>} />
          <Route path="/workflows/:workflowId/tasks/new" element={<ProtectedRoute roles={['ADMIN','WRITER']}><TaskFormPage /></ProtectedRoute>} />
          <Route path="/schedules" element={<ProtectedRoute><SchedulesPage /></ProtectedRoute>} />
          <Route path="/profile" element={<ProtectedRoute><ProfilePage /></ProtectedRoute>} />

          {/* Admin only */}
          <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminPage /></ProtectedRoute>} />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

