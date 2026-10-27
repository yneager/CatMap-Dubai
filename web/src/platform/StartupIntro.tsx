import { useCallback, useEffect, useRef } from 'react';
import { t } from '../i18n';

import originalUrl from '../assets/startup/original.json?url';
import originalDarkUrl from '../assets/startup/original-dark.json?url';
import rooftopUrl from '../assets/startup/rooftop-rescue.json?url';
import rooftopDarkUrl from '../assets/startup/rooftop-rescue-dark.json?url';
import relayUrl from '../assets/startup/neighborhood-relay.json?url';
import relayDarkUrl from '../assets/startup/neighborhood-relay-dark.json?url';
import patrolUrl from '../assets/startup/night-patrol.json?url';
import patrolDarkUrl from '../assets/startup/night-patrol-dark.json?url';

// The same five-second Lottie stories as Android (app/src/main/res/raw/startup_*.json). They are separate files
// (precached for offline launches), so a launch reads only the story it plays.
const stories = {
  original: { light: originalUrl, dark: originalDarkUrl },
  'rooftop-rescue': { light: rooftopUrl, dark: rooftopDarkUrl },
  'neighborhood-relay': { light: relayUrl, dark: relayDarkUrl },
  'night-patrol': { light: patrolUrl, dark: patrolDarkUrl },
} as const;
export type StartupStory = keyof typeof stories;
export const startupStories = Object.keys(stories) as StartupStory[];

/** Kept through the logout wipe (DeviceWipe.ts): a reload in the same session is not a new launch. */
export const INTRO_SEEN_KEY = 'catmap_intro_seen';
/** How long the player and story may take to load before the app is shown without them. */
const LOAD_TIMEOUT_MS = 2000;
/** Never hold the app longer than this, whatever happens to playback. */
const DEADLINE_MS = 7000;

export function pickStory(random = Math.random): StartupStory {
  return startupStories[
    Math.min(startupStories.length - 1, Math.floor(random() * startupStories.length))
  ];
}

/**
 * The story for this page load, drawn once: none after a reload in the same session (logout, an update), or when
 * the system asks for reduced motion.
 */
export function launchStory(): StartupStory | null {
  try {
    if (sessionStorage.getItem(INTRO_SEEN_KEY)) return null;
    sessionStorage.setItem(INTRO_SEEN_KEY, '1');
  } catch {
    // Storage blocked: play it; there is nothing to remember it with.
  }
  return matchMedia('(prefers-reduced-motion: reduce)').matches ? null : pickStory();
}

/**
 * Plays [story] once above the app, which keeps starting underneath, then calls onDone exactly once. A tap skips it;
 * a load failure or a slow load ends it. Switching theme mid-way swaps the file and keeps its place.
 */
export function StartupIntro({
  story,
  dark,
  onDone,
}: {
  story: StartupStory;
  dark: boolean;
  onDone: () => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const done = useRef(false);
  const startedAt = useRef<number | null>(null);
  const latestDone = useRef(onDone);
  latestDone.current = onDone;
  const finish = useCallback(() => {
    if (done.current) return;
    done.current = true;
    latestDone.current();
  }, []);
  useEffect(() => {
    const deadline = window.setTimeout(finish, DEADLINE_MS);
    return () => window.clearTimeout(deadline);
  }, [finish]);
  useEffect(() => {
    let cancelled = false;
    let animation: { destroy(): void } | null = null;
    const slow =
      startedAt.current === null ? window.setTimeout(finish, LOAD_TIMEOUT_MS) : undefined;
    void Promise.all([
      import('lottie-web/build/player/lottie_light'),
      fetch(stories[story][dark ? 'dark' : 'light']).then((response) => {
        if (!response.ok) throw new Error('startup story ' + response.status);
        return response.json() as Promise<object>;
      }),
    ])
      .then(([player, data]) => {
        if (cancelled || done.current || !container.current) return;
        window.clearTimeout(slow);
        const anim = player.default.loadAnimation({
          container: container.current,
          renderer: 'svg',
          loop: false,
          autoplay: false,
          animationData: data,
          rendererSettings: { preserveAspectRatio: 'xMidYMid meet' },
        });
        animation = anim;
        anim.addEventListener('complete', finish);
        anim.addEventListener('data_failed', finish);
        const now = performance.now();
        if (startedAt.current === null) {
          startedAt.current = now;
          anim.play();
        } else {
          const frame = ((now - startedAt.current) / 1000) * anim.frameRate;
          if (frame >= anim.totalFrames) finish();
          else anim.goToAndPlay(frame, true);
        }
      })
      .catch(finish);
    return () => {
      cancelled = true;
      window.clearTimeout(slow);
      animation?.destroy();
    };
  }, [story, dark, finish]);
  return (
    <div
      className={'startup-intro' + (dark ? ' dark' : '')}
      role="img"
      aria-label={t('app_name')}
      data-story={story}
      onClick={finish}
    >
      <div ref={container} aria-hidden="true" />
    </div>
  );
}
