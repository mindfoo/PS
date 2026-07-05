import { ExecutionStatus } from "../api/executions";

const STATUS_MAP: Record<ExecutionStatus, { icon: string; cls: string }> = {
	[ExecutionStatus.SUCCESS]: { icon: "✅", cls: "badge-status-success" },
	[ExecutionStatus.ERROR]: { icon: "❌", cls: "badge-status-error" },
	[ExecutionStatus.RUNNING]: { icon: "⏳", cls: "badge-status-running" },
	[ExecutionStatus.PENDING]: { icon: "🕐", cls: "badge-status-pending" },
	[ExecutionStatus.CANCELED]: { icon: "—", cls: "badge-status-canceled" },
};

interface StatusBadgeProps {
	status: ExecutionStatus;
	showIcon?: boolean;
}

export function StatusBadge({ status, showIcon = false }: StatusBadgeProps) {
	const entry = STATUS_MAP[status] ?? { icon: "•", cls: "badge-muted" };
	return (
		<span className={`badge ${entry.cls}`} title={`Status: ${status}`}>
			{showIcon ? `${entry.icon} ${status}` : status}
		</span>
	);
}
