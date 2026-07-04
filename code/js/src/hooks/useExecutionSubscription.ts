import { useCallback, useEffect, useRef } from "react";
import { executionApi, type ExecutionEvent } from "../api/executions";

/**
 * Keeps at most one live SSE subscription to an execution's status stream.
 *
 * - Calling `subscribe` again (e.g. to follow a newly started execution)
 *   closes the previous connection first, so callers never leak sockets.
 * - The connection closes itself once the server sends a `terminal` event.
 * - The connection also closes on unmount.
 *
 * This hook only manages the connection's lifecycle; callers own what
 * happens with each event (updating state, refetching, etc.) via `onEvent`.
 */
export function useExecutionSubscription(onEvent: (event: ExecutionEvent) => void) {
	const unsubscribeRef = useRef<(() => void) | null>(null);

	// Always call the latest onEvent without having to recreate `subscribe`.
	const onEventRef = useRef(onEvent);
	onEventRef.current = onEvent;

	const unsubscribe = useCallback(() => {
		unsubscribeRef.current?.();
		unsubscribeRef.current = null;
	}, []);

	const subscribe = useCallback(
		(executionId: string) => {
			unsubscribe();
			unsubscribeRef.current = executionApi.subscribeToExecution(executionId, (event) => {
				onEventRef.current(event);
				if (event.terminal) unsubscribe();
			});
		},
		[unsubscribe],
	);

	useEffect(() => unsubscribe, [unsubscribe]);

	return { subscribe, unsubscribe };
}
