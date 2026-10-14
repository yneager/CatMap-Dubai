import { test, expect } from '@playwright/test';
import { randomBytes } from 'node:crypto';
import {
  account,
  navigate,
  login,
  waitRateLimit,
  document,
  collection,
  write,
  headers,
  firestoreBase,
} from './helpers';
test('community cat, sighting privacy, rescue, station check-in and maintenance fit unchanged rules', async ({
  page,
}) => {
  const a = await account();
  await login(page, a);
  const catId = await page.evaluate(async () => {
    const { CatRepository } = await import('/src/data/CatRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
    return CatRepository.submitSighting(
      {
        existingCat: null,
        newCatName: 'Browser community cat',
        description: 'Orange cat',
        distinctiveMarks: 'White paws',
        sex: 'unknown',
        coatColor: 'orange',
        pattern: 'tabby',
        size: 'medium',
        breedHints: ['arabian_mau'],
        photoIds: [],
        features: [],
        lat: 25.2048,
        lng: 55.2708,
        accuracy: 5,
        locationSource: 'manual',
        seenAt: Date.now(),
        healthTags: ['good_weight'],
        behaviorTags: ['friendly'],
        earTip: 'unsure',
        possiblyNotNeutered: false,
        needsMedical: false,
        notes: 'Community observation',
        matchScore: 0,
        aiSummary: '',
        suggestedCatIds: [],
        rejectedCatIds: [],
      },
      me,
      'standard',
      true,
    );
  });
  const cat = await document('cats/' + catId);
  expect(cat.name).toBe('Browser community cat');
  expect(cat.lat).not.toBe(25.2048);
  const sightings = await collection('sightings');
  const sighting = sightings.find((s) => s.fields.userId?.stringValue === a.uid)!;
  const privateLocation = await document('privateLocations/' + sighting.name.split('/').at(-1));
  expect(privateLocation.lat).toBe(25.2048);
  expect(privateLocation.ownerId).toBe(a.uid);
  await waitRateLimit(a.uid);
  const rescueId = await page.evaluate(async () => {
    const { RescueRepository } = await import('/src/data/RescueRepository.ts' /* @vite-ignore */),
      { RescueReportDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
    return RescueRepository.submit(
      { ...RescueReportDefaults, description: 'Needs help', lat: 25.20481, lng: 55.27081 },
      me,
    );
  });
  expect((await document('rescues/' + rescueId)).status).toBe('submitted');
  const stationId = 'web-station-' + randomBytes(5).toString('hex');
  await write('stations/' + stationId, {
    name: 'Web care station',
    description: '',
    operatorName: 'Volunteers',
    schedule: 'Daily',
    foodType: 'Dry food',
    hasWater: true,
    lat: 25.2048,
    lng: 55.2708,
    neighborhood: 'downtown',
    verified: true,
    active: true,
    sample: true,
    checkinCount: 0,
    lastCheckinAt: 0,
    lastCheckinBy: '',
    createdAt: Date.now(),
    updatedAt: Date.now(),
  });
  await page.evaluate(async (id) => {
    const { StationRepository } = await import('/src/data/StationRepository.ts' /* @vite-ignore */),
      { FeedingStationFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
      station = (await read('stations/' + id, FeedingStationFromDoc))!;
    await StationRepository.checkIn(station, 2, [], 'full', 'full', 'Topped up supplies', me);
  }, stationId);
  expect((await document('stations/' + stationId)).checkinCount).toBe(1);
  await waitRateLimit(a.uid);
  await page.evaluate(async (id) => {
    const { StationRepository } = await import('/src/data/StationRepository.ts' /* @vite-ignore */),
      { FeedingStationFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */);
    await StationRepository.reportMaintenance(
      (await read('stations/' + id, FeedingStationFromDoc))!,
      'damaged',
      'Broken bowl',
      (await UserRepository.profile(authentication().currentUser!.uid))!,
    );
  }, stationId);
  expect(
    (await collection('maintenance')).some((d) => d.fields.userId?.stringValue === a.uid),
  ).toBe(true);
});
test('data export excludes credentials/images and deletion preserves counterpart chat history', async ({
  browser,
}) => {
  const a = await account(),
    b = await account(),
    ca = await browser.newContext(),
    cb = await browser.newContext(),
    pa = await ca.newPage(),
    pb = await cb.newPage();
  await login(pa, a);
  await login(pb, b);
  const chatId = await pa.evaluate(async (other) => {
    const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */),
      { ChatFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
      id = await ChatRepository.open(me, other),
      chat = (await read('chats/' + id, ChatFromDoc))!;
    await ChatRepository.send(chat, me, 'Delete my own message');
    return id;
  }, b.uid);
  await pb.evaluate(async (id) => {
    const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */),
      { ChatFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
    await ChatRepository.send(
      (await read('chats/' + id, ChatFromDoc))!,
      (await UserRepository.profile(authentication().currentUser!.uid))!,
      'Keep the other member\u2019s message',
    );
  }, chatId);
  const payload = await pa.evaluate(async () => {
    const { UserDataExportRepository } = await import(
        '/src/data/UserDataExport.ts' /* @vite-ignore */
      ),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
    return UserDataExportRepository.build(authentication().currentUser!.uid);
  });
  expect(payload.schemaVersion).toBe(1);
  expect(payload.records.messages).toHaveLength(2);
  expect(JSON.stringify(payload)).not.toMatch(/"(password|tokens|data)":/);
  await pa.evaluate(async (password) => {
    const { deleteAccount } = await import('/src/data/AccountDeletion.ts' /* @vite-ignore */);
    await deleteAccount(password);
  }, a.password);
  const result = await fetch(firestoreBase + '/users/' + a.uid, { headers });
  expect(result.status).toBe(404);
  const chat = await document('chats/' + chatId);
  expect((chat.names as Record<string, string>)[a.uid]).toBe('');
  const messages = await collection(`chats/${chatId}/messages`);
  expect(messages).toHaveLength(1);
  expect(messages[0].fields.senderId.stringValue).toBe(b.uid);
  await ca.close();
  await cb.close();
});
test('encrypted demo vault and post drafts survive reload without plain-text storage', async ({
  page,
}) => {
  await login(page, await account());
  const password = randomBytes(24).toString('base64url');
  await page.evaluate(async (password) => {
    const { DemoVault } = await import('/src/data/DemoAccounts.ts' /* @vite-ignore */);
    await DemoVault.save(password);
  }, password);
  await page.reload();
  const checks = await page.evaluate(async (password) => {
    const { DemoVault } = await import('/src/data/DemoAccounts.ts' /* @vite-ignore */),
      { get } = await import('/src/util/idb.ts' /* @vite-ignore */);
    const key = (await get('vault_key')) as CryptoKey;
    let exported = false;
    try {
      await crypto.subtle.exportKey('raw', key);
      exported = true;
    } catch {
      /* Non-extractable key. */
    }
    return {
      correct: (await DemoVault.password()) === password,
      exported,
      plaintext: JSON.stringify(localStorage).includes(password),
    };
  }, password);
  expect(checks).toEqual({ correct: true, exported: false, plaintext: false });
  await navigate(page, '/post-new');
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Choose location', exact: true }).click();
  await page.getByRole('button', { name: 'Use this location', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByLabel('Name', { exact: true }).fill('Persistent browser draft');
  await page.waitForTimeout(700);
  await page.reload();
  await page.getByRole('button', { name: 'Continue draft', exact: true }).click();
  await expect(page.getByLabel('Name', { exact: true })).toHaveValue('Persistent browser draft');
  await page.evaluate(async () => {
    const { DemoVault } = await import('/src/data/DemoAccounts.ts' /* @vite-ignore */);
    await DemoVault.clear();
  });
  expect(
    await page.evaluate(async () => {
      const { DemoVault } = await import('/src/data/DemoAccounts.ts' /* @vite-ignore */);
      return DemoVault.saved();
    }),
  ).toBe(false);
});
