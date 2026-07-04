import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import reactPlugin from "eslint-plugin-react";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";

export default tseslint.config(
	// Ignore build artefacts
	{ ignores: ["dist", "node_modules", "coverage"] },

	// Base JS recommended rules
	js.configs.recommended,

	// TypeScript type-aware rules
	...tseslint.configs.recommendedTypeChecked,

	// React-specific rules
	{
		files: ["src/**/*.{ts,tsx}"],
		plugins: {
			react: reactPlugin,
			"react-hooks": reactHooks,
			"react-refresh": reactRefresh,
		},
		languageOptions: {
			globals: globals.browser,
			parserOptions: {
				project: true,
				tsconfigRootDir: import.meta.dirname,
			},
		},
		settings: {
			react: { version: "detect" },
		},
		rules: {
			// ── React ──────────────────────────────────────────────────────────────
			...reactPlugin.configs.recommended.rules,
			...reactHooks.configs.recommended.rules,
			"react/react-in-jsx-scope": "off", // not needed with react-jsx transform
			"react/prop-types": "off", // TypeScript handles prop types
			"react-refresh/only-export-components": ["warn", { allowConstantExport: true }],

			// ── TypeScript ─────────────────────────────────────────────────────────
			"@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_", varsIgnorePattern: "^_" }],
			"@typescript-eslint/no-explicit-any": "error",
			"@typescript-eslint/consistent-type-imports": ["error", { prefer: "type-imports" }],
			"@typescript-eslint/no-floating-promises": "error",
			"@typescript-eslint/no-misused-promises": ["error", { checksVoidReturn: { attributes: false } }],

			// ── General quality ────────────────────────────────────────────────────
			"no-console": ["warn", { allow: ["warn", "error"] }],
			eqeqeq: ["error", "always"],
		},
	},

	// Relax type-checking rules for test files (they use vi.mock patterns)
	{
		files: ["src/test/**/*.{ts,tsx}"],
		rules: {
			"@typescript-eslint/no-unsafe-assignment": "off",
			"@typescript-eslint/no-unsafe-call": "off",
			"@typescript-eslint/no-unsafe-member-access": "off",
			"@typescript-eslint/no-explicit-any": "off",
		},
	},
);
