# Prompt for Codex — CatMap Dubai, next round of improvements

Copy everything below the line into Codex. Run it from the project root (`C:\Users\mohammad\Desktop\cat`).

---

You are working on **CatMap Dubai**, a native Android app (Kotlin + Jetpack Compose + Firebase) for Dubai's animal
community: lost/found pets, adoption, pet sitting, messaging, street cats and admin tools.

**Current state:**
* The feature set is complete.
* It is published on GitHub (`yneager/CatMap-Dubai`) as signed test releases. **v0.0.3** is the latest, and the first release connected to the real Firebase project `catmap-dubai`.
* Installed apps update themselves from GitHub Releases.

**Your job:** do **everything** in this prompt, in the order given:
* Task 0: a bug fix.
* Tasks A–C: the owner's main requests (themed icons replacing every emoji, UI consistency, performance).
* Tasks 1–15: features and quality work.

Then write the handoff described at the end.

## Read first (mandatory)

1. `HANDOFF.md`: current state, toolchain, design decisions and the test workflow. Treat it as the source of truth.
2. `README.md` (feature → code map) and `SETUP.md` (Firebase, releases, local demo).
3. Before changing any code, read it, and match its style: comment density, naming, no DI framework, and
   repositories in `data/` exposed through `AppContainer`.

## Hard rules

* **Secrets:** never read out, print, log, copy or modify any of these:
  * `keystore.properties`, `release-keys/`, `*.jks`
  * `app/google-services.json`, `local.properties`
  * `tools/seed/demo-accounts.local.json`, `test-accounts.local.txt`

  Never regenerate the signing key.
* **Git, releases and production:**
  * Don't commit, push, tag or add remotes unless the user explicitly tells you to.
  * **Never deploy anything to the real Firebase project** (`firebase deploy`, console changes). The Firebase CLI on this machine is logged in to the owner's account; leave production alone.
  * Test rules only with the emulator commands below.
* **Toolchain:** don't upgrade or downgrade Gradle, AGP, Kotlin, the Compose BOM, the Firebase BOM, MapLibre, compileSdk or targetSdk.
  Only add a dependency when a task allows it or there is no reasonable alternative, and list it in your report.
* **Free plan first:** everything must work on the Firebase Spark plan. Anything that needs Blaze stays behind the
  existing `catmap.blaze` / `STORAGE_ENABLED` / `PUSH_ENABLED` flags. Add no paid APIs and no new API keys.
* **Arabic: leave it alone for now** (owner's instruction).
  * Don't translate, reword or review Arabic text, and don't spend time on RTL testing.
  * Do keep the code RTL-safe: use start/end rather than left/right, and keep auto-mirrored icons.
  * Strings are generated, so never hand-edit `values*/strings.xml`. Add new strings to `tools/strings/strings_[a-h].py`.
    The generator requires an Arabic entry, so put the **English text in the Arabic column** and add a `# TODO-AR` comment on that line.
  * If you add a table file (`strings_i.py`), add its letter to the `for part in "abcdefgh"` loop in `tools/strings/build_strings.py`.
  * Run `python tools/strings/build_strings.py .`. It must report no missing or unused keys.
  * The one exception is Task A: you may remove emoji characters from Arabic strings, without rewording them.
* **Security lives in `firestore.rules`:**
  * Every new collection or field needs validated rules and positive *and* negative tests in `tools/rulestest/test.mjs`.
  * Keep the existing patterns:
    * `verified()` excludes suspended users.
    * Rate limiting uses `lastPostAt == request.time`.
    * Single-doc reads use a get/list split, with `resource == null` allowed on get.
    * Notifications use deterministic, create-only ids.
  * New composite queries need entries in `firestore.indexes.json`.
* **Map:** in `ui/map/CommunityMap.kt`, always go through `MapHolder.style`, which returns only a fully loaded style.
  Never keep a raw `Style` reference.
* **Demo isolation:** demo data only goes into the local emulators (`tools/seed/seed.mjs` guards this).
* **Honesty:** never claim something works unless you ran it. If you can't run something (no Android emulator, no
  device), say exactly what was not verified.

## How to build and test

