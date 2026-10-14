import { getCountFromServer, where, limit, updateDoc } from 'firebase/firestore';
import { ref, listQuery } from './Firestore';
import { enums } from './model/Enums';
import type { UserProfile } from './model';
import { ModerationRepository } from './ModerationRepository';
import { NotificationRepository } from './NotificationRepository';
export const count = async (
  path: string,
  ...clauses: Parameters<typeof listQuery> extends [string, ...infer R] ? R : never
) => (await getCountFromServer(listQuery(path, ...clauses))).data().count;
export const AdminRepository = {
  users: () => listQuery('users', limit(1000)),
  stats: async () => {
    const queries: Record<string, Promise<number>> = {
      users: count('users'),
      posts: count('posts'),
      openLost: count('posts', where('category', '==', 'lost'), where('status', '==', 'open')),
      reunited: count('posts', where('status', '==', 'reunited')),
      adoptionAvailable: count(
        'posts',
        where('category', '==', 'adoption'),
        where('adoptionStatus', '==', 'available'),
      ),
      adopted: count('posts', where('adoptionStatus', '==', 'adopted')),
      sitters: count('sitters'),
      bookings: count('bookings'),
      cats: count('cats'),
      openReports: count('contentReports', where('status', '==', 'open')),
      hiddenPosts: count('posts', where('hidden', '==', true)),
      suspendedUsers: count('users', where('suspended', '==', true)),
    };
    for (const c of enums.PostCategory)
      queries[c.key] = count('posts', where('category', '==', c.key));
    return Object.fromEntries(
      await Promise.all(
        Object.entries(queries).map(async ([key, p]) => [key, await p.catch(() => 0)]),
      ),
    );
  },
  setSuspended: async (user: UserProfile, suspended: boolean, admin: UserProfile, note = '') => {
    await updateDoc(ref(`users/${user.uid}`), { suspended });
    await ModerationRepository.log(
      admin,
      suspended ? 'user_suspended' : 'user_restored',
      'user',
      user.uid,
      user.username,
      note,
    );
    await NotificationRepository.send([user.uid], 'moderation', {
      target: 'account',
      label: user.username,
      decision: suspended ? 'suspended' : 'restored',
    });
  },
};
