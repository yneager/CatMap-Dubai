import { test, expect } from '@playwright/test';
import { account } from './helpers';
import { preview } from 'vite';
test.skip(
  process.env.CATMAP_PWA !== 'true',
  'Requires a production emulator build on vite preview',
);
test('production manifest, service worker, persistent login and offline app shell', async ({
  page,
  context,
  browserName,
}) => {
  const server = await preview({ preview: { host: '127.0.0.1', port: 4317, strictPort: true } });
  const origin = 'http://127.0.0.1:4317';
  let stopped = false;
  try {
    const manifestResponse = await page.request.get(origin + '/manifest.webmanifest');
    expect(manifestResponse.ok()).toBe(true);
    const manifest = await manifestResponse.json();
    expect(manifest.name).toBe('CatMap Dubai');
    expect(manifest.display).toBe('standalone');
    expect(manifest.start_url).toBe('/map');
    expect(manifest.icons.some((icon: { purpose?: string }) => icon.purpose === 'maskable')).toBe(
      true,
    );
    const a = await account();
    await page.goto(origin + '/login');
    await page.getByLabel('Email or username', { exact: true }).fill(a.email);
    await page.getByLabel('Password', { exact: true }).fill(a.password);
    await page.getByRole('button', { name: 'Log in', exact: true }).click();
    await expect(page).toHaveURL(/\/map$/);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();
    await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);
    await page.getByRole('link', { name: 'Explore', exact: true }).click();
    await expect(page).toHaveURL(/\/explore$/);
    await expect(page.getByRole('link', { name: 'Profile', exact: true })).toBeVisible();
    const entries = await page.evaluate(async () => {
      const keys = await caches.keys();
      const precache = keys.find((key) => key.includes('precache'));
      return precache ? (await (await caches.open(precache)).keys()).length : 0;
    });
    expect(entries).toBeGreaterThan(50);
    // Playwright #42775 rejects even literal SW responses with WebKit setOffline.
    // Stopping this test's own origin verifies a real unavailable server instead.
    if ('closeAllConnections' in server.httpServer) server.httpServer.closeAllConnections();
    await new Promise<void>((resolve, reject) =>
      server.httpServer.close((error) => (error ? reject(error) : resolve())),
    );
    stopped = true;
    if (browserName === 'chromium') await context.setOffline(true);
    await page.reload();
    await expect(page.getByRole('link', { name: 'Profile', exact: true })).toBeVisible();
    if (browserName === 'chromium')
      await expect(page.getByRole('status').filter({ hasText: /offline/i })).toBeVisible();
    await context.setOffline(false);
    console.log(
      `Production PWA: ${entries} cached assets; login and Explore survive reload with origin stopped (${browserName})`,
    );
  } finally {
    if (!stopped) {
      if ('closeAllConnections' in server.httpServer) server.httpServer.closeAllConnections();
      await new Promise<void>((resolve) => server.httpServer.close(() => resolve()));
    }
  }
});
