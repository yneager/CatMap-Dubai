import { test, expect, type BrowserContext } from '@playwright/test';
import { account, login, navigate, write, document, collection, type Account } from './helpers';

// The lost-to-reunited workflow with two real (disposable) accounts and the app's own code: the AI worker's
// suggestions are seeded exactly as cat-server/recovery.py writes them (that part is tested in
// cat-server/tests/test_recovery_e2e.py); everything after is clicked through in the browser.
const now = Date.now();
function post(owner: Account, id: string, extra: Record<string, unknown>) {
  return write('posts/' + id, {
    ownerId: owner.uid,
    ownerName: owner.username,
    ownerPhotoId: '',
    animalType: 'cat',
    category: 'spotted',
    status: 'open',
    name: '',
    description: 'Orange cat',
    color: 'orange',
    breed: '',
    features: 'white mark near the left ear',
    sex: 'unknown',
    ageGroup: 'unknown',
    photoIds: [],
    videoPath: '',
    lat: 25.2048,
    lng: 55.2708,
    approximate: false,
    areaRadiusM: 0,
    neighborhood: 'jumeirah',
    lastSeenAt: now,
    reward: 0,
    contactMethod: 'in_app',
    contactPhone: '',
    contactEmail: '',
    adoptionStatus: 'available',
    health: '',
    vaccinated: false,
    neutered: false,
    updatesCount: 0,
    hidden: false,
    keywords: ['orange'],
    createdAt: now,
    updatedAt: now,
    resolvedAt: 0,
    lastActivityAt: now,
    fingerprint: [],
    microchip: 'unknown',
    resolution: '',
    resolvedMatchId: '',
    ...extra,
  });
}
function match(
  lost: string,
  sighting: string,
  a: Account,
  b: Account,
  tier: string,
  visual: number,
) {
  return write(`matches/${lost}__${sighting}`, {
    lostPostId: lost,
    sightingPostId: sighting,
    lostOwnerId: a.uid,
    sightingOwnerId: b.uid,
    members: [a.uid, b.uid],
    animalType: 'cat',
    score: 0.7,
    visual,
    tier,
    method: 'embedding',
    model: 'pet-recognition-base+detr-resnet-50/v1',
    distanceKm: 1.2,
    daysApart: 1.5,
    reasons: ['photo_very_similar', 'near', 'recent'],
    source: 'worker',
    ownerState: 'new',
    reporterState: 'new',
    createdBy: 'worker',
    createdAt: now,
    updatedAt: now,
    notified: true,
  });
}

