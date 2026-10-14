import { ReportButton } from '../components/ReportButton';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useList, useDoc, useAction } from '../../state';
import { RescueRepository } from '../../data/RescueRepository';
import { CatRepository } from '../../data/CatRepository';
import { ImageRepository } from '../../data/ImageRepository';
import { RescueReportDefaults, RescueReportFromDoc, type RescueReport } from '../../data/model';
import { enums } from '../../data/model/Enums';
import { Dubai } from '../../data/Neighborhoods';
import { processPhoto, type ProcessedPhoto } from '../../util/images';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Field,
  SectionHeader,
  Chips,
  Photo,
  StatusPill,
  EmptyState,
  Loading,
  ErrorText,
  Switch,
} from '../components/Components';
import { LocationField } from '../components/LocationField';
export default function RescueScreens() {
  const path = useLocation().pathname;
  return path === '/rescue-new' ? (
    <RescueEditor />
  ) : path === '/rescue' ? (
    <RescueList />
  ) : (
    <RescueDetail />
  );
}
function RescueList() {
  const { profile } = useApp(),
    uid = profile?.uid ?? '',
    volunteer = ['volunteer', 'moderator', 'admin'].includes(profile?.role ?? ''),
    [board, setBoard] = useState(false);
  const reports = useList(
    () =>
      uid ? (board ? RescueRepository.volunteerBoard() : RescueRepository.myReports(uid)) : null,
    RescueReportFromDoc,
    'rescue-list-' + uid + '-' + board,
  );
  return (
    <>
      <AppTopBar title={t('rescue_reports')} />
      <div className="screen">
        <Link className="primary" to="/rescue-new">
          {t('report_emergency')}
        </Link>
        {volunteer && <Switch label={t('volunteer_board')} checked={board} onChange={setBoard} />}{' '}
        {!reports ? (
          <Loading />
        ) : reports.length ? (
          reports
            .sort((a, b) => b.updatedAt - a.updatedAt)
            .map((r) => (
              <Link className="card" to={'/rescue/' + r.id} key={r.id}>
                <div className="row">
                  <StatusPill group="Urgency" value={r.urgency} />
                  <StatusPill group="RescueStatus" value={r.status} />
                </div>
                <h3>
                  {t(enums.RescueType.find((x) => x.key === r.type)?.label ?? 'rescue_other')}
                </h3>
                <p dir="auto">{r.description}</p>
                <small>
                  {Dubai.nameOf(r.neighborhood)} · {dateTime(r.createdAt)}
                </small>
              </Link>
            ))
        ) : (
          <EmptyState text={t('no_rescue_reports')} />
        )}
      </div>
    </>
  );
}
function RescueEditor() {
  const params = new URLSearchParams(useLocation().search),
    catId = params.get('catId'),
    { profile } = useApp(),
    action = useAction(),
    navigate = useNavigate(),
    [post, setPost] = useState<RescueReport>({ ...RescueReportDefaults }),
    [photos, setPhotos] = useState<{ data: ProcessedPhoto; id?: string }[]>([]),
    [processing, setProcessing] = useState(false);
  useEffect(() => {
    if (catId)
      void CatRepository.cat(catId).then((cat) => {
        if (cat)
          setPost((p) => ({ ...p, catId: cat.id, catName: cat.name, lat: cat.lat, lng: cat.lng }));
      });
  }, [catId]);
  const change = (patch: Partial<RescueReport>) => setPost((p) => ({ ...p, ...patch }));
  return (
    <>
      <AppTopBar title={t('report_emergency')} />
      <form
        className="screen"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            const ids = [];
            for (const p of photos) {
              p.id ??= await ImageRepository.upload(profile!.uid, p.data);
              ids.push(p.id);
            }
            const id = await RescueRepository.submit({ ...post, photoIds: ids }, profile!);
            navigate('/rescue/' + id);
          }, t('saved'));
        }}
      >
        <SectionHeader>{t('record_type')}</SectionHeader>
        <Chips options={enums.RescueType} value={post.type} onChange={(type) => change({ type })} />
        <SectionHeader>{t('urgency')}</SectionHeader>
        <Chips
          options={enums.Urgency}
          value={post.urgency}
          onChange={(urgency) => change({ urgency })}
        />
        <Field
          label={t('description')}
          value={post.description}
          onChange={(description) => change({ description })}
          multiline
          required
        />
        <Field
          label={t('landmark')}
          value={post.landmark}
          onChange={(landmark) => change({ landmark })}
        />
        <label className="outlined file-button">
          {t('add_photo')} ({photos.length}/4)
          <input
            type="file"
            accept="image/*"
            multiple
            disabled={processing || photos.length >= 4}
            onChange={(e) => {
              const files = Array.from(e.target.files ?? []).slice(0, 4 - photos.length);
              setProcessing(true);
              void action
                .run(async () => {
                  const all: { data: ProcessedPhoto; id?: string }[] = [];
                  for (const file of files) all.push({ data: await processPhoto(file) });
                  setPhotos((p) => [...p, ...all]);
                })
                .finally(() => setProcessing(false));
            }}
          />
        </label>
        <LocationField
          lat={post.lat}
          lng={post.lng}
          onChange={(lat, lng) => change({ lat, lng })}
        />
        <ErrorText text={action.error} />
        <button
          className="primary"
          disabled={action.busy || processing || !Dubai.contains(post.lat, post.lng)}
        >
          {t('submit')}
        </button>
      </form>
    </>
  );
}
function RescueDetail() {
  const { id = '' } = useParams(),
    { profile } = useApp(),
    action = useAction(),
    report = useDoc(() => RescueRepository.report(id), RescueReportFromDoc, 'rescue-' + id),
    [note, setNote] = useState('');
  if (report === undefined) return <Loading />;
  if (!report) return <EmptyState text={t('rescue_not_found')} />;
  const mod = ['moderator', 'admin'].includes(profile?.role ?? ''),
    volunteer = ['volunteer', 'moderator', 'admin'].includes(profile?.role ?? '');
  return (
    <>
      <AppTopBar title={t('rescue_report')} />
      <div className="screen">
        <div className="row">
          <StatusPill group="RescueStatus" value={report.status} />
          <StatusPill group="Urgency" value={report.urgency} />
        </div>
        <h2>{t(enums.RescueType.find((x) => x.key === report.type)?.label ?? 'rescue_other')}</h2>
        <p dir="auto">{report.description}</p>
        <p dir="auto">{report.landmark}</p>
        <p>{Dubai.nameOf(report.neighborhood)}</p>
        <div className="photo-grid">
          {report.photoIds.map((p) => (
            <Photo id={p} key={p} />
          ))}
        </div>
        {report.catId && <Link to={'/cat/' + report.catId}>{report.catName}</Link>}
        <ReportButton type="rescue" id={id} label={report.catName || report.type} />
        <SectionHeader>{t('progress_updates')}</SectionHeader>
        {report.updates.map((u, i) => (
          <article className="card" key={i}>
            <strong>
              {t(enums.RescueStatus.find((x) => x.key === u.status)?.label ?? 'rstatus_submitted')}
            </strong>
            <p dir="auto">{u.note}</p>
            <small>
              @{u.byName} · {dateTime(u.at)}
            </small>
          </article>
        ))}
        <Field label={t('note')} value={note} onChange={setNote} multiline />
        <button
          className="outlined"
          disabled={action.busy || !note.trim()}
          onClick={() =>
            void action.run(async () => {
              await RescueRepository.addNote(report, note, profile!);
              setNote('');
            }, t('saved'))
          }
        >
          {t('add_update')}
        </button>
        <div className="row wrap">
          {mod &&
            report.status === 'submitted' &&
            ['approved', 'rejected'].map((status) => (
              <button
                className="primary"
                key={status}
                disabled={action.busy}
                onClick={() =>
                  void action.run(() =>
                    RescueRepository.changeStatus(report, status, note, profile!),
                  )
                }
              >
                {t(status === 'approved' ? 'approve' : 'reject')}
              </button>
            ))}
          {volunteer && ['approved', 'in_progress'].includes(report.status) && (
            <>
              <button
                className="outlined"
                disabled={action.busy}
                onClick={() =>
                  void action.run(() =>
                    RescueRepository.changeStatus(report, 'in_progress', note, profile!, true),
                  )
                }
              >
                {t('take_action')}
              </button>
              <button
                className="primary"
                disabled={action.busy}
                onClick={() =>
                  void action.run(() =>
                    RescueRepository.changeStatus(report, 'resolved', note, profile!),
                  )
                }
              >
                {t('mark_resolved')}
              </button>
            </>
          )}
        </div>
        <ErrorText text={action.error} />
      </div>
    </>
  );
}
