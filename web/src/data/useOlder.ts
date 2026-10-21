import { useRef, useState } from 'react';
import {
  getDoc,
  getDocs,
  query,
  startAfter,
  limit,
  type Query,
  type DocumentData,
  type QueryDocumentSnapshot,
} from 'firebase/firestore';
import { ref, AppErrors } from './Firestore';
export function useOlder<T extends { id: string }>(
  factory: () => Query,
  mapper: (id: string, data: DocumentData) => T,
  key: string,
  size: number,
) {
  const [state, setState] = useState<{ key: string; items: T[]; done: boolean; busy: boolean }>({
    key,
    items: [],
    done: false,
    busy: false,
  });
  const cursor = useRef<{ key: string; doc: QueryDocumentSnapshot } | null>(null),
    running = useRef(false);
  return {
    items: state.key === key ? state.items : [],
    done: state.key === key && state.done,
    busy: state.key === key && state.busy,
    load: async (oldestPath: string) => {
      if (running.current) return;
      running.current = true;
      setState((s) => ({ ...s, key, busy: true }));
      try {
        const boundary =
          cursor.current?.key === key ? cursor.current.doc : await getDoc(ref(oldestPath));
        if (!boundary.exists()) return;
        const snap = await getDocs(query(factory(), startAfter(boundary), limit(size)));
        if (snap.docs.length) cursor.current = { key, doc: snap.docs.at(-1)! };
        setState((s) => ({
          key,
          items: [
            ...(s.key === key ? s.items : []),
            ...snap.docs.map((d) => mapper(d.id, d.data())),
          ],
          busy: false,
          done: snap.size < size,
        }));
      } catch (e) {
        AppErrors.report(e);
      } finally {
        running.current = false;
        setState((s) => ({ ...s, busy: false }));
      }
    },
  };
}
