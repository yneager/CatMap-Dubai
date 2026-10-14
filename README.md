# CatMap Dubai

A community app for Dubai's animals: lost, found and spotted pets of any kind, animals that need help,
street cats, adoption, and a pet-sitting marketplace. It has a live vector map, messaging, alerts for
lost animals nearby, on-device AI for cat photos, Ehsan feeding stations, an admin dashboard, and full
English/Arabic support (RTL). New strings from the 13 October work temporarily use English in Arabic;
their `# TODO-AR` entries await the owner's translation pass. Updates are delivered from GitHub Releases.

## Improvements added on 13 October 2026

* Original CatMap vector icons, shared screen components and readable Explore status-bar insets.
  [Preview all 60 icons](docs/icons-preview.png).
* Post drafts save per account, including cached photos; continue or discard after restarting.
* Lost/found reminders after 14 inactive days, with Still looking / Mark reunited actions. Open posts
  inactive for 90 days are hidden by default; **Include older posts** restores them.
* Photo-assisted possible matches use a small on-device colour/layout fingerprint, distance and time.
  Older posts without fingerprints use a clearly labelled type/location/time comparison.
* A microchip yes/no/unknown field in posts and filters. Chip numbers are never requested or stored publicly.
* Map/List toggle with the same filtered points, sorted by GPS distance or the map center when GPS is absent.
* Help directory from Explore or login, maintained by admins; verified sitter badges and a Verified only filter.
* Settings → **Download my data** shares the signed-in account's JSON records through Android's share sheet.
  It includes posts, authored updates, chats, requests, sitter profile, alert subscriptions and private own
  locations, with photo IDs instead of image bytes. Auth credentials and push tokens are excluded.
* Settings → **Quiet hours** uses device time, including overnight windows. Inbox alerts remain available;
  suppressed system notifications are not replayed later.
* Admin weekly post/member charts and an eight-week lost/found reunion rate, using Firestore count queries.
* `catmap://post/<id>` links in shared text and poster QR codes. The app must be installed to open these links.
* Release notes once after upgrading; optional SHA-256 release sidecars plus package, version and signing checks.
* Shared feed listeners, thumbnail/target-size decoding, memory/disk caching and a device-generated baseline profile.

Implementation, verification boundaries, new fields/indexes and remaining owner work are in
[the dated handoff](HANDOFF.md). No production deployment or release was made for this round.

