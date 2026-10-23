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
    patch: (id: string, change: Partial<T>) =>
      setState((s) =>
        s.key === key
          ? { ...s, items: s.items.map((v) => (v.id === id ? { ...v, ...change } : v)) }
          : s,
      ),
    items: state.key === key ? state.items : [],
    done: state.key === key && state.done,
    busy: state.key === key && state.busy,
    load: async (oldestPath: string) => {
      if (running.current) return;
      running.current = true;
      setState((s) =>
        s.key === key ? { ...s, busy: true } : { key, items: [], done: false, busy: true },
      );
      try {
        let boundary =
          cursor.current?.key === key ? cursor.current.doc : await getDoc(ref(oldestPath));
        if (!boundary.exists()) {
          // The live boundary can be deleted between rendering and the click. Reconcile its
          // bounded head once, then continue from an actual snapshot (including timestamp ties).
          const head = await getDocs(query(factory(), limit(size)));
          if (!head.docs.length) {
            setState((s) => (s.key === key ? { ...s, done: true } : s));
            return;
          }
          boundary = head.docs.at(-1)!;
        }
        const snap = await getDocs(query(factory(), startAfter(boundary), limit(size)));
        if (snap.docs.length) cursor.current = { key, doc: snap.docs.at(-1)! };
        setState((s) =>
          s.key !== key
            ? s
            : {
                key,
                items: [
                  ...(s.key === key ? s.items : []),
                  ...snap.docs.map((d) => mapper(d.id, d.data())),
                ],
                busy: false,
                done: snap.size < size,
              },
        );
      } catch (e) {
        AppErrors.report(e);
      } finally {
        running.current = false;
        setState((s) => (s.key === key ? { ...s, busy: false } : s));
      }
    },
  };
}
