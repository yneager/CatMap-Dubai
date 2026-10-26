import { test, expect } from '@playwright/test';
import { account, login, readStatusAs, registered } from './helpers';

test('private chat photo transfers avoid SDK persistence and reject outsiders and blocked reads', async ({
  page,
  browser,
}) => {
  const a = await account(),
    b = await account(),
    // The outsider reads over REST with their own sign-in: a third signed-in browser tab exhausts WebKit's six
    // HTTP/1.1 connections to the emulator (production Firestore uses HTTP/2), and their check needs no app.
    x = await registered(await account());
  const bc = await browser.newContext();
  try {
    const bp = await bc.newPage();
    await login(bp, b);
    await login(page, a);
    const image = await page.evaluate(async (other) => {
      const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
      const { UserRepository } = await import('/src/data/UserRepository.ts' /* @vite-ignore */);
      const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */);
      const { ImageRepository } = await import('/src/data/ImageRepository.ts' /* @vite-ignore */);
      const { read } = await import('/src/data/Firestore.ts' /* @vite-ignore */);
      const { ChatFromDoc } = await import('/src/data/model/index.ts' /* @vite-ignore */);
      const me = (await UserRepository.profile(authentication().currentUser!.uid))!;
      const chat = await ChatRepository.open(me, other);
      const jpeg = new Uint8Array([255, 216, 255, 217]);
      const id = await ImageRepository.upload(
        me.uid,
        { full: jpeg, thumb: jpeg, analysis: document.createElement('canvas') },
        chat,
      );
      await ChatRepository.send((await read('chats/' + chat, ChatFromDoc))!, me, '', id);
      return id;
    }, b.uid);
    expect(image).toMatch(/^c_/);
    const readPhoto = async (target: typeof page) =>
      target.evaluate(async (id) => {
        const { ImageRepository } = await import('/src/data/ImageRepository.ts' /* @vite-ignore */);
        return (await ImageRepository.bytes(id))?.length ?? 0;
      }, image);
    expect(await readPhoto(bp)).toBe(4);
    for (const doc of ['images/' + image, 'images/' + image + '_t'])
      expect(await readStatusAs(x, doc)).toBe(403);
    for (const target of [page, bp])
      expect(
        await target.evaluate(async (id) => {
          const probeUrl = '/e2e/fixtures/private-cache-probe.ts';
          const { hasPrivateBytes } = (await import(
            /* @vite-ignore */ probeUrl
          )) as typeof import('./fixtures/private-cache-probe');
          return hasPrivateBytes(id);
        }, image),
      ).toBe(false);
    await bp.evaluate(async (other) => {
      const { ChatRepository } = await import('/src/data/ChatRepository.ts' /* @vite-ignore */);
      const { authentication } = await import('/src/firebase.ts' /* @vite-ignore */);
      await ChatRepository.block(authentication().currentUser!.uid, other);
    }, a.uid);
    expect(await readPhoto(bp)).toBe(0);
  } finally {
    await bc.close();
  }
});
