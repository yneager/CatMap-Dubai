import { roundEven } from '../util/math';
import places from './neighborhoods.json';
import { locale } from '../i18n';
export interface Neighborhood {
  key: string;
  nameEn: string;
  nameAr: string;
  lat: number;
  lng: number;
}
export function distanceKm(a: number, b: number, c: number, d: number) {
  const rad = Math.PI / 180;
  const h =
    Math.sin(((c - a) * rad) / 2) ** 2 +
    Math.cos(a * rad) * Math.cos(c * rad) * Math.sin(((d - b) * rad) / 2) ** 2;
  return 12742 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
}
const other: Neighborhood = {
  key: 'other',
  nameEn: 'Elsewhere in Dubai',
  nameAr: 'مناطق أخرى في دبي',
  lat: 25.2048,
  lng: 55.2708,
};
export const Dubai = {
  CENTER_LAT: 25.2048,
  CENTER_LNG: 55.2708,
  NORTH: 25.4,
  SOUTH: 24.75,
  EAST: 55.65,
  WEST: 54.9,
  neighborhoods: places.filter((p) => p.key !== 'other'),
  souqAreas: ['deira', 'al_ras', 'naif', 'bur_dubai', 'al_fahidi'],
  marinaAreas: ['dubai_marina', 'jbr', 'jlt', 'palm_jumeirah'],
  contains: (lat: number, lng: number) =>
    lat >= 24.75 && lat <= 25.4 && lng >= 54.9 && lng <= 55.65,
  byKey: (key?: string) => places.find((p) => p.key === key) ?? other,
  nameOf: (key?: string) =>
    !key ? '' : locale() === 'ar' ? Dubai.byKey(key).nameAr : Dubai.byKey(key).nameEn,
  nearest: (lat: number, lng: number) => {
    const best = Dubai.neighborhoods.reduce((a, b) =>
      distanceKm(lat, lng, a.lat, a.lng) < distanceKm(lat, lng, b.lat, b.lng) ? a : b,
    );
    return distanceKm(lat, lng, best.lat, best.lng) <= 6 ? best : other;
  },
  search: (text: string) => {
    const q = text.trim().toLowerCase();
    return Dubai.neighborhoods.filter(
      (p) => p.nameEn.toLowerCase().includes(q) || p.nameAr.includes(q) || p.key.includes(q),
    );
  },
};
export const LocationPrivacy = {
  cellSize: (level: string) => (level === 'high' ? 0.0045 : 0.002),
  fuzz: (lat: number, lng: number, level: string, seed: string) => {
    const cell = LocationPrivacy.cellSize(level);
    let h = 0;
    for (let i = 0; i < seed.length; i++) h = (31 * h + seed.charCodeAt(i)) | 0;
    return {
      lat:
        roundEven(
          (Math.floor(lat / cell) * cell + cell / 2 + ((h & 255) / 255 - 0.5) * cell * 0.6) * 1e5,
        ) / 1e5,
      lng:
        roundEven(
          (Math.floor(lng / cell) * cell + cell / 2 + (((h >> 8) & 255) / 255 - 0.5) * cell * 0.6) *
            1e5,
        ) / 1e5,
    };
  },
  approximate: (v: number) => roundEven(v * 10000) / 10000,
};
