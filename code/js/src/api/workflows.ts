import { api } from "./client";
import type { ExecutionStatus } from "./executions";

export interface WorkflowResponse {
	id: string;
	name: string;
	ownerId: string;
	ownerUsername: string;
	lastRunStatus?: ExecutionStatus | null;
	isPrivate: boolean;
}

export interface TaskOrderItem {
	orderId: string;
	taskOrder: number;
}

export const workflowApi = {
	list: () => api.get<WorkflowResponse[]>("/workflows"),
	getById: (id: string) => api.get<WorkflowResponse>(`/workflows/${id}`),
	create: (body: { name: string; isPrivate?: boolean }) => api.post<WorkflowResponse>("/workflows", body),
	update: (id: string, body: { name: string; isPrivate?: boolean }) =>
		api.put<WorkflowResponse>(`/workflows/${id}`, body),
	delete: (id: string) => api.delete<void>(`/workflows/${id}`),
	run: (id: string) => api.post<{ executionId: string; status: ExecutionStatus }>(`/workflows/${id}/run`, {}),
	reorderTasks: (id: string, items: TaskOrderItem[]) => api.patch<void>(`/workflows/${id}/task-order`, { items }),
	updateRetryPolicy: (workflowId: string, taskId: string, retryPolicy: number) =>
		api.patch<void>(`/workflows/${workflowId}/tasks/${taskId}/retry-policy`, { retryPolicy }),
	linkTask: (workflowId: string, taskId: string) =>
		api.post<void>(`/workflows/${workflowId}/link-task/${taskId}`, {}),
	unlinkTask: (workflowId: string, taskId: string) =>
		api.delete<void>(`/workflows/${workflowId}/link-task/${taskId}`),
};
