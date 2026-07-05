import { Navigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import type { RoleType } from "../contexts/AuthContext";
import { LoadingSpinner } from "./LoadingSpinner";
import type { ReactNode } from "react";

interface Props {
	children: ReactNode;
	roles?: RoleType[];
}

export function ProtectedRoute({ children, roles }: Props) {
	const { user, loading } = useAuth();

	if (loading) return <LoadingSpinner />;
	if (!user) return <Navigate to="/login" replace />;
	if (roles && !roles.includes(user.role)) return <Navigate to="/dashboard" replace />;

	return <>{children}</>;
}
