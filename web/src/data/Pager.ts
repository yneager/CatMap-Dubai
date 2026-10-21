import {
  getDocs,
  query,
  startAfter,
  limit,
  type Query,
  type QueryDocumentSnapshot,
  type DocumentData,
} from 'firebase/firestore';
export const PagerMetrics = {
  queries: 0,
  reads: 0,
  reset() {
    this.queries = this.reads = 0;
  },
};
export class Pager<T> {
  items: T[] = [];
  hasMore = true;
  busy = false;
  private cursor?: QueryDocumentSnapshot;
  constructor(
    private source: () => Query | null,
    private mapper: (id: string, data: DocumentData) => T,
    readonly size = 25,
    private fallback?: () => Query | null,
  ) {}
  async next() {
    if (this.busy || !this.hasMore) return;
    let base = this.source();
    if (!base) {
      this.hasMore = false;
      return;
    }
    this.busy = true;
    try {
      const page = async () => {
        PagerMetrics.queries++;
        const result = await getDocs(
          query(base!, ...(this.cursor ? [startAfter(this.cursor)] : []), limit(this.size)),
        );
        if (!result.metadata?.fromCache) PagerMetrics.reads += Math.max(1, result.size);
        return result;
      };
      let snap;
      try {
        snap = await page();
      } catch (e) {
        if ((e as { code?: string }).code !== 'failed-precondition' || !this.fallback) throw e;
        const fallback = this.fallback();
        if (!fallback) throw e;
        base = fallback;
        this.source = () => fallback;
        this.cursor = undefined;
        this.items = [];
        snap = await page();
      }
      this.items.push(...snap.docs.map((d) => this.mapper(d.id, d.data())));
      this.cursor = snap.docs.at(-1) ?? this.cursor;
      this.hasMore = snap.size === this.size;
    } finally {
      this.busy = false;
    }
  }
}
