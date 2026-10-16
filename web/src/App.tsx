import {
  lazy,
  Suspense,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { Navigate, Route, Routes, Link, useLocation, useNavigate } from 'react-router-dom';
import { updates } from './platform/updates';
import { edgeBack } from './platform/appFeel';
import { configured } from './firebase';
import { config } from './config';
import { useApp, useSettings } from './state';
import { AppErrors } from './data/Firestore';
import { SettingsRepository } from './data/SettingsRepository';
import { t } from './i18n';
import { AppTopBar, Loading, EmptyState, ControlIcon, Dialog } from './ui/components/Components';
import { PostChooser } from './ui/posts/PostChooser';
import bundled from './whats-new.json';
const Auth = lazy(() => import('./ui/auth/AuthScreens')),
  Map = lazy(() => import('./ui/map/MapScreen')),
  Pick = lazy(() => import('./ui/map/LocationPickerScreen')),
  Explore = lazy(() => import('./ui/explore/ExploreScreen')),
  Editor = lazy(() => import('./ui/posts/PostEditorScreen')),
  Detail = lazy(() => import('./ui/posts/PostDetailScreen')),
  MatchReview = lazy(() => import('./ui/matches/MatchScreens')),
  Poster = lazy(() => import('./ui/posts/PosterScreen')),
  Lists = lazy(() => import('./ui/posts/PostLists')),
  Messages = lazy(() => import('./ui/messages/MessagesScreens')),
  Adoption = lazy(() => import('./ui/adoption/AdoptionScreens')),
  Sitting = lazy(() => import('./ui/sitting/SittingScreens')),
  Profile = lazy(() => import('./ui/profile/ProfileScreens')),
  Settings = lazy(() => import('./ui/settings/SettingsAndNotifications')),
  Cats = lazy(() => import('./ui/cats/CatScreens')),
  Sightings = lazy(() => import('./ui/sighting/SightingScreens')),
  Rescue = lazy(() => import('./ui/rescue/RescueScreens')),
  Stations = lazy(() => import('./ui/stations/StationScreens')),
  Help = lazy(() => import('./ui/help/HelpDirectoryScreen')),
  Admin = lazy(() => import('./ui/admin/AdminScreens'));
function Gate({
  children,
  admin = false,
  mod = false,
  screen,
}: {
  children: ReactNode;
  admin?: boolean;
  mod?: boolean;
  screen: string;
}) {
  const { user, verified, profile, ready } = useApp(),
    location = useLocation();
  if (!ready) return <Loading />;
  const raw = location.pathname;
  const detail =
    /^\/(post|match|post-edit|poster|cat|cat-edit|cat-merge|photo|sighting|sighting-edit|rescue|station|sitter|book|user|chat|chat-with)\//.test(
      raw,
    );
  if (
    detail &&
    !(
      raw.startsWith('/chat/')
        ? /^\/chat\/[A-Za-z0-9_-]{1,257}$/
        : /^\/[a-z-]+\/[A-Za-z0-9_-]{1,128}$/
    ).test(raw)
  )
    return <EmptyState text={t('post_removed')} />;
  if (!user)
    return (
      <Navigate to="/login" state={{ returnTo: location.pathname + location.search }} replace />
    );
  if (!verified)
    return (
      <Navigate to="/verify" state={{ returnTo: location.pathname + location.search }} replace />
    );
  if (!profile) return <Loading />;
  if (
    (admin && profile.role !== 'admin') ||
    (mod && !['admin', 'moderator'].includes(profile.role))
  )
    return <EmptyState text={t('err_permission')} />;
  return (
    <div className="route-screen" data-screen={screen}>
      {children}
    </div>
  );
}
function NavIcon({ name, active }: { name: string; active: boolean }) {
  const paths: Record<string, string> = {
    map: 'M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3V6m6-3v15m6-12v15',
    explore: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0m-6-3-2 6-4 2 2-6 4-2',
    messages: 'M3 4h18v13H8l-5 4V4',
    profile: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0M4 21v-3a8 8 0 0 1 16 0v3',
  };
  return (
    <svg
      width="24"
      height="24"
      viewBox="0 0 24 24"
      fill={active ? 'currentColor' : 'none'}
      fillOpacity={active ? 0.15 : 0}
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
export default function App() {
  const location = useLocation(),
    navigate = useNavigate(),
    { user, profile, ready, unreadMessages } = useApp(),
    settings = useSettings(),
    [chooser, setChooser] = useState(false),
    [offline, setOffline] = useState(!navigator.onLine),
    [notes, setNotes] = useState(false);
  const needsRefresh = useSyncExternalStore(updates.subscribe, updates.get);
  const appFrame = useRef<HTMLDivElement>(null);
  const previousUid = useRef<string | undefined>(undefined);
  const listenerError = useSyncExternalStore(AppErrors.subscribe, AppErrors.get);
  const tabs = ['map', 'explore', 'messages', 'profile'],
    tab = location.pathname.slice(1),
    isTab = tabs.includes(tab);
  useEffect(() => {
    const online = () => setOffline(!navigator.onLine);
    window.addEventListener('online', online);
    window.addEventListener('offline', online);
    return () => {
      window.removeEventListener('online', online);
      window.removeEventListener('offline', online);
    };
  }, []);
  useEffect(() => {
    if (!ready) return;
    if (user && profile && settings.lastSeenVersion && settings.lastSeenVersion !== config.version)
      setNotes(true);
    if (user && profile && !settings.lastSeenVersion)
      void SettingsRepository.set({ lastSeenVersion: config.version });
  }, [ready, user, profile, settings.lastSeenVersion]);
  useEffect(() => {
    if (!ready) return;
    if (previousUid.current && !user) navigate('/login', { replace: true, state: null });
    else if (previousUid.current && user && previousUid.current !== user.uid)
      navigate('/map', { replace: true });
    previousUid.current = user?.uid;
  }, [ready, user, navigate]);
  useEffect(() => {
    document.title = t('web_app_title');
    appFrame.current?.scrollTo(0, 0);
  }, [location.pathname, settings.language]);
  useEffect(() => {
    if (isTab || !user || !appFrame.current) return;
    return edgeBack(appFrame.current, () => navigate(-1));
  }, [isTab, user, navigate, location.pathname]);
  if (!configured)
    return (
      <div className="app">
        <div className="auth-column">
          <img className="setup-logo" src="/icon-192.png" alt="" />
          <h1>{t('setup_title')}</h1>
          <p>{t('setup_body')}</p>
          <p>{t('web_setup_instructions')}</p>
        </div>
      </div>
    );
  const gate = (element: ReactNode, admin = false, mod = false) => (
    <Gate admin={admin} mod={mod} screen={location.pathname}>
      {element}
    </Gate>
  );
  return (
    <div
      ref={appFrame}
      className={'app ' + (isTab ? 'with-nav' : '')}
      data-route={location.pathname + location.search}
    >
      {config.emulators && <span className="emulator-chip">{t('web_emulator')}</span>}
      {offline && user && (
        <div className="offline-banner" role="status">
          {t('offline_banner')}
        </div>
      )}
      {profile?.suspended && <div className="error-banner">{t('suspended')}</div>}
      {listenerError && (
        <div className="error-banner" role="alert">
          {listenerError}
          <button className="text-button" onClick={AppErrors.retry}>
            {t('try_again')}
          </button>
        </div>
      )}
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route path="/" element={<Navigate to="/map" replace />} />
          {['login', 'register', 'forgot', 'verify'].map((path) => (
            <Route
              key={path}
              path={'/' + path}
              element={
                <div className="route-screen" data-screen={'/' + path}>
                  <Auth />
                </div>
              }
            />
          ))}
          <Route
            path="/help-directory"
            element={
              <div className="route-screen" data-screen="/help-directory">
                <Help />
              </div>
            }
          />
          <Route path="/map" element={gate(<Map />)} />
          <Route path="/pick-location" element={gate(<Pick />)} />
          {['explore', 'search'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Explore />)} />
          ))}
          <Route path="/post/:id" element={gate(<Detail />)} />
          <Route path="/match/:id" element={gate(<MatchReview />)} />
          <Route path="/post-new" element={gate(<Editor key="new" />)} />
          <Route path="/post-edit/:id" element={gate(<Editor key="edit" />)} />
          <Route path="/poster/:id" element={gate(<Poster />)} />
          {['my-posts', 'favorites', 'area-alerts'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Lists />)} />
          ))}
          {['messages', 'chat/:id', 'chat-with/:uid'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Messages />)} />
          ))}
          {['adoption', 'adoption-requests'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Adoption />)} />
          ))}
          {['sitters', 'sitter/:uid', 'sitter-edit', 'book/:uid', 'bookings'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Sitting />)} />
          ))}
          {['profile', 'profile-edit', 'user/:uid', 'achievements'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Profile />)} />
          ))}
          {['settings', 'notifications'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Settings />)} />
          ))}
          {['cats', 'cat/:id', 'cat-edit/:id', 'cat-merge/:id', 'photo/:id'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Cats />)} />
          ))}
          {['sighting-new', 'sighting/:id', 'sighting-edit/:id'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Sightings />)} />
          ))}
          {['rescue', 'rescue-new', 'rescue/:id'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Rescue />)} />
          ))}
          {['stations', 'station/:id'].map((path) => (
            <Route key={path} path={'/' + path} element={gate(<Stations />)} />
          ))}
          <Route path="/station-edit" element={gate(<Stations />, true)} />
          {[
            'admin',
            'admin-posts',
            'admin-users',
            'admin-sitters',
            'admin-reports',
            'admin-rescues',
            'admin-merges',
            'admin-volunteers',
            'admin-maintenance',
            'admin-submissions',
            'admin-log',
            'admin-recognition',
            'admin-ai-server',
          ].map((path) => (
            <Route
              key={path}
              path={'/' + path}
              element={gate(
                <Admin />,
                [
                  'admin-users',
                  'admin-volunteers',
                  'admin-recognition',
                  'admin-ai-server',
                ].includes(path),
                true,
              )}
            />
          ))}
          <Route
            path="*"
            element={
              <>
                <AppTopBar title={t('app_name')} />
                <EmptyState text={t('post_removed')} />
              </>
            }
          />
        </Routes>
      </Suspense>
      {isTab && user && (
        <nav className="bottom-nav" aria-label={t('app_name')}>
          {tabs.map((name, i) => (
            <div className="nav-slot" key={name}>
              {i === 2 && (
                <button
                  className="post-nav"
                  onClick={() => setChooser(true)}
                  aria-label={t('post_new')}
                >
                  <ControlIcon name="plus" />
                </button>
              )}
              <Link
                to={'/' + name}
                className={'nav-tab ' + (tab === name ? 'active' : '')}
                aria-current={tab === name ? 'page' : undefined}
              >
                <span>
                  <NavIcon name={name} active={tab === name} />
                  {name === 'messages' && unreadMessages > 0 && (
                    <b className="badge">{unreadMessages > 99 ? '99+' : unreadMessages}</b>
                  )}
                </span>
                <small>{t('tab_' + name)}</small>
              </Link>
            </div>
          ))}
        </nav>
      )}
      {chooser && <PostChooser onClose={() => setChooser(false)} />}{' '}
      {needsRefresh && (
        <div className="snackbar update-snackbar" role="status">
          {t('web_update_available')}
          <button onClick={() => void updates.reload()}>{t('web_reload')}</button>
        </div>
      )}
      {notes && (
        <Dialog
          title={t('whats_new_version', config.version)}
          onClose={() => {
            setNotes(false);
            void SettingsRepository.set({ lastSeenVersion: config.version });
          }}
        >
          <p>{bundled.notes}</p>
          <button
            className="primary"
            onClick={() => {
              setNotes(false);
              void SettingsRepository.set({ lastSeenVersion: config.version });
              navigate('/map');
            }}
          >
            {t('close')}
          </button>
        </Dialog>
      )}
    </div>
  );
}
