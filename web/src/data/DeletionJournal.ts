import { runTransaction } from 'firebase/firestore';
import { auth, database } from '../firebase';
import { ref } from './Firestore';
import type { Post } from './model';

/** Durable IDs survive a closed app or failed cleanup; the PC resumes these jobs on Spark. */
export async function journalPostDeletion(post: Post) {
  const target = ref(`postDeletions/${post.id}`);
  await runTransaction(database(), async (tx) => {
    if ((await tx.get(target)).exists()) return;
    const source = await tx.get(ref(`posts/${post.id}`));
    if (!source.exists()) return;
    tx.set(target, {
      ownerId: source.data().ownerId,
      actorId: auth?.currentUser?.uid ?? '',
      photoIds: source.data().photoIds ?? [],
      videoPath: source.data().videoPath ?? '',
      createdAt: Date.now(),
      status: 'pending',
    });
    tx.update(source.ref, { hidden: true });
  });
}
