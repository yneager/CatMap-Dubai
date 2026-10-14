# CatMap Web

The iPhone/browser version of CatMap Dubai is a React/TypeScript PWA using Android's Firebase documents, security rules, strings and icons. It includes the phone layout, English/Arabic, themes, posts, maps, chats, adoption, sitting, care, admin tools, drafts, posters and account export/deletion.

This implementation is local and uncommitted. Nothing has been deployed. Review [the work report](../work%20done.txt) and [visual parity](docs/parity.md) before publishing. WebKit substitutes for an iPhone; a real iPhone and Android reading web records have not been tested.

## Local run

Use two PowerShell windows from `C:\Users\mohammad\Desktop\cat`. Reuse the emulators if they are already running. Do not export over the saved demo data.

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
npx.cmd -y firebase-tools@latest emulators:start --only auth,firestore,storage --project demo-catmap --import ./.emulator-data
```

```powershell
Set-Location C:\Users\mohammad\Desktop\cat\web
npm.cmd ci
npm.cmd run dev:emulators
```

Open `http://127.0.0.1:5173`. Both development commands force emulator mode, using `demo-catmap` by default, even if an owner configuration exists. The Emulator chip identifies the build. Tests create disposable accounts with random passwords and never use saved demo passwords. Saved emulator import data is not rewritten.

## Environment

Copy `.env.example` to `.env.local` yourself; keep actual values out of Git.

- `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_APP_ID`, `VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_STORAGE_BUCKET`: the Firebase **Web app** configuration. Do not copy Android's configuration file.
- `VITE_USE_EMULATORS`: keep `false` in `.env.local`. The dev commands and `--mode emulators` switch to the emulators on their own, and a normal production build refuses to run with it set to `true`. Local ports are Auth 9099, Firestore 8080 and optional Storage 9199, at `127.0.0.1`.
- `VITE_FIRESTORE_EMULATOR_PORT`: optional local test override; default 8080. The final isolated suite uses Firestore 8180, Auth 9098, Storage 9198 and project `demo-catmap-web-test`, with disposable accounts. These overrides have no effect outside emulator mode; see the full isolated-suite commands below.
- `VITE_MAPTILER_KEY`: optional satellite tiles; hides the control when blank.
- `VITE_BLAZE`: defaults to `false`. Enables Android-compatible Storage video, requiring the paid plan in production. Photos use Firestore blobs.
- `VITE_PUBLIC_URL`: optional public HTTPS origin for share links and poster QR codes. Without it the poster uses `catmap://post/<id>`.
- `VITE_GITHUB_REPO`: public release notes repository, default `yneager/CatMap-Dubai`; bundled notes are the offline fallback.

Missing real config without emulator mode shows the setup screen and creates no Firebase client. Username login matches Android: ordinary accounts use email unless admin tooling supplied `usernames/<name>.loginEmail`; demo usernames use their existing fixed email mapping.

## Build and checks

```powershell
npm.cmd run lint
npm.cmd run typecheck
npm.cmd run strings:check
npm.cmd run test
npm.cmd run build
npm.cmd run preview
```

Preview is `http://127.0.0.1:4173`. For a production build that is safe to test locally, use `npm.cmd run build -- --mode emulators`. Do not deploy that build.

With emulators running, `npm.cmd run test:e2e` checks Chromium and WebKit. `npm.cmd run screens` captures every route, six editor steps, sheets and Android component fixtures at 393×852 in WebKit, in light/dark/Arabic. The additional component fixtures use a 360px CSS width; native bitmap dimensions differ, as documented in the parity report. Report directories are excluded from automatic development reloads.

For production offline checks, first build with emulator mode:

```powershell
$env:CATMAP_PWA='true'
npx.cmd playwright test e2e/pwa.spec.ts --output=pwa-test-results --reporter=line
Remove-Item Env:CATMAP_PWA
```

