import {
  orderBy,
  limit,
  where,
  writeBatch,
  setDoc,
  updateDoc,
  deleteDoc,
} from 'firebase/firestore';
import { database } from '../firebase';
import { ref, listQuery, AppException } from './Firestore';
import {
  AdoptionRequestDefaults,
  AdoptionRequestToDoc,
  type AdoptionRequest,
  type Post,
  type UserProfile,
} from './model';
import { NotificationRepository as notifications } from './NotificationRepository';
export const AdoptionRepository = {
  requestId: (postId: string, uid: string) => `${postId}_${uid}`,
  requestsQuery: (uid: string, sent: boolean) =>
    listQuery(
      'adoptionRequests',
      where(sent ? 'requesterId' : 'ownerId', '==', uid),
      orderBy('createdAt', 'desc'),
    ),
  received: (uid: string) =>
    listQuery(
      'adoptionRequests',
      where('ownerId', '==', uid),
      orderBy('createdAt', 'desc'),
      limit(50),
    ),
  sent: (uid: string) =>
    listQuery(
      'adoptionRequests',
      where('requesterId', '==', uid),
      orderBy('createdAt', 'desc'),
      limit(50),
    ),
  myRequestFor: (postId: string, uid: string) => ref(`adoptionRequests/${postId}_${uid}`),
  request: async (post: Post, message: string, home: string, me: UserProfile) => {
    if (post.ownerId === me.uid) throw new AppException('err_own_listing');
    await setDoc(
      AdoptionRepository.myRequestFor(post.id, me.uid),
      AdoptionRequestToDoc({
        ...AdoptionRequestDefaults,
        postId: post.id,
        postName: post.name,
        postPhotoId: post.photoIds[0] ?? '',
        ownerId: post.ownerId,
        requesterId: me.uid,
        requesterName: me.username,
        message: message.trim().slice(0, 1000),
        home: home.trim().slice(0, 600),
        createdAt: Date.now(),
      }),
    );
    await notifications.send(
      [post.ownerId],
      'adoption_request',
      { user: me.username, name: post.name },
      'adoption-requests',
      undefined,
      AdoptionRepository.requestId(post.id, me.uid),
    );
  },
  decide: async (request: AdoptionRequest, approve: boolean, note: string) => {
    const b = writeBatch(database());
    b.update(ref(`adoptionRequests/${request.id}`), {
      status: approve ? 'approved' : 'declined',
      ownerNote: note.trim().slice(0, 500),
      decidedAt: Date.now(),
    });
    if (approve)
      b.update(ref(`posts/${request.postId}`), {
        adoptionStatus: 'pending',
        updatedAt: Date.now(),
      });
    await b.commit();
    await notifications.send(
      [request.requesterId],
      'adoption_decision',
      { name: request.postName, decision: approve ? 'approved' : 'declined' },
      `post/${request.postId}`,
      undefined,
      request.id,
    );
  },
  delete: (request: AdoptionRequest) => deleteDoc(ref(`adoptionRequests/${request.id}`)),
  withdraw: (request: AdoptionRequest) =>
    updateDoc(ref(`adoptionRequests/${request.id}`), { status: 'withdrawn' }),
};
