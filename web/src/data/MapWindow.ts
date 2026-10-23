import { withItems, type Area } from './MapArea';
import type { Post, Cat } from './model';

/** A bounded live query is a display window, never proof that an evicted document was deleted. */
export function replaceMapWindow(
  a: Area,
  cell: string,
  key: 'posts' | 'cats',
  values: (Post | Cat)[],
): Area {
  const ids = new Set(values.map((v) => v.id));
  const next = {
    ...a,
    [key]: Object.fromEntries(
      Object.entries(a[key]).filter(([id, v]) => !v.geoCells.includes(cell) || ids.has(id)),
    ),
    seen: { ...a.seen },
  };
  for (const id of Object.keys(a[key]))
    if (!(id in next[key])) delete next.seen[(key === 'posts' ? 'p:' : 'c:') + id];
  return key === 'posts'
    ? withItems(next, values as Post[], [])
    : withItems(next, [], values as Cat[]);
}
