import { test, expect } from '@playwright/test';

test('the privacy policy and terms load and are linked from sign-up', async ({ page }) => {
  for (const [path, heading] of [
    ['/privacy.html', 'Privacy policy'],
    ['/terms.html', 'Terms of use'],
  ] as const) {
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
  }
  // A full page load: the legal pages are outside the app, so in-app navigation can't start from them.
  await page.goto('/register');
  await expect(
    page.getByText('By creating an account, you agree to the terms of use'),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'Terms of use' })).toHaveAttribute(
    'href',
    '/terms.html',
  );
  await expect(page.getByRole('link', { name: 'Privacy policy' })).toHaveAttribute(
    'href',
    '/privacy.html',
  );
});
