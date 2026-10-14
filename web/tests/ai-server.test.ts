import { it, expect, vi } from 'vitest';
import { AiServerClient, type AiTransport } from '../src/data/AiServer';
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
