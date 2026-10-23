import { beforeEach, describe, expect, it, vi } from 'vitest';
const state = vi.hoisted(() => ({
  docs: new Map<string, Record<string, unknown>>(),
  beforeRead: undefined as undefined | ((path: string) => void),
  deletes: [] as string[],
}));
vi.mock('../src/firebase', () => ({ database: () => ({}), auth: { currentUser: { uid: 'bob' } } }));
vi.mock('../src/data/Firestore', async (original) => ({
  ...(await original<typeof import('../src/data/Firestore')>()),
  ref: (path: string) => ({ path, id: path.split('/').at(-1) }),
  listQuery: (path: string) => ({ path }),
}));
vi.mock('../src/data/ImageRepository', () => ({
  ImageRepository: { delete: vi.fn(async () => {}), retain: vi.fn(async () => {}) },
}));
vi.mock('firebase/firestore', async (original) => {
  const snap = (path: string) => ({
    id: path.split('/').at(-1)!,
    ref: { path },
    exists: () => state.docs.has(path),
    data: () => state.docs.get(path),
  });
  return {
    ...(await original<typeof import('firebase/firestore')>()),
    getDocsFromServer: vi.fn(async (q: { path: string }) => ({
      docs: [...state.docs.entries()]
        .filter(([path]) => path.startsWith(q.path + '/'))
        .sort((a, b) => Number(b[1].createdAt) - Number(a[1].createdAt))
        .slice(0, 2)
        .map(([path]) => snap(path)),
    })),
    runTransaction: vi.fn(async (_db, callback) => {
      const writes: (() => void)[] = [];
      await callback({
        get: async (ref: { path: string }) => {
          state.beforeRead?.(ref.path);
          return snap(ref.path);
        },
        delete: (ref: { path: string }) =>
          writes.push(() => {
            state.docs.delete(ref.path);
            state.deletes.push(ref.path);
          }),
        update: (ref: { path: string }, data: Record<string, unknown>) =>
          writes.push(() => state.docs.set(ref.path, { ...state.docs.get(ref.path), ...data })),
      });
      writes.forEach((write) => write());
    }),
  };
});
import { ChatRepository } from '../src/data/ChatRepository';
import { MessageDefaults } from '../src/data/model';
const path = 'chats/alice_bob';
const a = { ...MessageDefaults, id: 'a', senderId: 'alice', text: 'A', createdAt: 10 };
const b = { ...MessageDefaults, id: 'b', senderId: 'bob', text: 'B', createdAt: 20 };
beforeEach(() => {
  state.docs.clear();
  state.deletes.length = 0;
  state.beforeRead = undefined;
  state.docs.set(path, { lastMessageId: 'b', lastSender: 'bob', lastText: 'B', lastAt: 20 });
  for (const m of [a, b]) state.docs.set(`${path}/messages/${m.id}`, m);
});
describe('atomic last-message deletion', () => {
  it('restores all predecessor fields, then clears a fully deleted chat', async () => {
    await ChatRepository.deleteMessage('alice_bob', b, 'bob');
    expect(state.docs.get(path)).toMatchObject({
      lastMessageId: 'a',
      lastSender: 'alice',
      lastText: 'A',
      lastAt: 10,
    });
    await ChatRepository.deleteMessage('alice_bob', a, 'alice');
    expect(state.docs.get(path)).toMatchObject({
      lastMessageId: '',
      lastSender: '',
      lastText: '',
      lastAt: 0,
    });
  });
  it('requeries when the selected predecessor disappears before its transaction read', async () => {
    state.beforeRead = (ref) => {
      if (ref === `${path}/messages/a`) {
        state.docs.delete(ref);
        state.beforeRead = undefined;
      }
    };
    await ChatRepository.deleteMessage('alice_bob', b, 'bob');
    expect(state.docs.get(path)).toMatchObject({ lastMessageId: '', lastSender: '', lastText: '' });
  });
  it('does not rewrite a newer message preview when deleting an older message', async () => {
    state.docs.set(path, {
      lastMessageId: 'new',
      lastSender: 'alice',
      lastText: 'New',
      lastAt: 30,
    });
    state.docs.set(`${path}/messages/new`, { ...a, id: 'new', text: 'New', createdAt: 30 });
    await ChatRepository.deleteMessage('alice_bob', b, 'bob');
    expect(state.docs.get(path)?.lastMessageId).toBe('new');
    expect(state.docs.has(`${path}/messages/b`)).toBe(false);
  });
  it('does not delete a counterpart message', async () => {
    await expect(ChatRepository.deleteMessage('alice_bob', a, 'bob')).rejects.toThrow(
      'Message owner mismatch',
    );
    expect(state.docs.has(`${path}/messages/a`)).toBe(true);
  });
});
