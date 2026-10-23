import {
  doc,
  where,
  orderBy,
  limit,
  writeBatch,
  updateDoc,
  arrayUnion,
  increment,
  documentId,
} from 'firebase/firestore';
import { database } from '../firebase';
import { ImageRepository } from './ImageRepository';
import { col, ref, read, list, listQuery } from './Firestore';
import {
  CatDefaults,
  CatFromDoc,
  CatToDoc,
  SightingDefaults,
  SightingToDoc,
  PrivateLocationDefaults,
  PrivateLocationToDoc,
  PrivateLocationFromDoc,
  MedicalRecordDefaults,
  MedicalRecordToDoc,
  MergeRequestDefaults,
  MergeRequestToDoc,
  type Cat,
  type Sighting,
  type UserProfile,
  type MergeRequest,
} from './model';
import { Dubai, LocationPrivacy, distanceKm } from './Neighborhoods';
import { Geohash } from './Geohash';
import { SearchTerms } from './SearchTerms';
import { UserRepository as users } from './UserRepository';
import { NotificationRepository as notifications } from './NotificationRepository';
import { ModerationRepository as moderation } from './ModerationRepository';
export interface SightingDraft {
  existingCat: Cat | null;
  newCatName: string;
  description: string;
  distinctiveMarks: string;
  sex: string;
  coatColor: string;
  pattern: string;
  size: string;
  breedHints: string[];
  photoIds: string[];
  features: number[];
  lat: number;
  lng: number;
  accuracy: number;
  locationSource: string;
  seenAt: number;
  healthTags: string[];
  behaviorTags: string[];
  earTip: string;
  possiblyNotNeutered: boolean;
  needsMedical: boolean;
  notes: string;
  matchScore: number;
  aiSummary: string;
  suggestedCatIds: string[];
  rejectedCatIds: string[];
}
export const CatRepository = {
  directoryQuery: (field = 'lastSeen', descending = true) =>
    listQuery('cats', orderBy(field, descending ? 'desc' : 'asc')),
  neighborhoodQuery: (key: string) =>
    listQuery('cats', where('neighborhoods', 'array-contains', key), orderBy('lastSeen', 'desc')),
  searchQuery: (word: string) =>
    listQuery(
      'cats',
      where('searchTerms', 'array-contains-any', SearchTerms.query(word)),
      orderBy('lastSeen', 'desc'),
    ),
  byIds: async (ids: string[]) => {
    const unique = [...new Set(ids)],
      result: Cat[] = [];
    for (let i = 0; i < unique.length; i += 30)
      result.push(
        ...(await list(
          listQuery('cats', where(documentId(), 'in', unique.slice(i, i + 30))),
          CatFromDoc,
        )),
      );
    return result
      .filter((c) => !c.hidden && !c.mergedInto)
      .sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id));
  },
  near: async (lat: number, lng: number, radius: number, perCell = 150) => {
    const pages = await Promise.all(
      Geohash.around(lat, lng, radius).map((cell) =>
        list(
          listQuery(
            'cats',
            where('geoCells', 'array-contains', cell),
            orderBy('updatedAt', 'desc'),
            limit(perCell),
          ),
          CatFromDoc,
        ),
      ),
    );
    return [...new Map(pages.flat().map((c) => [c.id, c])).values()].filter(
      (c) => !c.hidden && !c.mergedInto && distanceKm(lat, lng, c.lat, c.lng) <= radius,
    );
  },
  privateLocation: (id: string) =>
    read(`privateLocations/${id}`, PrivateLocationFromDoc).catch(() => null),
  catFlow: (id: string) => ref(`cats/${id}`),
  cat: (id: string) => read(`cats/${id}`, CatFromDoc),
  newest: async (count = 300) =>
    (await list(listQuery('cats', orderBy('lastSeen', 'desc'), limit(count)), CatFromDoc)).filter(
      (c) => !c.hidden && !c.mergedInto,
    ),
  sightingsForCat: (id: string) => listQuery('sightings', where('catId', '==', id)),
  sightingsForUser: (uid: string) => listQuery('sightings', where('userId', '==', uid)),
  recentSightings: () => listQuery('sightings', orderBy('seenAt', 'desc'), limit(600)),
  pendingSightings: () => listQuery('sightings', where('status', '==', 'pending')),
  sightingFlow: (id: string) => ref(`sightings/${id}`),
  submitSighting: async (
    d: SightingDraft,
    me: UserProfile,
    privacyLevel: string,
    shareExact: boolean,
  ) => {
    await ImageRepository.retain(d.photoIds);
    const created = Date.now(),
      sighting = doc(col('sightings')),
      existing = d.existingCat,
      catRef = existing ? ref(`cats/${existing.id}`) : doc(col('cats')),
      position = LocationPrivacy.fuzz(d.lat, d.lng, privacyLevel, sighting.id),
      hood = Dubai.nearest(d.lat, d.lng).key,
      name = existing?.name ?? d.newCatName.trim();
    const concerning =
      d.needsMedical ||
      d.healthTags.some((t) => !['good_weight', 'healthy_coat', 'pregnant', 'nursing'].includes(t));
    const health = d.healthTags.some((t) => ['wound', 'limping'].includes(t))
      ? 'injured'
      : d.needsMedical
        ? 'needs_attention'
        : concerning
          ? 'minor_issues'
          : d.healthTags.length
            ? 'healthy'
            : 'unknown';
    const tnr = existing?.tnrVerified
      ? existing.tnrStatus
      : d.earTip === 'yes'
        ? 'ear_tipped'
        : d.possiblyNotNeutered
          ? 'likely_not_neutered'
          : (existing?.tnrStatus ?? 'unknown');
    const b = writeBatch(database());
    if (!existing)
      b.set(
        catRef,
        CatToDoc({
          ...CatDefaults,
          name,
          description: d.description.trim(),
          distinctiveMarks: d.distinctiveMarks.trim(),
          coatColor: d.coatColor,
          pattern: d.pattern,
          size: d.size,
          sex: d.sex,
          breedHints: d.breedHints,
          photoIds: d.photoIds,
          coverPhotoId: d.photoIds[0] ?? '',
          createdBy: me.uid,
          createdByName: me.username,
          contributors: [me.uid],
          followers: [me.uid],
          firstSeen: d.seenAt,
          lastSeen: d.seenAt,
          sightingCount: 1,
          counterSourceId: sighting.id,
          ...position,
          geoCells: Geohash.cells(position.lat, position.lng),
          searchTerms: SearchTerms.of(SearchTerms.words(name)),
          neighborhood: hood,
          neighborhoods: [hood],
          tnrStatus: tnr,
          healthStatus: health,
          features: d.features,
          createdAt: created,
          updatedAt: created,
        }),
      );
    else {
      const changes: Record<string, unknown> = {
        sightingCount: increment(1),
        counterSourceId: sighting.id,
        contributors: arrayUnion(me.uid),
        neighborhoods: arrayUnion(hood),
        updatedAt: created,
        tnrStatus: tnr,
      };
      if (d.healthTags.length || d.needsMedical) changes.healthStatus = health;
      if (d.photoIds.length) {
        changes.photoIds = arrayUnion(...d.photoIds);
        if (!existing.coverPhotoId) changes.coverPhotoId = d.photoIds[0];
      }
      if (d.seenAt >= existing.lastSeen)
        Object.assign(changes, {
          lastSeen: d.seenAt,
          ...position,
          geoCells: Geohash.cells(position.lat, position.lng),
          neighborhood: hood,
        });
      if (d.features.length) {
        const n = Math.max(1, Math.min(9, existing.sightingCount));
        changes.features =
          existing.features.length === d.features.length
            ? d.features.map(
                (v, i) => Math.round(((existing.features[i] * n + v) / (n + 1)) * 10000) / 10000,
              )
            : d.features;
      }
      b.update(catRef, changes);
    }
    b.set(
      sighting,
      SightingToDoc({
        ...SightingDefaults,
        catId: catRef.id,
        catName: name,
        userId: me.uid,
        username: me.username,
        photoIds: d.photoIds,
        seenAt: d.seenAt,
        ...position,
        neighborhood: hood,
        locationSource: d.locationSource,
        coatColor: d.coatColor,
        pattern: d.pattern,
        size: d.size,
        healthTags: d.healthTags,
        behaviorTags: d.behaviorTags,
        earTipped: d.earTip,
        possiblyNotNeutered: d.possiblyNotNeutered,
        needsMedical: d.needsMedical,
        notes: d.notes.trim(),
        newCat: !existing,
        matchScore: d.matchScore,
        aiSummary: d.aiSummary,
        suggestedCatIds: d.suggestedCatIds.slice(0, 6),
        rejectedCatIds: d.rejectedCatIds.slice(0, 6),
        createdAt: created,
        updatedAt: created,
      }),
    );
    if (shareExact)
      b.set(
        ref(`privateLocations/${sighting.id}`),
        PrivateLocationToDoc({
          ...PrivateLocationDefaults,
          ownerId: me.uid,
          lat: d.lat,
          lng: d.lng,
          accuracy: d.accuracy,
        }),
      );
    const hour = Number(
      new Intl.DateTimeFormat('en', {
        hour: 'numeric',
        hourCycle: 'h23',
        timeZone: 'Asia/Dubai',
      }).format(d.seenAt),
    );
    users.addStats(
      b,
      me.uid,
      {
        sightings: 1,
        photos: d.photoIds.length,
        catsRegistered: existing ? 0 : 1,
        tnrReports: d.earTip === 'yes' || d.possiblyNotNeutered ? 1 : 0,
        nightSightings: hour >= 21 || hour < 4 ? 1 : 0,
        souqSightings: Dubai.souqAreas.includes(hood) ? 1 : 0,
        marinaSightings: Dubai.marinaAreas.includes(hood) ? 1 : 0,
      },
      10 + 2 * d.photoIds.length + (existing ? 0 : 15),
      hood,
      true,
    );
    if (!existing) b.update(ref(`users/${me.uid}`), { followedCats: arrayUnion(catRef.id) });
    await b.commit();
    if (existing)
      await notifications.send(
        existing.followers.filter((uid) => uid !== me.uid),
        'followed_cat',
        { cat: name, user: me.username, area: hood },
        `cat/${catRef.id}`,
        undefined,
        sighting.id,
      );
    return catRef.id;
  },
  updateCat: async (id: string, changes: Record<string, unknown>) => {
    if (typeof changes.name === 'string' || Array.isArray(changes.aliases)) {
      const cat = await CatRepository.cat(id);
      if (cat)
        changes.searchTerms = SearchTerms.of(
          SearchTerms.words(
            typeof changes.name === 'string' ? changes.name : cat.name,
            ...(Array.isArray(changes.aliases) ? (changes.aliases as string[]) : cat.aliases),
          ),
        );
    }
    if (typeof changes.lat === 'number' && typeof changes.lng === 'number')
      changes.geoCells = Geohash.cells(changes.lat, changes.lng);
    await updateDoc(ref(`cats/${id}`), { ...changes, updatedAt: Date.now() });
  },
  addAlias: async (id: string, alias: string) => {
    const cat = await CatRepository.cat(id);
    if (cat)
      await CatRepository.updateCat(id, { aliases: [...new Set([...cat.aliases, alias.trim()])] });
  },
  setCoverPhoto: (id: string, photoId: string) =>
    CatRepository.updateCat(id, { coverPhotoId: photoId }),
  updateSighting: (s: Sighting, changes: Record<string, unknown>) =>
    updateDoc(ref(`sightings/${s.id}`), { ...changes, updatedAt: Date.now() }),
  updateSightingLocation: async (
    s: Sighting,
    lat: number,
    lng: number,
    privacyLevel: string,
    shareExact: boolean,
  ) => {
    const b = writeBatch(database());
    b.update(ref(`sightings/${s.id}`), {
      ...LocationPrivacy.fuzz(lat, lng, privacyLevel, s.id),
      neighborhood: Dubai.nearest(lat, lng).key,
      locationSource: 'manual',
      updatedAt: Date.now(),
    });
    if (shareExact)
      b.set(
        ref(`privateLocations/${s.id}`),
        PrivateLocationToDoc({ ...PrivateLocationDefaults, ownerId: s.userId, lat, lng }),
      );
    else b.delete(ref(`privateLocations/${s.id}`));
    await b.commit();
  },
  deleteSighting: async (s: Sighting) => {
    const b = writeBatch(database());
    b.delete(ref(`sightings/${s.id}`));
    b.delete(ref(`privateLocations/${s.id}`));
    b.update(ref(`cats/${s.catId}`), {
      sightingCount: increment(-1),
      counterSourceId: s.id,
      updatedAt: Date.now(),
    });
    users.addStats(b, s.userId, { sightings: -1 }, -10);
    await b.commit();
  },
  medicalRecords: (catId: string) => listQuery('medicalRecords', where('catId', '==', catId)),
  addMedicalRecord: async (cat: Cat, type: string, note: string, by: UserProfile) => {
    const record = doc(col('medicalRecords'));
    const b = writeBatch(database());
    b.set(
      record,
      MedicalRecordToDoc({
        ...MedicalRecordDefaults,
        catId: cat.id,
        type,
        note: note.trim(),
        byUid: by.uid,
        byName: by.username,
        at: Date.now(),
      }),
    );
    if (type === 'tnr_confirmed')
      b.update(ref(`cats/${cat.id}`), {
        tnrStatus: 'neutered_verified',
        tnrVerified: true,
        updatedAt: Date.now(),
      });
    await b.commit();
    await notifications.send(
      cat.followers.filter((uid) => uid !== by.uid),
      'followed_cat',
      { cat: cat.name, user: by.username, area: 'medical' },
      `cat/${cat.id}`,
      undefined,
      record.id,
    );
  },
  mergeRequests: () => listQuery('mergeRequests', where('status', '==', 'pending')),
  suggestMerge: async (
    primary: Cat,
    duplicate: Cat,
    reason: string,
    similarity: number,
    me: UserProfile,
  ) => {
    const b = writeBatch(database());
    b.set(
      doc(col('mergeRequests')),
      MergeRequestToDoc({
        ...MergeRequestDefaults,
        primaryCatId: primary.id,
        primaryName: primary.name,
        duplicateCatId: duplicate.id,
        duplicateName: duplicate.name,
        requestedBy: me.uid,
        requestedByName: me.username,
        reason: reason.trim(),
        similarity,
        createdAt: Date.now(),
      }),
    );
    users.addStats(b, me.uid, { mergeSuggestions: 1 }, 5, undefined, true);
    await b.commit();
  },
  rejectMerge: async (r: MergeRequest, me: UserProfile, note: string) => {
    await updateDoc(ref(`mergeRequests/${r.id}`), {
      status: 'rejected',
      reviewedBy: me.uid,
      reviewedByName: me.username,
      reviewNote: note,
      reviewedAt: Date.now(),
    });
    await moderation.log(
      me,
      'merge_rejected',
      'cat',
      r.primaryCatId,
      `${r.duplicateName} â†’ ${r.primaryName}`,
      note,
    );
    await notifications.send(
      [r.requestedBy],
      'moderation',
      { target: 'merge', label: `${r.duplicateName} â†’ ${r.primaryName}`, decision: 'rejected' },
      `cat/${r.primaryCatId}`,
    );
  },
  approveMerge: async (r: MergeRequest, me: UserProfile, note: string) => {
    const primary = await CatRepository.cat(r.primaryCatId),
      duplicate = await CatRepository.cat(r.duplicateCatId);
    if (!primary || !duplicate) throw new Error('Missing cat');
    for (const path of ['sightings', 'medicalRecords']) {
      const docs = await list(listQuery(path, where('catId', '==', duplicate.id)), (id, d) => ({
        id,
        ...d,
      }));
      for (let i = 0; i < docs.length; i += 400) {
        const b = writeBatch(database());
        docs
          .slice(i, i + 400)
          .forEach((d) =>
            b.update(
              ref(`${path}/${d.id}`),
              path === 'sightings'
                ? { catId: primary.id, catName: primary.name, newCat: false }
                : { catId: primary.id },
            ),
          );
        await b.commit();
      }
    }
    const b = writeBatch(database());
    const changes: Record<string, unknown> = {
      sightingCount: increment(duplicate.sightingCount),
      firstSeen: Math.min(...[primary.firstSeen, duplicate.firstSeen].filter((v) => v > 0)),
      updatedAt: Date.now(),
    };
    for (const field of ['photoIds', 'contributors', 'followers', 'neighborhoods'] as const)
      if (duplicate[field].length) changes[field] = arrayUnion(...duplicate[field]);
    const aliases = [duplicate.name, ...duplicate.aliases].filter((n) => n && n !== primary.name);
    if (aliases.length) changes.aliases = arrayUnion(...aliases);
    changes.searchTerms = SearchTerms.of(
      SearchTerms.words(primary.name, ...new Set([...primary.aliases, ...aliases])),
    );
    if (duplicate.lastSeen > primary.lastSeen)
      Object.assign(changes, {
        lastSeen: duplicate.lastSeen,
        lat: duplicate.lat,
        lng: duplicate.lng,
        geoCells: Geohash.cells(duplicate.lat, duplicate.lng),
        neighborhood: duplicate.neighborhood,
      });
    b.update(ref(`cats/${primary.id}`), changes);
    b.update(ref(`cats/${duplicate.id}`), {
      mergedInto: primary.id,
      hidden: true,
      updatedAt: Date.now(),
    });
    b.update(ref(`mergeRequests/${r.id}`), {
      status: 'approved',
      reviewedBy: me.uid,
      reviewedByName: me.username,
      reviewNote: note,
      reviewedAt: Date.now(),
    });
    await b.commit();
    for (const uid of duplicate.followers) {
      const p = await users.profile(uid);
      if (p)
        await updateDoc(ref(`users/${uid}`), {
          followedCats: [
            ...new Set([...p.followedCats.filter((id) => id !== duplicate.id), primary.id]),
          ],
        });
    }
    await moderation.log(
      me,
      'merge_approved',
      'cat',
      primary.id,
      `${duplicate.name} â†’ ${primary.name}`,
      note,
    );
    await notifications.send(
      [r.requestedBy],
      'moderation',
      { target: 'merge', label: `${duplicate.name} â†’ ${primary.name}`, decision: 'approved' },
      `cat/${primary.id}`,
    );
  },
  reviewSighting: async (s: Sighting, approve: boolean, me: UserProfile) => {
    const b = writeBatch(database());
    b.update(ref(`sightings/${s.id}`), {
      status: approve ? 'approved' : 'rejected',
      updatedAt: Date.now(),
    });
    if (approve) users.addStats(b, s.userId, { approvedSightings: 1 }, 5);
    await b.commit();
    await moderation.log(
      me,
      approve ? 'sighting_approved' : 'sighting_rejected',
      'sighting',
      s.id,
      s.catName,
      '',
    );
    await notifications.send(
      [s.userId],
      'moderation',
      { target: 'sighting', label: s.catName, decision: approve ? 'approved' : 'rejected' },
      `sighting/${s.id}`,
    );
  },
  setCatHidden: async (cat: Cat, hidden: boolean, me: UserProfile, note = '') => {
    await CatRepository.updateCat(cat.id, { hidden });
    await moderation.log(me, hidden ? 'cat_hidden' : 'cat_restored', 'cat', cat.id, cat.name, note);
    if (hidden)
      await notifications.send([cat.createdBy], 'moderation', {
        target: 'cat',
        label: cat.name,
        decision: 'removed',
      });
  },
};
