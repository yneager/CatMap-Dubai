# CatMap Dubai – handoff

Read this first in a new chat. It covers the current state, how to build and test, the decisions already made,
and what's left. **Last updated: 12 Oct 2026 (version 2.0.0).**

## What this is

CatMap Dubai is a native Android app (Kotlin + Jetpack Compose) for Dubai's animal community. Version 1 covered
street cats: sightings with on-device AI, cat profiles, rescue, Ehsan feeding stations, badges and admin.
**Version 2.0** implements the owner's "Major App Upgrade" spec (13 sections):

* New maps.
* Lost/found/spotted/needs-help/adoption posts for any animal, with smart filters.
* Adoption and a pet-sitting marketplace.
* Messaging and alerts.
* Five demo accounts with an admin dashboard.
* Modern UI with EN/AR.
* Sharing and posters.
* Security and indexes.
* Signed GitHub-release updates.

Feature-to-code map: `README.md`. Firebase, Blaze, MapTiler, GitHub releases and the local demo: `SETUP.md`.

**Owner decisions (asked 12 Oct):**

* Free Spark plan only. Videos and instant push are built but only switched on with `catmap.blaze=true`.
* Demo login is username + password. Firebase needs at least 6 characters, so the short demo passwords from the spec are padded.
* Maps are OpenFreeMap, with MapTiler for satellite (key optional).
* "Code + keystore, no push yet." **Never push to GitHub without the owner giving the repo name and approving.**

## State at the end of 12 Oct 2026

* **Build:** debug, staging and a **signed** release all build. Lint (debug and release): **0 errors**.
* **Tests:**
  * 35 Android unit tests pass.
  * The Firestore rules suite passes (**190 checks**).
  * The push-function test passes (6 checks).
* **Strings:** 1065 per language (no missing or unused keys).
* **Release APK:** `app/build/outputs/apk/release/app-release.apk`, about 22 MB, ARM only, signed with the new
  release key (SHA-256 `25:6D:F9:05:…:CD:F4:28:87`, verified with apksigner).
* **Git:** pushed to the public repo **https://github.com/yneager/CatMap-Dubai** (branch `main`) on 12 Oct 2026, with the owner's approval.
  * Commits use the repo-local identity `mohammad <mohammed.duae@gmail.com>` (`git config` in this repo only).
  * A scan of all 160 committed files for every local password found **none**. `.gitattributes` keeps `gradlew` and `*.sh` LF for the Linux runners.
  * `.gitignore` covers `keystore.properties`, `release-keys/`, `*.jks`, `app/google-services.json`, `local.properties`, `test-accounts.local.txt`, `tools/seed/demo-accounts.local.json`, `.emulator-data/` and `node_modules`.
* **Not done (owner setup):**
  * The real Firebase project. `google-services.json` is still missing, so normal builds show "Firebase setup required".
  * The GitHub repo **secrets** (SETUP.md §9). The repo exists; no `v*` tag has been pushed, because a tag starts the release build, which fails without the secrets.
  * Blaze (optional).
  * A MapTiler key (optional).
  * Exact steps are in SETUP.md §1–9.

### Release signing (important)

* `release-keys/catmap-release.jks` (PKCS12, alias `catmap`, RSA 4096, valid 10 000 days) with the passwords in
  `keystore.properties`. Both are git-ignored. **The owner must back them up.** Every future update must be signed with this key.
* `bash tools/release/github-secrets.sh` writes the 4 secret values to `release-keys/github-secrets.txt` (git-ignored)
  for pasting into GitHub. The file exists now; the owner should delete it after adding the secrets.
* **Never print these passwords or the demo/test passwords in chat.**

### CI / release workflows (`.github/workflows/`)

* `release.yml`: runs on tags `v*.*.*`.
  1. Checks that the tag is a version.
  2. Restores the keystore from secrets and fails if any are missing, so an unsigned APK never ships.
  3. Writes `google-services.json` from the `GOOGLE_SERVICES_JSON` secret.
  4. Runs `testDebugUnitTest assembleRelease` with `-Pcatmap.versionName=<tag> -Pcatmap.githubRepo=$GITHUB_REPOSITORY`.
  5. Verifies the signature and creates or updates the GitHub Release with `CatMap-<version>.apk`.
* `ci.yml`: unit tests, lint and a debug build, plus the rules suite through `firebase emulators:exec`.
* `ci.yml` ran on GitHub for the first push (see "CI on GitHub" below). `release.yml` has not run yet: it needs the secrets and a tag.
* **The updater needs a public repo** (it calls `api.github.com/repos/<owner>/<repo>/releases/latest` without a login). It picks the first `.apk` asset. The version comes from `tag_name` minus `v`, and the release body is shown as the notes.

