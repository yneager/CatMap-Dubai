import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { test, expect } from '@playwright/test';
import { account, login, waitRateLimit, get, collection } from './helpers';
type Field = { type: string; documentId: boolean };
function kotlinFields() {
  const directory = path.resolve('..', 'app/src/main/java/com/catmap/dubai/data/model');
  const schema: Record<string, Record<string, Field>> = {};
  for (const file of readdirSync(directory).filter((f) => f.endsWith('.kt'))) {
    const source = readFileSync(path.join(directory, file), 'utf8').replace(
      /\/\*[\s\S]*?\*\/|\/\/[^\n]*/g,
      '',
    );
    for (const match of source.matchAll(/data class (\w+)\(\s*\n([\s\S]*?)\n\)/g)) {
      const fields: Record<string, Field> = {};
      for (const m of match[2].matchAll(/(?:(@DocumentId)\s+)?var (\w+):\s*(.*?)\s*=/g))
        fields[m[2]] = { type: m[3], documentId: !!m[1] };
      schema[match[1]] = fields;
    }
  }
  return schema;
}
function assertType(value: Record<string, unknown>, type: string) {
  if (type === 'String') expect(value).toHaveProperty('stringValue');
  else if (type === 'Boolean') expect(value).toHaveProperty('booleanValue');
  else if (type === 'Long' || type === 'Int') {
    expect(value).toHaveProperty('integerValue');
    expect(Number.isInteger(Number(value.integerValue))).toBe(true);
  } else if (type === 'Double') expect(value).toHaveProperty('doubleValue');
  else if (type === 'Timestamp?')
    expect('timestampValue' in value || 'nullValue' in value).toBe(true);
  else if (type.startsWith('List<')) {
    expect(value).toHaveProperty('arrayValue');
    const inner = type.slice(5, -1);
    for (const item of (value.arrayValue as { values?: Record<string, unknown>[] }).values ?? [])
      assertType(item, inner);
  } else if (type.startsWith('Map<String, ')) {
    expect(value).toHaveProperty('mapValue');
    const inner = type.slice(12, -1);
    for (const item of Object.values(
      (value.mapValue as { fields?: Record<string, Record<string, unknown>> }).fields ?? {},
    ))
      assertType(item, inner);
  } else throw new Error('Unrecognized Kotlin type: ' + type);
}
async function compare(path: string, model: string, schema: ReturnType<typeof kotlinFields>) {
  const doc = await get(path);
  const expected = Object.entries(schema[model]).filter(([, v]) => !v.documentId);
  expect(Object.keys(doc.fields).sort(), model + ' field set').toEqual(
    expected.map(([k]) => k).sort(),
  );
  for (const [key, field] of expected) assertType(doc.fields[key], field.type);
}
test('web writes exactly the Kotlin post/update/chat/message/request/booking/review/user/inbox shapes', async ({
  browser,
}) => {
  const schema = kotlinFields(),
    a = await account(),
    b = await account(),
    ca = await browser.newContext(),
    cb = await browser.newContext(),
    pa = await ca.newPage(),
    pb = await cb.newPage();
  await login(pa, a);
  await login(pb, b);
  const postId = await pa.evaluate(async () => {
    const { PostRepository } = await import('/src/data/PostRepository.ts' /* @vite-ignore */),
      { PostDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
    return PostRepository.create(
      {
        ...PostDefaults,
        name: 'Shape cat',
        category: 'adoption',
        description: 'Shape parity fixture',
        lat: 25.2048,
        lng: 55.2708,
        reward: 25.9,
        areaRadiusM: 122.2,
      },
      me,
    );
  });
  await compare('posts/' + postId, 'Post', schema);
  await waitRateLimit(b.uid);
  await pb.evaluate(async (postId) => {
    const { PostRepository } = await import('/src/data/PostRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { AdoptionRepository } = await import('/src/data/AdoptionRepository.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
      post = (await PostRepository.post(postId))!;
    await PostRepository.addUpdate(post, 'sighting', 'A web update', '', 25.2048, 55.2708, me);
    await AdoptionRepository.request(post, 'I can adopt', 'Secure home', me);
  }, postId);
  const updates = await collection(`posts/${postId}/updates`);
  await compare(updates[0].name.split('/documents/')[1], 'PostUpdate', schema);
  await compare(`adoptionRequests/${postId}_${b.uid}`, 'AdoptionRequest', schema);
  const chatId = await pa.evaluate(async (other) => {
    const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */),
      { ChatFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */),
      { SittingRepository } = await import('/src/data/SittingRepository.ts' /* @vite-ignore */),
      { SitterProfileDefaults } = await import('/src/data/model/index.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
      id = await ChatRepository.open(me, other),
      chat = (await read('chats/' + id, ChatFromDoc))!;
    await ChatRepository.send(chat, me, 'Web shape message');
    await SittingRepository.saveSitter(me, {
      ...SitterProfileDefaults,
      headline: 'Shape sitter',
      description: 'Shape parity',
      lat: 25.2048,
      lng: 55.2708,
      petTypes: ['cat'],
      services: ['home_visits'],
      rate: 99.4,
    });
    return id;
  }, b.uid);
  await compare('chats/' + chatId, 'Chat', schema);
  const messages = await collection(`chats/${chatId}/messages`);
  await compare(messages[0].name.split('/documents/')[1], 'Message', schema);
  await waitRateLimit(b.uid);
  const bookingId = await pb.evaluate(async (uid) => {
    const { SittingRepository } = await import('/src/data/SittingRepository.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */),
      { SitterProfileFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
    const sitter = (await read('sitters/' + uid, SitterProfileFromDoc))!,
      me = (await UserRepository.profile(authentication().currentUser!.uid))!;
    return SittingRepository.requestBooking(
      sitter,
      ['cat'],
      2.4,
      Date.now() + 86400000,
      Date.now() + 172800000,
      'Shape booking',
      me,
    );
  }, a.uid);
  await compare('bookings/' + bookingId, 'Booking', schema);
  await pa.evaluate(async (id) => {
    const { SittingRepository } = await import('/src/data/SittingRepository.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */),
      { BookingFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
      booking = (await read('bookings/' + id, BookingFromDoc))!;
    await SittingRepository.setStatus(booking, 'accepted', me);
    await SittingRepository.setStatus({ ...booking, status: 'accepted' }, 'completed', me);
  }, bookingId);
  await pb.evaluate(async (id) => {
    const { SittingRepository } = await import('/src/data/SittingRepository.ts' /* @vite-ignore */),
      { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */),
      { authentication } = await import('/src/firebase.ts' /* @vite-ignore */),
      { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */),
      { BookingFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!,
      booking = (await read('bookings/' + id, BookingFromDoc))!;
    await SittingRepository.review(booking, 4.4, 'Shape review', me);
  }, bookingId);
  await compare('reviews/' + bookingId, 'Review', schema);
  await compare('users/' + a.uid, 'UserProfile', schema);
  const notifications = await collection(`users/${a.uid}/notifications`);
  expect(notifications.length).toBeGreaterThan(0);
  for (const n of notifications)
    await compare(n.name.split('/documents/')[1], 'AppNotification', schema);
  await ca.close();
  await cb.close();
});
