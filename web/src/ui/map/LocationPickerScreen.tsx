import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import CommunityMap from './CommunityMap';
import { AppTopBar, SectionHeader } from '../components/Components';
import { Dubai } from '../../data/Neighborhoods';
import { t } from '../../i18n';
export default function LocationPickerScreen() {
  const location = useLocation(),
    navigate = useNavigate(),
    params = new URLSearchParams(location.search);
  const [position, setPosition] = useState({
      lat: Number(params.get('lat') ?? 25.2048),
      lng: Number(params.get('lng') ?? 55.2708),
    }),
    [radius, setRadius] = useState(Number(params.get('radius') ?? 0));
  return (
    <>
      <AppTopBar title={t('pick_location')} />
      <div className="picker-map">
        <CommunityMap
          center={[position.lng, position.lat]}
          onMove={(lat, lng) => setPosition({ lat, lng })}
          radius={radius}
        />
        <span className="picker-pin">●</span>
      </div>
      <div className="screen">
        <p>{t('pick_location_hint')}</p>
        <SectionHeader>{Dubai.nameOf(Dubai.nearest(position.lat, position.lng).key)}</SectionHeader>
        {params.get('area') === 'true' && (
          <label>
            {t('search_area_m', radius)}
            <input
              type="range"
              min="0"
              max="3000"
              step="50"
              value={radius}
              onChange={(e) => setRadius(Number(e.target.value))}
            />
          </label>
        )}
        <button
          className="primary"
          disabled={!Dubai.contains(position.lat, position.lng)}
          onClick={() =>
            navigate(location.state?.returnTo ?? '/map', {
              replace: true,
              state: { picked: { ...position, radius } },
            })
          }
        >
          {t('use_this_location')}
        </button>
      </div>
    </>
  );
}
