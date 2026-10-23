import { auth } from '../firebase';
import { PostRepository, type PostAvailability } from './PostRepository';
interface Entry {
  at: number;
  promise: Promise<PostAvailability>;
  signals: Set<AbortSignal>;
}
const entries = new Map<string, Entry>();
let running = 0,
  owner = '';
const waiting: (() => void)[] = [];
async function slot<T>(task: () => Promise<T>): Promise<T> {
  if (running >= 4) await new Promise<void>((resolve) => waiting.push(resolve));
  else running++;
  try {
    return await task();
  } finally {
    const next = waiting.shift();
    if (next) next();
    else running--;
  }
}
/** UID/foreground scoped and short-lived; queued requests retire when every consumer has left. */
export function cachedAvailability(
  uid: string,
  id: string,
  revision: number,
  signal: AbortSignal,
): Promise<PostAvailability> {
  if (owner !== uid) {
    entries.clear();
    owner = uid;
  }
  const key = `${uid}/${revision}/${id}`;
  const old = entries.get(key);
  if (old && Date.now() - old.at < 30000) {
    old.signals.add(signal);
    return old.promise;
  }
  const signals = new Set([signal]);
  const promise = slot(async () => {
    await Promise.resolve(); // Let the entry be installed before a queued cancellation retires it.
    if (auth?.currentUser?.uid !== uid || [...signals].every((s) => s.aborted)) {
      if (entries.get(key)?.promise === promise) entries.delete(key);
      return 'there';
    }
    return PostRepository.availability(id);
  });
  entries.set(key, { at: Date.now(), promise, signals });
  while (entries.size > 256) entries.delete(entries.keys().next().value!);
  return promise;
}
