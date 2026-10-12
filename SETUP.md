# CatMap Dubai – setup guide

The app is a native Android app (Kotlin + Jetpack Compose). Accounts, posts, chats and photos use
**Firebase**, and the free *Spark* plan is enough for everything except videos and instant push (see
step 7). The map uses **OpenFreeMap** vector tiles (no key). All AI runs on the phone.

* Steps 1–6 take about 15 minutes.
* Steps 7–9 are optional extras.
* [Local demo](#local-demo-no-firebase-project-needed) runs everything on your PC with no Firebase project.

---

## 1. Create the Firebase project

1. Go to <https://console.firebase.google.com> and sign in with a Google account.
2. **Create a project** (for example `catmap-dubai`). Google Analytics is not used, so you can turn it off.

## 2. Register the Android app

1. In the project overview, click the **Android** icon ("Add app").
2. **Android package name:** `com.catmap.dubai` (must match exactly).
3. Click **Register app**, then **Download google-services.json**, and put it at `cat/app/google-services.json`.
   You can skip the remaining "Add Firebase SDK" steps. The file is git-ignored, so it never reaches GitHub.

## 3. Turn on email sign-in

Console → **Build → Authentication → Get started → Sign-in method → Email/Password → Enable → Save**.

## 4. Create the database and publish rules and indexes

1. Console → **Build → Firestore Database → Create database**. Pick a location near the UAE (for example
   `me-central1`) and choose **production mode**.
2. Install the [Firebase CLI](https://firebase.google.com/docs/cli) (`npm i -g firebase-tools`, then
   `firebase login`). From this folder, run:

   ```bash
   firebase deploy --only firestore --project <your-project-id>
   ```

   This publishes `firestore.rules` and the composite indexes in `firestore.indexes.json`. The feed,
   bookings and alerts need those indexes. Index builds take a few minutes the first time.

## 5. Build and run

Open the `cat` folder in Android Studio, let Gradle sync, pick a phone or emulator, and press **Run ▶**.
Without `google-services.json` the app still builds, but it shows a "Firebase setup required" screen.

## 6. Make yourself an administrator

1. Create an account in the app and verify your email.
2. Firebase console → **Firestore → Data → `users`** → your user id → change `role` from `user` to `admin`.
   The other roles are `moderator` and `volunteer`.
3. **Profile → Admin dashboard** now appears. Admin actions are enforced by `firestore.rules`, so a modified
   app can't grant itself admin rights.

---

## 7. Optional: Blaze plan extras (videos and instant push)

Both need the pay-as-you-go **Blaze** plan. A student-sized project normally stays within the free
allowance, but set a **budget alert** in Google Cloud Billing first.

1. Upgrade the project to Blaze (console → ⚙ → Usage and billing).
2. **Videos:** console → **Build → Storage → Get started**. Then publish the storage rules:
   `firebase deploy --only storage --project <id>`.
3. **Push:** set `REGION` in `functions/index.js` (or `CATMAP_REGION=...` in a `functions/.env` file) to your
   Firestore location, then deploy the function:

   ```bash
   npm --prefix functions install
   firebase deploy --only functions --project <id>
   ```

   The push contains only an id. The phone reads the alert itself, so no names, messages or locations
   pass through Google's push servers.
4. Build with `-Pcatmap.blaze=true` (or add `catmap.blaze=true` to `local.properties`). For GitHub
   releases, set the repository **variable** `CATMAP_BLAZE` to `true`.

Without Blaze, the app hides the video button. Alerts still arrive live while the app is open and through
a background check about every 15 minutes.

## 8. Optional: satellite layer (MapTiler)

1. Create a free account at <https://cloud.maptiler.com> and copy your API key. Under the key's settings,
   restrict it to the Android app if you like.
2. Add `catmap.maptilerKey=<key>` to `local.properties`. For GitHub releases, add a repository secret
   named `MAPTILER_KEY`.

Without a key, the satellite switch is hidden. Everything else on the map works.

## 9. Updates through GitHub Releases (no Play Store)

Installed apps check `https://api.github.com/repos/<owner>/<repo>/releases/latest`. When a newer version
is published, they offer to download and install its APK. They check automatically every 12 hours,
and you can check any time from **Settings → Check for updates**.

**One-time setup**

1. **Signing key.** It has already been generated, at `release-keys/catmap-release.jks`, with its passwords
   in `keystore.properties`. Both are git-ignored. **Back up the `release-keys` folder and
   `keystore.properties` somewhere safe** (a password manager or a private drive). Android only installs
   an update signed with the *same* key, so losing it means everyone has to uninstall and reinstall.
2. **Repository.** The updater calls GitHub without a login, so the releases must be in a **public**
   repository. If the code should stay private, create a second public repo just for releases and
   adapt the workflow's publish step.
3. **Secrets.** Run `bash tools/release/github-secrets.sh`. It writes the four values to
   `release-keys/github-secrets.txt`. Add each one under GitHub → **Settings → Secrets and variables →
   Actions** as a repository secret, then delete that text file:
   * `CATMAP_KEYSTORE_BASE64`
   * `CATMAP_KEYSTORE_PASSWORD`
   * `CATMAP_KEY_ALIAS`
   * `CATMAP_KEY_PASSWORD`

   Also add these:
   * `GOOGLE_SERVICES_JSON`: the full text of `app/google-services.json`.
   * `MAPTILER_KEY`: optional.

**Each release**

```bash
git tag v2.0.1
git push origin v2.0.1
```

The **Release APK** workflow runs the unit tests, builds the signed APK with that version, checks the
signature, and attaches `CatMap-2.0.1.apk` to a new GitHub Release. The version must go up each time
(2.0.1 → 2.0.2 → 2.1.0). Edit the release text on GitHub if you like, because it is shown in the update dialog.

**First install on a phone:** download the APK from the release page and allow "Install unknown apps"
for the browser. If the phone has a debug build from Android Studio, uninstall that first. It is signed
with a different key, so Android won't update it in place.

---

## Local demo (no Firebase project needed)

Everything runs on your PC in the Firebase Emulator Suite, including videos and five demo accounts.
Demo data can't reach a real project: the seed script only accepts a localhost emulator with a `demo-`
project id, and the debug build always uses the `demo-catmap` project when pointed at the emulators.

1. Install Node.js and Java (Android Studio's JBR works). From this folder, run:

   ```bash
   npx -y firebase-tools@latest emulators:start --only auth,firestore,storage --project demo-catmap --import ./.emulator-data --export-on-exit
   ```

2. **Demo accounts** (first time only):
   1. Copy `tools/seed/demo-accounts.example.json` to `tools/seed/demo-accounts.local.json` and set
      the passwords. Firebase needs at least 6 characters. The `.local.json` file is git-ignored.
   2. Run `node tools/seed/seed.mjs`.

   This creates the following accounts plus demo posts, sitters, a booking, a review and a chat:

   | Username | Role |
   | --- | --- |
   | `admin` | admin (dashboard, moderation, suspend members, hide/delete posts) |
   | `ali`, `mohammad`, `anirudh`, `hassan` | members |

   Sign in with the **username** and its password from `demo-accounts.local.json`. Logging in by username
   only works for accounts whose username record carries a `loginEmail`. Only the seed script, or another
   admin tool, can set that field; the rules don't let the app write it.
3. Run the app against the emulators:
   * **Android emulator:** `.\gradlew.bat :app:installDebug '-Pcatmap.emulatorHost=10.0.2.2'`
   * **Phone over USB:** first run `adb reverse tcp:9099 tcp:9099`, then the same for ports `8080` and
     `9199`. Then install with `'-Pcatmap.emulatorHost=127.0.0.1'`.

Verification emails aren't sent by the emulator. The link is printed in the emulator terminal.

## Testing tips

* **Emulator location:** use the emulator's **⋯ → Location** to search for Dubai (25.2048, 55.2708).
  Any post can also be placed with the map pin picker.
* **Photos on the emulator:** drag pictures onto the emulator window to add them to its gallery.
* **Rate limit:** the rules require about 10 seconds between new posts and reports, to stop spam.

## Test the rules

```powershell
npm --prefix tools/rulestest ci
firebase emulators:exec --config firebase.rules-test.json --only firestore --project demo-catmap-rules-test "npm --prefix tools/rulestest test"
```

This uses port 8180 and a disposable project, so the demo data on 8080 is untouched. After changing
`firestore.rules`, run the suite and redeploy with `firebase deploy --only firestore`.
