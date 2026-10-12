# Prompt for Codex — CatMap Dubai, next round of improvements

Copy everything below the line into Codex. Run it from the project root (`C:\Users\mohammad\Desktop\cat`).

---

You are working on **CatMap Dubai**, a native Android app (Kotlin + Jetpack Compose + Firebase) for Dubai's animal
community: lost/found pets, adoption, pet sitting, messaging, street cats, admin tools. The 2.0 feature set is complete (published as test releases v0.0.1 and v0.0.2)
and tested. Your job is the next round of improvements listed below.

## Read first (mandatory)

1. `HANDOFF.md`: current state, toolchain, design decisions and the test workflow. Treat it as the source of truth.
2. `README.md` (feature → code map) and `SETUP.md` (Firebase, Blaze, releases, local demo).
3. Skim the code you are about to change before changing it, and match its style: comment density, naming, no DI
   framework, and repositories in `data/` exposed through `AppContainer`.

## Hard rules

* **Secrets:** never read out, print, log, copy or modify `keystore.properties`, `release-keys/`, `*.jks`,
  `tools/seed/demo-accounts.local.json`, `test-accounts.local.txt`, `local.properties` or `app/google-services.json`.
  Never regenerate the signing key.
* **Git:** do not commit, push, add remotes or create tags unless the user explicitly tells you to.
* **Toolchain:** don't upgrade or downgrade Gradle, AGP, Kotlin, the Compose BOM, the Firebase BOM, MapLibre, compileSdk
  or targetSdk. Only add a new dependency when a task says so or there is no reasonable alternative, and say so in your report.
* **Free plan first:** everything must work on the Firebase **Spark** plan. Anything that needs Blaze (Storage,
  Cloud Functions, FCM) must stay behind the existing `catmap.blaze` / `BuildConfig.STORAGE_ENABLED` /
  `PUSH_ENABLED` flags. Don't add paid APIs or services that need new API keys.
* **Strings:** never hand-edit `values/strings.xml` or `values-ar/strings.xml`. They are generated. Add each string in both
  English **and** Arabic to the tables in `tools/strings/strings_[a-h].py`. If you add a new table file
  (`strings_i.py`), also add its letter to the `for part in "abcdefgh"` loop in `tools/strings/build_strings.py`.
  Then run `python tools/strings/build_strings.py .`, which must report no missing or unused keys.
  The convention for counts is "Label: N", so there are no plurals.
* **Security lives in `firestore.rules`:**
  * Any new collection or field needs rules with type and size validation, plus tests in `tools/rulestest/test.mjs`, positive *and* negative.
  * Keep the existing patterns:
    * `verified()` excludes suspended users.
    * Rate limiting uses `lastPostAt == request.time`.
    * Single-doc reads use a get/list split, with `resource == null` allowed on get.
    * Notifications use deterministic, create-only ids.
  * New composite queries need entries in `firestore.indexes.json`.
* **RTL and themes:** every new screen must work in Arabic RTL and in dark mode. Body text already uses `TextDirection.Content`.
  Check new screens for layout direction bugs.
* **Map:** in `ui/map/CommunityMap.kt`, always go through `MapHolder.style`, which returns only a fully loaded style.
  Never keep a raw `Style` reference.
* **Demo isolation:** demo data only ever goes into the local emulators (`tools/seed/seed.mjs` guards this). Keep it that way.
* **Honesty:** never claim something works unless you ran it. If you can't run something (no Android emulator, no
  Firebase project), say exactly what was not verified.

## How to build and test

These commands are for Windows with Git Bash. Java isn't on PATH, so set it first:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH"
python tools/strings/build_strings.py .
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug
# Firestore rules suite (separate emulator on port 8180; needs Node + Java):
npm --prefix tools/rulestest ci
npx -y firebase-tools@latest emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"
```

* Don't set `MSYS_NO_PATHCONV=1` in the same command as `gradlew.bat`: it breaks `JAVA_HOME`.
* If an Android emulator and the Firebase emulators are available, install with
  `./gradlew.bat :app:installDebug -Pcatmap.emulatorHost=10.0.2.2` or `:app:installStaging` (the R8-shrunk build).
  Drive it with `tools/adb.sh` as described in HANDOFF.md.
* **Before you finish each task:**
  * Unit tests and both lint variants must pass, with **0 lint errors**.
  * If you touched rules, the rules suite must pass.
  * If you touched release, the R8 or reflection code, `:app:assembleRelease` must build.

## Tasks (in priority order — finish and verify each before starting the next)

### Tier 1 — reliability and quality

1. **Screenshot tests that run without a device.**
   * **What:** add Roborazzi (Robolectric + Compose) screenshot tests for `PostCard`, `PostGridCard`, `StatusBadge`, the post-detail header section, the filter sheet, and the bitmap from `util/PosterMaker.kt`. Cover each in EN/AR × light/dark.
   * **Skip:** the MapLibre map itself, which won't render under Robolectric.
   * **Done when:** `./gradlew :app:verifyRoborazziDebug` passes against recorded baselines, and HANDOFF.md explains how to re-record.
2. **Remaining lint warnings.**
   * **What:**
     * Reword the four PluralsCandidate strings to the "Label: N" convention.
     * Use the lambda `Modifier.offset { }` in `LocationPickerScreen.kt` and `MapScreen.kt`.
     * Apply the KTX suggestions (`toUri`, `withTranslation`, `withClip`, `scale`).
     * Replace `LocalContext.current.resources` reads in composables with `LocalResources.current`.
   * **Leave alone:** OldTargetApi, GradleDependency and LogNotTimber.
   * **Done when:** only those three warning types remain.
3. **Draft autosave for the post wizard.**
   * **What:** `ui/posts/PostEditorViewModel.kt` loses everything if the app is killed. Save the draft to DataStore on every step change: the text fields, category, animal, pin, radius and reward. Keep the photos in `cacheDir/drafts/`. When the editor opens with a draft saved, offer "Continue draft / Discard". Clear the draft after a successful publish. Edits of existing posts don't use drafts.
   * **Done when:** there's a unit test for the draft serialisation, and (if an emulator is available) a draft survives `adb shell am force-stop`.
4. **"Still missing?" reminders and stale posts.**
   * **What:** when the owner opens the app, check their open Lost/Found posts with no update in 14 days. Send one inbox alert per post per 14-day window, with a deterministic id like `stale_{postId}_{window}`. Tapping it opens the post with "Mark reunited/resolved" and "Still looking" actions. "Still looking" bumps `lastActivityAt`.
   * **Feed and map:** hide open posts older than 90 days with no activity by default, and add an "Include older posts" switch to the filter sheet.
   * **Done when:** the rules and rules tests allow the owner to bump `lastActivityAt`, there are unit tests for the staleness logic, and the strings exist in EN and AR.
5. **Deep links and a QR code on the poster.**
   * **What:**
     * Add a `catmap://post/{id}` intent filter on `MainActivity`, routed through the existing navigation in `ui/navigation/Routes.kt` / `AppNavHost.kt`. Reuse the same "wait for the graph" logic the notification links use.
     * Put the link in share texts (`util/Share.kt`).
     * Print a QR code of the link on the poster. zxing `core` (pure Java) is acceptable for this.
   * **Done when:** there are unit tests for link parsing (valid, invalid and injected ids rejected), and `adb shell am start -d catmap://post/<id>` opens the post if an emulator is available.

