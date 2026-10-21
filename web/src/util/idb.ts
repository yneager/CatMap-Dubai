let connection: Promise<IDBDatabase> | undefined;
let accountWritesEnabled = true;
export function blockAccountWrites() {
  accountWritesEnabled = false;
}
function open() {
  return (connection ??= new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open('catmap-device', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('values');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  }));
}
export async function get<T>(key: string): Promise<T | undefined> {
  const db = await open();
  return new Promise((resolve, reject) => {
    const r = db.transaction('values').objectStore('values').get(key);
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}
export async function put(key: string, value: unknown) {
  if (key !== 'settings' && !accountWritesEnabled) return;
  const db = await open();
  if (key !== 'settings' && !accountWritesEnabled) return;
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('values', 'readwrite');
    tx.objectStore('values').put(value, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
export async function remove(key: string) {
  const db = await open();
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('values', 'readwrite');
    tx.objectStore('values').delete(key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
export async function clearAccountData() {
  blockAccountWrites();
  const db = await open();
  return new Promise<void>((resolve, reject) => {
    const tx = db.transaction('values', 'readwrite'),
      store = tx.objectStore('values');
    const r = store.openCursor();
    r.onsuccess = () => {
      const cursor = r.result;
      if (!cursor) return;
      if (cursor.key !== 'settings') cursor.delete();
      cursor.continue();
    };
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
