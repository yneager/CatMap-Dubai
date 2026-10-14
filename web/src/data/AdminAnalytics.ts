import { where } from 'firebase/firestore';
import { count } from './AdminRepository';
export interface AdminAnalytics {
  weeks: { start: number; posts: number; members: number }[];
  searchPosts: number;
  reunited: number;
  reunionRate: number;
}
let cached: { at: number; value: AdminAnalytics } | undefined;
export const AdminAnalyticsRepository = {
  load: async (): Promise<AdminAnalytics> => {
    if (cached && Date.now() - cached.at < 300000) return cached.value;
    const now = new Date(Date.now() + 4 * 3600000);
    const day = (now.getUTCDay() + 6) % 7;
    const monday =
      Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - day) - 4 * 3600000;
    const starts = Array.from({ length: 8 }, (_, i) => monday - (7 - i) * 7 * 86400000);
    const weeks = await Promise.all(
      starts.map(async (start) => ({
        start,
        posts: await count(
          'posts',
          where('createdAt', '>=', start),
          where('createdAt', '<', start + 7 * 86400000),
        ),
        members: await count(
          'users',
          where('createdAt', '>=', start),
          where('createdAt', '<', start + 7 * 86400000),
        ),
      })),
    );
    const searchPosts = await count(
      'posts',
      where('category', 'in', ['lost', 'found']),
      where('createdAt', '>=', starts[0]),
    );
    const reunited = await count(
      'posts',
      where('category', 'in', ['lost', 'found']),
      where('createdAt', '>=', starts[0]),
      where('status', '==', 'reunited'),
    );
    const value = {
      weeks,
      searchPosts,
      reunited,
      reunionRate: searchPosts ? (reunited * 100) / searchPosts : 0,
    };
    cached = { at: Date.now(), value };
    return value;
  },
};
