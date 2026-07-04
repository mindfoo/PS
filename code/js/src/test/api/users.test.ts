import { describe, it, expect, vi, afterEach } from "vitest";

const baseUser = { id: "1", username: "alice", role: "READER", permissions: ["workflow:read"] };
const baseRole = { name: "READER", permissions: ["workflow:read"] };

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

describe("api/users", () => {
	afterEach(() => vi.restoreAllMocks());

	it("list returns an array of users", async () => {
		mockFetch([baseUser]);
		const { usersApi } = await import("../../api/users");
		const result = await usersApi.list();
		expect(result).toHaveLength(1);
		expect(result[0].username).toBe("alice");
	});

	it("listRoles returns role list", async () => {
		mockFetch([baseRole]);
		const { usersApi } = await import("../../api/users");
		const result = await usersApi.listRoles();
		expect(result[0].name).toBe("READER");
	});

	it("updateRole sends PATCH and returns updated user", async () => {
		mockFetch({ ...baseUser, role: "WRITER", permissions: ["workflow:write"] });
		const { usersApi } = await import("../../api/users");
		const result = await usersApi.updateRole("1", "WRITER");
		expect(result.role).toBe("WRITER");
	});

	it("list throws when server returns error", async () => {
		mockFetch({ title: "Forbidden" }, false, 403);
		const { usersApi } = await import("../../api/users");
		await expect(usersApi.list()).rejects.toThrow();
	});
});
