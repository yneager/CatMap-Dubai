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
      use: { ...devices['Desktop Chrome'], viewport: { width: 393, height: 852 } },
    },
    { name: 'webkit', use: { ...devices['iPhone 15'], viewport: { width: 393, height: 852 } } },
  ],
  webServer: {
    command: 'npm run dev:emulators',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 60000,
  },
});
