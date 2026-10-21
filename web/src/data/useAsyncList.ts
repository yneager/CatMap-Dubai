import { useEffect, useRef, useState } from 'react';
import { AppErrors } from './Firestore';
export function useAsyncList<T>(factory: () => Promise<T[]>, key: string) {
  const latest = useRef(factory);
  latest.current = factory;
  const [state, setState] = useState<{ key: string; items: T[] }>({ key: '', items: [] });
  useEffect(() => {
    let active = true;
    void latest
      .current()
      .then((items) => {
        if (active) setState({ key, items });
      })
      .catch((e) => {
        if (active) {
          AppErrors.report(e);
          setState({ key, items: [] });
        }
      });
    return () => {
      active = false;
    };
  }, [key]);
  return state.key === key ? state.items : undefined;
}
