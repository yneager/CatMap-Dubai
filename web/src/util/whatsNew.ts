import { config } from '../config';
import { SettingsRepository } from '../data/SettingsRepository';
import bundled from '../whats-new.json';
export async function getReleaseNotes() {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 4000);
    try {
      const r = await fetch(`https://api.github.com/repos/${config.githubRepo}/releases/latest`, {
        signal: controller.signal,
      });
      if (!r.ok) throw new Error('Release unavailable');
      const release = (await r.json()) as { body?: string };
      if (release.body) {
        await SettingsRepository.set({
          releaseNotes: release.body.slice(0, 50000),
          releaseNotesVersion: config.version,
        });
        return release.body;
      }
    } finally {
      clearTimeout(timer);
    }
  } catch {
    /* Bundled notes work offline. */
  }
  return bundled.notes;
}
