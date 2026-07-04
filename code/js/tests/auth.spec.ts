import { test, expect } from "@playwright/test";

test.describe("Auth flow", () => {
	test("login page renders and shows sign-in form", async ({ page }) => {
		await page.goto("/login");
		await expect(page.getByPlaceholder("username")).toBeVisible();
		await expect(page.getByPlaceholder("••••••••")).toBeVisible();
		await expect(page.getByRole("button", { name: /sign in/i })).toBeVisible();
	});

	test("shows error on invalid credentials", async ({ page }) => {
		await page.goto("/login");
		await page.getByPlaceholder("username").fill("nobody");
		await page.getByPlaceholder("••••••••").fill("wrongpassword");
		await page.getByRole("button", { name: /sign in/i }).click();
		// Expect an error message to appear (exact text depends on server response)
		await expect(page.locator(".alert-error")).toBeVisible({ timeout: 5000 });
	});

	test("register page is accessible from login page", async ({ page }) => {
		await page.goto("/login");
		await page.getByRole("link", { name: /register/i }).click();
		await expect(page).toHaveURL(/register/);
		await expect(page.getByRole("button", { name: /create account/i })).toBeVisible();
	});
});
