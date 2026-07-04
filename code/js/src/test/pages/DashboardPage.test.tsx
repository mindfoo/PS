import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { DashboardPage } from "../../pages/DashboardPage";

const mockList = vi.fn();
const mockDelete = vi.fn();
const mockRun = vi.fn();

const sampleWorkflows = [
	{
		id: "wf1",
		name: "Pipeline A",
		ownerId: "u1",
		ownerUsername: "alice",
		lastRunStatus: "SUCCESS",
		isPrivate: false,
	},
	{
		id: "wf2",
		name: "Nightly Job",
		ownerId: "u1",
		ownerUsername: "alice",
		lastRunStatus: null,
		isPrivate: false,
	},
];

vi.mock("../../contexts/AuthContext", () => ({
	useAuth: () => ({ user: { id: "u1", username: "alice", role: "WRITER" }, loading: false }),
	usePermissions: () => ({
		canReadWorkflows: true,
		canWriteWorkflows: true,
		canDeleteWorkflows: true,
		canExecuteWorkflows: true,
		isReader: false,
	}),
}));

vi.mock("../../api/workflows", () => ({
	workflowApi: {
		list: (...args: unknown[]) => mockList(...args) as unknown,
		delete: (...args: unknown[]) => mockDelete(...args) as unknown,
		run: (...args: unknown[]) => mockRun(...args) as unknown,
	},
}));

vi.mock("../../components/Layout", () => ({
	Layout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

describe("DashboardPage", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockList.mockResolvedValue(sampleWorkflows);
	});

	function renderPage() {
		return render(
			<MemoryRouter>
				<DashboardPage />
			</MemoryRouter>,
		);
	}

	it("renders workflow names after load", async () => {
		renderPage();
		await waitFor(() => {
			expect(screen.getByText("Pipeline A")).toBeDefined();
			expect(screen.getByText("Nightly Job")).toBeDefined();
		});
	});

	it("shows loading spinner initially", () => {
		mockList.mockReturnValue(
			new Promise(() => {
				/* never resolves */
			}),
		);
		renderPage();
		expect(screen.queryByText("Pipeline A")).toBeNull();
	});

	it("shows error message when list fails", async () => {
		mockList.mockRejectedValue(new Error("Server error"));
		renderPage();
		await waitFor(() => {
			expect(screen.getByText(/Server error/i)).toBeDefined();
		});
	});

	it("shows empty state when no workflows exist", async () => {
		mockList.mockResolvedValue([]);
		renderPage();
		await waitFor(() => {
			expect(screen.getByText(/no workflows yet/i)).toBeDefined();
		});
	});

	it("renders + New Workflow button for writer", async () => {
		renderPage();
		await waitFor(() => {
			expect(screen.getByRole("button", { name: /new workflow/i })).toBeDefined();
		});
	});
});
