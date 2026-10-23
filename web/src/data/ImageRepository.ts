import { doc, getDoc, Bytes, writeBatch, runTransaction } from 'firebase/firestore';
import { auth, database } from '../firebase';
import { privateImageBytes, uploadPrivateImage } from './PrivateImageTransport';
import { col, ref } from './Firestore';
import * as idb from '../util/idb';
import type { ProcessedPhoto } from '../util/images';
interface Cached {
  data: Uint8Array;
  at: number;
}
const urls = new Map<string, { url: string; size: number }>();
const pending = new Map<string, Promise<Uint8Array | null>>();
const MAX_BYTES = 80 * 1024 * 1024,
  TTL = 30 * 86400000;
let cacheQueue = Promise.resolve();
let epoch = 0;
let urlBytes = 0;
const touched = new Map<string, number>();
const privateImage = (id: string) => id.startsWith('c_');
const diskKey = (id: string) => 'image_v2_' + id;
function cache(key: string, data: Uint8Array) {
  const version = epoch;
  if (privateImage(key)) return Promise.resolve();
  cacheQueue = cacheQueue
    .catch(() => {})
    .then(() => (version === epoch ? writeCache(key, data) : undefined));
  return cacheQueue;
}
async function writeCache(key: string, data: Uint8Array) {
  await idb.put(diskKey(key), { data, at: Date.now() });
  let index = (await idb.get<{ key: string; size: number; at: number }[]>('image_index')) ?? [];
  index = index.filter((entry) => entry.key !== key);
  index.push({ key, size: data.byteLength, at: Date.now() });
  let total = index.reduce((sum, x) => sum + x.size, 0);
  while (index.length && (total > MAX_BYTES || Date.now() - index[0].at > TTL)) {
    const entry = index.shift()!;
    total -= entry.size;
    await idb.remove(diskKey(entry.key));
  }
  await idb.put('image_index', index);
}
async function bytes(id: string, thumb = false): Promise<Uint8Array | null> {
  if (!id) return null;
  const key = id + (thumb ? '_t' : ''),
    version = epoch;
  const saved = privateImage(id)
    ? undefined
    : await idb.get<Cached>(diskKey(key)).catch(() => undefined);
  if (version !== epoch) return null;
  if (saved && Date.now() - saved.at < TTL) {
    // A hit never rewrites the JPEG. Touch only small eviction metadata, at most hourly.
    if (Date.now() - (touched.get(key) ?? 0) > 3600000) {
      touched.set(key, Date.now());
      while (touched.size > 4096) touched.delete(touched.keys().next().value!);
      cacheQueue = cacheQueue
        .catch(() => {})
        .then(async () => {
          if (version !== epoch) return;
          const index =
            (await idb.get<{ key: string; size: number; at: number }[]>('image_index')) ?? [];
          const entry = index.find((x) => x.key === key);
          if (entry) {
            entry.at = Date.now();
            index.sort((a, b) => a.at - b.at);
            await idb.put('image_index', index);
          }
        });
    }
    return saved.data;
  }
  if (pending.has(key)) return pending.get(key)!;
  const load = (async () => {
    const version = epoch;
    try {
      let data: Uint8Array | null;
      if (privateImage(id)) data = await privateImageBytes(key);
      else {
        const snap = await getDoc(ref('images/' + key)),
          record = snap.data();
        const value =
          record?.audience === 'community' || record?.ownerId === auth?.currentUser?.uid
            ? record?.data
            : null;
        data = value instanceof Bytes ? value.toUint8Array() : null;
      }
      if (version !== epoch) return null;
      if (data) await cache(key, data).catch(() => {});
      return data ?? (thumb ? bytes(id, false) : null);
    } catch {
      return thumb ? bytes(id, false) : null;
    }
  })();
  pending.set(key, load);
  try {
    return await load;
  } finally {
    pending.delete(key);
  }
}
export const ImageRepository = {
  bytes,
  url: async (id: string, thumb = true) => {
    const version = epoch;
    const key = id + (thumb ? '_t' : '');
    const cached = urls.get(key);
    if (cached && !privateImage(id)) {
      urls.delete(key);
      urls.set(key, cached);
      return cached.url;
    }
    const data = await bytes(id, thumb);
    if (version !== epoch) return null;
    if (!data) {
      if (cached) {
        URL.revokeObjectURL(cached.url);
        urls.delete(key);
        urlBytes -= cached.size;
      }
      return null;
    }
    if (cached) return cached.url; // Private cached URLs are used only after a fresh server authorization.
    const url = URL.createObjectURL(new Blob([new Uint8Array(data)], { type: 'image/jpeg' }));
    urls.set(key, { url, size: data.byteLength });
    urlBytes += data.byteLength;
    while (urlBytes > MAX_BYTES && urls.size > 1) {
      const first = urls.keys().next().value!;
      URL.revokeObjectURL(urls.get(first)!.url);
      urlBytes -= urls.get(first)!.size;
      urls.delete(first);
    }
    return url;
  },
  upload: async (ownerId: string, p: ProcessedPhoto, chatId = '') => {
    const generated = doc(col('images')).id;
    const target = ref('images/' + (chatId ? 'c_' : '') + generated);
    const audience = chatId ? 'chat' : 'community';
    const createdAt = Date.now();
    if (chatId) {
      await uploadPrivateImage(target.id, ownerId, chatId, p.full, p.thumb, createdAt);
      return target.id;
    }
    const b = writeBatch(database());
    b.set(target, {
      data: Bytes.fromUint8Array(p.full),
      ownerId,
      createdAt,
      audience,
      chatId,
      rootId: target.id,
      messageId: '',
    });
    b.set(ref(`images/${target.id}_t`), {
      data: Bytes.fromUint8Array(p.thumb),
      ownerId,
      createdAt,
      audience,
      chatId,
      rootId: target.id,
      messageId: '',
    });
    b.set(ref(`mediaUploads/${target.id}`), {
      ownerId,
      chatId,
      audience,
      status: 'staged',
      expiresAt: createdAt + 7 * 86400000,
    });
    await b.commit();
    await Promise.allSettled([cache(target.id, p.full), cache(target.id + '_t', p.thumb)]);
    return target.id;
  },
  retain: async (ids: string[]) => {
    const unique = [...new Set(ids.filter(Boolean))];
    for (let i = 0; i < unique.length; i += 6)
      await runTransaction(database(), async (tx) => {
        const leases = await Promise.all(
          unique.slice(i, i + 6).map((id) => tx.get(ref(`mediaUploads/${id}`))),
        );
        for (const lease of leases)
          if (lease.exists()) {
            if (!['staged', 'linked'].includes(lease.data().status))
              throw new Error('Staged photo expired; select it again');
            tx.update(lease.ref, { expiresAt: Date.now() + 7 * 86400000 });
          }
      });
  },
  delete: async (id: string) => {
    await cacheQueue.catch(() => {});
    const b = writeBatch(database());
    b.delete(ref('images/' + id));
    b.delete(ref('images/' + id + '_t'));
    b.delete(ref('mediaUploads/' + id));
    await b.commit();
    for (const key of [id, id + '_t']) {
      const value = urls.get(key);
      if (value) URL.revokeObjectURL(value.url);
      if (value) urlBytes -= value.size;
      urls.delete(key);
      await idb.remove(diskKey(key));
      await idb.remove('image_' + key);
    }
    const index = (await idb.get<{ key: string; size: number; at: number }[]>('image_index')) ?? [];
    await idb.put(
      'image_index',
      index.filter((x) => x.key !== id && x.key !== id + '_t'),
    );
  },
  clearCache: async () => {
    epoch++;
    await cacheQueue.catch(() => {});
    for (const value of urls.values()) URL.revokeObjectURL(value.url);
    urls.clear();
    urlBytes = 0;
    touched.clear();
    for (const entry of (await idb.get<{ key: string }[]>('image_index')) ?? []) {
      await idb.remove(diskKey(entry.key));
      await idb.remove('image_' + entry.key);
    }
    await idb.remove('image_index');
  },
};
