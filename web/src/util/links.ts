import { config } from '../config';
const pattern = /^[A-Za-z0-9_-]{1,128}$/;
export const PostLinks = {
  validId: (id: string) => pattern.test(id),
  create: (id: string) => {
    if (!pattern.test(id)) throw new Error('Invalid post id');
    return config.hostUrl ? `${config.hostUrl}/post/${id}` : `catmap://post/${id}`;
  },
  parse: (value: string | null) => {
    if (!value) return null;
    const match = /^catmap:\/\/post\/([A-Za-z0-9_-]{1,128})$/.exec(value);
    if (match) return match[1];
    if (config.hostUrl) {
      const prefix = config.hostUrl + '/post/';
      if (value.startsWith(prefix) && pattern.test(value.slice(prefix.length)))
        return value.slice(prefix.length);
    }
    return null;
  },
};
export function safeRoute(route: string) {
  const value = route.replace(/^\//, '');
  return /^[A-Za-z][A-Za-z0-9-]*(?:\/[A-Za-z0-9_-]{1,256})?(?:\?[A-Za-z0-9_=&-]+)?$/.test(value)
    ? '/' + value
    : '/notifications';
}
