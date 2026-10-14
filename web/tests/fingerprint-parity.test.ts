import { readFileSync } from 'node:fs';
import { it, expect } from 'vitest';
import { describePixels } from '../src/ai/PostAppearance';
import { describe } from '../src/ai/CatAnalyzer';
import { cosine } from '../src/ai/Similarity';
interface Fixture {
  file: string;
  post: { width: number; height: number; rgba: string; values: number[] };
  analysis: { width: number; height: number; rgba: string; values: number[] };
}
it('matches real Android bitmap fingerprints within 0.03 per value and cosine >= 0.98', () => {
  const fixtures = JSON.parse(
    readFileSync(new URL('./fixtures/android-fingerprints.json', import.meta.url), 'utf8'),
  ) as Fixture[];
  expect(fixtures.length).toBeGreaterThanOrEqual(3);
  for (const fixture of fixtures)
    for (const kind of ['post', 'analysis'] as const) {
      const input = fixture[kind],
        pixels = {
          width: input.width,
          height: input.height,
          data: new Uint8ClampedArray(Buffer.from(input.rgba, 'base64')),
        };
      const actual = kind === 'post' ? describePixels(pixels) : describe(pixels).features;
      const differences = actual.map((v, i) => Math.abs(v - input.values[i])),
        max = Math.max(...differences),
        cos = cosine(actual, input.values);
      console.log(
        `${fixture.file} ${kind}: maxDelta=${max.toFixed(6)}, cosine=${cos.toFixed(8)}, indices=${differences
          .map((v, i) => (v > 0.001 ? i + '=' + v.toFixed(4) : ''))
          .filter(Boolean)
          .join(' ')}`,
      );
      expect(max).toBeLessThanOrEqual(0.03);
      expect(cos).toBeGreaterThanOrEqual(0.98);
    }
});
