import {
  getDoc,
  getDocs,
  query,
  where,
  collectionGroup,
  orderBy,
  limit,
  startAfter,
  Timestamp,
  Bytes,
  type Query,
  type DocumentData,
} from 'firebase/firestore';
import { database } from '../firebase';
import { ref, listQuery } from './Firestore';
import { download } from '../util/share';
export async function* pages(q: Query, size = 10) {
  let cursor: import('firebase/firestore').QueryDocumentSnapshot | undefined;
  while (true) {
    const snapshots = await getDocs(
      cursor ? query(q, startAfter(cursor), limit(size)) : query(q, limit(size)),
    );
    if (!snapshots.docs.length) return;
    yield snapshots.docs;
    if (snapshots.docs.length < size) return;
    cursor = snapshots.docs.at(-1);
  }
}
export function cleanValue(value: unknown): unknown {
  if (value instanceof Timestamp) return value.toMillis();
  if (value instanceof Bytes) return null;
  if (Array.isArray(value)) return value.map(cleanValue);
  if (value && typeof value === 'object')
    return Object.fromEntries(
      Object.entries(value)
        .filter(([key]) => !['tokens', 'password'].includes(key))
        .map(([key, value]) => [key, cleanValue(value)]),
    );
  if (typeof value === 'number' && !Number.isFinite(value)) return null;
  return value;
}
const snapshot = (id: string, data: DocumentData) => ({
  ...(cleanValue(
    Object.fromEntries(
      Object.entries(data).filter(([key]) => !['data', 'tokens', 'password'].includes(key)),
    ),
  ) as object),
  id,
});
export const UserDataExportRepository = {
  build: async (uid: string) => {
    const records: Record<string, unknown[]> = {};
    for (const [key, path, always] of [
      ['profile', `users/${uid}`, true],
      ['account', `users/${uid}/private/account`, true],
      ['areaFollowPrivate', `users/${uid}/private/areaFollow`, false],
      ['sitters', `sitters/${uid}`, false],
      ['areaFollows', `areaFollows/${uid}`, false],
      ['volunteerApplications', `volunteerApplications/${uid}`, false],
    ] as [string, string, boolean][]) {
      const d = await getDoc(ref(path));
      records[key] = d.exists() || always ? [snapshot(d.id, d.data() ?? {})] : [];
    }
    const fields: Record<string, string[]> = {
      posts: ['ownerId'],
      sightings: ['userId'],
      rescues: ['reporterId'],
      checkins: ['userId'],
      privateLocations: ['ownerId'],
      cats: ['createdBy'],
      maintenance: ['userId'],
      contentReports: ['reporterId'],
      mergeRequests: ['requestedBy'],
      images: ['ownerId'],
      medicalRecords: ['byUid'],
      adoptionRequests: ['requesterId', 'ownerId'],
      bookings: ['ownerId', 'sitterId'],
      reviews: ['authorId', 'sitterId'],
    };
    for (const [name, owners] of Object.entries(fields)) {
      const seen = new Set<string>();
      records[name] = [];
      for (const owner of owners)
        for await (const page of pages(listQuery(name, where(owner, '==', uid))))
          for (const d of page)
            if (!seen.has(d.id)) {
              records[name].push(snapshot(d.id, d.data()));
              seen.add(d.id);
            }
    }
    for (const name of ['notifications', 'notificationReceipts', 'favorites', 'blocked']) {
      records[name] = [];
      for await (const page of pages(listQuery(`users/${uid}/${name}`)))
        records[name].push(...page.map((d) => snapshot(d.id, d.data())));
    }
    records.chats = [];
    records.messages = [];
    for await (const page of pages(listQuery('chats', where('members', 'array-contains', uid))))
      for (const d of page) {
        records.chats.push(snapshot(d.id, d.data()));
        for await (const messages of pages(
          listQuery(`chats/${d.id}/messages`, orderBy('createdAt')),
        ))
          records.messages.push(
            ...messages.map((m) => ({ ...snapshot(m.id, m.data()), chatId: d.id })),
          );
      }
    records.postUpdates = [];
    for await (const page of pages(
      query(collectionGroup(database(), 'updates'), where('byUid', '==', uid)),
    ))
      for (const d of page)
        if (/^posts\/[^/]+\/updates\/[^/]+$/.test(d.ref.path))
          records.postUpdates.push(snapshot(d.id, d.data()));
    return { schemaVersion: 1, userId: uid, exportedAt: Date.now(), records };
  },
  download: async (uid: string) => {
    const payload = await UserDataExportRepository.build(uid);
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(uid));
    const key = [...new Uint8Array(digest).slice(0, 8)]
      .map((v) => v.toString(16).padStart(2, '0'))
      .join('');
    download(
      new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }),
      `catmap-my-data-${key}.json`,
    );
  },
  clear: async (_uid: string) => {},
};
