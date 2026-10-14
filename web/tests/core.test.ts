import { describe, it, expect, afterEach } from 'vitest';
import {
  PostDefaults,
  PostFromDoc,
  PostToDoc,
  UserProfileDefaults,
  UserProfileFromDoc,
  BookingDefaults,
  BookingToDoc,
  ReviewDefaults,
  ReviewToDoc,
} from '../src/data/model';
import schema from '../src/data/model/kotlin-schema.json';
import * as models from '../src/data/model';
import { keywords, likelyDuplicates, toPost } from '../src/data/PostRepository';
import { Dubai, LocationPrivacy, distanceKm } from '../src/data/Neighborhoods';
import { PostStaleness } from '../src/data/PostStaleness';
import { QuietHours } from '../src/notifications/QuietHours';
import { notificationId } from '../src/data/NotificationRepository';
import { NotificationDelivery } from '../src/notifications/NotificationDelivery';
import { AppNotificationDefaults } from '../src/data/model';
import { PostLinks } from '../src/util/links';
import { format, t, plural, setLanguage } from '../src/i18n';
import { compressUnder } from '../src/util/images';
import { histBin, classify, describe as analyze } from '../src/ai/CatAnalyzer';
import { PostAppearance, describePixels, roundEven } from '../src/ai/PostAppearance';
import { Similarity } from '../src/ai/Similarity';
import { RecognitionEvaluation } from '../src/ai/RecognitionEvaluation';
import { newlyEarned, Levels } from '../src/gamification/Badges';
import { cleanValue } from '../src/data/UserDataExport';
import { Bytes, Timestamp } from 'firebase/firestore';
afterEach(() => setLanguage('en'));
describe('Kotlin-derived model compatibility', () => {
  it('fills missing defaults, preserves document ids and old post defaults', () => {
    expect(PostFromDoc('old', { name: 'Cat', unknownField: 'ignored' })).toEqual({
      ...PostDefaults,
      id: 'old',
      name: 'Cat',
    });
    expect(UserProfileFromDoc('uid', {})).toEqual({ ...UserProfileDefaults, uid: 'uid' });
  });
  it('rounds every Kotlin Long, Int and nested Long map in every model', () => {
    const registry = models as unknown as Record<string, unknown>;
    for (const [name, fields] of Object.entries(schema)) {
      const defaults = registry[name + 'Defaults'] as Record<string, unknown>,
        writer = registry[name + 'ToDoc'] as (
          v: Record<string, unknown>,
        ) => Record<string, unknown>;
      const value = { ...defaults };
      for (const [key, field] of Object.entries(fields)) {
        if (['Long', 'Int'].includes(field.type)) value[key] = 23.6;
        if (field.type === 'Map<String, Long>') value[key] = { count: 9.8 };
      }
      const written = writer(value);
      for (const [key, field] of Object.entries(fields)) {
        if (field.documentId) expect(written).not.toHaveProperty(key);
        else if (['Long', 'Int'].includes(field.type)) expect(written[key]).toBe(24);
        else if (field.type === 'Map<String, Long>') expect(written[key]).toEqual({ count: 10 });
      }
    }
  });
  it('keeps decimal double fields and strict post field whitelist', () => {
    const doc = PostToDoc({ ...PostDefaults, lat: 25.2048, lng: 55.2708, reward: 123.8 });
    expect(doc.lat).toBe(25.2048);
    expect(doc.reward).toBe(124);
    expect(Object.keys(doc).sort()).toEqual(
      Object.entries(schema.Post)
        .filter(([, f]) => !f.documentId)
        .map(([k]) => k)
        .sort(),
    );
    expect(BookingToDoc({ ...BookingDefaults, petCount: 2.8 }).petCount).toBe(3);
    expect(ReviewToDoc({ ...ReviewDefaults, rating: 4.4 }).rating).toBe(4);
  });
});
describe('posts and Dubai', () => {
  it('uses Unicode words, Arabic area aliases, dedupe, min length and 80-word cap', () => {
    const keys = keywords('Cat CAT x قطة 123', 'Downtown collar', 'downtown');
    expect(keys).toContain('cat');
    expect(keys).toContain('قطة');
    expect(keys).not.toContain('x');
    expect(keys.filter((k) => k === 'cat')).toHaveLength(1);
    expect(keys).toContain('دبي');
    expect(
      keywords(Array.from({ length: 100 }, (_, i) => 'word' + i).join(' '), 'other'),
    ).toHaveLength(80);
  });
  it('uses the exact geographic nearest cutoff, bounds and Kotlin privacy hash', () => {
    expect(Dubai.nearest(25.2711, 55.3075).key).toBe('deira');
    expect(Dubai.nearest(0, 0).key).toBe('other');
    expect(Dubai.contains(25.2, 55.3)).toBe(true);
    expect(Dubai.contains(26, 55)).toBe(false);
    expect(distanceKm(25, 55, 25, 55)).toBe(0);
    expect(LocationPrivacy.fuzz(25.2048, 55.2708, 'standard', 'abc')).toEqual(
      LocationPrivacy.fuzz(25.2048, 55.2708, 'standard', 'abc'),
    );
  });
  it('applies input trimming and category reward/number rules', () => {
    const p = toPost(
      {
        ...PostDefaults,
        name: ' x '.repeat(40),
        description: 'z'.repeat(2100),
        reward: 22.8,
        areaRadiusM: 7000,
        category: 'street',
        lat: 25.2,
        lng: 55.3,
      },
      { ...UserProfileDefaults, uid: 'u', username: 'name' },
      100,
    );
    expect(p.name).toHaveLength(60);
    expect(p.description).toHaveLength(2000);
    expect(p.reward).toBe(0);
    expect(p.areaRadiusM).toBe(5000);
    expect(p.ownerName).toBe('name');
  });
  it('filters and ranks likely duplicates by time, species, distance and shared words', () => {
    const p = {
      ...PostDefaults,
      id: 'draft',
      createdAt: 30 * 86400000,
      lat: 25.2,
      lng: 55.3,
      category: 'lost',
      keywords: ['collar', 'orange'],
    };
    const good = { ...p, id: 'good' };
    expect(
      likelyDuplicates(p, [
        good,
        { ...good, id: 'dog', animalType: 'dog' },
        { ...good, id: 'old', createdAt: 1 },
        { ...good, id: 'far', lat: 25.8 },
      ]),
    ).toEqual([good]);
  });
  it('uses max activity and exact 14-day reminders / 90-day visibility', () => {
    const p = {
      ...PostDefaults,
      id: 'p',
      category: 'lost',
      createdAt: 100,
      lastActivityAt: 300,
      updatedAt: 200,
    };
    expect(PostStaleness.activity(p)).toBe(300);
    expect(PostStaleness.needsReminder(p, 300 + 14 * 86400000)).toBe(true);
    expect(PostStaleness.needsReminder(p, 299 + 14 * 86400000)).toBe(false);
    expect(PostStaleness.hiddenByDefault(p, 300 + 90 * 86400000)).toBe(true);
    expect(PostStaleness.reminderId(p, 42 * 86400000)).toBe('stale_p_3');
  });
});
describe('notifications, privacy and links', () => {
  it('handles overnight, full-day, exact boundaries and invalid quiet hours', () => {
    expect(QuietHours.contains(1380, 1320, 420)).toBe(true);
    expect(QuietHours.contains(60, 1320, 420)).toBe(true);
    expect(QuietHours.contains(420, 1320, 420)).toBe(false);
    expect(QuietHours.contains(500, 500, 500)).toBe(true);
    expect(QuietHours.contains(500, 300, 600)).toBe(true);
    expect(() => QuietHours.contains(1440, 0, 60)).toThrow();
  });
  it('preserves notification dedupe names and baseline/checkpoint rules', () => {
    expect(notificationId.message('c')).toBe('msg_c');
    expect(notificationId.lost('p')).toBe('lost_p');
    expect(notificationId.match('p')).toBe('match_p');
    expect(notificationId.update('p')).toBe('upd_p');
    const item = { ...AppNotificationDefaults, id: 'new', createdAt: 100 };
    expect(NotificationDelivery.fresh([item], undefined)).toEqual([]);
    expect(NotificationDelivery.fresh([item], [])).toEqual([item]);
    expect(NotificationDelivery.checkpoint([item], ['old', 'new'])).toEqual(['old', 'new']);
  });
  it.each([
    'catmap://post/%61',
    'catmap://post/a/b',
    'catmap://post/a?x=1',
    'catmap://post/a#x',
    'catmap://post:80/a',
    'catmap://user@post/a',
    'catmap://post/../admin',
    'catmap://post//a',
    'https://evil/post/a',
    'catmap://post/',
    'catmap://POST/a',
  ])('rejects unsafe link %s', (link) => expect(PostLinks.parse(link)).toBeNull());
  it('accepts exact valid ids and rejects unsafe creation', () => {
    expect(PostLinks.parse('catmap://post/a_Z-9')).toBe('a_Z-9');
    expect(() => PostLinks.create('a/b')).toThrow();
  });
  it('excludes photos/credentials from exported nested records and normalizes timestamps', () => {
    expect(
      cleanValue({
        password: 'secret',
        tokens: ['token'],
        at: Timestamp.fromMillis(123),
        photo: Bytes.fromUint8Array(new Uint8Array([1])),
        nested: { password: 'secret', name: 'visible' },
      }),
    ).toEqual({ at: 123, photo: null, nested: { name: 'visible' } });
  });
});
describe('text, images and analyser', () => {
  it('formats Android positional, sequential and escaped percent placeholders', () => {
    expect(format('%2$s %1$d %s %d %%', [7, 'Cat'])).toBe('Cat 7 7 NaN %');
    expect(t('step_x_of_y', 2, 6)).toBe('Step 2 of 6');
  });
  it('selects English and all six Arabic plural categories', () => {
    expect(plural('unread_notifications', 1)).toBe('1 unread notification');
    expect(plural('unread_notifications', 3)).toBe('3 unread notifications');
    setLanguage('ar');
    expect(plural('unread_notifications', 0)).toBe('لا توجد إشعارات غير مقروءة');
    expect(plural('unread_notifications', 1)).toBe('إشعار واحد غير مقروء');
    expect(plural('unread_notifications', 2)).toBe('إشعاران غير مقروءين');
    for (const count of [3, 11, 102])
      expect(plural('unread_notifications', count)).not.toBe('unread_notifications');
  });
  it('uses Android quality sequence then shrinking/reset without accepting oversized photos', async () => {
    const calls: { size: number; quality: number }[] = [];
    const result = await compressUnder(
      1280,
      async (size, quality) => {
        calls.push({ size, quality });
        return new Uint8Array(size === 1280 ? 650001 : 123);
      },
      (size, ratio) => Math.round(size * ratio),
    );
    expect(result.byteLength).toBe(123);
    expect(calls).toEqual([
      { size: 1280, quality: 85 },
      { size: 1280, quality: 75 },
      { size: 1280, quality: 65 },
      { size: 1280, quality: 55 },
      { size: 1024, quality: 80 },
    ]);
  });
  it('keeps hist-bin boundaries and full descriptors bounded', () => {
    expect(histBin([0, 1, 1])).toBe(3);
    expect(histBin([0, 0, 1])).toBe(53);
    expect(classify([30, 0.8, 0.9])).toBe(3);
    const image = {
      width: 16,
      height: 16,
      data: Uint8ClampedArray.from({ length: 16 * 16 * 4 }, (_, i) =>
        i % 4 === 3 ? 255 : i % 4 === 0 ? 220 : i % 4 === 1 ? 130 : 40,
      ),
    };
    expect(PostAppearance.valid(describePixels(image))).toBe(true);
    expect(Similarity.valid(analyze(image).features)).toBe(true);
    expect(roundEven(2.5)).toBe(2);
    expect(roundEven(3.5)).toBe(4);
  });
  it('averages only valid descriptors and retains strength lead/uncertainty behaviour', () => {
    const f = new Array(27).fill(0.5);
    expect(PostAppearance.average([f, []])).toEqual(f);
    expect(PostAppearance.similarity([], f)).toBe(0);
    expect(Similarity.strength(0.81, 0.8)).toBe('possible');
    expect(Similarity.strength(0.85, 0.75)).toBe('strong');
    expect(Similarity.proximity(1, 1)).toBe(1);
    expect(Similarity.proximity(Infinity)).toBeNull();
  });
  it('uses leave-one-out recognition evaluation and rejects insufficient data', () => {
    expect(RecognitionEvaluation.evaluate([])).toBeNull();
    const feature = (bin: number) =>
      Array.from({ length: 117 }, (_, i) => (i < 54 ? (i === bin ? 1 : 0) : 0.5));
    const result = RecognitionEvaluation.evaluate(
      ['a', 'a', 'b', 'b'].map((identity, i) => ({
        identity,
        file: String(i),
        features: feature(identity === 'a' ? 1 : 3),
        coat: identity === 'a' ? 'orange' : 'black',
        pattern: 'solid',
        millis: 10,
      })),
    );
    expect(result?.probes).toBe(4);
    expect(result?.top1).toBe(1);
  });
  it('awards exactly the source thresholds and levels', () => {
    expect(
      newlyEarned({ ...UserProfileDefaults, stats: { sightings: 1 } }).map((b) => b.key),
    ).toEqual(['first_whiskers']);
    expect(Levels.of(100)).toBe(2);
    expect(Levels.progress(100)).toBe(0);
    expect(Levels.of(3000)).toBe(6);
  });
});
