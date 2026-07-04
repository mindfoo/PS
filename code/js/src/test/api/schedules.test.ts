import { describe, it, expect, vi, afterEach } from "vitest";

const baseSched = {
	id: "s1",
	workflowId: "wf1",
	workflowName: "Pipeline",
	cronExpression: "0 30 9 * * *",
	timezone: "UTC",
	enabled: true,
	nextRunAt: "2026-06-01T09:30:00",
	lastRunAt: null,
	description: null,
};

function mockFetch(body: unknown, ok = true, status = 200) {
	vi.stubGlobal(
		"fetch",
		vi.fn().mockResolvedValue({
			ok,
			status,
			json: () => Promise.resolve(body),
		}),
	);
}

describe("api/schedules", () => {
	afterEach(() => vi.restoreAllMocks());

	it("list returns schedules array", async () => {
		mockFetch([baseSched]);
		const { scheduleApi } = await import("../../api/schedules");
		const result = await scheduleApi.list();
		expect(result).toHaveLength(1);
		expect(result[0].cronExpression).toBe("0 30 9 * * *");
	});

	it("create sends POST and returns created schedule", async () => {
		mockFetch(baseSched, true, 201);
		const { scheduleApi } = await import("../../api/schedules");
		const result = await scheduleApi.create({
			workflowId: "wf1",
			cronExpression: "0 30 9 * * *",
			timezone: "UTC",
		});
		expect(result.id).toBe("s1");
		expect(result.enabled).toBe(true);
	});

	it("update sends PUT and returns updated schedule", async () => {
		const updated = { ...baseSched, cronExpression: "0 0 10 * * *" };
		mockFetch(updated);
		const { scheduleApi } = await import("../../api/schedules");
		const result = await scheduleApi.update("s1", {
			cronExpression: "0 0 10 * * *",
			timezone: "UTC",
			enabled: true,
		});
		expect(result.cronExpression).toBe("0 0 10 * * *");
	});

	it("delete sends DELETE and resolves", async () => {
		mockFetch(undefined, true, 204);
		const { scheduleApi } = await import("../../api/schedules");
		await expect(scheduleApi.delete("s1")).resolves.toBeUndefined();
	});

	it("list throws on server error", async () => {
		mockFetch({ title: "Forbidden" }, false, 403);
		const { scheduleApi } = await import("../../api/schedules");
		await expect(scheduleApi.list()).rejects.toThrow();
	});

	it("create throws on 400 invalid cron", async () => {
		mockFetch({ title: "Invalid cron expression" }, false, 400);
		const { scheduleApi } = await import("../../api/schedules");
		await expect(
			scheduleApi.create({
				workflowId: "wf1",
				cronExpression: "INVALID",
			}),
		).rejects.toThrow();
	});
});
