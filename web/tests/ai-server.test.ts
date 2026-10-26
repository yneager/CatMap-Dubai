import { it, expect, vi } from 'vitest';
import { AiServerClient, parseReview, type AiTransport } from '../src/data/AiServer';
it('ignores stale answers and unsubscribes only after the exact request answer', async () => {
  let next: ((d: Record<string, unknown>) => void) | undefined;
  let written: Record<string, unknown> = {};
  const unsub = vi.fn();
  const io: AiTransport = {
    uid: () => 'uid',
    get: async () => null,
    set: async (_path, data) => {
      written = data;
    },
    listen: (_path, callback) => {
      next = callback;
      return unsub;
    },
    timestamp: () => 'server',
  };
  const run = new AiServerClient(io, 100).run({ kind: 'embed', images: [], ids: [] });
  await vi.waitFor(() => expect(next).toBeDefined());
  next!({ status: 'done', answeredRequestId: 'old' });
  expect(unsub).not.toHaveBeenCalled();
  const answer = {
    status: 'done',
    answeredRequestId: written.requestId,
    embeddings: [{ v: [1, 0] }],
  };
  next!(answer);
  expect(await run).toEqual(answer);
  expect(unsub).toHaveBeenCalledTimes(1);
  expect(written).toMatchObject({ uid: 'uid', status: 'pending', createdAt: 'server' });
});
it('returns null and unsubscribes after timeout or matching error', async () => {
  const unsubscribe = vi.fn();
  const io: AiTransport = {
    uid: () => 'uid',
    get: async () => null,
    set: async () => {},
    listen: () => unsubscribe,
    timestamp: () => null,
  };
  expect(await new AiServerClient(io, 10).run({ kind: 'embed' })).toBeNull();
  expect(unsubscribe).toHaveBeenCalledTimes(1);
});
it('checks the 390-second heartbeat and returns null on transport failure', async () => {
  const io: AiTransport = {
    uid: () => null,
    get: async () => ({ online: true, lastSeen: { toMillis: () => Date.now() - 390001 } }),
    set: async () => {},
    listen: () => () => {},
    timestamp: () => null,
  };
  expect(await new AiServerClient(io).health()).toBeNull();
  expect(await new AiServerClient(io).run({})).toBeNull();
  io.get = async () => {
    throw new Error('offline');
  };
  expect(await new AiServerClient(io).health()).toBeNull();
});
it('deletes only the answered request, and never an aborted replacement', async () => {
  let current: Record<string, unknown> | null = null;
  let next: ((d: Record<string, unknown>) => void) | undefined;
  const remove = vi.fn(async () => {});
  const io: AiTransport = {
    uid: () => 'uid',
    get: async () => current,
    set: async (_path, data) => {
      current = data;
    },
    listen: (_path, callback) => {
      next = callback;
      return () => {};
    },
    timestamp: () => null,
    delete: remove,
  };
  const run = new AiServerClient(io, 50).run({ kind: 'embed' });
  await vi.waitFor(() => expect(next).toBeDefined());
  next!({ status: 'done', answeredRequestId: current!.requestId });
  await run;
  await vi.waitFor(() => expect(remove).toHaveBeenCalledOnce());
  remove.mockClear();
  next = undefined;
  current = null;
  const controller = new AbortController();
  const aborted = new AiServerClient(io, 50).run({ kind: 'embed' }, controller.signal);
  await vi.waitFor(() => expect(next).toBeDefined());
  controller.abort();
  await aborted;
  expect(remove).not.toHaveBeenCalled();
  current = { requestId: 'newer-request' };
  await new Promise((resolve) => setTimeout(resolve, 65));
  expect(remove).not.toHaveBeenCalled();
});
it('deletes its own request after the timeout and tolerates a refused delete', async () => {
  let current: Record<string, unknown> | null = null;
  const remove = vi.fn(async () => {
    throw new Error('refused');
  });
  const io: AiTransport = {
    uid: () => 'uid',
    get: async () => current,
    set: async (_p, d) => {
      current = d;
    },
    listen: () => () => {},
    timestamp: () => null,
    delete: remove,
  };
  expect(await new AiServerClient(io, 10).run({ kind: 'embed' })).toBeNull();
  await vi.waitFor(() => expect(remove).toHaveBeenCalledOnce());
});
// Photo review (docs/MATCHING_REVIEW_V1.md); the same cases as Android's PhotoReviewTest.
it('reads a photo review in the PC order, keeping only asked cats with two or more photos', () => {
  const asked = ['tom', 'ash', 'solo'];
  expect(
    parseReview(
      {
        protocol: 'review-v1',
        ranked: [
          { id: 'ash', score: 0.31, photoIds: ['a1', 'a2', 'a3'] },
          { id: 'stranger', photoIds: ['x1', 'x2'] },
          { id: 'tom', photoIds: ['t1', 't2', 't3', 't4', 't5'] },
          { id: 'tom', photoIds: ['t1', 't2'] },
          { id: 'solo', photoIds: ['s1'] },
          'not a map',
        ],
        unavailable: [
          { id: 'solo', reason: 'too_few_photos' },
          { id: 'stranger', reason: 'hidden' },
        ],
      },
      asked,
    ),
  ).toEqual({
    ranked: [
      { id: 'ash', photoIds: ['a1', 'a2', 'a3'] },
      { id: 'tom', photoIds: ['t1', 't2', 't3', 't4'] },
    ],
    unavailable: [{ id: 'solo', reason: 'too_few_photos' }],
  });
  expect(parseReview(null, asked)).toBeNull();
  expect(parseReview({ ranked: [] }, asked)).toBeNull();
  expect(parseReview({ protocol: 'review-v2', ranked: [] }, asked)).toBeNull();
});
it('sends a photo review only to a PC that offers it, with its photo and the cat ids only', async () => {
  let written: Record<string, unknown> = {};
  let next: ((d: Record<string, unknown>) => void) | undefined;
  let capabilities: string[] = [];
  const io: AiTransport = {
    uid: () => 'uid',
    get: async (path) =>
      path === 'config/aiWorker'
        ? { online: true, lastSeen: { toMillis: () => Date.now() }, capabilities }
        : null,
    set: async (_path, data) => {
      written = data;
    },
    listen: (_path, callback) => {
      next = callback;
      return () => {};
    },
    timestamp: () => null,
  };
  const client = new AiServerClient(io, 100, 100);
  const photo = new Uint8Array([0xff, 0xd8, 0xff, 1]);
  expect(await client.review(photo, ['tom'], ['Tom'])).toBeNull();
  expect(written).toEqual({});
  capabilities = ['review-v1'];
  expect(await client.review(photo, [], [])).toBeNull();
  expect(
    await client.review(
      photo,
      Array.from({ length: 9 }, (_, i) => 'c' + i),
      [],
    ),
  ).toBeNull();
  expect(await client.review(new Uint8Array(150001), ['tom'], [])).toBeNull();
  const pending = client.review(photo, ['tom', 'ash'], ['Tom', 'Ash']);
  await vi.waitFor(() => expect(next).toBeDefined());
  expect(written).toMatchObject({
    kind: 'review',
    images: [],
    photos: [],
    ids: ['tom', 'ash'],
    labels: ['Tom', 'Ash'],
  });
  next!({
    status: 'done',
    answeredRequestId: written.requestId,
    review: {
      protocol: 'review-v1',
      ranked: [{ id: 'ash', photoIds: ['a1', 'a2'] }],
      unavailable: [],
    },
  });
  expect(await pending).toEqual({
    ranked: [{ id: 'ash', photoIds: ['a1', 'a2'] }],
    unavailable: [],
  });
  // A PC that answers without a review (an older one) gives "not reviewed", never "no match".
  next = undefined;
  const old = client.review(photo, ['tom'], ['Tom']);
  await vi.waitFor(() => expect(next).toBeDefined());
  next!({ status: 'done', answeredRequestId: written.requestId, results: [] });
  expect(await old).toBeNull();
});
it('runs one job at a time, so a new job never replaces a pending one', async () => {
  const writes: string[] = [];
  const listeners: ((d: Record<string, unknown>) => void)[] = [];
  let current: Record<string, unknown> = {};
  const io: AiTransport = {
    uid: () => 'uid',
    get: async () => null,
    set: async (_path, data) => {
      current = data;
      writes.push(String(data.kind));
    },
    listen: (_path, callback) => {
      listeners.push(callback);
      return () => {};
    },
    timestamp: () => null,
  };
  const client = new AiServerClient(io, 200);
  const first = client.run({ kind: 'check' });
  const second = client.run({ kind: 'review' });
  await vi.waitFor(() => expect(listeners.length).toBe(1));
  expect(writes).toEqual(['check']);
  listeners[0]({ status: 'done', answeredRequestId: current.requestId });
  await first;
  await vi.waitFor(() => expect(writes).toEqual(['check', 'review']));
  listeners[1]({ status: 'done', answeredRequestId: current.requestId });
  expect(await second).toMatchObject({ status: 'done' });
});
