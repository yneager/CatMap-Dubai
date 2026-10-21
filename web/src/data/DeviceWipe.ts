import * as idb from '../util/idb';
import { SettingsRepository, settingsDefaults } from './SettingsRepository';
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
  sessionStorage.clear();
  if (typeof caches !== 'undefined')
    await Promise.all(
      (await caches.keys())
        .filter((key) => !key.includes('precache'))
        .map((key) => caches.delete(key)),
    );
}
