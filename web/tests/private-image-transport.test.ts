import { beforeEach, expect, it, vi } from 'vitest';
const state = vi.hoisted(() => ({
  auth: {
    app: { options: { projectId: 'demo-catmap' } },
    currentUser: { uid: 'alice', getIdToken: vi.fn(async () => 'synthetic-token') },
  },
}));
vi.mock('../src/firebase', () => ({
  authentication: () => state.auth,
  appCheckToken: async () => '',
}));
vi.mock('../src/config', () => ({ config: { emulators: true } }));
import { privateImageBytes, uploadPrivateImage } from '../src/data/PrivateImageTransport';
beforeEach(() => vi.restoreAllMocks());
it('uses no-store HTTP with the current token and decodes private bytes', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(
      new Response(JSON.stringify({ fields: { data: { bytesValue: 'AQID' } } }), { status: 200 }),
    );
  expect(await privateImageBytes('c_demo')).toEqual(new Uint8Array([1, 2, 3]));
  expect(fetch.mock.calls[0][0]).toContain('127.0.0.1:8080/v1/projects/demo-catmap/');
  expect(fetch.mock.calls[0][1]).toMatchObject({
    cache: 'no-store',
    credentials: 'omit',
    headers: { Authorization: 'Bearer synthetic-token' },
  });
});
it('commits both photos and the lease together without SDK writes', async () => {
  const fetch = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(new Response('{}', { status: 200 }));
  await uploadPrivateImage(
    'c_demo',
    'alice',
    'alice_bob',
    new Uint8Array([1]),
    new Uint8Array([2]),
    100,
  );
  const body = JSON.parse(fetch.mock.calls[0][1]!.body as string);
  expect(body.writes).toHaveLength(3);
  expect(
    body.writes.every(
      (w: { currentDocument: { exists: boolean } }) => w.currentDocument.exists === false,
    ),
  ).toBe(true);
  expect(body.writes[0].update.fields.data.bytesValue).toBe('AQ==');
  expect(body.writes[2].update.name).toContain('/mediaUploads/c_demo');
});
it('refuses a denied download instead of using a stale fallback', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 403 }));
  await expect(privateImageBytes('c_demo')).rejects.toThrow('(403)');
});
