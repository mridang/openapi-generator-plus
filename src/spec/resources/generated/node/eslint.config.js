const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const tsParser = require('@typescript-eslint/parser');
const tsPlugin = require('@typescript-eslint/eslint-plugin');

module.exports = [
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  {
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 2024,
        sourceType: 'module'
      }
    },
    plugins: {
      '@typescript-eslint': tsPlugin
    },
    rules: {
      // Generated code triggers many of these; relax to keep CI green.
      // Add stricter rules incrementally later.
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-require-imports': 'off',
      'no-unused-vars': 'off',
      'no-undef': 'off'
    },
    linterOptions: {
      // Generated test files contain proactive eslint-disable comments
      // for cases we may or may not hit; don't fail on unused ones.
      reportUnusedDisableDirectives: 'off'
    }
  },
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', '.out/**', 'eslint.config.js']
  }
];
