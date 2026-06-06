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
      // These rules are disabled here, at the single config home, rather than
      // with inline suppressions in the generated sources. They cannot apply
      // to generated code:
      //   - no-explicit-any: deserialized API payloads and dynamic dispatch
      //     legitimately surface `any` at the transport boundary.
      //   - no-unused-vars / no-unused-vars (core): generators emit complete
      //     parameter lists and imports per the spec, some unused per operation.
      //   - no-require-imports: CommonJS interop in emitted config/helpers.
      //   - no-undef: redundant for TypeScript, which already checks bindings.
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
      '@typescript-eslint/no-require-imports': 'off',
      'no-unused-vars': 'off',
      'no-undef': 'off'
    }
  },
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', '.out/**', 'eslint.config.js']
  }
];
