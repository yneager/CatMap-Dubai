import { getDocs, onSnapshot, where, orderBy, limit } from 'firebase/firestore';
import { auth } from '../firebase';
import { listQuery, AppErrors } from './Firestore';
import { PostFromDoc, CatFromDoc } from './model';
import {
  emptyArea,
  areaPlan,
  toLoad,
  withCell,
  withItems,
  encodeArea,
  decodeArea,
  OVERLAP,
  type Box,
  type Area,
} from './MapArea';
import * as idb from '../util/idb';
let area = emptyArea(),
  owner = '',
  generation = 0;
const subscribers = new Set<() => void>();
let saveQueue = Promise.resolve();
function publish(next: Area) {
  area = next;
  subscribers.forEach((fn) => fn());
  const uid = owner,
    saved = encodeArea(next),
    version = generation;
  saveQueue = saveQueue
    .catch(() => {})
    .then(async () => {
      if (uid && generation === version && auth?.currentUser?.uid === uid)
        await idb.put('map_' + uid, saved);
    });
}
let stop: (() => void) | undefined,
  fallback = false,
  fallbackLoaded = false;
const inFlight = new Set<string>();
let ready = false, queuedViewport: Box | null = null;
const metrics = { areaQueries: 0, areaReads: 0, initialChangeReads: 0, fallbackReads: 0 };
export const MapRepository = {
  metrics: () => ({ ...metrics }),
  resetMetrics: () => {
    metrics.areaQueries =
      metrics.areaReads =
      metrics.initialChangeReads =
      metrics.fallbackReads =
        0;
  },
  flush: () => saveQueue,
  get: () => area,
  subscribe: (fn: () => void) => {
    subscribers.add(fn);
    return () => {
      subscribers.delete(fn);
    };
  },
  clear: async () => {
    generation++;
    ready = false; queuedViewport = null;
    stop?.();
    stop = undefined;
    owner = '';
    area = emptyArea();
    inFlight.clear();
    await saveQueue.catch(() => {});
    subscribers.forEach((fn) => fn());
  },
  start: async (uid: string) => {
    stop?.();
    ready = false;
    const version = ++generation;
    if (owner !== uid) {
      owner = uid;
      area = emptyArea();
      subscribers.forEach((fn) => fn());
    }
    const saved = await idb.get('map_' + uid).catch(() => undefined);
    if (version !== generation || auth?.currentUser?.uid !== uid) return;
    publish(decodeArea(saved));
    fallback = false;
    fallbackLoaded = false;
    const since = area,
      now = Date.now();
    const stops = (['posts', 'cats'] as const).map((key) => {
      let first = true;
      return onSnapshot(
        listQuery(
          key,
          ...(key === 'posts' ? [where('hidden', '==', false)] : []),
          where(
            'updatedAt',
            '>',
            (since[key === 'posts' ? 'postsSince' : 'catsSince'] || now) - OVERLAP,
          ),
          orderBy('updatedAt', 'desc'),
          limit(300),
        ),
        { includeMetadataChanges: true },
        (snap) => {
          if (version !== generation) return;
          const next =
            key === 'posts'
              ? withItems(
                  area,
                  snap.docs.map((d) => PostFromDoc(d.id, d.data())),
                  [],
                )
              : withItems(
                  area,
                  [],
                  snap.docs.map((d) => CatFromDoc(d.id, d.data())),
                );
          if (!snap.metadata.fromCache) {
            if (first) metrics.initialChangeReads += Math.max(1, snap.size);
            next[key === 'posts' ? 'postsSince' : 'catsSince'] = Date.now();
            if (first && snap.size >= 300) next.cells = {};
            first = false;
          }
          publish(next);
        },
        (e) => {
          if (e.code !== 'failed-precondition') AppErrors.report(e);
        },
      );
    });
    stop = () => stops.forEach((fn) => fn());
    ready = true;
    if (queuedViewport) { const box = queuedViewport; queuedViewport = null; void MapRepository.viewport(box); }
  },
  stop: () => {
    generation++;
    ready = false; queuedViewport = null;
    stop?.();
    stop = undefined;
    inFlight.clear();
  },
  viewport: async (box: Box) => {
    if (!ready) { queuedViewport = box; return; }
    if (!owner || auth?.currentUser?.uid !== owner) return;
    const version = generation,
      plan = areaPlan(box);
    const loadFallback = async () => {
      if (fallbackLoaded) return;
      fallbackLoaded = true;
      try {
        const [p, c] = await Promise.all([
          getDocs(
            listQuery(
              'posts',
              where('hidden', '==', false),
              orderBy('createdAt', 'desc'),
              limit(400),
            ),
          ),
          getDocs(listQuery('cats', orderBy('lastSeen', 'desc'), limit(800))),
        ]);
        metrics.fallbackReads += Math.max(1, p.size) + Math.max(1, c.size);
        if (version === generation)
          publish(
            withItems(
              area,
              p.docs.map((d) => PostFromDoc(d.id, d.data())),
              c.docs.map((d) => CatFromDoc(d.id, d.data())),
            ),
          );
      } catch (e) {
        fallbackLoaded = false;
        AppErrors.report(e);
      }
    };
    if (fallback) return loadFallback();
    const todo = toLoad(area, plan.cells).filter((c) => !inFlight.has(c));
    todo.forEach((c) => inFlight.add(c));
    // Three cells at a time, matching Android's semaphore.
    for (let i = 0; i < todo.length; i += 3)
      await Promise.all(
        todo.slice(i, i + 3).map(async (cell) => {
          try {
            metrics.areaQueries += 2;
            const [p, c] = await Promise.all([
              getDocs(
                listQuery(
                  'posts',
                  where('hidden', '==', false),
                  where('geoCells', 'array-contains', cell),
                  orderBy('updatedAt', 'desc'),
                  limit(plan.perCell),
                ),
              ),
              getDocs(
                listQuery(
                  'cats',
                  where('geoCells', 'array-contains', cell),
                  orderBy('updatedAt', 'desc'),
                  limit(plan.perCell),
                ),
              ),
            ]);
            metrics.areaReads += Math.max(1, p.size) + Math.max(1, c.size);
            if (version === generation)
              publish(
                withCell(
                  area,
                  cell,
                  p.docs.map((d) => PostFromDoc(d.id, d.data())),
                  c.docs.map((d) => CatFromDoc(d.id, d.data())),
                  plan.perCell,
                ),
              );
          } catch (e) {
            if (version !== generation) return;
            if (
              ['failed-precondition', 'invalid-argument'].includes((e as { code: string }).code)
            ) {
              fallback = true;
              await loadFallback();
            } else AppErrors.report(e);
          } finally {
          if (version === generation) inFlight.delete(cell);
          }
        }),
      );
  },
};
