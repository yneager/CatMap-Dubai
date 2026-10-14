import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useApp, useList, useDoc, useAction } from '../../state';
import { StationRepository } from '../../data/StationRepository';
import {
  FeedingStationDefaults,
  FeedingStationFromDoc,
  StationCheckinFromDoc,
  MaintenanceReportFromDoc,
  type FeedingStation,
} from '../../data/model';
import { enums } from '../../data/model/Enums';
import { read } from '../../data/Firestore';
import { Dubai } from '../../data/Neighborhoods';
import { t, dateTime } from '../../i18n';
import {
  AppTopBar,
  Loading,
  EmptyState,
  Field,
  Switch,
  SectionHeader,
  Chips,
  Dialog,
  ErrorText,
  StatusPill,
} from '../components/Components';
import { LocationField } from '../components/LocationField';
export default function StationScreens() {
  const path = useLocation().pathname;
  return path === '/station-edit' ? (
    <StationEdit />
  ) : path === '/stations' ? (
    <StationList />
  ) : (
    <StationDetail />
  );
}
function StationList() {
  const stations = useList(StationRepository.stations, FeedingStationFromDoc, 'stations'),
    { profile } = useApp(),
    [inactive, setInactive] = useState(false),
    action = useAction();
  const shown = stations
    ?.filter((s) => inactive || s.active)
    .sort((a, b) => a.name.localeCompare(b.name));
  return (
    <>
      <AppTopBar title={t('feeding_stations')} />
      <div className="screen">
        {profile?.role === 'admin' && (
          <div className="row">
            <Link className="primary" to="/station-edit">
              {t('add')}
            </Link>
            <button
              className="outlined"
              disabled={action.busy}
              onClick={() =>
                void action.run(() => StationRepository.seedSamples(profile), t('saved'))
              }
            >
              {t('add_sample_stations')}
            </button>
          </div>
        )}
        <Switch label={t('show_inactive')} checked={inactive} onChange={setInactive} />
        {!shown ? (
          <Loading />
        ) : shown.length ? (
          shown.map((s) => (
            <Link className="card" key={s.id} to={'/station/' + s.id}>
              <h3 dir="auto">{s.name}</h3>
              <p>
                {Dubai.nameOf(s.neighborhood)} · {s.schedule}
              </p>
              {s.sample && <small>{t('demo_entry_label')}</small>}
              <p>
                {s.checkinCount} {t('total_checkins')}
              </p>
            </Link>
          ))
        ) : (
          <EmptyState text={t('no_stations')} />
        )}
      </div>
    </>
  );
}
function StationDetail() {
  const { id = '' } = useParams(),
    { profile } = useApp(),
    action = useAction(),
    station = useDoc(() => StationRepository.station(id), FeedingStationFromDoc, 'station-' + id),
    checkins = useList(
      () => StationRepository.checkins(id),
      StationCheckinFromDoc,
      'checkins-' + id,
    ),
    maintenance = useList(
      () => StationRepository.maintenanceFor(id),
      MaintenanceReportFromDoc,
      'maintenance-' + id,
    ),
    [modal, setModal] = useState(''),
    [cats, setCats] = useState('0'),
    [food, setFood] = useState('unknown'),
    [water, setWater] = useState('unknown'),
    [note, setNote] = useState(''),
    [issue, setIssue] = useState('other');
  if (station === undefined) return <Loading />;
  if (!station) return <EmptyState text={t('station_not_found')} />;
  return (
    <>
      <AppTopBar title={station.name}>
        {profile?.role === 'admin' && (
          <Link className="text-button" to={'/station-edit?id=' + id}>
            {t('edit')}
          </Link>
        )}
      </AppTopBar>
      <div className="screen">
        <h2 dir="auto">{station.name}</h2>
        <p dir="auto">{station.description}</p>
        <p>
          {station.operatorName} · {station.schedule}
        </p>
        <p>
          {station.foodType} · {station.hasWater ? t('water') : ''}
        </p>
        <p>{Dubai.nameOf(station.neighborhood)}</p>
        {station.sample && <p className="muted">{t('sample_data')}</p>}
        <div className="row">
          <button
            className="primary"
            onClick={() => {
              setNote('');
              setModal('checkin');
            }}
          >
            {t('check_in')}
          </button>
          <button
            className="outlined"
            onClick={() => {
              setNote('');
              setModal('maintenance');
            }}
          >
            {t('report_problem')}
          </button>
        </div>
        <SectionHeader>{t('total_checkins')}</SectionHeader>
        {checkins
          ?.sort((a, b) => b.at - a.at)
          .slice(0, 50)
          .map((c) => (
            <article className="card" key={c.id}>
              <strong>
                @{c.username} · {c.catsSeen}
              </strong>
              <p>
                {t(enums.SupplyLevel.find((v) => v.key === c.foodLevel)?.label ?? 'level_unknown')}{' '}
                ·{' '}
                {t(enums.SupplyLevel.find((v) => v.key === c.waterLevel)?.label ?? 'level_unknown')}
              </p>
              <p dir="auto">{c.note}</p>
              <small>{dateTime(c.at)}</small>
            </article>
          ))}
        <SectionHeader>{t('maintenance_reports')}</SectionHeader>
        {maintenance
          ?.sort((a, b) => b.createdAt - a.createdAt)
          .map((r) => (
            <article className="card" key={r.id}>
              <h3>
                {t(enums.MaintenanceIssue.find((v) => v.key === r.issue)?.label ?? 'issue_other')}
              </h3>
              <StatusPill value={r.status} group="MaintenanceStatus" />
              <p dir="auto">{r.description}</p>
              {profile?.role === 'admin' && (
                <button
                  className="text-button"
                  disabled={action.busy}
                  onClick={() =>
                    void action.run(() =>
                      StationRepository.updateMaintenance(r, 'resolved', '', profile),
                    )
                  }
                >
                  {t('mark_resolved')}
                </button>
              )}
            </article>
          ))}
      </div>
      {modal === 'checkin' && (
        <Dialog title={t('check_in')} onClose={() => setModal('')}>
          <Field
            label={t('cats_seen_here')}
            value={cats}
            onChange={setCats}
            type="number"
            min="0"
          />
          <SectionHeader>{t('food_level')}</SectionHeader>
          <Chips options={enums.SupplyLevel} value={food} onChange={setFood} />
          <SectionHeader>{t('water_level')}</SectionHeader>
          <Chips options={enums.SupplyLevel} value={water} onChange={setWater} />
          <Field label={t('note')} value={note} onChange={setNote} multiline />
          <ErrorText text={action.error} />
          <button
            className="primary"
            disabled={action.busy}
            onClick={() =>
              void action.run(async () => {
                await StationRepository.checkIn(
                  station,
                  Number(cats),
                  [],
                  food,
                  water,
                  note,
                  profile!,
                );
                setModal('');
              }, t('saved'))
            }
          >
            {t('save')}
          </button>
        </Dialog>
      )}
      {modal === 'maintenance' && (
        <Dialog title={t('report_problem')} onClose={() => setModal('')}>
          <Chips options={enums.MaintenanceIssue} value={issue} onChange={setIssue} />
          <Field label={t('description')} value={note} onChange={setNote} multiline />
          <button
            className="primary"
            disabled={action.busy || !note.trim()}
            onClick={() =>
              void action.run(async () => {
                await StationRepository.reportMaintenance(station, issue, note, profile!);
                setModal('');
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
function StationEdit() {
  const id = new URLSearchParams(useLocation().search).get('id'),
    { profile } = useApp(),
    navigate = useNavigate(),
    action = useAction(),
    [station, setStation] = useState<FeedingStation>({ ...FeedingStationDefaults });
  useEffect(() => {
    if (id)
      void read(`stations/${id}`, FeedingStationFromDoc).then((p) => {
        if (p) setStation(p);
      });
  }, [id]);
  const change = (patch: Partial<FeedingStation>) => setStation((p) => ({ ...p, ...patch }));
  return (
    <>
      <AppTopBar title={t('feeding_stations')} />
      <form
        className="screen"
        onSubmit={(e) => {
          e.preventDefault();
          void action.run(async () => {
            const id = await StationRepository.save(station, profile!);
            navigate('/station/' + id);
          }, t('saved'));
        }}
      >
        {(
          [
            ['name', 'name'],
            ['description', 'description'],
            ['operatorName', 'operator'],
            ['schedule', 'schedule'],
            ['foodType', 'food_type'],
          ] as const
        ).map(([key, label]) => (
          <Field
            key={key}
            label={t(label)}
            value={station[key]}
            onChange={(value) => change({ [key]: value })}
            multiline={key === 'description'}
            required={key === 'name'}
          />
        ))}
        <Switch
          label={t('water')}
          checked={station.hasWater}
          onChange={(hasWater) => change({ hasWater })}
        />
        <Switch
          label={t('active')}
          checked={station.active}
          onChange={(active) => change({ active })}
        />
        <Switch
          label={t('verified')}
          checked={station.verified}
          onChange={(verified) => change({ verified })}
        />
        <LocationField
          lat={station.lat}
          lng={station.lng}
          onChange={(lat, lng) => change({ lat, lng })}
        />
        <ErrorText text={action.error} />
        <button
          className="primary"
          disabled={action.busy || !Dubai.contains(station.lat, station.lng)}
        >
          {t('save')}
        </button>
        {id && (
          <button
            type="button"
            className="text-button danger"
            onClick={() =>
              void action.run(async () => {
                await StationRepository.delete(station, profile!);
                navigate('/stations');
              })
            }
          >
            {t('delete')}
          </button>
        )}
      </form>
    </>
  );
}
