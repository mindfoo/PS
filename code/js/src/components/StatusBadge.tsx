const STATUS_MAP: Record<string, { icon: string; cls: string }> = {
	SUCCESS: { icon: "✅", cls: "badge-status-success" },
	ERROR: { icon: "❌", cls: "badge-status-error" },
	RUNNING: { icon: "⏳", cls: "badge-status-running" },
	PENDING: { icon: "🕐", cls: "badge-status-pending" },
	CANCELED: { icon: "—", cls: "badge-status-canceled" },
};

interface StatusBadgeProps {
	status: string;
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
