import { useEffect, useState, useCallback, useMemo } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { DragDropContext, Droppable, Draggable, type DropResult } from "@hello-pangea/dnd";
import { workflowApi, type TaskOrderItem } from "../api/workflows";
import { taskApi, type WorkflowTaskEntry, type TaskResponse } from "../api/tasks";
import {
	executionApi,
	isActiveExecutionStatus,
	ExecutionTriggerType,
	ExecutionStatus,
	type ExecutionSummaryResponse,
} from "../api/executions";
import { usePermissions } from "../contexts/AuthContext";
import { Layout } from "../components/Layout";
import type { WorkflowResponse } from "../api/workflows";
import { StatusBadge } from "../components/StatusBadge";
import { EmptyState } from "../components/EmptyState";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { configSummary } from "../utils/task";

const STAGE_COLOURS = ["#6366f1", "#22c55e", "#f59e0b", "#ef4444", "#06b6d4", "#a855f7"];

function compactStages(entries: WorkflowTaskEntry[]): WorkflowTaskEntry[] {
	const uniqueStages = [...new Set(entries.map((e) => e.taskOrder))].sort((a, b) => a - b);
	const stageMap = new Map(uniqueStages.map((s, i) => [s, i + 1]));
	return entries.map((e) => ({ ...e, taskOrder: stageMap.get(e.taskOrder)! }));
}

function stageColour(stage: number): string {
	return STAGE_COLOURS[(stage - 1) % STAGE_COLOURS.length];
}

