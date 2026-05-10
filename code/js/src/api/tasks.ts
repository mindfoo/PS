import { api } from './client'
import type {TaskType} from '../pages/TaskFormPage.tsx'

export interface TaskResponse {
  id: string
  name: string
  type: TaskType
  config: Record<string, unknown>
  workflowId: string
}

/** Enriched task entry returned by GET /tasks?workflowId=... */
export interface WorkflowTaskEntry {
  taskId: string
  name: string
  type: TaskType
  config: Record<string, unknown>
  orderId: string | null   
  taskOrder: number        
  retryPolicy: number
  dependsOnTaskId: string | null
}

export const taskApi = {
  listAll: () => api.get<TaskResponse[]>('/tasks'),
  listByWorkflow: (workflowId: string) =>
    api.get<WorkflowTaskEntry[]>(`/tasks?workflowId=${workflowId}`),
  getById: (id: string) => api.get<TaskResponse>(`/tasks/${id}`),
  create: (body: { name: string; type: string; workflowId?: string; config?: Record<string, unknown> }) =>
    api.post<TaskResponse>('/tasks', body),
  update: (id: string, body: { name: string; type: string; config?: Record<string, unknown> }) =>
    api.put<TaskResponse>(`/tasks/${id}`, body),
  delete: (id: string) => api.delete<void>(`/tasks/${id}`),
  run: (id: string) => api.post<{ executionId: string; status: string }>(`/tasks/${id}/run`, {}),
}

