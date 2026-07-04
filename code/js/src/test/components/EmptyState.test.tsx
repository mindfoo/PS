import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "../../components/EmptyState";

describe("EmptyState", () => {
	it("renders default message", () => {
		render(<EmptyState />);
		expect(screen.getByText("Nothing here yet.")).toBeInTheDocument();
	});

	it("renders custom message", () => {
		render(<EmptyState message="No tasks found." />);
		expect(screen.getByText("No tasks found.")).toBeInTheDocument();
	});

	it("renders action when provided", () => {
		render(<EmptyState action={<button>Create</button>} />);
		expect(screen.getByRole("button", { name: "Create" })).toBeInTheDocument();
	});

	it("omits action when not provided", () => {
		render(<EmptyState message="Empty." />);
		expect(screen.queryByRole("button")).not.toBeInTheDocument();
	});

	it("has empty-state class", () => {
		const { container } = render(<EmptyState />);
		expect(container.firstChild).toHaveClass("empty-state");
	});
});
