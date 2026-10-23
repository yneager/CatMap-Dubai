import { onSnapshot, where, orderBy, limit } from 'firebase/firestore';
import { auth } from '../firebase';
import { listQuery, AppErrors } from './Firestore';
import { PostFromDoc, CatFromDoc } from './model';
import {
  emptyArea,
  areaPlan,
  withItems,
  encodeArea,
  decodeArea,
  type Box,
  type Area,
} from './MapArea';
import { replaceMapWindow } from './MapWindow';
import * as idb from '../util/idb';

let area = emptyArea(),
  owner = '',
  generation = 0;
const subscribers = new Set<() => void>();
const windows = new Map<string, { size: number; stop: () => void }>();
let saveQueue = Promise.resolve();
let saveTimer: ReturnType<typeof setTimeout> | undefined;
let viewportTimer: ReturnType<typeof setTimeout> | undefined;
let queuedViewport: Box | null = null,
  ready = false;
const metrics = {
  areaQueries: 0,
  areaReads: 0,
  initialChangeReads: 0,
  fallbackReads: 0,
  changeDeliveries: 0,
  activeListeners: 0,
  cacheWrites: 0,
};

function persist() {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = undefined;
  const uid = owner,
    version = generation;
  saveQueue = saveQueue
    .catch(() => {})
    .then(async () => {
      if (uid && generation === version && auth?.currentUser?.uid === uid) {
        await idb.put('map_' + uid, encodeArea(area));
        metrics.cacheWrites++;
      }
    });
  return saveQueue;
}
function publish(next: Area) {
  area = next;
  subscribers.forEach((fn) => fn());
  if (!saveTimer)
    saveTimer = setTimeout(() => {
      void persist();
    }, 1000);
}
function closeWindows() {
  windows.forEach((v) => v.stop());
  windows.clear();
  metrics.activeListeners = 0;
  if (viewportTimer) clearTimeout(viewportTimer);
  viewportTimer = undefined;
}
function listenViewport(box: Box) {
  if (!ready || !owner || auth?.currentUser?.uid !== owner) return;
  const plan = areaPlan(box),
    version = generation;
  for (const [cell, old] of windows)
    if (!plan.cells.includes(cell) || old.size !== plan.perCell) {
      old.stop();
      windows.delete(cell);
    }
  for (const cell of plan.cells) {
    if (windows.has(cell)) continue;
    let active = true;
    const stops = (['posts', 'cats'] as const).map((key) => {
      let first = true;
      metrics.areaQueries++;
      return onSnapshot(
        listQuery(
          key,
          where('hidden', '==', false),
          ...(key === 'cats' ? [where('mergedInto', '==', '')] : []),
          where('geoCells', 'array-contains', cell),
          orderBy('updatedAt', 'desc'),
          limit(plan.perCell),
        ),
        { includeMetadataChanges: true },
        (snap) => {
          if (!active || version !== generation) return;
          const values = snap.docs.map((d) =>
            key === 'posts' ? PostFromDoc(d.id, d.data()) : CatFromDoc(d.id, d.data()),
          );
          if (!snap.metadata.fromCache) {
            const changes = snap.docChanges().filter((c) => c.type !== 'removed').length;
            metrics.areaReads += first ? Math.max(1, snap.size) : changes;
            metrics.changeDeliveries += snap.docChanges().length;
            if (first) metrics.initialChangeReads += Math.max(1, snap.size);
            first = false;
            publish(replaceMapWindow(area, cell, key, values));
          } else {
            publish(
              key === 'posts'
                ? withItems(area, values as ReturnType<typeof PostFromDoc>[], [])
                : withItems(area, [], values as ReturnType<typeof CatFromDoc>[]),
            );
          }
        },
        (error) => {
          if (active && version === generation) AppErrors.report(error);
        },
      );
    });
    windows.set(cell, {
      size: plan.perCell,
      stop: () => {
        active = false;
        stops.forEach((fn) => fn());
      },
    });
  }
  metrics.activeListeners = windows.size * 2;
}

export const MapRepository = {
  metrics: () => ({ ...metrics }),
  resetMetrics: () => {
    for (const k of Object.keys(metrics) as (keyof typeof metrics)[]) metrics[k] = 0;
    metrics.activeListeners = windows.size * 2;
  },
  flush: persist,
  get: () => area,
  subscribe: (fn: () => void) => {
    subscribers.add(fn);
    return () => {
      subscribers.delete(fn);
    };
  },
  clear: async () => {
    generation++;
    ready = false;
    queuedViewport = null;
    closeWindows();
    if (saveTimer) clearTimeout(saveTimer);
    saveTimer = undefined;
    owner = '';
    area = emptyArea();
    await saveQueue.catch(() => {});
    subscribers.forEach((fn) => fn());
  },
  start: async (uid: string) => {
    closeWindows();
    ready = false;
    await saveQueue.catch(() => {});
    const version = ++generation;
    if (owner !== uid) {
      owner = uid;
      area = emptyArea();
      subscribers.forEach((fn) => fn());
    }
    const saved = await idb.get('map_' + uid).catch(() => undefined);
    if (version !== generation || auth?.currentUser?.uid !== uid) return;
    publish(decodeArea(saved));
    ready = true;
    if (queuedViewport) listenViewport(queuedViewport);
  },
  stop: () => {
    ready = false;
    queuedViewport = null;
    closeWindows();
    void persist();
  },
  viewport: async (box: Box) => {
    queuedViewport = box;
    if (viewportTimer) clearTimeout(viewportTimer);
    viewportTimer = setTimeout(() => {
      viewportTimer = undefined;
      listenViewport(box);
    }, 400);
  },
};
