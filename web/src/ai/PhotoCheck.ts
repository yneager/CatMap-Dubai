export interface PhotoVerdict {
  kind: string;
  animalType: string;
  phrase: string;
  score: number;
  byServer: boolean;
}
export const unknownPhoto = (): PhotoVerdict => ({ kind: 'unknown', animalType: '', phrase: '', score: 0, byServer: false });
export const blocksPhoto = (verdict?: PhotoVerdict) => verdict?.kind === 'person' || verdict?.kind === 'object';
export function parseClassification(value: unknown, expected: number): PhotoVerdict[] | null {
  if (!Array.isArray(value) || value.length !== expected) return null;
  return value.map((item: unknown) => {
    const c = item && typeof item === 'object' ? item as Record<string, unknown> : {};
    return {
      kind: typeof c.kind === 'string' ? c.kind : 'unknown',
      animalType: typeof c.animalType === 'string' ? c.animalType : '',
      phrase: typeof c.phrase === 'string' ? c.phrase : '',
      score: typeof c.score === 'number' && Number.isFinite(c.score) ? c.score : 0,
      byServer: true,
    };
  });
}
