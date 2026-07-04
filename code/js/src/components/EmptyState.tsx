import type { ReactNode } from "react";

interface EmptyStateProps {
	message?: string;
	action?: ReactNode;
}

export function EmptyState({ message = "Nothing here yet.", action }: EmptyStateProps) {
	return (
		<div className="empty-state">
			<p>{message}</p>
			{action}
		</div>
	);
}
