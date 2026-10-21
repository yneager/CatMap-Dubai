import { it, expect, vi, beforeEach } from 'vitest';
const sdk = vi.hoisted(() => ({
  getDocs: vi.fn(),
  query: vi.fn((...args: unknown[]) => args),
  startAfter: vi.fn((v: unknown) => ({ after: v })),
  limit: vi.fn((n: number) => ({ limit: n })),
}));
vi.mock('firebase/firestore', () => sdk);
import { Pager } from '../src/data/Pager';
import type { Query } from 'firebase/firestore';
const base = { name: 'base' } as unknown as Query;
const doc = (id: string) => ({ id, data: () => ({ text: id }) });
beforeEach(() => vi.clearAllMocks());
it('uses the last document cursor across equal-timestamp pages and stops at an incomplete page', async () => {
  const a = doc('a'),
    b = doc('b');
  sdk.getDocs
    .mockResolvedValueOnce({ docs: [a, b], size: 2 })
    .mockResolvedValueOnce({ docs: [doc('c')], size: 1 });
  const p = new Pager(
    () => base,
    (id, d) => ({ id, ...d }),
    2,
  );
  await p.next();
  expect(p.hasMore).toBe(true);
  await p.next();
  expect(sdk.startAfter).toHaveBeenCalledWith(b);
  expect(p.items).toEqual([
    { id: 'a', text: 'a' },
    { id: 'b', text: 'b' },
    { id: 'c', text: 'c' },
  ]);
  await p.next();
  expect(sdk.getDocs).toHaveBeenCalledTimes(2);
  expect(p.hasMore).toBe(false);
});
it('falls back only on a missing index and keeps failures retryable', async () => {
  const fallback = { name: 'fallback' } as unknown as Query;
  sdk.getDocs
    .mockRejectedValueOnce({ code: 'failed-precondition' })
    .mockResolvedValueOnce({ docs: [doc('x')], size: 1 });
  const p = new Pager(
    () => base,
    (id) => id,
    25,
    () => fallback,
  );
  await p.next();
  expect(p.items).toEqual(['x']);
  expect(sdk.query).toHaveBeenLastCalledWith(fallback, { limit: 25 });
  sdk.getDocs.mockRejectedValueOnce({ code: 'permission-denied' });
  const bad = new Pager(
    () => base,
    (id) => id,
    25,
    () => fallback,
  );
  await expect(bad.next()).rejects.toEqual({ code: 'permission-denied' });
  expect(bad.busy).toBe(false);
  expect(bad.hasMore).toBe(true);
});
