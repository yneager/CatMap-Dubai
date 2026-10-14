import js from '@eslint/js';
import ts from 'typescript-eslint';
import hooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
export default ts.config(
  {
    ignores: [
      'dist/**',
      'dev-dist/**',
      'node_modules/**',
      'playwright-report/**',
      'test-results/**',
      'screen-test-results/**',
      'pwa-test-results/**',
      'webkit-recheck/**',
      'chrome-audit/**',
      'src/icons/generated/**',
    ],
  },
  js.configs.recommended,
  ...ts.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': hooks },
    rules: {
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
  { files: ['**/*.mjs', '*.js'], languageOptions: { globals: globals.node } },
);
