import { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import {
	executionApi,
	isActiveExecutionStatus,
	ExecutionStatus,
	ExecutionType,
	type ExecutionEvent,
	type ExecutionSummaryResponse,
	type TaskExecutionSummary,
} from "../api/executions";
import { workflowApi } from "../api/workflows";
import { taskApi } from "../api/tasks";
import { usePermissions } from "../contexts/AuthContext";
import { Layout } from "../components/Layout";
import { StatusBadge } from "../components/StatusBadge";
import { LoadingSpinner } from "../components/LoadingSpinner";

function applyStatusEvent(execution: ExecutionSummaryResponse, event: ExecutionEvent): ExecutionSummaryResponse {
	return {
		...execution,
		status: event.status,
		taskExecutions:
			execution.taskExecutions?.map((te) => ({
				...te,
				status: event.taskStatuses[te.taskId ?? ""] ?? te.status,
			})) ?? null,
	};
}

export function ExecutionDetailPage() {
	const { workflowId, executionId } = useParams<{ workflowId: string; executionId: string }>();
	const navigate = useNavigate();
	const perms = usePermissions();

	const [execution, setExecution] = useState<ExecutionSummaryResponse | null>(null);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [cancelling, setCancelling] = useState(false);
	const [retrying, setRetrying] = useState(false);

	useEffect(() => {
		if (!executionId) return;

		let cancelled = false;

		const unsubscribe = executionApi.subscribeToExecution(executionId, (event: ExecutionEvent) => {
			setExecution((prev) => (prev ? applyStatusEvent(prev, event) : prev));
			if (!isActiveExecutionStatus(event.status)) {
				unsubscribe();
				void executionApi.getById(event.executionId).then(setExecution);
			}
		});

		executionApi
			.getById(executionId)
			.then((data) => {
				if (cancelled) return;
				setExecution(data);
				if (!isActiveExecutionStatus(data.status)) unsubscribe();
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load execution");
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});

		return () => {
			cancelled = true;
			unsubscribe();
		};
	}, [executionId]);

	async function handleCancel() {
		if (!executionId || !confirm("Cancel this execution?")) return;
		setCancelling(true);
		setError("");
		try {
			await executionApi.cancel(executionId);
			setExecution((prev) => (prev ? { ...prev, status: ExecutionStatus.CANCELED } : prev));
		} catch (err) {
			setError(err instanceof Error ? err.message : "Cancel failed");
		} finally {
			setCancelling(false);
		}
	}

	async function handleRetry() {
		if (!workflowId) return;
		setRetrying(true);
		setError("");
		try {
			const res =
				execution?.type === ExecutionType.TASK && execution.taskExecutions?.[0]?.taskId
					? await taskApi.run(execution.taskExecutions[0].taskId)
					: await workflowApi.run(workflowId);
			navigate(`/workflows/${workflowId}/executions/${res.executionId}`);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Retry failed");
		} finally {
			setRetrying(false);
		}
	}

	if (loading)
		return (
			<Layout>
				<LoadingSpinner />
			</Layout>
		);

	if (error || !execution) {
		return (
			<Layout>
				<div className="page-header">
					<Link to={`/workflows/${workflowId}`} className="back-link">
						← Back to workflow
					</Link>
				</div>
				<div className="alert alert-error">{error || "Execution not found."}</div>
			</Layout>
		);
	}

	const isActive = isActiveExecutionStatus(execution.status);
	const isTerminal = !isActive;

	return (
		<Layout>
			<div className="back-link-row">
				<Link to={`/workflows/${workflowId}`} className="back-link">
					← Back to workflow
				</Link>
			</div>

			<div className="page-header">
				<div>
					<h1 className="execution-title">
						Execution <code className="execution-id">{execution.id.slice(0, 8)}…</code>
					</h1>
					<p className="text-muted">
						{execution.type === ExecutionType.WORKFLOW ? "Full workflow run" : "Single task run"}
						{" · by "}
						<strong>{execution.triggeredBy}</strong>
					</p>
				</div>
				<div className="header-actions">
					{isActive && perms.canExecuteWorkflows && (
						<button className="btn btn-danger" onClick={handleCancel} disabled={cancelling}>
							{cancelling ? "Cancelling…" : "Cancel execution"}
						</button>
					)}
					{isTerminal && perms.canExecuteWorkflows && (
						<button className="btn btn-secondary" onClick={handleRetry} disabled={retrying}>
							{retrying ? "Starting…" : "↺ Retry"}
						</button>
					)}
				</div>
			</div>

			{error && <div className="alert alert-error">{error}</div>}

			<div className="detail-card">
				<div className="detail-grid">
					<div className="detail-item">
						<span className="detail-label">Status</span>
						<StatusBadge status={execution.status} showIcon />
					</div>
					<div className="detail-item">
						<span className="detail-label">Started</span>
						<span>{new Date(execution.startedAt).toLocaleString()}</span>
					</div>
					<div className="detail-item">
						<span className="detail-label">Finished</span>
						<span>{execution.finishedAt ? new Date(execution.finishedAt).toLocaleString() : "—"}</span>
					</div>
					<div className="detail-item">
						<span className="detail-label">Retries</span>
						<span>{execution.retryCount}</span>
					</div>
					<div className="detail-item">
						<span className="detail-label">Trigger</span>
						<span className="badge">{execution.triggeredType}</span>
					</div>
					<div className="detail-item">
						<span className="detail-label">Type</span>
						<span className="badge">{execution.type}</span>
					</div>
				</div>
			</div>

			{execution.taskExecutions && execution.taskExecutions.length > 0 && (
				<section className="exec-section">
					<h2 className="section-title">Task executions</h2>
					<div className="table-wrapper">
						<table className="table">
							<thead>
								<tr>
									<th>Task</th>
									<th>Status</th>
									<th>Started</th>
									<th>Finished</th>
									<th>Output</th>
								</tr>
							</thead>
							<tbody>
								{execution.taskExecutions.map((te: TaskExecutionSummary) => (
									<tr key={te.executionId}>
										<td>
											<strong>{te.taskName ?? te.taskId ?? "—"}</strong>
										</td>
										<td>
											<StatusBadge status={te.status} showIcon />
										</td>
										<td className="td-timestamp">{new Date(te.startedAt).toLocaleString()}</td>
										<td className="td-timestamp">
											{te.finishedAt ? new Date(te.finishedAt).toLocaleString() : "—"}
										</td>
										<td>
											{te.output ? (
												<pre className="execution-output-pre">
													{JSON.stringify(te.output, null, 2)}
												</pre>
											) : (
												<span className="text-muted">—</span>
											)}
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</section>
			)}

			{execution.output && (
				<section className="exec-section">
					<h2 className="section-title">Output</h2>
					<pre className="execution-output-pre--full">{JSON.stringify(execution.output, null, 2)}</pre>
				</section>
			)}

			{!execution.taskExecutions?.length && !execution.output && (
				<p className="text-muted">No output recorded.</p>
			)}
		</Layout>
	);
}
