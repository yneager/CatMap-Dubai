import { lazy, Suspense, useState } from 'react';
import { Dubai } from '../../data/Neighborhoods';
import { LocationRepository } from '../../data/LocationRepository';
import { t } from '../../i18n';
import { useAction, toast } from '../../state';
import { Sheet, ControlIcon, Loading } from './Components';
const Map = lazy(() => import('../map/CommunityMap'));
export function LocationField({
  lat,
  lng,
  onChange,
  radius = 0,
  onRadius,
}: {
  lat: number;
  lng: number;
  onChange: (lat: number, lng: number) => void;
  radius?: number;
  onRadius?: (n: number) => void;
}) {
  const [open, setOpen] = useState(false),
    [position, setPosition] = useState({ lat: lat || 25.2048, lng: lng || 55.2708 });
  const action = useAction();
  return (
    <div className="location-field">
      <p className="muted">
        {lat ? Dubai.nameOf(Dubai.nearest(lat, lng).key) : t('no_location_yet')}
      </p>
      <button
        type="button"
        className="outlined"
        onClick={() => {
          setPosition({ lat: lat || 25.2048, lng: lng || 55.2708 });
          setOpen(true);
        }}
      >
        {t('pick_location')}
      </button>
      <button
        type="button"
        className="text-button"
        disabled={action.busy}
        onClick={() =>
          navigator.geolocation?.getCurrentPosition(
            (p) => {
              LocationRepository.set({
                lat: p.coords.latitude,
                lng: p.coords.longitude,
                accuracy: p.coords.accuracy,
              });
              onChange(p.coords.latitude, p.coords.longitude);
            },
            () => toast(t('web_location_denied')),
          )
        }
      >
        <ControlIcon name="location" />
        {t('use_my_location')}
      </button>
      {open && (
        <Sheet title={t('pick_location')} onClose={() => setOpen(false)}>
          <p>{t('pick_location_hint')}</p>
          <label className="field">
            <span>{t('neighborhood')}</span>
            <select
              value={Dubai.nearest(position.lat, position.lng).key}
              onChange={(e) => {
                const p = Dubai.byKey(e.target.value);
                setPosition({ lat: p.lat, lng: p.lng });
              }}
            >
              {Dubai.neighborhoods.map((p) => (
                <option value={p.key} key={p.key}>
                  {Dubai.nameOf(p.key)}
                </option>
              ))}
            </select>
          </label>
          <div className="inline-picker">
            <Suspense fallback={<Loading rows={1} />}>
              <Map
                key={Math.round(position.lat * 100) + '-' + Math.round(position.lng * 100)}
                center={[position.lng, position.lat]}
                onMove={(lat, lng) => setPosition({ lat, lng })}
                radius={radius}
                compact
              />
            </Suspense>
            <span className="picker-pin">●</span>
          </div>
          {onRadius && (
            <label>
              {t('search_area_m', radius)}
              <input
                type="range"
                min="0"
                max="3000"
                step="50"
                value={radius}
                onChange={(e) => onRadius(Number(e.target.value))}
              />
            </label>
          )}
          <button
            type="button"
            className="primary"
            disabled={!Dubai.contains(position.lat, position.lng)}
            onClick={() => {
              onChange(position.lat, position.lng);
              setOpen(false);
            }}
          >
            {t('use_this_location')}
          </button>
        </Sheet>
      )}
    </div>
  );
}
