import { describe, it, expect, vi, afterEach } from "vitest";
import { RoleType } from "../../contexts/AuthContext";

const baseUser = { id: "1", username: "alice", role: RoleType.READER, permissions: ["workflow:read"] };
const baseRole = { name: RoleType.READER, permissions: ["workflow:read"] };

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
		expect(result[0].name).toBe(RoleType.READER);
	});

	it("updateRole sends PATCH and returns updated user", async () => {
		mockFetch({ ...baseUser, role: RoleType.WRITER, permissions: ["workflow:write"] });
		const { usersApi } = await import("../../api/users");
		const result = await usersApi.updateRole("1", RoleType.WRITER);
		expect(result.role).toBe(RoleType.WRITER);
	});

	it("list throws when server returns error", async () => {
		mockFetch({ title: "Forbidden" }, false, 403);
		const { usersApi } = await import("../../api/users");
		await expect(usersApi.list()).rejects.toThrow();
	});
});
