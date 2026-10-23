import {
  collectionGroup,
  query,
  where,
  getDocs,
  runTransaction,
  updateDoc,
  writeBatch,
  deleteDoc,
} from 'firebase/firestore';
import { VideoRepository } from './VideoRepository';
import { database } from '../firebase';
import { ref, listQuery, deleteDocuments } from './Firestore';
async function matchingPaths(path: string, field: string, uid: string) {
  return (await getDocs(listQuery(path, where(field, '==', uid)))).docs.map((d) => d.ref.path);
}
export const CommunityAccountCleanup = {
  clean: async (uid: string) => {
    const updates = await getDocs(
      query(collectionGroup(database(), 'updates'), where('byUid', '==', uid)),
    );
    for (const d of updates.docs) {
      if (!/^posts\/[^/]+\/updates\/[^/]+$/.test(d.ref.path)) continue;
      await runTransaction(database(), async (tx) => {
        const source = await tx.get(d.ref),
          parent = ref(d.ref.path.split('/').slice(0, 2).join('/'));
        const p = await tx.get(parent);
        if (source.exists()) {
          tx.delete(d.ref);
          if (p.exists())
            tx.update(parent, { updatesCount: Math.max(0, (p.data().updatesCount ?? 0) - 1) });
        }
      });
    }
    const posts = await getDocs(listQuery('posts', where('ownerId', '==', uid)));
    for (const post of posts.docs) {
      const path = post.ref.path;
      await deleteDocuments(
        (await getDocs(listQuery(path + '/updates'))).docs.map((d) => d.ref.path),
      );
      await deleteDocuments([path]);
    }
    // Matches may be deleted only after their posts are gone.
    await getDocs(listQuery('matches', where('members', 'array-contains', uid)))
      .then((snap) => Promise.allSettled(snap.docs.map((d) => deleteDoc(d.ref))))
      .catch((error) => console.info('Account match cleanup failed', error));
    await Promise.allSettled(posts.docs.map(async (post) => {
      await deleteDocuments([`postEmbeddings/${post.id}`]).catch((error) => console.info('Embedding cleanup failed', error));
      await VideoRepository.delete(post.data().videoPath ?? '');
    }));
    for (const field of ['requesterId', 'ownerId'])
      await deleteDocuments(await matchingPaths('adoptionRequests', field, uid));
    for (const field of ['ownerId', 'sitterId'])
      for (const path of await matchingPaths('bookings', field, uid))
        await updateDoc(
          ref(path),
          field === 'ownerId' ? { ownerName: '', notes: '' } : { sitterName: '', sitterNote: '' },
        );
    for (const path of await matchingPaths('reviews', 'authorId', uid))
      await updateDoc(ref(path), { authorName: '', text: '' });
    const chats = await getDocs(listQuery('chats', where('members', 'array-contains', uid)));
    for (const chat of chats.docs) {
      await deleteDocuments(await matchingPaths(chat.ref.path + '/messages', 'senderId', uid));
      const data: Record<string, unknown> = {
        [`names.${uid}`]: '',
        [`photos.${uid}`]: '',
        [`unread.${uid}`]: 0,
      };
      if (chat.data().lastSender === uid) Object.assign(data, { lastText: '', lastSender: '' });
      await updateDoc(chat.ref, data);
    }
    await deleteDocuments([`sitters/${uid}`, `areaFollows/${uid}`]);
    for (const path of ['favorites', 'blocked', 'private']) {
      const docs = await getDocs(listQuery(`users/${uid}/${path}`));
      for (let i = 0; i < docs.docs.length; i += 200) {
        const b = writeBatch(database());
        docs.docs.slice(i, i + 200).forEach((d) => b.delete(d.ref));
        await b.commit();
      }
    }
  },
};
