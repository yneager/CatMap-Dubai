import { orderBy, limit } from 'firebase/firestore';
import { listQuery } from './Firestore';
import { setPrivateSlot } from './PrivateSlots';
export const FavoritesRepository = {
  favorites: (uid: string) =>
    listQuery(`users/${uid}/favorites`, orderBy('savedAt', 'desc'), limit(500)),
  set: (uid: string, postId: string, saved: boolean) =>
    setPrivateSlot(uid, 'favorites', postId, saved ? { postId, savedAt: Date.now() } : null),
};
