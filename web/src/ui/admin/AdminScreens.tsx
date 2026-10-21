import AdminVolunteers from './AdminVolunteers';
import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { usePager, PagingFooter, useDebounced } from '../../data/usePager';
import { where, orderBy, documentId } from 'firebase/firestore';
import { useApp, useList, useAction } from '../../state';
import { AdminRepository } from '../../data/AdminRepository';
import { AdminAnalyticsRepository, type AdminAnalytics } from '../../data/AdminAnalytics';
import { PostRepository } from '../../data/PostRepository';
import { SittingRepository } from '../../data/SittingRepository';
import { ModerationRepository } from '../../data/ModerationRepository';
import { CatRepository } from '../../data/CatRepository';
import { StationRepository } from '../../data/StationRepository';
import { RescueRepository } from '../../data/RescueRepository';
import { ImageRepository } from '../../data/ImageRepository';
import { UserRepository } from '../../data/UserRepository';
import { listQuery, read } from '../../data/Firestore';
import {
  PostFromDoc,
  UserProfileFromDoc,
  SitterProfileFromDoc,
  ContentReportFromDoc,
  RescueReportFromDoc,
  MergeRequestFromDoc,
  VolunteerApplicationFromDoc,
  MaintenanceReportFromDoc,
  SightingFromDoc,
  ModActionFromDoc,
  CatFromDoc,
  type ContentReport,
  type UserProfile,
} from '../../data/model';
import { enums } from '../../data/model/Enums';
import { AiServer } from '../../data/AiServer';
import { RecognitionEvaluation, type Sample } from '../../ai/RecognitionEvaluation';
import { describe } from '../../ai/CatAnalyzer';
import { processPhoto } from '../../util/images';
import { download } from '../../util/share';
import { config } from '../../config';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Loading,
  EmptyState,
  Field,
  SectionHeader,
  Photo,
  Avatar,
  StatusPill,
  Switch,
  Dialog,
  Chips,
  ErrorText,
} from '../components/Components';
const routes = [
  ['admin-posts', 'manage_posts'],
  ['admin-users', 'manage_users'],
  ['admin-sitters', 'manage_sitters'],
  ['admin-reports', 'content_reports'],
  ['admin-rescues', 'rescue_reports'],
  ['admin-merges', 'merge_requests'],
  ['admin-volunteers', 'volunteers'],
  ['admin-maintenance', 'maintenance_reports'],
  ['admin-submissions', 'submissions'],
  ['admin-log', 'moderation_log'],
  ['stations', 'feeding_stations'],
  ['help-directory', 'help_directory'],
  ['admin-recognition', 'recognition_test'],
  ['admin-ai-server', 'ai_server'],
];
export default function AdminScreens() {
  const path = useLocation().pathname;
  const { profile } = useApp();
  if (!profile || !['admin', 'moderator'].includes(profile.role))
    return <EmptyState text={t('err_permission')} />;
  if (
    ['admin-users', 'admin-volunteers', 'admin-recognition', 'admin-ai-server'].includes(
      path.slice(1),
    ) &&
    profile.role !== 'admin'
  )
    return <EmptyState text={t('err_permission')} />;
  return path === '/admin' ? (
    <Dashboard />
  ) : path === '/admin-posts' ? (
    <AdminPosts />
  ) : path === '/admin-users' ? (
    <AdminUsers />
  ) : path === '/admin-sitters' ? (
    <AdminSitters />
  ) : path === '/admin-volunteers' ? (
    <AdminVolunteers />
  ) : path === '/admin-ai-server' ? (
    <AiStatus />
  ) : path === '/admin-recognition' ? (
    <Recognition />
  ) : (
    <AdminQueue />
  );
}
function Dashboard() {
  const { profile } = useApp(),
    action = useAction(),
    [stats, setStats] = useState<Record<string, number> | null>(null),
    [analytics, setAnalytics] = useState<AdminAnalytics | null>(null);
  useEffect(() => {
    if (profile?.role === 'admin') {
      void AdminRepository.stats().then(setStats);
      void AdminAnalyticsRepository.load()
        .then(setAnalytics)
        .catch(() => {});
    }
  }, [profile?.role]);
  return (
    <>
      <AppTopBar title={t('admin_dashboard')} />
      <div className="screen">
        {profile?.role === 'admin' && (
          <>
            <div className="hero-card">
              <div className="row between">
                <h2>{t('app_statistics')}</h2>
                <button
                  className="text-button"
                  onClick={() =>
                    void action.run(async () => setStats(await AdminRepository.stats()))
                  }
                >
                  {t('refresh')}
                </button>
              </div>
              <div className="stats-grid">
                {[
                  ['users', 'stat_users'],
                  ['posts', 'stat_posts'],
                  ['openLost', 'stat_open_lost'],
                  ['reunited', 'stat_reunited'],
                  ['adopted', 'stat_adopted'],
                  ['sitters', 'stat_sitters'],
                  ['bookings', 'stat_bookings'],
                  ['openReports', 'stat_open_reports'],
                  ['hiddenPosts', 'stat_hidden'],
                ].map(([key, label]) => (
                  <div className="stat-tile" key={key}>
                    <strong>{stats?.[key] ?? '…'}</strong>
                    <small>{t(label)}</small>
                  </div>
                ))}
              </div>
            </div>
            <SectionHeader>{t('posts_by_category')}</SectionHeader>
            {enums.PostCategory.map((c) => (
              <div className="chart-bar" key={c.key}>
                <span>{t(c.label)}</span>
                <div>
                  <i
                    style={{
                      width:
                        Math.max(
                          1,
                          ((stats?.[c.key] ?? 0) /
                            Math.max(1, ...enums.PostCategory.map((c) => stats?.[c.key] ?? 0))) *
                            100,
                        ) + '%',
                      background: c.color,
                    }}
                  />
                </div>
                <strong>{stats?.[c.key] ?? 0}</strong>
              </div>
            ))}
            <SectionHeader>{t('admin_analytics')}</SectionHeader>
            {analytics ? (
              <>
                <h3>{t('reunion_rate', analytics.reunionRate.toFixed(1))}</h3>
                <p>{t('reunion_rate_basis', analytics.reunited, analytics.searchPosts)}</p>
                <svg
                  role="img"
                  aria-label={t(
                    'analytics_accessibility',
                    analytics.weeks.map((w) => w.posts).join(', '),
                    analytics.weeks.map((w) => w.members).join(', '),
                  )}
                  className="analytics-chart"
                  viewBox="0 0 360 160"
                >
                  {analytics.weeks.map((w, i) => {
                    const max = Math.max(
                      1,
                      ...analytics.weeks.flatMap((w) => [w.posts, w.members]),
                    );
                    return (
                      <g key={w.start}>
                        <rect
                          x={i * 44 + 5}
                          y={140 - (w.posts / max) * 120}
                          width="14"
                          height={(w.posts / max) * 120}
                          fill="var(--primary)"
                        />
                        <rect
                          x={i * 44 + 20}
                          y={140 - (w.members / max) * 120}
                          width="14"
                          height={(w.members / max) * 120}
                          fill="var(--secondary)"
                        />
                        <text x={i * 44 + 4} y="157" fontSize="8" fill="var(--on-surface)">
                          {new Intl.DateTimeFormat('en', {
                            month: 'short',
                            day: 'numeric',
                            timeZone: 'Asia/Dubai',
                          }).format(w.start)}
                        </text>
                      </g>
                    );
                  })}
                </svg>
                {analytics.matches && (
                  <section>
                    <SectionHeader>{t('analytics_matches')}</SectionHeader>
                    <p>
                      {t(
                        'analytics_matches_body',
                        analytics.matches.total,
                        analytics.matches.strong,
                        analytics.matches.possible,
                        analytics.matches.weak,
                        analytics.matches.maybe,
                        analytics.matches.notMine,
                      )}
                    </p>
                    <p>
                      {t(
                        'analytics_reunions_body',
                        analytics.reunited,
                        analytics.matches.viaCatmap,
                        analytics.matches.confirmed,
                        analytics.matches.verified,
                      )}
                    </p>
                  </section>
                )}
                <p className="small">
                  {t('weekly_posts')} · {t('weekly_members')} · {t('analytics_axis')}
                </p>
              </>
            ) : (
              <button
                className="text-button"
                onClick={() =>
                  void action.run(async () => setAnalytics(await AdminAnalyticsRepository.load()))
                }
              >
                {t('try_again')}
              </button>
            )}
          </>
        )}
        <div className="choice-grid">
          {routes
            .filter(
              ([route]) =>
                profile?.role === 'admin' ||
                ![
                  'admin-users',
                  'admin-volunteers',
                  'admin-recognition',
                  'admin-ai-server',
                  'stations',
                  'help-directory',
                ].includes(route),
            )
            .map(([route, label]) => (
              <Link className="choice-tile" key={route} to={'/' + route}>
                <strong>{t(label)}</strong>
              </Link>
            ))}
        </div>
      </div>
    </>
  );
}
function AdminPosts() {
  const { profile } = useApp(),
    action = useAction(),
    [search, setSearch] = useState(''),
    [hidden, setHidden] = useState(false),
    [category, setCategory] = useState(''),
    [manage, setManage] = useState(''),
    [note, setNote] = useState('');
  const page = usePager(
    () =>
      listQuery(
        'posts',
        ...(hidden ? [where('hidden', '==', true)] : []),
        ...(category ? [where('category', '==', category)] : []),
        orderBy('createdAt', 'desc'),
      ),
    PostFromDoc,
    'admin-posts-' + hidden + category,
    30,
  );
  const posts = page.items;
  const shown = posts?.filter(
      (p) =>
        (!hidden || p.hidden) &&
        (!category || p.category === category) &&
        [p.name, p.ownerName, ...p.keywords].join(' ').toLowerCase().includes(search.toLowerCase()),
    ),
    selected = posts?.find((p) => p.id === manage);
  return (
    <>
      <AppTopBar title={t('manage_posts')} />
      <div className="screen">
        <Field label={t('search')} value={search} onChange={setSearch} />
        <Switch label={t('hidden_only')} checked={hidden} onChange={setHidden} />
        <Chips
          options={[{ key: '', label: 'all' }, ...enums.PostCategory]}
          value={category}
          onChange={setCategory}
        />
        {!shown ? (
          <Loading />
        ) : shown.length ? (
          shown.map((p) => (
            <article className="card admin-row" key={p.id}>
              <Photo id={p.photoIds[0]} />
              <div>
                <Link to={'/post/' + p.id}>
                  <h3 dir="auto">{p.name}</h3>
                </Link>
                <small>@{p.ownerName}</small>
                <StatusPill value={p.status} />
                {p.hidden && <strong>{t('hidden')}</strong>}
              </div>
              <button
                className="text-button"
                onClick={() => {
                  setNote('');
                  setManage(p.id);
                }}
              >
                {t('manage')}
              </button>
            </article>
          ))
        ) : (
          <EmptyState text={t('queue_empty')} />
        )}
        <PagingFooter page={page} visibleCount={shown?.length} />
      </div>
      {selected && (
        <Dialog title={t('manage_post')} onClose={() => setManage('')}>
          <Field label={t('note_optional')} value={note} onChange={setNote} multiline />
          <Link className="outlined" to={'/post-edit/' + selected.id}>
            {t('edit')}
          </Link>
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await PostRepository.setHidden(selected, !selected.hidden, profile!, note);
                page.patch((p) => (p.id === selected.id ? { ...p, hidden: !selected.hidden } : p));
                setManage('');
              }, t('updated'))
            }
          >
            {t(selected.hidden ? 'unhide_post' : 'hide_post')}
          </button>
          <button
            className="text-button danger"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await PostRepository.moderatorDelete(selected, profile!, note);
                page.patch((p) => (p.id === selected.id ? null : p));
                setManage('');
              }, t('updated'))
            }
          >
            {t('delete_post')}
          </button>
        </Dialog>
      )}
    </>
  );
}
function AdminUsers() {
  const { profile } = useApp(),
    action = useAction(),
    [search, setSearch] = useState(''),
    [suspended, setSuspended] = useState(false),
    [manage, setManage] = useState<UserProfile | null>(null),
    [note, setNote] = useState('');
  const settled = useDebounced(search.trim().replace(/^@/, '').toLowerCase());
  const page = usePager(
    () => AdminRepository.users(settled, suspended),
    UserProfileFromDoc,
    'admin-users-' + settled + suspended,
    40,
  );
  const users = page.items;
  const shown = users
    ?.filter(
      (u) =>
        (!suspended || u.suspended) &&
        [u.username, u.displayName]
          .join(' ')
          .toLowerCase()
          .includes(search.trim().replace(/^@/, '').toLowerCase()),
    )
    .sort((a, b) => b.createdAt - a.createdAt);
  return (
    <>
      <AppTopBar title={t('manage_users')} />
      <div className="screen">
        <Field label={t('search')} value={search} onChange={setSearch} />
        <Switch label={t('suspended_only')} checked={suspended} onChange={setSuspended} />
        {!shown ? (
          <Loading />
        ) : shown.length ? (
          shown.map((u) => (
            <article className="card admin-row" key={u.uid}>
              <Avatar name={u.username} id={u.photoId} />
              <div>
                <Link to={'/user/' + u.uid}>@{u.username}</Link>
                <p>
                  {t('role_' + u.role)} {u.suspended && t('suspended')}
                </p>
              </div>
              {u.uid !== profile?.uid && (
                <button
                  className="text-button"
                  onClick={() => {
                    setNote('');
                    setManage(u);
                  }}
                >
                  {t('manage')}
                </button>
              )}
            </article>
          ))
        ) : (
          <EmptyState text={t('user_not_found')} />
        )}
        <PagingFooter page={page} visibleCount={shown?.length} />
      </div>
      {manage && (
        <Dialog title={'@' + manage.username} onClose={() => setManage(null)}>
          <Field label={t('reason')} value={note} onChange={setNote} multiline />
          <button
            className="primary"
            disabled={action.busy || (!manage.suspended && !note.trim())}
            onClick={() =>
              void action.run(async () => {
                await AdminRepository.setSuspended(manage, !manage.suspended, profile!, note);
                page.patch((u) => (u.uid === manage.uid ? { ...u, suspended: !manage.suspended } : u));
                setManage(null);
              }, t('updated'))
            }
          >
            {t(manage.suspended ? 'restore_account' : 'suspend_account')}
          </button>
          {['user', 'volunteer', 'moderator', 'admin']
            .filter((r) => r !== manage.role)
            .map((role) => (
              <button
                className="link-row"
                key={role}
                disabled={action.busy}
                onClick={() =>
                  void action.run(async () => {
                    await ModerationRepository.changeRole(manage, role, profile!);
                    page.patch((u) => (u.uid === manage.uid ? { ...u, role } : u));
                    setManage(null);
                  }, t('updated'))
                }
              >
                {t('make_x', t('role_' + role))}
              </button>
            ))}
          <ErrorText text={action.error} />
        </Dialog>
      )}
    </>
  );
}
function AdminSitters() {
  const page = usePager(
      () => listQuery('sitters', orderBy(documentId())),
      SitterProfileFromDoc,
      'admin-sitters',
      30,
    ),
    sitters = page.items,
    { profile } = useApp(),
    action = useAction();
  return (
    <>
      <AppTopBar title={t('manage_sitters')} />
      <div className="screen">
        {!sitters ? (
          <Loading />
        ) : sitters.length ? (
          sitters.map((s) => (
            <article className="card" key={s.uid}>
              <Link to={'/sitter/' + s.uid}>
                <h3>@{s.name}</h3>
              </Link>
              <p dir="auto">{s.headline}</p>
              <p>
                {s.verified ? t('verified_sitter') : ''} {s.active ? '' : t('inactive')}
              </p>
              {profile?.role === 'admin' && (
                <button
                  className="outlined"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(async () => {
                      await SittingRepository.setVerified(s, !s.verified, profile);
                      page.patch((x) => (x.uid === s.uid ? { ...x, verified: !s.verified } : x));
                    }, t('updated'))
                  }
                >
                  {t(s.verified ? 'remove_verification' : 'verify_sitter')}
                </button>
              )}
              <button
                className="text-button danger"
                disabled={action.busy}
                onClick={() =>
                  void action.run(async () => {
                    await SittingRepository.moderatorRemove(s, profile!, '');
                    page.patch((x) => (x.uid === s.uid ? null : x));
                  }, t('updated'))
                }
              >
                {t('remove_sitter')}
              </button>
            </article>
          ))
        ) : (
          <EmptyState text={t('no_sitters')} />
        )}
        <PagingFooter page={page} />
      </div>
    </>
  );
}
function AdminQueue() {
  const path = useLocation().pathname,
    { profile } = useApp(),
    action = useAction(),
    [note, setNote] = useState(''),
    [rescueStatus, setRescueStatus] = useState('');
  const reports = useList(
      () => (path === '/admin-reports' ? ModerationRepository.openReports() : null),
      ContentReportFromDoc,
      'admin-reports-' + path,
    ),
    rescues = useList(
      () => (path === '/admin-rescues' ? RescueRepository.allReports(rescueStatus) : null),
      RescueReportFromDoc,
      'admin-rescues-' + path + rescueStatus,
    ),
    merges = useList(
      () => (path === '/admin-merges' ? CatRepository.mergeRequests() : null),
      MergeRequestFromDoc,
      'admin-merges-' + path,
    ),
    applications = useList(
      () => (path === '/admin-volunteers' ? ModerationRepository.pendingApplications() : null),
      VolunteerApplicationFromDoc,
      'admin-volunteers-' + path,
    ),
    maintenance = useList(
      () => (path === '/admin-maintenance' ? StationRepository.openMaintenance() : null),
      MaintenanceReportFromDoc,
      'admin-maintenance-' + path,
    ),
    submissions = useList(
      () => (path === '/admin-submissions' ? CatRepository.pendingSightings() : null),
      SightingFromDoc,
      'admin-submissions-' + path,
    ),
    cats = useList(
      () =>
        path === '/admin-submissions'
          ? listQuery('cats', where('createdAt', '>', Date.now() - 7 * 86400000))
          : null,
      CatFromDoc,
      'admin-new-cats-' + path,
    ),
    logPage = usePager(
      () => (path === '/admin-log' ? listQuery('modActions', orderBy('at', 'desc')) : null),
      ModActionFromDoc,
      'admin-log-' + path,
      40,
    ),
    log = path === '/admin-log' ? logPage.items : undefined;
  const title = routes.find(([route]) => '/' + route === path)?.[1] ?? 'admin_dashboard';
  const actOnReport = async (r: ContentReport) => {
    if (r.targetType === 'post') {
      const p = await PostRepository.post(r.targetId);
      if (p) await PostRepository.setHidden(p, true, profile!, note);
    } else if (r.targetType === 'cat') {
      const c = await CatRepository.cat(r.targetId);
      if (c) await CatRepository.setCatHidden(c, true, profile!, note);
    } else if (r.targetType === 'sighting') {
      const s = await read(`sightings/${r.targetId}`, SightingFromDoc);
      if (s) await CatRepository.reviewSighting(s, false, profile!);
    } else if (r.targetType === 'photo') await ImageRepository.delete(r.targetId);
    else if (r.targetType === 'user' && profile?.role === 'admin') {
      const u = await UserRepository.profile(r.targetId);
      if (u) await AdminRepository.setSuspended(u, true, profile, note);
    }
    await ModerationRepository.closeReport(r, 'resolved', note, profile!);
  };
  const loaded = [reports, rescues, merges, applications, maintenance, submissions, log].find(
    (list) => list !== undefined,
  );
  return (
    <>
      <AppTopBar title={t(title)} />
      <div className="screen">
        {path === '/admin-rescues' && (
          <Chips
            options={[{ key: '', label: 'all' }, ...enums.RescueStatus]}
            value={rescueStatus}
            onChange={setRescueStatus}
          />
        )}
        {path === '/admin-log' && <PagingFooter page={logPage} />}
        {path !== '/admin-log' && (
          <Field label={t('resolution_note')} value={note} onChange={setNote} multiline />
        )}
        <ErrorText text={action.error} />
        {loaded === undefined ? (
          <Loading />
        ) : loaded.length === 0 ? (
          <EmptyState text={t(path === '/admin-log' ? 'no_actions' : 'queue_empty')} />
        ) : null}
        {reports?.map((r) => (
          <article className="card" key={r.id}>
            <h3>{r.targetLabel}</h3>
            <p>
              {r.reason} · @{r.reporterName}
            </p>
            <p dir="auto">{r.details}</p>
            <button
              className="primary"
              disabled={action.busy || (r.targetType === 'user' && profile?.role !== 'admin')}
              onClick={() => void action.run(() => actOnReport(r), t('updated'))}
            >
              {t('take_action')}
            </button>
            <button
              className="text-button"
              disabled={action.busy}
              onClick={() =>
                void action.run(
                  () => ModerationRepository.closeReport(r, 'dismissed', note, profile!),
                  t('updated'),
                )
              }
            >
              {t('dismiss')}
            </button>
          </article>
        ))}
        {rescues?.map((r) => (
          <article className="card" key={r.id}>
            <Link to={'/rescue/' + r.id}>
              <h3>{r.catName || r.type}</h3>
            </Link>
            <StatusPill value={r.status} group="RescueStatus" />
            <p dir="auto">{r.description}</p>
            {r.status === 'submitted' &&
              ['approved', 'rejected'].map((status) => (
                <button
                  key={status}
                  className="outlined"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(
                      () => RescueRepository.changeStatus(r, status, note, profile!),
                      t('updated'),
                    )
                  }
                >
                  {t(status === 'approved' ? 'approve' : 'reject')}
                </button>
              ))}
          </article>
        ))}
        {merges?.map((r) => (
          <article className="card" key={r.id}>
            <div className="row">
              <Link to={'/cat/' + r.primaryCatId}>{r.primaryName}</Link> ←{' '}
              <Link to={'/cat/' + r.duplicateCatId}>{r.duplicateName}</Link>
            </div>
            <p dir="auto">{r.reason}</p>
            <p>{Math.round(r.similarity * 100)}%</p>
            <button
              className="primary"
              disabled={action.busy}
              onClick={() =>
                void action.run(() => CatRepository.approveMerge(r, profile!, note), t('updated'))
              }
            >
              {t('approve_merge')}
            </button>
            <button
              className="text-button"
              disabled={action.busy}
              onClick={() =>
                void action.run(() => CatRepository.rejectMerge(r, profile!, note), t('updated'))
              }
            >
              {t('reject')}
            </button>
          </article>
        ))}
        {applications?.map((a) => (
          <article className="card" key={a.id}>
            <Link to={'/user/' + a.userId}>
              <h3>@{a.username}</h3>
            </Link>
            <p dir="auto">{a.motivation}</p>
            <p dir="auto">{a.experience}</p>
            <p>{a.area}</p>
            {[true, false].map((approve) => (
              <button
                className="outlined"
                key={String(approve)}
                disabled={action.busy}
                onClick={() =>
                  void action.run(
                    () => ModerationRepository.decideApplication(a, approve, profile!),
                    t('updated'),
                  )
                }
              >
                {t(approve ? 'approve' : 'reject')}
              </button>
            ))}
          </article>
        ))}
        {maintenance?.map((r) => (
          <article className="card" key={r.id}>
            <Link to={'/station/' + r.stationId}>
              <h3>{r.stationName}</h3>
            </Link>
            <StatusPill value={r.status} group="MaintenanceStatus" />
            <p dir="auto">{r.description}</p>
            {profile?.role === 'admin' &&
              ['in_progress', 'resolved'].map((status) => (
                <button
                  className="outlined"
                  key={status}
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(
                      () => StationRepository.updateMaintenance(r, status, note, profile),
                      t('updated'),
                    )
                  }
                >
                  {t(status === 'resolved' ? 'mark_resolved' : 'mstatus_in_progress')}
                </button>
              ))}
          </article>
        ))}
        {submissions?.map((s) => (
          <article className="card" key={s.id}>
            <Link to={'/sighting/' + s.id}>
              <h3>{s.catName}</h3>
            </Link>
            <p dir="auto">{s.notes}</p>
            {[true, false].map((approve) => (
              <button
                className="outlined"
                key={String(approve)}
                disabled={action.busy}
                onClick={() =>
                  void action.run(
                    () => CatRepository.reviewSighting(s, approve, profile!),
                    t('updated'),
                  )
                }
              >
                {t(approve ? 'approve' : 'reject')}
              </button>
            ))}
          </article>
        ))}{' '}
        {cats && (
          <>
            <SectionHeader>{t('new_cats_week_n', cats.length)}</SectionHeader>
            {cats.map((c) => (
              <Link className="card" key={c.id} to={'/cat/' + c.id}>
                {c.name} · @{c.createdByName}
              </Link>
            ))}
          </>
        )}
        {log?.map((l) => (
          <article className="card" key={l.id}>
            <strong>{t('log_' + l.action)}</strong>
            <p dir="auto">{l.targetLabel}</p>
            <p dir="auto">{l.note}</p>
            <small>
              @{l.moderatorName} · {dateTime(l.at)}
            </small>
          </article>
        ))}
      </div>
    </>
  );
}
function AiStatus() {
  const [health, setHealth] = useState<Awaited<ReturnType<typeof AiServer.health>>>(null),
    [seen, setSeen] = useState<number | null>(null),
    [checked, setChecked] = useState(false),
    action = useAction();
  const check = async () => {
    setHealth(await AiServer.health());
    setSeen(await AiServer.lastSeen());
    setChecked(true);
  };
  useEffect(() => {
    void check();
  }, []);
  return (
    <>
      <AppTopBar title={t('ai_server')} />
      <div className="screen">
        <p>{t('ai_server_intro')}</p>
        {!checked ? (
          <Loading />
        ) : health ? (
          <>
            <h2>{t('ai_server_ok')}</h2>
            <p>
              {health.model} · {health.provider}
            </p>
          </>
        ) : (
          <>
            <h2>{t('ai_server_down')}</h2>
            <p>{seen ? dateTime(seen) : t('ai_server_never')}</p>
          </>
        )}
        <button className="primary" disabled={action.busy} onClick={() => void action.run(check)}>
          {t('ai_server_test')}
        </button>
      </div>
    </>
  );
}
function Recognition() {
  const action = useAction(),
    [report, setReport] = useState(''),
    [progress, setProgress] = useState(''),
    [useAi, setUseAi] = useState(true);
  const run = async (files: FileList | null) => {
    if (!files) return;
    await action.run(async () => {
      const samples: Sample[] = [],
        images: HTMLCanvasElement[] = [],
        ids: string[] = [];
      const selected = Array.from(files)
        .filter((f) => f.type.startsWith('image/'))
        .slice(0, 400);
      for (let i = 0; i < selected.length; i++) {
        const file = selected[i],
          parts = file.webkitRelativePath.split('/'),
          identity = parts.length >= 3 ? parts.at(-2)! : file.name.split('_')[0];
        setProgress(`${i + 1} / ${selected.length}`);
        try {
          const start = performance.now(),
            p = await processPhoto(file),
            analysis = describe(
              p.analysis.getContext('2d')!.getImageData(0, 0, p.analysis.width, p.analysis.height),
            );
          samples.push({
            identity,
            file: file.name,
            features: analysis.features,
            coat: analysis.coatColor,
            pattern: analysis.pattern,
            millis: Math.round(performance.now() - start),
          });
          images.push(p.analysis);
          ids.push(identity);
        } catch {
          samples.push({
            identity,
            file: file.name,
            features: [],
            coat: 'unknown',
            pattern: 'unknown',
            millis: 0,
          });
        }
      }
      const result = RecognitionEvaluation.evaluate(samples);
      if (!result) {
        setReport(t('recognition_too_few'));
        return;
      }
      let text = RecognitionEvaluation.report(result, config.version);
      setReport(text);
      if (useAi) {
        const health = await AiServer.health();
        if (health) {
          const all: { identity: string; embedding: number[] }[] = [];
          for (let i = 0; i < images.length; i += 16) {
            setProgress(`AI ${Math.min(i + 16, images.length)} / ${images.length}`);
            const embedded = await AiServer.embed(images.slice(i, i + 16));
            embedded?.forEach((embedding, j) => all.push({ identity: ids[i + j], embedding }));
          }
          const ai = RecognitionEvaluation.evaluateEmbeddings(all, health.same, health.maybe);
          text +=
            '\n\n' +
            (ai
              ? RecognitionEvaluation.embeddingReport(ai, health.model, health.same, health.maybe)
              : t('recognition_ai_unavailable'));
        } else text += '\n\n' + t('recognition_ai_unavailable');
        setReport(text);
      }
      setProgress('');
    });
  };
  return (
    <>
      <AppTopBar title={t('recognition_test')} />
      <div className="screen">
        <p>{t('web_analyser_limits')}</p>
        <p>{t('web_analyser')}</p>
        <Switch label={t('ai_server')} checked={useAi} onChange={setUseAi} />
        <label className="primary file-button">
          {t('recognition_choose_folder')}
          <input
            type="file"
            multiple
            accept="image/*"
            {...{ webkitdirectory: '' }}
            disabled={action.busy}
            onChange={(e) => void run(e.target.files)}
          />
        </label>
        {progress && <p role="status">{progress}</p>}
        <ErrorText text={action.error} />
        {report && (
          <>
            <pre className="recognition-report">{report}</pre>
            <button
              className="outlined"
              onClick={() =>
                download(new Blob([report], { type: 'text/plain' }), 'catmap-recognition.txt')
              }
            >
              {t('recognition_share')}
            </button>
          </>
        )}
      </div>
    </>
  );
}
