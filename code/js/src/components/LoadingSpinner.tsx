interface LoadingSpinnerProps {
	message?: string;
}

export function LoadingSpinner({ message = "Loading…" }: LoadingSpinnerProps) {
	return <div className="loading">{message}</div>;
}
