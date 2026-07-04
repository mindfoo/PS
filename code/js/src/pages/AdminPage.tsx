import { useEffect, useMemo, useState } from "react";
import { usersApi, type RoleSummary, type UserResponse } from "../api/users";
import { workflowApi, type WorkflowResponse } from "../api/workflows";
import { Layout } from "../components/Layout";
import { PageHeader } from "../components/PageHeader";
import { LoadingSpinner } from "../components/LoadingSpinner";

export function AdminPage() {
	const [users, setUsers] = useState<UserResponse[]>([]);
	const [roles, setRoles] = useState<RoleSummary[]>([]);
	const [workflows, setWorkflows] = useState<WorkflowResponse[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [tab, setTab] = useState<"users" | "workflows">("users");
	const [savingUserId, setSavingUserId] = useState<string | null>(null);

	const rolePermissions = useMemo(() => new Map(roles.map((r) => [r.name, r.permissions] as const)), [roles]);

	useEffect(() => {
		let cancelled = false;
		Promise.all([
			usersApi.list().catch(() => []),
			usersApi.listRoles().catch(() => []),
			workflowApi.list().catch(() => []),
		])
			.then(([u, r, w]) => {
				if (cancelled) return;
				setUsers(Array.isArray(u) ? u : []);
				setRoles(Array.isArray(r) ? r : []);
				setWorkflows(Array.isArray(w) ? w : []);
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load");
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, []);

	async function handleRoleChange(userId: string, roleName: string) {
		setError("");
		setSavingUserId(userId);
		try {
			const updated = await usersApi.updateRole(userId, roleName);
			setUsers((prev) => prev.map((u) => (u.id === userId ? updated : u)));
		} catch (err) {
			setError(err instanceof Error ? err.message : "Failed to update role");
		} finally {
			setSavingUserId(null);
		}
	}

	return (
		<Layout>
			<PageHeader title="🛡 Admin Panel" />

			{error && <div className="alert alert-error">{error}</div>}

			<div className="stats-row">
				<div className="stat-card">
					<div className="stat-value">{users.length}</div>
					<div className="stat-label">Users</div>
				</div>
				<div className="stat-card">
					<div className="stat-value">{workflows.length}</div>
					<div className="stat-label">Workflows</div>
				</div>
			</div>

			<div className="tabs">
				<button className={`tab ${tab === "users" ? "active" : ""}`} onClick={() => setTab("users")}>
					Users
				</button>
				<button className={`tab ${tab === "workflows" ? "active" : ""}`} onClick={() => setTab("workflows")}>
					All Workflows
				</button>
			</div>

			{loading ? (
				<LoadingSpinner />
			) : (
				<>
					{tab === "users" && (
						<div className="table-wrapper">
							<table className="table">
								<thead>
									<tr>
										<th>Username</th>
										<th>Role</th>
										<th>Permissions</th>
									</tr>
								</thead>
								<tbody>
									{users.length === 0 ? (
										<tr>
											<td colSpan={3} className="text-muted">
												No users returned
											</td>
										</tr>
									) : (
										users.map((u) => (
											<tr key={u.id}>
												<td>{u.username}</td>
												<td>
													<div className="form-group form-group--inline">
														<select
															value={u.role}
															disabled={savingUserId === u.id}
															onChange={(e) =>
																void handleRoleChange(u.id, e.target.value)
															}
														>
															{roles.map((r) => (
																<option key={r.name} value={r.name}>
																	{r.name}
																</option>
															))}
														</select>
													</div>
												</td>
												<td className="permissions-cell">
													{(u.permissions?.length
														? u.permissions
														: (rolePermissions.get(u.role) ?? [])
													).map((p) => (
														<span key={p} className="badge">
															{p}
														</span>
													))}
												</td>
											</tr>
										))
									)}
								</tbody>
							</table>
						</div>
					)}

					{tab === "workflows" && (
						<div className="table-wrapper">
							<table className="table">
								<thead>
									<tr>
										<th>Name</th>
										<th>Owner</th>
									</tr>
								</thead>
								<tbody>
									{workflows.map((w) => (
										<tr key={w.id}>
											<td>{w.name}</td>
											<td>{w.ownerUsername}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					)}
				</>
			)}
		</Layout>
	);
}