// Signed-in contexts hold emulator connections; WebKit has six per host for the whole browser, so a context left open
// here makes later tests' sign-ins hang.
const opened: BrowserContext[] = [];
test.afterEach(async () => {
  await Promise.all(opened.splice(0).map((c) => c.close()));
});
test('lost report, AI match, review, chat and reunion across two accounts', async ({ browser }) => {
  test.setTimeout(240_000);
  const a = await account(),
    b = await account(),
    c = await account();
  const ca = await browser.newContext(),
    cb = await browser.newContext();
  opened.push(ca, cb);
  const pageA = await ca.newPage();
  const pageB = await cb.newPage();
  await login(pageA, a);
  await login(pageB, b);
  const L = 'L' + a.uid.slice(0, 8),
    S = 'S' + b.uid.slice(0, 8),
    X = 'X' + c.uid.slice(0, 8);
  await post(a, L, { category: 'lost', name: 'Simba', areaRadiusM: 500 });
  await post(b, S, { lastSeenAt: now + 36 * 3600_000 });
  await post(c, X, { features: '' });
  await match(L, S, a, b, 'strong', 0.91);
  await match(L, X, a, c, 'possible', 0.8);

  // The owner sees both suggestions on the lost report, with words, not percentages.
  await navigate(pageA, '/post/' + L);
  await expect(pageA.locator('.match-card')).toHaveCount(2);
  await expect(pageA.locator('.match-card').first()).toContainText('Strong possible match');
  await expect(pageA.locator('.match-card').first()).not.toContainText('%');

  // A look-alike is rejected: stored, and gone from the list.
  await navigate(pageA, `/match/${L}__${X}`);
  await expect(
    pageA.getByText('This is a suggestion, not a confirmation.', { exact: false }),
  ).toBeVisible();
  await pageA.getByRole('button', { name: 'This is not my animal', exact: true }).click();
  await expect.poll(async () => (await document(`matches/${L}__${X}`)).ownerState).toBe('not_mine');

  // The likely one: "might be mine", then a conversation that carries the match card.
  await navigate(pageA, `/match/${L}__${S}`);
  await expect(pageA.getByRole('table')).toContainText('white mark near the left ear');
  await pageA.getByRole('button', { name: 'This might be my animal', exact: true }).click();
  await expect.poll(async () => (await document(`matches/${L}__${S}`)).ownerState).toBe('maybe');
  await pageA.getByRole('button', { name: 'Message about this match', exact: true }).click();
  await expect(pageA).toHaveURL(/\/chat\//);
  const chatId = pageA.url().split('/chat/')[1].split('?')[0];
  await expect(pageA.locator('.match-message')).toHaveCount(1);
  await pageA
    .getByLabel('Message', { exact: true })
    .fill('Did you notice a white mark near its left ear?');
  await pageA.getByRole('button', { name: 'Send', exact: true }).click();
  // Opening the chat again from the match reuses it and doesn't repeat the card.
  await navigate(pageA, `/match/${L}__${S}`);
  await pageA.getByRole('button', { name: 'Message about this match', exact: true }).click();
  await expect(pageA).toHaveURL(new RegExp('/chat/' + chatId));
  await expect(pageA.locator('.match-message')).toHaveCount(1);

  // The person who saw the cat replies, and confirms from their side.
  await navigate(pageB, '/chat/' + chatId);
  await expect(pageB.locator('.match-message')).toHaveCount(1);
  await expect(
    pageB.locator('.chat-messages').getByText('Did you notice a white mark near its left ear?'),
  ).toBeVisible();
  await pageB
    .getByLabel('Message', { exact: true })
    .fill('Yes, I noticed that marking. I saw the cat near the park.');
  await pageB.getByRole('button', { name: 'Send', exact: true }).click();
  await navigate(pageB, `/match/${L}__${S}`);
  await pageB.getByRole('button', { name: "I think it's the same animal", exact: true }).click();
  await expect
    .poll(async () => (await document(`matches/${L}__${S}`)).reporterState)
    .toBe('confirmed');
  // The reporter can't answer for the owner.
  await expect(
    pageB.getByRole('button', { name: 'This might be my animal', exact: true }),
  ).toHaveCount(0);

  // Reunited, credited to that sighting, once.
  await navigate(pageA, '/post/' + L);
  await expect(pageA.locator('.match-card')).toHaveCount(1);
  await pageA.getByRole('button', { name: 'Mark as reunited', exact: true }).first().click();
  await pageA
    .locator('.dialog')
    .getByRole('button', { name: 'Mark as reunited', exact: true })
    .click();
  await expect(pageA.getByText('Simba is home!')).toBeVisible();
  await expect.poll(async () => (await document('posts/' + L)).status).toBe('reunited');
  const lost = await document('posts/' + L);
  expect(lost.resolution).toBe('catmap');
  expect(lost.resolvedMatchId).toBe(`${L}__${S}`);
  expect((await document(`matches/${L}__${S}`)).ownerState).toBe('reunited');
  expect(
    (await collection(`users/${b.uid}/notifications`)).some((n) =>
      n.name.endsWith(`/ru_${L}__${S}`),
    ),
  ).toBe(true);
  // The rejected look-alike stays rejected and is never thanked.
  expect((await document(`matches/${L}__${X}`)).ownerState).toBe('not_mine');
  expect((await collection(`users/${c.uid}/notifications`)).length).toBe(0);
  await pageA.locator('.celebrate').getByRole('button', { name: 'Close', exact: true }).click();
  await expect(pageA.locator('.reunion-banner')).toContainText(
    'Confirmed by the person who saw them',
  );
  // No second "Mark as reunited" on a reunited report.
  await expect(pageA.getByRole('button', { name: 'Mark as reunited', exact: true })).toHaveCount(0);
});
