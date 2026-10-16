// CatMap's match rules, mirroring cat-server/matching.py and Android ai/MatchEngine.kt (docs/MATCHING.md).
// tools/matching/cases.json holds shared cases all three must pass. The web app uses them while the PC is off,
// with the colour fingerprint as the photo evidence, which is never enough for "strong". Place and time only move
// a label one step. Nothing here decides that two animals are the same: a person reviews every suggestion.
import { distanceKm } from '../data/Neighborhoods';
import type { Post } from '../data/model';
import { PostAppearance } from './PostAppearance';

export const SPEC_VERSION = 1;
export const MODEL_FINGERPRINT = 'fingerprint27/v1';
export const MODEL_DETAILS = 'details/v1';
export const TIERS = ['strong', 'possible', 'weak', 'none'] as const;
export type Tier = (typeof TIERS)[number];
// pet-recognition-base thresholds (strong, possible, weak) per species, measured in cat-server/eval.
export const EMBED: Record<string, [number, number, number]> = {
  cat: [0.88, 0.79, 0.68],
  dog: [0.89, 0.66, 0.27],
};
export const PRINT_POSSIBLE = 0.9;
export const PRINT_WEAK = 0.83;
const GPS_KM = 0.15,
  BLUR_KM = 0.45,
  ROAM_KM = 3,
  ROAM_KM_PER_DAY = 1,
  ROAM_MAX_KM = 25,
  NEAR_KM = 1.5,
  RECENT_DAYS = 14,
  BEFORE_DAYS = 2,
  PREFILTER_KM = 45,
  DAY_MS = 86400000;
export const LOST = ['lost'];
export const SIGHTINGS = ['found', 'spotted', 'street'];

export interface Side {
  lat: number;
  lng: number;
  timeMs: number;
  approximate?: boolean;
  areaM?: number;
  hasPhotos?: boolean;
  colourWords?: string[];
}
export interface MatchResult {
  tier: Tier;
  score: number;
  visual: number;
  method: string;
  distanceKm: number;
  daysApart: number;
  reasons: string[];
}

const embedThresholds = (animalType: string) => EMBED[animalType] ?? EMBED.cat;
export const down = (tier: Tier): Tier =>
  TIERS[Math.min(TIERS.length - 1, TIERS.indexOf(tier) + 1)];
// Python's round() rounds halves to even; the shared cases compare with a small tolerance anyway.
const round = (v: number, places: number) => Math.round(v * 10 ** places) / 10 ** places;

export function visualTier(method: string, visual: number, animalType = 'cat'): Tier {
  if (method === 'embedding') {
    const [strong, possible, weak] = embedThresholds(animalType);
    return visual >= strong
      ? 'strong'
      : visual >= possible
        ? 'possible'
        : visual >= weak
          ? 'weak'
          : 'none';
  }
  if (method === 'fingerprint')
    return visual >= PRINT_POSSIBLE ? 'possible' : visual >= PRINT_WEAK ? 'weak' : 'none';
  return 'none';
}

export function visualNorm(method: string, visual: number, animalType = 'cat') {
  let points: [number, number][];
  if (method === 'embedding') {
    const [strong, possible, weak] = embedThresholds(animalType);
    points = [
      [weak - 0.15, 0],
      [weak, 0.25],
      [possible, 0.5],
      [strong, 0.8],
      [1, 1],
    ];
  } else if (method === 'fingerprint')
    points = [
      [PRINT_WEAK - 0.15, 0],
      [PRINT_WEAK, 0.25],
      [PRINT_POSSIBLE, 0.5],
      [1, 0.7],
    ];
  else return 0;
  if (visual <= points[0][0]) return 0;
  for (let i = 1; i < points.length; i++) {
    const [x0, y0] = points[i - 1],
      [x1, y1] = points[i];
    if (visual <= x1) return y0 + ((y1 - y0) * (visual - x0)) / (x1 - x0);
  }
  return points[points.length - 1][1];
}

