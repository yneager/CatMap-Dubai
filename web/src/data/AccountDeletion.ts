import { EmailAuthProvider, reauthenticateWithCredential, deleteUser } from 'firebase/auth';
import {
  where,
  getDoc,
  getDocs,
  deleteDoc,
  runTransaction,
  updateDoc,
  arrayRemove,
  setDoc,
  serverTimestamp,
} from 'firebase/firestore';
import { authentication, database } from '../firebase';
import { ref, listQuery, deleteDocuments } from './Firestore';
import { pages } from './UserDataExport';
import { CommunityAccountCleanup } from './CommunityAccountCleanup';
import { UserRepository } from './UserRepository';
import { ImageRepository } from './ImageRepository';
import { SettingsRepository } from './SettingsRepository';
import { DemoVault } from './DemoAccounts';
import { PostDraftStore } from './PostDraftStore';
import { signOut } from 'firebase/auth';
import { wipeAndReload } from './LocalWipe';
export async function deleteAccount(password: string) {
  const user = authentication().currentUser;
  if (!user) return;
  await reauthenticateWithCredential(
    user,
    EmailAuthProvider.credential(user.email ?? '', password),
  );
  const uid = user.uid,
    profile = await UserRepository.profile(uid);
  await setDoc(ref(`accountDeletions/${uid}`), { requestedAt: serverTimestamp() }, { merge: true });
  const jobRef = ref(`aiJobs/${uid}`),
    job = await getDoc(jobRef);
  if (job.exists()) {
    if (!['done', 'error'].includes(job.data().status)) {
      const age = Date.now() - (job.data().createdAt?.toMillis?.() ?? Date.now());
      await new Promise((resolve) => setTimeout(resolve, Math.max(0, 15000 - age)));
    }
    await deleteDoc(jobRef);
  }
  await CommunityAccountCleanup.clean(uid);
  for (const [path, parentPath, field, aggregate] of [
    ['sightings', 'cats', 'catId', 'sightingCount'],
    ['checkins', 'stations', 'stationId', 'checkinCount'],
  ]) {
    const sources = await getDocs(listQuery(path, where('userId', '==', uid)));
    for (const d of sources.docs)
      await runTransaction(database(), async (tx) => {
        const fresh = await tx.get(d.ref);
        if (!fresh.exists()) return;
        const parentId = String(fresh.data()[field] ?? ''),
          parent = parentId ? ref(`${parentPath}/${parentId}`) : null,
          snap = parent ? await tx.get(parent) : null;
        tx.delete(d.ref);
        if (path === 'sightings') tx.delete(ref(`privateLocations/${d.id}`));
        if (parent && snap?.exists()) {
          const changes: Record<string, unknown> = {
            [aggregate]: Math.max(0, (snap.data()[aggregate] ?? 0) - 1),
            counterSourceId: d.id,
          };
          if (path === 'checkins' && snap.data().lastCheckinAt === fresh.data().at)
            Object.assign(changes, { lastCheckinAt: 0, lastCheckinBy: '' });
          tx.update(parent, changes);
        }
      });
  }
  const photos: string[] = [];
  for await (const page of pages(listQuery('images', where('ownerId', '==', uid))))
    photos.push(...page.map((d) => d.ref.path));
  const ids = photos.map((path) => path.split('/')[1]).filter((id) => !id.endsWith('_t'));
  for (let i = 0; i < ids.length; i += 3) {
    const chunk = ids.slice(i, i + 3),
      cats = await getDocs(listQuery('cats', where('photoIds', 'array-contains-any', chunk)));
    for (const d of cats.docs)
      await runTransaction(database(), async (tx) => {
        const fresh = await tx.get(d.ref);
        if (!fresh.exists()) return;
        const remaining = (fresh.data().photoIds ?? []).filter((id: string) => !chunk.includes(id));
        tx.update(d.ref, {
          photoIds: remaining,
          removedPhotoIds: chunk.filter((id) => (fresh.data().photoIds ?? []).includes(id)),
          ...(chunk.includes(fresh.data().coverPhotoId)
            ? { coverPhotoId: remaining[0] ?? '' }
            : {}),
        });
      });
  }
  for (const field of ['followers', 'contributors'])
    for (const d of (await getDocs(listQuery('cats', where(field, 'array-contains', uid)))).docs)
      await updateDoc(d.ref, { [field]: arrayRemove(uid) });
  const refs = [
    ...photos,
    `volunteerApplications/${uid}`,
    `users/${uid}/private/areaFollow`,
    `areaFollows/${uid}`,
  ];
  for (const [path, owner] of [
    ['privateLocations', 'ownerId'],
    ['rescues', 'reporterId'],
    ['maintenance', 'userId'],
    ['contentReports', 'reporterId'],
    ['mergeRequests', 'requestedBy'],
  ])
    refs.push(
      ...(await getDocs(listQuery(path, where(owner, '==', uid)))).docs.map((d) => d.ref.path),
    );
  refs.push(
    ...(await getDocs(listQuery(`users/${uid}/notifications`))).docs.map((d) => d.ref.path),
  );
  for (const lease of (await getDocs(listQuery('videoUploads', where('ownerId', '==', uid))))
    .docs) {
    await (
      await import('./VideoRepository')
    ).VideoRepository.deleteRequired(lease.data().path ?? '');
    refs.push(lease.ref.path);
  }
  refs.push(
    ...(await getDocs(listQuery('mediaUploads', where('ownerId', '==', uid)))).docs.map(
      (d) => d.ref.path,
    ),
  );
  await deleteDocuments(refs);
  await deleteDocuments([
    `userLimits/${uid}`,
    `users/${uid}/private/account`,
    ...(profile?.username ? [`usernames/${profile.username.toLowerCase()}`] : []),
    `users/${uid}`,
  ]);
  localStorage.setItem('catmap_pending_wipe', '1');
  await deleteUser(user);
  await Promise.allSettled([
    SettingsRepository.clearDeliveredNotifications(uid),
    DemoVault.clear(),
    PostDraftStore.clear(uid),
    ImageRepository.clearCache(),
  ]);
  await signOut(authentication());
  await wipeAndReload();
}
