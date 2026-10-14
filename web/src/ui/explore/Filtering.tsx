import { enums } from '../../data/model/Enums';
import type { Post } from '../../data/model';
import {
  SettingsRepository,
  filterDefaults,
  type FeedFilters,
} from '../../data/SettingsRepository';
import { PostStaleness } from '../../data/PostStaleness';
import { distanceKm } from '../../data/Neighborhoods';
import { t } from '../../i18n';
import { Sheet, SectionHeader, Chips, Switch } from '../components/Components';
export const layers = [
  ...enums.PostCategory.filter((c) => c.key !== 'other').map((c) => ({
    ...c,
    key: c.key === 'street' ? 'street_cats' : c.key,
  })),
  { key: 'sitting', label: 'layer_sitting' },
];
export function matches(
  p: Post,
  f: FeedFilters,
  fix: { lat: number; lng: number } | null = null,
  includeClosed = false,
  now = Date.now(),
) {
  if (!f.includeOlder && PostStaleness.hiddenByDefault(p, now)) return false;
  if (f.microchip && f.microchip !== p.microchip) return false;
  if (!includeClosed && p.status !== 'open') return false;
  if (
    f.layers.length &&
    !f.layers.map((l) => (l === 'street_cats' ? 'street' : l)).includes(p.category)
  )
    return false;
  if (f.animalTypes.length && !f.animalTypes.includes(p.animalType)) return false;
  if (f.rewardOnly && p.reward <= 0) return false;
  if (f.maxAgeDays > 0 && now - (p.lastSeenAt || p.createdAt) > f.maxAgeDays * 86400000)
    return false;
  if (fix && f.maxDistanceKm > 0 && distanceKm(fix.lat, fix.lng, p.lat, p.lng) > f.maxDistanceKm)
    return false;
  return true;
}
export const toggle = (values: string[], value: string) =>
  values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
export function FilterSheet({
  filters,
  onClose,
  hasLocation = false,
}: {
  filters: FeedFilters;
  onClose: () => void;
  hasLocation?: boolean;
}) {
  const set = (value: Partial<FeedFilters>) =>
    void SettingsRepository.set({ filters: { ...filters, ...value } });
  return (
    <Sheet title={t('filters')} onClose={onClose}>
      <button
        className="text-button"
        onClick={() => void SettingsRepository.set({ filters: structuredClone(filterDefaults) })}
      >
        {t('clear_filters')}
      </button>
      <SectionHeader>{t('show')}</SectionHeader>
      <Chips
        options={layers}
        multi
        value={filters.layers}
        onChange={(key) => set({ layers: toggle(filters.layers, key) })}
      />
      <SectionHeader>{t('animal_type')}</SectionHeader>
      <Chips
        options={enums.AnimalType}
        multi
        value={filters.animalTypes}
        onChange={(key) => set({ animalTypes: toggle(filters.animalTypes, key) })}
      />
      <SectionHeader>{t('distance')}</SectionHeader>
      {!hasLocation && <p className="small muted">{t('distance_needs_location')}</p>}
      <div className="chips">
        {[0, 1, 3, 5, 10, 25].map((km) => (
          <button
            className={'chip ' + (filters.maxDistanceKm === km ? 'selected' : '')}
            key={km}
            onClick={() => set({ maxDistanceKm: km })}
          >
            {km ? t('within_km', km) : t('any_distance')}
          </button>
        ))}
      </div>
      <SectionHeader>{t('date')}</SectionHeader>
      <div className="chips">
        {[0, 1, 3, 7, 30].map((days) => (
          <button
            className={'chip ' + (filters.maxAgeDays === days ? 'selected' : '')}
            key={days}
            onClick={() => set({ maxAgeDays: days })}
          >
            {days === 0 ? t('any_time') : days === 1 ? t('last_24h') : t('last_n_days', days)}
          </button>
        ))}
      </div>
      <Switch
        label={t('reward_only')}
        checked={filters.rewardOnly}
        onChange={(value) => set({ rewardOnly: value })}
      />
      <Switch
        label={t('include_older_posts')}
        checked={filters.includeOlder}
        onChange={(value) => set({ includeOlder: value })}
      />
      <SectionHeader>{t('microchip')}</SectionHeader>
      <Chips
        options={[
          { key: 'all', label: 'all' },
          { key: 'yes', label: 'yes' },
          { key: 'no', label: 'no' },
          { key: 'unknown', label: 'unknown' },
        ]}
        value={filters.microchip ?? 'all'}
        onChange={(key) => set({ microchip: key === 'all' ? null : key })}
      />
    </Sheet>
  );
}
