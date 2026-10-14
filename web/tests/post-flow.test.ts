import { describe, it, expect } from 'vitest';
import { restoredStep, stepAfter, visibleSteps } from '../src/ui/posts/PostSteps';
import { parseClassification, blocksPhoto, unknownPhoto } from '../src/ai/PhotoCheck';
describe('Android 0.0.8 posting steps and draft migration', () => {
  it('places location first and omits contact only for street animals', () => {
    expect(visibleSteps('lost')).toEqual([
      'TYPE',
      'PLACE',
      'PHOTOS',
      'DETAILS',
      'CONTACT',
      'REVIEW',
    ]);
    expect(visibleSteps('street')).toEqual(['TYPE', 'PLACE', 'PHOTOS', 'DETAILS', 'REVIEW']);
    expect(stepAfter('DETAILS', 'street', true)).toBe('REVIEW');
    expect(stepAfter('REVIEW', 'street', false)).toBe('DETAILS');
    expect(stepAfter('CONTACT', 'street', true)).toBe('REVIEW');
  });
  it('prefers stepName and maps every old web/Android position', () => {
    expect([0, 1, 2, 3, 4, 5].map((step) => restoredStep({ step }))).toEqual([
      'TYPE',
      'PHOTOS',
      'DETAILS',
      'PLACE',
      'CONTACT',
      'REVIEW',
    ]);
    expect(restoredStep({ stepName: 'PLACE', step: 1 })).toBe('PLACE');
    expect(restoredStep({ step: '3' })).toBe('PLACE');
    expect(restoredStep({ step: 99 })).toBe('TYPE');
    expect(restoredStep({ stepName: 'missing', step: '-1' })).toBe('TYPE');
  });
});
describe('PC photo classification, with permissive unknown fallback', () => {
  it('blocks person/object, accepts animals and preserves the server phrase/score', () => {
    const verdicts = parseClassification(
      [
        { kind: 'person', phrase: 'a selfie', score: 0.9 },
        { kind: 'object', phrase: 'a cup' },
        { kind: 'animal', animalType: 'dog', score: 0.8 },
      ],
      3,
    )!;
    expect(verdicts.map(blocksPhoto)).toEqual([true, true, false]);
    expect(verdicts[0]).toMatchObject({ phrase: 'a selfie', score: 0.9, byServer: true });
    expect(verdicts[2].animalType).toBe('dog');
  });
  it('rejects incomplete batches and safely handles malformed or unknown answers', () => {
    expect(parseClassification([], 1)).toBeNull();
    expect(parseClassification(null, 1)).toBeNull();
    expect(blocksPhoto(unknownPhoto())).toBe(false);
    expect(
      parseClassification([null, { kind: 5, animalType: 4, phrase: 7, score: NaN }], 2)?.map(
        (v) => [v.kind, v.phrase, v.score],
      ),
    ).toEqual([
      ['unknown', '', 0],
      ['unknown', '', 0],
    ]);
  });
});
