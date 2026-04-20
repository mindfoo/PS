import { api } from './client'

export interface TaskResponse {
  id: string
  name: string
  type: string
  config: Record<string, unknown>
  workflowId: string
}

export const taskApi = {
  listByWorkflow: (workflowId: string) =>
    api.get<TaskResponse[]>(`/tasks?workflowId=${workflowId}`),
  getById: (id: string) => api.get<TaskResponse>(`/tasks/${id}`),
  create: (body: { name: string; type: string; workflowId: string; config?: Record<string, unknown> }) =>
    api.post<TaskResponse>('/tasks', body),
  update: (id: string, body: { name: string; type: string; config?: Record<string, unknown> }) =>
    api.put<TaskResponse>(`/tasks/${id}`, body),
  delete: (id: string) => api.delete<void>(`/tasks/${id}`),
}

