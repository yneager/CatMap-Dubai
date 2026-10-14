import { test, expect } from '@playwright/test';
import { account, login, navigate, pickLocation, photoPath } from './helpers';
import { fakePhotoWorker } from './fake-photo-worker';
test('claimed fake worker blocks a person photo and fills the animal type on a replacement', async ({
  page,
}) => {
  const user = await account();
  await login(page, user);
  let kind = 'person';
  const worker = await fakePhotoWorker(user.uid, () =>
    kind === 'person'
      ? { kind, phrase: 'a selfie or a photo of a person' }
      : { kind, animalType: 'dog', phrase: 'a dog' },
  );
  try {
    await navigate(page, '/post-new?category=street');
    await pickLocation(page);
    await page.getByRole('button', { name: 'Next', exact: true }).click();
    await page.locator('input[type=file][multiple]').setInputFiles(photoPath);
    await expect(page.locator('.photo-check-error')).toContainText(
      'a selfie or a photo of a person',
    );
    await expect(page.getByRole('button', { name: 'Next', exact: true })).toBeDisabled();
    await expect(page.locator('.blocked-photo')).toHaveCount(1);
    const footer = page.locator('.editor-footer');
    await footer.getByRole('button', { name: 'Back', exact: true }).click();
    await footer.getByRole('button', { name: 'Back', exact: true }).click();
    await expect(footer.getByRole('button', { name: 'Next', exact: true })).toBeEnabled();
    await footer.getByRole('button', { name: 'Next', exact: true }).click();
    await footer.getByRole('button', { name: 'Next', exact: true }).click();
    await expect(page.locator('.photo-check-error')).toBeVisible();
    await expect(footer.getByRole('button', { name: 'Next', exact: true })).toBeDisabled();
    kind = 'animal';
    await page.locator('.photo-tile').getByRole('button', { name: 'Remove', exact: true }).click();
    await page.locator('input[type=file][multiple]').setInputFiles(photoPath);
    await expect(page.locator('.photo-verdict')).toHaveText('a dog');
    await expect(page.getByRole('button', { name: 'Next', exact: true })).toBeEnabled();
    await expect(page.locator('.photo-check-error')).toHaveCount(0);
    await page.locator('.editor-footer').getByRole('button', { name: 'Back', exact: true }).click();
    await page.locator('.editor-footer').getByRole('button', { name: 'Back', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Dog', exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(worker.jobs.length).toBeGreaterThanOrEqual(2);
    expect(
      worker.jobs.every((job) => job.kind === 'check' && job.photos <= 6 && job.ids <= 8),
    ).toBe(true);
  } finally {
    await worker.stop();
  }
});
