import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useApp, useDoc, useAction, useSettings } from '../../state';
import { CatRepository } from '../../data/CatRepository';
import { SightingFromDoc } from '../../data/model';
import { enums } from '../../data/model/Enums';
import { t } from '../../i18n';
import {
  AppTopBar,
  Field,
  Chips,
  Switch,
  SectionHeader,
  Loading,
  EmptyState,
  ErrorText,
} from '../components/Components';
import { LocationField } from '../components/LocationField';
import { toggle } from '../explore/Filtering';
export default function EditSighting() {
  const { id = '' } = useParams(),
    navigate = useNavigate(),
    { profile } = useApp(),
    settings = useSettings(),
    action = useAction(),
    sighting = useDoc(() => CatRepository.sightingFlow(id), SightingFromDoc, 'sighting-edit-' + id),
    [notes, setNotes] = useState(''),
    [position, setPosition] = useState({ lat: 0, lng: 0 }),
    [changed, setChanged] = useState(false),
    [health, setHealth] = useState<string[]>([]),
    [behavior, setBehavior] = useState<string[]>([]),
    [ear, setEar] = useState('unsure'),
    [tnr, setTnr] = useState(false),
    [medical, setMedical] = useState(false);
  useEffect(() => {
    if (sighting) {
      setNotes(sighting.notes);
      setPosition({ lat: sighting.lat, lng: sighting.lng });
      setHealth(sighting.healthTags);
      setBehavior(sighting.behaviorTags);
      setEar(sighting.earTipped);
      setTnr(sighting.possiblyNotNeutered);
      setMedical(sighting.needsMedical);
    }
  }, [sighting]);
  if (sighting === undefined) return <Loading />;
  if (!sighting) return <EmptyState text={t('sighting_deleted')} />;
  if (sighting.userId !== profile?.uid) return <ErrorText text={t('err_permission')} />;
  return (
    <>
      <AppTopBar title={t('edit')} />
      <div className="screen">
        <SectionHeader>{t('health_observations')}</SectionHeader>
        <Chips
          options={enums.HealthTag}
          multi
          value={health}
          onChange={(value) => setHealth((items) => toggle(items, value))}
        />
        <SectionHeader>{t('behavior_observations')}</SectionHeader>
        <Chips
          options={enums.BehaviorTag}
          multi
          value={behavior}
          onChange={(value) => setBehavior((items) => toggle(items, value))}
        />
        <SectionHeader>{t('ear_tipped')}</SectionHeader>
        <Chips options={enums.EarTip} value={ear} onChange={setEar} />
        <Switch label={t('report_not_neutered')} checked={tnr} onChange={setTnr} />
        <Switch label={t('needs_medical')} checked={medical} onChange={setMedical} />
        <Field label={t('notes')} value={notes} limit={1000} onChange={setNotes} multiline />
        <LocationField
          {...position}
          onChange={(lat, lng) => {
            setPosition({ lat, lng });
            setChanged(true);
          }}
        />
        <ErrorText text={action.error} />
        <button
          className="primary"
          disabled={action.busy}
          onClick={() =>
            void action.run(async () => {
              await CatRepository.updateSighting(sighting, {
                notes: notes.trim(),
                healthTags: health,
                behaviorTags: behavior,
                earTipped: ear,
                possiblyNotNeutered: tnr,
                needsMedical: medical,
              });
              if (changed)
                await CatRepository.updateSightingLocation(
                  sighting,
                  position.lat,
                  position.lng,
                  settings.privacyLevel,
                  settings.shareExactWithVolunteers,
                );
              navigate('/sighting/' + id);
            }, t('saved'))
          }
        >
          {t('save')}
        </button>
      </div>
    </>
  );
}
