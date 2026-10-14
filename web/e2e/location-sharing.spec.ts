import { test, expect } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import { account, login, navigate, write } from './helpers';

test('GPS distance filter stays shared between Map and Explore', async ({ page, context }) => {
  await context.grantPermissions(['geolocation'], { origin: 'http://127.0.0.1:5173' });
  await context.setGeolocation({ latitude: 25.2048, longitude: 55.2708, accuracy: 5 });
  const a = await account();
  await login(page, a);
  const suffix = randomBytes(4).toString('hex');
  const fixtures = await page.evaluate(
    async ({ uid, username, suffix }) => {
      const { PostDefaults, PostToDoc } = await import(
        '/src/data/model/index.ts' /* @vite-ignore */
      );
      return ['near', 'far'].map((kind) => ({
        id: 'gps-' + kind + '-' + suffix,
        data: PostToDoc({
          ...PostDefaults,
          ownerId: uid,
          ownerName: username,
          name: 'GPS ' + kind + ' ' + suffix,
          lat: kind === 'near' ? 25.20481 : 25.3048,
          lng: 55.27081,
          createdAt: Date.now(),
          lastSeenAt: Date.now(),
          updatedAt: Date.now(),
        }),
      }));
    },
    { uid: a.uid, username: a.username, suffix },
  );
  for (const fixture of fixtures) await write('posts/' + fixture.id, fixture.data);
  await page.getByRole('button', { name: 'My location', exact: true }).click();
  await page.getByRole('button', { name: 'Filters', exact: true }).click();
  await expect(page.getByText('Location is needed', { exact: false })).not.toBeVisible();
  await page.getByRole('button', { name: 'Within 1 km', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Close', exact: true }).click();
  await navigate(page, '/explore');
  await expect(
    page.getByRole('heading', { name: 'GPS near ' + suffix, exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole('heading', { name: 'GPS far ' + suffix, exact: true }),
  ).not.toBeVisible();
});
