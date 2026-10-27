import {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  writeBatch,
  type QueryConstraint,
  type DocumentData,
  type Query,
} from 'firebase/firestore';
import { auth, database } from '../firebase';
import { t } from '../i18n';
export const col = (path: string) => collection(database(), path);
export const ref = (path: string) => doc(database(), path);
export const listQuery = (path: string, ...constraints: QueryConstraint[]) =>
  query(col(path), ...constraints);
export async function read<T>(
  path: string,
  mapper: (id: string, data: DocumentData) => T,
): Promise<T | null> {
  const snap = await getDoc(ref(path));
  return snap.exists() ? mapper(snap.id, snap.data()) : null;
}
export async function list<T>(q: Query, mapper: (id: string, data: DocumentData) => T) {
  return (await getDocs(q)).docs.map((s) => mapper(s.id, s.data()));
}
let listenerError = '';
let retryVersion = 0;
const errors = new Set<() => void>();
export function errorMessage(error: unknown) {
  const code = (error as { code?: string })?.code ?? '';
  const keys: Record<string, string> = {
    'auth/weak-password': 'err_weak_password',
    'auth/email-already-in-use': 'err_email_in_use',
    'auth/invalid-credential': 'err_wrong_credentials',
    'auth/user-not-found': 'err_wrong_credentials',
    'auth/wrong-password': 'err_wrong_credentials',
    'auth/requires-recent-login': 'err_recent_login',
    'auth/network-request-failed': 'err_network',
    'auth/too-many-requests': 'err_too_many_requests',
    'permission-denied': 'err_permission',
    unavailable: 'err_network',
    'resource-exhausted': 'err_too_many_requests',
  };
  return t(keys[code] ?? (error instanceof AppException ? error.key : 'err_generic'));
}
export class AppException extends Error {
  constructor(public key: string) {
    super(key);
  }
}
export const AppErrors = {
  get: () => listenerError,
  version: () => retryVersion,
  subscribe: (fn: () => void) => {
    errors.add(fn);
    return () => {
      errors.delete(fn);
    };
  },
  report: (e: unknown) => {
    if (!auth?.currentUser) return;
    listenerError = errorMessage(e);
    errors.forEach((fn) => fn());
  },
  retry: () => {
    listenerError = '';
    retryVersion++;
    errors.forEach((fn) => fn());
  },
};
export async function deleteDocuments(paths: string[]) {
  for (let i = 0; i < paths.length; i += 400) {
    const batch = writeBatch(database());
    paths.slice(i, i + 400).forEach((path) => batch.delete(ref(path)));
    await batch.commit();
  }
}