## Verified live on emulator-5554 (12 Oct, against the Firebase emulators)

Debug build with `-Pcatmap.emulatorHost=10.0.2.2`:

* **Maps:** Modern light/dark (tilt plus 3D buildings), Classic, clustering with tap-to-zoom, filter chips, the layers/filter sheet (type, distance, date, reward, map layers), search with a result card, and Arabic RTL.
* **Accounts:** username login (admin and members).
* **Lost post:** the full 6-step wizard (photo, details, pin plus a 1.7 km search area, reward), then the detail screen.
* **Owner actions:** edit, delete (gone from Firestore and My posts), and "I saw this animal" update (the owner is notified).
* **Poster:** generated and shareable; Arabic layout checked.
* **Admin:** stats, and suspend/restore a member (moderation log written).
* **Messaging:** chat with a post link, a deduped `msg_` alert, and block, then unblock.
* **Favourites.**
* **Adoption:** request, then approve, and the listing becomes Pending.
* **Sitting:** booking request, then accept, then complete, then a review (rating recalculated).
* **Alerts:** a lost-nearby alert reaches a follower of the area (`lost_<postId>`).
* **Updates:** Check for updates shows "not configured" (no repo is set in debug).

**Staging build** (R8 shrunk like release, debug-signed, against the emulators):

* Login, map, Explore, Messages, chat, Profile → My posts / Adoption requests / Bookings / Sitter profile / Lost-pet alerts, and the Post chooser all work.
* No crashes and no reflection errors.
* Cold start is **2.3–2.9 s**. The debug build takes 12–15 s; it's debuggable and has no AOT compilation, so ignore that number.

**Push function:** tested against the Firestore emulator with FCM stubbed. It sends to the stored tokens, includes no content in the push, removes tokens FCM reports as dead, and does nothing when there are no tokens. It has **not** been deployed, which needs Blaze.

### Bugs found and fixed in the final pass (12 Oct)

1. **Crash on cold start.** `Calling getSourceAs when a newer style is loading`: a map style reload at startup (the settings flow emits defaults, then the saved value) left `holder.style` pointing at the replaced style. `MapHolder.style` now returns only a fully loaded style and is cleared before `setStyle`.
2. **Poster.** The header subtitle overlapped the headline, Arabic body lines were on the wrong side, and the footer's word order was scrambled. Text direction is now explicit: RTL for body text in Arabic, first-strong for centred lines.
3. **Bidi.** An English description in the Arabic UI put the full stop at the wrong end. Body text styles now use `TextDirection.Content`.
4. **Owner label.** The owner of a Lost post saw "I saw this animal"; it now says "Add update".
5. **"All animals" chip** stayed selected while an animal-type filter was on. It now reflects and clears that filter too (Map and Explore).
6. **Blocked chat** still showed the "sharing a post" strip. It is now hidden.
7. **Lint errors.** Fixed 5 non-observable locale/resource reads in composables (new `currentLocale()` helper) and the always-true conditions.
8. **Release APK size** dropped from 97 MB to 22 MB: ARM ABIs only for release, plus compressed native libraries.

## Remaining limits / not tested

* **Nothing has run against a real Firebase project**, including Auth emails, index builds, quotas, Storage and FCM. Do this after SETUP.md §1–6.
* **GitHub Actions** have not run, and nothing in-app has been tested against a real GitHub release. The download/install path (FileProvider + package installer) is implemented, but only "not configured" was exercised. Test it with the first real tag: install v2.0.0, tag v2.0.1, then Settings → Check for updates.
* **Real phone with the new build.** The phone (R5CW131KBSY) was not connected for this pass. To install it, see SETUP.md "Local demo" step 3: `adb reverse` for ports 9099/8080/9199 and `-Pcatmap.emulatorHost=127.0.0.1`.
* **Video upload** works only with Storage: the emulator always, or real Firebase with Blaze. The video UI was built but not re-driven in this final pass.
* **Instant push** depends on Blaze plus the deployed function. Without it, delivery is live while the app is open plus a background check about every 15 minutes.
* **Lint warnings left** (intentional or optional): target SDK 36 and the pinned Firebase BOM, `Log` vs Timber, plural-candidate strings, the lambda offset overload, and a few KTX suggestions.
* **Older v1 limits still apply**, including:
  * Client-managed points/badges.
  * Non-atomic account deletion.
  * Image cache not revoked in real time after moderation.
  * TalkBack checked only through the accessibility tree.