export function score(
  lost: Side,
  sighting: Side,
  method: string,
  visual = -1,
  animalType = 'cat',
): MatchResult {
  const d = distanceKm(lost.lat, lost.lng, sighting.lat, sighting.lng);
  const uncertainty =
    2 * GPS_KM +
    (lost.approximate ? BLUR_KM : 0) +
    (sighting.approximate ? BLUR_KM : 0) +
    (lost.areaM ?? 0) / 1000;
  const effective = Math.max(0, d - uncertainty);
  const days = (sighting.timeMs - lost.timeMs) / DAY_MS;
  const roam = Math.min(ROAM_MAX_KM, ROAM_KM + ROAM_KM_PER_DAY * Math.max(0, days));
  const far = effective > roam,
    near = effective <= NEAR_KM,
    before = days < -BEFORE_DAYS,
    recent = days >= -BEFORE_DAYS && days <= RECENT_DAYS;
  const reasons: string[] = [];
  let tier: Tier;
  let m = method;
  if (m === 'details' || visual < 0) {
    m = 'details';
    const words = new Set(sighting.colourWords ?? []);
    const shared = (lost.colourWords ?? []).some((w) => words.has(w));
    tier = near && recent && shared ? 'weak' : 'none';
    reasons.push('no_photo');
    if (shared) reasons.push('same_colour_words');
  } else {
    tier = visualTier(m, visual, animalType);
    reasons.push(
      (
        {
          strong: 'photo_very_similar',
          possible: 'photo_similar',
          weak: 'photo_somewhat_similar',
          none: 'photo_different',
        } as const
      )[tier],
    );
    if (m === 'fingerprint') {
      reasons.push('colour_only');
      if (!near) tier = down(tier);
    }
    if (far) tier = down(tier);
    if (before) tier = down(tier);
  }
  reasons.push(near ? 'near' : far ? 'far' : 'in_range');
  reasons.push(before ? 'before_lost' : recent ? 'recent' : 'long_after');
  const proximity = Math.exp(-effective / Math.max(roam, 1e-9));
  const timeFit = before ? 0.3 : Math.exp(-Math.max(0, days) / 30);
  const rank = 0.85 * visualNorm(m, visual, animalType) + 0.15 * (0.5 * proximity + 0.5 * timeFit);
  return {
    tier,
    score: round(rank, 4),
    visual: round(visual, 4),
    method: m,
    distanceKm: round(d, 3),
    daysApart: round(days, 2),
    reasons,
  };
}

export function applyFeedback(
  r: MatchResult,
  likeRejected: boolean,
  reporterRejected: boolean,
): MatchResult {
  let out = r;
  if (likeRejected)
    out = { ...out, tier: down(out.tier), reasons: [...out.reasons, 'like_rejected'] };
  if (reporterRejected)
    out = { ...out, tier: down(out.tier), reasons: [...out.reasons, 'reporter_rejected'] };
  return out;
}

// ---------- posts ----------

export const postTime = (p: Post) => (p.lastSeenAt > 0 ? p.lastSeenAt : p.createdAt);
export const colourWords = (p: Post) =>
  `${p.color} ${p.features}`
    .toLowerCase()
    .replace(/,/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length >= 3);
export const side = (p: Post): Side => ({
  lat: p.lat,
  lng: p.lng,
  timeMs: postTime(p),
  approximate: p.approximate,
  areaM: LOST.includes(p.category) ? p.areaRadiusM : 0,
  hasPhotos: p.photoIds.length > 0,
  colourWords: colourWords(p),
});
export const isLost = (p: Post) => LOST.includes(p.category);
export const isSighting = (p: Post) => SIGHTINGS.includes(p.category);
export const matchId = (lostId: string, sightingId: string) => `${lostId}__${sightingId}`;

export function candidates(post: Post, feed: Post[]) {
  const other = isLost(post) ? SIGHTINGS : isSighting(post) ? LOST : null;
  if (!other) return [];
  return feed.filter(
    (p) =>
      p.id !== post.id &&
      other.includes(p.category) &&
      p.status === 'open' &&
      !p.hidden &&
      p.animalType === post.animalType &&
      p.ownerId !== post.ownerId &&
      distanceKm(post.lat, post.lng, p.lat, p.lng) <= PREFILTER_KM,
  );
}

export interface Pairing {
  id: string;
  lost: Post;
  sighting: Post;
  result: MatchResult;
}

/** The device's own matching (PC offline): colour fingerprints when both have one, otherwise the details. */
export function deviceMatches(post: Post, feed: Post[]): Pairing[] {
  return candidates(post, feed)
    .map((c) => {
      const [lost, sighting] = isLost(post) ? [post, c] : [c, post];
      const result =
        PostAppearance.valid(lost.fingerprint) && PostAppearance.valid(sighting.fingerprint)
          ? score(
              side(lost),
              side(sighting),
              'fingerprint',
              PostAppearance.similarity(lost.fingerprint, sighting.fingerprint),
              lost.animalType,
            )
          : score(side(lost), side(sighting), 'details', -1, lost.animalType);
      return { id: matchId(lost.id, sighting.id), lost, sighting, result };
    })
    .sort((a, b) => b.result.score - a.result.score);
}
