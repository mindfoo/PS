import { useEffect, useReducer, useState } from "react";
import { useParams, useNavigate, Navigate, useSearchParams } from "react-router-dom";
import { taskApi, TaskType } from "../api/tasks";
import { Method } from "../api/client";
import { Layout } from "../components/Layout";
import { PageHeader } from "../components/PageHeader";
import { LoadingSpinner } from "../components/LoadingSpinner";
import { genericFormReducer } from "../utils/form";
import type { FormEvent } from "react";

export interface TaskFormInputs {
	name: string;
	type: TaskType;
	url: string;
	method: string;
	headers: string;
	body: string;
	command: string;
	fileName: string;
	args: string;
}

const DEFAULT_INPUTS: TaskFormInputs = {
	name: "",
	type: TaskType.HTTP,
	url: "",
	method: Method.GET,
	headers: "",
	body: "",
	command: "",
	fileName: "",
	args: "",
};

// Mirrors the backend allowlist in ExecutionService.runScriptTask.
const ALLOWED_COMMANDS = ["node", "python3", "python", "bash", "sh"];

function parseHeaders(text: string): Record<string, string> {
	const headers: Record<string, string> = {};
	for (const line of text.split("\n")) {
		const idx = line.indexOf(":");
		if (idx > 0) headers[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
	}
	return headers;
}

function headersToText(headers: unknown): string {
	if (!headers || typeof headers !== "object") return "";
	return Object.entries(headers as Record<string, string>)
		.map(([name, value]) => `${name}: ${value}`)
		.join("\n");
}

function fromTask(t: { name: string; type: TaskType; config: Record<string, unknown> }): TaskFormInputs {
	const configuration = t.config as Record<string, string>;
	return {
		...DEFAULT_INPUTS,
		name: t.name,
		type: t.type,
		url: configuration.url ?? "",
		method: configuration.method ?? Method.GET,
		headers: headersToText(configuration.headers),
		body: configuration.body ?? "",
		command: configuration.command ?? "",
		fileName: configuration.fileName ?? "",
		args: Array.isArray(configuration.args)
			? (configuration.args as unknown as string[]).join(" ")
			: (configuration.args ?? ""),
	};
}

function buildConfig(inputs: TaskFormInputs): Record<string, unknown> {
	switch (inputs.type) {
		case TaskType.HTTP: {
			const cfg: Record<string, unknown> = { url: inputs.url, method: inputs.method };
			const headers = parseHeaders(inputs.headers);
			if (Object.keys(headers).length > 0) cfg.headers = headers;
			if (inputs.body.trim()) cfg.body = inputs.body;
			return cfg;
		}
		case TaskType.SCRIPT: {
			const cfg: Record<string, unknown> = { command: inputs.command, fileName: inputs.fileName };
			if (inputs.args) cfg.args = inputs.args.trim().split(/\s+/);
			return cfg;
		}
		default:
			return {};
	}
}

export function TaskFormPage() {
	const { id, workflowId: routeWorkflowId } = useParams<{ id?: string; workflowId?: string }>();
	const [searchParams] = useSearchParams();
	const queryWorkflowId = searchParams.get("workflowId") ?? undefined;
	const isEdit = !!id;

	const [initialInputs, setInitialInputs] = useState<TaskFormInputs | null>(isEdit ? null : DEFAULT_INPUTS);
	const [workflowId, setWorkflowId] = useState(routeWorkflowId ?? queryWorkflowId ?? "");
	const [initialIsPrivate, setInitialIsPrivate] = useState(false);
	const [error, setError] = useState("");

	useEffect(() => {
		if (!isEdit) return;
		let cancelled = false;
		taskApi
			.getById(id)
			.then((t) => {
				if (cancelled) return;
				setWorkflowId(t.workflowId ?? "");
				setInitialIsPrivate(t.isPrivate);
				setInitialInputs(fromTask(t));
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load task");
			});
		return () => {
			cancelled = true;
		};
	}, [id, isEdit]);

	if (error)
		return (
			<Layout>
				<div className="alert alert-error">{error}</div>
			</Layout>
		);
	if (!initialInputs)
		return (
			<Layout>
				<LoadingSpinner />
			</Layout>
		);

	return (
		<TaskForm
			initialInputs={initialInputs}
			workflowId={workflowId}
			taskId={id}
			initialIsPrivate={initialIsPrivate}
		/>
	);
}

function TaskForm({
	initialInputs,
	workflowId,
	taskId,
	initialIsPrivate = false,
}: {
	initialInputs: TaskFormInputs;
	workflowId: string;
	taskId?: string;
	initialIsPrivate?: boolean;
}) {
	const isEdit = !!taskId;
	const navigate = useNavigate();
	const backUrl = workflowId ? `/workflows/${workflowId}` : "/tasks";
	const [isPrivate, setIsPrivate] = useState(initialIsPrivate);
	const [availableScripts, setAvailableScripts] = useState<string[]>([]);

	useEffect(() => {
		taskApi
			.listAvailableScripts()
			.then(setAvailableScripts)
			.catch(() => setAvailableScripts([]));
	}, []);

	const [state, dispatch] = useReducer(genericFormReducer<TaskFormInputs>, {
		tag: "editing",
		inputs: initialInputs,
	});

	async function handleSubmit(e: FormEvent<HTMLFormElement>) {
		e.preventDefault();
		if (state.tag !== "editing") return;
		dispatch({ type: "submit" });
		try {
			const { name, type } = state.inputs;
			const config = buildConfig(state.inputs);
			if (isEdit) {
				await taskApi.update(taskId, { name, type, config, isPrivate });
			} else {
				await taskApi.create({ name, type, workflowId: workflowId || undefined, config, isPrivate });
			}
			dispatch({ type: "success" });
		} catch (err: unknown) {
			dispatch({ type: "error", message: err instanceof Error ? err.message : "Save failed" });
		}
	}

	if (state.tag === "redirect") {
		return <Navigate to={backUrl} replace />;
	}

	function field(name: keyof TaskFormInputs) {
		return (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) =>
			dispatch({ type: "edit", name, value: e.target.value });
	}

	const { inputs } = state;
	const isSubmitting = state.tag === "submitting";

	return (
		<Layout>
			<PageHeader title={isEdit ? "Edit Task" : "New Task"} back={{ href: backUrl }} />

			<div className="form-card">
				{state.tag === "editing" && state.error && <div className="alert alert-error">{state.error}</div>}

				<form onSubmit={handleSubmit} className="form">
					<fieldset disabled={isSubmitting} style={{ border: "none", padding: 0, margin: 0 }}>
						<div className="form-group">
							<label>Task name</label>
							<input value={inputs.name} onChange={field("name")} placeholder="Fetch Data" required />
						</div>

						<div className="form-group">
							<label>Type</label>
							<select value={inputs.type} onChange={field("type")}>
								<option value={TaskType.HTTP}>{TaskType.HTTP}</option>
								<option value={TaskType.SCRIPT}>{TaskType.SCRIPT}</option>
							</select>
						</div>

						{inputs.type === TaskType.HTTP && (
							<>
								<div className="form-group">
									<label>URL</label>
									<input
										type="url"
										value={inputs.url}
										onChange={field("url")}
										placeholder="https://example.com/api"
										required
									/>
								</div>
								<div className="form-group">
									<label>Method</label>
									<select value={inputs.method} onChange={field("method")}>
										{Object.values(Method).map((m) => (
											<option key={m}>{m}</option>
										))}
									</select>
								</div>
								<div className="form-group">
									<label>
										Headers
										<span className="text-muted">(optional)</span>
									</label>
									<textarea
										value={inputs.headers}
										onChange={field("headers")}
										placeholder={"Content-Type: application/json\netc: etc"}
										rows={3}
									/>
								</div>
								<div className="form-group">
									<label>
										Body <span className="text-muted">(optional)</span>
									</label>
									<textarea
										value={inputs.body}
										onChange={field("body")}
										placeholder='{"key": "value"}'
										rows={4}
									/>
								</div>
							</>
						)}

						{inputs.type === TaskType.SCRIPT && (
							<>
								<div className="form-group">
									<label>Command</label>
									<select value={inputs.command} onChange={field("command")} required>
										<option value="">— select a command —</option>
										{[...new Set([inputs.command, ...ALLOWED_COMMANDS].filter(Boolean))].map(
											(comm) => (
												<option key={comm} value={comm}>
													{comm}
												</option>
											),
										)}
									</select>
								</div>
								<div className="form-group">
									<label>File name</label>
									<select value={inputs.fileName} onChange={field("fileName")} required>
										<option value="">— select a file —</option>
										{[...new Set([inputs.fileName, ...availableScripts].filter(Boolean))].map(
											(f) => (
												<option key={f} value={f}>
													{f}
												</option>
											),
										)}
									</select>
									<span className="text-muted">
										Files placed manually in the scripts folder by DEV/ADMIN.
									</span>
								</div>
								<div className="form-group">
									<label>
										Extra args <span className="text-muted">(optional, space-separated)</span>
									</label>
									<input value={inputs.args} onChange={field("args")} placeholder="ex: --env prod" />
								</div>
							</>
						)}

						<div className="form-group">
							<label className="checkbox-label">
								<input
									type="checkbox"
									checked={isPrivate}
									onChange={(e) => setIsPrivate(e.target.checked)}
								/>
								Private (visible only to you and admins)
							</label>
						</div>

						<div className="form-actions">
							<button type="button" className="btn btn-ghost" onClick={() => navigate(backUrl)}>
								Cancel
							</button>
							<button type="submit" className="btn btn-primary">
								{isSubmitting ? "Saving…" : isEdit ? "Save changes" : "Create task"}
							</button>
						</div>
					</fieldset>
				</form>
			</div>
		</Layout>
	);
}
