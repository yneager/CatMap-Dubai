import { query, limit, type Query, type DocumentData } from 'firebase/firestore';
import { useList } from '../state';
import { useOlder } from './useOlder';
import { useForegroundRefresh } from './ForegroundRefresh';
import { t } from '../i18n';

/** Only the newest window stays live. Older records use snapshot cursors, including timestamp ties. */
export function useHistory<T extends { id: string }>(
  factory: () => Query | null,
  mapper: (id: string, data: DocumentData) => T,
  key: string,
  collection: string,
  size = 50,
) {
  const foreground = useForegroundRefresh(),
    scope = key + '/' + foreground;
  const live = useList(
    () => {
      const base = factory();
      return base ? query(base, limit(size)) : null;
    },
    mapper,
    scope,
  );
  const older = useOlder(() => factory()!, mapper, scope, size);
  // Preserve the query's ordering: the live window precedes older pages.
  const seen = new Set(live?.map((v) => v.id));
  const ordered = live && [
    ...live,
    ...older.items.filter((v) => !seen.has(v.id) && !!seen.add(v.id)),
  ];
  return {
    items: ordered,
    busy: older.busy,
    more: !!live && (live.length >= size || older.items.length > 0) && !older.done,
    load: () => {
      const last = older.items.at(-1) ?? live?.at(-1);
      return last ? older.load(`${collection}/${last.id}`) : Promise.resolve();
    },
    patch: older.patch,
  };
}
export function HistoryMore({
  history,
}: {
  history: { more: boolean; busy: boolean; load: () => Promise<void> };
}) {
  return history.more ? (
    <button className="text-button" disabled={history.busy} onClick={() => void history.load()}>
      {t(history.busy ? 'web_loading' : 'load_more')}
    </button>
  ) : null;
}
