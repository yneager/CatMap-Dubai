// The lost-to-reunited workflow on the web (same documents and wording as Android's ui/matches):
// possible matches on a report, the side-by-side review, linking a sighting, the reunion and the report's history.
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  PostFromDoc,
  PostMatchFromDoc,
  type Post,
  type PostMatch,
  type PostUpdate,
} from '../../data/model';
import { PostRepository } from '../../data/PostRepository';
import { MatchRepository, visibleMatch } from '../../data/MatchRepository';
import { AiServer } from '../../data/AiServer';
import { Dubai } from '../../data/Neighborhoods';
import * as Engine from '../../ai/MatchEngine';
import { dateTime, distanceText, t } from '../../i18n';
import { useApp, useDoc, useList, useAction } from '../../state';
import {
  AppTopBar,
  Photo,
  StatusPill,
  SectionHeader,
  Dialog,
  Loading,
  EmptyState,
  ErrorText,
  enumLabel,
} from '../components/Components';

const day = (ms: number) => new Date(ms).toLocaleDateString();
export const animalName = (p: Post | null | undefined) =>
  p?.name || enumLabel('AnimalType', p?.animalType ?? 'other');
const daysText = (d: number) =>
  Math.abs(d) < 1 ? t('days_less_than_one') : t('days_n', Math.round(Math.abs(d)));

/** One sentence per reason, in plain words. */
export function matchReasons(m: PostMatch) {
  const k: Record<string, () => string> = {
    photo_very_similar: () => t('reason_photo_very_similar'),
    photo_similar: () => t('reason_photo_similar'),
    photo_somewhat_similar: () => t('reason_photo_somewhat_similar'),
    photo_different: () => t('reason_photo_different'),
    colour_only: () => t('reason_colour_only'),
    no_photo: () => t('reason_no_photo'),
    same_colour_words: () => t('reason_same_colour_words'),
    near: () => t('reason_near', distanceText(m.distanceKm)),
    in_range: () => t('reason_in_range', distanceText(m.distanceKm)),
    far: () => t('reason_far', distanceText(m.distanceKm)),
    recent: () => t('reason_recent', daysText(m.daysApart)),
    long_after: () => t('reason_long_after', daysText(m.daysApart)),
    before_lost: () => t('reason_before_lost'),
    like_rejected: () => t('reason_like_rejected'),
    reporter_rejected: () => t('reason_reporter_rejected'),
    reporter_linked: () => t('reason_reporter_linked'),
  };
  return m.reasons.flatMap((r) => (k[r] ? [k[r]()] : []));
}

export function answerLine(m: PostMatch, viewerIsOwner: boolean) {
  if (m.ownerState === 'reunited') return t('match_reunited_badge');
  if (viewerIsOwner) {
    if (m.ownerState === 'maybe') return t('match_you_said_maybe');
    if (m.ownerState === 'not_mine') return t('match_you_said_no');
    if (m.reporterState === 'confirmed') return t('match_reporter_said_same');
    if (m.reporterState === 'not_same') return t('reason_reporter_rejected');
  } else {
    if (m.ownerState === 'maybe') return t('match_owner_said_maybe');
    if (m.ownerState === 'not_mine') return t('match_owner_said_no');
    if (m.ownerState === 'need_info') return t('match_owner_wants_info');
  }
  return null;
}

function usePost(id: string) {
  return useDoc(() => (id ? PostRepository.postFlow(id) : null), PostFromDoc, 'match-post-' + id);
}

function SideBySide({
  lost,
  sighting,
  onOpen,
}: {
  lost?: Post | null;
  sighting?: Post | null;
  onOpen?: (id: string) => void;
}) {
  return (
    <div className="match-pair">
      {[
        [lost, t('match_lost_photo')],
        [sighting, t('match_sighting_photo')],
      ].map(([p, label], i) => {
        const photo = (p as Post | null | undefined)?.photoIds[0] ?? '';
        return (
          <figure key={i}>
            <Photo
              id={photo}
              alt={label as string}
              onClick={onOpen && photo ? () => onOpen(photo) : undefined}
            />
            <figcaption className="small muted">{label as string}</figcaption>
          </figure>
        );
      })}
    </div>
  );
}

