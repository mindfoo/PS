import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { StatusBadge } from "../../components/StatusBadge";

describe("StatusBadge", () => {
	it.each([
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
		render(<StatusBadge status="UNKNOWN" />);
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
