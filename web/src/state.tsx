import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { onIdTokenChanged, type User } from 'firebase/auth';
import {
  onSnapshot,
  type Query,
  type DocumentReference,
  type DocumentData,
} from 'firebase/firestore';
import { auth } from './firebase';
import { AppErrors, errorMessage } from './data/Firestore';
import { SettingsRepository } from './data/SettingsRepository';
import { LocationRepository } from './data/LocationRepository';
import {
  UserProfileFromDoc,
  AppNotificationFromDoc,
  FavoriteFromDoc,
  ChatFromDoc,
  type Chat,
  type UserProfile,
  type AppNotification,
} from './data/model';
import { UserRepository } from './data/UserRepository';
import { PostRepository } from './data/PostRepository';
import { NotificationRepository } from './data/NotificationRepository';
import { FavoritesRepository } from './data/FavoritesRepository';
import { ChatRepository } from './data/ChatRepository';
import { t, setLanguage } from './i18n';
import { QuietHours } from './notifications/QuietHours';
import { NotificationText, NotificationDelivery } from './notifications/NotificationDelivery';
export function useSettings() {
  return useSyncExternalStore(SettingsRepository.subscribe, SettingsRepository.get);
}
export function useDeviceLocation() {
  return useSyncExternalStore(LocationRepository.subscribe, LocationRepository.get);
}
export function useLive<T>(
  factory: () => Query | DocumentReference | null,
  mapper: (id: string, data: DocumentData) => T,
  key: string,
  unavailableOnDenied = false,
) {
  const latest = useRef({ factory, mapper });
  latest.current = { factory, mapper };
  const retry = useSyncExternalStore(AppErrors.subscribe, AppErrors.version);
  const [result, setResult] = useState<{ key: string; value: T[] | T | null | undefined }>({
    key,
    value: undefined,
  });
  useEffect(() => {
    const q = latest.current.factory(),
      subscribedUid = auth?.currentUser?.uid;
    if (!q) return;
    return onSnapshot(
      q as Query,
      (snap) => {
        const m = latest.current.mapper;
        let value: T[] | T | null;
        if ('docs' in snap) value = snap.docs.map((d) => m(d.id, d.data()));
        else {
          const d = snap as unknown as import('firebase/firestore').DocumentSnapshot;
          value = d.exists() ? m(d.id, d.data()) : null;
        }
        setResult({ key, value });
      },
      (error) => {
        if (unavailableOnDenied && error.code === 'permission-denied')
          setResult({ key, value: null });
        else if (auth?.currentUser?.uid === subscribedUid) AppErrors.report(error);
      },
    );
  }, [key, retry, unavailableOnDenied]);
  return result.key === key ? result.value : undefined;
}
export function useList<T>(
  factory: () => Query | null,
  mapper: (id: string, data: DocumentData) => T,
  key: string,
): T[] | undefined {
  return useLive(factory, mapper, key) as T[] | undefined;
}
export function useDoc<T>(
  factory: () => DocumentReference | null,
  mapper: (id: string, data: DocumentData) => T,
  key: string,
  unavailableOnDenied = false,
): T | null | undefined {
  return useLive(factory, mapper, key, unavailableOnDenied) as T | null | undefined;
}
interface State {
  user: User | null;
  verified: boolean;
  profile: UserProfile | null | undefined;
  ready: boolean;
  inbox: AppNotification[];
  favoriteIds: string[];
  chats: Chat[] | undefined;
  unreadMessages: number;
  toast: (text: string) => void;
}
const Context = createContext<State>({
  user: null,
  verified: false,
  profile: null,
  ready: false,
  inbox: [],
  favoriteIds: [],
  chats: undefined,
  unreadMessages: 0,
  toast: () => {},
});
export const useApp = () => useContext(Context);
let toastSink: (text: string) => void = () => {};
export const toast = (text: string) => toastSink(text);
export function useAction() {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const running = useRef(false);
  return {
    busy,
    error,
    run: async (action: () => Promise<unknown>, success?: string) => {
      if (running.current) return false;
      running.current = true;
      setBusy(true);
      setError('');
      try {
        await action();
        if (success) toast(success);
        return true;
      } catch (e) {
        const message = errorMessage(e);
        setError(message);
        toast(message);
        return false;
      } finally {
        running.current = false;
        setBusy(false);
      }
    },
  };
}
export function AppProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState({
    user: null as User | null,
    verified: false,
    ready: !auth,
  });
  const [message, setMessage] = useState('');
  const settings = useSettings();
  const uid = session.user?.uid ?? '';
  const profile = useDoc(
    () => (uid ? UserRepository.profileFlow(uid) : null),
    UserProfileFromDoc,
    'profile-' + uid,
  );
  const inbox = useList(
    () => (uid ? NotificationRepository.notifications(uid) : null),
    AppNotificationFromDoc,
    'inbox-' + uid,
  );
  const chats = useList(
    () => (uid ? ChatRepository.chatsFor(uid) : null),
    ChatFromDoc,
    'shared-chats-' + uid,
  );
  const favorites = useList(
    () => (uid ? FavoritesRepository.favorites(uid) : null),
    FavoriteFromDoc,
    'favorites-' + uid,
  );
  useEffect(() => {
    void SettingsRepository.load().catch(() => {});
    if (!auth) return;
    return onIdTokenChanged(auth, (user) =>
      setSession({ user, verified: user?.emailVerified ?? false, ready: true }),
    );
  }, []);
  useEffect(() => {
    setLanguage(settings.language || navigator.language);
    const media = matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      document.documentElement.dataset.theme =
        settings.theme === 'system' ? (media.matches ? 'dark' : 'light') : settings.theme;
      document.documentElement.style.setProperty('--text-scale', settings.largeText ? '1.18' : '1');
      document
        .querySelector('meta[name="theme-color"]')
        ?.setAttribute(
          'content',
          document.documentElement.dataset.theme === 'dark' ? '#1A1410' : '#A9531A',
        );
    };
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [settings.theme, settings.largeText, settings.language]);
  useEffect(() => {
    toastSink = setMessage;
    return () => {
      toastSink = () => {};
    };
  }, []);
  useEffect(() => {
    if (!message) return;
    const timeout = setTimeout(() => setMessage(''), 5000);
    return () => clearTimeout(timeout);
  }, [message]);
  useEffect(() => {
    if (uid && session.verified) {
      void PostRepository.remindStalePosts(uid).catch(AppErrors.report);
      void PostRepository.moveAreaCentreToPrivate(uid).catch(AppErrors.report);
    }
  }, [uid, session.verified]);
  useEffect(() => {
    if (profile && session.verified)
      void UserRepository.awardNewBadges(profile).catch(AppErrors.report);
  }, [profile, session.verified]);
  const delivery = useRef(Promise.resolve());
  useEffect(() => {
    if (!uid || !inbox) return;
    delivery.current = delivery.current
      .catch(() => {})
      .then(async () => {
        const before = await SettingsRepository.deliveredNotifications(uid);
        if (auth?.currentUser?.uid !== uid) return;
        const now = new Date(),
          s = SettingsRepository.get(),
          quiet =
            s.quietHoursEnabled &&
            QuietHours.contains(now.getHours() * 60 + now.getMinutes(), s.quietStart, s.quietEnd);
        for (const n of NotificationDelivery.fresh(inbox, before)) {
          const category = NotificationText.category(n.type);
          if (!quiet && s.notificationsEnabled && category && s.categories[category] !== false)
            setMessage(NotificationText.format(n).join(' · '));
        }
        await SettingsRepository.setDeliveredNotifications(
          uid,
          NotificationDelivery.checkpoint(inbox, before),
        );
      });
  }, [inbox, uid]);
  return (
    <Context.Provider
      value={{
        ...session,
        profile: uid ? profile : null,
        inbox: inbox ?? [],
        favoriteIds: favorites?.map((f) => f.id) ?? [],
        chats,
        unreadMessages: chats?.reduce((sum, c) => sum + (c.unread[uid] ?? 0), 0) ?? 0,
        toast: setMessage,
      }}
    >
      {children}
      {message && (
        <div className="snackbar" role="status">
          {message}
          <button aria-label={t('close')} onClick={() => setMessage('')}>
            ×
          </button>
        </div>
      )}
    </Context.Provider>
  );
}
