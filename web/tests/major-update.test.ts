import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { Geohash } from '../src/data/Geohash';
import { SearchTerms } from '../src/data/SearchTerms';
import { alertRoute } from '../src/util/AppRoutes';
import {
  PostDefaults,
  CatDefaults,
  PostFromDoc,
  CatFromDoc,
  AppNotificationFromDoc,
  RescueReportFromDoc,
  ChatFromDoc,
} from '../src/data/model';
import {
  areaPlan,
  withItems,
  emptyArea,
  encodeArea,
  decodeArea,
  KEEP,
} from '../src/data/MapArea';
import { parseSearch } from '../src/data/SearchQuery';
import { nameIdeas } from '../src/ai/NameIdeas';

describe('Android GeohashTest vectors (also shared with the worker)', () => {
  it.each([
    [57.64911, 10.40744, 11, 'u4pruydqqvj'],
    [25.1972, 55.2744, 6, 'thrr3g'],
    [25.2048, 55.2708, 6, 'thrr3s'],
    [-33.8688, 151.2093, 6, 'r3gx2f'],
  ] as const)('encodes %s,%s', (lat, lng, n, expected) =>
    expect(Geohash.encode(lat, lng, n)).toBe(expected),
  );
  it('stores all three prefixes, except the missing position', () => {
    expect(Geohash.cells(25.2048, 55.2708)).toEqual(['thrr', 'thrr3', 'thrr3s']);
    expect(Geohash.cells(0, 0)).toEqual([]);
  });
  it('origin and size describe the cell', () => {
    const [lat, lng] = Geohash.origin('thrr3s'),
      [h, w] = Geohash.cellSize(6);
    expect(Geohash.encode(lat + h / 2, lng + w / 2, 6)).toBe('thrr3s');
    expect(25.2048 >= lat && 25.2048 <= lat + h && 55.2708 >= lng && 55.2708 <= lng + w).toBe(true);
  });
  it('covers corners and centre once, and stops past the maximum', () => {
    const s = 25.05,
      w = 55.1,
      n = 25.25,
      e = 55.35,
      cells = Geohash.covering(s, w, n, e, 5);
    expect(cells.length).toBe(new Set(cells).size);
    for (const [lat, lng] of [
      [s, w],
      [s, e],
      [n, w],
      [n, e],
      [(s + n) / 2, (w + e) / 2],
    ])
      expect(cells).toContain(Geohash.encode(lat, lng, 5));
    expect(Geohash.covering(24.75, 54.9, 25.4, 55.65, 6, 6)).toHaveLength(7);
  });
});
describe('Android SearchTermsTest vectors', () => {
  it('also keeps Android coat-based name suggestions', () => {
    expect(nameIdeas('orange_white')).toEqual(['Zafran', 'Mishmish', 'Asal', 'Burtuqala']);
    expect(nameIdeas('gray_white')).toEqual(['Ramadi', 'Ghaim', 'Fidda', 'Dukhan']);
    expect(nameIdeas('unknown')).toEqual(['Bissa', 'Najma', 'Sahm', 'Habibi']);
  });
  it('preserves word and prefix order', () =>
    expect(SearchTerms.of(['simba', 'orange', 'tabby', 'al', 'barsha'])).toEqual([
      'simba',
      'orange',
      'tabby',
      'al',
      'barsha',
      'sim',
      'simb',
      'ora',
      'oran',
      'orang',
      'tab',
      'tabb',
      'bar',
      'bars',
      'barsh',
    ]));
  it('normalizes English and Arabic words', () => {
    expect(SearchTerms.words('Simba (the KING)', 'simba', 'a')).toEqual(['simba', 'the', 'king']);
    expect(SearchTerms.words('قطة برتقالية')).toEqual(['قطة', 'برتقالية']);
  });
  it('middle words have no prefixes; head and tail do', () => {
    const terms = SearchTerms.of(
      Array.from({ length: 40 }, (_, i) => 'w' + String(i).padStart(2, '0') + 'xxxxxx'),
    );
    expect(terms).toContain('w00xxxx');
    expect(terms).toContain('w39xxxx');
    expect(terms).toContain('w30xxxxxx');
    expect(terms).not.toContain('w30xxx');
  });
  it('caps at 200, includes aliases and queries long prefixes', () => {
    expect(
      SearchTerms.of(Array.from({ length: 80 }, (_, i) => String(i).padStart(3, '0') + 'abcdefgh')),
    ).toHaveLength(200);
    expect(SearchTerms.of(SearchTerms.words('Zafran', 'Ginger'))).toEqual([
      'zafran',
      'ginger',
      'zaf',
      'zafr',
      'zafra',
      'gin',
      'ging',
      'ginge',
    ]);
    expect(SearchTerms.query('beautiful')).toEqual(['beautiful', 'beautif']);
    expect(SearchTerms.query('tab')).toEqual(['tab']);
    expect(SearchTerms.matches('tab', ['orange', 'tabby'])).toBe(true);
  });
  it('turns animal/category words into filters', () => {
    expect(parseSearch('lost dog orange')).toMatchObject({
      word: 'orange',
      types: ['dog'],
      categories: ['lost'],
    });
  });
});
describe('untrusted Firestore documents', () => {
  it('uses typed defaults and removes malformed list entries', () => {
    const p = PostFromDoc('p', {
      color: 5,
      photoIds: [1, 'photo', null],
      lat: NaN,
      reward: '9',
      hidden: 'false',
      keywords: {},
      searchTerms: ['sim', 4],
    });
    expect(p).toMatchObject({
      id: 'p',
      color: '',
      photoIds: ['photo'],
      lat: 0,
      reward: 0,
      hidden: false,
      keywords: [],
      searchTerms: ['sim'],
    });
    expect(
      CatFromDoc('c', {
        name: [],
        aliases: ['ok', 1],
        features: [1, Infinity, '2'],
        photoIds: [1],
      }),
    ).toMatchObject({ id: 'c', name: '', aliases: ['ok'], features: [1], photoIds: [] });
    expect(
      AppNotificationFromDoc('n', {
        type: 7,
        route: {},
        params: { user: 3, text: 'safe' },
        createdAt: null,
      }),
    ).toMatchObject({ type: '', route: '', params: { text: 'safe' }, createdAt: 0 });
    expect(ChatFromDoc('c', { names: [], unread: { a: 3, b: '4', c: NaN } }).unread).toEqual({
      a: 3,
    });
  });
  it('also validates nested rescue history', () => {
    const r = RescueReportFromDoc('r', {
      updates: [1, null, { note: 4, byName: 'safe', at: 'x' }],
    });
    expect(r.updates).toHaveLength(1);
    expect(r.updates[0]).toMatchObject({ note: '', byName: 'safe', at: 0 });
  });
  it.each([
    'post/..',
    'settings',
    'https://evil.invalid',
    '//evil.invalid',
    'post/a/b',
    'post/%2e%2e',
    'post/_id',
    'post/.id',
    'bookings?tab=evil',
    'post/a\n',
  ])('ignores route %s', (route) => expect(alertRoute(route)).toBeNull());
  it.each([
    'post/A_1.-',
    'cat/a',
    'sighting/a',
    'rescue/a',
    'station/a',
    'sitter/a',
    'user/a',
    'match/a',
    'chat/a',
    'adoption-requests',
    'achievements',
    'profile',
    'bookings',
    'bookings?tab=sitter',
    'bookings?tab=owner',
    'notifications',
  ])('allows route %s', (route) => expect(alertRoute(route)).toBe('/' + route));
});
describe('map saved-copy rules', () => {
  const now = 2000000000,
    p = {
      ...PostDefaults,
      id: 'p',
      lat: 25.2048,
      lng: 55.2708,
      updatedAt: 100,
      geoCells: ['thrr', 'thrr3', 'thrr3s'],
    };
  it('covers a viewport in six or fewer cells with the Android budget', () => {
    const plan = areaPlan({ south: 25.05, west: 55.1, north: 25.25, east: 55.35 });
    expect(plan.cells.length).toBeLessThanOrEqual(6);
    expect(plan.perCell).toBeGreaterThanOrEqual(40);
    expect(plan.perCell).toBeLessThanOrEqual(150);
  });
  it('ignores older updates', () => {
    const a = withItems(emptyArea(), [p], [], now);
    expect(withItems(a, [{ ...p, name: 'old', updatedAt: 99 }], [], now).posts.p.name).toBe('');
  });
  it('drops hidden/merged cats and saves pin fields only, for at most a week', () => {
    const c = { ...CatDefaults, id: 'c', updatedAt: 100 };
    let a = withItems(
      emptyArea(),
      [{ ...p, contactEmail: 'private', description: 'not a pin' }],
      [c],
      now,
    );
    const saved = encodeArea(a);
    expect(JSON.stringify(saved)).not.toContain('private');
    expect(JSON.stringify(saved)).not.toContain('not a pin');
    expect(decodeArea(saved, now).posts.p.id).toBe('p');
    expect(decodeArea(saved, now + KEEP).posts).toEqual({});
    a = withItems(a, [], [{ ...c, hidden: true }], now);
    expect(a.cats).toEqual({});
  });
});
it('hosting CSP is explicit and the service worker has no account response cache', () => {
  const config = JSON.parse(readFileSync('../firebase.json', 'utf8'));
  const headers = config.hosting.headers.flatMap(
    (v: { headers: { key: string; value: string }[] }) => v.headers,
  ) as { key: string; value: string }[];
  const csp = headers.find((v) => v.key === 'Content-Security-Policy')!.value;
  expect(csp).not.toContain('*');
  expect(csp).not.toContain("'unsafe-eval'");
  for (const value of [
    "frame-ancestors 'none'",
    "object-src 'none'",
    'https://www.google.com',
    'https://www.gstatic.com',
    'https://recaptchaenterprise.googleapis.com',
    'https://content-firebaseappcheck.googleapis.com',
  ])
    expect(csp).toContain(value);
  expect(headers).toContainEqual({ key: 'Strict-Transport-Security', value: 'max-age=31536000' });
  expect(headers).toContainEqual({ key: 'Cross-Origin-Opener-Policy', value: 'same-origin' });
  const sw = readFileSync('vite.config.ts', 'utf8');
  expect(sw).toContain('urlPattern: optionalRouteAsset');
  expect(sw).not.toMatch(/NetworkFirst|StaleWhileRevalidate/);
});
