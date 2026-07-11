import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { scheduleApi, type ScheduleResponse } from "../api/schedules";
import { workflowApi, type WorkflowResponse } from "../api/workflows";
import { usePermissions } from "../contexts/AuthContext";
import { Layout } from "../components/Layout";
import { PageHeader } from "../components/PageHeader";
import { EmptyState } from "../components/EmptyState";
import { LoadingSpinner } from "../components/LoadingSpinner";

interface ScheduleSlot {
	everyDay: boolean;
	days: number[];
	hour: number;
	minute: number;
}

const DAY_LABELS: [number, string][] = [
	[1, "Mon"],
	[2, "Tue"],
	[3, "Wed"],
	[4, "Thu"],
	[5, "Fri"],
	[6, "Sat"],
	[0, "Sun"],
];

const TIMEZONE_OPTIONS: { label: string; value: string }[] = [
	{ label: "Portugal (Lisbon)", value: "Europe/Lisbon" },
	{ label: "UTC", value: "UTC" },
	{ label: "UK (London)", value: "Europe/London" },
	{ label: "Central Europe (Paris)", value: "Europe/Paris" },
	{ label: "Eastern Europe (Helsinki)", value: "Europe/Helsinki" },
	{ label: "US Eastern (New York)", value: "America/New_York" },
	{ label: "US Central (Chicago)", value: "America/Chicago" },
	{ label: "US Western (Los Angeles)", value: "America/Los_Angeles" },
	{ label: "Brazil (São Paulo)", value: "America/Sao_Paulo" },
	{ label: "India (Kolkata)", value: "Asia/Kolkata" },
	{ label: "China (Shanghai)", value: "Asia/Shanghai" },
	{ label: "Japan (Tokyo)", value: "Asia/Tokyo" },
	{ label: "Australia (Sydney)", value: "Australia/Sydney" },
];

function browserTimezone(): string {
	try {
		const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
		return TIMEZONE_OPTIONS.some((o) => o.value === tz) ? tz : "Europe/Lisbon";
	} catch {
		return "Europe/Lisbon";
	}
}

/** Format a UTC LocalDateTime string from the backend for display in local time. */
function formatUtcDate(dateStr: string | null): string {
	if (!dateStr) return "—";
	return new Date(dateStr + "Z").toLocaleString();
}

/* Placeholder values for form */
function defaultSlot(): ScheduleSlot {
	return { everyDay: true, days: [], hour: 9, minute: 0 };
}

function slotToCron(slot: ScheduleSlot): string {
	const daysPart = slot.everyDay || slot.days.length === 0 ? "*" : [...slot.days].sort((a, b) => a - b).join(",");

	/* Spring cron expression requires 6 fields: sec, min, hour, day, month, dayOfWeek */
	return `0 ${slot.minute} ${slot.hour} * * ${daysPart}`;
}

function cronToSlot(cron: string): ScheduleSlot {
	/* Spring 6-field cron: sec min hour day month dayOfWeek */
	const parts = cron.trim().split(/\s+/);
	const minute = parseInt(parts[1] ?? "0", 10);
	const hour = parseInt(parts[2] ?? "9", 10);
	const daysPart = parts[5] ?? "*";
	const everyDay = daysPart === "*";
	const days = everyDay ? [] : daysPart.split(",").map(Number);
	return { everyDay, days, hour, minute };
}


function slotDescription(slot: ScheduleSlot): string {
	const dayNames = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
	const daysStr = slot.everyDay
		? "Every day"
		: [...slot.days]
				.sort((a, b) => a - b)
				.map((d) => dayNames[d])
				.join(", ");
	const time = `${String(slot.hour).padStart(2, "0")}:${String(slot.minute).padStart(2, "0")}`;
	return `${daysStr} at ${time}`;
}


