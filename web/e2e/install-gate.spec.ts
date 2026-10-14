import { test, expect } from '@playwright/test';
const iphone =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1';
test('iPhone gate keeps a deep link, shows Share instructions and loads no Firebase client', async ({
  browser,
}) => {
  const context = await browser.newContext({
    userAgent: iphone,
    viewport: { width: 393, height: 852 },
    hasTouch: true,
  });
  const page = await context.newPage();
  const firebase: string[] = [];
  page.on('request', (request) => {
    if (/firestore|identitytoolkit|securetoken|\/src\/firebase\.ts/.test(request.url()))
      firebase.push(request.url());
  });
  await page.goto('http://127.0.0.1:5174/post/shared-link');
  await expect(page.locator('[data-install-gate]')).toBeVisible();
  await expect(page.getByText('Open the Share menu.', { exact: true })).toBeVisible();
  await expect(page.getByText('Keep Open as Web App switched on.', { exact: true })).toBeVisible();
  await expect(page.locator('input[type=password]')).toHaveCount(0);
  await expect(page).toHaveURL(/\/post\/shared-link$/);
  await page.waitForLoadState('networkidle');
  expect(firebase).toEqual([]);
  await page.addStyleTag({ content: ':root{--safe-top:59px;--safe-bottom:34px}' });
  for (const [width, height] of [
    [393, 852],
    [430, 932],
  ]) {
    await page.setViewportSize({ width, height });
    expect((await page.locator('.install-preferences').boundingBox())!.y).toBeGreaterThanOrEqual(
      59,
    );
    await page.screenshot({
      path: `round2-results/install-${width}x${height}-${test.info().project.name}.png`,
      animations: 'disabled',
    });
  }
  await context.close();
});
test('Android captures the install prompt and waits for a home-screen launch', async ({
  browser,
}) => {
  const context = await browser.newContext({
    userAgent:
      'Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140.0.0.0 Mobile Safari/537.36',
  });
  const page = await context.newPage();
  await page.goto('http://127.0.0.1:5174/map');
  await expect(page.locator('[data-install-gate]')).toBeVisible();
  await page.evaluate(() => {
    const event = new Event('beforeinstallprompt', { cancelable: true });
    Object.assign(event, {
      prompt: async () => {
        document.documentElement.dataset.prompted = 'true';
      },
      userChoice: Promise.resolve({ outcome: 'accepted' }),
    });
    dispatchEvent(event);
  });
  await page.getByRole('button', { name: 'Install CatMap', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-prompted', 'true');
  await page.evaluate(() => dispatchEvent(new Event('appinstalled')));
  await expect(page.getByRole('status')).toHaveText(
    'Installed. Open CatMap from your home screen.',
  );
  await expect(page.locator('input[type=password]')).toHaveCount(0);
  await context.close();
});
test('standalone opens login while an in-app browser gives a Safari handoff', async ({
  browser,
}) => {
  const installed = await browser.newContext({ userAgent: iphone });
  await installed.addInitScript(() => {
    const original = window.matchMedia.bind(window);
    window.matchMedia = (query) => {
      const media = original(query);
      if (query === '(display-mode: standalone)')
        Object.defineProperty(media, 'matches', { value: true });
      return media;
    };
  });
  const page = await installed.newPage();
  await page.goto('http://127.0.0.1:5174/login');
  await expect(page.getByLabel('Email or username', { exact: true })).toBeVisible();
  await expect(page.locator('[data-install-gate]')).toHaveCount(0);
  await installed.close();
  const embedded = await browser.newContext({
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148',
  });
  const webview = await embedded.newPage();
  await webview.goto('http://127.0.0.1:5174/map');
  await expect(
    webview.getByText('Open this page in Safari to install CatMap.', { exact: true }),
  ).toBeVisible();
  await expect(webview.getByRole('button', { name: 'Copy link', exact: true })).toBeVisible();
  await embedded.close();
});
