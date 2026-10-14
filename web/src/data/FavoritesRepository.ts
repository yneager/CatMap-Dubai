import { orderBy, limit, setDoc, deleteDoc } from 'firebase/firestore';
import { ref, listQuery } from './Firestore';
export const FavoritesRepository = {
  favorites: (uid: string) =>
    listQuery(`users/${uid}/favorites`, orderBy('savedAt', 'desc'), limit(500)),
  set: (uid: string, postId: string, saved: boolean) =>
    saved
      ? setDoc(ref(`users/${uid}/favorites/${postId}`), { postId, savedAt: Date.now() })
      : deleteDoc(ref(`users/${uid}/favorites/${postId}`)),
};
