import { Geohash } from './Geohash';
import { PostFromDoc, CatFromDoc, type Post, type Cat } from './model';
export interface Box {
  south: number;
  west: number;
  north: number;
  east: number;
}
export interface Area {
  posts: Record<string, Post>;
  cats: Record<string, Cat>;
  seen: Record<string, number>;
  cells: Record<string, { at: number; complete: boolean }>;
  postsSince: number;
  catsSince: number;
}
export const emptyArea = (): Area => ({
  posts: {},
  cats: {},
  seen: {},
  cells: {},
  postsSince: 0,
  catsSince: 0,
});
export const CELL_TTL = 86400000,
  KEEP = 7 * CELL_TTL,
  OVERLAP = 3600000;
export function areaPlan(b: Box) {
  for (const precision of [6, 5, 4]) {
    const cells = Geohash.covering(b.south, b.west, b.north, b.east, precision, 6);
    if (cells.length <= 6)
      return {
        cells,
        perCell: Math.max(40, Math.min(150, Math.floor(300 / Math.max(1, cells.length)))),
      };
  }
  const distance = (c: string) => {
    const [s, w] = Geohash.origin(c),
      [h, width] = Geohash.cellSize(c.length);
    return (
      (s + h / 2 - (b.south + b.north) / 2) ** 2 + (w + width / 2 - (b.west + b.east) / 2) ** 2
    );
  };
  return {
    cells: Geohash.covering(b.south, b.west, b.north, b.east, 4)
      .sort((a, c) => distance(a) - distance(c))
      .slice(0, 6),
    perCell: 50,
  };
}
export function toLoad(a: Area, cells: string[], now = Date.now()) {
  return cells.filter(
    (cell) =>
      !Array.from({ length: cell.length - 3 }, (_, i) => cell.slice(0, i + 4)).some((parent) => {
        const load = a.cells[parent];
        return load && now - load.at < CELL_TTL && (parent === cell || load.complete);
      }),
  );
}
export function withItems(a: Area, posts: Post[], cats: Cat[], now = Date.now()): Area {
  const next = { ...a, posts: { ...a.posts }, cats: { ...a.cats }, seen: { ...a.seen } };
  for (const p of posts) {
    if (!next.posts[p.id] || p.updatedAt >= next.posts[p.id].updatedAt) next.posts[p.id] = p;
    next.seen['p:' + p.id] = now;
  }
  for (const c of cats) {
    if (next.cats[c.id] && c.updatedAt < next.cats[c.id].updatedAt) continue;
    if (c.hidden || c.mergedInto) {
      delete next.cats[c.id];
      delete next.seen['c:' + c.id];
    } else {
      next.cats[c.id] = c;
      next.seen['c:' + c.id] = now;
    }
  }
  return next;
}
export function withCell(
  a: Area,
  cell: string,
  posts: Post[],
  cats: Cat[],
  size: number,
  now = Date.now(),
) {
  const next = { ...a, posts: { ...a.posts }, cats: { ...a.cats }, seen: { ...a.seen } };
  for (const [key, incoming, prefix] of [
    ['posts', posts, 'p:'],
    ['cats', cats, 'c:'],
  ] as const) {
    const ids = new Set(incoming.map((v) => v.id)),
      oldest = Math.min(...incoming.map((v) => v.updatedAt));
    for (const item of Object.values(next[key]))
      if (
        !ids.has(item.id) &&
        item.geoCells.includes(cell) &&
        (incoming.length < size || item.updatedAt >= oldest)
      ) {
        delete next[key][item.id];
        delete next.seen[prefix + item.id];
      }
  }
  return {
    ...withItems(next, posts, cats, now),
    cells: { ...a.cells, [cell]: { at: now, complete: posts.length < size && cats.length < size } },
  };
}
const postKeys = [
  'id',
  'animalType',
  'category',
  'status',
  'name',
  'photoIds',
  'lat',
  'lng',
  'neighborhood',
  'lastSeenAt',
  'reward',
  'adoptionStatus',
  'keywords',
  'createdAt',
  'updatedAt',
  'resolvedAt',
  'lastActivityAt',
  'microchip',
  'geoCells',
  'hidden',
] as const;
const catKeys = [
  'id',
  'name',
  'coatColor',
  'coverPhotoId',
  'lat',
  'lng',
  'neighborhood',
  'healthStatus',
  'updatedAt',
  'geoCells',
] as const;
export function encodeArea(a: Area) {
  return {
    ...a,
    posts: Object.fromEntries(
      Object.entries(a.posts).map(([id, p]) => [
        id,
        Object.fromEntries(
          postKeys.map((k) => [k, k === 'photoIds' ? p.photoIds.slice(0, 1) : p[k]]),
        ),
      ]),
    ),
    cats: Object.fromEntries(
      Object.entries(a.cats).map(([id, c]) => [
        id,
        Object.fromEntries(catKeys.map((k) => [k, c[k]])),
      ]),
    ),
  };
}
export function decodeArea(value: unknown, now = Date.now()): Area {
  if (!value || typeof value !== 'object') return emptyArea();
  const a = value as Area,
    out = emptyArea();
  for (const [key, mapper, prefix] of [
    ['posts', PostFromDoc, 'p:'],
    ['cats', CatFromDoc, 'c:'],
  ] as const) {
    if (!a[key] || typeof a[key] !== 'object') continue;
    for (const [id, data] of Object.entries(a[key])) {
      if (
        typeof a.seen?.[prefix + id] !== 'number' ||
        now - a.seen[prefix + id] >= KEEP ||
        !data ||
        typeof data !== 'object'
      )
        continue;
      Object.assign(out[key], { [id]: mapper(id, data as unknown as Record<string, unknown>) });
      out.seen[prefix + id] = a.seen[prefix + id];
    }
  }
  for (const [key, c] of Object.entries(a.cells ?? {}))
    if (
      /^[0-9bcdefghjkmnpqrstuvwxyz]{4,6}$/.test(key) &&
      typeof c?.at === 'number' &&
      typeof c.complete === 'boolean' &&
      now - c.at < KEEP
    )
      out.cells[key] = c;
  out.postsSince = typeof a.postsSince === 'number' ? a.postsSince : 0;
  out.catsSince = typeof a.catsSince === 'number' ? a.catsSince : 0;
  return out;
}
