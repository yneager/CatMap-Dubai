import { beforeEach, describe, expect, it, vi } from 'vitest';
const state = vi.hoisted(() => ({
  auth: { currentUser: { uid: 'alice' } },
  availability: vi.fn(),
}));
vi.mock('../src/firebase', () => ({ auth: state.auth }));
vi.mock('../src/data/PostRepository', () => ({
  PostRepository: { availability: state.availability },
}));
beforeEach(() => {
  vi.resetModules();
  state.auth.currentUser.uid = 'alice';
  state.availability.mockReset();
  state.availability.mockResolvedValue('there');
});
describe('bounded post availability work', () => {
  it('coalesces the same source within a foreground revision', async () => {
    const { cachedAvailability } = await import('../src/data/PostAvailabilityCache');
    const signal = new AbortController().signal;
    const a = cachedAvailability('alice', 'p', 1, signal),
      b = cachedAvailability('alice', 'p', 1, signal);
    expect(a).toBe(b);
    await a;
    expect(state.availability).toHaveBeenCalledTimes(1);
  });
  it('refreshes after a foreground revision changes', async () => {
    const { cachedAvailability } = await import('../src/data/PostAvailabilityCache');
    const signal = new AbortController().signal;
    await cachedAvailability('alice', 'p', 1, signal);
    await cachedAvailability('alice', 'p', 2, signal);
    expect(state.availability).toHaveBeenCalledTimes(2);
  });
  it('abandons queued work when every consumer leaves', async () => {
    const { cachedAvailability } = await import('../src/data/PostAvailabilityCache');
    const controller = new AbortController();
    controller.abort();
    await cachedAvailability('alice', 'p', 1, controller.signal);
    expect(state.availability).not.toHaveBeenCalled();
  });
  it('does not run more than four server checks at once', async () => {
    const releases: (() => void)[] = [];
    let active = 0,
      max = 0;
    state.availability.mockImplementation(
      () =>
        new Promise((resolve) => {
          active++;
          max = Math.max(max, active);
          releases.push(() => {
            active--;
            resolve('there');
          });
        }),
    );
    const { cachedAvailability } = await import('../src/data/PostAvailabilityCache');
    const signal = new AbortController().signal;
    const pending = Array.from({ length: 12 }, (_, i) =>
      cachedAvailability('alice', 'p' + i, 1, signal),
    );
    for (let i = 0; i < 12; i++) {
      await vi.waitFor(() => expect(releases.length).toBeGreaterThan(i));
      releases[i]();
    }
    await Promise.all(pending);
    expect(max).toBe(4);
    expect(active).toBe(0);
  });
  it('retires requests for a different signed-in account', async () => {
    const { cachedAvailability } = await import('../src/data/PostAvailabilityCache');
    state.auth.currentUser.uid = 'bob';
    await cachedAvailability('alice', 'p', 1, new AbortController().signal);
    expect(state.availability).not.toHaveBeenCalled();
  });
});
