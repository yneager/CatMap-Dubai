import { t } from '../i18n';
import { PostLinks } from './links';
import type { Post } from '../data/model';
export async function sharePost(p: Post) {
  const text = [p.name, p.description, PostLinks.create(p.id)].filter(Boolean).join('\n');
  try {
    if (navigator.share) {
      await navigator.share({ title: p.name, text });
      return '';
    }
    await navigator.clipboard.writeText(globalThis.location.origin + '/post/' + p.id);
    return t('web_link_copied');
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
