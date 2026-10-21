import {
  getDoc,
  setDoc,
  onSnapshot,
  serverTimestamp,
  Bytes,
  deleteDoc,
  runTransaction,
  type DocumentData,
} from 'firebase/firestore';
import { auth, database } from '../firebase';
import { ref } from './Firestore';
import { compress, centreCrop, scaleToMax } from '../util/images';
import { parseClassification } from '../ai/PhotoCheck';
export interface AiTransport {
  uid: () => string | null;
  get: (path: string) => Promise<DocumentData | null>;
  set: (path: string, fields: DocumentData) => Promise<void>;
  listen: (path: string, next: (fields: DocumentData) => void, error: () => void) => () => void;
  timestamp: () => unknown;
  delete?: (path: string) => Promise<void>;
  deleteRequest?: (path: string, requestId: string) => Promise<void>;
}
const transport: AiTransport = {
  uid: () => auth?.currentUser?.uid ?? null,
  get: async (path) => (await getDoc(ref(path))).data() ?? null,
  set: (path, fields) => setDoc(ref(path), fields),
  listen: (path, next, error) => onSnapshot(ref(path), (s) => next(s.data() ?? {}), error),
  timestamp: serverTimestamp,
  delete: (path) => deleteDoc(ref(path)),
  deleteRequest: (path, requestId) =>
    runTransaction(database(), async (tx) => {
      const target = ref(path),
        current = await tx.get(target);
      if (current.data()?.requestId === requestId) tx.delete(target);
    }),
};
export interface AiMatch {
  id: string;
  similarity: number;
  verdict: string;
}
export class AiServerClient {
  static ONLINE_WINDOW_MS = 390000;
  static TIMEOUT_MS = 15000;
  constructor(
    private io: AiTransport = transport,
    private timeout = AiServerClient.TIMEOUT_MS,
  ) {}
  async lastSeen() {
    try {
      const data = await this.io.get('config/aiWorker');
      return data?.lastSeen?.toMillis?.() ?? null;
    } catch {
      return null;
    }
  }
  async health() {
    try {
      const d = await this.io.get('config/aiWorker');
      const lastSeen = d?.lastSeen?.toMillis?.();
      return d?.online === true && lastSeen !== undefined && Date.now() - lastSeen <= 390000
        ? {
            model: String(d.model ?? ''),
            provider: String(d.provider ?? ''),
            same: Number(d.same ?? 0.75),
            maybe: Number(d.maybe ?? 0.6),
            lastSeen,
          }
        : null;
    } catch {
      return null;
    }
  }
  async run(fields: DocumentData, signal?: AbortSignal): Promise<DocumentData | null> {
    const uid = this.io.uid();
    if (!uid || signal?.aborted) return null;
    const requestId = crypto.randomUUID(),
      path = `aiJobs/${uid}`;
    try {
      const previous = await this.io.get(path).catch(() => null);
      const seen = previous?.createdAt?.toMillis?.();
      const wait = seen === undefined ? 0 : 1200 - (Date.now() - seen);
      if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
      if (signal?.aborted) return null;
      await this.io.set(path, {
        ...fields,
        uid,
        status: 'pending',
        requestId,
        createdAt: this.io.timestamp(),
      });
      const started = Date.now();
      const cleanup = async () => {
        if (this.io.deleteRequest) {
          await this.io.deleteRequest(path, requestId).catch(() => {});
          return;
        }
        if (!this.io.delete) return;
        // An aborted photo check may have been replaced by a newer request.
        const current = await this.io.get(path).catch(() => null);
        if (current?.requestId === requestId) await this.io.delete(path).catch(() => {});
      };
      return await new Promise((resolve) => {
        let unsubscribe: () => void = () => {};
        let finished = false;
        const done = (value: DocumentData | null, cleanup = false) => {
          if (finished) return;
          finished = true;
          clearTimeout(timer);
          signal?.removeEventListener('abort', aborted);
          unsubscribe();
          if (cleanup) void removeJob();
          resolve(value);
        };
        const removeJob = cleanup;
        const timer = setTimeout(() => done(null, true), this.timeout);
        const aborted = () => {
          done(null);
          setTimeout(() => void removeJob(), Math.max(0, this.timeout - (Date.now() - started)));
        };
        signal?.addEventListener('abort', aborted, { once: true });
        unsubscribe = this.io.listen(
          path,
          (data) => {
            if (data.answeredRequestId === requestId && ['done', 'error'].includes(data.status))
              done(data.status === 'done' ? data : null, true);
          },
          aborted,
        );
        if (finished) unsubscribe();
        if (signal?.aborted) aborted();
      });
    } catch {
      return null;
    }
  }
  async check(
    photos: HTMLCanvasElement[],
    query: HTMLCanvasElement | null,
    candidates: { id: string; image: HTMLCanvasElement }[],
    labels: string[] = [],
    signal?: AbortSignal,
  ) {
    if (signal?.aborted || !(await this.health())) return null;
    try {
      const selected = candidates.slice(0, 8);
      const compared = !!query && selected.length > 0;
      const jpeg = async (image: HTMLCanvasElement, crop = false) =>
        Bytes.fromUint8Array(await compress(scaleToMax(crop ? centreCrop(image) : image, 320), 85));
      const done = await this.run(
        {
          kind: 'check',
          photos: await Promise.all(photos.slice(0, 6).map((image) => jpeg(image))),
          ...(compared ? { query: await jpeg(query!, true) } : {}),
          images: compared ? await Promise.all(selected.map((c) => jpeg(c.image, true))) : [],
          ids: compared ? selected.map((c) => c.id) : [],
          labels: compared ? labels.slice(0, 8).map((label) => label.slice(0, 60)) : [],
        },
        signal,
      );
      if (!done) return null;
      return {
        photos: parseClassification(done.classification, Math.min(photos.length, 6)),
        matches: (Array.isArray(done.results) ? done.results : [])
          .filter((r: AiMatch) => typeof r.id === 'string')
          .map((r: AiMatch) => ({
            id: r.id,
            similarity: typeof r.similarity === 'number' ? r.similarity : 0,
            verdict: typeof r.verdict === 'string' ? r.verdict : 'different',
          })),
        compared,
      };
    } catch {
      return null;
    }
  }
  async compare(
    query: HTMLCanvasElement,
    candidates: { id: string; image: HTMLCanvasElement }[],
    labels: string[] = [],
  ): Promise<AiMatch[] | null> {
    if (!(await this.health())) return null;
    try {
      const selected = candidates.slice(0, 8);
      const jpeg = (image: HTMLCanvasElement) => compress(scaleToMax(centreCrop(image), 320), 85);
      const done = await this.run({
        kind: 'compare',
        query: Bytes.fromUint8Array(await jpeg(query)),
        images: await Promise.all(
          selected.map(async (c) => Bytes.fromUint8Array(await jpeg(c.image))),
        ),
        ids: selected.map((c) => c.id),
        labels: labels.slice(0, 8).map((l) => l.slice(0, 60)),
      });
      return Array.isArray(done?.results)
        ? done.results
            .filter((r: AiMatch) => typeof r.id === 'string')
            .map((r: AiMatch) => ({
              id: r.id,
              similarity: typeof r.similarity === 'number' ? r.similarity : 0,
              verdict: typeof r.verdict === 'string' ? r.verdict : 'different',
            }))
        : null;
    } catch {
      return null;
    }
  }
  async embed(images: HTMLCanvasElement[]): Promise<number[][] | null> {
    try {
      const done = await this.run({
        kind: 'embed',
        images: await Promise.all(
          images
            .slice(0, 16)
            .map(async (i) =>
              Bytes.fromUint8Array(await compress(scaleToMax(centreCrop(i), 320), 85)),
            ),
        ),
        ids: [],
      });
      return Array.isArray(done?.embeddings)
        ? done.embeddings.map((item: { v?: unknown }) =>
            Array.isArray(item.v)
              ? item.v.filter((value: unknown) => typeof value === 'number')
              : [],
          )
        : null;
    } catch {
      return null;
    }
  }
}
export const AiServer = new AiServerClient();
