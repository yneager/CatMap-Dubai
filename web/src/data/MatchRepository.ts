// The lost-to-reunited workflow on the web, the same documents and rules as Android's MatchRepository.kt:
// suggestions (the PC worker, or this device while the PC is off), answers, the chat about a match, the reunion.
import {
  doc,
  where,
  orderBy,
  limit,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  increment,
  runTransaction,
} from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, listQuery, read, list } from './Firestore';
import {
  PostMatchFromDoc,
  PostFromDoc,
  MessageDefaults,
  MessageToDoc,
  ChatFromDoc,
  type Post,
  type PostMatch,
  type UserProfile,
} from './model';
import { PostRepository } from './PostRepository';
import { ChatRepository } from './ChatRepository';
import { UserRepository } from './UserRepository';
import { NotificationRepository as notifications } from './NotificationRepository';
import { AiServer } from './AiServer';
import * as Engine from '../ai/MatchEngine';

export const MAX_DEVICE_MATCHES = 5;

/** Worth showing: a real suggestion, or one somebody already answered. */
export const visibleMatch = (m: PostMatch) =>
  m.tier !== 'none' ||
  ['maybe', 'need_info', 'reunited'].includes(m.ownerState) ||
  m.reporterState === 'confirmed';

async function announce(lost: Post, sighting: Post, id: string, me: UserProfile) {
  const params = {
    name: lost.name,
    type: lost.animalType,
    area: sighting.neighborhood,
    lostArea: lost.neighborhood,
    matchId: id,
  };
  if (lost.ownerId !== me.uid)
    await notifications.send([lost.ownerId], 'match_found', params, `match/${id}`, `pm_${id}`);
  if (sighting.ownerId !== me.uid)
    await notifications.send(
      [sighting.ownerId],
      'match_reporter',
      params,
      `match/${id}`,
      `pr_${id}`,
    );
}

function matchDoc(p: Engine.Pairing, me: UserProfile, extra: Record<string, unknown> = {}) {
  const r = p.result,
    now = Date.now();
  return {
    lostPostId: p.lost.id,
    sightingPostId: p.sighting.id,
    lostOwnerId: p.lost.ownerId,
    sightingOwnerId: p.sighting.ownerId,
    members: [p.lost.ownerId, p.sighting.ownerId],
    animalType: p.lost.animalType,
    score: r.score,
    visual: r.visual,
    tier: r.tier,
    method: r.method,
    model: r.method === 'fingerprint' ? Engine.MODEL_FINGERPRINT : Engine.MODEL_DETAILS,
    distanceKm: r.distanceKm,
    daysApart: r.daysApart,
    reasons: r.reasons,
    source: 'device',
    ownerState: 'new',
    reporterState: 'new',
    createdBy: me.uid,
    createdAt: now,
    updatedAt: now,
    notified: r.tier === 'possible',
    ...extra,
  };
}

