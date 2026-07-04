import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

/** api/client */

describe("api/client", () => {
	let fetchMock: ReturnType<typeof vi.fn>;

	beforeEach(() => {
		fetchMock = vi.fn();
		vi.stubGlobal("fetch", fetchMock);
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	it("GET request returns parsed JSON on 200", async () => {
		fetchMock.mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve({ id: "1" }) });
		const { api } = await import("../../api/client");
		const result = await api.get<{ id: string }>("/test");
		expect(result.id).toBe("1");
	});

	it("returns undefined on 204", async () => {
		fetchMock.mockResolvedValue({ ok: true, status: 204, json: () => Promise.reject(new Error()) });
		const { api } = await import("../../api/client");
		const result = await api.delete("/test");
		expect(result).toBeUndefined();
	});

	it("throws on non-ok response", async () => {
		fetchMock.mockResolvedValue({
			ok: false,
			status: 404,
			json: () => Promise.resolve({ title: "Not found" }),
		});
		const { api } = await import("../../api/client");
		await expect(api.get("/missing")).rejects.toThrow();
	});
});
