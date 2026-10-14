import type { UserProfile } from '../data/model';
const specs: [string, string, number][] = [
  ['first_whiskers', 'sightings', 1],
  ['souq_resident', 'souqSightings', 5],
  ['abra_rider', 'neighborhoods', 3],
  ['marina_watcher', 'marinaSightings', 3],
  ['majlis_host', 'catsRegistered', 5],
  ['ehsan_hero', 'checkins', 10],
  ['iftar_feeder', 'iftarCheckins', 1],
  ['night_patrol', 'nightSightings', 5],
  ['ghaf_guardian', 'rescueReports', 3],
  ['falaj_keeper', 'maintenanceReports', 3],
  ['tnr_champion', 'tnrReports', 5],
  ['pearl_diver', 'approvedSightings', 10],
  ['palm_photographer', 'photos', 20],
  ['merge_detective', 'mergeSuggestions', 3],
  ['heart_of_dubai', 'followedCats', 5],
  ['falcon_eye', 'sightings', 25],
  ['burj_explorer', 'neighborhoods', 10],
  ['caravan_leader', 'sightings', 100],
];
export const badges = specs.map(([key, stat, threshold]) => ({
  key,
  stat,
  threshold,
  progress: (p: UserProfile) =>
    stat === 'neighborhoods'
      ? p.neighborhoods.length
      : stat === 'followedCats'
        ? p.followedCats.length
        : (p.stats[stat] ?? 0),
}));
export const newlyEarned = (p: UserProfile) =>
  badges.filter((b) => !p.badges.includes(b.key) && b.progress(p) >= b.threshold);
export const Points = {
  SIGHTING: 10,
  NEW_CAT: 15,
  PHOTO: 2,
  RESCUE: 20,
  CHECKIN: 5,
  MAINTENANCE: 5,
  MERGE: 5,
  APPROVED: 5,
};
export const Levels = {
  all: [0, 100, 300, 700, 1500, 3000],
  of: (points: number) => Math.max(1, Levels.all.filter((p) => points >= p).length),
  progress: (points: number) => {
    const index = Levels.of(points) - 1;
    return index === 5
      ? 1
      : (points - Levels.all[index]) / (Levels.all[index + 1] - Levels.all[index]);
  },
};
