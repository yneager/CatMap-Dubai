import * as idb from '../util/idb';
import { SettingsRepository, settingsDefaults } from './SettingsRepository';
import { INTRO_SEEN_KEY } from '../platform/StartupIntro';
export async function clearDeviceData() {
  const saved =
    (await idb.get<Partial<typeof settingsDefaults>>('settings')) ?? SettingsRepository.get();
  await idb.clearAccountData();
  const { language, theme, largeText, installHintDismissed } = saved;
  await SettingsRepository.set({
    ...structuredClone(settingsDefaults),
    language,
    theme,
    largeText,
    installHintDismissed,
  });
  for (const key of Object.keys(localStorage))
    if (/^(area_follow_private_|stale_check_|catmap.*(?:account|draft|delivered|map))/.test(key))
      localStorage.removeItem(key);
  // The startup story flag holds no account data, and keeping it stops the logout reload replaying the story.
  const introSeen = sessionStorage.getItem(INTRO_SEEN_KEY);
  sessionStorage.clear();
  if (introSeen) sessionStorage.setItem(INTRO_SEEN_KEY, introSeen);
  if (typeof caches !== 'undefined')
    await Promise.all(
      (await caches.keys())
        .filter((key) => !key.includes('precache'))
        .map((key) => caches.delete(key)),
    );
}
