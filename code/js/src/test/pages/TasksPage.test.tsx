import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { TasksPage } from "../../pages/TasksPage";

const mockListAll = vi.fn();
const mockDelete = vi.fn();

const sampleTasks = [
	{
		id: "t1",
		name: "Compile code",
		type: "SCRIPT",
		config: {},
		workflowId: null,
		isPrivate: false,
	},
	{ id: "t2", name: "HTTP check", type: "HTTP", config: {}, workflowId: "wf1", isPrivate: false },
];

vi.mock("../../contexts/AuthContext", () => ({
	useAuth: () => ({ user: { id: "u1", username: "alice", role: "WRITER" }, loading: false }),
	usePermissions: () => ({
		canReadTasks: true,
		canWriteTasks: true,
		canDeleteTasks: true,
		isReader: false,
	}),
}));

vi.mock("../../api/tasks", () => ({
	taskApi: {
		listAll: (...args: unknown[]) => mockListAll(...args) as unknown,
		delete: (...args: unknown[]) => mockDelete(...args) as unknown,
	},
	TaskType: { HTTP: "HTTP", SCRIPT: "SCRIPT" },
}));

vi.mock("../../components/Layout", () => ({
	Layout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

describe("TasksPage", () => {
	beforeEach(() => {
		vi.clearAllMocks();
		mockListAll.mockResolvedValue(sampleTasks);
	});

	function renderPage() {
		return render(
			<MemoryRouter>
				<TasksPage />
			</MemoryRouter>,
		);
	}

	it("renders task names after load", async () => {
		renderPage();
		await waitFor(() => {
			expect(screen.getByText("Compile code")).toBeDefined();
			expect(screen.getByText("HTTP check")).toBeDefined();
		});
	});

	it("shows error message when listAll fails", async () => {
		mockListAll.mockRejectedValue(new Error("Unauthorized"));
		renderPage();
		await waitFor(() => {
			expect(screen.getByText(/Unauthorized/i)).toBeDefined();
		});
	});

	it("shows empty state when no tasks exist", async () => {
		mockListAll.mockResolvedValue([]);
		renderPage();
		await waitFor(() => {
			expect(screen.getByText(/no tasks yet/i)).toBeDefined();
		});
	});

	it("renders + New Task button for writer", async () => {
		renderPage();
		await waitFor(() => {
			expect(screen.getByRole("button", { name: /new task/i })).toBeDefined();
		});
	});

	it("renders type badges for tasks", async () => {
		renderPage();
		await waitFor(() => {
			expect(screen.getByText("SCRIPT")).toBeDefined();
			expect(screen.getByText("HTTP")).toBeDefined();
		});
	});
});
