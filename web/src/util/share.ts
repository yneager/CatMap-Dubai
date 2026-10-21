import { t } from '../i18n';
import { PostLinks } from './links';
import type { Post, Cat } from '../data/model';
import { ImageRepository } from '../data/ImageRepository';
import { enums } from '../data/model/Enums';
import { Dubai } from '../data/Neighborhoods';
import { dateTime } from '../i18n';
import { postHeadline } from '../ui/components/postText';
const label = (group: keyof typeof enums, key: string) =>
  t(enums[group].find((v) => v.key === key)?.label ?? 'unknown');
export function postSummary(p: Post) {
  return [
    p.name ? postHeadline(p) + ': ' + p.name : postHeadline(p),
    Dubai.nameOf(p.neighborhood) + (p.lastSeenAt ? ' · ' + dateTime(p.lastSeenAt) : ''),
    p.reward > 0 ? t('reward_aed', p.reward) : '',
    (p.features || p.description).slice(0, 280),
    t('share_post_footer', p.ownerName),
    PostLinks.create(p.id),
  ]
    .filter(Boolean)
    .join('\n');
}
export function catSummary(c: Cat) {
  return t(
    'share_cat_text',
    c.name,
    label('CoatColor', c.coatColor),
    label('CoatPattern', c.pattern),
    Dubai.nameOf(c.neighborhood),
    c.sightingCount,
    dateTime(c.firstSeen),
    dateTime(c.lastSeen),
    label('TnrStatus', c.tnrStatus),
  );
}
async function share(title: string, text: string, photo: string, url: string) {
  if (!navigator.share) {
    await navigator.clipboard.writeText(url);
    return t('web_link_copied');
  }
  const bytes = photo ? await ImageRepository.bytes(photo) : null;
  const files = bytes
    ? [new File([new Uint8Array(bytes)], 'catmap.jpg', { type: 'image/jpeg' })]
    : [];
  await navigator.share({
    title,
    text,
    ...(files.length && navigator.canShare?.({ files }) ? { files } : {}),
  });
  return '';
}
export async function sharePost(p: Post) {
  try {
    return await share(
      p.name,
      postSummary(p),
      p.photoIds[0] ?? '',
      globalThis.location.origin + '/post/' + p.id,
    );
  } catch (error) {
    if ((error as Error).name === 'AbortError') return '';
    throw error;
  }
}
export async function shareCat(c: Cat) {
  try {
    return await share(
      c.name,
      catSummary(c),
      c.coverPhotoId || c.photoIds[0] || '',
      globalThis.location.origin + '/cat/' + c.id,
    );
  } catch (error) {
    if ((error as Error).name === 'AbortError') return '';
    throw error;
  }
}
export function download(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
