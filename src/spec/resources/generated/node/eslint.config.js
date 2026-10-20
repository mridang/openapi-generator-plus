const eslint = require("@eslint/js");
const tseslint = require("typescript-eslint");

module.exports = [
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // Build output only. No generated or hand-written source is excluded from
    // the linter: every rule above applies to every file in src/ and test/.
    ignores: ["dist/**", "node_modules/**", "coverage/**", ".out/**"],
  },
  {
    // This file is the flat config itself. ESLint loads it with `require`, so
    // it is CommonJS where the rest of the package is ESM; it is still linted,
    // with only the one rule that exists to keep ESM sources ESM named off.
    files: ["eslint.config.js"],
    languageOptions: {
      sourceType: "commonjs",
      globals: { module: "writable", require: "readonly" },
    },
    rules: {
      "@typescript-eslint/no-require-imports": "off",
    },
  },
];
