export const config = {
  emulators: import.meta.env.VITE_USE_EMULATORS === 'true',
  blaze: import.meta.env.VITE_BLAZE === 'true',
  maptiler: import.meta.env.VITE_MAPTILER_KEY || '',
  hostUrl: (import.meta.env.VITE_PUBLIC_URL || '').replace(/\/$/, ''),
  githubRepo: import.meta.env.VITE_GITHUB_REPO || 'yneager/CatMap-Dubai',
  version: import.meta.env.VITE_APP_VERSION || 'development',
};
