import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';
import * as Engine from '../src/ai/MatchEngine';
import { PostDefaults, type Post } from '../src/data/model';

// The web copy of the match rules must give the PC's answers (cat-server/matching.py wrote these cases).
interface RawSide {
  lat: number;
  lng: number;
  time_ms: number;
  approximate: boolean;
  area_m: number;
  has_photos: boolean;
  colour_words: string[];
}
const spec = JSON.parse(
  readFileSync(new URL('../../tools/matching/cases.json', import.meta.url), 'utf8'),
);
const side = (s: RawSide): Engine.Side => ({
  lat: s.lat,
  lng: s.lng,
  timeMs: s.time_ms,
  approximate: s.approximate,
  areaM: s.area_m,
  hasPhotos: s.has_photos,
  colourWords: s.colour_words,
});

it('uses the same thresholds as the PC', () => {
  expect(spec.spec).toBe(Engine.SPEC_VERSION);
  expect(spec.embed).toEqual(Engine.EMBED);
  expect(spec.print).toEqual([Engine.PRINT_POSSIBLE, Engine.PRINT_WEAK]);
});

it('gives the PC answer for every shared case', () => {
  expect(spec.cases.length).toBeGreaterThan(100);
  spec.cases.forEach((c: Record<string, unknown>, i: number) => {
    let r = Engine.score(
      side(c.lost as RawSide),
      side(c.sighting as RawSide),
      c.method as string,
      c.visual as number,
      c.animalType as string,
    );
    if ('likeRejected' in c)
      r = Engine.applyFeedback(r, c.likeRejected as boolean, c.reporterRejected as boolean);
    const e = c.expected as Record<string, unknown>;
    expect(r.tier, `case ${i}`).toBe(e.tier);
    expect(r.method, `case ${i}`).toBe(e.method);
    expect(r.reasons, `case ${i}`).toEqual(e.reasons);
    expect(Math.abs(r.score - (e.score as number)), `case ${i}`).toBeLessThan(0.0002);
    expect(Math.abs(r.distanceKm - (e.distance_km as number)), `case ${i}`).toBeLessThan(0.002);
  });
});

it('on the device: never strong, both directions, skips own, closed, hidden and other animals', () => {
  const print = Array.from({ length: 27 }, (_, i) => (i < 18 ? (i === 3 ? 1 : 0) : 0.5));
  const lost: Post = {
    ...PostDefaults,
    id: 'L',
    ownerId: 'a',
    category: 'lost',
    animalType: 'cat',
    lat: 25.2,
    lng: 55.27,
    createdAt: 1000,
    fingerprint: print,
  };
  const seen: Post = { ...lost, id: 'S', ownerId: 'b', category: 'spotted' };
  const feed = [
    seen,
    { ...seen, id: 'mine', ownerId: 'a' },
    { ...seen, id: 'closed', status: 'resolved' },
    { ...seen, id: 'hidden', hidden: true },
    { ...seen, id: 'dog', animalType: 'dog' },
  ];
  const pairs = Engine.deviceMatches(lost, feed);
  expect(pairs.map((p) => p.id)).toEqual(['L__S']);
  expect(pairs[0].result.tier).toBe('possible');
  expect(Engine.deviceMatches(seen, [lost]).map((p) => p.lost.id)).toEqual(['L']);
});
