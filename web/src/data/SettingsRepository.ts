import * as idb from '../util/idb';
import { setLanguage } from '../i18n';
export interface FeedFilters {
  layers: string[];
  animalTypes: string[];
  maxDistanceKm: number;
  maxAgeDays: number;
  rewardOnly: boolean;
  includeOlder: boolean;
  microchip: string | null;
}
export const filterDefaults: FeedFilters = {
  layers: [],
  animalTypes: [],
  maxDistanceKm: 0,
  maxAgeDays: 0,
  rewardOnly: false,
  includeOlder: false,
  microchip: null,
};
export const categories = [
  'followed',
  'rescue',
  'volunteer',
  'achievements',
  'moderation',
  'messages',
  'lost_nearby',
  'post_updates',
  'adoption',
  'sitting',
];
export const settingsDefaults = {
  theme: 'system',
  largeText: false,
  language: '',
  notificationsEnabled: true,
  categories: Object.fromEntries(categories.map((c) => [c, true])),
  privacyLevel: 'standard',
  shareExactWithVolunteers: true,
  mapMode: 'modern',
  satellite: false,
  showStations: true,
  heatmap: false,
  filters: filterDefaults,
  quietHoursEnabled: false,
  quietStart: 1320,
  quietEnd: 420,
  lastSeenVersion: '',
  releaseNotesVersion: '',
  releaseNotes: '',
  skippedVersion: '',
  lastUpdateCheck: 0,
  installHintDismissed: false,
  /** Photo review on new sightings (docs/MATCHING_REVIEW_V1.md); offered to moderators only, off by default. */
  photoReview: false,
};
export type AppSettings = typeof settingsDefaults;
let value: AppSettings = structuredClone(settingsDefaults);
const subscribers = new Set<() => void>();
export const SettingsRepository = {
  get: () => value,
  subscribe: (fn: () => void) => {
    subscribers.add(fn);
    return () => {
      subscribers.delete(fn);
    };
  },
  load: async () => {
    const saved = await idb.get<Partial<AppSettings>>('settings');
    if (saved)
      value = {
        ...value,
        ...saved,
        filters: { ...filterDefaults, ...saved.filters },
        categories: { ...value.categories, ...saved.categories },
      };
    setLanguage(value.language || (typeof navigator !== 'undefined' ? navigator.language : 'en'));
    subscribers.forEach((fn) => fn());
  },
  set: async (patch: Partial<AppSettings>) => {
    value = { ...value, ...patch };
    if (patch.language !== undefined)
      setLanguage(value.language || (typeof navigator !== 'undefined' ? navigator.language : 'en'));
    subscribers.forEach((fn) => fn());
    await idb.put('settings', value);
  },
  deliveredNotifications: (uid: string) => idb.get<string[]>('delivered_' + uid),
  setDeliveredNotifications: (uid: string, ids: string[]) => idb.put('delivered_' + uid, ids),
  clearDeliveredNotifications: (uid: string) => idb.remove('delivered_' + uid),
};
