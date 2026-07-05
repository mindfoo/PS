import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { taskApi, type TaskResponse } from "../api/tasks";
import { usePermissions } from "../contexts/AuthContext";
import { Layout } from "../components/Layout";
import { PageHeader } from "../components/PageHeader";
import { EmptyState } from "../components/EmptyState";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { configSummary } from "../utils/task";

export function TasksPage() {
	const navigate = useNavigate();
	const perms = usePermissions();

	const [tasks, setTasks] = useState<TaskResponse[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");

	useEffect(() => {
		let cancelled = false;
		taskApi
			.listAll()
			.then((data) => {
				if (!cancelled) setTasks(data);
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load tasks");
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, []);

	async function handleDelete(id: string) {
		if (!confirm("Delete this task? This cannot be undone.")) return;
		try {
			await taskApi.delete(id);
			setTasks((prev) => prev.filter((t) => t.id !== id));
		} catch (err) {
			alert(err instanceof Error ? err.message : "Delete failed");
		}
	}

	if (loading)
		return (
			<Layout>
				<LoadingSpinner />
			</Layout>
		);
	if (error)
		return (
			<Layout>
				<div className="alert alert-error">{error}</div>
			</Layout>
		);

	return (
		<Layout>
			<PageHeader
				title="Tasks"
				subtitle="All tasks — reusable across multiple workflows"
				actions={
					perms.canWriteTasks ? (
						<button className="btn btn-primary" onClick={() => navigate("/tasks/new")}>
							+ New Task
						</button>
					) : undefined
				}
			/>

			{tasks.length === 0 ? (
				<EmptyState message="No tasks yet." />
			) : (
				<div className="table-wrapper">
					<table className="table">
						<thead>
							<tr>
								<th>Name</th>
								<th>Type</th>
								<th>Config</th>
								<th>Workflow</th>
								<th>Privacy</th>
								<th>Actions</th>
							</tr>
						</thead>
						<tbody>
							{tasks.map((t) => {
								const inUse = !!t.workflowId;
								return (
									<tr key={t.id}>
										<td>{t.name}</td>
										<td>
											<span className="badge">{t.type}</span>
										</td>
										<td>
											<code className="config-preview">{configSummary(t.type, t.config)}</code>
										</td>
										<td>
											{inUse ? (
												<span className="badge badge-muted">Linked</span>
											) : (
												<span className="text-muted">—</span>
											)}
										</td>
										<td>
											{t.isPrivate ? (
												<span className="badge badge-private">🔒 Private</span>
											) : (
												<span className="text-muted">Public</span>
											)}
										</td>
										<td className="actions-cell">
											{perms.canWriteTasks && (
												<Link to={`/tasks/${t.id}/edit`} className="btn btn-sm btn-secondary">
													Edit
												</Link>
											)}
											{perms.canDeleteTasks && (
												<button
													className="btn btn-sm btn-danger"
													onClick={() => void handleDelete(t.id)}
													disabled={inUse}
													title={
														inUse
															? "Cannot delete: task is linked to a workflow. Unlink it first."
															: undefined
													}
												>
													Delete
												</button>
											)}
										</td>
									</tr>
								);
							})}
						</tbody>
					</table>
				</div>
			)}
		</Layout>
	);
}
