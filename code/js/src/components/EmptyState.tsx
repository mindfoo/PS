interface EmptyStateProps {
	message?: string;
}

export function EmptyState({ message = "Nothing here yet." }: EmptyStateProps) {
	return (
		<div className="empty-state">
			<p>{message}</p>
		</div>
	);
}