### Tier 2 — features

6. **Photo-assisted matching for lost/found.**
   * **What:** `PostRepository.create` currently suggests possible matches by animal type and a 5 km distance. Store a small colour fingerprint on each post when it is created. Reuse the colour-histogram / layout parts of `ai/CatAnalyzer.kt`; they're not cat-specific. Cap it at about 64 floats and validate its size in the rules.
   * **Ranking:** rank candidates by fingerprint similarity + distance + time. Show a "Possible matches" section on Lost/Found post details with the score, and keep the existing `match_{postId}` alert.
   * **Done when:** there are unit tests for ranking, and the rules tests cover the new field.
7. **Accessible list view for the map.**
   * **What:** map markers can't be reached with TalkBack. Add a Map/List toggle on `ui/map/MapScreen.kt` that shows the same filtered features as a list sorted by distance, using the existing cards. Give the marker preview card proper semantics.
   * **Done when:** the list honours every active filter, and it works in RTL.
8. **Help directory, managed by admins.**
   * **What:** add a `helpDirectory` collection of vets, shelters and emergency contacts with name, kind, area, phone, hours and an optional lat/lng. Everyone can read it; only admins can write it. Add a screen reached from Explore, and admin create/edit/delete in the admin dashboard.
   * **No real data:** don't hard-code any real phone numbers or businesses. The demo seed may add clearly fake entries, marked "Demo".
   * **Done when:** the rules and rules tests are in place, and the strings exist in EN and AR.
9. **Verified sitters.**
   * **What:** admins can mark a sitter profile `verified` from `AdminSitters` in `ui/admin/AdminCommunityScreens.kt`. Show a badge in search results and on the profile, and add a "Verified only" filter. Only admins may set the field.
   * **Done when:** the rules tests cover it (sitter self-verification is denied, admin is allowed).
10. **Download my data.**
    * **What:** add Settings → "Download my data", which builds a JSON file of the user's profile, posts (with updates), favourites, adoption requests, bookings, reviews they wrote and messages they sent. Leave photos out, but list their ids. Share the file through the existing FileProvider.
    * **Done when:** there's a unit test for the JSON builder, and nothing belonging to other users is included beyond what the user can already read.
11. **Quiet hours for notifications.**
    * **What:** add a start/end time to Settings. During quiet hours, system notifications are not shown, but the alerts still land in the inbox. Messages from an ongoing booking are exempt.
    * **Done when:** there are unit tests for the time-window logic, including windows that cross midnight.

### Tier 3 — polish (only if time remains)

12. **Admin analytics.** Add charts to the admin stats panel: posts per week for 8 weeks, reunion rate, new members per week. Use Firestore `count()` aggregations per time range, with no full collection reads. Draw the charts in Compose without a new library.
13. **"What's new" after an update.** Keep the release notes `update/Updater.kt` already fetches. On the first launch after versionCode increases, show them once in a sheet.
14. **Update integrity.**
    * In `.github/workflows/release.yml`, also upload a `CatMap-<version>.apk.sha256` asset.
    * `Updater` verifies the downloaded APK against it when the asset is present, and refuses to install on a mismatch.
    * Add a unit test for the verification.
15. **Microchip field.** Add a "Microchipped: yes / no / unknown" field to posts. Never store the chip number publicly. Add it to the editor, detail screen, poster and filters.

## When you finish

* Update `HANDOFF.md`:
  * Add a dated section listing what you changed, what you verified (with the commands and their results), what you could not verify, and any new decisions.
  * Update the test counts and the string count.
* Update `README.md` if a feature was added.
* In your final message, give a short table with each task, its status (done / partial / skipped) and how it was verified. List any new dependencies and any rules changes.
