import { runTransaction } from 'firebase/firestore';
import { database } from '../firebase';
import { ref, AppException } from './Firestore';

/** Atomic, rules-enforced per-account capacity. Existing installations are backfilled before rollout. */
export async function setPrivateSlot(
  uid: string,
  bucket: 'favorites' | 'blocked',
  id: string,
  value: Record<string, unknown> | null,
) {
  await runTransaction(database(), async (tx) => {
    const target = ref(`users/${uid}/${bucket}/${id}`),
      limits = ref(`userLimits/${uid}`);
    const current = await tx.get(target),
      quota = await tx.get(limits);
    if (current.exists() === (value !== null)) return;
    if (!quota.exists()) throw new AppException('err_generic');
    const count = Number(quota.data()[bucket] ?? 0) + (value ? 1 : -1);
    if (count < 0 || count > 500) throw new AppException('err_too_many_requests');
    tx.update(limits, { [bucket]: count, [bucket + 'Source']: id });
    if (value) tx.set(target, value);
    else tx.delete(target);
  });
}
