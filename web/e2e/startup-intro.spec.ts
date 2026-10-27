import { test, expect } from '@playwright/test';

// The rest of the suite runs with reduced motion, which skips the story; these tests play it.
test.use({ reducedMotion: 'no-preference' });

const draws = [
  [0.1, 'original'],
  [0.3, 'rooftop-rescue'],
  [0.6, 'neighborhood-relay'],
  [0.9, 'night-patrol'],
] as const;

for (const [random, story] of draws) {
  test(`plays ${story} once over the loading app, then never again in the session`, async ({
    page,
  }) => {
    await page.addInitScript((value) => {
      Math.random = () => value;
    }, random);
    await page.goto('/');
    const intro = page.locator('.startup-intro');
    await expect(intro).toHaveAttribute('data-story', story);
    await expect(intro.locator('svg')).toBeAttached();
    const shown = Date.now();
    await expect(intro).toHaveCount(0, { timeout: 9000 });
    const played = Date.now() - shown;
    expect(played).toBeGreaterThan(3000);
    expect(played).toBeLessThan(8000);
    // The app started underneath, so it is ready the moment the story ends.
    await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible({ timeout: 1000 });
    await page.reload();
    await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible();
    await expect(intro).toHaveCount(0);
  });
}

test('follows the dark theme and a tap skips it', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.goto('/');
  const intro = page.locator('.startup-intro');
  await expect(intro).toHaveClass(/\bdark\b/);
  await expect(intro.locator('svg')).toBeAttached();
  await intro.click();
  await expect(intro).toHaveCount(0, { timeout: 1000 });
  await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible();
});
