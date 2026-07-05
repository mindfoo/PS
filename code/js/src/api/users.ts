import { api } from "./client";
import type { RoleType } from "../contexts/AuthContext.tsx";

export interface UserResponse {
	id: string;
	username: string;
	role: RoleType;
	permissions: string[];
}

export interface RoleSummary {
	name: RoleType;
	permissions: string[];
}

export const usersApi = {
	list: () => api.get<UserResponse[]>("/users"),
	listRoles: () => api.get<RoleSummary[]>("/users/roles"),
	updateRole: (userId: string, roleName: RoleType) => api.patch<UserResponse>(`/users/${userId}/role`, { roleName }),
};
