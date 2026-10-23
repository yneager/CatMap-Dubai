import {
  orderBy,
  doc,
  where,
  limit,
  setDoc,
  updateDoc,
  deleteDoc,
  writeBatch,
  serverTimestamp,
  runTransaction,
} from 'firebase/firestore';
import { database } from '../firebase';
import { col, ref, read, listQuery, AppException } from './Firestore';
import {
  SitterProfileDefaults,
  SitterProfileFromDoc,
  SitterProfileToDoc,
  BookingDefaults,
  BookingToDoc,
  ReviewDefaults,
  ReviewToDoc,
  type SitterProfile,
  type Booking,
  type UserProfile,
} from './model';
import { Dubai } from './Neighborhoods';
import { NotificationRepository as notifications } from './NotificationRepository';
import { ModerationRepository } from './ModerationRepository';
export const SittingRepository = {
  activeSitters: () => listQuery('sitters', where('active', '==', true), limit(500)),
  allSitters: () => listQuery('sitters', limit(800)),
  sitterFlow: (uid: string) => ref(`sitters/${uid}`),
  setVerified: async (p: SitterProfile, verified: boolean, admin: UserProfile) => {
    await updateDoc(ref(`sitters/${p.uid}`), { verified, updatedAt: Date.now() });
    await ModerationRepository.log(
      admin,
      verified ? 'sitter_verified' : 'sitter_unverified',
      'sitter',
      p.uid,
      p.name,
      '',
    );
  },
  saveSitter: async (me: UserProfile, p: SitterProfile) => {
    const existing = await read(`sitters/${me.uid}`, SitterProfileFromDoc);
    const data = {
      name: me.username,
      photoId: me.photoId,
      headline: p.headline.trim().slice(0, 80),
      description: p.description.trim().slice(0, 1500),
      experience: p.experience.trim().slice(0, 600),
      petTypes: p.petTypes,
      services: p.services,
      availability: p.availability.trim().slice(0, 300),
      rate: Math.round(Math.max(0, Math.min(99999, p.rate))),
      rateUnit: p.rateUnit,
      lat: p.lat,
      lng: p.lng,
      neighborhood: Dubai.nearest(p.lat, p.lng).key,
      active: p.active,
      updatedAt: Date.now(),
    };
    if (existing) await updateDoc(ref(`sitters/${me.uid}`), data);
    else
      await setDoc(
        ref(`sitters/${me.uid}`),
        SitterProfileToDoc({ ...SitterProfileDefaults, ...data, createdAt: Date.now() }),
      );
  },
  setActive: (uid: string, active: boolean) =>
    updateDoc(ref(`sitters/${uid}`), { active, updatedAt: Date.now() }),
  moderatorRemove: async (p: SitterProfile, me: UserProfile, note: string) => {
    await deleteDoc(ref(`sitters/${p.uid}`));
    await ModerationRepository.log(me, 'sitter_removed', 'sitter', p.uid, p.name, note);
    await notifications.send([p.uid], 'moderation', {
      target: 'sitter',
      label: p.name,
      decision: 'removed',
    });
  },
  bookingsQuery: (uid: string, sitter: boolean) =>
    listQuery(
      'bookings',
      where(sitter ? 'sitterId' : 'ownerId', '==', uid),
      orderBy('createdAt', 'desc'),
    ),
  reviewsQuery: (uid: string) =>
    listQuery('reviews', where('sitterId', '==', uid), orderBy('createdAt', 'desc')),
  asOwner: (uid: string) =>
    listQuery('bookings', where('ownerId', '==', uid), orderBy('createdAt', 'desc'), limit(50)),
  asSitter: (uid: string) =>
    listQuery('bookings', where('sitterId', '==', uid), orderBy('createdAt', 'desc'), limit(50)),
  allBookings: () => listQuery('bookings', limit(500)),
  bookingFlow: (id: string) => ref(`bookings/${id}`),
  requestBooking: async (
    sitter: SitterProfile,
    petTypes: string[],
    petCount: number,
    startAt: number,
    endAt: number,
    notes: string,
    me: UserProfile,
  ) => {
    if (sitter.uid === me.uid) throw new AppException('err_own_listing');
    if (endAt <= startAt) throw new AppException('err_dates');
    const target = doc(col('bookings'));
    const b = writeBatch(database());
    b.set(
      target,
      BookingToDoc({
        ...BookingDefaults,
        ownerId: me.uid,
        ownerName: me.username,
        sitterId: sitter.uid,
        sitterName: sitter.name,
        petTypes,
        petCount: Math.max(1, Math.min(20, Math.round(petCount))),
        startAt: Math.round(startAt),
        endAt: Math.round(endAt),
        notes: notes.trim().slice(0, 1000),
        createdAt: Date.now(),
        updatedAt: Date.now(),
      }),
    );
    b.update(ref(`users/${me.uid}`), { lastPostAt: serverTimestamp() });
    await b.commit();
    await notifications.send(
      [sitter.uid],
      'booking_request',
      { user: me.username },
      'bookings?tab=sitter',
      undefined,
      target.id,
    );
    return target.id;
  },
  setStatus: async (booking: Booking, status: string, me: UserProfile, note = '') => {
    await updateDoc(ref(`bookings/${booking.id}`), {
      status,
      updatedAt: Date.now(),
      ...(me.uid === booking.sitterId && note.trim()
        ? { sitterNote: note.trim().slice(0, 500) }
        : {}),
    });
    const other = me.uid === booking.sitterId ? booking.ownerId : booking.sitterId;
    await notifications.send(
      [other],
      'booking_update',
      { user: me.username, status },
      other === booking.sitterId ? 'bookings?tab=sitter' : 'bookings',
      undefined,
      booking.id,
    );
  },
  reviewsFor: (uid: string) =>
    listQuery('reviews', where('sitterId', '==', uid), orderBy('createdAt', 'desc'), limit(50)),
  review: async (booking: Booking, rating: number, text: string, me: UserProfile) => {
    const score = Math.max(1, Math.min(5, Math.round(rating)));
    await runTransaction(database(), async (tx) => {
      const sitterRef = ref(`sitters/${booking.sitterId}`);
      const sitter = await tx.get(sitterRef);
      tx.set(
        ref(`reviews/${booking.id}`),
        ReviewToDoc({
          ...ReviewDefaults,
          sitterId: booking.sitterId,
          authorId: me.uid,
          authorName: me.username,
          rating: score,
          text: text.trim().slice(0, 800),
          createdAt: Date.now(),
        }),
      );
      if (sitter.exists())
        tx.update(sitterRef, {
          ratingSum: (sitter.data().ratingSum ?? 0) + score,
          ratingCount: (sitter.data().ratingCount ?? 0) + 1,
          lastReviewId: booking.id,
          updatedAt: Date.now(),
        });
      tx.update(ref(`bookings/${booking.id}`), { reviewed: true, updatedAt: Date.now() });
    });
    await notifications.send(
      [booking.sitterId],
      'review',
      { user: me.username, rating: String(score) },
      `sitter/${booking.sitterId}`,
      undefined,
      booking.id,
    );
  },
};
