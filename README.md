# CatMap Dubai 🐾

A community app for Dubai's animals: lost, found and spotted pets of any kind, animals that need help,
street cats, adoption, and a pet-sitting marketplace. It has a live vector map, messaging, alerts for
lost animals nearby, on-device AI for cat photos, Ehsan feeding stations, an admin dashboard, and full
English/Arabic support (RTL). Updates are delivered from GitHub Releases, with no Play Store needed.

**First time? Follow [SETUP.md](SETUP.md).** It takes about 15 minutes: create a free Firebase project,
add `google-services.json`, publish the rules, then press Run. To try it with no Firebase project at all,
use the local emulators and the demo accounts ([SETUP.md → Local demo](SETUP.md#local-demo-no-firebase-project-needed)).

## Where each feature lives

| Area | In the app | Code |
| --- | --- | --- |
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

Build with `-Pcatmap.blaze=true` once the project is on Blaze (see SETUP.md). Both features are
already on in the local emulator demo.

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
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:lintRelease :app:assembleRelease
```

* **Unit tests** cover filters, duplicate detection, keywords, version comparison, matching, privacy and notification delivery.
* **Firestore rules suite** (`tools/rulestest`, 190 checks) covers every collection, including admin-only actions, suspension, blocking and rate limits:
  `firebase emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"`
* **Push function test**: `npm --prefix functions test`, with the Firestore emulator running and FCM stubbed.
* **Staging build**: R8-shrunk like a release, but debug-signed and able to use the emulators:
  `.\gradlew.bat :app:installStaging '-Pcatmap.emulatorHost=10.0.2.2'`. Use it to catch shrinking problems before tagging a release.

A release APK is signed only when `keystore.properties` (or the CI secrets) is present. It contains ARM code only (about 22 MB).