function SlotRow({
	slot,
	onChange,
	onRemove,
	showRemove,
}: {
	slot: ScheduleSlot;
	onChange: (updated: ScheduleSlot) => void;
	onRemove: () => void;
	showRemove: boolean;
}) {
	function toggleDay(day: number) {
		const next = slot.days.includes(day) ? slot.days.filter((d) => d !== day) : [...slot.days, day];
		onChange({ ...slot, days: next, everyDay: false });
	}

	return (
		<div className="schedule-slot">
			<div className="slot-row">
				<div className="form-group">
					<label>Days</label>
					<div className="day-picker">
						<label className="day-check every-day">
							<input
								type="checkbox"
								checked={slot.everyDay}
								onChange={(e) => onChange({ ...slot, everyDay: e.target.checked, days: [] })}
							/>
							Every day
						</label>
						{!slot.everyDay &&
							DAY_LABELS.map(([num, label]) => (
								<label key={num} className="day-check">
									<input
										type="checkbox"
										checked={slot.days.includes(num)}
										onChange={() => toggleDay(num)}
									/>
									{label}
								</label>
							))}
					</div>
				</div>

				<div className="slot-time">
					<div className="form-group">
						<label>Hour</label>
						<select value={slot.hour} onChange={(e) => onChange({ ...slot, hour: Number(e.target.value) })}>
							{Array.from({ length: 24 }, (_, i) => (
								<option key={i} value={i}>
									{String(i).padStart(2, "0")}
								</option>
							))}
						</select>
					</div>
					<div className="form-group">
						<label>Minute</label>
						<select
							value={slot.minute}
							onChange={(e) => onChange({ ...slot, minute: Number(e.target.value) })}
						>
							{[0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55].map((m) => (
								<option key={m} value={m}>
									{String(m).padStart(2, "0")}
								</option>
							))}
						</select>
					</div>
				</div>

				{showRemove && (
					<button type="button" className="btn btn-sm btn-danger slot-remove" onClick={onRemove}>
						✕
					</button>
				)}
			</div>
			<div className="slot-preview">
				<code>{slotToCron(slot)}</code>
				<span className="slot-preview-label">{slotDescription(slot)}</span>
			</div>
		</div>
	);
}

