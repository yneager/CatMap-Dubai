import { PostDetailHeader, postTitle } from '../components/PostDetailHeader';
import { VideoPlayer } from '../components/VideoPlayer';
import { lazy, Suspense, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  PostFromDoc,
  PostUpdateFromDoc,
  AdoptionRequestFromDoc,
} from '../../data/model';
import {
  usePostMatches,
  PostMatchesSection,
  ReunionBanner,
  ReuniteDialog,
  ReunitedCelebration,
  LinkSightingButton,
  ReportHistory,
} from '../matches/MatchScreens';
import * as MatchEngine from '../../ai/MatchEngine';
import { PostRepository } from '../../data/PostRepository';
import { ImageRepository } from '../../data/ImageRepository';
import { ModerationRepository } from '../../data/ModerationRepository';
import { AdoptionRepository } from '../../data/AdoptionRepository';
import { enums } from '../../data/model/Enums';
import { processPhoto } from '../../util/images';
import { sharePost } from '../../util/share';
import { dateTime, t } from '../../i18n';
import { useApp, useDoc, useList, useAction } from '../../state';
import { Dubai } from '../../data/Neighborhoods';
import {
  AppTopBar,
  Photo,
  FavoriteButton,
  StatusPill,
  SectionHeader,
  Field,
  Chips,
  Dialog,
  Loading,
  EmptyState,
  ErrorText,
  enumLabel,
} from '../components/Components';
import { useOlder } from '../../data/useOlder';
import { listQuery } from '../../data/Firestore';
import { orderBy } from 'firebase/firestore';
import { LocationPrivacy } from '../../data/Neighborhoods';
const Map = lazy(() => import('../map/CommunityMap'));
export default function PostDetailScreen() {
  const { id = '' } = useParams(),
    navigate = useNavigate(),
    { profile, toast } = useApp(),
    action = useAction();
  const post = useDoc(() => PostRepository.postFlow(id), PostFromDoc, 'post-' + id, true),
    liveUpdates = useList(() => PostRepository.updates(id), PostUpdateFromDoc, 'updates-' + id),
    postMatches = usePostMatches(id) ?? [];
  const history = useOlder(
    () => listQuery(`posts/${id}/updates`, orderBy('createdAt', 'desc')),
    PostUpdateFromDoc,
    'updates-' + id,
    30,
  );
  const updates = liveUpdates
    ? [
        ...new globalThis.Map([...history.items, ...liveUpdates].map((u) => [u.id, u])).values(),
      ].sort((a, b) => b.createdAt - a.createdAt)
    : undefined;
  const myRequest = useDoc(
    () =>
      profile && post?.category === 'adoption' && post.ownerId !== profile.uid
        ? AdoptionRepository.myRequestFor(id, profile.uid)
        : null,
    AdoptionRequestFromDoc,
    'post-adoption-' + id + '-' + profile?.uid + '-' + post?.category,
  );
  const [modal, setModal] = useState(''),
    [text, setText] = useState(''),
    [home, setHome] = useState(''),
    [kind, setKind] = useState('sighting'),
    [reason, setReason] = useState('other'),
    [file, setFile] = useState<File | null>(null),
    [position, setPosition] = useState({ lat: 0, lng: 0 }),
    [photoIndex, setPhotoIndex] = useState(0);
  if (post === undefined) return <Loading />;
  if (!post)
    return (
      <>
        <AppTopBar title={t('post_new')} />
        <EmptyState text={t('post_removed')} />
      </>
    );
  const own = post.ownerId === profile?.uid,
    mod = profile && ['admin', 'moderator'].includes(profile.role),
    searchPost = MatchEngine.isLost(post) || MatchEngine.isSighting(post);
  const open = (value: string) => {
    setText('');
    setFile(null);
    setPosition({ lat: 0, lng: 0 });
    setModal(value);
  };
  const saveUpdate = () =>
    action.run(
      async () => {
        if (!profile) return;
        const photoId = file
          ? await ImageRepository.upload(profile.uid, await processPhoto(file))
          : '';
        await PostRepository.addUpdate(
          post,
          kind,
          text,
          photoId,
          position.lat,
          position.lng,
          profile,
        );
        setModal('');
      },
      t(kind === 'sighting' ? 'sighting_thanks' : 'update_added'),
    );
  return (
    <>
      <AppTopBar title={postTitle(post)}>
        <FavoriteButton post={post} />
        <button
          className="text-button"
          onClick={() =>
            void action.run(async () => {
              const message = await sharePost(post);
              if (message) toast(message);
            })
          }
        >
          {t('share')}
        </button>
      </AppTopBar>
      <div className="photo-pager">
        {post.photoIds.length ? (
          <Photo
            id={post.photoIds[photoIndex] ?? post.photoIds[0]}
            thumb={false}
            alt={post.name}
            onClick={() => navigate('/photo/' + post.photoIds[photoIndex])}
          />
        ) : (
          <EmptyState text={t('no_photos')} />
        )}{' '}
        {post.photoIds.length > 1 && (
          <div className="pager-dots">
            {post.photoIds.map((p, i) => (
              <button
                key={p}
                aria-label={t('photo_n', i + 1)}
                aria-pressed={i === photoIndex}
                onClick={() => setPhotoIndex(i)}
              />
            ))}
          </div>
        )}
      </div>
      <div className="screen detail">
        <VideoPlayer path={post.videoPath} />
        <PostDetailHeader post={post} />
        <ReunionBanner
          post={post}
          credited={postMatches.find((m) => m.id === post.resolvedMatchId)}
        />
        <div className="chips facts">
          {[
            enumLabel('AnimalType', post.animalType),
            post.color,
            post.breed,
            enumLabel('CatSex', post.sex),
            enumLabel('AgeGroup', post.ageGroup),
            t('microchip') + ': ' + t(post.microchip),
          ]
            .filter(Boolean)
            .map((text, i) => (
              <span className="chip" key={i}>
                {text}
              </span>
            ))}
        </div>
        <p dir="auto">{post.description}</p>
        {post.features && <p dir="auto">{post.features}</p>}
        {post.category === 'adoption' && (
          <>
            <StatusPill group="AdoptionStatus" value={post.adoptionStatus} />
            <p dir="auto">{post.health}</p>
            <p>
              {t(post.vaccinated ? 'vaccinated' : 'not_vaccinated')} ·{' '}
              {t(post.neutered ? 'neutered' : 'not_neutered')}
            </p>
          </>
        )}
        <SectionHeader>{t('location')}</SectionHeader>
        <p>
          {Dubai.nameOf(post.neighborhood)} · {dateTime(post.lastSeenAt || post.createdAt)}
        </p>
        <div className="mini-map">
          <Suspense fallback={<Loading rows={1} />}>
            <Map
              compact
              interactive={false}
              center={[post.lng, post.lat]}
              radius={post.areaRadiusM}
              points={[
                {
                  id: post.id,
                  kind: 'post',
                  lat: post.lat,
                  lng: post.lng,
                  category: post.category,
                  animalType: post.animalType,
                },
              ]}
            />
          </Suspense>
        </div>
        {post.approximate && <p className="small muted">{t('post_location_approximate')}</p>}
        <Link className="link-row" to={'/user/' + post.ownerId}>
          @{post.ownerName}
        </Link>
        {!own && profile && (
          <Link className="primary" to={'/chat-with/' + post.ownerId + '?postId=' + id}>
            {t('message_owner')}
          </Link>
        )}
        {post.contactMethod === 'phone' && post.contactPhone && (
          <a className="outlined" href={'tel:' + post.contactPhone.replace(/[^+\d]/g, '')}>
            {post.contactPhone}
          </a>
        )}
        {post.contactMethod === 'email' && post.contactEmail && (
          <a className="outlined" href={'mailto:' + encodeURIComponent(post.contactEmail)}>
            {post.contactEmail}
          </a>
        )}
        {!own &&
          post.category === 'adoption' &&
          post.adoptionStatus !== 'adopted' &&
          myRequest === null && (
            <button className="primary" onClick={() => open('adoption')}>
              {t('request_to_adopt')}
            </button>
          )}
        {myRequest && (
          <div className="card">
            <StatusPill value={myRequest.status} group="RequestStatus" />
            {myRequest.ownerNote && <p dir="auto">{myRequest.ownerNote}</p>}
            {myRequest.status === 'pending' && (
              <button
                className="text-button"
                disabled={action.busy}
                onClick={() =>
                  void action.run(() => AdoptionRepository.withdraw(myRequest), t('updated'))
                }
              >
                {t('withdraw_request')}
              </button>
            )}
          </div>
        )}
        {own && (
          <>
            <SectionHeader>{t('manage_post')}</SectionHeader>
            <Link className="outlined" to={'/post-edit/' + id}>
              {t('edit_post')}
            </Link>
            <div className="chips">
              {post.status !== 'reunited' && ['lost', 'found'].includes(post.category) && (
                <button className="chip" onClick={() => open('reunited')}>
                  {t('mark_reunited')}
                </button>
              )}
              {post.status !== 'resolved' && (
                <button
                  className="chip"
                  onClick={() =>
                    void action.run(() => PostRepository.setStatus(post, 'resolved', profile!))
                  }
                >
                  {t('mark_resolved')}
                </button>
              )}
              {post.status !== 'open' && (
                <button
                  className="chip"
                  onClick={() =>
                    void action.run(() => PostRepository.setStatus(post, 'open', profile!))
                  }
                >
                  {t('pstatus_open')}
                </button>
              )}
              {post.status === 'open' && ['lost', 'found'].includes(post.category) && (
                <button
                  className="chip"
                  onClick={() =>
                    void action.run(() => PostRepository.stillLooking(post), t('updated'))
                  }
                >
                  {t('still_looking')}
                </button>
              )}
            </div>
            {post.category === 'adoption' && (
              <Chips
                options={enums.AdoptionStatus}
                value={post.adoptionStatus}
                onChange={(status) =>
                  void action.run(() => PostRepository.setAdoptionStatus(post, status))
                }
              />
            )}
            <button className="text-button danger" onClick={() => open('delete')}>
              {t('delete_post')}
            </button>
          </>
        )}
        {(own || post.status === 'open') && (
          <div className="row wrap">
            <button
              className="outlined"
              onClick={() => {
                setKind('sighting');
                open('update');
              }}
            >
              {t('i_saw_this_animal')}
            </button>
            <button
              className="outlined"
              onClick={() => {
                setKind(own ? 'progress' : 'sighting');
                open('update');
              }}
            >
              {t('add_update')}
            </button>
          </div>
        )}
        <Link className="outlined" to={'/poster/' + id}>
          {t('make_poster')}
        </Link>
        {!own && (
          <button className="text-button" onClick={() => open('report')}>
            {t('report_post')}
          </button>
        )}
        {mod && !own && (
          <button
            className="text-button"
            onClick={() =>
              void action.run(() => PostRepository.setHidden(post, !post.hidden, profile!))
            }
          >
            {t(post.hidden ? 'unhide_post' : 'hide_post')}
          </button>
        )}
        {own && post.category === 'adoption' && (
          <Link className="outlined" to="/adoption-requests">
            {t('view_adoption_requests')}
          </Link>
        )}
        {(post.lat || post.lng) !== 0 && (
          <a
            className="outlined"
            href={'https://www.google.com/maps/dir/?api=1&destination=' + post.lat + ',' + post.lng}
            target="_blank"
            rel="noopener noreferrer"
          >
            {t('directions')}
          </a>
        )}
        <SectionHeader>{t('updates_and_sightings')}</SectionHeader>
        {!history.done && (liveUpdates?.length ?? 0) >= 30 && (
          <button
            className="text-button"
            disabled={history.busy}
            onClick={() => void history.load(`posts/${id}/updates/${liveUpdates!.at(-1)!.id}`)}
          >
            {t('load_more')}
          </button>
        )}
        {!updates ? (
          <Loading rows={1} />
        ) : updates.length ? (
          updates.map((u) => (
            <article className="update card" key={u.id}>
              <div className="row between">
                <strong>{enumLabel('UpdateKind', u.kind)}</strong>
                <small>{dateTime(u.createdAt)}</small>
              </div>
              <p dir="auto">{u.text}</p>
              {u.photoId && <Photo id={u.photoId} />}
              <p>
                @{u.byName} {Dubai.nameOf(u.neighborhood)}
              </p>
              {(u.byUid === profile?.uid || own || mod) && (
                <button
                  className="text-button danger"
                  onClick={() => void action.run(() => PostRepository.deleteUpdate(post, u))}
                >
                  {t('delete')}
                </button>
              )}
            </article>
          ))
        ) : (
          <EmptyState text={t('no_updates_yet')} />
        )}{' '}
        {searchPost && <PostMatchesSection post={post} />}
        {post.category === 'lost' && !own && post.status === 'open' && (
          <LinkSightingButton lost={post} />
        )}
        {['lost', 'found', 'spotted'].includes(post.category) && updates && (
          <ReportHistory post={post} updates={updates} matches={postMatches} />
        )}
      </div>
      {modal === 'update' && (
        <Dialog title={t('add_update')} onClose={() => setModal('')}>
          <Chips options={enums.UpdateKind} value={kind} onChange={setKind} />
          <Field label={t('note')} value={text} onChange={setText} limit={1000} multiline />
          <label className="outlined file-button">
            {t('add_photo')}
            <input
              type="file"
              accept="image/*"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          {file && <p>{file.name}</p>}
          <label>
            <input
              type="checkbox"
              checked={!!(position.lat || position.lng)}
              onChange={(e) => {
                if (!e.target.checked) setPosition({ lat: 0, lng: 0 });
                else
                  navigator.geolocation?.getCurrentPosition(
                    (p) =>
                      setPosition({
                        lat: LocationPrivacy.approximate(p.coords.latitude),
                        lng: LocationPrivacy.approximate(p.coords.longitude),
                      }),
                    () => toast(t('web_location_denied')),
                  );
              }}
            />
            {t('add_my_location')}
          </label>
          {!!(position.lat || position.lng) && <p>{t('location_added')}</p>}
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy || (!text.trim() && !file)}
            onClick={() => void saveUpdate()}
          >
            {t('save')}
          </button>
        </Dialog>
      )}
      {modal === 'adoption' && (
        <Dialog title={t('request_to_adopt')} onClose={() => setModal('')}>
          <Field
            label={t('adopt_message')}
            value={text}
            onChange={setText}
            limit={1000}
            multiline
          />
          <Field label={t('adopt_home')} value={home} onChange={setHome} limit={600} multiline />
          <button
            className="primary"
            disabled={action.busy || !text.trim()}
            onClick={() =>
              void action.run(async () => {
                await AdoptionRepository.request(post, text, home, profile!);
                setModal('');
              }, t('adopt_request_sent'))
            }
          >
            {t('send')}
          </button>
          <ErrorText text={action.error} />
        </Dialog>
      )}
      {modal === 'report' && (
        <Dialog title={t('report_post')} onClose={() => setModal('')}>
          <Chips options={enums.ReportReason} value={reason} onChange={setReason} />
          <Field
            label={t('details_optional')}
            value={text}
            onChange={setText}
            limit={500}
            multiline
          />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await ModerationRepository.reportContent(
                  'post',
                  post.id,
                  post.name,
                  reason,
                  text,
                  profile!,
                );
                setModal('');
              }, t('saved'))
            }
          >
            {t('send')}
          </button>
        </Dialog>
      )}
      {modal === 'reunited' && (
        <ReuniteDialog
          post={post}
          matches={postMatches}
          onClose={() => setModal('')}
          onDone={() => setModal('celebrate')}
        />
      )}
      {modal === 'celebrate' && <ReunitedCelebration post={post} onClose={() => setModal('')} />}
      {modal === 'delete' && (
        <Dialog title={t('delete_post')} onClose={() => setModal('')}>
          <p>{t('delete_post_body')}</p>
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await PostRepository.delete(post);
                navigate('/my-posts');
                setModal('');
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
