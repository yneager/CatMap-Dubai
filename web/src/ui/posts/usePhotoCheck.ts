import { useEffect, useRef, useState } from 'react';
import { AiServer } from '../../data/AiServer';
import { PostRepository } from '../../data/PostRepository';
import { ImageRepository } from '../../data/ImageRepository';
import { PostAppearance } from '../../ai/PostAppearance';
import { unknownPhoto, type PhotoVerdict } from '../../ai/PhotoCheck';
import { distanceKm } from '../../data/Neighborhoods';
import { decodeUpright, scaleToMax, type ProcessedPhoto } from '../../util/images';
import { enums } from '../../data/model/Enums';
import type { Post } from '../../data/model';
export interface SameAnimalMatch {
  post: Post;
  similarity: number;
  verdict: string;
  server: boolean;
}
interface Photo {
  key: string;
  processed: ProcessedPhoto;
}
export function usePhotoCheck(
  photos: Photo[],
  post: Post,
  editing: boolean,
  posts: Post[] | undefined,
  picked: React.RefObject<boolean>,
  fillType: (type: string) => void,
) {
  const [verdicts, setVerdicts] = useState<Record<string, PhotoVerdict>>({});
  const [matches, setMatches] = useState<SameAnimalMatch[]>([]);
  const [running, setRunning] = useState(false);
  const comparedKey = useRef<string | null>(null);
  const latest = useRef({ posts, fillType, animalType: post.animalType });
  latest.current = { posts, fillType, animalType: post.animalType };
  const key =
    !editing && post.category === 'street' && photos[0] && post.lat && post.lng
      ? `${photos[0].key}|${post.lat}|${post.lng}`
      : null;
  const pending =
    photos.some((photo) => !verdicts[photo.key]) || (!!key && key !== comparedKey.current);
  useEffect(() => {
    const unchecked = photos.filter((photo) => !verdicts[photo.key]);
    const compare = !!key && key !== comparedKey.current;
    if (!key) {
      comparedKey.current = null;
      setMatches([]);
    }
    if (!unchecked.length && !compare) {
      setRunning(false);
      return;
    }
    const controller = new AbortController();
    setRunning(true);
    void (async () => {
      const nearby = !compare
        ? []
        : (await PostRepository.near(post.lat, post.lng, 1))
            .filter(
              (candidate) =>
                candidate.category === 'street' &&
                candidate.status === 'open' &&
                !candidate.hidden &&
                candidate.photoIds.length &&
                distanceKm(candidate.lat, candidate.lng, post.lat, post.lng) <= 1,
            )
            .sort(
              (a, b) =>
                distanceKm(a.lat, a.lng, post.lat, post.lng) -
                distanceKm(b.lat, b.lng, post.lat, post.lng),
            )
            .slice(0, 8);
      const crops: { id: string; image: HTMLCanvasElement }[] = [];
      for (const candidate of nearby) {
        if (controller.signal.aborted) return;
        const bytes = await ImageRepository.bytes(candidate.photoIds[0]);
        if (!bytes) continue;
        const image = await decodeUpright(
          new Blob([new Uint8Array(bytes)], { type: 'image/jpeg' }),
        );
        crops.push({ id: candidate.id, image: scaleToMax(image, 640) });
        if ('close' in image) image.close();
      }
      const server = await AiServer.check(
        unchecked.map((p) => p.processed.analysis),
        crops.length ? photos[0].processed.analysis : null,
        crops,
        crops.map((c) => {
          const candidate = nearby.find((p) => p.id === c.id)!;
          return candidate.name || 'Street ' + candidate.animalType;
        }),
        controller.signal,
      );
      if (controller.signal.aborted) return;
      const checked = { ...verdicts };
      unchecked.forEach((photo, index) => {
        checked[photo.key] = server?.photos?.[index] ?? unknownPhoto();
      });
      const cover = checked[photos[0]?.key];
      let type = latest.current.animalType;
      if (
        cover?.kind === 'animal' &&
        !picked.current &&
        enums.AnimalType.some((a) => a.key === cover.animalType)
      ) {
        type = cover.animalType;
        latest.current.fillType(type);
      }
      if (compare) {
        comparedKey.current = key;
        const allowed = nearby.filter((candidate) => candidate.animalType === type);
        const result: SameAnimalMatch[] = server?.compared
          ? server.matches
              .filter((m) => ['same', 'maybe'].includes(m.verdict))
              .flatMap((m) => {
                const candidate = allowed.find((p) => p.id === m.id);
                return candidate
                  ? [
                      {
                        post: candidate,
                        similarity: m.similarity,
                        verdict: m.verdict,
                        server: true,
                      },
                    ]
                  : [];
              })
          : allowed
              .map((candidate) => ({
                post: candidate,
                similarity: PostAppearance.similarity(
                  PostAppearance.describe(photos[0].processed.analysis),
                  candidate.fingerprint,
                ),
                verdict: 'maybe',
                server: false,
              }))
              .filter((m) => m.similarity >= 0.85)
              .sort((a, b) => b.similarity - a.similarity)
              .slice(0, 3);
        setMatches(result.sort((a, b) => b.similarity - a.similarity));
      }
      setVerdicts(checked);
    })()
      .catch(() => {
        if (controller.signal.aborted) return;
        const checked = { ...verdicts };
        unchecked.forEach((photo) => {
          checked[photo.key] ??= unknownPhoto();
        });
        if (compare) {
          comparedKey.current = key;
          setMatches([]);
        }
        setVerdicts(checked);
      })
      .finally(() => {
        if (!controller.signal.aborted) setRunning(false);
      });
    return () => controller.abort();
  }, [photos, key, post.lat, post.lng, verdicts, picked]);
  return { verdicts, matches, checking: running || pending };
}