## Toolchain (versions matter – don't downgrade)

* **Android Studio** 2026.2.1. JDK: its JBR at `C:\Program Files\Android\Android Studio\jbr`. Java is **not** on PATH, so set `JAVA_HOME`.
* **Gradle and Kotlin:** Gradle 9.8.1 wrapper, and AGP 9.4.1 with built-in Kotlin 2.4.21 (no `kotlin-android` plugin).
* **SDK levels:** compileSdk 37, targetSdk 36, minSdk 26.
* **Compose and AndroidX:** Compose BOM 2026.09.00 (Material3 1.4), Navigation 2.10.2, lifecycle 2.11.0, WorkManager 2.12.0, DataStore 1.2.1.
* **Firebase:** BOM 34.19.0, with auth, firestore, storage and messaging (FCM 25 uses `onRegistered`, not `onNewToken`).
* **Map and AI:** MapLibre Android 13.6.1 (osmdroid removed), ML Kit labelling and subject segmentation.
* **Functions:** Node 22, firebase-functions 7.4, firebase-admin 14.5.

```bash
cd /c/Users/mohammad/Desktop/cat
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
python tools/strings/build_strings.py .
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleRelease
ANDROID_SERIAL=emulator-5554 ./gradlew.bat :app:installDebug -Pcatmap.emulatorHost=10.0.2.2     # or :app:installStaging
```

Gradle properties are read from `-P…` or from `local.properties`:

| Property | What it sets |
| --- | --- |
| `catmap.emulatorHost` | Points debug and staging builds at the local emulators |
| `catmap.githubRepo` | `owner/repo`, the repository the updater checks |
| `catmap.maptilerKey` | Key for the satellite layer |
| `catmap.blaze` | `true` turns on Storage videos and FCM |
| `catmap.versionName` | Version in `a.b.c` form; versionCode = a·10000 + b·100 + c |

## Architecture in one screen

Package `com.catmap.dubai`, no DI framework.

* `CatMapApp`: MapLibre init, notification channels, the WorkManager poll, live inbox delivery, and push registration (only when Blaze is on).
* `AppContainer`: repositories plus `Session`, `Connectivity` and `Updater`.
* `data/`: one repository per area.
  * **v2:** Post, Adoption, Sitting, Chat, Favorites, Admin, Video.
  * **v1:** Auth, User, Cat, Rescue, Station, Moderation, Notification, Image, Settings.
  * Models are in `data/model/Community.kt` (v2) and `Models.kt`.
* `ui/map/`:
  * `CommunityMap.kt`: MapLibre `MapView` in Compose; GeoJSON clustering; symbol markers from `MarkerFactory` emoji bitmaps; a pulsing circle layer; area circle; LocationComponent; `MapController`; and `MapMemory` for the camera across screens.
  * `MapStyles.kt`: OpenFreeMap liberty/dark plus a runtime "Modern" recolour.
  * `MiniMap.kt`: static snapshot.
  * `LocationPickerScreen.kt`: pin plus radius.
* `ui/posts` (editor wizard, detail, lists, poster), `ui/explore` (feed, `Filtering.kt`, search), `ui/adoption`, `ui/sitting`, `ui/messages`, `ui/admin` (+ `AdminCommunityScreens.kt`).
* `update/Updater.kt` + `UpdateUi.kt`: GitHub releases check (every 12 h or manually), download with HTTPS redirects, install through FileProvider.
* `notifications/`: inbox to system notifications, a background poll, and `PushService` (FCM).
* `functions/index.js`: `pushOnNotification` (Firestore trigger on `users/{uid}/notifications/{id}`).

### Key design decisions (keep these)

* **No google-services plugin.** `app/build.gradle.kts` parses `google-services.json` itself. If it's missing, the app shows the setup screen.
* **Photos are stored as Firestore blobs** (`images/{id}`, `{id}_t`) so the free plan works. Videos go to Storage `videos/{uid}/{id}`, only when `STORAGE_ENABLED` is set.
* **Security lives in `firestore.rules`:**
  * Roles: `verified()` includes `!suspended()`.
  * Rate limit: `lastPostAt == request.time`.
  * Admin-only actions are checked server-side.
  * `usernames/{name}` may only hold `uid` from clients, so `loginEmail` (username login) can only be set by admin tooling.
  * Single-doc reads use a get/list split with `resource == null` allowed, so a listener on a missing doc reports "doesn't exist" rather than dying. This was the cause of the earlier adoption-request PERMISSION_DENIED.
