import { usePager, PagingFooter, useDebounced } from '../../data/usePager';
import { useAsyncList } from '../../data/useAsyncList';
import { SearchTerms } from '../../data/SearchTerms';
import { list } from '../../data/Firestore';
import { query, limit } from 'firebase/firestore';
import { BreedTraits } from '../components/BreedTraits';
import { shareCat } from '../../util/share';
import PhotoViewer from './PhotoViewer';
import { ReportButton } from '../components/ReportButton';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useList, useDoc, useAction } from '../../state';
import { CatRepository } from '../../data/CatRepository';
import { UserRepository } from '../../data/UserRepository';
import { CatFromDoc, SightingFromDoc, MedicalRecordFromDoc, type Cat } from '../../data/model';
import { enums } from '../../data/model/Enums';
import { Similarity } from '../../ai/Similarity';
import { Dubai, distanceKm } from '../../data/Neighborhoods';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Loading,
  EmptyState,
  Field,
  Chips,
  Photo,
  SectionHeader,
  StatusPill,
  Dialog,
  Switch,
  ErrorText,
  enumLabel,
} from '../components/Components';
export default function CatScreens() {
  const path = useLocation().pathname;
  return path === '/cats' ? (
    <CatList />
  ) : path.startsWith('/cat-edit/') ? (
    <CatEdit />
  ) : path.startsWith('/cat-merge/') ? (
    <CatMerge />
  ) : path.startsWith('/photo/') ? (
    <PhotoViewer />
  ) : (
    <CatDetail />
  );
}
function CatList() {
  const { profile } = useApp(),
    [search, setSearch] = useState(''),
    [following, setFollowing] = useState(false),
    [sort, setSort] = useState('recent'),
    [coat, setCoat] = useState(''),
    [health, setHealth] = useState(''),
    [tnr, setTnr] = useState(''),
    [neighborhood, setNeighborhood] = useState(''),
    [period, setPeriod] = useState(0),
    [filters, setFilters] = useState(false);
  const settled = useDebounced(search),
    word = SearchTerms.words(settled).sort((a, b) => b.length - a.length)[0] ?? '';
  const page = usePager(
    () =>
      following
        ? null
        : word
          ? CatRepository.searchQuery(word)
          : neighborhood
            ? CatRepository.neighborhoodQuery(neighborhood)
            : CatRepository.directoryQuery(
                sort === 'name' ? 'name' : sort === 'sightings' ? 'sightingCount' : 'lastSeen',
                sort !== 'name',
              ),
    CatFromDoc,
    'cats-' + settled + '-' + following + '-' + neighborhood + '-' + sort,
    25,
    () => (word ? null : CatRepository.directoryQuery()),
  );
  const followed = useAsyncList(
    () => (following ? CatRepository.byIds(profile?.followedCats ?? []) : Promise.resolve([])),
    'followed-' + profile?.uid + '-' + following + '-' + profile?.followedCats.join(','),
  );
  const cats = following ? followed : page.items;
  const shown = cats
    ?.filter(
      (c) =>
        !c.hidden &&
        !c.mergedInto &&
        (!following || profile?.followedCats.includes(c.id)) &&
        (!coat || c.coatColor === coat) &&
        (!health || c.healthStatus === health) &&
        (!tnr || c.tnrStatus === tnr) &&
        (!neighborhood || c.neighborhoods.includes(neighborhood)) &&
        (period === 0 || Date.now() - c.lastSeen <= period * 86400000) &&
        SearchTerms.words(settled).every((w) =>
          SearchTerms.matches(w, SearchTerms.words(c.name, ...c.aliases)),
        ),
    )
    .sort((a, b) =>
      sort === 'name'
        ? a.name.localeCompare(b.name)
        : sort === 'sightings'
          ? b.sightingCount - a.sightingCount
          : b.lastSeen - a.lastSeen,
    );
  return (
    <>
      <AppTopBar title={t('community_cats')} />
      <div className="screen">
        <Field label={t('search_cats')} value={search} onChange={setSearch} />
        <label className="field">
          <span>{t('neighborhood')}</span>
          <select value={neighborhood} onChange={(e) => setNeighborhood(e.target.value)}>
            <option value="">{t('all_neighborhoods')}</option>
            {Dubai.neighborhoods.map((n) => (
              <option value={n.key} key={n.key}>
                {Dubai.nameOf(n.key)}
              </option>
            ))}
          </select>
        </label>
        <div className="row">
          <button className="outlined" onClick={() => setFilters(true)}>
            {t('filters')}
          </button>
          <Link className="primary" to="/sighting-new">
            {t('report_sighting')}
          </Link>
        </div>
        <Switch label={t('following')} checked={following} onChange={setFollowing} />
        <div className="chips">
          {['recent', 'name', 'sightings'].map((s) => (
            <button
              className={'chip ' + (sort === s ? 'selected' : '')}
              key={s}
              onClick={() => setSort(s)}
            >
              {t('sort_' + s)}
            </button>
          ))}
        </div>
        {!shown ? (
          <Loading />
        ) : shown.length ? (
          shown.map((c) => (
            <Link className="card cat-card" to={'/cat/' + c.id} key={c.id}>
              <Photo id={c.coverPhotoId || c.photoIds[0]} />
              <div>
                <h3 dir="auto">{c.name}</h3>
                <p>
                  {enumLabel('CoatColor', c.coatColor)} · {enumLabel('CoatPattern', c.pattern)}
                </p>
                <p>
                  {Dubai.nameOf(c.neighborhood)} · {c.sightingCount}
                </p>
                <StatusPill group="HealthStatus" value={c.healthStatus} />
              </div>
            </Link>
          ))
        ) : (
          <EmptyState text={t('no_cats_found')} />
        )}
        {!following && <PagingFooter page={page} visibleCount={shown?.length} />}
      </div>
      {filters && (
        <Dialog title={t('filters')} onClose={() => setFilters(false)}>
          <SectionHeader>{t('filter_coat')}</SectionHeader>
          <Chips
            options={[{ key: '', label: 'all' }, ...enums.CoatColor]}
            value={coat}
            onChange={setCoat}
          />
          <SectionHeader>{t('filter_last_seen')}</SectionHeader>
          <div className="chips">
            {[0, 1, 7, 30, 90].map((days, i) => (
              <button
                className={'chip ' + (period === days ? 'selected' : '')}
                key={days}
                onClick={() => setPeriod(days)}
              >
                {t(
                  ['period_any', 'period_day', 'period_week', 'period_month', 'period_quarter'][i],
                )}
              </button>
            ))}
          </div>
          <SectionHeader>{t('filter_health')}</SectionHeader>
          <Chips
            options={[{ key: '', label: 'all' }, ...enums.HealthStatus]}
            value={health}
            onChange={setHealth}
          />
          <SectionHeader>{t('filter_tnr')}</SectionHeader>
          <Chips
            options={[{ key: '', label: 'all' }, ...enums.TnrStatus]}
            value={tnr}
            onChange={setTnr}
          />
          <button
            className="text-button"
            onClick={() => {
              setCoat('');
              setHealth('');
              setTnr('');
              setPeriod(0);
            }}
          >
            {t('reset')}
          </button>
          <button className="primary" onClick={() => setFilters(false)}>
            {t('apply')}
          </button>
        </Dialog>
      )}
    </>
  );
}
function CatDetail() {
  const { id = '' } = useParams(),
    { profile, toast } = useApp(),
    action = useAction();
  const cat = useDoc(() => CatRepository.catFlow(id), CatFromDoc, 'cat-' + id),
    sightings = useList(
      () => CatRepository.sightingsForCat(id),
      SightingFromDoc,
      'cat-sightings-' + id,
    ),
    records = useList(
      () => CatRepository.medicalRecords(id),
      MedicalRecordFromDoc,
      'cat-medical-' + id,
    );
  const [tab, setTab] = useState('overview'),
    [modal, setModal] = useState(''),
    [text, setText] = useState(''),
    [recordType, setRecordType] = useState('note');
  if (cat === undefined) return <Loading />;
  if (!cat) return <EmptyState text={t('cat_not_found')} />;
  const own = cat.createdBy === profile?.uid,
    mod = !!profile && ['moderator', 'admin'].includes(profile.role),
    volunteer = !!profile && ['volunteer', 'moderator', 'admin'].includes(profile.role),
    following = profile?.followedCats.includes(id) ?? false;
  return (
    <>
      <AppTopBar title={cat.name}>
        <button
          className="text-button"
          onClick={() =>
            void action.run(async () => {
              const message = await shareCat(cat);
              if (message) toast(message);
            })
          }
        >
          {t('share_cat')}
        </button>
        {(own || mod) && (
          <Link className="text-button" to={'/cat-edit/' + id}>
            {t('edit')}
          </Link>
        )}
      </AppTopBar>
      {cat.hidden && <p className="banner">{t('hidden_by_moderators')}</p>}
      {cat.mergedInto && (
        <Link className="banner" to={'/cat/' + cat.mergedInto}>
          {t('merged_banner')}
        </Link>
      )}
      <div className="cat-cover">
        <Photo id={cat.coverPhotoId || cat.photoIds[0]} thumb={false} />
      </div>
      <div className="screen">
        <h2 dir="auto">{cat.name}</h2>
        <div className="row wrap">
          <StatusPill group="HealthStatus" value={cat.healthStatus} />
          <span className="chip">{enumLabel('TnrStatus', cat.tnrStatus)}</span>
        </div>
        <p>{t('seen_count_last', cat.sightingCount, dateTime(cat.lastSeen))}</p>
        <div className="row wrap">
          <Link className="primary" to={'/sighting-new?catId=' + id}>
            {t('i_saw_this_cat')}
          </Link>
          <button
            className="outlined"
            disabled={action.busy}
            onClick={() =>
              void action.run(() => UserRepository.setFollowing(profile!.uid, id, !following))
            }
          >
            {t(following ? 'following' : 'follow')}
          </button>
        </div>
        <div className="segments">
          {['overview', 'timeline', 'photos', 'health'].map((value) => (
            <button
              key={value}
              className={tab === value ? 'selected' : ''}
              onClick={() => setTab(value)}
            >
              {t('tab_' + value)}
            </button>
          ))}
        </div>
        {tab === 'overview' && (
          <>
            <SectionHeader>{t('description')}</SectionHeader>
            <p dir="auto">{cat.description}</p>
            <p dir="auto">{cat.distinctiveMarks}</p>
            <SectionHeader>{t('appearance')}</SectionHeader>
            <div className="chips">
              {[
                enumLabel('CoatColor', cat.coatColor),
                enumLabel('CoatPattern', cat.pattern),
                enumLabel('CatSize', cat.size),
                enumLabel('CatSex', cat.sex),
              ].map((text, i) => (
                <span className="chip" key={i}>
                  {text}
                </span>
              ))}
            </div>
            <p>{t('last_area', Dubai.nameOf(cat.neighborhood))}</p>
            <p className="muted">{t('location_is_approximate')}</p>
            <p>
              {t('registered_by')} @{cat.createdByName}
            </p>
            <p>
              {t('first_seen')} {dateTime(cat.firstSeen)}
            </p>
            <BreedTraits hints={cat.breedHints} />
            <SectionHeader>{t('also_known_as')}</SectionHeader>
            <p dir="auto">{cat.aliases.join(' · ')}</p>
            <button
              className="text-button"
              onClick={() => {
                setText('');
                setModal('alias');
              }}
            >
              {t('add_alias')}
            </button>
            <Link className="text-button" to={'/cat-merge/' + id}>
              {t('suggest_merge')}
            </Link>
          </>
        )}
        {tab === 'timeline' &&
          (sightings
            ?.filter((s) => s.status !== 'rejected')
            .sort((a, b) => b.seenAt - a.seenAt)
            .map((s) => (
              <Link className="card" to={'/sighting/' + s.id} key={s.id}>
                <p>
                  @{s.username} · {dateTime(s.seenAt)}
                </p>
                <p dir="auto">{s.notes}</p>
                {s.photoIds[0] && <Photo id={s.photoIds[0]} />}
              </Link>
            )) ?? <Loading />)}
        {tab === 'photos' && (
          <div className="photo-grid">
            {cat.photoIds.map((photoId) => (
              <Link to={'/photo/' + photoId + '?catId=' + id} key={photoId}>
                <Photo id={photoId} />
              </Link>
            ))}
          </div>
        )}
        {tab === 'health' && (
          <>
            <SectionHeader>{t('verified_medical_info')}</SectionHeader>
            {records?.length ? (
              records
                .sort((a, b) => b.at - a.at)
                .map((r) => (
                  <article className="card" key={r.id}>
                    <strong>{enumLabel('MedicalType', r.type)}</strong>
                    <p dir="auto">{r.note}</p>
                    <small>
                      @{r.byName} · {dateTime(r.at)}
                    </small>
                  </article>
                ))
            ) : (
              <EmptyState text={t('no_verified_records')} />
            )}{' '}
            {volunteer && (
              <button
                className="outlined"
                onClick={() => {
                  setText('');
                  setModal('medical');
                }}
              >
                {t('add_verified_record')}
              </button>
            )}
            <SectionHeader>{t('community_observations')}</SectionHeader>
            {(sightings ?? [])
              .filter(
                (s) =>
                  s.healthTags.length ||
                  s.needsMedical ||
                  s.possiblyNotNeutered ||
                  s.earTipped === 'yes',
              )
              .map((s) => (
                <article className="card" key={s.id}>
                  <Link to={'/sighting/' + s.id}>
                    {dateTime(s.seenAt)} · @{s.username}
                  </Link>
                  <p>
                    {[
                      s.earTipped === 'yes' ? t('obs_ear_tipped') : '',
                      s.possiblyNotNeutered ? t('obs_not_neutered') : '',
                      s.needsMedical ? t('needs_medical') : '',
                      ...s.healthTags.map((v) => enumLabel('HealthTag', v)),
                    ]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                  <small>{t('community_observation_label')}</small>
                </article>
              ))}
            {sightings &&
              !sightings.some(
                (s) =>
                  s.healthTags.length ||
                  s.needsMedical ||
                  s.possiblyNotNeutered ||
                  s.earTipped === 'yes',
              ) && <p>{t('no_observations')}</p>}
            <p>{t('tnr_community_note')}</p>
            <Link className="text-button" to={'/sighting-new?catId=' + id}>
              {t('add_observation')}
            </Link>
          </>
        )}
        <ReportButton type="cat" id={id} label={cat.name} />
        <Link className="outlined" to={'/rescue-new?catId=' + id}>
          {t('request_help')}
        </Link>
        {mod && (
          <button
            className="text-button danger"
            onClick={() =>
              void action.run(() => CatRepository.setCatHidden(cat, !cat.hidden, profile!))
            }
          >
            {t(cat.hidden ? 'restore_profile' : 'hide_profile')}
          </button>
        )}
      </div>
      {modal === 'alias' && (
        <Dialog title={t('add_alias')} onClose={() => setModal('')}>
          <Field label={t('alias')} value={text} limit={40} onChange={setText} />
          <button
            className="primary"
            disabled={action.busy || !text.trim()}
            onClick={() =>
              void action.run(async () => {
                await CatRepository.addAlias(id, text);
                setModal('');
              }, t('saved'))
            }
          >
            {t('save')}
          </button>
        </Dialog>
      )}
      {modal === 'medical' && (
        <Dialog title={t('add_verified_record')} onClose={() => setModal('')}>
          <Chips options={enums.MedicalType} value={recordType} onChange={setRecordType} />
          <Field label={t('note')} value={text} onChange={setText} multiline />
          <button
            className="primary"
            disabled={action.busy || !text.trim()}
            onClick={() =>
              void action.run(async () => {
                await CatRepository.addMedicalRecord(cat, recordType, text, profile!);
                setModal('');
              }, t('saved'))
            }
          >
            {t('save')}
          </button>
        </Dialog>
      )}
    </>
  );
}
function CatEdit() {
  const { id = '' } = useParams(),
    { profile } = useApp(),
    navigate = useNavigate(),
    action = useAction(),
    [cat, setCat] = useState<Cat | null>(null);
  useEffect(() => {
    void CatRepository.cat(id).then(setCat);
  }, [id]);
  if (!cat) return <Loading />;
  if (cat.createdBy !== profile?.uid && !['moderator', 'admin'].includes(profile?.role ?? ''))
    return <ErrorText text={t('err_permission')} />;
  const change = (patch: Partial<Cat>) => setCat((p) => (p ? { ...p, ...patch } : p));
  return (
    <>
      <AppTopBar title={t('edit_profile_cat')} />
      <form
        className="screen"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            await CatRepository.updateCat(id, {
              name: cat.name.trim(),
              description: cat.description.trim(),
              distinctiveMarks: cat.distinctiveMarks.trim(),
              coatColor: cat.coatColor,
              pattern: cat.pattern,
              size: cat.size,
              sex: cat.sex,
            });
            navigate('/cat/' + id);
          }, t('saved'));
        }}
      >
        <Field
          label={t('name')}
          value={cat.name}
          limit={40}
          onChange={(name) => change({ name })}
          required
        />
        <Field
          label={t('description')}
          value={cat.description}
          limit={800}
          onChange={(description) => change({ description })}
          multiline
        />
        <Field
          label={t('distinctive_marks')}
          value={cat.distinctiveMarks}
          limit={300}
          onChange={(distinctiveMarks) => change({ distinctiveMarks })}
          multiline
        />
        {(['CoatColor', 'CoatPattern', 'CatSize', 'CatSex'] as const).map((group, i) => (
          <div key={group}>
            <SectionHeader>{t(['coat_color', 'pattern', 'size', 'sex'][i])}</SectionHeader>
            <Chips
              options={enums[group]}
              value={[cat.coatColor, cat.pattern, cat.size, cat.sex][i]}
              onChange={(value) => change({ [['coatColor', 'pattern', 'size', 'sex'][i]]: value })}
            />
          </div>
        ))}
        <button className="primary" disabled={action.busy}>
          {t('save')}
        </button>
        <ErrorText text={action.error} />
      </form>
    </>
  );
}
function CatMerge() {
  const { id = '' } = useParams(),
    { profile } = useApp(),
    action = useAction(),
    navigate = useNavigate(),
    [duplicate, setDuplicate] = useState(''),
    [search, setSearch] = useState(''),
    [keepThis, setKeepThis] = useState(true),
    [reason, setReason] = useState('');
  const primary = useDoc(() => CatRepository.catFlow(id), CatFromDoc, 'merge-primary-' + id);
  const settled = useDebounced(search);
  const cats = useAsyncList(
    () =>
      settled.trim()
        ? list(
            query(CatRepository.searchQuery(SearchTerms.words(settled)[0] ?? settled), limit(40)),
            CatFromDoc,
          )
        : primary
          ? CatRepository.near(primary.lat, primary.lng, 5)
          : Promise.resolve([]),
    'merge-candidates-' + id + '-' + primary?.lat + '-' + primary?.lng + '-' + settled,
  );
  const other = cats?.find((c) => c.id === duplicate);
  const score = (other: NonNullable<typeof primary>) =>
    primary
      ? Similarity.score(
          primary.features,
          other.features,
          primary.coatColor,
          other.coatColor,
          primary.pattern,
          other.pattern,
          distanceKm(primary.lat, primary.lng, other.lat, other.lng),
        )
      : 0;
  const ranked = cats
    ?.filter(
      (c) =>
        c.id !== id &&
        !c.hidden &&
        !c.mergedInto &&
        [c.name, ...c.aliases].some((name) => name.toLowerCase().includes(search.toLowerCase())),
    )
    .map((cat) => ({ cat, score: score(cat) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, 40);
  return (
    <>
      <AppTopBar title={t('suggest_merge')} />
      <div className="screen">
        <p>{t('merge_hint', primary?.name ?? '')}</p>
        <Field label={t('search_cats')} value={search} onChange={setSearch} />
        {!ranked ? (
          <Loading />
        ) : (
          ranked.map(({ cat, score }) => (
            <button
              className="card merge-candidate"
              key={cat.id}
              onClick={() => {
                setDuplicate(cat.id);
                setKeepThis((primary?.sightingCount ?? 0) >= cat.sightingCount);
                setReason('');
              }}
            >
              <Photo id={cat.coverPhotoId || cat.photoIds[0]} />
              <span>
                <strong>{cat.name}</strong>
                <small>{Dubai.nameOf(cat.neighborhood)}</small>
                <progress value={score} max={1} />
              </span>
              <b>{t('percent', Math.trunc(score * 100))}</b>
            </button>
          ))
        )}
      </div>
      {primary && other && (
        <Dialog title={t('suggest_merge')} onClose={() => setDuplicate('')}>
          <p>{t('which_profile_to_keep')}</p>
          {[
            { keep: true, cat: primary },
            { keep: false, cat: other },
          ].map(({ keep, cat }) => (
            <label className="switch-row" key={cat.id}>
              <input
                type="radio"
                name="keep-cat"
                checked={keepThis === keep}
                onChange={() => setKeepThis(keep)}
              />
              <span>{cat.name}</span>
            </label>
          ))}
          <Field
            label={t('why_same_cat')}
            value={reason}
            limit={300}
            onChange={setReason}
            multiline
          />
          <p className="small muted">{t('merge_moderator_note')}</p>
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy || !primary || !other || !reason.trim()}
            onClick={() =>
              void action.run(async () => {
                await CatRepository.suggestMerge(
                  keepThis ? primary : other,
                  keepThis ? other : primary,
                  reason,
                  score(other),
                  profile!,
                );
                navigate('/cat/' + id);
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
