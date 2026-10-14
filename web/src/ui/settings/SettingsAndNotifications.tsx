import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useApp, useSettings, useAction } from '../../state';
import { SettingsRepository, categories } from '../../data/SettingsRepository';
import { NotificationRepository } from '../../data/NotificationRepository';
import { NotificationText } from '../../notifications/NotificationDelivery';
import { DemoAccounts, DemoVault } from '../../data/DemoAccounts';
import { AuthRepository } from '../../data/AuthRepository';
import { UserRepository } from '../../data/UserRepository';
import { PostRepository } from '../../data/PostRepository';
import { UserDataExportRepository } from '../../data/UserDataExport';
import { deleteAccount } from '../../data/AccountDeletion';
import { safeRoute } from '../../util/links';
import { getReleaseNotes } from '../../util/whatsNew';
import { t, dateTime } from '../../i18n';
import { config } from '../../config';
import {
  AppTopBar,
  SectionHeader,
  Switch,
  Chips,
  Field,
  Dialog,
  EmptyState,
  ErrorText,
  LinkRow,
} from '../components/Components';
export default function SettingsAndNotifications() {
  return useLocation().pathname === '/notifications' ? <Inbox /> : <Settings />;
}
function Inbox() {
  const { inbox, user, profile } = useApp(),
    navigate = useNavigate(),
    action = useAction(),
    uid = user?.uid ?? '',
    [clear, setClear] = useState(false);
  return (
    <>
      <AppTopBar title={t('notifications')}>
        <button
          className="text-button"
          disabled={action.busy}
          onClick={() =>
            void action.run(() =>
              NotificationRepository.markAllRead(
                uid,
                inbox.map((n) => n.id),
              ),
            )
          }
        >
          {t('mark_all_read')}
        </button>
      </AppTopBar>
      <div className="screen">
        {inbox.length ? (
          inbox.map((n) => {
            const [title, body] = NotificationText.format(n);
            return (
              <article className={'notification ' + (!n.read ? 'unread' : '')} key={n.id}>
                <button
                  className="notification-open"
                  onClick={() =>
                    void action.run(async () => {
                      await NotificationRepository.markRead(uid, n.id);
                      if (n.route) navigate(safeRoute(n.route));
                    })
                  }
                >
                  <strong dir="auto">{title}</strong>
                  <p dir="auto">{body}</p>
                  <small>{dateTime(n.createdAt)}</small>
                </button>
                {n.type === 'stale_post' && (
                  <div className="row">
                    <button
                      className="text-button"
                      disabled={action.busy}
                      onClick={() =>
                        void action.run(async () => {
                          const p = await PostRepository.post(n.route.split('/')[1]);
                          if (p?.ownerId === uid) await PostRepository.stillLooking(p);
                        }, t('updated'))
                      }
                    >
                      {t('still_looking')}
                    </button>
                    <button
                      className="text-button"
                      disabled={action.busy}
                      onClick={() =>
                        void action.run(async () => {
                          const p = await PostRepository.post(n.route.split('/')[1]);
                          if (p?.ownerId === uid)
                            await PostRepository.setStatus(p, 'reunited', profile!);
                        }, t('updated'))
                      }
                    >
                      {t('mark_reunited')}
                    </button>
                  </div>
                )}
                <button
                  className="text-button"
                  onClick={() => void action.run(() => NotificationRepository.delete(uid, n.id))}
                >
                  {t('delete')}
                </button>
              </article>
            );
          })
        ) : (
          <EmptyState text={t('no_notifications')} />
        )}{' '}
        {inbox.length > 0 && (
          <button className="text-button" onClick={() => setClear(true)}>
            {t('clear_all')}
          </button>
        )}
      </div>
      {clear && (
        <Dialog title={t('clear_all')} onClose={() => setClear(false)}>
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await NotificationRepository.clearAll(uid);
                setClear(false);
              })
            }
          >
            {t('confirm')}
          </button>
        </Dialog>
      )}
    </>
  );
}
function Settings() {
  const { profile, user } = useApp(),
    settings = useSettings(),
    action = useAction(),
    navigate = useNavigate(),
    [modal, setModal] = useState(''),
    [password, setPassword] = useState(''),
    [demoSaved, setDemoSaved] = useState(false),
    [notes, setNotes] = useState('');
  useEffect(() => {
    void DemoVault.saved().then(setDemoSaved);
  }, [user]);
  const set = SettingsRepository.set;
  const time = (minute: number) =>
      String(Math.floor(minute / 60)).padStart(2, '0') + ':' + String(minute % 60).padStart(2, '0'),
    minute = (value: string) => {
      const [h, m] = value.split(':').map(Number);
      return h * 60 + m;
    };
  return (
    <>
      <AppTopBar title={t('settings')} />
      <div className="screen settings">
        <SectionHeader>{t('language')}</SectionHeader>
        <Chips
          options={[
            { key: '', label: 'theme_system' },
            { key: 'en', label: 'lang_english' },
            { key: 'ar', label: 'lang_arabic' },
          ]}
          value={settings.language}
          onChange={(language) => void set({ language })}
        />
        <SectionHeader>{t('appearance_settings')}</SectionHeader>
        <Chips
          options={[
            { key: 'system', label: 'theme_system' },
            { key: 'light', label: 'theme_light' },
            { key: 'dark', label: 'theme_dark' },
          ]}
          value={settings.theme}
          onChange={(theme) => void set({ theme })}
        />
        <Switch
          label={t('larger_text')}
          checked={settings.largeText}
          onChange={(largeText) => void set({ largeText })}
        />
        <SectionHeader>{t('map_style')}</SectionHeader>
        <Chips
          options={[
            { key: 'modern', label: 'map_modern' },
            { key: 'classic', label: 'map_classic' },
          ]}
          value={settings.mapMode}
          onChange={(mapMode) => void set({ mapMode })}
        />
        {config.maptiler && (
          <Switch
            label={t('satellite')}
            checked={settings.satellite}
            onChange={(satellite) => void set({ satellite })}
          />
        )}
        <SectionHeader>{t('quiet_hours')}</SectionHeader>
        <Switch
          label={t('quiet_hours')}
          checked={settings.quietHoursEnabled}
          onChange={(quietHoursEnabled) => void set({ quietHoursEnabled })}
        />
        <div className="field-pair">
          <Field
            label={t('quiet_start')}
            value={time(settings.quietStart)}
            onChange={(value) => {
              const n = minute(value);
              if (Number.isInteger(n) && n >= 0 && n < 1440) void set({ quietStart: n });
            }}
            type="time"
          />
          <Field
            label={t('quiet_end')}
            value={time(settings.quietEnd)}
            onChange={(value) => {
              const n = minute(value);
              if (Number.isInteger(n) && n >= 0 && n < 1440) void set({ quietEnd: n });
            }}
            type="time"
          />
        </div>
        <p className="small muted">{t('quiet_hours_note')}</p>
        <SectionHeader>{t('notifications')}</SectionHeader>
        <Switch
          label={t('notifications_enabled')}
          checked={settings.notificationsEnabled}
          onChange={(notificationsEnabled) => void set({ notificationsEnabled })}
        />
        {categories.map((category) => (
          <Switch
            key={category}
            label={t('notif_cat_' + category)}
            checked={settings.categories[category] !== false}
            onChange={(value) =>
              void set({ categories: { ...settings.categories, [category]: value } })
            }
          />
        ))}
        <LinkRow to="/area-alerts" label={t('area_alerts')} />
        <SectionHeader>{t('privacy')}</SectionHeader>
        <p>{t('public_location_precision')}</p>
        <div className="chips">
          {['standard', 'high'].map((level) => (
            <button
              key={level}
              className={'chip ' + (settings.privacyLevel === level ? 'selected' : '')}
              onClick={() => void set({ privacyLevel: level })}
            >
              {t(
                level === 'standard' ? 'precision_standard' : 'precision_high',
                level === 'standard' ? 177 : 399,
              )}
            </button>
          ))}
        </div>
        <Switch
          label={t('share_exact_volunteers')}
          checked={settings.shareExactWithVolunteers}
          onChange={(shareExactWithVolunteers) => void set({ shareExactWithVolunteers })}
        />
        {profile && (
          <Switch
            label={t('show_stats_publicly')}
            checked={profile.showStatsPublicly}
            onChange={(value) =>
              void action.run(() => UserRepository.setShowStats(profile.uid, value))
            }
          />
        )}
        {DemoAccounts.isDemoEmail(user?.email) && demoSaved && (
          <>
            <SectionHeader>{t('demo_accounts')}</SectionHeader>
            <p>{t('demo_accounts_body')}</p>
            {DemoAccounts.ALL.map((a) => (
              <button
                className="link-row"
                key={a.username}
                disabled={action.busy}
                onClick={() =>
                  void action.run(async () => {
                    await AuthRepository.switchDemoAccount(a);
                    navigate('/map');
                  })
                }
              >
                @{a.username} · {a.displayName}
              </button>
            ))}
          </>
        )}
        <SectionHeader>{t('account')}</SectionHeader>
        <p>{user?.email}</p>
        <button
          className="link-row"
          disabled={action.busy}
          onClick={() =>
            void action.run(() => UserDataExportRepository.download(user!.uid), t('saved'))
          }
        >
          {t('download_my_data')}
        </button>
        <button
          className="link-row"
          disabled={action.busy}
          onClick={() =>
            void action.run(
              () => AuthRepository.sendPasswordReset(user?.email ?? ''),
              t('reset_sent', user?.email),
            )
          }
        >
          {t('change_password')}
        </button>
        <button className="link-row" onClick={() => setModal('logout')}>
          {t('logout')}
        </button>
        <button
          className="link-row danger"
          onClick={() => {
            setPassword('');
            setModal('delete');
          }}
        >
          {t('delete_account')}
        </button>
        <SectionHeader>{t('about')}</SectionHeader>
        <p>{t('about_text', config.version)}</p>
        <p>{t('map_attribution')}</p>
        <button
          className="link-row"
          onClick={() =>
            void action.run(async () => {
              setNotes(await getReleaseNotes());
              setModal('notes');
            })
          }
        >
          {t('whats_new')}
        </button>
        <ErrorText text={action.error} />
      </div>
      {modal === 'logout' && (
        <Dialog title={t('logout')} onClose={() => setModal('')}>
          <p>{t('logout_body')}</p>
          <button
            className="primary"
            onClick={() =>
              void action.run(async () => {
                await AuthRepository.logout();
                navigate('/login', { replace: true, state: { returnTo: '/map' } });
              })
            }
          >
            {t('confirm')}
          </button>
        </Dialog>
      )}
      {modal === 'delete' && (
        <Dialog title={t('delete_account')} onClose={() => setModal('')}>
          <p>{t('delete_account_body')}</p>
          <Field
            label={t('password')}
            value={password}
            onChange={setPassword}
            type="password"
            autoComplete="current-password"
          />
          <ErrorText text={action.error} />
          <button
            className="primary danger"
            disabled={action.busy || !password}
            onClick={() =>
              void action.run(async () => {
                await deleteAccount(password);
                navigate('/login', { replace: true, state: { returnTo: '/map' } });
              }, t('account_deleted'))
            }
          >
            {t('delete_account')}
          </button>
        </Dialog>
      )}
      {modal === 'notes' && (
        <Dialog title={t('whats_new_version', config.version)} onClose={() => setModal('')}>
          <div className="release-notes" dir="auto">
            {notes}
          </div>
        </Dialog>
      )}
    </>
  );
}
