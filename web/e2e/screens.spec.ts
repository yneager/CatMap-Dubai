import { test, expect, type Page } from '@playwright/test';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { account, login, navigate, promote, write, headers, photoPath, authBase } from './helpers';

test.skip(process.env.CATMAP_SCREENS !== 'true', 'Run with npm run screens');
test.describe.configure({ timeout: 1_200_000 });

for (const variant of ['light', 'dark', 'arabic'] as const) {
  test(`visual screenshots: every route, ${variant}`, async ({ page, browser }) => {
    const output = `docs/screens/${variant}`;
    await mkdir(output, { recursive: true });
    const owner = await account(true),
      other = await account(),
      suffix = randomBytes(4).toString('hex');
    await login(page, owner);
    await promote(owner);
    const photo = await page.evaluate(
      async (bytes) => {
        const { processPhoto } = await import('/src/util/images.ts' /* @vite-ignore */);
        const { ImageRepository } = await import('/src/data/ImageRepository.ts' /* @vite-ignore */);
        const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
        const file = new Blob([new Uint8Array(bytes)], { type: 'image/jpeg' });
        return ImageRepository.upload(authentication().currentUser!.uid, await processPhoto(file));
      },
      [...(await readFile(photoPath))],
    );
    const ids = {
      post: `screens-post-${suffix}`,
      cat: `screens-cat-${suffix}`,
      sighting: `screens-sighting-${suffix}`,
      rescue: `screens-rescue-${suffix}`,
      station: `screens-station-${suffix}`,
      chat: [owner.uid, other.uid].sort().join('_'),
    };
    const fixtures = await page.evaluate(
      async ({ ids, owner, other, photo }) => {
        const m = await import('/src/data/model/index.ts' /* @vite-ignore */);
        const now = Date.now();
        return [
          [
            'users/' + other.uid,
            m.UserProfileToDoc({
              ...m.UserProfileDefaults,
              uid: other.uid,
              username: other.username,
              displayName: 'Maya',
              photoId: photo,
              createdAt: now,
            }),
          ],
          [
            'posts/' + ids.post,
            m.PostToDoc({
              ...m.PostDefaults,
              id: ids.post,
              ownerId: owner.uid,
              ownerName: owner.username,
              name: 'Amber',
              description: 'Orange coat, teal collar. Last seen near the park.',
              category: 'lost',
              animalType: 'cat',
              photoIds: [photo],
              lat: 25.2048,
              lng: 55.2708,
              neighborhood: 'deira',
              reward: 250,
              createdAt: now,
              updatedAt: now,
              lastSeenAt: now,
            }),
          ],
          [
            'cats/' + ids.cat,
            m.CatToDoc({
              ...m.CatDefaults,
              id: ids.cat,
              name: 'Amber',
              description: 'Friendly community cat',
              coatColor: 'orange',
              pattern: 'tabby',
              size: 'medium',
              photoIds: [photo],
              coverPhotoId: photo,
              createdBy: owner.uid,
              createdByName: owner.username,
              contributors: [owner.uid],
              lat: 25.2048,
              lng: 55.2708,
              neighborhood: 'deira',
              firstSeen: now,
              lastSeen: now,
              sightingCount: 1,
              createdAt: now,
              updatedAt: now,
            }),
          ],
          [
            'sightings/' + ids.sighting,
            m.SightingToDoc({
              ...m.SightingDefaults,
              id: ids.sighting,
              catId: ids.cat,
              catName: 'Amber',
              userId: owner.uid,
              username: owner.username,
              photoIds: [photo],
              seenAt: now,
              lat: 25.2048,
              lng: 55.2708,
              neighborhood: 'deira',
              coatColor: 'orange',
              pattern: 'tabby',
              notes: 'Resting near the park.',
              createdAt: now,
              updatedAt: now,
            }),
          ],
          [
            'rescues/' + ids.rescue,
            m.RescueReportToDoc({
              ...m.RescueReportDefaults,
              id: ids.rescue,
              userId: owner.uid,
              username: owner.username,
              description: 'A community cat needs a check-up.',
              photoIds: [photo],
              lat: 25.2048,
              lng: 55.2708,
              neighborhood: 'deira',
              createdAt: now,
              updatedAt: now,
            }),
          ],
          [
            'stations/' + ids.station,
            m.FeedingStationToDoc({
              ...m.FeedingStationDefaults,
              id: ids.station,
              name: 'Park feeding station',
              description: 'Fresh food and water for the local cats.',
              operatorName: 'Community volunteers',
              schedule: 'Daily',
              foodType: 'Dry food',
              hasWater: true,
              lat: 25.2048,
              lng: 55.2708,
              neighborhood: 'deira',
              verified: true,
              createdAt: now,
              updatedAt: now,
            }),
          ],
          [
            'sitters/' + other.uid,
            m.SitterProfileToDoc({
              ...m.SitterProfileDefaults,
              uid: other.uid,
              name: 'Maya',
              headline: 'Kind, experienced cat care',
              description: 'Visits and overnight care.',
              photoId: photo,
              petTypes: ['cat'],
              services: ['visits'],
              availability: 'Weekdays and weekends',
              rate: 75,
              lat: 25.2048,
              lng: 55.2708,
              neighborhood: 'deira',
              verified: true,
              createdAt: now,
              updatedAt: now,
            }),
          ],
          [
            'chats/' + ids.chat,
            m.ChatToDoc({
              ...m.ChatDefaults,
              id: ids.chat,
              members: [owner.uid, other.uid],
              names: { [owner.uid]: owner.username, [other.uid]: 'Maya' },
              lastAt: now,
              lastText: 'Thank you for checking on Amber.',
            }),
          ],
          [
            'chats/' + ids.chat + '/messages/fixture',
            m.MessageToDoc({
              ...m.MessageDefaults,
              id: 'fixture',
              senderId: other.uid,
              text: 'Thank you for checking on Amber.',
              createdAt: now,
            }),
          ],
        ] as [string, Record<string, unknown>][];
      },
      { ids, owner, other, photo },
    );
    for (const [path, data] of fixtures) await write(path, data);
    await page.evaluate(async (variant) => {
      const { SettingsRepository } = await import(
        '/src/data/SettingsRepository.ts' /* @vite-ignore */
      );
      await SettingsRepository.set({
        theme: variant === 'dark' ? 'dark' : 'light',
        language: variant === 'arabic' ? 'ar' : 'en',
        installHintDismissed: true,
      });
    }, variant);
    const capture = async (name: string, route?: string, target: Page = page) => {
      if (route) await navigate(target, route);
      if (route === '/explore') await expect(target.locator('.explore-hero')).toBeVisible();
      if (route === '/map') await expect(target.locator('.map-screen')).toBeVisible();
      if (route?.startsWith('/chat-with/'))
        await expect(target.locator('.route-screen')).toHaveAttribute('data-screen', /\/chat\//);
      await expect(
        target.locator('.topbar, .map-screen, .auth-column, .explore-hero').first(),
      ).toBeVisible();
      await target.locator('.skeleton').first().waitFor({ state: 'hidden', timeout: 45000 });
      await target.evaluate(() => document.fonts.ready);
      await expect(target.locator('.app')).toBeVisible();
      await target.screenshot({
        path: `${output}/${name}.png`,
        scale: 'css',
        animations: 'disabled',
      });
    };
    const routes: [string, string][] = [
      ['map', '/map'],
      ['explore', '/explore'],
      ['search', '/search'],
      ['pick-location', '/pick-location'],
      ['post-detail', '/post/' + ids.post],
      ['post-edit', '/post-edit/' + ids.post],
      ['poster', '/poster/' + ids.post],
      ...[
        'my-posts',
        'favorites',
        'area-alerts',
        'messages',
        'adoption',
        'adoption-requests',
        'sitters',
        'sitter-edit',
        'bookings',
        'profile',
        'profile-edit',
        'achievements',
        'settings',
        'notifications',
        'cats',
        'sighting-new',
        'rescue',
        'rescue-new',
        'stations',
        'station-edit',
        'help-directory',
        'admin',
        'admin-posts',
        'admin-users',
        'admin-sitters',
        'admin-reports',
        'admin-rescues',
        'admin-merges',
        'admin-volunteers',
        'admin-maintenance',
        'admin-submissions',
        'admin-log',
        'admin-recognition',
        'admin-ai-server',
      ].map((route): [string, string] => [route, '/' + route]),
      ['cat-detail', '/cat/' + ids.cat],
      ['cat-edit', '/cat-edit/' + ids.cat],
      ['cat-merge', '/cat-merge/' + ids.cat],
      ['photo', '/photo/' + photo + '?catId=' + ids.cat],
      ['sighting-detail', '/sighting/' + ids.sighting],
      ['sighting-edit', '/sighting-edit/' + ids.sighting],
      ['rescue-detail', '/rescue/' + ids.rescue],
      ['station-detail', '/station/' + ids.station],
      ['sitter-detail', '/sitter/' + other.uid],
      ['book', '/book/' + other.uid],
      ['user', '/user/' + other.uid],
      ['chat', '/chat/' + ids.chat],
      ['chat-with', '/chat-with/' + other.uid],
    ];
    for (const [name, route] of routes) await capture(name, route);
    await navigate(page, '/map');
    await page.locator('button.post-nav').click();
    await capture('chooser');
    await page.locator('dialog .icon-button').click();
    await page
      .locator('.map-overlay-top .chip')
      .filter({ has: page.locator('.control-icon') })
      .click();
    await capture('filter-sheet');
    await page.locator('dialog .icon-button').click();
    await navigate(page, '/post-new');
    for (let step = 0; step < 6; step++) {
      if (step === 2) {
        await page.locator('input[maxlength="60"]').first().fill('Amber');
        await page
          .locator('textarea[maxlength="2000"]')
          .fill('Orange coat, teal collar. Last seen near the park.');
      }
      if (step === 3) {
        await page.locator('.location-field button').first().click();
        await page.locator('dialog button.primary').click();
      }
      await capture('editor-step-' + (step + 1));
      if (step < 5) await page.locator('.editor-footer button.primary').click();
    }
    const signedOut = await browser.newContext({ viewport: { width: 393, height: 852 } });
    const anon = await signedOut.newPage();
    await anon.goto('/login');
    await anon.evaluate(async (variant) => {
      const { SettingsRepository } = await import(
        '/src/data/SettingsRepository.ts' /* @vite-ignore */
      );
      await SettingsRepository.set({
        theme: variant === 'dark' ? 'dark' : 'light',
        language: variant === 'arabic' ? 'ar' : 'en',
      });
    }, variant);
    for (const route of ['login', 'register', 'forgot']) await capture(route, '/' + route, anon);
    const unverified = await account();
    const response = await fetch(
      authBase + '/identitytoolkit.googleapis.com/v1/accounts:update?key=demo-key',
      {
        method: 'POST',
        headers,
        body: JSON.stringify({ localId: unverified.uid, emailVerified: false }),
      },
    );
    expect(response.ok).toBe(true);
    await anon.goto('/login');
    await anon.locator('input').first().fill(unverified.email);
    await anon.locator('input[type=password]').fill(unverified.password);
    await anon.locator('button.primary').click();
    await expect(anon).toHaveURL(/\/verify$/);
    await capture('verify', undefined, anon);
    await signedOut.close();
    for (const component of [
      'post-card',
      'post-grid-card',
      'detail-header',
      'status-badge',
      'filter-sheet',
    ]) {
      await page.goto(
        `/e2e/fixtures/components.html?component=${component}&theme=${variant === 'dark' ? 'dark' : 'light'}&language=${variant === 'arabic' ? 'ar' : 'en'}`,
      );
      await page.evaluate(() => document.fonts.ready);
      const target =
        component === 'filter-sheet' ? page.locator('dialog') : page.locator('#fixture');
      await target.screenshot({
        path: `${output}/fixture-${component}.png`,
        scale: 'css',
        animations: 'disabled',
      });
    }
    if (variant === 'light') {
      const poster = await page.evaluate(async () => {
        const { makePoster } = await import('/src/util/poster.ts' /* @vite-ignore */);
        const { PostDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */);
        return (
          await makePoster({
            ...PostDefaults,
            id: 'fixture',
            name: 'Amber',
            category: 'lost',
            animalType: 'cat',
            description: 'Orange coat, teal collar. Last seen near the park.',
            neighborhood: 'deira',
            reward: 250,
          })
        ).toDataURL();
      });
      await writeFile(`${output}/fixture-poster.png`, Buffer.from(poster.split(',')[1], 'base64'));
    }
    await writeFile(
      `${output}/routes.json`,
      JSON.stringify(
        routes.map(([name, route]) => ({ name, route })),
        null,
        2,
      ),
    );
    console.log(`${variant}: ${routes.length + 17} route, editor, sheet and component screenshots`);
  });
}