export function WorkflowDetailPage() {
	const { id } = useParams<{ id: string }>();
	const navigate = useNavigate();
	const perms = usePermissions();

	// --- UI/Core State ---
	const [workflow, setWorkflow] = useState<WorkflowResponse | null>(null);
	const [tasks, setTasks] = useState<WorkflowTaskEntry[]>([]);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState("");
	const [saving, setSaving] = useState(false);
	const [running, setRunning] = useState(false);

	// --- Inline Editing State ---
	const [editingStage, setEditingStage] = useState<string | null>(null);
	const [stageInput, setStageInput] = useState("");
	const [editingRetry, setEditingRetry] = useState<string | null>(null);
	const [retryInput, setRetryInput] = useState("");

	// --- Link/Picker State ---
	const [showLinkPicker, setShowLinkPicker] = useState(false);
	const [allTasks, setAllTasks] = useState<TaskResponse[]>([]);
	const [linkLoading, setLinkLoading] = useState(false);

	// --- Execution State ---
	const [executions, setExecutions] = useState<ExecutionSummaryResponse[]>([]);
	const [execLoading, setExecLoading] = useState(false);
	const [execOpen, setExecOpen] = useState(true);

	const taskStatuses = useMemo(() => {
		const statuses: Record<string, ExecutionStatus> = {};
		executions[0]?.taskExecutions?.forEach((te) => {
			if (te.taskId) statuses[te.taskId] = te.status;
		});
		return statuses;
	}, [executions]);

	// --- Core API Data Loaders ---
	const loadExecutions = useCallback(async () => {
		if (!id) return;
		setExecLoading(true);
		try {
			const data = await executionApi.listByWorkflow(id);
			setExecutions(data);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Failed to load executions");
		} finally {
			setExecLoading(false);
		}
	}, [id]);

	useEffect(() => {
		if (!id) return;
		let cancelled = false;
		Promise.all([workflowApi.getById(id), taskApi.listByWorkflow(id)])
			.then(([w, t]) => {
				if (!cancelled) {
					setWorkflow(w);
					setTasks(compactStages(t));
				}
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load");
			})
			.finally(() => {
				if (!cancelled) setLoading(false);
			});

		void loadExecutions();
		return () => {
			cancelled = true;
		};
	}, [id, loadExecutions]);


	useEffect(() => {
		if (!id) return;
		const hasActive = executions.some((e) => isActiveExecutionStatus(e.status));
		const timer = setTimeout(
			() => {
				executionApi
					.listByWorkflow(id)
					.then(setExecutions)
					.catch(() => {});
			},
			hasActive ? 5_000 : 20_000,
		);
		return () => clearTimeout(timer);
	}, [id, executions]);

	async function persistOrder(updated: WorkflowTaskEntry[]) {
		if (!id) return;
		const items: TaskOrderItem[] = updated
			.filter((t) => t.orderId !== null)
			.map((t) => ({ orderId: t.orderId!, taskOrder: t.taskOrder }));
		if (items.length === 0) return;
		setSaving(true);
		try {
			await workflowApi.reorderTasks(id, items);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Failed to save order");
		} finally {
			setSaving(false);
		}
	}

	function onDragEnd(result: DropResult) {
		if (!result.destination) return;
		const from = result.source.index;
		const to = result.destination.index;
		if (from === to) return;

		const reordered = Array.from(tasks);
		const [moved] = reordered.splice(from, 1);
		reordered.splice(to, 0, moved);

		const renumbered = reordered.map((t, i) => ({ ...t, taskOrder: i + 1 }));
		setTasks(renumbered);
		void persistOrder(renumbered);
	}


	function beginEditStage(taskId: string, current: number) {
		setEditingStage(taskId);
		setStageInput(String(current));
	}

	function commitStageEdit(taskId: string) {
		const newStage = parseInt(stageInput, 10);
		if (isNaN(newStage) || newStage < 1) {
			setEditingStage(null);
			return;
		}

		const updated = tasks.map((t) => (t.taskId === taskId ? { ...t, taskOrder: newStage } : t));
		const sorted = [...updated].sort((a, b) => a.taskOrder - b.taskOrder);
		const compact = compactStages(sorted);
		setTasks(compact);
		void persistOrder(compact);
		setEditingStage(null);
	}

	function beginEditRetry(taskId: string, current: number) {
		setEditingRetry(taskId);
		setRetryInput(String(current));
	}

	async function commitRetryEdit(taskId: string) {
		const value = parseInt(retryInput, 10);
		if (isNaN(value) || value < 0) {
			setEditingRetry(null);
			return;
		}
		if (!id) {
			setEditingRetry(null);
			return;
		}
		try {
			await workflowApi.updateRetryPolicy(id, taskId, value);
			setTasks((prev) => prev.map((t) => (t.taskId === taskId ? { ...t, retryPolicy: value } : t)));
		} catch (err) {
			setError(err instanceof Error ? err.message : "Failed to update retry policy");
		} finally {
			setEditingRetry(null);
		}
	}

	async function handleRunWorkflow() {
		if (!id) return;
		setRunning(true);
		setError("");
		try {
			await workflowApi.run(id);
			void loadExecutions();
		} catch (err) {
			setError(err instanceof Error ? err.message : "Run failed");
		} finally {
			setRunning(false);
		}
	}

	async function handleCancelExecution(executionId: string) {
		try {
			await executionApi.cancel(executionId);
			void loadExecutions();
		} catch (err) {
			setError(err instanceof Error ? err.message : "Cancel failed");
		}
	}

	// --- Task Link / Deletion Helpers ---
	async function handleDeleteTask(taskId: string) {
		if (!confirm("Delete this task?")) return;
		try {
			await taskApi.delete(taskId);
			const updated = compactStages(tasks.filter((t) => t.taskId !== taskId));
			setTasks(updated);
			void persistOrder(updated);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Delete failed");
		}
	}

	async function openLinkPicker() {
		setLinkLoading(true);
		setShowLinkPicker(true);
		try {
			const all = await taskApi.listAll();
			const linkedIds = new Set(tasks.map((t) => t.taskId));
			setAllTasks(all.filter((t) => !linkedIds.has(t.id)));
		} catch (err) {
			setError(err instanceof Error ? err.message : "Failed to load tasks");
			setShowLinkPicker(false);
		} finally {
			setLinkLoading(false);
		}
	}

	async function handleLinkTask(taskId: string) {
		if (!id) return;
		try {
			await workflowApi.linkTask(id, taskId);
			const updated = compactStages(await taskApi.listByWorkflow(id));
			setTasks(updated);
			setShowLinkPicker(false);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Link failed");
		}
	}

	async function handleUnlinkTask(taskId: string) {
		if (!id || !confirm("Remove this task from the workflow?")) return;
		try {
			await workflowApi.unlinkTask(id, taskId);
			const updated = compactStages(tasks.filter((t) => t.taskId !== taskId));
			setTasks(updated);
			if (updated.length > 0) void persistOrder(updated);
		} catch (err) {
			setError(err instanceof Error ? err.message : "Unlink failed");
		}
	}

	function toggleExecutions() {
		if (!execOpen) void loadExecutions();
		setExecOpen((o) => !o);
	}

	// --- Optimized Derivations ---
	const stageCounts = useMemo(() => {
		return tasks.reduce<Record<number, number>>((acc, t) => {
			acc[t.taskOrder] = (acc[t.taskOrder] ?? 0) + 1;
			return acc;
		}, {});
	}, [tasks]);

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
	if (!workflow)
		return (
			<Layout>
				<div>Not found</div>
			</Layout>
		);

	return (
		<Layout>
			<div className="page-header">
				<div>
					<Link to="/dashboard" className="back-link">
						← Workflows List
					</Link>
				</div>
				{!perms.isReader && (
					<div className="header-actions">
						{perms.canWriteWorkflows && (
							<Link to={`/workflows/${id}/edit`} className="btn btn-secondary">
								Edit Workflow
							</Link>
						)}
						{perms.canWriteSchedules && (
							<Link to={`/schedules?workflowId=${id}`} className="btn btn-secondary">
								🕒 Schedule
							</Link>
						)}
						{perms.canExecuteWorkflows && (
							<button className="btn btn-success" onClick={handleRunWorkflow} disabled={running}>
								{running ? "Starting…" : "▶ Run workflow"}
							</button>
						)}
					</div>
				)}
			</div>

			<div className="page-sub-header">
				<h1>{workflow.name}</h1>
				<p className="text-muted">Owner: {workflow.ownerUsername}</p>
				{workflow.isPrivate && <span className="badge badge-private">🔒 Private</span>}
			</div>

			<div className="section-header">
				<div className="section-header-title">
					<h2>Tasks ({tasks.length})</h2>
					{saving && <span className="text-muted saving-indicator">Saving…</span>}
				</div>
				{perms.canWriteTasks && (
					<div className="header-actions">
						<button className="btn btn-secondary" onClick={openLinkPicker}>
							Link existing
						</button>
						<button className="btn btn-primary" onClick={() => navigate(`/workflows/${id}/tasks/new`)}>
							+ New Task
						</button>
					</div>
				)}
			</div>

			{perms.canWriteTasks && tasks.length > 1 && (
				<p className="text-muted task-hint">
					Drag rows to reorder. Click a stage number to set it — tasks sharing the same stage run{" "}
					<strong>in parallel</strong>.
				</p>
			)}

			{tasks.length === 0 ? (
				<EmptyState message="No tasks yet." />
			) : (
				<div className="table-wrapper">
					<DragDropContext onDragEnd={onDragEnd}>
						<table className="table">
							<thead>
								<tr>
									{perms.canWriteTasks && <th className="th-drag-handle"></th>}
									<th>Stage</th>
									<th>Name</th>
									<th>Type</th>
									<th>Config</th>
									<th>Retries</th>
									<th>Status</th>
									{!perms.isReader && <th>Actions</th>}
								</tr>
							</thead>
							<Droppable droppableId="tasks">
								{(provided) => (
									<tbody ref={provided.innerRef} {...provided.droppableProps}>
										{tasks.map((t, index) => {
											const isParallel = stageCounts[t.taskOrder] > 1;
											const colour = stageColour(t.taskOrder);
											return (
												<Draggable
													key={t.taskId}
													draggableId={t.taskId}
													index={index}
													isDragDisabled={!perms.canWriteTasks}
												>
													{(drag, snapshot) => (
														<tr
															ref={drag.innerRef}
															{...drag.draggableProps}
															style={{
																...drag.draggableProps.style,
																display: snapshot.isDragging ? "table" : undefined,
																background: snapshot.isDragging ? "#22263a" : undefined,
																borderLeft: isParallel
																	? `3px solid ${colour}`
																	: undefined,
															}}
														>
															{perms.canWriteTasks && (
																<td
																	{...drag.dragHandleProps}
																	className="td-drag-handle"
																	title="Drag to reorder"
																>
																	⠿
																</td>
															)}
															<td>
																{perms.canWriteTasks ? (
																	editingStage === t.taskId ? (
																		<input
																			className="stage-input"
																			type="number"
																			min={1}
																			value={stageInput}
																			autoFocus
																			onChange={(e) =>
																				setStageInput(e.target.value)
																			}
																			onBlur={() => commitStageEdit(t.taskId)}
																			onKeyDown={(e) => {
																				if (e.key === "Enter")
																					commitStageEdit(t.taskId);
																				if (e.key === "Escape")
																					setEditingStage(null);
																			}}
																		/>
																	) : (
																		<span
																			className="stage-badge"
																			style={{
																				background: colour,
																				cursor: "pointer",
																			}}
																			title={
																				isParallel
																					? `Parallel group — stage ${t.taskOrder}`
																					: `Stage ${t.taskOrder} — click to change`
																			}
																			onClick={() =>
																				beginEditStage(t.taskId, t.taskOrder)
																			}
																		>
																			{t.taskOrder}
																			{isParallel && " ∥"}
																		</span>
																	)
																) : (
																	<span
																		className="stage-badge"
																		style={{ background: colour }}
																	>
																		{t.taskOrder}
																		{isParallel && " ∥"}
																	</span>
																)}
															</td>
															<td>{t.name}</td>
															<td>
																<span className="badge">{t.type}</span>
															</td>
															<td>
																<code className="config-preview">
																	{configSummary(t.type, t.config)}
																</code>
															</td>
															<td>
																{perms.canWriteTasks ? (
																	editingRetry === t.taskId ? (
																		<input
																			className="stage-input"
																			type="number"
																			min={0}
																			value={retryInput}
																			autoFocus
																			onChange={(e) =>
																				setRetryInput(e.target.value)
																			}
																			onBlur={() => commitRetryEdit(t.taskId)}
																			onKeyDown={(e) => {
																				if (e.key === "Enter")
																					void commitRetryEdit(t.taskId);
																				if (e.key === "Escape")
																					setEditingRetry(null);
																			}}
																		/>
																	) : (
																		<span
																			className="retry-badge retry-badge--clickable"
																			title="Click to edit retry count"
																			onClick={() =>
																				beginEditRetry(t.taskId, t.retryPolicy)
																			}
																		>
																			{t.retryPolicy}
																		</span>
																	)
																) : (
																	<span className="retry-badge">{t.retryPolicy}</span>
																)}
															</td>
															<td>
																{t.taskId && taskStatuses[t.taskId] ? (
																	<StatusBadge status={taskStatuses[t.taskId]} />
																) : (
																	<span className="badge badge-muted">—</span>
																)}
															</td>
															{!perms.isReader && (
																<td className="actions-cell">
																	{perms.canWriteTasks && (
																		<Link
																			to={`/tasks/${t.taskId}/edit?workflowId=${id}`}
																			className="btn btn-sm btn-secondary"
																		>
																			Edit
																		</Link>
																	)}
																	{perms.canWriteTasks && (
																		<button
																			className="btn btn-sm btn-secondary"
																			onClick={() => handleUnlinkTask(t.taskId)}
																			title="Remove from this workflow"
																		>
																			Unlink
																		</button>
																	)}
																	{perms.canDeleteTasks && (
																		<button
																			className="btn btn-sm btn-danger"
																			onClick={() => handleDeleteTask(t.taskId)}
																		>
																			Delete
																		</button>
																	)}
																</td>
															)}
														</tr>
													)}
												</Draggable>
											);
										})}
										{provided.placeholder}
									</tbody>
								)}
							</Droppable>
						</table>
					</DragDropContext>
				</div>
			)}

			{showLinkPicker && (
				<div className="modal-backdrop" onClick={() => setShowLinkPicker(false)}>
					<div className="modal-card" onClick={(e) => e.stopPropagation()}>
						<div className="modal-header">
							<h3>Link existing task</h3>
							<button className="btn btn-ghost btn-sm" onClick={() => setShowLinkPicker(false)}>
								✕
							</button>
						</div>
						{linkLoading ? (
							<LoadingSpinner message="Loading tasks…" />
						) : allTasks.length === 0 ? (
							<p className="text-muted">
								No available tasks to link. <Link to="/tasks/new">Create one</Link> first.
							</p>
						) : (
							<table className="table">
								<thead>
									<tr>
										<th>Name</th>
										<th>Type</th>
										<th></th>
									</tr>
								</thead>
								<tbody>
									{allTasks.map((t) => (
										<tr key={t.id}>
											<td>{t.name}</td>
											<td>
												<span className="badge">{t.type}</span>
											</td>
											<td>
												<button
													className="btn btn-sm btn-primary"
													onClick={() => {
														if (t.id) void handleLinkTask(t.id);
													}}
												>
													Link
												</button>
											</td>
										</tr>
									))}
								</tbody>
							</table>
						)}
					</div>
				</div>
			)}

			{/* Execution history Section */}
			<div className="background-wrapper">
				<div className="section-header section-header--spaced">
					<div>
						<h2>Execution History</h2>
						{executions.length > 0 && (
							<div className="exec-run-stats">
								<span className="badge badge-muted">
									{executions.filter((e) => e.triggeredType === ExecutionTriggerType.MANUAL).length}{" "}
									manual{" "}
									{executions.filter((e) => e.triggeredType === ExecutionTriggerType.MANUAL)
										.length === 1
										? "run"
										: "runs"}
								</span>
								{executions.some((e) => e.triggeredType === ExecutionTriggerType.CRON) && (
									<span className="badge badge-muted">
										{executions.filter((e) => e.triggeredType === ExecutionTriggerType.CRON).length}{" "}
										cron{" "}
										{executions.filter((e) => e.triggeredType === ExecutionTriggerType.CRON)
											.length === 1
											? "run"
											: "runs"}
									</span>
								)}
							</div>
						)}
					</div>
					<button className="btn btn-secondary btn-sm" onClick={toggleExecutions}>
						{execOpen ? "Hide" : "Show"}
					</button>
				</div>

				{execOpen &&
					(execLoading ? (
						<LoadingSpinner />
					) : executions.length === 0 ? (
						<EmptyState message="No executions yet." />
					) : (
						<div className="table-wrapper">
							<table className="table">
								<thead>
									<tr>
										<th>Status</th>
										<th>Type</th>
										<th>Trigger</th>
										<th>Started</th>
										<th>Finished</th>
										<th>By</th>
										<th>Actions</th>
									</tr>
								</thead>
								<tbody>
									{executions.map((ex) => (
										<tr key={ex.id}>
											<td>
												<StatusBadge status={ex.status} showIcon />
											</td>
											<td>
												<span className="badge">{ex.type}</span>
											</td>
											<td>
												<span className="badge">{ex.triggeredType}</span>
											</td>
											<td className="td-timestamp">{new Date(ex.startedAt).toLocaleString()}</td>
											<td className="td-timestamp">
												{ex.finishedAt ? new Date(ex.finishedAt).toLocaleString() : "—"}
											</td>
											<td>{ex.triggeredBy}</td>
											<td className="actions-cell">
												<Link
													to={`/workflows/${id}/executions/${ex.id}`}
													className="btn btn-sm btn-secondary"
												>
													Details
												</Link>
												{isActiveExecutionStatus(ex.status) && perms.canExecuteWorkflows && (
													<button
														className="btn btn-sm btn-danger"
														onClick={() => void handleCancelExecution(ex.id)}
													>
														Cancel
													</button>
												)}
											</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					))}
			</div>
		</Layout>
	);
}