function MatchCard({ m, viewerIsOwner }: { m: PostMatch; viewerIsOwner: boolean }) {
  const lost = usePost(m.lostPostId),
    sighting = usePost(m.sightingPostId);
  const other = viewerIsOwner ? sighting : lost;
  const headline = viewerIsOwner
    ? t('match_seen_near', Dubai.nameOf(sighting?.neighborhood ?? '') || '-')
    : t('match_found_headline', animalName(lost)) +
      ' — ' +
      t('match_lost_near', Dubai.nameOf(lost?.neighborhood ?? '') || '-');
  const answer = answerLine(m, viewerIsOwner);
  return (
    <Link className="card match-card" to={'/match/' + m.id}>
      <SideBySide lost={lost} sighting={sighting} />
      <div className="row">
        <StatusPill group="MatchTier" value={m.tier} />
        <strong>{headline}</strong>
      </div>
      <p className="small muted">
        {[
          other ? day(other.lastSeenAt || other.createdAt) : '',
          t('match_distance_apart', distanceText(m.distanceKm)),
        ]
          .filter(Boolean)
          .join(' · ')}
      </p>
      {answer && <p className="small strong">{answer}</p>}
      <span className="link">{t('match_review')}</span>
    </Link>
  );
}

/**
 * The person's matches on a post whose other post still exists (Android's MatchRepository.forPost). Deleting a post
 * removes its matches, but not always (a moderator's delete, an older app), so each other post is checked once and a
 * pair whose post is gone is dropped and deleted.
 */
export function usePostMatches(postId: string) {
  const { profile } = useApp();
  const uid = profile?.uid ?? '';
  const all = useList(
    () => (uid ? MatchRepository.mine(uid) : null),
    PostMatchFromDoc,
    'matches-' + uid,
  );
  const [gone, setGone] = useState<Record<string, boolean>>({});
  const asked = useRef(new Set<string>());
  const here = all?.filter((m) => m.lostPostId === postId || m.sightingPostId === postId);
  const other = (m: PostMatch) => (m.lostPostId === postId ? m.sightingPostId : m.lostPostId);
  useEffect(() => {
    for (const m of here ?? []) {
      const id = other(m);
      if (asked.current.has(id)) continue;
      asked.current.add(id);
      void PostRepository.exists(id).then((exists) => setGone((g) => ({ ...g, [id]: !exists })));
    }
    for (const m of here ?? [])
      if (gone[other(m)] && !asked.current.has(m.id)) {
        asked.current.add(m.id);
        MatchRepository.drop(m);
      }
  });
  if (!here) return undefined;
  // Until the first answers arrive, nothing (rather than a list that loses a card a moment later).
  if (here.length && !here.some((m) => other(m) in gone)) return undefined;
  return here.filter((m) => gone[other(m)] === false);
}

/** "Possible matches" on a lost report or sighting, for the two people involved. */
export function PostMatchesSection({ post }: { post: Post }) {
  const { profile } = useApp();
  const uid = profile?.uid ?? '';
  const all = usePostMatches(post.id);
  const [pcOnline, setPcOnline] = useState<boolean | null>(null),
    [showWeak, setShowWeak] = useState(false);
  const isLost = post.category === 'lost',
    isOwner = post.ownerId === uid;
  useEffect(() => {
    void AiServer.health().then((h) => setPcOnline(!!h));
  }, [post.id]);
  // While the PC is off, the owner's device pairs the report with sightings posted since (sighting first).
  useEffect(() => {
    if (pcOnline === false && isOwner && profile)
      void MatchRepository.candidates(post)
        .then((feed) => MatchRepository.reconcile(post, feed, profile))
        .catch(() => 0);
  }, [pcOnline, isOwner, profile, post]);
  if (!all) return null;
  const visible = all
    .filter(
      (m) =>
        visibleMatch(m) &&
        !(m.lostOwnerId === uid && m.ownerState === 'not_mine') &&
        !(m.sightingOwnerId === uid && m.reporterState === 'not_same'),
    )
    .sort((a, b) => b.score - a.score);
  const shown = visible.filter(
    (m) => m.tier !== 'weak' || m.ownerState !== 'new' || m.reporterState !== 'new',
  );
  const weak = visible.filter((m) => !shown.includes(m));
  if (!visible.length && !isOwner) return null;
  const fresh = Date.now() - post.createdAt < 3 * 60000;
  return (
    <section aria-label={t('possible_post_matches')}>
      <SectionHeader>{t('possible_post_matches')}</SectionHeader>
      {!visible.length && fresh && pcOnline ? (
        <p role="status">{t(isLost ? 'matches_checking' : 'matches_checking_sighting')}</p>
      ) : !visible.length ? (
        <p className="muted">{t(isLost ? 'matches_none_lost' : 'matches_none_sighting')}</p>
      ) : null}
      {visible.some((m) => m.source === 'device') && (
        <p className="small muted">{t('matches_offline')}</p>
      )}
      {[...shown, ...(showWeak ? weak : [])].map((m) => (
        <MatchCard key={m.id} m={m} viewerIsOwner={m.lostOwnerId === uid} />
      ))}
      {weak.length > 0 && !showWeak && (
        <button className="text-button" onClick={() => setShowWeak(true)}>
          {t('show_weak_matches', weak.length)}
        </button>
      )}
    </section>
  );
}

