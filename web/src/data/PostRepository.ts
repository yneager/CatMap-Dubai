import {
  doc,
  where,
  orderBy,
  limit,
  writeBatch,
  updateDoc,
  increment,
  deleteDoc,
  deleteField,
  getDoc,
  getDocs,
  getDocFromServer,
} from 'firebase/firestore';
import { VideoRepository } from './VideoRepository';
import { database } from '../firebase';
import { col, ref, listQuery, list, read, deleteDocuments } from './Firestore';
import {
  ContentReportFromDoc,
  PostDefaults,
  PostFromDoc,
  PostMatchFromDoc,
  PostToDoc,
  PostUpdateDefaults,
  PostUpdateToDoc,
  PostUpdateFromDoc,
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
import { Geohash } from './Geohash';
import { SearchTerms } from './SearchTerms';
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
    geoCells: Geohash.cells(clean.lat, clean.lng),
    searchTerms: SearchTerms.of(
      keywords(clean.name, clean.color, clean.breed, clean.features, clean.description, hood),
    ),
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
export type PostAvailability = 'gone' | 'hidden' | 'there';

export const PostRepository = {
  feedQuery: (categories: string[] = []) =>
    listQuery(
      'posts',
      where('hidden', '==', false),
      ...(categories.length ? [where('category', 'in', categories)] : []),
      orderBy('createdAt', 'desc'),
    ),
  searchQuery: (word: string) =>
    listQuery(
      'posts',
      where('hidden', '==', false),
      where('searchTerms', 'array-contains-any', SearchTerms.query(word)),
      orderBy('createdAt', 'desc'),
    ),
  byIds: async (ids: string[]) =>
    (
      await Promise.all([...new Set(ids)].map((id) => PostRepository.post(id).catch(() => null)))
    ).filter((p): p is Post => !!p && !p.hidden),
  near: async (lat: number, lng: number, radius: number, perCell = 30) => {
    const pages = await Promise.all(
      Geohash.around(lat, lng, radius).map((cell) =>
        list(
          listQuery(
            'posts',
            where('hidden', '==', false),
            where('geoCells', 'array-contains', cell),
            orderBy('updatedAt', 'desc'),
            limit(perCell),
          ),
          PostFromDoc,
        ),
      ),
    );
    return [...new Map(pages.flat().map((p) => [p.id, p])).values()].filter(
      (p) => distanceKm(lat, lng, p.lat, p.lng) <= radius,
    );
  },
  newest: (count = 300) =>
    list(
      listQuery('posts', where('hidden', '==', false), orderBy('createdAt', 'desc'), limit(count)),
      PostFromDoc,
    ),
  postFlow: (id: string) => ref(`posts/${id}`),
  post: (id: string) => read(`posts/${id}`, PostFromDoc),
  /** Delete leftovers only after a confirmed server miss; hidden posts may return. */
  availability: async (id: string): Promise<PostAvailability> => {
    try {
      return (await getDocFromServer(ref(`posts/${id}`))).exists() ? 'there' : 'gone';
    } catch (error) {
      return (error as { code?: string }).code === 'permission-denied' ? 'hidden' : 'there';
    }
  },
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
    // Possible matches are found by MatchRepository.afterPublish (the PC's AI model, or this device offline).
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
      'resolution',
      'resolvedMatchId',
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
      // Reopening forgets how it ended; the match history itself is kept.
      ...(status === 'open' ? { resolution: '', resolvedMatchId: '' } : {}),
    });
    if (status === 'reunited' && post.status !== 'reunited' && post.ownerId === me.uid)
      UserRepository.addStats(b, me.uid, { reunited: 1 }, 25, undefined, true);
    await b.commit();
  },
  stillLooking: (p: Post) =>
    updateDoc(ref(`posts/${p.id}`), { lastActivityAt: Date.now(), updatedAt: Date.now() }),
  remindStalePosts: async (uid: string) => {
    const now = Date.now();
    const key = 'stale_check_' + uid;
    if (now - Number(localStorage.getItem(key) ?? 0) < 12 * 3600000) return;
    localStorage.setItem(key, String(now));
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
    await VideoRepository.delete(p.videoPath).catch((error) => console.info('Post video cleanup failed', error));
    await deleteDoc(ref(`postEmbeddings/${p.id}`)).catch((error) => console.info('Embedding cleanup failed', error));
    await getDocs(listQuery('adoptionRequests', where('ownerId', '==', p.ownerId), where('postId', '==', p.id)))
      .then((snap) => Promise.allSettled(snap.docs.map((d) => deleteDoc(d.ref))))
      .catch((error) => console.info('Adoption cleanup failed', error));
    await Promise.allSettled(
      [...p.photoIds, ...updates.map((u) => u.photoId).filter(Boolean)].map(ImageRepository.delete),
    );
    // Its match suggestions too (allowed once the post is gone). A moderator can't delete them; the two people's
    // apps drop them when they next see them (usePostMatches).
    const matches = await list(
      listQuery('matches', where('members', 'array-contains', p.ownerId)),
      PostMatchFromDoc,
    ).catch(() => []);
    await Promise.allSettled(
      matches
        .filter((m) => m.lostPostId === p.id || m.sightingPostId === p.id)
        .map((m) => deleteDoc(ref(`matches/${m.id}`))),
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
    const reports = await list(listQuery('contentReports', where('targetType', '==', 'post'),
      where('targetId', '==', p.id), where('status', '==', 'open')), ContentReportFromDoc)
      .catch((error) => { console.info('Post report lookup failed', error); return []; });
    await Promise.allSettled(reports.map((report) =>
      ModerationRepository.closeReport(report, 'resolved', note || 'post deleted', me)
        .catch((error) => console.info('Post report cleanup failed', error))));
    await ModerationRepository.log(me, 'post_deleted', 'post', p.id, p.name || p.category, note);
    await notifications.send([p.ownerId], 'moderation', {
      target: 'post',
      label: p.name || p.category,
      decision: 'removed',
    });
  },
  updates: (id: string) =>
    listQuery(`posts/${id}/updates`, orderBy('createdAt', 'desc'), limit(30)),
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
  areaCentre: (uid: string) => ref(`users/${uid}/private/areaFollow`),
  moveAreaCentreToPrivate: async (uid: string) => {
    const flag = 'area_follow_private_' + uid;
    if (localStorage.getItem(flag)) return;
    const legacy = (await getDoc(ref(`areaFollows/${uid}`))).data();
    if (typeof legacy?.lat === 'number' && typeof legacy.lng === 'number') {
      const b = writeBatch(database());
      b.set(PostRepository.areaCentre(uid), {
        lat: legacy.lat,
        lng: legacy.lng,
        radiusKm: typeof legacy.radiusKm === 'number' ? legacy.radiusKm : 3,
        updatedAt: Date.now(),
      });
      b.update(ref(`areaFollows/${uid}`), {
        lat: deleteField(),
        lng: deleteField(),
        radiusKm: deleteField(),
      });
      await b.commit();
    }
    localStorage.setItem(flag, '1');
  },
  followArea: async (uid: string, lat: number, lng: number, radiusKm: number) => {
    let neighborhoods = Dubai.neighborhoods
      .filter((p) => distanceKm(lat, lng, p.lat, p.lng) <= radiusKm)
      .map((p) => p.key);
    if (!neighborhoods.length) neighborhoods = [Dubai.nearest(lat, lng).key];
    const b = writeBatch(database()),
      updatedAt = Date.now();
    b.set(ref(`areaFollows/${uid}`), { neighborhoods, updatedAt });
    b.set(PostRepository.areaCentre(uid), { lat, lng, radiusKm, updatedAt });
    await b.commit();
  },
  unfollowArea: async (uid: string) => {
    const b = writeBatch(database());
    b.delete(ref(`areaFollows/${uid}`));
    b.delete(PostRepository.areaCentre(uid));
    await b.commit();
  },
  keywords,
  likelyDuplicates,
};
