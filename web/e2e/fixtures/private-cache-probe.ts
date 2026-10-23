import { doc, getDocFromCache } from 'firebase/firestore';
import { database } from '../../src/firebase';

// Served by Vite only for local browser tests; it is not an application entry point.
export async function hasPrivateBytes(id: string) {
  try {
    return !!(await getDocFromCache(doc(database(), 'images', id))).data()?.data;
  } catch {
    return false;
  }
}
