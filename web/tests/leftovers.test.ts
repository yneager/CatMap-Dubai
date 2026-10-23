import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getDocFromServer, deleteDoc } from 'firebase/firestore';
import { PostRepository } from '../src/data/PostRepository';
import { MatchRepository } from '../src/data/MatchRepository';
import { PostMatchDefaults } from '../src/data/model';

vi.mock('firebase/firestore', async (original) => ({
  ...await original<typeof import('firebase/firestore')>(),
  getDocFromServer: vi.fn(),
  deleteDoc: vi.fn(() => Promise.resolve()),
}));
vi.mock('../src/data/Firestore', async (original) => ({
  ...await original<typeof import('../src/data/Firestore')>(),
  ref: (path: string) => ({ path }),
}));
beforeEach(() => vi.clearAllMocks());

describe('server-confirmed post availability', () => {
  it('marks only a server miss as gone', async () => {
    vi.mocked(getDocFromServer).mockResolvedValue({ exists: () => false } as never);
    expect(await PostRepository.availability('missing')).toBe('gone');
  });
  it('keeps readable posts', async () => {
    vi.mocked(getDocFromServer).mockResolvedValue({ exists: () => true } as never);
    expect(await PostRepository.availability('visible')).toBe('there');
  });
  it('distinguishes hidden posts so cleanup can retain their references', async () => {
    vi.mocked(getDocFromServer).mockRejectedValue({ code: 'permission-denied' });
    expect(await PostRepository.availability('hidden')).toBe('hidden');
  });
  it.each(['unavailable', 'deadline-exceeded', 'unauthenticated'])('does not delete on %s', async (code) => {
    vi.mocked(getDocFromServer).mockRejectedValue({ code });
    expect(await PostRepository.availability('unknown')).toBe('there');
  });
});

describe('match cleanup', () => {
  const match = { ...PostMatchDefaults, id: 'lost__seen', lostPostId: 'lost', sightingPostId: 'seen' };
  it('hides a hidden match without deleting it', async () => {
    vi.spyOn(PostRepository, 'availability').mockResolvedValueOnce('hidden').mockResolvedValueOnce('there');
    expect(await MatchRepository.orphaned(match)).toBe(true);
    expect(deleteDoc).not.toHaveBeenCalled();
  });
  it('deletes a match only when a post is confirmed gone', async () => {
    vi.spyOn(PostRepository, 'availability').mockResolvedValueOnce('hidden').mockResolvedValueOnce('gone');
    expect(await MatchRepository.orphaned(match)).toBe(true);
    expect(deleteDoc).toHaveBeenCalledWith({ path: 'matches/lost__seen' });
  });
  it('retains matches when the server is unavailable', async () => {
    vi.spyOn(PostRepository, 'availability').mockResolvedValue('there');
    expect(await MatchRepository.orphaned(match)).toBe(false);
    expect(deleteDoc).not.toHaveBeenCalled();
  });
});
