import { randomBytes } from 'node:crypto';
import { test, expect } from '@playwright/test';
import {
  account,
  login,
  navigate,
  write,
  document,
  waitRateLimit,
  firestoreBase,
  headers,
} from './helpers';
import { PostDefaults, PostToDoc, CatDefaults, CatToDoc } from '../src/data/model';
import { Geohash } from '../src/data/Geohash';
import { SearchTerms } from '../src/data/SearchTerms';
import { fakePhotoWorker } from './fake-photo-worker';

test('lost and street posts carry Android geoCells and searchTerms accepted by the current rules', async ({
  page,
}) => {
  const a = await account();
  await login(page, a);
  for (const category of ['lost', 'street']) {
    await waitRateLimit(a.uid);
    const id = await page.evaluate(async (category) => {
      const { PostRepository } = await import('/src/data/PostRepository.ts' /* @vite-ignore */);
      const { PostDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */);
      const { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
      const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
      const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
      return PostRepository.create(
        {
          ...PostDefaults,
          category,
          name: 'Zafran',
          description: 'Orange tabby',
          lat: 25.2048,
          lng: 55.2708,
        },
        me,
      );
    }, category);
    const stored = await document('posts/' + id);
    expect(stored.geoCells).toEqual(['thrr', 'thrr3', 'thrr3s']);
    expect(stored.searchTerms).toEqual(SearchTerms.of(stored.keywords as string[]));
    await navigate(page, '/post/' + id);
    await expect(page.getByRole('heading', { name: /Zafran/ }).first()).toBeVisible();
  }
});

test('map area pins survive reopening with zero repeated cell queries; Explore reads cursor pages', async ({
  page,
}) => {
  const a = await account(),
    suffix = randomBytes(5).toString('hex'),
    now = Date.now() - 7200000;
  const ids = Array.from(
    { length: 55 },
    (_, i) => 'round3_' + suffix + '_' + String(i).padStart(2, '0'),
  );
  for (let i = 0; i < ids.length; i++)
    await write(
      'posts/' + ids[i],
      PostToDoc({
        ...PostDefaults,
        ownerId: a.uid,
        ownerName: 'seed',
        name: 'Ultramarine ' + suffix + ' ' + i,
        keywords: ['ultramarine', suffix],
        searchTerms: SearchTerms.of(['ultramarine', suffix]),
        lat: 25.2048,
        lng: 55.2708,
        geoCells: Geohash.cells(25.2048, 55.2708),
        createdAt: now - i,
        updatedAt: now - i,
        lastActivityAt: Date.now(),
      }),
    );
  await write(
    'cats/round3_' + suffix,
    CatToDoc({
      ...CatDefaults,
      name: 'Area cat ' + suffix,
      lat: 25.2048,
      lng: 55.2708,
      geoCells: Geohash.cells(25.2048, 55.2708),
      updatedAt: now,
      lastSeen: now,
    }),
  );
  await login(page, a);
  await navigate(page, '/settings');
  const measured = await page.evaluate(async (uid) => {
    const { MapRepository: map } = await import('/src/data/MapRepository.ts' /* @vite-ignore */);
    const idb = await import('/src/util/idb.ts' /* @vite-ignore */);
    await map.clear();
    await idb.remove('map_' + uid);
    map.resetMetrics();
    await map.start(uid);
    const box = { south: 25.2, west: 55.265, north: 25.21, east: 55.28 };
    await map.viewport(box);
    await map.flush();
    const cold = map.metrics(),
      pins = Object.keys(map.get().posts).length;
    map.stop();
    map.resetMetrics();
    await map.start(uid);
    await map.viewport(box);
    await map.flush();
    const warm = map.metrics();
    map.stop();
    return { cold, warm, pins };
  }, a.uid);
  expect(measured.pins).toBeGreaterThanOrEqual(55);
  expect(measured.cold.areaQueries).toBeGreaterThan(0);
  expect(measured.warm.areaQueries).toBe(0);
  console.log('Round3 actual map query/read counters: ' + JSON.stringify(measured));
  await page.evaluate(async () => {
    const { PagerMetrics } = await import('/src/data/Pager.ts' /* @vite-ignore */);
    PagerMetrics.reset();
  });
  await navigate(page, '/explore');
  await expect(page.locator('.post-card').first()).toBeVisible();
  const before = await page.locator('.post-card').count();
  expect(before).toBeLessThanOrEqual(25);
  await page.getByRole('button', { name: 'Load more', exact: true }).scrollIntoViewIfNeeded();
  await expect.poll(() => page.locator('.post-card').count()).toBeGreaterThan(before);
  console.log(
    'Round3 Explore rendered page sizes: ' +
      before +
      ' -> ' +
      (await page.locator('.post-card').count()),
  );
  console.log(
    'Round3 Explore actual page query/read counts: ' +
      JSON.stringify(
        await page.evaluate(async () => {
          const { PagerMetrics } = await import('/src/data/Pager.ts' /* @vite-ignore */);
          return { queries: PagerMetrics.queries, reads: PagerMetrics.reads };
        }),
      ),
  );
  for (const word of ['ultramarine', 'ult']) {
    await navigate(page, '/search');
    await page.getByRole('textbox', { name: 'Search', exact: true }).fill(word + ' ' + suffix);
    await expect(page.locator('.post-card').first()).toContainText('Ultramarine');
  }
});

test('malformed posts and alerts do not break lists, and a foreign route never navigates', async ({
  page,
}) => {
  const a = await account();
  await login(page, a);
  const suffix = randomBytes(5).toString('hex');
  await write('posts/malformed_' + suffix, {
    ...PostToDoc({
      ...PostDefaults,
      name: 'Malformed safe ' + suffix,
      ownerId: a.uid,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      lastActivityAt: Date.now(),
      lat: 25.2048,
      lng: 55.2708,
      geoCells: Geohash.cells(25.2048, 55.2708),
    }),
    color: 5,
    photoIds: [1],
    keywords: [2],
  });
  await write(`users/${a.uid}/notifications/bad_${suffix}`, {
    type: 5,
    params: { text: 4 },
    route: { bad: true },
    read: false,
    createdAt: Date.now(),
  });
  await write(`users/${a.uid}/notifications/foreign_${suffix}`, {
    type: 'message',
    params: { user: 'Foreign route', text: 'Ignore this route' },
    route: 'https://evil.invalid',
    read: false,
    createdAt: Date.now() + 1,
  });
  await navigate(page, '/explore');
  await expect(
    page.locator('.post-card').filter({ hasText: 'Malformed safe ' + suffix }),
  ).toBeVisible();
  await navigate(page, '/map');
  await expect(page.locator('.community-map')).toBeVisible();
  await navigate(page, '/notifications');
  await page.locator('.notification-open').filter({ hasText: 'Ignore this route' }).click();
  await expect(page).toHaveURL(/\/notifications$/);
  await expect(page.locator('.notification')).toHaveCount(3);
});

for (const offline of [false, true])
  test(`logout wipes chat/account caches (${offline ? 'offline' : 'online'}) before the next account`, async ({
    page,
    context,
    browser,
  }) => {
    const a = await account(),
      b = await account(),
      other = await browser.newContext(),
      otherPage = await other.newPage();
    try {
      await login(otherPage, b);
      await login(page, a);
      const chatId = await page.evaluate(async (other) => {
        const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */);
        const { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
        const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
        const { ChatFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
        const { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */);
        const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
          id = await ChatRepository.open(me, other),
          chat = (await read('chats/' + id, ChatFromDoc))!;
        await ChatRepository.send(chat, me, 'Private account A text');
        return id;
      }, b.uid);
      await navigate(page, '/chat/' + chatId);
      await expect(page.getByText('Private account A text', { exact: true })).toBeVisible();
      // B is a third account: the legitimate chat recipient retains their history on the server.
      const next = await account();
      await navigate(page, '/settings');
      if (offline) await context.setOffline(true);
      await page
        .evaluate(async () => {
          const { AuthRepository } = await import('/src/data/AuthRepository.ts' /* @vite-ignore */);
          await AuthRepository.logout();
        })
        .catch((e) => {
          if (!String(e).includes('Execution context was destroyed')) throw e;
        });
      if (offline) await context.setOffline(false);
      await expect(page).toHaveURL(/\/login$/);
      const keys = await page.evaluate(async () => {
        return new Promise<IDBValidKey[]>((resolve, reject) => {
          const request = indexedDB.open('catmap-device');
          request.onsuccess = () => {
            const db = request.result,
              r = db.transaction('values').objectStore('values').getAllKeys();
            r.onsuccess = () => {
              db.close();
              resolve(r.result);
            };
            r.onerror = () => reject(r.error);
          };
        });
      });
      expect(keys).toEqual(['settings']);
      await login(page, next);
      await navigate(page, '/messages');
      if (offline) await context.setOffline(true);
      await expect(page.getByText('Private account A text', { exact: true })).toHaveCount(0);
      await expect(page.locator('.chat-row')).toHaveCount(0);
    } finally {
      await context.setOffline(false);
      await other.close();
    }
  });

test('the claimed fake worker answer is read before the AI job is deleted', async ({ page }) => {
  const a = await account();
  await login(page, a);
  const worker = await fakePhotoWorker(a.uid, () => ({ kind: 'animal', animalType: 'cat' }));
  try {
    const result = await page.evaluate(async () => {
      const { AiServer } = await import('/src/data/AiServer.ts' /* @vite-ignore */);
      return AiServer.run({ kind: 'check', photos: [], images: [], ids: [], labels: [] });
    });
    expect(result?.status).toBe('done');
    expect(worker.jobs.length).toBeGreaterThan(0);
    await expect
      .poll(async () => (await fetch(firestoreBase + '/aiJobs/' + a.uid, { headers })).status)
      .toBe(404);
  } finally {
    await worker.stop();
  }
});
