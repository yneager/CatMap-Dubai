import { readFileSync } from 'node:fs';
import path from 'node:path';
import { test, expect } from '@playwright/test';
import { account, login } from './helpers';
import { cosine } from '../src/ai/Similarity';
interface Fixture {
  file: string;
  post: { values: number[] };
  analysis: { values: number[] };
}
test('browser decoding/scaling produces Android-compatible descriptors and strips JPEG metadata', async ({
  page,
}) => {
  await login(page, await account());
  const fixtures = JSON.parse(
    readFileSync('tests/fixtures/android-fingerprints.json', 'utf8'),
  ) as Fixture[];
  for (const f of fixtures) {
    const input = readFileSync(path.resolve('..', f.file)).toString('base64');
    const actual = await page.evaluate(async (base64) => {
      const { processPhoto } = await import('/src/util/images.ts' /* @vite-ignore */),
        { PostAppearance } = await import('/src/ai/PostAppearance.ts' /* @vite-ignore */),
        { describe } = await import('/src/ai/CatAnalyzer.ts' /* @vite-ignore */);
      const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0)),
        p = await processPhoto(new Blob([bytes], { type: 'image/jpeg' }));
      return {
        post: PostAppearance.describe(p.analysis) as number[],
        analysis: describe(
          p.analysis.getContext('2d')!.getImageData(0, 0, p.analysis.width, p.analysis.height),
        ).features as number[],
        size: p.full.length,
        width: p.analysis.width,
        height: p.analysis.height,
        metadata: !new TextDecoder('latin1').decode(p.full).includes('Exif\0\0'),
      };
    }, input);
    expect(actual.size).toBeLessThanOrEqual(650000);
    expect(Math.max(actual.width, actual.height)).toBeLessThanOrEqual(512);
    expect(actual.metadata).toBe(true);
    for (const kind of ['post', 'analysis'] as const) {
      const delta = Math.max(...actual[kind].map((v, i) => Math.abs(v - f[kind].values[i]))),
        cos = cosine(actual[kind], f[kind].values);
      console.log(`${f.file} browser ${kind}: delta=${delta.toFixed(6)}, cosine=${cos.toFixed(8)}`);
      expect(delta).toBeLessThanOrEqual(0.03);
      expect(cos).toBeGreaterThanOrEqual(0.98);
    }
  }
});
