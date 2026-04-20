import { api } from './client'

export interface WorkflowResponse {
  id: string
  name: string
  ownerId: string
  ownerUsername: string
}

export const workflowApi = {
  list: () => api.get<WorkflowResponse[]>('/workflows'),
  getById: (id: string) => api.get<WorkflowResponse>(`/workflows/${id}`),
  create: (body: { name: string }) => api.post<WorkflowResponse>('/workflows', body),
  update: (id: string, body: { name: string }) => api.put<WorkflowResponse>(`/workflows/${id}`, body),
  delete: (id: string) => api.delete<void>(`/workflows/${id}`),
  run: (id: string) => api.post<{ executionId: string; status: string }>(`/workflows/${id}/run`, {}),
}

