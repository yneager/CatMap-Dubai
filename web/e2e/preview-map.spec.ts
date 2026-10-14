import { test, expect, type Locator } from '@playwright/test';
import { account } from './helpers';
type MapHost = HTMLElement & { catmapMap: { getZoom(): number; isStyleLoaded(): boolean } };
async function mapReady(host: Locator) {
  await expect(host).toBeVisible();
  expect((await host.boundingBox())!.height).toBeGreaterThan(300);
  const canvas = host.locator('canvas.maplibregl-canvas');
  await expect(canvas).toBeVisible();
  const size = await canvas.boundingBox();
  expect(size!.width).toBeGreaterThan(0);
  expect(size!.height).toBeGreaterThan(0);
  await expect
    .poll(() => host.evaluate((element) => (element as MapHost).catmapMap?.isStyleLoaded()))
    .toBe(true);
}
test('production CSS keeps full and compact maps visible and preserves map/photo zoom', async ({
  page,
}) => {
  // Exercise the real bundled MapLibre/worker with a deterministic style, independent of tile-provider uptime.
  await page.route('https://tiles.openfreemap.org/styles/**', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({
        version: 8,
        glyphs: 'http://127.0.0.1:4318/test-fonts/{fontstack}/{range}.pbf',
        sources: {},
        layers: [
          { id: 'background', type: 'background', paint: { 'background-color': '#d7e9ec' } },
        ],
      }),
    }),
  );
  await page.route('**/test-fonts/**', (route) =>
    route.fulfill({ status: 200, body: Buffer.alloc(0) }),
  );
  const user = await account();
  await page.goto('/login');
  await page.getByLabel('Email or username', { exact: true }).fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page).toHaveURL(/\/map$/);
  const map = page.locator('.map-canvas');
  await mapReady(map);
  await page.bringToFront();
  const before = await map.evaluate((element) => (element as MapHost).catmapMap.getZoom());
  const bounds = (await map.boundingBox())!;
  await page.evaluate(
    () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
  );
  const tapPoint = await map.evaluate((element) => {
    const host = element as MapHost & {
      catmapMap: MapHost['catmapMap'] & {
        queryRenderedFeatures(point: [number, number]): unknown[];
      };
    };
    const rect = element.getBoundingClientRect();
    for (let y = Math.min(400, rect.height - 80); y < rect.height - 60; y += 70)
      for (let x = 70; x < rect.width - 90; x += 70)
        if (
          document
            .elementFromPoint(rect.x + x, rect.y + y)
            ?.classList.contains('maplibregl-canvas') &&
          !host.catmapMap.queryRenderedFeatures([x, y]).length
        )
          return { x, y };
    throw new Error('No unobstructed map canvas point for the touch test');
  });
  await page.touchscreen.tap(bounds.x + tapPoint.x, bounds.y + tapPoint.y);
  // Keep the two touch sequences within a human double-tap interval, with a render frame between them.
  await page.waitForTimeout(100);
  await page.touchscreen.tap(bounds.x + tapPoint.x, bounds.y + tapPoint.y);
  await expect
    .poll(() => map.evaluate((element) => (element as MapHost).catmapMap.getZoom()))
    .toBeGreaterThan(before + 0.5);
  const rules = await page.evaluate(() => {
    const outsideTouch = new Event('touchstart', { bubbles: true });
    Object.defineProperty(outsideTouch, 'touches', { value: [{ clientX: 180, clientY: 200 }] });
    document.body.dispatchEvent(outsideTouch);
    return {
      supported: CSS.supports('overscroll-behavior', 'none'),
      html: getComputedStyle(document.documentElement).getPropertyValue('overscroll-behavior'),
      body: getComputedStyle(document.body).getPropertyValue('overscroll-behavior'),
      pageScroll: document.scrollingElement!.scrollHeight <= innerHeight,
      gesture: !document.dispatchEvent(
        new Event('gesturestart', { cancelable: true, bubbles: true }),
      ),
    };
  });
  expect(rules.pageScroll).toBe(true);
  expect(rules.gesture).toBe(true);
  const browserMenus = await page.evaluate(() => {
    const wheel = new WheelEvent('wheel', { ctrlKey: true, cancelable: true, bubbles: true });
    const key = new KeyboardEvent('keydown', {
      key: '+',
      ctrlKey: true,
      cancelable: true,
      bubbles: true,
    });
    const menu = new Event('contextmenu', { cancelable: true, bubbles: true });
    const input = document.createElement('input');
    document.body.append(input);
    const fieldMenu = input.dispatchEvent(
      new Event('contextmenu', { cancelable: true, bubbles: true }),
    );
    input.remove();
    return {
      wheel: !document.body.dispatchEvent(wheel),
      key: !document.body.dispatchEvent(key),
      menu: !document.body.dispatchEvent(menu),
      fieldMenu,
    };
  });
  expect(browserMenus).toEqual({ wheel: true, key: true, menu: true, fieldMenu: true });
  if (rules.supported) {
    expect(rules.html).toBe('none');
    expect(rules.body).toBe('none');
  } else
    test.info().annotations.push({
      type: 'Engine limitation',
      description:
        'This WebKit build has no overscroll-behavior support. Fixed frame and touch-edge prevention are tested instead.',
    });
  expect(
    await page.evaluate(() => {
      const send = (type: string, y: number) => {
        const event = new Event(type, { cancelable: true, bubbles: true });
        Object.defineProperty(event, 'touches', { value: [{ clientX: 180, clientY: y }] });
        return document.body.dispatchEvent(event);
      };
      send('touchstart', 200);
      return !send('touchmove', 230);
    }),
  ).toBe(true);
  await page.goto('/post-new?category=lost');
  await expect(page.getByRole('heading', { name: 'Where & when', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Choose location', exact: true }).click();
  await mapReady(page.locator('dialog .map-canvas'));
  await page.getByRole('button', { name: 'Use this location', exact: true }).click();
  for (const input of await page.locator('input,textarea,select').all())
    expect(
      await input.evaluate((element) => parseFloat(getComputedStyle(element).fontSize)),
    ).toBeGreaterThanOrEqual(16);
  await page.goto('/photo/missing-test-photo');
  const viewer = page.locator('.zoom-viewer');
  await expect(viewer).toBeVisible();
  await viewer.dblclick();
  await expect(viewer.locator(':scope > div')).toHaveAttribute('style', /scale\(2\)/);
  // Pointer-touch pinch follows the same browser path used by the viewer on iPhone.
  await viewer.evaluate((element) => {
    for (const [type, id, x] of [
      ['pointerdown', 1, 100],
      ['pointerdown', 2, 200],
      ['pointermove', 2, 270],
      ['pointerup', 1, 100],
      ['pointerup', 2, 270],
    ] as const) {
      const event = new PointerEvent(type, {
        pointerId: id,
        clientX: x,
        clientY: 200,
        pointerType: 'touch',
        bubbles: true,
      });
      // Synthetic pointers aren't registered as active hardware pointers for capture.
      element.setPointerCapture = () => {};
      element.dispatchEvent(event);
    }
  });
  await expect(viewer.locator(':scope > div')).toHaveAttribute('style', /scale\(3\.4/);
});
test('edge back follows the leading edge in English and Arabic; phone insets stay clear', async ({
  page,
}) => {
  const user = await account();
  await page.goto('/login');
  await page.getByLabel('Email or username', { exact: true }).fill(user.email);
  await page.getByLabel('Password', { exact: true }).fill(user.password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page).toHaveURL(/\/map$/);
  await page.goto('/profile');
  for (const rtl of [false, true]) {
    await page.locator('a[href="/settings"]').click();
    await expect(page).toHaveURL(/\/settings$/);
    await expect(page.locator('.route-screen')).toHaveAttribute('data-screen', '/settings');
    await expect(page.locator('.topbar h1')).toBeVisible();
    await page.evaluate(
      () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
    );
    if (rtl)
      await page
        .getByRole('button', { name: '\u0627\u0644\u0639\u0631\u0628\u064a\u0629', exact: true })
        .click();
    await page.locator('.app').evaluate((element, rtl) => {
      const bounds = element.getBoundingClientRect(),
        x = rtl ? bounds.right - 2 : bounds.left + 2;
      const send = (type: string, dx: number) => {
        const event = new Event(type, { bubbles: true, cancelable: true });
        Object.defineProperty(event, 'touches', {
          value: type === 'touchend' ? [] : [{ clientX: x + dx, clientY: 220 }],
        });
        element.dispatchEvent(event);
      };
      send('touchstart', 0);
      send('touchmove', rtl ? -130 : 130);
    }, rtl);
    await expect(page.locator('.app')).toHaveClass(/edge-dragging/);
    await page
      .locator('.app')
      .evaluate((element) => element.dispatchEvent(new Event('touchend', { bubbles: true })));
    await expect(page).toHaveURL(/\/profile$/);
  }
  // Desktop WebKit has no physical notch; emulate its insets explicitly and retain both phone renders.
  await page.addStyleTag({ content: ':root{--safe-top:59px;--safe-bottom:34px}' });
  for (const [width, height] of [
    [393, 852],
    [430, 932],
  ]) {
    await page.setViewportSize({ width, height });
    const header = await page.locator('.topbar h1').boundingBox();
    expect(header!.y).toBeGreaterThanOrEqual(59);
    const nav = await page.locator('.bottom-nav .nav-tab').first().boundingBox();
    expect(nav!.y + nav!.height).toBeLessThanOrEqual(height - 34 + 1);
    await page.screenshot({
      path: `round2-results/phone-${width}x${height}-${test.info().project.name}.png`,
      animations: 'disabled',
    });
  }
});