**First time? Follow [SETUP.md](SETUP.md).** It takes about 15 minutes: create a free Firebase project,
add `google-services.json`, publish the rules, then press Run. To try it with no Firebase project at all,
use the local emulators and the demo accounts ([SETUP.md → Local demo](SETUP.md#local-demo-no-firebase-project-needed)).

## Where each feature lives

| Area | In the app | Code |
| --- | --- | --- |
| Web app | iPhone Safari/Home Screen and browser PWA with shared Firebase data, rules, strings and icons. Local implementation and owner publishing instructions | [`web/README.md`](web/README.md), [`web/docs/parity.md`](web/docs/parity.md) |
| Map | Map tab with **Modern** (tilted 3D buildings, recoloured streets) and **Classic** styles, light/dark, optional satellite. Shows clustered status markers (pulsing for urgent posts), a tap preview card, recenter, and filter chips (multi-select + Clear). The chosen style is remembered | `ui/map` (MapLibre + OpenFreeMap) |
| Posts | **+ Post**: Lost / Found / Spotted / Needs help / Street animal / Adoption / Other, for any animal. A 6-step guided form takes up to 6 photos plus an optional video, a pin and search-area radius, last seen time, reward, and a contact method. It warns about likely duplicates. Posts can be edited, deleted, or marked reunited/resolved. "I saw this animal" sends updates with a photo and location | `ui/posts`, `data/PostRepository.kt` |
| Explore & search | Nearby feed (newest/closest), filters for type, distance, date and reward (remembered), and search across names, breeds, colours and areas in both languages | `ui/explore` |
| Adoption | Listings with Available / Pending / Adopted. People send adoption requests; owners approve or decline | `ui/adoption`, `data/AdoptionRepository.kt` |
| Pet sitting | Sitter profiles (services, rates, area), search, booking requests, accept/decline/complete, and reviews with ratings | `ui/sitting`, `data/SittingRepository.kt` |
| Messages | One-to-one chats with post links and photos, an optional phone/email shown only when chosen, and block/report | `ui/messages`, `data/ChatRepository.kt` |
| Alerts | Lost animals near a followed area, sightings of your post, possible matches, messages, adoption and booking updates. Each category can be switched off, and repeats are de-duplicated | `notifications/`, `data/NotificationRepository.kt` |
| Sharing | Share to WhatsApp and other apps; a printable **poster** image (1080×1350) for lost and found animals | `util/Share.kt`, `util/PosterMaker.kt` |
| Street cats | Sightings with on-device AI (coat colour/pattern, possible matches), cat profiles, health & TNR, rescue reports, Ehsan feeding stations, badges | `ui/cats`, `ui/sighting`, `ui/rescue`, `ui/stations`, `ai/` |
| Admin | Dashboard with stats, posts (hide/delete), members (suspend/restore), sitters, reports, rescue approvals, roles, and a moderation log. Admin rights are checked by `firestore.rules`, not by the app | `ui/admin`, `data/AdminRepository.kt` |
| Updates | Checks GitHub Releases (Settings → Check for updates, and automatically every 12 h), then downloads and installs the signed APK | `update/` |
| Privacy & security | Photos re-encoded without GPS/EXIF, approximate public locations, a private email, role-based rules, rate limits, suspended accounts blocked server-side | `firestore.rules`, `storage.rules` |

## Plans: what works on the free Spark plan

Everything above works on the free plan, with two exceptions that need the paid **Blaze** plan:

* **Videos on posts**, which need Cloud Storage. Photos are stored in Firestore and work on the free plan.
* **Instant push notifications**, which need the Cloud Function in `functions/`. Without it, alerts still arrive in real time while the app is open and through a background check about every 15 minutes.

Build with `-Pcatmap.blaze=true` once the project is on Blaze (see SETUP.md). Local demo builds
automatically enable the free Storage emulator; push still needs its flag and function/FCM test setup.

## Project layout

```
app/src/main/java/com/catmap/dubai/
  ai/              on-device cat photo analysis + similarity
  data/            Firebase repositories and models (posts, adoption, sitting, chats, admin…)
  notifications/   Android notifications, background check, FCM receiver
  update/          GitHub Releases updater
  ui/              Compose screens (map, explore, posts, adoption, sitting, messages, admin…)
  util/            images, sharing, poster
functions/         Cloud Function: content-free push when an alert is written (Blaze)
firestore.rules    security rules (roles, privacy, rate limits, admin)
.github/workflows  CI (tests, lint, rules) and signed releases on version tags
tools/             string generator, rules tests, demo seed, adb helper, release secrets helper
```

## Build and checks

Use Android Studio's bundled JDK and the checked-in Gradle wrapper (see HANDOFF.md for the exact toolchain).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
python tools/strings/build_strings.py .
.\gradlew.bat --no-parallel --max-workers=1 :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
```

* **63 Android tests** include drafts, staleness, strict link parsing, matching, image-cache hits, quiet hours,
  export JSON, checksums and upgrade-note policy. Three rendering tests produce **14 screenshot fixtures**.
* **Screenshot re-record:** run `.\gradlew.bat :app:recordRoborazziDebug`, inspect changes under
  `app/src/test/screenshots`, then run `.\gradlew.bat :app:verifyRoborazziDebug` in a separate invocation.
  Rendering is English light/dark, device-free; MapLibre and Arabic are deliberately excluded.
* **Firestore rules suite** (`tools/rulestest`, 244 checks) covers every collection, including admin-only actions, suspension, blocking and rate limits:
  `npx.cmd -y firebase-tools@latest emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"`
* **Push function test**: `npm --prefix functions test`, with the Firestore emulator running and FCM stubbed.
* **Staging build**: R8-shrunk like a release, but debug-signed and able to use the emulators:
  `.\gradlew.bat :app:installStaging '-Pcatmap.emulatorHost=10.0.2.2'`. Use it to catch shrinking problems before tagging a release.

A release APK is signed only when `keystore.properties` (or the CI secrets) is present. It contains ARM code only (about 24 MB).

To regenerate the baseline profile with a connected emulator:
`.\gradlew.bat :app:generateStagingBaselineProfile '-Pcatmap.emulatorHost=10.0.2.2'`.
Copy the generated staging profile files into `app/src/main/generated/baselineProfiles` after review.
Normal builds consume the checked-in profile without requiring a device. Profile collection may reset the
staging app's local sign-in; use a disposable demo account. Keep all demo writes in local emulators.
