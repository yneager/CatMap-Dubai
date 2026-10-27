import { ReportButton } from '../components/ReportButton';
import EditSighting from './EditSighting';
import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useDoc, useAction, useSettings } from '../../state';
import { CatRepository, type SightingDraft } from '../../data/CatRepository';
import { SightingFromDoc, type Cat, type PrivateLocation } from '../../data/model';
import { AiServer, MAX_REVIEW_CATS } from '../../data/AiServer';
import { enums } from '../../data/model/Enums';
import { ImageRepository } from '../../data/ImageRepository';
import { LocationRepository } from '../../data/LocationRepository';
import { useAsyncList } from '../../data/useAsyncList';
import { useDebounced } from '../../data/usePager';
import { BreedTraits } from '../components/BreedTraits';
import { describe } from '../../ai/CatAnalyzer';
import { nameIdeas } from '../../ai/NameIdeas';
import { Similarity } from '../../ai/Similarity';
import { processPhoto, type ProcessedPhoto } from '../../util/images';
import { Dubai, distanceKm } from '../../data/Neighborhoods';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  SectionHeader,
  Field,
  Chips,
  Switch,
  Loading,
  EmptyState,
  Photo,
  ErrorText,
  StatusPill,
  Dialog,
} from '../components/Components';
import { LocationField } from '../components/LocationField';
import { toggle } from '../explore/Filtering';
const draftDefault: SightingDraft = {
  existingCat: null,
  newCatName: '',
  description: '',
  distinctiveMarks: '',
  sex: 'unknown',
  coatColor: 'unknown',
  pattern: 'unknown',
  size: 'unknown',
  breedHints: [],
  photoIds: [],
  features: [],
  lat: 0,
  lng: 0,
  accuracy: 0,
  locationSource: 'manual',
  seenAt: Date.now(),
  healthTags: [],
  behaviorTags: [],
  earTip: 'unsure',
  possiblyNotNeutered: false,
  needsMedical: false,
  notes: '',
  matchScore: 0,
  aiSummary: '',
  suggestedCatIds: [],
  rejectedCatIds: [],
};
export default function SightingScreens() {
  const path = useLocation().pathname;
  return path === '/sighting-new' ? (
    <NewSighting />
  ) : path.startsWith('/sighting-edit/') ? (
    <EditSighting />
  ) : (
    <SightingDetail />
  );
}
function NewSighting() {
  const { profile } = useApp(),
    settings = useSettings(),
    action = useAction(),
    navigate = useNavigate(),
    params = new URLSearchParams(useLocation().search),
    preset = params.get('catId');
  const [draft, setDraft] = useState<SightingDraft>(() => {
      const fix = LocationRepository.get();
      return {
        ...draftDefault,
        seenAt: Date.now(),
        ...(fix ? { ...fix, accuracy: fix.accuracy ?? 0, locationSource: 'gps' } : {}),
      };
    }),
    [step, setStep] = useState(0),
    [photos, setPhotos] = useState<{ data: ProcessedPhoto; url: string; id?: string }[]>([]),
    [processing, setProcessing] = useState(false),
    [manualCoat, setManualCoat] = useState(false);
  const positionKey = useDebounced(draft.lat + ',' + draft.lng);
  const cats = useAsyncList(
    () =>
      preset
        ? CatRepository.byIds([preset])
        : draft.lat || draft.lng
          ? CatRepository.near(draft.lat, draft.lng, 3)
          : CatRepository.newest(300),
    'sighting-cats-' + preset + '-' + positionKey,
  );
  const change = (patch: Partial<SightingDraft>) => setDraft((d) => ({ ...d, ...patch }));
  useEffect(() => {
    const cat = cats?.find((c) => c.id === preset);
    if (cat)
      setDraft((d) => ({
        ...d,
        existingCat: cat,
        coatColor: cat.coatColor,
        pattern: cat.pattern,
        size: cat.size,
      }));
  }, [preset, cats]);
  const candidates = (cats ?? [])
    .filter((c) => !c.hidden && !c.mergedInto && c.features.length)
    .map((cat) => ({
      cat,
      score: Similarity.score(
        draft.features,
        cat.features,
        draft.coatColor,
        cat.coatColor,
        draft.pattern,
        cat.pattern,
        draft.lat ? distanceKm(cat.lat, cat.lng, draft.lat, draft.lng) : null,
        (draft.accuracy + 399) / 1000,
        Math.max(0, draft.seenAt - cat.lastSeen) / 86400000,
      ),
    }))
    .filter((c) => c.score >= 0.45)
    .sort((a, b) => b.score - a.score)
    .slice(0, 6);
  const visibleCandidates = candidates.filter((c) => !draft.rejectedCatIds.includes(c.cat.id));
  // Photo review (docs/MATCHING_REVIEW_V1.md, NewSightingViewModel.startReview): the AI server ranks the nearest cats
  // with two or more photos, by distance only. Moderators with the switch on; once per first photo and place.
  const [review, setReview] = useState<{ cat: Cat; photoIds: string[]; distance: number }[] | null>(
      null,
    ),
    [reviewing, setReviewing] = useState(false),
    [reviewFailed, setReviewFailed] = useState(false),
    [reviewUnavailable, setReviewUnavailable] = useState(0),
    [zoom, setZoom] = useState<string | null>(null);
  const reviewInputs = useRef({ cats, first: photos[0], lat: draft.lat, lng: draft.lng });
  useEffect(() => {
    reviewInputs.current = { cats, first: photos[0], lat: draft.lat, lng: draft.lng };
  });
  const mod = !!profile && ['moderator', 'admin'].includes(profile.role);
  const reviewKey =
    !preset && mod && settings.photoReview && photos[0] && (draft.lat || draft.lng) && cats
      ? photos[0].url + '|' + positionKey
      : '';
  useEffect(() => {
    setReview(null);
    setReviewing(false);
    setReviewFailed(false);
    setReviewUnavailable(0);
    const { cats, first, lat, lng } = reviewInputs.current;
    if (!reviewKey || !cats || !first) return;
    const nearby = cats
      .filter((c) => !c.hidden && !c.mergedInto && new Set(c.photoIds).size >= 2)
      .map((cat) => ({ cat, distance: distanceKm(cat.lat, cat.lng, lat, lng) }))
      .filter((c) => Number.isFinite(c.distance))
      .sort((a, b) => a.distance - b.distance)
      .slice(0, MAX_REVIEW_CATS);
    if (!nearby.length) return;
    const controller = new AbortController();
    setReviewing(true);
    void AiServer.review(
      first.data.thumb,
      nearby.map((c) => c.cat.id),
      nearby.map((c) => c.cat.name),
      controller.signal,
    ).then((result) => {
      if (controller.signal.aborted) return;
      setReviewing(false);
      if (!result) {
        setReviewFailed(true);
        return;
      }
      const byId = new Map(nearby.map((c) => [c.cat.id, c]));
      setReview(
        result.ranked.flatMap((r) => {
          const c = byId.get(r.id);
          return c ? [{ ...c, photoIds: r.photoIds }] : [];
        }),
      );
      setReviewUnavailable(result.unavailable.length);
    });
    return () => controller.abort();
  }, [reviewKey]);
  const visibleReview = (review ?? []).filter((c) => !draft.rejectedCatIds.includes(c.cat.id));
  const pick = async (files: FileList | null) => {
    if (!files) return;
    setProcessing(true);
    await action.run(async () => {
      const added = [];
      for (const file of Array.from(files).slice(0, 5 - photos.length)) {
        const data = await processPhoto(file),
          blob = new Blob([new Uint8Array(data.full)], { type: 'image/jpeg' });
        added.push({ data, url: URL.createObjectURL(blob) });
      }
      const all = [...photos, ...added];
      setPhotos(all);
      const analyses = all.map((p) =>
        describe(
          p.data.analysis
            .getContext('2d')!
            .getImageData(0, 0, p.data.analysis.width, p.data.analysis.height),
        ),
      );
      const best = analyses.slice().sort((a, b) => b.colorConfidence - a.colorConfidence)[0],
        pattern = analyses.slice().sort((a, b) => b.patternConfidence - a.patternConfidence)[0];
      change({
        features: Similarity.average(analyses.map((a) => a.features)),
        ...(!manualCoat && !preset ? { coatColor: best.coatColor, pattern: pattern.pattern } : {}),
        breedHints: best.breedHints.map((b) => b.key),
        aiSummary: `${best.coatColor}:${pattern.pattern}:${best.colorConfidence.toFixed(2)}`,
      });
    });
    setProcessing(false);
  };
  const submit = () =>
    action.run(async () => {
      const ids = [];
      for (const p of photos) {
        p.id ??= await ImageRepository.upload(profile!.uid, p.data);
        ids.push(p.id);
      }
      const catId = await CatRepository.submitSighting(
        {
          ...draft,
          photoIds: ids,
          suggestedCatIds: (review ?? candidates).map((c) => c.cat.id),
          matchScore: draft.existingCat
            ? (candidates.find((c) => c.cat.id === draft.existingCat?.id)?.score ?? 0)
            : 0,
        },
        profile!,
        settings.privacyLevel,
        settings.shareExactWithVolunteers,
      );
      photos.forEach((p) => URL.revokeObjectURL(p.url));
      navigate('/cat/' + catId, { replace: true });
    }, t('sighting_saved'));
  const valid =
    step === 0
      ? !!(preset || photos.length)
      : step === 2
        ? !!(draft.existingCat || draft.newCatName.trim())
        : step === 3
          ? Dubai.contains(draft.lat, draft.lng)
          : true;
  return (
    <>
      <AppTopBar title={t('report_sighting')} />
      <div className="screen editor">
        <small>{t('step_x_of_y', step + 1, 4)}</small>
        <h2>{t(['step_photos', 'step_ai', 'step_details', 'step_location'][step])}</h2>
        <progress max="4" value={step + 1} />
        {step === 0 && (
          <>
            <p>{t('photos_hint')}</p>
            <p className="small muted">{t('metadata_removed_note')}</p>
            <p className="small muted">{t('several_subjects_hint')}</p>
            <p>{t('auto_time', dateTime(draft.seenAt))}</p>
            <div className="photo-grid">
              {photos.map((p, i) => (
                <img key={i} src={p.url} alt={t('photo_n', i + 1)} />
              ))}
            </div>
            <label className="outlined file-button">
              {t('add_photo')}
              <input
                type="file"
                accept="image/*"
                multiple
                disabled={processing || photos.length >= 5}
                onChange={(e) => void pick(e.target.files)}
              />
            </label>
          </>
        )}
        {step === 1 && (
          <>
            <p>{t('web_analyser')}</p>
            <BreedTraits hints={draft.breedHints} />
            <p>
              {t('coat_color')}: {t('coat_' + draft.coatColor)} · {t('pattern')}:{' '}
              {t('pattern_' + draft.pattern)}
            </p>
            {reviewing && <p className="small muted">{t('photo_review_checking')}</p>}
            {reviewFailed && <p className="small muted">{t('photo_review_failed')}</p>}
            {draft.existingCat ? (
              <p>{t('will_add_to', draft.existingCat.name)}</p>
            ) : review ? (
              // No percentages or "likely" labels: the model can't name a cat reliably, so the person decides.
              <>
                <SectionHeader>{t('photo_review_title')}</SectionHeader>
                <p className="small">{t('photo_review_hint')}</p>
                {visibleReview.length ? (
                  visibleReview.map((c) => (
                    <article className="card" key={c.cat.id}>
                      <div className="row">
                        <strong>{c.cat.name}</strong>
                        <span>{t('km_away', c.distance.toFixed(1))}</span>
                      </div>
                      <div className="row">
                        {c.photoIds.map((id) => (
                          <Photo key={id} id={id} alt={c.cat.name} onClick={() => setZoom(id)} />
                        ))}
                      </div>
                      <div className="row">
                        <button className="primary" onClick={() => change({ existingCat: c.cat })}>
                          {t('photo_review_this_cat')}
                        </button>
                        <button
                          className="outlined"
                          onClick={() =>
                            change({ rejectedCatIds: toggle(draft.rejectedCatIds, c.cat.id) })
                          }
                        >
                          {t('not_this_cat')}
                        </button>
                      </div>
                    </article>
                  ))
                ) : (
                  <EmptyState text={t(review.length ? 'all_matches_rejected' : 'no_matches')} />
                )}
                {visibleReview.length > 0 && (
                  <button
                    className="outlined"
                    onClick={() =>
                      change({
                        rejectedCatIds: [
                          ...new Set([...draft.rejectedCatIds, ...review.map((c) => c.cat.id)]),
                        ],
                      })
                    }
                  >
                    {t('photo_review_none')}
                  </button>
                )}
                {reviewUnavailable > 0 && (
                  <p className="small muted">{t('photo_review_unavailable', reviewUnavailable)}</p>
                )}
                {zoom && (
                  <Dialog title={t('photo_review_title')} onClose={() => setZoom(null)}>
                    <Photo id={zoom} thumb={false} />
                  </Dialog>
                )}
              </>
            ) : visibleCandidates.length ? (
              visibleCandidates.map((c, i) => (
                <article className="card" key={c.cat.id}>
                  <div className="row">
                    <Photo id={c.cat.coverPhotoId} />
                    <strong>{c.cat.name}</strong>
                    <span>
                      {Math.round(c.score * 100)}% ·{' '}
                      {t(
                        'match_' +
                          Similarity.strength(c.score, candidates[i === 0 ? 1 : 0]?.score ?? null),
                      )}
                    </span>
                  </div>
                  <div className="row">
                    <button className="primary" onClick={() => change({ existingCat: c.cat })}>
                      {t('same_animal_yes')}
                    </button>
                    <button
                      className="outlined"
                      onClick={() =>
                        change({ rejectedCatIds: toggle(draft.rejectedCatIds, c.cat.id) })
                      }
                    >
                      {t('same_animal_no')}
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <EmptyState text={t(candidates.length ? 'all_matches_rejected' : 'no_matches')} />
            )}
            <button className="text-button" onClick={() => change({ existingCat: null })}>
              {t('new_cat_profile')}
            </button>
          </>
        )}
        {step === 2 && (
          <>
            {!draft.existingCat && (
              <>
                <Field
                  label={t('name')}
                  value={draft.newCatName}
                  limit={40}
                  onChange={(newCatName) => change({ newCatName })}
                />
                <p>{t('name_ideas')}</p>
                <div className="chips">
                  {nameIdeas(draft.coatColor).map((name) => (
                    <button
                      type="button"
                      className="chip"
                      key={name}
                      onClick={() => change({ newCatName: name })}
                    >
                      {name}
                    </button>
                  ))}
                </div>
                <Field
                  label={t('description')}
                  value={draft.description}
                  limit={800}
                  onChange={(description) => change({ description })}
                  multiline
                />
                <Field
                  label={t('distinctive_marks')}
                  value={draft.distinctiveMarks}
                  limit={300}
                  onChange={(distinctiveMarks) => change({ distinctiveMarks })}
                  multiline
                />
              </>
            )}
            <SectionHeader>{t('coat_color')}</SectionHeader>
            <Chips
              options={enums.CoatColor}
              value={draft.coatColor}
              onChange={(coatColor) => {
                setManualCoat(true);
                change({ coatColor });
              }}
            />
            <SectionHeader>{t('pattern')}</SectionHeader>
            <Chips
              options={enums.CoatPattern}
              value={draft.pattern}
              onChange={(pattern) => {
                setManualCoat(true);
                change({ pattern });
              }}
            />
            <SectionHeader>{t('size')}</SectionHeader>
            <Chips
              options={enums.CatSize}
              value={draft.size}
              onChange={(size) => change({ size })}
            />
            <SectionHeader>{t('sex')}</SectionHeader>
            <Chips options={enums.CatSex} value={draft.sex} onChange={(sex) => change({ sex })} />
            <SectionHeader>{t('health_observations')}</SectionHeader>
            <Chips
              options={enums.HealthTag}
              multi
              value={draft.healthTags}
              onChange={(value) => change({ healthTags: toggle(draft.healthTags, value) })}
            />
            <SectionHeader>{t('behavior_observations')}</SectionHeader>
            <Chips
              options={enums.BehaviorTag}
              multi
              value={draft.behaviorTags}
              onChange={(value) => change({ behaviorTags: toggle(draft.behaviorTags, value) })}
            />
            <SectionHeader>{t('ear_tipped')}</SectionHeader>
            <Chips
              options={enums.EarTip}
              value={draft.earTip}
              onChange={(earTip) => change({ earTip })}
            />
            <Switch
              label={t('report_not_neutered')}
              checked={draft.possiblyNotNeutered}
              onChange={(possiblyNotNeutered) => change({ possiblyNotNeutered })}
            />
            <Switch
              label={t('needs_medical')}
              checked={draft.needsMedical}
              onChange={(needsMedical) => change({ needsMedical })}
            />
            {draft.needsMedical && <p>{t('medical_rescue_tip')}</p>}
            <Field
              label={t('notes')}
              value={draft.notes}
              limit={1000}
              onChange={(notes) => change({ notes })}
              multiline
            />
          </>
        )}
        {step === 3 && (
          <>
            <LocationField
              lat={draft.lat}
              lng={draft.lng}
              onChange={(lat, lng) => {
                const fix = LocationRepository.get(),
                  gps = fix?.lat === lat && fix?.lng === lng;
                change({
                  lat,
                  lng,
                  accuracy: gps ? (fix.accuracy ?? 0) : 0,
                  locationSource: gps ? 'gps' : 'manual',
                });
              }}
            />
            {draft.accuracy > 0 && <p>{t('accuracy_m', Math.round(draft.accuracy))}</p>}
            <Field
              label={t('last_seen')}
              value={new Date(draft.seenAt + 4 * 3600000).toISOString().slice(0, 16)}
              onChange={(value) => change({ seenAt: new Date(value + '+04:00').getTime() })}
              type="datetime-local"
            />
            <p className="muted">{t('location_is_approximate')}</p>
            <p>
              {t(
                settings.shareExactWithVolunteers
                  ? 'privacy_exact_shared'
                  : 'privacy_exact_not_shared',
              )}
            </p>
          </>
        )}
        <ErrorText text={action.error} />
        <div className="editor-footer">
          {step > 0 && (
            <button className="outlined" onClick={() => setStep((s) => s - 1)}>
              {t('back')}
            </button>
          )}
          <button
            className="primary"
            disabled={!valid || action.busy || processing}
            onClick={() => (step < 3 ? setStep((s) => s + 1) : void submit())}
          >
            {t(step === 3 ? 'submit' : 'next')}
          </button>
        </div>
      </div>
    </>
  );
}
function SightingDetail() {
  const navigate = useNavigate();
  const { id = '' } = useParams(),
    { profile } = useApp(),
    action = useAction();
  const [exact, setExact] = useState<PrivateLocation | null>(null),
    [confirmDelete, setDelete] = useState(false);
  const sighting = useDoc(() => CatRepository.sightingFlow(id), SightingFromDoc, 'sighting-' + id);
  const allowedExact =
    sighting?.userId === profile?.uid ||
    ['volunteer', 'moderator', 'admin'].includes(profile?.role ?? '');
  useEffect(() => {
    let active = true;
    setExact(null);
    if (allowedExact)
      void CatRepository.privateLocation(id).then((value) => {
        if (active) setExact(value);
      });
    return () => {
      active = false;
    };
  }, [id, allowedExact]);
  if (sighting === undefined) return <Loading />;
  if (!sighting) return <EmptyState text={t('sighting_deleted')} />;
  return (
    <>
      <AppTopBar title={t('sighting')} />
      <div className="screen">
        <Link to={'/cat/' + sighting.catId}>
          <h2>{sighting.catName}</h2>
        </Link>
        <StatusPill group="SubmissionStatus" value={sighting.status} />
        {sighting.newCat && <p>{t('first_sighting')}</p>}
        {sighting.matchScore > 0 && (
          <p>{t('ai_match_score', Math.round(sighting.matchScore * 100))}</p>
        )}
        <p>
          <Link to={'/user/' + sighting.userId}>@{sighting.username}</Link> ·{' '}
          {dateTime(sighting.seenAt)} · {Dubai.nameOf(sighting.neighborhood)}
        </p>
        <div className="photo-grid">
          {sighting.photoIds.map((photoId) => (
            <Photo id={photoId} key={photoId} onClick={() => navigate('/photo/' + photoId)} />
          ))}
        </div>
        <p dir="auto">{sighting.notes}</p>
        <p>
          {sighting.healthTags
            .map((v) => t(enums.HealthTag.find((x) => x.key === v)?.label ?? 'unknown'))
            .join(' · ')}
        </p>
        <p>
          {t('location_source')}:{' '}
          {t(sighting.locationSource === 'gps' ? 'source_gps' : 'source_manual')}
        </p>
        {sighting.needsMedical && <p>{t('flag_needs_medical')}</p>}
        {sighting.possiblyNotNeutered && <p>{t('obs_not_neutered')}</p>}
        <p className="muted">{t('location_is_approximate')}</p>
        {exact && (
          <div className="card exact-location">
            {t('exact_location', exact.lat.toFixed(5) + ', ' + exact.lng.toFixed(5))}
          </div>
        )}
        {profile &&
          ['moderator', 'admin'].includes(profile.role) &&
          sighting.status === 'pending' && (
            <div className="row">
              {[true, false].map((approve) => (
                <button
                  className="outlined"
                  key={String(approve)}
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(
                      () => CatRepository.reviewSighting(sighting, approve, profile),
                      t('updated'),
                    )
                  }
                >
                  {t(approve ? 'approve' : 'reject')}
                </button>
              ))}
            </div>
          )}
        {sighting.userId === profile?.uid && (
          <>
            <Link className="outlined" to={'/sighting-edit/' + id}>
              {t('edit')}
            </Link>
            <button className="text-button danger" onClick={() => setDelete(true)}>
              {t('delete')}
            </button>
          </>
        )}
        {sighting.userId !== profile?.uid && (
          <ReportButton type="sighting" id={id} label={sighting.catName} />
        )}
      </div>
      {confirmDelete && (
        <Dialog title={t('delete')} onClose={() => setDelete(false)}>
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await CatRepository.deleteSighting(sighting);
                setDelete(false);
              }, t('saved'))
            }
          >
            {t('confirm')}
          </button>
        </Dialog>
      )}
    </>
  );
}