* **Alert de-duplication:** deterministic notification ids (`msg_{chatId}`, `lost_{postId}`, `match_{postId}`, `upd_{postId}`), create-only.
* **Demo isolation:** `tools/seed/seed.mjs` refuses any host that isn't localhost or any project not starting with `demo-`. The emulator overrides apply only to debug/staging (`useEmulators()` in the Gradle file), so release can never get fake config.
* **Map style safety:** `MapHolder.style` returns only a fully loaded style (see bug 1 above). Always go through it.
* **Text direction:** body styles use `TextDirection.Content`. Poster text sets direction explicitly.
* **Strings:** edit `tools/strings/strings_[a-h].py`, then run `python tools/strings/build_strings.py .`. Never hand-edit the XML.
* **Detail screens use `Flow.loaded()`**, so missing documents show "removed" instead of an endless spinner.

## Local testing

* **Firebase emulators:** `npx -y firebase-tools@latest emulators:start --only auth,firestore,storage --project demo-catmap --import ./.emulator-data`.
  * Add `--export-on-exit` only when you mean to save changes.
  * The emulators were **left running** at the end of this pass, without export, so `.emulator-data` holds the clean seeded state. Today's test posts (Storm, a sighting update, and so on) disappear when they stop.
  * A backup of the pre-2.0 data is at `build/emulator-data-backup-before-2.0`.
* **Demo accounts:** `admin`, `ali`, `mohammad`, `anirudh`, `hassan`. Passwords are in `tools/seed/demo-accounts.local.json` (git-ignored; never paste them into chat). The v1 test admin `layla` is listed in `test-accounts.local.txt`.
* **Driving the emulator:** `bash build/login.sh <user> <password>` (set `PKG=com.catmap.dubai.staging` for staging) runs `pm clear`, grants notification permission and logs in.
  * `tools/adb.sh` takes `shot NAME`, `ui`, `tap X Y`, `text STR`, `key CODE` and `swipe`. Read coordinates from a fresh `ui` dump.
  * **Don't send Back presses blindly.** Leaving the app lands on the launcher, and stray taps there open Google account screens. Never enter anything into those.
* **Emulator app state:**
  * The debug app is signed in as `anirudh`, in **Arabic + dark**.
  * The staging app (`com.catmap.dubai.staging`) is also installed, signed in as `mohammad`.
  * Install over the running app, wait about 10 s, then start it. Starting it during the OS's "package update" screen gives a harmless "no focused window" ANR.
* **Emulator REST:** use the header `Authorization: Bearer owner` against `http://127.0.0.1:8080/v1/projects/demo-catmap/databases/(default)/documents/...`. Verification links are at `http://127.0.0.1:9099/emulator/v1/projects/demo-catmap/oobCodes`.
* **Rules suite:** `firebase emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"`. It uses port 8180, so the demo data is untouched.
* **Function test:** `FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 GCLOUD_PROJECT=demo-catmap npm --prefix functions test`.

## File map of non-code items

| Path | Purpose |
|---|---|
| `README.md`, `SETUP.md` | Feature map; full setup incl. Blaze, MapTiler, GitHub releases, local demo |
| `firestore.rules`, `storage.rules`, `firestore.indexes.json`, `firebase.json` | Backend rules, 4 composite indexes, emulator/functions config |
| `functions/` | Push Cloud Function + its test |
| `.github/workflows/` | `release.yml` (signed APK on tags), `ci.yml` |
| `release-keys/`, `keystore.properties` | **Release signing key and passwords (git-ignored, back up!)** |
| `tools/release/github-secrets.sh` | Prepares the GitHub secret values |
| `tools/seed/` | Demo seed (local emulators only), demo photos, example accounts file |
| `tools/strings/` | String tables + generator |
| `tools/rulestest/` | Firestore rules tests |
| `tools/adb.sh`, `build/login.sh`, `build/shots/` | Emulator driving helpers and screenshots |
| `.emulator-data/` | Saved emulator state (git-ignored) |
| `test-accounts.local.txt` | Test credentials (git-ignored, keep out of chat) |

## Next steps for the owner

1. Back up `release-keys/` and `keystore.properties`.
2. Create the Firebase project (SETUP.md §1–6), then test the signed release with real accounts.
3. Add the four signing secrets plus `GOOGLE_SERVICES_JSON` to https://github.com/yneager/CatMap-Dubai (SETUP.md §9), then push tag `v2.0.0`. Ask the owner before pushing any tag or further commits.
4. Optional: Blaze (videos and push) and a MapTiler key.
