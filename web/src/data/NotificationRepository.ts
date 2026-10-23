import {
  doc,
  orderBy,
  limit,
  where,
  getDocsFromServer,
  runTransaction,
  updateDoc,
  deleteDoc,
  writeBatch,
} from 'firebase/firestore';
import { auth, database } from '../firebase';
import { ref, col, listQuery, deleteDocuments, list } from './Firestore';
import { AppNotificationDefaults, AppNotificationFromDoc, AppNotificationToDoc } from './model';
export const notificationId = {
  message: (id: string) => `msg_${id}`,
  lost: (id: string) => `lost_${id}`,
  match: (id: string) => `match_${id}`,
  update: (id: string) => `upd_${id}`,
};
const inbox = (uid: string) => `users/${uid}/notifications`;
const readingChats = new Map<string, Promise<void>>();
export const NotificationRepository = {
  readChat: async (uid: string, chatId: string) => {
    const key = uid + '/' + chatId;
    if (readingChats.has(key)) return readingChats.get(key)!;
    const task = (async () => {
      while (true) {
        const page = await getDocsFromServer(
          listQuery(
            inbox(uid),
            where('route', '==', `chat/${chatId}`),
            where('read', '==', false),
            limit(100),
          ),
        );
        if (page.empty) return;
        const batch = writeBatch(database());
        page.docs.forEach((d) => batch.update(d.ref, { read: true }));
        await batch.commit();
        if (page.size < 100) return;
      }
    })();
    readingChats.set(key, task);
    try {
      await task;
    } finally {
      readingChats.delete(key);
    }
  },
  notifications: (uid: string) => listQuery(inbox(uid), orderBy('createdAt', 'desc'), limit(30)),
  latest: (uid: string) => list(NotificationRepository.notifications(uid), AppNotificationFromDoc),
  markRead: (uid: string, id: string) => updateDoc(ref(`${inbox(uid)}/${id}`), { read: true }),
  markAllRead: async (uid: string, ids: string[]) => {
    for (let i = 0; i < ids.length; i += 400) {
      const b = writeBatch(database());
      ids.slice(i, i + 400).forEach((id) => b.update(ref(`${inbox(uid)}/${id}`), { read: true }));
      await b.commit();
    }
  },
  dismiss: async (uid: string, id: string) => {
    try {
      await deleteDoc(ref(`${inbox(uid)}/${id}`));
    } catch {
      /* Delivery must not undo a completed action. */
    }
  },
  delete: (uid: string, id: string) => deleteDoc(ref(`${inbox(uid)}/${id}`)),
  clearAll: async (uid: string) =>
    deleteDocuments(
      (await list(listQuery(inbox(uid)), AppNotificationFromDoc)).map(
        (n) => `${inbox(uid)}/${n.id}`,
      ),
    ),
  send: async (
    recipients: string[],
    type: string,
    params: Record<string, string>,
    route = '',
    dedupeKey?: string,
    sourceId = '',
  ) => {
    const fromUid = auth?.currentUser?.uid;
    if (!fromUid) return;
    const targets = [...new Set(recipients.filter(Boolean))];
    const value = AppNotificationToDoc({
      ...AppNotificationDefaults,
      type,
      params,
      route,
      fromUid,
      sourceId:
        sourceId ||
        params.matchId ||
        (route.includes('/') ? route.slice(route.indexOf('/') + 1) : ''),
      createdAt: Date.now(),
    });
    const results = await Promise.allSettled(
      targets.map(async (uid) => {
        const stable =
          dedupeKey ||
          (value.sourceId
            ? `${type}_${value.sourceId}_${params.status || params.decision || ''}`
            : type === 'welcome'
              ? 'welcome'
              : type === 'achievement'
                ? `achievement_${params.badge}`
                : '');
        const target = stable ? ref(`${inbox(uid)}/${stable}`) : doc(col(inbox(uid)));
        const receipt = ref(`users/${uid}/notificationReceipts/${target.id}`);
        await runTransaction(database(), async (tx) => {
          if (!(await tx.get(receipt)).exists()) {
            tx.set(target, value);
            tx.set(receipt, { fromUid, sourceId: value.sourceId, createdAt: value.createdAt });
          }
        });
      }),
    );
    for (const result of results)
      if (result.status === 'rejected') console.info('Notification delivery failed', result.reason);
  },
};
