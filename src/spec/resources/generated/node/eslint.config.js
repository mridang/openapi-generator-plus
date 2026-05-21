const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const tsParser = require('@typescript-eslint/parser');
const tsPlugin = require('@typescript-eslint/eslint-plugin');
const unicorn = require('eslint-plugin-unicorn');

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
      '@typescript-eslint': tsPlugin,
      unicorn
    },
    rules: {
      'unicorn/prefer-node-protocol': 'error'
    }
  },
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', '.out/**']
  }
];
