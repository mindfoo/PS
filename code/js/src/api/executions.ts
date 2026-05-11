import { api } from './client'

type ExecutionType = 'WORKFLOW' | 'TASK'
export type ExecutionStatus = 'ERROR' | 'SUCCESS' | 'CANCELED' | 'RUNNING' | 'PENDING'
type ExecutionTriggerType = 'MANUAL' | 'CRON' | 'EVENT'

export interface TaskExecutionSummary {
  executionId: string
  taskId: string | null
  taskName: string | null
  status: ExecutionStatus
  startedAt: string
  finishedAt: string | null
  output: Record<string, unknown> | null
}

export interface ExecutionSummaryResponse {
  id: string
  triggeredType: ExecutionTriggerType
  type: ExecutionType
  status: ExecutionStatus
  startedAt: string
  finishedAt: string | null
  triggeredBy: string
  retryCount: number
  output: Record<string, unknown> | null
  taskExecutions: TaskExecutionSummary[] | null
}

export const executionApi = {
  listByWorkflow: (workflowId: string) =>
    api.get<ExecutionSummaryResponse[]>(`/workflows/${workflowId}/executions`),
  getById: (executionId: string) =>
    api.get<ExecutionSummaryResponse>(`/executions/${executionId}`),
  cancel: (executionId: string) =>
    api.post<void>(`/executions/${executionId}/cancel`, {}),
}
