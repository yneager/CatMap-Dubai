import { spawn, spawnSync } from 'node:child_process';
const vite = 'node_modules/vite/bin/vite.js';
const env = { ...process.env, VITE_INSTALL_GATE: 'off', VITE_USE_EMULATORS: 'true' };
const build = spawnSync(
  process.execPath,
  [vite, 'build', '--mode', 'emulators', '--outDir', '.preview-dist'],
  { stdio: 'inherit', env },
);
if (build.status !== 0) process.exit(build.status ?? 1);
const server = spawn(
  process.execPath,
  [
    vite,
    'preview',
    '--outDir',
    '.preview-dist',
    '--host',
    '127.0.0.1',
    '--port',
    '4318',
    '--strictPort',
  ],
  { stdio: 'inherit', env },
);
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.kill());
server.on('exit', (code) => {
  process.exitCode = code ?? 1;
});
