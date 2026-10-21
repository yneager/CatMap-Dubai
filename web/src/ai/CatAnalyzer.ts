export interface Pixels {
  width: number;
  height: number;
  data: Uint8ClampedArray;
}
const f = Math.fround;
const clamp = (v: number, min = 0, max = 1) => Math.max(min, Math.min(max, v));
export const FAMILY = ['black', 'white', 'gray', 'orange', 'cream', 'brown'] as const;
export function hsv(r: number, g: number, b: number): [number, number, number] {
  r /= 255;
  g /= 255;
  b /= 255;
  const max = Math.max(r, g, b),
    min = Math.min(r, g, b),
    delta = max - min;
  let h =
    delta === 0
      ? 0
      : max === r
        ? 60 * (((g - b) / delta) % 6)
        : max === g
          ? 60 * ((b - r) / delta + 2)
          : 60 * ((r - g) / delta + 4);
  if (h < 0) h += 360;
  return [Math.fround(h), Math.fround(max === 0 ? 0 : delta / max), Math.fround(max)];
}
export function histBin([h, s, v]: number[]) {
  if (s < 0.18 || v < 0.15) return 48 + clamp(Math.trunc(v * 6), 0, 5);
  return clamp(Math.trunc((h / 360) * 12), 0, 11) * 4 + (s < 0.5 ? 0 : 1) * 2 + (v < 0.55 ? 0 : 1);
}
export function classify([h, s, v]: number[]): number | null {
  if (v < 0.22) return 0;
  if (s < 0.14 && v > 0.78) return 1;
  if (s < 0.18) return v < 0.3 ? 0 : 2;
  if (h < 50 || h > 345) return s > 0.42 && v > 0.45 ? 3 : v > 0.68 ? 4 : 5;
  if (h < 65) return v > 0.6 ? 4 : 5;
  if (h >= 180 && h <= 260 && s < 0.3) return v > 0.75 ? 1 : 2;
  return null;
}
export function centreWeights(width: number, height: number) {
  return Float32Array.from({ length: width * height }, (_, i) => {
    const dx = f(f((i % width) - f(width / 2)) / f(width * f(0.3))),
      dy = f(f(Math.floor(i / width) - f(height / 2)) / f(height * f(0.32)));
    return f(Math.exp(f(-f(f(dx * dx) + f(dy * dy)) / 2)));
  });
}
export function colourLayout(image: Pixels, weights: Float32Array) {
  const { width: w, height: h, data } = image;
  let minX = w,
    minY = h,
    maxX = 0,
    maxY = 0;
  for (let y = 0; y < h; y++)
    for (let x = 0; x < w; x++)
      if (weights[y * w + x] > 0.5) {
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
      }
  if (maxX <= minX || maxY <= minY) {
    minX = 0;
    minY = 0;
    maxX = w - 1;
    maxY = h - 1;
  }
  const sums = new Array<number>(48).fill(0),
    totals = new Array<number>(16).fill(0);
  for (let y = minY; y <= maxY; y++)
    for (let x = minX; x <= maxX; x++) {
      const weight = weights[y * w + x];
      if (weight <= 0.2) continue;
      const cell =
        clamp(Math.floor(((y - minY) / (maxY - minY + 1)) * 4), 0, 3) * 4 +
        clamp(Math.floor(((x - minX) / (maxX - minX + 1)) * 4), 0, 3);
      totals[cell] += weight;
      for (let c = 0; c < 3; c++) sums[cell * 3 + c] += (data[(y * w + x) * 4 + c] / 255) * weight;
    }
  return sums.map((v, i) => (totals[Math.floor(i / 3)] > 0 ? v / totals[Math.floor(i / 3)] : 0.5));
}
export function stripeRate(gray: Float32Array, weights: Float32Array, w: number, h: number) {
  let length = 0,
    transitions = 0;
  const scan = (indices: number[]) => {
    let extreme = -1,
      rising = 0;
    for (const i of indices) {
      if (weights[i] < 0.5) {
        extreme = -1;
        rising = 0;
        continue;
      }
      length++;
      const v = gray[i];
      if (extreme < 0) {
        extreme = v;
        continue;
      }
      if (!rising) {
        if (Math.abs(v - extreme) > 0.07) {
          rising = v > extreme ? 1 : -1;
          extreme = v;
        }
      } else if (rising > 0) {
        if (v > extreme) extreme = v;
        else if (extreme - v > 0.07) {
          transitions++;
          rising = -1;
          extreme = v;
        }
      } else {
        if (v < extreme) extreme = v;
        else if (v - extreme > 0.07) {
          transitions++;
          rising = 1;
          extreme = v;
        }
      }
    }
  };
  for (let y = 0; y < h; y += 3) scan(Array.from({ length: w }, (_, x) => y * w + x));
  for (let x = 0; x < w; x += 3) scan(Array.from({ length: h }, (_, y) => y * w + x));
  return length ? (transitions * 100) / length : 0;
}
const confidence = (n: number) => clamp(n, 0.2, 0.97);
export function coatColorFrom(shares: number[]): [string, number] {
  const [b, w, g, o, c, r] = shares,
    t = o + 0.5 * r;
  if (t >= 0.12 && b >= 0.12 && w >= 0.15) return ['calico', confidence(Math.min(t, b, w) * 3)];
  if (t >= 0.15 && b >= 0.18 && w < 0.15)
    return ['tortoiseshell', confidence(Math.min(t, b) * 2.5)];
  if (c + w >= 0.55 && r + b >= 0.08 && r + b <= 0.35 && o < 0.12)
    return ['colorpoint', confidence(0.45)];
  if (w >= 0.2 && b >= 0.2) return ['black_white', confidence(Math.min(w, b) * 2.2)];
  if (w >= 0.2 && o + 0.5 * c >= 0.2)
    return ['orange_white', confidence(Math.min(w, o + 0.5 * c) * 2.2)];
  if (w >= 0.2 && g >= 0.2) return ['gray_white', confidence(Math.min(w, g) * 2.2)];
  const max = Math.max(...shares);
  return max <= 0 ? ['unknown', 0] : [FAMILY[shares.indexOf(max)], confidence(max)];
}
export function patternFrom(
  coat: string,
  shares: number[],
  rate: number,
  edge: number,
): [string, number] {
  const striped = rate > 2.5 || edge > 0.3;
  switch (coat) {
    case 'calico':
      return ['calico', 0.8];
    case 'tortoiseshell':
      return ['tortoiseshell', 0.75];
    case 'colorpoint':
      return ['colorpoint', 0.5];
    case 'black_white':
      return shares[0] > shares[1] * 1.4 ? ['tuxedo', 0.65] : ['bicolor', 0.7];
    case 'orange_white':
    case 'gray_white':
      return striped ? ['tabby', 0.55] : ['bicolor', 0.7];
    case 'black':
    case 'white':
      return ['solid', 0.75];
    case 'unknown':
      return ['unknown', 0];
    default:
      return striped
        ? ['tabby', confidence(0.5 + (rate - 2.5) / 10)]
        : ['solid', confidence(0.4 + (2.5 - rate) / 10)];
  }
}
export function breedHints(coat: string, pattern: string) {
  const result = [
    { key: 'arabian_mau', confidence: 0.65 },
    { key: 'domestic_shorthair', confidence: 0.55 },
  ];
  if (pattern === 'colorpoint') result.push({ key: 'colorpoint', confidence: 0.5 });
  if (['tabby', 'tuxedo'].includes(pattern)) result.push({ key: pattern, confidence: 0.6 });
  if (['orange', 'orange_white'].includes(coat))
    result.push({ key: 'orange_male', confidence: 0.8 });
  if (['calico', 'tortoiseshell'].includes(coat))
    result.push({ key: 'tortie_female', confidence: 0.95 });
  if (coat === 'white') result.push({ key: 'white_deafness', confidence: 0.4 });
  if (coat === 'gray') result.push({ key: 'gray', confidence: 0.35 });
  if (coat === 'white') result.push({ key: 'white_deafness', confidence: 0.4 });
  if (coat === 'gray') result.push({ key: 'gray', confidence: 0.35 });
  return result.sort((a, b) => b.confidence - a.confidence);
}
export function describe(image: Pixels) {
  const { width: w, height: h, data } = image;
  const weights = centreWeights(w, h),
    hist = new Array<number>(54).fill(0),
    families = new Array<number>(6).fill(0),
    gray = new Float32Array(w * h),
    orientation = new Array<number>(8).fill(0);
  let catWeight = 0,
    areaWeight = 0,
    edgeWeight = 0;
  for (let i = 0; i < w * h; i++) {
    const rgb = [data[i * 4], data[i * 4 + 1], data[i * 4 + 2]];
    gray[i] = f(f(f(f(rgb[0] * f(0.299)) + f(rgb[1] * f(0.587))) + f(rgb[2] * f(0.114))) / 255);
    if (weights[i] > 0.05) {
      const color = hsv(rgb[0], rgb[1], rgb[2]);
      hist[histBin(color)] += weights[i];
      const family = classify(color);
      if (family !== null) {
        families[family] = f(families[family] + weights[i]);
        catWeight = f(catWeight + weights[i]);
      }
    }
  }
  for (let y = 1; y < h - 1; y++)
    for (let x = 1; x < w - 1; x++) {
      const i = y * w + x,
        weight = weights[i];
      if (weight <= 0.3) continue;
      areaWeight += weight;
      const gx = f(gray[i + 1] - gray[i - 1]),
        gy = f(gray[i + w] - gray[i - w]),
        magnitude = f(Math.sqrt(f(f(gx * gx) + f(gy * gy))));
      if (magnitude > 0.08) {
        edgeWeight += weight;
        orientation[
          clamp(Math.trunc(((f(Math.atan2(gy, gx)) + Math.PI) / (2 * Math.PI)) * 8), 0, 7)
        ] += magnitude * weight;
      }
    }
  const total = hist.reduce((a, b) => a + b, 0) || 1,
    textureTotal = orientation.reduce((a, b) => a + b, 0) || 1;
  const shares = families.map((v) => (catWeight ? f(v / catWeight) : 0)),
    edge = areaWeight ? f(edgeWeight / areaWeight) : 0;
  const [coatColor, colorConfidence] = coatColorFrom(shares),
    rate = stripeRate(gray, weights, w, h),
    [pattern, patternConfidence] = patternFrom(coatColor, shares, rate, edge);
  const features = [
    ...hist.map((v) => v / total),
    ...colourLayout(image, weights),
    ...orientation.map((v) => v / textureTotal),
    edge,
    ...shares,
  ].map((v) => Math.round(v * 10000) / 10000);
  return {
    features,
    coatColor,
    colorConfidence,
    pattern,
    patternConfidence,
    breedHints: breedHints(coatColor, pattern),
    catDetected: null,
    subjectCount: 1,
  };
}
export const CatAnalyzer = {
  FEATURE_SIZE: 117,
  histBin,
  classify,
  centreWeights,
  colourLayout,
  stripeRate,
  coatColorFrom,
  patternFrom,
  breedHints,
  describe,
};
