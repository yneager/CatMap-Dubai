const clamp = (v: number) => Math.max(0, Math.min(1, v));
const valid = (v: number[]) =>
  v.length === 117 && v.every((n) => Number.isFinite(n) && n >= 0 && n <= 1);
export function cosine(a: number[], b: number[]) {
  const dot = a.reduce((s, v, i) => s + v * (b[i] ?? 0), 0),
    x = a.reduce((s, v) => s + v * v, 0),
    y = b.reduce((s, v) => s + v * v, 0);
  return x && y ? dot / Math.sqrt(x * y) : 0;
}
function components(a: number[], b: number[]) {
  if (!valid(a) || !valid(b)) return { colour: 0, texture: 0, layout: 0 };
  const histogram = clamp(a.slice(0, 54).reduce((s, v, i) => s + Math.min(v, b[i]), 0));
  const layout = clamp(
    1 - a.slice(54, 102).reduce((s, v, i) => s + Math.abs(v - b[i + 54]), 0) / 48 / 0.35,
  );
  const texture = clamp(
    0.6 * cosine(a.slice(102, 110), b.slice(102, 110)) +
      0.4 * (1 - Math.min(1, Math.abs(a[110] - b[110]) * 3)),
  );
  const share = clamp(1 - a.slice(111).reduce((s, v, i) => s + Math.abs(v - b[i + 111]), 0) / 2);
  return { colour: clamp(0.55 * histogram + 0.45 * share), texture, layout };
}
export const Similarity = {
  valid,
  visual: (a: number[], b: number[]) => {
    const c = components(a, b);
    return 0.6 * c.colour + 0.25 * c.texture + 0.15 * c.layout;
  },
  proximity: (distance: number, uncertainty = 0, daysApart = 0) =>
    Number.isFinite(distance)
      ? Math.exp(
          -Math.max(distance - Math.max(uncertainty, 0), 0) /
            (1.5 + Math.min(Math.max(daysApart, 0) * 0.05, 2.5)),
        )
      : null,
  score: (
    a: number[],
    b: number[],
    coatA: string,
    coatB: string,
    patternA: string,
    patternB: string,
    distance: number | null = null,
    uncertainty = 0,
    daysApart = 0,
  ) => {
    const visual = Similarity.visual(a, b),
      attributes = (coatA === coatB ? 0.65 : 0) + (patternA === patternB ? 0.35 : 0),
      proximity = distance === null ? null : Similarity.proximity(distance, uncertainty, daysApart);
    return clamp(
      proximity === null
        ? 0.77 * visual + 0.23 * attributes
        : 0.65 * visual + 0.2 * attributes + 0.15 * proximity,
    );
  },
  strength: (score: number, runnerUp: number | null = null) =>
    score >= 0.8 && (runnerUp === null || score - runnerUp >= 0.05)
      ? 'strong'
      : score >= 0.62
        ? 'possible'
        : 'weak',
  average: (values: number[][]) => {
    const good = values.filter(valid);
    return good.length
      ? Array.from(
          { length: 117 },
          (_, i) => Math.round((good.reduce((s, v) => s + v[i], 0) / good.length) * 10000) / 10000,
        )
      : [];
  },
};
