import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useApp, useList, useAction } from '../../state';
import { AdoptionRepository } from '../../data/AdoptionRepository';
import { usePager, PagingFooter } from '../../data/usePager';
import { PostRepository, type PostAvailability } from '../../data/PostRepository';
import { PostFromDoc, AdoptionRequestFromDoc } from '../../data/model';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  SectionHeader,
  PostCard,
  Loading,
  EmptyState,
  Switch,
  StatusPill,
  Dialog,
  Field,
  Photo,
} from '../components/Components';
export default function AdoptionScreens() {
  const path = useLocation().pathname,
    { profile } = useApp(),
    action = useAction(),
    [showAdopted, setShowAdopted] = useState(false),
    [sent, setSent] = useState(false),
    [decision, setDecision] = useState<{ id: string; approve: boolean } | null>(null),
    [note, setNote] = useState(''),
    uid = profile?.uid ?? '';
  const requests = useList(
    () =>
      uid && path === '/adoption-requests'
        ? sent
          ? AdoptionRepository.sent(uid)
          : AdoptionRepository.received(uid)
        : null,
    AdoptionRequestFromDoc,
    'adoption-requests-' + uid + '-' + sent + '-' + path,
  );
  const availability = useRef(new Map<string, Promise<PostAvailability>>());
  const cleaned = useRef(new Set<string>());
  const [removed, setRemoved] = useState<Record<string, boolean>>({});
  useEffect(() => {
    let active = true;
    for (const r of requests ?? []) {
      if (!availability.current.has(r.postId))
        availability.current.set(r.postId, PostRepository.availability(r.postId));
      void availability.current.get(r.postId)!.then((state) => {
        if (!active || state !== 'gone') return;
        setRemoved((before) => (before[r.postId] ? before : { ...before, [r.postId]: true }));
        if (!cleaned.current.has(r.id)) {
          cleaned.current.add(r.id);
          void AdoptionRepository.delete(r).catch((error) =>
            console.info('Old request cleanup failed', error),
          );
        }
      });
    }
    return () => {
      active = false;
    };
  }, [requests]);
  const page = usePager(
    () => (path === '/adoption' ? PostRepository.feedQuery(['adoption']) : null),
    PostFromDoc,
    'adoption-' + path,
    25,
    PostRepository.feedQuery,
  );
  const posts = page.items;
  if (path === '/adoption') {
    const shown = posts?.filter(
      (p) => p.category === 'adoption' && (showAdopted || p.adoptionStatus !== 'adopted'),
    );
    return (
      <>
        <AppTopBar title={t('adoption')} />
        <div className="screen">
          <div className="hero-card">
            <h2>{t('adoption_hero_title')}</h2>
            <p>{t('adoption_hero_body')}</p>
          </div>
          <div className="row wrap">
            <Link className="outlined" to="/post-new?category=adoption">
              {t('list_for_adoption')}
            </Link>
            <Link className="text-button" to="/adoption-requests">
              {t('requests')}
            </Link>
          </div>
          <Switch label={t('show_adopted')} checked={showAdopted} onChange={setShowAdopted} />
          {!shown ? (
            <Loading />
          ) : shown.length ? (
            <div className="post-grid">
              {shown.map((p) => (
                <PostCard post={p} key={p.id} grid />
              ))}
            </div>
          ) : (
            <EmptyState text={t('no_adoption_listings')} />
          )}
          <PagingFooter page={page} visibleCount={shown?.length} />
        </div>
      </>
    );
  }
  const selected = requests?.find((r) => r.id === decision?.id);
  return (
    <>
      <AppTopBar title={t('adoption_requests')} />
      <div className="screen">
        <div className="segments">
          <button className={!sent ? 'selected' : ''} onClick={() => setSent(false)}>
            {t('received')}
          </button>
          <button className={sent ? 'selected' : ''} onClick={() => setSent(true)}>
            {t('sent')}
          </button>
        </div>
        {!requests ? (
          <Loading />
        ) : requests.length ? (
          requests
            .sort((a, b) => b.createdAt - a.createdAt)
            .map((r) => (
              <article className="card" key={r.id}>
                <Link to={'/post/' + r.postId} className="row">
                  <Photo id={r.postPhotoId} />
                  <h3 dir="auto">{r.postName}</h3>
                </Link>
                <StatusPill group="RequestStatus" value={r.status} />
                <SectionHeader>@{r.requesterName}</SectionHeader>
                <p dir="auto">{r.message}</p>
                <p dir="auto">{t('about_home_x', r.home)}</p>
                {r.ownerNote && <p dir="auto">{t('owner_note_x', r.ownerNote)}</p>}
                <small>{dateTime(r.createdAt)}</small>
                {removed[r.postId] && <p>{t('adoption_listing_removed')}</p>}
                {!removed[r.postId] && r.status === 'pending' && (
                  <div className="row">
                    {sent ? (
                      <button
                        className="text-button"
                        disabled={action.busy}
                        onClick={() =>
                          void action.run(() => AdoptionRepository.withdraw(r), t('updated'))
                        }
                      >
                        {t('withdraw_request')}
                      </button>
                    ) : (
                      <>
                        <button
                          className="primary"
                          onClick={() => {
                            setNote('');
                            setDecision({ id: r.id, approve: true });
                          }}
                        >
                          {t('approve')}
                        </button>
                        <button
                          className="outlined"
                          onClick={() => {
                            setNote('');
                            setDecision({ id: r.id, approve: false });
                          }}
                        >
                          {t('reject')}
                        </button>
                      </>
                    )}
                  </div>
                )}
              </article>
            ))
        ) : (
          <EmptyState text={t('no_requests')} />
        )}
      </div>
      {selected && !removed[selected.postId] && decision && (
        <Dialog
          title={t(decision.approve ? 'approve_request' : 'decline_request')}
          onClose={() => setDecision(null)}
        >
          <Field
            label={t('note_to_requester')}
            value={note}
            onChange={setNote}
            limit={500}
            multiline
          />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await AdoptionRepository.decide(selected, decision.approve, note);
                setDecision(null);
              }, t('updated'))
            }
          >
            {t('confirm')}
          </button>
        </Dialog>
      )}
    </>
  );
}
