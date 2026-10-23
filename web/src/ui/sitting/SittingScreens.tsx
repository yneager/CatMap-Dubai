import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useList, useDoc, useAction, useDeviceLocation } from '../../state';
import { SittingRepository } from '../../data/SittingRepository';
import {
  SitterProfileDefaults,
  SitterProfileFromDoc,
  BookingFromDoc,
  ReviewFromDoc,
  type SitterProfile,
  type Booking,
} from '../../data/model';
import { enums } from '../../data/model/Enums';
import { Dubai, LocationPrivacy, distanceKm } from '../../data/Neighborhoods';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Avatar,
  SectionHeader,
  StatusPill,
  EmptyState,
  Loading,
  Field,
  Chips,
  Switch,
  Dialog,
  ErrorText,
  enumLabel,
} from '../components/Components';
import { LocationField } from '../components/LocationField';
import { toggle } from '../explore/Filtering';
export default function SittingScreens() {
  const path = useLocation().pathname;
  return path === '/sitters' ? (
    <Sitters />
  ) : path === '/sitter-edit' ? (
    <SitterEdit />
  ) : path === '/bookings' ? (
    <Bookings />
  ) : path.startsWith('/book/') ? (
    <BookingEditor />
  ) : (
    <SitterDetail />
  );
}
function Sitters() {
  const { profile } = useApp();
  const fix = useDeviceLocation();
  const sitters = useList(SittingRepository.activeSitters, SitterProfileFromDoc, 'sitters'),
    [verified, setVerified] = useState(false),
    [pet, setPet] = useState(''),
    [search, setSearch] = useState('');
  const shown = sitters
    ?.filter(
      (p) =>
        p.uid !== profile?.uid &&
        (!pet || p.petTypes.includes(pet)) &&
        (!verified || p.verified) &&
        [p.name, p.headline, Dubai.nameOf(p.neighborhood)]
          .join(' ')
          .toLowerCase()
          .includes(search.toLowerCase()),
    )
    ?.sort((a, b) =>
      fix
        ? distanceKm(fix.lat, fix.lng, a.lat, a.lng) - distanceKm(fix.lat, fix.lng, b.lat, b.lng)
        : (b.ratingCount ? b.ratingSum / b.ratingCount : 0) -
          (a.ratingCount ? a.ratingSum / a.ratingCount : 0),
    );
  return (
    <>
      <AppTopBar title={t('pet_sitters')} />
      <div className="screen">
        <div className="row">
          <Link className="outlined" to="/sitter-edit">
            {t('sitting_offer')}
          </Link>
          <Link className="text-button" to="/bookings">
            {t('bookings')}
          </Link>
        </div>
        <Field label={t('search')} value={search} onChange={setSearch} />
        <Switch label={t('verified_only')} checked={verified} onChange={setVerified} />
        <p className="muted">{t('sitters_intro')}</p>
        <Chips
          options={[{ key: '', label: 'all' }, ...enums.AnimalType]}
          value={pet}
          onChange={setPet}
        />
        {!shown ? (
          <Loading />
        ) : shown.length ? (
          shown.map((p) => (
            <Link className="card sitter-card" key={p.uid} to={'/sitter/' + p.uid}>
              <div className="row">
                <Avatar name={p.name} id={p.photoId} />
                <div>
                  <h3>@{p.name}</h3>
                  <p dir="auto">{p.headline}</p>
                  {p.verified && <StatusPill value="approved" group="RequestStatus" />}
                </div>
              </div>
              <p>
                {Dubai.nameOf(p.neighborhood)} · {t('rate_x', p.rate, '')}{' '}
                {enumLabel('RateUnit', p.rateUnit)}
              </p>
              <p className="stars">
                {'★'.repeat(Math.round(p.ratingCount ? p.ratingSum / p.ratingCount : 0))}{' '}
                <span>
                  {p.ratingCount ? p.ratingSum / p.ratingCount : 0} ({p.ratingCount})
                </span>
              </p>
            </Link>
          ))
        ) : (
          <EmptyState text={t('no_sitters')} />
        )}
      </div>
    </>
  );
}
function SitterDetail() {
  const { uid = '' } = useParams(),
    { profile } = useApp();
  const sitter = useDoc(
      () => SittingRepository.sitterFlow(uid),
      SitterProfileFromDoc,
      'sitter-' + uid,
    ),
    reviews = useList(() => SittingRepository.reviewsFor(uid), ReviewFromDoc, 'reviews-' + uid);
  if (sitter === undefined) return <Loading />;
  if (!sitter) return <EmptyState text={t('sitter_removed')} />;
  return (
    <>
      <AppTopBar title={t('sitter_profile')} />
      <div className="screen">
        <div className="profile-header">
          <Avatar name={sitter.name} id={sitter.photoId} size={100} />
          <h2>@{sitter.name}</h2>
          <h3 dir="auto">{sitter.headline}</h3>
          {sitter.verified && <p>{t('verified_sitter')}</p>}
        </div>
        <p dir="auto">{sitter.description}</p>
        <SectionHeader>{t('services')}</SectionHeader>
        <div className="chips">
          {sitter.services.map((s) => (
            <span className="chip" key={s}>
              {enumLabel('SitterService', s)}
            </span>
          ))}
        </div>
        <p>{sitter.petTypes.map((s) => enumLabel('AnimalType', s)).join(' · ')}</p>
        <SectionHeader>{t('experience')}</SectionHeader>
        <p dir="auto">{sitter.experience}</p>
        <SectionHeader>{t('availability')}</SectionHeader>
        <p dir="auto">{sitter.availability}</p>
        <p>
          {t('rate_x', sitter.rate, '')} {enumLabel('RateUnit', sitter.rateUnit)}
        </p>
        <p>{t('sitter_area_note', Dubai.nameOf(sitter.neighborhood))}</p>
        {profile?.uid !== uid && (
          <>
            <Link className="primary" to={'/book/' + uid}>
              {t('request_booking')}
            </Link>
            <Link className="outlined" to={'/chat-with/' + uid}>
              {t('message')}
            </Link>
          </>
        )}
        {profile?.uid === uid && (
          <Link className="outlined" to="/sitter-edit">
            {t('edit')}
          </Link>
        )}
        <SectionHeader>{t('reviews')}</SectionHeader>
        {reviews?.length ? (
          reviews
            .sort((a, b) => b.createdAt - a.createdAt)
            .map((r) => (
              <article className="card" key={r.id}>
                <strong>
                  @{r.authorName} {'★'.repeat(r.rating)}
                </strong>
                <p dir="auto">{r.text}</p>
                <small>{dateTime(r.createdAt)}</small>
              </article>
            ))
        ) : (
          <EmptyState text={t('no_reviews')} />
        )}
      </div>
    </>
  );
}
function SitterEdit() {
  const { profile } = useApp(),
    uid = profile?.uid,
    navigate = useNavigate(),
    action = useAction(),
    [post, setPost] = useState<SitterProfile>({
      ...SitterProfileDefaults,
      petTypes: ['cat'],
      services: ['home_visits'],
    }),
    [loaded, setLoaded] = useState(false);
  useEffect(() => {
    if (uid)
      void import('../../data/Firestore')
        .then(({ read }) => read(`sitters/${uid}`, SitterProfileFromDoc))
        .then((p) => {
          if (p) setPost(p);
          setLoaded(true);
        });
  }, [uid]);
  const change = (p: Partial<SitterProfile>) => setPost((s) => ({ ...s, ...p }));
  if (!loaded) return <Loading />;
  return (
    <>
      <AppTopBar title={t('sitter_profile')} />
      <form
        className="screen"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            const p = LocationPrivacy.fuzz(post.lat, post.lng, 'high', profile!.uid);
            await SittingRepository.saveSitter(profile!, { ...post, ...p });
            navigate('/sitter/' + profile!.uid);
          }, t('saved'));
        }}
      >
        <Field
          label={t('sitter_headline')}
          value={post.headline}
          onChange={(headline) => change({ headline })}
          limit={80}
          required
        />
        <Field
          label={t('description')}
          value={post.description}
          onChange={(description) => change({ description })}
          limit={1500}
          multiline
          required
        />
        <Field
          label={t('experience')}
          value={post.experience}
          onChange={(experience) => change({ experience })}
          limit={600}
          multiline
        />
        <SectionHeader>{t('animal_type')}</SectionHeader>
        <Chips
          options={enums.AnimalType}
          value={post.petTypes}
          multi
          onChange={(v) => change({ petTypes: toggle(post.petTypes, v) })}
        />
        <SectionHeader>{t('services')}</SectionHeader>
        <Chips
          options={enums.SitterService}
          value={post.services}
          multi
          onChange={(v) => change({ services: toggle(post.services, v) })}
        />
        <Field
          label={t('availability')}
          value={post.availability}
          onChange={(availability) => change({ availability })}
          limit={300}
        />
        <Field
          label={t('rate_optional')}
          value={post.rate}
          onChange={(rate) => change({ rate: Number(rate) })}
          type="number"
          min="0"
          max="99999"
        />
        <Chips
          options={enums.RateUnit}
          value={post.rateUnit}
          onChange={(rateUnit) => change({ rateUnit })}
        />
        <LocationField
          lat={post.lat}
          lng={post.lng}
          onChange={(lat, lng) => change({ lat, lng })}
        />
        <p className="muted">{t('sitter_privacy_note')}</p>
        <Switch
          label={t('active')}
          checked={post.active}
          onChange={(active) => change({ active })}
        />
        <ErrorText text={action.error} />
        <button
          className="primary"
          disabled={
            action.busy ||
            !post.services.length ||
            !post.petTypes.length ||
            !Dubai.contains(post.lat, post.lng)
          }
        >
          {t('save')}
        </button>
      </form>
    </>
  );
}
function BookingEditor() {
  const { uid = '' } = useParams(),
    { profile } = useApp(),
    navigate = useNavigate(),
    action = useAction(),
    [petTypes, setPetTypes] = useState(['cat']),
    [count, setCount] = useState('1'),
    [start, setStart] = useState(new Date(Date.now() + 4 * 3600000).toISOString().slice(0, 10)),
    [end, setEnd] = useState(
      new Date(Date.now() + 4 * 3600000 + 86400000).toISOString().slice(0, 10),
    ),
    [notes, setNotes] = useState('');
  const sitter = useDoc(
    () => SittingRepository.sitterFlow(uid),
    SitterProfileFromDoc,
    'booking-sitter-' + uid,
  );
  if (sitter === undefined) return <Loading />;
  if (!sitter) return <EmptyState text={t('sitter_removed')} />;
  return (
    <>
      <AppTopBar title={t('request_booking')} />
      <form
        className="screen"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            await SittingRepository.requestBooking(
              sitter,
              petTypes,
              Number(count),
              new Date(start + 'T00:00:00+04:00').getTime(),
              new Date(end + 'T00:00:00+04:00').getTime(),
              notes,
              profile!,
            );
            navigate('/bookings');
          }, t('booking_sent'));
        }}
      >
        <h2>@{sitter.name}</h2>
        <SectionHeader>{t('animal_type')}</SectionHeader>
        <Chips
          options={enums.AnimalType}
          multi
          value={petTypes}
          onChange={(v) => setPetTypes((p) => toggle(p, v))}
        />
        <Field
          label={t('how_many_pets')}
          value={count}
          onChange={setCount}
          type="number"
          min="1"
          max="20"
          required
        />
        <Field label={t('quiet_start')} value={start} onChange={setStart} type="date" required />
        <Field label={t('quiet_end')} value={end} onChange={setEnd} type="date" required />
        <Field
          label={t('booking_notes')}
          value={notes}
          onChange={setNotes}
          limit={1000}
          multiline
        />
        <ErrorText text={action.error} />
        <button className="primary" disabled={action.busy || !petTypes.length}>
          {t('send_request')}
        </button>
      </form>
    </>
  );
}
function RemovedSitterNote({ uid }: { uid: string }) {
  const sitter = useDoc(() => SittingRepository.sitterFlow(uid), SitterProfileFromDoc, 'booked-sitter-' + uid);
  return sitter === null ? <p>{t('sitter_no_longer_listed')}</p> : null;
}

