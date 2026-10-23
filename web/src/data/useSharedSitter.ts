import { useCallback, useMemo, useSyncExternalStore } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { auth } from '../firebase';
import { ref, AppErrors } from './Firestore';
import { SitterProfileFromDoc, type SitterProfile } from './model';
interface Entry {
  value: SitterProfile | null | undefined;
  listeners: Set<() => void>;
  stop?: () => void;
  owner: string;
  id: string;
}
const entries = new Map<string, Entry>();
/** Share application work across booking rows with the same sitter; entries retire with their last row. */
export function useSharedSitter(id: string) {
  const owner = auth?.currentUser?.uid ?? '',
    key = owner + '/' + id;
  const entry = useMemo(() => {
    let found = entries.get(key);
    if (!found) {
      found = { value: undefined, listeners: new Set(), owner, id };
      entries.set(key, found);
    }
    return found;
  }, [key, owner, id]);
  const subscribe = useCallback(
    (listener: () => void) => {
      // React may unsubscribe/re-subscribe the same mounted row during StrictMode checks.
      if (!entries.has(key)) entries.set(key, entry);
      entry.listeners.add(listener);
      if (!entry.stop && owner && id)
        entry.stop = onSnapshot(
          ref(`sitters/${id}`),
          (snap) => {
            if (auth?.currentUser?.uid !== owner) return;
            entry.value = snap.exists() ? SitterProfileFromDoc(snap.id, snap.data()) : null;
            entry.listeners.forEach((fn) => fn());
          },
          AppErrors.report,
        );
      return () => {
        entry.listeners.delete(listener);
        if (!entry.listeners.size) {
          entry.stop?.();
          entry.stop = undefined;
          if (entries.get(key) === entry) entries.delete(key);
        }
      };
    },
    [entry, owner, id, key],
  );
  return useSyncExternalStore(subscribe, () => entry.value);
}
