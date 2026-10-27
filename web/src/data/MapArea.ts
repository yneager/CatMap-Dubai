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
}
export const emptyArea = (): Area => ({
  posts: {},
  cats: {},
  seen: {},
});
/** A saved pin not confirmed by the server for this long is dropped. */
export const KEEP = 7 * 86400000;
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
  return out;
}
