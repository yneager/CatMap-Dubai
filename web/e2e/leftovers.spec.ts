import { randomBytes } from 'node:crypto';
import { test, expect, type Page } from '@playwright/test';
import { account, login, navigate, write, firestoreBase, headers } from './helpers';
import {
  PostDefaults,
  PostToDoc,
  PostMatchDefaults,
  PostMatchToDoc,
  AdoptionRequestDefaults,
  AdoptionRequestToDoc,
} from '../src/data/model';

const suffix = () => randomBytes(6).toString('hex');
async function remove(path: string) {
  const result = await fetch(firestoreBase + '/' + path, { method: 'DELETE', headers });
  expect(result.ok).toBe(true);
}
async function missing(path: string) {
  return (await fetch(firestoreBase + '/' + path, { headers })).status === 404;
}
async function seedPost(id: string, ownerId: string, category: string, hidden = false) {
  await write(
    'posts/' + id,
    PostToDoc({
      ...PostDefaults,
      ownerId,
      category,
      hidden,
      name: id,
      lat: 25.2048,
      lng: 55.2708,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      lastActivityAt: Date.now(),
    }),
  );
}
async function deletePost(page: Page, id: string) {
  await page.evaluate(async (id) => {
    const { PostRepository } = await import('/src/data/PostRepository.ts' /* @vite-ignore */);
    const post = await PostRepository.post(id);
    if (!post) throw new Error('Missing fixture');
    await PostRepository.delete(post);
  }, id);
}

test('B1: hidden posts become unavailable without deleting matches, then return after restoring', async ({
  page,
}) => {
  const owner = await account(),
    reporter = await account(),
    lost = 'lost_' + suffix(),
    sighting = 'seen_' + suffix();
  await login(page, reporter);
  await seedPost(lost, owner.uid, 'lost');
  await seedPost(sighting, reporter.uid, 'spotted');
  const match = lost + '__' + sighting;
  await write(
    'matches/' + match,
    PostMatchToDoc({
      ...PostMatchDefaults,
      lostPostId: lost,
      sightingPostId: sighting,
      lostOwnerId: owner.uid,
      sightingOwnerId: reporter.uid,
      members: [owner.uid, reporter.uid],
      tier: 'possible',
      score: 0.75,
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }),
  );
  await navigate(page, '/post/' + sighting);
  await expect(page.locator('.match-card')).toHaveCount(1);
  await write('posts/' + lost, { hidden: true }, ['hidden']);
  await navigate(page, '/settings');
  await navigate(page, '/post/' + sighting);
  await expect(page.getByRole('heading', { name: sighting, exact: true }).first()).toBeVisible();
  await expect(page.locator('.match-card')).toHaveCount(0);
  await navigate(page, '/match/' + match);
  await expect(page.getByText('This match is no longer available.', { exact: true })).toBeVisible();
  await navigate(page, '/post/' + lost);
  await expect(
    page.getByText('This post was removed or is no longer available.', { exact: true }),
  ).toBeVisible();
  expect(await missing('matches/' + match)).toBe(false);
  await expect(page.locator('.snackbar')).not.toContainText(/permission/i);
  await write('posts/' + lost, { hidden: false }, ['hidden']);
  await navigate(page, '/post/' + sighting);
  await expect(page.locator('.match-card')).toHaveCount(1);
});

