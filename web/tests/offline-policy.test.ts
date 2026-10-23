import { describe, it, expect } from 'vitest';
import { optionalRouteAsset } from '../src/offlinePolicy';
describe('optional static route cache policy', () => {
  const match = (path: string, sameOrigin = true, destination = 'script') =>
    optionalRouteAsset({
      url: new URL(path, 'https://catmap.test'),
      sameOrigin,
      request: { destination } as Request,
    });
  it('caches rare static routes on demand', () => {
    expect(match('/assets/AdminScreens-abc_123.js')).toBe(true);
    expect(match('/assets/PosterScreen-xyz.js')).toBe(true);
  });
  it.each([
    '/users/alice',
    '/v1/projects/demo/databases/documents',
    '/images/private',
    '/assets/firebase-ab.js',
    '/chat/private',
    '/assets/AdminScreens-abc.json',
  ])('does not runtime-cache account or other responses: %s', (path) =>
    expect(match(path)).toBe(false),
  );
  it('refuses cross-origin and non-script requests', () => {
    expect(match('/assets/AdminScreens-abc.js', false)).toBe(false);
    expect(match('/assets/AdminScreens-abc.js', true, 'empty')).toBe(false);
  });
});