These commands are for Windows with Git Bash. Java isn't on PATH. In PowerShell, use `npx.cmd`, because script execution is disabled.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
python tools/strings/build_strings.py .
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
# Firestore rules suite (separate emulator on port 8180; needs Node + Java):
npm --prefix tools/rulestest ci
npx -y firebase-tools@latest emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"
```

* Don't set `MSYS_NO_PATHCONV=1` in the same command as `gradlew.bat`.
* With an Android emulator and the local Firebase emulators running, you can install builds against the emulators:
  * `./gradlew.bat :app:installDebug -Pcatmap.emulatorHost=10.0.2.2`
  * `:app:installStaging`: R8-shrunk like a release, so use it for performance work.
  * Drive the app with `tools/adb.sh` (HANDOFF.md explains it).
  * The local default versionName is 0.0.1. If the emulator already has another build, run `adb uninstall com.catmap.dubai` first.
* **Before you finish each task:**
  * Unit tests and both lint variants pass, with 0 lint errors.
  * The rules suite passes if you touched rules.
  * `assembleRelease` builds.
  * Take a screenshot of each screen you changed, if a device is available.

## Task 0 — the line stuck in the middle of the Explore tab (do this first)

* **Symptom:** on Explore, a dark, semi-transparent horizontal band about one status bar tall (~24 dp) sits across the middle of the screen and stays there while the list scrolls under it.
* **Cause (already diagnosed):** in `ui/explore/ExploreScreen.kt` (around line 197), the status-bar scrim is added as a **second root element next to `PullToRefreshBox`**:
  ```kotlin
  Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(...))
  ```
  Nothing anchors it to the top, so the navigation container places it somewhere in the middle.
* **Fix:**
  * Wrap the screen's content in one `Box(Modifier.fillMaxSize())` and put the scrim inside it with `Modifier.align(Alignment.TopCenter)`. Or drop the scrim and use a proper top inset or app bar.
  * Then look for the same "two root layouts" pattern on other screens (Map, Profile, detail screens) and fix any you find.
* **Done when:** the band is gone, the status-bar area is still readable over scrolled content, and you've confirmed it with a screenshot if a device is available.

## Task A — replace every emoji with CatMap's own icon set (owner's request)

The app uses emoji as icons in many places, and they look different on every phone. Replace **all** of them with a
custom icon set that you **draw yourself** as Android VectorDrawables. Don't use downloaded icon packs or emoji fonts.
Material Symbols may stay only for generic UI controls (back, close, search, settings).

1. **Inventory.** Search for emoji in all of these:
   * Kotlin code, string tables, the poster and the demo seed.
   * Notification texts and share texts.

   About 20 files contain them, including:
   * `gamification/Badges.kt` (18 badges)
   * `data/model/Community.kt` (`AnimalType.emoji`, categories, `MapLayer.emoji`)
   * `ui/map/MapMarkers.kt` (marker bubbles drawn from emoji text)
   * `ui/map/MapScreen.kt`, `ui/explore/ExploreScreen.kt` (tiles and chips such as "🐾 All animals"), `ui/explore/Filtering.kt`
   * `ui/posts/PostDetailScreen.kt` (fact chips such as 🎂🎨🧬), `PostEditorScreen.kt`, `PostLists.kt`
   * `ui/profile/ProfileScreens.kt`, `update/UpdateUi.kt` (🚀)
   * `util/Share.kt`, `data/ChatRepository.kt`
   * `tools/strings/strings_d/e/g/h.py`

   List every occurrence in the handoff.
2. **Design one consistent set** that fits the brand:
   * **Palette:** primary `#A9531A` (warm orange-brown), secondary `#00696B` (teal), tertiary `#7A5900`, and the brand gradient `#FF7A45 → #E5486B → #8E5CF7`.
   * **Style:** rounded, friendly, filled-plus-outline. 24 dp grid, 2 dp strokes, rounded caps and joins, simple shapes that stay readable at 16 dp.
   * Monochrome icons should tint from the theme, so they work in dark mode. Use multi-colour artwork only where colour carries meaning: animal types, categories, badges, map markers.
   * **Coverage:**
     * Animals: cat, dog, bird, rabbit, other/paw.
     * Post categories: lost, found, spotted, needs help, street animal, adoption, other.
     * Map layers and point kinds: stations, sitters, rescue.
     * Fact chips: age, colour, breed, sex, size, microchip.
     * Explore tiles, all 18 badges, the update sheet icon, and empty-state illustrations where emoji were used.
3. **Files and API:**
   * Put the icons in `app/src/main/res/drawable/ic_cm_*.xml`.
   * Add a single registry, e.g. `ui/icons/CatMapIcons.kt`, that maps each animal, category, layer and badge to its drawable.
   * Replace the `emoji` fields with icon references (`@DrawableRes`) and remove the old emoji fields.
   * Every icon used without visible text needs a `contentDescription`.
4. **Map markers:** `MarkerFactory` must render the vector icons into marker bitmaps (inside the existing pin/bubble
   shape, coloured by category) instead of drawing emoji text. Keep the clustering, the pulsing for urgent posts, and the selected-marker scale animation working.
