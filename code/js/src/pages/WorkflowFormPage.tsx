import { useEffect, useState, type FormEvent } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { workflowApi } from "../api/workflows";
import { Layout } from "../components/Layout";
import { PageHeader } from "../components/PageHeader";
import { LoadingSpinner } from "../components/LoadingSpinner";

export function WorkflowFormPage() {
	const { id } = useParams<{ id: string }>();
	const isEdit = !!id;
	const navigate = useNavigate();

	const [name, setName] = useState("");
	const [isPrivate, setIsPrivate] = useState(false);
	const [loading, setLoading] = useState(false);
	const [fetchLoading, setFetchLoading] = useState(isEdit);
	const [error, setError] = useState("");

	useEffect(() => {
		if (!isEdit) return;
		let cancelled = false;
		workflowApi
			.getById(id)
			.then((w) => {
				if (!cancelled) {
					setName(w.name);
					setIsPrivate(w.isPrivate);
				}
			})
			.catch((err) => {
				if (!cancelled) setError(err instanceof Error ? err.message : "Failed to load");
			})
			.finally(() => {
				if (!cancelled) setFetchLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [id, isEdit]);

	async function handleSubmit(e: FormEvent) {
		e.preventDefault();
		setError("");
		setLoading(true);
		try {
			if (isEdit) {
				await workflowApi.update(id, { name, isPrivate });
				navigate(`/workflows/${id}`);
			} else {
				const created = await workflowApi.create({ name, isPrivate });
				navigate(`/workflows/${created.id}`);
			}
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : "Save failed");
		} finally {
			setLoading(false);
		}
	}

	if (fetchLoading)
		return (
			<Layout>
				<LoadingSpinner />
			</Layout>
		);

	return (
		<Layout>
			<PageHeader
				title={isEdit ? "Edit Workflow" : "New Workflow"}
				back={{ href: "/dashboard", label: "← Workflows" }}
			/>

			<div className="form-card">
				{error && <div className="alert alert-error">{error}</div>}
				<form onSubmit={handleSubmit} className="form">
					<div className="form-group">
						<label>Workflow name</label>
						<input
							value={name}
							onChange={(e) => setName(e.target.value)}
							placeholder="My Pipeline"
							required
						/>
					</div>
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
						<button
							type="button"
							className="btn btn-ghost"
							onClick={() => navigate(isEdit ? `/workflows/${id}` : "/dashboard")}
						>
							Cancel
						</button>
						<button type="submit" className="btn btn-primary" disabled={loading}>
							{loading ? "Saving…" : isEdit ? "Save changes" : "Create workflow"}
						</button>
					</div>
				</form>
			</div>
		</Layout>
	);
}
