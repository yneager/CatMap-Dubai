import { doc, getDoc, Bytes, writeBatch } from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref } from './Firestore';
import * as idb from '../util/idb';
import type { ProcessedPhoto } from '../util/images';
interface Cached {
  data: Uint8Array;
  at: number;
}
const urls = new Map<string, { url: string; size: number }>();
const pending = new Map<string, Promise<Uint8Array | null>>();
const MAX_BYTES = 40 * 1024 * 1024,
  TTL = 86400000;
let cacheQueue = Promise.resolve();
function cache(key: string, data: Uint8Array) {
  cacheQueue = cacheQueue.catch(() => {}).then(() => writeCache(key, data));
  return cacheQueue;
}
async function writeCache(key: string, data: Uint8Array) {
  await idb.put('image_' + key, { data, at: Date.now() });
  let index = (await idb.get<{ key: string; size: number; at: number }[]>('image_index')) ?? [];
  index = index.filter((entry) => entry.key !== key);
  index.push({ key, size: data.byteLength, at: Date.now() });
  let total = index.reduce((sum, x) => sum + x.size, 0);
  while (index.length && (total > MAX_BYTES || Date.now() - index[0].at > TTL)) {
    const entry = index.shift()!;
    total -= entry.size;
    await idb.remove('image_' + entry.key);
  }
  await idb.put('image_index', index);
}
async function bytes(id: string, thumb = false): Promise<Uint8Array | null> {
  if (!id) return null;
  const key = id + (thumb ? '_t' : '');
  const saved = await idb.get<Cached>('image_' + key).catch(() => undefined);
  if (saved && Date.now() - saved.at < TTL) return saved.data;
  if (pending.has(key)) return pending.get(key)!;
  const load = (async () => {
    try {
      const snap = await getDoc(ref('images/' + key));
      const value = snap.data()?.data;
      const data = value instanceof Bytes ? value.toUint8Array() : null;
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
    const key = id + (thumb ? '_t' : '');
    const cached = urls.get(key);
    if (cached) {
      urls.delete(key);
      urls.set(key, cached);
      return cached.url;
    }
    const data = await bytes(id, thumb);
    if (!data) return null;
    const url = URL.createObjectURL(new Blob([new Uint8Array(data)], { type: 'image/jpeg' }));
    urls.set(key, { url, size: data.byteLength });
    while ([...urls.values()].reduce((s, v) => s + v.size, 0) > MAX_BYTES && urls.size > 1) {
      const first = urls.keys().next().value!;
      URL.revokeObjectURL(urls.get(first)!.url);
      urls.delete(first);
    }
    return url;
  },
  upload: async (ownerId: string, p: ProcessedPhoto) => {
    const target = doc(col('images'));
    const createdAt = Date.now();
    const b = writeBatch(database());
    b.set(target, { data: Bytes.fromUint8Array(p.full), ownerId, createdAt });
    b.set(ref(`images/${target.id}_t`), {
      data: Bytes.fromUint8Array(p.thumb),
      ownerId,
      createdAt,
    });
    await b.commit();
    await Promise.allSettled([cache(target.id, p.full), cache(target.id + '_t', p.thumb)]);
    return target.id;
  },
  delete: async (id: string) => {
    await cacheQueue.catch(() => {});
    const b = writeBatch(database());
    b.delete(ref('images/' + id));
    b.delete(ref('images/' + id + '_t'));
    await b.commit();
    for (const key of [id, id + '_t']) {
      const value = urls.get(key);
      if (value) URL.revokeObjectURL(value.url);
      urls.delete(key);
      await idb.remove('image_' + key);
    }
    const index = (await idb.get<{ key: string; size: number; at: number }[]>('image_index')) ?? [];
    await idb.put(
      'image_index',
      index.filter((x) => x.key !== id && x.key !== id + '_t'),
    );
  },
  clearCache: async () => {
    await cacheQueue.catch(() => {});
    for (const value of urls.values()) URL.revokeObjectURL(value.url);
    urls.clear();
    for (const entry of (await idb.get<{ key: string }[]>('image_index')) ?? [])
      await idb.remove('image_' + entry.key);
    await idb.remove('image_index');
  },
};
