import { authentication, appCheckToken } from '../firebase';
import { config } from '../config';

/** Private JPEGs never enter Firestore SDK persistence or an HTTP cache. */
async function request(suffix: string, body?: unknown) {
  const auth = authentication(),
    user = auth.currentUser;
  if (!user) throw new Error('Sign-in required for private photo');
  const project = auth.app.options.projectId;
  const origin = config.emulators
    ? `http://127.0.0.1:${Number(import.meta.env.VITE_FIRESTORE_EMULATOR_PORT || 8080)}`
    : 'https://firestore.googleapis.com';
  const headers: Record<string, string> = {
    Authorization: 'Bearer ' + (await user.getIdToken()),
    'Content-Type': 'application/json',
  };
  if (!config.emulators) {
    try {
      const token = await appCheckToken();
      if (token) headers['X-Firebase-AppCheck'] = token;
    } catch {
      /* Enforcement, if enabled, fails closed at the server. */
    }
  }
  const response = await fetch(
    `${origin}/v1/projects/${encodeURIComponent(project!)}/databases/(default)/documents${suffix}`,
    {
      method: body ? 'POST' : 'GET',
      headers,
      body: body ? JSON.stringify(body) : undefined,
      cache: 'no-store',
      credentials: 'omit',
      signal: AbortSignal.timeout(15000),
    },
  );
  if (auth.currentUser?.uid !== user.uid)
    throw new Error('Account changed during private photo transfer');
  if (!response.ok) throw new Error(`Private photo transfer failed (${response.status})`);
  return response.json();
}
function base64(bytes: Uint8Array) {
  let value = '';
  for (let i = 0; i < bytes.length; i += 8192)
    value += String.fromCharCode(...bytes.subarray(i, i + 8192));
  return btoa(value);
}
export async function privateImageBytes(id: string): Promise<Uint8Array | null> {
  if (!/^c_[A-Za-z0-9_-]+$/.test(id)) throw new Error('Invalid private photo ID');
  const data = await request('/images/' + encodeURIComponent(id));
  const value = data.fields?.data?.bytesValue;
  return typeof value === 'string' ? Uint8Array.from(atob(value), (c) => c.charCodeAt(0)) : null;
}
export async function uploadPrivateImage(
  id: string,
  ownerId: string,
  chatId: string,
  full: Uint8Array,
  thumb: Uint8Array,
  at: number,
) {
  const project = authentication().app.options.projectId;
  const name = (path: string) => `projects/${project}/databases/(default)/documents/${path}`;
  const fields = (data: Record<string, string | number>) =>
    Object.fromEntries(
      Object.entries(data).map(([key, value]) => [
        key,
        typeof value === 'number' ? { integerValue: String(value) } : { stringValue: value },
      ]),
    );
  const common = { ownerId, chatId, audience: 'chat', rootId: id, messageId: '', createdAt: at };
  return request(':commit', {
    writes: [
      ...[
        [id, full],
        [id + '_t', thumb],
      ].map(([key, bytes]) => ({
        update: {
          name: name('images/' + key),
          fields: { ...fields(common), data: { bytesValue: base64(bytes as Uint8Array) } },
        },
        currentDocument: { exists: false },
      })),
      {
        update: {
          name: name('mediaUploads/' + id),
          fields: fields({
            ownerId,
            chatId,
            audience: 'chat',
            status: 'staged',
            expiresAt: at + 7 * 86400000,
          }),
        },
        currentDocument: { exists: false },
      },
    ],
  });
}