test('B2/B3: deleting a listing cleans requests and embeddings; either person prunes old orphan requests', async ({
  page,
  browser,
}) => {
  const owner = await account(),
    requester = await account(),
    other = await browser.newContext(),
    sentPage = await other.newPage();
  try {
    await login(page, owner);
    await login(sentPage, requester);
    const seed = async () => {
      const id = 'adopt_' + suffix(),
        request = id + '_' + requester.uid;
      await seedPost(id, owner.uid, 'adoption');
      await write(
        'adoptionRequests/' + request,
        AdoptionRequestToDoc({
          ...AdoptionRequestDefaults,
          postId: id,
          postName: id,
          ownerId: owner.uid,
          requesterId: requester.uid,
          requesterName: requester.username,
          message: 'A safe home',
          createdAt: Date.now(),
        }),
      );
      return { id, request };
    };
    const current = await seed();
    await write('postEmbeddings/' + current.id, { ownerId: owner.uid, vectors: [] });
    await deletePost(page, current.id);
    expect(await missing('adoptionRequests/' + current.request)).toBe(true);
    expect(await missing('postEmbeddings/' + current.id)).toBe(true);
    const received = await seed();
    await remove('posts/' + received.id);
    await navigate(page, '/adoption-requests');
    await expect.poll(() => missing('adoptionRequests/' + received.request)).toBe(true);
    await expect(page.getByRole('button', { name: 'Approve', exact: true })).toHaveCount(0);
    const sent = await seed();
    await remove('posts/' + sent.id);
    await navigate(sentPage, '/adoption-requests');
    await sentPage.getByRole('button', { name: 'Sent', exact: true }).click();
    await expect.poll(() => missing('adoptionRequests/' + sent.request)).toBe(true);
    await expect(
      sentPage.getByRole('button', { name: 'Withdraw request', exact: true }),
    ).toHaveCount(0);
  } finally {
    await other.close();
  }
});

test('B7: deleted favourites are pruned while hidden favourites survive', async ({ page }) => {
  const viewer = await account(),
    owner = await account(),
    gone = 'gone_' + suffix(),
    hidden = 'hidden_' + suffix();
  await login(page, viewer);
  await seedPost(hidden, owner.uid, 'lost', true);
  for (const id of [gone, hidden])
    await write(`users/${viewer.uid}/favorites/${id}`, { postId: id, savedAt: Date.now() });
  await navigate(page, '/favorites');
  await expect.poll(() => missing(`users/${viewer.uid}/favorites/${gone}`)).toBe(true);
  expect(await missing(`users/${viewer.uid}/favorites/${hidden}`)).toBe(false);
});

test('B8: deleting latest text/photo restores the preceding preview and removes its thumbnail', async ({
  page,
}) => {
  const sender = await account(),
    other = await account();
  await login(page, sender);
  const result = await page.evaluate(async (otherUid) => {
    const { ChatRepository: chats } = await import(
      '/src/data/ChatRepository.ts' /* @vite-ignore */
    );
    const { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
    const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
    const { read, list } = await import('/src/data/Firestore.ts' /* @vite-ignore */);
    const { ImageRepository } = await import('/src/data/ImageRepository.ts' /* @vite-ignore */);
    const { ChatFromDoc, MessageFromDoc } = await import(
      '/src/data/model/index.ts' /* @vite-ignore */
    );
    const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
    const id = await chats.open(me, otherUid);
    let chat = (await read('chats/' + id, ChatFromDoc))!;
    await chats.send(chat, me, 'First message');
    await new Promise((resolve) => setTimeout(resolve, 5));
    await chats.send(chat, me, 'Second message');
    let messages = await list(chats.messages(id), MessageFromDoc);
    await chats.deleteMessage(id, messages.at(-1)!, me.uid);
    const preview = (await read('chats/' + id, ChatFromDoc))!.lastText;
    const jpeg = new Uint8Array([255, 216, 255, 217]);
    const image = await ImageRepository.upload(
      me.uid,
      {
        full: jpeg,
        thumb: jpeg,
        analysis: document.createElement('canvas'),
      },
      id,
    );
    chat = (await read('chats/' + id, ChatFromDoc))!;
    await chats.send(chat, me, '', image);
    messages = await list(chats.messages(id), MessageFromDoc);
    await chats.deleteMessage(id, messages.at(-1)!, me.uid);
    const photoGone = (await read('images/' + image, () => true)) === null;
    const thumbnailGone = (await read('images/' + image + '_t', () => true)) === null;
    await chats.send(chat, me, 'Older message');
    await new Promise((resolve) => setTimeout(resolve, 5));
    await chats.send(chat, me, 'Newest message');
    messages = await list(chats.messages(id), MessageFromDoc);
    await chats.deleteMessage(id, messages.at(-2)!, me.uid);
    const newestPreview = (await read('chats/' + id, ChatFromDoc))!.lastText;
    return { preview, photoGone, thumbnailGone, newestPreview };
  }, other.uid);
  expect(result).toEqual({
    preview: 'First message',
    photoGone: true,
    thumbnailGone: true,
    newestPreview: 'Newest message',
  });
});
