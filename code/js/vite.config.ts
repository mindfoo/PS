import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
	plugins: [react()],
	server: {
		proxy: {
			"/api": {
				target: "http://localhost:8080",
			},
		},
	},
	test: {
		globals: true,
		environment: "jsdom",
		setupFiles: "./src/test/setup.ts",
		exclude: ["**/node_modules/**", "**/tests/**", "**/*.spec.ts"],
		coverage: {
			provider: "v8",
			reporter: ["text", "html", "lcov"],
			thresholds: { lines: 80 },
			exclude: [
				"src/main.tsx",
				"src/vite-env.d.ts",
				"vite.config.ts",
				"src/test/**",
			],
		},
	},
});