function Bookings() {
  const { profile } = useApp(),
    location = useLocation(),
    uid = profile?.uid ?? '',
    action = useAction(),
    [sitter, setSitter] = useState(new URLSearchParams(location.search).get('tab') === 'sitter'),
    [review, setReview] = useState<Booking | null>(null),
    [rating, setRating] = useState('5'),
    [text, setText] = useState('');
  const bookings = useList(
    () =>
      uid ? (sitter ? SittingRepository.asSitter(uid) : SittingRepository.asOwner(uid)) : null,
    BookingFromDoc,
    'bookings-' + uid + '-' + sitter,
  );
  return (
    <>
      <AppTopBar title={t('bookings')} />
      <div className="screen">
        <div className="segments">
          <button className={!sitter ? 'selected' : ''} onClick={() => setSitter(false)}>
            {t('as_owner')}
          </button>
          <button className={sitter ? 'selected' : ''} onClick={() => setSitter(true)}>
            {t('as_sitter')}
          </button>
        </div>
        {!bookings ? (
          <Loading />
        ) : bookings.length ? (
          bookings
            .sort((a, b) => b.createdAt - a.createdAt)
            .map((b) => (
              <article className="card" key={b.id}>
                <h3>@{sitter ? b.ownerName : b.sitterName}</h3>
                <StatusPill value={b.status} group="BookingStatus" />
                {!sitter && <RemovedSitterNote uid={b.sitterId} />}
                <p>
                  {dateTime(b.startAt)} → {dateTime(b.endAt)}
                </p>
                <p dir="auto">{b.notes}</p>
                {b.sitterNote && <p>{t('sitter_note_x', b.sitterNote)}</p>}
                <div className="row wrap">
                  {sitter && b.status === 'requested' && (
                    <>
                      <button
                        className="primary"
                        disabled={action.busy}
                        onClick={() =>
                          void action.run(() =>
                            SittingRepository.setStatus(b, 'accepted', profile!),
                          )
                        }
                      >
                        {t('accept')}
                      </button>
                      <button
                        className="outlined"
                        disabled={action.busy}
                        onClick={() =>
                          void action.run(() =>
                            SittingRepository.setStatus(b, 'declined', profile!),
                          )
                        }
                      >
                        {t('decline')}
                      </button>
                    </>
                  )}
                  {b.status === 'accepted' && (
                    <button
                      className="primary"
                      disabled={action.busy}
                      onClick={() =>
                        void action.run(() => SittingRepository.setStatus(b, 'completed', profile!))
                      }
                    >
                      {t('mark_completed')}
                    </button>
                  )}
                  {['requested', 'accepted'].includes(b.status) &&
                    (!sitter || b.status === 'accepted') && (
                      <button
                        className="text-button"
                        disabled={action.busy}
                        onClick={() =>
                          void action.run(() =>
                            SittingRepository.setStatus(b, 'cancelled', profile!),
                          )
                        }
                      >
                        {t('cancel_booking')}
                      </button>
                    )}
                  {!sitter && b.status === 'completed' && !b.reviewed && (
                    <button
                      className="outlined"
                      onClick={() => {
                        setText('');
                        setRating('5');
                        setReview(b);
                      }}
                    >
                      {t('leave_review')}
                    </button>
                  )}
                </div>
              </article>
            ))
        ) : (
          <EmptyState text={t('no_bookings_owner')} />
        )}
      </div>
      {review && (
        <Dialog title={t('leave_review')} onClose={() => setReview(null)}>
          <label className="field">
            <span>{t('rating_n', Number(rating))}</span>
            <select value={rating} onChange={(e) => setRating(e.target.value)}>
              {[1, 2, 3, 4, 5].map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <Field label={t('review_text')} value={text} onChange={setText} limit={800} multiline />
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await SittingRepository.review(review, Number(rating), text, profile!);
                setReview(null);
              }, t('review_thanks'))
            }
          >
            {t('save')}
          </button>
        </Dialog>
      )}
    </>
  );
}
