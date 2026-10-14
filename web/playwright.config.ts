import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: 'e2e',
  timeout: 600000,
  expect: { timeout: 45000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.CATMAP_TEST_URL ?? 'http://127.0.0.1:5173',
    trace: 'retain-on-failure',
    actionTimeout: 45000,
    navigationTimeout: 45000,
    screenshot: 'only-on-failure',
    viewport: { width: 393, height: 852 },
  },
  projects: [
    {
      name: 'chromium',
      testIgnore: ['**/preview-map.spec.ts', '**/install-gate.spec.ts'],
      use: { ...devices['Desktop Chrome'], viewport: { width: 393, height: 852 } },
    },
    {
      name: 'webkit',
      testIgnore: ['**/preview-map.spec.ts', '**/install-gate.spec.ts'],
      use: { ...devices['iPhone 15'], viewport: { width: 393, height: 852 } },
    },
    ...(['chromium', 'webkit'] as const).map((browserName) => ({
      name: browserName + '-preview',
      testMatch: '**/preview-map.spec.ts',
      use: {
        ...devices[browserName === 'webkit' ? 'iPhone 15' : 'Pixel 7'],
        browserName,
        baseURL: 'http://127.0.0.1:4318',
        viewport: { width: 393, height: 852 },
        hasTouch: true,
      },
    })),
    ...(['chromium', 'webkit'] as const).map((browserName) => ({
      name: browserName + '-install',
      testMatch: '**/install-gate.spec.ts',
      use: {
        browserName,
        baseURL: 'http://127.0.0.1:5174',
        viewport: { width: 393, height: 852 },
        hasTouch: true,
      },
    })),
  ],
  webServer: [
    {
      command: 'npm run dev:emulators',
      url: 'http://127.0.0.1:5173',
      reuseExistingServer: true,
      timeout: 60000,
    },
    {
      command: 'node tools/preview-tests.mjs',
      url: 'http://127.0.0.1:4318',
      reuseExistingServer: !process.env.CI,
      timeout: 120000,
    },
    {
      command:
        'node node_modules/vite/bin/vite.js --mode emulators --host 127.0.0.1 --port 5174 --strictPort',
      env: { VITE_INSTALL_GATE: 'on' },
      url: 'http://127.0.0.1:5174',
      reuseExistingServer: !process.env.CI,
      timeout: 60000,
    },
  ],
});
