import { useEffect, useRef, useState } from 'react';
import type { Query, DocumentData } from 'firebase/firestore';
import { Pager } from './Pager';
import { AppErrors, errorMessage } from './Firestore';
import { t } from '../i18n';
export function useDebounced<T>(value: T, delay = 350) {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return settled;
}
export function usePager<T>(
  factory: () => Query | null,
  mapper: (id: string, d: DocumentData) => T,
  key: string,
  size = 25,
  fallback?: () => Query | null,
) {
  const latest = useRef({ factory, mapper, fallback });
  latest.current = { factory, mapper, fallback };
  const current = useRef<{ key: string; pager: Pager<T>; active: boolean } | null>(null);
  const [state, setState] = useState<{
    key: string;
    items: T[] | undefined;
    hasMore: boolean;
    busy: boolean;
    error: string;
  }>({ key, items: undefined, hasMore: true, busy: false, error: '' });
  const load = async () => {
    const holder = current.current;
    if (!holder?.active || holder.pager.busy || !holder.pager.hasMore) return;
    setState((s) => ({ ...s, busy: true, error: '' }));
    try {
      await holder.pager.next();
    } catch (e) {
      if (holder.active) {
        AppErrors.report(e);
        setState((s) => ({ ...s, busy: false, error: errorMessage(e) }));
      }
      return;
    }
    if (holder.active)
      setState({
        key: holder.key,
        items: [...holder.pager.items],
        hasMore: holder.pager.hasMore,
        busy: false,
        error: '',
      });
  };
  const refresh = async () => {
    if (current.current) current.current.active = false;
    const v = latest.current;
    current.current = {
      key,
      pager: new Pager(v.factory, v.mapper, size, v.fallback),
      active: true,
    };
    setState({ key, items: undefined, hasMore: true, busy: false, error: '' });
    await load();
  };
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  useEffect(() => {
    const v = latest.current,
      holder = { key, pager: new Pager(v.factory, v.mapper, size, v.fallback), active: true };
    current.current = holder;
    void load();
    return () => {
      holder.active = false;
    };
  }, [key, size]);
  useEffect(() => {
    let start: { x: number; y: number } | null = null;
    const down = (event: TouchEvent) => {
      const frame = document.querySelector('.app');
      const target = event.target instanceof Element ? event.target : null;
      if (
        !frame ||
        frame.scrollTop > 0 ||
        target?.closest('input,textarea,button,.community-map') ||
        !event.touches[0]
      )
        return;
      start = { x: event.touches[0].clientX, y: event.touches[0].clientY };
    };
    const up = (event: TouchEvent) => {
      const end = event.changedTouches[0];
      if (start && end && end.clientY - start.y > 90 && Math.abs(end.clientX - start.x) < 45)
        void refreshRef.current();
      start = null;
    };
    document.addEventListener('touchstart', down, { passive: true });
    document.addEventListener('touchend', up, { passive: true });
    return () => {
      document.removeEventListener('touchstart', down);
      document.removeEventListener('touchend', up);
    };
  }, []);
  return {
    ...(state.key === key ? state : { items: undefined, hasMore: true, busy: true, error: '' }),
    load,
    refresh,
  };
}
export function PagingFooter({
  page,
  visibleCount,
}: {
  page: {
    hasMore: boolean;
    busy: boolean;
    error: string;
    load: () => Promise<void>;
    refresh: () => Promise<void>;
    items?: unknown[];
  };
  visibleCount?: number;
}) {
  const ref = useRef<HTMLDivElement>(null),
    latest = useRef(page);
  latest.current = page;
  const count = page.items?.length ?? 0;
  useEffect(() => {
    if (
      visibleCount !== undefined &&
      visibleCount < 12 &&
      count > 0 &&
      count < 150 &&
      latest.current.hasMore &&
      !latest.current.busy &&
      !latest.current.error
    )
      void latest.current.load();
  }, [visibleCount, count, page.hasMore, page.busy, page.error, page.load]);
  useEffect(() => {
    if (!ref.current) return;
    const observer = new IntersectionObserver(
      (entries) => {
        const p = latest.current;
        if (
          entries.some((e) => e.isIntersecting) &&
          p.hasMore &&
          !p.busy &&
          !p.error &&
          (visibleCount === undefined || visibleCount >= 12 || count < 150)
        )
          void p.load();
      },
      { rootMargin: '200px' },
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [count, page.hasMore, page.busy, page.error, visibleCount]);
  return (
    <div ref={ref}>
      {page.error && <p role="alert">{page.error}</p>}
      {page.hasMore && (
        <button className="text-button" disabled={page.busy} onClick={() => void page.load()}>
          {t(page.busy ? 'web_loading' : 'load_more')}
        </button>
      )}
      <button className="text-button" disabled={page.busy} onClick={() => void page.refresh()}>
        {t('refresh')}
      </button>
    </div>
  );
}
