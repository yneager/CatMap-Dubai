import { doc, orderBy, limit, setDoc, updateDoc, deleteDoc, writeBatch } from 'firebase/firestore';
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
export const NotificationRepository = {
  notifications: (uid: string) => listQuery(inbox(uid), orderBy('createdAt', 'desc'), limit(100)),
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
      createdAt: Date.now(),
    });
    if (dedupeKey) {
      await Promise.allSettled(
        targets.map((uid) => setDoc(ref(`${inbox(uid)}/${dedupeKey}`), value)),
      );
      return;
    }
    try {
      for (let i = 0; i < targets.length; i += 400) {
        const b = writeBatch(database());
        targets.slice(i, i + 400).forEach((uid) => b.set(doc(col(inbox(uid))), value));
        await b.commit();
      }
    } catch {
      /* Inbox failures are intentionally non-fatal, matching Android. */
    }
  },
};