The spec starts and stops only its own preview on port 4317. This avoids a [WebKit offline-emulation defect](https://github.com/microsoft/playwright/issues/42775). The regular flow separately tests the offline banner using `context.setOffline(true)`. Normal E2E runs skip this spec and screenshots unless explicitly enabled.

From the repository root, run shared generator checks:

```powershell
python tools/strings/build_strings.py . --check
python tools/icons/vector_to_svg.py . --check
```

Omit `--check` to regenerate. Existing Arabic and Android XML stay unchanged. Web-only Arabic entries intentionally repeat English with `# TODO-AR`. The model generator reads Kotlin declarations and needs no Android Firebase config.

## Owner publishing and iPhone installation

**One click:** double-click `web/publish.cmd`. It registers the "CatMap Web" app in Firebase (once), writes its public
config to `web/.env.local`, builds, and deploys Hosting only to https://catmap-dubai.web.app. Run it again to publish
changes. The manual steps below do the same thing.

After review, register a Web app in the existing Firebase project, copy its public config to `.env.local`, set emulator mode to `false`, and confirm Authentication → Settings → Authorized domains. Include `catmap-dubai.web.app`, `catmap-dubai.firebaseapp.com`, and any custom domain.

From `web`, build; from the repository root, deploy **Hosting only**:

```powershell
npm.cmd run build
Set-Location ..
npx.cmd -y firebase-tools@latest deploy --only hosting
```

Verify that the CLI targets `catmap-dubai` first. The implementation agent did not deploy. Rules, indexes and Android are unchanged. Hosting supplies SPA routing, caching, and a restrictive CSP with self-hosted scripts/fonts/workers. Inline styles and WebAssembly support the UI/map.

iPhone users open the HTTPS address in Safari, tap **Share → Add to Home Screen**, and launch CatMap from that icon. The app displays a one-time dismissible hint. HTTPS is required for location and installation outside localhost. New service-worker versions prompt the user to Reload.

Browser push and background polling are unavailable; inbox alerts and quiet-hour-aware toasts work while open. Android ML Kit labels/segmentation are unavailable; the browser uses the centre-weight descriptor and optional existing worker protocol. Tiles need a connection; the cached shell, documents, photos and drafts can work offline. Real worker integration, optional satellite/video and actual iOS install/share/location still require owner testing. Visual differences are listed in `docs/parity.md`.

## Isolated suite for repeatable checks

The final checks use a fresh demo project and task-owned services (Auth 9098, Firestore 8180, Storage 9198), independent of the normal demo. From the repository root:

```powershell
node web/tools/prepare-test-emulators.mjs
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS='-Xmx1024m'
npx.cmd -y firebase-tools@latest emulators:start --config web/e2e/firebase-isolated.json --only auth,firestore,storage --project demo-catmap-web-test
```

The preparation script copies unchanged rules/indexes byte-for-byte to ignored runtime fixtures, because Firebase CLI cannot load rules outside the configuration's directory. It validates the copy before startup. There is no seed, import or export.

In the web development window, set:

```powershell
$env:VITE_AUTH_EMULATOR_PORT='9098'
$env:VITE_FIRESTORE_EMULATOR_PORT='8180'
$env:VITE_STORAGE_EMULATOR_PORT='9198'
$env:VITE_EMULATOR_PROJECT_ID='demo-catmap-web-test'
npm.cmd run dev:emulators
```

In the browser test window, set:

```powershell
$env:CATMAP_AUTH_PORT='9098'
$env:CATMAP_FIRESTORE_PORT='8180'
$env:CATMAP_EMULATOR_PROJECT='demo-catmap-web-test'
npm.cmd run test:e2e
```

Use the same VITE overrides when building a production emulator candidate for the PWA spec. Omit these overrides for the normal `demo-catmap` suite. Stop only the test-owned suite afterwards. Additional environment variables `VITE_AUTH_EMULATOR_PORT`, `VITE_STORAGE_EMULATOR_PORT` and `VITE_EMULATOR_PROJECT_ID` default to 9099, 9199 and demo-catmap, respectively, and affect only emulator mode.
