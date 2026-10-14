import { defineConfig } from 'vitest/config';
export default defineConfig({
  test: { include: ['tests/**/*.test.ts'], environment: 'node', restoreMocks: true },
  define: { 'import.meta.env.VITE_USE_EMULATORS': '"false"' },
});
