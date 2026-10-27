import { lazy, Suspense, useEffect, useState, useSyncExternalStore } from 'react';
import QRCode from 'qrcode';
import { SettingsRepository } from '../data/SettingsRepository';
import { setLanguage, t } from '../i18n';
import { installation, installGateEnabled, installPlatform } from './install';
import { launchStory, StartupIntro } from './StartupIntro';
const Runtime = lazy(() => import('./Runtime'));
function ShareIcon() {
  return (
    <svg
      className="ios-share"
      viewBox="0 0 24 28"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      aria-hidden="true"
    >
      <path
        d="M7 11H3v14h18V11h-4M12 19V2m-6 6 6-6 6 6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
export default function InstallRoot() {
  const state = useSyncExternalStore(installation.subscribe, installation.get);
  const settings = useSyncExternalStore(SettingsRepository.subscribe, SettingsRepository.get);
  const [qr, setQr] = useState(''),
    [dark, setDark] = useState(matchMedia('(prefers-color-scheme: dark)').matches),
    [message, setMessage] = useState('');
  const gated = installGateEnabled && !state.standalone;
  // Drawn once per launch, and only for the installed app: the install page is not a launch.
  const [intro, setIntro] = useState(() => (gated ? null : launchStory()));
  const platform = installPlatform();
  useEffect(() => {
    void SettingsRepository.load().catch(() => {});
  }, []);
  useEffect(() => {
    setLanguage(settings.language || navigator.language);
    document.title = t('web_app_title');
    const media = matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      document.documentElement.dataset.theme =
        settings.theme === 'system' ? (media.matches ? 'dark' : 'light') : settings.theme;
      setDark(document.documentElement.dataset.theme === 'dark');
      document.documentElement.style.setProperty('--text-scale', settings.largeText ? '1.18' : '1');
      document
        .querySelectorAll('meta[name="theme-color"]')
        .forEach((meta) =>
          meta.setAttribute(
            'content',
            document.documentElement.dataset.theme === 'dark' ? '#1A1410' : '#A9531A',
          ),
        );
    };
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [settings]);
  useEffect(() => {
    let active = true;
    if (gated && !platform.ios && !platform.android)
      void QRCode.toDataURL(location.href, { width: 240, margin: 2 }).then((data) => {
        if (active) setQr(data);
      });
    return () => {
      active = false;
    };
  }, [gated, platform.ios, platform.android]);
  if (!gated)
    return (
      <>
        <Suspense
          fallback={
            <div className="app boot-screen" role="status">
              {t('web_loading')}
            </div>
          }
        >
          <Runtime />
        </Suspense>
        {intro && <StartupIntro story={intro} dark={dark} onDone={() => setIntro(null)} />}
      </>
    );
  return (
    <main className={'app install-gate ' + (platform.ipad ? 'ipad' : '')} data-install-gate>
      <div className="install-preferences">
        <button
          className="text-button"
          onClick={() =>
            void SettingsRepository.set({
              language: document.documentElement.lang === 'ar' ? 'en' : 'ar',
            })
          }
        >
          {document.documentElement.lang === 'ar' ? t('lang_english') : t('lang_arabic')}
        </button>
        <button
          className="text-button"
          onClick={() =>
            void SettingsRepository.set({
              theme: document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark',
            })
          }
        >
          {dark ? t('theme_light') : t('theme_dark')}
        </button>
      </div>
      <img className="install-logo" src="/icon-192.png" alt="" draggable="false" />
      <h1>{t('app_name')}</h1>
      {state.installed ? (
        <p role="status">{t('web_installed_open')}</p>
      ) : platform.inApp ? (
        <>
          <h2>{t('web_install_safari')}</h2>
          <button
            className="primary"
            onClick={() =>
              void (async () => {
                try {
                  await navigator.clipboard.writeText(location.href);
                  setMessage(t('web_link_copied'));
                } catch {
                  setMessage(t('web_copy_link_failed'));
                }
              })()
            }
          >
            {t('web_copy_link')}
          </button>
        </>
      ) : platform.ios ? (
        <>
          <h2>{t('web_install_title')}</h2>
          <ol className="install-steps">
            <li>
              <ShareIcon />
              {t('web_install_share')}
            </li>
            <li>{t('web_install_home')}</li>
            {platform.ios26 && <li>{t('web_install_open_as_app')}</li>}
            <li>{t('web_install_launch')}</li>
          </ol>
          <div
            className={'share-pointer ' + (platform.ipad ? 'share-top' : 'share-bottom')}
            aria-hidden="true"
          >
            {platform.ipad ? '↑' : '↓'}
          </div>
        </>
      ) : platform.android ? (
        <>
          <h2>{t('web_install_title')}</h2>
          {state.prompt && (
            <button
              className="primary"
              onClick={() =>
                void installation.prompt().catch(() => setMessage(t('web_install_menu')))
              }
            >
              {t('web_install_button')}
            </button>
          )}
          <p>{t('web_install_menu')}</p>
          <p>{t('web_install_launch')}</p>
        </>
      ) : (
        <>
          <h2>{t('web_install_phone')}</h2>
          <p>{t('web_install_scan')}</p>
          {qr && (
            <img className="install-qr" src={qr} alt={t('web_install_scan')} draggable="false" />
          )}
          {state.prompt && (
            <button className="primary" onClick={() => void installation.prompt()}>
              {t('web_install_desktop')}
            </button>
          )}
        </>
      )}
      {message && <p role="status">{message}</p>}
    </main>
  );
}