export function SchedulesPage() {
	const [schedules, setSchedules] = useState<ScheduleResponse[]>([]);
	const [workflows, setWorkflows] = useState<WorkflowResponse[]>([]);
	const [loading, setLoading] = useState(true);
	const [showForm, setShowForm] = useState(false);
	const [editTarget, setEditTarget] = useState<ScheduleResponse | null>(null);
	const [error, setError] = useState("");
	const perms = usePermissions();
	const [searchParams] = useSearchParams();
	const preselectedWorkflowId = searchParams.get("workflowId") ?? "";
	const navigate = useNavigate();

	const [workflowId, setWorkflowId] = useState(preselectedWorkflowId);
	const [slots, setSlots] = useState<ScheduleSlot[]>([defaultSlot()]);
	const [timezone, setTimezone] = useState(browserTimezone());
	const [enabled, setEnabled] = useState(true);
	const [saving, setSaving] = useState(false);

	useEffect(() => {
		let cancelled = false;
		Promise.all([scheduleApi.list(), workflowApi.list()])
			.then(([s, w]) => {
				if (cancelled) return;
				setSchedules(s);
				setWorkflows(w);
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

	function openCreate() {
		setEditTarget(null);
		setWorkflowId(preselectedWorkflowId);
		setSlots([defaultSlot()]);
		setTimezone(browserTimezone());
		setEnabled(true);
		setShowForm(true);
	}

	function openEdit(s: ScheduleResponse) {
		setEditTarget(s);
		setWorkflowId(s.workflowId);
		setSlots([cronToSlot(s.cronExpression)]);
		setTimezone(s.timezone);
		setEnabled(s.enabled);
		setShowForm(true);
	}

	function updateSlot(index: number, updated: ScheduleSlot) {
		setSlots((prev) => prev.map((s, i) => (i === index ? updated : s)));
	}

	function removeSlot(index: number) {
		setSlots((prev) => prev.filter((_, i) => i !== index));
	}

	async function handleSubmit(e: FormEvent) {
		e.preventDefault();
		if (slots.some((s) => !s.everyDay && s.days.length === 0)) {
			setError('Please select at least one day for each slot, or toggle "Every day".');
			return;
		}
		setSaving(true);
		setError("");
		try {
			if (editTarget) {
				const slot = slots[0];
				const updated = await scheduleApi.update(editTarget.id, {
					cronExpression: slotToCron(slot),
					timezone,
					enabled,
				});
				setSchedules((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
			} else {
				const created = await Promise.all(
					slots.map((slot) =>
						scheduleApi.create({
							workflowId,
							cronExpression: slotToCron(slot),
							timezone,
							enabled,
						}),
					),
				);
				setSchedules((prev) => [...prev, ...created]);
			}
			setShowForm(false);
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : "Save failed");
		} finally {
			setSaving(false);
		}
	}

	async function handleDelete(id: string) {
		if (!confirm("Delete this schedule?")) return;
		try {
			await scheduleApi.delete(id);
			setSchedules((prev) => prev.filter((s) => s.id !== id));
		} catch (err: unknown) {
			setError(err instanceof Error ? err.message : "Delete failed");
		}
	}

	return (
		<Layout>
			<PageHeader
				title="Schedules"
				actions={
					perms.canWriteSchedules ? (
						<button className="btn btn-primary" onClick={openCreate}>
							+ New Schedule
						</button>
					) : undefined
				}
			/>

			{error && <div className="alert alert-error">{error}</div>}

			{showForm && (
				<div className="form-card">
					<h2>{editTarget ? "Edit Schedule" : "New Schedule"}</h2>
					<form onSubmit={handleSubmit} className="form">
						{!editTarget && (
							<div className="form-group">
								<label>Workflow</label>
								<select value={workflowId} onChange={(e) => setWorkflowId(e.target.value)} required>
									<option value="">Select workflow…</option>
									{workflows.map((w) => (
										<option key={w.id} value={w.id}>
											{w.name}
										</option>
									))}
								</select>
							</div>
						)}

						<div className="form-group">
							<label>Timezone</label>
							<select value={timezone} onChange={(e) => setTimezone(e.target.value)} required>
								{TIMEZONE_OPTIONS.map((o) => (
									<option key={o.value} value={o.value}>
										{o.label}
									</option>
								))}
							</select>
						</div>
						<div className="form-group form-check">
							<input
								type="checkbox"
								id="enabled"
								checked={enabled}
								onChange={(e) => setEnabled(e.target.checked)}
							/>
							<label htmlFor="enabled">Enabled</label>
						</div>

						<div className="slots-section">
							<div className="slots-header">
								<h3>Time slots</h3>
								{!editTarget && (
									<button
										type="button"
										className="btn btn-sm btn-secondary"
										onClick={() => setSlots((prev) => [...prev, defaultSlot()])}
									>
										+ Add slot
									</button>
								)}
							</div>
							<p className="slots-hint">
								Each slot creates one schedule record for the selected workflow.
							</p>
							{slots.map((slot, i) => (
								<SlotRow
									key={i}
									slot={slot}
									onChange={(updated) => updateSlot(i, updated)}
									onRemove={() => removeSlot(i)}
									showRemove={slots.length > 1}
								/>
							))}
						</div>

						<div className="form-actions">
							<button type="button" className="btn btn-ghost" onClick={() => setShowForm(false)}>
								Cancel
							</button>
							<button type="submit" className="btn btn-primary" disabled={saving}>
								{saving
									? "Saving…"
									: editTarget
										? "Save changes"
										: `Create ${slots.length > 1 ? `${slots.length} schedules` : "schedule"}`}
							</button>
						</div>
					</form>
				</div>
			)}

			{loading ? (
				<LoadingSpinner />
			) : schedules.length === 0 ? (
				<EmptyState message="No schedules yet." />
			) : (
				<div className="table-wrapper">
					<table className="table">
						<thead>
							<tr>
								<th>Workflow</th>
								<th>Cron</th>
								<th>Timezone</th>
								<th>Status</th>
								<th>Next run</th>
								<th>Last run</th>
								{perms.canWriteSchedules && <th>Actions</th>}
							</tr>
						</thead>
						<tbody>
							{schedules.map((s) => (
								<tr key={s.id}>
									<td>
										<button
											className="link-btn"
											onClick={() => navigate(`/workflows/${s.workflowId}`)}
										>
											{s.workflowName}
										</button>
									</td>
									<td>
										<code>{s.cronExpression}</code>
									</td>
									<td>{s.timezone}</td>
									<td>
										<span className={`badge ${s.enabled ? "badge-success" : "badge-muted"}`}>
											{s.enabled ? "Active" : "Disabled"}
										</span>
									</td>
									<td>{formatUtcDate(s.nextRunAt)}</td>
									<td>{formatUtcDate(s.lastRunAt)}</td>
									{perms.canWriteSchedules && (
										<td className="actions-cell">
											<button className="btn btn-sm btn-secondary" onClick={() => openEdit(s)}>
												Edit
											</button>
											{perms.canDeleteSchedules && (
												<button
													className="btn btn-sm btn-danger"
													onClick={() => handleDelete(s.id)}
												>
													Delete
												</button>
											)}
										</td>
									)}
								</tr>
							))}
						</tbody>
					</table>
				</div>
			)}
		</Layout>
	);
}