5. **Poster:** `util/PosterMaker.kt` must draw the vector icons where it used emoji, including the no-photo placeholder.
6. **Text:** remove emoji from strings in both languages (removal only, no rewording) and from share and notification texts.
   Where an icon helps, show it next to the text in the UI instead.
7. **Review sheet:** produce `docs/icons-preview.png`, a contact sheet of every icon at 48 dp and 16 dp on light and dark backgrounds.
   A Compose preview or a small Robolectric/Roborazzi test that renders it both work.
8. **Done when:**
   * A search for emoji in `app/src/main`, `tools/strings` and `tools/seed` finds none. Include the search command in your report.
   * The map, Explore, post editor, post detail, profile/badges and poster have screenshots, if a device is available.

## Task B — UI consistency pass (owner's request)

Audit every screen and make the same things look and behave the same way. At minimum, check and unify these:

* **Top bars:** use `AppTopBar` everywhere, with a back button on every pushed screen, consistent titles, and no screen without a way back.
* **Spacing:** use one horizontal gutter (16 dp) and consistent vertical rhythm. Section headers use `SectionHeader` everywhere.
* **Cards:** one corner radius per role (the theme shapes in `ui/theme/Theme.kt`), one elevation and border style, and no mixed card styles in the same list.
* **Buttons:** one primary action per screen (filled), with secondary actions outlined or text. Consistent heights, icon spacing, and loading states (`runner.run` + a disabled state while busy).
* **Chips and status pills:** `StatusPill` / `StatusBadge` colours come from the category everywhere: feed, map preview, detail, My posts, admin lists.
* **States:** every list has the same loading skeleton, empty state (`EmptyState` with the new icons) and error state with a retry. The offline banner behaves the same on every tab.
* **Typography:** use theme styles only. No hard-coded `fontSize` or colours outside the theme, and nothing hard-coded that breaks dark mode.
* **Insets:** no content under the status or navigation bar, bottom padding so floating buttons and the bottom bar never cover the last list item, and the keyboard never covers a focused field.
* **Small touches:** 48 dp touch targets, consistent date and distance formatting (`distanceText`, `dateTime`), and consistent "removed / not found" screens (`Flow.loaded()`).

Keep a short before/after list in the handoff. Don't redesign the app; make what exists consistent.

## Task C — performance (owner's request)

Measure before and after where you can, and report the numbers.

* **Startup:**
  * Check what runs in `CatMapApp.onCreate` and `AppContainer` on the main thread, and move non-urgent work (MapLibre init, notification channels, push registration, the WorkManager schedule) off the main thread or make it lazy where safe.
  * Measure the staging cold start with `adb shell am start -W` (about 2.3–2.9 s on the emulator today).
  * Add a Baseline Profile (`androidx.baselineprofile` + a macrobenchmark module) **only if** you can generate it on an emulator or device. Otherwise skip it and say so.
* **Compose:**
  * Stable `key`s and `contentType` in every lazy list.
  * Hoist expensive work out of composition (`remember`, `derivedStateOf`).
  * Avoid reading fast-changing state high in the tree (map camera, scroll offsets).
  * Mark model classes `@Immutable` / `@Stable` where correct.
  * Check recomposition counts with the Layout Inspector if available.
* **Images:**
  * Feeds and cards must load thumbnails (`{id}_t`), never full photos.
  * Decode at the target size.
  * Check that the memory and disk caches in `ImageRepository` / `ImageDiskCache` are hit when scrolling back.
* **Firestore:**
  * Review the listeners. The feed listens to up to 400 posts (`PostRepository.feed`).
  * Use one shared feed flow (`shareIn`/`stateIn` in the container) instead of a listener per screen.
  * Detach listeners when screens leave.
  * Add paging for Explore if you can do it without breaking filters.
* **Map:** update the GeoJSON source only when features actually change (diff by id and fields), and don't rebuild marker bitmaps that already exist in the `MarkerFactory` cache.
* **APK:** keep the release APK at or below about 24 MB. Check that your icons and new code don't grow it noticeably.

## Tasks 1–15 (in priority order)

### Tier 1 — reliability and quality

1. **Screenshot tests that run without a device.**
   * **What:** add Roborazzi (Robolectric + Compose) tests for `PostCard`, `PostGridCard`, `StatusBadge`, the post-detail header, the filter sheet, the `PosterMaker` bitmap and the icon set. Cover each in English light and dark; Arabic is skipped for now. Skip the MapLibre map itself.
   * **Done when:** `./gradlew :app:verifyRoborazziDebug` passes, and HANDOFF.md explains how to re-record.
