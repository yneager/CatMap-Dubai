import { test, expect } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import {
  account,
  navigate,
  login,
  publishLost,
  verify,
  write,
  document,
  setupUsername,
  waitRateLimit,
  photoPath,
  pickLocation,
  promote,
  collection,
  firestoreBase,
} from './helpers';
test('register, verify email, username login, logout and public help', async ({ page }) => {
  const username = 'reg_' + randomBytes(5).toString('hex'),
    email = username + '@web-test.invalid',
    password = randomBytes(24).toString('base64url');
  await navigate(page, '/register');
  await page.getByLabel('Display name', { exact: true }).fill('Web member');
  await page.getByLabel('Username', { exact: true }).fill(username);
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByLabel('Confirm password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page).toHaveURL(/\/verify$/);
  await verify(email);
  await page.getByRole('button', { name: "I've verified my email", exact: true }).click();
  await expect(page).toHaveURL(/\/map$/);
  const uid = await page.evaluate(async () => {
    const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
    return authentication().currentUser!.uid;
  });
  await setupUsername({ uid, username, email, password });
  await navigate(page, '/settings');
  await page.getByRole('button', { name: 'Log out', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Email or username', { exact: true }).fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page).toHaveURL(/\/map$/);
});
test('lost photo wizard, Explore/map, detail update, reunited, favourites and settings', async ({
  page,
  context,
}) => {
  const a = await account();
  await login(page, a);
  const name = 'Lost ' + randomBytes(3).toString('hex'),
    id = await publishLost(page, name);
  const post = await document('posts/' + id);
  expect(post.name).toBe(name);
  expect((post.photoIds as string[]).length).toBe(1);
  expect(post.areaRadiusM).toBe(0);
  await page
    .getByRole('banner')
    .getByRole('button', { name: 'Save to favourites', exact: true })
    .click();
  await expect(
    page.getByRole('banner').getByRole('button', { name: 'Remove from favourites', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true');
  await expect
    .poll(async () => {
      const items = await collection('users/' + a.uid + '/favorites');
      return items.some((item) => item.fields.postId?.stringValue === id);
    })
    .toBe(true);
  await navigate(page, '/favorites');
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  await navigate(page, '/explore');
  await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();
  await navigate(page, '/map');
  await page.getByRole('button', { name: 'List view', exact: true }).click();
  await expect(page.getByRole('button', { name, exact: true })).toBeVisible();
  await navigate(page, '/post/' + id);
  await waitRateLimit(a.uid);
  await page.getByRole('button', { name: 'Add update', exact: true }).click();
  await page.getByLabel('Note', { exact: true }).fill('Seen near the entrance');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  // The update shows at once from the local write; the form closes when the server has it.
  await expect(
    page.getByRole('article').getByText('Seen near the entrance', { exact: true }),
  ).toBeVisible();
  await expect(page.getByLabel('Note', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Mark as reunited', exact: true }).click();
  // No match to credit: the owner says how it ended (came home on its own), then sees the confirmation.
  await page.locator('.dialog').getByRole('button', { name: 'Mark as reunited', exact: true }).click();
  await expect(page.locator('.celebrate')).toBeVisible();
  await page.locator('.celebrate').getByRole('button', { name: 'Close', exact: true }).click();
  await expect(page.getByText('Reunited', { exact: true })).toBeVisible();
  await expect(page.locator('.reunion-banner')).toContainText('Came home on their own');
  await navigate(page, '/settings');
  await page.getByRole('button', { name: 'Dark', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.getByRole('switch', { name: 'Larger text', exact: true }).check();
  await page
    .getByRole('button', { name: '\u0627\u0644\u0639\u0631\u0628\u064a\u0629', exact: true })
    .click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.locator('.route-screen')).toHaveAttribute('data-screen', '/settings');
  await context.setOffline(true);
  await expect(
    page
      .getByRole('status')
      .filter({ hasText: /offline|\u063a\u064a\u0631 \u0645\u062a\u0635\u0644/ }),
  ).toBeVisible();
  await context.setOffline(false);
});
test('chat between two web users, adoption approval and booking/review', async ({ browser }) => {
  const a = await account(),
    b = await account(),
    ca = await browser.newContext({ viewport: { width: 393, height: 852 } }),
    cb = await browser.newContext({ viewport: { width: 393, height: 852 } });
  const pa = await ca.newPage(),
    pb = await cb.newPage();
  await login(pa, a);
  await login(pb, b);
  await pa.bringToFront();
  await navigate(pa, '/chat-with/' + b.uid);
  await expect(pa).toHaveURL(/\/chat\//);
  await pa.getByRole('textbox', { name: 'Message', exact: true }).fill('Hello from web A');
  await pa.getByRole('button', { name: 'Send', exact: true }).click();
  await expect(pa.getByRole('paragraph').filter({ hasText: /^Hello from web A$/ })).toBeVisible();
  await navigate(pb, '/messages');
  await pb.getByRole('link').filter({ hasText: 'Hello from web A' }).click();
  await expect(pb.getByText('Hello from web A', { exact: true })).toBeVisible();
  await pb.getByRole('textbox', { name: 'Message', exact: true }).fill('Hello from web B');
  await pb.getByRole('button', { name: 'Send', exact: true }).click();
  await expect(pa.getByText('Hello from web B', { exact: true })).toBeVisible();
  const id = await pa.evaluate(async () => {
    const { PostRepository } = await import('/src/data/PostRepository.ts' /* @vite-ignore */),
      { PostDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
    const me = await UserRepository.profile(authentication().currentUser!.uid);
    return PostRepository.create(
      {
        ...PostDefaults,
        name: 'Adopt web cat',
        category: 'adoption',
        description: 'Needs a loving home',
        lat: 25.2048,
        lng: 55.2708,
      },
      me!,
    );
  });
  await navigate(pb, '/post/' + id);
  await pb.getByRole('button', { name: 'Request to adopt', exact: true }).click();
  await pb
    .getByLabel('Why would you like to adopt?', { exact: true })
    .fill('I can provide a loving home.');
  await pb
    .getByLabel('About your home (other pets, space\u2026)', { exact: true })
    .fill('A secure apartment.');
  await pb.getByRole('button', { name: 'Send', exact: true }).click();
  await expect(pb.getByRole('dialog')).not.toBeVisible();
  await navigate(pa, '/adoption-requests');
  await pa.getByRole('button', { name: 'Approve', exact: true }).click();
  await pa.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(pa.getByText('Approved', { exact: true })).toBeVisible();
  await navigate(pa, '/sitter-edit');
  await pa.getByLabel('Headline (e.g. Cat lover in JLT)', { exact: true }).fill('Web sitter');
  await pa.getByLabel('Description', { exact: true }).fill('Experienced sitter for local cats.');
  await pickLocation(pa);
  await pa.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(pa).toHaveURL(/\/sitter\//);
  await navigate(pb, '/book/' + a.uid);
  await pb.getByLabel('Start', { exact: true }).fill('2026-10-20');
  await pb.getByLabel('End', { exact: true }).fill('2026-10-21');
  await pb.getByRole('button', { name: 'Send request', exact: true }).click();
  await expect(pb).toHaveURL(/\/bookings$/);
  await navigate(pa, '/bookings?tab=sitter');
  await pa.getByRole('button', { name: 'Accept', exact: true }).click();
  await pa.getByRole('button', { name: 'Mark completed', exact: true }).click();
  await navigate(pb, '/bookings');
  await pb.getByRole('button', { name: 'Leave a review', exact: true }).click();
  await pb.getByLabel('How did it go?', { exact: true }).fill('Great care');
  await pb.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(pb.getByRole('dialog')).not.toBeVisible();
  const bookings = await collection('bookings');
  const booking = bookings.find((d) => d.fields.ownerId?.stringValue === b.uid)!;
  expect((await document('reviews/' + booking.name.split('/').at(-1))).rating).toBe(5);
  // A slow start-chat response must not navigate after the user leaves that screen.
  for (const fail of [false, true]) {
    await pa.evaluate(async (fail) => {
      const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */);
      const original = ChatRepository.open;
      const pending = window as unknown as { finishChatOpen?: () => void };
      delete pending.finishChatOpen;
      const completions: (() => void)[] = [];
      ChatRepository.open = () =>
        new Promise<string>((resolve, reject) => {
          completions.push(() =>
            fail ? reject(new Error('Delayed test failure')) : resolve('late'),
          );
          pending.finishChatOpen = () => {
            ChatRepository.open = original;
            completions.forEach((finish) => finish());
          };
        });
    }, fail);
    await navigate(pa, '/chat-with/' + b.uid);
    await expect
      .poll(() =>
        pa.evaluate(
          () => typeof (window as unknown as { finishChatOpen?: () => void }).finishChatOpen,
        ),
      )
      .toBe('function');
    await navigate(pa, '/explore');
    await expect(pa.locator('.explore-hero')).toBeVisible();
    await pa.evaluate(async () => {
      (window as unknown as { finishChatOpen: () => void }).finishChatOpen();
      await new Promise(requestAnimationFrame);
      await new Promise(requestAnimationFrame);
    });
    await expect(pa).toHaveURL(/\/explore$/);
    await expect(pa.locator('.explore-hero')).toBeVisible();
  }
  await ca.close();
  await cb.close();
});
test('same street animal uses fake worker and adds sighting instead of a duplicate', async ({
  page,
}) => {
  const a = await account();
  await login(page, a);
  const id = await page.evaluate(async () => {
    const { PostRepository } = await import('/src/data/PostRepository.ts' /* @vite-ignore */),
      { PostDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { ImageRepository } = await import('/src/data/ImageRepository.ts' /* @vite-ignore */),
      { processPhoto } = await import('/src/util/images.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
    const canvas = globalThis.document.createElement('canvas');
    canvas.width = 100;
    canvas.height = 100;
    canvas.getContext('2d')!.fillRect(0, 0, 100, 100);
    const file = await new Promise<Blob>((resolve) => canvas.toBlob((b) => resolve(b!)));
    const imageId = await ImageRepository.upload(me.uid, await processPhoto(file));
    return PostRepository.create(
      {
        ...PostDefaults,
        name: 'Same street cat',
        category: 'street',
        description: 'Orange cat',
        photoIds: [imageId],
        lat: 25.2048,
        lng: 55.2708,
      },
      me,
    );
  });
  await write('config/aiWorker', {
    online: true,
    model: 'test-model',
    provider: 'fake',
    same: 0.75,
    maybe: 0.6,
    lastSeen: 0,
  });
  await fetch(`${firestoreBase}/config/aiWorker`, {
    method: 'PATCH',
    headers: { Authorization: 'Bearer owner', 'Content-Type': 'application/json' },
    body: JSON.stringify({
      fields: {
        online: { booleanValue: true },
        model: { stringValue: 'test-model' },
        provider: { stringValue: 'fake' },
        same: { doubleValue: 0.75 },
        maybe: { doubleValue: 0.6 },
        lastSeen: { timestampValue: new Date().toISOString() },
      },
    }),
  });
  await waitRateLimit(a.uid);
  let workerStopped = false;
  const worker = (async () => {
    while (!workerStopped) {
      try {
        const job = await document('aiJobs/' + a.uid);
        if (job.status === 'pending') {
          await write(
            'aiJobs/' + a.uid,
            {
              status: 'done',
              answeredRequestId: job.requestId,
              results: [{ id, similarity: 0.94, verdict: 'same' }],
              classification: [{ kind: 'animal', animalType: 'cat', phrase: 'a cat', score: 0.95 }],
            },
            ['status', 'answeredRequestId', 'results', 'classification'],
          );
          break;
        }
      } catch {
        /* Job not written yet. */
      }
      await new Promise((r) => setTimeout(r, 100));
    }
  })();
  await navigate(page, '/post-new?category=street');
  await expect(page.getByRole('heading', { name: 'Where & when', exact: true })).toBeVisible();
  await pickLocation(page);
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.locator('input[type=file][multiple]').setInputFiles(photoPath);
  await expect(page.getByText('Photos: 1 / 6')).toBeVisible();
  await expect(page.getByText('Likely the same 94%', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByLabel('Description', { exact: true }).fill('Orange street cat');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page.locator('.step-heading')).toContainText('Step 5 of 5');
  await page.getByRole('button', { name: 'Publish', exact: true }).click();
  await expect(page.getByRole('dialog').filter({ hasText: 'Our AI thinks' })).toBeVisible();
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Add to that post', exact: true })
    .click();
  await expect(page).toHaveURL(new RegExp('/post/' + id + '$'));
  workerStopped = true;
  await worker;
  expect((await document('posts/' + id)).updatesCount).toBe(1);
  const posts = await collection('posts');
  expect(posts.filter((p) => p.fields.ownerId?.stringValue === a.uid)).toHaveLength(1);
});
test('admin hides/restores post and suspends/restores member', async ({ page }) => {
  const a = await account(true),
    member = await account();
  await login(page, a);
  await promote(a);
  await write('users/' + member.uid, {
    username: member.username,
    displayName: member.username,
    bio: '',
    photoId: '',
    role: 'user',
    volunteerStatus: 'none',
    stats: {},
    points: 0,
    neighborhoods: [],
    badges: [],
    followedCats: [],
    showStatsPublicly: true,
    suspended: false,
    lastPostAt: null,
    createdAt: Date.now(),
  });
  const name = 'Admin web post ' + randomBytes(5).toString('hex');
  const id = await publishLost(page, name);
  await navigate(page, '/admin-posts');
  await page.getByLabel('Search', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Manage', exact: true }).click();
  await page.getByRole('button', { name: 'Hide post', exact: true }).click();
  await expect.poll(async () => (await document('posts/' + id)).hidden).toBe(true);
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await page.getByRole('button', { name: 'Manage', exact: true }).click();
  await page.getByRole('button', { name: 'Show post again', exact: true }).click();
  await expect.poll(async () => (await document('posts/' + id)).hidden).toBe(false);
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await navigate(page, '/admin-users');
  await page.getByLabel('Search', { exact: true }).fill(member.username);
  await page.getByRole('button', { name: 'Manage', exact: true }).click();
  await page.getByLabel('Reason', { exact: true }).fill('Emulator test');
  await page.getByRole('button', { name: 'Suspend account', exact: true }).click();
  await expect.poll(async () => (await document('users/' + member.uid)).suspended).toBe(true);
  await page.getByRole('button', { name: 'Manage', exact: true }).click();
  await page.getByRole('button', { name: 'Restore account', exact: true }).click();
  await expect.poll(async () => (await document('users/' + member.uid)).suspended).toBe(false);
});
