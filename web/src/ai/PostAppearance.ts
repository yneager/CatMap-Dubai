import type { Post } from '../data/model';
import { distanceKm } from '../data/Neighborhoods';
import { hsv, histBin, type Pixels } from './CatAnalyzer';
import { scaleToMax } from '../util/images';
import { roundEven } from '../util/math';
export { roundEven } from '../util/math';
const valid = (v: number[]) =>
  v.length === 27 && v.every((n) => Number.isFinite(n) && n >= 0 && n <= 1);
export function describePixels(image: Pixels) {
  const { width: w, height: h, data } = image;
  const values = new Array<number>(27).fill(0),
    totals = [0, 0, 0];
  let total = 0;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++) {
      const dx = (x - w / 2) / (w * 0.3),
        dy = (y - h / 2) / (h * 0.32),
        weight = Math.exp(-(dx * dx + dy * dy) / 2),
        i = (y * w + x) * 4;
      const bin = histBin(hsv(data[i], data[i + 1], data[i + 2]));
      values[bin < 48 ? Math.floor(bin / 4) : 12 + bin - 48] += weight;
      total += weight;
      const cell = Math.min(2, Math.floor((x * 3) / w));
      totals[cell] += weight;
      for (let c = 0; c < 3; c++) values[18 + cell * 3 + c] += (data[i + c] / 255) * weight;
    }
  return values.map(
    (v, i) =>
      roundEven((v / Math.max(1e-9, i < 18 ? total : totals[Math.floor((i - 18) / 3)])) * 10000) /
      10000,
  );
}
function describe(canvas: HTMLCanvasElement) {
  const small = scaleToMax(canvas, 128);
  return describePixels(small.getContext('2d')!.getImageData(0, 0, small.width, small.height));
}
const similarity = (a: number[], b: number[]) =>
  !valid(a) || !valid(b)
    ? 0
    : 0.8 *
        Math.min(
          1,
          a.slice(0, 18).reduce((sum, v, i) => sum + Math.min(v, b[i]), 0),
        ) +
      0.2 *
        Math.max(0, 1 - a.slice(18).reduce((sum, v, i) => sum + Math.abs(v - b[i + 18]), 0) / 9);
export const PostAppearance = {
  SIZE: 27,
  HIST_BINS: 18,
  valid,
  describe,
  describePixels,
  similarity,
  average: (values: number[][]) => {
    const good = values.filter(valid);
    return good.length
      ? Array.from({ length: 27 }, (_, i) => good.reduce((sum, v) => sum + v[i], 0) / good.length)
      : [];
  },
  matches: (p: Post, candidates: Post[]) =>
    candidates
      .filter(
        (c) =>
          c.id !== p.id &&
          !c.hidden &&
          c.status === 'open' &&
          c.animalType === p.animalType &&
          (p.category === 'lost'
            ? ['found', 'spotted'].includes(c.category)
            : ['found', 'spotted'].includes(p.category) && c.category === 'lost'),
      )
      .flatMap((post) => {
        const distance = distanceKm(p.lat, p.lng, post.lat, post.lng);
        if (!Number.isFinite(distance) || distance > 10) return [];
        const time = Math.exp(-Math.abs(p.createdAt - post.createdAt) / (21 * 86400000)),
          proximity = Math.exp(-distance / 3),
          photoBased = valid(p.fingerprint) && valid(post.fingerprint);
        return [
          {
            post,
            photoBased,
            score: photoBased
              ? 0.6 * similarity(p.fingerprint, post.fingerprint) + 0.25 * proximity + 0.15 * time
              : 0.6 * proximity + 0.4 * time,
          },
        ];
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, 6),
};
