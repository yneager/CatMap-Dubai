import { initializeApp, getApps } from 'firebase/app';
import {
  initializeAuth,
  getAuth,
  indexedDBLocalPersistence,
  connectAuthEmulator,
} from 'firebase/auth';
import {
  initializeFirestore,
  getFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  connectFirestoreEmulator,
} from 'firebase/firestore';
import { config } from './config';
import type { FirebaseApp } from 'firebase/app';
import type { Auth } from 'firebase/auth';
import type { Firestore } from 'firebase/firestore';
interface Clients {
  app: FirebaseApp | null;
  auth: Auth | null;
  db: Firestore | null;
  storageWired: boolean;
}
const clientsKey = Symbol.for('catmap.firebase.clients');
const clientRegistry = globalThis as unknown as Record<symbol, Clients | undefined>;
const shared = clientRegistry[clientsKey];
const real = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
};
const emulatorProject = import.meta.env.VITE_EMULATOR_PROJECT_ID || 'demo-catmap';
export const configured =
  config.emulators || !!(real.apiKey && real.authDomain && real.projectId && real.appId);
// Missing setup creates no Firebase client and therefore cannot contact production.
// Vite's hot updates and browser test imports can evaluate this module twice.
// Reuse initialized clients rather than reconnecting their emulator transports.
const existingApp = configured ? getApps().find((app) => app.name === '[DEFAULT]') : undefined;
const app = configured
  ? (shared?.app ??
    existingApp ??
    initializeApp(
      config.emulators
        ? {
            apiKey: 'demo-key',
            projectId: emulatorProject,
            appId: 'demo-app',
            authDomain: emulatorProject + '.firebaseapp.com',
            storageBucket: emulatorProject + '.appspot.com',
          }
        : real,
    ))
  : null;
export const auth = app
  ? (shared?.auth ??
    (existingApp ? getAuth(app) : initializeAuth(app, { persistence: indexedDBLocalPersistence })))
  : null;
export const db = app
  ? (shared?.db ??
    (existingApp
      ? getFirestore(app)
      : initializeFirestore(app, {
          localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
        })))
  : null;
if (config.emulators && auth && db && !shared && !existingApp) {
  connectAuthEmulator(
    auth,
    'http://127.0.0.1:' + (import.meta.env.VITE_AUTH_EMULATOR_PORT || 9099),
    { disableWarnings: true },
  );
  connectFirestoreEmulator(
    db,
    '127.0.0.1',
    Number(import.meta.env.VITE_FIRESTORE_EMULATOR_PORT || 8080),
  );
}
const clients: Clients = shared ?? { app, auth, db, storageWired: false };
clientRegistry[clientsKey] = clients;
export async function videoStorage() {
  if (!app || !config.blaze) throw new Error('Videos are disabled');
  const { getStorage, connectStorageEmulator } = await import('firebase/storage');
  const storage = getStorage(app);
  if (config.emulators && !clients.storageWired) {
    connectStorageEmulator(
      storage,
      '127.0.0.1',
      Number(import.meta.env.VITE_STORAGE_EMULATOR_PORT || 9199),
    );
    clients.storageWired = true;
  }
  return storage;
}
export function database() {
  if (!db) throw new Error('Firebase setup required');
  return db;
}
export function authentication() {
  if (!auth) throw new Error('Firebase setup required');
  return auth;
}
