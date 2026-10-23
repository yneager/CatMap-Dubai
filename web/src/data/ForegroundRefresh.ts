import { useSyncExternalStore } from 'react';
let version = 0;
const listeners = new Set<() => void>();
export const refreshForeground = () => {
  version++;
  listeners.forEach((fn) => fn());
};
if (typeof window !== 'undefined') {
  window.addEventListener('focus', refreshForeground);
  window.addEventListener('online', refreshForeground);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) refreshForeground();
  });
}
export function useForegroundRefresh() {
  return useSyncExternalStore(
    (fn) => {
      listeners.add(fn);
      return () => {
        listeners.delete(fn);
      };
    },
    () => version,
  );
}
