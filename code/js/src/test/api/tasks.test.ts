import { describe, it, expect, vi, afterEach } from "vitest";
import { TaskType } from "../../api/tasks";

const baseTask = { id: "t1", name: "Run script", type: TaskType.SCRIPT, config: {}, workflowId: null };

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

describe("api/tasks", () => {
	afterEach(() => vi.restoreAllMocks());

	it("list returns tasks array", async () => {
		mockFetch([baseTask]);
		const { taskApi } = await import("../../api/tasks");
		const result = await taskApi.listAll();
		expect(result).toHaveLength(1);
		expect(result[0].name).toBe("Run script");
	});

	it("create returns created task", async () => {
		mockFetch(baseTask, true, 201);
		const { taskApi } = await import("../../api/tasks");
		const result = await taskApi.create({ name: "Run script", type: TaskType.SCRIPT, config: {} });
		expect(result.type).toBe(TaskType.SCRIPT);
	});

	it("deleteById sends DELETE", async () => {
		mockFetch(undefined, true, 204);
		const { taskApi } = await import("../../api/tasks");
		await expect(taskApi.delete("t1")).resolves.toBeUndefined();
	});

	it("list throws on server error", async () => {
		mockFetch({ title: "Forbidden" }, false, 403);
		const { taskApi } = await import("../../api/tasks");
		await expect(taskApi.listAll()).rejects.toThrow();
	});
});
