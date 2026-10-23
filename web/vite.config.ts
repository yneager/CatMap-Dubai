import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { readFileSync } from 'node:fs';
import { optionalRouteAsset } from './src/offlinePolicy';
const packageVersion = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))
  .version as string;
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  if (command === 'build' && mode === 'production' && env.VITE_INSTALL_GATE === 'off')
    throw new Error(
      'VITE_INSTALL_GATE=off is forbidden in production. Use --mode emulators for local testing.',
    );
  // A production build that points at the local emulators would ship a site that talks to 127.0.0.1.
  // Local production-like builds use `--mode emulators` instead.
  if (command === 'build' && mode === 'production' && env.VITE_USE_EMULATORS === 'true') {
    throw new Error(
      'VITE_USE_EMULATORS=true in a production build. Set it to false in web/.env.local, or build with --mode emulators for local testing.',
    );
  }
  return {
    define: {
      'import.meta.env.VITE_APP_VERSION': JSON.stringify(packageVersion),
      ...(mode === 'emulators'
        ? { 'import.meta.env.VITE_USE_EMULATORS': JSON.stringify('true') }
        : {}),
      'import.meta.env.VITE_INSTALL_GATE': JSON.stringify(
        mode === 'emulators' && env.VITE_INSTALL_GATE !== 'on' ? 'off' : 'on',
      ),
    },
    optimizeDeps: { exclude: ['maplibre-gl'] },
    plugins: [
      react(),
      VitePWA({
        registerType: 'prompt',
        injectRegister: false,
        includeAssets: ['apple-touch-icon.png', 'favicon.png'],
        manifest: {
          name: 'CatMap Dubai',
          short_name: 'CatMap',
          description: 'Dubai’s animal community',
          lang: 'en',
          start_url: '/map',
          scope: '/',
          display: 'standalone',
          display_override: ['standalone'],
          id: '/',
          orientation: 'portrait',
          theme_color: '#A9531A',
          background_color: '#FFFBF7',
          icons: [
            { src: '/icon-192.png', sizes: '192x192', type: 'image/png' },
            { src: '/icon-512.png', sizes: '512x512', type: 'image/png' },
            {
              src: '/icon-maskable-512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable',
            },
          ],
        },
        workbox: {
          globPatterns: ['**/*.{js,mjs,css,html,png,svg,json,woff,woff2}'],
          // Core routes and Arabic fonts remain install-offline. Rare tools cache on first visit.
          globIgnores: ['**/AdminScreens-*.js', '**/PosterScreen-*.js'],
          maximumFileSizeToCacheInBytes: 3000000,
          navigateFallback: '/index.html',
          cleanupOutdatedCaches: true,
          runtimeCaching: [
            {
              urlPattern: optionalRouteAsset,
              handler: 'CacheFirst',
              options: {
                cacheName: 'optional-routes-v1',
                expiration: { maxEntries: 12, maxAgeSeconds: 30 * 86400 },
              },
            },
          ],
          navigateFallbackDenylist: [/^\/(?:__|v1)\//],
        },
      }),
    ],
    server: {
      port: 5173,
      strictPort: true,
      watch: {
        ignored: [
          '**/test-results/**',
          '**/screen-test-results/**',
          '**/pwa-test-results/**',
          '**/webkit-recheck/**',
          '**/chrome-audit/**',
          '**/playwright-report/**',
          '**/docs/**',
          '**/.preview-dist/**',
          '**/round2-results/**',
          '**/tests/**',
          '**/e2e/**',
        ],
      },
    },
    preview: { port: 4173, strictPort: true },
    build: {
      target: 'es2022',
      rollupOptions: {
        output: {
          manualChunks: (id: string) =>
            /node_modules[\\/](?:@firebase|firebase)[\\/]/.test(id)
              ? 'firebase'
              : /node_modules[\\/](?:react|react-dom|react-router|react-router-dom)[\\/]/.test(id)
                ? 'react'
                : undefined,
        },
      },
    },
  };
});
