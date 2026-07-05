import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "../../components/StatusBadge";
import type { ExecutionStatus } from "../../api/executions";

describe("StatusBadge", () => {
	it.each<[ExecutionStatus, string]>([
		["SUCCESS", "badge-status-success"],
		["ERROR", "badge-status-error"],
		["RUNNING", "badge-status-running"],
		["PENDING", "badge-status-pending"],
		["CANCELED", "badge-status-canceled"],
	])("renders %s with correct class", (status, cls) => {
		render(<StatusBadge status={status} />);
		const badge = screen.getByTitle(`Status: ${status}`);
		expect(badge).toHaveClass("badge", cls);
		expect(badge).toHaveTextContent(status);
	});

	it("renders unknown status with badge-muted class", () => {
		// Simulates an unrecognized status value that could reach the UI from a future backend enum
		render(<StatusBadge status={"UNKNOWN" as ExecutionStatus} />);
		expect(screen.getByTitle("Status: UNKNOWN")).toHaveClass("badge-muted");
	});

	it("prepends icon when showIcon is true", () => {
		render(<StatusBadge status="SUCCESS" showIcon />);
		expect(screen.getByTitle("Status: SUCCESS")).toHaveTextContent("✅ SUCCESS");
	});

	it("omits icon by default", () => {
		render(<StatusBadge status="SUCCESS" />);
		expect(screen.getByTitle("Status: SUCCESS")).toHaveTextContent("SUCCESS");
		expect(screen.getByTitle("Status: SUCCESS").textContent).not.toContain("✅");
	});
});
