import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useApp, useSettings, useDeviceLocation } from '../../state';
import { LocationRepository } from '../../data/LocationRepository';
import { t } from '../../i18n';
import {
  AppTopBar,
  Bell,
  SectionHeader,
  PostCard,
  EmptyState,
  Loading,
  Icon,
  ControlIcon,
  Switch,
  enumLabel,
} from '../components/Components';
import { CatMapIcons } from '../../icons/CatMapIcons';
import { FilterSheet, matches, layers, toggle } from './Filtering';
import { SettingsRepository } from '../../data/SettingsRepository';
import { distanceKm, Dubai } from '../../data/Neighborhoods';
const shortcuts = [
  { to: '/help-directory', label: 'help_directory', icon: CatMapIcons.HELP, color: '#00696B' },
  { to: '/adoption', label: 'adoption', icon: CatMapIcons.ADOPTION, color: '#8E24AA' },
  { to: '/sitters', label: 'pet_sitters', icon: CatMapIcons.SITTER, color: '#5E35B1' },
  { to: '/cats', label: 'community_cats', icon: CatMapIcons.CAT, color: '#A9531A' },
  { to: '/rescue', label: 'rescue_reports', icon: CatMapIcons.RESCUE, color: '#C62828' },
  { to: '/stations', label: 'feeding_stations', icon: CatMapIcons.STATION, color: '#00897B' },
  { to: '/area-alerts', label: 'area_alerts', icon: CatMapIcons.BELL, color: '#F9A825' },
];
export default function ExploreScreen() {
  const { posts, profile } = useApp(),
    settings = useSettings(),
    searchMode = useLocation().pathname === '/search';
  const [search, setSearch] = useState(''),
    [filter, setFilter] = useState(false),
    [sort, setSort] = useState('newest'),
    [includeClosed, setClosed] = useState(false);
  const fix = useDeviceLocation();
  const results = posts
    ?.filter((p) => matches(p, settings.filters, fix, searchMode && includeClosed))
    .filter(
      (p) =>
        !search ||
        [
          p.name,
          p.breed,
          p.color,
          p.description,
          enumLabel('AnimalType', p.animalType),
          enumLabel('PostCategory', p.category),
          Dubai.nameOf(p.neighborhood),
          ...p.keywords,
        ]
          .join(' ')
          .toLowerCase()
          .includes(search.toLowerCase()),
    )
    .sort((a, b) =>
      sort === 'nearest' && fix
        ? distanceKm(fix.lat, fix.lng, a.lat, a.lng) - distanceKm(fix.lat, fix.lng, b.lat, b.lng)
        : b.createdAt - a.createdAt,
    );
  return (
    <>
      {searchMode ? (
        <AppTopBar title={t('search')} />
      ) : (
        <>
          <div className="explore-hero">
            <div className="row between">
              <div>
                <p>{t('hello_x', profile?.displayName || profile?.username || '')}</p>
                <h1>{t('explore_title')}</h1>
              </div>
              <Bell />
            </div>
            <Link className="search" to="/search">
              <ControlIcon name="search" />
              <span>{t('search_hint')}</span>
            </Link>
          </div>
          <div className="shortcuts">
            {shortcuts.map((s) => (
              <Link
                className="shortcut"
                key={s.to}
                to={s.to}
                style={{ '--shortcut-color': s.color } as React.CSSProperties}
              >
                <Icon name={s.icon} size={42} />
                <span>{t(s.label)}</span>
              </Link>
            ))}
          </div>
        </>
      )}
      <div className="screen">
        {searchMode && (
          <label className="search">
            <ControlIcon name="search" />
            <input
              autoFocus
              aria-label={t('search')}
              placeholder={t('search_hint')}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
        )}
        <div className="row between">
          <SectionHeader>{t('nearby_reports')}</SectionHeader>
          <button className="text-button" onClick={() => setFilter(true)}>
            {t('filters')}
          </button>
        </div>
        <div className="chips scroll-chips">
          <button
            className={'chip ' + (!settings.filters.layers.length ? 'selected' : '')}
            onClick={() =>
              void SettingsRepository.set({ filters: { ...settings.filters, layers: [] } })
            }
          >
            {t('all')}
          </button>
          {layers
            .filter((l) => l.key !== 'sitting')
            .map((l) => (
              <button
                key={l.key}
                className={'chip ' + (settings.filters.layers.includes(l.key) ? 'selected' : '')}
                onClick={() =>
                  void SettingsRepository.set({
                    filters: {
                      ...settings.filters,
                      layers: toggle(settings.filters.layers, l.key),
                    },
                  })
                }
              >
                <Icon name={CatMapIcons.layer(l.key)} size={18} />
                {t(l.label)}
              </button>
            ))}
        </div>
        {searchMode ? (
          <Switch label={t('include_closed')} checked={includeClosed} onChange={setClosed} />
        ) : (
          <div className="chips">
            {['newest', 'nearest'].map((s) => (
              <button
                key={s}
                className={'chip ' + (s === sort ? 'selected' : '')}
                onClick={() => {
                  setSort(s);
                  if (s === 'nearest')
                    navigator.geolocation?.getCurrentPosition((p) =>
                      LocationRepository.set({
                        lat: p.coords.latitude,
                        lng: p.coords.longitude,
                        accuracy: p.coords.accuracy,
                      }),
                    );
                }}
              >
                {t('sort_' + s)}
              </button>
            ))}
          </div>
        )}
        {!results ? (
          <Loading />
        ) : results.length ? (
          results.slice(0, searchMode ? 100 : 400).map((p) => <PostCard post={p} key={p.id} />)
        ) : (
          <EmptyState
            text={t(
              search
                ? 'search_empty_hint'
                : settings.filters.layers.length
                  ? 'no_posts_filters'
                  : 'no_posts_yet',
            )}
          />
        )}
      </div>
      {filter && (
        <FilterSheet
          filters={settings.filters}
          onClose={() => setFilter(false)}
          hasLocation={!!fix}
        />
      )}
    </>
  );
}
