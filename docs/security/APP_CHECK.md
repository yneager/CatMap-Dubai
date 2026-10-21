# App Check: monitoring only (Phase 9, 21 Oct 2026)

**What it is:** Firebase App Check lets Firestore tell requests from the real CatMap app apart from requests made by a
script with the (public) API key. Each request carries a short-lived App Check token, which the app gets by proving
it's genuine: on Android through Google's **Play Integrity**, on the web through **reCAPTCHA Enterprise**.

**What CatMap does now:** the Android app asks for App Check tokens (from 0.1.x). Firestore is **not** set to require
them, so nothing is refused. The Firebase console then shows how many requests are verified, which tells the owner
whether enforcing would be safe. The web app gets the same in the Codex brief (`CLAUDE_TO_CODEX.txt`).

## Where it is in the code (Android)

* `CatMapApp.onCreate` installs App Check first, before any Firebase call, unless the build talks to the local
  emulators (they don't check it).
* `AppCheckSetup` is per build type: `src/release/java/...` uses Play Integrity; `src/debug/java/...` and
  `src/staging/java/...` use the debug provider. The debug library isn't in release builds at all.
* Libraries: `firebase-appcheck-playintegrity` (all builds), `firebase-appcheck-debug` (debug and staging only).

## The GitHub-APK risk (why it isn't enforced)

CatMap is installed from **GitHub Releases**, not Google Play. Play Integrity's verdict says whether the app is "the
one Google Play knows". An APK installed from GitHub is not recognised by Play, so its App Check token request may be
refused, and **if Firestore enforced App Check, every GitHub install would be locked out** (and old phones without
Google Play services too). So:

* Enforcement stays **off**.
* Only switch it on if the console shows that real installs pass (nearly all requests "verified" for a couple of
  weeks after the release), or after the app is published on Google Play and most people install from there.
* Play Integrity's standard limit is 10,000 checks a day, free. App Check tokens last an hour, so a phone asks a few
  times a day at most.

## Owner steps in the Firebase console (about 10 minutes, after the release)

1. Firebase console → project **catmap-dubai** → **Build** → **App Check** → **Get started**.
2. **Apps** tab → the Android app (`com.catmap.dubai`) → **Play Integrity** → **Register**.
   * It asks for the app's **SHA-256 certificate fingerprint**: use the release certificate
     `25:6D:F9:05:6B:4A:60:0C:05:39:CE:37:03:3E:47:5E:5D:6F:BA:C4:0F:E8:F9:B5:BE:AC:EB:98:CD:F4:28:87`
     (the same one `release.yml` checks).
   * Play Integrity may also need the Google Play Console linked to the project. CatMap isn't on Play, so this may
     not be possible; that's the GitHub-APK risk above, and monitoring still shows the result.
3. **APIs** tab → **Cloud Firestore**: leave it on **Unenforced** (monitoring). Don't click "Enforce".
4. After a week or two, open the same tab: the chart shows **verified**, **unverified: outdated client** (phones on
   v0.0.9 and other versions without App Check) and **unverified: invalid** requests. Tell Claude the numbers before
   deciding anything.
5. **Optional, for testing a debug or staging build against production:** start that build, find the line
   `Enter this debug secret into the allow list in the Firebase Console` in logcat (tag `DebugAppCheckProvider`),
   and add the value under **Apps** → the Android app → **Manage debug tokens**. Never share that token; it makes a
   device count as genuine.

**Web (Codex brief):** register the web app with **reCAPTCHA Enterprise** in the same App Check page; the site key
goes in `web/.env.local` (not committed). Also monitoring only. The emulator build must not initialise App Check.
