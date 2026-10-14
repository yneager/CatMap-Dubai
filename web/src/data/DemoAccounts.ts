import * as idb from '../util/idb';
export const DemoAccounts = {
  DOMAIN: 'demo.catmap.test',
  ALL: [
    { username: 'admin', displayName: 'CatMap Admin', admin: true },
    { username: 'demo_ali', displayName: 'Ali' },
    { username: 'demo_mohammad', displayName: 'Mohammad' },
    { username: 'demo_anirudh', displayName: 'Anirudh' },
    { username: 'demo_hassan', displayName: 'Hassan' },
  ],
  find: (input: string) => {
    const key = input.trim().replace(/^@/, '').toLowerCase();
    return DemoAccounts.ALL.find(
      (a) => a.username === key || `${a.username}@${DemoAccounts.DOMAIN}` === key,
    );
  },
  isDemoEmail: (email: string | null | undefined) =>
    email?.toLowerCase().endsWith('@demo.catmap.test') ?? false,
};
async function key() {
  let key = await idb.get<CryptoKey>('vault_key');
  if (!key) {
    key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, [
      'encrypt',
      'decrypt',
    ]);
    await idb.put('vault_key', key);
  }
  return key;
}
export const DemoVault = {
  saved: async () => !!(await idb.get('vault_data')),
  save: async (password: string) => {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const data = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      await key(),
      new TextEncoder().encode(password),
    );
    await idb.put('vault_data', { iv, data });
  },
  password: async () => {
    try {
      const saved = await idb.get<{ iv: Uint8Array; data: ArrayBuffer }>('vault_data');
      return saved
        ? new TextDecoder().decode(
            await crypto.subtle.decrypt(
              { name: 'AES-GCM', iv: new Uint8Array(saved.iv) },
              await key(),
              saved.data,
            ),
          )
        : null;
    } catch {
      return null;
    }
  },
  clear: () => idb.remove('vault_data'),
};
