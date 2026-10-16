import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { PostDefaults, type Post } from '../../data/model';
import { enums } from '../../data/model/Enums';
import { PostRepository, toPost, likelyDuplicates } from '../../data/PostRepository';
import { MatchRepository } from '../../data/MatchRepository';
import { PostDraftStore, type SavedDraft } from '../../data/PostDraftStore';
import { ImageRepository } from '../../data/ImageRepository';
import { VideoRepository } from '../../data/VideoRepository';
import { blocksPhoto } from '../../ai/PhotoCheck';
import { usePhotoCheck, type SameAnimalMatch as Match } from './usePhotoCheck';
import {
  postSteps,
  stepLabels,
  visibleSteps,
  restoredStep,
  stepAfter,
  type PostStep,
} from './PostSteps';
import { PostAppearance } from '../../ai/PostAppearance';
import { processPhoto, type ProcessedPhoto } from '../../util/images';
import { Dubai, LocationPrivacy } from '../../data/Neighborhoods';
import { useApp, useAction, useSettings } from '../../state';
import { t } from '../../i18n';
import {
  AppTopBar,
  SectionHeader,
  Chips,
  Field,
  Switch,
  Icon,
  Photo,
  Loading,
  ErrorText,
  Dialog,
  PostCard,
  ControlIcon,
} from '../components/Components';
import { LocationField } from '../components/LocationField';
import { CatMapIcons } from '../../icons/CatMapIcons';
interface LocalPhoto {
  key: string;
  blob: Blob;
  processed: ProcessedPhoto;
  url: string;
  uploaded?: string;
}
export default function PostEditorScreen() {
  const { id } = useParams(),
    location = useLocation(),
    navigate = useNavigate(),
    { profile, posts } = useApp(),
    settings = useSettings(),
    action = useAction();
  const uid = profile?.uid;
  const params = new URLSearchParams(location.search),
    preset = params.get('category'),
    editing = !!id;
  const [post, setPost] = useState<Post>(() => ({
      ...PostDefaults,
      category: enums.PostCategory.some((c) => c.key === preset) ? preset! : 'lost',
      animalType: enums.AnimalType.some((a) => a.key === params.get('type'))
        ? params.get('type')!
        : 'cat',
      approximate: preset === 'adoption',
      lastSeenAt: Date.now(),
    })),
    [existing, setExisting] = useState<Post | null>(null),
    [step, setStep] = useState<PostStep>(editing ? 'DETAILS' : preset ? 'PLACE' : 'TYPE'),
    [photos, setPhotos] = useState<LocalPhoto[]>([]),
    [draft, setDraft] = useState<SavedDraft | null>(null),
    [loaded, setLoaded] = useState(false),
    [video, setVideo] = useState<File | null>(null),
    [rejected, setRejected] = useState<string[]>([]),
    [guard, setGuard] = useState<Match | null>(null);
  const [busyPhoto, setBusyPhoto] = useState(false);
  const finished = useRef(false),
    queue = useRef(Promise.resolve()),
    photoRef = useRef(photos);
  photoRef.current = photos;
  const animalTypePicked = useRef(params.has('type') || editing);
  const { verdicts, matches, checking } = usePhotoCheck(
    photos,
    post,
    editing,
    posts,
    animalTypePicked,
    (animalType) => change({ animalType }),
  );
  const blockedPhoto = photos.map((photo) => verdicts[photo.key]).find(blocksPhoto);
  const steps = visibleSteps(post.category);
  const change = (patch: Partial<Post>) => setPost((p) => ({ ...p, ...patch }));
  const draftSnapshot = useRef<{ uid: string; saved: SavedDraft } | null>(null);
  draftSnapshot.current =
    !editing &&
    loaded &&
    !draft &&
    uid &&
    !finished.current &&
    (step !== 'TYPE' || post.name || post.description || photos.length || post.lat)
      ? {
          uid,
          saved: {
            post,
            step: postSteps.indexOf(step),
            stepName: step,
            photos: photos.map((p) => p.blob),
            at: Date.now(),
          },
        }
      : null;
  useEffect(() => {
    const save = () => {
      const snapshot = draftSnapshot.current;
      if (snapshot && !finished.current)
        queue.current = queue.current
          .catch(() => {})
          .then(() =>
            finished.current ? undefined : PostDraftStore.save(snapshot.uid, snapshot.saved),
          );
    };
    window.addEventListener('pagehide', save);
    return () => {
      window.removeEventListener('pagehide', save);
      save();
    };
  }, []);

  useEffect(() => {
    let active = true;
    void (async () => {
      if (editing) {
        const found = await PostRepository.post(id!);
        if (active) {
          setExisting(found);
          if (found) setPost(found);
        }
      } else if (uid) {
        const saved = await PostDraftStore.load(uid).catch(() => undefined);
        if (active && saved) setDraft(saved);
      }
      if (active) setLoaded(true);
    })();
    return () => {
      active = false;
    };
  }, [editing, id, uid]);
  useEffect(
    () => () => {
      photoRef.current.forEach((p) => URL.revokeObjectURL(p.url));
    },
    [],
  );
  useEffect(() => {
    if (
      editing ||
      !loaded ||
      draft ||
      !profile ||
      finished.current ||
      (!post.name && !post.description && !photos.length && !post.lat && step === 'TYPE')
    )
      return;
    const uid = profile.uid;
    const timeout = setTimeout(() => {
      const saved = {
        post,
        step: postSteps.indexOf(step),
        stepName: step,
        photos: photos.map((p) => p.blob),
        at: Date.now(),
      };
      queue.current = queue.current
        .catch(() => {})
        .then(() => (finished.current ? undefined : PostDraftStore.save(uid, saved)));
    }, 500);
    return () => clearTimeout(timeout);
  }, [post, step, photos, editing, loaded, draft, profile]);
  const restore = async () => {
    if (!draft) return;
    setPost({ ...PostDefaults, ...draft.post });
    const restored = restoredStep(draft);
    setStep(draft.post.category === 'street' && restored === 'CONTACT' ? 'REVIEW' : restored);
    animalTypePicked.current = true;
    const locals = [];
    for (const blob of draft.photos.slice(0, 6)) {
      try {
        const processed = await processPhoto(blob);
        locals.push({ key: crypto.randomUUID(), blob, processed, url: URL.createObjectURL(blob) });
      } catch {
        /* Unreadable saved photos are skipped, matching Android. */
      }
    }
    setPhotos(locals);
    setDraft(null);
  };
  const pickPhotos = async (files: FileList | null) => {
    if (!files) return;
    setBusyPhoto(true);
    try {
      const fresh: LocalPhoto[] = [];
      for (const file of Array.from(files).slice(0, 6 - post.photoIds.length - photos.length)) {
        const processed = await processPhoto(file);
        const blob = new Blob([new Uint8Array(processed.full)], { type: 'image/jpeg' });
        fresh.push({ key: crypto.randomUUID(), processed, blob, url: URL.createObjectURL(blob) });
      }
      setPhotos((p) => [...p, ...fresh]);
    } catch (e) {
      await action.run(() => Promise.reject(e));
    } finally {
      setBusyPhoto(false);
    }
  };
  const finishDraft = async () => {
    finished.current = true;
    await queue.current.catch(() => {});
    if (profile && !editing) await PostDraftStore.clear(profile.uid);
  };
  const publish = async (force = false) => {
    if (!profile) return;
    if (checking || blockedPhoto) return;
    const same = matches.find((m) => m.verdict === 'same' && !rejected.includes(m.post.id));
    if (same && !force) {
      setGuard(same);
      return;
    }
    await action.run(async () => {
      const fingerprint = PostAppearance.average([
        ...photos.map((p) => PostAppearance.describe(p.processed.analysis)),
        post.fingerprint,
      ]);
      const photoIds = [...post.photoIds];
      for (const p of photos) {
        if (!p.uploaded) p.uploaded = await ImageRepository.upload(profile.uid, p.processed);
        photoIds.push(p.uploaded);
      }
      let videoPath = post.videoPath;
      if (video) {
        videoPath = await VideoRepository.upload(profile.uid, video);
        if (post.videoPath) await VideoRepository.delete(post.videoPath);
        setVideo(null);
        change({ videoPath });
      }
      const position = post.approximate
        ? LocationPrivacy.fuzz(
            post.lat,
            post.lng,
            settings.privacyLevel,
            profile.uid + post.lat + post.lng,
          )
        : { lat: post.lat, lng: post.lng };
      const prepared = {
        ...post,
        ...position,
        photoIds,
        fingerprint,
        videoPath,
        areaRadiusM: post.category === 'lost' ? post.areaRadiusM : 0,
        contactMethod: post.category === 'street' ? 'in_app' : post.contactMethod,
        contactPhone:
          post.category === 'street' ? '' : post.contactMethod === 'phone' ? post.contactPhone : '',
        contactEmail:
          post.category === 'street' ? '' : post.contactMethod === 'email' ? post.contactEmail : '',
      };
      const target =
        editing && existing
          ? (await PostRepository.edit(existing, prepared, profile), existing.id)
          : await PostRepository.create(prepared, profile);
      // Look for matching lost reports / sightings in the background (the PC when online, this device otherwise).
      if (!editing) void MatchRepository.afterPublish(target, profile).catch(() => 0);
      if (existing)
        await Promise.allSettled(
          existing.photoIds.filter((id) => !photoIds.includes(id)).map(ImageRepository.delete),
        );
      await finishDraft();
      navigate('/post/' + target, { replace: true });
    }, t('post_published'));
  };
  const addToExisting = async (match: Match) => {
    if (!profile || !photos[0]) return;
    await action.run(async () => {
      const p = photos[0],
        photoId = p.uploaded ?? (await ImageRepository.upload(profile.uid, p.processed));
      p.uploaded = photoId;
      const position = post.approximate
        ? LocationPrivacy.fuzz(
            post.lat,
            post.lng,
            settings.privacyLevel,
            profile.uid + post.lat + post.lng,
          )
        : post;
      await PostRepository.addUpdate(
        match.post,
        'sighting',
        [post.description.trim(), post.features.trim()].filter(Boolean).join('\n'),
        photoId,
        position.lat,
        position.lng,
        profile,
      );
      await finishDraft();
      navigate('/post/' + match.post.id, { replace: true });
    }, t('same_animal_added'));
  };
  const photoCheckStatus = (
    <>
      {checking && (
        <p className="photo-check-status" role="status">
          <span className="photo-check-spinner" aria-hidden="true" />
          {t('photo_checking')}
        </p>
      )}
      {blockedPhoto && (
        <div className="photo-check-error" role="alert">
          <strong>
            {t(
              blockedPhoto.phrase ? 'photo_not_animal' : 'photo_not_animal_unnamed',
              blockedPhoto.phrase,
            )}
          </strong>
          <p>{t('photo_not_animal_action')}</p>
        </div>
      )}
    </>
  );
  const sameAnimalCards = (
    <>
      {checking && <p role="status">{t('same_animal_checking')}</p>}
      {matches
        .filter((m) => !rejected.includes(m.post.id))
        .map((m) => (
          <div className="same-animal card" key={m.post.id}>
            <h3>{t('same_animal_title')}</h3>
            <p>{t(m.server ? 'same_animal_hint_ai' : 'same_animal_hint_local')}</p>
            <Link to={'/post/' + m.post.id} className="row">
              <Photo id={m.post.photoIds[0]} />
              <strong>{m.post.name}</strong>
              <span>
                {t(m.verdict === 'same' ? 'same_animal_likely' : 'same_animal_maybe')}{' '}
                {Math.round(m.similarity * 100)}%
              </span>
            </Link>
            <button
              className="primary"
              disabled={action.busy}
              onClick={() => void addToExisting(m)}
            >
              {t('same_animal_add')}
            </button>
            <button
              className="text-button"
              onClick={() => setRejected((ids) => [...ids, m.post.id])}
            >
              {t('same_animal_no')}
            </button>
          </div>
        ))}
    </>
  );
  if (!loaded) return <Loading />;
  if (editing && !existing)
    return (
      <div className="screen">
        <p>{t('post_removed')}</p>
      </div>
    );
  if (!profile) return <Loading />;
  if (
    editing &&
    existing?.ownerId !== profile.uid &&
    !['admin', 'moderator'].includes(profile.role)
  )
    return <ErrorText text={t('err_permission')} />;
  const valid =
    step === 'DETAILS'
      ? !!(post.name.trim() || post.description.trim() || post.features.trim())
      : step === 'PLACE'
        ? Dubai.contains(post.lat, post.lng)
        : step === 'CONTACT'
          ? post.contactMethod === 'phone'
            ? post.contactPhone.replace(/\D/g, '').length >= 7
            : post.contactMethod === 'email'
              ? post.contactEmail.includes('@') && post.contactEmail.includes('.')
              : true
          : true;
  const duplicates = likelyDuplicates(toPost(post, profile), posts ?? []);
  return (
    <>
      <AppTopBar title={t(editing ? 'edit_post' : 'post_new')} />
      <main className="screen editor">
        <div className="step-heading">
          <small>{t('step_x_of_y', steps.indexOf(step) + 1, steps.length)}</small>
          <h2>{t(stepLabels[step])}</h2>
          <progress max={steps.length} value={steps.indexOf(step) + 1} />
        </div>
        {step === 'TYPE' && (
          <>
            <SectionHeader>{t('post_what_happened')}</SectionHeader>
            <div className="choice-grid">
              {enums.PostCategory.map((c) => (
                <button
                  className={'choice-tile ' + (post.category === c.key ? 'selected' : '')}
                  type="button"
                  key={c.key}
                  onClick={() => change({ category: c.key, approximate: c.key === 'adoption' })}
                >
                  <Icon name={CatMapIcons.category(c.key)} size={32} />
                  <strong>{t(c.label)}</strong>
                </button>
              ))}
            </div>
            <SectionHeader>{t('animal_type')}</SectionHeader>
            <Chips
              options={enums.AnimalType}
              value={post.animalType}
              onChange={(animalType) => {
                animalTypePicked.current = true;
                change({ animalType });
              }}
            />
          </>
        )}
        {step === 'PHOTOS' && (
          <>
            <p>{t('add_photos_hint')}</p>
            <div className="photo-grid">
              {post.photoIds.map((id) => (
                <div key={id}>
                  <Photo id={id} />
                  <button
                    className="text-button"
                    onClick={() => change({ photoIds: post.photoIds.filter((p) => p !== id) })}
                  >
                    {t('remove')}
                  </button>
                </div>
              ))}
              {photos.map((p, index) => (
                <div
                  className={'photo-tile ' + (blocksPhoto(verdicts[p.key]) ? 'blocked-photo' : '')}
                  key={p.key}
                >
                  <img src={p.url} alt={t('photo_n', index + 1)} draggable="false" />
                  {verdicts[p.key]?.phrase && (
                    <span
                      className={'photo-verdict ' + (blocksPhoto(verdicts[p.key]) ? 'blocked' : '')}
                    >
                      {verdicts[p.key].phrase}
                    </span>
                  )}
                  <div className="row">
                    <button
                      className="text-button"
                      aria-label={t('remove')}
                      onClick={() => {
                        URL.revokeObjectURL(p.url);
                        setPhotos((ps) => ps.filter((photo) => photo.key !== p.key));
                      }}
                    >
                      {t('remove')}
                    </button>
                    {index > 0 && (
                      <button
                        className="text-button"
                        aria-label={t('cover')}
                        onClick={() =>
                          setPhotos((ps) => [p, ...ps.filter((photo) => photo.key !== p.key)])
                        }
                      >
                        ↑
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
            <label className="outlined file-button">
              <Icon name={CatMapIcons.CAMERA} />
              {t('tap_to_add_photos')}
              <input
                type="file"
                accept="image/*,.heic,.heif"
                multiple
                disabled={busyPhoto || post.photoIds.length + photos.length >= 6}
                onChange={(e) => {
                  void pickPhotos(e.target.files);
                  e.target.value = '';
                }}
              />
            </label>
            <label className="text-button file-button">
              {t('camera')}
              <input
                type="file"
                accept="image/*"
                capture="environment"
                disabled={busyPhoto || post.photoIds.length + photos.length >= 6}
                onChange={(e) => void pickPhotos(e.target.files)}
              />
            </label>
            {photoCheckStatus}
            {sameAnimalCards}
            <small>{t('photos_n_of_max', post.photoIds.length + photos.length, 6)}</small>
            {busyPhoto && <Loading rows={1} />}{' '}
            {VideoRepository.enabled && (
              <label className="outlined file-button">
                {t('add_video')}
                <input
                  type="file"
                  accept="video/*"
                  onChange={(e) => setVideo(e.target.files?.[0] ?? null)}
                />
              </label>
            )}
          </>
        )}
        {step === 'DETAILS' && (
          <>
            <Field
              label={t('name')}
              value={post.name}
              onChange={(name) => change({ name })}
              limit={60}
            />
            <Field
              label={t('description')}
              value={post.description}
              onChange={(description) => change({ description })}
              limit={2000}
              multiline
            />
            <div className="field-pair">
              <Field
                label={t('color')}
                value={post.color}
                onChange={(color) => change({ color })}
                limit={60}
              />
              <Field
                label={t('breed')}
                value={post.breed}
                onChange={(breed) => change({ breed })}
                limit={60}
              />
            </div>
            <Field
              label={t('identifying_features')}
              value={post.features}
              onChange={(features) => change({ features })}
              limit={400}
              multiline
              hint={t('identifying_features_hint')}
            />
            <SectionHeader>{t('sex')}</SectionHeader>
            <Chips options={enums.CatSex} value={post.sex} onChange={(sex) => change({ sex })} />
            <SectionHeader>{t('age')}</SectionHeader>
            <Chips
              options={enums.AgeGroup}
              value={post.ageGroup}
              onChange={(ageGroup) => change({ ageGroup })}
            />
            <SectionHeader>{t('microchip')}</SectionHeader>
            <Chips
              options={[
                { key: 'yes', label: 'yes' },
                { key: 'no', label: 'no' },
                { key: 'unknown', label: 'unknown' },
              ]}
              value={post.microchip}
              onChange={(microchip) => change({ microchip })}
            />
            <small className="muted">{t('microchip_privacy')}</small>
            {post.category === 'adoption' && (
              <>
                <Field
                  label={t('health_notes')}
                  value={post.health}
                  onChange={(health) => change({ health })}
                  limit={400}
                  multiline
                />
                <Switch
                  label={t('vaccinated')}
                  checked={post.vaccinated}
                  onChange={(vaccinated) => change({ vaccinated })}
                />
                <Switch
                  label={t('neutered')}
                  checked={post.neutered}
                  onChange={(neutered) => change({ neutered })}
                />
              </>
            )}
          </>
        )}
        {step === 'PLACE' && (
          <>
            <LocationField
              lat={post.lat}
              lng={post.lng}
              onChange={(lat, lng) => change({ lat, lng })}
              radius={post.areaRadiusM}
              onRadius={
                post.category === 'lost' ? (areaRadiusM) => change({ areaRadiusM }) : undefined
              }
            />
            <Switch
              label={t('post_approximate_location')}
              checked={post.approximate}
              onChange={(approximate) => change({ approximate })}
            />
            <Field
              label={t('last_seen')}
              value={new Date(post.lastSeenAt + 4 * 3600000).toISOString().slice(0, 16)}
              onChange={(value) =>
                change({ lastSeenAt: new Date(value + '+04:00').getTime() || Date.now() })
              }
              type="datetime-local"
            />
            {post.category === 'lost' && (
              <Field
                label={t('reward_amount_aed')}
                value={post.reward || ''}
                onChange={(value) =>
                  change({ reward: Number(value.replace(/\D/g, '').slice(0, 6)) })
                }
                inputMode="numeric"
              />
            )}
          </>
        )}
        {step === 'CONTACT' && (
          <>
            <p>{t('how_contact')}</p>
            <Chips
              options={enums.ContactMethod}
              value={post.contactMethod}
              onChange={(contactMethod) => change({ contactMethod })}
            />
            {post.contactMethod === 'phone' && (
              <Field
                label={t('phone_number')}
                value={post.contactPhone}
                onChange={(contactPhone) => change({ contactPhone })}
                type="tel"
                limit={20}
              />
            )}{' '}
            {post.contactMethod === 'email' && (
              <Field
                label={t('email')}
                value={post.contactEmail}
                onChange={(contactEmail) => change({ contactEmail })}
                type="email"
                limit={80}
              />
            )}
            <p className="muted">{t('contact_privacy_note')}</p>
          </>
        )}
        {step === 'REVIEW' && (
          <>
            <div className="card">
              <div className="row">
                <Icon name={CatMapIcons.animal(post.animalType)} size={48} />
                <div>
                  <h3 dir="auto">{post.name || t('animal_' + post.animalType)}</h3>
                  <p>
                    {t('cat_' + post.category)} ·{' '}
                    {Dubai.nameOf(Dubai.nearest(post.lat, post.lng).key)}
                  </p>
                </div>
              </div>
              <p dir="auto">{post.description}</p>
              <p dir="auto">{post.features}</p>
              <p>{t('photos_n_of_max', post.photoIds.length + photos.length, 6)}</p>
            </div>
            {photoCheckStatus}
            {sameAnimalCards}
            {duplicates.length > 0 && (
              <>
                <SectionHeader>{t('possible_duplicates')}</SectionHeader>
                <p>{t('possible_duplicates_hint')}</p>
                {duplicates.map((p) => (
                  <PostCard key={p.id} post={p} />
                ))}
              </>
            )}
            <p className="muted">{t('post_rules_note')}</p>
          </>
        )}
        <ErrorText text={action.error} />
        <div className="editor-footer">
          {step !== 'TYPE' && (
            <button
              className="outlined"
              disabled={action.busy}
              onClick={() => setStep((s) => stepAfter(s, post.category, false))}
            >
              <ControlIcon name="back" />
              {t('back')}
            </button>
          )}
          <button
            className="primary"
            disabled={
              !valid ||
              action.busy ||
              busyPhoto ||
              ((step === 'PHOTOS' || step === 'REVIEW') && (checking || !!blockedPhoto))
            }
            onClick={() =>
              step !== 'REVIEW' ? setStep((s) => stepAfter(s, post.category, true)) : void publish()
            }
          >
            {action.busy ? t('web_loading') : t(step === 'REVIEW' ? 'publish' : 'next')}
          </button>
        </div>
      </main>
      {draft && (
        <Dialog title={t('draft_available')} onClose={() => setDraft(null)}>
          <button className="primary" onClick={() => void restore()}>
            {t('continue_draft')}
          </button>
          <button
            className="text-button"
            onClick={() => {
              if (profile) void PostDraftStore.clear(profile.uid);
              setDraft(null);
            }}
          >
            {t('discard_draft')}
          </button>
        </Dialog>
      )}
      {guard && (
        <Dialog title={t('same_animal_title')} onClose={() => setGuard(null)}>
          <p>{t('same_animal_publish_body', guard.post.name)}</p>
          <button
            className="primary"
            disabled={action.busy}
            onClick={() => void addToExisting(guard)}
          >
            {t('same_animal_add')}
          </button>
          <button
            className="outlined"
            disabled={action.busy}
            onClick={() => {
              setGuard(null);
              void publish(true);
            }}
          >
            {t('same_animal_post_anyway')}
          </button>
        </Dialog>
      )}
    </>
  );
}
