import type { Post } from './model';
const DAY = 86400000;
export const PostStaleness = {
  DAY,
  activity: (p: Post) => Math.max(p.lastActivityAt, p.updatedAt, p.createdAt),
  searchPost: (p: Post) => ['lost', 'found'].includes(p.category) && p.status === 'open',
  needsReminder: (p: Post, now: number) =>
    PostStaleness.searchPost(p) &&
    PostStaleness.activity(p) > 0 &&
    now - PostStaleness.activity(p) >= 14 * DAY,
  hiddenByDefault: (p: Post, now: number) =>
    p.status === 'open' &&
    PostStaleness.activity(p) > 0 &&
    now - PostStaleness.activity(p) >= 90 * DAY,
  reminderId: (p: Post, now: number) => `stale_${p.id}_${Math.floor(now / (14 * DAY))}`,
};
