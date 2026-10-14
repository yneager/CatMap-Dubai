let connection: Promise<IDBDatabase> | undefined;
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
  const db = await open();
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
