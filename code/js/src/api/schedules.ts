import { api } from "./client";

export interface ScheduleResponse {
	id: string;
	workflowId: string;
	workflowName: string;
	cronExpression: string;
	timezone: string;
	enabled: boolean;
	nextRunAt: string;
	lastRunAt: string | null;
	description: string | null;
}

export const scheduleApi = {
	list: () => api.get<ScheduleResponse[]>("/schedules"),
	create: (body: {
		workflowId: string;
		cronExpression: string;
		timezone?: string;
		enabled?: boolean;
		description?: string;
	}) => api.post<ScheduleResponse>("/schedules", body),
	update: (id: string, body: { cronExpression: string; timezone: string; enabled: boolean; description?: string }) =>
		api.put<ScheduleResponse>(`/schedules/${id}`, body),
	delete: (id: string) => api.delete<void>(`/schedules/${id}`),
};
