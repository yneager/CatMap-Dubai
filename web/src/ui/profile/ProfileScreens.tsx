import { useAsyncList } from '../../data/useAsyncList';
import { listQuery } from '../../data/Firestore';
import { where } from 'firebase/firestore';
import { MonthlyChart } from '../components/MonthlyChart';
import { ReportButton } from '../components/ReportButton';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useDoc, useList, useAction } from '../../state';
import { UserRepository } from '../../data/UserRepository';
import { PostRepository } from '../../data/PostRepository';
import { CatRepository } from '../../data/CatRepository';
import { UserProfileFromDoc, PostFromDoc, CatFromDoc, SightingFromDoc } from '../../data/model';
import { ImageRepository } from '../../data/ImageRepository';
import { ModerationRepository } from '../../data/ModerationRepository';
import { processPhoto } from '../../util/images';
import { t, dateTime } from '../../i18n';
import { badges, Levels } from '../../gamification/Badges';
import { CatMapIcons } from '../../icons/CatMapIcons';
import {
  AppTopBar,
  Bell,
  Avatar,
  Icon,
  SectionHeader,
  LinkRow,
  PostCard,
  Loading,
  EmptyState,
  Field,
  ErrorText,
  Dialog,
  ControlIcon,
} from '../components/Components';
export default function ProfileScreens() {
  const path = useLocation().pathname;
  return path === '/profile-edit' ? (
    <ProfileEdit />
  ) : path === '/achievements' ? (
    <Achievements />
  ) : (
    <Profile />
  );
}
function Profile() {
  const { uid: otherUid } = useParams(),
    { profile: me } = useApp(),
    uid = otherUid ?? me?.uid ?? '',
    own = !otherUid || otherUid === me?.uid,
    action = useAction();
  const profile = useDoc(
      () => (uid ? UserRepository.profileFlow(uid) : null),
      UserProfileFromDoc,
      'user-' + uid,
    ),
    posts = useList(
      () => (uid ? (own ? PostRepository.postsBy(uid) : PostRepository.publicPostsBy(uid)) : null),
      PostFromDoc,
      'user-posts-' + uid + '-' + own,
    ),
    sightings = useList(
      () => (uid ? CatRepository.sightingsForUser(uid) : null),
      SightingFromDoc,
      'profile-sightings-' + uid,
    );
  const followedCats = useAsyncList(
    () => CatRepository.byIds(profile?.followedCats ?? []),
    'profile-cats-' + uid + '-' + (profile?.followedCats ?? []).join(','),
  );
  const contributedCats = useList(
    () => (uid ? listQuery('cats', where('contributors', 'array-contains', uid)) : null),
    CatFromDoc,
    'contributed-' + uid,
  );
  const cats = [
    ...new Map(
      [...(followedCats ?? []), ...(contributedCats ?? [])].map((c) => [c.id, c]),
    ).values(),
  ];
  const [tab, setTab] = useState('posts'),
    [volunteer, setVolunteer] = useState(false),
    [motivation, setMotivation] = useState(''),
    [experience, setExperience] = useState(''),
    [area, setArea] = useState('');
  if (profile === undefined) return <Loading />;
  if (!profile) return <EmptyState text={t('profile_not_found')} />;
  return (
    <>
      <AppTopBar title={own ? t('tab_profile') : '@' + profile.username} back={!own}>
        {own && (
          <>
            <Bell />
            <Link className="icon-button" aria-label={t('settings')} to="/settings">
              <ControlIcon name="settings" />
            </Link>
          </>
        )}
      </AppTopBar>
      <div className="screen">
        <div className="profile-header">
          <Avatar name={profile.displayName || profile.username} id={profile.photoId} size={96} />
          <h2 dir="auto">{profile.displayName}</h2>
          <p>
            @{profile.username} · {t('role_' + profile.role)}
          </p>
          <p dir="auto">{profile.bio}</p>
          {profile.suspended && <p className="error">{t('suspended')}</p>}
        </div>
        {own ? (
          <Link className="outlined" to="/profile-edit">
            {t('edit_profile')}
          </Link>
        ) : (
          <>
            <Link className="primary" to={'/chat-with/' + uid}>
              {t('message')}
            </Link>
            <ReportButton type="user" id={uid} label={profile.username} />
          </>
        )}
        <div className="hero-card">
          <h3>{t('level_' + Levels.of(profile.points))}</h3>
          <p>{t('points_n', profile.points)}</p>
          <progress value={Levels.progress(profile.points)} max="1" />
          <p>
            {Levels.of(profile.points) === 6
              ? t('max_level')
              : t(
                  'points_to_next',
                  Levels.all[Levels.of(profile.points)] - profile.points,
                  t('level_' + (Levels.of(profile.points) + 1)),
                )}
          </p>
          {own && <Link to="/achievements">{t('achievements')}</Link>}
        </div>
        {own && (
          <>
            <div className="quick-links">
              {[
                ['/my-posts', 'my_posts'],
                ['/favorites', 'favorites'],
                ['/adoption-requests', 'adoption_requests'],
                ['/bookings', 'bookings'],
                ['/sitter-edit', 'sitter_profile'],
                ['/area-alerts', 'area_alerts'],
              ].map(([to, key]) => (
                <Link className="card" key={to} to={to}>
                  {t(key)}
                </Link>
              ))}
            </div>
            {['admin', 'moderator'].includes(profile.role) && (
              <LinkRow to="/admin" label={t('admin_dashboard')} icon={CatMapIcons.CHART} />
            )}{' '}
            {profile.role === 'user' && (
              <button className="outlined" onClick={() => setVolunteer(true)}>
                {t(
                  profile.volunteerStatus === 'applied'
                    ? 'volunteer_pending'
                    : profile.volunteerStatus === 'rejected'
                      ? 'apply_again_volunteer'
                      : 'become_volunteer',
                )}
              </button>
            )}
          </>
        )}
        {own || profile.showStatsPublicly ? (
          <>
            <SectionHeader>{t('contribution_counter')}</SectionHeader>
            <div className="stats-grid">
              {[
                ['stat_sightings', profile.stats.sightings ?? 0],
                ['stat_cats_spotted', new Set((sightings ?? []).map((s) => s.catId)).size],
                ['stat_approved', profile.stats.approvedSightings ?? 0],
                ['stat_registered', profile.stats.catsRegistered ?? 0],
                ['stat_photos', profile.stats.photos ?? 0],
                ['stat_rescues', profile.stats.rescueReports ?? 0],
                ['stat_checkins', profile.stats.checkins ?? 0],
                ['stat_areas', profile.neighborhoods.length],
                ['stat_badges', profile.badges.length],
              ].map(([key, value]) => (
                <div className="stat-tile" key={key}>
                  <strong>{value}</strong>
                  <small>{t(String(key))}</small>
                </div>
              ))}
            </div>
            <MonthlyChart sightings={sightings ?? []} />
            <div className="row wrap">
              {profile.badges.slice(0, 6).map((key) => (
                <Icon key={key} name={CatMapIcons.badge(key)} size={46} />
              ))}
            </div>
          </>
        ) : (
          <p>{t('stats_private')}</p>
        )}
        <div className="segments">
          {['posts', 'sightings', 'cats', 'following'].map((value) => (
            <button
              className={value === tab ? 'selected' : ''}
              key={value}
              onClick={() => setTab(value)}
            >
              {t(
                value === 'posts'
                  ? 'tab_posts'
                  : value === 'sightings'
                    ? 'tab_my_sightings'
                    : value === 'cats'
                      ? 'tab_my_cats'
                      : 'following',
              )}
            </button>
          ))}
        </div>
        {tab === 'posts' &&
          (!posts ? (
            <Loading />
          ) : posts.length ? (
            posts
              .sort((a, b) => b.createdAt - a.createdAt)
              .map((p) => <PostCard post={p} key={p.id} />)
          ) : (
            <EmptyState text={t('no_posts_profile')} />
          ))}
        {tab === 'sightings' && sightings?.length === 0 && (
          <EmptyState text={t('no_sightings_yet')} />
        )}
        {tab === 'following' && followedCats?.length === 0 && (
          <EmptyState text={t('not_following_any')} />
        )}
        {tab === 'cats' && contributedCats?.length === 0 && (
          <EmptyState text={t('no_contributed_cats')} />
        )}
        {tab === 'sightings' &&
          (sightings
            ?.filter((s) => own || s.status !== 'rejected')
            .map((s) => (
              <Link className="card" to={'/sighting/' + s.id} key={s.id}>
                <h3>{s.catName}</h3>
                <p dir="auto">{s.notes}</p>
                <small>{dateTime(s.seenAt)}</small>
              </Link>
            )) ?? <Loading />)}
        {['cats', 'following'].includes(tab) &&
          (cats
            ?.filter(
              (c) =>
                !c.hidden &&
                !c.mergedInto &&
                (tab === 'cats'
                  ? c.contributors.includes(uid)
                  : profile.followedCats.includes(c.id)),
            )
            .map((c) => (
              <Link className="card row" key={c.id} to={'/cat/' + c.id}>
                <Avatar name={c.name} id={c.coverPhotoId} />
                {c.name}
              </Link>
            )) ?? <Loading />)}
      </div>
      {volunteer && (
        <Dialog title={t('become_volunteer')} onClose={() => setVolunteer(false)}>
          <p>{t('volunteer_intro')}</p>
          <Field
            label={t('why_volunteer')}
            value={motivation}
            onChange={setMotivation}
            multiline
            limit={500}
          />
          <Field
            label={t('experience')}
            value={experience}
            onChange={setExperience}
            multiline
            limit={500}
          />
          <Field label={t('area_you_cover')} value={area} onChange={setArea} />
          <button
            className="primary"
            disabled={action.busy || !motivation.trim()}
            onClick={() =>
              void action.run(async () => {
                await ModerationRepository.applyAsVolunteer(profile, motivation, experience, area);
                setVolunteer(false);
              }, t('saved'))
            }
          >
            {t('send')}
          </button>
        </Dialog>
      )}
    </>
  );
}
function ProfileEdit() {
  const { profile } = useApp(),
    navigate = useNavigate(),
    action = useAction(),
    [username, setUsername] = useState(''),
    [displayName, setName] = useState(''),
    [bio, setBio] = useState(''),
    [photoId, setPhoto] = useState('');
  useEffect(() => {
    if (profile) {
      setUsername(profile.username);
      setName(profile.displayName);
      setBio(profile.bio);
      setPhoto(profile.photoId);
    }
  }, [profile]);
  return (
    <>
      <AppTopBar title={t('edit_profile')} />
      <form
        className="screen"
        onSubmit={(e) => {
          e.preventDefault();
          if (profile)
            void action.run(async () => {
              await UserRepository.updateProfile(profile, username, displayName, bio, photoId);
              navigate('/profile');
            }, t('saved'));
        }}
      >
        <div className="profile-header">
          <Avatar name={displayName} id={photoId} size={110} />
          <label className="text-button file-button">
            {t('add_photo')}
            <input
              type="file"
              accept="image/*"
              disabled={action.busy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file && profile)
                  void action.run(async () =>
                    setPhoto(await ImageRepository.upload(profile.uid, await processPhoto(file))),
                  );
              }}
            />
          </label>
        </div>
        <Field label={t('display_name')} value={displayName} onChange={setName} limit={60} />
        <Field
          label={t('username')}
          value={username}
          onChange={setUsername}
          limit={20}
          pattern="[A-Za-z0-9_.]{3,20}"
          required
        />
        <Field label={t('bio')} value={bio} onChange={setBio} multiline limit={300} />
        <ErrorText text={action.error} />
        <button className="primary" disabled={action.busy}>
          {t('save')}
        </button>
      </form>
    </>
  );
}
function Achievements() {
  const { profile } = useApp();
  if (!profile) return <Loading />;
  return (
    <>
      <AppTopBar title={t('achievements')} />
      <div className="screen">
        <h2>{t('points_n', profile.points)}</h2>
        <p>{t('level_' + Levels.of(profile.points))}</p>
        {badges
          .slice()
          .sort(
            (a, b) =>
              Number(profile.badges.includes(b.key)) - Number(profile.badges.includes(a.key)),
          )
          .map((b) => (
            <div
              key={b.key}
              className={'card badge-card ' + (profile.badges.includes(b.key) ? 'earned' : '')}
            >
              <Icon name={CatMapIcons.badge(b.key)} size={52} />
              <div>
                <h3>{t('badge_' + b.key)}</h3>
                <p>{t('badge_' + b.key + '_desc')}</p>
                <progress value={Math.min(b.progress(profile), b.threshold)} max={b.threshold} />
                <small>
                  {Math.min(b.progress(profile), b.threshold)} / {b.threshold}
                </small>
              </div>
            </div>
          ))}
      </div>
    </>
  );
}