2. **Remaining lint warnings.**
   * **What:**
     * Reword the four PluralsCandidate strings in English to the "Label: N" convention.
     * Use the lambda `Modifier.offset { }` in `LocationPickerScreen.kt` and `MapScreen.kt`.
     * Apply the KTX suggestions.
     * Replace `LocalContext.current.resources` reads in composables with `LocalResources.current`.
   * **Leave alone:** OldTargetApi, GradleDependency and LogNotTimber.
3. **Draft autosave for the post wizard.**
   * **What:** in `ui/posts/PostEditorViewModel.kt`, save the draft to DataStore on every step change: the text fields, category, animal, pin, radius and reward. Keep the photos in `cacheDir/drafts/`. When a draft exists, offer "Continue draft / Discard". Clear it after publishing. Edits of existing posts don't use drafts.
   * **Done when:** there's a unit test for the serialisation.
4. **"Still missing?" reminders.**
   * **What:** when the owner opens the app, check their open Lost/Found posts with no update in 14 days. Send one inbox alert per post per 14-day window (id `stale_{postId}_{window}`), with "Mark reunited/resolved" and "Still looking" actions. "Still looking" bumps `lastActivityAt`.
   * **Feed and map:** hide open posts older than 90 days with no activity by default, with an "Include older posts" filter switch.
   * **Done when:** the rules and tests are updated, and there are unit tests for the staleness logic.
5. **Deep links and a QR code on the poster.**
   * **What:**
     * Add a `catmap://post/{id}` intent filter on `MainActivity`, routed through `ui/navigation/Routes.kt` / `AppNavHost.kt`. Reuse the "wait for the graph" logic the notification links use.
     * Put the link in share texts.
     * Print a QR code of the link on the poster. zxing `core` is allowed.
   * **Done when:** there are unit tests for link parsing (invalid and injected ids rejected).

### Tier 2 — features

6. **Photo-assisted lost/found matching.**
   * **What:** store a small colour fingerprint on each post when it's created, reusing the histogram / layout parts of `ai/CatAnalyzer.kt`. Cap it at about 64 floats and validate its size in the rules.
   * **Ranking:** rank candidates by similarity + distance + time. Show a "Possible matches" section on post details, and keep the `match_{postId}` alert.
   * **Done when:** there are unit tests and rules tests.
7. **Accessible list view for the map.** Add a Map/List toggle on `ui/map/MapScreen.kt` showing the same filtered features sorted by distance, plus proper semantics on the marker preview card.
8. **Help directory, managed by admins.**
   * **What:** add a `helpDirectory` collection of vets, shelters and emergency contacts. Everyone can read it; only admins can write it. Add a screen from Explore and admin create/edit/delete.
   * **No real data:** never hard-code real businesses or phone numbers. The seed may add clearly fake entries marked "Demo".
   * **Done when:** the rules tests cover it.
9. **Verified sitters.** Admins set `verified` from `AdminSitters`. Show a badge, and add a "Verified only" filter. Only admins can set the field, with rules tests.
10. **Download my data.** Add Settings → "Download my data", a JSON export of the user's own data, shared through the FileProvider. Add a unit test for the builder.
11. **Quiet hours.** Add a start/end time in Settings. During quiet hours, system notifications are suppressed, but alerts still land in the inbox. Add unit tests, including windows that cross midnight.

### Tier 3 — polish

12. **Admin analytics charts:** posts per week, reunion rate and new members. Use Firestore `count()` aggregations, and draw the charts in Compose without a new library.
13. **"What's new" sheet:** show the release notes once after an update.
14. **Update integrity:**
    * `release.yml` also uploads `CatMap-<version>.apk.sha256`.
    * `Updater` verifies the download against it when the file is present.
    * Add a unit test.
15. **Microchip field** (yes / no / unknown; never store the chip number publicly) on posts: in the editor, detail screen, poster and filters.

Also fix the login screen tagline, which still says "Spot, protect and care for Dubai's community cats". It should
describe the all-animals app. Change the English only.

## When you finish — update HANDOFF.md (required)

Add a dated section at the top of `HANDOFF.md` with these parts:

1. **What I did:** each task (0, A, B, C, 1–15) with its status (done / partial / skipped), the files touched, and how it was verified, with commands and results.
2. **What I did not do, and why**, including anything you couldn't verify, such as screens with no device available or the final Android install step.
3. **What needs to be done next**, as a prioritised list. Include the `# TODO-AR` strings waiting for Arabic translation and an RTL review of the new icons and screens.
4. **New dependencies, rules changes and new collections/fields.**
5. **Updated counts:** unit tests, rules checks, strings, and the release APK size.
6. **Emoji inventory:** where each emoji was and what replaced it.

Also update `README.md` for any new feature.

Your final message should contain a short table of the tasks with their status and how each was verified. Do
**not** commit, push, tag or release; the owner will review first.
