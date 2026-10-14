import { Similarity, cosine } from './Similarity';
export interface Sample {
  identity: string;
  file: string;
  features: number[];
  coat: string;
  pattern: string;
  millis: number;
}
const mean = (list: number[]) => (list.length ? list.reduce((s, v) => s + v, 0) / list.length : 0);
const pct = (v: number) => (v * 100).toFixed(1) + '%';
function groups<T extends { identity: string }>(samples: T[]) {
  const result = new Map<string, T[]>();
  for (const sample of samples)
    result.set(sample.identity, [...(result.get(sample.identity) ?? []), sample]);
  return result;
}
export function evaluate(all: Sample[], threshold = 0.45) {
  const samples = all.filter((s) => s.features.length === 117),
    byCat = groups(samples),
    probes = samples.filter((s) => byCat.get(s.identity)!.length >= 2);
  if (byCat.size < 2 || !probes.length) return null;
  const genuine: number[] = [],
    impostor: number[] = [],
    perProbe: {
      identity: string;
      file: string;
      rankAverage: number;
      rankBestPhoto: number;
      genuine: number;
      bestImpostor: number;
    }[] = [];
  let top1 = 0,
    top3 = 0,
    top1b = 0,
    top3b = 0,
    any = 0,
    possible = 0,
    strong = 0;
  for (const probe of probes) {
    const scores = [...byCat].map(([identity, items]) => {
      const gallery = items.filter((s) => s !== probe),
        first = gallery[0];
      const average = Similarity.score(
          probe.features,
          Similarity.average(gallery.map((s) => s.features)),
          probe.coat,
          first.coat,
          probe.pattern,
          first.pattern,
        ),
        best = Math.max(
          ...gallery.map((s) =>
            Similarity.score(
              probe.features,
              s.features,
              probe.coat,
              s.coat,
              probe.pattern,
              s.pattern,
            ),
          ),
        );
      return { identity, average, best };
    });
    const own = scores.find((s) => s.identity === probe.identity)!;
    const rankAverage =
        1 + scores.filter((s) => s.identity !== probe.identity && s.average > own.average).length,
      rankBestPhoto =
        1 + scores.filter((s) => s.identity !== probe.identity && s.best > own.best).length;
    if (rankAverage === 1) top1++;
    if (rankAverage <= 3) top3++;
    if (rankBestPhoto === 1) top1b++;
    if (rankBestPhoto <= 3) top3b++;
    genuine.push(own.average);
    const others = scores
      .filter((s) => s.identity !== probe.identity)
      .map((s) => s.average)
      .sort((a, b) => b - a);
    impostor.push(...others);
    const best = others[0] ?? 0;
    if (best >= threshold) any++;
    const strength = Similarity.strength(best, others[1] ?? null);
    if (strength === 'strong') {
      strong++;
      possible++;
    } else if (strength === 'possible') possible++;
    perProbe.push({
      identity: probe.identity,
      file: probe.file,
      rankAverage,
      rankBestPhoto,
      genuine: own.average,
      bestImpostor: best,
    });
  }
  const n = probes.length,
    sorted = impostor.slice().sort((a, b) => a - b),
    threshold5 = sorted.length ? sorted[Math.trunc((sorted.length - 1) * 0.95)] : null;
  return {
    identities: byCat.size,
    photos: samples.length,
    probes: n,
    skippedPhotos: all.length - samples.length,
    top1: top1 / n,
    top3: top3 / n,
    top1BestPhoto: top1b / n,
    top3BestPhoto: top3b / n,
    unseenAnySuggestion: any / n,
    unseenPossible: possible / n,
    unseenStrong: strong / n,
    genuineMean: mean(genuine),
    impostorMean: mean(impostor),
    threshold5,
    genuineAtThreshold5:
      threshold5 === null ? null : genuine.filter((v) => v > threshold5).length / genuine.length,
    averageMillis: mean(samples.map((s) => s.millis)),
    perProbe,
  };
}
export function evaluateEmbeddings(
  samples: { identity: string; embedding: number[] }[],
  same: number,
  maybe: number,
) {
  const valid = samples.filter((s) => s.embedding.length),
    byCat = groups(valid),
    probes = valid.filter((s) => byCat.get(s.identity)!.length >= 2);
  if (byCat.size < 2 || !probes.length) return null;
  let top1a = 0,
    top1b = 0,
    top3b = 0,
    unseenSame = 0,
    unseenMaybe = 0,
    knownSame = 0;
  const genuine: number[] = [],
    impostor: number[] = [];
  for (const probe of probes) {
    const scores = [...byCat].map(([identity, items]) => {
      const gallery = items.filter((s) => s !== probe).map((s) => s.embedding);
      const average = gallery[0].map((_, i) => mean(gallery.map((v) => v[i])));
      return {
        identity,
        average: cosine(probe.embedding, average),
        best: Math.max(...gallery.map((v) => cosine(probe.embedding, v))),
      };
    });
    const own = scores.find((s) => s.identity === probe.identity)!;
    if (!scores.some((s) => s.identity !== probe.identity && s.average > own.average)) top1a++;
    const rank =
      1 + scores.filter((s) => s.identity !== probe.identity && s.best > own.best).length;
    if (rank === 1) top1b++;
    if (rank <= 3) top3b++;
    genuine.push(own.best);
    if (own.best >= same) knownSame++;
    const others = scores.filter((s) => s.identity !== probe.identity).map((s) => s.best);
    impostor.push(...others);
    const best = Math.max(...others);
    if (best >= same) unseenSame++;
    if (best >= maybe) unseenMaybe++;
  }
  const n = probes.length,
    sorted = impostor.slice().sort((a, b) => a - b),
    threshold5 = sorted.length ? sorted[Math.trunc((sorted.length - 1) * 0.95)] : null;
  return {
    probes: n,
    top1Average: top1a / n,
    top1BestPhoto: top1b / n,
    top3BestPhoto: top3b / n,
    unseenSame: unseenSame / n,
    unseenMaybe: unseenMaybe / n,
    knownSame: knownSame / n,
    genuineMean: mean(genuine),
    impostorMean: mean(impostor),
    threshold5,
    genuineAtThreshold5:
      threshold5 === null ? null : genuine.filter((v) => v > threshold5).length / genuine.length,
  };
}
export function report(r: NonNullable<ReturnType<typeof evaluate>>, version: string) {
  return `CatMap recognition test (app ${version}; web analyser)\nDataset: ${r.identities} cats, ${r.photos} photos, ${r.probes} test photos (cats with 2+ photos); ${r.skippedPhotos} photos could not be analysed.\nLocation is not used in this test; scores are visual + coat attributes.\n\nKnown cat (closed set, leave-one-out)\n  App method (averaged descriptor): top-1 ${pct(r.top1)}, top-3 ${pct(r.top3)}\n  Best single photo instead: top-1 ${pct(r.top1BestPhoto)}, top-3 ${pct(r.top3BestPhoto)}\n  Chance level: top-1 ${pct(1 / r.identities)}\n\nUnseen cat (open set: every suggestion is wrong)\n  Any suggestion shown (score >= 0.45): ${pct(r.unseenAnySuggestion)}\n  Labelled "possible" or better: ${pct(r.unseenPossible)}\n  Labelled "strong": ${pct(r.unseenStrong)}\n\nScores: same cat mean ${r.genuineMean.toFixed(3)}, different cats mean ${r.impostorMean.toFixed(3)}\n  At most 5% of different-cat pairs score above ${r.threshold5?.toFixed(3)}; ${pct(r.genuineAtThreshold5 ?? 0)} of same-cat pairs do.\nAverage analysis time per photo: ${Math.trunc(r.averageMillis)} ms\n\nidentity,file,rank_average,rank_best_photo,same_cat_score,best_other_score\n${r.perProbe.map((p) => `${p.identity},${p.file},${p.rankAverage},${p.rankBestPhoto},${p.genuine.toFixed(3)},${p.bestImpostor.toFixed(3)}`).join('\n')}`;
}
export function embeddingReport(
  r: NonNullable<ReturnType<typeof evaluateEmbeddings>>,
  model: string,
  same: number,
  maybe: number,
) {
  return `AI model (${model} on the AI server; verdict thresholds same >= ${same.toFixed(3)}, maybe >= ${maybe.toFixed(3)})\n  Known cat: top-1 ${pct(r.top1BestPhoto)} (best photo), ${pct(r.top1Average)} (averaged), top-3 ${pct(r.top3BestPhoto)}\n  Same cat reaches "same": ${pct(r.knownSame)}\n  Unseen cat called "same": ${pct(r.unseenSame)}; "maybe" or better: ${pct(r.unseenMaybe)}\n  Cosine: same cat mean ${r.genuineMean.toFixed(3)}, different cats mean ${r.impostorMean.toFixed(3)}\n  At most 5% of different-cat pairs score above ${r.threshold5?.toFixed(3)}; ${pct(r.genuineAtThreshold5 ?? 0)} of same-cat pairs do.`;
}
export const RecognitionEvaluation = { evaluate, evaluateEmbeddings, report, embeddingReport };
