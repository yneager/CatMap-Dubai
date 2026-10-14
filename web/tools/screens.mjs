import { spawnSync } from 'node:child_process';
const result = spawnSync(
  process.execPath,
  [
    'node_modules/@playwright/test/cli.js',
    'test',
    'e2e/screens.spec.ts',
    '--project=webkit',
    '--output=screen-test-results',
    '--reporter=line',
  ],
  {
    stdio: 'inherit',
    env: { ...process.env, CATMAP_SCREENS: 'true' },
  },
);
process.exitCode = result.status ?? 1;
