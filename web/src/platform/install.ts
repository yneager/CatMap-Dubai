export interface InstallPrompt extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}
export function isInstalled() {
  return (
    matchMedia('(display-mode: standalone)').matches ||
    matchMedia('(display-mode: fullscreen)').matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  );
}
let snapshot = { standalone: false, prompt: null as InstallPrompt | null, installed: false };
const listeners = new Set<() => void>();
const emit = (patch: Partial<typeof snapshot>) => {
  snapshot = { ...snapshot, ...patch };
  listeners.forEach((fn) => fn());
};
let started = false;
export function captureInstallEvents() {
  if (started) return;
  started = true;
  emit({ standalone: isInstalled() });
  window.addEventListener('beforeinstallprompt', (event) => {
    event.preventDefault();
    emit({ prompt: event as InstallPrompt });
  });
  window.addEventListener('appinstalled', () =>
    emit({ installed: true, prompt: null, standalone: isInstalled() }),
  );
  for (const mode of ['standalone', 'fullscreen'])
    matchMedia(`(display-mode: ${mode})`).addEventListener('change', () =>
      emit({ standalone: isInstalled() }),
    );
}
export const installation = {
  get: () => snapshot,
  subscribe: (fn: () => void) => {
    listeners.add(fn);
    return () => {
      listeners.delete(fn);
    };
  },
  prompt: async () => {
    const event = snapshot.prompt;
    if (!event) return;
    await event.prompt();
    await event.userChoice;
    emit({ prompt: null });
  },
};
export function installPlatform(ua = navigator.userAgent, touchPoints = navigator.maxTouchPoints) {
  const ipad = /iPad/.test(ua) || (/Macintosh/.test(ua) && touchPoints > 1);
  const ios = ipad || /iPhone|iPod/.test(ua);
  const genericWebview =
    /Mobile\//.test(ua) && !/Safari\/|CriOS\/|FxiOS\/|EdgiOS\/|OPiOS\//.test(ua);
  const inApp =
    ios &&
    (genericWebview ||
      /FBAN|FBAV|Instagram|WhatsApp|TikTok|Bytedance|GSA\/|Gmail|\bLine\/|MicroMessenger|Snapchat|Twitter|LinkedInApp/i.test(
        ua,
      ));
  const version = Math.max(
    Number(ua.match(/(?:CPU(?: iPhone)? OS|iPhone OS) (\d+)/)?.[1] ?? 0),
    Number(ua.match(/Version\/(\d+)/)?.[1] ?? 0),
  );
  return { ios, ipad, inApp, ios26: ios && version >= 26, android: /Android/.test(ua) };
}
export const installGateEnabled = import.meta.env.VITE_INSTALL_GATE === 'on';
