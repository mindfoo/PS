import { api } from "./client";

type ExecutionType = "WORKFLOW" | "TASK";
export type ExecutionStatus = keyof typeof ExecutionStatusEnum;
export enum ExecutionStatusEnum {
	ERROR = "ERROR",
	SUCCESS = "SUCCESS",
	CANCELED = "CANCELED",
	RUNNING = "RUNNING",
	PENDING = "PENDING"
}
type ExecutionTriggerType = "MANUAL" | "CRON";

export interface TaskExecutionSummary {
	executionId: string;
	taskId: string | null;
	taskName: string | null;
	status: ExecutionStatus;
	startedAt: string;
	finishedAt: string | null;
	output: Record<string, unknown> | null;
}

export interface ExecutionSummaryResponse {
	id: string;
	triggeredType: ExecutionTriggerType;
	type: ExecutionType;
	status: ExecutionStatus;
	startedAt: string;
	finishedAt: string | null;
	triggeredBy: string;
	retryCount: number;
	output: Record<string, unknown> | null;
	taskExecutions: TaskExecutionSummary[] | null;
}

export interface ExecutionEvent {
	executionId: string;
	status: ExecutionStatus;
	taskStatuses: Record<string, string>;
	terminal: boolean;
}

export function isActiveExecutionStatus(status: ExecutionStatus): boolean {
	return status === ExecutionStatusEnum.RUNNING.valueOf() || status === ExecutionStatusEnum.PENDING.valueOf();
}

export const executionApi = {
	listByWorkflow: (workflowId: string) => api.get<ExecutionSummaryResponse[]>(`/workflows/${workflowId}/executions`),
	getById: (executionId: string) => api.get<ExecutionSummaryResponse>(`/executions/${executionId}`),
	cancel: (executionId: string) => api.post<void>(`/executions/${executionId}/cancel`, {}),

	/**
	 * Opens an SSE connection for live execution status updates.
	 * Returns an unsubscribe function that closes the EventSource.
	 */
	subscribeToExecution: (
		executionId: string,
		onEvent: (event: ExecutionEvent) => void,
		onError?: (err: Event) => void,
	): (() => void) => {
		const eventSource = new EventSource(`/api/executions/${executionId}/events`, {
			withCredentials: true,
		});

		eventSource.addEventListener("execution", (e: MessageEvent) => {
			try {
				onEvent(JSON.parse(e.data as string) as ExecutionEvent);
			} catch {
				// ignore malformed SSE event
			}
		});

		if (onError) eventSource.onerror = onError;
		return () => eventSource.close();
	},
};
