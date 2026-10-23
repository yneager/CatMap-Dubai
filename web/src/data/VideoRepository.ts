import { videoStorage } from '../firebase';
import { config } from '../config';
import { AppException } from './Firestore';
import { ref as documentRef } from './Firestore';
import { setDoc, runTransaction } from 'firebase/firestore';
import { database } from '../firebase';
export const VideoRepository = {
  enabled: config.blaze,
  upload: async (uid: string, file: File) => {
    if (!config.blaze) throw new AppException('err_video_unavailable');
    if (file.size > 30 * 1024 * 1024) throw new AppException('err_video_too_big');
    if (!file.type.startsWith('video/')) throw new AppException('err_video_type');
    const { ref, uploadBytes } = await import('firebase/storage');
    const path = `videos/${uid}/${crypto.randomUUID()}`;
    await setDoc(documentRef('videoUploads/' + path.split('/').at(-1)), {
      ownerId: uid,
      path,
      status: 'staged',
      expiresAt: Date.now() + 7 * 86400000,
    });
    await uploadBytes(ref(await videoStorage(), path), file, { contentType: file.type });
    return path;
  },
  url: async (path: string) => {
    if (!config.blaze || !path) return null;
    const { ref, getDownloadURL } = await import('firebase/storage');
    return getDownloadURL(ref(await videoStorage(), path)).catch(() => null);
  },
  delete: async (path: string) => {
    if (config.blaze && path) {
      const { ref, deleteObject } = await import('firebase/storage');
      await deleteObject(ref(await videoStorage(), path)).catch((error) =>
        console.info('Video cleanup failed', error),
      );
    }
  },
  deleteRequired: async (path: string) => {
    if (!path) return;
    if (!config.blaze) throw new AppException('err_video_unavailable');
    const { ref, deleteObject } = await import('firebase/storage');
    await deleteObject(ref(await videoStorage(), path)).catch((error: unknown) => {
      if ((error as { code?: string }).code !== 'storage/object-not-found') throw error;
    });
  },
  retain: async (path: string) => {
    if (!path) return;
    await runTransaction(database(), async (tx) => {
      const lease = await tx.get(documentRef('videoUploads/' + path.split('/').at(-1)));
      if (!lease.exists()) return;
      if (!['staged', 'linked'].includes(lease.data().status))
        throw new Error('Staged video expired; select it again');
      tx.update(lease.ref, { expiresAt: Date.now() + 7 * 86400000 });
    });
  },
};
