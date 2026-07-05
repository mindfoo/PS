import { api } from "./client";
import type { ExecutionStatus } from "./executions";

export enum TaskType {
	HTTP = "HTTP",
	SCRIPT = "SCRIPT",
}

export interface TaskResponse {
	id: string;
	name: string;
	type: TaskType;
	config: Record<string, unknown>;
	workflowId: string;
	isPrivate: boolean;
}

export interface WorkflowTaskEntry {
	taskId: string;
	name: string;
	type: TaskType;
	config: Record<string, unknown>;
	orderId: string | null;
	taskOrder: number;
	retryPolicy: number;
	dependsOnTaskId: string | null;
	isPrivate: boolean;
}

export const taskApi = {
	listAll: () => api.get<TaskResponse[]>("/tasks"),
	listByWorkflow: (workflowId: string) => api.get<WorkflowTaskEntry[]>(`/tasks?workflowId=${workflowId}`),
	getById: (id: string) => api.get<TaskResponse>(`/tasks/${id}`),
	create: (body: {
		name: string;
		type: TaskType;
		workflowId?: string;
		config?: Record<string, unknown>;
		isPrivate?: boolean;
	}) => api.post<TaskResponse>("/tasks", body),
	update: (
		id: string,
		body: { name: string; type: TaskType; config?: Record<string, unknown>; isPrivate?: boolean },
	) => api.put<TaskResponse>(`/tasks/${id}`, body),
	delete: (id: string) => api.delete<void>(`/tasks/${id}`),
	run: (id: string) => api.post<{ executionId: string; status: ExecutionStatus }>(`/tasks/${id}/run`, {}),
	listAvailableScripts: () => api.get<string[]>("/tasks/scripts"),
};
