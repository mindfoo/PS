import { api } from "./client";
import type { RoleType } from "../contexts/AuthContext.tsx";

export interface UserAuth {
	id: string;
	username: string;
	role: RoleType;
}

export interface LoginRequest {
	username: string;
	password: string;
}

export interface RegisterRequest extends LoginRequest {
	roleName?: RoleType;
}

export const authApi = {
	login: (body: LoginRequest) => api.post("/auth/login", body),
	register: (body: RegisterRequest) => api.post<UserAuth>("/auth/register", body),
	logout: () => api.post<void>("/auth/logout", {}),
	profile: () => api.get<UserAuth>("/auth/profile"),
};