/** The report's reunion: when, how, and whether it was confirmed by the reporter or a moderator. */
export function ReunionBanner({ post, credited }: { post: Post; credited?: PostMatch }) {
  if (post.status !== 'reunited') return null;
  return (
    <div className="card reunion-banner" role="status">
      <strong>{t('reunited_banner', day(post.resolvedAt || post.updatedAt))}</strong>
      {post.resolution === 'catmap' && <p className="small">{t('reunited_via_catmap')}</p>}
      {post.resolution === 'home' && <p className="small">{t('reunited_home')}</p>}
      <p className="small muted">
        {t(
          credited?.verifiedBy
            ? 'reunion_verified'
            : credited?.reporterState === 'confirmed'
              ? 'reunion_confirmed'
              : 'reunion_owner_reported',
        )}
      </p>
    </div>
  );
}

export function ReuniteDialog({
  post,
  matches,
  onClose,
  onDone,
}: {
  post: Post;
  matches: PostMatch[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { profile } = useApp(),
    action = useAction();
  const candidates = matches.filter((m) => m.lostPostId === post.id && m.ownerState !== 'not_mine');
  const [how, setHow] = useState(candidates.length ? 'catmap' : 'home'),
    [chosen, setChosen] = useState(
      candidates.find((m) => m.ownerState === 'maybe')?.id ?? candidates[0]?.id ?? '',
    );
  return (
    <Dialog title={t('reunite_title', animalName(post))} onClose={onClose}>
      <fieldset>
        <legend>{t('reunite_how')}</legend>
        {['catmap', 'home', 'other'].map((r) => (
          <label className="radio-row" key={r}>
            <input type="radio" name="how" checked={how === r} onChange={() => setHow(r)} />{' '}
            {t('resolution_' + r)}
          </label>
        ))}
      </fieldset>
      {how === 'catmap' && candidates.length > 0 && (
        <fieldset>
          <legend>{t('reunite_which')}</legend>
          {candidates.map((m) => (
            <label className="radio-row" key={m.id}>
              <input
                type="radio"
                name="which"
                checked={chosen === m.id}
                onChange={() => setChosen(m.id)}
              />
              <SightingLabel id={m.sightingPostId} />
            </label>
          ))}
        </fieldset>
      )}
      <ErrorText text={action.error} />
      <button
        className="primary"
        disabled={action.busy || !profile}
        onClick={() =>
          void action
            .run(() =>
              MatchRepository.reunite(
                post,
                candidates.find((m) => m.id === chosen) ?? null,
                how,
                profile!,
              ),
            )
            .then((ok) => ok && onDone())
        }
      >
        {t('reunite_confirm')}
      </button>
    </Dialog>
  );
}

function SightingLabel({ id }: { id: string }) {
  const s = usePost(id);
  return <span>{'@' + (s?.ownerName ?? '') + ' · ' + Dubai.nameOf(s?.neighborhood ?? '')}</span>;
}

export function ReunitedCelebration({ post, onClose }: { post: Post; onClose: () => void }) {
  const text = t('reunited_share_text', animalName(post));
  return (
    <Dialog title={t('reunited_title', animalName(post))} onClose={onClose}>
      <div className="celebrate">
        <Photo id={post.photoIds[0] ?? ''} thumb={false} alt={animalName(post)} />
        <p>{t('reunited_body')}</p>
        <button
          className="outlined"
          onClick={() =>
            navigator.share
              ? navigator.share({ text }).catch(() => {})
              : navigator.clipboard?.writeText(text)
          }
        >
          {t('reunited_share')}
        </button>
        <button className="primary" onClick={onClose}>
          {t('close')}
        </button>
      </div>
    </Dialog>
  );
}

/** "I posted a sighting of this animal": links one of the person's open sightings to this lost report. */
export function LinkSightingButton({ lost }: { lost: Post }) {
  const { profile } = useApp(),
    navigate = useNavigate(),
    action = useAction(),
    [open, setOpen] = useState(false);
  const mine = useList(
    () => (profile ? PostRepository.postsBy(profile.uid) : null),
    PostFromDoc,
    'my-sightings-' + profile?.uid,
  );
  const sightings = (mine ?? []).filter(
    (p) => Engine.isSighting(p) && p.status === 'open' && p.animalType === lost.animalType,
  );
  if (!sightings.length) return null;
  return (
    <>
      <button className="outlined" onClick={() => setOpen(true)}>
        {t('link_my_sighting')}
      </button>
      {open && (
        <Dialog title={t('link_my_sighting')} onClose={() => setOpen(false)}>
          <p>{t('link_my_sighting_body')}</p>
          {sightings.map((s) => (
            <button
              key={s.id}
              className="outlined"
              disabled={action.busy}
              onClick={() =>
                void action.run(
                  async () =>
                    navigate('/match/' + (await MatchRepository.linkSighting(lost, s, profile!))),
                  t('match_told_owner'),
                )
              }
            >
              {(s.name || enumLabel('AnimalType', s.animalType)) +
                ' · ' +
                Dubai.nameOf(s.neighborhood)}
            </button>
          ))}
          <ErrorText text={action.error} />
        </Dialog>
      )}
    </>
  );
}

export function ReportHistory({
  post,
  updates,
  matches,
}: {
  post: Post;
  updates: PostUpdate[];
  matches: PostMatch[];
}) {
  const events: { at: number; text: string }[] = [
    {
      at: post.createdAt,
      text: t(post.category === 'lost' ? 'history_reported_lost' : 'history_posted'),
    },
    ...updates.map((u) => ({ at: u.createdAt, text: t('history_update', '@' + u.byName) })),
    ...matches
      .filter(visibleMatch)
      .flatMap((m) => [
        { at: m.createdAt, text: t('history_match', enumLabel('MatchTier', m.tier)) },
        ...(m.reviewedAt > 0 && answerLine(m, true)
          ? [{ at: m.reviewedAt, text: answerLine(m, true)! }]
          : []),
      ]),
    ...(post.status !== 'open' && post.resolvedAt > 0
      ? [{ at: post.resolvedAt, text: t('history_closed', enumLabel('PostStatus', post.status)) }]
      : []),
  ].sort((a, b) => b.at - a.at);
  return (
    <section>
      <SectionHeader>{t('report_history')}</SectionHeader>
      <ol className="history">
        {events.map((e, i) => (
          <li key={i}>
            <span>{e.text}</span>
            <time className="small muted">{dateTime(e.at)}</time>
          </li>
        ))}
      </ol>
    </section>
  );
}

/** Compare a lost report and a sighting, with the answers each person may give. */
export default function MatchReviewScreen() {
  const { id = '' } = useParams(),
    navigate = useNavigate(),
    { profile } = useApp(),
    action = useAction();
  const m = useDoc(() => MatchRepository.matchFlow(id), PostMatchFromDoc, 'match-' + id);
  const lost = usePost(m?.lostPostId ?? ''),
    sighting = usePost(m?.sightingPostId ?? '');
  // Opened from an old alert after one of the two posts was deleted: the pair is gone too.
  const [orphaned, setOrphaned] = useState<boolean | null>(null);
  const matchId = m?.id;
  useEffect(() => {
    if (m) void MatchRepository.orphaned(m).then(setOrphaned);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- once per match
  }, [matchId]);
  if (m === undefined || (m && orphaned === null)) return <Loading />;
  if (!m || orphaned)
    return (
      <>
        <AppTopBar title="" />
        <EmptyState text={t('match_unavailable')} />
      </>
    );
  const uid = profile?.uid,
    isOwner = uid === m.lostOwnerId,
    isReporter = uid === m.sightingOwnerId,
    isMod = profile && ['admin', 'moderator'].includes(profile.role);
  const chat = (info: boolean) =>
    action.run(async () => {
      const chatId = info
        ? await MatchRepository.requestInfo(m, lost ?? null, sighting ?? null, profile!)
        : await MatchRepository.openChat(m, lost ?? null, sighting ?? null, profile!);
      navigate('/chat/' + chatId);
    });
  const more = [...(lost?.photoIds.slice(1) ?? []), ...(sighting?.photoIds.slice(1) ?? [])];
  const rows: [string, string, string][] = [
    [t('colour'), lost?.color ?? '', sighting?.color ?? ''],
    [t('breed'), lost?.breed ?? '', sighting?.breed ?? ''],
    [
      t('sex'),
      lost ? enumLabel('CatSex', lost.sex) : '',
      sighting ? enumLabel('CatSex', sighting.sex) : '',
    ],
    [
      t('age'),
      lost ? enumLabel('AgeGroup', lost.ageGroup) : '',
      sighting ? enumLabel('AgeGroup', sighting.ageGroup) : '',
    ],
    [t('identifying_features'), lost?.features ?? '', sighting?.features ?? ''],
    [t('area'), Dubai.nameOf(lost?.neighborhood ?? ''), Dubai.nameOf(sighting?.neighborhood ?? '')],
    [
      t('match_when'),
      lost ? dateTime(lost.lastSeenAt || lost.createdAt) : '',
      sighting ? dateTime(sighting.lastSeenAt || sighting.createdAt) : '',
    ],
  ];
  const answer = answerLine(m, isOwner);
  const openPhoto = (photo: string) => navigate('/photo/' + photo);
  return (
    <>
      <AppTopBar title={t('match_review_title')} />
      <div className="screen match-review">
        <div className="row">
          <StatusPill group="MatchTier" value={m.tier} />
          <h2>{t('match_found_headline', animalName(lost))}</h2>
        </div>
        <SideBySide lost={lost} sighting={sighting} onOpen={openPhoto} />
        {more.length > 0 && (
          <div className="row wrap">
            {more.map((p) => (
              <Photo
                key={p}
                id={p}
                alt={t('open_photo')}
                className="match-thumb"
                onClick={() => openPhoto(p)}
              />
            ))}
          </div>
        )}
        <p className="small muted">
          {t(m.method === 'embedding' ? 'match_ai_label' : 'match_device_label')}
        </p>
        <div className="card">
          <h3>{t('match_why')}</h3>
          <ul>
            {matchReasons(m).map((r, i) => (
              <li key={i}>{r}</li>
            ))}
          </ul>
          <p className="small muted">{t('match_not_proof')}</p>
        </div>
        <SectionHeader>{t('match_compare_details')}</SectionHeader>
        <table className="compare">
          <thead>
            <tr>
              <th />
              <th>{t('match_lost_photo')}</th>
              <th>{t('match_sighting_photo')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([label, a, b]) => (
              <tr key={label}>
                <th scope="row">{label}</th>
                <td>{a || '—'}</td>
                <td>{b || '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <p>{t('match_compare_features')}</p>
        {lost?.features && <p className="strong">{t('match_owner_features', lost.features)}</p>}
        {answer && <p className="strong primary-text">{answer}</p>}
        <ErrorText text={action.error} />
        {isOwner && lost?.status === 'open' && (
          <div className="column">
            <button
              className="primary"
              disabled={action.busy || m.ownerState === 'maybe'}
              onClick={() =>
                void action.run(() => MatchRepository.setOwnerState(m, 'maybe'), t('saved'))
              }
            >
              {t('match_action_maybe')}
            </button>
            <button
              className="outlined"
              disabled={action.busy || m.ownerState === 'not_mine'}
              onClick={() =>
                void action.run(
                  () => MatchRepository.setOwnerState(m, 'not_mine'),
                  t('match_rejected_done'),
                )
              }
            >
              {t('match_action_not_mine')}
            </button>
            <button className="outlined" disabled={action.busy} onClick={() => void chat(true)}>
              {t('match_action_info')}
            </button>
            {m.ownerState !== 'new' && (
              <button
                className="text-button"
                onClick={() => void action.run(() => MatchRepository.setOwnerState(m, 'new'))}
              >
                {t('match_undo')}
              </button>
            )}
          </div>
        )}
        {isReporter && (
          <div className="column">
            <button
              className="primary"
              disabled={action.busy || m.reporterState === 'confirmed'}
              onClick={() =>
                void action.run(
                  () => MatchRepository.setReporterState(m, 'confirmed'),
                  t('match_told_owner'),
                )
              }
            >
              {t('match_action_same')}
            </button>
            <button
              className="outlined"
              disabled={action.busy || m.reporterState === 'not_same'}
              onClick={() =>
                void action.run(() => MatchRepository.setReporterState(m, 'not_same'), t('saved'))
              }
            >
              {t('match_action_not_same')}
            </button>
          </div>
        )}
        {(isOwner || isReporter) && (
          <button className="primary" disabled={action.busy} onClick={() => void chat(false)}>
            {t('match_message')}
          </button>
        )}
        <div className="row">
          <Link className="outlined" to={'/post/' + m.lostPostId}>
            {t('match_view_lost')}
          </Link>
          <Link className="outlined" to={'/post/' + m.sightingPostId}>
            {t('match_view_sighting')}
          </Link>
        </div>
        {isMod && m.ownerState === 'reunited' && (
          <button
            className="outlined"
            disabled={!!m.verifiedBy || action.busy}
            onClick={() =>
              void action.run(() => MatchRepository.verifyReunion(m, profile!), t('saved'))
            }
          >
            {t('match_verify')}
          </button>
        )}
        <p className="small muted">{t('match_suggested_at', dateTime(m.createdAt))}</p>
      </div>
    </>
  );
}
