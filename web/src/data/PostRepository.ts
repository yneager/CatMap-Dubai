import {
  doc,
  where,
  orderBy,
  limit,
  writeBatch,
  setDoc,
  updateDoc,
  increment,
  deleteDoc,
} from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, listQuery, list, read, deleteDocuments } from './Firestore';
import {
  PostDefaults,
  PostFromDoc,
  PostToDoc,
  PostUpdateDefaults,
  PostUpdateToDoc,
  PostUpdateFromDoc,
  AreaFollowDefaults,
  AreaFollowToDoc,
  AreaFollowFromDoc,
  type Post,
  type PostUpdate,
  type UserProfile,
} from './model';
import { Dubai, distanceKm } from './Neighborhoods';
import { UserRepository } from './UserRepository';
import { NotificationRepository as notifications } from './NotificationRepository';
import { PostStaleness } from './PostStaleness';
import { ImageRepository } from './ImageRepository';
import { ModerationRepository } from './ModerationRepository';
export function keywords(...parts: string[]) {
  const hood = Dubai.byKey(parts.at(-1));
  const split = (text: string) => text.toLowerCase().split(/[^\p{L}\p{N}]+/u);
  return [
    ...new Set(
      [...parts.slice(0, -1).flatMap(split), ...split(hood.nameEn), ...split(hood.nameAr)].filter(
        (w) => w.length >= 2,
      ),
    ),
  ].slice(0, 80);
}
export function likelyDuplicates(draft: Post, candidates: Post[]) {
  const words = new Set(draft.keywords);
  return candidates
    .filter((p) => p.id !== draft.id && p.animalType === draft.animalType && p.status === 'open')
    .filter((p) => draft.lat === 0 || distanceKm(p.lat, p.lng, draft.lat, draft.lng) <= 3)
    .filter(
      (p) => draft.createdAt === 0 || Math.abs(p.createdAt - draft.createdAt) <= 21 * 86400000,
    )
    .map((post) => ({
      post,
      score:
        post.keywords.filter((w) => words.has(w)).length +
        (post.category === draft.category ? 1 : 0),
    }))
    .filter((p) => p.score >= 2)
    .sort((a, b) => b.score - a.score)
    .slice(0, 5)
    .map((p) => p.post);
}
export function toPost(draft: Post, me: UserProfile, time = Date.now()): Post {
  const hood = Dubai.nearest(draft.lat, draft.lng).key;
  const clean = {
    ...PostDefaults,
    ...draft,
    id: '',
    ownerId: me.uid,
    ownerName: me.username,
    ownerPhotoId: me.photoId,
    status: 'open',
    name: draft.name.trim().slice(0, 60),
    description: draft.description.trim().slice(0, 2000),
    color: draft.color.trim().slice(0, 60),
    breed: draft.breed.trim().slice(0, 60),
    features: draft.features.trim().slice(0, 400),
    photoIds: draft.photoIds.slice(0, 6),
    areaRadiusM: Math.round(Math.max(0, Math.min(5000, draft.areaRadiusM))),
    reward: draft.category === 'lost' ? Math.round(Math.max(0, Math.min(999999, draft.reward))) : 0,
    contactPhone: draft.contactPhone.trim().slice(0, 30),
    contactEmail: draft.contactEmail.trim().slice(0, 80),
    health: draft.health.trim().slice(0, 400),
    neighborhood: hood,
    updatedAt: time,
  };
  return {
    ...clean,
    keywords: keywords(
      clean.name,
      clean.color,
      clean.breed,
      clean.features,
      clean.description,
      hood,
    ),
  };
}
export const PostRepository = {
  feed: () =>
    listQuery('posts', where('hidden', '==', false), orderBy('createdAt', 'desc'), limit(400)),
  allPosts: () => listQuery('posts', orderBy('createdAt', 'desc'), limit(600)),
  feedOnce: (count = 400) =>
    list(
      listQuery('posts', where('hidden', '==', false), orderBy('createdAt', 'desc'), limit(count)),
      PostFromDoc,
    ),
  postFlow: (id: string) => ref(`posts/${id}`),
  post: (id: string) => read(`posts/${id}`, PostFromDoc),
  postsBy: (uid: string) => listQuery('posts', where('ownerId', '==', uid)),
  publicPostsBy: (uid: string) =>
    listQuery('posts', where('ownerId', '==', uid), where('hidden', '==', false)),
  create: async (draft: Post, me: UserProfile) => {
    const target = doc(col('posts'));
    const now = Date.now();
    const post = {
      ...toPost(draft, me, now),
      createdAt: now,
      lastActivityAt: now,
      hidden: false,
      updatesCount: 0,
    };
    const b = writeBatch(database());
    b.set(target, PostToDoc(post));
    UserRepository.addStats(b, me.uid, { posts: 1 }, 10, post.neighborhood, true);
    await b.commit();
    const params = {
      type: post.animalType,
      category: post.category,
      area: post.neighborhood,
      name: post.name,
    };
    if (post.category === 'lost') {
      const followers = await list(
        listQuery(
          'areaFollows',
          where('neighborhoods', 'array-contains', post.neighborhood),
          limit(300),
        ),
        AreaFollowFromDoc,
      ).catch(() => []);
      await notifications.send(
        followers.map((p) => p.uid).filter((uid) => uid !== me.uid),
        'lost_nearby',
        params,
        `post/${target.id}`,
        `lost_${target.id}`,
      );
    }
    if (['found', 'spotted'].includes(post.category)) {
      const owners = (await PostRepository.feedOnce(300).catch(() => []))
        .filter(
          (p) =>
            p.category === 'lost' &&
            p.status === 'open' &&
            p.animalType === post.animalType &&
            p.ownerId !== me.uid &&
            distanceKm(p.lat, p.lng, post.lat, post.lng) <= 5,
        )
        .map((p) => p.ownerId);
      await notifications.send(
        owners,
        'possible_match',
        params,
        `post/${target.id}`,
        `match_${target.id}`,
      );
    }
    return target.id;
  },
  edit: async (existing: Post, draft: Post, me: UserProfile) => {
    const p = toPost(draft, me);
    const data = PostToDoc(p);
    for (const key of [
      'ownerId',
      'ownerName',
      'ownerPhotoId',
      'status',
      'adoptionStatus',
      'createdAt',
      'hidden',
      'updatesCount',
      'resolvedAt',
    ])
      delete data[key];
    data.lastActivityAt = p.updatedAt;
    await updateDoc(ref(`posts/${existing.id}`), data);
  },
  setStatus: async (post: Post, status: string, me: UserProfile) => {
    const now = Date.now();
    const b = writeBatch(database());
    b.update(ref(`posts/${post.id}`), {
      status,
      resolvedAt: status === 'open' ? 0 : now,
      updatedAt: now,
      lastActivityAt: now,
    });
    if (status === 'reunited' && post.status !== 'reunited' && post.ownerId === me.uid)
      UserRepository.addStats(b, me.uid, { reunited: 1 }, 25);
    await b.commit();
  },
  stillLooking: (p: Post) =>
    updateDoc(ref(`posts/${p.id}`), { lastActivityAt: Date.now(), updatedAt: Date.now() }),
  remindStalePosts: async (uid: string) => {
    const now = Date.now();
    for (const p of await list(PostRepository.postsBy(uid), PostFromDoc))
      if (!p.hidden && PostStaleness.needsReminder(p, now))
        await notifications.send(
          [uid],
          'stale_post',
          { name: p.name },
          `post/${p.id}`,
          PostStaleness.reminderId(p, now),
        );
  },
  setAdoptionStatus: (p: Post, status: string) =>
    updateDoc(ref(`posts/${p.id}`), { adoptionStatus: status, updatedAt: Date.now() }),
  delete: async (p: Post) => {
    const updates = await list(listQuery(`posts/${p.id}/updates`), PostUpdateFromDoc);
    await deleteDocuments(updates.map((u) => `posts/${p.id}/updates/${u.id}`));
    await deleteDoc(ref(`posts/${p.id}`));
    await Promise.allSettled(
      [...p.photoIds, ...updates.map((u) => u.photoId).filter(Boolean)].map(ImageRepository.delete),
    );
  },
  setHidden: async (p: Post, hidden: boolean, me: UserProfile, note = '') => {
    await updateDoc(ref(`posts/${p.id}`), { hidden, updatedAt: Date.now() });
    await ModerationRepository.log(
      me,
      hidden ? 'post_hidden' : 'post_restored',
      'post',
      p.id,
      p.name || p.category,
      note,
    );
    if (hidden)
      await notifications.send([p.ownerId], 'moderation', {
        target: 'post',
        label: p.name || p.category,
        decision: 'removed',
      });
  },
  moderatorDelete: async (p: Post, me: UserProfile, note = '') => {
    await PostRepository.delete(p);
    await ModerationRepository.log(me, 'post_deleted', 'post', p.id, p.name || p.category, note);
    await notifications.send([p.ownerId], 'moderation', {
      target: 'post',
      label: p.name || p.category,
      decision: 'removed',
    });
  },
  updates: (id: string) =>
    listQuery(`posts/${id}/updates`, orderBy('createdAt', 'desc'), limit(200)),
  addUpdate: async (
    p: Post,
    kind: string,
    text: string,
    photoId: string,
    lat: number,
    lng: number,
    me: UserProfile,
  ) => {
    const b = writeBatch(database());
    b.set(
      doc(col(`posts/${p.id}/updates`)),
      PostUpdateToDoc({
        ...PostUpdateDefaults,
        postId: p.id,
        kind,
        text: text.trim().slice(0, 1000),
        photoId,
        lat,
        lng,
        neighborhood: lat !== 0 || lng !== 0 ? Dubai.nearest(lat, lng).key : '',
        byUid: me.uid,
        byName: me.username,
        createdAt: Date.now(),
      }),
    );
    b.update(ref(`posts/${p.id}`), { updatesCount: increment(1), updatedAt: Date.now() });
    UserRepository.addStats(b, me.uid, { postUpdates: 1 }, 5, undefined, true);
    await b.commit();
    if (p.ownerId !== me.uid)
      await notifications.send(
        [p.ownerId],
        'post_update',
        { user: me.username, kind, name: p.name, type: p.animalType },
        `post/${p.id}`,
        `upd_${p.id}`,
      );
  },
  deleteUpdate: async (p: Post, u: PostUpdate) => {
    const b = writeBatch(database());
    b.delete(ref(`posts/${p.id}/updates/${u.id}`));
    b.update(ref(`posts/${p.id}`), { updatesCount: increment(-1) });
    await b.commit();
    if (u.photoId) await ImageRepository.delete(u.photoId).catch(() => {});
  },
  areaFollow: (uid: string) => ref(`areaFollows/${uid}`),
  followArea: async (uid: string, lat: number, lng: number, radiusKm: number) => {
    let neighborhoods = Dubai.neighborhoods
      .filter((p) => distanceKm(lat, lng, p.lat, p.lng) <= radiusKm)
      .map((p) => p.key);
    if (!neighborhoods.length) neighborhoods = [Dubai.nearest(lat, lng).key];
    await setDoc(
      ref(`areaFollows/${uid}`),
      AreaFollowToDoc({
        ...AreaFollowDefaults,
        neighborhoods,
        lat,
        lng,
        radiusKm,
        updatedAt: Date.now(),
      }),
    );
  },
  unfollowArea: (uid: string) => deleteDoc(ref(`areaFollows/${uid}`)),
  keywords,
  likelyDuplicates,
};
