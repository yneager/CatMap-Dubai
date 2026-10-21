import { readFileSync } from 'node:fs';
import { test, expect } from '@playwright/test';
test.setTimeout(60000);

test('canvas photo processing removes an injected EXIF block from full and thumbnail JPEGs', async ({
  page,
}) => {
  await page.goto('/e2e/fixtures/hardening.html');
  await expect(page.locator('[data-notification]')).toBeVisible();
  const jpeg = readFileSync('../tools/testdata/orange_tabby_1.jpg');
  // APP1 marker containing an EXIF/GPS-like payload: the image still decodes normally.
  const payload = Buffer.from('Exif\0\0GPS 25.2048,55.2708 private-location-test');
  const marker = Buffer.alloc(4);
  marker[0] = 0xff;
  marker[1] = 0xe1;
  marker.writeUInt16BE(payload.length + 2, 2);
  const input = Buffer.concat([jpeg.subarray(0, 2), marker, payload, jpeg.subarray(2)]).toString(
    'base64',
  );
  const result = await page.evaluate(async (base64) => {
    const { processPhoto } = await import('/src/util/images.ts' /* @vite-ignore */);
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    const photo = await processPhoto(new Blob([bytes], { type: 'image/jpeg' }));
    return [photo.full, photo.thumb].map((data) => ({
      size: data.length,
      header: Array.from(data.slice(0, 3)),
      exif: new TextDecoder('latin1').decode(data).includes('Exif\0\0'),
      gps: new TextDecoder('latin1').decode(data).includes('private-location-test'),
    }));
  }, input);
  expect(result[0].size).toBeLessThan(1000000);
  expect(result[1].size).toBeLessThan(200000);
  for (const p of result) {
    expect(p.header).toEqual([255, 216, 255]);
    expect(p.exif).toBe(false);
    expect(p.gps).toBe(false);
  }
});
test('untrusted notification/post text remains plain text in the browser', async ({ page }) => {
  await page.goto('/e2e/fixtures/hardening.html');
  await expect(page.getByText('<img src=x onerror=alert(1)>', { exact: true })).toBeVisible();
  await expect(page.locator('[data-notification]')).toContainText('<b>plain</b>');
  await expect(page.locator('#root script, #root img, #root b')).toHaveCount(0);
});
test('an interrupted wipe clears device data before the login screen starts', async ({ page }) => {
  await page.goto('/e2e/fixtures/hardening.html');
  await expect(page.locator('[data-notification]')).toBeVisible();
  await page.evaluate(async () => {
    const idb = await import('/src/util/idb.ts' /* @vite-ignore */);
    await idb.put('map_previous', { posts: ['private-post'] });
    await idb.put('draft_previous', { text: 'private-draft' });
    await idb.put('delivered_previous', ['private-notification']);
    await idb.put('image_private', new Uint8Array([1, 2, 3]));
    await idb.put('settings', { language: 'en', theme: 'dark', filters: { layers: ['lost'] } });
    localStorage.setItem('area_follow_private_previous', '1');
    localStorage.setItem('stale_check_previous', '1');
    localStorage.setItem('install-gate-test-state', 'keep');
    localStorage.setItem('catmap_pending_wipe', '1');
  });
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Log in', exact: true })).toBeVisible();
  const saved = await page.evaluate(async () => {
    const idb = await import('/src/util/idb.ts' /* @vite-ignore */);
    return {
      entries: await Promise.all(
        ['map_previous', 'draft_previous', 'delivered_previous', 'image_private'].map((k) =>
          idb.get(k),
        ),
      ),
      settings: await idb.get('settings'),
      keys: Object.keys(localStorage),
    };
  });
  expect(saved.entries).toEqual([undefined, undefined, undefined, undefined]);
  expect(saved.settings).toMatchObject({ language: 'en', theme: 'dark', filters: { layers: [] } });
  expect(saved.keys).toContain('install-gate-test-state');
  expect(saved.keys).not.toContain('catmap_pending_wipe');
  expect(saved.keys).not.toContain('area_follow_private_previous');
});