export const MatchRepository = {
  mine: (uid: string) => listQuery('matches', where('members', 'array-contains', uid)),
  matchFlow: (id: string) => ref(`matches/${id}`),
  /** The rules let either person delete a match once one of its posts is gone; not awaited, so it waits offline. */
  drop: (m: PostMatch) =>
    void deleteDoc(ref(`matches/${m.id}`)).catch((error) =>
      console.info('Match cleanup failed', error),
    ),
  /** Unavailable when either post is gone or hidden; only a confirmed deletion removes the match. */
  orphaned: async (m: PostMatch) => {
    const [lost, sighting] = await Promise.all([
      PostRepository.availability(m.lostPostId),
      PostRepository.availability(m.sightingPostId),
    ]);
    if (lost === 'gone' || sighting === 'gone') MatchRepository.drop(m);
    return lost !== 'there' || sighting !== 'there';
  },
  /** After publishing a lost report or a sighting: the PC does it when online, otherwise this device. */
  candidates: (post: Post) =>
    list(
      listQuery(
        'posts',
        where('hidden', '==', false),
        where('category', 'in', Engine.isLost(post) ? Engine.SIGHTINGS : Engine.LOST),
        orderBy('createdAt', 'desc'),
        limit(200),
      ),
      PostFromDoc,
    ),
  afterPublish: async (postId: string, me: UserProfile) => {
    const post = await PostRepository.post(postId);
    if (!post || (!Engine.isLost(post) && !Engine.isSighting(post))) return 0;
    if (await AiServer.health()) return 0;
    return MatchRepository.matchOnDevice(post, await MatchRepository.candidates(post), me);
  },
  /** The owner's open report while the PC is off: pairs it with what was posted since (sighting first). */
  reconcile: async (post: Post, feed: Post[], me: UserProfile) => {
    if (post.ownerId !== me.uid || post.status !== 'open' || (await AiServer.health())) return 0;
    return MatchRepository.matchOnDevice(post, feed, me);
  },
  matchOnDevice: async (post: Post, feed: Post[], me: UserProfile) => {
    let written = 0;
    for (const p of Engine.deviceMatches(post, feed)
      .filter((x) => x.result.tier !== 'none')
      .slice(0, MAX_DEVICE_MATCHES)) {
      const target = ref(`matches/${p.id}`);
      // An existing pair (maybe rejected) is never written again; the rules refuse overwrites anyway.
      const exists = await getDoc(target)
        .then((s) => s.exists())
        .catch(() => true);
      if (exists) continue;
      try {
        await setDoc(target, matchDoc(p, me));
        written++;
        if (p.result.tier === 'possible') await announce(p.lost, p.sighting, p.id, me);
      } catch {
        /* a suggestion that can't be saved is simply skipped */
      }
    }
    return written;
  },
  setOwnerState: (m: PostMatch, state: string) =>
    updateDoc(ref(`matches/${m.id}`), {
      ownerState: state,
      reviewedAt: Date.now(),
      updatedAt: Date.now(),
    }),
  setReporterState: (m: PostMatch, state: string) =>
    updateDoc(ref(`matches/${m.id}`), { reporterState: state, updatedAt: Date.now() }),
  linkSighting: async (lost: Post, sighting: Post, me: UserProfile) => {
    const id = Engine.matchId(lost.id, sighting.id);
    if (await read(`matches/${id}`, PostMatchFromDoc)) {
      await updateDoc(ref(`matches/${id}`), { reporterState: 'confirmed', updatedAt: Date.now() });
      return id;
    }
    const pairing = Engine.deviceMatches(sighting, [lost])[0] ?? {
      id,
      lost,
      sighting,
      result: Engine.score(
        Engine.side(lost),
        Engine.side(sighting),
        'details',
        -1,
        lost.animalType,
      ),
    };
    const r = pairing.result;
    await setDoc(
      ref(`matches/${id}`),
      matchDoc(
        {
          ...pairing,
          result: {
            ...r,
            tier: r.tier === 'strong' ? 'possible' : r.tier,
            reasons: [...r.reasons, 'reporter_linked'],
          },
        },
        me,
        {
          reporterState: 'confirmed',
          notified: true,
        },
      ),
    );
    await announce(lost, sighting, id, me);
    return id;
  },
  /** One chat per pair of people (reused), with the match's card added the first time it's discussed there. */
  openChat: async (m: PostMatch, lost: Post | null, sighting: Post | null, me: UserProfile) => {
    const other = me.uid === m.lostOwnerId ? m.sightingOwnerId : m.lostOwnerId;
    const chatId = await ChatRepository.open(me, other);
    const has = !(
      await getDocs(listQuery(`chats/${chatId}/messages`, where('matchId', '==', m.id), limit(1)))
    ).empty;
    if (!has) {
      const chat = await read(`chats/${chatId}`, ChatFromDoc);
      const otherUid = chat?.members.find((u) => u !== me.uid) ?? other;
      const title = lost?.name ?? '';
      const b = writeBatch(database());
      b.set(
        doc(col(`chats/${chatId}/messages`)),
        MessageToDoc({
          ...MessageDefaults,
          senderId: me.uid,
          postId: lost?.id ?? '',
          postTitle: title,
          postPhotoId: lost?.photoIds[0] ?? '',
          matchId: m.id,
          matchPhotoId: sighting?.photoIds[0] ?? '',
          createdAt: Date.now(),
        }),
      );
      b.update(ref(`chats/${chatId}`), {
        lastText: `Possible match: ${title}`.trim(),
        lastSender: me.uid,
        lastAt: Date.now(),
        [`unread.${otherUid}`]: increment(1),
        [`names.${me.uid}`]: me.username,
        [`photos.${me.uid}`]: me.photoId,
      });
      await b.commit();
    }
    return chatId;
  },
  requestInfo: async (m: PostMatch, lost: Post | null, sighting: Post | null, me: UserProfile) => {
    await MatchRepository.setOwnerState(m, 'need_info');
    const chatId = await MatchRepository.openChat(m, lost, sighting, me);
    await notifications.send(
      [m.sightingOwnerId],
      'match_info',
      { name: lost?.name ?? '', type: m.animalType, user: me.username },
      `chat/${chatId}`,
      `mi_${m.id}`,
    );
    return chatId;
  },
  /** Marks the report reunited once, in one transaction with the match that helped; thanks the reporter. */
  reunite: async (post: Post, match: PostMatch | null, resolution: string, me: UserProfile) => {
    const credited =
      resolution === 'catmap' && match && match.lostPostId === post.id ? match : null;
    const changed = await runTransaction(database(), async (tx) => {
      const fresh = await tx.get(ref(`posts/${post.id}`));
      if (fresh.data()?.status === 'reunited') return false;
      const at = Date.now();
      tx.update(ref(`posts/${post.id}`), {
        status: 'reunited',
        resolution,
        resolvedMatchId: credited?.id ?? '',
        resolvedAt: at,
        updatedAt: at,
        lastActivityAt: at,
      });
      if (credited)
        tx.update(ref(`matches/${credited.id}`), {
          ownerState: 'reunited',
          reviewedAt: at,
          updatedAt: at,
        });
      return true;
    });
    if (!changed) return false;
    if (post.ownerId === me.uid) {
      const b = writeBatch(database());
      UserRepository.addStats(b, me.uid, { reunited: 1 }, 25, undefined, true);
      await b.commit().catch(() => {});
    }
    if (credited)
      await notifications.send(
        [credited.sightingOwnerId],
        'match_reunited',
        { name: post.name, type: post.animalType },
        `post/${post.id}`,
        `ru_${credited.id}`,
      );
    return true;
  },
  verifyReunion: (m: PostMatch, moderator: UserProfile) =>
    updateDoc(ref(`matches/${m.id}`), { verifiedBy: moderator.uid, verifiedAt: Date.now() }),
};
