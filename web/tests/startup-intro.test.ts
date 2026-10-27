import { beforeEach, expect, it, vi } from 'vitest';
import { launchStory, pickStory, startupStories } from '../src/platform/StartupIntro';

let session: Map<string, string>;
let reduced = false;
beforeEach(() => {
  session = new Map();
  reduced = false;
  vi.stubGlobal('sessionStorage', {
    getItem: (key: string) => session.get(key) ?? null,
    setItem: (key: string, value: string) => session.set(key, value),
  });
  vi.stubGlobal('matchMedia', () => ({ matches: reduced }));
});

it('can draw each of the four stories, evenly', () => {
  expect(startupStories).toEqual([
    'original',
    'rooftop-rescue',
    'neighborhood-relay',
    'night-patrol',
  ]);
  expect([0, 0.25, 0.5, 0.75].map((r) => pickStory(() => r))).toEqual(startupStories);
  expect(pickStory(() => 0.999999)).toBe('night-patrol');
  expect(pickStory(() => 1)).toBe('night-patrol');
});

it('plays once per launch: not again after a reload in the same session', () => {
  vi.spyOn(Math, 'random').mockReturnValue(0.6);
  expect(launchStory()).toBe('neighborhood-relay');
  expect(launchStory()).toBeNull();
  // A fresh launch (new session) draws again.
  session.clear();
  vi.spyOn(Math, 'random').mockReturnValue(0.1);
  expect(launchStory()).toBe('original');
});

it('skips the story when the system asks for reduced motion', () => {
  reduced = true;
  expect(launchStory()).toBeNull();
});
