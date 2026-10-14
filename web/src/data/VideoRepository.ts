import { videoStorage } from '../firebase';
import { config } from '../config';
import { AppException } from './Firestore';
export const VideoRepository = {
  enabled: config.blaze,
  upload: async (uid: string, file: File) => {
    if (!config.blaze) throw new AppException('err_video_unavailable');
    if (file.size > 30 * 1024 * 1024) throw new AppException('err_video_too_big');
    if (!file.type.startsWith('video/')) throw new AppException('err_video_type');
    const { ref, uploadBytes } = await import('firebase/storage');
    const path = `videos/${uid}/${crypto.randomUUID()}`;
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
      await deleteObject(ref(await videoStorage(), path)).catch(() => {});
    }
  },
};
