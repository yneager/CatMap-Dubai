import { terminate, clearIndexedDbPersistence } from 'firebase/firestore';
import { database } from '../firebase';
import { ImageRepository } from './ImageRepository';
import { clearDeviceData } from './DeviceWipe';
import { LocationRepository } from './LocationRepository';
import { MapRepository } from './MapRepository';
import { blockAccountWrites } from '../util/idb';

export async function wipeAndReload() {
  // Persist intent first: a reload/crash must never skip a partially completed wipe.
  localStorage.setItem('catmap_pending_wipe', '1');
  blockAccountWrites();
  await MapRepository.clear();
  await terminate(database());
  // Other tabs receive the storage event and release their persistence lease.
  for (let attempt = 0; ; attempt++) {
    try {
      await clearIndexedDbPersistence(database());
      break;
    } catch (error) {
      if ((error as { code?: string }).code !== 'failed-precondition' || attempt >= 3) throw error;
      await new Promise((resolve) => setTimeout(resolve, 250 * (attempt + 1)));
    }
  }
  await ImageRepository.clearCache();
  await clearDeviceData();
  LocationRepository.set(null);
  localStorage.removeItem('catmap_pending_wipe');
  location.replace('/login');
}
