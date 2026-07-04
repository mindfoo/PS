import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { RegisterPage } from "../../pages/RegisterPage";

const mockNavigate = vi.fn();
const mockRegister = vi.fn();

vi.mock("../../api/auth", () => ({
	authApi: { register: (...args: unknown[]) => mockRegister(...args) as unknown },
}));

vi.mock("react-router-dom", async (importOriginal: () => Promise<Record<string, unknown>>) => {
	const actual = await importOriginal();
	return { ...actual, useNavigate: () => mockNavigate };
});

describe("RegisterPage", () => {
	beforeEach(() => vi.clearAllMocks());

	function renderPage() {
		return render(
			<MemoryRouter>
				<RegisterPage />
			</MemoryRouter>,
		);
	}

	it("renders username and password fields", () => {
		renderPage();
		expect(screen.getByPlaceholderText("username")).toBeInTheDocument();
		expect(screen.getByPlaceholderText("••••••••")).toBeInTheDocument();
	});

	it("navigates to /login on successful registration", async () => {
		mockRegister.mockResolvedValue(undefined);
		renderPage();

		await userEvent.type(screen.getByPlaceholderText("username"), "newuser");
		await userEvent.type(screen.getByPlaceholderText("••••••••"), "Secret1!");
		fireEvent.submit(screen.getByRole("button", { name: /create account/i }).closest("form")!);

		await waitFor(() => {
			expect(mockRegister).toHaveBeenCalledWith({ username: "newuser", password: "Secret1!" });
			expect(mockNavigate).toHaveBeenCalledWith("/login");
		});
	});

	it("shows error message when registration fails", async () => {
		mockRegister.mockRejectedValue(new Error("Username taken"));
		renderPage();

		await userEvent.type(screen.getByPlaceholderText("username"), "alice");
		await userEvent.type(screen.getByPlaceholderText("••••••••"), "pw");
		fireEvent.submit(screen.getByRole("button", { name: /create account/i }).closest("form")!);

		await waitFor(() => {
			expect(screen.getByText("Username taken")).toBeInTheDocument();
		});
	});

	it("disables button while submitting", async () => {
		mockRegister.mockReturnValue(new Promise(() => {}));
		renderPage();

		await userEvent.type(screen.getByPlaceholderText("username"), "alice");
		await userEvent.type(screen.getByPlaceholderText("••••••••"), "pw");
		fireEvent.submit(screen.getByRole("button", { name: /create account/i }).closest("form")!);

		await waitFor(() => {
			expect(screen.getByRole("button", { name: /creating/i })).toBeDisabled();
		});
	});
});
