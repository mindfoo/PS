import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { LoginPage } from "../../pages/LoginPage";

/** mock dependencies */

const mockLogin = vi.fn();
const mockNavigate = vi.fn();

vi.mock("../../contexts/AuthContext", () => ({
	useAuth: () => ({ login: mockLogin }),
}));

vi.mock("react-router-dom", async (importOriginal: () => Promise<Record<string, unknown>>) => {
	const actual = await importOriginal();
	return { ...actual, useNavigate: () => mockNavigate };
});

/** tests */

describe("LoginPage", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	function renderPage() {
		return render(
			<MemoryRouter>
				<LoginPage />
			</MemoryRouter>,
		);
	}

	it("renders username and password fields", () => {
		renderPage();
		expect(screen.getByPlaceholderText("username")).toBeInTheDocument();
		expect(screen.getByPlaceholderText("••••••••")).toBeInTheDocument();
	});

	it("renders a Sign in button", () => {
		renderPage();
		expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
	});

	it("calls login with entered credentials and navigates to /dashboard on success", async () => {
		mockLogin.mockResolvedValue(undefined);
		renderPage();

		await userEvent.type(screen.getByPlaceholderText("username"), "alice");
		await userEvent.type(screen.getByPlaceholderText("••••••••"), "Secret1!");
		await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

		await waitFor(() => {
			expect(mockLogin).toHaveBeenCalledWith("alice", "Secret1!");
			expect(mockNavigate).toHaveBeenCalledWith("/dashboard");
		});
	});

	it("shows an error message when login fails", async () => {
		mockLogin.mockRejectedValue(new Error("Invalid credentials"));
		renderPage();

		await userEvent.type(screen.getByPlaceholderText("username"), "alice");
		await userEvent.type(screen.getByPlaceholderText("••••••••"), "wrong");
		await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

		await waitFor(() => {
			expect(screen.getByText("Invalid credentials")).toBeInTheDocument();
		});
	});

	it("disables the button while loading", async () => {
		// login never resolves so loading stays true
		mockLogin.mockReturnValue(new Promise(() => {}));
		renderPage();

		await userEvent.type(screen.getByPlaceholderText("username"), "alice");
		await userEvent.type(screen.getByPlaceholderText("••••••••"), "pw");
		await userEvent.click(screen.getByRole("button", { name: /sign in/i }));

		await waitFor(() => {
			expect(screen.getByRole("button", { name: /signing in/i })).toBeDisabled();
		});
	});
});
