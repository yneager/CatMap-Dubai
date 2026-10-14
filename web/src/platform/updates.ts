import { registerSW } from 'virtual:pwa-register';
let registration: ServiceWorkerRegistration | undefined;
let refresh = false;
let reload: ((reload?: boolean) => Promise<void>) | undefined;
const listeners = new Set<() => void>();
export const updates = {
  get: () => refresh,
  subscribe: (fn: () => void) => {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },
  reload: () => reload?.(true),
  check: async () => {
    const worker = registration ?? (await navigator.serviceWorker?.getRegistration());
    if (worker) await worker.update();
  },
};
export function startUpdates() {
  if (reload) return;
  reload = registerSW({
    immediate: true,
    onRegisteredSW: (_url, worker) => {
      registration = worker;
    },
    onNeedRefresh: () => {
      refresh = true;
      listeners.forEach((fn) => fn());
    },
    onRegisterError: () => {},
  });
}
