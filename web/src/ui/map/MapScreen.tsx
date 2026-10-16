import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp, useSettings, useList, useDeviceLocation, toast } from '../../state';
import { LocationRepository } from '../../data/LocationRepository';
import { t, relativeTime } from '../../i18n';
import { CatFromDoc, FeedingStationFromDoc, SitterProfileFromDoc } from '../../data/model';
import { CatRepository } from '../../data/CatRepository';
import { StationRepository } from '../../data/StationRepository';
import { SittingRepository } from '../../data/SittingRepository';
import { SettingsRepository } from '../../data/SettingsRepository';
import { FilterSheet, matches, layers, toggle } from '../explore/Filtering';
import {
  Bell,
  ControlIcon,
  Icon,
  Photo,
  FavoriteButton,
  PostStatusBadge,
  Sheet,
  EmptyState,
} from '../components/Components';
import { postHeadline, postTitle } from '../components/postText';
import { enums } from '../../data/model/Enums';
import { CatMapIcons } from '../../icons/CatMapIcons';
import CommunityMap, { type MapPoint } from './CommunityMap';
import { Dubai, distanceKm } from '../../data/Neighborhoods';
import { config } from '../../config';
export default function MapScreen() {
  const { posts } = useApp(),
    settings = useSettings(),
    navigate = useNavigate();
  const cats = useList(CatRepository.allCats, CatFromDoc, 'map-cats'),
    stations = useList(StationRepository.stations, FeedingStationFromDoc, 'map-stations'),
    sitters = useList(SittingRepository.activeSitters, SitterProfileFromDoc, 'map-sitters');
  const [filter, setFilter] = useState(false),
    [style, setStyle] = useState(false),
    [listMode, setListMode] = useState(false),
    [selected, setSelected] = useState<MapPoint | null>(null),
    [search, setSearch] = useState(''),
    [center, setCenter] = useState({ lat: 25.2048, lng: 55.2708 }),
    [focus, setFocus] = useState<{ lat: number; lng: number; zoom: number } | null>(null);
  const fix = useDeviceLocation();
  const points = useMemo(() => {
    const result: MapPoint[] = (posts ?? [])
      .filter((p) => matches(p, settings.filters, fix))
      .map((p) => ({
        id: p.id,
        kind: 'post',
        lat: p.lat,
        lng: p.lng,
        category: p.category,
        animalType: p.animalType,
        pulse: p.status === 'open' && ['lost', 'needs_help'].includes(p.category),
        reunited: p.status === 'reunited',
      }));
    if (!settings.filters.layers.length || settings.filters.layers.includes('street_cats'))
      result.push(
        ...(cats ?? [])
          .filter((c) => !c.hidden && !c.mergedInto)
          .map((c) => ({
            id: c.id,
            kind: 'cat' as const,
            lat: c.lat,
            lng: c.lng,
            coat: c.coatColor,
            pulse: ['injured', 'needs_attention'].includes(c.healthStatus),
          })),
      );
    if (!settings.filters.layers.length && settings.showStations)
      result.push(
        ...(stations ?? [])
          .filter((s) => s.active)
          .map((s) => ({ id: s.id, kind: 'station' as const, lat: s.lat, lng: s.lng })),
      );
    if (settings.filters.layers.includes('sitting'))
      result.push(
        ...(sitters ?? []).map((s) => ({
          id: s.uid,
          kind: 'sitter' as const,
          lat: s.lat,
          lng: s.lng,
        })),
      );
    return result;
  }, [posts, settings.filters, settings.showStations, cats, stations, sitters, fix]);
  const shownPost = selected?.kind === 'post' ? posts?.find((p) => p.id === selected.id) : null;
  const target = (p: MapPoint) => `/${p.kind === 'sitter' ? 'sitter' : p.kind}/${p.id}`;
  return (
    <div className="map-screen">
      <CommunityMap
        points={points}
        selected={selected?.id}
        onSelect={setSelected}
        onMove={(lat, lng) => setCenter({ lat, lng })}
        location={fix}
        focus={focus}
      />
      <div className="map-overlay-top">
        <div className="row">
          <label className="search">
            <ControlIcon name="search" />
            <input
              aria-label={t('search_map')}
              placeholder={t('search_map')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <Bell />
        </div>
        {search.trim() && (
          <div className="map-search-results">
            {Dubai.search(search)
              .slice(0, 4)
              .map((hood) => (
                <button
                  className="link-row"
                  key={hood.key}
                  onClick={() => {
                    setFocus({ lat: hood.lat, lng: hood.lng, zoom: 14.5 });
                    setSearch('');
                  }}
                >
                  <Icon name={CatMapIcons.PIN} />
                  {Dubai.nameOf(hood.key)}
                </button>
              ))}
            {(posts ?? [])
              .filter((p) =>
                [p.name, p.animalType, p.category, ...p.keywords]
                  .join(' ')
                  .toLowerCase()
                  .includes(search.toLowerCase()),
              )
              .slice(0, 5)
              .map((p) => (
                <button
                  className="link-row"
                  key={p.id}
                  onClick={() => {
                    setFocus({ lat: p.lat, lng: p.lng, zoom: 16 });
                    setSelected({
                      id: p.id,
                      kind: 'post',
                      lat: p.lat,
                      lng: p.lng,
                      category: p.category,
                      animalType: p.animalType,
                    });
                    setSearch('');
                  }}
                >
                  <span>{p.name || t('animal_' + p.animalType)}</span>
                </button>
              ))}
          </div>
        )}
        <div className="chips scroll-chips">
          <button
            className={'chip ' + (!settings.filters.layers.length ? 'selected' : '')}
            onClick={() =>
              void SettingsRepository.set({
                filters: { ...settings.filters, layers: [], animalTypes: [] },
              })
            }
          >
            {t('all_animals')}
          </button>
          <button className="chip" onClick={() => setFilter(true)}>
            <ControlIcon name="settings" />
            {t('filters')}
          </button>
          {layers.map((l) => (
            <button
              key={l.key}
              className={'chip ' + (settings.filters.layers.includes(l.key) ? 'selected' : '')}
              onClick={() =>
                void SettingsRepository.set({
                  filters: { ...settings.filters, layers: toggle(settings.filters.layers, l.key) },
                })
              }
            >
              <Icon name={CatMapIcons.layer(l.key)} size={18} />
              {t(l.label)}
            </button>
          ))}
        </div>
        <button className="map-list-toggle chip" onClick={() => setListMode((v) => !v)}>
          {t(listMode ? 'show_map_view' : 'show_list_view')}
        </button>
      </div>
      <div className="map-controls">
        <button
          className="icon-button elevated"
          aria-label={t('my_location')}
          onClick={() =>
            navigator.geolocation?.getCurrentPosition(
              (p) => {
                const position = {
                  lat: p.coords.latitude,
                  lng: p.coords.longitude,
                  accuracy: p.coords.accuracy,
                };
                LocationRepository.set(position);
                setFocus({ ...position, zoom: 15.5 });
              },
              () => toast(t('web_location_denied')),
            )
          }
        >
          <ControlIcon name="location" />
        </button>
        <button
          className="icon-button elevated"
          aria-label={t('map_style')}
          onClick={() => setStyle(true)}
        >
          <ControlIcon name="settings" />
        </button>
      </div>
      {shownPost && (
        <div className="map-preview">
          <button
            className="icon-button close-preview"
            aria-label={t('close')}
            onClick={() => setSelected(null)}
          >
            <ControlIcon name="close" />
          </button>
          <div className="compact-preview card">
            <Link className="preview-row" to={'/post/' + shownPost.id}>
              <div className="preview-photo">
                <Photo id={shownPost.photoIds[0]} alt={shownPost.name} />
              </div>
              <div className="preview-copy">
                <div className="row wrap">
                  <span
                    className="category-pill"
                    style={{
                      background: enums.PostCategory.find((c) => c.key === shownPost.category)
                        ?.color,
                    }}
                  >
                    {t('cat_' + shownPost.category)}
                  </span>
                  {shownPost.reward > 0 && (
                    <span className="reward-pill">{t('reward_aed', shownPost.reward)}</span>
                  )}
                </div>
                <h3>{postTitle(shownPost)}</h3>
                {shownPost.name && <p>{postHeadline(shownPost)}</p>}
                <small>
                  {Dubai.nameOf(shownPost.neighborhood)} ·{' '}
                  {relativeTime(shownPost.lastSeenAt || shownPost.createdAt)}
                </small>
                <PostStatusBadge post={shownPost} />
              </div>
            </Link>
            <FavoriteButton post={shownPost} />
            <Link className="tonal" to={'/post/' + shownPost.id}>
              {t('view_details')}
            </Link>
          </div>
        </div>
      )}
      {selected && !shownPost && (
        <div className="map-preview">
          <Link className="card" to={target(selected)}>
            {selected.kind === 'cat'
              ? cats?.find((c) => c.id === selected.id)?.name
              : selected.kind === 'station'
                ? stations?.find((s) => s.id === selected.id)?.name
                : sitters?.find((s) => s.uid === selected.id)?.name}
            <span> ›</span>
          </Link>
        </div>
      )}
      {filter && (
        <FilterSheet
          filters={settings.filters}
          onClose={() => setFilter(false)}
          hasLocation={!!fix}
        />
      )}{' '}
      {listMode && (
        <Sheet title={t('show_list_view')} onClose={() => setListMode(false)}>
          {!fix && <p className="small muted">{t('distance_from_map_center')}</p>}
          {points.length ? (
            points
              .slice()
              .sort(
                (a, b) =>
                  distanceKm((fix ?? center).lat, (fix ?? center).lng, a.lat, a.lng) -
                  distanceKm((fix ?? center).lat, (fix ?? center).lng, b.lat, b.lng),
              )
              .map((p) => (
                <button
                  className="link-row"
                  key={p.kind + p.id}
                  onClick={() => navigate(target(p))}
                >
                  <Icon
                    name={
                      p.kind === 'post'
                        ? CatMapIcons.animal(p.animalType ?? 'cat')
                        : p.kind === 'cat'
                          ? CatMapIcons.CAT
                          : p.kind === 'station'
                            ? CatMapIcons.STATION
                            : CatMapIcons.SITTER
                    }
                  />
                  <span>
                    {p.kind === 'post'
                      ? posts?.find((x) => x.id === p.id)?.name
                      : p.kind === 'cat'
                        ? cats?.find((c) => c.id === p.id)?.name
                        : p.kind === 'station'
                          ? stations?.find((s) => s.id === p.id)?.name
                          : sitters?.find((s) => s.uid === p.id)?.name}
                  </span>
                </button>
              ))
          ) : (
            <EmptyState text={t('no_posts_filters')} />
          )}
        </Sheet>
      )}
      {style && (
        <Sheet title={t('map_style')} onClose={() => setStyle(false)}>
          {['modern', 'classic'].map((mode) => (
            <button
              key={mode}
              className={'link-row ' + (settings.mapMode === mode ? 'selected' : '')}
              onClick={() => void SettingsRepository.set({ mapMode: mode })}
            >
              {t('map_' + mode + '_desc')}
            </button>
          ))}
          {config.maptiler && (
            <button
              className="link-row"
              onClick={() => void SettingsRepository.set({ satellite: !settings.satellite })}
            >
              {t('satellite')}
            </button>
          )}
          <button
            className="link-row"
            onClick={() => void SettingsRepository.set({ showStations: !settings.showStations })}
          >
            {t('feeding_stations')} {settings.showStations ? '✓' : ''}
          </button>
          <button
            className="link-row"
            onClick={() => void SettingsRepository.set({ heatmap: !settings.heatmap })}
          >
            {t('heatmap')} {settings.heatmap ? '✓' : ''}
          </button>
        </Sheet>
      )}
    </div>
  );
}
