import {
  arrayUnion,
  arrayRemove,
  increment,
  serverTimestamp,
  writeBatch,
  updateDoc,
  where,
  type WriteBatch,
} from 'firebase/firestore';
import { database } from '../firebase';
import { ImageRepository } from './ImageRepository';
import { ref, read, listQuery, list, AppException } from './Firestore';
import { UserProfileFromDoc, type UserProfile } from './model';
import { newlyEarned } from '../gamification/Badges';
import { NotificationRepository as notifications } from './NotificationRepository';
export const UserRepository = {
  ref: (uid: string) => ref(`users/${uid}`),
  profileFlow: (uid: string) => ref(`users/${uid}`),
  profile: (uid: string) => read(`users/${uid}`, UserProfileFromDoc),
  usersWithRole: (role: string) => listQuery('users', where('role', '==', role)),
  uidsWithRoles: async (...roles: string[]) =>
    (await list(listQuery('users', where('role', 'in', roles)), UserProfileFromDoc)).map(
      (p) => p.uid,
    ),
  findByUsername: async (name: string) => {
    const record = await read(
      `usernames/${name.trim().replace(/^@/, '').toLowerCase()}`,
      (_id, d) => d,
    );
    return record?.uid ? UserRepository.profile(String(record.uid)) : null;
  },
  isUsernameFree: async (name: string) =>
    !(await read(`usernames/${name.trim().toLowerCase()}`, (_id, d) => d)),
  validateUsername: (name: string) => {
    if (!/^[A-Za-z0-9_.]{3,20}$/.test(name)) throw new AppException('err_username_format');
  },
  updateProfile: async (
    current: UserProfile,
    username: string,
    displayName: string,
    bio: string,
    photoId: string,
  ) => {
    await ImageRepository.retain([photoId]);
    const name = username.trim();
    UserRepository.validateUsername(name);
    const changing = name.toLowerCase() !== current.username.toLowerCase();
    if (changing && !(await UserRepository.isUsernameFree(name)))
      throw new AppException('err_username_taken');
    const b = writeBatch(database());
    if (changing) {
      b.set(ref(`usernames/${name.toLowerCase()}`), { uid: current.uid });
      if (current.username) b.delete(ref(`usernames/${current.username.toLowerCase()}`));
    }
    b.update(UserRepository.ref(current.uid), {
      username: name,
      displayName: displayName.trim().slice(0, 60),
      bio: bio.trim().slice(0, 300),
      photoId,
    });
    await b.commit();
  },
  setShowStats: (uid: string, show: boolean) =>
    updateDoc(UserRepository.ref(uid), { showStatsPublicly: show }),
  setFollowing: async (uid: string, catId: string, follow: boolean) => {
    const b = writeBatch(database());
    b.update(ref(`cats/${catId}`), { followers: follow ? arrayUnion(uid) : arrayRemove(uid) });
    b.update(UserRepository.ref(uid), {
      followedCats: follow ? arrayUnion(catId) : arrayRemove(catId),
    });
    await b.commit();
  },
  addStats: (
    batch: WriteBatch,
    uid: string,
    stats: Record<string, number>,
    points: number,
    neighborhood?: string,
    rateLimited = false,
  ) => {
    const data: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(stats))
      if (value) data[`stats.${key}`] = increment(Math.round(value));
    if (points) data.points = increment(Math.round(points));
    if (neighborhood && neighborhood !== 'other') data.neighborhoods = arrayUnion(neighborhood);
    if (rateLimited) data.lastPostAt = serverTimestamp();
    if (Object.keys(data).length) batch.update(UserRepository.ref(uid), data);
  },
  awardNewBadges: async (p: UserProfile) => {
    const earned = newlyEarned(p);
    if (!earned.length) return;
    await updateDoc(UserRepository.ref(p.uid), { badges: arrayUnion(...earned.map((b) => b.key)) });
    for (const b of earned)
      await notifications.send([p.uid], 'achievement', { badge: b.key }, 'achievements');
  },
  setRole: (uid: string, role: string, volunteerStatus?: string) =>
    updateDoc(UserRepository.ref(uid), { role, ...(volunteerStatus ? { volunteerStatus } : {}) }),
};
